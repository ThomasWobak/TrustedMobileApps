package com.example.mobileappstrusted.cryptography

import com.example.mobileappstrusted.cryptography.MerkleHasher.getEncryptionMessageDigest
import com.example.mobileappstrusted.protobuf.WavBlockProtos
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object WavBlockEncryptor {

    private fun deriveKeyFromPassword(password: String): SecretKeySpec {
        val keyBytes = getEncryptionMessageDigest().digest(password.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun encrypt(data: ByteArray, password: String): ByteArray {
        val secretKey = deriveKeyFromPassword(password)
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        return cipher.doFinal(data)
    }

    fun encryptDeletedBlocksWithPassword(
        blocks: List<WavBlockProtos.WavBlock>,
        password: String
    ): List<WavBlockProtos.WavBlock> {
        return blocks.map { block ->
            if (block.isDeleted) {
                val pcmData = block.pcmData.toByteArray()
                val undeletedHash = if (block.undeletedHash != null && !block.undeletedHash.isEmpty) block.undeletedHash.toByteArray() else null

                val matches = undeletedHash?.let {
                    getEncryptionMessageDigest().digest(pcmData).contentEquals(it)
                } ?: false

                if (matches) {
                    val encrypted = encrypt(pcmData, password)
                    return@map block.toBuilder()
                        .setPcmData(com.google.protobuf.ByteString.copyFrom(encrypted))
                        .build()
                }
            }
            block // unchanged if not deleted or hash doesnt match (already encrypted????)
        }
    }
}
