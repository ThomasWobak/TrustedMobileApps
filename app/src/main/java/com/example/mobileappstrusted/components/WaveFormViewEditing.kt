package com.example.mobileappstrusted.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.*
import com.example.mobileappstrusted.audio.InputStreamReader
import com.example.mobileappstrusted.protobuf.WavBlockProtos
import kotlin.math.min

@Composable
fun WaveformViewEditing(
    amplitudes: List<Int>,
    selectedVisualBlockIndices: Set<Int>,
    visibleBlocks: List<WavBlockProtos.WavBlock>,
    maxAmplitude: Int,
    startTimeOffsetSeconds: Float = 0f,
    blockDurationSeconds: Float = InputStreamReader.BLOCK_TIME, //Current Block size to time ratio
    onBarRangeSelect: (startBar: Int, endBar: Int) -> Unit
)
 {
    val barWidth = 2.dp
    val space = 1.dp

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .pointerInput(amplitudes) {
                awaitEachGesture {

                        var dragStarted = false
                        val startBar: Int

                        val down = awaitFirstDown()

                        val barSpacing = barWidth.toPx() + space.toPx()
                        val initialBar = (down.position.x / barSpacing).toInt()
                        startBar = initialBar

                        do {
                            val event = awaitPointerEvent()
                            val drag = event.changes.firstOrNull()?.position?.x
                            if (event.changes.any { it.pressed }) {
                                drag?.let { dragX ->
                                    dragStarted = true
                                    val currentBar = (dragX / barSpacing).toInt()
                                    onBarRangeSelect(
                                        min(initialBar, currentBar),
                                        maxOf(initialBar, currentBar)
                                    )
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        if (!dragStarted) {
                            // Single tap fallback
                            onBarRangeSelect(startBar, startBar)
                        }
                    }

            }

    ) {
        val barSpacing = barWidth.toPx() + space.toPx()
        val totalBars = (size.width / barSpacing).toInt().coerceAtLeast(1)
        val step = (amplitudes.size / totalBars).coerceAtLeast(1)
        val maxAmp = maxAmplitude.toFloat().coerceAtLeast(1f)


        // Draw shaded background rectangle for selected blocks based on CURRENT index
        if (visibleBlocks.isNotEmpty()) {
            val barsPerBlock = totalBars.toFloat() / visibleBlocks.size
            for (block in visibleBlocks) {
                if (selectedVisualBlockIndices.contains(block.currentIndex)) {
                    val visualIdx = visibleBlocks.indexOfFirst { it.currentIndex == block.currentIndex }
                    if (visualIdx != -1) {
                        val startBar = (visualIdx * barsPerBlock).toInt()
                        val endBar = ((visualIdx + 1) * barsPerBlock).toInt()

                        val startX = (startBar * barSpacing).coerceAtLeast(0f)
                        val endX = (endBar * barSpacing).coerceAtMost(size.width)

                        drawRect(
                            color = Color.Black.copy(alpha = 0.15f),
                            topLeft = Offset(startX, 0f),
                            size = androidx.compose.ui.geometry.Size(endX - startX, size.height)
                        )
                    }
                }
            }
        }

        // Draw waveform bars
        for (i in 0 until totalBars) {
            val amplitude = amplitudes.getOrNull(i * step) ?: 0
            val normalized = amplitude / maxAmp
            val lineHeight = normalized * size.height

            val x = i * barSpacing
            val yStart = size.height / 2 - lineHeight / 2
            val yEnd = size.height / 2 + lineHeight / 2

            drawLine(
                color = Color.Blue,
                start = Offset(x, yStart),
                end = Offset(x, yEnd),
                strokeWidth = barWidth.toPx()
            )
        }
    }
     Row(modifier = Modifier.fillMaxWidth()) {
         val totalDuration = visibleBlocks.size * blockDurationSeconds
         val markerCount = 5
         val secondsPerMarker = totalDuration / markerCount

         repeat(markerCount + 1) { i ->
             Text(
                 text = "${(startTimeOffsetSeconds + i * secondsPerMarker).toInt()}s",
                 modifier = Modifier.weight(1f),
                 style = MaterialTheme.typography.labelSmall
             )
         }
     }


 }
