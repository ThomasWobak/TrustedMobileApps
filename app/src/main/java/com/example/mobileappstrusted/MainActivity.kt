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
    var recorder: MediaRecorder? by remember { mutableStateOf(null) }
    var statusText by remember { mutableStateOf("Press to start recording") }
    var recordedFilePath by remember { mutableStateOf<String?>(null) }

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
                    if (!isRecording) {
                        try {
                            val outputFile = File(
                                context.externalCacheDir,
                                "audiorecord_${System.currentTimeMillis()}.mp3"
                            ).absolutePath

                            recorder = MediaRecorder().apply {
                                setAudioSource(MediaRecorder.AudioSource.MIC)
                                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                                setOutputFile(outputFile)
                                prepare()
                                start()
                            }
                            statusText = "Recording..."
                            isRecording = true
                            recordedFilePath = outputFile
                        } catch (e: Exception) {
                            statusText = "Recording failed: ${e.message}"
                        }
                    } else {
                        try {
                            recorder?.apply {
                                stop()
                                release()
                            }
                            recorder = null
                            isRecording = false
                            statusText = "Recording stopped"
                        } catch (e: Exception) {
                            statusText = "Stop failed: ${e.message}"
                        }
                    }
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
