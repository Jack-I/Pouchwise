# Pouchwise

A small, offline Android app for tracking nicotine pouches: how many today, a daily limit that
highlights overuse instead of nagging, a gradual-reduction plan, a day-by-day mood grid, and an
estimate of the nicotine and cotinine currently in your body with a forecast of when they clear.

No account, no server, no analytics. Everything lives in a local SQLite database on the phone.

> Pouchwise is a habit tracker, not a medical device. Body levels are rough estimates from a simple
> pharmacokinetic model.

## Features

| Screen | What it does |
|---|---|
| **Track** | Today card: pouches or mg left, progress bar, the day's face. One row per pouch brand with a **+** button (long-press to log at an earlier time). Tapping **+** puts the pouch "in": a card with a timer and a **Remove** button appears; if you never tap Remove, the pouch counts as removed after the pouch time (30 min by default). Today's log shows each pouch with its time in, and you can adjust the time or delete an entry. |
| **Body** | Nicotine and cotinine in the body right now (mg and an approximate ng/mL) and when each clears. A chart with a solid line for the past and a dashed line for the forecast if you stop now; 24 h / 48 h / 7 d ranges. |
| **History** | Five weeks of faces: one tile per day in seven steps, from 😎 for a pouch-free day to 🤢 for far over the limit. Streak of days within the limit, weekly average. Daily bars against the limit line, projected a week ahead. |
| **Plan** | Limit counted in pouches or in mg of nicotine. Either a fixed daily limit or a taper: start value → target value by a date, with today's limit interpolated linearly. |
| **Settings** | Manage pouch brands (name, strength in mg, colour; hide and restore). Hour at which a new day starts. Body-model parameters. Theme. Export to CSV or JSON through the share sheet. |

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

Each pouch is a constant-rate nicotine input for as long as it is in. A full pouch time
(`absorptionMinutes`, 30 min by default) delivers `absorptionFraction` × labelled mg (30 %).
Removing it earlier delivers less along a saturating curve, `(1 − e^(−2x)) / (1 − e^(−2))` where
x is the share of the full time: half the time gives about 73 % of the dose, a quarter about 46 %.
Time beyond the full pouch time does not count.

From there it is a one-compartment model: nicotine is eliminated with a 2 h half-life, 75 % of it
becomes cotinine (mass-adjusted), and cotinine is eliminated with a 16 h half-life. The cascade is
solved in closed form, so any instant is evaluated directly.

"Cleared" means less than 0.1 mg of nicotine or 0.5 mg of cotinine left — roughly the usual blood
detection thresholds. ng/mL uses volumes of distribution of 2.6 L/kg (nicotine) and 0.9 L/kg
(cotinine) with the body weight from Settings.

All parameters can be changed in Settings → Body model. Code: `model/Pharmacokinetics.kt`;
tests: `app/src/test/.../PharmacokineticsTest.kt`.

## Data

- Database `pouchwise.db` (Room). `pouch_types` and `log_entries`; an entry stores the strength at
  log time, so editing a brand never rewrites history. `endTimestamp` is when the pouch was removed
  (NULL = stayed in for the full pouch time).
- Export from Settings → Data: CSV
  (`id,timestamp_iso,epoch_ms,end_epoch_ms,duration_min,pouch,strength_mg`) or JSON (everything,
  settings included).
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
