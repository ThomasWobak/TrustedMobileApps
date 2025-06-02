// EditAudioScreen.kt
package com.example.mobileappstrusted.screens

import android.media.MediaPlayer
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.mobileappstrusted.audio.MerkleHasher
import com.example.mobileappstrusted.audio.ORIGINAL_MERKLE_ROOT_HASH_CHUNK_IDENTIFIER
import com.example.mobileappstrusted.components.WaveformView
import com.example.mobileappstrusted.components.NoPathGivenScreen
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

@Composable
fun EditAudioScreen(filePath: String) {
    if (filePath.isBlank()) {
        NoPathGivenScreen()
        return
    }

    var currentFilePath by remember { mutableStateOf(filePath) }
    var amplitudes by remember { mutableStateOf<List<Int>>(emptyList()) }
    val mediaPlayer = remember { MediaPlayer() }
    var isPlaying by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    var isOriginal by remember { mutableStateOf<Boolean?>(null) }

    // Cut controls
    var removeStartText by remember { mutableStateOf("") }
    var removeEndText by remember { mutableStateOf("") }
    var removeError by remember { mutableStateOf<String?>(null) }

    // Move controls
    var moveStartText by remember { mutableStateOf("") }
    var moveEndText by remember { mutableStateOf("") }
    var moveDestText by remember { mutableStateOf("") }
    var moveError by remember { mutableStateOf<String?>(null) }

    val isWav = currentFilePath.lowercase().endsWith(".wav")

    LaunchedEffect(currentFilePath) {
        val file = File(currentFilePath)
        amplitudes = if (file.exists() && isWav) {
            extractAmplitudesFromWav(file)
        } else {
            emptyList()
        }
        isOriginal = if (file.exists() && isWav) {
            MerkleHasher.verifyWavMerkleRoot(file)
        } else null

        mediaPlayer.reset()
        try {
            mediaPlayer.setDataSource(currentFilePath)
            mediaPlayer.prepare()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    DisposableEffect(mediaPlayer) {
        onDispose { mediaPlayer.release() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        when (isOriginal) {
            true -> Text("✔️ Original", color = MaterialTheme.colorScheme.primary)
            false -> Text("❌ Not original", color = MaterialTheme.colorScheme.error)
            null -> {} // Do nothing while loading
        }

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
            modifier = Modifier.fillMaxWidth()
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

        Spacer(modifier = Modifier.height(24.dp))

        if (!isWav) {
            Text(
                text = "Editing features only support WAV files.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            // Cut controls
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = removeStartText,
                    onValueChange = { removeStartText = it },
                    label = { Text("Remove Start (sec)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() }
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = removeEndText,
                    onValueChange = { removeEndText = it },
                    label = { Text("Remove End (sec)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() }
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        removeError = null
                        val startSec = removeStartText.toFloatOrNull()
                        val endSec = removeEndText.toFloatOrNull()
                        if (startSec == null || endSec == null || startSec < 0f || endSec <= startSec) {
                            removeError = "Invalid start/end"
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

            // Move controls
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = moveStartText,
                    onValueChange = { moveStartText = it },
                    label = { Text("Move Start (sec)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.clearFocus() }
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = moveEndText,
                    onValueChange = { moveEndText = it },
                    label = { Text("Move End (sec)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.clearFocus() }
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = moveDestText,
                    onValueChange = { moveDestText = it },
                    label = { Text("Insert After (sec)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() }
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        moveError = null
                        val startSec = moveStartText.toFloatOrNull()
                        val endSec = moveEndText.toFloatOrNull()
                        val destSec = moveDestText.toFloatOrNull()
                        if (startSec == null || endSec == null || destSec == null ||
                            startSec < 0f || endSec <= startSec || destSec < 0f
                        ) {
                            moveError = "Invalid values"
                        } else {
                            val movedFile =
                                moveWavSegment(currentFilePath, startSec, endSec, destSec)
                            if (movedFile != null) {
                                currentFilePath = movedFile.absolutePath
                                moveStartText = ""
                                moveEndText = ""
                                moveDestText = ""
                            } else {
                                moveError = "Move failed"
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Move Segment")
                }
                moveError?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

// ----- Helper: cutting WAV files -----
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

        val startSample =
            (startSec * sampleRate).toInt().coerceIn(0, totalSamples)
        val endSample =
            (endSec * sampleRate).toInt().coerceIn(startSample, totalSamples)
        val samplesBefore = startSample
        val samplesAfter = totalSamples - endSample
        val newDataSize = (samplesBefore + samplesAfter) * bytesPerSample
        val newTotalDataLen = newDataSize + 36

        val outputFile =
            File(inputFile.parentFile, "cut_${System.currentTimeMillis()}.wav")
        val outRaf = RandomAccessFile(outputFile, "rw")

        val newHeader = header.copyOf()
        writeIntLE(newHeader, 4, newTotalDataLen)
        writeIntLE(newHeader, 40, newDataSize)
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

// ----- Helper: moving WAV segment -----
private data class WavInfo(
    val sampleRate: Int,
    val channels: Int,
    val bitDepth: Int,
    val dataOffset: Long,
    val dataSize: Int
)

private fun parseWavHeader(raf: RandomAccessFile): WavInfo? {
    raf.seek(0)
    val riffHeader = ByteArray(12)
    raf.readFully(riffHeader)
    if (!(riffHeader[0] == 'R'.code.toByte() && riffHeader[1] == 'I'.code.toByte() &&
                riffHeader[2] == 'F'.code.toByte() && riffHeader[3] == 'F'.code.toByte() &&
                riffHeader[8] == 'W'.code.toByte() && riffHeader[9] == 'A'.code.toByte() &&
                riffHeader[10] == 'V'.code.toByte() && riffHeader[11] == 'E'.code.toByte())
    ) return null

    var sampleRate = 0
    var channels = 0
    var bitDepth = 0
    var dataOffset: Long = -1
    var dataSize = 0

    while (true) {
        val chunkHeader = ByteArray(8)
        if (raf.read(chunkHeader) < 8) break
        val chunkId = String(chunkHeader, 0, 4)
        val chunkSize = ByteBuffer.wrap(chunkHeader, 4, 4)
            .order(ByteOrder.LITTLE_ENDIAN).int
        when (chunkId) {
            "fmt " -> {
                val fmtBytes = ByteArray(chunkSize)
                raf.readFully(fmtBytes)
                val fmtBuffer = ByteBuffer.wrap(fmtBytes).order(ByteOrder.LITTLE_ENDIAN)
                val audioFormat = fmtBuffer.short.toInt() and 0xFFFF
                if (audioFormat != 1) return null
                channels = fmtBuffer.short.toInt() and 0xFFFF
                sampleRate = fmtBuffer.int
                fmtBuffer.int // byteRate
                fmtBuffer.short // blockAlign
                bitDepth = fmtBuffer.short.toInt() and 0xFFFF
            }
            "data" -> {
                dataOffset = raf.filePointer
                dataSize = chunkSize
                break
            }
            else -> {
                raf.seek(raf.filePointer + chunkSize)
            }
        }
    }
    return if (dataOffset >= 0) WavInfo(sampleRate, channels, bitDepth, dataOffset, dataSize) else null
}

fun moveWavSegment(inputPath: String, startSec: Float, endSec: Float, destSec: Float): File? {
    return try {
        val inputFile = File(inputPath)
        val raf = RandomAccessFile(inputFile, "r")
        val info = parseWavHeader(raf) ?: run { raf.close(); return null }
        if (info.channels != 1 || info.sampleRate != 44100 || info.bitDepth != 16) {
            raf.close()
            return null
        }

        val bytesPerSample = info.bitDepth / 8
        val totalSamples = info.dataSize / bytesPerSample
        val startSample = (startSec * info.sampleRate).toInt().coerceIn(0, totalSamples)
        val endSample = (endSec * info.sampleRate).toInt().coerceIn(startSample, totalSamples)
        val destSample = (destSec * info.sampleRate).toInt().coerceIn(0, totalSamples - (endSample - startSample))

        raf.seek(info.dataOffset)
        val allData = ByteArray(info.dataSize)
        raf.readFully(allData)

        fun getBytes(s: Int, e: Int): ByteArray {
            val bstart = s * bytesPerSample
            val bend = e * bytesPerSample
            return allData.copyOfRange(bstart, bend)
        }

        val bytesA = getBytes(0, startSample)
        val bytesB = getBytes(startSample, endSample)
        val bytesC = getBytes(endSample, totalSamples)

        val newData: ByteArray = if (destSample <= startSample) {
            val part1 = getBytes(0, destSample)
            val part2 = getBytes(destSample, startSample)
            part1 + bytesB + part2 + bytesC
        } else {
            val part1 = bytesA
            val part2 = getBytes(endSample, destSample)
            val part3 = getBytes(destSample, totalSamples)
            part1 + part2 + bytesB + part3
        }

        val newDataSize = newData.size
        val newTotalDataLen = newDataSize + 36

        val outputFile = File(inputFile.parentFile, "moved_${System.currentTimeMillis()}.wav")
        val outRaf = RandomAccessFile(outputFile, "rw")

        raf.seek(0)
        val headerBytes = ByteArray(info.dataOffset.toInt())
        raf.readFully(headerBytes)
        writeIntLE(headerBytes, 4, newTotalDataLen)
        writeIntLE(headerBytes, (info.dataOffset + 4).toInt(), newDataSize)
        outRaf.write(headerBytes)
        outRaf.write(newData)

        raf.close()
        outRaf.close()
        outputFile
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

// ----- Utility -----
private fun writeIntLE(b: ByteArray, offset: Int, value: Int) {
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

