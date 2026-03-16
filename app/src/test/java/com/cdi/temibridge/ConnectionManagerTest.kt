package com.cdi.temibridge

import com.cdi.temibridge.server.ConnectionManager
import org.java_websocket.WebSocket
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

class ConnectionManagerTest {

    private lateinit var connectionManager: ConnectionManager

    @Before
    fun setUp() {
        connectionManager = ConnectionManager()
    }

    private fun mockWebSocket(): WebSocket {
        return mock {
            on { isOpen } doReturn true
            on { remoteSocketAddress } doReturn java.net.InetSocketAddress("127.0.0.1", 9999)
        }
    }

    @Test
    fun `multiple clients can coexist`() {
        val ws1 = mockWebSocket()
        val ws2 = mockWebSocket()
        val ws3 = mockWebSocket()

        connectionManager.addClient(ws1)
        connectionManager.addClient(ws2)
        connectionManager.addClient(ws3)

        assertEquals(3, connectionManager.getClientCount())
    }

    @Test
    fun `sendToAll reaches all clients`() {
        val ws1 = mockWebSocket()
        val ws2 = mockWebSocket()

        connectionManager.addClient(ws1)
        connectionManager.addClient(ws2)

        connectionManager.sendToAll("test message")

        verify(ws1).send(eq("test message"))
        verify(ws2).send(eq("test message"))
    }

    @Test
    fun `removeClient only removes target`() {
        val ws1 = mockWebSocket()
        val ws2 = mockWebSocket()

        connectionManager.addClient(ws1)
        connectionManager.addClient(ws2)

        connectionManager.removeClient(ws1)

        assertEquals(1, connectionManager.getClientCount())

        connectionManager.sendToAll("after remove")
        verify(ws1, never()).send(eq("after remove"))
        verify(ws2).send(eq("after remove"))
    }
}
