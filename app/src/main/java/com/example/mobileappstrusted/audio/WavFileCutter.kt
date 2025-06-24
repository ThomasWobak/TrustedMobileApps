package com.example.mobileappstrusted.audio

import android.content.Context
import android.util.Log
import com.example.mobileappstrusted.cryptography.MerkleHasher
import java.io.File

object WavCutter {

    fun markBlockDeleted(context: Context,inputPath: String, blockIndex: Int): File? {
        val inputFile = File(inputPath)
        return try {
            val (header, blocks) = WavUtils.splitWavIntoBlocks(inputFile)

            if (blockIndex !in blocks.indices) return inputFile

            val updatedBlocks = blocks.mapIndexed { i, block ->
                if (i == blockIndex) {
                    val undeletedHash = MerkleHasher.hashChunk(block.pcmData.toByteArray())
                    block.toBuilder()
                        .setIsDeleted(true)
                        .setUndeletedHash(com.google.protobuf.ByteString.copyFrom(undeletedHash))
                        .build()
                } else {
                    block
                }
            }

            Log.w("AudioDebug", "Block deleted with index$blockIndex")

            val outputFile = File(inputFile.parentFile, "deleted_${System.currentTimeMillis()}.wav")
            WavUtils.writeBlocksToTempFile(context, header, updatedBlocks)
            return outputFile
        } catch (e: Exception) {
            e.printStackTrace()
            inputFile
        }
    }
}
