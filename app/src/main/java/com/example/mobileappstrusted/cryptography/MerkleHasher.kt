package com.example.mobileappstrusted.cryptography

import android.util.Log
import com.example.mobileappstrusted.audio.WavUtils
import com.example.mobileappstrusted.protobuf.WavBlockProtos
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

const val ORIGINAL_MERKLE_ROOT_HASH_CHUNK_IDENTIFIER = "omrh"

object MerkleHasher {

    private val messageDigest = MessageDigest.getInstance("SHA-256")

    private fun hashChunk(chunk: ByteArray): ByteArray {
        return messageDigest.digest(chunk)
    }

    fun buildMerkleRoot(blocks: List<WavBlockProtos.WavBlock>): ByteArray {
        var currentLevel = blocks.map { hashChunk(it.pcmData.toByteArray()) }

        while (currentLevel.size > 1) {
            currentLevel = currentLevel.chunked(2).map { pair ->
                val left = pair[0]
                val right = if (pair.size == 2) pair[1] else pair[0]
                hashChunk(left + right)
            }
        }

        return currentLevel.first()
    }

    private fun extractMerkleRootFromWav(file: File): ByteArray? {
        val bytes = file.readBytes()
        var offset = 12

        while (offset + 8 <= bytes.size) {
            val chunkId = String(bytes, offset, 4, Charsets.US_ASCII)
            val chunkSize = ByteBuffer.wrap(bytes, offset + 4, 4)
                .order(ByteOrder.LITTLE_ENDIAN).int

            if (chunkId == ORIGINAL_MERKLE_ROOT_HASH_CHUNK_IDENTIFIER) {
                return bytes.sliceArray(offset + 8 until offset + 8 + chunkSize)
            }

            offset += 8 + chunkSize
        }

        return null
    }

    fun verifyWavMerkleRoot(file: File): Boolean {
        if (!file.exists() || file.length() <= 44) return false

        val omrhHash = extractMerkleRootFromWav(file)
        if (omrhHash == null) {
            Log.w("AudioDebug", "No 'omrh' chunk found.")
            return false
        }

        val (_, blocks) = WavUtils.splitWavIntoBlocks(file)
        val sortedBlocks = blocks
            .map { it.toBuilder().setCurrentIndex(it.originalIndex).build() }
            .sortedBy { it.currentIndex }

        val recomputedRoot = buildMerkleRoot(sortedBlocks)
        val matches = omrhHash.contentEquals(recomputedRoot)

        if (matches) {
            Log.i("AudioDebug", "✅ Merkle root matches.")
        } else {
            Log.w("AudioDebug", "❌ Merkle root mismatch.")
        }

        return matches
    }
}
