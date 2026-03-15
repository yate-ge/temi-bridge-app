# Temi Bridge - Development Guide

## Architecture

```
app/src/main/java/com/cdi/temibridge/
├── TemiBridgeApplication.kt          # Application entry point
├── MainActivity.kt                    # UI, initializes all components, camera permission
│
├── server/
│   ├── JsonRpcModels.kt              # Request/Response/Error/Notification data classes
│   ├── JsonRpcDispatcher.kt          # Parses JSON-RPC messages, routes to handlers
│   ├── BridgeWebSocketServer.kt      # WebSocket server (port 8175), text + binary frames
│   └── ConnectionManager.kt          # Tracks connected clients, broadcasts events/media
│
├── handler/
│   ├── HandlerRegistry.kt            # Method name → handler function registry
│   ├── NavigationHandler.kt          # navigation.* methods
│   ├── MovementHandler.kt            # movement.* methods
│   ├── SpeechHandler.kt              # speech.* methods
│   ├── FollowHandler.kt              # follow.* methods
│   ├── TelepresenceHandler.kt        # telepresence.* methods
│   ├── SystemHandler.kt              # system.* methods
│   ├── KioskHandler.kt               # kiosk.* methods
│   ├── PermissionHandler.kt          # permission.* methods
│   ├── FaceHandler.kt                # face.* methods
│   ├── MediaControlHandler.kt        # media.* methods (start/stop video/audio)
│   └── BridgeHandler.kt              # bridge.* methods (capabilities, version, ping)
│
├── event/
│   └── EventBridge.kt                # Registers all temi SDK listeners → JSON-RPC notifications
│
├── media/
│   ├── MediaFrameHeader.kt           # 4-byte binary frame header encode/decode
│   └── VideoPipeline.kt              # CameraX → MediaCodec H.264 → binary WebSocket frames
│
└── service/
    └── BridgeForegroundService.kt     # Keeps bridge alive when app is in background
```

## Data Flow

### JSON-RPC Request/Response

```
Client (text WebSocket message)
  → BridgeWebSocketServer.onMessage()
    → JsonRpcDispatcher.dispatch()
      → Validates JSON-RPC 2.0 format
      → HandlerRegistry.handle(method, params, id)
        → Specific handler (e.g., NavigationHandler.goTo)
          → Calls temi SDK Robot API
          → Returns result map
      → Wraps in JsonRpcResponse
    → Sends JSON response to the requesting client only
```

### Event Notifications

```
Temi SDK callback (e.g., onTtsStatusChanged)
  → EventBridge listener method
    → ConnectionManager.sendNotification()
      → Broadcasts JSON-RPC notification to ALL connected clients
```

### Video Streaming

```
CameraX ImageAnalysis (YUV_420_888 frames @ ~20fps)
  → VideoPipeline.feedEncoder()
    → YUV420 → NV12 conversion
    → MediaCodec H.264 hardware encoder
  → VideoPipeline.drainEncoder()
    → Encoded NAL units (SPS/PPS, IDR, P-frames)
    → MediaFrameHeader (4-byte header: type=0x01, flags, seq)
    → ConnectionManager.sendToAll(binary)
      → Binary WebSocket frame to ALL connected clients
```

**Stream lifecycle:**
1. Client sends `media.startVideoStream` → binds CameraX, starts MediaCodec encoder
2. Binary frames pushed to all clients continuously
3. Client sends `media.stopVideoStream` → unbinds camera, releases encoder
4. Camera auto-releases when no clients are subscribed

**H.264 stream structure:**
- Frame 0: SPS + PPS (codec config, marked as keyframe)
- Frame 1: IDR keyframe
- Frames 2-N: P-frames
- Keyframes every ~2 seconds (configurable via `I_FRAME_INTERVAL`)

## Key Design Decisions

### Per-client responses vs broadcast

The old `TemiWebsocketServer` used `broadcast()` for everything. The new architecture:
- **JSON-RPC responses** → sent only to the requesting client
- **Event notifications** → broadcast to all clients
- **Video/audio binary frames** → broadcast to all clients

### Port reuse

`BridgeWebSocketServer` sets `isReuseAddr = true` to handle app restart without waiting for TCP TIME_WAIT. This prevents `EADDRINUSE` errors when the app is force-stopped and restarted quickly.

### Kotlin metadata compatibility

The temi SDK 0.10.77 is compiled with old Kotlin (metadata v1.1.16). Some boolean properties (`isTrackUserOn`, `isAutoReturnOn`, `isWakeupDisabled`, `setHardButtonsDisabled`) are not accessible via normal Kotlin property syntax from newer Kotlin versions. These are accessed via Java reflection:

```kotlin
private fun callBooleanGetter(name: String): Boolean {
    return robot.javaClass.getMethod(name).invoke(robot) as Boolean
}
```

### Handler registration pattern

Each handler class registers its methods with the `HandlerRegistry` in its `register()` method. The `HandlerRegistry` is a simple `Map<String, (JsonElement?, Any?) -> Any?>`. This keeps handlers decoupled and easy to add/remove.

### Video pipeline design

- **CameraX** `ImageAnalysis` is used instead of `Preview` — no surface/view needed
- **`STRATEGY_KEEP_ONLY_LATEST`** backpressure ensures the encoder isn't overwhelmed
- **MediaCodec hardware encoder** provides efficient H.264 encoding
- YUV420 → NV12 conversion handles Android's various YUV buffer layouts
- The encoder output drain runs on a dedicated thread to avoid blocking camera callbacks

## Adding a New Method

1. Choose the appropriate handler file (or create a new one)
2. Add a private method:

```kotlin
private fun myNewMethod(params: JsonElement?, id: Any?): Any? {
    val obj = params?.asJsonObject ?: throw InvalidParamsException("params required")
    val myParam = obj.get("myParam")?.asString ?: throw InvalidParamsException("myParam required")
    robot.someMethod(myParam)
    return mapOf("status" to "accepted")
}
```

3. Register it in the `register()` method:

```kotlin
registry.register("domain.myNewMethod", ::myNewMethod)
```

## Adding a New Event

1. Implement the appropriate temi SDK listener interface in `EventBridge.kt`
2. Register/unregister in `registerAll()` / `unregisterAll()`
3. In the callback, call `connectionManager.sendNotification()`:

```kotlin
override fun onSomeEvent(data: String) {
    connectionManager.sendNotification(
        "event.domain.someEvent",
        mapOf("data" to data)
    )
}
```

## Protocol Reference

### JSON-RPC 2.0

**Request:**
```json
{
    "jsonrpc": "2.0",
    "method": "domain.action",
    "params": { ... },
    "id": 1
}
```

**Response (success):**
```json
{
    "jsonrpc": "2.0",
    "result": { ... },
    "id": 1
}
```

**Response (error):**
```json
{
    "jsonrpc": "2.0",
    "error": { "code": -32601, "message": "Method not found: foo.bar" },
    "id": 1
}
```

**Notification (event, no id):**
```json
{
    "jsonrpc": "2.0",
    "method": "event.speech.ttsStatusChanged",
    "params": { "text": "hello", "status": "COMPLETED" }
}
```

### Binary Media Protocol

Binary WebSocket frames use a 4-byte header + payload:

| Byte | Content |
|------|---------|
| 0 | Stream type: `0x01`=H.264 video, `0x02`=Opus audio out, `0x03`=Opus audio in |
| 1 | Flags: bit0=keyframe, bit1=end-of-stream |
| 2-3 | Sequence number (uint16 big-endian, wraps at 65535) |
| 4+ | Payload (H.264 NAL units or Opus frame) |

**Video stream details:**
- Codec: H.264 Baseline via MediaCodec (hardware)
- Resolution: 640x480
- Frame rate: ~20 fps (target 15, actual depends on device)
- Bitrate: 1 Mbps
- Keyframe interval: 2 seconds
- Color format: YUV420 (NV12 input to encoder)
- NAL structure: SPS/PPS sent as first frame (keyframe flag), then IDR, then P-frames

## Method Reference

### navigation

| Method | Params | Returns |
|--------|--------|---------|
| `navigation.goTo` | `{location: string}` | `{status, location}` |
| `navigation.goToPosition` | `{x: float, y: float, yaw?: float, tiltAngle?: int}` | `{status}` |
| `navigation.repose` | none | `{status}` |
| `navigation.saveLocation` | `{name: string}` | `{success, name}` |
| `navigation.deleteLocation` | `{name: string}` | `{success, name}` |
| `navigation.getLocations` | none | `["loc1", "loc2", ...]` |
| `navigation.getMapData` | none | `{mapId, mapInfo}` |
| `navigation.getMapList` | none | `[{id, name}, ...]` |
| `navigation.loadMap` | `{mapId: string}` | `{status, mapId}` |

### movement

| Method | Params | Returns |
|--------|--------|---------|
| `movement.skidJoy` | `{x: float, y: float}` | `{status}` |
| `movement.turnBy` | `{degrees: int, speed?: float}` | `{status, degrees}` |
| `movement.tiltAngle` | `{angle: int, speed?: float}` | `{status, angle}` |
| `movement.tiltBy` | `{degrees: int, speed?: float}` | `{status, degrees}` |
| `movement.stopMovement` | none | `{status}` |

### speech

| Method | Params | Returns |
|--------|--------|---------|
| `speech.speak` | `{text: string, showOnConversationLayer?: bool}` | `{status}` |
| `speech.cancelAllTts` | none | `{status}` |
| `speech.wakeup` | none | `{status}` |
| `speech.askQuestion` | `{question: string}` | `{status}` |
| `speech.finishConversation` | none | `{status}` |
| `speech.getWakeupWord` | none | `{wakeupWord}` |
| `speech.startDefaultNlu` | none | `{status}` |

### follow

| Method | Params | Returns |
|--------|--------|---------|
| `follow.beWith` | none | `{status}` |
| `follow.constraintBeWith` | none | `{status}` |
| `follow.setDetectionMode` | `{on: bool, distance?: float}` | `{detectionMode}` |
| `follow.setTrackUser` | `{on: bool}` | `{trackUser}` |
| `follow.isTrackUserOn` | none | `{on}` |

### telepresence

| Method | Params | Returns |
|--------|--------|---------|
| `telepresence.getAllContacts` | none | `[{userId, name, picUrl}, ...]` |
| `telepresence.start` | `{displayName: string, peerId: string}` | `{status}` |

### system

| Method | Params | Returns |
|--------|--------|---------|
| `system.getBattery` | none | `{level, isCharging}` |
| `system.getSerialNumber` | none | `{serialNumber}` |
| `system.getVolume` | none | `{volume}` |
| `system.setVolume` | `{volume: int}` | `{volume}` |
| `system.getGoToSpeed` | none | `{speed}` (HIGH/MEDIUM/SLOW/DEFAULT) |
| `system.setGoToSpeed` | `{speed: string}` | `{speed}` |
| `system.getNavigationSafety` | none | `{safety}` (HIGH/MEDIUM/DEFAULT) |
| `system.setNavigationSafety` | `{safety: string}` | `{safety}` |
| `system.showTopBar` | none | `{status}` |
| `system.hideTopBar` | none | `{status}` |
| `system.toggleNavigationBillboard` | `{hide: bool}` | `{hidden}` |
| `system.toggleWakeup` | `{disable: bool}` | `{disabled}` |
| `system.isWakeupDisabled` | none | `{disabled}` |
| `system.setHardButtonsDisabled` | `{disabled: bool}` | `{disabled}` |
| `system.isHardButtonsDisabled` | none | `{disabled}` |
| `system.setPrivacyMode` | `{on: bool}` | `{privacyMode}` |
| `system.getPrivacyMode` | none | `{on}` |
| `system.restart` | none | `{status}` |
| `system.showAppList` | none | `{status}` |
| `system.setAutoReturn` | `{on: bool}` | `{autoReturn}` |
| `system.isAutoReturnOn` | none | `{on}` |

### kiosk

| Method | Params | Returns |
|--------|--------|---------|
| `kiosk.requestToBeKioskApp` | none | `{status}` |
| `kiosk.isSelectedKioskApp` | none | `{isKiosk}` |
| `kiosk.setKioskMode` | `{on: bool}` | `{kioskMode}` |
| `kiosk.isKioskModeOn` | none | `{on}` |

### permission

| Method | Params | Returns |
|--------|--------|---------|
| `permission.checkSelfPermission` | `{permission: string}` | `{permission, granted}` |
| `permission.requestPermissions` | `{permissions: [string]}` | `{status}` |

Permission values: `settings`, `face_recognition`, `map`, `sequence`

### face

| Method | Params | Returns |
|--------|--------|---------|
| `face.startRecognition` | none | `{status}` |
| `face.stopRecognition` | none | `{status}` |

### media

| Method | Params | Returns |
|--------|--------|---------|
| `media.startVideoStream` | none | `{status, format, resolution, fps}` |
| `media.stopVideoStream` | none | `{status}` |
| `media.startAudioCapture` | none | *not yet implemented* |
| `media.stopAudioCapture` | none | *not yet implemented* |
| `media.startAudioPlayback` | none | *not yet implemented* |
| `media.stopAudioPlayback` | none | *not yet implemented* |

### bridge

| Method | Params | Returns |
|--------|--------|---------|
| `bridge.getCapabilities` | none | `{methods, events, version, protocol, mediaProtocol}` |
| `bridge.getVersion` | none | `{version, sdk}` |
| `bridge.ping` | none | `{pong: timestamp}` |

## Build & Deploy

```bash
# Build
./gradlew assembleDebug

# Connect to temi
adb connect <TEMI_IP>:5555

# Install
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Launch
adb shell am start -n com.cdi.temibridge/.MainActivity

# View logs
adb logcat -s TemiBridgeApp:* BridgeWebSocketServer:* JsonRpcDispatcher:* EventBridge:* VideoPipeline:* MainActivity:*
```

## Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| temi SDK | 0.10.77 | Robot control API |
| Java-WebSocket | 1.5.6 | WebSocket server |
| Gson | 2.10.1 | JSON serialization |
| Kotlin Coroutines | 1.7.3 | Async operations |
| CameraX | 1.2.3 | Camera capture (camera-core, camera-camera2, camera-lifecycle) |
| AndroidX AppCompat | 1.6.1 | UI compatibility |

## Roadmap

- [x] Phase 1: Foundation & build upgrade (AGP 8.x, Kotlin, JSON-RPC 2.0 framework)
- [x] Phase 2: Complete SDK coverage (66 methods, 15 event types)
- [x] Phase 3: Video streaming (CameraX → H.264 MediaCodec → binary WebSocket, ~20fps 640x480)
- [ ] Phase 4: Audio streaming (AudioRecord → Opus → binary WebSocket)
- [ ] Phase 5: Hardening (rate limiting, backpressure for slow clients, reconnection)
