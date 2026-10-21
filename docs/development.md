# XpAuto 开发说明

本文说明手机端运行环境、悬浮窗、首页菜单和 Shell 的代码组织，供后续修改与排查问题使用。脚本作者使用的 Shell 接口另见 [Shell API](shell-api.md)。

## 开发工具一键安装（0.10.0）

`xpauto.install` 在设备视图标题、欢迎页和命令面板提供入口。命令优先读取 `xpauto.apkPath`，否则校验插件随附的 `resources/mobile/xpauto.apk` SHA256；未捆绑包的开发版本允许手动选择。安装使用配置的 ADB，不依赖手机开发服务。

`DeviceManager.selectDevice()` 与连接流程共用设备枚举和多设备选择。安装先检查 Android API 30 及以上，并只读查询已有开发服务状态，运行或调试中拒绝覆盖；本窗口存在调试会话时命令也会拒绝安装。ADB 安装使用独立参数 `-s SERIAL install -r APK`，总超时 5 分钟，防止普通连接的 15 秒超时误杀大型 APK 安装。失败保留 ADB 原始原因，绝不自动卸载应用或强制降级；成功后启动 DeveloperActivity，引导用户完成权限。安装和连接互斥，不会更改 F5 流程。

发布时 `npm run package` 的 prepackage 只收录已构建 APK，使用 aapt 校验应用标识并生成版本和摘要；构建产物不进 Git。自定义构建可通过 `XPAUTO_APK_PATH`、`XPAUTO_AAPT` 指定。新增安装测试覆盖设备筛选、取消、忙碌保护、版本限制、失败保留、启动失败和捆绑包损坏。

2026-10-21 验证：37 项插件测试及类型检查通过。`scripts/verify-vscode-install.mjs` 在显式选择的空闲 Pixel 7a 上通过真实命令验证覆盖安装、初次安装时间保留和打开应用。完整调试回归使用临时配置隔离内置 TypeScript/Copilot 补全提供者后通过，覆盖补全、节点抓取、断点、控制台、异常和暂停恢复停止；调试期间 PID 与安装时间均不变。已生成并安装插件 0.10.0。安装测试需要设置 `ANDROID_SERIAL` 和 `XPAUTO_ADB`，不会隐式挑选其他已连接设备。

## 开发约定

1. 新增或修改代码的关键注释使用中文，重点解释设计原因、线程约束和边界情况。变量名和方法名保持直观，不给显而易见的赋值逐行加注释。
2. 优先使用清晰的条件判断和小函数。只有多个地方确实需要共用时才抽取公共代码，不为简单界面增加通用框架。
3. 不在主线程执行 Shell、等待脚本完成或关闭 V8。性能优化应针对实际开销，不能为了减少少量代码而隐藏状态或生命周期。
4. 功能行为变化时同步修改相关文档。影响运行控制的修改要验证实际状态，不能只检查按钮文字。
5. 原生代码更新需要安装一次 APK；日常 JS 调试只连接应用和同步脚本，不应给 F5 增加构建、安装或重启动作。

## 工程命名与目录

统一使用以下命名，源码包声明必须与物理目录一致：

| 项目 | 名称 |
| --- | --- |
| 工程名称与应用显示名称 | XpAuto |
| Android applicationId 与 namespace | com.vixmobi.auto |
| Java 根包 | com.vixmobi.auto |
| 主源码目录 | app/src/main/java/com/vixmobi/auto |
| 单元测试目录 | app/src/test/java/com/vixmobi/auto |
| Android 测试目录 | app/src/androidTest/java/com/vixmobi/auto |
| 调试专用源码目录 | app/src/debug/java/com/vixmobi/auto |
| 界面主题 | Theme.XpAuto |

根包下保留各功能的现有边界：devtools 管理首页、悬浮窗与调试；node 提供无障碍操作；utils 提供 Shell、文件等工具；ui、activitys、service、config 等目录承载已有界面与系统服务。共享 Constant 和旧日志服务 devplugin 也归入同一个根包，不再散落到其他产品命名空间。

修改包名时同时检查 Manifest 组件、导航 XML、R 与 ViewBinding 导入、JNI 导出函数、测试脚本中的 ADB 包标识及文档链接。InspectorInterrupt 的 JNI 名称使用 Java_com_vixmobi_auto_devtools_ 前缀，遗漏会导致暂停功能在运行时找不到原生方法。

Android 将新的 applicationId 视为新应用，不能覆盖升级历史包名版本，也不会自动继承其私有数据和权限。测试手机安装新版前先停止旧版，避免两个调试服务争用 9229 端口；保留旧版数据，悬浮窗、无障碍和 Root 权限按新应用身份授权。后续普通版本升级保持此 applicationId 稳定。

## 环境与启动

- 使用 JDK 17 和 Android SDK Platform 34 构建。
- 构建工具为 AGP 8.9.2、Gradle 8.11.1，原生库与 APK/AAB 的 16 KB 检查见 [兼容性文档](native-16kb.md)。
- 原生中断桥接使用 NDK 27.0.12077973、CMake 3.22.1；可通过 Android Studio SDK Manager 安装。
- 应用最低 Android 11（API 30），当前目标 API 34。
- 应用包名为 **com.vixmobi.auto**，产品名称为 **XpAuto**。
- 打开首页或从授权页返回时，有悬浮窗权限就自动开启运行环境并显示悬浮球；运行脚本前才检查完整的必要权限。Root 不属于启动必需权限。
- 调试服务仅监听手机上的 **127.0.0.1:9229**，电脑使用 ADB 端口转发连接。

~~~sh
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.vixmobi.auto/com.vixmobi.auto.devtools.DeveloperActivity
~~~

手机端开发入口位于 [devtools 目录](../app/src/main/java/com/vixmobi/auto/devtools)。

## 文件职责

| 文件 | 负责的内容 |
| --- | --- |
| [DeveloperActivity.java](../app/src/main/java/com/vixmobi/auto/devtools/DeveloperActivity.java) | 首页、左侧抽屉、权限引导、运行按钮、日志展示 |
| [DeveloperService.java](../app/src/main/java/com/vixmobi/auto/devtools/DeveloperService.java) | 前台服务与通知、调试服务生命周期、悬浮窗操作分发 |
| [FloatingControls.java](../app/src/main/java/com/vixmobi/auto/devtools/FloatingControls.java) | 悬浮球、纵向菜单、拖动、边界限制、按钮状态 |
| [FloatingLogWindow.java](../app/src/main/java/com/vixmobi/auto/devtools/FloatingLogWindow.java) | 日志悬浮窗、拖动、自动滚动、清空与关闭 |
| [LogBuffer.java](../app/src/main/java/com/vixmobi/auto/devtools/LogBuffer.java) | 两个界面共用的日志缓存、容量限制、线程安全快照 |
| [DeveloperServer.java](../app/src/main/java/com/vixmobi/auto/devtools/DeveloperServer.java) | WebSocket 协议、V8 会话、脚本缓存、暂停/继续/停止 |
| [InspectorInterrupt.java](../app/src/main/java/com/vixmobi/auto/devtools/InspectorInterrupt.java) | 暂停中断的 Java 接口与生命周期 |
| [inspector_interrupt.cpp](../app/src/main/cpp/inspector_interrupt.cpp) | 调用 V8 的跨线程中断接口，把暂停切回脚本线程 |
| [AutomationPermissions.java](../app/src/main/java/com/vixmobi/auto/devtools/AutomationPermissions.java) | 悬浮窗显示和脚本运行各自所需的权限检查 |
| [DeviceInfo.java](../app/src/main/java/com/vixmobi/auto/devtools/DeviceInfo.java) | 读取本机网络地址和已安装应用的版本信息 |
| [UiStyle.java](../app/src/main/java/com/vixmobi/auto/devtools/UiStyle.java) | 两处界面共用的颜色、圆角背景、应用图标 |
| [drawable 目录](../app/src/main/res/drawable) | 以 xpauto_ic_ 开头的菜单和控制按钮矢量图标 |
| [verify-floating-controls.cjs](../scripts/verify-floating-controls.cjs) | 手机实际界面与运行控制回归 |

这些类按职责拆分，首页不直接管理 V8，悬浮窗不直接执行脚本，服务只负责把操作交给运行环境。

~~~mermaid
flowchart LR
    A[首页 / 悬浮球] --> B[DeveloperService]
    B --> C[DeveloperServer]
    D[VS Code 插件] -->|ADB 转发| C
    C --> E[独立 V8 会话]
    E --> F[Shell / 无障碍 API]
    C -->|状态通知| B
    B -->|更新按钮和文字| A
~~~

## 运行状态与线程

首页与悬浮窗读取相同的运行状态。不要通过中文提示文字反推状态，也不要在点击“暂停”时直接把界面标成“已暂停”。

| 状态 | 含义 |
| --- | --- |
| idle / ready | 等待连接或等待执行脚本 |
| running | JS 正在执行 |
| pausing | 已请求暂停，等待当前执行点可以暂停 |
| paused | Inspector 已确认暂停，包含断点暂停 |
| completed / error / stopped | 正常完成、执行失败、被用户停止 |

界面创建、拖动和窗口更新在 Android 主线程执行。每个 V8 会话使用一个脚本执行线程；悬浮窗的运行控制请求由服务分发到工作线程。服务每 250ms 轻量检查一次状态，确保脚本会话释放后“运行”按钮及时恢复。FloatingControls 会比较新旧状态，状态未改变时不会重复创建图标或更新按钮。

### 暂停与继续

1. 创建 V8 会话时就启用 Inspector 调试器，本地运行和 VS Code 调试均可暂停。
2. 点击暂停先进入 pausing 状态，通过 **RequestInterrupt** 请求 V8 在脚本线程进入调试暂停。重复点击不会重复排队。
3. 收到 **Debugger.paused** 通知后才进入 paused，按钮切换为“继续”。
4. 点击继续发送 **Debugger.resume**，收到 resumed 通知后恢复 running。

Javet 5.0.11 在未暂停时发送普通 Inspector 请求会获取 V8 隔离锁；忙循环持有该锁，因此直接发送 Debugger.pause 无法中断它。代码用很小的 JNI 桥接调用 V8 的 RequestInterrupt，再在执行线程有 JS 调用栈的位置调用 breakProgram。电脑发送的 Debugger.pause 也走同一入口。

桥接只在初始化时捕获当前 Isolate 指针、点击暂停时申请中断，不改写 JS 源码，不向循环插入检查，也不改变断点行号。中断回调只进入调试暂停，不执行用户 JS。关闭时先禁止新请求，等待脚本线程关闭 V8，最后释放原生连接及 Java 全局引用，避免排队回调访问已释放内存。

原生桥接通过 dlsym 查找公开 V8 方法的导出符号，不依赖 Javet 内部对象的内存偏移。动态库名称固定对应 Javet 5.0.11；升级 Javet 时必须同步核对库名与符号，并执行真机忙循环暂停、继续、停止及反复连接测试。相关依据：[Javet 5.0.11 的请求分发源码](https://github.com/caoccao/Javet/blob/5.0.11/cpp/jni/javet_jni_core_v8.cpp)、[V8 RequestInterrupt 文档](https://v8.github.io/api/head/classv8_1_1Isolate.html)。

暂停作用于 JS 执行。已经进入 Java 的 Shell 命令、系统调用或其他同步操作需要先返回，之后 JS 才能暂停；暂停不能撤回已经执行的点击或安装操作。此时界面保持“正在暂停”，用户仍可选择停止。

“不调试运行”通过 XpAuto.run 的 noDebug 参数忽略源码中的 debugger 语句和断点；用户主动点击暂停时临时恢复暂停能力，继续后恢复该模式。

### 停止与结束

- **停止**取消当前脚本和当前会话的 Shell 等待，但保留前台服务、悬浮窗和开发连接。
- **结束**调用服务停止流程，移除悬浮球和日志悬浮窗，取消脚本，关闭 V8 与 WebSocket 服务，删除前台通知。应用首页仍保留，可以重新开启运行环境。
- V8 与服务的关闭可能需要等待工作线程清理，所以关闭动作不直接在 UI 线程等待。

## 悬浮窗布局与交互

### 默认开启

DeveloperActivity 在 onResume 检查悬浮窗权限。有权限且服务未开启时启动前台服务，因此首次打开、再次进入应用、授权返回都会自动显示悬浮球；服务已开启时不重复启动。DeveloperService 创建悬浮窗只检查悬浮窗权限，脚本运行入口保留无障碍及悬浮窗权限检查，不会自动执行缓存脚本。

自动启动不能放进界面的定时刷新：用户点击“结束”或“关闭运行环境”后，本次停留期间保持关闭，下一次进入首页时再默认开启。暂停和停止不应被后续撤销的无障碍权限拦住。

### 尺寸与操作

默认显示直径 **48dp** 的应用图标，外框留 4dp 空间容纳阴影；实际触摸窗口约为 56dp 见方。图标直接读取应用图标资源，替换应用图标后无需另改悬浮窗图片。

展开菜单宽约 92dp，每个操作按钮高 44dp，按钮之间相隔 6dp。按钮使用矢量图标和文字，按以下顺序纵向排列：

| 按钮 | 行为与可用条件 |
| --- | --- |
| 运行 | 存在缓存脚本且没有占用中的会话时，运行上次同步脚本 |
| 暂停 / 继续 | 控制实际 JS 执行；运行时显示暂停，暂停时显示继续 |
| 停止 | 运行、正在暂停或已暂停时可用 |
| 日志 | 打开或收起实时日志悬浮窗，不改变脚本运行状态 |
| 面板 | 回到自动化首页 |
| 结束 | 关闭运行环境并移除悬浮球 |

运行按钮不会重新编译或安装 APK。VS Code 仍占用调试会话时，需要先结束该调试会话，手机才能独立运行缓存脚本。

实现要点：

- 使用同一个 WindowManager 窗口，收起时同时缩小窗口尺寸，不能留下透明菜单的触摸区域。
- 按下后移动距离超过系统触摸阈值才算拖动；没有拖动时通过 performClick 处理点击，避免释放手指时误展开。
- 位置记录的是收起状态的圆球锚点。展开时优先向下，底部空间不足时向上；横向和纵向都限制在系统栏、屏幕缺口之外的可用范围内。
- dp 转换为像素后再逐项求和，避免 2.625 等非整数屏幕密度使最后一个按钮被裁掉。
- 菜单高度按实际按钮数计算；横屏等可用高度不足时菜单可以滚动，保证“结束”等操作仍可触达。
- 拖动结束保存坐标，旋转屏幕或重新开启时重新限制边界。
- 点击窗口外部或执行按钮操作后收起，避免长时间遮挡其他应用。

修改尺寸时集中检查 FloatingControls 的按钮尺寸、menuHeight 和 layout 方法，并同步更新真机测试对小圆球与纵向菜单的检查。

### 日志悬浮窗

点击悬浮菜单中的“日志”打开独立窗口，再次点击收起。窗口内容约 320×280dp，另外留 4dp 阴影边距；小屏或横屏时按系统栏、缺口之外的可用空间收缩并限制位置。拖动“运行日志”标题可以移动，位置会保存。悬浮球保持在日志窗口上方，方便随时暂停、停止或结束。

窗口显示与首页控制台相同的时间戳、logd/logi/logw/loge、Shell 诊断以及脚本完成或异常信息，同时兼容 console.* 和 print。在其他应用上也可以查看，不需要额外权限或 Root。

- 默认开启“跟随”，新日志出现时自动滚到底部。
- 手动触摸滚动区域后关闭跟随，方便阅读历史内容；点击“跟随：关”恢复。关闭跟随只改变滚动位置，内容仍继续更新。
- “清空”清除手机进程内的共用日志缓存：首页和悬浮窗一起更新，VS Code 已收到的输出不受影响。
- 点击右上角“×”仅关闭日志窗口；脚本、悬浮球和日志收集继续运行，再次打开可看到最近日志。
- 结束运行环境时两个窗口一起移除；再次启动默认只显示悬浮球，日志窗口由用户按需打开。

DeveloperService 复用现有 250ms 刷新任务，只在日志窗口打开时更新它；文本不变时不重新布局。关闭窗口后不保留独立定时任务。日志在工作线程写入线程安全的 LogBuffer，View 只在主线程操作。

缓存最多保留 150 条、合计 64,000 个 UTF-16 字符，单条显示最多 4,000 个字符。超长内容标记“显示已截断”，容量超限淘汰最旧条目；不改动 Shell API 的返回结果，也不截断发送给 VS Code 的内容。快照仅在内容变化后拼接一次。日志只在进程内保存，不等同于运行日志导出功能。

## 首页抽屉

左上角按钮打开 AndroidX DrawerLayout。抽屉支持遮罩点击、侧滑和返回键关闭；重建 Activity 时保留抽屉开关状态。

设备信息来自系统：

- **IP**：优先读取 Wi-Fi 或有线网络地址；没有时读取当前活动网络。优先 IPv4，排除回环和链路本地地址。没有网络时显示“未连接网络”，不会伪造地址。
- **版本**：从已安装包的 PackageInfo 读取版本名称和版本号，不在界面写死。
- **包名**：从 Context 读取，支持长按复制。

IP 在打开抽屉及回到已打开的抽屉时刷新，不增加持续的网络轮询。系统设置和导出运行日志目前只是禁用的占位行，标有“待开放”，没有绑定设置页、文件导出或分享动作。

## Shell 实现入口

Shell 相关代码位于 [utils 目录](../app/src/main/java/com/vixmobi/auto/utils)：

- **ShellUtils**：保留旧 Java 静态调用方式，创建脚本会话使用的 ShellApi。
- **ShellApi**：方法参数、Root 路径与权限检查、应用管理命令、上一次结果、会话取消标志。
- **ShellProcess**：创建进程，同时排空 stdout 和 stderr，限制输出内存与执行时间，负责关闭流。

自定义 su 配置在应用进程内共享，取消标志属于各自脚本会话。每次 Root 命令校验实际 UID。不能在命令失败时自动更换 su 重试，因为命令可能已经产生副作用。

停止 Shell 时必须通过异常中断 Java 回调。只返回一个空字符串可能让 V8 在检查终止标志前继续执行下一条 JS 语句。

Android 命令使用独立进程组，监督进程通过输入管道的关闭获知取消或超时，终止本组命令。已交给系统服务的安装操作、主动脱离进程组的守护进程不保证随等待结束而停止。详见 [Shell API 的超时与结果说明](shell-api.md)。

## 验证与排查

~~~sh
# 原生编译与 Shell 单元测试
./gradlew :app:assembleDebug :app:testDebugUnitTest

# 插件协议测试
npm --prefix vscode-extension test

# 先安装新版 APK，在手机开启必要权限，再执行真机测试
node scripts/verify-floating-controls.cjs
node scripts/verify-overlay-autostart.cjs
node scripts/verify-shell.cjs

# 涉及调试器初始化、断点或 Inspector 协议时执行
node scripts/verify-vscode-debug.mjs
~~~

悬浮窗回归覆盖：抽屉信息和占位入口、小圆球尺寸、六个按钮的纵向排列、拖动与点击的区分、底部向上展开、窗口外部收起、日志实时更新/拖动/跟随/清空/关闭重开、真实暂停与继续、停止死循环、手机独立运行、面板、结束时移除全部悬浮窗及重新开启。测试最后恢复一个可正常完成的缓存示例。LogBufferTest 验证条数与容量限制、超长 Unicode 截断和清空后的继续写入。

默认开启回归会暂时调整测试手机上 XpAuto 的悬浮窗和无障碍权限，验证只有悬浮窗权限时也可显示、缺少必要权限时拒绝脚本、无悬浮窗权限时不启动、授权返回自动显示、结束后不会立即重启。测试在 finally 中恢复原权限配置。

查询悬浮窗节点时使用 UIAutomator 的全部窗口模式。只查询焦点窗口会漏掉不抢焦点的悬浮球。系统 UI 查询也可能短暂挂起其他无障碍服务，回归脚本需要在触发新的运行权限检查前等待服务恢复。

包名迁移时的 Debug APK 曾显示原生库 16 KB 对齐兼容性提示。后续已修正自研动态库的 LOAD/RELRO 对齐并更新 APK/AAB 打包工具，验证方法见 [16 KB 原生库兼容性](native-16kb.md)。不要通过关闭系统警告代替兼容性检查。

排查问题时先读取运行状态和手机控制台，再查看系统日志：

~~~sh
adb logcat -s XpAutoDevTools AndroidRuntime
adb shell dumpsys window windows
~~~

重点检查：

1. 悬浮球不显示：用户是否授权悬浮窗、前台服务是否成功启动、系统是否限制后台显示。
2. 运行按钮置灰：是否同步过脚本，VS Code 是否仍占用会话。
3. 暂停迟迟未生效：是否在同步 Shell / Java 调用中，中断桥接是否初始化成功，是否错误地绕过中断入口直接发送普通 Inspector 请求。
4. 菜单截断或漂移：窗口与子项是否使用相同的像素计算，是否扣除了系统栏和屏幕缺口。
5. 版本或地址不正确：是否仍在使用旧 APK，是否把 VPN 地址优先于真实局域网地址。

## VS Code 编写辅助与节点抓取

插件 0.2.0 增加类型推断驱动的代码提示、三栏节点分析面板，以及属性复制和选择器代码生成。手机使用现有无障碍服务读取节点，独立控制请求不会重建 V8 会话；截图由 ADB 二进制读取。接口、文件职责、边界与完整验证方法见 [代码提示与 UI 节点分析](ui-inspector.md)。

## 脚本悬浮窗 API

脚本通过独立的 `floaty` 对象管理窗口，接口、线程模型、生命周期、XML 支持范围和测试方法见 [悬浮窗 API](floaty-api.md)。新增代码保持中文注释；视图读写统一在主线程，停止或断开会话时及时释放窗口。不要直接从 V8 执行线程调用原生 View 修改方法，也不要让主线程回调同步等待 V8 锁。

## 网络 API 与节点工作台 0.3.0

网络接口、会话生命周期、同步回调约束和测试说明见 [网络 API](http-api.md)。接口实现使用中文注释，HTTP、下载和 WebSocket 按职责拆成三个 Java 类；JS 层只做参数适配和回调派发。

本次继续保留小圆球和六个纵向按钮，日志 icon 可呼出/隐藏独立日志悬浮窗；窗口打开时按钮显示浅绿色状态。日志内容与首页控制台同步，支持拖动、跟随、清空、关闭；关闭日志窗口不会停止任务。节点分析工作台新增历史搜索、单 Tab 模式、属性表头和完整像素颜色显示，细节见 [节点分析](ui-inspector.md)。

### 0.3.0 验证记录（2026-10-16）

Pixel 7a / Android 16 已验证：30 项 Android 单元测试、9 项插件测试全部通过；网络真机测试覆盖上传、续传、两种 WebSocket、心跳、重连和停止取消。悬浮窗 API、日志窗口全部交互、首页抽屉和六个控制按钮通过回归。真实 VS Code 扩展宿主验证了新增网络补全、暂停时抓取节点和更新浮窗、断点、F10/F11、跳出、控制台、异常暂停和反复断连；调试期间 Android PID 与安装时间保持不变。

已生成 `vscode-extension/xpauto-0.3.0.vsix`。插件更新后，已有 VS Code 窗口可执行 `Developer: Reload Window` 载入新版本。

## 日志与 Toast 0.3.1

脚本的首选输出改为 `logd/logi/logw/loge`，格式化、过滤、按行清除、Toast 参数与正常完成/停止的生命周期说明见 [日志与 Toast API](logging-toast-api.md)。示例和节点生成代码已同步；旧 `print` 与原生 `console.*` 保持兼容。

### 0.3.1 验证记录（2026-10-16）

34 项 Android 单元测试、12 项插件测试全部通过。Pixel 7a / Android 16 验证了四级日志、占位符、对象格式化、等级过滤、手机首页与日志悬浮窗同步、调试和设备通道无重复输出、按行清除；Toast 覆盖系统短/长提示、自定义坐标与尺寸、颜色、拖动、替换、定时消失、点击穿透、权限回退、正常完成与停止/异常/断连清理。测试修正了自定义提示因状态栏导致的坐标偏移，临时调整的悬浮窗权限已恢复。

真实 VS Code 扩展宿主验证了新增日志和 Toast 补全、错误日志的 stderr 分类、断点、单步、控制台、异常暂停、忙循环暂停/恢复/停止、暂停时节点抓取与浮窗操作，以及多次断连恢复。调试期间 Android PID 与安装时间保持不变。

已生成并安装 `vscode-extension/xpauto-0.3.1.vsix`，手机原生接口已更新。已有 VS Code 窗口执行 `Developer: Reload Window` 后使用新补全；后续 F5 继续仅连接手机并同步脚本。

## 节点选择器 0.4.0

选择器条件与节点生命周期已重写：全局入口互相独立，链式条件可以重复查询，精确匹配不再隐式包含；查询支持状态、层级、列表元数据、矩形、级联、局部范围和 XPath。返回节点使用独立副本，读取与点击不会回收自身。完整接口及三个兼容调整见 [选择器与节点 API](selector-node-api.md)。

### 0.4.0 验证记录（2026-10-16）

42 项 Android 单元测试、12 项插件测试通过。Pixel 7a / Android 16 真机测试覆盖精确/包含/前后缀/正则、独立复用、空文本和非法参数、布尔属性、层级与下标、行列元数据、矩形范围、级联与局部查询、父子兄弟节点、数组和 JSON、两种分析器 XPath、点击/手势/长按、输入/清除、正反滚动、跨窗口节点、失效刷新、动态等待、脚本与控制台中的等待取消、下一轮恢复。测试使用仅进入 debug 包的专用页面，结束后返回首页。

Chrome 中节点分析器的截图命中、属性和树联动、XPath、右键菜单、代码预览、工具栏及窄窗口布局回归通过。真实 VS Code 扩展宿主验证新增选择器和节点数组提示、断点暂停时运行复制的绝对/相对 XPath、断点/单步/控制台、异常暂停、忙循环暂停恢复停止及多次断连。整个调试回归期间手机 PID 与安装时间不变。

已生成并安装 `vscode-extension/xpauto-0.4.0.vsix`，手机原生实现已更新。已有 VS Code 窗口执行 `Developer: Reload Window` 载入新提示；后续 F5 不编译、不安装。

## 选择器日志 0.4.1

修复 `print(selector)` 输出 Java 对象地址的问题。`UiSelector.toString()` 按条件段输出可读链，`SelectorCondition` 使用 Gson 转义字符串参数，`NodeXPath` 保留原表达式；这些操作只读取筛选条件，不查询无障碍节点。`toJSON()` 及接收属性名的重载支持 Javet 的 `JSON.stringify`，使对象与数组中的选择器也保留信息。`logging.js` 对序列化为字符串的顶层对象显示原文字，嵌套值保留 JSON 结构。接口说明与正确判断节点存在的示例见 [打印选择器](selector-node-api.md#打印选择器)。

### 0.4.1 验证记录（2026-10-16）

47 项 Android 单元测试、13 项插件测试通过。新增测试覆盖空选择器、TaskBase 继承、所有条件运算、别名、矩形、级联边界、XPath 更新、条件复用，以及引号、控制字符、正则反斜杠的转义。`scripts/verify-selector-output.cjs` 在 Pixel 7a / Android 16 验证真实 Javet 的 String、拼接、模板字符串、toString、toJSON、嵌套 JSON、print 与四级日志，确认调试和设备通道内容一致。

真实 VS Code 扩展宿主验证 `print(text("搜索"))` 和链式正则选择器的日志内容，并通过断点、单步、控制台、异常、暂停恢复停止及反复断连回归；期间手机 PID 和安装时间不变。已更新一次 APK，并生成、安装 `vscode-extension/xpauto-0.4.1.vsix`。已有 VS Code 窗口可执行 `Developer: Reload Window` 载入新增提示，后续 F5 仍只同步脚本。

## 当前应用信息 0.4.2

新增全局 `getCurrentRunningPkg()` 与 `getRunningActivity()`，分别返回当前应用窗口包名和经过校验的 Activity 完整类名；空结果分别为 `""` 与 `null`，无需 Root。实现按窗口 ID 和包名隔离 Activity 缓存，忽略控件类名，服务断开时清理引用和缓存。窗口选择排除输入法与系统浮层。完整规则、兼容限制和脚本示例见 [当前应用与 Activity](running-app-api.md)。

修正旧代码使用 `TYPE_WINDOWS_CHANGED` 读取 Activity 的错误，改为监听 `TYPE_WINDOW_STATE_CHANGED`。真机发现原先 100ms 的通知延迟可能把 Activity 事件合并成随后的控件事件，导致页面切换或服务重连后只剩 FrameLayout 类名；无障碍配置改为立即通知，事件处理不遍历节点树。类名校验仅使用桌面入口、HOME 与系统自动可见包，不申请 QUERY_ALL_PACKAGES。

### 0.4.2 验证记录（2026-10-16）

52 项 Android 单元测试、13 项插件测试通过。Pixel 7a / Android 16 连续两轮验证同包 Activity 切换、伪装为窗口变化的自定义控件事件、Dialog、键盘、系统设置、桌面、脚本悬浮窗，以及关闭和恢复无障碍服务；Activity 与系统实际前台组件核对一致。验证脚本通过 finally 恢复配置并返回首页，期间应用 PID 与安装时间不变。

真实 VS Code 扩展宿主通过新增接口补全、断点、单步、调试控制台、异常暂停、忙循环暂停/恢复/停止、暂停时节点抓取与浮窗操作、多次断连回归；手机 PID 与安装时间保持不变。

已更新手机原生实现并安装 `vscode-extension/xpauto-0.4.2.vsix`。已有 VS Code 窗口执行 `Developer: Reload Window` 后可使用新增函数提示；日常 F5 不编译、不安装。

## 线程、工具、SQLite 与全局快捷操作（0.5.0）

接口文档：[线程](thread-api.md)、[工具](utils-api.md)、[SQLite](sqlite-api.md)、[全局快捷操作](global-shortcut-api.md)。完整示例见 `examples/script-modules/main.js`，类型提示源为 `vscode-extension/types/xpauto.d.ts`。

调试服务与旧本地入口共用 `ScriptModules`。增加会话资源时应同时接入 install/reset/cancel/finish，先停止子线程，再清理数据库、媒体和节点快照，最后关闭 V8。禁止在持有 Java 资源锁时等待另一个可能需要 V8 的线程。

新增 JNI 的 Locker/Unlocker 头文件位于 `app/src/main/cpp/v8-public/`，来自 V8 15.2.124.17，许可证一并保留。未读取 Javet 私有对象内存。Javet 或 V8 升级须重新核对公开 ABI 和四架构编译。

验证命令：`./gradlew :app:testDebugUnitTest :app:assembleDebug`、`node --test vscode-extension/test/*.test.js`、`node scripts/verify-script-modules.cjs`、`node scripts/verify-vscode-debug.mjs`。真机脚本通过 `XPAUTO_ADB` 指定 adb。新原生 API 需更新一次 APK，此后 F5 仍直接连接应用。

本次验证还包括 `verify-shortcuts.cjs`（实际触摸事件、输入法、节点快照、停止与重跑）、`verify-utility-files.cjs`（ZIP 与媒体）、`verify-module-permissions.cjs`（权限与 app-op）。权限测试会恢复原设置。当前测试机的 ADB 可见 su，但应用自身不可见：已验证 Root 缺失时返回 false，以及 ADB 预授权后的成功/复用路径；不能将它视为该应用 Root 首次授权成功的实测结果。

`verify-thread-debug.cjs` 已验证子线程断点、变量查看、继续、断点中停止、断点中断开再连接，应用进程保持不变。本轮 Android 单元测试 54 项、插件测试 15 项通过。

## 截图接口（插件 0.6.0）

截图授权、全屏/区域截图、Bitmap 和图片读写接入统一的 `ScriptModules`，详见 [截图 API](image-api.md)。`CaptureService` 单独声明 `mediaProjection` 前台服务类型，系统授权令牌只消费一次；普通横竖屏保持固定方形 Surface，截图时按实际尺寸裁掉系统居中的留白，脚本结束时释放服务、帧缓冲和会话图片。代码采用中文注释，JS 参数适配与 Android 投影生命周期分开。

`type=0/1` 使用系统授权；代理模式尚未实现，`type=2` 返回 false。`captureScreenshot/captureScreenSurface` 按文档的非代理行为回退到全屏截图。Root 命令截图复用 Shell 的自定义 su 和取消机制。图片相对路径位于应用私有 `files/images`，不会读取电脑项目目录。

### 系统属性与 0.6.0 验证记录（2026-10-16）

新增全局 `getprop`、`setprop`，详见 [系统属性 API](system-properties-api.md)。`properties.js` 负责类型校验，`ShellApi` 负责参数引用和命令执行；读写分别默认普通权限和 Root，函数用本次执行结果计算返回值。继承自定义 su、超时和取消机制，不重复实现进程管理。

62 项 Android 单元测试、19 项插件测试通过。截图真机测试在 Pixel 7a / Android 16 验证授权拒绝/超时、全屏尺寸与四色像素、区域裁剪、PNG/JPEG/WebP、Base64、图片回收与数量上限、横竖屏往返、安全窗口不泄露、释放后独立图片有效、重新授权，以及正常结束和运行中停止清理；测试前后应用 PID 不变。

系统属性真机测试核对型号、系统版本和 SDK 与 ADB 一致，并验证默认值、非法参数和 Root 失败结果。该设备的应用进程找不到 su，因此 Root 截图和属性写入成功路径未获真机验证；受控 JVM 测试覆盖自定义 su、成功写入、特殊字符、清空、拒绝、超时和取消。

后续排查确认：SukiSU 中已授权的是旧包 `com.xiaopang.xianyu`（UID 10314），新包 `com.vixmobi.auto`（UID 10319）的超级用户开关关闭，应用进程中 `/system/bin/su` 不可见。`persist.acp.instance_id` 属性名合法，错误发生在写入前。新包的 Root 授权尚待用户确认；排查步骤已补入系统属性文档。

真实 VS Code 扩展宿主通过截图/系统属性补全、断点内 getprop、节点抓取、单步、控制台、异常、暂停/继续/停止和重复断连回归；整个调试过程的应用 PID 与安装时间保持不变。已更新一次手机 APK，并打包安装 `vscode-extension/xpauto-0.6.0.vsix`；已有 VS Code 窗口执行 `Developer: Reload Window` 载入新插件，日常 F5 不构建或安装 APK。

## 官方浏览器 vixBrowser（插件 0.7.0）

全局 `browser` 封装浏览器工程 `android-webview-browser/HTTP_API.md` 的全部 HTTP 接口，并提供 `start`、`open`、`waitForPage` 和 `waitFor`。接口、错误语义和示例见 [浏览器 API](browser-api.md)。实现位于 `assets/browser.js`，经 `ScriptModules.ASSETS` 同时加载到开发服务和手机本地入口；启动复用 `utils.openApp`，通信复用 `http.request`，无需 Root。

配置实例互相独立，默认地址为手机本机 `127.0.0.1:8765`。导航返回的 202 只表示接受操作，等待函数检查页面错误并要求连续两次同 URL 空闲；每次 HTTP 和 sleep 都受剩余时间限制。POST 请求体按 UTF-8 校验 64 KiB 上限，错误保留浏览器原始码和 HTTP 状态。为避免重放有副作用的页面 JS，HTTP 增加可选 `retryOnConnectionFailure`，浏览器请求显式设为 false，原有 HTTP 调用默认行为保持兼容。

2026-10-16：63 项 Android 单元测试、25 项插件测试通过，TypeScript 类型检查通过。Pixel 7a / Android 16 的真实 vixBrowser 联调覆盖启动、配置原子更新、UA/Referer 请求头、文档开始 history 覆盖、重定向、页面等待、JS 标量/对象/数组/null、异常/Promise/BigInt/循环引用错误、SPA 条件、缓存保留 Cookie/DOM Storage、前进后退、刷新、配置清除、页面错误、超时、HTTP 等待中停止和下一轮恢复。测试后恢复浏览器原配置及可恢复的原页面，移除测试转发；XpAuto PID 与安装时间在联调期间保持不变。

真实 VS Code 扩展宿主验证了 `browser` 方法与状态字段补全、断点控制台中的模块加载，并通过节点抓取、单步、变量查看、异常暂停、忙循环暂停/恢复/停止及重复断连回归。调试期间 Android PID 与安装时间保持不变。已更新手机 APK，并生成、安装 `vscode-extension/xpauto-0.7.0.vsix`；已有 VS Code 窗口执行 `Developer: Reload Window` 载入新提示，日常 F5 不重新构建或安装应用。

### 浏览器前台启动修正（0.7.1）

原 `browser.start()` 在发出启动 Intent 后，只要后台 HTTP 服务可用就返回，早于界面实际切到前台。现在复用 `getCurrentRunningPkg()` 检查前台窗口，连续两轮确认浏览器在前台且 HTTP 就绪后返回；HTTP 等待期间也重新检查前台。HOME 切换覆盖首次启动时，每秒最多重发一次无 URL 的启动请求，不刷新页面。前台等待受同一总超时和停止机制约束，无法切到前台时抛 `foreground_timeout`，不会把后台服务在线当作启动成功。前台查询使用运行脚本已要求的无障碍服务。

2026-10-16：26 项插件测试及 TypeScript 检查通过，APK 构建成功。Pixel 7a / Android 16 验证从桌面唤起后台浏览器、HOME 后立即启动、进程未运行时冷启动、重复调用保留页面；同一次脚本求值检查返回时的包名，再通过系统 `topResumedActivity` 核对实际前台。完整浏览器接口回归通过，测试后恢复原配置与可恢复的原页面。已覆盖更新手机 APK，并打包、安装插件 0.7.1。

### vixBrowser 1.4 加载接口（插件 0.8.0）

对照浏览器工程最新 `HTTP_API.md` 和 `MainActivity`，增加 `browser.getLoading()` 对应 `GET /api/loading`。该端点字段位于响应根对象，客户端保留 `ok`、`status`、`loading`、`progress`、`url`、`title`、`page_error`，不按旧端点的 `state` 包装提取。查询成功时保留页面 `error` 状态供脚本显示；网络及接口错误仍沿用 `VixBrowserError`。

`open` 和 `waitForPage` 继续轮询完整状态并返回 `state`，1.4 要求 `load_status=complete` 且 `loading=false`，连续两次 URL 一致后完成；`error` 状态立即抛 `page_load_failed`。进度 100 不作为成功依据，`idle` 继续等待。旧浏览器缺少 `load_status` 时兼容原先的 `loading` 判断，不要求旧版提供新端点，也不推算其进度。

类型声明增加 `XpAutoBrowserLoading` 和四种状态联合类型，完整状态的 `load_status`、`progress` 标为可选以反映旧版兼容性。默认和自定义客户端共用实现。文档及示例见 [浏览器 API](browser-api.md) 和 `examples/browser/main.js`，新增代码注释和提示均为中文。

2026-10-16：28 项插件测试、TypeScript 类型检查及 APK 构建通过，新 APK 的 8 个原生库继续通过 16 KB ELF 与 ZIP 对齐检查。受控测试覆盖新版状态、进度 100 仍失败、旧版等待兼容、缺少端点的 404、503、非法响应和取消传递。Pixel 7a / Android 16 上使用真实 vixBrowser 1.4 验证四种加载状态、进度、重定向、导航等待、页面错误和停止恢复，并回归原有浏览器接口；测试结束恢复原配置与可恢复的原页面地址。已覆盖更新手机 APK 并安装插件 0.8.0。

真实 VS Code 扩展宿主验证新增方法和返回字段补全、断点内浏览器模块可用，并通过断点、单步、控制台、异常暂停、忙循环暂停/继续/停止和反复断连回归。调试期间 XpAuto PID 与安装时间保持不变。已有编辑器窗口执行 `Developer: Reload Window` 载入新插件；日常 F5 仍只连接与同步脚本。


### 项目同步、文件与节点兼容（插件 0.9.0）

对照已有 EasyClick 工程补充五项能力，接口与迁移示例见 [项目与兼容 API](project-api.md)。代码以中文注释解释状态、线程切换和资源清理。

- `ExactFiles` 统一 UTF-8 整文件读取、临时文件替换、字节写入；`FileUtils` 把错误与空内容区分，并修正 deleteLine 参数顺序和换行损失。URI 覆盖使用 wt。
- `DeviceApi` 读取系统屏幕、机型、电源与网络信息，通过包管理器枚举应用。应用列表在 `ScriptBlocking` 段执行，逐项检查取消；不使用 Root。
- `BuiltinFloatApi` 经 `UiDispatcher` 在主线程访问 `DeveloperService` 的已有窗口。日志继续使用服务的同一份缓存，隐藏不清空；配置为部分更新。
- 插件 `src/project.js` 从入口目录收集源码，显式包含资源；拒绝符号链接与路径越界，限制文件数和体积。`XpAuto.run.params.project` 携带 entry、sourceRoot、nodeMode 和 base64 文件表。控制握手增加 projectModules 能力；旧 APK 明确提示升级。
- `ScriptProject` 在脚本执行器内验证并安装独立快照，保存入口缓存成功后才清理旧快照。同步源码必须和入口文件一致。手机重跑使用缓存的完整快照；旧协议单文件运行保留原先全局作用域。
- `ModuleFiles` 负责路径定位与读取，`modules.js` 负责每轮 CommonJS 缓存和循环依赖。定位与读取分离，缓存命中不重读源码。SDK 在项目代码前注册；模块工厂使用各自的 sourceURL，保留源码行号。URL 必须与 VS Code 的 file:/// 和 UTF-8 百分号编码一致，不能由 URI.resolve 变成 file:/。
- `node-compat.js` 用 JS Proxy 转换属性并透传动作，WeakMap 保存原生对象映射。全局 click 和快捷操作在跨 Java 桥接前解包，避免把 JS 代理当作原生节点。方法模式默认保留；asEasyClick 提供明确的静态类型提示。

本地验证：`npm --prefix vscode-extension test`、TypeScript 声明检查、`:app:testDebugUnitTest :app:assembleDebug`。真机验证脚本 `scripts/verify-project.cjs` 覆盖原始字节、混合换行、短覆盖、空与失败区分、设备应用、内置窗口、节点属性/方法切换、CommonJS/JSON/循环依赖、依赖断点、更新缓存和资源移除。`scripts/verify-vscode-debug.mjs` 回归完整编辑器调试流程，并比较调试前后 Android PID 与安装时间。

2026-10-19 验证：70 项 JVM 单元测试、32 项插件测试、类型检查和 APK 构建通过，8 个原生库保持 16 KB ELF 与 ZIP 对齐。Pixel 7a / Android 16 通过五项 API 与依赖断点验证，真实 VS Code 通过补全、节点抓取、断点与单步、异常暂停、忙循环控制和多次断连回归，期间 Android PID 与安装时间未改变。已生成并安装插件 0.9.0；现有编辑器窗口使用 `Developer: Reload Window` 加载更新。
