# PULSE Privacy Policy

Release candidate for PULSE 1.0.1. The release containing this policy will establish its effective date.

## Who operates PULSE

PULSE is an Android app for personal wellness and activity tracking. The project is maintained by the GitHub account Threalcocoboy. PULSE has no developer-hosted account or health-record service. Its dashboard, history and progress calculations run on your device.

## What PULSE accesses and why

PULSE reads health measurements from the source you choose and only under the access you grant that source. Supported categories depend on your device, provider and permissions. They can include steps, distance, workouts, sleep and sleep stages, heart rate, resting heart rate, heart rate variability, energy burned, floors climbed and hydration. Garmin may also supply stress, Body Battery and intensity minutes.

PULSE uses these measurements for widgets, charts, daily details, goals, challenges, progress notifications and achievements. It also stores the information you enter, such as an optional display name, goal targets, challenge names and progress, your dashboard layouts and app preferences.

## Health Connect permissions

Health Connect lets you grant or revoke individual read permissions. PULSE uses steps, distance, exercise and energy permissions for activity summaries. It uses sleep and heart permissions for sleep and heart charts, and optional floors and hydration permissions for their respective metrics. Optional history access extends the period PULSE can read. Optional background access lets Android schedule local refreshes for progress while PULSE is closed. Health Connect manages access and supplies the permitted records. PULSE requests no Health Connect write permissions.

## Garmin Personal API

Garmin is optional and requires a personal API key that you obtain and enter. PULSE sends the key directly to Garmin's HTTPS token endpoint and then uses the resulting bearer token to request your permitted records. Garmin receives the credential or token, requested record categories and date ranges, and ordinary connection metadata. Garmin applies its own terms and privacy practices. The key is encrypted using Android Keystore-backed storage and the token is held in memory. Remove the key in the Garmin setup screen and revoke it with Garmin if you also want to invalidate it there.

## Google Health

Google Health sync is paused in the public build pending verification. The app does not initiate Google authorization, token renewal or Google Health API requests while it is paused. Existing local Google Health imports and connection settings remain on the device. If you previously granted access, you can revoke it in your Google Account settings. Deleting local PULSE data clears the local Google Health cache and connection. It leaves records and permissions in your Google Account for you to manage there.

## Storage and retention

PULSE keeps imported Health Connect and Garmin summaries and supported detail records in its private Room database. Preferences, goals, dashboard layouts and most challenge state are stored in Android DataStore. Provider-linked achievements and unlocks are stored locally. Legacy Google Health records, connection details and Google-linked progress use Keystore-backed encryption.

The app keeps settings, layouts, goals and progress until you edit or delete them or clear the app. Provider caches can retain older records to display history offline. Providers refresh different windows of data, and PULSE does not promise a uniform automatic deletion schedule. APK downloads and files prepared for sharing are kept in Android's app cache, which Android can evict. Exports can remain in the cache until you delete local data, clear the cache or Android evicts them.

## Network requests and third parties

PULSE has no developer-controlled health-record upload, hidden analytics, advertising SDK, health-data sale or advertising profile. Health Connect access uses Android's Health Connect interface. The optional Garmin integration contacts Garmin directly. Opening an outside link hands navigation to Android or a browser and is subject to the destination's practices.

A GitHub build checks GitHub Releases when you request it or enable automatic checks. The check sends an ordinary HTTPS request for public PULSE release metadata. An approved download requests the APK from GitHub's release delivery hosts. GitHub may receive your IP address, request time, requested resources and technical headers. These update requests include no health measurements, provider credentials or GitHub login. A Play build delegates update checks and downloads to Google Play, which applies its own privacy practices.

## Export and sharing

The export in Privacy & Data can include your PULSE preferences, layouts, the current supported dataset and cached Health Connect and Garmin records. It excludes credentials, the display name and the Google Health dataset. A medal image can show a month and step total. Sharing opens Android's share sheet and delivers the file to the destination you choose. Files copied, shared or uploaded through that destination follow its practices and remain outside PULSE's control.

## Disconnecting and deleting

Use Settings, then Devices & Data, to switch or disconnect the active source. Disconnecting stops that source from being used for new syncs and can leave its cache on the device. Use Health Connect's permission controls to revoke access separately. Use Garmin setup to remove a Garmin key and its local Garmin cache. Use Settings, then Privacy & Data, then Delete local PULSE data to clear PULSE health data, goals, challenges, achievements, layouts, provider credentials or connections and cached exports and restart onboarding. Android's Clear storage or uninstall also removes app-managed data. These actions leave original provider records, access you granted in external accounts and copies you previously shared under the control of those services and recipients.

## Device security

PULSE uses Android's app sandbox, disables cleartext network traffic and excludes its app data from its Android backup and transfer rules. Garmin key storage and legacy Google Health storage use Android Keystore-backed encryption. The general Room database and preferences rely on Android's app and device protections. Android displays local notifications under the permission you grant and the lock-screen visibility settings you choose.

## Children and privacy rights

PULSE is designed as a general-purpose personal wellness app. It does not ask for a date of birth or include child accounts. If local law requires a parent or guardian to authorize use or health-data permissions, obtain that authorization before using a provider. You can review, export and delete supported local data in the app. Where privacy law gives you additional rights, you can contact the maintainer. Providers handle requests for records they hold under their own privacy processes.

## Policy changes and contact

Each release includes the version of this policy it ships with. Changes are published alongside releases and in the project's current repository policy. You can find the maintainer at https://github.com/Threalcocoboy. Use the contact options on that profile for privacy questions. Keep health records, keys and account details out of public GitHub conversations.
