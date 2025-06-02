package com.example.mobileappstrusted.audio

import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

const val ORIGINAL_MERKLE_ROOT_HASH_CHUNK_IDENTIFIER="omrh"
object MerkleHasher {
    private fun hashChunk(chunk: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(chunk)
    }

    fun buildMerkleRoot(chunks: List<ByteArray>): ByteArray {
        if (chunks.isEmpty()) throw IllegalArgumentException("No chunks to hash")

        var currentLevel = chunks.map { hashChunk(it) }

        while (currentLevel.size > 1) {
            currentLevel = currentLevel.chunked(2).map { pair ->
                val left = pair[0]
                val right = if (pair.size == 2) pair[1] else pair[0] // duplicate last if odd
                hashChunk(left + right)
            }
        }

        return currentLevel[0]
    }

    fun extractMerkleRootFromWav(file: File): ByteArray? {
        val bytes = file.readBytes()
        var offset = 12

        while (offset + 8 <= bytes.size) {
            val chunkId = String(bytes, offset, 4, Charsets.US_ASCII)
            val chunkSize = ByteBuffer.wrap(bytes, offset + 4, 4)
                .order(ByteOrder.LITTLE_ENDIAN).int

            if (chunkId == ORIGINAL_MERKLE_ROOT_HASH_CHUNK_IDENTIFIER) {
                Log.w("AudioDebug", "Found chunk!!!")
                return bytes.sliceArray(offset + 8 until offset + 8 + chunkSize)
            }

            offset += 8 + chunkSize
        }
        Log.w("AudioDebug", "Shit")

        return null
    }

    fun verifyWavMerkleRoot(file: File): Boolean {
        val bytes = file.readBytes()
        if (bytes.size <= 44) return false

        // Step 1: Extract the 'omrh' chunk
        var omrhHash: ByteArray? = null
        var offset = 12
        var dataChunkStart = -1
        var dataChunkSize = -1

        while (offset + 8 <= bytes.size) {
            val chunkId = String(bytes, offset, 4, Charsets.US_ASCII)
            val chunkSize = ByteBuffer.wrap(bytes, offset + 4, 4)
                .order(ByteOrder.LITTLE_ENDIAN).int

            if (chunkId == "data") {
                dataChunkStart = offset + 8
                dataChunkSize = chunkSize
            } else if (chunkId == ORIGINAL_MERKLE_ROOT_HASH_CHUNK_IDENTIFIER) {
                val start = offset + 8
                val end = start + chunkSize
                if (end <= bytes.size) {
                    omrhHash = bytes.sliceArray(start until end)
                }
            }

            offset += 8 + chunkSize
        }

        if (omrhHash == null) {
            Log.w("AudioDebug", "No 'omrh' chunk found.")
            return false
        }

        if (dataChunkStart == -1 || dataChunkSize <= 0 || dataChunkStart + dataChunkSize > bytes.size) {
            Log.w("AudioDebug", "Invalid or missing PCM 'data' chunk.")
            return false
        }

        // Step 2: Recompute Merkle root
        val pcmData = bytes.sliceArray(dataChunkStart until (dataChunkStart + dataChunkSize))
        val recomputedRoot = buildMerkleRoot(
            AudioChunker.chunkAudioData(pcmData, 2048)
        )

        val matches = omrhHash.contentEquals(recomputedRoot)

        if (matches) {
            Log.i("AudioDebug", "✅ Merkle root matches.")
        } else {
            Log.w("AudioDebug", "❌ Merkle root mismatch.")
        }

        return matches
    }

}