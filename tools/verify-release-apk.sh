#!/usr/bin/env bash
set -euo pipefail

sdk_tools="$ANDROID_HOME/build-tools/37.0.0"
apk=app/build/outputs/apk/github/release/app-github-release.apk
test -f "$apk"
expected_signer=${PULSE_SIGNER_SHA256,,}
[[ "$expected_signer" =~ ^[0-9a-f]{64}$ ]]
signing=$("$sdk_tools/apksigner" verify --min-sdk-version 28 --verbose --print-certs "$apk")
actual_signer=$(sed -n 's/^Signer #[0-9]* certificate SHA-256 digest: //p' <<< "$signing")
test "$expected_signer" = "$actual_signer"

metadata=$("$sdk_tools/aapt2" dump badging "$apk")
package_pattern="^package: name='com\.pulse\.app' versionCode='([1-9][0-9]{0,9})' versionName='([0-9]{1,6}\.[0-9]{1,6}\.[0-9]{1,6})'"
[[ "$metadata" =~ $package_pattern ]]
version_code=${BASH_REMATCH[1]}
version=${BASH_REMATCH[2]}
test "v$version" = "$GITHUB_REF_NAME"
(( version_code <= 2100000000 ))
grep -qx "sdkVersion:'28'" <<< "$metadata"
grep -q "android.permission.REQUEST_INSTALL_PACKAGES" <<< "$metadata"
if grep -q '^application-debuggable' <<< "$metadata"; then
  exit 1
fi
test "$(head -n 1 RELEASE_NOTES.md)" = "# PULSE $version"
test "$(wc -c < "$apk")" -le 157286400

mkdir -p release-output
name="PULSE-$version-$version_code-min28.apk"
cp "$apk" "release-output/$name"
cp RELEASE_NOTES.md release-output/
(cd release-output && sha256sum "$name" > SHA256SUMS.txt)
