# PULSE targeted follow-up

## Changes and root causes

The submitted checkout contained calls to missing source files (Goals, AchievementCollection, MonthlyBadges, badge art/geometry, HeartHistory, HealthSyncWorker, Motion and EditScroll). These dependencies were restored rather than restarting the application. Existing Room v3 tables and provider scopes remain unchanged. No Garmin OAuth or Daash changes were made.

- Preview: fixed artificial preview dimensions. The popup renders the actual MetricWidget at the same four-column cell proportions as Home, scaling the whole result to fit. Shared measured text uses BasicText auto sizing, inherits the correct theme color, and avoids ellipsized counters. Numeric/unit groups can flow instead of squeezing the unit beyond the row. Distance uses separate, centered number and unit lines, retaining decimal and imperial formatting.
- Goals: circular tiles use an inscribed 70% square safe area, not a clipped rectangular row. Goal name, current measurement, target and progress are separate. Capacity depends on shape, height and font scale; extra goals are represented with an explicit remaining count. Sleep/distance retain their own value formatting. The same renderer is used in previews and Home.
- Charts: shared DotChart bars use paired horizontal dots; line samples use paired dots on the normal to the line, centered on the original sample path. Pair spacing is limited by bar spacing; selected point rings remain visible. History and other users of this primitive stay consistent.
- Seasonal badges: removed the disconnected lower-left spring extension. Spring now has one stem and two connected leaves. All seasonal ornaments have muted silhouettes with progress illumination: sequential snowflake arms, ray segments, leaf sections and sprout fill. The waveform and tier markers remain distinct from progress ornaments.
- Monthly steps: stable definition + YYYY-MM instance identities retain history in the existing ledger. Family earn count counts distinct earned months, not all four tier crossings in the same month. The main badge shows the highest achieved class and a separate next-class target. Details display class prominently. Repeat types distinguish STEP_FAMILY, MONTHLY challenge and ONE_TIME; no existing independent challenge is reclassified as a step-family badge.
- Navigation: clock hands rotate independently of the fixed dial; Home/Collection gain coordinated lift/rotation/emphasis. Existing page fade/slide transitions remain. App/system reduced-motion gates disable decorative motion.
- ECG and refresh: reusable ECG traces begin with a baseline, reveal repeating flat/spike/flat patterns, and stop when loading ends. Pull-to-refresh wraps Home (outside editing) and History, using their real nested scroll and ViewModel refresh path. Existing repository errors and cached-data handling remain; unchanged measurements get a no-new-data confirmation. Demo remains labeled.
- Add/configure: a compact three-panel popup keeps the real preview and action footer visible, with metric selection, shape/size, and compact 48dp toggles. Place commits a new widget near the top immediately, retaining drag and Undo. The previous unsaved placement stage is removed. Dropdowns and shape options can scroll independently; the popup itself has no vertical scrolling column.
- Studio: Undo/Redo/Add are 48dp icon actions with accessibility descriptions. Configure is removed from the toolbar; widget taps still configure. Edge scrolling uses frame-time-scaled, clamped speed and adds consumed scroll to the same drag baseline. Two-finger scrolling averages stable pointers and launches a bounded, cancelable decay fling; a fresh touch stops it. One-finger manipulation retains ownership.

## Files

Modified: MainActivity.kt, PulseViewModel.kt, ui/Home.kt, WidgetContent.kt, DashboardScreen.kt, StudioGrid.kt, BottomNavigation.kt, Design.kt, Achievements.kt.

Restored/new dependencies: core/Goals.kt, HeartHistory.kt, AchievementCollection.kt; integration/HealthSyncWorker.kt; ui/FitText.kt, Motion.kt, RefreshSurface.kt, EditScroll.kt, WidgetGeometry.kt, SeasonalBadge.kt, MonthlyBadges.kt.

Tests: FollowupTest.kt and FollowupUiTest.kt. Delivery/evidence: dist/PULSE-debug.apk, SHA256SUMS.txt and verification/followup*.

## Verification

Build/test results are finalized below. Earlier compiler errors from the text refactor were corrected. The initial refresh UI fixture lacked nested scrolling; it was replaced with a scrollable screen matching the real app. That earlier failed run remains recorded and is not represented as a pass.

## Limits

Live provider/network conditions, OEM multitouch/fling behavior, all font/shape combinations, background battery performance, and frame-rate profiling need broader device validation. At very small sizes or enlarged text, fewer goals are displayed intentionally. Compact popup tabs preserve options while avoiding a vertically scrolling form on the tested phone; dropdown options still scroll. Monthly awards retain the existing best-confirmed-progress policy and provider/timezone scoping. No database migration or deletion was introduced.


## Final results

- `assembleDebug`, `assembleDebugAndroidTest`, `testDebugUnitTest`, `lintDebug`: passed. Final log: `verification/followup-verified-build.log`.
- **90 JVM tests passed, zero failures**, including three new badge-family/month-instance/top-placement regressions. Previous-pass uncommitted test sources were absent from this checkout; this count covers the sources actually available and executed here.
- **Five emulator UI tests passed**: measured complete goal strings in a circle, readable Distance number/unit, popup Place behavior, reduced-motion navigation/ECG, and a long pull triggering refresh through a scrollable child. Log: `verification/followup-ui-tests.log`.
- **Lint: zero errors, 10 nonblocking suggestions**, chiefly dependency updates and KTX conveniences. A deprecated Compose test-rule warning remains. No checks were disabled.
- The demo smoke driver verifies unsaved configuration, placement at row zero, and Undo restoration; it captures Distance, circular Goals, tier labeling and thicker History charts. See `tools/followup_verify.py` and `verification/followup/`.
- Build outputs use the task's local directory outside OneDrive. Delivery: `dist/PULSE-debug.apk` and `dist/SHA256SUMS.txt`.
- `git diff --check`: passed.

The edge-scroll/inertia implementation is not claimed to have full multitouch stress or OEM/device coverage. Live online/offline provider conditions were not reproduced; the existing real repository refresh/error path is used. The restored lifetime badge-art fallback uses category glyphs; the missing original standalone artwork source was not present in this checkout. Monthly seasonal artwork is fully implemented here.
