// EditAudioActivity.kt
package com.example.mobileappstrusted

import android.media.MediaPlayer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.mobileappstrusted.ui.theme.MobileAppsTrustedTheme
import java.io.File
import kotlin.math.abs

class EditAudioActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val filePath = intent.getStringExtra("filePath")
        setContent {
            MobileAppsTrustedTheme {
                if (filePath != null) {
                    EditAudioScreen(filePath)
                } else {
                    Text(
                        "No file provided",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

@Composable
fun EditAudioScreen(filePath: String) {

    var amplitudes by remember { mutableStateOf<List<Int>>(emptyList()) }

    val mediaPlayer = remember { MediaPlayer() }

    var isPlaying by remember { mutableStateOf(false) }
    //logic for playing audio was done by ChatGPT
    // When filePath changes, load waveform + prepare MediaPlayer
    LaunchedEffect(filePath) {
        // Extract waveform amplitudes
        val file = File(filePath)
        if (file.exists()) {
            amplitudes = extractAmplitudesFromWav(file)
        }

        // Prepare MediaPlayer for this file
        try {
            mediaPlayer.reset()
            mediaPlayer.setDataSource(filePath)
            mediaPlayer.prepare()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Release MediaPlayer when composable leaves
    DisposableEffect(mediaPlayer) {
        onDispose {
            mediaPlayer.release()
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Edit Audio",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "Loaded file: ${File(filePath).name}",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // === Play/Pause Button ===
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(
                    onClick = {
                        if (!isPlaying) {
                            mediaPlayer.start()
                            isPlaying = true
                            mediaPlayer.setOnCompletionListener {
                                isPlaying = false
                            }
                        } else {
                            mediaPlayer.pause()
                            isPlaying = false
                        }
                    }
                ) {
                    Text(if (isPlaying) "Pause" else "Play")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // === Waveform Visualization ===
            if (amplitudes.isNotEmpty()) {
                WaveformView(amplitudes)
            } else {
                Text(
                    "Loading waveform...",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

fun extractAmplitudesFromWav(file: File, sampleEvery: Int = 200): List<Int> {
    val bytes = file.readBytes()
    if (bytes.size <= 44) return emptyList() // no audio data
    val amplitudes = mutableListOf<Int>()
    var i = 44
    while (i + 1 < bytes.size) {
        val low = bytes[i].toInt() and 0xFF
        val high = bytes[i + 1].toInt() // signed high byte
        val sample = (high shl 8) or low
        amplitudes.add(abs(sample))
        i += 2 * sampleEvery
    }
    return amplitudes
}

@Composable
fun WaveformView(amplitudes: List<Int>) {
    val barWidth = 2.dp
    val space = 1.dp

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
    ) {
        val maxAmp = amplitudes.maxOrNull()?.toFloat()?.coerceAtLeast(1f) ?: 1f
        val totalBars = (size.width / (barWidth.toPx() + space.toPx())).toInt().coerceAtLeast(1)
        val step = (amplitudes.size / totalBars).coerceAtLeast(1)

        for (i in 0 until totalBars) {
            val amplitude = amplitudes.getOrNull(i * step) ?: 0
            val normalized = amplitude / maxAmp
            val lineHeight = normalized * size.height

            val x = i * (barWidth.toPx() + space.toPx())
            val yStart = size.height / 2 - lineHeight / 2
            val yEnd = size.height / 2 + lineHeight / 2

            drawLine(
                color = Color.Blue,
                start = androidx.compose.ui.geometry.Offset(x, yStart),
                end = androidx.compose.ui.geometry.Offset(x, yEnd),
                strokeWidth = barWidth.toPx()
            )
        }
    }
}
