# Health Connect coverage — 8 November 2026

PULSE uses Health Connect Jetpack 1.1.0, min SDK 28, target/compile SDK 37. Availability is checked before access; Android 9 installations can still use demo mode. Individual base read permissions are optional. History and background access are requested only when the provider exposes the corresponding feature.

| Record | Permission suffix | Destination | Import/history/UI | Limitations |
|---|---|---|---|---|
| StepsRecord | READ_STEPS | STEPS / weekly / goals / monthly awards | Daily source-aware aggregate, cached by zone/date | Empty readings stay missing |
| DistanceRecord | READ_DISTANCE | DISTANCE | Daily aggregate in km; UI/axis/notification converts to miles when chosen | No route reconstruction |
| HeartRateRecord | READ_HEART_RATE | HEART | All pages, every authorized timestamp retained in Room; Samples and Daily averages are distinct modes | One deterministic origin per local day for plotted series; at most 600 actual observations plotted, retaining extrema; full cache retained |
| RestingHeartRateRecord | READ_RESTING_HEART_RATE | RESTING_HEART | Recorded daily average of timestamped observations | Not inferred from ordinary heart samples |
| HeartRateVariabilityRmssdRecord | READ_HEART_RATE_VARIABILITY | HRV | Recorded rMSSD milliseconds averaged per local day | Labeled daily average for Health Connect, not an invented overnight measure |
| TotalCaloriesBurnedRecord | READ_TOTAL_CALORIES_BURNED | CALORIES | Daily aggregate in kilocalories | Total energy; not mislabeled active energy |
| ExerciseSessionRecord | READ_EXERCISE | ACTIVITIES / ACTIVE | Session IDs, titles, type codes, local start times, duration; starts count on local start day; duration split at midnight and overlaps unioned | One origin per day; exercise minutes are not Garmin intensity minutes; route permission is not requested |
| SleepSessionRecord | READ_SLEEP | SLEEP / SLEEP_STAGES | Wake-date night assignment; session start/end, stage-duration totals, local cache and detail view | One source per night; totals do not claim a continuous sleep-stage timeline |
| ActiveCaloriesBurnedRecord | Not requested | Deferred | No active-energy-specific PULSE metric yet | Total and active energy are not interchangeable |
| FloorsClimbedRecord | Not requested | Deferred | No floor metric/goal/detail destination in current app | No permission without useful UI |
| SpeedRecord / ExerciseRoute | Not requested | Deferred | No speed/pace/route experience in this iteration | Location-sensitive route access deliberately avoided |
| WeightRecord / HeightRecord / BodyFatRecord | Not requested | Deferred | No body-measurement detail/goal model in current app | No indiscriminate permission expansion |
| HydrationRecord / NutritionRecord | Not requested | Deferred | No hydration/nutrition tracking UI in current app | No fabricated intake or nutrition totals |
| Body Battery / Training Readiness | No matching generic HC reader | Unsupported in HC | Missing/unsupported state | Proprietary Garmin measurements are never synthesized |

Historical access: without extra history access PULSE reads its recent 29-calendar-day window. Authorized history allows a 365-day backfill, followed by recent refreshes and weekly historical reconciliation. The staleness check ignores retained rows older than the active historical window, preventing repeated full backfills. Existing older cache rows remain. Heart sample windows are replaced transactionally with a composite zone/timestamp/origin key, so repeated imports do not duplicate observations. Permission-denied values and cached samples are hidden when permission is absent. Empty records, denied permissions and read failures are separate states.

Periodic access: a unique hourly WorkManager task requires Health Connect as the active source, completed onboarding, battery-not-low and granted background read permission. It does not promise an exact interval or continuous real-time monitoring. Foreground resume/Home refresh is throttled and preserves the last dataset while loading. Notification updates share this source snapshot and are quiet, permission-gated and timestamped.

References: [Health Connect data types](https://developer.android.com/health-and-fitness/health-connect/data-types), [raw reads, pagination, history and background permissions](https://developer.android.com/health-and-fitness/health-connect/read-data), [Android notification permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission).

Verification boundaries: compilation, pure mapping/axis tests, real Room sample replacement/migration tests and emulator UI inspection are distinct from testing a real populated Health Connect provider. No live personal health dataset was supplied for this iteration. Expanded provider reads and actual background timing therefore remain live-device integration checks.
