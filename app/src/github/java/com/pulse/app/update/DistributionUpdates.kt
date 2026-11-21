package com.pulse.app.update

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.pulse.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

internal class DistributionUpdates(private val context: Context) {
    val github = true
    val supportsAutomatic = true
    val available = !BuildConfig.DEBUG && BuildConfig.GITHUB_REPOSITORY_VERIFIED
    val downloadLabel = "Download update"
    private val directory = File(context.cacheDir, "updates")
    private val verified = File(directory, "pulse.apk")
    private val manager = context.packageManager
    private var selected: ReleaseApk? = null
    private var throttledUntil = 0L
    private var ready: ReleaseApk? = null

    suspend fun check(metadataOnly: Boolean = false): PulseRelease? = withContext(Dispatchers.IO) {
        if (System.currentTimeMillis() < throttledUntil) throw UpdateFailure("GitHub has limited update checks. Try again later.")
        // This directory is reserved for this updater, and the installer never uses partial files.
        if (!metadataOnly) {
            directory.listFiles()?.forEach { it.delete() }
            ready = null
            selected = null
        }
        val connection = connect(URI("https://api.github.com/repos/THREALCOCOBOY/PULSE/releases/latest"), metadata = true)
        try {
            when (connection.responseCode) {
                404 -> throw UpdateFailure("GitHub has no public PULSE release available. Open GitHub Releases to check for downloads.")
                403, 429 -> {
                    val now = System.currentTimeMillis()
                    val seconds = connection.getHeaderField("Retry-After")?.toLongOrNull()?.coerceIn(60, 3600)
                    val reset = connection.getHeaderField("X-RateLimit-Reset")?.toLongOrNull()
                    throttledUntil = seconds?.let { now + it * 1000 }
                        ?: reset?.coerceIn(now / 1000 + 60, now / 1000 + 3600)?.times(1000)
                        ?: now + 60_000
                    throw UpdateFailure("GitHub has limited update checks. Try again later.")
                }
                200 -> Unit
                else -> throw UpdateFailure("GitHub couldn't return the latest PULSE release.")
            }
            if (connection.contentType?.substringBefore(';') != "application/json") {
                throw UpdateFailure("GitHub returned an unexpected release response.")
            }
            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > 512 * 1024) throw UpdateFailure("GitHub release metadata was too large.")
                    output.write(buffer, 0, count)
                }
            }
            val latest = parseRelease(output.toString(Charsets.UTF_8.name()), BuildConfig.VERSION_CODE.toLong(), Build.VERSION.SDK_INT)
            if (!metadataOnly) selected = latest
            latest?.release
        } catch (_: org.json.JSONException) {
            throw UpdateFailure("GitHub returned unreadable release metadata.")
        } finally {
            connection.disconnect()
        }
    }

    suspend fun prepare(
        release: PulseRelease,
        playLauncher: ActivityResultLauncher<IntentSenderRequest>,
        progress: (Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val apk = selected?.takeIf { it.release == release } ?: throw UpdateFailure("Check for the update again.")
        ready = null
        verified.delete()
        if (!directory.isDirectory && !directory.mkdirs()) throw UpdateFailure("Couldn't create update storage.")
        val temporary = File.createTempFile("pulse-", ".apk", directory)
        try {
            progress(0)
            download(apk, temporary, progress)
            currentCoroutineContext().ensureActive()
            verifyPackage(temporary, apk)
            currentCoroutineContext().ensureActive()
            if (!temporary.renameTo(verified)) throw UpdateFailure("Couldn't save the verified update.")
            ready = apk
        } finally {
            temporary.delete()
        }
    }

    private suspend fun download(apk: ReleaseApk, file: File, progress: (Int) -> Unit) {
        val connection = connect(apk.url, metadata = false)
        try {
            val length = connection.contentLengthLong
            if (connection.responseCode != 200 || !validApkType(connection.contentType) || length >= 0 && length != apk.bytes) {
                throw UpdateFailure("GitHub returned an unexpected APK.")
            }
            var total = 0L
            var displayed = 0
            val digest = MessageDigest.getInstance("SHA-256")
            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > apk.bytes) throw UpdateFailure("The APK exceeds its published size.")
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        val percent = (total * 100 / apk.bytes).toInt()
                        if (percent != displayed) {
                            displayed = percent
                            progress(percent)
                        }
                    }
                }
            }
            if (total != apk.bytes || digest.digest().joinToString("") { "%02x".format(it) } != apk.digest) {
                throw UpdateFailure("The APK failed its SHA-256 integrity check.")
            }
        } finally {
            connection.disconnect()
        }
    }

    @Suppress("DEPRECATION")
    private fun verifyPackage(file: File, apk: ReleaseApk) {
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val archive = manager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: throw UpdateFailure("Android couldn't read the downloaded package.")
        val installed = manager.getPackageInfo(context.packageName, flags)
        val info = archive.applicationInfo ?: throw UpdateFailure("The downloaded package has no application metadata.")
        if (archive.packageName != context.packageName || archive.longVersionCode <= installed.longVersionCode ||
            archive.longVersionCode != apk.release.code || archive.versionName != apk.release.version ||
            info.minSdkVersion != apk.minimumSdk || info.minSdkVersion > Build.VERSION.SDK_INT ||
            info.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            throw UpdateFailure("The APK is incompatible with this installation.")
        }
        fun signers(pkg: PackageInfo) = pkg.signingInfo?.apkContentsSigners?.toSet().orEmpty()
        val trusted = signers(installed)
        if (trusted.isEmpty() || signers(archive) != trusted) {
            throw UpdateFailure("The APK signing certificate differs from this installation.")
        }
    }

    @Suppress("DEPRECATION")
    fun install(): Intent {
        val apk = ready ?: throw UpdateFailure("Download the update again.")
        if (verified.length() != apk.bytes) throw UpdateFailure("Download the update again.")
        if (!manager.canRequestPackageInstalls()) {
            return Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", verified)
        return Intent(Intent.ACTION_INSTALL_PACKAGE, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .putExtra(Intent.EXTRA_RETURN_RESULT, true)
    }

    private fun connect(start: URI, metadata: Boolean): HttpsURLConnection {
        var url = start
        repeat(4) {
            val allowedHosts = if (metadata) setOf("api.github.com")
                else setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")
            if (url.scheme != "https" || url.host?.lowercase() !in allowedHosts || url.userInfo != null ||
                url.fragment != null || url.port !in setOf(-1, 443)) throw UpdateFailure("GitHub returned an untrusted update address.")
            val connection = url.toURL().openConnection() as HttpsURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("User-Agent", "PULSE-Android")
            connection.setRequestProperty("Accept-Encoding", "identity")
            if (metadata) {
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            }
            try {
                if (connection.responseCode !in setOf(301, 302, 303, 307, 308)) return connection
                val next = connection.getHeaderField("Location")
                if (metadata || next == null) throw UpdateFailure("GitHub redirected the release request unexpectedly.")
                url = url.resolve(next)
            } catch (e: Exception) {
                connection.disconnect()
                throw e
            }
            connection.disconnect()
        }
        throw UpdateFailure("The update download redirected too many times.")
    }
}
