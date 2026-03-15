package com.cdi.temibridge.handler

import com.google.gson.JsonElement
import com.robotemi.sdk.Robot
import com.cdi.temibridge.server.SdkErrorException

class FaceHandler(private val robot: Robot) {

    fun register(registry: HandlerRegistry) {
        registry.register("face.startRecognition", ::startRecognition)
        registry.register("face.stopRecognition", ::stopRecognition)
    }

    private fun startRecognition(params: JsonElement?, id: Any?): Any? {
        robot.startFaceRecognition()
        return mapOf("status" to "started")
    }

    private fun stopRecognition(params: JsonElement?, id: Any?): Any? {
        robot.stopFaceRecognition()
        return mapOf("status" to "stopped")
    }
}
