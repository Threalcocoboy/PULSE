# Releasing PULSE

The canonical project destination is `THREALCOCOBOY/PULSE`. The GitHub owner ID is `340643353`. The shipped package is `com.pulse.app`. The GitHub and Play flavors share that package ID and use their respective update channels.

## Publish the source safely

Follow [the publication review](PUBLICATION.md) before pushing. Enable private vulnerability reporting. Set the description to “A customizable, privacy-first Android health dashboard.” Suggested topics are `android`, `kotlin`, `jetpack-compose`, `health-connect`, `fitness`, and `health-dashboard`. Use the existing PULSE logo in a social preview after confirming distribution rights. Add the CI and release badges after their first successful public runs.

## Configure approved signing

1. Identify the approved application signing key. Check the SHA-256 certificate on a representative previously distributed APK with `apksigner verify --print-certs previous.apk`. A different key cannot update that installation in place. Existing debug-key installs typically need to export supported data where appropriate, remove the debug build, and install the production build. App-local data can be lost and the app has no JSON restore flow. Android-managed provider records stay with the provider.
2. In GitHub Settings → Environments, create `github-signing` and `github-release`. Restrict both to approved `v*` tags, add required reviewers and disallow self-review where available. Protect creation and modification of release tags and restrict writes to `main`.
3. In the `github-signing` environment add `PULSE_KEYSTORE_BASE64`, `PULSE_KEYSTORE_PASSWORD`, `PULSE_KEY_ALIAS`, and `PULSE_KEY_PASSWORD` as secrets. Base64-encode the approved keystore locally as a single line and paste it directly into the secret field. Do not commit it or leave a plaintext copy in the repository. Set the environment variable `PULSE_SIGNER_SHA256` in the GitHub environment's Variables section to the approved certificate's 64 hex characters without colons. GitHub environments that cannot enforce separate review should produce artifacts only.

## Build and review the exact signed APK

1. Set a higher `versionCode` and the release's three-part `versionName` in `app/build.gradle.kts`. Write the actual changes in `RELEASE_NOTES.md` and update the changelog. Keep the first line of the release notes in the form `# PULSE X.Y.Z`. Finalize the policy's effective date before publication.
2. Run CI and review its reports. Tag the approved commit on `main` with `vX.Y.Z`. Push the tag to the verified target. Select the tag in **Actions → GitHub APK release → Run workflow**. Leave both options unchecked for an artifact-only run. Select **Attach the signed APK to the release draft for testing** to make the signed build available on the draft. For publication, select **Request publication after signed artifact review** when starting the run. Test that run’s signing artifact before approving the `github-release` environment. GitHub update checks and publication require public releases.
3. Download the `signed-github-release` artifact and check `SHA256SUMS.txt`. On an isolated test device, verify a fresh install and an in-place update from the prior compatible build using the exact APK from that run. Test Android's permission and confirmation screens, cancellation and the resulting version. Confirm native legal pages and provider behavior on the signed build.
4. Approve `github-release` only after testing that run's artifact. It publishes exactly that signed APK, its SHA-256 and the bundled release notes. Verify the published GitHub release and asset digest after publication. A failed digest check leaves a draft for review.

The release task uses the checked-in Gradle wrapper and distribution checksum, JDK 17, Android platform 37 and build tools 37.0.0. It records APK metadata using `aapt2` and verifies the signing certificate with `apksigner`. Identical artifacts across different JDK or SDK revisions are not asserted.

## Update format and distribution

Tag stable releases `vX.Y.Z`. Attach exactly one `PULSE-X.Y.Z-VERSIONCODE-min28.apk` to the release. PULSE obtains the digest from GitHub's authenticated HTTPS API metadata and compares the received bytes, package ID, Android version code, version name, minimum SDK and signing certificate before offering Android's installer. The supported certificate policy uses exact current signer matching. Plan signer rotation separately before changing the key. This format provides one universal APK. A split APK set or AAB cannot be delivered through this updater.

The GitHub build opts in to update requests at build time only after the workflow verifies the destination. Debug and ordinary local builds make no GitHub release requests. The Play flavor uses the Play in-app update API when distributed through Play. Only the GitHub manifest requests authority to launch APK installation.

Current policy URLs after public publication are [Privacy Policy](https://github.com/THREALCOCOBOY/PULSE/blob/main/PRIVACY.md) and [Terms of Service](https://github.com/THREALCOCOBOY/PULSE/blob/main/TERMS.md). The same files are bundled into the APK by Gradle.

## Re-enable Google Health after verification

Run a development build with `-Ppulse.googleHealth.enabled=true` once the intended Google Cloud project, OAuth setup, approved scopes, package and signing certificate are ready. Complete provider QA and update the public disclosures before enabling it for release. The default is off. The pause leaves the integration, encrypted cache and existing connection state in place.
