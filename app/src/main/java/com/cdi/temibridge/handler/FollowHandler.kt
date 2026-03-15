package com.cdi.temibridge.handler

import com.google.gson.JsonElement
import com.robotemi.sdk.Robot
import com.cdi.temibridge.server.InvalidParamsException

class FollowHandler(private val robot: Robot) {

    fun register(registry: HandlerRegistry) {
        registry.register("follow.beWith", ::beWith)
        registry.register("follow.constraintBeWith", ::constraintBeWith)
        registry.register("follow.setDetectionMode", ::setDetectionMode)
        registry.register("follow.setTrackUser", ::setTrackUser)
        registry.register("follow.isTrackUserOn", ::isTrackUserOn)
    }

    private fun beWith(params: JsonElement?, id: Any?): Any? {
        robot.beWithMe()
        return mapOf("status" to "accepted")
    }

    private fun constraintBeWith(params: JsonElement?, id: Any?): Any? {
        robot.constraintBeWith()
        return mapOf("status" to "accepted")
    }

    private fun setDetectionMode(params: JsonElement?, id: Any?): Any? {
        val obj = params?.asJsonObject ?: throw InvalidParamsException("params required")
        val on = obj.get("on")?.asBoolean ?: throw InvalidParamsException("on required")
        val distance = obj.get("distance")?.asFloat ?: 1.0f
        robot.setDetectionModeOn(on, distance)
        return mapOf("detectionMode" to on)
    }

    private fun setTrackUser(params: JsonElement?, id: Any?): Any? {
        val obj = params?.asJsonObject ?: throw InvalidParamsException("params required")
        val on = obj.get("on")?.asBoolean ?: throw InvalidParamsException("on required")
        // Use reflection to call setTrackUserOn due to Kotlin metadata version mismatch
        val method = robot.javaClass.getMethod("setTrackUserOn", Boolean::class.java)
        method.invoke(robot, on)
        return mapOf("trackUser" to on)
    }

    private fun isTrackUserOn(params: JsonElement?, id: Any?): Any? {
        val method = robot.javaClass.getMethod("isTrackUserOn")
        val result = method.invoke(robot) as Boolean
        return mapOf("on" to result)
    }
}
