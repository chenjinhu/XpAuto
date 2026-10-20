# vixBrowser 浏览器 API

XpAuto 官方推荐浏览器为 **vixBrowser**，Android 包名 `com.vixmobi.browser`。接口依据浏览器工程 `android-webview-browser/HTTP_API.md` 封装，覆盖 1.4 版文档的全部 HTTP 端点。XpAuto 提供全局 `browser`，无需 import。插件 0.8.0 提供中文代码提示、参数说明及返回类型推断，包括新增的加载状态与进度。

## 快速开始

手机安装 vixBrowser 和包含本模块的 XpAuto APK：

```javascript
browser.start();
const state = browser.open("https://example.com");
logi(state.title, state.url);
logi(browser.executeJs("({title: document.title, heading: document.querySelector('h1')?.textContent})"));
```

默认地址为**脚本所在手机**的 `http://127.0.0.1:8765`。XpAuto 与浏览器在同一手机时不需要 Root、不需要 ADB 转发，也不受手机系统 HTTP 代理影响。`start()` 通过普通应用启动接口打开浏览器；需要安装浏览器且 Android 允许 XpAuto 启动 Activity，通常从可见界面或已授权的悬浮窗运行。新模块需更新一次 XpAuto APK，此后 F5 仍只连接和同步脚本。

## 导航与页面操作

| 方法 | 返回值和行为 |
| --- | --- |
| `start(timeout=10000)` | 启动本机浏览器并切到前台，连续两轮确认前台及 HTTP 就绪后返回当前状态；不保证页面已加载完成 |
| `getState()` | 当前 `state` 对象 |
| `getLoading()` | 查询主页面加载状态、进度、地址、标题和错误，需要 vixBrowser 1.4 |
| `navigate(url, settings={})` | 发起跳转，返回 HTTP 202 中的状态；不等待加载 |
| `open(url, options={})` | 发起跳转并等待加载结束，返回最终状态；合计默认 30 秒 |
| `waitForPage(timeout=30000, pollInterval=200)` | 连续两次同 URL 加载完成后返回状态，加载失败抛异常；1.4 检查 `load_status=complete` 且 `loading=false`，旧版沿用 `loading=false` |
| `back()` / `forward()` / `refresh()` | 发起后退、前进或刷新，返回接受操作时的状态；完成条件另用 `waitForPage()` |
| `clearCache()` | 清理 WebView 内存及磁盘资源缓存，成功返回 true |
| `executeJs(script)` | 执行主页面 JS 并直接返回实际结果 |
| `waitFor(script, timeout=30000, pollInterval=200)` | 重复执行只读条件脚本，直到返回真值，返回该值 |

`url` 使用 HTTP/HTTPS 页面地址。`navigate` 接受 `referer`、`ua`、`history_length` 三个可选配置字段。`open` 另外支持 `timeout` 和 `pollInterval`；字段拼错会在发送请求前报错。

```javascript
const state = browser.open("https://example.com", {
    referer: "https://source.example/",
    ua: "Mozilla/5.0 XpAuto/0.7",
    history_length: 7,
    timeout: 30000,
    pollInterval: 200
});

browser.refresh();
browser.waitForPage();
```

这些设置由浏览器保存，省略时沿用之前保存的值。浏览器先验证所有导航参数再更新配置。清缓存保留 Cookie、DOM Storage 和真实历史记录。`history_length` 只影响页面 JS 的读数，不能让本来没有前后历史的页面执行前进或后退。

`getState()` / 导航 / 等待返回普通 JS 对象：

```javascript
{
    url: "https://example.com/", title: "Example Domain",
    loading: false, load_status: "complete", progress: 100,
    can_go_back: false, can_go_forward: false,
    page_error: null,
    referer: null, ua: null, effective_ua: "Mozilla/5.0 ...",
    history_length: null, history_length_supported: true
}
```

尚未打开页面时 `url`、`title` 可以为 null。`load_status` 和 `progress` 从 vixBrowser 1.4 开始提供，旧版本缺少这两个字段，类型声明标为可选。`loading=false` 也可能表示未打开网页或加载失败；SPA 异步请求、组件和业务数据可用性应使用 `waitFor` 判断。等待超时只结束本次等待，不会撤销浏览器已经开始的导航。

`start()` 会实际打开浏览器界面；浏览器已在后台时将其切到前台，重复调用保留当前页面。前台判断复用 XpAuto 无障碍服务，请先完成运行环境的权限检测。如果 HOME 等界面切换覆盖了首次启动，等待期间最多每秒重发一次无 URL 的启动请求。后台 HTTP 可用不视为启动成功；无法进入前台时抛 `foreground_timeout`，提示检查解锁状态、无障碍服务和系统后台弹出界面限制。

## 加载状态与进度（vixBrowser 1.4）

`browser.getLoading()` 对应 `GET /api/loading`，直接返回以下普通 JS 对象，默认客户端和 `createClient()` 均支持：

```javascript
{
    ok: true, status: "complete", loading: false, progress: 100,
    url: "https://example.com/", title: "Example Domain", page_error: null
}
```

| `status` | 含义 |
| --- | --- |
| `idle` | 尚未打开网页，`loading=false`、`progress=0`、`url=null` |
| `loading` | 主页面正在加载，`loading=true` |
| `complete` | 主页面加载完成，`loading=false`、`progress=100` |
| `error` | 主页面加载失败，`loading=false`，说明见 `page_error` |

`ok=true` 表示查询成功，即使页面加载失败，`getLoading()` 也会返回 `status="error"`，供脚本展示错误；连接或接口查询失败仍抛异常。进度是 WebView 报告的 0～100 整数，失败时也可能达到 100。URL 随重定向变化，加载中标题可能仍是上一页标题。

```javascript
browser.start();
browser.navigate("https://example.com");
const loading = browser.getLoading();
logi("当前状态：", loading.status, "进度：", loading.progress);
if (loading.status === "error") loge(loading.page_error);

// 内置等待负责超时、停止、重定向与失败处理，返回完整 state。
const state = browser.waitForPage(30000);
logi("加载完成：", state.load_status, state.progress, state.url);
```

完成指主框架的 `onPageFinished`，不保证下一帧已绘制，也不等待 SPA、fetch/XHR 或业务数据。需要业务条件时继续调用 `waitFor()`。等待函数不把 `idle` 或进度 100 当作成功。

旧版浏览器可继续使用 `getState()`、`open()`、`waitForPage()`。`getLoading()` 需要升级浏览器至 1.4，旧版缺少该端点时保留原始 HTTP 404 错误，不伪造进度。

## 页面 JS 与异步页面

`executeJs` 返回字符串、数字、布尔值、对象、数组或 null。页面 `undefined` 转为 null；false、0 和空字符串保持原值。抛出的异常、Promise、BigInt、循环引用返回 `js_error`。页面 JS 和 XpAuto 脚本是两个环境，不能在页面中直接调用 `logi`、`sleep` 或选择器 API。

```javascript
// XpAuto 变量需通过 JSON.stringify 安全地写入页面脚本，避免引号破坏语法。
const content = '中文 "hello"';
browser.executeJs("document.querySelector('input').value = " + JSON.stringify(content));

// waitFor 会重复执行条件，条件应只读取页面状态。
const title = browser.waitFor("document.querySelector('h1')?.textContent", 10000);

// 异步操作可以先启动并写入页面变量，然后读取条件；executeJs 不直接等待 Promise。
browser.executeJs("window.xpautoDone = false; setTimeout(() => { window.xpautoDone = true; }, 500); null");
browser.waitFor("window.xpautoDone === true", 5000);
```

POST 请求体最大 **64 KiB UTF-8**，包含 JSON 的字段和转义开销，发送前会校验。`Content-Type` 与 `Content-Length` 由 HTTP 层正确设置。客户端禁止重定向和连接失败自动重试，导航和页面 JS 不会在错误后自动重放；响应丢失或超时后，命令可能已经执行，应先查询状态。

## 浏览器配置

| 方法 | 返回值与清除方式 |
| --- | --- |
| `getSettings()` | 所有保存配置、`effective_ua` 和 `history_length_supported` |
| `setSettings(settings)` | 原子更新至少一个字段，返回更新后的全部配置；省略字段保持不变 |
| `getUa()` / `setUa(value)` | 自定义 UA 字符串或 null；传 null / 空字符串恢复默认 |
| `getReferer()` / `setReferer(value)` | 自定义 Referer 字符串或 null；传 null / 空字符串恢复默认 |
| `getHistoryLength()` / `setHistoryLength(value)` | 覆盖值或 null；传 null 恢复真实值 |

```javascript
const original = browser.getSettings();
try {
    browser.setSettings({ ua: "XpAuto/0.7", history_length: 7 });
    browser.open("https://example.com");
    logi(browser.executeJs("({ua: navigator.userAgent, history: history.length})"));
} finally {
    browser.setSettings({ referer: original.referer, ua: original.ua, history_length: original.history_length });
}
```

UA 最长 4096 字符，使用可打印 ASCII；Referer 最长 4096 字符，使用完整 HTTP/HTTPS URL，不带账号密码或控制字符。具体规范化由浏览器统一处理，读取接口返回保存后的值。Referer 不保证覆盖子资源、fetch、重定向等请求，仍受 WebView 和网页策略影响。

`history_length` 必须为 null 或 `0..2147483647` 的整数。若旧 WebView 不支持文档开始脚本，设置非空值会抛 `history_override_unavailable`（HTTP 409）。配置可跨浏览器重启、旋转与覆盖安装保留；设置自身不承诺重新加载页面，可显式刷新后等待。

## 错误与超时

运行失败抛出普通 JS Error，`name` 为 `VixBrowserError`，保留以下字段，避免把合法的 null 当作失败：

| 字段 | 含义 |
| --- | --- |
| `code` | 浏览器原始 error，如 `js_error`、`no_history`、`invalid_ua`；客户端错误见下表 |
| `statusCode` | HTTP 状态码；无 HTTP 响应或本地等待错误为 0 |
| `message` | 浏览器的错误说明或中文客户端提示 |
| `response` | 原始错误响应，页面加载失败时含 `{state}`；没有响应为 null |

客户端错误包括 `transport_error`（连接失败/请求超时，HTTP 日志有详细原因）、`invalid_response`、`app_launch_failed`、`foreground_timeout`（浏览器未切到前台）、`start_timeout`（前台界面的 HTTP 接口未就绪）、`page_load_failed`、`page_load_timeout`、`condition_timeout`。参数错误抛 TypeError 或 RangeError。

```javascript
try {
    browser.executeJs("throw new Error('页面异常')");
} catch (error) {
    loge(error.code, error.statusCode, error.message);
}
```

单次 HTTP 请求默认 12000ms；浏览器 WebView 操作自身可能在 8 秒后返回 HTTP 504。等待函数按剩余总时间缩短请求和 sleep，超时范围 `1..3600000ms`，轮询间隔 `50..60000ms`。用户停止或断开调试时沿用网络和 sleep 的取消机制，不将取消吞掉或改成普通浏览器失败。`start()` 可重试启动请求和状态读取，不重试有副作用的页面命令。

## 自定义客户端与开发结构

```javascript
const other = browser.createClient({
    baseUrl: "http://127.0.0.1:9876", // 已自行建立的服务地址或转发
    timeout: 12000,
    pollInterval: 200
});
logi(other.getState());
```

配置互相独立，不改变默认 `browser`。`baseUrl` 只接受 HTTP/HTTPS 地址和可选端口，不含路径、账号、查询参数或片段。自定义地址客户端不能调用 `start()`，远端浏览器或转发由调用者准备。

实现位于 `app/src/main/assets/browser.js`，通过 `ScriptModules.ASSETS` 同时加载到开发调试和本地脚本运行环境；网络复用 `http.request`，浏览器启动复用 `utils.openApp`。状态、超时和条件等待均为普通 JS，不额外创建常驻服务。所有实例操作浏览器当前同一个页面；并发导航仍需由脚本协调。

示例见 [examples/browser/main.js](../examples/browser/main.js)。开发测试：

```bash
npm --prefix vscode-extension test
./gradlew :app:assembleDebug :app:testDebugUnitTest
XPAUTO_ADB=/path/to/adb ANDROID_SERIAL=设备序列号 node scripts/verify-browser.cjs
```

真机测试使用本机测试网页与 adb reverse，不依赖公网；测试后恢复浏览器配置和原页面地址，并清理测试端口。测试导航会留下浏览历史记录。
