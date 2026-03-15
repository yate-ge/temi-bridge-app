package com.cdi.temibridge.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class VideoPipeline(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onFrame: (ByteArray) -> Unit
) {
    companion object {
        private const val TAG = "VideoPipeline"
        private const val WIDTH = 640
        private const val HEIGHT = 480
        private const val FRAME_RATE = 15
        private const val BIT_RATE = 1_000_000
        private const val I_FRAME_INTERVAL = 2
    }

    private var encoder: MediaCodec? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val encoderThread = Executors.newSingleThreadExecutor()
    private val isRunning = AtomicBoolean(false)
    private val sequenceNumber = AtomicInteger(0)

    fun start() {
        if (isRunning.getAndSet(true)) {
            Log.w(TAG, "Already running")
            return
        }
        sequenceNumber.set(0)
        startEncoder()
        startCamera()
        Log.i(TAG, "Video pipeline started (${WIDTH}x${HEIGHT} @ ${FRAME_RATE}fps)")
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return
        stopCamera()
        stopEncoder()
        Log.i(TAG, "Video pipeline stopped")
    }

    fun isStreaming(): Boolean = isRunning.get()

    private fun startEncoder() {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, WIDTH, HEIGHT).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
        }

        encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            start()
        }

        // Start output drain thread
        encoderThread.execute { drainEncoder() }
    }

    private fun stopEncoder() {
        try {
            encoder?.stop()
            encoder?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping encoder", e)
        }
        encoder = null
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider

                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(WIDTH, HEIGHT))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()

                imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                    if (isRunning.get()) {
                        feedEncoder(imageProxy)
                    }
                    imageProxy.close()
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, cameraSelector, imageAnalysis)
                Log.i(TAG, "Camera bound to lifecycle")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start camera", e)
                isRunning.set(false)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun stopCamera() {
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping camera", e)
        }
        cameraProvider = null
    }

    private fun feedEncoder(imageProxy: ImageProxy) {
        val enc = encoder ?: return
        try {
            val inputIndex = enc.dequeueInputBuffer(10_000)
            if (inputIndex < 0) return

            val inputBuffer = enc.getInputBuffer(inputIndex) ?: return
            val yuvData = yuv420ToNv12(imageProxy)
            inputBuffer.clear()
            inputBuffer.put(yuvData)

            val presentationTimeUs = System.nanoTime() / 1000
            enc.queueInputBuffer(inputIndex, 0, yuvData.size, presentationTimeUs, 0)
        } catch (e: Exception) {
            Log.e(TAG, "Error feeding encoder", e)
        }
    }

    private fun drainEncoder() {
        val bufferInfo = MediaCodec.BufferInfo()
        while (isRunning.get()) {
            val enc = encoder ?: break
            try {
                val outputIndex = enc.dequeueOutputBuffer(bufferInfo, 10_000)
                if (outputIndex < 0) continue

                val outputBuffer = enc.getOutputBuffer(outputIndex) ?: continue

                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                    // SPS/PPS config data — send as keyframe
                    val data = ByteArray(bufferInfo.size)
                    outputBuffer.get(data)
                    sendFrame(data, isKeyframe = true)
                    enc.releaseOutputBuffer(outputIndex, false)
                    continue
                }

                if (bufferInfo.size > 0) {
                    val data = ByteArray(bufferInfo.size)
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                    outputBuffer.get(data)

                    val isKey = bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
                    sendFrame(data, isKey)
                }

                enc.releaseOutputBuffer(outputIndex, false)

                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    break
                }
            } catch (e: IllegalStateException) {
                if (!isRunning.get()) break
                Log.e(TAG, "Encoder drain error", e)
            }
        }
    }

    private fun sendFrame(data: ByteArray, isKeyframe: Boolean) {
        val seq = sequenceNumber.getAndIncrement().toUShort()
        val flags: Byte = if (isKeyframe) FrameFlags.KEYFRAME else 0
        val header = MediaFrameHeader(StreamType.VIDEO_H264, flags, seq)
        val frame = header.encodeWithPayload(data)
        try {
            onFrame(frame)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending frame", e)
        }
    }

    private fun yuv420ToNv12(imageProxy: ImageProxy): ByteArray {
        val yPlane = imageProxy.planes[0]
        val uPlane = imageProxy.planes[1]
        val vPlane = imageProxy.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val width = imageProxy.width
        val height = imageProxy.height
        val nv12 = ByteArray(width * height * 3 / 2)

        // Copy Y plane
        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        if (yRowStride == width && yPixelStride == 1) {
            yBuffer.get(nv12, 0, width * height)
        } else {
            for (row in 0 until height) {
                yBuffer.position(row * yRowStride)
                for (col in 0 until width) {
                    nv12[row * width + col] = yBuffer.get(row * yRowStride + col * yPixelStride)
                }
            }
        }

        // Copy UV planes interleaved (NV12: UVUVUV...)
        val uvRowStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride
        val uvHeight = height / 2
        val uvWidth = width / 2
        var uvOffset = width * height

        if (uvPixelStride == 2 && uvRowStride == width) {
            // Already interleaved (NV21 or NV12 layout), just need to check U/V order
            // Android YUV_420_888 with pixelStride=2 is usually NV21 (VUVU)
            // We need NV12 (UVUV), so swap
            vBuffer.rewind()
            uBuffer.rewind()
            for (row in 0 until uvHeight) {
                for (col in 0 until uvWidth) {
                    val pos = row * uvRowStride + col * uvPixelStride
                    nv12[uvOffset++] = uBuffer.get(pos)
                    nv12[uvOffset++] = vBuffer.get(pos)
                }
            }
        } else {
            // Planar or other layout
            uBuffer.rewind()
            vBuffer.rewind()
            for (row in 0 until uvHeight) {
                for (col in 0 until uvWidth) {
                    val pos = row * uvRowStride + col * uvPixelStride
                    nv12[uvOffset++] = uBuffer.get(pos)
                    nv12[uvOffset++] = vBuffer.get(pos)
                }
            }
        }

        return nv12
    }
}
