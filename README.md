<div align="center">
  <img src="app/src/main/res/drawable-nodpi/pulse_logo.png" alt="PULSE" width="340">
  <h1>PULSE</h1>
  <p>A customizable Android health dashboard.</p>
  <img alt="Android 9+" src="https://img.shields.io/badge/Android-9%2B-202020?style=flat-square">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-Android-202020?style=flat-square">
  <img alt="Jetpack Compose" src="https://img.shields.io/badge/Jetpack-Compose-CB2437?style=flat-square">
</div>

## About PULSE

PULSE brings steps, sleep and other health stats into one dashboard you can arrange yourself. The charts take inspiration from Nothing’s dot-matrix displays. You can move widgets around and save layouts for different parts of the day.

## What’s in the app

| | |
| --- | --- |
| **Modular dashboard** | Arrange, resize and configure the widgets you want in PULSE Studio. |
| **PULSE Modes** | Save dashboard layouts for different parts of your day and switch between them. |
| **History** | Explore daily and longer-term trends in dot-based charts and open detailed records where available. |
| **Goals** | Set personal targets for the health metrics available from your source. |
| **Challenges** | Create manual or health-tracked challenges and follow their progress. |
| **Achievements** | Earn badges and monthly step medals as you make progress. |
| **Health Connect** | Read supported Android health measurements with the permissions you choose. |
| **Privacy** | Your health records stay under your control. PULSE processes them on your device. |

## Screenshots

<div align="center">
  <img src="docs/screenshots/history.png" alt="History and its dot chart" width="174">
  <img src="docs/screenshots/home.png" alt="Home dashboard" width="174">
  <img src="docs/screenshots/achievements.png" alt="Achievements tab" width="174">
</div>

[See all PULSE screenshots](https://github.com/Threalcocoboy/PULSE/blob/main/docs/screenshots/README.md)

## Health integrations

**Health Connect** reads supported records where Health Connect is available. Metrics depend on the records and permissions available on your device.

**Garmin Personal API** is an optional connection for people with their own Garmin Personal API key. PULSE connects directly to Garmin over HTTPS.

**Google Health.** Syncing is paused while verification is pending. Health data you already imported stays on your device.

## Privacy

PULSE processes health measurements on your device. The app has no hosted health-record service, ads, advertising profiles or hidden analytics. Android handles Health Connect permissions. Optional integrations and update checks contact their respective services. If you export your data or share a medal, you choose where it goes in Android’s share sheet.

## Install and update

The first signed public APK will be available on the Releases tab. PULSE runs on Android 9 or later. Compare the APK's SHA-256 with the value in the release's checksum file before opening it. Android may ask you to allow installs from the app you used to open the APK.

In an official GitHub build, open **More options → Check for updates**. PULSE checks stable releases for a higher Android build number and verifies the download's SHA-256, package identity, Android compatibility and signing certificate. You approve installation in Android. A build distributed through Google Play uses Google Play's update flow.

Updates require the same signing identity as the installed APK. See [release and migration instructions](docs/RELEASING.md) if you already use a development build.

## Build from source

Install JDK 17 or a compatible newer version, Android SDK platform 37 and Android build tools 37.0.0. Set your local SDK path in `local.properties` or configure Android Studio. Clone the repository and run the checked-in wrapper.

```bash
./gradlew :app:assembleGithubDebug :app:testGithubDebugUnitTest :app:lintGithubDebug
```

On Windows run `gradlew.bat` with the same tasks. The APK is created in `app/build/outputs/apk/github/debug/`. The `play` flavor builds separately and uses Google Play for updates.

## Architecture

- Kotlin and Jetpack Compose render the dashboard, History, Collection and Settings.
- Room caches provider health records. DataStore holds preferences, dashboard modes and challenge state.
- Health Connect and Garmin adapters map permitted provider data to the PULSE domain model. WorkManager schedules eligible provider refreshes.
- Distribution-specific update integrations select GitHub Releases or Google Play.

Read the [architecture notes](docs/ARCHITECTURE.md) and [data flow review](docs/PRIVACY_REVIEW.md).

## Roadmap

The next milestones are a signed release with GitHub updates and restoring Google Health sync after verification.

## Contributing and security

The [contribution guide](CONTRIBUTING.md) covers setup and testing with sample data. Please report security concerns privately using the [security policy](SECURITY.md).

## Legal, license and credits

- [Privacy Policy](PRIVACY.md) and [Terms of Service](TERMS.md) are included in the app.
- The PULSE source has no project-wide license on file. Public visibility alone does not grant reuse or redistribution rights.
- PULSE uses AndroidX, Jetpack Compose, Kotlin, Room and Health Connect. See [third-party notices](NOTICE.md) for licensing information.

## AI disclosure

AI tools are used to help build and document PULSE.
