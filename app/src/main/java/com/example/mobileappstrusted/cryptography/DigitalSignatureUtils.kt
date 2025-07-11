package com.example.mobileappstrusted.cryptography

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.mobileappstrusted.audio.DIGITAL_SIGNATURE_HASH_CHUNK_IDENTIFIER
import com.example.mobileappstrusted.audio.InputStreamReader.extractDigitalSignatureBlockFromWav
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStreamReader
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPrivateCrtKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec

object DigitalSignatureUtils {

    // Sign the input byte array using the private key
    fun signData(data: ByteArray, privateKey: PrivateKey): ByteArray {
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(privateKey)
        signature.update(data)
        return signature.sign()
    }

    // Verify the signature using the public key
    fun verifySignature(data: ByteArray, signatureBytes: ByteArray, publicKey: PublicKey): Boolean {
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initVerify(publicKey)
        signature.update(data)
        return signature.verify(signatureBytes)
    }

    fun verifyDigitalSignatureFromWav(file: File): Boolean {
        val fullBytes = file.readBytes()

        // Step 1: Extract the signature block
        val dsigBlock = extractDigitalSignatureBlockFromWav(file) ?: return false.also {
            Log.w("AudioDebug", "No signature block found.")
        }

        // Step 2: Remove the dsig chunk from the file data
        val cleanedBytes = removeSignatureChunk(fullBytes)

        // Step 3: Lookup public key using key ID
        val publicKeyId = dsigBlock.publicKeyId
        val publicKey: PublicKey = try {
            // TODO: Replace this with actual PKI fetch using publicKeyId
            fetchPublicKeyFromAauPKI(dsigBlock.publicKeyId)
        } catch (e: Exception) {
            Log.w("AudioDebug", "Public key fetch failed: ${e.message}")
            return false
        }

        // Step 4: Verify signature
        return try {
            val isValid = verifySignature(
                cleanedBytes,
                dsigBlock.digitalSignature.toByteArray(),
                publicKey
            )
            Log.i("AudioDebug", "Signature verification result: $isValid")
            isValid
        } catch (e: Exception) {
            Log.w("AudioDebug", "Signature verification failed: ${e.message}")
            false
        }
    }

    fun fetchPublicKeyFromAauPKI(keyId: String): PublicKey {
        val certUrl = URL("https://pki.aau.at/certs/$keyId.cer") // Example endpoint; adjust if different
        certUrl.openStream().use { input ->
            val certFactory = CertificateFactory.getInstance("X.509")
            val cert = certFactory.generateCertificate(input) as X509Certificate

            // Optional: validate certificate here using CA chain, revocation checks, etc.
            return cert.publicKey
        }
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
                // Skip this chunk
                buffer.position(buffer.position() + chunkSize)
            } else {
                // Write chunk ID + size + data
                output.write(chunkIdBytes)
                output.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(chunkSize).array())
                val chunkData = ByteArray(chunkSize)
                buffer.get(chunkData)
                output.write(chunkData)
            }
        }

        return output.toByteArray()
    }


    fun loadKeyPairFromPem(pemBytes: ByteArray): KeyPair {
        val reader = PEMParser(InputStreamReader(pemBytes.inputStream()))
        val converter = JcaPEMKeyConverter().setProvider("BC")
        val parsed = reader.readObject()
        reader.close()

        return when (parsed) {
            is PEMKeyPair -> {
                val bcKeyPair = converter.getKeyPair(parsed)
                KeyPair(bcKeyPair.public, bcKeyPair.private)
            }

            is PrivateKeyInfo -> {
                val privateKey = converter.getPrivateKey(parsed)
                if (privateKey is RSAPrivateCrtKey) {
                    val keyFactory = KeyFactory.getInstance("RSA")
                    val pubSpec = RSAPublicKeySpec(privateKey.modulus, privateKey.publicExponent)
                    val publicKey = keyFactory.generatePublic(pubSpec)
                    KeyPair(publicKey, privateKey)
                } else {
                    throw IllegalArgumentException("Only RSA key reconstruction supported from PrivateKeyInfo.")
                }
            }

            else -> throw IllegalArgumentException("Unsupported PEM format: ${parsed?.javaClass}")
        }
    }

    fun loadKeyPairFromP12(p12Bytes: ByteArray, password: String): KeyPair {
        val keyStore = KeyStore.getInstance("PKCS12")
        keyStore.load(p12Bytes.inputStream(), password.toCharArray())

        val alias = keyStore.aliases().nextElement()
        val privateKey = keyStore.getKey(alias, password.toCharArray()) as PrivateKey
        val cert = keyStore.getCertificate(alias)
        val publicKey = cert.publicKey

        return KeyPair(publicKey, privateKey)
    }

    fun storeKeyPair(context: Context, keyPair: KeyPair) {
        val privateKey = keyPair.private
        val publicKey = keyPair.public

        // Encode keys to base64
        val privateEncoded = Base64.encodeToString(privateKey.encoded, Base64.DEFAULT)
        val publicKeyId = getPublicKeyId(publicKey)

        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val sharedPrefs = EncryptedSharedPreferences.create(
            context,
            "secure_key_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        sharedPrefs.edit()
            .putString("private_key", privateEncoded)
            .putString("public_key_id", publicKeyId)
            .apply()
    }

    fun storeKeyPairFromPem(context: Context, pemBytes: ByteArray) {
        val keyPair = loadKeyPairFromPem(pemBytes)
        storeKeyPair(context, keyPair)
    }

    fun storeKeyPairFromP12(context: Context, p12Bytes: ByteArray, password: String) {
        val keyPair = loadKeyPairFromP12(p12Bytes, password)
        storeKeyPair(context, keyPair)
    }

    fun loadPrivateKeyFromPrefs(context: Context): PrivateKey? {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val prefs = EncryptedSharedPreferences.create(
            context,
            "secure_key_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        val encoded = prefs.getString("private_key", null) ?: return null
        val decoded = Base64.decode(encoded, Base64.DEFAULT)
        val keySpec = PKCS8EncodedKeySpec(decoded)
        return KeyFactory.getInstance("RSA").generatePrivate(keySpec)
    }

    fun loadPublicKeyIdFromPrefs(context: Context): String? {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val prefs = EncryptedSharedPreferences.create(
            context,
            "secure_key_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        return prefs.getString("public_key_id", null)
    }

    fun isPrivateKeyStored(context: Context): Boolean {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val prefs = EncryptedSharedPreferences.create(
            context,
            "secure_key_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        return prefs.contains("private_key")
    }

    fun getPublicKeyId(publicKey: PublicKey): String {
        val encoded = publicKey.encoded
        val digest = MessageDigest.getInstance("SHA-256").digest(encoded)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
