# Verification — 5 November 2026

**The checklist below records the original 0.1.0 milestone.** Current 0.2.0 evidence is in `REFINEMENT_REPORT.md` and `verification/refinement/`: 69 tests, build/lint pass, live Garmin mapping/display, revised gesture/edge tests and credential scans. The delivered `dist/PULSE-debug.apk` now contains 0.2.0; current checksum accompanies it.

## Build environment and commands

Windows 11; Android Studio JDK 21; SDK 37; Gradle 9.6.0; AGP 9.4.1. Minimum runtime API 28. Tested runtime: official Android 35 AOSP x86_64 image, 1080×2400 at 420dpi, hardware acceleration with SwiftShader rendering.

Final gate (PowerShell):

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:PULSE_BUILD_ROOT = 'C:\Users\rat17\.codex\visualizations\2026\11\05\01a1ac8e-bdc3-78a2-9ef5-f4bb499cd8ee\android-build'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain
```

42 tests: `AdaptiveFlowTest` 19, `HealthMathTest` 23. All pass. Lint: 0 errors, 9 nonblocking suggestions (dependency updates and KTX conveniences). No Kotlin compiler warnings. Dependency native libraries could not be stripped and are packaged as supplied; this does not prevent installation.

Earlier failures were fixed: ambiguous Preferences import, missing UI imports, API 29 guard, local Windows properties escaping. One test rerun failed before test execution because OneDrive locked `app/build/test-results`; the build output override resolved it. UI driver retries exposed scrolling/initial-state assumptions in the test script; the app controls were then exercised successfully from known states.

`tools/android_ui.py` drives ADB against `emulator-5554` only. Its scenarios are stateful, not a universal device test suite. Screenshot PNGs in `verification/` show actual rendered emulator output. Raw UI XML and emulator/SDK files are ignored in Git.

## Application and Home

- [x] Debug APK builds and installs; native activity launches.
- [x] Onboarding, goal save and persisted completion.
- [x] Home, History, Achievements and secondary Settings/detail navigation.
- [x] Restart preserves committed layout, source and modified goals.
- [x] Featured dot ring; numerical progress; circle/rectangle widgets; labeled demo data.
- [x] System-bar contrast corrected; no Home content/system-bar overlap in inspected portrait frames.
- [x] 320dp width / 150% text Home inspected and crowding corrected; secondary content reduces.
- [x] Landscape configuration renders and returns to portrait with saved preferences/layout intact.
- [ ] Full tablet, all font scales and TalkBack/keyboard audit.

## Studio / Adaptive Flow

- [x] Long press entry and Done/system Back exit.
- [x] Widget add and duplicate-capable framework; remove and restore.
- [x] Real pointer drag commits changed logical positions; no lost IDs or overlapping persisted cells.
- [x] Resize through sheet and dragged corner handle commits a valid new footprint and reflows siblings.
- [x] Undo and redo after move, resize and removal.
- [x] Restart persists the resized dashboard; shape/configuration changes use the same validated model.
- [x] Unit tests: expansion at right edge, shrink compaction, row movement, invalid size/oval/duplicate rejection, deterministic output, empty/full/mixed grids, serialization, canceled-preview isolation, history restoration, 1,000 randomized moves/resizes.
- [ ] Edge auto-scroll and cancellation races fully exercised by instrumented touch tests.
- [ ] 100-widget physical-device performance/frame profiling.

## History

- [x] Dotted graphs render; 7/30/90-day and 12-month period model and metric-aware aggregation.
- [x] Period/metric UI switching, point selection and Open day navigation exercised.
- [x] Calendar heatmap/daily summaries implemented; no fake hourly timelines.
- [x] Unit tests: daily replacement, weekly sums/comparison, monthly buckets, missing vs zero, insufficient comparison coverage, DST, cross-midnight/overlapping sleep.
- [ ] Every calendar/date/period combination visually inspected.

## Achievements

- [x] Exact boundaries 89,999 / 90,000 / 149,999 / 150,000 / 224,999 / 225,000 / 299,999 / 300,000 tested.
- [x] February/leap years, month boundaries, provisional/final/incomplete/future states, late corrections, duplicate protection and seasonal/date engraving tested.
- [x] Twelve-month three-column collection, detail view, totals, best day and dotted daily plot.
- [x] Shared renderer supports all four metallic palettes; demo calendar includes varied tiers.
- [x] Native share sheet opens and produces a valid branded PNG, explicitly labeled for demo mode.
- [ ] Real-source final awards and one-time unlock animation/notifications.

## Settings, integration and privacy

- [x] Goals changed to 8,000 steps / 7.5 hours and survived force-stop/restart; Home reflected them.
- [x] Dark/light theme switching exercised; settings/layout codec round trips and corrupt input fallback tested.
- [x] Health Connect native authorization opens with exactly four read categories and no write categories.
- [x] Denial returns “Read permission required”; approval permits a successful sync against an empty real provider.
- [x] Source switch preserves separation; unavailable/unsupported values remain empty, no demo fallback.
- [x] Privacy rationale action opens the in-app privacy explanation.
- [x] JSON export opens native share sheet; parsed file identifies DEMO mode, layout and cached real rows.
- [x] Local deletion confirmation/cancel and confirmed deletion exercised. Confirmed empty Room tables, cleared DataStore preferences, removed cached exports and restored onboarding; result is recorded in `verification/final-result.json`.
- [ ] Populated Health Connect/physical Garmin route, revocation/offline/late-sync behavior tested on real hardware.
- [ ] Background synchronization and notifications (not implemented).

## Delivery

`dist/PULSE-debug.apk` is a development-key-signed APK. `dist/SHA256SUMS.txt` records its checksum; `verification/final-result.json` records the last test counts, lint status, installation and deletion checks. No release signing or store deployment was performed.
