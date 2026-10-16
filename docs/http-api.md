# 网络 API 与开发说明

接口命名参考 [EasyClick 网络函数](https://ieasyclick.com/docs/funcs/http-api)。XpAuto 使用全局 `http` 对象，提供同步 HTTP、上传、断点续传和 WebSocket。需要更新一次 APK；日常 F5 仍只连接手机和同步脚本。插件 0.3.0 提供中文补全、参数提示和返回类型推断。

## HTTP 请求

```javascript
const response = http.request({
    url: "https://httpbin.org/get",
    data: { keyword: "中文" },
    timeout: 10000,
    header: { "User-Agent": "XpAuto" }
});
if (response) {
    logi("状态：" + response.statusCode);
    logi(response.body);
}
```

| 方法 | 参数 | 返回值 |
| --- | --- | --- |
| `request` / `requestEx` | `options` | 响应对象或 `null` |
| `httpGetDefault` | `url, timeout=30000, headers={}` | 正文或 `null` |
| `httpGet` | `url, params=null, timeout=30000, headers={}` | 正文或 `null` |
| `httpPost` / `httpPostEx` | `url, params, files=null, timeout=30000, headers={}` | 正文或 `null` |
| `postJSON` | `url, json, timeout=30000, headers={}` | 正文或 `null` |
| `downloadFile` | `url, path, timeout=30000, headers={}` | 成功为 `true` |
| `downloadFileDefault` | `url, path, headers={}` | 成功为 `true` |
| `get` / `post` | `url` / `url, json` | 原有接口兼容入口，正文或 `null` |

超时单位为**毫秒**，必须大于 0。普通失败写入运行日志并返回 `null` / `false`，不会把错误栈当作响应正文。用户停止脚本时直接取消，不继续执行后面的业务语句。`requestEx` 与 `request` 行为相同；`httpPostEx` 采用标准 multipart，未额外实现未公开的 E2EE 私有协议。

`request(options)` 支持：

| 字段 | 说明 |
| --- | --- |
| `url`, `method`, `timeout` | 地址、方法（默认 GET）、总超时 |
| `header`, `cookie` | 请求头和手动 Cookie 对象 |
| `data` | GET/HEAD 查询参数；其他方法的表单字段；也可传已编码字符串 |
| `requestBody` | 原始文本正文；JSON 需先序列化并设置 Content-Type |
| `file` | `{key, filePath, fileName?, contentType?}` 数组；与 `data` 组成 multipart |
| `proxy` | `{host, port}` HTTP 代理；`false` 表示直连 |
| `followRedirects` | 默认 `true` |
| `userAgent`, `referrer` | 覆盖 User-Agent / Referer |
| `ignoreHttpErrors` | 默认 `false`，4xx/5xx 返回 null；设为 true 可读取错误响应 |
| `ignoreContentType` | 默认 `true`，允许将任意类型解码为字符串；false 时仅允许文本/JSON/XML 等 |
| `maxBodySize` | 默认 8 MiB，允许 1～32 MiB，超限失败；大文件用下载函数 |
| `responseCharset` | 强制字符集；默认响应声明的编码或 UTF-8 |

响应是普通 JS 对象，包含 `header`、`cookie`、`statusCode`、`statusMessage`、`charset`、`contentType`、`body`。重复响应头取最后一个值；Cookie 只返回本次响应中的值，不自动保存登录状态。请求头名称不区分大小写，响应对象保留服务器/协议实际返回的字段名。HTTPS/WSS 使用正常证书和主机名校验。普通请求沿用系统代理，回环地址（localhost、127.x.x.x、::1）默认直连；显式代理对象优先。

表单和上传：

```javascript
const result = http.httpPost("https://example.com/upload",
    { description: "任务截图" },
    { image: "/sdcard/Pictures/result.png" }, 30000,
    { Authorization: "Bearer YOUR_TOKEN" });
const json = http.postJSON("https://example.com/report", { completed: 3 }, 10000);
```

`params` 对象自动编码；字符串按已经编码的表单内容发送。`postJSON` 接收对象或 JSON 字符串。上传时 `params` 必须为对象，文件和 `requestBody` 不能混用。文件路径是**手机路径**，相对路径从应用私有 files 目录解析；仍受 Android 文件访问权限限制。

## 下载与续传

```javascript
const ok = http.downloadFile("https://example.com/data.zip", "downloads/data.zip", 60000);
logi(ok ? "下载完成" : "下载失败，详见日志");
```

下载流式写入目标旁的 `.xpauto.part` 文件，并记录 `.xpauto.part.json` 元数据。只在 URL 一致且有强 ETag 或 Last-Modified 时续传，发送 Range/If-Range，并校验 Content-Range、版本和完整长度。服务器忽略 Range 时从头写入；无验证标记时也重新下载，避免拼接不同版本。成功后替换目标并清除元数据；超时/断线/停止时保留部分数据，原有目标文件不变。重新调用同一目标路径即可继续。

不要同时操作下载目标和旁路文件；不需要续传时可删除两个旁路文件。下载不将整个文件载入内存。

## WebSocket

```javascript
const ws = http.newWebsocket("ws://192.168.1.10:8080/ws", null, 1);
ws.onOpen((socket, code, message) => logi("已连接：" + code));
ws.onText((socket, text) => logi("收到：" + text));
ws.onBinary((socket, bytes) => logi("字节数：" + bytes.length));
ws.onError((socket, message) => logi("网络错误：" + message));
ws.onClose((socket, code, reason) => logi("已关闭：" + reason));
if (ws.connect(10000)) {
    ws.setAutoReconnect(true);
    ws.startHeartBeat(null, () => "ping", 3000, true);
    ws.sendText("你好");
    ws.sendBinary(new Uint8Array([1, 2, 255]));
    for (let i = 0; i < 60; i++) sleep(1000);
    ws.close();
}
```

`type=1` 使用 OkHttp；`type=2` 使用 Java-WebSocket。每个脚本会话最多创建 16 个连接对象，关闭后可 `reset()` / `connect()` 复用。

| 方法 | 行为 |
| --- | --- |
| `connect(timeout=10000)` | 阻塞等待连接，毫秒，返回布尔值 |
| `close()` / `reset()` | 关闭并停止心跳和重连 / 重置后允许再次连接 |
| `isConnected()` / `isClosed()` | 当前连接状态 |
| `sendText(text)` / `sendBinary(bytes)` | 返回是否接受发送；单条最大 1 MiB |
| `onOpen/onText/onBinary/onClose/onError` | 设置回调；传 null 取消监听 |
| `setAutoReconnect(enabled)` | 断线后自动重试，尝试间隔至少 3 秒；主动 close 不重连 |
| `startHeartBeat(binaryCallback, textCallback, period, cancelOld=true)` | 生成应用心跳数据，周期至少 50ms；null 不发送 |
| `stopHeartBeat()` | 停止当前连接所有应用心跳 |
| `setCallTimeout/setReadTimeout/setWriteTimeout/setPingInterval(seconds)` | type=1，在连接前设置；0 表示不限/关闭 |
| `setConnectionLostTimeout(seconds)` | type=2 的连接存活检查；0 关闭 |

V8 是单线程引擎，网络线程**只入队数据，不调用 JS**。`connect()` 返回前、`sleep()` 期间以及显式调用 `http.pollEvents()` 时在脚本线程派发回调、生成心跳、处理自动重连。没有 sleep/pollEvents 的忙循环、同步 HTTP、原生长调用和断点暂停会延后回调；恢复后继续处理，心跳不会补发所有遗漏次数。回调抛出的异常按普通脚本异常处理，不会静默吞掉。

这是与 EasyClick 的主要运行差异：不依赖其 Java 反射对象或独立回调线程，二进制接收使用 `Uint8Array`，发送接受 `Uint8Array` 或数值数组。每个连接最多积压 256 个事件或约 4 MiB，超过时关闭连接并产生错误事件。脚本完成、出错、停止或调试断开时关闭该会话所有连接，释放事件和监听器。

## 开发结构与验证

- `network/HttpApi.java`：请求构建、响应解码、容量限制和会话取消。
- `network/HttpDownload.java`：临时文件、条件续传和完整性校验。
- `network/ScriptSocket.java`：两种连接实现和有界事件队列。
- `assets/http.js`：兼容接口、普通 JS 对象、线程内回调、心跳和重连。
- `DeveloperServer.Session` 与旧 `TaskBase` 入口各自创建 HttpApi；禁止将连接放入静态全局对象。
- `vscode-extension/types/xpauto.d.ts` 是中文补全和类型提示的共同来源。

验证命令：

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
node --test vscode-extension/test/*.test.js
XPAUTO_ADB=/path/to/adb node scripts/verify-network.cjs
```

Java 测试使用 OkHttp 官方 MockWebServer；手机测试临时启动本机 HTTP/WebSocket 服务，通过 adb reverse 连接，不依赖公网。覆盖中文/特殊字符、GET/POST/JSON、上传、错误、大小限制、断点续传与字节一致性、两种 WebSocket、心跳、重连、停止取消和下一轮恢复。验证结束清理测试文件与端口，并核对手机 PID/安装时间未改变。

若错误日志显示连接的是某个代理地址，请先检查手机系统代理。只对某一次请求直连可使用 `http.request({ url: "https://example.com", proxy: false })`，不需要更改整个手机的网络设置。
