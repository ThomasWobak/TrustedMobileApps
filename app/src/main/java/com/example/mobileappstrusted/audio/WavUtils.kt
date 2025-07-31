package com.example.mobileappstrusted.audio

import android.content.Context
import android.util.Log
import com.example.mobileappstrusted.audio.InputStreamReader.chunkRawPcm
import com.example.mobileappstrusted.audio.OutputStreamWriter.writeEditHistoryChunkToStream
import com.example.mobileappstrusted.audio.OutputStreamWriter.writeMerkleRootChunkToStream
import com.example.mobileappstrusted.audio.OutputStreamWriter.writeMetaDataChunkToStream
import com.example.mobileappstrusted.audio.OutputStreamWriter.writeSignatureChunkToStream
import com.example.mobileappstrusted.audio.OutputStreamWriter.writeWavBlocksToStream
import com.example.mobileappstrusted.audio.OutputStreamWriter.writeWavHeaderToStream
import com.example.mobileappstrusted.cryptography.DigitalSignatureUtils
import com.example.mobileappstrusted.cryptography.MerkleHasher
import com.example.mobileappstrusted.protobuf.EditHistoryProto
import com.example.mobileappstrusted.protobuf.RecordingMetadataProto
import com.example.mobileappstrusted.protobuf.SignatureBlockProto
import com.example.mobileappstrusted.protobuf.WavBlockProtos
import com.google.protobuf.ByteString
import com.google.protobuf.CodedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
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

    fun writeWavFile(pcmData: ByteArray, outputFile: File, metadata: RecordingMetadataProto.RecordingMetadata) {
        val blocks = chunkRawPcm(pcmData)
        val merkleRoot = MerkleHasher.buildMerkleRoot(blocks)

        val dataBlocksSize = blocks.sumOf { block ->
            val blockBytes = block.toByteArray()
            val prefixSize = CodedOutputStream.computeUInt32SizeNoTag(blockBytes.size)
            prefixSize + blockBytes.size
        }

        outputFile.outputStream().use { outputStream ->
            writeWavHeaderToStream( pcmDataSize = dataBlocksSize,
                merkleChunkSize = 35,
                editHistorySize = 0,
                metaDataSize= metadata.toByteArray().size, 0,
                outputStream)

            writeWavBlocksToStream(outputStream, blocks)
            writeMerkleRootChunkToStream(outputStream, merkleRoot)
            writeMetaDataChunkToStream(outputStream, metadata)
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

    fun writeWavFileToPersistentStorage(
        context: Context,
        outputStream: OutputStream,
        blocks: List<WavBlockProtos.WavBlock>,
        merkleRoot: ByteArray,
        editHistory: EditHistoryProto.EditHistory,
        metaData: RecordingMetadataProto.RecordingMetadata
    ) {
        val tempBuffer = ByteArrayOutputStream()

        val dataBlocksSize = blocks.sumOf { block ->
            val blockBytes = block.toByteArray()
            val prefixSize = CodedOutputStream.computeUInt32SizeNoTag(blockBytes.size)
            prefixSize + blockBytes.size
        }

        val editHistorySize = editHistory.toByteArray().size
        val metaDataSize = metaData.toByteArray().size

        var digitalSignatureBlockSize = 0
        if (DigitalSignatureUtils.isPrivateKeyStored(context)){
            val prefixSize = CodedOutputStream.computeUInt32SizeNoTag(833)
            digitalSignatureBlockSize = 833 + prefixSize
        }

        writeWavHeaderToStream(
            pcmDataSize = dataBlocksSize,
            merkleChunkSize = 35,
            editHistorySize = editHistorySize,
            metaDataSize = metaDataSize,
            signatureSize = digitalSignatureBlockSize,
            outputStream = tempBuffer
        )

        writeWavBlocksToStream(tempBuffer, blocks)
        writeMerkleRootChunkToStream(tempBuffer, merkleRoot)
        writeEditHistoryChunkToStream(tempBuffer, editHistory)
        writeMetaDataChunkToStream(tempBuffer, metaData)

        val wavBytes = tempBuffer.toByteArray()

        val signatureBlock: SignatureBlockProto.SignatureBlock? = if (DigitalSignatureUtils.isPrivateKeyStored(context)) {
            try {

                val publicKey = DigitalSignatureUtils.loadPublicKeyFromPrefs(context)!!

                val signature = DigitalSignatureUtils.signData(wavBytes, context)

                Log.i("AudioDebug", "Length of digital signature: " + signature.size)

                SignatureBlockProto.SignatureBlock.newBuilder()
                    .setDigitalSignature(ByteString.copyFrom(signature))
                    .setPublicKey(ByteString.copyFrom(publicKey))
                    .setSignatureAlgorithm("SHA256withRSA")
                    .setTimestamp(System.currentTimeMillis())
                    .setSignerIdentity(metaData.deviceId)
                    .build()
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        } else null

        outputStream.write(wavBytes)

        signatureBlock?.let {
            writeSignatureChunkToStream(outputStream, it)
        }
    }

    fun writeWavToTemporaryStorage(pcmData: ByteArray, outputFile: File) {
        val blocks = chunkRawPcm(pcmData)
        val merkleRoot = MerkleHasher.buildMerkleRoot(blocks)

        val dataBlocksSize = blocks.sumOf { block ->
            val blockBytes = block.toByteArray()
            val prefixSize = CodedOutputStream.computeUInt32SizeNoTag(blockBytes.size)
            prefixSize + blockBytes.size
        }

        outputFile.outputStream().use { outputStream ->
            writeWavHeaderToStream( pcmDataSize = dataBlocksSize,
                merkleChunkSize = 35,
                editHistorySize = 0,
                metaDataSize = 0,
                signatureSize = 0,
                outputStream)

            writeWavBlocksToStream(outputStream, blocks)
            writeMerkleRootChunkToStream(outputStream, merkleRoot)
        }
    }
}
