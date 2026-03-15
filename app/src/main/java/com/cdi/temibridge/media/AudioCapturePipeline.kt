package com.cdi.temibridge.media

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class AudioCapturePipeline(
    private val onFrame: (ByteArray) -> Unit
) {
    companion object {
        private const val TAG = "AudioCapturePipeline"
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val FRAME_DURATION_MS = 20
        const val FRAME_SIZE = SAMPLE_RATE * FRAME_DURATION_MS / 1000 * 2 // 640 bytes per 20ms frame
    }

    private var audioRecord: AudioRecord? = null
    private var captureThread: Thread? = null
    private val isRunning = AtomicBoolean(false)
    private val sequenceNumber = AtomicInteger(0)

    fun start() {
        if (isRunning.getAndSet(true)) {
            Log.w(TAG, "Already capturing")
            return
        }
        sequenceNumber.set(0)

        val bufferSize = maxOf(
            AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT),
            FRAME_SIZE * 4
        )

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                isRunning.set(false)
                audioRecord?.release()
                audioRecord = null
                return
            }

            audioRecord?.startRecording()

            captureThread = Thread({
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
                captureLoop()
            }, "AudioCapture").apply { start() }

            Log.i(TAG, "Audio capture started (${SAMPLE_RATE}Hz mono 16-bit, ${FRAME_DURATION_MS}ms frames)")
        } catch (e: SecurityException) {
            Log.e(TAG, "No RECORD_AUDIO permission", e)
            isRunning.set(false)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio capture", e)
            isRunning.set(false)
            audioRecord?.release()
            audioRecord = null
        }
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord", e)
        }
        audioRecord = null
        captureThread?.join(1000)
        captureThread = null
        Log.i(TAG, "Audio capture stopped")
    }

    fun isCapturing(): Boolean = isRunning.get()

    private fun captureLoop() {
        val buffer = ByteArray(FRAME_SIZE)

        while (isRunning.get()) {
            val bytesRead = audioRecord?.read(buffer, 0, FRAME_SIZE) ?: -1
            if (bytesRead > 0) {
                sendFrame(buffer, bytesRead)
            } else if (bytesRead < 0) {
                Log.e(TAG, "AudioRecord read error: $bytesRead")
                break
            }
        }
    }

    private fun sendFrame(data: ByteArray, size: Int) {
        val seq = sequenceNumber.getAndIncrement().toUShort()
        val header = MediaFrameHeader(StreamType.AUDIO_OPUS_OUT, 0, seq)
        val payload = if (size == data.size) data else data.copyOf(size)
        val frame = header.encodeWithPayload(payload)
        try {
            onFrame(frame)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending audio frame", e)
        }
    }
}
