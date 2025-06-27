// RecordAudioScreenWithImport.kt
package com.example.mobileappstrusted.screens


import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
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
import com.example.mobileappstrusted.audio.WavUtils.extractAmplitudesFromWav
import com.example.mobileappstrusted.audio.WavUtils.writeWavFile
import com.example.mobileappstrusted.components.WaveformView
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

@SuppressLint("MissingPermission")
@Composable
fun RecordAudioScreen(onRecordingComplete: (String) -> Unit) {
    val context = LocalContext.current

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

    val importAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val importedFile = copyUriToCache(
                context = context,
                uri = it,
                prefix = "imported_",
                suffix = getFileExtensionFromUri(context.contentResolver, it) ?: ".wav"
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

    val startRecording = {
        isRecording = true
        hasStoppedRecording = false
        isImported = false
        statusText = "Recording..."
        audioRecord.startRecording()

        val thread = Thread {
            val buffer = ByteArray(bufferSize)
            while (isRecording) {
                val read = audioRecord.read(buffer, 0, buffer.size)
                if (read > 0) {
                    synchronized(recordedChunks) {
                        recordedChunks.addAll(buffer.copyOf(read).toList())
                    }
                }
            }
            audioRecord.stop()
        }
        thread.start()
    }

    val stopRecording = {
        isRecording = false
        hasStoppedRecording = true
        isImported = false
        statusText = "Recording stopped"

        val pcm = synchronized(recordedChunks) {
            recordedChunks.toByteArray()
        }

        val tempWavFile = File.createTempFile("preview", ".wav", context.cacheDir)
        writeWavFile(pcm, tempWavFile, sampleRate, 1, 16)
        amplitudes = extractAmplitudesFromWav(tempWavFile)
        lastTempFile = tempWavFile
    }

    val discardAudio = {
        isRecording = false
        hasStoppedRecording = false
        isImported = false
        recordedChunks.clear()
        amplitudes = emptyList()
        lastTempFile = null
        isPlaying = false
        statusText = "Press to start recording or import an audio file"
        mediaPlayer.reset()
    }

    val resumeRecording = {
        startRecording()
    }

    val finishRecordingAndGoToEdit = {
        if (recordedChunks.isEmpty() && lastTempFile != null) {
            // Imported file
            onRecordingComplete(lastTempFile!!.absolutePath)
        } else {
            val outputFile = File(
                context.externalCacheDir ?: context.cacheDir,
                "record_${System.currentTimeMillis()}.wav"
            )
            val finalBytes = synchronized(recordedChunks) {
                recordedChunks.toByteArray()
            }
            writeWavFile(finalBytes, outputFile, sampleRate, 1, 16)
            onRecordingComplete(outputFile.absolutePath)
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
                WaveformView(amplitudes)
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (!hasStoppedRecording) {
                // Show both buttons in initial state or while recording
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Button(
                        onClick = {
                            if (isRecording) stopRecording() else startRecording()
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
                                mediaPlayer.setOnCompletionListener {
                                    isPlaying = false
                                }
                            } catch (e: Exception) {
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
                }

                Button(
                    onClick = { discardAudio() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text("Discard Audio")
                }

                if (!isImported) {
                    Button(
                        onClick = { resumeRecording() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text("Resume Recording")
                    }
                }

                Button(
                    onClick = { finishRecordingAndGoToEdit() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text("Go to Edit")
                }
            }
        }
    }
}

/**
 * Copies a content URI to the app's cache directory, using [prefix] + timestamp + [suffix].
 * Returns the File, or null if failure.
 */
fun copyUriToCache(
    context: Context,
    uri: Uri,
    prefix: String,
    suffix: String
): File? {
    return try {
        val cacheDir = context.externalCacheDir ?: context.cacheDir
        val destFile = File(cacheDir, prefix + System.currentTimeMillis() + suffix)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                val buf = ByteArray(4096)
                var bytesRead: Int
                while (input.read(buf).also { bytesRead = it } != -1) {
                    output.write(buf, 0, bytesRead)
                }
                output.flush()
            }
        }
        destFile
    } catch (e: IOException) {
        e.printStackTrace()
        null
    }
}

/**
 * Attempts to infer a file extension (e.g. ".wav", ".mp3") from the URI's mime type or path.
 */
fun getFileExtensionFromUri(resolver: ContentResolver, uri: Uri): String? {
    val mimeType = resolver.getType(uri)
    if (mimeType != null) {
        when (mimeType) {
            "audio/wav", "audio/x-wav" -> return ".wav"
            "audio/mpeg" -> return ".mp3"
            "audio/mp4", "audio/aac", "audio/x-m4a" -> return ".m4a"
            "audio/ogg" -> return ".ogg"
            "audio/flac" -> return ".flac"
        }
    }
    // Fallback: get extension from URI path
    val path = uri.path ?: return null
    val lastDot = path.lastIndexOf('.')
    if (lastDot != -1 && lastDot < path.length - 1) {
        return path.substring(lastDot)
    }
    return null
}

