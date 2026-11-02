# ✈️ Bari – kelionės į Barį kainų stebėjimas

**Bari** – Android programėlė, kuri pati stebi visų transporto priemonių kainas kalėdinei kelionei
Vilnius → Baris → Vilnius (2026-12-26 – 2027-01-03) ir praneša **tik jūsų telefone**, kai kas nors atpinga.
Jokių rankiniu būdu įvedamų kainų – kiekvienas bilietas tikrinamas automatiškai.

## 📲 Atsisiųsti

**[⬇️ Atsisiųsti Bari.apk (naujausia versija)](../../releases/latest/download/Bari.apk)** · [visos versijos](../../releases)

1. Atsisiųskite `Bari.apk` telefone (reikia būti prisijungus prie GitHub, nes saugykla privati).
2. Atidarykite failą ir leiskite diegti iš šio šaltinio (*Install unknown apps*).
3. Paleiskite **Bari** ir leiskite pranešimus.

Nauja versija įdiegiama ant senos – kainų istorija išlieka.

## Iš kur gaunamos kainos

| Transportas | Šaltinis | Kaip |
|---|---|---|
| Ryanair | Ryanair kainų API | Vieša `services-api.ryanair.com` sąsaja; ±3 d. kainų kalendorius |
| Kitos aviakompanijos – Lufthansa, Wizz Air, LOT, Austrian, ITA, airBaltic, Turkish… | **Google Flights** | Šios bendrovės viešos kainų API neturi, todėl programėlė atidaro tą patį Google Flights rezultatų puslapį kaip naršyklė ir perskaito kiekvieno reiso aprašą (kaina, vežėjas, laikai, persėdimai). Galima filtruoti pagal aviakompaniją, persėdimų skaičių ir trukmę |
| Traukiniai Italijoje | **Trenitalia** (lefrecce.it) | Ta pati bilietų paieška, kurią naudoja Trenitalia svetainė |
| Autobusai | **FlixBus** | Viešas FlixBus paieškos API |

Italo traukinių ir LTG Link kainų kol kas nėra – jų svetainės neturi atviros paieškos.

## Maršrutų variantai

Programėlė atskirai reitinguoja **nuvykimo** ir **grįžimo** variantus, o viršuje rodo pigiausią visos kelionės derinį. Kiekvienam variantui tikrinami persėdimai: 🟢 patogu, 🟡 trumpas, 🟠 rizikingas, 🔴 nespėsite,
⏳ ilgas laukimas, 🌙 nakvynė.

**Nuvykimas (gruodžio 26 d.)**
- Iš Kauno per Bergamą – FlixBus Vilnius → Kaunas, Ryanair KUN → BGY → BRI
- Per Turiną – ryto persėdimas arba diena Turine (Ryanair)
- Į Romą + traukinys (Trenitalia) arba + autobusas (FlixBus) į Barį
- Per Varšuvą – LOT + Wizz Air / Ryanair
- Vienu bilietu – pigiausias Google Flights variantas (iki 12 val.)

**Grįžimas (sausio 2–3 d.)**
- Per Vieną (rytas) arba su nakvyne Vienoje (Ryanair / Austrian)
- Per Bergamą su nakvyne
- Traukiniu arba autobusu į Romą + Ryanair iš Čiampino
- Per Varšuvą
- Vienu bilietu (Google Flights)

Variantus galima keisti programėlėje (skiltys „Bilietai“ ir „Kelionė“) arba faile
[`config/trip.json`](config/trip.json): `segments` – stebimi bilietai (`source`: `ryanair`, `google`, `trenitalia`, `flixbus`),
`routes` – jų deriniai (`direction`: `out` arba `back`). Atnaujinus programėlę, nepakeisti variantai atsinaujina automatiškai,
o jūsų redaguoti ar pridėti – išlieka.

## Pranešimai

Programėlė fone tikrina kainas (kas 3/6/12/24 val., nustatymuose) ir telefone parodo pranešimą, kai:
- bilieto kaina nukrenta (galima nustatyti minimalų pokytį),
- pasiekiama jūsų nustatyta tikslinė kaina,
- atpinga visa kelionė.

## Struktūra

```
config/trip.json        kelionės aprašas (numatytieji bilietai ir maršrutai)
android/core/           kainų šaltiniai, maršrutų skaičiavimas, testai (gryna Kotlin/JVM)
android/app/            Android programėlė (Jetpack Compose, WorkManager)
.github/workflows/      APK kūrimas ir kainų šaltinių patikra
```

Kainų šaltinių patikrą galima paleisti ir kompiuteryje: `cd android && ./gradlew :core:run --args="check config/trip.json"`.
GitHub Actions ją paleidžia po kiekvieno pakeitimo ([Price sources](.github/workflows/sources.yml)) –
taip iškart matyti, jei kuri nors svetainė pasikeitė.

## 🔐 Programėlės pasirašymas (nebūtina)

APK pasirašomas GitHub runner'io *debug* raktu, kuris saugomas Actions cache. Norint pastovaus rakto, pridėkite slaptus
kintamuosius `BARI_KEYSTORE_BASE64` (`base64 -w0 bari.jks`), `BARI_KEYSTORE_PASSWORD`, `BARI_KEY_ALIAS` ir `BARI_KEY_PASSWORD`.
