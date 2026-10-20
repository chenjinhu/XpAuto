# XpAuto

> 这是一款基于无障碍开发的自动化工具, 支持Javascript语法编写脚本.
> 


> 感谢FATJS项目提供的思路, 本项目基础架构全抄FatJS.
> 
> API接口参考easyclick 尽可能和easyclick保持一致.



- 免费
- 学习
- 长期维护更新
- 交流/提建议/定制化需求 加微信:cjh-18888


## 官方浏览器

官方推荐浏览器 **vixBrowser** 已提供全局 `browser` 接口：启动、导航与等待、页面 JS、Referer/UA/history.length 配置、前进后退及清缓存。用法见 [浏览器 API](docs/browser-api.md) 和 [调用示例](examples/browser/main.js)。同一手机直接连接，无需 Root。

## 截图与图片

支持 `image.requestScreenCapture()` 授权、全屏/区域截图、Bitmap、PNG/JPEG/WebP 保存、Base64、像素读取和图片回收。接口及权限边界见 [截图 API](docs/image-api.md)，可运行示例见 [截图示例](examples/screenshot/main.js)。

全局 `getprop()`、`setprop()` 支持读取和写入 Android 系统属性，复用 Shell 的自定义 su、超时和取消机制。用法见 [系统属性 API](docs/system-properties-api.md)。

## 文档

开发者请先阅读 **[开发说明](docs/development.md)**：包含中文注释约定、代码职责、悬浮窗与抽屉实现、暂停/继续状态、Shell 内部结构和验证方法。

工程名称为 **XpAuto**，Android 包名与源码命名空间统一为 **com.vixmobi.auto**，主源码位于 `app/src/main/java/com/vixmobi/auto`。新包名版作为独立应用安装，历史版本的数据和授权不会自动继承；调试前先停止旧版，避免端口冲突。

### 节点选择器

支持精确/模糊/正则匹配、状态与层级、列表行列、级联、XPath、可取消等待和节点操作。详见 **[节点选择器 API](docs/selector-node-api.md)** 与 **[选择器示例](examples/selectors/main.js)**。0.4.0 的超时、精确匹配和空结果语义调整请阅读文档兼容说明。

### 当前应用信息

`getCurrentRunningPkg()` 获取前台应用包名，`getRunningActivity()` 获取当前窗口的 Activity 完整类名；需要无障碍服务，不需要 Root。空值规则、页面切换与实现说明见 **[当前应用与 Activity API](docs/running-app-api.md)**，示例见 **[应用信息示例](examples/running-app/main.js)**。

### 日志与提示

新代码使用 `logd / logi / logw / loge`，支持 `{}` 占位符、对象输出和等级过滤；提供 `toast / toast1 / toast2` 及自定义提示参数。完整说明见 **[日志与 Toast API](docs/logging-toast-api.md)**，示例见 **[日志示例](examples/logging/main.js)**。

### 脚本悬浮窗

已支持 `floaty` 的 13 个接口：权限申请、XML / View 创建、位置与尺寸、触摸、焦点、查询及关闭。脚本窗口独立于悬浮球和日志窗，停止或断开时自动清理；插件提供中文补全和参数提示。查看 **[悬浮窗 API 与开发文档](docs/floaty-api.md)**，运行 **[悬浮窗示例](examples/floaty/main.js)**。

### 网络函数

支持 HTTP 请求、表单/JSON、文件上传、断点续传和两种 WebSocket 连接。停止脚本会取消请求并清理连接；接口与线程约束见 **[网络 API 与开发文档](docs/http-api.md)**，示例见 **[网络示例](examples/network/main.js)**。

### Shell 与 Root

已支持 `shell.addSuBin()`、`su()`、`execCommand()`、`sudo()` / `ROOT()`、`installApp()`、`uninstallApp()`、`stopApp()` 和 `getLastResult()`。命令支持超时、停止控制和错误输出；应用管理操作需要授予 XpAuto Root 权限。

完整签名、权限说明、示例和失败处理见 **[Shell API 文档](docs/shell-api.md)**。可直接运行 [Shell 示例](examples/shell/main.js)。

### XpAuto 手机端与 VS Code 插件

插件 0.9.0 提供 **JavaScript 代码提示、UI 节点抓取与分析、节点属性复制和代码生成**。在 XpAuto 侧栏点击节点树图标即可打开截图、节点树与属性联动面板，支持快照保存/载入、XPath 测试及六种右键复制操作。新增历史搜索、单 Tab 模式和 ARGB/HEX 像素颜色。用法与开发结构见 **[节点分析与代码提示文档](docs/ui-inspector.md)**。

手机主界面提供权限状态、运行环境开关、最近同步脚本和运行控制台。打开应用或从授权页返回时，只要已有悬浮窗权限，就自动开启运行环境并显示悬浮球，无需再次点击启动。无障碍权限不影响悬浮球显示，但运行脚本前仍会检查必要权限。通知权限用于显示前台运行状态，拒绝通知不影响脚本执行。脚本保存在应用私有目录，不要求全盘存储权限。

开启运行环境后显示 48dp 的应用图标悬浮球。点击展开纵向菜单：运行、暂停/继续、停止、日志、面板、结束。拖动悬浮球可移动位置，位置会记住；靠近底部时菜单向上展开，点击外部或选择操作后收起。暂停按钮按真实脚本状态切换，尚无可运行脚本或当前无法执行的操作会置灰。运行按钮执行上次同步脚本，需先结束 VS Code 调试会话；暂停/继续和停止可控制正在调试或手机独立运行的脚本。等待 Shell 等原生操作时，暂停会显示“正在暂停”，在操作返回后暂停 JS。结束会停止脚本、断开开发连接并移除悬浮球和日志窗口，首页可重新开启运行环境。上次同步的脚本会保留，应用下次启动仍可运行。

点击“日志”打开实时日志悬浮窗，可在其他应用上查看 console.log、print、错误和运行结果。拖动标题移动窗口，点击“×”或再次点击菜单“日志”收起，脚本继续运行。默认跟随最新日志，手动浏览时暂停跟随，可用底部按钮恢复；“清空”同时清除首页和悬浮窗的手机日志。缓存保留最近 150 条并限制总文本大小，超长单条会标记截断，不影响 Shell 返回值和 VS Code 输出。

首页左上角菜单打开左侧抽屉，显示本机 IP（优先 Wi-Fi / 有线局域网地址）、实际应用版本及版本号、包名（长按复制）。系统设置和导出运行日志目前仅提供标为“待开放”的界面入口。返回键或点击遮罩可关闭抽屉。

首次更新 Android 原生功能需要安装新版 APK：

```sh
# 仅开发 Android 原生代码时需要 JDK 17 和 Android SDK
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

安装本地 VS Code 插件（打包需要 Node.js 22+）：

```sh
npm --prefix vscode-extension ci
npm --prefix vscode-extension run package
code --install-extension vscode-extension/xpauto-0.9.0.vsix --force
```

1. 手机打开 **XpAuto**，授予悬浮窗权限后自动显示悬浮球；运行脚本前开启无障碍服务。
2. 通过 USB 连接电脑并授权 USB 调试，`adb devices` 应显示 `device`。电脑只需要 ADB；可用 VS Code 设置 `xpauto.adbPath` 指定路径。
3. VS Code 打开本仓库，在 **XpAuto** 侧栏连接设备，多台设备时会提示选择。
4. 打开 `examples/inspector-probe/main.js`，在第 8 行 `let total = start + increment` 设置断点，选择 **XpAuto: 调试示例** 并按 F5。
5. 断点停住后，变量面板显示 `start = 5`、`increment = 7`，控制台输入 `start + increment` 返回 `12`。F10 单步、F11 进入函数、Shift+F11 跳出、F5 继续。
6. 完成后控制台可继续输入 `probeResult`，返回 `24`。状态栏与手机均显示运行状态。Shift+F5 结束会话后，修改脚本再按 F5。

**日常 F5 只连接已运行应用并发送当前 JS，不执行 Gradle、安装 APK 或重启应用。** 不调试时可用命令 **XpAuto: 运行当前脚本**。控制台显示 `console.log`、`print`、异常堆栈和原始文件行号，支持暂停时及运行完成后的表达式求值。停止脚本使用独立控制连接，支持中止 V8 死循环和断点暂停。

VS Code 插件使用自己的 DAP 适配器，手机端集成 Javet V8 Inspector 和现有无障碍 SDK。每次编辑器调试创建独立 runtime，SDK 与业务脚本分别加载，保留业务文件行号。支持 CommonJS 多文件项目、JSON 与显式资源同步，入口和依赖均可打断点；用法见 [项目与兼容 API](docs/project-api.md)。运行环境开启时监听 `127.0.0.1:9229`，通过插件创建的临时 ADB 转发连接；debug 和 release 均包含该用户可控入口，点击“结束”或“关闭运行环境”即可关闭。

验证命令（先打开手机应用并完成必要授权，均不会构建或安装 APK）：

```sh
npm --prefix vscode-extension test
node scripts/prepare-vscode-debug.mjs
# 使用真实 VS Code 扩展进程测试断点、单步、控制台、异常、停止与重连，
# 同时断言手机进程 PID 和包安装更新时间不变。
node scripts/verify-vscode-debug.mjs
# 真机 UI：抽屉、悬浮球拖动与展开、暂停/继续、停止、独立运行、面板与结束
node scripts/verify-floating-controls.cjs
# 默认开启：权限缺失、授权返回、无障碍未授权和手动结束；测试后恢复权限
node scripts/verify-overlay-autostart.cjs
```

集成测试默认使用 macOS 的标准 VS Code 路径；其他安装位置用 `XPAUTO_CODE` 指向 Code 可执行文件。ADB 路径可用 `XPAUTO_ADB` 指定，多设备测试用 `ANDROID_SERIAL`。连接失败时查看 VS Code **输出 → XpAuto** 或 `adb logcat -s XpAutoDevTools AndroidRuntime`，并检查手机运行环境和 USB 授权。插件入口是 `.vscode/launch.json` 中的 `type: xpauto` 配置，不再使用旧的 `pwa-node` 构建任务。

### 全局快捷事件
####  点击
##### clickPoint 点击节点
- 点击坐标
- @param x x坐标
- @param y y坐标
- @return {boolean|布尔型}
```javascript
function main() {
    var result = clickPoint(100, 100);
    if (result) {
        toast("点击成功");
    } else {
        toast("点击失败");
    }
}

main();
```

##### click 选择器点击
- 点击选择器
- @param selectors 选择器对象
- @return {boolean|布尔型}
```javascript
function main() {
    var selector = text("我是文本").findOne();
    var result = selector.click();
    if (result) {
        toast("点击成功");
    } else {
        toast("点击失败");
    }
}

main();
```
####  滑动

### 选择器 && 节点

#### text 属性选择
##### 全文本匹配
```javascript
function main() {
    //获取选择器对象
    var selector = text("设置");
    click(selector);
}

main();
```
##### 正则匹配
```javascript
function main() {
    //获取选择器对象
    var selector = textMatch(".*设置.*");
    var result = click(selector);
    if (result) {
        toast("点击成功");
    } else {
        toast("点击失败");
    }
}

main();
```

#### 节点信息类

##### 说明
节点对象NodeInfo，可以通过获取getNodeInfo方法获取到节点信息的数组,节点包含的信息如下
- id: 字符串，资源的ID
- clz: 字符串，视图类名，例如 android.widget.TextView
- pkg: 字符串，包名，例如com.xx
- desc: 字符串，内容描述
- text: 字符串，文本
- checkable: 布尔型，是否可选中
- checked: 布尔型，是否选中
- clickable: 布尔型，是否可点击
- enabled: 布尔型，是否启用
- focusable: 布尔型，是否可获取焦点
- focused: 布尔型，是否聚焦
- longClickable: 布尔型，是否可长点击
- scrollable: 布尔型，是否滚动
- selected: 布尔型，是否被选择
- childCount: 整型，子节点的个数
- index: 整型 节点的索引
- depth: 整型 节点的层级深度
- drawingOrder: 整型 节点的绘制顺序
- bounds: Rect型，空间对象
- - top: 整型，顶部位置
- - bottom: 整型，底部位置
- - left: 整型，左边位置
- - right: 整型，右边位置
- visibleBounds: Rect型，可视空间对象
- - top: 整型，顶部位置
- - bottom: 整型，底部位置
- - left: 整型，左边位置
- - right: 整型，右边位置

##### 选择器获取一个节点 getOneNodeInfo
- 通过选择器 获取第一个节点信息
- @param timeout 等待时间，单位是毫秒, 如果是0，代表不等待
- @return NodeInfo 对象 或者null
```javascript
function main() {
    //获取选择器对象
    //选择 节点 clz=android.widget.CheckBox所有节点，
    var node = clz("android.widget.CheckBox").getOneNodeInfo(10000);

    if (node) {
        var x = node.click();
        logd(x);
    } else {
        toast("无节点");
    }
}

main(); 
```
---
参考项目
- https://github.com/1754048656/FATJS (基本框架 + 大部分基础功能)
- https://ieasyclick.com/ (参考API设计)
- https://github.com/caoccao/Javet (V8引擎)
- https://github.com/kkevsekk1/AutoX (功能实现)

新增脚本模块：线程/定时器、工具与二维码、SQLite、点击/多点触摸/拖拽、输入法、节点快照和动态权限。参见 [线程](docs/thread-api.md)、[工具](docs/utils-api.md)、[数据库](docs/sqlite-api.md)、[快捷操作](docs/global-shortcut-api.md)；示例：`examples/script-modules/main.js`。

新增项目能力：精确 UTF-8 / 二进制读写、`device` 与应用列表、内置悬浮球和日志窗 API、`require()` 与资源同步、EasyClick 节点属性模式。详见 [项目与兼容 API](docs/project-api.md)，示例为 `examples/project/`。
