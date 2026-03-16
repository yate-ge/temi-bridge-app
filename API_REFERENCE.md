# Temi Bridge API 使用手册

本文档面向使用 Temi Bridge 控制 temi 机器人的开发者。通过 WebSocket 连接，你可以用任何编程语言（Python、JavaScript、Node.js 等）远程控制机器人的语音、导航、运动、摄像头等全部功能。

---

## 目录

- [连接方式](#连接方式)
- [协议格式](#协议格式)
- [快速上手](#快速上手)
- [语音控制 (speech)](#语音控制-speech)
- [导航控制 (navigation)](#导航控制-navigation)
- [运动控制 (movement)](#运动控制-movement)
- [跟随与检测 (follow)](#跟随与检测-follow)
- [视频传输 (telepresence)](#视频通话-telepresence)
- [系统设置 (system)](#系统设置-system)
- [Kiosk 模式 (kiosk)](#kiosk-模式-kiosk)
- [权限管理 (permission)](#权限管理-permission)
- [人脸识别 (face)](#人脸识别-face)
- [屏幕显示 (display)](#屏幕显示-display)
- [媒体流 (media)](#媒体流-media)
- [桥接信息 (bridge)](#桥接信息-bridge)
- [事件监听](#事件监听)
- [错误处理](#错误处理)
- [完整示例](#完整示例)

---

## 连接方式

1. 在 temi 上安装并启动 Temi Bridge 应用
2. 应用界面会显示 WebSocket 地址，例如 `ws://192.168.123.100:8175`
3. 用任何 WebSocket 客户端连接到该地址
4. 连接成功后即可发送指令

**端口：** 8175（固定）

**协议：** 同一个 WebSocket 连接同时传输两种数据：
- **文本消息** — JSON-RPC 2.0 指令和响应
- **二进制消息** — 视频流数据（仅在开启视频流后）

---

## 协议格式

所有指令使用 [JSON-RPC 2.0](https://www.jsonrpc.org/specification) 标准格式。

### 发送指令

```json
{
    "jsonrpc": "2.0",
    "method": "方法名",
    "params": { "参数": "值" },
    "id": 1
}
```

- `jsonrpc` — 固定为 `"2.0"`
- `method` — 要调用的方法，格式为 `域名.动作`，例如 `speech.speak`
- `params` — 参数对象（某些方法不需要参数，可省略）
- `id` — 请求编号，用于匹配响应（每次递增即可）

### 收到响应

**成功：**
```json
{
    "jsonrpc": "2.0",
    "result": { "status": "accepted" },
    "id": 1
}
```

**失败：**
```json
{
    "jsonrpc": "2.0",
    "error": { "code": -32601, "message": "Method not found: xxx" },
    "id": 1
}
```

### 收到事件（异步推送）

机器人状态变化时会主动推送事件通知，没有 `id` 字段：

```json
{
    "jsonrpc": "2.0",
    "method": "event.speech.ttsStatusChanged",
    "params": { "text": "你好", "status": "COMPLETED" }
}
```

---

## 快速上手

### Python 最简示例

```bash
pip install websockets
```

```python
import asyncio, websockets, json

async def main():
    async with websockets.connect("ws://192.168.123.100:8175") as ws:
        # 让机器人说话
        await ws.send(json.dumps({
            "jsonrpc": "2.0",
            "method": "speech.speak",
            "params": {"text": "你好，我是temi"},
            "id": 1
        }))
        response = json.loads(await ws.recv())
        print("说话指令:", response["result"])

asyncio.run(main())
```

### JavaScript (浏览器) 最简示例

```javascript
const ws = new WebSocket('ws://192.168.123.100:8175');

ws.onopen = () => {
    // 让机器人说话
    ws.send(JSON.stringify({
        jsonrpc: '2.0',
        method: 'speech.speak',
        params: { text: '你好，我是temi' },
        id: 1
    }));
};

ws.onmessage = (event) => {
    if (typeof event.data === 'string') {
        const msg = JSON.parse(event.data);
        if (msg.id) console.log('响应:', msg);
        else console.log('事件:', msg);
    }
};
```

### Node.js 最简示例

```bash
npm install ws
```

```javascript
const WebSocket = require('ws');
const ws = new WebSocket('ws://192.168.123.100:8175');

ws.on('open', () => {
    ws.send(JSON.stringify({
        jsonrpc: '2.0',
        method: 'speech.speak',
        params: { text: '你好，我是temi' },
        id: 1
    }));
});

ws.on('message', (data) => {
    if (typeof data === 'string') {
        console.log(JSON.parse(data));
    }
});
```

---

## 语音控制 (speech)

> **语音接管模式：** Temi Bridge 默认接管 temi 的语音系统。当用户对机器人说话时：
> 1. temi 的 ASR（语音识别）捕获文字
> 2. Bridge 通过 `event.speech.asrResult` 将识别结果转发给外部大脑
> 3. Bridge 立即调用 `finishConversation()` 阻止 temi 自行处理
> 4. 外部大脑收到文字后决定如何回应（调用 `speech.speak` 回复，或执行其他动作）
>
> 同理，NLU 结果也会被拦截转发，temi 不会自行执行语音命令。
>
> 这意味着外部大脑完全控制机器人的对话流程。

### 说话

让机器人朗读一段文字。

```json
{
    "jsonrpc": "2.0",
    "method": "speech.speak",
    "params": {
        "text": "你好，欢迎来到我们的展厅",
        "showOnConversationLayer": true
    },
    "id": 1
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `text` | string | 是 | 要朗读的文字 |
| `showOnConversationLayer` | bool | 否 | 是否在屏幕上显示对话气泡（默认 true） |

**响应：** `{"status": "accepted"}`

**关联事件：** 说话过程中和结束时会收到 `event.speech.ttsStatusChanged` 事件：
```json
{"jsonrpc": "2.0", "method": "event.speech.ttsStatusChanged", "params": {"text": "你好，欢迎来到我们的展厅", "status": "COMPLETED"}}
```
`status` 值：`PROCESSING`（正在说）、`COMPLETED`（说完了）、`ERROR`（出错）

### 停止说话

立即停止所有语音朗读。

```json
{
    "jsonrpc": "2.0",
    "method": "speech.cancelAllTts",
    "id": 2
}
```

无需参数。

### 提问并等待回答

让机器人说出一个问题，然后自动开启语音识别，等待用户回答。

```json
{
    "jsonrpc": "2.0",
    "method": "speech.askQuestion",
    "params": {
        "question": "请问你想去哪里？"
    },
    "id": 3
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `question` | string | 是 | 要提问的内容 |

**关联事件：** 用户回答后会收到 `event.speech.asrResult` 事件：
```json
{"jsonrpc": "2.0", "method": "event.speech.asrResult", "params": {"text": "我想去客厅"}}
```

### 结束对话

结束当前对话状态，关闭语音识别。

```json
{
    "jsonrpc": "2.0",
    "method": "speech.finishConversation",
    "id": 4
}
```

### 唤醒机器人

触发机器人的唤醒动作（等同于喊唤醒词）。

```json
{
    "jsonrpc": "2.0",
    "method": "speech.wakeup",
    "id": 5
}
```

### 获取唤醒词

查询当前设置的唤醒词。

```json
{
    "jsonrpc": "2.0",
    "method": "speech.getWakeupWord",
    "id": 6
}
```

**响应：** `{"wakeupWord": "DingDang"}`

### 启动默认 NLU

启动默认的自然语言理解引擎。

```json
{
    "jsonrpc": "2.0",
    "method": "speech.startDefaultNlu",
    "id": 7
}
```

---

## 导航控制 (navigation)

### 去指定位置

让机器人导航到预先保存的位置（位置名称必须与 temi 中保存的完全一致）。

```json
{
    "jsonrpc": "2.0",
    "method": "navigation.goTo",
    "params": {
        "location": "客厅"
    },
    "id": 10
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `location` | string | 是 | 位置名称，必须与 temi 中保存的一致 |

**关联事件：** 导航过程中会持续收到 `event.navigation.goToLocationStatusChanged`：
```json
{"jsonrpc": "2.0", "method": "event.navigation.goToLocationStatusChanged", "params": {"location": "客厅", "status": "going", "descriptionId": 0, "description": ""}}
```
`status` 值：`going`（正在前往）、`complete`（到达）、`abort`（中断）

### 去坐标位置

让机器人导航到地图上的指定坐标。

```json
{
    "jsonrpc": "2.0",
    "method": "navigation.goToPosition",
    "params": {
        "x": 1.5,
        "y": 2.3,
        "yaw": 90.0,
        "tiltAngle": 0
    },
    "id": 11
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `x` | float | 是 | X 坐标（米） |
| `y` | float | 是 | Y 坐标（米） |
| `yaw` | float | 否 | 到达后朝向角度（默认 0） |
| `tiltAngle` | int | 否 | 到达后头部倾斜角度（默认 0） |

### 获取所有已保存位置

查询机器人上已保存的所有位置名称列表。

```json
{
    "jsonrpc": "2.0",
    "method": "navigation.getLocations",
    "id": 12
}
```

**响应示例：**
```json
{"result": ["home base", "待命点", "餐厅", "厨房", "书房", "客厅", "卧室"]}
```

### 保存当前位置

将机器人当前所在位置保存为指定名称。

```json
{
    "jsonrpc": "2.0",
    "method": "navigation.saveLocation",
    "params": {
        "name": "会议室入口"
    },
    "id": 13
}
```

**响应：** `{"success": true, "name": "会议室入口"}`

### 删除已保存位置

```json
{
    "jsonrpc": "2.0",
    "method": "navigation.deleteLocation",
    "params": {
        "name": "会议室入口"
    },
    "id": 14
}
```

### 回到充电桩

让机器人自动返回充电桩。

```json
{
    "jsonrpc": "2.0",
    "method": "navigation.repose",
    "id": 15
}
```

### 获取地图数据

获取当前地图的完整数据，包括地图尺寸、位置点坐标、虚拟墙、路径等。

> **注意：** 需要 MAP 权限（在 temi center 后台批准）。

```json
{
    "jsonrpc": "2.0",
    "method": "navigation.getMapData",
    "id": 16
}
```

**响应示例：**
```json
{
    "mapId": "map_001",
    "mapInfo": {
        "width": 1024,
        "height": 1024,
        "originX": -12.5,
        "originY": -12.5,
        "resolution": 0.05
    },
    "mapImage": {
        "rows": 1024,
        "cols": 1024,
        "typeId": "CV_8UC1"
    },
    "locations": [
        {
            "layerId": "客厅",
            "layerCategory": 0,
            "layerStatus": 0,
            "poses": [{"x": 1.5, "y": 3.2, "theta": 0.0}]
        }
    ],
    "virtualWalls": [],
    "greenPaths": []
}
```

| 字段 | 说明 |
|------|------|
| `mapInfo.width/height` | 地图栅格尺寸（像素） |
| `mapInfo.originX/originY` | 地图原点在真实世界的坐标（米） |
| `mapInfo.resolution` | 每像素对应的真实距离（米/像素） |
| `locations[].poses[].x/y/theta` | 各保存位置在地图上的坐标和朝向 |
| `virtualWalls` | 虚拟墙的端点坐标 |
| `greenPaths` | 绿色路径的路径点坐标 |

### 获取地图列表

```json
{
    "jsonrpc": "2.0",
    "method": "navigation.getMapList",
    "id": 17
}
```

**响应：** `[{"id": "map_001", "name": "办公室地图"}, ...]`

### 加载指定地图

```json
{
    "jsonrpc": "2.0",
    "method": "navigation.loadMap",
    "params": {
        "mapId": "map_001"
    },
    "id": 18
}
```

---

## 运动控制 (movement)

### 抬头 / 低头

控制机器人头部上下倾斜到指定角度。

```json
{
    "jsonrpc": "2.0",
    "method": "movement.tiltAngle",
    "params": {
        "angle": 45,
        "speed": 1.0
    },
    "id": 20
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `angle` | int | 是 | 目标角度。正值=抬头（最大约55°），负值=低头（最小约-25°），0=正前方 |
| `speed` | float | 否 | 速度（默认 1.0） |

**常见用法：**
- 抬头看人脸：`{"angle": 45}`
- 低头看地面：`{"angle": -15}`
- 回到正前方：`{"angle": 0}`

### 相对倾斜

在当前角度基础上，头部再倾斜指定度数。

```json
{
    "jsonrpc": "2.0",
    "method": "movement.tiltBy",
    "params": {
        "degrees": 10,
        "speed": 1.0
    },
    "id": 21
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `degrees` | int | 是 | 相对倾斜度数，正值=向上，负值=向下 |
| `speed` | float | 否 | 速度（默认 1.0） |

### 原地转身

控制机器人原地旋转指定角度。

```json
{
    "jsonrpc": "2.0",
    "method": "movement.turnBy",
    "params": {
        "degrees": 90,
        "speed": 1.0
    },
    "id": 22
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `degrees` | int | 是 | 旋转角度，正值=逆时针，负值=顺时针 |
| `speed` | float | 否 | 速度（默认 1.0） |

**常见用法：**
- 左转90°：`{"degrees": 90}`
- 右转90°：`{"degrees": -90}`
- 掉头：`{"degrees": 180}`

### 遥控移动 (摇杆)

模拟摇杆控制，让机器人自由移动。适合做实时遥控。

```json
{
    "jsonrpc": "2.0",
    "method": "movement.skidJoy",
    "params": {
        "x": 0.0,
        "y": 0.5
    },
    "id": 23
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `x` | float | 是 | 左右方向，范围 -1.0~1.0。正值=左转，负值=右转 |
| `y` | float | 是 | 前后方向，范围 -1.0~1.0。正值=前进，负值=后退 |

**注意：** 需要持续发送指令来保持运动，停止发送后机器人会停下。建议每 100-200ms 发送一次。

### 停止所有运动

立即停止机器人的所有移动（包括导航、转身等）。

```json
{
    "jsonrpc": "2.0",
    "method": "movement.stopMovement",
    "id": 24
}
```

---

## 跟随与检测 (follow)

### 跟随我

让机器人跟随最近检测到的人。

```json
{
    "jsonrpc": "2.0",
    "method": "follow.beWith",
    "id": 30
}
```

**关联事件：** `event.follow.beWithMeStatusChanged`
```json
{"params": {"status": "start"}}   // 开始跟随
{"params": {"status": "stop"}}    // 停止跟随
```

### 约束跟随

类似跟随，但保持一定距离约束。

```json
{
    "jsonrpc": "2.0",
    "method": "follow.constraintBeWith",
    "id": 31
}
```

### 开启/关闭人体检测

```json
{
    "jsonrpc": "2.0",
    "method": "follow.setDetectionMode",
    "params": {
        "on": true,
        "distance": 1.5
    },
    "id": 32
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `on` | bool | 是 | true=开启检测，false=关闭 |
| `distance` | float | 否 | 检测距离，单位米（默认 1.0） |

**关联事件：** `event.follow.detectionStateChanged`
```json
{"params": {"state": 2}}   // 2=检测到人
```

### 开启/关闭人脸追踪

开启后机器人会自动转头面向检测到的人。

```json
{
    "jsonrpc": "2.0",
    "method": "follow.setTrackUser",
    "params": {
        "on": true
    },
    "id": 33
}
```

### 查询人脸追踪状态

```json
{
    "jsonrpc": "2.0",
    "method": "follow.isTrackUserOn",
    "id": 34
}
```

**响应：** `{"on": true}`

---

## 视频通话 (telepresence)

### 获取所有联系人

```json
{
    "jsonrpc": "2.0",
    "method": "telepresence.getAllContacts",
    "id": 40
}
```

**响应示例：**
```json
{
    "result": [
        {"userId": "abc123", "name": "张三", "picUrl": "https://..."},
        {"userId": "def456", "name": "李四", "picUrl": "https://..."}
    ]
}
```

### 发起视频通话

```json
{
    "jsonrpc": "2.0",
    "method": "telepresence.start",
    "params": {
        "displayName": "张三",
        "peerId": "abc123"
    },
    "id": 41
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `displayName` | string | 是 | 对方显示名称 |
| `peerId` | string | 是 | 对方的 userId（从 getAllContacts 获取） |

---

## 系统设置 (system)

### 查询电池状态

```json
{
    "jsonrpc": "2.0",
    "method": "system.getBattery",
    "id": 50
}
```

**响应：** `{"level": 96, "isCharging": false}`

- `level`：电量百分比 (0-100)
- `isCharging`：是否正在充电

### 获取序列号

```json
{
    "jsonrpc": "2.0",
    "method": "system.getSerialNumber",
    "id": 51
}
```

**响应：** `{"serialNumber": "00119381818"}`

### 查询/设置音量

```json
// 查询
{"jsonrpc": "2.0", "method": "system.getVolume", "id": 52}
// 响应：{"volume": 5}

// 设置
{"jsonrpc": "2.0", "method": "system.setVolume", "params": {"volume": 8}, "id": 53}
```

| 参数 | 类型 | 说明 |
|------|------|------|
| `volume` | int | 音量值（范围取决于设备） |

### 查询/设置导航速度

```json
// 查询
{"jsonrpc": "2.0", "method": "system.getGoToSpeed", "id": 54}
// 响应：{"speed": "MEDIUM"}

// 设置
{"jsonrpc": "2.0", "method": "system.setGoToSpeed", "params": {"speed": "HIGH"}, "id": 55}
```

可选值：`HIGH`（快）、`MEDIUM`（中）、`SLOW`（慢）、`DEFAULT`

### 查询/设置导航安全等级

```json
// 查询
{"jsonrpc": "2.0", "method": "system.getNavigationSafety", "id": 56}
// 响应：{"safety": "HIGH"}

// 设置
{"jsonrpc": "2.0", "method": "system.setNavigationSafety", "params": {"safety": "MEDIUM"}, "id": 57}
```

可选值：`HIGH`（高，遇到障碍物会早停）、`MEDIUM`（中）、`DEFAULT`

### 显示/隐藏顶部栏

```json
// 显示
{"jsonrpc": "2.0", "method": "system.showTopBar", "id": 58}

// 隐藏
{"jsonrpc": "2.0", "method": "system.hideTopBar", "id": 59}
```

### 显示/隐藏导航指示牌

```json
{"jsonrpc": "2.0", "method": "system.toggleNavigationBillboard", "params": {"hide": true}, "id": 60}
```

### 启用/禁用唤醒词

```json
// 禁用唤醒词
{"jsonrpc": "2.0", "method": "system.toggleWakeup", "params": {"disable": true}, "id": 61}

// 查询是否已禁用
{"jsonrpc": "2.0", "method": "system.isWakeupDisabled", "id": 62}
// 响应：{"disabled": false}
```

### 启用/禁用物理按键

```json
// 禁用
{"jsonrpc": "2.0", "method": "system.setHardButtonsDisabled", "params": {"disabled": true}, "id": 63}

// 查询
{"jsonrpc": "2.0", "method": "system.isHardButtonsDisabled", "id": 64}
```

### 启用/禁用隐私模式

隐私模式下摄像头和麦克风会被关闭。

```json
// 开启隐私模式
{"jsonrpc": "2.0", "method": "system.setPrivacyMode", "params": {"on": true}, "id": 65}

// 查询
{"jsonrpc": "2.0", "method": "system.getPrivacyMode", "id": 66}
// 响应：{"on": false}
```

### 启用/禁用自动回充

```json
// 开启自动回充
{"jsonrpc": "2.0", "method": "system.setAutoReturn", "params": {"on": true}, "id": 67}

// 查询
{"jsonrpc": "2.0", "method": "system.isAutoReturnOn", "id": 68}
```

### 重启机器人

```json
{"jsonrpc": "2.0", "method": "system.restart", "id": 69}
```

### 显示应用列表

```json
{"jsonrpc": "2.0", "method": "system.showAppList", "id": 70}
```

---

## Kiosk 模式 (kiosk)

### 申请成为 Kiosk 应用

```json
{"jsonrpc": "2.0", "method": "kiosk.requestToBeKioskApp", "id": 80}
```

### 查询是否为 Kiosk 应用

```json
{"jsonrpc": "2.0", "method": "kiosk.isSelectedKioskApp", "id": 81}
// 响应：{"isKiosk": true}
```

### 开启/关闭 Kiosk 模式

```json
{"jsonrpc": "2.0", "method": "kiosk.setKioskMode", "params": {"on": true}, "id": 82}
```

### 查询 Kiosk 模式状态

```json
{"jsonrpc": "2.0", "method": "kiosk.isKioskModeOn", "id": 83}
// 响应：{"on": true}
```

---

## 权限管理 (permission)

### 检查权限

```json
{
    "jsonrpc": "2.0",
    "method": "permission.checkSelfPermission",
    "params": {
        "permission": "settings"
    },
    "id": 85
}
```

**响应：** `{"permission": "settings", "granted": true}`

可用权限值：`settings`、`face_recognition`、`map`、`sequence`

### 请求权限

```json
{
    "jsonrpc": "2.0",
    "method": "permission.requestPermissions",
    "params": {
        "permissions": ["settings", "map"]
    },
    "id": 86
}
```

---

## 人脸识别 (face)

### 开始人脸识别

```json
{"jsonrpc": "2.0", "method": "face.startRecognition", "id": 90}
```

### 停止人脸识别

```json
{"jsonrpc": "2.0", "method": "face.stopRecognition", "id": 91}
```

---

## 屏幕显示 (display)

控制 temi 屏幕上显示的内容。可以加载网页 URL、直接渲染 HTML，或执行 JavaScript。适合用于显示表情、信息面板、用户交互界面等。

### 加载网页

在 temi 屏幕上全屏显示一个网页。

```json
{
    "jsonrpc": "2.0",
    "method": "display.loadUrl",
    "params": {
        "url": "https://example.com"
    },
    "id": 110
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `url` | string | 是 | 要加载的网页地址 |

**响应：** `{"status": "accepted", "url": "https://example.com"}`

### 加载 HTML 内容

直接在 temi 屏幕上渲染 HTML 代码。适合显示自定义表情、提示信息等。

```json
{
    "jsonrpc": "2.0",
    "method": "display.loadHtml",
    "params": {
        "html": "<html><body style='background:#1a1a2e;display:flex;justify-content:center;align-items:center;height:100vh'><h1 style='color:white;font-size:60px'>你好！</h1></body></html>"
    },
    "id": 111
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `html` | string | 是 | HTML 内容 |
| `baseUrl` | string | 否 | 基础 URL，用于解析相对路径（默认 about:blank） |

**响应：** `{"status": "accepted", "contentLength": 150}`

### 清除屏幕

关闭网页显示，恢复到状态界面。

```json
{
    "jsonrpc": "2.0",
    "method": "display.clear",
    "id": 112
}
```

### 获取当前 URL

```json
{
    "jsonrpc": "2.0",
    "method": "display.getCurrentUrl",
    "id": 113
}
```

**响应：** `{"url": "https://example.com", "visible": true}`

### 执行 JavaScript

在当前加载的网页中执行 JavaScript 代码。可以动态修改页面内容。

```json
{
    "jsonrpc": "2.0",
    "method": "display.executeJavaScript",
    "params": {
        "script": "document.body.style.background = 'red'"
    },
    "id": 114
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `script` | string | 是 | 要执行的 JavaScript 代码 |

### 使用场景示例

**显示表情：**
```python
await ws.send(json.dumps({
    "jsonrpc": "2.0",
    "method": "display.loadHtml",
    "params": {"html": "<html><body style='margin:0;display:flex;justify-content:center;align-items:center;height:100vh;background:#000'><div style='font-size:300px'>😊</div></body></html>"},
    "id": 1
}))
```

**显示信息面板：**
```python
await ws.send(json.dumps({
    "jsonrpc": "2.0",
    "method": "display.loadHtml",
    "params": {"html": "<html><body style='margin:0;padding:40px;background:#1a1a2e;color:white;font-family:sans-serif'><h1>欢迎光临</h1><p style='font-size:24px'>请在屏幕上选择您要去的地方：</p><ul style='font-size:20px'><li>客厅</li><li>餐厅</li><li>书房</li></ul></body></html>"},
    "id": 1
}))
```

**动态更新内容：**
```python
# 先加载基础 HTML
await ws.send(json.dumps({
    "jsonrpc": "2.0",
    "method": "display.loadHtml",
    "params": {"html": "<html><body style='background:#000;color:white;display:flex;justify-content:center;align-items:center;height:100vh'><h1 id='msg'>等待中...</h1></body></html>"},
    "id": 1
}))

# 然后用 JS 动态更新
await ws.send(json.dumps({
    "jsonrpc": "2.0",
    "method": "display.executeJavaScript",
    "params": {"script": "document.getElementById('msg').innerText = '客人已到达！'"},
    "id": 2
}))
```

### WebView 兼容性

temi 的 WebView 基于 Android System WebView（Chromium 内核）。以下功能经过实机测试：

| 功能 | 支持情况 | 说明 |
|------|---------|------|
| HTML5 | ✅ | 完整支持 |
| CSS3 Flexbox | ✅ | 布局正常 |
| CSS Grid | ✅ | grid-template-columns 等正常 |
| CSS Gradients | ✅ | linear-gradient 渲染正确 |
| CSS Animation (@keyframes) | ✅ | 动画流畅 |
| CSS border-radius / box-shadow | ✅ | 圆角和阴影正常 |
| JavaScript (ES6+) | ✅ | const/let/箭头函数/模板字符串等 |
| DOM 操作 | ✅ | getElementById, querySelector 等 |
| setInterval / setTimeout | ✅ | 定时器正常 |
| requestAnimationFrame | ✅ | 动画帧回调正常 |
| SVG | ✅ | circle/path/text 渲染正确 |
| Canvas 2D | ✅ | 绘图和动画正常 |
| Date API / Intl | ✅ | 日期和国际化正常 |
| 外部 URL (HTTPS) | ✅ | 可加载外部网页 |
| Mixed Content (HTTP in HTTPS) | ✅ | 已开启允许 |
| localStorage / sessionStorage | ✅ | DOM Storage 已启用 |
| 音频/视频自动播放 | ✅ | 已禁用用户手势要求 |
| Unicode Emoji | ⚠️ | 部分 emoji 不显示，建议用 SVG 替代 |
| WebGL | ❓ | 未测试，取决于硬件 |
| WebRTC | ❓ | 未测试 |

**WebView 设置：**
- JavaScript 已启用
- DOM Storage 已启用
- 缩放已禁用（固定全屏）
- Mixed Content 允许（HTTP 资源可在 HTTPS 页面加载）
- 文件访问和跨域访问已启用
- 媒体自动播放已启用（无需用户手势）
- 宽视口模式（useWideViewPort + loadWithOverviewMode）

**建议：** 使用 SVG 绘制表情而非 Unicode Emoji，兼容性更好且可精确控制大小和动画。

---

## 媒体流 (media)

### 开启视频流

开启后，机器人摄像头画面会以 H.264 编码通过二进制 WebSocket 帧持续推送。

```json
{
    "jsonrpc": "2.0",
    "method": "media.startVideoStream",
    "id": 100
}
```

**响应：** `{"status": "started", "format": "H.264", "resolution": "640x480", "fps": 15}`

**视频流参数：**

| 参数 | 值 |
|------|-----|
| 编码 | H.264 Baseline（硬件编码） |
| 分辨率 | 640x480 |
| 帧率 | ~20 fps |
| 码率 | ~1 Mbps |
| 关键帧间隔 | 2 秒 |

### 停止视频流

```json
{
    "jsonrpc": "2.0",
    "method": "media.stopVideoStream",
    "id": 101
}
```

### 视频帧格式

视频通过**二进制 WebSocket 消息**发送，每帧结构：

```
[字节0]  流类型 = 0x01 (H.264视频)
[字节1]  标志位: bit0=关键帧, bit1=流结束
[字节2-3] 序列号 (uint16 大端序, 从0递增)
[字节4+] H.264 NAL 数据
```

**流的第一帧**是 SPS + PPS 编解码器配置数据（标记为关键帧），随后是 IDR 关键帧，然后是 P 帧。客户端应从第一个关键帧开始解码。

### Python 接收视频流示例

```python
import asyncio, websockets, json, struct

async def receive_video():
    async with websockets.connect("ws://192.168.123.100:8175") as ws:
        # 开启视频流
        await ws.send(json.dumps({
            "jsonrpc": "2.0", "method": "media.startVideoStream", "id": 1
        }))

        # 保存为 H.264 文件
        with open("temi_video.h264", "wb") as f:
            for _ in range(300):  # 接收300帧（约15秒）
                msg = await ws.recv()
                if isinstance(msg, bytes) and len(msg) >= 4:
                    stream_type = msg[0]     # 0x01 = 视频
                    flags = msg[1]           # 关键帧标志
                    seq = struct.unpack('>H', msg[2:4])[0]  # 序列号
                    payload = msg[4:]        # H.264 数据
                    if stream_type == 0x01:
                        f.write(payload)

        # 停止
        await ws.send(json.dumps({
            "jsonrpc": "2.0", "method": "media.stopVideoStream", "id": 2
        }))

asyncio.run(receive_video())
# 播放: ffplay -f h264 temi_video.h264
```

### 浏览器播放视频流示例 (jmuxer.js)

```html
<!DOCTYPE html>
<html>
<head>
    <title>Temi 摄像头</title>
    <script src="https://cdn.jsdelivr.net/npm/jmuxer@2/dist/jmuxer.min.js"></script>
</head>
<body>
    <video id="player" autoplay muted style="width:640px;height:480px;background:#000"></video>
    <br>
    <button onclick="startVideo()">开始</button>
    <button onclick="stopVideo()">停止</button>

    <script>
    let ws;
    const jmuxer = new JMuxer({
        node: 'player',
        mode: 'video',
        fps: 15,
        debug: false
    });

    function startVideo() {
        ws = new WebSocket('ws://192.168.123.100:8175');
        ws.binaryType = 'arraybuffer';

        ws.onopen = () => {
            ws.send(JSON.stringify({
                jsonrpc: '2.0', method: 'media.startVideoStream', id: 1
            }));
        };

        ws.onmessage = (event) => {
            if (event.data instanceof ArrayBuffer) {
                const data = new Uint8Array(event.data);
                if (data[0] === 0x01) {  // H.264 视频帧
                    jmuxer.feed({ video: data.slice(4) });
                }
            }
        };
    }

    function stopVideo() {
        if (ws && ws.readyState === WebSocket.OPEN) {
            ws.send(JSON.stringify({
                jsonrpc: '2.0', method: 'media.stopVideoStream', id: 2
            }));
            ws.close();
        }
    }
    </script>
</body>
</html>
```

### 开启麦克风采集

开启后，机器人麦克风录到的声音会以 PCM 格式通过二进制 WebSocket 帧持续推送给客户端。

```json
{
    "jsonrpc": "2.0",
    "method": "media.startAudioCapture",
    "id": 102
}
```

**响应：**
```json
{
    "status": "started",
    "format": "PCM",
    "sampleRate": 16000,
    "channels": 1,
    "bitsPerSample": 16,
    "frameDurationMs": 20,
    "frameSizeBytes": 640
}
```

**音频帧格式：**

```
[字节0]  流类型 = 0x02 (麦克风音频)
[字节1]  标志位 (一般为0)
[字节2-3] 序列号 (uint16 大端序)
[字节4+] PCM 数据 (16-bit 有符号小端序, 640字节 = 20ms)
```

### 停止麦克风采集

```json
{
    "jsonrpc": "2.0",
    "method": "media.stopAudioCapture",
    "id": 103
}
```

### 开启扬声器播放

开启后，客户端可以发送二进制 PCM 音频帧，机器人扬声器会播放出来。

```json
{
    "jsonrpc": "2.0",
    "method": "media.startAudioPlayback",
    "id": 104
}
```

**响应：**
```json
{
    "status": "started",
    "format": "PCM",
    "sampleRate": 16000,
    "channels": 1,
    "bitsPerSample": 16,
    "streamType": "0x03"
}
```

开启后，向 WebSocket 发送二进制消息即可播放：

```
[字节0]  流类型 = 0x03 (扬声器播放)
[字节1]  标志位 (一般为0)
[字节2-3] 序列号 (uint16 大端序, 可填0)
[字节4+] PCM 数据 (16-bit 有符号小端序, 16kHz 单声道)
```

**建议：** 每帧发送 640 字节（20ms），每 20ms 发送一帧以保持流畅播放。

### 停止扬声器播放

```json
{
    "jsonrpc": "2.0",
    "method": "media.stopAudioPlayback",
    "id": 105
}
```

### Python 录制麦克风示例

```python
import asyncio, websockets, json, wave

async def record_mic():
    async with websockets.connect("ws://192.168.123.100:8175") as ws:
        # 开启麦克风
        await ws.send(json.dumps({
            "jsonrpc": "2.0", "method": "media.startAudioCapture", "id": 1
        }))

        # 录制3秒（每秒约50帧）
        frames = []
        for _ in range(150):
            msg = await ws.recv()
            if isinstance(msg, bytes) and len(msg) >= 4 and msg[0] == 0x02:
                frames.append(msg[4:])  # 跳过4字节头

        # 停止
        await ws.send(json.dumps({
            "jsonrpc": "2.0", "method": "media.stopAudioCapture", "id": 2
        }))

        # 保存为 WAV 文件
        with wave.open("temi_mic.wav", "wb") as wf:
            wf.setnchannels(1)
            wf.setsampwidth(2)  # 16-bit
            wf.setframerate(16000)
            for f in frames:
                wf.writeframes(f)
        print(f"已保存 {len(frames)} 帧到 temi_mic.wav")

asyncio.run(record_mic())
```

### Python 播放音频到扬声器示例

```python
import asyncio, websockets, json, struct, wave

async def play_to_speaker():
    async with websockets.connect("ws://192.168.123.100:8175") as ws:
        # 开启扬声器播放
        await ws.send(json.dumps({
            "jsonrpc": "2.0", "method": "media.startAudioPlayback", "id": 1
        }))
        await ws.recv()  # 等待响应

        # 读取 WAV 文件并发送
        with wave.open("audio.wav", "rb") as wf:
            while True:
                data = wf.readframes(320)  # 320个采样 = 640字节 = 20ms
                if not data:
                    break
                # 构造二进制帧: 4字节头 + PCM数据
                header = struct.pack('>BBH', 0x03, 0x00, 0)
                await ws.send(header + data)
                await asyncio.sleep(0.02)  # 每20ms发一帧

        # 停止播放
        await asyncio.sleep(0.5)
        await ws.send(json.dumps({
            "jsonrpc": "2.0", "method": "media.stopAudioPlayback", "id": 2
        }))

asyncio.run(play_to_speaker())
```

### Python 全双工音频示例（同时听和说）

```python
import asyncio, websockets, json, struct

async def full_duplex():
    async with websockets.connect("ws://192.168.123.100:8175") as ws:
        # 同时开启麦克风和扬声器
        await ws.send(json.dumps({
            "jsonrpc": "2.0", "method": "media.startAudioCapture", "id": 1
        }))
        await ws.send(json.dumps({
            "jsonrpc": "2.0", "method": "media.startAudioPlayback", "id": 2
        }))

        # 回声测试：把麦克风采集到的音频原样播放回扬声器
        for _ in range(500):  # 约10秒
            msg = await ws.recv()
            if isinstance(msg, bytes) and len(msg) >= 4 and msg[0] == 0x02:
                # 收到麦克风音频(0x02)，改为扬声器类型(0x03)后发回
                playback_header = struct.pack('>BBH', 0x03, 0x00, 0)
                await ws.send(playback_header + msg[4:])

        # 停止
        await ws.send(json.dumps({
            "jsonrpc": "2.0", "method": "media.stopAudioCapture", "id": 3
        }))
        await ws.send(json.dumps({
            "jsonrpc": "2.0", "method": "media.stopAudioPlayback", "id": 4
        }))

asyncio.run(full_duplex())
```

---

## 桥接信息 (bridge)

### Ping（测试连接）

```json
{"jsonrpc": "2.0", "method": "bridge.ping", "id": 200}
```

**响应：** `{"pong": 1773594135209}`（时间戳，可用于计算延迟）

### 获取版本信息

```json
{"jsonrpc": "2.0", "method": "bridge.getVersion", "id": 201}
```

**响应：** `{"version": "1.0.0", "sdk": "temi-sdk-0.10.77"}`

### 获取完整能力列表

查询桥接支持的所有方法和事件。

```json
{"jsonrpc": "2.0", "method": "bridge.getCapabilities", "id": 202}
```

**响应：**
```json
{
    "methods": ["bridge.getCapabilities", "bridge.getVersion", "bridge.ping", "follow.beWith", ...],
    "events": ["event.navigation.goToLocationStatusChanged", "event.speech.ttsStatusChanged", ...],
    "version": "1.0.0",
    "protocol": "JSON-RPC 2.0",
    "mediaProtocol": {
        "headerSize": 4,
        "streamTypes": {"0x01": "H.264 video", "0x02": "Opus audio out", "0x03": "Opus audio in"}
    }
}
```

---

## 事件监听

事件是机器人主动推送的状态变化通知。你不需要请求，只要 WebSocket 保持连接就会自动收到。

### 所有事件一览

| 事件 | 触发时机 | 参数 |
|------|----------|------|
| `event.speech.ttsStatusChanged` | 说话状态变化 | `{text, status, id}` — status: PROCESSING/COMPLETED/ERROR |
| `event.speech.asrResult` | 语音识别出结果 | `{text}` — 识别到的文字 |
| `event.speech.wakeupWord` | 检测到唤醒词 | `{wakeupWord, direction}` |
| `event.speech.conversationViewAttached` | 对话界面显示/隐藏 | `{isAttached}` |
| `event.speech.nluResult` | NLU 处理完成 | `{action, params}` |
| `event.navigation.goToLocationStatusChanged` | 导航状态变化 | `{location, status, descriptionId, description}` — status: going/complete/abort |
| `event.navigation.distanceToLocationChanged` | 到各位置的距离变化 | `{distances}` — 位置名到距离的映射 |
| `event.navigation.locationsUpdated` | 保存的位置列表变化 | `{locations}` — 位置名称列表 |
| `event.navigation.reposeStatusChanged` | 回充状态变化 | `{status, description}` |
| `event.movement.statusChanged` | 运动状态变化 | `{type, status}` — type: turn/tilt, status: start/complete/abort |
| `event.follow.beWithMeStatusChanged` | 跟随状态变化 | `{status}` — start/stop/... |
| `event.follow.constraintBeWithStatusChanged` | 约束跟随状态变化 | `{isConstraint}` |
| `event.follow.detectionStateChanged` | 人体检测状态变化 | `{state}` — 2=检测到人 |
| `event.navigation.currentPositionChanged` | 机器人位置变化（实时） | `{x, y, yaw, tiltAngle}` |
| `event.navigation.loadMapStatusChanged` | 地图加载状态 | `{status, statusName}` — statusName: start/complete/error_* |
| `event.movement.velocityChanged` | 移动速度变化（导航时） | `{velocity}` — 负值=前进 |
| `event.system.robotReady` | 机器人就绪状态 | `{isReady}` |
| `event.system.userInteraction` | 用户交互（触摸屏幕等） | `{isInteracting}` |
| `event.system.robotLifted` | 机器人被抬起 | `{isLifted, reason}` |

### Python 监听事件示例

```python
import asyncio, websockets, json

async def listen():
    async with websockets.connect("ws://192.168.123.100:8175") as ws:
        while True:
            msg = await ws.recv()
            if isinstance(msg, str):
                data = json.loads(msg)
                # 事件通知没有 id 字段
                if "id" not in data and "method" in data:
                    event = data["method"]
                    params = data.get("params", {})
                    print(f"事件: {event}")
                    print(f"参数: {params}")
                    print("---")

asyncio.run(listen())
```

---

## 错误处理

当指令执行失败时，响应中会包含 `error` 字段：

```json
{
    "jsonrpc": "2.0",
    "error": {
        "code": -32602,
        "message": "location required"
    },
    "id": 1
}
```

### 错误码表

| 错误码 | 含义 | 常见原因 |
|--------|------|----------|
| -32700 | JSON 解析错误 | 发送的不是有效 JSON |
| -32600 | 无效请求 | 缺少 `jsonrpc` 或 `method` 字段 |
| -32601 | 方法不存在 | 方法名拼写错误 |
| -32602 | 参数错误 | 缺少必填参数，或参数类型不对 |
| -32001 | SDK 错误 | temi SDK 调用失败 |
| -32002 | 权限不足 | 需要先申请对应权限 |
| -32003 | 机器人未就绪 | 机器人还在初始化 |
| -32004 | 媒体错误 | 摄像头/音频相关错误 |

---

## 完整示例

### 场景：迎宾机器人

让机器人在门口等待，检测到人后主动打招呼，问对方要去哪里，然后带路。

```python
import asyncio, websockets, json

async def greeter():
    async with websockets.connect("ws://192.168.123.100:8175") as ws:
        msg_id = 0

        def make_request(method, params=None):
            nonlocal msg_id
            msg_id += 1
            req = {"jsonrpc": "2.0", "method": method, "id": msg_id}
            if params:
                req["params"] = params
            return json.dumps(req)

        async def send_and_wait(method, params=None):
            await ws.send(make_request(method, params))
            while True:
                r = await ws.recv()
                if isinstance(r, str):
                    data = json.loads(r)
                    if data.get("id") == msg_id:
                        return data

        async def wait_for_event(event_name, timeout=30):
            deadline = asyncio.get_event_loop().time() + timeout
            while asyncio.get_event_loop().time() < deadline:
                r = await asyncio.wait_for(ws.recv(), timeout=timeout)
                if isinstance(r, str):
                    data = json.loads(r)
                    if data.get("method") == event_name:
                        return data.get("params", {})
            return None

        # 1. 去门口等待
        print("前往门口...")
        await send_and_wait("navigation.goTo", {"location": "待命点"})
        result = await wait_for_event("event.navigation.goToLocationStatusChanged")
        print(f"到达: {result}")

        # 2. 开启人体检测
        await send_and_wait("follow.setDetectionMode", {"on": True, "distance": 2.0})

        # 3. 等待检测到人
        print("等待客人...")
        while True:
            event = await wait_for_event("event.follow.detectionStateChanged", timeout=300)
            if event and event.get("state") == 2:
                break

        # 4. 打招呼
        await send_and_wait("speech.speak", {"text": "你好！欢迎光临，请问你想去哪里？"})
        await wait_for_event("event.speech.ttsStatusChanged")

        # 5. 提问并等待回答
        await send_and_wait("speech.askQuestion", {"question": "你想去客厅、书房还是餐厅？"})
        answer = await wait_for_event("event.speech.asrResult")
        user_answer = answer.get("text", "") if answer else ""
        print(f"客人说: {user_answer}")

        # 6. 根据回答带路
        destination = "客厅"  # 默认
        if "书房" in user_answer:
            destination = "书房"
        elif "餐厅" in user_answer:
            destination = "餐厅"

        await send_and_wait("speech.speak", {"text": f"好的，请跟我来，我带你去{destination}"})
        await wait_for_event("event.speech.ttsStatusChanged")

        await send_and_wait("navigation.goTo", {"location": destination})
        await wait_for_event("event.navigation.goToLocationStatusChanged")

        await send_and_wait("speech.speak", {"text": f"我们到{destination}了，祝你愉快！"})

asyncio.run(greeter())
```

### 场景：远程监控

通过视频流查看机器人视角，同时控制机器人转头观察。

```python
import asyncio, websockets, json, struct

async def monitor():
    async with websockets.connect("ws://192.168.123.100:8175") as ws:
        # 开启视频流
        await ws.send(json.dumps({
            "jsonrpc": "2.0", "method": "media.startVideoStream", "id": 1
        }))

        # 抬头看
        await ws.send(json.dumps({
            "jsonrpc": "2.0", "method": "movement.tiltAngle",
            "params": {"angle": 30}, "id": 2
        }))

        # 接收5秒视频 + 保存
        frame_count = 0
        with open("monitor.h264", "wb") as f:
            import time
            start = time.time()
            while time.time() - start < 5:
                msg = await asyncio.wait_for(ws.recv(), timeout=2)
                if isinstance(msg, bytes) and len(msg) >= 4 and msg[0] == 0x01:
                    f.write(msg[4:])
                    frame_count += 1

        print(f"录制完成，共 {frame_count} 帧")

        # 左转查看
        await ws.send(json.dumps({
            "jsonrpc": "2.0", "method": "movement.turnBy",
            "params": {"degrees": 90}, "id": 3
        }))

        # 停止视频流
        await ws.send(json.dumps({
            "jsonrpc": "2.0", "method": "media.stopVideoStream", "id": 4
        }))

asyncio.run(monitor())
```

---

## 方法速查表

| 想要做什么 | 方法 | 关键参数 |
|-----------|------|----------|
| 说话 | `speech.speak` | `text` |
| 停止说话 | `speech.cancelAllTts` | — |
| 提问等回答 | `speech.askQuestion` | `question` |
| 去某个位置 | `navigation.goTo` | `location` |
| 查看所有位置 | `navigation.getLocations` | — |
| 回充电桩 | `navigation.repose` | — |
| 抬头/低头 | `movement.tiltAngle` | `angle` (正=抬头, 负=低头) |
| 左转/右转 | `movement.turnBy` | `degrees` (正=左, 负=右) |
| 遥控移动 | `movement.skidJoy` | `x`, `y` |
| 停止运动 | `movement.stopMovement` | — |
| 跟随我 | `follow.beWith` | — |
| 人体检测 | `follow.setDetectionMode` | `on` |
| 查电量 | `system.getBattery` | — |
| 设音量 | `system.setVolume` | `volume` |
| 显示网页 | `display.loadUrl` | `url` |
| 显示 HTML | `display.loadHtml` | `html` |
| 清除屏幕 | `display.clear` | — |
| 执行 JS | `display.executeJavaScript` | `script` |
| 开视频流 | `media.startVideoStream` | — |
| 关视频流 | `media.stopVideoStream` | — |
| 开麦克风 | `media.startAudioCapture` | — |
| 关麦克风 | `media.stopAudioCapture` | — |
| 开扬声器播放 | `media.startAudioPlayback` | — |
| 关扬声器播放 | `media.stopAudioPlayback` | — |
| 测试连接 | `bridge.ping` | — |
| 查能力列表 | `bridge.getCapabilities` | — |
