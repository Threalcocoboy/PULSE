# Design and Garmin refinement

Preserve the working app, schemas, preferences, dashboard configurations and fixed medal rules. Baseline build and 42 tests passed.

1. Audit actual emulator screens and typography/card/gesture code. The five external reference screenshots are absent from the project and attachment store; use the written direction and linked brand role reference without claiming screenshot fidelity.
2. Centralize a restrained type/spacing system; compact Home and source status; refine History and Settings; simplify shared medal renderer.
3. Give each widget a single gesture owner that chooses move or selected-corner resize at pointer-down, supports same-gesture long press/drag, hysteresis, cancellation and edge scrolling. Retain Adaptive Flow and saved layouts.
4. Integrate the official Garmin Personal API with runtime-only Keystore-encrypted key import, bounded authentication/retry, documented field mappings and a provider-separated Room cache. Keep Health Connect and Demo intact.
5. Run regression/unit/real-touch tests, validate live Garmin display, inspect narrow/large-text screens, scan source/APK for secrets, and deliver a verified debug APK.

Secret audit: `garmin.txt` exists; untracked, ignored, no history entries. Its contents are never printed, passed as command arguments, put into app resources, or included in exports.
