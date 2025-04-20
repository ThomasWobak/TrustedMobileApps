package com.example.mobileappstrusted

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.example.mobileappstrusted.ui.theme.MobileAppsTrustedTheme


import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.navArgument
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Color
import java.io.File
//TODO: Import audio from filesystem
//TODO: Be able to read .wav files
//TODO: improve audio recording
//TODO: Implement looking at audio
//TODO: Implement cutting of audio
//TODO: Implement removing of audio
//TODO: Implement menu in editing screen
//TODO: Implement exporting of project
//TODO: Implement adding hash value when cutting/removing
//TODO: Implement hash tree to editing steps
//TODO: Implement validate Recording
//TODO: Implement going back using strg+z using hash tree
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MobileAppsTrustedTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "home") {
        composable("home") { HomeScreen(navController) }
        composable("record_audio") { RecordAudioScreen(navController) }
        composable(
            "edit_audio/{filePath}",
            arguments = listOf(navArgument("filePath") { defaultValue = "" })
        ) { backStackEntry ->
            val filePath = backStackEntry.arguments?.getString("filePath") ?: ""
            EditAudioScreen(filePath)
        }
    }
}

@Composable
fun HomeScreen(navController: NavHostController) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = { navController.navigate("record_audio") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text("Go to Record Audio")
            }
            Button(
                onClick = { navController.navigate("edit_audio") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text("Go to Edit Audio")
            }
        }
    }
}

@Composable
fun RecordAudioScreen(navController: NavHostController) {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Press to start recording") }
    var recordedFilePath by remember { mutableStateOf<String?>(null) }
    var recordingThread: Thread? by remember { mutableStateOf(null) }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            statusText = if (granted) {
                "Permission granted. Ready to record."
            } else {
                "Permission denied."
            }
        }
    )

    LaunchedEffect(Unit) {
        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    val startRecording = {
        val outputFile = File(context.externalCacheDir, "recording_${System.currentTimeMillis()}.wav")
        val sampleRate = 44100
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
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
            recordedFilePath = outputFile.absolutePath
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

            if (!isRecording && recordedFilePath != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        val encodedPath = java.net.URLEncoder.encode(recordedFilePath, "utf-8")
                        navController.navigate("edit_audio/$encodedPath")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Go to Edit Screen")
                }
            }
        }
    }
}



@Composable
fun EditAudioScreen(filePath: String) {
    val waveform = remember { generateFakeWaveform(500) } // 500 bars for smoothness

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Edit Audio",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = "Loaded file: ${File(filePath).name}",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            WaveformView(waveform)

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "This is a placeholder waveform.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
@Composable
fun WaveformView(amplitudes: List<Int>) {
    val barWidth = 3.dp
    val space = 1.dp

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
    ) {
        val totalBars = (size.width / (barWidth.toPx() + space.toPx())).toInt()
        val step = (amplitudes.size / totalBars).coerceAtLeast(1)

        for (i in 0 until totalBars) {
            val amplitude = amplitudes.getOrNull(i * step) ?: 0
            val normalized = amplitude / 100f
            val height = normalized * size.height.coerceAtMost(100f)

            drawLine(
                color = Color.Blue,
                start = androidx.compose.ui.geometry.Offset(
                    x = i * (barWidth.toPx() + space.toPx()),
                    y = size.height / 2 - height / 2
                ),
                end = androidx.compose.ui.geometry.Offset(
                    x = i * (barWidth.toPx() + space.toPx()),
                    y = size.height / 2 + height / 2
                ),
                strokeWidth = barWidth.toPx()
            )
        }
    }
}

fun generateFakeWaveform(size: Int): List<Int> {
    return List(size) { i ->
        val base = (Math.sin(i / 10.0) + 1) / 2  // sine wave
        val noise = Math.random() * 0.2          // slight random variation
        ((base + noise) * 100).toInt().coerceAtMost(100)
    }
}



@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MobileAppsTrustedTheme {
        HomeScreen(rememberNavController())
    }
}


/***
 * Written by ChatGPT, dont know how exactly it works
 */
fun writeWavFile(pcmData: ByteArray, outputFile: File, sampleRate: Int, channels: Int, bitDepth: Int) {
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
    header[12] = 'f'.code.toByte()
    header[13] = 'm'.code.toByte()
    header[14] = 't'.code.toByte()
    header[15] = ' '.code.toByte()
    writeInt(header, 16, 16)
    writeShort(header, 20, 1) // PCM
    writeShort(header, 22, channels.toShort())
    writeInt(header, 24, sampleRate)
    writeInt(header, 28, byteRate)
    writeShort(header, 32, (channels * bitDepth / 8).toShort())
    writeShort(header, 34, bitDepth.toShort())
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

private fun writeInt(header: ByteArray, offset: Int, value: Int) {
    header[offset] = (value and 0xff).toByte()
    header[offset + 1] = ((value shr 8) and 0xff).toByte()
    header[offset + 2] = ((value shr 16) and 0xff).toByte()
    header[offset + 3] = ((value shr 24) and 0xff).toByte()
}

private fun writeShort(header: ByteArray, offset: Int, value: Short) {
    header[offset] = (value.toInt() and 0xff).toByte()
    header[offset + 1] = ((value.toInt() shr 8) and 0xff).toByte()
}
