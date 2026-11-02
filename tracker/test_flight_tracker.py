import unittest
from datetime import datetime, timezone

import flight_tracker as ft

TRIP = {
    "name": "Test",
    "passengers": 2,
    "segments": [
        {"id": "A", "label": "Vilnius → Turinas", "origin": "VNO", "dest": "TRN", "date": "2026-12-26", "targetPrice": 100},
        {"id": "B", "label": "Turinas → Baris", "origin": "TRN", "dest": "BRI", "date": "2026-12-26"},
        {"id": "M", "label": "Rankinis", "origin": "BRI", "dest": "VNO", "date": "2027-01-03", "source": "manual",
         "price": 50, "flight": "XX1", "departure": "23:30", "arrival": "01:10"},
    ],
    "routes": [
        {"id": "r1", "name": "A+B", "segments": ["A", "B"]},
        {"id": "r2", "name": "A+B+M", "segments": ["A", "B", "M"], "extraCost": 30},
    ],
}

PAYLOAD = {
    "fares": [
        {"outbound": {"flightNumber": "FR2", "departureDate": "2026-12-26T21:40:00", "arrivalDate": "2026-12-26T23:20:00",
                      "price": {"value": 82.0, "currencyCode": "EUR"}}},
        {"outbound": {"flightNumber": "FR1", "departureDate": "2026-12-26T10:20:00", "arrivalDate": "2026-12-26T12:00:00",
                      "price": {"value": 93.49, "currencyCode": "EUR"}}},
        {"outbound": {"price": None}},
    ]
}


def fake_fetcher(prices):
    def fetch(seg):
        p = prices.get(seg["id"])
        if p is None:
            return None, "HTTP 503"
        dep = "2026-12-26T06:55:00" if seg["id"] == "A" else "2026-12-26T10:20:00"
        arr = "2026-12-26T08:35:00" if seg["id"] == "A" else "2026-12-26T12:00:00"
        return {"price": p, "currency": "EUR", "flightNumber": "FR" + seg["id"], "departure": dep, "arrival": arr}, None
    return fetch


class TrackerTest(unittest.TestCase):
    def test_parse_picks_cheapest(self):
        fare = ft.parse_one_way_fares(PAYLOAD)
        self.assertEqual(fare["price"], 82.0)
        self.assertEqual(fare["flightNumber"], "FR2")
        self.assertIsNone(ft.parse_one_way_fares({"fares": []}))

    def test_euro_format(self):
        self.assertEqual(ft.euro(128.0), "128 €")
        self.assertEqual(ft.euro(93.5), "93,50 €")
        self.assertEqual(ft.euro(1234.5), "1 234,50 €")

    def test_manual_fare_overnight_arrival(self):
        fare = ft.manual_fare(TRIP["segments"][2])
        self.assertEqual(fare["arrival"], "2027-01-04T01:10")

    def test_run_tracks_drops_targets_and_keeps_price_on_error(self):
        now = datetime(2026, 11, 2, 12, tzinfo=timezone.utc)
        prices, history, alerts = ft.run(TRIP, {}, {}, fake_fetcher({"A": 128, "B": 93}), now)
        self.assertEqual(alerts, [])
        self.assertEqual(prices["A"]["fare"]["price"], 128)

        prices, history, alerts = ft.run(TRIP, prices, history, fake_fetcher({"A": 99, "B": None}), now)
        self.assertEqual(prices["A"]["previousPrice"], 128)
        self.assertEqual(prices["A"]["lowest"], 99)
        # Klaida neištrina ankstesnės kainos
        self.assertEqual(prices["B"]["fare"]["price"], 93)
        self.assertEqual(prices["B"]["lastError"], "HTTP 503")
        text = "\n".join(alerts)
        self.assertIn("📉", text)
        self.assertIn("🎯", text)
        self.assertIn("Pigiausias maršrutas", text)
        self.assertEqual(len(history["A"]), 2)

        routes = ft.summarize_routes(TRIP, prices)
        self.assertEqual(routes[0]["route"]["id"], "r1")
        self.assertEqual(routes[0]["total"], (99 + 93) * 2)
        self.assertEqual(routes[0]["layovers"][0]["minutes"], 105)
        self.assertEqual(routes[0]["layovers"][0]["level"], "tight")
        self.assertEqual(routes[1]["total"], (99 + 93 + 50) * 2 + 30)

    def test_layover_levels(self):
        def leg(o, d, dep, arr):
            return {"origin": o, "dest": d, "fare": {"departure": dep, "arrival": arr}}
        to_trn = leg("VNO", "TRN", "2026-12-26T06:55:00", "2026-12-26T08:35:00")
        self.assertEqual(ft.layover(to_trn, leg("TRN", "BRI", "2026-12-26T21:40:00", "2026-12-26T23:20:00"))["level"], "long")
        to_bgy = leg("BRI", "BGY", "2027-01-02T23:25:00", "2027-01-03T00:55:00")
        self.assertEqual(ft.layover(to_bgy, leg("BGY", "VNO", "2027-01-03T06:10:00", "2027-01-03T09:40:00"))["level"], "overnight")
        stay = ft.layover(to_trn, leg("TRN", "VNO", "2027-01-03T08:00:00", "2027-01-03T10:00:00"))
        self.assertEqual(stay["level"], "stay")
        self.assertEqual(ft.stop_text(stay), "🏖 Turinas 7 d.")

    def test_signature_change_resets_history(self):
        now = datetime(2026, 11, 2, 12, tzinfo=timezone.utc)
        prices, history, _ = ft.run(TRIP, {}, {}, fake_fetcher({"A": 128, "B": 93}), now)
        trip2 = {**TRIP, "segments": [{**TRIP["segments"][0], "date": "2026-12-27"}] + TRIP["segments"][1:]}
        prices, history, alerts = ft.run(trip2, prices, history, fake_fetcher({"A": 50, "B": 93}), now)
        self.assertNotIn("previousPrice", prices["A"])
        self.assertEqual(len(history["A"]), 1)
        self.assertFalse(any("📉" in a for a in alerts))

    def test_readme_update_between_markers(self):
        readme = "# Bari\n\nintro\n\n<!-- PRICES:START -->\nold\n<!-- PRICES:END -->\n\nfooter\n"
        out = ft.update_readme(readme, "new")
        self.assertIn("<!-- PRICES:START -->\nnew\n<!-- PRICES:END -->", out)
        self.assertIn("footer", out)
        self.assertNotIn("old", out)

    def test_render_markdown(self):
        now = datetime(2026, 11, 2, 12, tzinfo=timezone.utc)
        prices, _, _ = ft.run(TRIP, {}, {}, fake_fetcher({"A": 128, "B": 93}), now)
        md = ft.render_markdown(TRIP, prices, now)
        self.assertIn("🏆", md)
        self.assertIn("442 €", md)  # (128 + 93) * 2
        self.assertIn("ryanair.com", md)


if __name__ == "__main__":
    unittest.main()
