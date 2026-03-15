package com.cdi.temibridge.handler

import com.google.gson.JsonElement
import com.cdi.temibridge.media.AudioCapturePipeline
import com.cdi.temibridge.media.AudioPlaybackPipeline
import com.cdi.temibridge.media.VideoPipeline
import com.cdi.temibridge.server.SdkErrorException

class MediaControlHandler {

    var videoPipeline: VideoPipeline? = null
    var audioCapturePipeline: AudioCapturePipeline? = null
    var audioPlaybackPipeline: AudioPlaybackPipeline? = null

    fun register(registry: HandlerRegistry) {
        registry.register("media.startVideoStream", ::startVideoStream)
        registry.register("media.stopVideoStream", ::stopVideoStream)
        registry.register("media.startAudioCapture", ::startAudioCapture)
        registry.register("media.stopAudioCapture", ::stopAudioCapture)
        registry.register("media.startAudioPlayback", ::startAudioPlayback)
        registry.register("media.stopAudioPlayback", ::stopAudioPlayback)
    }

    private fun startVideoStream(params: JsonElement?, id: Any?): Any? {
        val pipeline = videoPipeline ?: throw SdkErrorException("Video pipeline not initialized")
        if (pipeline.isStreaming()) {
            return mapOf("status" to "already_streaming")
        }
        pipeline.start()
        return mapOf("status" to "started", "format" to "H.264", "resolution" to "640x480", "fps" to 15)
    }

    private fun stopVideoStream(params: JsonElement?, id: Any?): Any? {
        val pipeline = videoPipeline ?: throw SdkErrorException("Video pipeline not initialized")
        pipeline.stop()
        return mapOf("status" to "stopped")
    }

    private fun startAudioCapture(params: JsonElement?, id: Any?): Any? {
        val pipeline = audioCapturePipeline ?: throw SdkErrorException("Audio capture not initialized — RECORD_AUDIO permission may be missing")
        if (pipeline.isCapturing()) {
            return mapOf("status" to "already_capturing")
        }
        pipeline.start()
        return mapOf(
            "status" to "started",
            "format" to "PCM",
            "sampleRate" to AudioCapturePipeline.SAMPLE_RATE,
            "channels" to 1,
            "bitsPerSample" to 16,
            "frameDurationMs" to AudioCapturePipeline.FRAME_DURATION_MS,
            "frameSizeBytes" to AudioCapturePipeline.FRAME_SIZE
        )
    }

    private fun stopAudioCapture(params: JsonElement?, id: Any?): Any? {
        val pipeline = audioCapturePipeline ?: throw SdkErrorException("Audio capture not initialized")
        pipeline.stop()
        return mapOf("status" to "stopped")
    }

    private fun startAudioPlayback(params: JsonElement?, id: Any?): Any? {
        val pipeline = audioPlaybackPipeline ?: throw SdkErrorException("Audio playback not initialized")
        if (pipeline.isPlaying()) {
            return mapOf("status" to "already_playing")
        }
        pipeline.start()
        return mapOf(
            "status" to "started",
            "format" to "PCM",
            "sampleRate" to AudioPlaybackPipeline.SAMPLE_RATE,
            "channels" to 1,
            "bitsPerSample" to 16,
            "streamType" to "0x03"
        )
    }

    private fun stopAudioPlayback(params: JsonElement?, id: Any?): Any? {
        val pipeline = audioPlaybackPipeline ?: throw SdkErrorException("Audio playback not initialized")
        pipeline.stop()
        return mapOf("status" to "stopped")
    }
}
