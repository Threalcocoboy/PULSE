# PULSE

A native Kotlin / Jetpack Compose Android health application. Version 0.2.0 adds a refined visual system, separate move/resize gestures and the official Garmin Personal API. See the [refinement report](docs/REFINEMENT_REPORT.md).

## Build

Requirements: Android SDK platform 37, JDK 17+ (tested with Studio's JDK 21), network access for first dependency resolution. The Gradle wrapper pins 9.6.0. Minimum Android 9 / API 28; target API 37. Android 9 can explore demo data even when Health Connect is unavailable.

1. Open this folder in Android Studio, or set `JAVA_HOME` and put `sdk.dir=...` in untracked `local.properties`.
2. Run `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` (`.\gradlew.bat` on Windows).
3. Default APK: `app/build/outputs/apk/debug/app-debug.apk`. The verified delivery copy is `dist/PULSE-debug.apk` after the final verification run.

On Windows cloud-synced checkouts, optionally set `PULSE_BUILD_ROOT` to an absolute local directory outside OneDrive before building. All app build outputs, including tests/lint/APK, then go there. Source and Room schema stay in the repository.

## Use

- Complete onboarding with demo data or Health Connect. Garmin can be configured in Settings → Devices & Data → Garmin Personal API → Import key file (or Enter key).
- Home has a settings icon and exactly three primary tabs: Home, History, Achievements.
- Long press and continue dragging without lifting to move a widget. While editing, drag its body directly; tap to select. Drag the selected lower-right two-arrow handle to resize. Configure offers exact dimensions. Add/remove, undo/redo and default restoration persist on release.
- History has 7/30/90-day and 12-month views, touch inspection, metric selection, period comparisons and a daily calendar.
- Collection details include fixed monthly thresholds, seasonal engravings, data completeness and native image sharing.
- Settings persist goals, units, theme, motion, haptics, insights and source. Privacy offers JSON export and confirmed local deletion.

## Data honesty

Demo samples stay labeled and memory-only. Health Connect retains its four read-only categories. Garmin Personal API supports steps, step distance, resting/sampled heart rate, sleep/stage totals, stress, Body Battery, HRV, calories, intensity minutes and activities when present. Sources have separate caches and never silently fall back or double-count.

Garmin keys are imported only at runtime and encrypted using Android Keystore. `garmin.txt` is ignored: never commit it, add it to resources/BuildConfig, or include it in an APK. Exports exclude credentials. Internet permission is used for direct official Garmin requests; no analytics or external AI receives records. Native imports were verified against a real account in the emulator. This Personal API flow is for the owner's personal use.

Notifications/background sync and exact sleep-stage timelines are deferred. Real final medals still require confirmed coverage; partial imported months remain incomplete. See [known issues](docs/KNOWN_ISSUES.md), [precise mappings](docs/DATA_INTEGRATION.md), and [verification](docs/REFINEMENT_REPORT.md).
