package com.example.mobileappstrusted.audio

import android.util.Log
import com.example.mobileappstrusted.cryptography.ORIGINAL_MERKLE_ROOT_HASH_CHUNK_IDENTIFIER
import com.example.mobileappstrusted.protobuf.OmrhBlockProtos
import com.example.mobileappstrusted.protobuf.WavBlockProtos
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object InputStreamReader {
    private const val BLOCK_SIZE = 100 * 1024  // 100 KB per block, roughly 1.16 seconds

    fun splitWavIntoBlocks(file: File): Pair<ByteArray, List<WavBlockProtos.WavBlock>> {
        return splitWavIntoBlocks(file.readBytes())
    }

    fun splitWavIntoBlocks(bytes: ByteArray): Pair<ByteArray, List<WavBlockProtos.WavBlock>> {
        require(bytes.size >= 44)
        val header = bytes.copyOfRange(0, 44)
        val (dataStart, dataSize) = findDataChunk(bytes)

        if (dataStart + dataSize > bytes.size) {
            throw IllegalStateException("Data chunk size exceeds file length.")
        }

        val data = bytes.copyOfRange(dataStart, dataStart + dataSize)
        val blocks = chunkRawPcm(data)

        return header to blocks
    }
    fun chunkRawPcm(data: ByteArray): List<WavBlockProtos.WavBlock> {
        val blocks = mutableListOf<WavBlockProtos.WavBlock>()
        Log.d("AudioDebug", "Data Size: ${data.size}")

        // Try parsing using parseDelimitedFrom with ByteArrayInputStream
        try {
            val inputStream = ByteArrayInputStream(data)
            while (true) {
                val block = WavBlockProtos.WavBlock.parseDelimitedFrom(inputStream)
                    ?: break  // End of stream

                if (block.pcmData.isEmpty) {
                    Log.d("AudioDebug", "Empty PCM data found, treating as invalid.")
                    throw IllegalArgumentException("Empty block encountered")
                }

                blocks.add(block)
                Log.d("AudioDebug", "Parsed delimited block, PCM size: ${block.pcmData.size()}")
            }

            return blocks
        } catch (e: Exception) {
            Log.d("AudioDebug", "Delimited parsing failed, falling back to raw PCM: ${e.message}")
        }

        // Fallback: treat as raw PCM
        Log.d("AudioDebug", "Falling back to raw PCM mode")
        var offset = 0
        var index = 0
        while (offset < data.size) {
            val end = minOf(offset + BLOCK_SIZE, data.size)
            val chunkBytes = data.copyOfRange(offset, end)

            val block = WavBlockProtos.WavBlock.newBuilder()
                .setOriginalIndex(index)
                .setCurrentIndex(index)
                .setIsDeleted(false)
                .setUndeletedHash(com.google.protobuf.ByteString.EMPTY)
                .setPcmData(com.google.protobuf.ByteString.copyFrom(chunkBytes))
                .build()

            blocks.add(block)
            offset = end
            index++
        }

        return blocks
    }


    fun extractMerkleRootFromWav(file: File): OmrhBlockProtos.OmrhBlock? {
        val input = file.inputStream().buffered()
        input.skip(12) // Skip RIFF header

        val targetIdBytes = ORIGINAL_MERKLE_ROOT_HASH_CHUNK_IDENTIFIER.toByteArray(Charsets.US_ASCII)

        while (true) {
            val idBytes = ByteArray(4)
            val sizeBytes = ByteArray(4)

            if (input.read(idBytes) != 4 || input.read(sizeBytes) != 4) break

            val chunkSize = ByteBuffer.wrap(sizeBytes).order(ByteOrder.LITTLE_ENDIAN).int

            if (idBytes.contentEquals(targetIdBytes)) {
                return try {
                    OmrhBlockProtos.OmrhBlock.parseDelimitedFrom(input)
                } catch (e: Exception) {
                    Log.w("AudioDebug", "omrh block invalid: ${e.message}")
                    null
                }
            } else {
                try {
                    skipFully(input, chunkSize.toLong())
                } catch (e: Exception) {
                    Log.w("AudioDebug", "Failed to skip chunk: ${e.message}")
                    break
                }
            }
        }

        Log.w("AudioDebug", "No 'omrh' block found.")
        return null
    }

    private fun skipFully(input: InputStream, bytesToSkip: Long) {
        var remaining = bytesToSkip
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped <= 0) throw java.io.EOFException("Unable to skip $remaining more bytes")
            remaining -= skipped
        }
    }


    fun debugPrintAllChunkHeaders(file: File) {
        val input = file.inputStream().buffered()
        input.skip(12) // Skip RIFF header

        var offset = 12
        val bytes = file.readBytes()

        while (offset + 8 <= bytes.size) {
            val chunkId = bytes.copyOfRange(offset, offset + 4)
                .toString(Charsets.US_ASCII)
            val chunkSize = ByteBuffer.wrap(bytes, offset + 4, 4)
                .order(ByteOrder.LITTLE_ENDIAN).int

            Log.d("AudioDebug", "Chunk found at offset $offset: ID='$chunkId' size=$chunkSize")

            offset += 8 + chunkSize
        }
    }




    fun findDataChunk(bytes: ByteArray): Pair<Int, Int> {
        var offset = 12 // skip RIFF header

        while (offset + 8 <= bytes.size) {
            val id = String(bytes, offset, 4, Charsets.US_ASCII)
            val size = ByteBuffer.wrap(bytes, offset + 4, 4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .int

            val chunkDataStart = offset + 8
            val chunkDataEnd = chunkDataStart + size

            if (chunkDataEnd > bytes.size) {
                throw IllegalStateException("Chunk $id size exceeds file length.")
            }

            if (id == "data") {
                return chunkDataStart to size
            }

            offset += 8 + size
        }

        throw IllegalStateException("Could not find 'data' chunk.")
    }

}