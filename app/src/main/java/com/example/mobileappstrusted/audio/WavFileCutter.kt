package com.example.mobileappstrusted.audio

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavCutter {

    fun cutWavFile(inputPath: String, startSec: Float, endSec: Float): File? {
        return try {
            val inputFile = File(inputPath)
            val raf = RandomAccessFile(inputFile, "r")

            val header = ByteArray(44)
            raf.readFully(header)
            val sampleRate =
                ByteBuffer.wrap(header, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val channels =
                ByteBuffer.wrap(header, 22, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
            val bitDepth =
                ByteBuffer.wrap(header, 34, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()

            if (channels != 1 || sampleRate != 44100 || bitDepth != 16) {
                raf.close()
                return null
            }

            val bytesPerSample = bitDepth / 8
            val dataSize = inputFile.length().toInt() - 44
            val totalSamples = dataSize / bytesPerSample

            val startSample = (startSec * sampleRate).toInt().coerceIn(0, totalSamples)
            val endSample = (endSec * sampleRate).toInt().coerceIn(startSample, totalSamples)
            val samplesBefore = startSample
            val samplesAfter = totalSamples - endSample
            val newDataSize = (samplesBefore + samplesAfter) * bytesPerSample
            val newTotalDataLen = newDataSize + 36

            val outputFile = File(inputFile.parentFile, "cut_${System.currentTimeMillis()}.wav")
            val outRaf = RandomAccessFile(outputFile, "rw")

            val newHeader = header.copyOf()
            WavUtils.writeIntLE(newHeader, 4, newTotalDataLen)
            WavUtils.writeIntLE(newHeader, 40, newDataSize)
            outRaf.write(newHeader)

            val buffer = ByteArray(4096)
            raf.seek(44)
            var bytesToCopy = samplesBefore * bytesPerSample
            while (bytesToCopy > 0) {
                val toRead = minOf(buffer.size, bytesToCopy)
                val read = raf.read(buffer, 0, toRead)
                if (read == -1) break
                outRaf.write(buffer, 0, read)
                bytesToCopy -= read
            }

            raf.seek((44 + endSample * bytesPerSample).toLong())
            bytesToCopy = samplesAfter * bytesPerSample
            while (bytesToCopy > 0) {
                val toRead = minOf(buffer.size, bytesToCopy)
                val read = raf.read(buffer, 0, toRead)
                if (read == -1) break
                outRaf.write(buffer, 0, read)
                bytesToCopy -= read
            }

            raf.close()
            outRaf.close()
            outputFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

