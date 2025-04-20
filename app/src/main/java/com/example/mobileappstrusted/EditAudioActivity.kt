package com.example.mobileappstrusted

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
                if (filePath != null) EditAudioScreen(filePath) else Text("No file provided")
            }
        }
    }
}

@Composable
fun EditAudioScreen(filePath: String) {
    var amplitudes by remember { mutableStateOf<List<Int>>(emptyList()) }

    LaunchedEffect(filePath) {
        val file = File(filePath)
        if (file.exists()) {
            amplitudes = extractAmplitudesFromWav(file)
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Edit Audio", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 16.dp))
            Text("Loaded file: ${File(filePath).name}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 16.dp))

            if (amplitudes.isNotEmpty()) WaveformView(amplitudes) else Text("Loading waveform...")
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

@Composable
fun WaveformView(amplitudes: List<Int>) {
    val barWidth = 2.dp
    val space = 1.dp

    Canvas(modifier = Modifier.fillMaxWidth().height(100.dp)) {
        val maxAmp = amplitudes.maxOrNull()?.toFloat()?.coerceAtLeast(1f) ?: 1f
        val totalBars = (size.width / (barWidth.toPx() + space.toPx())).toInt()
        val step = (amplitudes.size / totalBars).coerceAtLeast(1)

        for (i in 0 until totalBars) {
            val amplitude = amplitudes.getOrNull(i * step) ?: 0
            val normalized = amplitude / maxAmp
            val height = normalized * size.height

            drawLine(
                color = Color.Blue,
                start = androidx.compose.ui.geometry.Offset(x = i * (barWidth.toPx() + space.toPx()), y = size.height / 2 - height / 2),
                end = androidx.compose.ui.geometry.Offset(x = i * (barWidth.toPx() + space.toPx()), y = size.height / 2 + height / 2),
                strokeWidth = barWidth.toPx()
            )
        }
    }
}
