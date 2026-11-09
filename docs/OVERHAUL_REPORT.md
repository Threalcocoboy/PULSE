# PULSE cohesive UX and data refinement — 8 November 2026

## 1. Executive summary

This pass extends the existing Kotlin/Compose app and preserves its dashboard layout codec, Undo/Redo, metric detail navigation, themes, local ledger IDs and provider scopes. It replaces generic lifetime badge fallbacks, adds durable unlock acknowledgements, gives History metric-aware axes and retained heart samples, unifies logo geometry, expands onboarding and optional periodic progress notifications, and removes routine success snackbars.

No Daash or delegated Garmin OAuth architecture was added. Optional key setup reuses the existing personal-key transport/Keystore flow, with a real token verification before replacing a saved credential.

## 2. Root causes

- Lifetime badge art had collapsed into three category glyphs despite 36 distinct reward conditions.
- Award progress persisted, but there was no separate durable acknowledgement state.
- Heart samples were reduced to daily averages before caching, making a raw time series impossible to display.
- Charts scaled from zero to an observed maximum without a proper metric domain or tick model.
- Header and intro used separate logo renderers (drawn dots versus a filtered raster), so geometry and reveals did not share a baseline.
- Onboarding was a short set of settings forms without local-name, optional key or notification stages.
- Layout and sync success messages used a global snackbar for routine events.
- The display-name draft was reset by asynchronous preference echoes, corrupting fast typing; the local input draft now remains stable while saves complete.

The prior checkout already had substantial uncommitted work. Those changes were preserved and built upon. Referenced screenshot/video files were not present beside the pasted brief; no claim is made to have inspected them.

## 3. Code changes

Core: Models.kt, ChartAxis.kt, BadgeIdentity.kt, HistorySamples.kt, RefreshPolicy.kt. Existing achievement registry IDs and thresholds remain unchanged.

Data: Stores.kt (backward-compatible preferences extension, Room migration 3→4, unlock events, heart samples and extra health-day payloads), ShareExport.kt (chosen display name excluded).

Integration: HealthRepository.kt, HealthSyncWorker.kt, new ProgressNotifications.kt. GarminApi.kt gains verification via its existing token exchange; GarminSetup.kt exposes honest inline status and the existing secure import/entry/delete controls.

UI: BrandMotion.kt, BadgeArt.kt, UnlockCelebration.kt, AxisChart.kt, NotificationSetup.kt; updates to MainActivity.kt, PulseViewModel.kt, Onboarding.kt, Settings.kt, DashboardScreen.kt, StudioGrid.kt, Home.kt, History.kt, Design.kt, Motion.kt, BottomNavigation.kt, MonthlyBadges.kt, SeasonalBadge.kt and MetricDetailScreen.kt.

Manifest/schema: app/src/main/AndroidManifest.xml adds only the matching read/notification permissions; app/schemas/com.pulse.app.data.PulseDatabase/4.json records the additive schema.

Tests: new OverhaulTest.kt, OverhaulPersistenceTest.kt, OverhaulUiTest.kt; existing FollowupUiTest.kt updated for the Add label. README.md and dist/README.md describe the current behavior. Verification scripts, logs, screenshots and structured results live under tools/overhaul_verify.py and verification/overhaul/.

## 4. Achievements

Every registered achievement has an explicit meaningful identity: feet, moon, constellation, calendar, stopwatch, runner, sunrise, sun, night movement, route, metric controls or PULSE trace, with condition-specific marks. The registry/identity test rejects missing mappings and verifies distinct identities.

The monthly step family retains its per-month attempts and historical earn count. Monthly-reset challenges and one-time achievements retain their existing separate lifecycle rules; the new queue adds acknowledgement state without replacing progress or changing IDs.

Monthly tiers use geometry as well as labels: Bronze single ring; Silver inner ring; Gold radial marks; Diamond an outer diamond frame. Tier dots remain. Main/detail/year views show class labels. No invented tiers were assigned to one-time achievements.

Room stores unlock events separately from progress. Saving real awards and recording newly unlocked instances is one transaction; repeated sync cannot create duplicate events. Continue acknowledges the complete visible stack. Dismissal is not inferred from composition. Pending events survive database reopen. Migration seeds pre-existing unlocked rows as acknowledged to avoid a legacy-award flood. Demo awards are memory-only and never trigger genuine unlock celebrations. Provider replacement clears the appropriate provider's acknowledgement events.

The overlay is full screen, with a bounded vertical list and a pinned Continue action. Small staggered reveals use the same motion primitive as other screens. It does not show sequential full-screen dialogs.

## 5. Motion and logo

The logo uses a shared normalized 45×10 coordinate system, one set of letter-dot centers and one waveform baseline (y=5.5). Header, onboarding and full-screen introduction use the same Canvas component; no screen-specific padding positions the waveform. Static and reduced-motion versions use the same geometry.

Fresh incomplete onboarding shows the full introduction once, persisting completion only when the animation completes. A process-scoped cold-launch flag and saved header acknowledgement prevent replay on ordinary navigation or activity recreation. Header motion is non-blocking. The sequence uses two restrained beats followed by a red waveform reveal. Returning before completion does not restart the header sequence.

Existing page fade/slide transitions remain. Sections, initial tiles and unlock rows use restrained opacity/translation reveals; only the first eight list items animate. Clock hands rotate within a stable dial, Home pulses/lifts, and Collection rotates/traces through existing emphasis. Repeated tab taps can retrigger icon feedback without restarting a complete page.

Sync feedback derives from the shared HealthSnapshot: idle is static; loading runs the vector ECG; successful completion briefly shows a checkmark; errors stay visible. The ECG is a loading/brand trace, not a fabricated heart measurement. Routine layout/sync success snackbar messages were removed; storage, permission, credential and import errors remain.

## 6. Charts and heart history

Dot columns and plotted strokes were thickened approximately twofold, keeping paired-dot geometry and the same data positions. AxisMath calculates clean bounds/ticks for nonzero ranges, flat/empty datasets, zero-based totals, durations and fixed score domains. Labels occupy their own gutter. Score/out-of-domain values are omitted with an explanation. Distance axes follow the miles setting.

History offers actual samples and explicitly labeled daily averages. Raw samples retain source and timestamp in Room; the plot selects one origin per local day, deduplicates timestamps and reduces large datasets to at most 600 actual extrema-preserving observations. It never invents interpolated observations or claims a mean is a full time series. Exact selection displays the selected observation time and bpm. Empty intervals, permission/source absence and cached import failure are explained.

## 7. Onboarding

Seven stages: welcome; optional local display name; granular Health Connect; optional Garmin personal key; goals; optional periodic notification; final summary. The current step and goal/name preferences persist in the existing store. Back and optional skips are available. The name is local only, editable/deletable in Settings, and excluded from exports and provider requests.

Common goals appear first. + New goal offers remaining supported metrics. Slider ranges and units differ by metric; workouts are weekly. Slider completion/new-goal changes persist through the same goal definitions used by Home/settings. No competing goal repository was added.

Garmin instructions were checked against the [official Personal API guide](https://developer.garmin.com/gc-developer-program/personal/personal-api/). The guide links to Garmin rather than inventing screenshots or registration requirements. The existing token endpoint verifies a candidate before replacing a credential; a nonempty field is never treated as a validated connection. No real credential was supplied or tested in this iteration.

Notification opt-in requests Android's permission only on an explicit enable action. Users choose metrics and see an accurately labeled preview, can decline, and can change preferences later. Actual notifications use imported values, a last-updated timestamp, a low-importance channel, private lock-screen visibility and no foreground service. They update after foreground or permitted periodic sync, not continuously. Demo values are never posted as live progress. Disabling/revocation/unavailable selected data cancels the notification.

## 8. Health Connect

See HEALTH_CONNECT_COVERAGE.md for the full record/permission/destination inventory, history policy and exclusions. Useful existing destinations now include resting heart, rMSSD, total calories, exercise count/duration and sleep stages/start/end. Exercise minutes are labeled recorded exercise duration, not Garmin intensity minutes. Nightly sleep retains the established wake-date policy. Proprietary scores remain unsupported in Health Connect.

## 9. Widget picker

The existing compact picker is simplified further: initial Add only exposes metric and shape/size; advanced display options remain in Edit widget. Add commits a valid tile near the top and highlights it for subsequent drag/resize. No drag-preview placement stage is required. The preview continues to use the same WidgetContent rendering and shape-safe number/goal layouts as placed tiles, including fitted number/unit rules. Existing packing, saved layouts, Undo/Redo, two-finger scrolling and drag-edge behavior remain.

## 10. Verification

Exact commands, test counts and evidence are recorded in the final results below. Intermediate and final logs are retained separately under verification/overhaul*. UI tests run only against emulator-5554; no attached phone is targeted.

## 11. Remaining work and boundaries

No live populated Health Connect account or real Garmin credential was supplied. Expanded read paths, actual network failures, manufacturer-specific background scheduling, long-run battery behavior, full TalkBack, all font/density combinations and frame profiling require additional device verification. The data inventory explicitly defers body composition, hydration, nutrition, floors and route/pace permissions until useful matching product destinations exist. Notification progress is periodic, not continuous live monitoring.

UI rendering, pure unit results and Android persistence results must not be conflated; final evidence specifies each separately.


### Synchronization correction

Historical backfill checks only rows inside the current 365-day window. Old retained rows outside that window cannot force full backfills indefinitely. Lifecycle refresh throttles recent attempts (including failed attempts), while explicit manual refresh remains available. Resume checks Health Connect grant changes before applying the freshness threshold, so revoked permissions are not ignored merely because the last read was recent. Successful refresh does not clear an unrelated storage/credential error.

### Source and test preservation

The working tree included prior uncommitted helper files and tests; they were retained. Schema migration is additive and the emulator's original rows were counted before and after opening the upgraded database. Local-name smoke testing uses a temporary QA name and deletes it afterward; no personal name or key is written into the report.

### Final verified results

| Check | Final result |
|---|---|
| Debug app, test APK, JVM suite and lint | BUILD SUCCESSFUL |
| JVM tests | 104 passed, 0 failures/errors/skips |
| Android UI/persistence tests | 14 passed on API 35 emulator-5554 |
| Lint | 0 errors, 12 warnings |
| Emulator smoke | Axes, sample/average modes, art/classes, actual notification denial, personalized greeting and name deletion passed |
| Installed schema 3→4 | 29 daily, 2 medals, 0 Garmin daily and 44 achievement-progress rows preserved |
| Working-tree whitespace check | git diff --check passed |

JVM suite counts: AdaptiveFlow 19; Followup 3; Garmin 20; HealthMath 23; Overhaul 14; StudioGesture 11; WidgetSystemAndDetail 14. Android counts: Followup UI 5; Overhaul UI 5; Overhaul persistence 4. Android assertions include durable pending/reopen/acknowledgement, repeated sample imports, legacy-award migration, multi-unlock stack, local-name navigation/deletion, delayed preference echoes while typing, extra goals and reduced motion. These tests do not replace a populated live Health Connect integration check.

The final name-entry smoke exposed a real asynchronous draft-reset defect. It was fixed and rerun successfully. During regression-test development, two assertions compared field labels or annotated IME text rather than plain editable characters; those failed before correction. One test-only build also failed on an unavailable helper call, then compiled after correction. Initial logs are preserved as overhaul-ui-tests-initial-assertion.log, overhaul-ui-tests-annotated-assertion.log and overhaul-test-assertion-build-failure.log. The final suite passes; no failing result is hidden or counted as passing.

Remaining lint warnings: six dependency-update suggestions, four KTX suggestions, one default-locale formatting warning and one unused original logo resource. Build tools also report the existing experimental Kotlin source-set setting and an SDK XML tool-version mismatch warning. Dependencies and the retained original logo asset were not changed merely to silence those warnings.

Reproduction in PowerShell, from the repository root:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:PULSE_BUILD_ROOT='C:\Users\rat17\.codex\visualizations\2026\11\07\01a1b669-9c8d-7052-89bf-b750fed65bd6\overhaul-build'
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug --no-daemon --max-workers=2 --console=plain
.\gradlew.bat :app:assembleDebugAndroidTest --no-daemon --max-workers=2 --console=plain
$adb='C:\Users\rat17\AppData\Local\Android\Sdk\platform-tools\adb.exe'
& $adb -s emulator-5554 install -r "$env:PULSE_BUILD_ROOT\outputs\apk\debug\app-debug.apk"
& $adb -s emulator-5554 install -r "$env:PULSE_BUILD_ROOT\outputs\apk\androidTest\debug\app-debug-androidTest.apk"
& $adb -s emulator-5554 shell am instrument -w -r com.pulse.app.test/androidx.test.runner.AndroidJUnitRunner
python -m tools.overhaul_verify
git diff --check
```

The smoke script intentionally uses Demo mode and a temporary QA name, then removes that name. It must only target the isolated emulator. The official release-check log is verification/overhaul-release-check.log; the final Android run is verification/overhaul-ui-tests.log; smoke evidence and screenshots are under verification/overhaul/. Machine-readable counts are in results.json, with unit XMLs and lint XML alongside them. Full-screen animation timing, all themes/font scales and actual provider/background imports remain outside the scope of these particular automated checks.

Delivery: dist/PULSE-debug.apk, with dist/SHA256SUMS.txt. This is the existing version 0.3.0 development build, using the existing debug signing setup; it is not a Play Store release.
