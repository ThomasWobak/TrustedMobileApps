package com.example.mobileappstrusted.cryptography

import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature

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
}
