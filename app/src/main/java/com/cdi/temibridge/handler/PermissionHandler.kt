package com.cdi.temibridge.handler

import com.google.gson.JsonElement
import com.robotemi.sdk.Robot
import com.robotemi.sdk.permission.Permission
import com.cdi.temibridge.server.InvalidParamsException

class PermissionHandler(private val robot: Robot) {

    fun register(registry: HandlerRegistry) {
        registry.register("permission.checkSelfPermission", ::checkSelfPermission)
        registry.register("permission.requestPermissions", ::requestPermissions)
    }

    private fun checkSelfPermission(params: JsonElement?, id: Any?): Any? {
        val obj = params?.asJsonObject ?: throw InvalidParamsException("params required")
        val permissionStr = obj.get("permission")?.asString ?: throw InvalidParamsException("permission required")
        val permission = parsePermission(permissionStr)
        val result = robot.checkSelfPermission(permission)
        return mapOf("permission" to permissionStr, "granted" to (result == Permission.GRANTED))
    }

    private fun requestPermissions(params: JsonElement?, id: Any?): Any? {
        val obj = params?.asJsonObject ?: throw InvalidParamsException("params required")
        val permissionsArray = obj.getAsJsonArray("permissions")
            ?: throw InvalidParamsException("permissions array required")
        val permissions = permissionsArray.map { parsePermission(it.asString) }
        robot.requestPermissions(permissions, 1)
        return mapOf("status" to "requested")
    }

    private fun parsePermission(name: String): Permission {
        return when (name.lowercase()) {
            "settings" -> Permission.SETTINGS
            "face_recognition" -> Permission.FACE_RECOGNITION
            "map" -> Permission.MAP
            "sequence" -> Permission.SEQUENCE
            else -> throw InvalidParamsException("Unknown permission: $name. Valid: settings, face_recognition, map, sequence")
        }
    }
}
