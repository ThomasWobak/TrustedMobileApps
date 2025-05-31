// RecordAudioActivity.kt
package com.example.mobileappstrusted

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.mobileappstrusted.ui.theme.MobileAppsTrustedTheme
import java.io.*

class RecordAudioActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MobileAppsTrustedTheme {
                RecordAudioScreen { filePath ->
                    // Whenever recording is finished OR an audio file is imported,
                    // we launch EditAudioActivity with the absolute file path.
                    val intent = Intent(this, EditAudioActivity::class.java)
                    intent.putExtra("filePath", filePath)
                    startActivity(intent)
                }
            }
        }
    }
}

@Composable
fun RecordAudioScreen(onRecordingComplete: (String) -> Unit) {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Press to start recording") }
    var recordingThread: Thread? by remember { mutableStateOf(null) }

    // Request RECORD_AUDIO permission at startup
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            statusText = if (granted) {
                "Permission granted. Ready to record."
            } else {
                "Permission denied. Cannot record."
            }
        }
    )

    LaunchedEffect(Unit) {
        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // === Launcher for importing an existing audio file ===
    val importAudioLauncher = rememberLauncherForActivityResult(
        contract = OpenDocument(),
        onResult = { uri: Uri? ->
            uri?.let {
                val importedFile = copyUriToCache(
                    context = context,
                    uri = it,
                    prefix = "imported_",
                    suffix = getFileExtensionFromUri(context.contentResolver, it) ?: ".wav"
                )
                if (importedFile != null) {
                    statusText = "Imported: ${importedFile.name}"
                    onRecordingComplete(importedFile.absolutePath)
                } else {
                    statusText = "Failed to import file."
                }
            }
        }
    )

    // Lambda to start recording from the microphone and write to a WAV in cache
    val startRecording = {
        val outputFile = File(
            context.externalCacheDir,
            "recording_${System.currentTimeMillis()}.wav"
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
            onRecordingComplete(outputFile.absolutePath)
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
                text = statusText,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Button(
                onClick = {
                    if (!isRecording) startRecording()
                    else stopRecording()
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

/**
 * Copies the content pointed to by [uri] into a new file in the app's cache directory.
 * The new file will have the given [prefix] and [suffix]. Returns the resulting File,
 * or null on failure.
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
                val buf = ByteArray(4 * 1024)
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
 * Tries to extract a file extension (including the dot) from the [Uri], using ContentResolver's metadata.
 * Returns null if it cannot determine an extension.
 */
fun getFileExtensionFromUri(resolver: ContentResolver, uri: Uri): String? {
    // First, try to get it from the MIME type
    val mimeType = resolver.getType(uri)
    if (mimeType != null) {
        val extension = when (mimeType) {
            "audio/wav", "audio/x-wav" -> ".wav"
            "audio/mpeg" -> ".mp3"
            "audio/mp4", "audio/aac", "audio/x-m4a" -> ".m4a"
            "audio/ogg" -> ".ogg"
            "audio/flac" -> ".flac"
            else -> null
        }
        if (extension != null) return extension
    }

    // Fallback: try to parse from the document URI itself
    val path = uri.path ?: return null
    val lastDot = path.lastIndexOf('.')
    if (lastDot != -1 && lastDot < path.length - 1) {
        return path.substring(lastDot)
    }
    return null
}

fun writeWavFile(
    pcmData: ByteArray,
    outputFile: File,
    sampleRate: Int,
    channels: Int,
    bitDepth: Int
) {
    val byteRate = sampleRate * channels * bitDepth / 8
    val totalDataLen = pcmData.size + 36
    val header = ByteArray(44)

    // RIFF header
    header[0] = 'R'.code.toByte()
    header[1] = 'I'.code.toByte()
    header[2] = 'F'.code.toByte()
    header[3] = 'F'.code.toByte()
    writeInt(header, 4, totalDataLen)
    header[8] = 'W'.code.toByte()
    header[9] = 'A'.code.toByte()
    header[10] = 'V'.code.toByte()
    header[11] = 'E'.code.toByte()

    // "fmt " subchunk
    header[12] = 'f'.code.toByte()
    header[13] = 'm'.code.toByte()
    header[14] = 't'.code.toByte()
    header[15] = ' '.code.toByte()
    writeInt(header, 16, 16)                   // Subchunk1Size (16 for PCM)
    writeShort(header, 20, 1)                  // AudioFormat (1 for PCM)
    writeShort(header, 22, channels.toShort()) // NumChannels
    writeInt(header, 24, sampleRate)           // SampleRate
    writeInt(header, 28, byteRate)             // ByteRate
    writeShort(header, 32, (channels * bitDepth / 8).toShort()) // BlockAlign
    writeShort(header, 34, bitDepth.toShort())               // BitsPerSample

    // "data" subchunk
    header[36] = 'd'.code.toByte()
    header[37] = 'a'.code.toByte()
    header[38] = 't'.code.toByte()
    header[39] = 'a'.code.toByte()
    writeInt(header, 40, pcmData.size)

    outputFile.outputStream().use {
        it.write(header)
        it.write(pcmData)
    }
}

fun writeInt(b: ByteArray, offset: Int, value: Int) {
    b[offset] = (value and 0xff).toByte()
    b[offset + 1] = ((value shr 8) and 0xff).toByte()
    b[offset + 2] = ((value shr 16) and 0xff).toByte()
    b[offset + 3] = ((value shr 24) and 0xff).toByte()
}

fun writeShort(b: ByteArray, offset: Int, value: Short) {
    b[offset] = (value.toInt() and 0xff).toByte()
    b[offset + 1] = ((value.toInt() shr 8) and 0xff).toByte()
}
