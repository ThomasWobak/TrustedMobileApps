package com.example.mobileappstrusted.audio

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.provider.Settings.Secure.getString
import com.example.mobileappstrusted.protobuf.RecordingMetadataProto
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

object MetadataCollector {

    @SuppressLint("HardwareIds")
    fun collectRecordingMetadata(context: Context): RecordingMetadataProto.RecordingMetadata {
        val pm = context.packageManager
        val pkgInfo = pm.getPackageInfo(context.packageName, 0)
        val config = context.resources.configuration
        val locale = config.locales.get(0).toString()
        val displayMetrics = context.resources.displayMetrics
        val tz = java.util.TimeZone.getDefault().id

        return RecordingMetadataProto.RecordingMetadata.newBuilder()
            .setDeviceId(getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown")
            .setDeviceName("${Build.MANUFACTURER} ${Build.MODEL}")
            .setManufacturer(Build.MANUFACTURER ?: "unknown")
            .setModel(Build.MODEL ?: "unknown")
            .setBrand(Build.BRAND ?: "unknown")
            .setProduct(Build.PRODUCT ?: "unknown")
            .setHardware(Build.HARDWARE ?: "unknown")
            .setBoard(Build.BOARD ?: "unknown")
            .setHost(Build.HOST ?: "unknown")
            .setAndroidVersion(Build.VERSION.RELEASE ?: "unknown")
            .setAndroidSdk(Build.VERSION.SDK_INT.toString())
            .setAppVersion(pkgInfo.versionName ?: "unknown")
            .setAppPackage(context.packageName)
            .setLocale(locale)
            .setTimezone(tz)
            .setTimestamp(System.currentTimeMillis())
            .setDisplayMetrics("${displayMetrics.widthPixels}x${displayMetrics.heightPixels}@${displayMetrics.densityDpi}dpi")
            .setCpuAbi(Build.CPU_ABI ?: "unknown")
            .setSupportedAbis(Build.SUPPORTED_ABIS.joinToString())
            .setBootloader(Build.BOOTLOADER ?: "unknown")
            .setFingerprint(Build.FINGERPRINT ?: "unknown")
            .setUser(Build.USER ?: "unknown")
            .setType(Build.TYPE ?: "unknown")
            .build()
    }

    fun extractMetaDataFromWav(file: File): RecordingMetadataProto.RecordingMetadata? {
        val bytes = file.readBytes()
        var i = 12 // skip RIFF header

        while (i + 8 < bytes.size) {
            val chunkId = bytes.copyOfRange(i, i + 4).toString(Charsets.US_ASCII)
            val chunkSize = ByteBuffer.wrap(bytes, i + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val chunkStart = i + 8
            val chunkEnd = chunkStart + chunkSize

            if (chunkId == "meta" && chunkEnd <= bytes.size) {
                val chunkData = bytes.copyOfRange(chunkStart, chunkEnd)
                return try {
                    RecordingMetadataProto.RecordingMetadata.parseFrom(chunkData)
                } catch (e: Exception) {
                    null
                }
            }

            i += 8 + chunkSize
        }

        return null
    }
}