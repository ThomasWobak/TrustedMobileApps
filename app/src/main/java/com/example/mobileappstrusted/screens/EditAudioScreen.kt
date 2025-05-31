// EditAudioScreen.kt
package com.example.mobileappstrusted.screens

import android.media.MediaPlayer
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mobileappstrusted.components.WaveformView
import com.example.mobileappstrusted.components.NoPathGivenScreen
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
