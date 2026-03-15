package com.cdi.temibridge.handler

import com.google.gson.JsonElement
import com.robotemi.sdk.Robot
import com.robotemi.sdk.UserInfo
import com.cdi.temibridge.server.InvalidParamsException

class TelepresenceHandler(private val robot: Robot) {

    fun register(registry: HandlerRegistry) {
        registry.register("telepresence.getAllContacts", ::getAllContacts)
        registry.register("telepresence.start", ::start)
    }

    private fun getAllContacts(params: JsonElement?, id: Any?): Any? {
        val contacts: List<UserInfo> = robot.allContact
        return contacts.map { contact ->
            mapOf(
                "userId" to contact.userId,
                "name" to contact.name,
                "picUrl" to contact.picUrl
            )
        }
    }

    private fun start(params: JsonElement?, id: Any?): Any? {
        val obj = params?.asJsonObject ?: throw InvalidParamsException("params required")
        val displayName = obj.get("displayName")?.asString ?: throw InvalidParamsException("displayName required")
        val peerId = obj.get("peerId")?.asString ?: throw InvalidParamsException("peerId required")
        robot.startTelepresence(displayName, peerId)
        return mapOf("status" to "accepted")
    }
}
