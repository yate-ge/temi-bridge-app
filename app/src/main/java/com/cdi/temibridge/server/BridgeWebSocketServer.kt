package com.cdi.temibridge.server

import android.util.Log
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress
import java.nio.ByteBuffer

class BridgeWebSocketServer(
    port: Int,
    private val dispatcher: JsonRpcDispatcher,
    private val connectionManager: ConnectionManager
) : WebSocketServer(InetSocketAddress(port)) {

    companion object {
        private const val TAG = "BridgeWebSocketServer"
    }

    init {
        isReuseAddr = true
    }

    var onBinaryMessage: ((WebSocket, ByteBuffer) -> Unit)? = null

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        connectionManager.addClient(conn)
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
        connectionManager.removeClient(conn)
    }

    override fun onMessage(conn: WebSocket, message: String) {
        Log.d(TAG, "Received: $message")
        val response = dispatcher.dispatch(message)
        if (response != null) {
            try {
                conn.send(response)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send response", e)
            }
        }
    }

    override fun onMessage(conn: WebSocket, message: ByteBuffer) {
        onBinaryMessage?.invoke(conn, message)
    }

    override fun onError(conn: WebSocket?, ex: Exception) {
        Log.e(TAG, "WebSocket error", ex)
    }

    override fun onStart() {
        Log.i(TAG, "Bridge WebSocket server started on port ${address.port}")
        connectionLostTimeout = 60
    }
}
