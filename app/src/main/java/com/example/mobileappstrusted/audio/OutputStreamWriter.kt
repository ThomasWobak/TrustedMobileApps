package com.example.mobileappstrusted.audio

import android.util.Log
import com.example.mobileappstrusted.cryptography.ORIGINAL_MERKLE_ROOT_HASH_CHUNK_IDENTIFIER
import com.example.mobileappstrusted.protobuf.EditHistoryProto
import com.example.mobileappstrusted.protobuf.OmrhBlockProtos
import com.example.mobileappstrusted.protobuf.RecordingMetadataProto
import com.example.mobileappstrusted.protobuf.WavBlockProtos
import com.google.protobuf.ByteString
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object OutputStreamWriter {

    fun writeWavBlocksToStream(
        outputStream: OutputStream,
        blocks: List<WavBlockProtos.WavBlock>
    ) {
        blocks.forEach { block ->
            block.writeDelimitedTo(outputStream)
            Log.d("AudioDebug", "Wrote Block")
        }
    }

    fun writeMerkleRootChunkToStream(
        outputStream: OutputStream,
        merkleRoot: ByteArray
    ) {
        val block = OmrhBlockProtos.OmrhBlock.newBuilder()
            .setOriginalRootHash(ByteString.copyFrom(merkleRoot))
            .build()

        val chunkId = ORIGINAL_MERKLE_ROOT_HASH_CHUNK_IDENTIFIER.toByteArray(Charsets.US_ASCII)

        // Temporarily serialize the delimited block to count real length
        val tempStream = ByteArrayOutputStream()
        block.writeDelimitedTo(tempStream)
        val blockBytes = tempStream.toByteArray()

        val chunkSizeBytes = ByteBuffer.allocate(4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(blockBytes.size)  // Only the actual bytes, not Protobuf internal size prefix logic
            .array()

        outputStream.write(chunkId)
        outputStream.write(chunkSizeBytes)
        outputStream.write(blockBytes)
    }



    fun writeEditHistoryChunkToStream(
        outputStream: OutputStream,
        editHistory: EditHistoryProto.EditHistory
    ) {
        val historyBytes = editHistory.toByteArray()
        val chunkId = "edhi".toByteArray(Charsets.US_ASCII)
        val chunkSizeBytes = ByteBuffer.allocate(4)
            .order(ByteOrder.LITTLE_ENDIAN).putInt(historyBytes.size).array()

        outputStream.write(chunkId)
        outputStream.write(chunkSizeBytes)
        outputStream.write(historyBytes)
    }

    fun writeMetaDataChunkToStream(outputStream: OutputStream, metadata: RecordingMetadataProto.RecordingMetadata) {
        val metadataBytes = metadata.toByteArray()
        val chunkId = "meta".toByteArray(Charsets.US_ASCII)
        val chunkSizeBytes = ByteBuffer.allocate(4)
            .order(ByteOrder.LITTLE_ENDIAN).putInt(metadataBytes.size).array()

        outputStream.write(chunkId)
        outputStream.write(chunkSizeBytes)
        outputStream.write(metadataBytes)
    }

    fun writeWavHeaderToStream(pcmDataSize: Int, merkleChunkSize: Int, editHistorySize: Int, metaDataSize: Int, outputStream: OutputStream) {
        val sampleRate = 44100
        val channels = 1
        val bitDepth = 16
        val byteRate = sampleRate * channels * bitDepth / 8

        val chunkFmtSize = 16
        val chunkFmtHeader = 8
        val chunkDataHeader = 8
        val chunkOmrhHeader = 8
        val chunkEdhiHeader = 8
        val chunkMetaHeader = 8
        val riffHeader = 4



        val riffSize = riffHeader +
                chunkFmtHeader + chunkFmtSize +
                chunkDataHeader + pcmDataSize +
                chunkOmrhHeader + merkleChunkSize +
                chunkEdhiHeader + editHistorySize +
                chunkMetaHeader + metaDataSize


        val header = ByteArray(44)

        // RIFF header
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        WavUtils.writeInt(header, 4, riffSize - 8)
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()

        // fmt chunk
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        WavUtils.writeInt(header, 16, 16)
        WavUtils.writeShort(header, 20, 1)
        WavUtils.writeShort(header, 22, channels.toShort())
        WavUtils.writeInt(header, 24, sampleRate)
        WavUtils.writeInt(header, 28, byteRate)
        WavUtils.writeShort(header, 32, (channels * bitDepth / 8).toShort())
        WavUtils.writeShort(header, 34, bitDepth.toShort())

        // data chunk
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        WavUtils.writeInt(header, 40, pcmDataSize)

        outputStream.write(header)
    }

    fun writeSignatureChunkToStream(outputStream: OutputStream, signature: ByteArray) {
        val chunkId = "dsig".toByteArray(Charsets.US_ASCII)
        val chunkSize = signature.size
        val sizeBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(chunkSize).array()

        outputStream.write(chunkId)     // 4 bytes: 'dsig'
        outputStream.write(sizeBytes)   // 4 bytes: chunk size
        outputStream.write(signature)   // N bytes: actual signature
    }
}