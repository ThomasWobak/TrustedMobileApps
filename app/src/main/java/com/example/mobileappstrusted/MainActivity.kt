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

import java.io.File

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
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Edit Audio Screen",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                text = "Loaded file: $filePath",
                style = MaterialTheme.typography.bodyLarge
            )

        }
    }
}


@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MobileAppsTrustedTheme {
        HomeScreen(rememberNavController())
    }
}
