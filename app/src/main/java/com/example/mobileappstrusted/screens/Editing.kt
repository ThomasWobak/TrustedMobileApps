package com.example.mobileappstrusted.screens

import android.content.ContentValues
import android.media.MediaPlayer
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.example.mobileappstrusted.audio.EditScriptUtils.undoLastEdit
import com.example.mobileappstrusted.audio.InputStreamReader
import com.example.mobileappstrusted.audio.WavUtils.writeBlocksToTempFile
import com.example.mobileappstrusted.audio.WavUtils.writeWavFileToPersistentStorage
import com.example.mobileappstrusted.components.NoPathGivenScreen
import com.example.mobileappstrusted.components.WaveformViewEditing
import com.example.mobileappstrusted.cryptography.MerkleHasher
import com.example.mobileappstrusted.protobuf.EditHistoryProto
import com.example.mobileappstrusted.protobuf.RecordingMetadataProto
import com.example.mobileappstrusted.protobuf.WavBlockProtos
import java.io.File

@Composable
fun EditAudioScreen(filePath: String) {
    val context = LocalContext.current
    if (filePath.isBlank()) {
        NoPathGivenScreen()
        return
    }

    val editHistoryEntries = remember { mutableStateListOf<EditHistoryProto.EditHistoryEntry>() }
    var metaData by remember { mutableStateOf(RecordingMetadataProto.RecordingMetadata.newBuilder().build()) }
    val currentFilePath by remember { mutableStateOf(filePath) }
    var amplitudes by remember { mutableStateOf<List<Int>>(emptyList()) }
    val mediaPlayer = remember { MediaPlayer() }
    var isPlaying by remember { mutableStateOf(false) }
    var isOriginal by remember { mutableStateOf<Boolean?>(null) }
    var verificationChecked by remember { mutableStateOf(false) }

    var reorderText by remember { mutableStateOf("") }
    var insertAtText by remember { mutableStateOf("") }
    var editError by remember { mutableStateOf<String?>(null) }

    var deletedBlockIndices by remember { mutableStateOf<Set<Int>>(emptySet()) }
    val isWav = currentFilePath.lowercase().endsWith(".wav")
    var wavHeader by remember { mutableStateOf<ByteArray?>(null) }
    var blocks by remember { mutableStateOf<List<WavBlockProtos.WavBlock>>(emptyList()) }
    var playbackFile by remember { mutableStateOf<File?>(null) }
    var selectedBlockIndices by remember { mutableStateOf<Set<Int>>(emptySet()) }

    val visibleBlocks = remember(blocks, deletedBlockIndices) {
        blocks.filterNot { deletedBlockIndices.contains(it.originalIndex) }.sortedBy { it.currentIndex }
    }

    var maxAmplitude by remember { mutableStateOf(1) }
    var selectedPlaybackFile by remember { mutableStateOf<File?>(null) }

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
            maxAmplitude = 1
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

    val selectedPlayer = remember { MediaPlayer() }
    var isPlayingSelected by remember { mutableStateOf(false) }
    DisposableEffect(mediaPlayer) { onDispose { mediaPlayer.release() } }
    DisposableEffect(selectedPlayer) { onDispose { selectedPlayer.release() } }

    var selectedAmplitudes by remember { mutableStateOf<List<Int>>(emptyList()) }
    LaunchedEffect(selectedBlockIndices, blocks, deletedBlockIndices) {
        val hdr = wavHeader ?: return@LaunchedEffect
        val selectedBlocks = blocks.filterNot { deletedBlockIndices.contains(it.originalIndex) }
            .filter { selectedBlockIndices.contains(it.currentIndex) }
        if (selectedBlocks.isNotEmpty()) {
            val tempFile = writeBlocksToTempFile(context, hdr, selectedBlocks)
            selectedPlaybackFile = tempFile
            selectedAmplitudes = extractAmplitudesFromWav(tempFile)
        } else {
            selectedAmplitudes = emptyList()
        }
        selectedPlayer.reset()
        if (selectedBlocks.isNotEmpty()) {
            selectedPlaybackFile?.let {
                selectedPlayer.setDataSource(it.absolutePath)
                selectedPlayer.prepare()
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        when (isOriginal) {
            true -> Text("\u2714\uFE0F Verified", color = MaterialTheme.colorScheme.primary)
            false -> Text("\u274C Not Verified", color = MaterialTheme.colorScheme.error)
            null -> {}
        }

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

        val barWidthPx = with(LocalDensity.current) { 2.dp.toPx() }
        val spacePx = with(LocalDensity.current) { 1.dp.toPx() }
        val canvasWidth = LocalConfiguration.current.screenWidthDp.dp
        val canvasWidthPx = with(LocalDensity.current) { canvasWidth.toPx() }
        val totalBars = (canvasWidthPx / (barWidthPx + spacePx)).toInt().coerceAtLeast(1)

        if (amplitudes.isNotEmpty()) WaveformViewEditing(
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
                reorderText = blockIndices.sorted().joinToString(",")
            }
        )
        else if (isWav) Text("Loading waveform…", style = MaterialTheme.typography.bodyMedium)
        else Text("Cannot display waveform for non-WAV file.", style = MaterialTheme.typography.bodyMedium)

        Spacer(Modifier.height(24.dp))

        if (isWav) {
            Text("Selected Blocks", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = reorderText,
                onValueChange = { reorderText = it },
                label = { Text("Selected block indices (comma-separated)") },
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = insertAtText,
                onValueChange = { insertAtText = it },
                label = { Text("Insert at position (0-based)") },
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Button(onClick = {
                    val indices = reorderText.split(",").mapNotNull { it.trim().toIntOrNull() }
                    val invalid = indices.filter { idx ->
                        !blocks.any { it.originalIndex == idx } || deletedBlockIndices.contains(idx)
                    }
                    if (indices.isEmpty()) {
                        editError = "Invalid input"
                    } else if (invalid.isNotEmpty()) {
                        editError = "Invalid or already deleted: ${invalid.joinToString(",")}"
                    } else {
                        deletedBlockIndices = deletedBlockIndices + indices
                        reorderText = ""
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
                }) { Text("Mark as Deleted") }

                Button(onClick = {
                    val fromIndices = reorderText.split(",").mapNotNull { it.trim().toIntOrNull() }.distinct().sorted()
                    val toPos = insertAtText.toIntOrNull()
                    if (fromIndices.isEmpty() || toPos == null) {
                        editError = "Invalid"
                    } else {
                        var sorted = blocks.filterNot { deletedBlockIndices.contains(it.originalIndex) }
                            .sortedBy { it.currentIndex }.toMutableList()
                        if (fromIndices.any { it !in sorted.indices } || toPos !in 0..sorted.size) {
                            editError = "Out of range"
                        } else {
                            val blocksToMove = fromIndices.map { sorted[it] }
                            fromIndices.sortedDescending().forEach { sorted.removeAt(it) }
                            sorted.addAll(toPos.coerceAtMost(sorted.size), blocksToMove)
                            sorted = sorted.mapIndexed { i, blk ->
                                blk.toBuilder().setCurrentIndex(i).build()
                            }.toMutableList()
                            blocks = sorted
                            reorderText = ""
                            insertAtText = ""
                            blocksToMove.forEach { blk ->
                                val entry = EditHistoryProto.EditHistoryEntry.newBuilder()
                                    .setUserId(getDeviceName())
                                    .setDeviceId(getDeviceId(context))
                                    .setTimestamp(System.currentTimeMillis())
                                    .setChangeType(EditHistoryProto.ChangeType.REORDER_BLOCK)
                                    .putDetails("Moved Block", "to $toPos")
                                    .putDetails("Original Index", blk.currentIndex.toString())
                                    .build()
                                editHistoryEntries.add(entry)
                            }
                        }
                    }
                }) { Text("Apply Reorder") }
            }
            Spacer(Modifier.height(8.dp))
            editError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Button(
                    onClick = {
                        val (newBlocks, newDeleted, newHistory) = undoLastEdit(blocks, deletedBlockIndices, editHistory = EditHistoryProto.EditHistory.newBuilder()
                            .addAllEntries(editHistoryEntries)
                            .build())
                        blocks = newBlocks
                        deletedBlockIndices = newDeleted
                        editHistoryEntries.clear()
                        editHistoryEntries.addAll(newHistory.entriesList)


                        Toast.makeText(context, "Last state restored from edit history", Toast.LENGTH_SHORT).show()
                    },

                ) {
                    Text("Undo last edit")
                }

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

                ) {
                    Text("Export Audio")
                }
            }
        }

        if (selectedAmplitudes.isNotEmpty()) {

            Spacer(Modifier.height(24.dp))
            Text("Preview of Selected Blocks", style = MaterialTheme.typography.headlineSmall)
            val previewBlocks = visibleBlocks.filter { selectedBlockIndices.contains(it.currentIndex) }
            val previewStartTime = previewBlocks.minOfOrNull { it.currentIndex }?.times(
                InputStreamReader.BLOCK_TIME) ?: 0f

            WaveformViewEditing(
                amplitudes = selectedAmplitudes,
                selectedVisualBlockIndices = emptySet(),
                visibleBlocks = previewBlocks,
                maxAmplitude = maxAmplitude,
                startTimeOffsetSeconds = previewStartTime, // <-- NEW
                onBarRangeSelect = { _, _ -> }
            )


            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                if (!isPlayingSelected) {
                    selectedPlayer.start()
                    isPlayingSelected = true
                    selectedPlayer.setOnCompletionListener { isPlayingSelected = false }
                } else {
                    selectedPlayer.pause()
                    isPlayingSelected = false
                }
            }) {
                Text(if (isPlayingSelected) "Pause Selected" else "Play Selected")
            }
        }
    }
}
