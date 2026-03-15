package com.cdi.temibridge

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.WebView
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.robotemi.sdk.Robot
import com.robotemi.sdk.listeners.OnRobotReadyListener
import com.cdi.temibridge.event.EventBridge
import com.cdi.temibridge.handler.*
import com.cdi.temibridge.media.AudioCapturePipeline
import com.cdi.temibridge.media.AudioPlaybackPipeline
import com.cdi.temibridge.media.StreamType
import com.cdi.temibridge.media.VideoPipeline
import com.cdi.temibridge.server.BridgeWebSocketClient
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
        private const val PERMISSIONS_REQUEST_CODE = 100
        private const val PREFS_NAME = "temi_bridge_prefs"
        private const val PREF_MODE = "mode"           // "server" or "client"
        private const val PREF_BRAIN_URL = "brain_url"
    }

    private lateinit var robot: Robot
    private lateinit var connectionManager: ConnectionManager
    private lateinit var handlerRegistry: HandlerRegistry
    private lateinit var dispatcher: JsonRpcDispatcher
    private lateinit var eventBridge: EventBridge
    private lateinit var mediaControlHandler: MediaControlHandler
    private lateinit var prefs: SharedPreferences

    private var server: BridgeWebSocketServer? = null
    private var client: BridgeWebSocketClient? = null
    private var videoPipeline: VideoPipeline? = null
    private var audioCapturePipeline: AudioCapturePipeline? = null
    private var audioPlaybackPipeline: AudioPlaybackPipeline? = null

    private var bridgeStarted = false

    // UI elements
    private lateinit var configPanel: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var modeGroup: RadioGroup
    private lateinit var radioServer: RadioButton
    private lateinit var radioClient: RadioButton
    private lateinit var clientConfig: LinearLayout
    private lateinit var serverDesc: TextView
    private lateinit var brainUrlInput: EditText
    private lateinit var btnStart: Button
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Bind UI
        configPanel = findViewById(R.id.configPanel)
        statusText = findViewById(R.id.statusText)
        modeGroup = findViewById(R.id.modeGroup)
        radioServer = findViewById(R.id.radioServer)
        radioClient = findViewById(R.id.radioClient)
        clientConfig = findViewById(R.id.clientConfig)
        serverDesc = findViewById(R.id.serverDesc)
        brainUrlInput = findViewById(R.id.brainUrlInput)
        btnStart = findViewById(R.id.btnStart)
        webView = findViewById(R.id.webView)

        // Restore saved config
        val savedMode = prefs.getString(PREF_MODE, "server")
        val savedUrl = prefs.getString(PREF_BRAIN_URL, "") ?: ""
        if (savedMode == "client") {
            radioClient.isChecked = true
            clientConfig.visibility = View.VISIBLE
            serverDesc.visibility = View.GONE
        }
        brainUrlInput.setText(savedUrl)

        // Mode toggle
        modeGroup.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.radioClient) {
                clientConfig.visibility = View.VISIBLE
                serverDesc.visibility = View.GONE
            } else {
                clientConfig.visibility = View.GONE
                serverDesc.visibility = View.VISIBLE
            }
        }

        // Start button
        btnStart.setOnClickListener { onStartClicked() }

        // Init robot and handlers (but don't start bridge yet)
        robot = Robot.getInstance()
        connectionManager = ConnectionManager()
        handlerRegistry = HandlerRegistry()

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

        DisplayHandler(this, webView, statusText).register(handlerRegistry)

        BridgeHandler(handlerRegistry).register(handlerRegistry)

        dispatcher = JsonRpcDispatcher(handlerRegistry)
        eventBridge = EventBridge(robot, connectionManager)

        // Request permissions
        requestMediaPermissions()

        Log.i(TAG, "TemiBridge initialized with ${handlerRegistry.getMethods().size} methods")

        // Support auto-start via intent extras:
        //   adb shell am start -n com.cdi.temibridge/.MainActivity --es mode client --es brain_url ws://IP:PORT
        //   adb shell am start -n com.cdi.temibridge/.MainActivity --es mode server
        val intentMode = intent.getStringExtra("mode")
        val intentBrainUrl = intent.getStringExtra("brain_url")
        if (intentMode != null) {
            val isClient = intentMode == "client"
            val url = intentBrainUrl ?: savedUrl
            if (isClient && url.isNotEmpty()) {
                prefs.edit().putString(PREF_MODE, "client").putString(PREF_BRAIN_URL, url).apply()
                configPanel.visibility = View.GONE
                statusText.visibility = View.VISIBLE
                startBridge(true, url)
            } else if (!isClient) {
                prefs.edit().putString(PREF_MODE, "server").apply()
                configPanel.visibility = View.GONE
                statusText.visibility = View.VISIBLE
                startBridge(false, "")
            }
        }
    }

    private fun onStartClicked() {
        val isClientMode = radioClient.isChecked
        val brainUrl = brainUrlInput.text.toString().trim()

        if (isClientMode && brainUrl.isEmpty()) {
            brainUrlInput.error = "请输入 Brain 地址"
            return
        }

        // Save config
        prefs.edit()
            .putString(PREF_MODE, if (isClientMode) "client" else "server")
            .putString(PREF_BRAIN_URL, brainUrl)
            .apply()

        // Switch to status view
        configPanel.visibility = View.GONE
        statusText.visibility = View.VISIBLE

        startBridge(isClientMode, brainUrl)
    }

    private fun startBridge(isClientMode: Boolean, brainUrl: String) {
        if (bridgeStarted) return
        bridgeStarted = true

        robot.addOnRobotReadyListener(this)
        eventBridge.registerAll()

        if (isClientMode) {
            // Client mode: connect to external brain
            client = BridgeWebSocketClient(brainUrl, dispatcher, connectionManager).also { c ->
                c.onBinaryFromBrain = { buffer ->
                    audioPlaybackPipeline?.feedFrame(buffer)
                }
                c.onConnected = { runOnUiThread { updateStatus(isClientMode, brainUrl) } }
                c.onDisconnected = { runOnUiThread { updateStatus(isClientMode, brainUrl) } }
            }
            client?.start()
            Log.i(TAG, "Client mode: connecting to $brainUrl")
        } else {
            // Server mode: listen for connections
            server = BridgeWebSocketServer(WS_PORT, dispatcher, connectionManager).also { srv ->
                srv.onBinaryMessage = { _, buffer ->
                    if (buffer.remaining() >= 4) {
                        val streamType = buffer.get(buffer.position())
                        if (streamType == StreamType.AUDIO_OPUS_IN) {
                            audioPlaybackPipeline?.feedFrame(buffer)
                        }
                    }
                }
            }
            server?.start()
            Log.i(TAG, "Server mode: listening on port $WS_PORT")
        }

        // Start foreground service
        val serviceIntent = Intent(this, BridgeForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        updateStatus(isClientMode, brainUrl)
    }

    override fun onDestroy() {
        super.onDestroy()
        videoPipeline?.stop()
        audioCapturePipeline?.stop()
        audioPlaybackPipeline?.stop()
        eventBridge.unregisterAll()
        robot.removeOnRobotReadyListener(this)
        try {
            server?.stop(1000)
            server = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping server", e)
        }
        client?.stop()
        client = null
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
        }
    }

    // --- Permissions ---

    private fun requestMediaPermissions() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.CAMERA)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.RECORD_AUDIO)
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), PERMISSIONS_REQUEST_CODE)
        } else {
            initMediaPipelines()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            initMediaPipelines()
        }
    }

    private fun initMediaPipelines() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            videoPipeline = VideoPipeline(this, this) { frame ->
                connectionManager.sendToAll(frame)
            }
            mediaControlHandler.videoPipeline = videoPipeline
            Log.i(TAG, "Video pipeline initialized")
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            audioCapturePipeline = AudioCapturePipeline { frame ->
                connectionManager.sendToAll(frame)
            }
            mediaControlHandler.audioCapturePipeline = audioCapturePipeline
            Log.i(TAG, "Audio capture pipeline initialized")
        }

        audioPlaybackPipeline = AudioPlaybackPipeline()
        mediaControlHandler.audioPlaybackPipeline = audioPlaybackPipeline
        Log.i(TAG, "Audio playback pipeline initialized")
    }

    // --- Status ---

    private fun updateStatus(isClientMode: Boolean, brainUrl: String) {
        val methods = handlerRegistry.getMethods().size
        val video = if (videoPipeline != null) "ready" else "no perm"
        val audio = if (audioCapturePipeline != null) "ready" else "no perm"

        val modeInfo = if (isClientMode) {
            val connected = client?.isConnected() == true
            val state = if (connected) "connected" else "reconnecting..."
            "Client Mode → $brainUrl\nStatus: $state"
        } else {
            val ip = getLocalIpAddress() ?: "unknown"
            "Server Mode\nws://$ip:$WS_PORT"
        }

        runOnUiThread {
            statusText.text = "Temi Bridge\n\n$modeInfo\n\n${methods} methods\nVideo: $video | Audio: $audio"
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
