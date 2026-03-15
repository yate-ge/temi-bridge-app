package com.cdi.temibridge.handler

import com.google.gson.JsonElement
import com.cdi.temibridge.media.VideoPipeline
import com.cdi.temibridge.server.SdkErrorException

class MediaControlHandler {

    var videoPipeline: VideoPipeline? = null

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
        throw SdkErrorException("Audio capture not yet implemented (Phase 4)")
    }

    private fun stopAudioCapture(params: JsonElement?, id: Any?): Any? {
        throw SdkErrorException("Audio capture not yet implemented (Phase 4)")
    }

    private fun startAudioPlayback(params: JsonElement?, id: Any?): Any? {
        throw SdkErrorException("Audio playback not yet implemented (Phase 4)")
    }

    private fun stopAudioPlayback(params: JsonElement?, id: Any?): Any? {
        throw SdkErrorException("Audio playback not yet implemented (Phase 4)")
    }
}
