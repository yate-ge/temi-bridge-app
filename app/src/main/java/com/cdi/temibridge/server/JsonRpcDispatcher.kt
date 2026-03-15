package com.cdi.temibridge.server

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import com.cdi.temibridge.handler.HandlerRegistry

class JsonRpcDispatcher(
    private val handlerRegistry: HandlerRegistry,
    private val gson: Gson = Gson()
) {
    companion object {
        private const val TAG = "JsonRpcDispatcher"
    }

    fun dispatch(message: String): String? {
        val json: JsonElement
        try {
            json = JsonParser.parseString(message)
        } catch (e: JsonSyntaxException) {
            Log.w(TAG, "Parse error: ${e.message}")
            return gson.toJson(
                JsonRpcResponse.error(null, ErrorCodes.PARSE_ERROR, "Parse error: ${e.message}")
            )
        }

        if (!json.isJsonObject) {
            return gson.toJson(
                JsonRpcResponse.error(null, ErrorCodes.INVALID_REQUEST, "Request must be a JSON object")
            )
        }

        val obj = json.asJsonObject

        val jsonrpc = obj.get("jsonrpc")?.asString
        if (jsonrpc != "2.0") {
            return gson.toJson(
                JsonRpcResponse.error(null, ErrorCodes.INVALID_REQUEST, "jsonrpc must be \"2.0\"")
            )
        }

        val method = obj.get("method")?.asString
        if (method == null) {
            return gson.toJson(
                JsonRpcResponse.error(null, ErrorCodes.INVALID_REQUEST, "Missing method")
            )
        }

        val params = obj.get("params")
        val id = obj.get("id")

        // If no id, this is a notification from client — no response needed
        if (id == null || id.isJsonNull) {
            try {
                handlerRegistry.handle(method, params, null)
            } catch (e: Exception) {
                Log.e(TAG, "Error handling notification $method", e)
            }
            return null
        }

        val idValue: Any = when {
            id.isJsonPrimitive && id.asJsonPrimitive.isNumber -> id.asLong
            id.isJsonPrimitive && id.asJsonPrimitive.isString -> id.asString
            else -> id.toString()
        }

        return try {
            val result = handlerRegistry.handle(method, params, idValue)
            gson.toJson(JsonRpcResponse.success(idValue, result))
        } catch (e: MethodNotFoundException) {
            gson.toJson(
                JsonRpcResponse.error(idValue, ErrorCodes.METHOD_NOT_FOUND, "Method not found: $method")
            )
        } catch (e: InvalidParamsException) {
            gson.toJson(
                JsonRpcResponse.error(idValue, ErrorCodes.INVALID_PARAMS, e.message ?: "Invalid params")
            )
        } catch (e: SdkErrorException) {
            gson.toJson(
                JsonRpcResponse.error(idValue, ErrorCodes.SDK_ERROR, e.message ?: "SDK error")
            )
        } catch (e: PermissionDeniedException) {
            gson.toJson(
                JsonRpcResponse.error(idValue, ErrorCodes.PERMISSION_DENIED, e.message ?: "Permission denied")
            )
        } catch (e: RobotNotReadyException) {
            gson.toJson(
                JsonRpcResponse.error(idValue, ErrorCodes.ROBOT_NOT_READY, e.message ?: "Robot not ready")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error handling $method", e)
            gson.toJson(
                JsonRpcResponse.error(idValue, ErrorCodes.SDK_ERROR, "Internal error: ${e.message}")
            )
        }
    }
}

class MethodNotFoundException(method: String) : Exception("Method not found: $method")
class InvalidParamsException(message: String) : Exception(message)
class SdkErrorException(message: String) : Exception(message)
class PermissionDeniedException(message: String) : Exception(message)
class RobotNotReadyException(message: String = "Robot not ready") : Exception(message)
