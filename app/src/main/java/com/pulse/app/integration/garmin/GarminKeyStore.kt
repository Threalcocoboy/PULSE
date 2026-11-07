package com.pulse.app.integration.garmin

import android.content.Context
import android.security.keystore.*
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.*
import javax.crypto.spec.GCMParameterSpec

/** Only an AES-GCM ciphertext and IV leave memory. Android Keystore owns the encryption key. */
class GarminKeyStore(context: Context) {
    private val file=AtomicFile(File(context.noBackupFilesDir,"garmin-key.enc"))
    private val alias="pulse.garmin.personal.v1"
    fun configured()=file.baseFile.exists()
    private fun encryptionKey(): javax.crypto.SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        (store.getKey(alias,null) as? javax.crypto.SecretKey)?.let{return it}
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        }.generateKey()
    }
    @Synchronized fun save(raw: String) {
        val key=raw.trim();require(key.length in 16..4096 && key.none {it.isWhitespace() || it.code !in 33..126}){"Invalid key format"}
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,encryptionKey())
        val encrypted=cipher.doFinal(key.toByteArray(Charsets.UTF_8));val output=file.startWrite()
        try {output.write(cipher.iv);output.write(encrypted);file.finishWrite(output)}catch(e: Exception){file.failWrite(output);throw GarminFailure(GarminError.KEY_REQUIRED)}
    }
    @Synchronized fun read(): String? {
        if(!configured())return null
        return try {val bytes=file.readFully();require(bytes.size>28);val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,encryptionKey(),GCMParameterSpec(128,bytes.copyOfRange(0,12)));String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)),Charsets.UTF_8)}
        catch(e: Exception){throw GarminFailure(GarminError.KEY_REQUIRED)}
    }
    @Synchronized fun delete(){file.delete();KeyStore.getInstance("AndroidKeyStore").apply{load(null)}.deleteEntry(alias)}
}
