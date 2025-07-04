package com.example.mobileappstrusted.audio

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.util.Log
import androidx.annotation.RequiresPermission
import com.example.mobileappstrusted.audio.InputStreamReader.splitWavIntoBlocks
import com.example.mobileappstrusted.audio.WavUtils.extractAmplitudesFromWav
import com.example.mobileappstrusted.audio.WavUtils.writeBlocksToTempFile
import com.example.mobileappstrusted.audio.WavUtils.writeWavFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
private var internalAudioRecord: AudioRecord? = null
class RecordingUtils(
    private val context: Context,

    private val sampleRate: Int,
    private val recordedChunks: MutableList<Byte>,
    private val mediaPlayer: MediaPlayer
) {
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startRecording(
        onUpdateStatus: (String) -> Unit,
        setIsRecording: (Boolean) -> Unit,
        setHasStoppedRecording: (Boolean) -> Unit,
        setIsImported: (Boolean) -> Unit
    ) {
        Log.i("Recording", "Started Recording")
        if (internalAudioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            Log.i("Recording", "Already Recording")
            return // Already recording
        }
        setIsRecording(true)
        setHasStoppedRecording(false)
        setIsImported(false)
        onUpdateStatus("Recording...")
        // Create a fresh instance each time
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        internalAudioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        internalAudioRecord?.startRecording()

        val recordingThread = Thread {
            val buffer = ByteArray(bufferSize)
            while (internalAudioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val read = internalAudioRecord!!.read(buffer, 0, buffer.size)
                if (read > 0) {
                    synchronized(recordedChunks) {
                        recordedChunks.addAll(buffer.copyOf(read).toList())
                    }
                }
            }

        }
        recordingThread.start()
    }

    fun stopRecording(
        onUpdateStatus: (String) -> Unit,
        setIsRecording: (Boolean) -> Unit,
        setHasStoppedRecording: (Boolean) -> Unit,
        setIsImported: (Boolean) -> Unit,
        updateAmplitudes: (List<Int>) -> Unit,
        updateTempFile: (File) -> Unit
    ) {
        setIsRecording(false)
        setHasStoppedRecording(true)
        setIsImported(false)
        onUpdateStatus("Recording stopped")

        internalAudioRecord?.let {
            try {
                it.stop()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            it.release()
        }
        internalAudioRecord = null

        val pcm = synchronized(recordedChunks) {
            recordedChunks.toByteArray()
        }

        val tempWavFile = File.createTempFile("preview", ".wav", context.cacheDir)
        writeWavFile(pcm, tempWavFile)
        updateTempFile(tempWavFile)
        updateAmplitudes(extractAmplitudesFromWav(tempWavFile))
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
        internalAudioRecord?.let {
            if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                it.stop()
            }
            it.release()
        }
        internalAudioRecord = null
        updateStatus("Press to start recording or import an audio file")
        mediaPlayer.reset()
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
            writeWavFile(finalBytes, outputFile)
            onRecordingComplete(outputFile.absolutePath)
        }
        setShouldClearState(true)

    }

    fun convertToRawWavForPlayback(context: Context, file: File): File {
        val (header, blocks) = splitWavIntoBlocks(file)
        return writeBlocksToTempFile(context, header, blocks)
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