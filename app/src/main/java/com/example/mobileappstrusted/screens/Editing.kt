package com.example.mobileappstrusted.screens

import android.content.ContentValues
import android.media.MediaPlayer
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.mobileappstrusted.audio.InputStreamReader.splitWavIntoBlocks
import com.example.mobileappstrusted.audio.WavUtils.extractAmplitudesFromWav

import com.example.mobileappstrusted.audio.EditScriptUtils.extractEditHistoryFromWav
import com.example.mobileappstrusted.audio.MetadataCollector.extractMetaDataFromWav
import com.example.mobileappstrusted.audio.EditScriptUtils.getDeviceId
import com.example.mobileappstrusted.audio.EditScriptUtils.getDeviceName
import com.example.mobileappstrusted.audio.WavUtils.writeBlocksToTempFile
import com.example.mobileappstrusted.audio.WavUtils.writeWavFileToPersistentStorage
import com.example.mobileappstrusted.components.NoPathGivenScreen
import com.example.mobileappstrusted.components.WaveformViewEditing
import com.example.mobileappstrusted.cryptography.MerkleHasher
import com.example.mobileappstrusted.protobuf.EditHistoryProto
import com.example.mobileappstrusted.protobuf.RecordingMetadataProto
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

    //Edit History
    val editHistoryEntries = remember { mutableStateListOf<EditHistoryProto.EditHistoryEntry>() }
    var metaData by remember {
        mutableStateOf(RecordingMetadataProto.RecordingMetadata.newBuilder().build())
    }




    val currentFilePath by remember { mutableStateOf(filePath) }
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

    //highlight selected Block
    var selectedBlockIndices by remember { mutableStateOf<Set<Int>>(emptySet()) }

    val visibleBlocks = remember(blocks, deletedBlockIndices) {
        blocks
            .filterNot { deletedBlockIndices.contains(it.originalIndex) }
            .sortedBy { it.currentIndex }
    }
    var maxAmplitude by remember { mutableStateOf(1) }

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
            maxAmplitude = amplitudes.maxOrNull()?.coerceAtLeast(1) ?: 1

            extractEditHistoryFromWav(f)?.let { history ->
                editHistoryEntries.clear()
                editHistoryEntries.addAll(history.entriesList)
            }
            extractMetaDataFromWav(f)?.let { metadata ->
                metaData = metadata
            }
        } else {
            amplitudes = emptyList()
            maxAmplitude = amplitudes.maxOrNull()?.coerceAtLeast(1) ?: 1

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


    var selectedAmplitudes by remember { mutableStateOf<List<Int>>(emptyList()) }

    LaunchedEffect(selectedBlockIndices, blocks, deletedBlockIndices) {
        val hdr = wavHeader ?: return@LaunchedEffect
        val selectedBlocks = blocks
            .filterNot { deletedBlockIndices.contains(it.originalIndex) }
            .filter { selectedBlockIndices.contains(it.currentIndex) }

        if (selectedBlocks.isNotEmpty()) {
            val tempFile = writeBlocksToTempFile(context, hdr, selectedBlocks)
            selectedAmplitudes = extractAmplitudesFromWav(tempFile)
        } else {
            selectedAmplitudes = emptyList()
        }
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


        val barWidthPx = with(LocalDensity.current) { 2.dp.toPx() }
        val spacePx = with(LocalDensity.current) { 1.dp.toPx() }
        val canvasWidth = LocalConfiguration.current.screenWidthDp.dp
        val canvasWidthPx = with(LocalDensity.current) { canvasWidth.toPx() }

        val totalBars = (canvasWidthPx / (barWidthPx + spacePx)).toInt().coerceAtLeast(1)

        // waveform
        if (amplitudes.isNotEmpty()) WaveformViewEditing (
            amplitudes = amplitudes,
            selectedVisualBlockIndices = selectedBlockIndices,
            visibleBlocks = visibleBlocks,
            maxAmplitude = maxAmplitude,
            onBarRangeSelect = { start, end ->
                val barsPerBlock = totalBars.toFloat() / visibleBlocks.size.coerceAtLeast(1)
                val blockIndices = (start..end).mapNotNull { bar ->
                    val blockIndex = (bar / barsPerBlock).toInt()
                    visibleBlocks.getOrNull(blockIndex)?.currentIndex
                }.toSet()

                selectedBlockIndices = blockIndices
                removeBlockText = blockIndices.sorted().joinToString(",")
            }
        )





        else if (isWav) Text("Loading waveform…", style = MaterialTheme.typography.bodyMedium)
        else Text("Cannot display waveform for non-WAV file.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))

        if (!isWav) {
            Text("Editing features only support WAV files.", color = MaterialTheme.colorScheme.error)
        } else {
            Text("Remove Block", style = MaterialTheme.typography.headlineSmall)

            OutlinedTextField(
                value = removeBlockText,
                onValueChange = { removeBlockText = it },
                label = { Text("Remove block # (original index)") },
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
            )
            Spacer(Modifier.height(8.dp))

            Button(onClick = {
                val indices = removeBlockText.split(",").mapNotNull { it.trim().toIntOrNull() }
                val invalid = indices.filter { idx ->
                    !blocks.any { it.originalIndex == idx } || deletedBlockIndices.contains(idx)
                }

                if (indices.isEmpty()) {
                    removeBlockError = "Invalid input"
                } else if (invalid.isNotEmpty()) {
                    removeBlockError = "Invalid or already deleted: ${invalid.joinToString(",")}"
                } else {
                    deletedBlockIndices = deletedBlockIndices + indices
                    removeBlockText = ""

                    indices.forEach { idx ->
                        val entry = EditHistoryProto.EditHistoryEntry.newBuilder()
                            .setUserId(getDeviceName())
                            .setDeviceId(getDeviceId(context))
                            .setTimestamp(System.currentTimeMillis())
                            .setChangeType(EditHistoryProto.ChangeType.DELETE_BLOCK)
                            .putDetails("blockIndex", idx.toString())
                            .build()
                        editHistoryEntries.add(entry)
                    }
                }


            }, modifier = Modifier.align(Alignment.End)) {
                Text("Mark as Deleted")
            }
            Spacer(Modifier.height(8.dp))
            removeBlockError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(24.dp))

            Text("Reorder Blocks", style = MaterialTheme.typography.headlineSmall)

            OutlinedTextField(
                value = reorderFromText,
                onValueChange = { reorderFromText = it },
                label = { Text("Move block # (current index, 0-based)") },
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
                if (fromIdx == null || toPos == null) {
                    reorderError = "Invalid"
                } else {
                    var sorted = blocks
                        .filterNot { deletedBlockIndices.contains(it.originalIndex) }
                        .sortedBy { it.currentIndex }
                        .toMutableList()

                    if (fromIdx !in sorted.indices || toPos !in 0..sorted.size) {
                        reorderError = "Out of range"
                    } else {
                        val block = sorted.removeAt(fromIdx)
                        sorted.add(min(toPos, sorted.size), block)

                        sorted = sorted.mapIndexed { i, blk ->
                            blk.toBuilder()
                                .setCurrentIndex(i)
                                .build()
                        }.toMutableList()

                        blocks = sorted
                        reorderFromText = ""
                        reorderToText = ""

                        val entry = EditHistoryProto.EditHistoryEntry.newBuilder()
                            .setUserId(getDeviceName())
                            .setDeviceId(getDeviceId(context))
                            .setTimestamp(System.currentTimeMillis())
                            .setChangeType(EditHistoryProto.ChangeType.REORDER_BLOCK)
                            .putDetails("Moved Block", "from $fromIdx to $toPos")
                            .build()
                        editHistoryEntries.add(entry)
                    }
                }
            }, Modifier.align(Alignment.End)) {
                Text("Apply Reorder")
            }
            Spacer(Modifier.height(8.dp))


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
                                put(
                                    MediaStore.MediaColumns.RELATIVE_PATH,
                                    Environment.DIRECTORY_MUSIC
                                )
                            }

                            val audioUri = resolver.insert(
                                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                contentValues
                            )

                            if (audioUri != null) {
                                resolver.openOutputStream(audioUri)?.use { outStream ->
                                    val merkleRoot = MerkleHasher.buildMerkleRoot(blocks)
                                    val editHistory = EditHistoryProto.EditHistory.newBuilder()
                                        .addAllEntries(editHistoryEntries)
                                        .build()


                                    writeWavFileToPersistentStorage(
                                        outStream,
                                        blocks,
                                        merkleRoot,
                                        editHistory,
                                        metaData
                                    )


                                    Toast.makeText(
                                        context,
                                        "Audio exported to Music/$fileName",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            } else {
                                Toast.makeText(
                                    context,
                                    "Failed to create export file",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                "Export failed: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                            e.printStackTrace()
                        }
                    }
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Export Audio")
            }
        }
        if (selectedAmplitudes.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text("Preview of Selected Blocks", style = MaterialTheme.typography.headlineSmall)
            WaveformViewEditing(
                amplitudes = selectedAmplitudes,
                selectedVisualBlockIndices = emptySet(),
                visibleBlocks = emptyList(), // not needed for this waveform
                maxAmplitude = maxAmplitude,
                onBarRangeSelect = { _, _ -> } // no-op for preview
            )
        }
    }
}
