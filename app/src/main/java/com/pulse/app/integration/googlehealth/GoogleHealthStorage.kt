package com.pulse.app.integration.googlehealth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.pulse.app.data.AchievementProgressEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class GoogleHealthConnection(val account: String, val id: String, val scopes: Set<String>, val synced: Instant? = null) {
    val cacheId: String get() = MessageDigest.getInstance("SHA-256").digest(id.toByteArray())
        .joinToString("") { "%02x".format(it) }
    val sourceScope get() = "GOOGLE_HEALTH:$cacheId"
    override fun toString() = "GoogleHealthConnection([redacted])"
}

internal data class GoogleHealthWindow(val start: LocalDate, val end: LocalDate) {
    companion object {
        const val DAYS = 7L
        fun containing(date: LocalDate): GoogleHealthWindow {
            val start = LocalDate.ofEpochDay(Math.floorDiv(date.toEpochDay(), DAYS) * DAYS)
            return GoogleHealthWindow(start, start.plusDays(DAYS))
        }
    }
}

internal data class GoogleHealthBatch(
    val date: LocalDate,
    val through: LocalDate,
    val updated: Instant,
    val points: List<JSONObject>,
    val details: List<JSONObject> = emptyList()
)

internal interface GoogleHealthStore {
    fun connection(): GoogleHealthConnection?
    fun setConnection(connection: GoogleHealthConnection?)
    fun batch(account: GoogleHealthConnection, type: String, start: LocalDate): GoogleHealthBatch?
    fun dates(account: GoogleHealthConnection, type: String): Set<LocalDate>
    fun save(account: GoogleHealthConnection, type: String, batch: GoogleHealthBatch)
    fun delete(account: GoogleHealthConnection, type: String, start: LocalDate)
    fun remove(account: GoogleHealthConnection)
    fun removeAll()
}

/** Separate, account-bound cache. Backup and transfer exclusions are inherited from noBackupFilesDir. */
internal class GoogleHealthStorage(private val root: File, private val cipher: GoogleHealthCipher) : GoogleHealthStore {
    constructor(context: Context) : this(File(context.noBackupFilesDir, "google-health"), GoogleHealthCipher())
    private val connectionFile = File(root, "connection.enc")

    override fun connection(): GoogleHealthConnection? = read(connectionFile)?.let { json ->
        val scopes = json.getJSONArray("scopes")
        GoogleHealthConnection(json.getString("account"), json.getString("id"),
            (0 until scopes.length()).map(scopes::getString).toSet().intersect(GoogleHealthMetrics.scopes),
            json.string("synced")?.let(Instant::parse))
    }

    override fun setConnection(connection: GoogleHealthConnection?) {
        if (connection == null) {
            AtomicFile(connectionFile).delete()
            check(!connectionFile.exists())
        } else {
            write(connectionFile, JSONObject().put("account", connection.account).put("id", connection.id)
                .put("scopes", JSONArray(connection.scopes.toList())).put("synced", connection.synced?.toString()))
        }
    }

    override fun batch(account: GoogleHealthConnection, type: String, start: LocalDate): GoogleHealthBatch? =
        read(file(account, type, start))?.let { json ->
            GoogleHealthBatch(start, LocalDate.parse(json.getString("through")), Instant.parse(json.getString("updated")),
                json.optJSONArray("points").objects(), json.optJSONArray("details").objects())
        }

    override fun dates(account: GoogleHealthConnection, type: String): Set<LocalDate> =
        directory(account, type).listFiles().orEmpty().mapNotNull {
            if (it.extension != "enc") null else runCatching { LocalDate.parse(it.nameWithoutExtension) }.getOrNull()
        }.toSet()

    override fun save(account: GoogleHealthConnection, type: String, batch: GoogleHealthBatch) {
        write(file(account, type, batch.date), JSONObject()
            .put("through", batch.through.toString()).put("updated", batch.updated.toString())
            .put("points", JSONArray(batch.points)).put("details", JSONArray(batch.details)))
    }

    override fun delete(account: GoogleHealthConnection, type: String, start: LocalDate) {
        AtomicFile(file(account, type, start)).delete()
    }

    override fun remove(account: GoogleHealthConnection) {
        check(File(root, account.cacheId).deleteRecursively())
    }

    override fun removeAll() {
        check(root.deleteRecursively())
        cipher.deleteKey()
    }

    fun achievements(scope: String): List<AchievementProgressEntity> = awards(scope).values.map { (entry, _) -> entry }

    fun pending(scope: String): Set<String> = awards(scope).filterValues { (entry, acknowledged) ->
        entry.unlocked && !acknowledged
    }.keys

    fun record(scope: String, entries: List<AchievementProgressEntity>) {
        val existing = awards(scope)
        writeAwards(scope, existing + entries.associate { entry ->
            entry.instance to (entry to (existing[entry.instance]?.second == true))
        })
    }

    fun acknowledge(scope: String, ids: List<String>) {
        val existing = awards(scope)
        if (existing.isNotEmpty()) writeAwards(scope, existing.mapValues { (id, saved) ->
            saved.first to (saved.second || id in ids)
        })
    }

    private fun awardsFile(scope: String): File {
        val cacheId = scope.removePrefix("GOOGLE_HEALTH:")
        require(cacheId.matches(Regex("[a-f0-9]{64}")))
        return File(File(root, cacheId), "awards.enc")
    }

    private fun awards(scope: String): Map<String, Pair<AchievementProgressEntity, Boolean>> =
        read(awardsFile(scope))?.optJSONArray("awards").objects().associate { row ->
            val id = row.getString("id")
            id to (AchievementProgressEntity(scope, id, row.getString("definition"), row.string("period"),
                row.getDouble("progress"), row.getBoolean("unlocked"), row.string("date")) to row.optBoolean("acknowledged"))
        }

    private fun writeAwards(scope: String, awards: Map<String, Pair<AchievementProgressEntity, Boolean>>) {
        val rows = awards.values.map { (entry, acknowledged) ->
            JSONObject().put("id", entry.instance).put("definition", entry.definition).put("period", entry.period)
                .put("progress", entry.progress).put("unlocked", entry.unlocked).put("date", entry.unlockedDate)
                .put("acknowledged", acknowledged)
        }
        write(awardsFile(scope), JSONObject().put("awards", JSONArray(rows)))
    }

    private fun directory(account: GoogleHealthConnection, type: String): File {
        require(type.matches(Regex("[a-z0-9-]+")))
        return File(File(root, account.cacheId), type)
    }

    private fun file(account: GoogleHealthConnection, type: String, date: LocalDate) =
        File(directory(account, type), "$date.enc")

    private fun aad(file: File) = file.relativeTo(root).invariantSeparatorsPath.toByteArray(Charsets.UTF_8)

    private fun read(file: File): JSONObject? {
        val atomic = AtomicFile(file)
        val bytes = try {
            atomic.readFully()
        } catch (e: java.io.FileNotFoundException) {
            if (!file.exists()) return null
            throw e
        }
        return JSONObject(String(cipher.decrypt(bytes, aad(file)), Charsets.UTF_8))
    }

    private fun write(file: File, json: JSONObject) {
        check(file.parentFile?.let { it.isDirectory || it.mkdirs() || it.isDirectory } == true)
        val bytes = cipher.encrypt(json.toString().toByteArray(Charsets.UTF_8), aad(file))
        val atomic = AtomicFile(file)
        val output = atomic.startWrite()
        try {
            output.write(bytes)
            atomic.finishWrite(output)
        } catch (e: Exception) {
            atomic.failWrite(output)
            throw e
        }
    }
}

internal class GoogleHealthCipher(private val alias: String = "pulse.google.health.cache.v1") {
    private companion object {
        val keyLock = Any()
        val keyHandles = mutableMapOf<String, SecretKey>()
    }

    private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun key(create: Boolean): SecretKey = synchronized(keyLock) {
        keyHandles[alias] ?: ((store().getKey(alias, null) as? SecretKey) ?: run {
            check(create)
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256).build())
            }.generateKey()
        }).also { keyHandles[alias] = it }
    }

    fun deleteKey() = synchronized(keyLock) {
        store().deleteEntry(alias)
        keyHandles.remove(alias)
        Unit
    }

    fun encrypt(plaintext: ByteArray, context: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(create = true))
        cipher.updateAAD(context)
        return cipher.iv + cipher.doFinal(plaintext)
    }

    fun decrypt(bytes: ByteArray, context: ByteArray): ByteArray {
        check(bytes.size >= 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(create = false), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        cipher.updateAAD(context)
        return cipher.doFinal(bytes, 12, bytes.size - 12)
    }
}
