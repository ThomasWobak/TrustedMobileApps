package com.example.mobileappstrusted.audio

import java.io.InputStream

object AudioChunker {
    fun readWavFile(inputStream: InputStream): ByteArray {
        val headerSize = 44
        val fullBytes = inputStream.readBytes()
        return fullBytes.sliceArray(headerSize until fullBytes.size)
    }

    fun chunkAudioData(data: ByteArray, chunkSize: Int): List<ByteArray> {
        return data.asList().chunked(chunkSize).map { it.toByteArray() }
    }
}
