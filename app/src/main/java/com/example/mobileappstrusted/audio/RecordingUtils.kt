package com.example.mobileappstrusted.audio

import android.content.ContentResolver
import android.content.Context
import android.media.AudioRecord
import android.media.MediaPlayer
import android.net.Uri
import com.example.mobileappstrusted.audio.WavUtils.extractAmplitudesFromWav
import com.example.mobileappstrusted.audio.WavUtils.writeWavFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class RecordingUtils(
    private val context: Context,
    private val audioRecord: AudioRecord,
    private val sampleRate: Int,
    private val recordedChunks: MutableList<Byte>,
    private val mediaPlayer: MediaPlayer
) {
    fun startRecording(
        onUpdateStatus: (String) -> Unit,
        setIsRecording: (Boolean) -> Unit,
        setHasStoppedRecording: (Boolean) -> Unit,
        setIsImported: (Boolean) -> Unit
    ) {
        setIsRecording(true)
        setHasStoppedRecording(false)
        setIsImported(false)
        onUpdateStatus("Recording...")
        audioRecord.startRecording()

        val thread = Thread {
            val buffer = ByteArray(audioRecord.bufferSizeInFrames)
            while (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val read = audioRecord.read(buffer, 0, buffer.size)
                if (read > 0) {
                    synchronized(recordedChunks) {
                        recordedChunks.addAll(buffer.copyOf(read).toList())
                    }
                }
            }
            audioRecord.stop()
        }
        thread.start()
    }

    fun stopRecording(
        onUpdateStatus: (String) -> Unit,
        setIsRecording: (Boolean) -> Unit,
        setHasStoppedRecording: (Boolean) -> Unit,
        setIsImported: (Boolean) -> Unit,
        updateTempFile: (File) -> Unit,
        updateAmplitudes: (List<Int>) -> Unit
    ) {
        setIsRecording(false)
        setHasStoppedRecording(true)
        setIsImported(false)
        onUpdateStatus("Recording stopped")

        val pcm = synchronized(recordedChunks) {
            recordedChunks.toByteArray()
        }

        val tempWavFile = File.createTempFile("preview", ".wav", context.cacheDir)
        writeWavFile(pcm, tempWavFile, sampleRate, 1, 16)
        updateAmplitudes(extractAmplitudesFromWav(tempWavFile))
        updateTempFile(tempWavFile)
    }

    fun discardAudio(
        setIsRecording: (Boolean) -> Unit,
        setHasStoppedRecording: (Boolean) -> Unit,
        setIsImported: (Boolean) -> Unit,
        updateStatus: (String) -> Unit,
        updateTempFile: (File?) -> Unit,
        updateAmplitudes: (List<Int>) -> Unit,
        setIsPlaying: (Boolean) -> Unit
    ) {
        setIsRecording(false)
        setHasStoppedRecording(false)
        setIsImported(false)
        recordedChunks.clear()
        updateAmplitudes(emptyList())
        updateTempFile(null)
        setIsPlaying(false)
        updateStatus("Press to start recording or import an audio file")
        mediaPlayer.reset()
    }

    fun resumeRecording(
        startRecording: () -> Unit
    ) {
        startRecording()
    }

    fun finishRecordingAndGoToEdit(
        lastTempFile: File?,
        onRecordingComplete: (String) -> Unit,
        setShouldClearState: (Boolean) -> Unit
    ) {
        if (recordedChunks.isEmpty() && lastTempFile != null) {
            onRecordingComplete(lastTempFile.absolutePath)
        } else {
            val outputFile = File(
                context.externalCacheDir ?: context.cacheDir,
                "record_${System.currentTimeMillis()}.wav"
            )
            val finalBytes = synchronized(recordedChunks) {
                recordedChunks.toByteArray()
            }
            writeWavFile(finalBytes, outputFile, sampleRate, 1, 16)
            onRecordingComplete(outputFile.absolutePath)
        }
        setShouldClearState(true)
    }

    /**
     * Copies a content URI to the app's cache directory, using [prefix] + timestamp + [suffix].
     * Returns the File, or null if failure.
     */
    fun copyUriToCache(
        context: Context,
        uri: Uri,
        prefix: String,
        suffix: String
    ): File? {
        return try {
            val cacheDir = context.externalCacheDir ?: context.cacheDir
            val destFile = File(cacheDir, prefix + System.currentTimeMillis() + suffix)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    val buf = ByteArray(4096)
                    var bytesRead: Int
                    while (input.read(buf).also { bytesRead = it } != -1) {
                        output.write(buf, 0, bytesRead)
                    }
                    output.flush()
                }
            }
            destFile
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Attempts to infer a file extension (e.g. ".wav", ".mp3") from the URI's mime type or path.
     */
    fun getFileExtensionFromUri(resolver: ContentResolver, uri: Uri): String? {
        val mimeType = resolver.getType(uri)
        if (mimeType != null) {
            when (mimeType) {
                "audio/wav", "audio/x-wav" -> return ".wav"
                "audio/mpeg" -> return ".mp3"
                "audio/mp4", "audio/aac", "audio/x-m4a" -> return ".m4a"
                "audio/ogg" -> return ".ogg"
                "audio/flac" -> return ".flac"
            }
        }
        // Fallback: get extension from URI path
        val path = uri.path ?: return null
        val lastDot = path.lastIndexOf('.')
        if (lastDot != -1 && lastDot < path.length - 1) {
            return path.substring(lastDot)
        }
        return null
    }

}