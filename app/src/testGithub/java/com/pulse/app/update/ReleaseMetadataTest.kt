package com.pulse.app.update

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ReleaseMetadataTest {
    private val file = "PULSE-1.2.0-29-min28.apk"
    private val asset = JSONObject().put("name", file).put("state", "uploaded")
        .put("content_type", "application/vnd.android.package-archive")
        .put("size", 1200).put("digest", "sha256:" + "a".repeat(64))
        .put("browser_download_url", "$PULSE_REPOSITORY/releases/download/v1.2.0/$file")
    private val release = JSONObject().put("tag_name", "v1.2.0").put("draft", false)
        .put("prerelease", false).put("body", "Release notes")
        .put("assets", JSONArray().put(asset))

    @Test fun newerBuildUsesApkVersionCodeAndGithubDigest() {
        val apk = requireNotNull(parseRelease(release.toString(), 28, 35))
        assertEquals(PulseRelease("1.2.0", 29, "Release notes", "$PULSE_REPOSITORY/releases/tag/v1.2.0"), apk.release)
        assertEquals("a".repeat(64), apk.digest)
        assertEquals(1200L, apk.bytes)
    }

    @Test fun stableChannelRejectsDraftsPreviewsAndNonUpgrades() {
        assertNull(parseRelease(release.toString(), 29, 35))
        assertNull(parseRelease(release.toString(), 30, 35))
        assertNull(parseRelease(release.put("prerelease", true).toString(), 0, 35))
        release.put("prerelease", false)
        assertNull(parseRelease(release.put("draft", true).toString(), 0, 35))
    }

    @Test fun rejectsMissingSdkCompatibilityOrAmbiguousAsset() {
        assertThrows(UpdateFailure::class.java) { parseRelease(release.toString(), 0, 27) }
        assertThrows(UpdateFailure::class.java) { parseRelease(release.put("assets", JSONArray()).toString(), 0, 35) }
        assertThrows(UpdateFailure::class.java) {
            parseRelease(release.put("assets", JSONArray().put(asset).put(asset)).toString(), 0, 35)
        }
    }

    @Test fun rejectsUntrustedSourceOversizeAndMissingDigest() {
        asset.put("browser_download_url", "https://example.invalid/$file")
        assertThrows(UpdateFailure::class.java) { parseRelease(release.toString(), 0, 35) }
        asset.put("browser_download_url", "$PULSE_REPOSITORY/releases/download/v1.2.0/$file")
        asset.put("size", MAX_APK_BYTES + 1)
        assertThrows(UpdateFailure::class.java) { parseRelease(release.toString(), 0, 35) }
        asset.put("size", 1200)
        asset.remove("digest")
        assertThrows(UpdateFailure::class.java) { parseRelease(release.toString(), 0, 35) }
    }
}
