package com.cdi.temibridge.handler

import com.google.gson.JsonElement

class BridgeHandler(private val registry: HandlerRegistry) {

    companion object {
        const val VERSION = "1.0.0"
    }

    fun register(registry: HandlerRegistry) {
        registry.register("bridge.getCapabilities", ::getCapabilities)
        registry.register("bridge.getVersion", ::getVersion)
        registry.register("bridge.ping", ::ping)
    }

    private fun getCapabilities(params: JsonElement?, id: Any?): Any? {
        val methods = registry.getMethods().sorted()
        val events = listOf(
            "event.navigation.goToLocationStatusChanged",
            "event.navigation.distanceToLocationChanged",
            "event.movement.statusChanged",
            "event.speech.ttsStatusChanged",
            "event.speech.asrResult",
            "event.speech.wakeupWord",
            "event.speech.conversationViewAttached",
            "event.speech.nluResult",
            "event.follow.beWithMeStatusChanged",
            "event.follow.constraintBeWithStatusChanged",
            "event.follow.detectionStateChanged",
            "event.telepresence.statusChanged",
            "event.system.robotReady",
            "event.system.batteryStatusChanged",
            "event.system.privacyModeChanged",
            "event.face.recognitionResult",
            "event.navigation.reposeStatusChanged",
            "event.navigation.locationsUpdated",
            "event.navigation.currentFloorChanged",
            "event.system.userInteraction",
            "event.system.disabledFeatureListUpdated"
        )
        return mapOf(
            "methods" to methods,
            "events" to events,
            "version" to VERSION,
            "protocol" to "JSON-RPC 2.0",
            "mediaProtocol" to mapOf(
                "headerSize" to 4,
                "streamTypes" to mapOf(
                    "0x01" to "H.264 video",
                    "0x02" to "Opus audio out",
                    "0x03" to "Opus audio in"
                )
            )
        )
    }

    private fun getVersion(params: JsonElement?, id: Any?): Any? {
        return mapOf("version" to VERSION, "sdk" to "temi-sdk-0.10.77")
    }

    private fun ping(params: JsonElement?, id: Any?): Any? {
        return mapOf("pong" to System.currentTimeMillis())
    }
}
