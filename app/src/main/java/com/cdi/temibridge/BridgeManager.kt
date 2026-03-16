package com.cdi.temibridge

import android.util.Log
import com.cdi.temibridge.server.BridgeWebSocketClient
import com.cdi.temibridge.server.BridgeWebSocketServer
import com.cdi.temibridge.server.ConnectionManager
import com.cdi.temibridge.server.JsonRpcDispatcher
import java.nio.ByteBuffer

class BridgeManager(
    port: Int,
    private val dispatcher: JsonRpcDispatcher,
    private val connectionManager: ConnectionManager,
    private val onBinaryMessage: (ByteBuffer) -> Unit,
    autoConnectUrl: String? = null,
    serverFactory: (Int, JsonRpcDispatcher, ConnectionManager) -> BridgeWebSocketServer = ::defaultServerFactory,
    private val clientFactory: (String, JsonRpcDispatcher, ConnectionManager) -> BridgeWebSocketClient = ::defaultClientFactory
) {
    companion object {
        private const val TAG = "BridgeManager"

        private fun defaultServerFactory(
            port: Int,
            dispatcher: JsonRpcDispatcher,
            connectionManager: ConnectionManager
        ): BridgeWebSocketServer = BridgeWebSocketServer(port, dispatcher, connectionManager)

        private fun defaultClientFactory(
            url: String,
            dispatcher: JsonRpcDispatcher,
            connectionManager: ConnectionManager
        ): BridgeWebSocketClient = BridgeWebSocketClient(url, dispatcher, connectionManager)
    }

    val server: BridgeWebSocketServer
    var client: BridgeWebSocketClient? = null
        private set

    var onClientConnected: (() -> Unit)? = null
    var onClientDisconnected: (() -> Unit)? = null

    init {
        server = serverFactory(port, dispatcher, connectionManager).also { srv ->
            srv.onBinaryMessage = { _, buffer -> onBinaryMessage(buffer) }
            srv.start()
        }
        Log.i(TAG, "Server started on port $port")

        if (!autoConnectUrl.isNullOrEmpty()) {
            startClient(autoConnectUrl)
        }
    }

    fun startClient(brainUrl: String) {
        if (client?.isConnected() == true) {
            Log.i(TAG, "Client already connected, ignoring startClient")
            return
        }

        stopClientInternal()

        client = clientFactory(brainUrl, dispatcher, connectionManager).also { c ->
            c.onBinaryFromBrain = { buffer -> onBinaryMessage(buffer) }
            c.onConnected = { onClientConnected?.invoke() }
            c.onDisconnected = { onClientDisconnected?.invoke() }
            c.start()
        }
        Log.i(TAG, "Client connecting to $brainUrl")
    }

    fun stopClient() {
        stopClientInternal()
        Log.i(TAG, "Client stopped")
    }

    fun isClientConnected(): Boolean = client?.isConnected() == true

    fun destroy() {
        stopClientInternal()
        try {
            server.stop(1000)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping server", e)
        }
        Log.i(TAG, "BridgeManager destroyed")
    }

    private fun stopClientInternal() {
        client?.stop()
        client = null
    }
}
