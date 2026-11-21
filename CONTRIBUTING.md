# Contributing to PULSE

Use the build steps in the [README](README.md). Start from a current copy of `main` and keep changes scoped to the feature or fix. Add a small test when it captures a regression and run the relevant GitHub flavor build, unit tests and lint before proposing a change. Run the Play flavor checks when touching shared code or distribution.

Use a clean Android emulator and synthetic records for development screenshots, exports and reproducible issue reports. Keep personal health data, names, credentials, local databases, diagnostic logs and signing material out of commits and issues. Never add a provider secret to APK resources or build configuration.

Describe the user-visible change and how you verified it. Include a sanitized reproduction with Android version, PULSE version and distribution for issues. Follow [SECURITY.md](SECURITY.md) for confidential reports. Review the [license status](README.md#legal-license-and-credits) before submitting or reusing source.
