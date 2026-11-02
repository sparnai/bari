#!/usr/bin/env python3
"""Bari – serverinis skrydžių kainų stebėjimas (veikia GitHub Actions).

Skriptas:
  * nuskaito kelionės aprašą iš config/trip.json (tą patį naudoja ir Android programėlė),
  * paklausia viešos Ryanair kainų sąsajos pigiausio bilieto kiekvienam segmentui,
  * kaupia kainų istoriją (data/history.json) ir naujausias kainas (data/prices.json),
  * atnaujina kainų lentelę README.md faile tarp žymų <!-- PRICES:START --> ir <!-- PRICES:END -->,
  * siunčia Telegram žinutę, kai kaina nukrenta arba pasiekia tikslinę kainą.

Paleidimas:  python tracker/flight_tracker.py [--dry-run]
"""
from __future__ import annotations

import argparse
import html
import json
import os
import sys
import time
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any, Callable, Optional

try:
    from zoneinfo import ZoneInfo

    LOCAL_TZ = ZoneInfo("Europe/Vilnius")
except Exception:  # pragma: no cover - zoneinfo visada yra Python 3.9+
    LOCAL_TZ = timezone.utc

ROOT = Path(__file__).resolve().parent.parent
TRIP_FILE = ROOT / "config" / "trip.json"
DATA_DIR = ROOT / "data"
PRICES_FILE = DATA_DIR / "prices.json"
HISTORY_FILE = DATA_DIR / "history.json"
README_FILE = ROOT / "README.md"

START_MARK = "<!-- PRICES:START -->"
END_MARK = "<!-- PRICES:END -->"

API_URL = "https://services-api.ryanair.com/farfnd/v4/oneWayFares"
HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 "
        "(KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
    ),
    "Accept": "application/json",
}
MAX_HISTORY = 400

CITY = {
    "VNO": "Vilnius", "KUN": "Kaunas", "PLQ": "Palanga", "RIX": "Ryga",
    "TRN": "Turinas", "BRI": "Baris", "VIE": "Viena", "BGY": "Bergamas",
    "MXP": "Milanas", "LIN": "Milanas", "WAW": "Varšuva", "WMI": "Varšuva",
    "ROM": "Roma", "CIA": "Roma", "FCO": "Roma", "NAP": "Neapolis",
    "BDS": "Brindizis", "FRA": "Frankfurtas", "MUC": "Miunchenas",
}

Fetcher = Callable[[dict], tuple[Optional[dict], Optional[str]]]


# ---------------------------------------------------------------------------
# Pagalbinės funkcijos
# ---------------------------------------------------------------------------

def euro(value: Optional[float]) -> str:
    if value is None:
        return "—"
    text = f"{value:,.2f}".replace(",", " ").replace(".", ",")
    if text.endswith(",00"):
        text = text[:-3]
    return f"{text} €"


def parse_local(value: Optional[str]) -> Optional[datetime]:
    if not value:
        return None
    try:
        return datetime.strptime(value[:16], "%Y-%m-%dT%H:%M")
    except ValueError:
        return None


def hhmm(value: Optional[str]) -> str:
    dt = parse_local(value)
    return dt.strftime("%H:%M") if dt else "?"


def duration_text(minutes: int) -> str:
    hours, mins = divmod(abs(minutes), 60)
    if hours and mins:
        return f"{hours} val. {mins} min."
    if hours:
        return f"{hours} val."
    return f"{mins} min."


def booking_url(seg: dict) -> str:
    if seg.get("source", "ryanair") == "ryanair":
        return (
            "https://www.ryanair.com/gb/en/trip/flights/select?adults=1&teens=0&children=0"
            f"&infants=0&dateOut={seg['date']}&isConnectedFlight=false&isReturn=false"
            f"&discount=0&originIata={seg['origin']}&destinationIata={seg['dest']}"
        )
    return (
        "https://www.google.com/travel/flights?q="
        f"Flights%20from%20{seg['origin']}%20to%20{seg['dest']}%20on%20{seg['date']}"
    )


def load_json(path: Path, default: Any) -> Any:
    if path.exists():
        try:
            with path.open("r", encoding="utf-8") as fh:
                return json.load(fh)
        except (OSError, json.JSONDecodeError):
            return default
    return default


def save_json(path: Path, data: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(path.suffix + ".tmp")
    with tmp.open("w", encoding="utf-8") as fh:
        json.dump(data, fh, indent=2, ensure_ascii=False)
        fh.write("\n")
    tmp.replace(path)


# ---------------------------------------------------------------------------
# Ryanair
# ---------------------------------------------------------------------------

def parse_one_way_fares(payload: dict) -> Optional[dict]:
    """Iš oneWayFares atsakymo išrenka pigiausią bilietą."""
    best: Optional[dict] = None
    for item in payload.get("fares") or []:
        outbound = (item or {}).get("outbound") or {}
        price = (outbound.get("price") or {}).get("value")
        if price is None:
            continue
        fare = {
            "price": round(float(price), 2),
            "currency": (outbound.get("price") or {}).get("currencyCode") or "EUR",
            "flightNumber": outbound.get("flightNumber") or "",
            "departure": outbound.get("departureDate"),
            "arrival": outbound.get("arrivalDate"),
        }
        if best is None or fare["price"] < best["price"]:
            best = fare
    return best


def fetch_ryanair(seg: dict, session=None, retries: int = 3) -> tuple[Optional[dict], Optional[str]]:
    import requests  # importuojama čia, kad testams nereikėtų bibliotekos

    session = session or requests.Session()
    params = {
        "departureAirportIataCode": seg["origin"],
        "arrivalAirportIataCode": seg["dest"],
        "outboundDepartureDateFrom": seg["date"],
        "outboundDepartureDateTo": seg["date"],
        "outboundDepartureTimeFrom": seg.get("timeFrom") or "00:00",
        "outboundDepartureTimeTo": seg.get("timeTo") or "23:59",
        "currency": "EUR",
    }
    last_error = "nežinoma klaida"
    for attempt in range(retries):
        try:
            response = session.get(API_URL, params=params, headers=HEADERS, timeout=20)
            if response.status_code in (429, 500, 502, 503, 504):
                last_error = f"HTTP {response.status_code}"
            else:
                response.raise_for_status()
                fare = parse_one_way_fares(response.json())
                if fare is None:
                    return None, "Skrydžių nerasta"
                return fare, None
        except Exception as exc:  # tinklo klaidos, JSON klaidos
            last_error = str(exc)[:200]
        time.sleep(2 * (attempt + 1))
    return None, last_error


def manual_fare(seg: dict) -> Optional[dict]:
    if seg.get("price") is None:
        return None
    dep = f"{seg['date']}T{seg['departure']}" if seg.get("departure") else None
    arr = None
    if seg.get("arrival"):
        arr_dt = datetime.strptime(f"{seg['date']}T{seg['arrival']}", "%Y-%m-%dT%H:%M")
        if dep and seg["arrival"] < seg["departure"]:
            arr_dt += timedelta(days=1)
        arr = arr_dt.strftime("%Y-%m-%dT%H:%M")
    return {
        "price": round(float(seg["price"]), 2),
        "currency": "EUR",
        "flightNumber": seg.get("flight") or "",
        "departure": dep,
        "arrival": arr,
    }


# ---------------------------------------------------------------------------
# Logika
# ---------------------------------------------------------------------------

def layover(prev_leg: dict, next_leg: dict) -> Optional[dict]:
    arr = parse_local((prev_leg.get("fare") or {}).get("arrival"))
    dep = parse_local((next_leg.get("fare") or {}).get("departure"))
    if not arr or not dep:
        return None
    minutes = int((dep - arr).total_seconds() // 60)
    same_airport = prev_leg["dest"] == next_leg["origin"]
    # The wait covers the night if 03:00 falls between arrival and departure.
    night = arr.replace(hour=3, minute=0, second=0, microsecond=0)
    if night < arr:
        night += timedelta(days=1)
    if minutes < 0:
        level = "impossible"
    elif minutes < 60:
        level = "risky"
    elif minutes < (120 if same_airport else 180):
        level = "tight"
    elif minutes >= 24 * 60:
        level = "stay"
    elif night <= dep:
        level = "overnight"
    elif minutes >= 5 * 60:
        level = "long"
    else:
        level = "ok"
    return {"minutes": minutes, "level": level, "airportChange": not same_airport, "at": next_leg["origin"]}


def summarize_routes(trip: dict, prices: dict) -> list[dict]:
    pax = max(1, int(trip.get("passengers") or 1))
    seg_by_id = {s["id"]: s for s in trip.get("segments", [])}
    out = []
    for route in trip.get("routes", []):
        legs = []
        missing = []
        for sid in route.get("segments", []):
            seg = seg_by_id.get(sid)
            if not seg:
                missing.append(sid)
                continue
            entry = prices.get(sid) or {}
            fare = entry.get("fare")
            if not fare:
                missing.append(seg.get("label", sid))
            legs.append({**seg, "fare": fare, "previous": entry.get("previousPrice")})
        per_person = None
        previous = None
        if not missing and legs:
            per_person = round(sum(l["fare"]["price"] for l in legs), 2)
            previous = round(sum((l["previous"] if l["previous"] is not None else l["fare"]["price"]) for l in legs), 2)
        extra = float(route.get("extraCost") or 0)
        total = round(per_person * pax + extra, 2) if per_person is not None else None
        prev_total = round(previous * pax + extra, 2) if previous is not None else None
        stops = [layover(a, b) for a, b in zip(legs, legs[1:])]
        out.append({
            "route": route, "legs": legs, "layovers": stops, "perPerson": per_person,
            "total": total, "previousTotal": prev_total, "missing": missing,
        })
    out.sort(key=lambda r: (r["total"] is None, r["total"] or 0))
    return out


def update_entry(entry: Optional[dict], fare: Optional[dict], error: Optional[str], now_iso: str) -> dict:
    entry = dict(entry or {})
    entry["lastChecked"] = now_iso
    if fare is None:
        entry["lastError"] = error or "Skrydžių nerasta"
        return entry
    old = (entry.get("fare") or {}).get("price")
    if old is not None and old != fare["price"]:
        entry["previousPrice"] = old
        entry["changedAt"] = now_iso
    entry["fare"] = fare
    entry["lowest"] = min(entry.get("lowest") if entry.get("lowest") is not None else fare["price"], fare["price"])
    entry["highest"] = max(entry.get("highest") if entry.get("highest") is not None else fare["price"], fare["price"])
    entry.pop("lastError", None)
    return entry


def alerts_for(seg: dict, before: Optional[dict], fare: dict, min_drop: float = 1.0) -> list[str]:
    alerts = []
    old = ((before or {}).get("fare") or {}).get("price")
    label = html.escape(seg["label"])
    if old is not None and old - fare["price"] >= max(min_drop, 0.01):
        lowest = (before or {}).get("lowest")
        record = " · <b>žemiausia kada nors!</b>" if lowest is not None and fare["price"] < lowest else ""
        alerts.append(
            f"📉 <b>{label}</b> ({seg['date']}): <b>{euro(fare['price'])}</b> "
            f"(buvo {euro(old)}, −{euro(round(old - fare['price'], 2))}){record}"
        )
    target = seg.get("targetPrice")
    if target is not None and fare["price"] <= target and (old is None or old > target):
        alerts.append(f"🎯 <b>{label}</b>: {euro(fare['price'])} – pasiekta tikslinė kaina ({euro(target)})")
    return alerts


def run(trip: dict, prices: dict, history: dict, fetcher: Fetcher, now: datetime) -> tuple[dict, dict, list[str]]:
    """Atnaujina kainas. Grąžina (prices, history, alerts)."""
    now_iso = now.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    best_before = next((r for r in summarize_routes(trip, prices) if r["total"] is not None), None)
    new_prices: dict = {}
    alerts: list[str] = []
    for seg in trip.get("segments", []):
        sid = seg["id"]
        before = prices.get(sid)
        # Jei segmentas buvo pakeistas (kita data ar kryptis) – istoriją pradedame iš naujo.
        signature = f"{seg['origin']}-{seg['dest']}-{seg['date']}-{seg.get('timeFrom')}-{seg.get('timeTo')}"
        if before and before.get("signature") != signature:
            before = None
            history.pop(sid, None)
        if seg.get("source", "ryanair") == "manual":
            fare, error = manual_fare(seg), None
        else:
            fare, error = fetcher(seg)
            if fare:
                alerts.extend(alerts_for(seg, before, fare))
        entry = update_entry(before, fare, error, now_iso)
        entry["label"] = seg["label"]
        entry["date"] = seg["date"]
        entry["signature"] = signature
        new_prices[sid] = entry
        if fare:
            points = history.setdefault(sid, [])
            points.append([now_iso, fare["price"]])
            del points[:-MAX_HISTORY]
    best_after = next((r for r in summarize_routes(trip, new_prices) if r["total"] is not None), None)
    if alerts and best_after:
        line = f"💶 Pigiausias maršrutas: <b>{html.escape(best_after['route']['name'])}</b> – <b>{euro(best_after['total'])}</b>"
        if best_before and best_before["total"] is not None and best_after["total"] < best_before["total"]:
            line += f" (−{euro(round(best_before['total'] - best_after['total'], 2))})"
        alerts.append(line)
    return new_prices, history, alerts


# ---------------------------------------------------------------------------
# README
# ---------------------------------------------------------------------------

LEVEL_ICON = {
    "ok": "🟢", "tight": "🟡", "risky": "🟠", "impossible": "🔴",
    "long": "⏳", "overnight": "🌙", "stay": "🏖",
}


def stop_text(stop: dict) -> str:
    icon = LEVEL_ICON.get(stop["level"], "")
    city = CITY.get(stop["at"], stop["at"])
    if stop["level"] == "stay":
        return f"{icon} {city} {stop['minutes'] // (24 * 60)} d."
    return f"{icon} {city} {duration_text(stop['minutes'])}"


def trend(current: Optional[float], previous: Optional[float]) -> str:
    if current is None or previous is None or current == previous:
        return ""
    diff = round(current - previous, 2)
    return f" 🔻{euro(-diff)}" if diff < 0 else f" 🔺{euro(diff)}"


def render_markdown(trip: dict, prices: dict, now: datetime) -> str:
    local = now.astimezone(LOCAL_TZ)
    pax = max(1, int(trip.get("passengers") or 1))
    routes = summarize_routes(trip, prices)
    lines = [
        f"_Paskutinis patikrinimas: **{local.strftime('%Y-%m-%d %H:%M')}** (Vilniaus laiku)"
        + (f" · keleivių: {pax}" if pax > 1 else "") + "_",
        "",
        "### Maršrutų variantai",
        "",
        "| # | Maršrutas | Kaina | Pokytis | Persėdimai |",
        "| :-: | :--- | ---: | :--- | :--- |",
    ]
    for idx, r in enumerate(routes, 1):
        name = r["route"]["name"]
        if r["total"] is None:
            price = "nepilni duomenys"
        else:
            price = f"**{euro(r['total'])}**"
            if r["route"].get("extraCostLabel") and not r["route"].get("extraCost"):
                price += f" + {r['route']['extraCostLabel']}"
        change = trend(r["total"], r["previousTotal"]).strip()
        stops = ", ".join(stop_text(s) for s in r["layovers"] if s)
        medal = "🏆" if idx == 1 and r["total"] is not None else str(idx)
        lines.append(f"| {medal} | {name} | {price} | {change} | {stops} |")
    lines += [
        "",
        "### Atskiri skrydžiai",
        "",
        "| Kryptis | Data | Reisas | Kaina | Žemiausia | Šaltinis |",
        "| :--- | :--- | :--- | ---: | ---: | :--- |",
    ]
    for seg in trip.get("segments", []):
        entry = prices.get(seg["id"]) or {}
        fare = entry.get("fare")
        if fare:
            flight = f"{fare.get('flightNumber') or ''} {hhmm(fare.get('departure'))}–{hhmm(fare.get('arrival'))}".strip()
            price = f"**{euro(fare['price'])}**{trend(fare['price'], entry.get('previousPrice'))}"
        else:
            flight = entry.get("lastError") or "—"
            price = "—"
        if entry.get("lastError") and fare:
            price += " ⚠️"
        source = "Ryanair (auto)" if seg.get("source", "ryanair") == "ryanair" else f"{seg.get('carrier') or 'rankinė'} (rankinė)"
        lines.append(
            f"| [{seg['label']}]({booking_url(seg)}) | {seg['date']} | {flight} | {price} | {euro(entry.get('lowest'))} | {source} |"
        )
    lines += ["", "🟢 patogus persėdimas · 🟡 trumpas · 🟠 rizikingas · 🔴 nespėsite · ⏳ ilgas laukimas · 🌙 nakvynė · 🏖 viešnagė"]
    return "\n".join(lines)


def update_readme(readme: str, block: str) -> str:
    section = f"{START_MARK}\n{block}\n{END_MARK}"
    if START_MARK in readme and END_MARK in readme:
        head, rest = readme.split(START_MARK, 1)
        _, tail = rest.split(END_MARK, 1)
        return head + section + tail
    return readme.rstrip() + "\n\n## ✈️ Dabartinės kainos\n\n" + section + "\n"


# ---------------------------------------------------------------------------
# Telegram
# ---------------------------------------------------------------------------

def send_telegram(text: str) -> bool:
    token = os.environ.get("TELEGRAM_BOT_TOKEN")
    chat_id = os.environ.get("TELEGRAM_CHAT_ID")
    if not token or not chat_id:
        print("Telegram nesukonfigūruotas – pranešimas nesiunčiamas.")
        return False
    import requests

    try:
        resp = requests.post(
            f"https://api.telegram.org/bot{token}/sendMessage",
            json={"chat_id": chat_id, "text": text, "parse_mode": "HTML", "disable_web_page_preview": True},
            timeout=15,
        )
        ok = resp.ok
        if not ok:
            print(f"Telegram klaida: HTTP {resp.status_code} {resp.text[:200]}")
        return ok
    except Exception as exc:
        print(f"Telegram klaida: {exc}")
        return False


def main(argv: Optional[list[str]] = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--dry-run", action="store_true", help="tik atspausdinti, nieko neįrašyti ir nesiųsti")
    args = parser.parse_args(argv)

    trip = load_json(TRIP_FILE, None)
    if not trip:
        print(f"Nerastas kelionės aprašas: {TRIP_FILE}")
        return 1
    prices = load_json(PRICES_FILE, {})
    history = load_json(HISTORY_FILE, {})
    now = datetime.now(timezone.utc)

    import requests

    session = requests.Session()
    new_prices, history, alerts = run(trip, prices, history, lambda s: fetch_ryanair(s, session), now)

    block = render_markdown(trip, new_prices, now)
    print(block)
    ok = sum(1 for p in new_prices.values() if not p.get("lastError"))
    print(f"\nSėkmingai patikrinta: {ok}/{len(new_prices)}")

    if args.dry_run:
        if alerts:
            print("\nPranešimai:\n" + "\n".join(alerts))
        return 0

    save_json(PRICES_FILE, new_prices)
    save_json(HISTORY_FILE, history)
    readme = README_FILE.read_text(encoding="utf-8") if README_FILE.exists() else "# Bari\n"
    README_FILE.write_text(update_readme(readme, block), encoding="utf-8")

    if alerts:
        send_telegram("🔔 <b>Bari: skrydžių kainos pasikeitė</b>\n\n" + "\n".join(alerts))
    return 0


if __name__ == "__main__":
    sys.exit(main())
