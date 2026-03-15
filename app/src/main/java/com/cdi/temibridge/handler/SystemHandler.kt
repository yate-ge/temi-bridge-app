package com.cdi.temibridge.handler

import com.google.gson.JsonElement
import com.robotemi.sdk.Robot
import com.robotemi.sdk.navigation.model.SpeedLevel
import com.robotemi.sdk.navigation.model.SafetyLevel
import com.cdi.temibridge.server.InvalidParamsException

class SystemHandler(private val robot: Robot) {

    fun register(registry: HandlerRegistry) {
        registry.register("system.getBattery", ::getBattery)
        registry.register("system.getSerialNumber", ::getSerialNumber)
        registry.register("system.getVolume", ::getVolume)
        registry.register("system.setVolume", ::setVolume)
        registry.register("system.getGoToSpeed", ::getGoToSpeed)
        registry.register("system.setGoToSpeed", ::setGoToSpeed)
        registry.register("system.getNavigationSafety", ::getNavigationSafety)
        registry.register("system.setNavigationSafety", ::setNavigationSafety)
        registry.register("system.showTopBar", ::showTopBar)
        registry.register("system.hideTopBar", ::hideTopBar)
        registry.register("system.toggleNavigationBillboard", ::toggleNavigationBillboard)
        registry.register("system.toggleWakeup", ::toggleWakeup)
        registry.register("system.isWakeupDisabled", ::isWakeupDisabled)
        registry.register("system.setHardButtonsDisabled", ::setHardButtonsDisabled)
        registry.register("system.isHardButtonsDisabled", ::isHardButtonsDisabled)
        registry.register("system.setPrivacyMode", ::setPrivacyMode)
        registry.register("system.getPrivacyMode", ::getPrivacyMode)
        registry.register("system.restart", ::restart)
        registry.register("system.showAppList", ::showAppList)
        registry.register("system.setAutoReturn", ::setAutoReturn)
        registry.register("system.isAutoReturnOn", ::isAutoReturnOn)
    }

    // Helper for calling Robot methods hidden by Kotlin metadata version mismatch
    private fun callBooleanGetter(name: String): Boolean {
        return robot.javaClass.getMethod(name).invoke(robot) as Boolean
    }

    private fun callBooleanSetter(name: String, value: Boolean) {
        robot.javaClass.getMethod(name, Boolean::class.java).invoke(robot, value)
    }

    private fun getBattery(params: JsonElement?, id: Any?): Any? {
        val battery = robot.batteryData
        return if (battery != null) {
            mapOf("level" to battery.level, "isCharging" to battery.isCharging)
        } else {
            mapOf("level" to -1, "isCharging" to false)
        }
    }

    private fun getSerialNumber(params: JsonElement?, id: Any?): Any? {
        return mapOf("serialNumber" to robot.serialNumber)
    }

    private fun getVolume(params: JsonElement?, id: Any?): Any? {
        return mapOf("volume" to robot.volume)
    }

    private fun setVolume(params: JsonElement?, id: Any?): Any? {
        val obj = params?.asJsonObject ?: throw InvalidParamsException("params required")
        val volume = obj.get("volume")?.asInt ?: throw InvalidParamsException("volume required")
        robot.volume = volume
        return mapOf("volume" to volume)
    }

    private fun getGoToSpeed(params: JsonElement?, id: Any?): Any? {
        return mapOf("speed" to robot.goToSpeed.name)
    }

    private fun setGoToSpeed(params: JsonElement?, id: Any?): Any? {
        val obj = params?.asJsonObject ?: throw InvalidParamsException("params required")
        val speed = obj.get("speed")?.asString ?: throw InvalidParamsException("speed required")
        val level = try {
            SpeedLevel.valueOf(speed.uppercase())
        } catch (e: IllegalArgumentException) {
            throw InvalidParamsException("Invalid speed level: $speed. Valid: HIGH, MEDIUM, SLOW, DEFAULT")
        }
        robot.goToSpeed = level
        return mapOf("speed" to level.name)
    }

    private fun getNavigationSafety(params: JsonElement?, id: Any?): Any? {
        return mapOf("safety" to robot.navigationSafety.name)
    }

    private fun setNavigationSafety(params: JsonElement?, id: Any?): Any? {
        val obj = params?.asJsonObject ?: throw InvalidParamsException("params required")
        val safety = obj.get("safety")?.asString ?: throw InvalidParamsException("safety required")
        val level = try {
            SafetyLevel.valueOf(safety.uppercase())
        } catch (e: IllegalArgumentException) {
            throw InvalidParamsException("Invalid safety level: $safety. Valid: HIGH, MEDIUM, DEFAULT")
        }
        robot.navigationSafety = level
        return mapOf("safety" to level.name)
    }

    private fun showTopBar(params: JsonElement?, id: Any?): Any? {
        robot.showTopBar()
        return mapOf("status" to "shown")
    }

    private fun hideTopBar(params: JsonElement?, id: Any?): Any? {
        robot.hideTopBar()
        return mapOf("status" to "hidden")
    }

    private fun toggleNavigationBillboard(params: JsonElement?, id: Any?): Any? {
        val obj = params?.asJsonObject ?: throw InvalidParamsException("params required")
        val hide = obj.get("hide")?.asBoolean ?: throw InvalidParamsException("hide required")
        robot.toggleNavigationBillboard(hide)
        return mapOf("hidden" to hide)
    }

    private fun toggleWakeup(params: JsonElement?, id: Any?): Any? {
        val obj = params?.asJsonObject ?: throw InvalidParamsException("params required")
        val disable = obj.get("disable")?.asBoolean ?: throw InvalidParamsException("disable required")
        robot.toggleWakeup(disable)
        return mapOf("disabled" to disable)
    }

    private fun isWakeupDisabled(params: JsonElement?, id: Any?): Any? {
        return mapOf("disabled" to callBooleanGetter("isWakeupDisabled"))
    }

    private fun setHardButtonsDisabled(params: JsonElement?, id: Any?): Any? {
        val obj = params?.asJsonObject ?: throw InvalidParamsException("params required")
        val disabled = obj.get("disabled")?.asBoolean ?: throw InvalidParamsException("disabled required")
        callBooleanSetter("setHardButtonsDisabled", disabled)
        return mapOf("disabled" to disabled)
    }

    private fun isHardButtonsDisabled(params: JsonElement?, id: Any?): Any? {
        return mapOf("disabled" to callBooleanGetter("isHardButtonsDisabled"))
    }

    private fun setPrivacyMode(params: JsonElement?, id: Any?): Any? {
        val obj = params?.asJsonObject ?: throw InvalidParamsException("params required")
        val on = obj.get("on")?.asBoolean ?: throw InvalidParamsException("on required")
        robot.privacyMode = on
        return mapOf("privacyMode" to on)
    }

    private fun getPrivacyMode(params: JsonElement?, id: Any?): Any? {
        return mapOf("on" to robot.privacyMode)
    }

    private fun restart(params: JsonElement?, id: Any?): Any? {
        robot.restart()
        return mapOf("status" to "restarting")
    }

    private fun showAppList(params: JsonElement?, id: Any?): Any? {
        robot.showAppList()
        return mapOf("status" to "shown")
    }

    private fun setAutoReturn(params: JsonElement?, id: Any?): Any? {
        val obj = params?.asJsonObject ?: throw InvalidParamsException("params required")
        val on = obj.get("on")?.asBoolean ?: throw InvalidParamsException("on required")
        callBooleanSetter("setAutoReturnOn", on)
        return mapOf("autoReturn" to on)
    }

    private fun isAutoReturnOn(params: JsonElement?, id: Any?): Any? {
        return mapOf("on" to callBooleanGetter("isAutoReturnOn"))
    }
}
