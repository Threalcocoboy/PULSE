# Google Health integration

PULSE reads the stable Google Health v4 API with Google Play services' Android `AuthorizationClient`. The account chooser and consent surface run through Google. PULSE requests an access token for the selected Android account and calls `GET /v4/users/me/identity` before saving a connection. It checks that identity again before each sync. The Android client obtains fresh access tokens through Play services and clears a rejected token before one renewal attempt. Access tokens stay in memory. The optional web authorization-code and refresh-token architecture would require a confidential backend.

## Google Cloud and a signed APK

1. In Google Cloud, select the existing project **`pulse-helathapp`** and enable the [Google Health API](https://console.cloud.google.com/apis/library/health.googleapis.com?project=pulse-helathapp). Configure the app name, support email, homepage, privacy policy URL, and developer contacts in Google Auth Platform **Branding**.
2. Open [Clients](https://console.cloud.google.com/auth/clients?project=pulse-helathapp). The existing **Pulse Android** entry is registered for `com.threalcocoboy.pulse` and SHA-1 `C4:97:DE:B3:D9:3D:92:52:26:5F:0C:72:B9:7A:4C:38:0E:E9:E6:9C`. Register the debug APK under a separate Android client with the values below.
3. Reuse the entry if it is an **Android** client registered for package **`com.pulse.app`** and the signing **SHA-1** below. Otherwise create an **Android** OAuth client with that package and SHA-1 in the same project. Android authorization discovers the Cloud registration from the signed app. The Android client needs zero redirect URIs, JavaScript origins, local JSON configuration, or secret. The Web Server redirect in Google's Health quickstart is used by its web example.
4. For a Play-distributed build, add an Android client with the same package and the **app signing certificate** SHA-1 in Play Console, under **App integrity**. For a separately signed APK, use the actual distribution certificate. The repository currently has no configured release signing. Reproduce the local report with `./gradlew :app:signingReport`, or `.\gradlew.bat :app:signingReport` on Windows.
5. For testing with a personal Gmail account, set **Audience** to **External / Testing** and add that account under **Test users**. In [Data Access](https://console.cloud.google.com/auth/scopes?project=pulse-helathapp), start with the **Activity** read scope below. Register other read scopes when ready to test their categories.
6. On a device with current Google Play services, set up Google Health using that same Google account and confirm that the app has a recent activity measurement. Install the matching signed APK. Select **Google Health → Connect with Google** with only **Activity & fitness** checked, choose the account, and grant access. Check identity, timestamp and an actual measurement in History. Add more categories with **Grant selected permissions** after the first connection succeeds.

| Debug signing certificate | Fingerprint |
| --- | --- |
| SHA-1 used by the Android OAuth client | `F6:7F:CF:2C:55:BF:9C:5F:A0:A5:83:6F:B8:99:85:E8:C0:61:E4:33` |
| SHA-256, for comparing the actual APK certificate | `01:19:B9:35:D9:F3:C7:7D:4E:C2:A7:2D:22:CE:EC:1D:19:A1:7B:57:A8:08:0E:81:9D:48:61:AA:AE:03:2E:0F` |

[Android authorization setup](https://developer.android.com/identity/authorization) · [Google Health Cloud setup](https://developers.google.com/health/setup) · [Scopes](https://developers.google.com/health/scopes) · [Identity endpoint](https://developers.google.com/health/reference/rest/v4/users/getIdentity)

### Diagnosing the Google consent handoff

The former Compose callback discarded Google's result intent whenever Android returned a non-OK result code. That could turn a Google Play services error or an Android intent-sender launch failure into a cancellation message. The account picker also treated a missing account in a successful result as cancellation.

PULSE now registers both launchers in its activity and decodes the consent payload even on a non-OK return. The `PulseGoogleHealth` log tag records the stage, error category, and numeric Android, Google Play services or HTTP status when available. For a device investigation, collect the app's diagnostic entries with `adb logcat -d -s PulseGoogleHealth:I '*:S'`. A consent return without a result payload indicates an interrupted flow. Successful authorization proceeds to Google Health identity verification before saving a connection.

The OAuth repair build is `dist/PULSE-Google-Health-OAuth-debug.apk`. On November 19, 2026, 145 unit tests and 21 API 35 emulator tests passed. The regression includes a synthetic `RESULT_CANCELED` with a Google `DEVELOPER_ERROR` payload. The emulator confirmed the Play services recovery path. Live consent and connection on a supported device remain pending.

If PULSE displays the exact message **Google authorization was cancelled.**, check the installed APK. The message is present in `dist/PULSE-Google-Health-debug.apk`. The OAuth repair APK uses the specific error descriptions above and carries the `PulseGoogleHealth` diagnostic tag.

## Consent, storage, and sync

- **Activity** is `https://www.googleapis.com/auth/googlehealth.activity_and_fitness.readonly`.
- **Vitals** is `https://www.googleapis.com/auth/googlehealth.health_metrics_and_measurements.readonly`.
- **Sleep** is `https://www.googleapis.com/auth/googlehealth.sleep.readonly`.
- **Nutrition** is `https://www.googleapis.com/auth/googlehealth.nutrition.readonly`.

Any one of these scopes supports the identity check. The settings account label comes from the authorized Android account selection. Google Health profile and settings scopes are unnecessary for this flow. An unlinked account gets guidance for finishing Google Health setup. A successful identity check gates the connection state, and a completed recent import gates onboarding continuation.

PULSE imports the current and previous seven-day windows on initial setup. Device-local, network-constrained WorkManager work refreshes the current window and fetches one older seven-day window per granted type per run, up to 365 days. Android controls scheduling. WorkManager uses silent Play services authorization and pauses when Google requires interaction. HTTP pagination stays inside the requested range, and windows replace cached copies only after successful reads. Rate-limit and transient failures use bounded exponential retry and honor the server's retry interval. Historical daily totals use Google's recorded civil date. Overnight sleep belongs to the local wake date. Recent heart-rate history collects today and, on a background pass, the previous day, and keeps up to seven days of samples. The longer heart history uses daily averages.

Settings lets Health Connect run alongside Google Health. PULSE chooses one provider per date for shared measurements, with Google Health first by default. A preferred source can be set for each shared stream. PULSE falls back when that source has no fresh reading and the other has one. Sleep timing, duration, and stages share one source for the day. Workout counts, durations, and session lists share one source, so source changes cannot add overlapping workout or step totals together. Valid workouts unique to the lower-priority provider may be omitted when both providers have workout coverage on the same day. Garmin retains its existing independent provider flow.

PULSE stores the verified identity, scoped health cache and measurement-based achievements using AES-GCM and Android Keystore in the app's no-backup directory. Google-linked challenge definitions and progress are encrypted in the existing app preferences store. Cache partitions are keyed by the verified Google Health subject. Disconnect revokes the available app grant through Play services and stops local synchronization. You can keep the encrypted partition for the same account or remove it. Removing it also removes the measurement-based achievement cache and resets affected in-progress automatic challenges. Existing user-defined goals and completed challenge rewards remain. If remote revocation fails, remove PULSE from [Google Account third-party connections](https://myaccount.google.com/connections).

**Privacy & Data → Delete local data** clears retained partitions, local challenges and keys. The JSON export covers PULSE settings and the existing Health Connect and Garmin exports. Google Health records stay in the encrypted partition. PULSE handles Google Health data under the [Google Health API Developer and User Data Policy](https://developers.google.com/health/policies/health-api-developer-user-data-policy), including its Limited Use requirements.

## Metric coverage

The [registry](../app/src/main/java/com/pulse/app/integration/googlehealth/GoogleHealthMetrics.kt) records data type IDs, scope, read method, and mapped PULSE metrics. Corresponding parser fields and conversions are in [GoogleHealthMapping.kt](../app/src/main/java/com/pulse/app/integration/googlehealth/GoogleHealthMapping.kt). Scope names in the table refer to the full URLs above. **Fixture** means a dedicated automated response or mapping test. **Schema** means implemented against the official contract and compiled with the provider. Production retrieval and device-specific availability await an authenticated test for every row.

| Google Health data type | Scope | PULSE mapping | Verification |
| --- | --- | --- | --- |
| `steps` | Activity | Reconciled civil-day step count | Fixture |
| `distance` | Activity | Daily millimeters converted to km | Fixture |
| `active-minutes` | Activity | Activity minutes and intensity breakdown, daily and weekly goals | Schema |
| `active-zone-minutes` | Activity | Zone-weighted minutes | Schema |
| `activity-level` | Activity | Light, moderate and vigorous duration | Schema |
| `sedentary-period` | Activity | Recorded sedentary minutes | Schema |
| `altitude` | Activity | Daily elevation gain in m | Schema |
| `floors` | Activity | Daily floors climbed | Schema |
| `total-calories` | Activity | Daily total kcal including basal energy | Schema |
| `basal-energy-burned` | Activity | Reconciled basal kcal | Schema |
| `active-energy-burned` | Activity | Daily active kcal | Schema |
| `exercise` | Activity | Unique sessions, type, start, active duration, distance, and available workout summary | Fixture |
| `time-in-heart-rate-zone` | Activity | Recorded time and zone breakdown | Schema |
| `calories-in-heart-rate-zone` | Activity | Recorded kcal and zone breakdown | Schema |
| `daily-vo2-max` | Activity | Daily VO₂ max in mL/kg/min | Schema |
| `vo2-max` | Activity | Recorded VO₂ max in mL/kg/min | Schema |
| `run-vo2-max` | Activity | Running VO₂ max in mL/kg/min | Schema |
| `swim-lengths-data` | Activity | Stroke count and stroke-type breakdown. Pool lengths come from exercise summaries | Schema |
| `sleep` | Sleep | Wake-day sessions, asleep duration, time in bed, sleep period, awake time, stages and short awakenings | Fixture |
| `heart-rate` | Vitals | Daily average bpm and recent timestamped samples | Schema |
| `daily-resting-heart-rate` | Vitals | Daily resting bpm | Schema |
| `daily-heart-rate-zones` | Vitals | Zone thresholds in record details | Schema |
| `heart-rate-variability` | Vitals | Recorded RMSSD in ms | Schema |
| `daily-heart-rate-variability` | Vitals | Daily HRV average in ms | Schema |
| `oxygen-saturation` | Vitals | Valid recorded saturation samples summarized by day | Schema |
| `daily-oxygen-saturation` | Vitals | Google's daily saturation summary in percent | Schema |
| `respiratory-rate-sleep-summary` | Vitals | Full-sleep respiratory rate in breaths/min | Schema |
| `daily-respiratory-rate` | Vitals | Daily respiratory rate in breaths/min | Schema |
| `daily-sleep-temperature-derivations` | Vitals | Nightly skin temperature and personal baseline in °C | Schema |
| `core-body-temperature` | Vitals | Recorded core temperature in °C | Schema |
| `weight` | Vitals | Grams converted to kg | Fixture |
| `body-fat` | Vitals | Daily body fat percent | Schema |
| `height` | Vitals | Recorded height in cm | Schema |
| `blood-glucose` | Vitals | Recorded glucose summarized in mg/dL | Schema |
| `hydration-log` | Nutrition | Daily liters and water entries | Fixture |
| `nutrition-log` | Nutrition | Food entries and daily kcal, macros and available nutrient totals | Schema |
| `food` | Nutrition | Bounded lookups for names on referenced foods when missing from a log | Schema |
| `food-measurement-unit` | Nutrition | Bounded lookups for units on referenced servings | Schema |
| `skin-temperature-sensors` | Vitals | Raw wrist telemetry available through v4beta. PULSE uses stable nightly summaries | Outside stable v4 |
| `electrocardiogram` | ECG | Requires a dedicated ECG feature and consent | Outside current feature scope |
| `irregular-rhythm-notification` | IRN | Requires a dedicated rhythm feature and consent | Outside current feature scope |
| Exercise GPS / TCX | Location | Requires a routes feature and location consent | Outside current feature scope |
| `menstrual-period`, `ovulation-test` | Reproductive health | Documented public operations are write-only | Outside read integration |
| `moods` | Mindfulness | Documented public operations are write-only | Outside read integration |
| `symptoms` | Logged symptoms | Documented public operations are write-only | Outside read integration |

Sleep efficiency or readiness scores, Garmin Body Battery, Garmin stress, ECG interpretations, diagnoses, and unsupported summaries have no Google mapping. Availability depends on consent, linked account, supported hardware, and the data Google exposes for that account. PULSE can show a saved reading offline. Goal progress, automatic challenges, and new badge evaluations use eligible readings.

[Official data types](https://developers.google.com/health/data-types) · [REST v4 schema](https://developers.google.com/health/reference/rest) · [Daily rollup](https://developers.google.com/health/reference/rest/v4/users.dataTypes.dataPoints/dailyRollUp) · [Reconciliation](https://developers.google.com/health/reference/rest/v4/users.dataTypes.dataPoints/reconcile)

## Verification

| Check | Result |
| --- | --- |
| Android debug and instrumentation APK builds | Passed |
| JVM unit tests | 139 passed, including 16 Google Health tests |
| Android API 35 emulator | App cold launch succeeded. All 21 instrumentation tests passed, including encrypted cache and badge persistence, account-specific cleanup, source selection, and recovery when Google Play services are unavailable |
| Android lint | 0 errors and 7 advisory warnings |
| Debug APK | Android v2 signature verified. APK entries contain zero credential JSON or keystore files |
| Live authorization, native chooser cancellation, and Google Health requests | Pending Cloud registration confirmation and a linked test account on a device with Google Play services |

The test APK and app were built with `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug`. Test fixtures cover identity and unlinked profiles, expired and revoked access, partial consent, missing versus zero, pagination, transient failures, incremental history, offline state, DST, conversions, provider priority, progress and disconnect. The signed app is `dist/PULSE-Google-Health-debug.apk`. Its SHA-256 file digest is `2858844cccbfd932a2db6348961dd3bfda7266bdabf70bc483348fa60d415fd4`.

## Public release

Submit restricted-scope OAuth verification with the exact four scopes used, a justification for each feature, the in-app consent flow, verified domains/branding, and the published privacy policy and data-deletion instructions. Follow Google's security assessment instructions when requested. Verification and CASA status belong to the Cloud project and are separate from a Google Play review. A backend deployment for webhooks or refresh-token access would need a confidential web client, server-only key and refresh-token storage, a registered HTTPS callback, and verified authenticated webhook delivery. PULSE's current sync is device-local polling.

[Google Health app verification](https://developers.google.com/health/app-verification) · [Google Health developer checklist](https://developers.google.com/health/developer-checklist)
