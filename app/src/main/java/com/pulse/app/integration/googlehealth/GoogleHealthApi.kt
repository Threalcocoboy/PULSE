package com.pulse.app.integration.googlehealth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicLong

internal enum class GoogleHealthProblem(val description: String) {
    NOT_LINKED("Open the Google Health app, sign in with the same Google account, and finish setting up your profile. Then reconnect in PULSE."),
    CONSENT("Grant at least one Google Health permission to connect."),
    ACCOUNT_SELECTION_CANCELLED("No Google account was selected. Tap Connect with Google to choose one."),
    ACCOUNT_SELECTION_FAILED("The account picker did not return a Google account. Try again or check that the account is on this device."),
    AUTHORIZATION_CANCELLED("The Google consent screen was closed. Try again when you’re ready to grant access."),
    AUTHORIZATION_INTERRUPTED("Google did not complete authorization. Try again with Activity & fitness only. If it keeps happening, check the app’s OAuth settings."),
    AUTHORIZATION_LAUNCH_FAILED("PULSE could not open the Google authorization screen. Update Google Play services and try again."),
    AUTHENTICATION("Google could not sign in to this account. Check the account on this device and try again."),
    REAUTHORIZE("Google access needs attention. Reconnect to continue synchronizing."),
    ACCOUNT_CHANGED("This Google account differs from the connected account. Use Switch account to connect it."),
    PLAY_SERVICES("Install or update Google Play services to connect Google Health."),
    CONFIGURATION("Google Health authorization could not start. Check PULSE's OAuth client and app signing in Google Cloud."),
    EXPIRED("Google authorization expired. Reconnect if this continues."),
    PERMISSION("Google Health denied access to this data."),
    RETRY("Google Health is temporarily unavailable. Try syncing again."),
    NETWORK("Could not reach Google Health. Check your connection and try again."),
    UNSUPPORTED("Google Health does not provide this measurement for this connection."),
    RESPONSE("Google Health could not return some records. Try syncing again."),
    STORAGE("PULSE could not open or save the encrypted Google Health data.")
}

internal class GoogleHealthFailure(val problem: GoogleHealthProblem, val statusCode: Int? = null) : Exception(problem.description)

internal class GoogleHealthToken(val value: String, val scopes: Set<String>) {
    override fun toString() = "GoogleHealthToken([redacted])"
}

internal data class GoogleHealthResponse(val status: Int, val json: JSONObject, val retryAfterSeconds: Long? = null)

internal fun interface GoogleHealthTransport {
    suspend fun request(path: String, token: String, body: JSONObject?): GoogleHealthResponse
}

internal class GoogleHealthHttpTransport : GoogleHealthTransport {
    override suspend fun request(path: String, token: String, body: JSONObject?) = withContext(Dispatchers.IO) {
        val wait = (retryAfter.get() - System.currentTimeMillis() + 999) / 1000
        if (wait > 0) return@withContext GoogleHealthResponse(429, JSONObject(), wait)
        val connection = URI.create("https://health.googleapis.com/v4/$path").toURL().openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 12_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Accept-Language", "en-US")
            if (body != null) {
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { input ->
                val buffer = ByteArray(8192)
                val output = java.io.ByteArrayOutputStream()
                var read = input.read(buffer)
                while (read != -1) {
                    if (output.size() + read > 8 * 1024 * 1024) throw GoogleHealthFailure(GoogleHealthProblem.RESPONSE)
                    output.write(buffer, 0, read)
                    read = input.read(buffer)
                }
                output.toByteArray()
            }
            val json = try {
                bytes?.takeIf { it.isNotEmpty() }?.let { JSONObject(String(it, Charsets.UTF_8)) } ?: JSONObject()
            } catch (_: org.json.JSONException) {
                if (status in 200..299) throw GoogleHealthFailure(GoogleHealthProblem.RESPONSE)
                JSONObject()
            }
            val retry = connection.getHeaderField("Retry-After")?.let { value ->
                value.toLongOrNull() ?: runCatching {
                    (ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() -
                        System.currentTimeMillis() + 999) / 1000
                }.getOrNull()
            }?.coerceIn(0, 86_400)
            if (retry != null && (status == 429 || status == 503)) {
                val until = System.currentTimeMillis() + retry * 1000
                retryAfter.updateAndGet { maxOf(it, until) }
            }
            GoogleHealthResponse(status, json, retry)
        } finally {
            connection.disconnect()
        }
    }

    private companion object { val retryAfter = AtomicLong() }
}

internal class GoogleHealthApi(
    private val transport: GoogleHealthTransport = GoogleHealthHttpTransport(),
    private val pause: suspend (Long) -> Unit = { delay(it) }
) {
    suspend fun identity(token: GoogleHealthToken): String =
        request("users/me/identity", token).optString("healthUserId")
            .takeIf { it.matches(Regex("[A-Za-z0-9-]{1,63}")) }
            ?: throw GoogleHealthFailure(GoogleHealthProblem.RESPONSE)

    suspend fun records(
        type: GoogleHealthType, token: GoogleHealthToken, start: LocalDate, end: LocalDate,
        method: GoogleHealthMethod = type.method
    ): List<JSONObject> {
        require(end > start && end <= start.plusDays(14))
        val path = "users/me/dataTypes/${type.id}/dataPoints${method.suffix}"
        return pages(method.responseKey) { page ->
            when (method) {
                GoogleHealthMethod.DAILY_ROLLUP -> {
                    val body = JSONObject()
                        .put("range", JSONObject().put("start", midnight(start)).put("end", midnight(end)))
                        .put("windowSizeDays", 1)
                    if (page != null) body.put("pageToken", page)
                    request(path, token, body)
                }
                else -> {
                    val field = "${type.id.replace('-', '_')}.${type.dateFilter}"
                    val filter = """$field >= "$start" AND $field < "$end""""
                    val size = when (type.id) {
                        "exercise", "sleep" -> 25
                        "heart-rate" -> 2000
                        else -> 500
                    }
                    val query = "pageSize=$size&filter=${encode(filter)}" +
                        (page?.let { "&pageToken=${encode(it)}" } ?: "")
                    request("$path?$query", token)
                }
            }
        }
    }

    suspend fun foodReference(name: String, type: String, token: GoogleHealthToken): JSONObject? {
        require(type == "food" || type == "food-measurement-unit")
        val parts = name.split('/')
        if (parts.size != 6 || parts[0] != "users" || parts[2] != "dataTypes" || parts[3] != type ||
            parts[4] != "dataPoints" || parts.any { !it.matches(Regex("[A-Za-z0-9_.~-]+")) || it in setOf(".", "..") }) return null
        return request("users/me/dataTypes/$type/dataPoints/${parts.last()}", token)
    }

    private suspend fun pages(key: String, fetch: suspend (String?) -> JSONObject): List<JSONObject> {
        val result = mutableListOf<JSONObject>()
        val seen = hashSetOf<String>()
        var page: String? = null
        do {
            val json = fetch(page)
            result += json.optJSONArray(key).objects()
            page = json.optString("nextPageToken").takeIf(String::isNotBlank)
            if (page != null && !seen.add(page)) throw GoogleHealthFailure(GoogleHealthProblem.RESPONSE)
        } while (page != null)
        return result
    }

    private suspend fun request(path: String, token: GoogleHealthToken, body: JSONObject? = null): JSONObject {
        repeat(3) { attempt ->
            val response = try {
                transport.request(path, token.value, body)
            } catch (_: IOException) {
                if (attempt == 2) throw GoogleHealthFailure(GoogleHealthProblem.NETWORK)
                pause(500L shl attempt)
                return@repeat
            }
            if (response.status in 200..299) return response.json
            val reasons = response.json.optJSONObject("error")?.optJSONArray("details").objects()
                .mapNotNull { it.optString("reason").takeIf(String::isNotBlank) }.toSet()
            val problem = when {
                "ACCOUNT_NOT_LINKED" in reasons -> GoogleHealthProblem.NOT_LINKED
                reasons.any { it in setOf("SERVICE_DISABLED", "ACCESS_NOT_CONFIGURED") } -> GoogleHealthProblem.CONFIGURATION
                "ACCESS_TOKEN_SCOPE_INSUFFICIENT" in reasons -> GoogleHealthProblem.CONSENT
                response.status == 401 -> GoogleHealthProblem.EXPIRED
                response.status == 403 -> GoogleHealthProblem.PERMISSION
                response.status == 501 -> GoogleHealthProblem.UNSUPPORTED
                response.status == 429 || response.status in 500..599 -> GoogleHealthProblem.RETRY
                else -> GoogleHealthProblem.RESPONSE
            }
            if (problem != GoogleHealthProblem.RETRY || attempt == 2) throw GoogleHealthFailure(problem, response.status)
            val retryAfter = response.retryAfterSeconds ?: 0L
            if (retryAfter > 10) throw GoogleHealthFailure(problem)
            pause(maxOf(500L shl attempt, retryAfter * 1000))
        }
        error("Unreachable")
    }

    private fun midnight(date: LocalDate) = JSONObject()
        .put("date", JSONObject().put("year", date.year).put("month", date.monthValue).put("day", date.dayOfMonth))
        .put("time", JSONObject())

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}

internal fun org.json.JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull(::optJSONObject)
