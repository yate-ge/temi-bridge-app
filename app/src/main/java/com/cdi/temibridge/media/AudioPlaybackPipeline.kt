package com.cdi.temibridge.media

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class AudioPlaybackPipeline {

    companion object {
        private const val TAG = "AudioPlaybackPipeline"
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val MAX_QUEUE_SIZE = 100 // ~2 seconds of 20ms frames
    }

    private var audioTrack: AudioTrack? = null
    private var playbackThread: Thread? = null
    private val isRunning = AtomicBoolean(false)
    private val frameQueue = LinkedBlockingQueue<ByteArray>(MAX_QUEUE_SIZE)

    fun start() {
        if (isRunning.getAndSet(true)) {
            Log.w(TAG, "Already playing")
            return
        }
        frameQueue.clear()

        val bufferSize = maxOf(
            AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT),
            AudioCapturePipeline.FRAME_SIZE * 4
        )

        try {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_CONFIG)
                        .setEncoding(AUDIO_FORMAT)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()

            playbackThread = Thread({
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
                playbackLoop()
            }, "AudioPlayback").apply { start() }

            Log.i(TAG, "Audio playback started (${SAMPLE_RATE}Hz mono 16-bit)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio playback", e)
            isRunning.set(false)
            audioTrack?.release()
            audioTrack = null
        }
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return
        frameQueue.clear()
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioTrack", e)
        }
        audioTrack = null
        playbackThread?.join(1000)
        playbackThread = null
        Log.i(TAG, "Audio playback stopped")
    }

    fun isPlaying(): Boolean = isRunning.get()

    fun feedFrame(payload: ByteArray) {
        if (!isRunning.get()) return
        // Drop oldest frames if queue is full to prevent unbounded latency
        if (!frameQueue.offer(payload)) {
            frameQueue.poll()
            frameQueue.offer(payload)
        }
    }

    fun feedFrame(buffer: ByteBuffer) {
        if (!isRunning.get()) return
        // Skip the 4-byte media header
        if (buffer.remaining() < MediaFrameHeader.HEADER_SIZE) return
        val header = MediaFrameHeader.decode(buffer)
        if (header.streamType != StreamType.AUDIO_OPUS_IN) return

        val payload = ByteArray(buffer.remaining())
        buffer.get(payload)
        feedFrame(payload)
    }

    private fun playbackLoop() {
        while (isRunning.get()) {
            try {
                val frame = frameQueue.poll(100, TimeUnit.MILLISECONDS) ?: continue
                audioTrack?.write(frame, 0, frame.size)
            } catch (e: InterruptedException) {
                break
            } catch (e: Exception) {
                Log.e(TAG, "Playback error", e)
            }
        }
    }
}
