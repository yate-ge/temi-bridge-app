package com.cdi.temibridge.server

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val method: String,
    val params: JsonElement? = null,
    val id: Any? = null
)

data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val result: Any? = null,
    val error: JsonRpcError? = null,
    val id: Any? = null
) {
    companion object {
        fun success(id: Any?, result: Any? = true): JsonRpcResponse =
            JsonRpcResponse(result = result, id = id)

        fun error(id: Any?, code: Int, message: String, data: Any? = null): JsonRpcResponse =
            JsonRpcResponse(error = JsonRpcError(code, message, data), id = id)
    }
}

data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: Any? = null
)

data class JsonRpcNotification(
    val jsonrpc: String = "2.0",
    val method: String,
    val params: Any? = null
)

object ErrorCodes {
    const val PARSE_ERROR = -32700
    const val INVALID_REQUEST = -32600
    const val METHOD_NOT_FOUND = -32601
    const val INVALID_PARAMS = -32602
    const val SDK_ERROR = -32001
    const val PERMISSION_DENIED = -32002
    const val ROBOT_NOT_READY = -32003
    const val MEDIA_ERROR = -32004
}
