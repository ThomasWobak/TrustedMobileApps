package com.example.mobileappstrusted.dataclass

data class WavBlock(
    val originalIndex: Int,
    var currentIndex: Int,
    val data: ByteArray,
    val isDeleted: Boolean
)
