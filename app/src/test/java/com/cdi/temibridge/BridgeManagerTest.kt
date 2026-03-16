package com.cdi.temibridge

import com.cdi.temibridge.server.BridgeWebSocketClient
import com.cdi.temibridge.server.BridgeWebSocketServer
import com.cdi.temibridge.server.ConnectionManager
import com.cdi.temibridge.server.JsonRpcDispatcher
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import java.nio.ByteBuffer

class BridgeManagerTest {

    private lateinit var connectionManager: ConnectionManager
    private lateinit var dispatcher: JsonRpcDispatcher
    private val onBinaryMessage: (ByteBuffer) -> Unit = {}

    private lateinit var mockServerFactory: (Int, JsonRpcDispatcher, ConnectionManager) -> BridgeWebSocketServer
    private lateinit var mockClientFactory: (String, JsonRpcDispatcher, ConnectionManager) -> BridgeWebSocketClient
    private lateinit var mockServer: BridgeWebSocketServer
    private lateinit var mockClient: BridgeWebSocketClient

    @Before
    fun setUp() {
        connectionManager = mock()
        dispatcher = mock()
        mockServer = mock()
        mockClient = mock()
        mockServerFactory = mock {
            on { invoke(any(), any(), any()) } doReturn mockServer
        }
        mockClientFactory = mock {
            on { invoke(any(), any(), any()) } doReturn mockClient
        }
    }

    @Test
    fun `server is started on init`() {
        val manager = BridgeManager(
            port = 8175,
            dispatcher = dispatcher,
            connectionManager = connectionManager,
            onBinaryMessage = onBinaryMessage,
            serverFactory = mockServerFactory,
            clientFactory = mockClientFactory
        )

        verify(mockServerFactory).invoke(eq(8175), eq(dispatcher), eq(connectionManager))
        verify(mockServer).start()
        assertNotNull(manager.server)
    }

    @Test
    fun `client is not started on init when autoConnect is false`() {
        val manager = BridgeManager(
            port = 8175,
            dispatcher = dispatcher,
            connectionManager = connectionManager,
            onBinaryMessage = onBinaryMessage,
            serverFactory = mockServerFactory,
            clientFactory = mockClientFactory
        )

        assertNull(manager.client)
        verifyNoInteractions(mockClientFactory)
    }

    @Test
    fun `client is started on init when autoConnect is true and url is set`() {
        val manager = BridgeManager(
            port = 8175,
            dispatcher = dispatcher,
            connectionManager = connectionManager,
            onBinaryMessage = onBinaryMessage,
            autoConnectUrl = "ws://192.168.1.100:8080",
            serverFactory = mockServerFactory,
            clientFactory = mockClientFactory
        )

        verify(mockClientFactory).invoke(eq("ws://192.168.1.100:8080"), eq(dispatcher), eq(connectionManager))
        verify(mockClient).start()
        assertNotNull(manager.client)
    }

    @Test
    fun `startClient starts client connection`() {
        val manager = BridgeManager(
            port = 8175,
            dispatcher = dispatcher,
            connectionManager = connectionManager,
            onBinaryMessage = onBinaryMessage,
            serverFactory = mockServerFactory,
            clientFactory = mockClientFactory
        )

        manager.startClient("ws://brain:8080")

        verify(mockClientFactory).invoke(eq("ws://brain:8080"), eq(dispatcher), eq(connectionManager))
        verify(mockClient).start()
        assertNotNull(manager.client)
    }

    @Test
    fun `stopClient stops client without affecting server`() {
        val manager = BridgeManager(
            port = 8175,
            dispatcher = dispatcher,
            connectionManager = connectionManager,
            onBinaryMessage = onBinaryMessage,
            serverFactory = mockServerFactory,
            clientFactory = mockClientFactory
        )

        manager.startClient("ws://brain:8080")
        manager.stopClient()

        verify(mockClient).stop()
        assertNull(manager.client)
        // Server should never be stopped
        verify(mockServer, never()).stop(any())
    }

    @Test
    fun `startClient when already connected does nothing`() {
        whenever(mockClient.isConnected()).thenReturn(true)

        val manager = BridgeManager(
            port = 8175,
            dispatcher = dispatcher,
            connectionManager = connectionManager,
            onBinaryMessage = onBinaryMessage,
            serverFactory = mockServerFactory,
            clientFactory = mockClientFactory
        )

        manager.startClient("ws://brain:8080")
        // Try to start again with same URL
        manager.startClient("ws://brain:8080")

        // Factory should only be called once
        verify(mockClientFactory, times(1)).invoke(any(), any(), any())
    }

    @Test
    fun `destroy stops both server and client`() {
        val manager = BridgeManager(
            port = 8175,
            dispatcher = dispatcher,
            connectionManager = connectionManager,
            onBinaryMessage = onBinaryMessage,
            serverFactory = mockServerFactory,
            clientFactory = mockClientFactory
        )

        manager.startClient("ws://brain:8080")
        manager.destroy()

        verify(mockClient).stop()
        verify(mockServer).stop(any())
    }
}
