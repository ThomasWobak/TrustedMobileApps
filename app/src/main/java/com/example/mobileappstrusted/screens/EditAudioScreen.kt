// EditAudioScreen.kt
package com.example.mobileappstrusted.screens

import android.media.MediaPlayer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.math.abs

@Composable
fun EditAudioScreen(filePath: String) {
    // If no path was provided, show a “no path” UI:
    if (filePath.isBlank()) {
        NoPathGivenScreen()
        return
    }

    // Otherwise, proceed exactly as before:
    var amplitudes by remember { mutableStateOf<List<Int>>(emptyList()) }
    val mediaPlayer = remember { MediaPlayer() }
    var isPlaying by remember { mutableStateOf(false) }

    LaunchedEffect(filePath) {
        val file = File(filePath)
        if (file.exists()) {
            amplitudes = extractAmplitudesFromWav(file)
        }
        mediaPlayer.reset()
        try {
            mediaPlayer.setDataSource(filePath)
            mediaPlayer.prepare()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    DisposableEffect(mediaPlayer) {
        onDispose {
            mediaPlayer.release()
        }
    }

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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(onClick = {
                if (!isPlaying) {
                    mediaPlayer.start()
                    isPlaying = true
                    mediaPlayer.setOnCompletionListener { isPlaying = false }
                } else {
                    mediaPlayer.pause()
                    isPlaying = false
                }
            }) {
                Text(if (isPlaying) "Pause" else "Play")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (amplitudes.isNotEmpty()) {
            WaveformView(amplitudes)
        } else {
            Text("Loading waveform...", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * If `filePath` is blank or missing, show this placeholder UI.
 */
@Composable
fun NoPathGivenScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No audio file provided",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Please record or import a file first.",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

fun extractAmplitudesFromWav(file: File, sampleEvery: Int = 200): List<Int> {
    val bytes = file.readBytes()
    if (bytes.size <= 44) return emptyList()
    val amplitudes = mutableListOf<Int>()
    var i = 44
    while (i + 1 < bytes.size) {
        val low = bytes[i].toInt() and 0xFF
        val high = bytes[i + 1].toInt()
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
        val totalBars = (size.width / (barWidth.toPx() + space.toPx()))
            .toInt()
            .coerceAtLeast(1)
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
