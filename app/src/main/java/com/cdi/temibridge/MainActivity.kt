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
import androidx.appcompat.app.AlertDialog
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
        private const val PREF_BRAIN_URL = "brain_url"
        private const val PREF_AUTO_CONNECT = "auto_connect"
    }

    private lateinit var robot: Robot
    private lateinit var connectionManager: ConnectionManager
    private lateinit var handlerRegistry: HandlerRegistry
    private lateinit var dispatcher: JsonRpcDispatcher
    private lateinit var eventBridge: EventBridge
    private lateinit var mediaControlHandler: MediaControlHandler
    private lateinit var prefs: SharedPreferences

    private var bridgeManager: BridgeManager? = null
    private var videoPipeline: VideoPipeline? = null
    private var audioCapturePipeline: AudioCapturePipeline? = null
    private var audioPlaybackPipeline: AudioPlaybackPipeline? = null

    // UI elements
    private lateinit var statusText: TextView
    private lateinit var webView: WebView
    private lateinit var statusPanel: LinearLayout
    private lateinit var btnConfig: Button

    private var configDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Bind UI
        statusText = findViewById(R.id.statusText)
        webView = findViewById(R.id.webView)
        statusPanel = findViewById(R.id.statusPanel)
        btnConfig = findViewById(R.id.btnConfig)

        btnConfig.setOnClickListener { showConfigDialog() }

        // Init robot and handlers
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

        DisplayHandler(this, webView, statusPanel).register(handlerRegistry)
        BridgeHandler(handlerRegistry).register(handlerRegistry)

        dispatcher = JsonRpcDispatcher(handlerRegistry)
        eventBridge = EventBridge(robot, connectionManager)

        // Request permissions
        requestMediaPermissions()

        Log.i(TAG, "TemiBridge initialized with ${handlerRegistry.getMethods().size} methods")

        // Start bridge (server always starts)
        startBridge()
    }

    private fun startBridge() {
        robot.addOnRobotReadyListener(this)
        eventBridge.registerAll()

        // Determine auto-connect URL
        val intentBrainUrl = intent.getStringExtra("brain_url")
        val savedUrl = prefs.getString(PREF_BRAIN_URL, "") ?: ""
        val autoConnect = prefs.getBoolean(PREF_AUTO_CONNECT, false)

        val autoConnectUrl = when {
            !intentBrainUrl.isNullOrEmpty() -> {
                // Intent-provided URL always auto-connects and saves preference
                prefs.edit()
                    .putString(PREF_BRAIN_URL, intentBrainUrl)
                    .putBoolean(PREF_AUTO_CONNECT, true)
                    .apply()
                intentBrainUrl
            }
            autoConnect && savedUrl.isNotEmpty() -> savedUrl
            else -> null
        }

        bridgeManager = BridgeManager(
            port = WS_PORT,
            dispatcher = dispatcher,
            connectionManager = connectionManager,
            onBinaryMessage = { buffer ->
                if (buffer.remaining() >= 4) {
                    val streamType = buffer.get(buffer.position())
                    if (streamType == StreamType.AUDIO_OPUS_IN) {
                        audioPlaybackPipeline?.feedFrame(buffer)
                    }
                }
            },
            autoConnectUrl = autoConnectUrl
        ).also { mgr ->
            mgr.onClientConnected = { runOnUiThread { updateStatus() } }
            mgr.onClientDisconnected = { runOnUiThread { updateStatus() } }
        }

        // Start foreground service
        val serviceIntent = Intent(this, BridgeForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        updateStatus()
    }

    private fun showConfigDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_bridge_config, null)
        val serverStatus = dialogView.findViewById<TextView>(R.id.dialogServerStatus)
        val brainUrlInput = dialogView.findViewById<EditText>(R.id.dialogBrainUrl)
        val btnConnect = dialogView.findViewById<Button>(R.id.dialogBtnConnect)
        val btnDisconnect = dialogView.findViewById<Button>(R.id.dialogBtnDisconnect)
        val clientStatus = dialogView.findViewById<TextView>(R.id.dialogClientStatus)
        val autoConnectCheck = dialogView.findViewById<CheckBox>(R.id.dialogAutoConnect)

        // Populate current state
        val ip = getLocalIpAddress() ?: "unknown"
        val clientCount = connectionManager.getClientCount()
        serverStatus.text = "ws://$ip:$WS_PORT  |  $clientCount client(s)"

        val savedUrl = prefs.getString(PREF_BRAIN_URL, "") ?: ""
        brainUrlInput.setText(savedUrl)
        autoConnectCheck.isChecked = prefs.getBoolean(PREF_AUTO_CONNECT, false)

        fun updateClientStatus() {
            val connected = bridgeManager?.isClientConnected() == true
            clientStatus.text = if (connected) "Connected" else "Not connected"
            btnConnect.isEnabled = !connected
            btnDisconnect.isEnabled = connected
        }
        updateClientStatus()

        btnConnect.setOnClickListener {
            val url = brainUrlInput.text.toString().trim()
            if (url.isEmpty()) {
                brainUrlInput.error = "Enter brain URL"
                return@setOnClickListener
            }
            prefs.edit().putString(PREF_BRAIN_URL, url).apply()
            bridgeManager?.startClient(url)
            // Delay status update to allow connection
            brainUrlInput.postDelayed({ updateClientStatus(); updateStatus() }, 500)
        }

        btnDisconnect.setOnClickListener {
            bridgeManager?.stopClient()
            updateClientStatus()
            updateStatus()
        }

        autoConnectCheck.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(PREF_AUTO_CONNECT, isChecked).apply()
        }

        configDialog = AlertDialog.Builder(this)
            .setTitle("Temi Bridge Config")
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .create()
        configDialog?.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        videoPipeline?.stop()
        audioCapturePipeline?.stop()
        audioPlaybackPipeline?.stop()
        eventBridge.unregisterAll()
        robot.removeOnRobotReadyListener(this)
        bridgeManager?.destroy()
        bridgeManager = null
        configDialog?.dismiss()
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

    private fun updateStatus() {
        val methods = handlerRegistry.getMethods().size
        val video = if (videoPipeline != null) "ready" else "no perm"
        val audio = if (audioCapturePipeline != null) "ready" else "no perm"

        val ip = getLocalIpAddress() ?: "unknown"
        val serverInfo = "Server: ws://$ip:$WS_PORT"

        val clientInfo = if (bridgeManager?.isClientConnected() == true) {
            val url = prefs.getString(PREF_BRAIN_URL, "") ?: ""
            "Brain: connected → $url"
        } else if (bridgeManager?.client != null) {
            "Brain: reconnecting..."
        } else {
            "Brain: not connected"
        }

        runOnUiThread {
            statusText.text = "Temi Bridge\n\n$serverInfo\n$clientInfo\n\n${methods} methods\nVideo: $video | Audio: $audio"
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
