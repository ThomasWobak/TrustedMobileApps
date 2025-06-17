package com.example.mobileappstrusted.dataclass

data class WavInfo(
    val sampleRate: Int,
    val channels: Int,
    val bitDepth: Int,
    val dataOffset: Long,
    val dataSize: Int
)