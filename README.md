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
| **Track** | Today card: pouches or mg left, progress bar, the day's face. One row per pouch brand with a **+** button (long-press to log at an earlier time). Tapping **+** puts the pouch "in": a card with a timer and a **Remove** button appears; if you never tap Remove, the pouch counts as removed after the pouch time (30 min by default). Today's log shows each pouch with when it went in and how long it stayed; you can correct both or delete an entry. With the tolerance plan on, the Today card says the next pouch is better after a given time, and pouches taken earlier are marked *early*. With prices set, each pouch row shows its price and the Today card what today cost. |
| **Body** | Nicotine and cotinine in the body right now (mg and an approximate ng/mL) and when 95 % of each will be gone. A chart you can drag: back through up to 30 days of history (solid) and ahead through the forecast if you stop now (dashed). "Now" starts two thirds of the way across, and **Now** brings it back; 6 h / 48 h / 7 d windows. With the tolerance plan on, the chart draws its level and marks when the forecast drops below it. "Receptors at rest today" counts the time nicotine was low enough to leave most of them free. |
| **History** | Five weeks of day tiles: pouches and mg per day, coloured in seven clearly different steps (blue for a day without pouches, through green to yellow at the limit, then orange, red and purple) by the share of the limit that applied on that day. Streak within the limit, days over the limit, the last 30 and 7 finished days, totals. Daily bars against the limit line, dragged through the whole history and a week ahead, every day labelled. Two more views behind a switch at the top: patterns over the last 30 days (pouches by hour and by weekday, the time of the first pouch and its trend, the typical gap, the average time in the mouth) and spending (this week and month, and the saving against what you used before). |
| **Plan** | Limit counted in whole pouches or whole mg of nicotine. Either a fixed daily limit or a taper: start value → target value by a date, with today's limit interpolated linearly. Switching the unit converts the plan with your typical pouch strength, and switching back restores the values you had. A change applies from today on; past days keep the plan they had. Tolerance: off, hold or lower (see below). |
| **Settings** | Manage pouches: brand from a catalogue (or any other), optional flavour, strength in mg, a "fast release" tick for mini / dry-slim lines, price per can and pouches in it, colour; hide and restore. Pouches a day before Pouchwise, and the currency. Hour at which a new day starts. Body weight and pouch time. Theme and, on Android 13+, language. Export to CSV or JSON through the share sheet; import a JSON export to move everything to another phone. |

On first launch the app asks for your usual pouch, how many you use per day, and where you want to be
in two months, and turns that into a plan. Everything can be changed later.

### Home-screen widgets

Long-press the home screen → Widgets → Pouchwise. Two sizes:

- **1×1 ring** — the logo inside a ring that fills up towards today's limit and turns red once you
  are over it, with the app name underneath. Tap to open the app.
- **2×1 counter** — the same ring plus "4/10" (or mg), and two buttons. **+** pops up a chooser of
  your pouches over the home screen and logs the one you tap (with a single pouch it logs straight
  away). **✓** takes the pouch out; with two or more in, it asks which one. While a pouch is in, its
  brand and a running timer show under the count, and ✓ turns white; otherwise ✓ is greyed out.

On a Galaxy Z Flip the cover screen gets its own widget: Settings → Cover screen → Widgets → Pouchwise.
Samsung gives such widgets the whole page. It shows the ring and today's numbers, the pouch that is in
with a timer and ✓ (with two or more in, it asks which one, right on the cover screen), or when the
last one came out, and buttons for the three pouches you used most in the last two weeks, in the order
of your list: a tap logs that pouch. Tapping the ring opens the app on the cover screen.

All widgets refresh right after every change made in the app, whenever the app is opened, when a new
tracking day starts and when a pouch reaches its pouch time. The last two use an ordinary alarm that
needs no permission and lands within about ten minutes of the moment. They also refresh when the
time zone or the system language changes.

Going over the limit never blocks logging. The day card turns red with "N pouches over the limit"
and the extra entries are marked in the log — the data stays honest.

The UI is in English and Russian and follows the phone's language; on Android 13 and newer you can
pick the language in Settings instead.

## Install

Pouchwise needs Android 8.0 or newer. It is distributed as an APK; it is not on Google Play.

1. On the phone, open
   [github.com/Jack-I/Pouchwise/releases/latest/download/Pouchwise.apk](https://github.com/Jack-I/Pouchwise/releases/latest/download/Pouchwise.apk):
   the latest version downloads straight away. Older versions and release notes are on the
   [Releases page](https://github.com/Jack-I/Pouchwise/releases). You can also build it yourself (below).
2. Open the downloaded file. Android asks once to allow installs from your browser or Files app,
   then installs. Play Protect may warn about an unknown developer — choose *Install anyway*. The app
   requests no permissions at all.

Updates install over the previous version and keep your data as long as the APK is signed with the
same key.

Release APKs are signed with the certificate below. To check a download, run
`apksigner verify --print-certs Pouchwise.apk` (Android SDK build-tools) and compare
the SHA-256 digest:

```
2b595b4b0f6ba05d546a4d8911fe0d2fa57e1d5580783b6904e15fff3b766b9a
```

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

### Signing the APK

This is the cryptographic signature of the APK file that Android checks on install and update; it has
nothing to do with accounts, and the app has no login. Without a keystore, `assembleRelease` signs
with the debug key, which is fine for one phone. For a
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

While a pouch is in, nicotine comes in as the pouch releases it: fast at first, then slowing down
(first-order). Taking the pouch out stops the input. The model is anchored at one point: a pouch kept
in for 30 minutes delivers 30 % of its labelled nicotine. Any other time in scales that by
`(1 − e^(−k·t)) / (1 − e^(−k·30))`, with t the minutes actually in, so a shorter time gives less and a
longer one more. The pouch-time setting only decides when a pouch counts as removed by itself; every
entry keeps the value that was set when it went in, so changing the setting affects new pouches only.

The 30 % is the typical value in eight studies that measured blood nicotine after pouches: fitted to
them, this model needs a median of about 27–31 %. Products differ a lot, and most of these studies were
funded by manufacturers, so treat the absolute levels on the Body screen as approximate. For scale,
24–52 % of the labelled nicotine leaves a pouch within 20 minutes
([Mallock-Ohnesorg et al., Front Pharmacol 2024](https://doi.org/10.3389/fphar.2024.1392027)), and only
part of that reaches the blood.

The rate k starts from the brand you pick when creating a pouch. The catalogue holds the seven
brands measured by in-vitro dissolution in artificial saliva
(Platt et al., [Sci Rep 2026](https://www.nature.com/articles/s41598-025-34556-5), USP-4 flow-through, 60 min):

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
slowest, while the dry ZYN was the fastest. Lines within a brand can differ (mini, dry-slim,
extra-strong); the "fast release" tick on a pouch raises its k to 0.10 /min (ZYN-like) when the brand's
own value is lower. Pouches above 12 mg get a blunt tolerance warning in the dialog.

In the mouth, release is slower than in that flow-through test: blood levels after pouches and snus
kept in for different times fit k × 0.25 (`IN_MOUTH_RELEASE_FACTOR`). With the average k, a pouch taken
out after 15 minutes has given 0.56 of its 30-minute dose and one kept in for 60 minutes 1.63×; for
ZYN 0.60 and 1.46×, for Loop 0.52 and 1.85×.

From there it is a one-compartment model: nicotine is eliminated with a 2 h half-life, 75 % of it
becomes cotinine (mass-adjusted), and cotinine is eliminated with a 16 h half-life. The cascade is
solved in closed form, so any instant is evaluated directly.

The Body screen quotes when 95 % will be gone, counted from the highest level still ahead (now, or the
peak a pouch that is in will bring): log₂(20) ≈ 4.3 half-lives, about 8 h 40 min for nicotine and
2 d 21 h for cotinine after each has peaked. Below 0.1 mg of nicotine or 0.5 mg of cotinine, now and
ahead, it says the substance has cleared. Body weight does not change these times; it only enters the
ng/mL conversion, through volumes of distribution of 2.6 L/kg (nicotine) and 0.9 L/kg (cotinine).

Two numbers live side by side. The daily total and the limit count the labelled mg of each pouch —
what you put in, independent of how long it stayed. The *absorbed* estimate (labelled × 30 % ×
released share for the minutes in) is shown next to it: on the Today card, per entry in the log, in
the day detail on History, in the export, and it is what drives the Body chart. The chart follows the
same curve while a pouch is in, so taking one out never redraws the past.
Code: `model/Pharmacokinetics.kt`, `model/Brands.kt`; tests: `app/src/test/.../model/`.

## Tolerance plan

No study gives an interval that keeps or lowers tolerance, so the plan is a rule built on what is measured.
Half of the brain's α4β2 nicotinic receptors are taken at about 0.87 ng/mL of nicotine in the blood, around
a 25th of a regular smoker's level, and a cigarette takes more than 88 % of them
([Brody et al., Arch Gen Psychiatry 2006](https://doi.org/10.1001/archpsyc.63.8.907)): with regular use they
stay busy all day. The extra receptors that build up with long use go back to normal over 4–12 weeks
without nicotine ([Cosgrove et al. 2009](https://pmc.ncbi.nlm.nih.gov/articles/PMC2796827)).

- **Hold**: the next pouch is better after nicotine is back below the level at which you usually take one
  (the median over the last two weeks, leaving out each day's first pouch). Days stay as they are.
- **Lower**: a quarter below that, worked out from the two weeks before Monday and fixed for the week, so
  the gaps grow week by week; never below the level at which half of the receptors are free.

Each pouch records the plan level in force when it went in; one taken while the model still had nicotine
above it is marked *early*, never blocked. Levels are mg in the body and go through body weight to ng/mL,
as on the Body screen. "Receptors at rest" is the time today with nicotine below 0.87 ng/mL.

## Data

The rule throughout: whatever shapes the past is recorded when it happens, and settings only shape the
future.

- Database `pouchwise.db` (Room, schema v8). `pouch_types` and `log_entries`; an entry stores the
  strength, release constant, pouch time (`pouchMinutes`), tracking day (`day`), price (`pricePerPouch`) and
  tolerance plan level (`planLevelMg`) at log time, so
  editing a pouch or the pouch time never rewrites old entries. A new day-start hour or time zone
  re-files only the pouches of the running day, so they stay in "today"; finished days keep theirs. `endTimestamp` is when the pouch was removed (NULL = stayed in for its full
  pouch time). `plan_versions` keeps the limit plan from the day each change was made, and every past
  day is judged by the version in force on that day.
- Export from Settings → Data: CSV
  (`id,timestamp_iso,epoch_ms,end_epoch_ms,duration_min,pouch,brand,strength_mg,release_k,absorbed_mg,pouch_min,day,price,plan_level_mg`)
  or JSON (everything: pouches, entries, plan history and settings).
- Import from Settings → Data, or from the first-launch screen: a JSON export replaces all pouches,
  entries and settings on the phone after a confirmation that shows what is on the phone and what is
  in the file. It runs in one transaction, so a damaged or foreign file changes nothing. CSV is for
  spreadsheets and cannot be imported.
- Cloud backup is off, so the log never leaves the phone through the app or Google's backup. On
  Android 12 and newer a phone-to-phone transfer during setup still carries it over; otherwise use
  export and import.

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
