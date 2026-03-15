package com.cdi.temibridge.handler

import com.google.gson.JsonElement
import com.robotemi.sdk.Robot
import com.cdi.temibridge.server.InvalidParamsException

class KioskHandler(private val robot: Robot) {

    fun register(registry: HandlerRegistry) {
        registry.register("kiosk.requestToBeKioskApp", ::requestToBeKioskApp)
        registry.register("kiosk.isSelectedKioskApp", ::isSelectedKioskApp)
        registry.register("kiosk.setKioskMode", ::setKioskMode)
        registry.register("kiosk.isKioskModeOn", ::isKioskModeOn)
    }

    private fun requestToBeKioskApp(params: JsonElement?, id: Any?): Any? {
        robot.requestToBeKioskApp()
        return mapOf("status" to "requested")
    }

    private fun isSelectedKioskApp(params: JsonElement?, id: Any?): Any? {
        return mapOf("isKiosk" to robot.isSelectedKioskApp())
    }

    private fun setKioskMode(params: JsonElement?, id: Any?): Any? {
        val obj = params?.asJsonObject ?: throw InvalidParamsException("params required")
        val on = obj.get("on")?.asBoolean ?: throw InvalidParamsException("on required")
        if (on) {
            robot.requestToBeKioskApp()
        }
        return mapOf("kioskMode" to on)
    }

    private fun isKioskModeOn(params: JsonElement?, id: Any?): Any? {
        return mapOf("on" to robot.isSelectedKioskApp())
    }
}
