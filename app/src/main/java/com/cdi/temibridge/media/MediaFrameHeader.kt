package com.cdi.temibridge.media

import java.nio.ByteBuffer
import java.nio.ByteOrder

object StreamType {
    const val VIDEO_H264: Byte = 0x01
    const val AUDIO_OPUS_OUT: Byte = 0x02
    const val AUDIO_OPUS_IN: Byte = 0x03
}

object FrameFlags {
    const val KEYFRAME: Byte = 0x01
    const val END_OF_STREAM: Byte = 0x02
}

data class MediaFrameHeader(
    val streamType: Byte,
    val flags: Byte,
    val sequenceNumber: UShort
) {
    companion object {
        const val HEADER_SIZE = 4

        fun decode(data: ByteArray): MediaFrameHeader {
            require(data.size >= HEADER_SIZE) { "Data too short for header" }
            val seq = ByteBuffer.wrap(data, 2, 2).order(ByteOrder.BIG_ENDIAN).short.toUShort()
            return MediaFrameHeader(data[0], data[1], seq)
        }

        fun decode(buffer: ByteBuffer): MediaFrameHeader {
            require(buffer.remaining() >= HEADER_SIZE) { "Buffer too small for header" }
            val streamType = buffer.get()
            val flags = buffer.get()
            val seq = buffer.short.toUShort()
            return MediaFrameHeader(streamType, flags, seq)
        }
    }

    fun encode(): ByteArray {
        val buffer = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.BIG_ENDIAN)
        buffer.put(streamType)
        buffer.put(flags)
        buffer.putShort(sequenceNumber.toShort())
        return buffer.array()
    }

    fun encodeWithPayload(payload: ByteArray): ByteArray {
        val frame = ByteArray(HEADER_SIZE + payload.size)
        val header = encode()
        System.arraycopy(header, 0, frame, 0, HEADER_SIZE)
        System.arraycopy(payload, 0, frame, HEADER_SIZE, payload.size)
        return frame
    }

    val isKeyframe: Boolean get() = (flags.toInt() and FrameFlags.KEYFRAME.toInt()) != 0
    val isEndOfStream: Boolean get() = (flags.toInt() and FrameFlags.END_OF_STREAM.toInt()) != 0
}
