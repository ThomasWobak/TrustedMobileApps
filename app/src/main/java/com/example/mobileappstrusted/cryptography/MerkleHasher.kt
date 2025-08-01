package com.example.mobileappstrusted.cryptography

import android.util.Log
import com.example.mobileappstrusted.audio.InputStreamReader.extractMerkleRootFromWav
import com.example.mobileappstrusted.audio.InputStreamReader.splitWavIntoBlocks
import com.example.mobileappstrusted.protobuf.WavBlockProtos
import java.io.File
import java.security.MessageDigest


object MerkleHasher {

    private val encryptionMessageDigest = MessageDigest.getInstance("SHA-256")

    fun getEncryptionMessageDigest ():  MessageDigest{
        return encryptionMessageDigest
    }

    fun hashChunk(chunk: ByteArray): ByteArray {
        return encryptionMessageDigest.digest(chunk)
    }

    fun buildMerkleRoot(blocks: List<WavBlockProtos.WavBlock>): ByteArray {
        val sortedBlocks = blocks
            .sortedBy { it.originalIndex }

        var currentLevel = sortedBlocks.map {
            if (it.isDeleted && it.undeletedHash != null) {
                it.undeletedHash.toByteArray()
            } else {
                hashChunk(it.pcmData.toByteArray())
            }
        }

        while (currentLevel.size > 1) {
            currentLevel = currentLevel.chunked(2).map { pair ->
                val left = pair[0]
                val right = if (pair.size == 2) pair[1] else pair[0]
                hashChunk(left + right)
            }
        }

        return currentLevel.first()
    }

    fun verifyWavMerkleRoot(file: File): Boolean {
        if (!file.exists() || file.length() <= 44) return false

        val omrhHash = extractMerkleRootFromWav(file)
        if (omrhHash == null) {
            Log.w("AudioDebug", "No 'omrh' chunk found.")
            return false
        }

        val (_, blocks) = splitWavIntoBlocks(file)
        val sortedBlocks = blocks
            .sortedBy { it.originalIndex }

        val recomputedRoot = buildMerkleRoot(sortedBlocks)

        val matches = omrhHash.originalRootHash.toByteArray().contentEquals(recomputedRoot)

        if (matches) {
            Log.i("AudioDebug", "✅ Merkle root matches.")
        } else {
            Log.w("AudioDebug", "❌ Merkle root mismatch.")
        }

        return matches
    }

}
