# ✈️ Bari – kelionės į Barį kainų stebėjimas

**Bari** stebi skrydžių kainas kalėdinei kelionei Vilnius → Baris → Vilnius (2026-12-26 – 2027-01-03).
Ją sudaro dvi dalys, kurios naudoja tą patį kelionės aprašą [`config/trip.json`](config/trip.json):

| | Kas tai | Kur veikia |
|---|---|---|
| 📱 **Android programėlė** | Maršrutų palyginimas, kainų istorija, persėdimų tikrinimas, pranešimai telefone | Jūsų telefone |
| ☁️ **Serverinis stebėjimas** | Kas 6 val. tikrina kainas, atnaujina lentelę žemiau ir siunčia Telegram žinutę | GitHub Actions (nemokamai) |

## 📲 Atsisiųsti programėlę

**[⬇️ Atsisiųsti Bari.apk (naujausia versija)](../../releases/latest/download/Bari.apk)** · [visos versijos](../../releases)

1. Atsisiųskite `Bari.apk` telefone.
2. Atidarykite failą ir leiskite diegti iš šio šaltinio (*Install unknown apps*).
3. Paleiskite **Bari** ir leiskite pranešimus.

Kiekvienas pakeitimas `android/` arba `config/trip.json` aplankuose automatiškai sukuria naują APK versiją ([workflow](.github/workflows/android.yml)).

## Kas naujo 2.0 versijoje

- **Maršrutų variantai** – 6 variantai (per Turiną ir Vieną, diena Turine, per Bergamą, per Vieną su nakvyne, per Varšuvą ir Romą, Lufthansa + ITA) surikiuoti pagal bendrą kainą, pigiausias pažymėtas 🏆.
- **Persėdimų tikrinimas** – programėlė apskaičiuoja laiką tarp skrydžių ir įspėja: 🟢 patogu, 🟡 trumpas, 🟠 rizikingas, 🔴 nespėsite, ⏳ ilgas laukimas, 🌙 nakvynė, 🏖 viešnagė.
- **Laiko langai** – pvz. „Turinas → Baris tik po 10:00“, kad būtų sekama kaina reiso, į kurį spėsite persėsti.
- **Kainų istorija ir grafikai**, žemiausia / aukščiausia / vidutinė kaina.
- **Tikslinė kaina** kiekvienam skrydžiui – pranešimas, kai kaina ją pasiekia.
- **Gretimos dienos (±3 d.)** – pigiausios dienos kalendorius; vienu paspaudimu galima perkelti stebėjimą.
- **Foninis tikrinimas** (3/6/12/24 val.) ir **pranešimai** apie atpigimą.
- **Rankinės kainos** kitiems vežėjams (Austrian, LOT, Wizz Air, traukinys), kad juos būtų galima palyginti su Ryanair.
- **Keleivių skaičius**, papildomos išlaidos (nakvynė), dalinimasis maršrutu, mygtukas „Rezervuoti“.
- Atnaujinus programėlę, `config/trip.json` pakeitimai pritaikomi automatiškai – jūsų pačių redaguoti ar pridėti skrydžiai išlieka.
- Klaida nebeištrina paskutinės žinomos kainos; tamsi tema; nauja piktograma.

## ☁️ Serverinis stebėjimas ir Telegram

[`tracker/flight_tracker.py`](tracker/flight_tracker.py) paleidžiamas [`track_prices.yml`](.github/workflows/track_prices.yml) kas 6 valandas (ir pakeitus `config/trip.json`).
Jis įrašo kainas į `data/prices.json`, istoriją į `data/history.json` ir atnaujina lentelę žemiau.

Telegram pranešimams: per [@BotFather](https://t.me/BotFather) susikurkite botą ir saugyklos
**Settings → Secrets and variables → Actions** pridėkite `TELEGRAM_BOT_TOKEN` ir `TELEGRAM_CHAT_ID`.

> GitHub pagal grafiką paleidžia darbo eigas tik numatytojoje šakoje (`main`).

## ✏️ Kaip pakeisti maršrutus

Redaguokite [`config/trip.json`](config/trip.json): `segments` – stebimi skrydžiai (`source: "ryanair"` tikrinami automatiškai,
`source: "manual"` – su jūsų įvesta kaina), `routes` – skrydžių deriniai. Programėlėje tą patį galima daryti
skiltyse „Skrydžiai“ (mygtukas **+**) ir „Kelionė“.

## 🔐 Programėlės pasirašymas (nebūtina)

Be papildomų nustatymų APK pasirašomas GitHub runner'io *debug* raktu (jis saugomas Actions cache, todėl naujos versijos
paprastai diegiasi ant senų). Norint pastovaus rakto, pridėkite slaptus kintamuosius `BARI_KEYSTORE_BASE64`
(`base64 -w0 bari.jks`), `BARI_KEYSTORE_PASSWORD`, `BARI_KEY_ALIAS` ir `BARI_KEY_PASSWORD`.

## Struktūra

```
config/trip.json        bendras kelionės aprašas (programėlė + serveris)
android/                Android programėlė (Kotlin, Jetpack Compose, WorkManager)
tracker/                Python skriptas GitHub Actions stebėjimui + testai
data/                   automatiškai atnaujinamos kainos ir istorija
.github/workflows/      APK kūrimas ir kainų stebėjimas
```

Vietinis kūrimas: `cd android && ./gradlew assembleDebug` (reikia Android SDK / Android Studio).

## ✈️ Dabartinės kainos

<!-- PRICES:START -->
_Paskutinis patikrinimas: **2026-11-02 11:51** (Vilniaus laiku)_

### Maršrutų variantai

| # | Maršrutas | Kaina | Pokytis | Persėdimai |
| :-: | :--- | ---: | :--- | :--- |
| 🏆 | Diena Turine, atgal per Vieną | **397,96 €** |  | ⏳ Turinas 13 val. 5 min., 🏖 Baris 7 d., 🟡 Viena 1 val. 20 min. |
| 2 | Per Turiną ir Vieną | **409,97 €** |  | 🟡 Turinas 1 val. 45 min., 🏖 Baris 7 d., 🟡 Viena 1 val. 20 min. |
| 3 | Vienas bilietas (Lufthansa + ITA) | **449 €** |  |  |
| 4 | Per Turiną ir Bergamą | **457,84 €** + nakvynė prie Bergamo oro uosto |  | 🟡 Turinas 1 val. 45 min., 🏖 Baris 7 d., 🌙 Bergamas 5 val. 15 min. |
| 5 | Per Vieną su nakvyne | **460,98 €** + nakvynė Vienoje |  | 🟡 Turinas 1 val. 45 min., 🏖 Baris 7 d., 🌙 Viena 16 val. 5 min. |
| 6 | Per Varšuvą ir Romą | **518,39 €** |  | 🟢 Varšuva 3 val. 25 min., 🏖 Baris 7 d., 🟢 Roma 4 val. 30 min. |

### Atskiri skrydžiai

| Kryptis | Data | Reisas | Kaina | Žemiausia | Šaltinis |
| :--- | :--- | :--- | ---: | ---: | :--- |
| [Vilnius → Turinas](https://www.ryanair.com/gb/en/trip/flights/select?adults=1&teens=0&children=0&infants=0&dateOut=2026-12-26&isConnectedFlight=false&isReturn=false&discount=0&originIata=VNO&destinationIata=TRN) | 2026-12-26 | FR8365 06:55–08:35 | **125,99 €** | 125,99 € | Ryanair (auto) |
| [Turinas → Baris (rytas)](https://www.ryanair.com/gb/en/trip/flights/select?adults=1&teens=0&children=0&infants=0&dateOut=2026-12-26&isConnectedFlight=false&isReturn=false&discount=0&originIata=TRN&destinationIata=BRI) | 2026-12-26 | FR6180 10:20–12:00 | **94 €** | 94 € | Ryanair (auto) |
| [Turinas → Baris (vakaras)](https://www.ryanair.com/gb/en/trip/flights/select?adults=1&teens=0&children=0&infants=0&dateOut=2026-12-26&isConnectedFlight=false&isReturn=false&discount=0&originIata=TRN&destinationIata=BRI) | 2026-12-26 | FR264 21:40–23:20 | **81,99 €** | 81,99 € | Ryanair (auto) |
| [Baris → Viena (rytas)](https://www.ryanair.com/gb/en/trip/flights/select?adults=1&teens=0&children=0&infants=0&dateOut=2027-01-03&isConnectedFlight=false&isReturn=false&discount=0&originIata=BRI&destinationIata=VIE) | 2027-01-03 | FR9067 09:30–11:05 | **88,99 €** | 88,99 € | Ryanair (auto) |
| [Viena → Vilnius](https://www.ryanair.com/gb/en/trip/flights/select?adults=1&teens=0&children=0&infants=0&dateOut=2027-01-03&isConnectedFlight=false&isReturn=false&discount=0&originIata=VIE&destinationIata=VNO) | 2027-01-03 | FR198 12:25–15:15 | **100,99 €** | 100,99 € | Ryanair (auto) |
| [Baris → Bergamas](https://www.ryanair.com/gb/en/trip/flights/select?adults=1&teens=0&children=0&infants=0&dateOut=2027-01-02&isConnectedFlight=false&isReturn=false&discount=0&originIata=BRI&destinationIata=BGY) | 2027-01-02 | FR4659 23:25–00:55 | **83,86 €** | 83,86 € | Ryanair (auto) |
| [Bergamas → Vilnius](https://www.ryanair.com/gb/en/trip/flights/select?adults=1&teens=0&children=0&infants=0&dateOut=2027-01-03&isConnectedFlight=false&isReturn=false&discount=0&originIata=BGY&destinationIata=VNO) | 2027-01-03 | FR2872 06:10–09:40 | **153,99 €** | 153,99 € | Ryanair (auto) |
| [Baris → Viena (šeštadienio vakaras)](https://www.ryanair.com/gb/en/trip/flights/select?adults=1&teens=0&children=0&infants=0&dateOut=2027-01-02&isConnectedFlight=false&isReturn=false&discount=0&originIata=BRI&destinationIata=VIE) | 2027-01-02 | FR9005 16:20–17:55 | **137,99 €** | 137,99 € | Ryanair (auto) |
| [Viena → Vilnius (Austrian)](https://www.google.com/travel/flights?q=Flights%20from%20VIE%20to%20VNO%20on%202027-01-03) | 2027-01-03 | OS917 10:00–12:45 | **103 €** | 103 € | Austrian Airlines (rankinė) |
| [Vilnius → Varšuva (LOT)](https://www.google.com/travel/flights?q=Flights%20from%20VNO%20to%20WAW%20on%202026-12-26) | 2026-12-26 | LO772 14:40–14:45 | **117 €** | 117 € | LOT (rankinė) |
| [Varšuva → Baris (Wizz Air)](https://www.google.com/travel/flights?q=Flights%20from%20WAW%20to%20BRI%20on%202026-12-26) | 2026-12-26 | W61339 18:10–20:20 | **132 €** | 132 € | Wizz Air (rankinė) |
| [Traukinys Baris → Roma](https://www.google.com/travel/flights?q=Flights%20from%20BRI%20to%20ROM%20on%202027-01-03) | 2027-01-03 | Frecciarossa 06:50–11:30 | **45 €** | 45 € | Trenitalia (rankinė) |
| [Roma (Čiampinas) → Vilnius](https://www.ryanair.com/gb/en/trip/flights/select?adults=1&teens=0&children=0&infants=0&dateOut=2027-01-03&isConnectedFlight=false&isReturn=false&discount=0&originIata=CIA&destinationIata=VNO) | 2027-01-03 | FR1308 16:00–19:45 | **224,39 €** | 224,39 € | Ryanair (auto) |
| [Vilnius ⇄ Baris (Lufthansa + ITA, vienas bilietas)](https://www.google.com/travel/flights?q=Flights%20from%20VNO%20to%20BRI%20on%202026-12-26) | 2026-12-26 | per FRA ir FCO 16:00–23:00 | **449 €** | 449 € | Lufthansa / ITA Airways (rankinė) |

🟢 patogus persėdimas · 🟡 trumpas · 🟠 rizikingas · 🔴 nespėsite · ⏳ ilgas laukimas · 🌙 nakvynė · 🏖 viešnagė
<!-- PRICES:END -->
