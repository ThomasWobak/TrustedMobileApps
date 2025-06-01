// EditAudioScreen.kt
package com.example.mobileappstrusted.screens

import android.media.MediaPlayer
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mobileappstrusted.components.WaveformView
import com.example.mobileappstrusted.components.NoPathGivenScreen
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.abs

@Composable
fun EditAudioScreen(filePath: String) {
    // If no path was provided, show placeholder
    if (filePath.isBlank()) {
        NoPathGivenScreen()
        return
    }

    var currentFilePath by remember { mutableStateOf(filePath) }
    var amplitudes by remember { mutableStateOf<List<Int>>(emptyList()) }
    val mediaPlayer = remember { MediaPlayer() }
    var isPlaying by remember { mutableStateOf(false) }

    var removeStartText by remember { mutableStateOf("") }
    var removeEndText by remember { mutableStateOf("") }
    var removeError by remember { mutableStateOf<String?>(null) }

    val isWav = currentFilePath.lowercase().endsWith(".wav")

    LaunchedEffect(currentFilePath) {
        val file = File(currentFilePath)
        amplitudes = if (file.exists() && isWav) {
            extractAmplitudesFromWav(file)
        } else {
            emptyList()
        }
        mediaPlayer.reset()
        try {
            mediaPlayer.setDataSource(currentFilePath)
            mediaPlayer.prepare()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    DisposableEffect(mediaPlayer) {
        onDispose {
            mediaPlayer.release()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Edit Audio",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "Loaded file: ${File(currentFilePath).name}",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(onClick = {
                if (!isPlaying) {
                    mediaPlayer.start()
                    isPlaying = true
                    mediaPlayer.setOnCompletionListener { isPlaying = false }
                } else {
                    mediaPlayer.pause()
                    isPlaying = false
                }
            }) {
                Text(if (isPlaying) "Pause" else "Play")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // If not WAV, show message and skip cut controls
        if (!isWav) {
            Text(
                text = "Cutting is only supported for WAV files.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            // Cut segment controls
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = removeStartText,
                    onValueChange = { removeStartText = it },
                    label = { Text("Remove Start (sec)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = removeEndText,
                    onValueChange = { removeEndText = it },
                    label = { Text("Remove End (sec)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        removeError = null
                        val startSec = removeStartText.toFloatOrNull()
                        val endSec = removeEndText.toFloatOrNull()
                        if (startSec == null || endSec == null || startSec < 0f || endSec <= startSec) {
                            removeError = "Invalid start/end values"
                        } else {
                            val cutFile = cutWavFile(currentFilePath, startSec, endSec)
                            if (cutFile != null) {
                                currentFilePath = cutFile.absolutePath
                                removeStartText = ""
                                removeEndText = ""
                            } else {
                                removeError = "Cut failed"
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Remove Segment")
                }
                removeError?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        if (amplitudes.isNotEmpty()) {
            WaveformView(amplitudes)
        } else {
            if (isWav) {
                Text("Loading waveform...", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    "Cannot display waveform for non-WAV file.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

// ----- Helper: cutting WAV files -----
fun cutWavFile(inputPath: String, startSec: Float, endSec: Float): File? {
    try {
        val inputFile = File(inputPath)
        val raf = RandomAccessFile(inputFile, "r")

        // Read header (44 bytes)
        val header = ByteArray(44)
        raf.readFully(header)

        // Extract sampleRate, channels, bitDepth from header
        val sampleRate = ((header[27].toInt() and 0xFF) shl 24) or
                ((header[26].toInt() and 0xFF) shl 16) or
                ((header[25].toInt() and 0xFF) shl 8) or
                (header[24].toInt() and 0xFF)
        val channels = ((header[23].toInt() and 0xFF) shl 8) or (header[22].toInt() and 0xFF)
        val bitDepth = ((header[35].toInt() and 0xFF) shl 8) or (header[34].toInt() and 0xFF)

        if (channels != 1 || sampleRate != 44100 || bitDepth != 16) {
            raf.close()
            return null
        }

        val bytesPerSample = bitDepth / 8
        val dataSize = inputFile.length().toInt() - 44
        val totalSamples = dataSize / bytesPerSample

        val startByte = (startSec * sampleRate * bytesPerSample).toInt()
        val endByte = (endSec * sampleRate * bytesPerSample).toInt()
        val startSample = startByte / bytesPerSample
        val endSample = endByte / bytesPerSample

        if (startSample < 0 || endSample > totalSamples) {
            raf.close()
            return null
        }

        // New data size = (samples before start) + (samples after end)
        val samplesBefore = startSample
        val samplesAfter = totalSamples - endSample
        val newDataSize = (samplesBefore + samplesAfter) * bytesPerSample
        val newTotalDataLen = newDataSize + 36

        // Prepare output file
        val outputFile = File(inputFile.parentFile, "cut_${System.currentTimeMillis()}.wav")
        val outRaf = RandomAccessFile(outputFile, "rw")

        // Create new header
        val newHeader = header.copyOf()
        writeIntToByteArray(newHeader, 4, newTotalDataLen)
        writeIntToByteArray(newHeader, 40, newDataSize)
        outRaf.write(newHeader)

        // Copy first segment (0 to startSample)
        val buffer = ByteArray(4096)
        var bytesToCopy = samplesBefore * bytesPerSample
        raf.seek(44)
        while (bytesToCopy > 0) {
            val toRead = minOf(buffer.size, bytesToCopy)
            val read = raf.read(buffer, 0, toRead)
            if (read == -1) break
            outRaf.write(buffer, 0, read)
            bytesToCopy -= read
        }

        // Skip the removed segment
        raf.seek((44 + endSample * bytesPerSample).toLong())

        // Copy remainder (endSample to totalSamples)
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
        return outputFile
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}

private fun writeIntToByteArray(b: ByteArray, offset: Int, value: Int) {
    b[offset] = (value and 0xFF).toByte()
    b[offset + 1] = ((value shr 8) and 0xFF).toByte()
    b[offset + 2] = ((value shr 16) and 0xFF).toByte()
    b[offset + 3] = ((value shr 24) and 0xFF).toByte()
}

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
