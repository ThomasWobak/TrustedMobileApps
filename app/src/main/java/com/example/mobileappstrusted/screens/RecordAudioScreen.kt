// RecordAudioScreenWithImport.kt
package com.example.mobileappstrusted.screens


import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

@Composable
fun RecordAudioScreen(onRecordingComplete: (String) -> Unit) {
    val context = LocalContext.current

    var isRecording by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Press to start recording or import an audio file") }
    var recordingThread: Thread? by remember { mutableStateOf(null) }

    // Holds the path once recording/import finishes
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

/** WAV-writing helper (created by ChatGPT) */
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
    writeInt(header, 16, 16)
    writeShort(header, 20, 1)
    writeShort(header, 22, channels.toShort())
    writeInt(header, 24, sampleRate)
    writeInt(header, 28, byteRate)
    writeShort(header, 32, (channels * bitDepth / 8).toShort())
    writeShort(header, 34, bitDepth.toShort())

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
