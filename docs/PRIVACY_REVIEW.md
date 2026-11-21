# Release data-flow review

| Entry point | Data flow | Storage or destination |
| --- | --- | --- |
| Health Connect | Android grants read access per category. The importer converts permitted records into summary and detail models. | Private Room database and in-memory snapshots. |
| Garmin | The configured key is sent to Garmin to obtain a bearer token. Read requests include the token and the date range for the requested category. | Garmin's HTTPS host. Encrypted key in app storage, bearer token in memory and mapped records in Room. |
| Paused Google Health | The public build blocks account selection, authorization, refresh, manual sync and background work. Existing Google Health users can still display their encrypted local cache. | Keystore-backed encrypted cache and connection state. No Google Health network request in the paused path. |
| Goals, layouts and challenges | PULSE calculates progress locally. | DataStore and Room. Legacy Google Health challenge progress is encrypted. |
| Notifications | The app renders progress on device using a local notification. | Android notification UI follows the device's lock-screen settings. |
| Export and medals | The user creates a shareable file and picks a destination using Android. | App cache and the recipient the user chooses. JSON export excludes the Google Health dataset, provider credentials and display name. |
| GitHub update | PULSE requests the latest stable public release. If approved, a separate request downloads its verified APK. | GitHub receives connection metadata. APK remains in private cache and Android's installer receives a scoped URI. |
| Play update | Google Play provides availability and the installation flow. | Google Play. No GitHub APK update path or installation permission in the Play flavor. |

The main app manifest disables cleartext traffic and opts out of Android backups and device transfer with explicit exclusion rules. Room and ordinary preferences rely on the Android sandbox. No ads, analytics or general-purpose crash reporting are declared by the project. The Google Play Services dependency is used for the dormant Google Health authorization flow. The Play-only update SDK contacts Google Play for updates.

Review before publication. Confirm the operator identity and a private contact route, intended audience, applicable law and whether GDPR controller details, lawful basis, special-category processing conditions, rights and regulator disclosures are required. Confirm the rights to distribute the original branding and launch animation and review transitive notices and the distribution terms for each provider.
