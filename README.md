# Temi Bridge

A universal WebSocket bridge for the temi v2 robot. Exposes the full temi SDK via **JSON-RPC 2.0** protocol, enabling external programs (Python, Node.js, browser, etc.) to fully control the robot remotely. Includes real-time **H.264 video streaming** from the robot's camera.

Evolved from the [temi-woz-android](https://github.com/tongji-cdi/temi-woz-android) Wizard-of-Oz experiment tool.

## Quick Start

### 1. Install

```bash
adb connect <TEMI_IP>:5555
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 2. Connect

Connect via WebSocket to `ws://<TEMI_IP>:8175`. The app displays the WebSocket address on screen after launch.

### 3. Send Commands

All commands use [JSON-RPC 2.0](https://www.jsonrpc.org/specification) format:

```json
{"jsonrpc": "2.0", "method": "speech.speak", "params": {"text": "Hello!"}, "id": 1}
```

Response:

```json
{"jsonrpc": "2.0", "result": {"status": "accepted"}, "id": 1}
```

## Example: Control Robot (Python)

```python
import asyncio, websockets, json

async def main():
    async with websockets.connect("ws://192.168.1.100:8175") as ws:
        # Speak
        await ws.send(json.dumps({
            "jsonrpc": "2.0",
            "method": "speech.speak",
            "params": {"text": "你好，我是temi"},
            "id": 1
        }))
        print(await ws.recv())

        # Get battery
        await ws.send(json.dumps({
            "jsonrpc": "2.0",
            "method": "system.getBattery",
            "id": 2
        }))
        print(await ws.recv())

        # Navigate
        await ws.send(json.dumps({
            "jsonrpc": "2.0",
            "method": "navigation.goTo",
            "params": {"location": "客厅"},
            "id": 3
        }))
        print(await ws.recv())

asyncio.run(main())
```

## Example: Video Streaming (Python)

```python
import asyncio, websockets, json, struct

async def stream_video():
    async with websockets.connect("ws://192.168.1.100:8175") as ws:
        # Start video stream
        await ws.send(json.dumps({
            "jsonrpc": "2.0",
            "method": "media.startVideoStream",
            "id": 1
        }))

        # Receive H.264 frames (binary WebSocket messages)
        # Each frame: 4-byte header + H.264 NAL unit payload
        with open("output.h264", "wb") as f:
            for _ in range(100):
                msg = await ws.recv()
                if isinstance(msg, bytes) and len(msg) >= 4:
                    stream_type = msg[0]   # 0x01 = H.264 video
                    flags = msg[1]         # bit0 = keyframe
                    seq = struct.unpack('>H', msg[2:4])[0]
                    payload = msg[4:]      # H.264 NAL units
                    f.write(payload)

        # Stop video stream
        await ws.send(json.dumps({
            "jsonrpc": "2.0",
            "method": "media.stopVideoStream",
            "id": 2
        }))

asyncio.run(stream_video())
# Play with: ffplay -f h264 output.h264
```

## Example: Video in Browser (jmuxer.js)

```html
<script src="https://cdn.jsdelivr.net/npm/jmuxer@2/dist/jmuxer.min.js"></script>
<video id="player" autoplay></video>
<script>
const jmuxer = new JMuxer({
    node: 'player',
    mode: 'video',
    fps: 15,
    debug: false
});

const ws = new WebSocket('ws://TEMI_IP:8175');
ws.binaryType = 'arraybuffer';

ws.onopen = () => {
    ws.send(JSON.stringify({jsonrpc: '2.0', method: 'media.startVideoStream', id: 1}));
};

ws.onmessage = (event) => {
    if (event.data instanceof ArrayBuffer) {
        const data = new Uint8Array(event.data);
        if (data[0] === 0x01) { // H.264 video
            jmuxer.feed({ video: data.slice(4) });
        }
    }
};
</script>
```

## Available Methods (66 total)

| Domain | Methods |
|--------|---------|
| `navigation` | goTo, goToPosition, repose, saveLocation, deleteLocation, getLocations, getMapData, getMapList, loadMap |
| `movement` | skidJoy, turnBy, tiltAngle, tiltBy, stopMovement |
| `speech` | speak, cancelAllTts, wakeup, askQuestion, finishConversation, getWakeupWord, startDefaultNlu |
| `follow` | beWith, constraintBeWith, setDetectionMode, setTrackUser, isTrackUserOn |
| `telepresence` | getAllContacts, start |
| `system` | getBattery, getSerialNumber, getVolume, setVolume, getGoToSpeed, setGoToSpeed, getNavigationSafety, setNavigationSafety, showTopBar, hideTopBar, toggleNavigationBillboard, toggleWakeup, isWakeupDisabled, setHardButtonsDisabled, isHardButtonsDisabled, setPrivacyMode, getPrivacyMode, restart, showAppList, setAutoReturn, isAutoReturnOn |
| `kiosk` | requestToBeKioskApp, isSelectedKioskApp, setKioskMode, isKioskModeOn |
| `permission` | checkSelfPermission, requestPermissions |
| `face` | startRecognition, stopRecognition |
| `media` | startVideoStream, stopVideoStream, startAudioCapture, stopAudioCapture, startAudioPlayback, stopAudioPlayback |
| `bridge` | getCapabilities, getVersion, ping |

Use `bridge.getCapabilities` to get the full list of methods and events at runtime.

## Video Streaming

The bridge streams **H.264 video** from temi's camera via binary WebSocket frames.

| Parameter | Value |
|-----------|-------|
| Codec | H.264 Baseline (hardware MediaCodec) |
| Resolution | 640x480 |
| Frame rate | ~20 fps |
| Bitrate | ~1 Mbps |
| Keyframe interval | 2 seconds |
| Transport | Binary WebSocket frames with 4-byte header |

**Binary frame format:**

| Byte | Content |
|------|---------|
| 0 | Stream type (`0x01` = H.264 video) |
| 1 | Flags (bit0 = keyframe, bit1 = end-of-stream) |
| 2-3 | Sequence number (uint16 big-endian) |
| 4+ | H.264 NAL units |

The first frame is always SPS/PPS (codec config), followed by an IDR keyframe, then P-frames. Clients should buffer from the first keyframe.

## Events

Events are pushed as JSON-RPC notifications (no `id` field):

```json
{"jsonrpc": "2.0", "method": "event.speech.ttsStatusChanged", "params": {"text": "Hello", "status": "COMPLETED"}}
```

| Event | Description |
|-------|-------------|
| `event.navigation.goToLocationStatusChanged` | Navigation progress/completion |
| `event.navigation.distanceToLocationChanged` | Distance updates to saved locations |
| `event.navigation.locationsUpdated` | Saved location list changed |
| `event.navigation.reposeStatusChanged` | Repose status |
| `event.movement.statusChanged` | Movement (turn/tilt) completion |
| `event.speech.ttsStatusChanged` | TTS started/completed |
| `event.speech.asrResult` | Speech recognition result |
| `event.speech.wakeupWord` | Wakeup word detected |
| `event.speech.conversationViewAttached` | Conversation UI state |
| `event.speech.nluResult` | NLU processing result |
| `event.follow.beWithMeStatusChanged` | Follow-me status |
| `event.follow.constraintBeWithStatusChanged` | Constraint follow status |
| `event.follow.detectionStateChanged` | Human detection state |
| `event.system.robotReady` | Robot ready state |
| `event.system.userInteraction` | User interaction detected |

## Error Codes

| Code | Meaning |
|------|---------|
| -32700 | Parse error (malformed JSON) |
| -32600 | Invalid request (missing jsonrpc/method) |
| -32601 | Method not found |
| -32602 | Invalid params |
| -32001 | SDK error |
| -32002 | Permission denied |
| -32003 | Robot not ready |
| -32004 | Media error |

## Building

Requirements: Android Studio, JDK 17, Android SDK 33

```bash
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

## Architecture

See [DEVELOPMENT.md](DEVELOPMENT.md) for architecture details and development guide.

## License

MIT
