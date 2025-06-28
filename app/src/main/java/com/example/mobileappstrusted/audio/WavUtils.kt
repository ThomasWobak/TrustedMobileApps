package com.example.mobileappstrusted.audio

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings.Secure.ANDROID_ID
import android.provider.Settings.Secure.getString
import com.example.mobileappstrusted.audio.InputStreamReader.chunkRawPcm
import com.example.mobileappstrusted.audio.OutputStreamWriter.writeEditHistoryChunkToStream
import com.example.mobileappstrusted.audio.OutputStreamWriter.writeMerkleRootChunkToStream
import com.example.mobileappstrusted.audio.OutputStreamWriter.writeWavBlocksToStream
import com.example.mobileappstrusted.audio.OutputStreamWriter.writeWavHeaderToStream
import com.example.mobileappstrusted.cryptography.MerkleHasher
import com.example.mobileappstrusted.protobuf.EditHistoryProto
import com.example.mobileappstrusted.protobuf.WavBlockProtos
import com.google.protobuf.CodedOutputStream
import java.io.File
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

object WavUtils {

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



    fun writeBlocksToTempFile(
        context: Context,
        header: ByteArray,
        blocks: List<WavBlockProtos.WavBlock>
    ): File {
        val tempDir = context.externalCacheDir ?: context.cacheDir
        val outFile = File.createTempFile("reorder_", ".wav", tempDir)

        val body = blocks.sortedBy { it.currentIndex }
            .fold(ByteArray(0)) { acc, b -> acc + b.pcmData.toByteArray() }

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

    fun writeWavFile(pcmData: ByteArray, outputFile: File) {
        val blocks = chunkRawPcm(pcmData)
        val merkleRoot = MerkleHasher.buildMerkleRoot(blocks)

        val dataBlocksSize = blocks.sumOf { block ->
            val blockBytes = block.toByteArray()
            val prefixSize = CodedOutputStream.computeUInt32SizeNoTag(blockBytes.size)
            prefixSize + blockBytes.size
        }


        outputFile.outputStream().use { outputStream ->
            writeWavHeaderToStream( pcmDataSize = dataBlocksSize,
                merkleChunkSize = merkleRoot.size,
                editHistorySize = 0,
                outputStream)

            writeWavBlocksToStream(outputStream, blocks)
            writeMerkleRootChunkToStream(outputStream, merkleRoot)
        }
    }

    fun writeIntLE(b: ByteArray, offset: Int, v: Int) {
        b[offset] = (v and 0xFF).toByte()
        b[offset + 1] = ((v shr 8) and 0xFF).toByte()
        b[offset + 2] = ((v shr 16) and 0xFF).toByte()
        b[offset + 3] = ((v shr 24) and 0xFF).toByte()
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

    fun writeBlocksWithMetadata(
        outputStream: OutputStream,
        blocks: List<WavBlockProtos.WavBlock>,
        merkleRoot: ByteArray,
        editHistory: EditHistoryProto.EditHistory
    ) {
        val dataBlocksSize = blocks.sumOf { block ->
            val blockBytes = block.toByteArray()
            val prefixSize = CodedOutputStream.computeUInt32SizeNoTag(blockBytes.size)
            prefixSize + blockBytes.size
        }

        val editHistorySize = editHistory.toByteArray().size


        writeWavHeaderToStream( pcmDataSize = dataBlocksSize,
            merkleChunkSize = merkleRoot.size,
            editHistorySize = editHistorySize,
            outputStream)

        writeWavBlocksToStream(outputStream, blocks)
        writeMerkleRootChunkToStream(outputStream, merkleRoot)
        writeEditHistoryChunkToStream(outputStream, editHistory)
    }





    @SuppressLint("HardwareIds")
    fun getDeviceId(context: Context): String {
        return getString(
            context.contentResolver,
            ANDROID_ID
        )
    }
    fun getDeviceName(): String {
        return "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
    }



    fun extractEditHistoryFromWav(file: File): EditHistoryProto.EditHistory? {
        val bytes = file.readBytes()
        var i = 12 // skip RIFF header

        while (i + 8 < bytes.size) {
            val chunkId = bytes.copyOfRange(i, i + 4).toString(Charsets.US_ASCII)
            val chunkSize = ByteBuffer.wrap(bytes, i + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val chunkStart = i + 8
            val chunkEnd = chunkStart + chunkSize

            if (chunkId == "edhi" && chunkEnd <= bytes.size) {
                val chunkData = bytes.copyOfRange(chunkStart, chunkEnd)
                return try {
                    EditHistoryProto.EditHistory.parseFrom(chunkData)
                } catch (e: Exception) {
                    null
                }
            }

            i += 8 + chunkSize
        }

        return null
    }

}
