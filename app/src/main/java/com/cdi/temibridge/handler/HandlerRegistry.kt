package com.cdi.temibridge.handler

import com.google.gson.JsonElement
import com.cdi.temibridge.server.MethodNotFoundException

typealias RpcHandler = (params: JsonElement?, id: Any?) -> Any?

class HandlerRegistry {

    private val handlers = mutableMapOf<String, RpcHandler>()

    fun register(method: String, handler: RpcHandler) {
        handlers[method] = handler
    }

    fun handle(method: String, params: JsonElement?, id: Any?): Any? {
        val handler = handlers[method] ?: throw MethodNotFoundException(method)
        return handler(params, id)
    }

    fun getMethods(): Set<String> = handlers.keys.toSet()

    fun hasMethod(method: String): Boolean = handlers.containsKey(method)
}
