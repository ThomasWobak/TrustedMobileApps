// EditAudioScreen.kt
package com.example.mobileappstrusted.screens

import android.media.MediaPlayer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.mobileappstrusted.audio.MerkleHasher
import com.example.mobileappstrusted.audio.WavCutter.cutWavFile
import com.example.mobileappstrusted.audio.WavUtils.extractAmplitudesFromWav
import com.example.mobileappstrusted.audio.WavUtils.splitWavIntoBlocks
import com.example.mobileappstrusted.audio.WavUtils.writeBlocksToTempFile
import com.example.mobileappstrusted.components.NoPathGivenScreen
import com.example.mobileappstrusted.components.WaveformView
import com.example.mobileappstrusted.dataclass.WavBlock
import java.io.File
import kotlin.math.min

@Composable
fun EditAudioScreen(filePath: String) {
    val context = LocalContext.current
    if (filePath.isBlank()) {
        NoPathGivenScreen()
        return
    }

    var currentFilePath by remember { mutableStateOf(filePath) }
    var amplitudes by remember { mutableStateOf<List<Int>>(emptyList()) }
    val mediaPlayer = remember { MediaPlayer() }
    var isPlaying by remember { mutableStateOf(false) }

    var isOriginal by remember { mutableStateOf<Boolean?>(null) }

    // Cut controls

    var removeStartText by remember { mutableStateOf("") }
    var removeEndText by remember { mutableStateOf("") }
    var removeError by remember { mutableStateOf<String?>(null) }

    // reorder controls
    var reorderFromText by remember { mutableStateOf("") }
    var reorderToText   by remember { mutableStateOf("") }
    var reorderError    by remember { mutableStateOf<String?>(null) }

    val isWav = currentFilePath.lowercase().endsWith(".wav")

    // header + blocks + playback temp file
    var wavHeader by remember { mutableStateOf<ByteArray?>(null) }
    var blocks    by remember { mutableStateOf<List<WavBlock>>(emptyList()) }
    var playbackFile by remember { mutableStateOf<File?>(null) }

    // 1) load amplitudes + initial mediaPlayer when path changes
    LaunchedEffect(currentFilePath) {
        val f = File(currentFilePath)
        amplitudes = if (f.exists() && isWav) extractAmplitudesFromWav(f) else emptyList()

        if (isWav) {
            val (hdr, blks) = splitWavIntoBlocks(f)
            wavHeader = hdr
            blocks = blks
            playbackFile = writeBlocksToTempFile(context, hdr, blks)
        }
        
        isOriginal = if (f.exists() && isWav) {
            MerkleHasher.verifyWavMerkleRoot(f)
        } else null


        mediaPlayer.reset()
        playbackFile?.let {
            mediaPlayer.setDataSource(it.absolutePath)
            mediaPlayer.prepare()
        }
    }

    // 2) whenever blocks reorder, rewrite playback + reload player
    LaunchedEffect(blocks) {
        val hdr = wavHeader ?: return@LaunchedEffect
        playbackFile = writeBlocksToTempFile(context, hdr, blocks)
        mediaPlayer.reset()
        playbackFile?.let {
            mediaPlayer.setDataSource(it.absolutePath)
            mediaPlayer.prepare()
        }
    }

    DisposableEffect(mediaPlayer) {
        onDispose { mediaPlayer.release() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {

        when (isOriginal) {
            true -> Text("✔️ Verified", color = MaterialTheme.colorScheme.primary)
            false -> Text("❌ Not Verified", color = MaterialTheme.colorScheme.error)
            null -> {}
        }

        // play controls
        Text("Edit Audio", style = MaterialTheme.typography.headlineMedium)
        Text("Loaded: ${File(currentFilePath).name}", style = MaterialTheme.typography.bodyLarge)
        Row(Modifier.fillMaxWidth()) {
            Button({

                if (!isPlaying) {
                    mediaPlayer.start(); isPlaying = true
                    mediaPlayer.setOnCompletionListener { isPlaying = false }
                } else {
                    mediaPlayer.pause(); isPlaying = false
                }
            }) { Text(if (isPlaying) "Pause" else "Play") }
        }
        Spacer(Modifier.height(24.dp))

        // waveform
        if (amplitudes.isNotEmpty()) WaveformView(amplitudes)
        else if (isWav) Text("Loading waveform…", style = MaterialTheme.typography.bodyMedium)
        else Text("Cannot display waveform for non-WAV file.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))

        if (!isWav) {
            Text("Editing features only support WAV files.", color = MaterialTheme.colorScheme.error)
        } else {
            // cut controls (unchanged)
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = removeStartText,
                    onValueChange = { removeStartText = it },
                    label = { Text("Remove Start (sec)") },
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = removeEndText,
                    onValueChange = { removeEndText = it },
                    label = { Text("Remove End (sec)") },
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    removeError = null
                    val s = removeStartText.toFloatOrNull()
                    val e = removeEndText.toFloatOrNull()
                    if (s==null||e==null||s<0f||e<=s) removeError="Invalid"
                    else {
                        val cf = cutWavFile(currentFilePath,s,e)
                        if(cf!=null){ currentFilePath=cf.absolutePath; removeStartText=""; removeEndText="" }
                        else removeError="Cut failed"
                    }
                }, Modifier.align(Alignment.End)) {
                    Text("Remove Segment")
                }
                removeError?.let { Text(it, color=MaterialTheme.colorScheme.error) }
            }
            Spacer(Modifier.height(24.dp))

            // reorder controls
            Text("Reorder Blocks", style=MaterialTheme.typography.headlineSmall)
            Text(
                "Order: " + blocks.sortedBy { it.currentIndex }
                    .joinToString(",") { it.originalIndex.toString() }
            )
            OutlinedTextField(
                value = reorderFromText,
                onValueChange = { reorderFromText = it },
                label = { Text("Move block # (orig index)") },
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = reorderToText,
                onValueChange = { reorderToText = it },
                label = { Text("Insert at position (0-based)") },
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                reorderError = null
                val fromIdx = reorderFromText.toIntOrNull()
                val toPos   = reorderToText.toIntOrNull()
                if (fromIdx==null||toPos==null) reorderError="Invalid"
                else {
                    val sorted = blocks.sortedBy { it.currentIndex }.toMutableList()
                    val rem = sorted.indexOfFirst { it.originalIndex==fromIdx }
                    if (rem<0||toPos<0||toPos>sorted.size) reorderError="Out of range"
                    else {
                        val b = sorted.removeAt(rem)
                        sorted.add(min(toPos, sorted.size), b)
                        sorted.forEachIndexed{ i, blk-> blk.currentIndex=i }
                        blocks = sorted
                        reorderFromText=""; reorderToText=""
                    }
                }
            }, Modifier.align(Alignment.End)) {
                Text("Apply Reorder")
            }
            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {

                    // reset every block to its original position
                    blocks = blocks
                        .map { it.apply { currentIndex = originalIndex } }
                        .sortedBy { it.currentIndex }
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Reset to Original Order")
            }
            reorderError?.let { Text(it, color=MaterialTheme.colorScheme.error) }
        }
    }
}