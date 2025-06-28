// RecordAudioScreenWithImport.kt
package com.example.mobileappstrusted.screens


import android.Manifest
import android.annotation.SuppressLint
import android.media.MediaPlayer
import androidx.compose.runtime.DisposableEffect
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.mobileappstrusted.audio.RecordingUtils
import com.example.mobileappstrusted.audio.WavUtils.extractAmplitudesFromWav
import com.example.mobileappstrusted.components.WaveformView
import java.io.File

@SuppressLint("MissingPermission")
@Composable
fun RecordAudioScreen(onRecordingComplete: (String) -> Unit) {
    val context = LocalContext.current

    //fixes bottom navigation
    var shouldClearState by remember { mutableStateOf(false) }

    //Used for replaying the audio recorded
    val mediaPlayer = remember { MediaPlayer() }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer.release()
        }
    }


    //Used to resume recording functionality
    var hasStoppedRecording by remember { mutableStateOf(false) }
    val recordedChunks = remember { mutableListOf<Byte>() }
    var isPlaying by remember { mutableStateOf(false) }
    var lastTempFile by remember { mutableStateOf<File?>(null) }
    var isImported by remember { mutableStateOf(false) }

    var isRecording by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Press to start recording or import an audio file") }

    var amplitudes by remember { mutableStateOf<List<Int>>(emptyList()) }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        statusText = if (granted) "Ready to record." else "Permission denied."
    }
    LaunchedEffect(Unit) {
        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
    val sampleRate = 44100
    val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )


    val audioRecord = remember {
        AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
    }
    val utils = remember {
        RecordingUtils(
            context = context,
            audioRecord = audioRecord,
            sampleRate = sampleRate,
            recordedChunks = recordedChunks,
            mediaPlayer = mediaPlayer
        )
    }
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
                amplitudes = extractAmplitudesFromWav(importedFile)
                lastTempFile = importedFile
                hasStoppedRecording = true
                isRecording = false
                isImported=true
            } else {
                statusText = "Failed to import file."
            }
        }
    }




    DisposableEffect(Unit) {
        onDispose { mediaPlayer.release() }
    }



    LaunchedEffect(shouldClearState) {
        if (shouldClearState) {
            // Delay to let Edit screen read the path
            kotlinx.coroutines.delay(500)
            isRecording = false
            hasStoppedRecording = false
            isImported = false
            recordedChunks.clear()
            amplitudes = emptyList()
            lastTempFile = null
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
            Text(statusText, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 16.dp))

            if (hasStoppedRecording && amplitudes.isNotEmpty()) {
                WaveformView(amplitudes, onBarClick = { i: Int, i1: Int -> })
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (!hasStoppedRecording) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Button(
                        onClick = {
                            if (isRecording) {
                                utils.stopRecording(
                                    onUpdateStatus = { statusText = it },
                                    setIsRecording = { isRecording = it },
                                    setHasStoppedRecording = { hasStoppedRecording = it },
                                    setIsImported = { isImported = it },
                                    updateTempFile = { lastTempFile = it },
                                    updateAmplitudes = { amplitudes = it }
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
                        modifier = Modifier.weight(1f).padding(end = 4.dp)
                    ) {
                        Text(if (isRecording) "Stop Recording" else "Start Recording")
                    }

                    if (!isRecording) {
                        Button(
                            onClick = {
                                importAudioLauncher.launch(arrayOf("audio/*"))
                            },
                            modifier = Modifier.weight(1f).padding(start = 4.dp)
                        ) {
                            Text("Import Audio")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            if (hasStoppedRecording) {
                if (lastTempFile != null) {
                    Button(
                        onClick = {
                            try {
                                mediaPlayer.reset()
                                mediaPlayer.setDataSource(lastTempFile!!.absolutePath)
                                mediaPlayer.prepare()
                                mediaPlayer.start()
                                isPlaying = true
                                mediaPlayer.setOnCompletionListener { isPlaying = false }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                statusText = "Playback failed"
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text(if (isPlaying) "Playing..." else "Play Audio")
                    }
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
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Text("Discard Audio")
                }

                if (!isImported) {
                    Button(
                        onClick = {
                            utils.resumeRecording {
                                utils.startRecording(
                                    onUpdateStatus = { statusText = it },
                                    setIsRecording = { isRecording = it },
                                    setHasStoppedRecording = { hasStoppedRecording = it },
                                    setIsImported = { isImported = it }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text("Resume Recording")
                    }
                }

                Button(
                    onClick = {
                        utils.finishRecordingAndGoToEdit(
                            lastTempFile = lastTempFile,
                            onRecordingComplete = onRecordingComplete,
                            setShouldClearState = { shouldClearState = it }
                        )
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Text("Go to Edit")
                }
            }
        }
    }
}

