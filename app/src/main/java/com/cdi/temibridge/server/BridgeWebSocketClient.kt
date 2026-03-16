package com.cdi.temibridge.server

import android.util.Log
import org.java_websocket.client.WebSocketClient
import org.java_websocket.framing.Framedata
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class BridgeWebSocketClient(
    private val serverUri: String,
    private val dispatcher: JsonRpcDispatcher,
    private val connectionManager: ConnectionManager
) {
    companion object {
        private const val TAG = "BridgeWSClient"
        private const val INITIAL_RETRY_DELAY_MS = 1000L
        private const val MAX_RETRY_DELAY_MS = 30000L
    }

    private var client: InnerClient? = null
    private val shouldReconnect = AtomicBoolean(false)
    private val retryCount = AtomicInteger(0)
    private var reconnectThread: Thread? = null

    // Dispatch RPC on a separate thread so WebSocket read thread is never blocked
    private val dispatchExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "WSDispatch").apply { isDaemon = true }
    }

    var onConnected: (() -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null

    fun start() {
        shouldReconnect.set(true)
        retryCount.set(0)
        connect()
    }

    fun stop() {
        shouldReconnect.set(false)
        reconnectThread?.interrupt()
        reconnectThread = null
        try {
            client?.closeBlocking()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing client", e)
        }
        client = null
        dispatchExecutor.shutdownNow()
        Log.i(TAG, "Client stopped")
    }

    fun isConnected(): Boolean = client?.isOpen == true

    private fun connect() {
        try {
            val uri = URI(serverUri)
            client = InnerClient(uri)
            // Disable client-side keepalive to avoid conflict with brain server's ping
            // The brain server manages ping/pong; we just need to respond (handled by library)
            client?.connectionLostTimeout = 0
            client?.connect()
            Log.i(TAG, "Connecting to $serverUri ...")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create connection to $serverUri", e)
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        if (!shouldReconnect.get()) return

        val attempt = retryCount.getAndIncrement()
        val delay = minOf(INITIAL_RETRY_DELAY_MS * (1L shl minOf(attempt, 14)), MAX_RETRY_DELAY_MS)
        Log.i(TAG, "Reconnecting in ${delay}ms (attempt ${attempt + 1})...")

        reconnectThread = Thread({
            try {
                Thread.sleep(delay)
                if (shouldReconnect.get()) {
                    connect()
                }
            } catch (e: InterruptedException) {
                // stopped
            }
        }, "WSReconnect").apply { isDaemon = true; start() }
    }

    private inner class InnerClient(uri: URI) : WebSocketClient(uri) {

        override fun onOpen(handshake: ServerHandshake) {
            Log.i(TAG, "Connected to $serverUri (status=${handshake.httpStatus})")
            retryCount.set(0)
            connectionManager.addClient(this)
            onConnected?.invoke()
        }

        override fun onMessage(message: String) {
            Log.d(TAG, "Received: ${message.take(200)}")
            // Dispatch on separate thread to keep WebSocket read thread free for ping/pong
            val ws = this
            dispatchExecutor.execute {
                try {
                    val response = dispatcher.dispatch(message)
                    if (response != null) {
                        try {
                            ws.send(response)
                            Log.d(TAG, "Sent response: ${response.take(200)}")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to send response", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error dispatching message", e)
                }
            }
        }

        override fun onMessage(bytes: ByteBuffer) {
            Log.d(TAG, "Received binary: ${bytes.remaining()} bytes")
            if (bytes.remaining() >= 4) {
                val streamType = bytes.get(bytes.position())
                if (streamType == com.cdi.temibridge.media.StreamType.AUDIO_OPUS_IN) {
                    onBinaryFromBrain?.invoke(bytes)
                }
            }
        }

        override fun onWebsocketPing(conn: org.java_websocket.WebSocket?, f: Framedata?) {
            Log.d(TAG, "Ping received from brain, sending pong")
            super.onWebsocketPing(conn, f)
        }

        override fun onWebsocketPong(conn: org.java_websocket.WebSocket?, f: Framedata?) {
            Log.d(TAG, "Pong received from brain")
            super.onWebsocketPong(conn, f)
        }

        override fun onClose(code: Int, reason: String, remote: Boolean) {
            Log.i(TAG, "Disconnected from $serverUri (code=$code, reason=$reason, remote=$remote)")
            connectionManager.removeClient(this)
            onDisconnected?.invoke()
            scheduleReconnect()
        }

        override fun onError(ex: Exception) {
            Log.e(TAG, "WebSocket client error: ${ex.message}", ex)
        }
    }

    var onBinaryFromBrain: ((ByteBuffer) -> Unit)? = null
}
