# 多文件项目与 EasyClick 迁移

插件 0.9.0 支持 JS/CJS/JSON 模块、显式资源同步、设备与应用列表、内置窗口控制及 EasyClick 节点属性模式。首次使用需更新包含这些 API 的 APK；之后 F5 只连接和同步项目，不编译或安装应用。

## 项目结构与运行

```text
my-project/
  main.js
  config.json
  lib/task.js
  assets/message.txt
```

`main.js`：

```js
const task = require('./lib/task');
const config = require('./config.json');
const message = file.readFile(__dirname + '/assets/message.txt');
if (message === null) throw new Error(file.getErrorMsg());
task.process(config, message);
```

`lib/task.js`：

```js
exports.process = function (config, message) {
    logi('{}：{}', config.name, message);
};
```

在 `.vscode/launch.json` 中配置：

```json
{
  "version": "0.2.0",
  "configurations": [{
    "type": "xpauto",
    "request": "launch",
    "name": "XpAuto: 项目调试",
    "program": "${workspaceFolder}/main.js",
    "projectRoot": "${workspaceFolder}",
    "resources": ["assets"],
    "nodeMode": "xpauto"
  }]
}
```

`projectRoot` 默认是入口所在目录，自动递归同步其中的 `.js`、`.cjs`、`.json`。`resources` 是根目录内的相对文件或目录路径，不使用 glob；资源以原始字节同步。默认跳过隐藏项目以及 `node_modules`、`build`、`dist`、`out`、`coverage`。纯 JS npm 包可显式加入 `resources: ["node_modules/包名"]`，传递依赖也应包含。同步拒绝符号链接和越界路径，最多 512 个文件、单文件 16 MiB、总计 32 MiB。

每次运行安装一份独立快照。新快照成功保存后才替换缓存并清理旧目录，移除的资源不会留到下次运行；同步失败仍保留上一份可运行项目。手机悬浮球的“运行”使用最近保存的入口和整份依赖。`__dirname` 是手机快照目录，适合读资源或临时写入；需要跨同步保留的数据应另存到应用有权限访问的固定目录。

入口和依赖均在 CommonJS 局部作用域执行，可声明 `var task`、`function getprop()` 等局部名称。模块之间通过导出传数据，不共享各自的局部变量。无障碍 SDK 仍是全局 API。暂停时可在控制台查看当前帧的局部变量；运行完成后可读取全局变量及 `require.main.exports`。

## require 规则

| 接口 / 形式 | 行为 |
| --- | --- |
| `require('./helper')` / `require('../helper')` | 相对当前调用模块定位 |
| `require('/sdcard/Download/task.js')` | 读取手机绝对路径；需具有文件权限 |
| `require('pkg')` | 沿调用模块的父目录搜索 `node_modules/pkg`，再搜索项目根目录 |
| 省略后缀 | 依次尝试原路径、`.js`、`.cjs`、`.json` |
| 目录模块 | 读取 `package.json` 的 `main`，或 `index.js` / `index.cjs` / `index.json` |
| `exports.x = value` / `module.exports = value` | CommonJS 导出；JSON 直接返回解析值 |
| `require.resolve(name)` | 返回手机模块绝对路径，不执行模块 |
| `require.cache` | 本轮缓存，按手机绝对路径索引 |
| `require.main` | 当前入口模块 |
| `__filename` / `__dirname` | 当前模块在手机上的路径 / 目录 |

同一模块每轮执行一次，支持循环依赖（循环期间可能读到尚未填充完的 exports）。异常模块从缓存移除，可修复文件后重试。下一轮运行重新建缓存；手动重新加载可用 `delete require.cache[require.resolve(path)]`，已经持有旧导出的变量不会自动改变。

这是 V8 CommonJS 运行环境，不包含 Node 的 `fs`、`path`、`process` 等系统 API，也不支持 ES module `import/export` 或 Java/Dex 插件加载。文件使用 `file`，HTTP 使用 `http`。动态下载的 JS 可以通过已有 `http.downloadFile()` 保存后 `require(手机路径)`。

项目依赖映射为电脑文件 URL，支持依赖断点、单步与异常定位。包装保留源码行号，第一行的列偏移包含函数包装前缀；手机外部动态下载的文件没有对应电脑源码时显示其手机路径。

## 文件读写准确性

| API | 返回 / 约定 |
| --- | --- |
| `file.readFile(path)` | UTF-8 文本；失败返回 `null`，空文件返回 `""` |
| `file.writeFile(content, path)` | 内容在前，覆盖写入；成功返回 boolean |
| `file.writeToTxt(path, content)` | 路径在前，与 `writeFile` 使用相同写入实现 |
| `file.appendFile(path, content)` | UTF-8 追加，不自动添加换行 |
| `file.appendLine(line, path)` | 追加内容和一个 LF |
| `file.readFileBytes(path)` | `Int8Array` 或 `null`；字节的无符号值使用 `b & 255` |
| `file.writeFileBytes(path, bytes, append=false)` | 写 `Int8Array`，不经过文本编码 |
| `file.readAllLines(path)` | 不带换行符的各行，失败 `null` |
| `file.readLine(path, index)` | 零基行；无对应行返回空串 |
| `file.deleteLine(path, index, contains)` | 删除指定零基行或包含文字的行；`-1` 忽略行号，`null` 忽略文字条件 |
| `file.getErrorMsg()` | 当前线程最近一次读写的错误，成功后清空 |

文本整文件读取保留 BOM、CRLF、CR、LF 和末尾无换行的状态；不再按行拼接。覆盖先写同目录临时文件，再替换目标，短内容不会留下旧尾巴，写入失败不会提前删除旧目标。追加不具备整文件替换的原子性；跨线程写同一文件应自行串行化。任意二进制文件或非 UTF-8 内容应使用字节接口。普通文件接口不会自动请求 Root。

原生 URI 文本读写也使用 UTF-8；读取保留换行，覆盖使用提供器的 `wt` 截断模式。URI 写入的原子性由对应文档提供器决定。

## 设备信息与应用列表

```js
logi('{} / {}，{} × {}', device.getBrand(), device.getModel(),
    device.getScreenWidth(), device.getScreenHeight());
const apps = utils.getInstalledApps(); // 默认第三方应用
logd(apps);
const allPackages = utils.getInstalledPkgName(true); // 含系统应用
```

`device` 提供：`getScreenWidth/Height`（当前方向的物理像素）、`getDensity`、`getDensityDpi`、`getBrand`、`getModel`、`getManufacturer`、`getSdkInt`、`getOSVersion`、`getAndroidId`、`getBattery`、`isCharging`、`isScreenOn`、`getBrightness`、`getIpAddress`。

Android ID 遵循当前用户和应用签名范围，不是硬件序列号。亮度无法读取为 `-1`，电量未知可能为负数；IP 返回第一个活动接口的非回环 IPv4，无可用地址时为空字符串。

`utils.getInstalledApps(includeSystem=false)` 返回按包名排序的普通数组。每项包含 `packageName`、`appName`、`versionName`、`versionCode`、`system`、`enabled`、`launchable`、`firstInstallTime`、`lastUpdateTime`（时间为毫秒）。`getInstalledPkgName()` / `getInstalledAppName()` 分别返回包名 / 名称数组。应用声明了 `QUERY_ALL_PACKAGES` 供本地自动化枚举使用；设备用户、工作资料与系统包管理限制仍适用。

## 内置悬浮窗控制

```js
if (hasFloatViewPermission()) {
    setCtrlViewSizeEx({x: 20, y: 300});
    setLogViewSizeEx({x: 20, y: 500, w: 700, h: 500,
        textSize: 12, title: '任务日志', showCloseBtn: true});
    showLogWindow();
    logi('开始任务');
}
```

| API | 行为 |
| --- | --- |
| `hasFloatViewPermission()` | 检查系统悬浮窗权限 |
| `showLogWindow()` / `closeLogWindow()` | 显示 / 隐藏内置日志窗口；保留日志 |
| `showCtrlWindow()` / `closeCtrlWindow()` | 显示 / 隐藏内置悬浮球 |
| `setCtrlViewSizeEx({x,y,backgroundColor})` | 部分更新悬浮球位置和背景色，图标保持 48dp |
| `setLogViewSizeEx({...})` | 部分更新日志配置，隐藏时也可设置 |
| `setLogViewSize(w,h,textSize=12,backgroundColor='#ffffff')` | 简写日志配置 |
| `showLog()` / `hideLog()` | 内置日志窗的兼容别名 |

日志配置支持 `x,y,w,h,textSize,backgroundColor,title,showTitle,showCloseBtn`。坐标和宽高是像素，字号为 sp；宽高至少 160dp × 100dp，并限制在当前屏幕内。颜色使用 `#RRGGBB` 或 `#AARRGGBB`。`showTitle=false` 隐藏整个标题栏，包括关闭按钮和拖动入口，可继续通过 API 关闭。

除权限检查外，这些方法返回 boolean；未开启开发服务或未授予悬浮窗权限返回 false，参数错误抛出异常。配置保存在当前服务中，服务重新启动后恢复默认；关闭窗口不终止脚本。自定义业务窗口继续使用独立的 `floaty` API。

## EasyClick 节点对象兼容

在 launch 配置中设置 `"nodeMode": "easyclick"`，或脚本开始调用 `setNodeCompatibility('easyclick')`：

```js
setNodeCompatibility('easyclick');
const n = text('确定').getOneNodeInfo(1000);
if (n) {
    logd(n.text, n.clz, n.bounds, n.clickable);
    if (n.clickable) n.click();
    n.recycle();
}
```

字符串、状态、边界和索引等使用属性形式（`text/id/desc/clz/pkg`、`clickable/checked/enabled`、`bounds/visibleBounds/boundsInParent`、`depth/index/row/column` 等）。布尔值为真实 boolean，`false` 不会被包装成真值对象。动作、父子关系、`childCount()` 和集合操作仍是方法；后续查询结果、数组和集合自动转为兼容节点。选择器 `constructor.name` 为 `S`，节点为 `NodeInfo`。

`selector.attr` 返回可序列化的 `{expression: 条件链}`，可用于现有脚本的条件比较；不提供 EasyClick 私有 attr 字段结构。`Rect` 支持 `new Rect(left,top,right,bottom)` 和 `width/height/centerX/centerY()`。

默认模式保持 `node.text()`、`node.clickable()`。`setNodeCompatibility('xpauto')` 恢复后续查询的方法模式；已得到的兼容节点仍保留属性形式。要在混合脚本中使用准确的属性代码提示，可显式转换：

```js
const n = asEasyClick(text('确定').getOneNodeInfo());
if (n && n.clickable) logi(n.text); // VS Code 推断 clickable 为 boolean，text 为 string
```

类型提示无法根据运行时切换自动改变所有全局选择器签名，因此属性模式使用 `asEasyClick()` 或 `XpAutoEasyClickNode` 的 JSDoc 类型。兼容范围是已实现的选择器和节点动作，不意味着能够直接运行依赖 EasyClick Java 插件、OCR、ONNX 或外部系统环境的完整脚本。
