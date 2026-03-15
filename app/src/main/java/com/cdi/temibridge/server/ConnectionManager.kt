package com.cdi.temibridge.server

import android.util.Log
import com.google.gson.Gson
import org.java_websocket.WebSocket
import java.util.concurrent.ConcurrentHashMap

class ConnectionManager {

    companion object {
        private const val TAG = "ConnectionManager"
    }

    private val clients = ConcurrentHashMap.newKeySet<WebSocket>()
    private val gson = Gson()

    fun addClient(conn: WebSocket) {
        clients.add(conn)
        Log.i(TAG, "Client connected: ${conn.remoteSocketAddress} (total: ${clients.size})")
    }

    fun removeClient(conn: WebSocket) {
        clients.remove(conn)
        Log.i(TAG, "Client disconnected: ${conn.remoteSocketAddress} (total: ${clients.size})")
    }

    fun sendToAll(message: String) {
        for (client in clients) {
            try {
                if (client.isOpen) {
                    client.send(message)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send to ${client.remoteSocketAddress}", e)
            }
        }
    }

    fun sendToAll(data: ByteArray) {
        for (client in clients) {
            try {
                if (client.isOpen) {
                    client.send(data)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send binary to ${client.remoteSocketAddress}", e)
            }
        }
    }

    fun sendNotification(method: String, params: Any? = null) {
        val notification = JsonRpcNotification(method = method, params = params)
        sendToAll(gson.toJson(notification))
    }

    fun getClientCount(): Int = clients.size

    fun hasClients(): Boolean = clients.isNotEmpty()
}
