package com.cdi.temibridge.handler

import com.google.gson.JsonElement
import com.robotemi.sdk.Robot
import com.cdi.temibridge.server.InvalidParamsException

class MovementHandler(private val robot: Robot) {

    fun register(registry: HandlerRegistry) {
        registry.register("movement.skidJoy", ::skidJoy)
        registry.register("movement.turnBy", ::turnBy)
        registry.register("movement.tiltAngle", ::tiltAngle)
        registry.register("movement.tiltBy", ::tiltBy)
        registry.register("movement.stopMovement", ::stopMovement)
    }

    private fun skidJoy(params: JsonElement?, id: Any?): Any? {
        val obj = params?.asJsonObject ?: throw InvalidParamsException("params required")
        val x = obj.get("x")?.asFloat ?: throw InvalidParamsException("x required")
        val y = obj.get("y")?.asFloat ?: throw InvalidParamsException("y required")
        robot.skidJoy(x, y)
        return mapOf("status" to "accepted")
    }

    private fun turnBy(params: JsonElement?, id: Any?): Any? {
        val obj = params?.asJsonObject ?: throw InvalidParamsException("params required")
        val degrees = obj.get("degrees")?.asInt ?: throw InvalidParamsException("degrees required")
        val speed = obj.get("speed")?.asFloat ?: 1.0f
        robot.turnBy(degrees, speed)
        return mapOf("status" to "accepted", "degrees" to degrees)
    }

    private fun tiltAngle(params: JsonElement?, id: Any?): Any? {
        val obj = params?.asJsonObject ?: throw InvalidParamsException("params required")
        val angle = obj.get("angle")?.asInt ?: throw InvalidParamsException("angle required")
        val speed = obj.get("speed")?.asFloat ?: 1.0f
        robot.tiltAngle(angle, speed)
        return mapOf("status" to "accepted", "angle" to angle)
    }

    private fun tiltBy(params: JsonElement?, id: Any?): Any? {
        val obj = params?.asJsonObject ?: throw InvalidParamsException("params required")
        val degrees = obj.get("degrees")?.asInt ?: throw InvalidParamsException("degrees required")
        val speed = obj.get("speed")?.asFloat ?: 1.0f
        robot.tiltBy(degrees, speed)
        return mapOf("status" to "accepted", "degrees" to degrees)
    }

    private fun stopMovement(params: JsonElement?, id: Any?): Any? {
        robot.stopMovement()
        return mapOf("status" to "stopped")
    }
}
