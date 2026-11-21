package com.pulse.app.update

import org.json.JSONObject
import java.net.URI

private const val APK_MIME = "application/vnd.android.package-archive"
internal const val MAX_APK_BYTES = 150L * 1024 * 1024

internal data class ReleaseApk(
    val release: PulseRelease,
    val url: URI,
    val digest: String,
    val bytes: Long,
    val minimumSdk: Int
)

internal fun parseRelease(content: String, installedCode: Long, sdk: Int): ReleaseApk? {
    val json = JSONObject(content)
    if (json.getBoolean("draft") || json.getBoolean("prerelease")) return null
    val version = json.getString("tag_name").removePrefix("v")
    if (!Regex("""[0-9]{1,6}\.[0-9]{1,6}\.[0-9]{1,6}""").matches(version) ||
        json.getString("tag_name") != "v$version") {
        throw UpdateFailure("The latest release uses an unsupported version format.")
    }
    val page = "$PULSE_REPOSITORY/releases/tag/v$version"
    val pattern = Regex("""PULSE-${Regex.escape(version)}-([1-9][0-9]{0,9})-min([1-9][0-9]{0,2})\.apk""")
    val assets = json.getJSONArray("assets")
    val matches = (0 until assets.length()).mapNotNull { index ->
        val asset = assets.getJSONObject(index)
        pattern.matchEntire(asset.optString("name"))?.let { asset to it }
    }
    val (asset, match) = matches.singleOrNull()
        ?: throw UpdateFailure("The latest release does not have a single official PULSE APK.")
    val code = match.groupValues[1].toLong()
    val minimumSdk = match.groupValues[2].toInt()
    if (code > 2_100_000_000L) throw UpdateFailure("The release version code is invalid.")
    if (code <= installedCode) return null
    if (minimumSdk > sdk) throw UpdateFailure("The latest PULSE release requires a newer Android version.")
    val name = asset.getString("name")
    val url = URI("$PULSE_REPOSITORY/releases/download/v$version/$name")
    val size = asset.getLong("size")
    val digest = asset.optString("digest")
    val type = asset.optString("content_type")
    if (!url.toString().equals(asset.optString("browser_download_url"), ignoreCase = true) ||
        asset.optString("state") != "uploaded" || size !in 1..MAX_APK_BYTES ||
        !Regex("sha256:[a-f0-9]{64}").matches(digest) || !validApkType(type)) {
        throw UpdateFailure("GitHub's APK metadata could not be verified.")
    }
    return ReleaseApk(PulseRelease(version, code, json.optString("body").take(12_000), page),
        url, digest.removePrefix("sha256:"), size, minimumSdk)
}

internal fun validApkType(type: String?) = type?.substringBefore(';')?.trim()?.lowercase() in
    setOf(APK_MIME, "application/octet-stream")
