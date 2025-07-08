package com.example.mobileappstrusted.cryptography

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import java.io.InputStreamReader
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec

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

    private fun loadPrivateKeyFromPem(pemBytes: ByteArray): PrivateKey {
        val reader = PEMParser(InputStreamReader(pemBytes.inputStream()))
        val converter = JcaPEMKeyConverter().setProvider("BC")
        val parsed = reader.readObject()
        reader.close()

        val privateKey = when (parsed) {
            is org.bouncycastle.openssl.PEMKeyPair -> converter.getKeyPair(parsed).private
            is org.bouncycastle.openssl.PEMEncryptedKeyPair -> {
                throw IllegalArgumentException("Encrypted PEMs not supported in this method.")
            }
            is org.bouncycastle.asn1.pkcs.PrivateKeyInfo -> converter.getPrivateKey(parsed)
            else -> throw IllegalArgumentException("Unsupported PEM format: ${parsed?.javaClass}")
        }

        return privateKey
    }

    fun storePrivateKey(context: Context, pemBytes: ByteArray) {
        storePrivateKey(context, loadPrivateKeyFromPem(pemBytes))
    }

    fun storePrivateKey(context: Context, key: PrivateKey) {
        // 1. Encode private key to base64
        val encodedKey = Base64.encodeToString(key.encoded, Base64.DEFAULT)

        // 2. Create or get master key
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        // 3. Create encrypted shared preferences
        val sharedPrefs = EncryptedSharedPreferences.create(
            context,
            "secure_key_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        // 4. Store the base64-encoded private key
        sharedPrefs.edit()
            .putString("private_key", encodedKey)
            .apply()
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

    fun isPrivateKeyStored(context: android.content.Context): Boolean {
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

}
