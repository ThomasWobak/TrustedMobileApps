// EditAudioScreen.kt
package com.example.mobileappstrusted.screens

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.mobileappstrusted.components.WaveformView
import com.example.mobileappstrusted.components.NoPathGivenScreen
import com.example.mobileappstrusted.dataclass.WavBlock
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.min


private const val BLOCK_SIZE = 100 * 1024  // 100 KB per block, roughly 1.16 seconds

@Composable
fun EditAudioScreen(filePath: String) {
    val context = LocalContext.current
    if (filePath.isBlank()) {
        NoPathGivenScreen()
        return
    }

    var currentFilePath by remember { mutableStateOf(filePath) }
    var amplitudes by remember { mutableStateOf<List<Int>>(emptyList()) }
    val mediaPlayer = remember { MediaPlayer() }
    var isPlaying by remember { mutableStateOf(false) }

    // cut controls (unchanged)
    var removeStartText by remember { mutableStateOf("") }
    var removeEndText by remember { mutableStateOf("") }
    var removeError by remember { mutableStateOf<String?>(null) }

    // reorder controls
    var reorderFromText by remember { mutableStateOf("") }
    var reorderToText   by remember { mutableStateOf("") }
    var reorderError    by remember { mutableStateOf<String?>(null) }

    val isWav = currentFilePath.lowercase().endsWith(".wav")

    // header + blocks + playback temp file
    var wavHeader by remember { mutableStateOf<ByteArray?>(null) }
    var blocks    by remember { mutableStateOf<List<WavBlock>>(emptyList()) }
    var playbackFile by remember { mutableStateOf<File?>(null) }

    // 1) load amplitudes + initial mediaPlayer when path changes
    LaunchedEffect(currentFilePath) {
        val f = File(currentFilePath)
        amplitudes = if (f.exists() && isWav) extractAmplitudesFromWav(f) else emptyList()

        if (isWav) {
            val (hdr, blks) = splitWavIntoBlocks(f, BLOCK_SIZE)
            wavHeader = hdr
            blocks = blks
            playbackFile = writeBlocksToTempFile(context, hdr, blks)
        }

        mediaPlayer.reset()
        playbackFile?.let {
            mediaPlayer.setDataSource(it.absolutePath)
            mediaPlayer.prepare()
        }
    }

    // 2) whenever blocks reorder, rewrite playback + reload player
    LaunchedEffect(blocks) {
        val hdr = wavHeader ?: return@LaunchedEffect
        playbackFile = writeBlocksToTempFile(context, hdr, blocks)
        mediaPlayer.reset()
        playbackFile?.let {
            mediaPlayer.setDataSource(it.absolutePath)
            mediaPlayer.prepare()
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
        // play controls
        Text("Edit Audio", style = MaterialTheme.typography.headlineMedium)
        Text("Loaded: ${File(currentFilePath).name}", style = MaterialTheme.typography.bodyLarge)
        Row(Modifier.fillMaxWidth()) {
            Button({
                if (!isPlaying) {
                    mediaPlayer.start(); isPlaying = true
                    mediaPlayer.setOnCompletionListener { isPlaying = false }
                } else {
                    mediaPlayer.pause(); isPlaying = false
                }
            }) { Text(if (isPlaying) "Pause" else "Play") }
        }
        Spacer(Modifier.height(24.dp))

        // waveform
        if (amplitudes.isNotEmpty()) WaveformView(amplitudes)
        else if (isWav) Text("Loading waveform…", style = MaterialTheme.typography.bodyMedium)
        else Text("Cannot display waveform for non-WAV file.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))

        if (!isWav) {
            Text("Editing features only support WAV files.", color = MaterialTheme.colorScheme.error)
        } else {
            // cut controls (unchanged)
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = removeStartText,
                    onValueChange = { removeStartText = it },
                    label = { Text("Remove Start (sec)") },
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = removeEndText,
                    onValueChange = { removeEndText = it },
                    label = { Text("Remove End (sec)") },
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    removeError = null
                    val s = removeStartText.toFloatOrNull()
                    val e = removeEndText.toFloatOrNull()
                    if (s==null||e==null||s<0f||e<=s) removeError="Invalid"
                    else {
                        val cf = cutWavFile(currentFilePath,s,e)
                        if(cf!=null){ currentFilePath=cf.absolutePath; removeStartText=""; removeEndText="" }
                        else removeError="Cut failed"
                    }
                }, Modifier.align(Alignment.End)) {
                    Text("Remove Segment")
                }
                removeError?.let { Text(it, color=MaterialTheme.colorScheme.error) }
            }
            Spacer(Modifier.height(24.dp))

            // reorder controls
            Text("Reorder Blocks", style=MaterialTheme.typography.headlineSmall)
            Text(
                "Order: " + blocks.sortedBy { it.currentIndex }
                    .joinToString(",") { it.originalIndex.toString() }
            )
            OutlinedTextField(
                value = reorderFromText,
                onValueChange = { reorderFromText = it },
                label = { Text("Move block # (orig index)") },
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = reorderToText,
                onValueChange = { reorderToText = it },
                label = { Text("Insert at position (0-based)") },
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                reorderError = null
                val fromIdx = reorderFromText.toIntOrNull()
                val toPos   = reorderToText.toIntOrNull()
                if (fromIdx==null||toPos==null) reorderError="Invalid"
                else {
                    val sorted = blocks.sortedBy { it.currentIndex }.toMutableList()
                    val rem = sorted.indexOfFirst { it.originalIndex==fromIdx }
                    if (rem<0||toPos<0||toPos>sorted.size) reorderError="Out of range"
                    else {
                        val b = sorted.removeAt(rem)
                        sorted.add(min(toPos, sorted.size), b)
                        sorted.forEachIndexed{ i, blk-> blk.currentIndex=i }
                        blocks = sorted
                        reorderFromText=""; reorderToText=""
                    }
                }
            }, Modifier.align(Alignment.End)) {
                Text("Apply Reorder")
            }
            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {

                    // reset every block to its original position
                    blocks = blocks
                        .map { it.apply { currentIndex = originalIndex } }
                        .sortedBy { it.currentIndex }
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Reset to Original Order")
            }
            reorderError?.let { Text(it, color=MaterialTheme.colorScheme.error) }
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



// ----- Utility -----

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
/**
 * Reads the WAV header (first 44 bytes) and splits the data chunk into equal blocks.
 * Returns Pair<headerBytes, listOf blocks>.
 */
fun splitWavIntoBlocks(file: File, blockSize: Int): Pair<ByteArray, List<WavBlock>> {
    val all = file.readBytes()
    require(all.size > 44)
    val header = all.copyOfRange(0, 44)
    val data = all.copyOfRange(44, all.size)
    val count = (data.size + blockSize - 1) / blockSize
    val blocks = List(count) { idx ->
        val start = idx * blockSize
        val end = min(start + blockSize, data.size)
        WavBlock(originalIndex = idx, currentIndex = idx, data = data.copyOfRange(start, end))
    }
    return header to blocks
}

/**
 * Rewrites a temp WAV file by concatenating blocks in order of currentIndex.
 */
fun writeBlocksToTempFile(
    context: Context,
    header: ByteArray,
    blocks: List<WavBlock>
): File {
    // choose cache directory from the passed context
    val tempDir = context.externalCacheDir ?: context.cacheDir
    val outFile = File.createTempFile("reorder_", ".wav", tempDir)

    // sort & merge
    val body = blocks.sortedBy { it.currentIndex }
        .fold(ByteArray(0)) { acc, b -> acc + b.data }

    // patch header lengths
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
    b[offset+1] = ((v shr 8) and 0xFF).toByte()
    b[offset+2] = ((v shr 16) and 0xFF).toByte()
    b[offset+3] = ((v shr 24) and 0xFF).toByte()
}

