package com.example.mobileappstrusted.cryptography

import com.example.mobileappstrusted.cryptography.MerkleHasher.getEncryptionMessageDigest
import com.example.mobileappstrusted.protobuf.WavBlockProtos
import com.google.protobuf.ByteString
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object WavBlockDecrypter {

    private fun deriveKeyFromPassword(password: String): SecretKeySpec {
        val keyBytes = getEncryptionMessageDigest().digest(password.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun decrypt(encryptedData: ByteArray, password: String): ByteArray {
        val secretKey = deriveKeyFromPassword(password)
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey)
        return cipher.doFinal(encryptedData)
    }

    fun decryptDeletedBlocksWithPassword(
        blocks: List<WavBlockProtos.WavBlock>,
        password: String
    ): List<WavBlockProtos.WavBlock> {
        return blocks.map { block ->
            if (block.isDeleted) {
                if (block.undeletedHash == null || block.undeletedHash.isEmpty) {
                    throw IllegalStateException("Missing undeleted_hash for deleted block at index ${block.originalIndex}")
                }

                val decryptedPcm = decrypt(block.pcmData.toByteArray(), password)

                val actualHash = getEncryptionMessageDigest().digest(decryptedPcm)
                val expectedHash = block.undeletedHash.toByteArray()

                if (!actualHash.contentEquals(expectedHash)) {
                    throw IllegalStateException("Decrypted PCM does not match undeleted_hash for block at index ${block.originalIndex}")
                }

                return@map block.toBuilder()
                    .setPcmData(ByteString.copyFrom(decryptedPcm))
                    .setIsDeleted(false)
                    .clearUndeletedHash()
                    .build()
            }

            block // unchanged if not deleted
        }
    }
}
