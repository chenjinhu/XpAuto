# VS Code 代码提示与 UI 节点分析

适用于 XpAuto 插件 0.2.0 和包含 `captureNodes` 能力的手机端。此次新增原生抓取接口，需要更新一次 APK；以后获取节点、运行脚本和 F5 调试均直接连接已经运行的应用，不会重新编译或安装。

## 使用步骤

1. 手机打开 XpAuto，开启无障碍服务与运行环境，通过 USB 授权 ADB 调试。节点抓取不需要 Root。
2. VS Code 打开 JavaScript 文件，即可获得全局 API、`shell.`、选择器链、节点和集合变量的代码提示。输入 `(` 或 `,` 查看参数，鼠标悬停查看中文说明；也可以用编辑器的“触发建议”命令。
3. 点击 XpAuto 侧栏标题上的节点树图标，或执行命令 **XpAuto: UI 节点抓取与分析**。点击“获取节点”，左侧保存抓取历史，中间显示手机截图，右侧显示节点树与属性。
4. 点击截图定位最深的可见节点。在“命中节点”列表中切换父节点或其他重叠节点；节点树也可以直接选择、展开、筛选，并使用父/子按钮导航。截图中的红框表示选中节点，黄框表示 XPath 匹配结果。鼠标移动显示手机像素坐标及截图颜色，缩放不改变坐标。
5. 在属性表勾选条件，底部自动生成选择器、单节点或多节点代码，并显示当前窗口的快照匹配数量。点击“复制代码”后粘贴到自己的 JS 文件中使用。

属性值支持双击复制。右键菜单包含：

| 操作 | 复制内容 |
| --- | --- |
| 复制值 | 当前右键行的完整属性值 |
| 复制选择器代码 | `text("确定").packageName("com.example")` 等表达式 |
| 复制单节点代码 | 查询节点、判断存在、打印文本的完整示例 |
| 复制多节点代码 | 使用集合 `size()`、`get(i)` 遍历的完整示例 |
| 复制绝对路径 XPath | 从 hierarchy / window 根开始的路径 |
| 复制相对路径 XPath | 按属性定位的表达式，重复时带位置限定 |

代码生成优先选择 ID、文本、描述，再补充包名；仍有重复时加入类名或边界。可以手动取消坐标条件，改选稳定属性。字符串中的引号、换行和特殊字符会自动转义。节点 `nid` 是本次快照的标识，不是可跨次抓取使用的选择器。

例如：

```javascript
const node = text("确定").packageName("com.example").getOneNodeInfo();
if (node && node.exists()) {
    logi(node.text());
    // 确认目标后，可自行加入 node.click() 等操作。
}

const nodes = clz("android.widget.TextView").find();
for (let i = 0; i < nodes.size(); i++) {
    const node = nodes.get(i);
    logi(node.text());
}
```

`UiCollection` 是 Java 集合包装器，需要使用 `size()` 和 `get(i)`；不是原生 JavaScript 数组。`getOneNodeInfo(timeout=0)` 未找到时返回 null；`getNodeInfo(timeout=0)` 返回节点数组。完整查询语义见 [选择器 API](selector-node-api.md)。

## XPath 与快照文件

XPath 在电脑上的本次快照中执行，例如 `//node[@text='确定']`、`//node[@clickable='true']`。节点属性采用面板中的 `id`、`clz`、`pkg`、`desc` 等名称。查询结果只选中和标记节点，不点击手机。0.4.0 起手机 SDK 提供 `xpath()`，复制的路径也可直接用于实时节点查询。

“保存节点与截图”将当前快照保存为 `.xpauto-ui.json`，包含节点、窗口、屏幕尺寸、抓取时间、设备序列号以及 PNG 截图。可以在未连接设备时载入、分析和复制代码。该文件含当时屏幕内容，应选择合适的保存位置。历史仅保存在当前插件进程中，最多 10 份且合计不超过 64 MiB，重载 VS Code 后清空；需要保留时主动保存。

抓取先读取节点树，再通过 ADB 获取截图，两者不是原子操作。页面动画、弹窗或滚动可能造成轻微不一致，建议在页面稳定时重新抓取。屏幕尺寸发生改变时会提示重试。系统禁止截图的页面可能显示黑色区域；自绘控件或部分 WebView 也可能没有完整无障碍节点。

分析器显示多个系统窗口，脚本选择器仍遵循现有 SDK，优先搜索当前活动窗口，并搜索其他无障碍窗口。节点查询也支持其他系统暴露的无障碍窗口。匹配数量是快照估算；界面变化、节点树截断或切换窗口之后，需要重新验证。

## 实现结构

| 文件 | 职责 |
| --- | --- |
| `vscode-extension/types/xpauto.d.ts` | 实际 SDK 的类型、中文说明与 `@xpauto` 标记 |
| `src/language-service.js` | 使用 TypeScript 语言服务推断 JS 变量与链式调用 |
| `src/completions.js` | 注册 VS Code 补全、悬停和参数提示 |
| `src/device.js`、`src/ui-capture.js` | 控制通道抓取、ADB 二进制截图、组装与导入快照 |
| `src/inspector.js` | Webview 生命周期、历史、文件对话框和剪贴板 |
| `src/inspector-html.js`、`media/inspector.js`、`media/inspector.css` | 三栏界面、截图命中、属性菜单与离线 XPath |
| `media/inspector-model.js` | 两端共用的格式校验、选择条件、代码与 XPath 生成 |
| `app/.../devtools/UiNodeCapture.java` | 使用现有无障碍服务只读遍历窗口与节点 |
| `app/.../devtools/DeveloperServer.java` | 提供 `XpAuto.captureNodes` 控制请求 |

新增 SDK 方法时同步更新 `.d.ts` 并保留中文注释及 `@xpauto` 标签。提示不会改动用户源码或 `jsconfig.json`，也不需要连接手机；当前覆盖 JS 本地文件及未保存文件中的全局接口、选择器、节点、Shell，以及常用 HTTP、文件和工具接口，不承担完整项目诊断或多文件模块解析。

控制通道握手通过 `capabilities: ["captureNodes"]` 声明抓取能力。旧 APK 会得到明确的更新提示。抓取在独立单线程执行器上运行，同一时间只处理一次抓取；不创建新的 V8 runtime、不接管调试会话，也不使用可能暂停其他无障碍服务的 UIAutomator。断点暂停时仍可抓取。

节点上限 5,000 个、深度上限 80、文本总预算 300,000 字符、单项最多 1,024 字符、遍历时间预算 4 秒。达到预算时返回部分节点并标记截断；已截断的文本和描述不能用于生成精确匹配条件。节点对象在读取后及时释放。截图采用 `adb exec-out screencap -p` 二进制读取，最大 16 MiB；导入文件最大 24 MiB，并检查树结构、循环引用、数量、类型及 PNG 尺寸。

Webview 使用限制资源来源的 CSP，节点文本通过 DOM `textContent` 展示，不拼入 HTML，不执行节点文本。文件操作由扩展宿主弹出原生对话框；网页只发送节点标识与复制类型，宿主重新生成要复制的内容。

VS Code 能力接入参考官方 [Programmatic Language Features](https://code.visualstudio.com/api/language-extensions/programmatic-language-features) 和 [Webview API](https://code.visualstudio.com/api/extension-guides/webview)。

## 开发验证

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest --offline
npm --prefix vscode-extension test
# 已安装新版 APK、手机位于 XpAuto 首页且运行环境开启：
node scripts/verify-vscode-debug.mjs
npm --prefix vscode-extension run package
code --install-extension vscode-extension/xpauto-0.2.1.vsix --force
```

单元测试覆盖类型推断、普通对象与字符串排除、参数与悬停信息、快照往返、无效树与 PNG、代码转义、集合遍历、坐标命中、切换设备与屏幕方向异常。

真实 VS Code 集成测试覆盖补全/悬停/参数 API、断点暂停时抓取、保存载入、六种剪贴板操作、生成的选择器和集合代码在 Android 执行，以及原有断点、单步、异常、死循环暂停/继续/停止与重连。测试同时断言手机 PID 和安装更新时间不变。

网页交互测试使用 Chrome 和 Playwright，载入一份在 XpAuto 首页抓取的真实快照，验证截图点击、颜色、树与属性联动、XPath、右键菜单、代码模式、工具栏和较窄窗口布局：

```sh
# XPAUTO_PLAYWRIGHT 可指定现有 Playwright 模块的绝对路径。
XPAUTO_SNAPSHOT=/path/to/home.xpauto-ui.json node scripts/verify-ui-inspector.cjs
```

截图输出到系统临时目录下的 `xpauto-node-inspector.png` 和 `xpauto-node-inspector-compact.png`，用于人工检查布局。此测试只打开独立的浏览器进程，不控制日常浏览器。

## 0.3.0 工作台布局

工具栏保留获取、保存、载入、XPath 查询，减少标题占用。左栏按包名、时间或设备搜索抓取历史；中栏显示截图和节点边界；右栏提供节点树、父子跳转、带表头的属性列表和代码预览。截图移动时显示原始像素坐标、Android 有符号 ARGB 整数及 RGB 十六进制值。

“单 Tab 模式”表示下一次抓取或载入替换当前选中的快照记录，其他历史保留；关闭后新增历史，仍受 10 份/64 MiB 上限约束。切换模式不会丢弃当前节点选择。模式与历史均属于当前插件进程，重启编辑器后重置；需要持久保留时使用“保存节点&截图”。

属性支持双击复制值，以及右键的六个动作：复制值、选择器代码、单节点代码、多节点代码、绝对 XPath、相对 XPath。代码使用 XpAuto 实际选择器；XPath 既可用于本地快照分析，也可传给手机 `xpath()` 查询实时节点。
