package com.example.mobileappstrusted.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.*
import androidx.compose.foundation.gestures.detectTapGestures

@Composable
fun WaveformView(
    amplitudes: List<Int>,
    onBarClick: (barIndex: Int, amplitudeIndex: Int) -> Unit,
    selectedVisualBlockIndex: Int?,
    totalBlocks: Int = 0
) {
    val barWidth = 2.dp
    val space = 1.dp

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .pointerInput(amplitudes) {
                detectTapGestures { offset ->
                    val barSpacing = barWidth.toPx() + space.toPx()
                    val totalBars = (size.width / barSpacing).toInt().coerceAtLeast(1)
                    val step = (amplitudes.size / totalBars).coerceAtLeast(1)

                    val clickedBarIndex = (offset.x / barSpacing).toInt()
                    val amplitudeIndex = clickedBarIndex * step

                    onBarClick(clickedBarIndex, amplitudeIndex)
                }
            }
    ) {
        val barSpacing = barWidth.toPx() + space.toPx()
        val totalBars = (size.width / barSpacing).toInt().coerceAtLeast(1)
        val step = (amplitudes.size / totalBars).coerceAtLeast(1)
        val maxAmp = amplitudes.maxOrNull()?.toFloat()?.coerceAtLeast(1f) ?: 1f

        // Draw shaded background rectangle for selected block
        if (selectedVisualBlockIndex != null && selectedVisualBlockIndex >= 0 && totalBlocks > 0) {
            val barsPerBlock = totalBars.toFloat() / totalBlocks
            val startBar = (selectedVisualBlockIndex * barsPerBlock).toInt()
            val endBar = ((selectedVisualBlockIndex + 1) * barsPerBlock).toInt()

            val startX = (startBar * barSpacing).coerceAtLeast(0f)
            val endX = (endBar * barSpacing).coerceAtMost(size.width)

            drawRect(
                color = Color.Black.copy(alpha = 0.15f),
                topLeft = Offset(startX, 0f),
                size = androidx.compose.ui.geometry.Size(endX - startX, size.height)
            )
        }


        // Draw bars
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
}



