package com.example.mobileappstrusted.audio

import com.example.mobileappstrusted.cryptography.MerkleHasher
import com.example.mobileappstrusted.protobuf.WavBlockProtos

object WavCutter {

    fun markBlockDeleted(block: WavBlockProtos.WavBlock): WavBlockProtos.WavBlock {

                    val undeletedHash = MerkleHasher.hashChunk(block.pcmData.toByteArray())
                   return  block.toBuilder()
                        .setIsDeleted(true)
                        .setUndeletedHash(com.google.protobuf.ByteString.copyFrom(undeletedHash))
                        .build()
    }
}
