// RecordAudioScreenWithImport.kt
package com.example.mobileappstrusted.screens


import android.Manifest
import android.content.ContentResolver
import android.content.Context
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
import com.example.mobileappstrusted.audio.WavUtils.writeWavFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

@Composable
fun RecordAudioScreen(onRecordingComplete: (String) -> Unit) {
    val context = LocalContext.current

    var isRecording by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Press to start recording or import an audio file") }
    var recordingThread: Thread? by remember { mutableStateOf(null) }

    var finishedFilePath by remember { mutableStateOf<String?>(null) }

    // Request RECORD_AUDIO permission
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        statusText = if (granted) "Ready to record." else "Permission denied."
    }
    LaunchedEffect(Unit) {
        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // Launcher for picking an existing audio file (MIME "audio/*")
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
                finishedFilePath = importedFile.absolutePath
            } else {
                statusText = "Failed to import file."
            }
        }
    }

    val startRecording = {
        val outputFile = File(
            context.externalCacheDir ?: context.cacheDir,
            "record_${System.currentTimeMillis()}.wav"
        )
        val sampleRate = 44100
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        val audioData = mutableListOf<Byte>()
        isRecording = true
        statusText = "Recording..."
        audioRecord.startRecording()

        val thread = Thread {
            val buffer = ByteArray(bufferSize)
            while (isRecording) {
                val read = audioRecord.read(buffer, 0, buffer.size)
                if (read > 0) {
                    audioData.addAll(buffer.copyOf(read).toList())
                }
            }
            audioRecord.stop()
            audioRecord.release()

            val wavBytes = audioData.toByteArray()
            writeWavFile(wavBytes, outputFile, sampleRate, 1, 16)
            finishedFilePath = outputFile.absolutePath
        }
        thread.start()
        recordingThread = thread
    }

    val stopRecording = {
        isRecording = false
        statusText = "Recording stopped"
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

            if (finishedFilePath != null) {
                Text(
                    text = "Recording done",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(onClick = {
                        // Discard: reset everything
                        finishedFilePath = null
                        statusText = "Press to start recording or import an audio file"
                    }) {
                        Text("Discard")
                    }
                    Button(onClick = {
                        onRecordingComplete(finishedFilePath!!)
                    }) {
                        Text("Go to Edit")
                    }
                }
            } else {
                Button(
                    onClick = {
                        if (!isRecording) startRecording() else stopRecording()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isRecording) "Stop Recording" else "Start Recording")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        importAudioLauncher.launch(arrayOf("audio/*"))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Import Audio from Filesystem")
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

