# 日志与 Toast API

接口名称和参数参考 [EasyClick 全局模块](https://ieasyclick.com/docs/funcs/global/#logd-调试日志)。新脚本统一使用 `logd / logi / logw / loge`。手机首页、日志悬浮窗、VS Code 调试控制台和设备输出通道接收同一条分级日志，Android Logcat 的标签为 `XpAutoScript`。

## 四种日志等级

```javascript
logd("当前节点：{}", { text: "确定", enabled: true });
logi("进度 {}/{}", 2, 5);
logw("第 {} 次重试", 3);
loge("请求失败：{}", "超时");
```

| 函数 | 日志等级 | 显示前缀 |
| --- | --- | --- |
| `logd(message, ...args)` | debug | `[DEBUG]` |
| `logi(message, ...args)` | info | `[INFO]` |
| `logw(message, ...args)` | warn | `[WARN]` |
| `loge(message, ...args)` | error | `[ERROR]` |

字符串中的 `{}` 按顺序替换；参数不足时保留占位符，多出的参数用空格追加。对象和数组转成 JSON，循环或重复对象引用标记为 `[Circular]`；BigInt 转成带 `n` 的字符串；Error 输出其堆栈。日志函数返回 `undefined`，`loge` 只记录错误，不会抛出业务异常。

选择器会显示完整筛选条件，例如 `print(text("搜索"))` 输出 `[DEBUG] text("搜索")`。对象的 `toJSON()` 返回字符串时，顶层日志直接显示该文字；嵌入对象或数组时仍使用 JSON 转义。打印选择器不会查询界面，更多示例见 [打印选择器](selector-node-api.md#打印选择器)。

`print` 保留为 `logd` 的兼容别名，也支持多参数。现有 `console.*` 仍可使用，走原有 V8 通道；新日志过滤规则作用于 `logd/logi/logw/loge/print`。引擎异常、Shell/HTTP 诊断和运行状态仍会输出，避免关闭业务日志后无法排查运行失败。

## 过滤与清除

```javascript
setLogLevel("warn", false); // 只输出警告和错误，关闭 Toast
logd("这条不会输出");
logw("仍然输出");
setLogLevel("off", true);   // 关闭业务日志，但允许 Toast
setLogLevel("debug", true); // 恢复默认设置
clearLog(3);                // 清除手机缓存最早的三行
clearLog();                 // 等同 clearLog(-1)，清空手机缓存
```

`setLogLevel(level, displayToast=true)` 返回布尔值。允许 `debug/info/warn/error/off`，不区分大小写；非法等级返回 false，不改变原有设置。每轮脚本开始重置为 `debug, true`，设置不会影响下轮运行或其他会话。关闭 Toast 会同时移除当前会话已经显示的提示。

`clearLog(lines=-1)` 操作首页与日志悬浮窗的共享缓存，不删除 VS Code 已收到的输出。行数按实际换行符计算，不按界面自动折行计算；0 不操作，-1 全部清除。缓存继续沿用 150 条/64000 字符、单条 4000 字符的上限。

## Toast 提示

```javascript
toast("操作成功");
toast1("系统短提示");
toast2("系统长提示");
toast("拖动可移动此提示", {
    x: 80, y: 400, width: 600, height: -2,
    duration: 3000, textColor: "#A7F3D0", draggable: true
});
```

`toast(message, extra?)` 不传 extra 时使用系统短 Toast。传入对象并有悬浮窗权限时显示自定义提示，否则回退为系统短 Toast，忽略位置等扩展样式，不自动打开权限设置。系统 Toast 的时长、两行限制、后台显示及频率限制由 Android 控制。

参考文档未定义 `toast1` 与 `toast2` 的具体样式差异，XpAuto 明确定义：`toast1(message)` 使用系统短 Toast，`toast2(message)` 使用系统长 Toast。二者不接受扩展样式；三个方法都返回 `undefined`，都受 `displayToast` 控制。

| extra 字段 | 类型与行为 |
| --- | --- |
| `x`, `y` | 整数像素坐标，屏幕左上为原点，自动限制在系统栏和缺口之外；默认靠近底部居中 |
| `duration` | 1～60000 毫秒，默认 2000；仅自定义提示精确使用该时长 |
| `textColor` | `#RRGGBB` 或 `#AARRGGBB`，默认白色 |
| `width`, `height` | 正整数像素，最大 16000；-1 使用屏幕可用尺寸，-2 自适应内容；超出屏幕时收缩 |
| `draggable` | 默认 false；true 可拖动，false 不拦截下层应用点击 |

全应用同时显示一条提示，连续调用会替换上一条。提示在主线程异步显示，不阻塞脚本。普通完成后按时长消失，因此 `toast("完成")` 后立即结束脚本也可见；脚本停止、异常或运行中断连时清理当前会话提示。结束运行环境清理全部提示。自定义提示不抢输入焦点，不可拖动时按 Android 触摸规则设置窗口透明度。

## 开发结构与验证

- `assets/logging.js`：格式化消息、全局函数和参数适配；不拼接或执行用户代码。
- `logging/LogPolicy.java`：可单测的等级过滤和 Toast 开关。
- `logging/ScriptLogApi.java`：每个会话独立的输出、设置和生命周期。
- `logging/ToastPresenter.java`：主线程系统 Toast、自定义浮层、拖动及定时清理。
- `devtools/LogBuffer.java`：缓存限额与按行清除。
- `DeveloperServer.Session`：将同一条消息发到手机缓存、调试通道和设备控制通道。
- `vscode-extension/types/xpauto.d.ts`：中文补全、参数提示和 Toast 属性说明。

新代码包含中文注释。示例见 [examples/logging/main.js](../examples/logging/main.js)。验证命令：

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
node --test vscode-extension/test/*.test.js
XPAUTO_ADB=/path/to/adb node scripts/verify-logging.cjs
XPAUTO_ADB=/path/to/adb node scripts/verify-vscode-debug.mjs
```

需要更新一次 APK 才能使用新增原生接口；后续 F5 继续只同步 JS，不编译、不安装。
