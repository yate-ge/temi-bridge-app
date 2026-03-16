# Temi Bridge - Camera / Video Streaming Guide

## Overview

Temi Bridge 通过 WebSocket 提供实时 H.264 视频流。摄像头**默认关闭**，需要通过 JSON-RPC 命令手动启动/停止。视频帧以二进制 WebSocket 消息推送给**所有已连接的客户端**。

## Quick Start

### 1. 连接 WebSocket

```python
import asyncio, websockets, json

ws = await websockets.connect("ws://TEMI_IP:8175")
```

### 2. 启动视频流

```json
{"jsonrpc": "2.0", "method": "media.startVideoStream", "id": 1}
```

响应:
```json
{"jsonrpc": "2.0", "result": {"status": "started", "format": "H.264", "resolution": "640x480", "fps": 15}, "id": 1}
```

### 3. 接收二进制帧

启动后，WebSocket 连接上会混合收到两种消息:
- **文本消息**: JSON-RPC 事件通知 (如 `event.navigation.*`)
- **二进制消息**: 视频帧 (4 字节头 + H.264 payload)

需要根据消息类型分别处理。

### 4. 停止视频流

```json
{"jsonrpc": "2.0", "method": "media.stopVideoStream", "id": 2}
```

响应:
```json
{"jsonrpc": "2.0", "result": {"status": "stopped"}, "id": 2}
```

## API Reference

| Method | Description | Response |
|--------|-------------|----------|
| `media.startVideoStream` | 启动摄像头和 H.264 编码器 | `{status, format, resolution, fps}` |
| `media.stopVideoStream` | 停止摄像头和编码器，释放资源 | `{status: "stopped"}` |

### 状态响应

| status | 含义 |
|--------|------|
| `started` | 摄像头已启动，帧数据开始推送 |
| `already_streaming` | 摄像头已在运行，无需重复启动 |
| `stopped` | 摄像头已关闭 |

### 错误响应

```json
{"jsonrpc": "2.0", "error": {"code": -32001, "message": "Video pipeline not initialized"}, "id": 1}
```

当 CAMERA 权限未授予时会返回此错误。需要在机器人上授予摄像头权限。

## Binary Frame Format

每个二进制 WebSocket 消息的格式:

```
┌─────────────────────────────────────────────┐
│  4-byte Header  │       Payload             │
├────┬────┬───────┼───────────────────────────┤
│ B0 │ B1 │ B2-B3 │ H.264 NAL unit(s)        │
└────┴────┴───────┴───────────────────────────┘
```

| Byte | Field | Description |
|------|-------|-------------|
| 0 | `streamType` | `0x01` = H.264 视频 (固定值) |
| 1 | `flags` | bit0: `0x01` = 关键帧 (IDR/SPS+PPS), `0x02` = EOS |
| 2-3 | `sequenceNumber` | uint16 big-endian, 从 0 递增, 65535 后回绕 |
| 4+ | payload | H.264 NAL unit 数据 (带 `00 00 00 01` start code) |

### 帧类型

| 顺序 | flags | 内容 | 说明 |
|------|-------|------|------|
| 第 1 帧 | `0x01` (keyframe) | SPS + PPS | 编解码器配置参数 (必须保存，解码器初始化需要) |
| 第 2 帧 | `0x01` (keyframe) | IDR frame | 第一个完整画面 |
| 后续帧 | `0x00` | P-frame | 差分帧 (依赖前面的关键帧) |
| 每 ~2 秒 | `0x01` (keyframe) | IDR frame | 周期性关键帧 |

## Video Parameters

| Parameter | Value |
|-----------|-------|
| Codec | H.264 Baseline (硬件 MediaCodec) |
| Resolution | 640 x 480 |
| Target FPS | 15 (实际 ~15-20) |
| Bitrate | 1 Mbps |
| Keyframe interval | 2 秒 |
| Color format | YUV420 (NV12 输入编码器) |
| Camera | 后置摄像头 (`DEFAULT_BACK_CAMERA`) |
| NAL format | Annex B (带 `00 00 00 01` start code) |

## Code Examples

### Python: 抓取单帧截图

```python
import asyncio, websockets, json, subprocess

async def capture_frame():
    async with websockets.connect("ws://TEMI_IP:8175") as ws:
        # 启动视频流
        await ws.send(json.dumps({
            "jsonrpc": "2.0", "method": "media.startVideoStream", "id": 1
        }))

        h264_data = bytearray()
        frame_count = 0

        # 收集足够的帧 (至少包含 SPS/PPS + IDR + 一些 P-frame)
        while frame_count < 30:
            msg = await asyncio.wait_for(ws.recv(), timeout=10)
            if isinstance(msg, bytes) and len(msg) >= 4 and msg[0] == 0x01:
                h264_data.extend(msg[4:])  # 跳过 4 字节头
                frame_count += 1

        # 停止视频流
        await ws.send(json.dumps({
            "jsonrpc": "2.0", "method": "media.stopVideoStream", "id": 2
        }))

        # ffmpeg 解码第一帧为 JPEG
        with open("/tmp/temi.h264", "wb") as f:
            f.write(h264_data)

        subprocess.run([
            "ffmpeg", "-y", "-f", "h264", "-i", "/tmp/temi.h264",
            "-frames:v", "1", "-q:v", "2", "/tmp/temi_frame.jpg"
        ])
        print("Saved: /tmp/temi_frame.jpg")

asyncio.run(capture_frame())
```

### Python: 录制视频文件

```python
import asyncio, websockets, json

async def record_video(duration_sec=10):
    async with websockets.connect("ws://TEMI_IP:8175") as ws:
        await ws.send(json.dumps({
            "jsonrpc": "2.0", "method": "media.startVideoStream", "id": 1
        }))

        # 收集 H.264 帧
        with open("temi_video.h264", "wb") as f:
            end_time = asyncio.get_event_loop().time() + duration_sec
            while asyncio.get_event_loop().time() < end_time:
                msg = await asyncio.wait_for(ws.recv(), timeout=5)
                if isinstance(msg, bytes) and len(msg) >= 4 and msg[0] == 0x01:
                    f.write(msg[4:])

        await ws.send(json.dumps({
            "jsonrpc": "2.0", "method": "media.stopVideoStream", "id": 2
        }))

        print("Saved: temi_video.h264")
        print("Play:  ffplay -f h264 temi_video.h264")
        print("To MP4: ffmpeg -f h264 -i temi_video.h264 -c copy temi_video.mp4")

asyncio.run(record_video(10))
```

### Python: 帧解析 (带 header 信息)

```python
import struct

def parse_frame(binary_msg):
    """解析一个二进制 WebSocket 消息"""
    if len(binary_msg) < 4:
        return None

    stream_type = binary_msg[0]
    flags = binary_msg[1]
    seq = struct.unpack('>H', binary_msg[2:4])[0]
    payload = binary_msg[4:]

    is_video = (stream_type == 0x01)
    is_keyframe = (flags & 0x01) != 0
    is_eos = (flags & 0x02) != 0

    return {
        "stream_type": stream_type,
        "is_video": is_video,
        "is_keyframe": is_keyframe,
        "is_eos": is_eos,
        "seq": seq,
        "payload_size": len(payload),
        "payload": payload
    }
```

### JavaScript: 浏览器实时播放 (JMuxer)

```html
<!DOCTYPE html>
<html>
<head>
    <title>Temi Camera</title>
    <script src="https://cdn.jsdelivr.net/npm/jmuxer@2/dist/jmuxer.min.js"></script>
</head>
<body>
    <video id="player" autoplay muted style="width:100%; max-width:640px;"></video>
    <br>
    <button onclick="startStream()">Start</button>
    <button onclick="stopStream()">Stop</button>
    <span id="status">Disconnected</span>

    <script>
    const TEMI_WS = 'ws://TEMI_IP:8175';
    let ws, jmuxer, msgId = 1;

    function initPlayer() {
        jmuxer = new JMuxer({
            node: 'player',
            mode: 'video',
            fps: 15,
            debug: false
        });
    }

    function startStream() {
        initPlayer();
        ws = new WebSocket(TEMI_WS);
        ws.binaryType = 'arraybuffer';

        ws.onopen = () => {
            document.getElementById('status').textContent = 'Connected';
            ws.send(JSON.stringify({
                jsonrpc: '2.0',
                method: 'media.startVideoStream',
                id: msgId++
            }));
        };

        ws.onmessage = (event) => {
            if (event.data instanceof ArrayBuffer) {
                const data = new Uint8Array(event.data);
                if (data[0] === 0x01) { // H.264 video
                    jmuxer.feed({ video: data.slice(4) });
                }
            }
            // Text messages (JSON-RPC responses/events) can be parsed as JSON
        };

        ws.onclose = () => {
            document.getElementById('status').textContent = 'Disconnected';
        };
    }

    function stopStream() {
        if (ws && ws.readyState === WebSocket.OPEN) {
            ws.send(JSON.stringify({
                jsonrpc: '2.0',
                method: 'media.stopVideoStream',
                id: msgId++
            }));
            setTimeout(() => ws.close(), 500);
        }
        if (jmuxer) {
            jmuxer.destroy();
            jmuxer = null;
        }
    }
    </script>
</body>
</html>
```

### Node.js: 转发到 RTSP/RTMP

```javascript
const WebSocket = require('ws');
const { spawn } = require('child_process');

const ws = new WebSocket('ws://TEMI_IP:8175');
ws.binaryType = 'nodebuffer';

// ffmpeg pipe: H.264 stdin → RTMP output
const ffmpeg = spawn('ffmpeg', [
    '-f', 'h264', '-i', 'pipe:0',
    '-c', 'copy',
    '-f', 'flv', 'rtmp://server/live/temi'
]);

let msgId = 1;

ws.on('open', () => {
    ws.send(JSON.stringify({
        jsonrpc: '2.0',
        method: 'media.startVideoStream',
        id: msgId++
    }));
});

ws.on('message', (data, isBinary) => {
    if (isBinary && data.length >= 4 && data[0] === 0x01) {
        ffmpeg.stdin.write(data.slice(4)); // H.264 payload to ffmpeg
    }
});
```

## Architecture

```
CameraX (后置摄像头)
  │  YUV_420_888 @ ~15-20fps
  ▼
ImageAnalysis (STRATEGY_KEEP_ONLY_LATEST)
  │  背压: 编码器忙时跳帧
  ▼
yuv420ToNv12() 颜色空间转换
  │  NV12 640x480
  ▼
MediaCodec H.264 硬件编码器
  │  1Mbps, Baseline Profile
  ▼
drainEncoder() 输出线程
  │  NAL units (SPS/PPS, IDR, P-frame)
  ▼
MediaFrameHeader 封装 (4 字节头 + payload)
  │
  ▼
ConnectionManager.sendToAll(binary)
  │  广播给所有 WebSocket 客户端
  ▼
WebSocket binary frame → Client
```

## Important Notes

### 权限
- 需要 `CAMERA` 权限。如未授权，`media.startVideoStream` 返回错误 `-32001`
- 权限在 app 启动时请求，也可以在 Android 设置中手动授予

### 摄像头方向
- temi 使用**后置摄像头** (`DEFAULT_BACK_CAMERA`)
- 画面可能是**旋转的** (取决于机器人姿态和摄像头物理安装角度)
- 客户端需要自行处理旋转 (通常需要顺时针旋转 90 度)

### 资源管理
- **用完必须调 `stopVideoStream`**: 不停止会一直占用摄像头和编码器资源
- 如果 WebSocket 断开但未调 stop, 摄像头会继续运行直到 app 被关闭
- 多个客户端连接时，任一客户端调 `startVideoStream` 即可启动, 所有客户端都会收到帧
- 任一客户端调 `stopVideoStream` 会为所有客户端停止视频流

### 性能
- H.264 使用硬件编码器，CPU 开销小
- 帧率 ~15-20 fps，码率 ~1 Mbps
- 在 LAN 环境下延迟通常 < 200ms
- `STRATEGY_KEEP_ONLY_LATEST` 确保编码器不会过载 (忙时自动跳帧)

### 混合消息处理
WebSocket 连接上同时传输文本和二进制消息:
- 文本: JSON-RPC 响应和事件通知
- 二进制: 视频帧 (type=0x01), 音频帧 (type=0x02, 0x03)

客户端必须检查消息类型, 并用 `msg[0]` (streamType) 区分视频和音频。

### 解码提示
- 第一帧 (SPS/PPS) 是解码器初始化必需的, 丢失则无法解码
- 如果中途开始接收 (错过第一帧), 需要等到下一个关键帧 (每 2 秒)
- 建议客户端缓存最近的 SPS/PPS, 以便断线重连后快速恢复解码
