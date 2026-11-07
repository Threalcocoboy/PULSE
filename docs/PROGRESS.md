# Progress — 5 November 2026

## Current: 0.2.0 refinement

- Redesigned typography, Home/History/compositions, compact source detail sheet, selected-only two-arrow Studio handle, single-owner gestures, hysteresis, edge auto-scroll, flat readable medals and compact navigation. Existing layouts/settings preserved across upgrade; default changes apply only to new/reset layouts.
- Official Garmin authentication and all 12 investigated metric categories verified live, mapped, cached separately and displayed with correct provider label. Runtime file import, Keystore ciphertext, offline cache and Health Connect source separation checked. Host key is ignored/untracked with no history entries.
- 69 unit tests pass, lint has 0 errors/8 suggestions, build passes. Actual touch tests cover uninterrupted long press/drag, direct editing move, inward/outward resize, circles/right edge, bottom auto-scroll, cancel, undo/redo and restart.
- Current details/evidence/limitations: `REFINEMENT_REPORT.md`, `KNOWN_ISSUES.md`, `DATA_INTEGRATION.md` and `verification/refinement/`. No five reference screenshot files were available for direct comparison.
- Final fresh-start smoke passed after deletion: onboarding, all tabs, theme changes, widget add/undo/redo and restart. Export excluded the actual key; encrypted key removal and all-provider local deletion passed. APK v2 signature and actual-key source/APK scans passed. Final machine-readable evidence: `verification/final-result.json`; raw personal probe responses were removed.

## Previous milestone: 0.1.0

- Initialized a native Kotlin/Compose app from the empty repository; preserved and packaged the supplied logo. Min SDK 28, target/compile 37; Gradle 9.6.0, AGP 9.4.1, Kotlin 2.4.10; JDK 21 in this environment.
- Implemented onboarding, three-tab navigation, Home's dotted widgets, Studio/Adaptive Flow, history and interactive dot charts/calendar, seasonal medals/details/sharing, settings, local observations and privacy controls.
- Room and DataStore are integrated. Demo records are memory-only; real Health Connect reads are permission-gated and never fall back to demo.
- 42 unit tests pass, including 1,000 randomized layout operations, exact medal thresholds, calendar/DST calculations and preference/layout encoding.
- Android 35 x86_64 emulator was provisioned from the official image after checksum verification. Onboarding, navigation, source permission denial/grant, empty provider sync, goal persistence, themes, Studio operations, charts/day details, JSON/image sharing, screen-size changes and privacy rationale were exercised. No app crash appeared in the emulator crash buffer.
- OneDrive locked a test-result directory during a rerun. Outputs moved to the optional `PULSE_BUILD_ROOT`; later tests passed. Lint found and prompted a fix for an API-29-only call on minimum API 28 and a local properties escape. Current lint: 0 errors, 9 nonblocking dependency-update/KTX suggestions.
- Final APK is delivered in `dist/PULSE-debug.apk`; hashes and test evidence accompany it. See `VERIFICATION.md` for actual checked scope and `KNOWN_ISSUES.md` for deferred work. This is a functional debug release, not completion of every advanced requirement.
