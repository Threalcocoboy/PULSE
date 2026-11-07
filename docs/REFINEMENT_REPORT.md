# PULSE 0.2.0 — design and Garmin refinement

## Changes

1. Home: proportional labels, editorial serif headings, compact source row/detail sheet, gray/red plots and a side-by-side step count/dot ring. New defaults use a 4×2 feature. Saved footprints/configuration are retained; larger cards gain a useful seven-day plot.
2. Studio: one pointer owner selects move or corner resize at touch-down. Long press continues into a move without lifting. Editing permits immediate body drag. Only the selected widget shows a 48dp target with vector opposite-diagonal arrows. Body taps select; Configure is secondary. Hysteresis stabilizes resize, edge scrolling supports both gestures, cancel discards preview, and circles can grow vertically at the right edge without becoming ovals.
3. History: grouped period segments, proportional metric tabs with More metrics affordance, gray dots/red selection, haptics, compact values and open comparison summaries.
4. Medals: flat vector circles, one border, readable date, one seasonal symbol and seven sparse dots. No texture/bevel/concentric rim. Collected/Diamond counts include finalized earned medals only. Best month includes month/year and total.
5. Other screens: consistent smaller typography/spacing, denser settings and a compact 64dp three-tab navigation row. Original PULSE logo preserved. Android system serif/sans/mono fonts used; no Nothing fonts redistributed.

## Audit evidence and limits

The five described reference screenshots were absent from the project and attachment store. Pixel comparison was impossible. The written descriptions and [typography role reference](https://nothing.wiki/nothing/brand_reference) guided the redesign; no proprietary graphics were copied.

Fresh baseline Home and History emulator captures showed a dominant ring, repeated uppercase mono labels, oversized separate chips and an all-red plot. Final inspected screens are in `verification/refinement/14-final-home.png`, `15-final-history.png`, and `16-final-collection.png`. Narrow/150% text, Studio and source configuration have separate captures. Loading/wrong-state captures were rejected as evidence. This is not a full accessibility certification.

## Garmin result

Official Personal API authentication, documented data requests, parsing, domain mapping, separate Room caching and in-app display succeeded. A displayed step count matched the mapped cache with the Garmin source label. All investigated categories returned real data: steps, step distance, resting heart rate, sampled mean heart rate, sleep, sleep-stage totals, stress, Body Battery, HRV, intensity minutes, calories and activities.

Summaries request 90 days; sleep/HRV/activities request 31; heart samples are requested for today. Missing dates stay missing. Sleep stages are duration totals, not reconstructed chronology. Full historical heart traces, guaranteed final real medals and background synchronization remain unimplemented.

Runtime key import and Keystore ciphertext were checked. Temporary plaintext emulator copy removed. Host `garmin.txt` is ignored, untracked, with zero history entries. No credential values were printed. APK/source scan evidence is in `verification/refinement/secret-scan.json`. Keys/ciphertext/tokens are excluded from exports and APK resources; bearer tokens live only in memory. Runtime key removal and all-provider local deletion passed; raw personal-data probe responses were removed. Final fresh-start onboarding/navigation/theme/Studio/restart smoke passed after cleanup.

## Verification

Baseline build and 42 original tests passed. Updated build, 69 tests (0 failures) and lint (0 errors, 8 suggestions) pass. Emulator checks cover uninterrupted long-press/move, direct edit drag, selected handle, inward/outward resize, circle/right edge, bottom auto-scroll, cancel, undo/redo, restart, live Garmin mapping, offline real cache, Health Connect/source separation, sharing and export.

Room v1→v2 upgrade added a separate Garmin table without destructive fallback; existing settings and layouts were retained. Earlier test-driver failures involved loading frames, restored secondary routes and offscreen narrow-screen controls. Actual defects fixed included the Activity lookup lint error and one-axis circle resizing. One KSP daemon emitted an internal shutdown NPE during a successful build, without an app crash.

## Delivery / next

APK: `dist/PULSE-debug.apk`; checksum: `dist/SHA256SUMS.txt`. Personal-use development build, not store distribution. Next: validate on the user's phone, provide the five reference images for closer comparison, audit TalkBack/tablet/performance, then define source completeness and optional background features.
