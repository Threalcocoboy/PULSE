# Known issues — PULSE 0.2.0

- Five reference images were not available locally. The redesign follows the written direction and typography roles; screenshot fidelity cannot be claimed. System fonts replace unlicensed Nothing fonts.
- Real Garmin data was verified in the emulator; physical-phone verification remains. Health Connect's permission/empty-source path works; populated physical Health Connect remains untested.
- Real monthly medals remain incomplete without a source completeness guarantee. Fixed thresholds are unchanged; collected counts exclude incomplete/provisional results.
- Garmin summary window: 90 requested days. Sleep/HRV/activities: 31. Heart samples: today only. Historical heart gaps are not filled from resting values. Data beyond the sync horizon remains cached; comprehensive source-deletion reconciliation is not implemented.
- Garmin Distance means documented step distance, not all exercise distance. Active minutes are moderate + vigorous, not weighted intensity-goal points. Stages are duration totals, not chronological segments.
- Availability depends on the account/device. Negative sentinels remain missing. Optional endpoint failures retain previous values as stale/error; only unavailable endpoints become unsupported.
- Full TalkBack/keyboard/tablet and device profiling remain. Large text may abbreviate the Achievements tab; Studio toolbar scrolls horizontally on narrow screens.
- Reflow is deterministic first-fit and can move several widgets during expansion. Pinned layouts and named saved layouts are deferred. Tests cover primary gestures/edges, not every multitouch race.
- Background sync, notifications/reminders, unlock animations, a full general-achievement ledger and external AI remain deferred.
- Exports contain current data, active-timezone Health Connect cache and separate Garmin cache; other-timezone HC caches are omitted. Credentials are excluded. Deletion clears all tables, encrypted key, settings and cached exports.
- Lint has 8 nonblocking dependency/KTX suggestions. One successful build produced a transient KSP shutdown NPE; no related application crash was observed.

This is a personal-use debug delivery. Store publishing, release signing and commercial Garmin integration are outside scope.
