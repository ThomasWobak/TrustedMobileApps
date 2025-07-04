package com.example.mobileappstrusted.audio

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings.Secure.ANDROID_ID
import android.provider.Settings.Secure.getString
import com.example.mobileappstrusted.protobuf.EditHistoryProto
import com.example.mobileappstrusted.protobuf.WavBlockProtos
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

object EditScriptUtils {
    @SuppressLint("HardwareIds")
    fun getDeviceId(context: Context): String {
        return getString(
            context.contentResolver,
            ANDROID_ID
        )
    }
    fun getDeviceName(): String {
        return "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
    }



    fun extractEditHistoryFromWav(file: File): EditHistoryProto.EditHistory? {
        val bytes = file.readBytes()
        var i = 12 // skip RIFF header

        while (i + 8 < bytes.size) {
            val chunkId = bytes.copyOfRange(i, i + 4).toString(Charsets.US_ASCII)
            val chunkSize = ByteBuffer.wrap(bytes, i + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val chunkStart = i + 8
            val chunkEnd = chunkStart + chunkSize

            if (chunkId == "edhi" && chunkEnd <= bytes.size) {
                val chunkData = bytes.copyOfRange(chunkStart, chunkEnd)
                return try {
                    EditHistoryProto.EditHistory.parseFrom(chunkData)
                } catch (e: Exception) {
                    null
                }
            }

            i += 8 + chunkSize
        }

        return null
    }



    fun reverseEdits(
        blocks: List<WavBlockProtos.WavBlock>,
        deletedBlockIndices: Set<Int>,
        editHistory: EditHistoryProto.EditHistory
    ): Pair<List<WavBlockProtos.WavBlock>, Set<Int>> {

        var currentBlocks = blocks.toMutableList()
        val currentDeleted = deletedBlockIndices.toMutableSet()

        val reversedHistory = editHistory.entriesList.reversed()

        for (entry in reversedHistory) {
            when (entry.changeType) {
                EditHistoryProto.ChangeType.DELETE_BLOCK -> {
                    val idx = entry.detailsMap["blockIndex"]?.toIntOrNull()
                    if (idx != null) currentDeleted.remove(idx)
                }

                EditHistoryProto.ChangeType.REORDER_BLOCK -> {
                    val moved = entry.detailsMap["Moved Block"] ?: continue
                    val match = Regex("""from (\d+) to (\d+)""").find(moved)
                    val fromIndex = match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: continue
                    val toIndex = match.groupValues.getOrNull(2)?.toIntOrNull() ?: continue

                    val sorted = currentBlocks.filterNot { currentDeleted.contains(it.originalIndex) }
                        .sortedBy { it.currentIndex }
                        .toMutableList()

                    if (toIndex >= sorted.size || fromIndex > sorted.size) continue

                    // Remove the block at position `toIndex` (where it was moved to)
                    val movedBlock = sorted.removeAt(toIndex)

                    // Insert it back at position `fromIndex` (where it came from)
                    sorted.add(fromIndex, movedBlock)

                    // Reassign currentIndex values to reflect new order
                    currentBlocks = sorted.mapIndexed { i, b ->
                        b.toBuilder().setCurrentIndex(i).build()
                    }.toMutableList()
                }


                EditHistoryProto.ChangeType.RESTORE_ORDER -> {
                    currentBlocks = currentBlocks
                        .map { it.toBuilder().setCurrentIndex(it.originalIndex).build() }
                        .sortedBy { it.currentIndex }
                        .toMutableList()
                }

                else -> {}
            }
        }

        return currentBlocks to currentDeleted
    }


    fun undoLastEdit(
        blocks: List<WavBlockProtos.WavBlock>,
        deletedBlockIndices: Set<Int>,
        editHistory: EditHistoryProto.EditHistory
    ): Triple<List<WavBlockProtos.WavBlock>, Set<Int>, EditHistoryProto.EditHistory> {

        if (editHistory.entriesList.isEmpty()) return Triple(blocks, deletedBlockIndices, editHistory)

        val last = editHistory.entriesList.last()
        val newHistory = EditHistoryProto.EditHistory.newBuilder()
            .addAllEntries(editHistory.entriesList.dropLast(1)) // remove last
            .build()

        var currentBlocks = blocks.toMutableList()
        var currentDeleted = deletedBlockIndices.toMutableSet()

        when (last.changeType) {
            EditHistoryProto.ChangeType.DELETE_BLOCK -> {
                val idx = last.detailsMap["blockIndex"]?.toIntOrNull()
                if (idx != null) currentDeleted.remove(idx)
            }

            EditHistoryProto.ChangeType.RESTORE_BLOCK -> {
                // Cannot undo “restore all” without more data
                // Could reapply deletions if you tracked previous deletions
            }

            EditHistoryProto.ChangeType.REORDER_BLOCK -> {
                val moved = last.detailsMap["Moved Block"] ?: return Triple(blocks, deletedBlockIndices, editHistory)
                val match = Regex("""from (\d+) to (\d+)""").find(moved)
                val fromIndex = match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return Triple(blocks, deletedBlockIndices, editHistory)
                val toIndex = match.groupValues.getOrNull(2)?.toIntOrNull() ?: return Triple(blocks, deletedBlockIndices, editHistory)

                val sorted = currentBlocks.filterNot { currentDeleted.contains(it.originalIndex) }
                    .sortedBy { it.currentIndex }
                    .toMutableList()

                if (toIndex >= sorted.size || fromIndex > sorted.size) return Triple(blocks, deletedBlockIndices, editHistory)

                // Remove the block at position `toIndex` (where it was moved to)
                val movedBlock = sorted.removeAt(toIndex)

                // Insert it back at position `fromIndex` (where it came from)
                sorted.add(fromIndex, movedBlock)

                // Reassign currentIndex values to reflect new order
                currentBlocks = sorted.mapIndexed { i, b ->
                    b.toBuilder().setCurrentIndex(i).build()
                }.toMutableList()
            }

            EditHistoryProto.ChangeType.RESTORE_ORDER -> {
                currentBlocks = currentBlocks
                    .map { it.toBuilder().setCurrentIndex(it.originalIndex).build() }
                    .sortedBy { it.currentIndex }
                    .toMutableList()
            }

            else -> {}
        }

        return Triple(currentBlocks, currentDeleted, newHistory)
    }

}