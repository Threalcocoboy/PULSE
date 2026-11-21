# PULSE data integration

## Separate, explicit sources

Garmin Personal API and Health Connect are selected independently. Snapshots carry source and cache scope so the app can keep readings from different providers or accounts separate.

Health Connect: existing read-only steps, session sleep, average heart rate and distance, recent 29 local dates with permissions. Platform aggregates reconcile cumulative records. Sleep uses wake dates and interval union from one deterministic source package. Cache keys preserve system timezone. History and background reads require separately granted platform access.

Garmin: official Personal API only, with runtime personal key. Separate `garmin_daily` Room table; migration 1→2 adds it without changing old tables/preferences. No Garmin password or undocumented SSO endpoint.

## Authentication and secrets

POST `https://personal.connectapi.garmin.com/personalapitoken-service/personal-api/personal-api-key/token`, Authorization ApiKey, Accept */*. The official reference client and live response confirm a **raw JWT**, not a JSON envelope. Expiry claim is used only for renewal scheduling; Garmin validates the token. In-memory token renews before expiry and is capped at 24 hours/key expiry. No refresh token. One data-call 401 permits one exchange/replay.

A screenshot-protected text field accepts the key. After token exchange and a verified data read, PULSE stores the credential in noBackupFilesDir using Android Keystore-backed AES-GCM. Key replacement keeps the same cache scope when Garmin account identity matches. Key removal clears the credential and Garmin measurement cache. Backups and device transfer are disabled.

HTTPS redirects are rejected, timeouts bounded and bodies capped at 8MB. Remote error bodies/transport messages never enter diagnostics. Invalid/expired/revoked key, malformed response, network failure, unsupported endpoint and 429 are distinct. Retry-After creates a cooldown; Optional progress notifications or active Garmin automatic challenges can use quiet hourly WorkManager sync.

## Verified mappings

| Metric | Documented endpoint/field | Meaning |
|---|---|---|
| Steps | usersummary-service/v2/dailysummaries → movement.steps.value | Daily replacement, never duplicate sum |
| Distance | same → movement.steps.distanceInMeters | Step distance, converted to km |
| Resting heart | same → heartRate.restingValue | Garmin's provided value, with documented minimum fallback |
| Stress | same → stress.avgLevel | Daily mean, negative sentinels missing |
| Body Battery | same → bodyBattery.latestValue | Latest summary, not charged/drained sum |
| Total and active calories | same → calories.burnedTotal and burnedActive | Daily kcal |
| Floors | same → movement.floorsAscended.value | Recorded floors ascended |
| Hydration | same → hydration.consumedInFractionalMl | Recorded fluid intake in liters |
| Active minutes | same → intensityMinutes.moderate + vigorous | Unweighted recorded minutes |
| Sleep | sleep-service/sleep/dailySleepSummariesByDate → sleepTimeSeconds | Measured sleep excluding naps, hours |
| Stages | same → deep/light/REM/awake seconds | Duration totals, no invented exact times |
| HRV | hrv-service/hrv/daily/from/to → hrvSummaries.lastNightAvg | Overnight mean ms |
| Heart | wellness-service/wellness/dailyHeartRate | Mean valid samples using descriptor indices |
| Activities | activitylist-service/activities/search/activities | Deduplicated IDs, provider-local starts, type/name/duration/distance |

Summary window is 90 days (published maximum one year). Sleep requests at most 31 (published limit). HRV and activities use 31. Activity pages are 99, exclude children, and are bounded at 990; hitting the cap produces an incomplete/error result, not a false final count. Heart samples are fetched for today; older daily means accrue in cache. Body Battery reports also passed a live probe; the app uses summary latestValue.

Garmin calendarDate and startTimeLocal remain provider-local calendar values, never rebucketed into the phone zone. Garmin caches are scoped to a verified account where identity is available and otherwise to the verified credential. Cached records show while refreshing. Failed optional metrics retain old values/timestamps as stale, or error when absent; 404 is unsupported. Whole-sync failures retain real cache and a warning.

## Medals and privacy

Fixed tiers: 90k/150k/225k/300k. Future locked, current provisional, past requires explicit coverage for final. Real imports remain conservatively incomplete. Daily upsert supports late corrections.

Exports identify source/calendar and exclude all credentials. Native sharing is user-triggered. No analytics or external AI receives records. Internet permission supports direct Garmin reads. Deletion clears local tables, key, preferences and export cache; it neither revokes the remote key nor deletes original provider records.

## Evidence / primary references


- [Garmin authentication](https://developer.garmin.com/gc-developer-program/personal/personal-api/)
- [Garmin expiry/personal-use FAQ](https://developer.garmin.com/gc-developer-program/personal/personal-faq/)
- [Official Swagger](https://developer.garmin.com/gc-developer-program/personal/swagger-docs/)
- [Official reference client](https://github.com/garmin/connect-personal-api-python)
- [Health Connect aggregates](https://developer.android.com/health-and-fitness/health-connect/aggregate-data)
