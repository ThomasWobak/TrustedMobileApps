package com.example.mobileappstrusted.cryptography

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.mobileappstrusted.audio.DIGITAL_SIGNATURE_HASH_CHUNK_IDENTIFIER
import com.example.mobileappstrusted.audio.InputStreamReader.extractDigitalSignatureBlockFromWav
import org.pgpainless.PGPainless
import org.pgpainless.decryption_verification.ConsumerOptions
import org.pgpainless.sop.SOPImpl
import org.pgpainless.util.ArmorUtils
import sop.SOP
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object DigitalSignatureUtils {

    fun signData(data: ByteArray, context: Context): ByteArray {
        val privateKey = loadPrivateKeyFromPrefs(context)
        val sop: SOP = SOPImpl()
        val result = sop.detachedSign()
            .key(ByteArrayInputStream(privateKey))
            .data(data)
            .toByteArrayAndResult()
        return result.bytes
    }

    fun verifySignature(data: ByteArray, signatureBytes: ByteArray, publicKeyBytes: ByteArray): Boolean {
        val publicKeyRing = try {
            PGPainless.readKeyRing().publicKeyRing(ByteArrayInputStream(publicKeyBytes))
        } catch (e: Exception) {
            Log.i("AudioDebug", "Couldnt read key")
            return false
        }

        val options = publicKeyRing?.let {
            ConsumerOptions()
                .addVerificationCert(it)
                .addVerificationOfDetachedSignatures(ByteArrayInputStream(signatureBytes))
        }

        return try {
            if (options != null) {
                PGPainless.decryptAndOrVerify()
                    .onInputStream(ByteArrayInputStream(data))
                    .withOptions(options)
                    .use { it.readFully() }
            } else false
            true
        } catch (_: Exception) {
            Log.i("AudioDebug", "Verification didnt work")
            false
        }
    }

    private fun InputStream.readFully(): ByteArray {
        return this.use { input ->
            val buffer = ByteArrayOutputStream()
            val data = ByteArray(4096)
            var bytesRead: Int
            while (input.read(data).also { bytesRead = it } != -1) {
                buffer.write(data, 0, bytesRead)
            }
            buffer.toByteArray()
        }
    }
    @OptIn(ExperimentalStdlibApi::class)
    fun verifyDigitalSignatureFromWav(file: File): Boolean {
        val fullBytes = file.readBytes()
        val dsig = extractDigitalSignatureBlockFromWav(file) ?: return false
        val cleaned = removeSignatureChunk(fullBytes)

        val publicKeyBytes = dsig.publicKey.toByteArray() ?: return false

        return verifySignature(cleaned, dsig.digitalSignature.toByteArray(), publicKeyBytes)
    }


    fun storeKeyPairFromFile(context: Context,  inputStream: InputStream){
        val armoredKey = inputStream.bufferedReader(Charsets.US_ASCII).readText()
        val secretKeyRing = try {
            PGPainless.readKeyRing().secretKeyRing(armoredKey)
        } catch (e: Exception) {
            Log.i("AudioDebug", "Error reading file")
            throw IllegalArgumentException("File does not contain a valid PGP private key", e)
        }

        val publicKeyRing = secretKeyRing?.let { PGPainless.extractCertificate(it) }
        val keyId = secretKeyRing?.secretKeys?.asSequence()?.firstOrNull()?.keyID?.toString(16) ?: "unknown"

        if (publicKeyRing != null) {
            encryptedPrefs(context).edit()
                .putString("private_key", armoredKey)
                .putString("public_key", ArmorUtils.toAsciiArmoredString(publicKeyRing.encoded))
                .putString("public_key_id", keyId)
                .apply()
            Log.i("AudioDebug", "Read file correctly")
            return
        }
        Log.i("AudioDebug", "Could not get publicKeyRing")
        throw IllegalArgumentException("Could not generate a PGP publicKeyRing from passed file.")
    }

    private fun removeSignatureChunk(input: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteBuffer.wrap(input).order(ByteOrder.LITTLE_ENDIAN)

        // Copy RIFF header (first 12 bytes)
        output.write(input, 0, 12)
        buffer.position(12)

        val targetIdBytes = DIGITAL_SIGNATURE_HASH_CHUNK_IDENTIFIER.toByteArray(Charsets.US_ASCII)

        while (buffer.remaining() >= 8) {
            val chunkIdBytes = ByteArray(4)
            buffer.get(chunkIdBytes)

            val chunkSize = buffer.int

            if (chunkIdBytes.contentEquals(targetIdBytes)) {
                buffer.position(buffer.position() + chunkSize)
            } else {
                output.write(chunkIdBytes)
                output.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(chunkSize).array())
                val chunkData = ByteArray(chunkSize)
                buffer.get(chunkData)
                output.write(chunkData)
            }
        }

        return output.toByteArray()
    }

    // 5️⃣ STORE PRIVATE & PUBLIC KEY
    fun storeKeyPair(context: Context, armoredSecretKey: String) {
        val secretKeyRing = PGPainless.readKeyRing().secretKeyRing(armoredSecretKey)
        val publicKeyRing = secretKeyRing?.let { PGPainless.extractCertificate(it) }

        if (publicKeyRing != null) {
            encryptedPrefs(context).edit()
                .putString("private_key", armoredSecretKey)
                .putString("public_key", ArmorUtils.toAsciiArmoredString(publicKeyRing.encoded))
                .putString("public_key_id", extractKeyIdFromSecret(armoredSecretKey))
                .apply()
        }
    }

    // 6️⃣ LOAD PRIVATE KEY
    fun loadPrivateKeyFromPrefs(context: Context): ByteArray? {
        return encryptedPrefs(context).getString("private_key", null)?.toByteArray(Charsets.US_ASCII)
    }

    // 7️⃣ LOAD PUBLIC KEY
    fun loadPublicKeyFromPrefs(context: Context): ByteArray? {
        return encryptedPrefs(context).getString("public_key", null)?.toByteArray(Charsets.US_ASCII)
    }

    // 8️⃣ LOAD PUBLIC KEY ID
    fun loadPublicKeyIdFromPrefs(context: Context): String? {
        return encryptedPrefs(context).getString("public_key_id", null)
    }

    // 9️⃣ CHECK IF PRIVATE KEY IS STORED
    fun isPrivateKeyStored(context: Context): Boolean {
        return encryptedPrefs(context).contains("private_key")
    }

    // 🔟 UTILITY: EXTRACT KEY ID FROM ARMORED SECRET KEY
    private fun extractKeyIdFromSecret(armoredSecretKey: String): String {
        val secretKeyRing = PGPainless.readKeyRing().secretKeyRing(armoredSecretKey)
        if (secretKeyRing != null) {
            return secretKeyRing.secretKeys.next()?.keyID?.toString(16) ?: "unknown"
        }
        return "unknown"
    }

    // 🔐 ENCRYPTED PREFS HELPER
    private fun encryptedPrefs(context: Context) = EncryptedSharedPreferences.create(
        context, "secure_key_prefs",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}
