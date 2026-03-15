package com.cdi.temibridge.handler

import com.google.gson.JsonElement
import com.robotemi.sdk.Robot
import com.robotemi.sdk.TtsRequest
import com.cdi.temibridge.server.InvalidParamsException

class SpeechHandler(private val robot: Robot) {

    fun register(registry: HandlerRegistry) {
        registry.register("speech.speak", ::speak)
        registry.register("speech.cancelAllTts", ::cancelAllTts)
        registry.register("speech.wakeup", ::wakeup)
        registry.register("speech.askQuestion", ::askQuestion)
        registry.register("speech.finishConversation", ::finishConversation)
        registry.register("speech.getWakeupWord", ::getWakeupWord)
        registry.register("speech.startDefaultNlu", ::startDefaultNlu)
    }

    private fun speak(params: JsonElement?, id: Any?): Any? {
        val obj = params?.asJsonObject ?: throw InvalidParamsException("params required")
        val text = obj.get("text")?.asString ?: throw InvalidParamsException("text required")
        val isShowOnConversationLayer = obj.get("showOnConversationLayer")?.asBoolean ?: true
        val ttsRequest = TtsRequest.create(text, isShowOnConversationLayer)
        robot.speak(ttsRequest)
        return mapOf("status" to "accepted")
    }

    private fun cancelAllTts(params: JsonElement?, id: Any?): Any? {
        robot.cancelAllTtsRequests()
        return mapOf("status" to "cancelled")
    }

    private fun wakeup(params: JsonElement?, id: Any?): Any? {
        robot.wakeup()
        return mapOf("status" to "accepted")
    }

    private fun askQuestion(params: JsonElement?, id: Any?): Any? {
        val obj = params?.asJsonObject ?: throw InvalidParamsException("params required")
        val question = obj.get("question")?.asString ?: throw InvalidParamsException("question required")
        robot.askQuestion(question)
        return mapOf("status" to "accepted")
    }

    private fun finishConversation(params: JsonElement?, id: Any?): Any? {
        robot.finishConversation()
        return mapOf("status" to "finished")
    }

    private fun getWakeupWord(params: JsonElement?, id: Any?): Any? {
        return mapOf("wakeupWord" to robot.wakeupWord)
    }

    private fun startDefaultNlu(params: JsonElement?, id: Any?): Any? {
        robot.startDefaultNlu(robot.wakeupWord)
        return mapOf("status" to "accepted")
    }
}
