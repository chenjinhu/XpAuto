# XpAuto

0.10.1 修复调试启动失败后仍停留在“等待断点配置”的问题。手机拒绝运行或初始断点配置失败时，控制台显示具体错误并退出会话、释放手机连接；调试环境配置、请求启动和手机实际开始执行分别显示状态。

0.10.0 新增 **安装 XpAuto 到设备**：侧栏下载图标和命令面板均可使用。插件随附 APK，无需 Android 工程或 Gradle；多设备时选择目标设备，覆盖安装保留数据，完成后自动打开手机应用。手机仍需完成权限授权和开启运行环境，之后点击连接设备。F5 只同步项目。

需要安装其他 XpAuto 构建时，可设置 `xpauto.apkPath` 为本地 APK 的绝对路径。未捆绑 APK 的开发版本会显示文件选择框。运行或调试中需先停止会话；安装失败显示 ADB 原始原因，不自动卸载或降级。

0.9.0 新增 CommonJS 多文件项目、资源同步、`device` 与应用列表、内置悬浮窗控制、EasyClick 节点属性提示。首次更新对应 APK 后，日常 F5 只同步 JS 与资源。用法见工程 `docs/project-api.md`，示例见 `examples/project/`。

0.8.0 适配 vixBrowser 1.4：增加 `browser.getLoading()` 及 `status`、`progress` 代码提示，完整状态新增 `load_status`、`progress`。页面等待识别完成与失败状态，并兼容旧版浏览器。已有启动、导航、页面 JS、配置、缓存与历史操作均保留。手机端需更新一次 APK；完整文档位于工程 `docs/browser-api.md`，示例为 `examples/browser/main.js`。默认连接同机 `127.0.0.1:8765`，无需 Root。

通过 USB 连接已经运行的 XpAuto Android 应用，在 VS Code 中运行和调试 JavaScript。F5 只同步脚本，不构建、安装或重启应用。

手机打开 XpAuto，授予悬浮窗权限后自动开启运行环境并显示悬浮球，运行脚本前开启无障碍服务；电脑确保 ADB 可用。打开 XpAuto 侧栏连接设备，再打开本地 `.js` 文件，运行 **XpAuto: 调试当前脚本**。

支持源文件断点、条件断点、单步、调用栈、变量、异常暂停、调试控制台日志和表达式求值。侧栏和状态栏显示连接、运行、暂停、完成及错误状态。**XpAuto: 运行当前脚本** 可直接执行；**XpAuto: 停止脚本** 可中止执行。脚本完成后调试会话保留，方便控制台求值；Shift+F5 结束后可重新调试。

## 代码提示与节点分析

打开 `.js` 文件后支持 XpAuto 全局 API、Shell、链式选择器、节点变量和集合的补全、中文悬停说明及参数提示。输入 `shell.` 或 `text("确定").` 即可使用，无需连接手机。

点击侧栏的节点树图标，或执行 **XpAuto: UI 节点抓取与分析**，再点击“获取节点”。支持截图点选、节点树、属性筛选、坐标与颜色、离线 XPath 测试和快照保存/载入。属性双击复制值，右键可复制值、选择器、单节点、多节点及绝对/相对 XPath。底部可勾选属性并预览生成的代码。

节点抓取需更新一次手机 APK，并开启无障碍和运行环境；不需要 Root。抓取时不会重建调试会话，F5 仍只同步脚本。XPath 既支持电脑快照分析，也可直接传给手机 `xpath()` 查询。`getNodeInfo()` 返回节点数组；旧 `find()` 集合仍通过 `size()` 与 `get(i)` 遍历。抓取历史最多 10 份，重载编辑器后清空，需保留时点击保存。

也可以在 `.vscode/launch.json` 中配置：

```json
{
  "version": "0.2.0",
  "configurations": [{
    "type": "xpauto",
    "request": "launch",
    "name": "XpAuto: 调试当前脚本",
    "program": "${file}",
    "internalConsoleOptions": "openOnSessionStart"
  }]
}
```

可选 `device` 指定 ADB 序列号，`stopOnEntry` 在执行入口暂停。设置 `xpauto.adbPath` 可指定 ADB 路径。连接服务仅监听手机回环地址，插件按需建立并清理自己的 ADB 转发。

默认同步入口目录的 JS/CJS/JSON；launch 配置可设置 `projectRoot`、`resources` 和 `nodeMode`。入口与依赖在 CommonJS 局部作用域运行，支持 `require()`、`module.exports` 和依赖断点，手机无障碍 SDK 仍为全局 API。资源用 `file.readFile(__dirname + "/assets/文件")` 读取。

开发与验证：`npm ci`、`npm test`、`npm run package`。打包会先验证并收录已有的 `app/build/outputs/apk/debug/app-debug.apk`，不会自动构建；`XPAUTO_APK_PATH` 可选择其他 XpAuto APK，`XPAUTO_AAPT` 可指定 Android aapt 路径。需要先在发布机安装 Android build-tools。仓库根目录的 `node scripts/verify-vscode-debug.mjs` 使用真实 VS Code 扩展宿主和已运行的手机做集成测试。

## 0.2.1：悬浮窗函数提示

新增 `floaty.` 的 13 个函数提示，以及 `showFloatXml()` 返回视图、`findViewWithTag()` 子控件的链式提示和中文说明。手机端需更新到包含 floaty 的 APK；窗口坐标使用像素，XML 支持常用 Android 控件。返回视图是自动转到主线程操作的句柄。停止、异常、脚本完成或断开会话后自动清理脚本窗口。

## 0.3.0：网络函数与紧凑节点工作台

新增 `http` 请求、上传、断点续传和 WebSocket 的中文代码提示、参数说明及返回类型。`http.request()` 的响应和 `http.newWebsocket()` 的连接对象支持链式推断。手机端需要更新到包含网络模块的 APK；日常 F5 不编译、不安装。

节点面板增加历史搜索、单 Tab 模式、属性表头，以及坐标、Android ARGB 整数和 HEX 颜色显示。单 Tab 模式下新快照替换当前记录；关闭后继续保留多份历史。截图、节点树、六种属性右键复制和代码预览保持联动。

## 0.3.1：日志与 Toast

新增 `logd/logi/logw/loge`、`setLogLevel`、`toast/toast1/toast2` 与 Toast 扩展字段的中文补全和参数提示。节点代码改用 `logd` 输出；`print` 保留兼容。手机端需更新一次，随后 F5 仍不重新编译或安装。

## 0.4.0：节点选择器

补齐包名、状态、层级、列表位置、级联、XPath 与节点数组的中文提示。节点属性可勾选 checked、visible、focusable、focused、password、depth、index；复制的 XPath 可直接用于手机查询。选择器的精确匹配、超时和空结果语义已修正，升级前参阅工程 `docs/selector-node-api.md` 的兼容说明。

## 0.4.1：选择器日志

`print(text("搜索"))` 和四级日志显示完整条件链，字符串拼接与嵌套 JSON 同步支持；增加 `toString/toJSON` 中文提示。打印只描述条件，不触发节点查询。手机端需要更新一次原生实现，后续 F5 仍只同步脚本。

## 0.4.2：当前应用信息

新增 `getCurrentRunningPkg()` 和 `getRunningActivity()` 的中文补全与返回值说明。包名无结果为 `""`，Activity 无可靠记录为 `null`；手机端通过无障碍窗口和经过校验的窗口事件获取，不要求 Root。

0.5.0 补齐线程、定时器、utils、encodeDecoder、sqlite、多点触摸、拖拽、输入法、节点控制和权限函数的中文补全、悬停说明与参数提示。对应手机端需安装包含这些原生 API 的新版 APK；F5 不触发 APK 编译或安装。

### 截图与图片（0.6.0）

同时增加全局 `getprop`、`setprop` 的补全、中文说明及参数提示；系统属性用法见仓库 `docs/system-properties-api.md`，只读示例位于 `examples/system-properties/main.js`。

新增 `image` 的中文代码提示：系统截图授权、全屏/区域截图、Bitmap、文件保存、Base64、像素读取、裁剪和内存回收。返回对象可以继续补全 `getWidth/getHeight/isRecycled/recycle`。用法与兼容边界见 工程内 `docs/image-api.md`，示例见 工程内 `examples/screenshot/main.js`。需要更新一次手机 APK；日常 F5 继续只同步 JS。

0.7.1 修正 `browser.start()`：启动并切到浏览器前台，确认界面和 HTTP 接口均就绪后返回；已有页面保留，无法进入前台时明确报错。
