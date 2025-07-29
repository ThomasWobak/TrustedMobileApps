package com.example.mobileappstrusted.cryptography

import com.example.mobileappstrusted.cryptography.MerkleHasher.getEncryptionMessageDigest
import com.example.mobileappstrusted.protobuf.WavBlockProtos
import com.google.protobuf.ByteString
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.xor

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
        val digest = getEncryptionMessageDigest()
        var chainingValue = digest.digest(password.toByteArray(Charsets.UTF_8))

        return blocks.map { block ->
            if (block.isDeleted && !block.isEncrypted) {
                val pcmData = block.pcmData.toByteArray()

                val undeletedHash = MerkleHasher.hashChunk(pcmData)

                val chainedInput = ByteArray(pcmData.size) { i ->
                    pcmData[i].xor(chainingValue[i % chainingValue.size])
                }

                val encrypted = encrypt(chainedInput, password)

                chainingValue = digest.digest(encrypted)

                return@map block.toBuilder()
                    .setPcmData(ByteString.copyFrom(encrypted))
                    .setUndeletedHash(ByteString.copyFrom(undeletedHash))
                    .setIsEncrypted(true)
                    .build()
            }

            block // leave unchanged if not marked deleted
        }
    }
}
