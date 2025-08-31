package com.example.mobileappstrusted.screens

import android.Manifest
import android.annotation.SuppressLint
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.mobileappstrusted.audio.InputStreamReader.splitWavIntoBlocks
import com.example.mobileappstrusted.audio.RecordingUtils
import com.example.mobileappstrusted.audio.WavUtils.extractAmplitudesFromWav
import com.example.mobileappstrusted.audio.WavUtils.writeBlocksToTempFile
import com.example.mobileappstrusted.components.WaveformViewEditing
import com.example.mobileappstrusted.cryptography.DigitalSignatureUtils
import com.example.mobileappstrusted.protobuf.WavBlockProtos
import java.io.File

@SuppressLint("MissingPermission")
@Composable
fun RecordAudioScreen(onRecordingComplete: (String) -> Unit) {
    val context = LocalContext.current

    // fixes bottom navigation
    var shouldClearState by remember { mutableStateOf(false) }

    // player
    val mediaPlayer = remember { MediaPlayer() }
    DisposableEffect(Unit) {
        onDispose { mediaPlayer.release() }
    }

    // recording/import state
    var hasStoppedRecording by remember { mutableStateOf(false) }
    val recordedChunks = remember { mutableListOf<Byte>() }
    var isPlaying by remember { mutableStateOf(false) }
    var lastTempFile by remember { mutableStateOf<File?>(null) }
    var isImported by remember { mutableStateOf(false) }

    var isRecording by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Press to start recording or import an audio file") }

    // waveform/amplitude state
    var amplitudes by remember { mutableStateOf<List<Int>>(emptyList()) }
    var wavHeader by remember { mutableStateOf<ByteArray?>(null) }
    var blocks by remember { mutableStateOf<List<WavBlockProtos.WavBlock>>(emptyList()) }
    var deletedBlockIndices by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var playbackFile by remember { mutableStateOf<File?>(null) } // visible-only WAV for preview/playback
    var maxAmplitude by remember { mutableStateOf(1) }


    fun regenerateWaveformFromVisibleBlocks(srcFile: File) {
        val (hdr, blks) = splitWavIntoBlocks(srcFile)
        wavHeader = hdr
        blocks = blks
        deletedBlockIndices = blks.filter { it.isDeleted }.map { it.originalIndex }.toSet()

        val visible = blks.filterNot {
            deletedBlockIndices.contains(it.originalIndex) || it.isDeleted || it.isEncrypted
        }.sortedBy { it.currentIndex }

        playbackFile = writeBlocksToTempFile(context, hdr, visible)

        // amplitudes from the filtered audio
        amplitudes = extractAmplitudesFromWav(playbackFile!!)
        maxAmplitude = amplitudes.maxOrNull()?.coerceAtLeast(1) ?: 1
    }

    // permission
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        statusText = if (granted) "Ready to record." else "Permission denied."
    }
    LaunchedEffect(Unit) {
        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    val sampleRate = 44100
    val utils = remember {
        RecordingUtils(
            context = context,
            sampleRate = sampleRate,
            recordedChunks = recordedChunks,
            mediaPlayer = mediaPlayer
        )
    }

    // import
    val importAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val importedFile = utils.copyUriToCache(
                context = context,
                uri = it,
                prefix = "imported_",
                suffix = utils.getFileExtensionFromUri(context.contentResolver, it) ?: ".wav"
            )
            if (importedFile != null) {
                statusText = "Imported: ${importedFile.name}"
                lastTempFile = importedFile
                hasStoppedRecording = true
                isRecording = false
                isImported = true

                regenerateWaveformFromVisibleBlocks(importedFile)
            } else {
                statusText = "Failed to import file."
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            utils.discardAudio(
                setIsRecording = { isRecording = it },
                setHasStoppedRecording = { hasStoppedRecording = it },
                setIsImported = { isImported = it },
                updateStatus = { statusText = it },
                updateTempFile = { lastTempFile = it },
                updateAmplitudes = { amplitudes = it },
                setIsPlaying = { isPlaying = it }
            )
            mediaPlayer.release()
        }
    }

    // clear UI state after navigation to edit/debug
    LaunchedEffect(shouldClearState) {
        if (shouldClearState) {
            kotlinx.coroutines.delay(500)
            isRecording = false
            hasStoppedRecording = false
            isImported = false
            recordedChunks.clear()
            amplitudes = emptyList()
            lastTempFile = null
            playbackFile = null
            blocks = emptyList()
            deletedBlockIndices = emptySet()
            isPlaying = false
            statusText = "Press to start recording or import an audio file"
            mediaPlayer.reset()
            shouldClearState = false
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                statusText,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (hasStoppedRecording && amplitudes.isNotEmpty()) {
                val visibleBlocks = blocks
                    .filterNot {
                        deletedBlockIndices.contains(it.originalIndex) || it.isDeleted || it.isEncrypted
                    }
                    .sortedBy { it.currentIndex }

                WaveformViewEditing(
                    amplitudes = amplitudes,
                    selectedVisualBlockIndices = emptySet(),
                    visibleBlocks = visibleBlocks,
                    maxAmplitude = maxAmplitude,
                    onBarRangeSelect = { _, _ -> } // no-op
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (!hasStoppedRecording) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = {
                            if (isRecording) {
                                utils.stopRecording(
                                    onUpdateStatus = { statusText = it },
                                    setIsRecording = { isRecording = it },
                                    setHasStoppedRecording = { hasStoppedRecording = it },
                                    setIsImported = { isImported = it },
                                    updateAmplitudes = { },
                                    updateTempFile = { f ->
                                        lastTempFile = f
                                        f?.let { regenerateWaveformFromVisibleBlocks(it) }
                                    }
                                )
                            } else {
                                utils.startRecording(
                                    onUpdateStatus = { statusText = it },
                                    setIsRecording = { isRecording = it },
                                    setHasStoppedRecording = { hasStoppedRecording = it },
                                    setIsImported = { isImported = it }
                                )
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 4.dp)
                    ) {
                        Text(if (isRecording) "Stop Recording" else "Start Recording")
                    }

                    if (!isRecording) {
                        Button(
                            onClick = { importAudioLauncher.launch(arrayOf("audio/*")) },
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 4.dp)
                        ) {
                            Text("Import Audio")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            if (hasStoppedRecording) {
                Button(
                    onClick = {
                        try {
                            mediaPlayer.reset()

                            val src = playbackFile ?: lastTempFile
                            val playFile = src?.let { utils.convertToRawWavForPlayback(context, it) } ?: src

                            requireNotNull(playFile) { "No audio available for playback" }

                            mediaPlayer.setDataSource(playFile.absolutePath)
                            mediaPlayer.prepare()
                            mediaPlayer.start()
                            isPlaying = true
                            mediaPlayer.setOnCompletionListener { isPlaying = false }
                        } catch (e: Exception) {
                            Log.e("Recording", e.stackTraceToString())
                            e.printStackTrace()
                            statusText = "Playback failed"
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text(if (isPlaying) "Playing..." else "Play Audio")
                }

                Button(
                    onClick = {
                        utils.discardAudio(
                            setIsRecording = { isRecording = it },
                            setHasStoppedRecording = { hasStoppedRecording = it },
                            setIsImported = { isImported = it },
                            updateStatus = { statusText = it },
                            updateTempFile = { lastTempFile = it },
                            updateAmplitudes = { amplitudes = it },
                            setIsPlaying = { isPlaying = it }
                        )
                        // also clear filtered preview
                        playbackFile = null
                        blocks = emptyList()
                        deletedBlockIndices = emptySet()
                        maxAmplitude = 1
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text("Discard Audio")
                }

                if (!isImported) {
                    Button(
                        onClick = {
                            utils.startRecording(
                                onUpdateStatus = { statusText = it },
                                setIsRecording = { isRecording = it },
                                setHasStoppedRecording = { hasStoppedRecording = it },
                                setIsImported = { isImported = it }
                            )
                            // clear previous preview
                            amplitudes = emptyList()
                            playbackFile = null
                            blocks = emptyList()
                            deletedBlockIndices = emptySet()
                            maxAmplitude = 1
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text("Resume Recording")
                    }
                }

                Button(
                    onClick = {
                        if( DigitalSignatureUtils.isPrivateKeyStored(context)) {
                            utils.finishRecordingAndGoToEdit(
                                lastTempFile = lastTempFile,
                                onRecordingComplete = onRecordingComplete,
                                setShouldClearState = { shouldClearState = it }
                            )
                        }
                        else {
                            Toast.makeText(context, "Provide a private key before editing recordings.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text("Go to Edit")
                }

                Button(
                    onClick = {
                        lastTempFile?.let { file ->
                            onRecordingComplete("debug:${file.absolutePath}")
                        }
                        shouldClearState = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text("Go to Debug")
                }
            }
        }
    }
}
