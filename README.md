# Pouchwise

A small, offline Android app for tracking nicotine pouches: how many today, a daily limit that
highlights overuse instead of nagging, a gradual-reduction plan, a day-by-day grid, and an
estimate of the nicotine and cotinine currently in your body with a forecast of when they clear.

No account, no server, no analytics. Everything lives in a local SQLite database on the phone.

> Pouchwise is a habit tracker, not a medical device. Body levels are rough estimates from a simple
> pharmacokinetic model.

## Features

| Screen | What it does |
|---|---|
| **Track** | Today card: pouches or mg left, progress bar, the day's face. One row per pouch brand with a **+** button (long-press to log at an earlier time). Tapping **+** puts the pouch "in": a card with a timer and a **Remove** button appears; if you never tap Remove, the pouch counts as removed after the pouch time (30 min by default). Today's log shows each pouch with its time in, and you can adjust the time or delete an entry. |
| **Body** | Nicotine and cotinine in the body right now (mg and an approximate ng/mL) and when 95 % of each will be gone. A chart with a solid line for the past and a dashed line for the forecast if you stop now; 24 h / 48 h / 7 d ranges. |
| **History** | Five weeks of day tiles: pouches and mg per day, coloured in seven steps by how the day went against the limit. Streak within the limit, days over the limit, weekly average, totals. Daily bars against the limit line, projected a week ahead. |
| **Plan** | Limit counted in pouches or in mg of nicotine. Either a fixed daily limit or a taper: start value → target value by a date, with today's limit interpolated linearly. |
| **Settings** | Manage pouches: brand from a catalogue (or any other), optional flavour, strength in mg, a "fast release" tick for mini / dry-slim lines, colour; hide and restore. Hour at which a new day starts. Body weight and pouch time. Theme. Export to CSV or JSON through the share sheet. |

On first launch the app asks for your usual pouch, how many you use per day, and where you want to be
in two months, and turns that into a plan. Everything can be changed later.

### Home-screen widgets

Long-press the home screen → Widgets → Pouchwise. Two sizes:

- **1×1 ring** — the logo inside a ring that fills up towards today's limit and turns red once you
  are over it, with the app name underneath. Tap to open the app.
- **2×1 counter** — the same ring plus "4 / 10" (or mg), and a **+** that pops up a chooser of your
  pouches over the home screen and logs the one you tap (with a single pouch it logs straight away).

Widgets refresh right after every change made in the app and once an hour on their own, so the day
rolls over on time.

Going over the limit never blocks logging. The day card turns red with "N pouches over the limit"
and the extra entries are marked in the log — the data stays honest.

The UI is in English and Russian, following the system language.

## Install

Pouchwise is distributed as an APK; it is not on Google Play.

1. Download the latest `Pouchwise-x.y.z-release.apk` from the Releases page, or build it yourself (below).
2. Copy the file to the phone (USB, cloud drive, messenger).
3. Open it from the Files app. Android asks to allow installs from that app once, then installs.
   Play Protect may warn about an unknown developer — choose *Install anyway*. The app requests no
   permissions at all.

Updates install over the previous version and keep your data as long as the APK is signed with the
same key.

## Build

Requirements: JDK 17 and an Android SDK with `platforms;android-35` and `build-tools;35.0.0`
(Android Studio installs these, or use the command-line tools and `sdkmanager`). Android Studio
itself is optional.

```bash
./gradlew assembleRelease
```

The APK lands in `app/build/outputs/apk/release/`. On Windows, `build.ps1` wraps the same thing
(`.\build.ps1 release`, `.\build.ps1 test`, `.\build.ps1 install`).

Point Gradle at the SDK with `local.properties` next to `settings.gradle.kts`:

```properties
sdk.dir=/path/to/Android/Sdk
```

### Signing

Without a keystore, `assembleRelease` signs with the debug key, which is fine for one phone. For a
permanent key, create one and describe it in `keystore.properties` (both are git-ignored):

```bash
keytool -genkeypair -v -keystore keystore/pouchwise-release.jks -alias pouchwise -keyalg RSA -keysize 2048 -validity 10000
```

```properties
storeFile=keystore/pouchwise-release.jks
storePassword=...
keyAlias=pouchwise
keyPassword=...
```

Back the keystore up: an update signed with a different key cannot be installed over the old one.

### Troubleshooting

If Gradle on Windows dies with `java.io.IOException: Unable to establish loopback connection`, the
JDK is failing to open its internal Unix-domain sockets. Make it fall back to TCP:

```bash
set JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:\__no_unix_sockets__
```

`build.ps1` sets this automatically.

## Body model

Each pouch is a constant-rate nicotine input for as long as it is in. A full pouch time (30 min by
default, adjustable) delivers 30 % of the labelled nicotine. Removing the pouch earlier delivers
less, following first-order release from the pouch normalised to the full time:
`(1 − e^(−k·t)) / (1 − e^(−k·T))`, with t the minutes in and T the full pouch time.

The rate k comes with the brand you pick when creating a pouch. The catalogue holds the seven
brands measured by in-vitro dissolution in artificial saliva
([Sci Rep 2025](https://www.nature.com/articles/s41598-025-34556-5), USP-4 flow-through, 60 min):

| Brand | k, 1/min | released in the first 20 min |
|---|---|---|
| ZYN | 0.103 | ~87 % |
| on! | 0.080 | ~80 % |
| Rogue | 0.078 | ~79 % |
| Velo | 0.057 | ~69 % |
| Volt | 0.056 | ~68 % |
| Dryft | 0.042 | ~60 % |
| Loop | 0.021 | ~48 % |
| any other brand | 0.062 (average) | ~73 % |

Two things from that study shaped the design: every product released nicotine front-loaded
(first-order), and "dry" versus "wet" did not predict speed — the wet pouches (Loop, Volt) were the
slowest, while the dry ZYN was the fastest. A pouch taken out at half time has given up ~72 % of its
dose with the average k, ~82 % for ZYN, ~58 % for Loop. Lines within a brand can differ (mini,
dry-slim, extra-strong); the "fast release" tick on a pouch raises its k to 0.10 /min (ZYN-like) when
the brand's own value is lower. Pouches above 12 mg get a blunt tolerance warning in the dialog.

From there it is a one-compartment model: nicotine is eliminated with a 2 h half-life, 75 % of it
becomes cotinine (mass-adjusted), and cotinine is eliminated with a 16 h half-life. The cascade is
solved in closed form, so any instant is evaluated directly.

The Body screen quotes the moment 95 % of the current amount will be gone: log₂(20) ≈ 4.3 half-lives,
about 8 h 40 min for nicotine and 2 d 21 h for cotinine once absorption has stopped. Body weight does
not change these times; it only enters the ng/mL conversion, through volumes of distribution of
2.6 L/kg (nicotine) and 0.9 L/kg (cotinine).

Two numbers live side by side. The daily total and the limit count the labelled mg of each pouch —
what you put in, independent of how long it stayed. The *absorbed* estimate (labelled × 30 % ×
released share for the time in) is shown next to it: on the Today card, per entry in the log, in the
day detail on History, in the export, and it is what drives the Body chart.
Code: `model/Pharmacokinetics.kt`, `model/Brands.kt`; tests: `app/src/test/.../model/`.

## Data

- Database `pouchwise.db` (Room, schema v4). `pouch_types` and `log_entries`; an entry stores the
  strength and release constant at log time, so editing a pouch never rewrites history.
  `endTimestamp` is when the pouch was removed (NULL = stayed in for the full pouch time).
- Export from Settings → Data: CSV
  (`id,timestamp_iso,epoch_ms,end_epoch_ms,duration_min,pouch,brand,strength_mg,release_k,absorbed_mg`)
  or JSON (everything, settings included).
- Android Auto Backup is enabled, but the export is the backup to rely on.

## Project layout

```
app/src/main/java/app/pouchwise/
  MainActivity.kt           entry point, theme
  AppViewModel.kt           state: brands, entries, settings, clock
  data/                     Room (Entities, PouchDao, AppDatabase), DataStore (SettingsRepository)
  model/                    pure logic: DayKeys, Limits, PouchTiming, Pharmacokinetics, Stats
  ui/PouchwiseApp.kt        navigation (tabs + settings)
  ui/screens/               Track, Body, History, Plan, Settings, PouchTypes
  ui/components/            Charts (Canvas), Dialogs, shared cards
  util/                     Fmt (formatting), Export (CSV/JSON + share)
app/src/main/res/values/strings.xml      English
app/src/main/res/values-ru/strings.xml   Russian
```

Kotlin, Jetpack Compose with Material 3, Room, DataStore. minSdk 26, targetSdk 35. Charts are drawn
on a Canvas; there are no third-party UI libraries.

## License

[MIT](LICENSE)
