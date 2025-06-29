package com.example.mobileappstrusted.screens

import android.media.MediaPlayer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth

import android.content.ContentValues
import android.provider.MediaStore
import android.os.Environment
import android.widget.Toast

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.mobileappstrusted.audio.WavUtils.extractAmplitudesFromWav
import com.example.mobileappstrusted.audio.WavUtils.splitWavIntoBlocks
import com.example.mobileappstrusted.audio.WavUtils.writeBlocksToTempFile
import com.example.mobileappstrusted.audio.WavUtils.writeBlocksWithMerkleRoot
import com.example.mobileappstrusted.components.NoPathGivenScreen
import com.example.mobileappstrusted.components.WaveformView
import com.example.mobileappstrusted.cryptography.MerkleHasher
import com.example.mobileappstrusted.protobuf.WavBlockProtos
import java.io.File
import kotlin.math.min

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

    var isOriginal by remember { mutableStateOf<Boolean?>(null) }
    var verificationChecked by remember { mutableStateOf(false) }

    var reorderFromText by remember { mutableStateOf("") }
    var reorderToText by remember { mutableStateOf("") }
    var reorderError by remember { mutableStateOf<String?>(null) }

    var removeBlockText by remember { mutableStateOf("") }
    var removeBlockError by remember { mutableStateOf<String?>(null) }

    var deletedBlockIndices by remember { mutableStateOf<Set<Int>>(emptySet()) }

    val isWav = currentFilePath.lowercase().endsWith(".wav")

    // header + blocks + playback temp file
    var wavHeader by remember { mutableStateOf<ByteArray?>(null) }
    var blocks by remember { mutableStateOf<List<WavBlockProtos.WavBlock>>(emptyList()) }
    var playbackFile by remember { mutableStateOf<File?>(null) }

    // 1) load amplitudes + initial mediaPlayer when path changes
    LaunchedEffect(currentFilePath) {
        val f = File(currentFilePath)
        if (f.exists() && isWav) {
            val (hdr, blks) = splitWavIntoBlocks(f)
            wavHeader = hdr
            blocks = blks
            deletedBlockIndices = emptySet()
            val visibleBlocks = blks.filterNot { deletedBlockIndices.contains(it.originalIndex) }
            playbackFile = writeBlocksToTempFile(context, hdr, visibleBlocks)
            amplitudes = extractAmplitudesFromWav(playbackFile!!)
        } else {
            amplitudes = emptyList()
        }

        mediaPlayer.reset()
        playbackFile?.let {
            mediaPlayer.setDataSource(it.absolutePath)
            mediaPlayer.prepare()
        }

        if (!verificationChecked && f.exists() && isWav) {
            isOriginal = MerkleHasher.verifyWavMerkleRoot(f)
            verificationChecked = true
        }
    }

    LaunchedEffect(blocks, deletedBlockIndices) {
        val hdr = wavHeader ?: return@LaunchedEffect
        val visibleBlocks = blocks.filterNot { deletedBlockIndices.contains(it.originalIndex) }
        playbackFile = writeBlocksToTempFile(context, hdr, visibleBlocks)
        amplitudes = if (playbackFile != null) extractAmplitudesFromWav(playbackFile!!) else emptyList()
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
        when (isOriginal) {
            true -> Text("\u2714\uFE0F Verified", color = MaterialTheme.colorScheme.primary)
            false -> Text("\u274C Not Verified", color = MaterialTheme.colorScheme.error)
            null -> {}
        }

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
            }) {
                Text(if (isPlaying) "Pause" else "Play")
            }
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
            Text("Remove Block", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Visible Order: " + blocks
                    .filterNot { deletedBlockIndices.contains(it.originalIndex) }
                    .sortedBy { it.currentIndex }
                    .joinToString(",") { it.originalIndex.toString() }
            )

            OutlinedTextField(
                value = removeBlockText,
                onValueChange = { removeBlockText = it },
                label = { Text("Remove block # (original index)") },
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
            )
            Spacer(Modifier.height(8.dp))

            Button(onClick = {
                removeBlockError = null
                val indexToRemove = removeBlockText.toIntOrNull()
                if (indexToRemove == null) {
                    removeBlockError = "Invalid input"
                } else if (!blocks.any { it.originalIndex == indexToRemove }) {
                    removeBlockError = "Block not found"
                } else if (deletedBlockIndices.contains(indexToRemove)) {
                    removeBlockError = "Already deleted"
                } else {
                    deletedBlockIndices = deletedBlockIndices + indexToRemove
                    removeBlockText = ""
                }
            }, modifier = Modifier.align(Alignment.End)) {
                Text("Mark as Deleted")
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                deletedBlockIndices = emptySet()
            }, modifier = Modifier.align(Alignment.End)) {
                Text("Restore All")
            }
            removeBlockError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(24.dp))

            Text("Reorder Blocks", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Order: " + blocks
                    .filterNot { deletedBlockIndices.contains(it.originalIndex) }
                    .sortedBy { it.currentIndex }
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
                val toPos = reorderToText.toIntOrNull()
                if (fromIdx == null || toPos == null) reorderError = "Invalid"
                else {
                    var sorted = blocks
                        .filterNot { deletedBlockIndices.contains(it.originalIndex) }
                        .sortedBy { it.currentIndex }
                        .toMutableList()
                    val rem = sorted.indexOfFirst { it.originalIndex == fromIdx }
                    if (rem < 0 || toPos < 0 || toPos > sorted.size) reorderError = "Out of range"
                    else {
                        val b = sorted.removeAt(rem)
                        sorted.add(min(toPos, sorted.size), b)

                        sorted = sorted.mapIndexed { i, blk ->
                            blk.toBuilder()
                                .setCurrentIndex(i)
                                .build()
                        }.toMutableList()

                        blocks = sorted
                        reorderFromText = ""
                        reorderToText = ""
                    }
                }
            }, Modifier.align(Alignment.End)) {
                Text("Apply Reorder")
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                blocks = blocks
                    .map { it.toBuilder().setCurrentIndex(it.originalIndex).build() }
                    .sortedBy { it.currentIndex }
            }, modifier = Modifier.align(Alignment.End)) {
                Text("Reset to Original Order")
            }
            reorderError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Spacer(Modifier.height(16.dp))
            Text("Deleted Blocks: ${deletedBlockIndices.sorted().joinToString(", ")}")

            reorderError?.let { Text(it, color=MaterialTheme.colorScheme.error) }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val header = wavHeader
                    if (header != null) {
                        try {
                            val resolver = context.contentResolver
                            val fileName = "exported_audio_${System.currentTimeMillis()}.wav"

                            val contentValues = ContentValues().apply {
                                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                                put(MediaStore.MediaColumns.MIME_TYPE, "audio/wav")
                                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MUSIC)
                            }

                            val audioUri = resolver.insert(
                                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                contentValues
                            )

                            if (audioUri != null) {
                                resolver.openOutputStream(audioUri)?.use { outStream ->
                                    val merkleRoot = MerkleHasher.buildMerkleRoot(blocks)
                                    writeBlocksWithMerkleRoot(outStream, header, blocks, merkleRoot)
                                    Toast.makeText(context, "Audio exported to Music/$fileName", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                Toast.makeText(context, "Failed to create export file", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                            e.printStackTrace()
                        }
                    }
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Export Audio")
            }
        }
    }
}
