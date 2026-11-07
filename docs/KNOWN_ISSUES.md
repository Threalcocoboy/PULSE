# Known issues — PULSE 0.3.0

- Physical-phone, full TalkBack, keyboard, tablet, performance and multitouch stress verification remain. The emulator checks cover the documented gestures and a narrow screen with enlarged text.
- Current monthly challenges use the four existing step thresholds. Reaching a threshold can be confirmed from recorded steps; final seasonal medals still require confirmed monthly coverage and remain incomplete for partial real imports.
- There was no durable general-achievement ledger before 0.3.0. Awards missing from both retained records and existing caches cannot be reconstructed. New confirmed awards and best progress persist separately per source/period, including after goal changes. Exact unlock dates are shown only when known.
- JSON export does not yet include the achievement ledger as a restorable backup, and no backup restore/import flow exists. Explicit local deletion includes all tables, the encrypted key, settings and cached exports.
- Garmin summary window: 90 requested days. Sleep/HRV/activities: 31. Heart samples: today only. Previously cached detail outside a shorter fetch window is retained as stale. Comprehensive provider-deletion reconciliation remains unimplemented.
- Garmin distance is step distance. Active minutes are moderate + vigorous, not weighted intensity-goal points. Sleep stages are duration totals, not a chronological timeline. Source availability depends on the account and device; missing values are not invented.
- Real Garmin integration was verified in the earlier iteration; this audit preserves its protocol and tests without repeating a live-account import. Health Connect's available/empty-data path was checked, but populated physical Health Connect remains untested.
- Grid reflow is deterministic first-fit and can move several widgets during expansion. Pinned positions and named layouts remain deferred. Saving display settings at an unchanged footprint preserves manual placement.
- The smallest widget footprints may omit secondary content. Large-text collection layouts use one badge column and wrapping controls.
- Notifications, scheduled background sync and external AI remain deferred. No Garmin OAuth or Daash work was added in this iteration.
- Lint reports seven nonblocking dependency/KTX suggestions. Build, unit tests and lint have no failing checks in the final verification.

The delivery is a personal-use debug APK. Release signing, store publishing and commercial Garmin integration are outside this iteration. See POST_AI_AUDIT_REPORT.md and verification/post-audit/ for evidence.
