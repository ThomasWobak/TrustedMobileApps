package com.example.mobileappstrusted.cryptography

import com.example.mobileappstrusted.cryptography.MerkleHasher.getEncryptionMessageDigest
import com.example.mobileappstrusted.protobuf.WavBlockProtos
import com.google.protobuf.ByteString
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.xor

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
    ): List<WavBlockProtos.WavBlock>? {
        val digest = getEncryptionMessageDigest()
        var chainingValue = digest.digest(password.toByteArray(Charsets.UTF_8)) // Initial chaining value from password

        return try {
            blocks.map { block ->
                if (block.isEncrypted) {
                    if (block.undeletedHash == null || block.undeletedHash.isEmpty) {
                        block
                    }

                    val decryptedChained = decrypt(block.pcmData.toByteArray(), password)

                    // Reverse XOR to recover original PCM
                    val originalPcm = ByteArray(decryptedChained.size) { i ->
                        decryptedChained[i].xor(chainingValue[i % chainingValue.size])
                    }

                    // Verify hash
                    val actualHash = digest.digest(originalPcm)
                    val expectedHash = block.undeletedHash.toByteArray()

                    if (!actualHash.contentEquals(expectedHash)) {
                        return null
                    }

                    // Update chaining value for next block
                    chainingValue = digest.digest(block.pcmData.toByteArray())

                    block.toBuilder()
                        .setPcmData(ByteString.copyFrom(originalPcm))
                        .setIsDeleted(false)
                        .clearUndeletedHash()
                        .setIsEncrypted(false)
                        .setCurrentIndex(block.currentIndex)
                        .setOriginalIndex(block.originalIndex)
                        .build()
                } else {
                    block
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
