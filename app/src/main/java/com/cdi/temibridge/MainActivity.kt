package com.cdi.temibridge

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.robotemi.sdk.Robot
import com.robotemi.sdk.listeners.OnRobotReadyListener
import com.cdi.temibridge.event.EventBridge
import com.cdi.temibridge.handler.*
import com.cdi.temibridge.media.VideoPipeline
import com.cdi.temibridge.server.BridgeWebSocketServer
import com.cdi.temibridge.server.ConnectionManager
import com.cdi.temibridge.server.JsonRpcDispatcher
import com.cdi.temibridge.service.BridgeForegroundService
import java.net.Inet4Address
import java.net.NetworkInterface

class MainActivity : AppCompatActivity(), OnRobotReadyListener {

    companion object {
        private const val TAG = "MainActivity"
        private const val WS_PORT = 8175
        private const val CAMERA_PERMISSION_CODE = 100
    }

    private lateinit var robot: Robot
    private lateinit var connectionManager: ConnectionManager
    private lateinit var handlerRegistry: HandlerRegistry
    private lateinit var dispatcher: JsonRpcDispatcher
    private var server: BridgeWebSocketServer? = null
    private lateinit var eventBridge: EventBridge
    private var videoPipeline: VideoPipeline? = null
    private lateinit var mediaControlHandler: MediaControlHandler

    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)

        robot = Robot.getInstance()
        connectionManager = ConnectionManager()
        handlerRegistry = HandlerRegistry()

        // Register all handlers
        NavigationHandler(robot).register(handlerRegistry)
        MovementHandler(robot).register(handlerRegistry)
        SpeechHandler(robot).register(handlerRegistry)
        FollowHandler(robot).register(handlerRegistry)
        TelepresenceHandler(robot).register(handlerRegistry)
        SystemHandler(robot).register(handlerRegistry)
        KioskHandler(robot).register(handlerRegistry)
        PermissionHandler(robot).register(handlerRegistry)
        FaceHandler(robot).register(handlerRegistry)

        mediaControlHandler = MediaControlHandler()
        mediaControlHandler.register(handlerRegistry)

        BridgeHandler(handlerRegistry).register(handlerRegistry)

        dispatcher = JsonRpcDispatcher(handlerRegistry)

        // Create event bridge
        eventBridge = EventBridge(robot, connectionManager)

        // Request camera permission and init video pipeline
        requestCameraPermission()

        Log.i(TAG, "TemiBridge initialized with ${handlerRegistry.getMethods().size} methods")
    }

    override fun onResume() {
        super.onResume()
        robot.addOnRobotReadyListener(this)
        eventBridge.registerAll()

        // Create and start WebSocket server (recreate each time to avoid port binding issues)
        if (server == null) {
            server = BridgeWebSocketServer(WS_PORT, dispatcher, connectionManager)
            server?.start()
        }
        updateStatus()

        // Start foreground service
        val serviceIntent = Intent(this, BridgeForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        Log.i(TAG, "Bridge started on port $WS_PORT")
    }

    override fun onPause() {
        super.onPause()
        robot.removeOnRobotReadyListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        videoPipeline?.stop()
        eventBridge.unregisterAll()
        try {
            server?.stop(1000)
            server = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping server", e)
        }
        stopService(Intent(this, BridgeForegroundService::class.java))
        Log.i(TAG, "Bridge stopped")
    }

    override fun onRobotReady(isReady: Boolean) {
        if (isReady) {
            try {
                val activityInfo = packageManager.getActivityInfo(componentName, PackageManager.GET_META_DATA)
                robot.onStart(activityInfo)
            } catch (e: PackageManager.NameNotFoundException) {
                Log.e(TAG, "Failed to start robot", e)
            }
            robot.hideTopBar()
            updateStatus()
        }
    }

    private fun requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_CODE
            )
        } else {
            initVideoPipeline()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initVideoPipeline()
            } else {
                Log.w(TAG, "Camera permission denied — video streaming unavailable")
            }
        }
    }

    private fun initVideoPipeline() {
        videoPipeline = VideoPipeline(this, this) { frame ->
            connectionManager.sendToAll(frame)
        }
        mediaControlHandler.videoPipeline = videoPipeline
        Log.i(TAG, "Video pipeline initialized")
    }

    private fun updateStatus() {
        val ip = getLocalIpAddress() ?: "unknown"
        val methods = handlerRegistry.getMethods().size
        val videoStatus = if (videoPipeline != null) "ready" else "no permission"
        runOnUiThread {
            statusText.text = "Temi Bridge\nws://$ip:$WS_PORT\n${methods} methods registered\nJSON-RPC 2.0\nVideo: $videoStatus"
        }
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get IP address", e)
        }
        return null
    }
}
