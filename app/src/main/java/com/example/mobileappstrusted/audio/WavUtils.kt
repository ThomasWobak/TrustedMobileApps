package com.example.mobileappstrusted.audio

import android.content.Context
import android.util.Log
import com.example.mobileappstrusted.dataclass.WavBlock
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.min

object WavUtils {

    private const val BLOCK_SIZE = 100 * 1024  // 100 KB per block, roughly 1.16 seconds
    private const val OMRH_BLOCK_SIZE = 40

    fun extractAmplitudesFromWav(file: File, sampleEvery: Int = 200): List<Int> {
        val bytes = file.readBytes()
        if (bytes.size <= 44) return emptyList()
        val amplitudes = mutableListOf<Int>()
        var i = 44
        while (i + 1 < bytes.size) {
            val low = bytes[i].toInt() and 0xFF
            val high = bytes[i + 1].toInt()
            val sample = (high shl 8) or low
            amplitudes.add(abs(sample))
            i += 2 * sampleEvery
        }
        return amplitudes
    }

    fun splitWavIntoBlocks(file: File): Pair<ByteArray, List<WavBlock>> {
        return splitWavIntoBlocks(file.readBytes())
    }

    fun splitWavIntoBlocks(bytes: ByteArray): Pair<ByteArray, List<WavBlock>> {
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

    fun chunkRawPcm(pcm: ByteArray): List<WavBlock> {
        val count = (pcm.size + BLOCK_SIZE - 1) / BLOCK_SIZE
        return List(count) { idx ->
            val start = idx * BLOCK_SIZE
            val end = minOf(start + BLOCK_SIZE, pcm.size)
            WavBlock(
                originalIndex = idx,
                currentIndex = idx,
                data = pcm.copyOfRange(start, end),
                isDeleted = false
            )
        }
    }

    fun findDataChunk(bytes: ByteArray): Pair<Int, Int> {
        var offset = 12 // skip RIFF header

        while (offset + 8 <= bytes.size) {
            val id = String(bytes, offset, 4, Charsets.US_ASCII)
            val size = ByteBuffer.wrap(bytes, offset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int

            if (id == "data") {
                return offset + 8 to size // start of PCM, and its length
            }

            offset += 8 + size
        }

        throw IllegalStateException("Could not find 'data' chunk.")
    }

    fun writeBlocksToTempFile(
        context: Context,
        header: ByteArray,
        blocks: List<WavBlock>
    ): File {
        val tempDir = context.externalCacheDir ?: context.cacheDir
        val outFile = File.createTempFile("reorder_", ".wav", tempDir)

        val body = blocks.sortedBy { it.currentIndex }
            .fold(ByteArray(0)) { acc, b -> acc + b.data }

        val newTotal = body.size + 36
        val newHdr = header.copyOf().also {
            writeIntLE(it, 4, newTotal)
            writeIntLE(it, 40, body.size)
        }

        outFile.outputStream().use {
            it.write(newHdr)
            it.write(body)
        }
        return outFile
    }

    fun writeIntLE(b: ByteArray, offset: Int, v: Int) {
        b[offset] = (v and 0xFF).toByte()
        b[offset + 1] = ((v shr 8) and 0xFF).toByte()
        b[offset + 2] = ((v shr 16) and 0xFF).toByte()
        b[offset + 3] = ((v shr 24) and 0xFF).toByte()
    }

    /** WAV-writing helper (used with raw PCM input) */
    fun writeWavFile(
        pcmData: ByteArray,
        outputFile: File,
        sampleRate: Int,
        channels: Int,
        bitDepth: Int
    ) {
        val byteRate = sampleRate * channels * bitDepth / 8
        val audioDataSize = pcmData.size

        val merkleRoot = MerkleHasher.buildMerkleRoot(
            chunkRawPcm(pcmData)
        )
        val merkleChunkSize = merkleRoot.size

        val chunkFmtSize = 16
        val chunkFmtHeader = 8
        val chunkDataHeader = 8
        val chunkOmrhHeader = 8
        val riffHeader = 4

        val riffSize = riffHeader +
                chunkFmtHeader + chunkFmtSize +
                chunkDataHeader + audioDataSize +
                chunkOmrhHeader + merkleChunkSize

        val header = ByteArray(44)

        // RIFF header
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        writeInt(header, 4, riffSize - 8) // total length minus first 8 bytes
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()

        // fmt chunk
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        writeInt(header, 16, 16)
        writeShort(header, 20, 1)
        writeShort(header, 22, channels.toShort())
        writeInt(header, 24, sampleRate)
        writeInt(header, 28, byteRate)
        writeShort(header, 32, (channels * bitDepth / 8).toShort())
        writeShort(header, 34, bitDepth.toShort())

        // data chunk
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        writeInt(header, 40, audioDataSize)

        outputFile.outputStream().use { out ->
            out.write(header)
            out.write(pcmData)

            // Write "omrh" chunk
            val chunkId = ORIGINAL_MERKLE_ROOT_HASH_CHUNK_IDENTIFIER.toByteArray(Charsets.US_ASCII)
            val chunkSizeBytes = ByteBuffer.allocate(4)
                .order(ByteOrder.LITTLE_ENDIAN).putInt(merkleChunkSize).array()

            out.write(chunkId)
            out.write(chunkSizeBytes)
            out.write(merkleRoot)
        }

    }

    fun writeInt(b: ByteArray, offset: Int, value: Int) {
        b[offset] = (value and 0xff).toByte()
        b[offset + 1] = ((value shr 8) and 0xff).toByte()
        b[offset + 2] = ((value shr 16) and 0xff).toByte()
        b[offset + 3] = ((value shr 24) and 0xff).toByte()
    }

    fun writeShort(b: ByteArray, offset: Int, value: Short) {
        b[offset] = (value.toInt() and 0xff).toByte()
        b[offset + 1] = ((value.toInt() shr 8) and 0xff).toByte()
    }
}
