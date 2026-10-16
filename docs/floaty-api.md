# 悬浮窗 API

脚本通过全局对象 `floaty` 管理自己的悬浮窗。方法命名和基本参数参考 [EasyClick 悬浮窗函数](https://ieasyclick.com/docs/funcs/floaty-api)。本页说明 XpAuto 的实际行为和支持范围。

此功能需要更新一次 Android APK。以后 F5 仍然只同步 JS，不重新构建或安装。插件 0.2.1 已加入 `floaty`、返回视图和子控件的中文补全、悬停与参数提示。完整示例见 [examples/floaty/main.js](../examples/floaty/main.js)。

## 快速使用

```javascript
if (floaty.requestFloatViewPermission(10000)) {
    const view = floaty.showFloatXml("progress", `
        <LinearLayout layout_width="240dp" layout_height="wrap_content"
            orientation="vertical" padding="16dp" background="#EE20293B" cornerRadius="14dp">
            <TextView text="任务进度" textColor="#FFFFFF" textSize="18sp"/>
            <TextView tag="status" text="准备开始" textColor="#BBDDFF"/>
        </LinearLayout>`, 60, 400);
    if (view) {
        view.findViewWithTag("status").setText("已完成 1 / 3");
        sleep(3000);
        floaty.close("progress");
    }
}
```

窗口默认可触摸、不抢输入焦点。带输入框的窗口先调用 `floaty.focusable(tag, true)`，再点击输入框。窗口不会自动拖动，脚本用 `updateX`、`updateY` 调整位置。

脚本正常返回、抛出异常、点击停止、断开调试连接或关闭运行环境时，都会清理本次脚本的窗口。需要持续显示时让脚本继续运行；暂停在断点时窗口保留。在调试控制台直接调用创建的预览窗口，会保留到主动关闭、下一次运行、停止或断开连接。

脚本窗口拥有独立的标签表，不会关闭或替换应用悬浮球、菜单或日志窗口。每个会话最多 16 个窗口；同标签的新窗口创建成功后替换旧窗口，创建失败时保留旧窗口。

## 方法

`tag` 接受非空字符串或数字，建议使用简短字符串。坐标和 API 尺寸均为**屏幕像素**；XML 的无单位尺寸按 dp、无单位字号按 sp 解释。系统可能为可用屏幕区域调整最终显示位置。

| 方法 | 返回值与说明 |
| --- | --- |
| `requestFloatViewPermission(timeoutMs = 10000)` | 已授权立即返回 `true`；否则打开系统设置并等待，超时返回 `false`。等待时间范围 0–120000 毫秒；0 仍会打开授权页，但不等待。用户点击停止可中断等待。 |
| `showFloatXml(tag, xml, x, y)` | `xml` 为 XML 字符串或手机文件路径；成功返回 `XpAutoFloatView` 句柄，失败为 `null`。 |
| `showFloatView(tag, view, x, y)` | 接受本会话返回的视图句柄，或 Java 集成方提供的原生 Android View；返回句柄或 `null`。 |
| `updateX(tag, x)` | 更新左侧偏移，成功为 `true`。 |
| `updateY(tag, y)` | 更新顶部偏移，成功为 `true`。 |
| `updateSize(tag, width, height)` | 宽高为 0–16000 的整数像素；`-1` 表示匹配屏幕，`-2` 表示按内容测量。成功为 `true`。 |
| `close(tag)` | 移除指定窗口；不存在时返回 `false`。 |
| `touchable(tag, value)` | 控制窗口是否接收触摸，成功为 `true`；设为 `false` 时窗口整体透明度设为 0.7，恢复触摸时设为 1。 |
| `focusable(tag, value)` | 控制窗口能否获得输入焦点，成功为 `true`；关闭时请求隐藏该窗口的软键盘。 |
| `getX(tag)` / `getY(tag)` | 读取窗口配置的像素坐标，窗口不存在时为 `-1`。 |
| `getWidth(tag)` / `getHeight(tag)` | 读取窗口的测量尺寸；首帧前也会主动测量，窗口不存在时为 `-1`。 |

除取消执行外，`floaty` 方法失败返回 `false`、`null` 或 `-1`，详细原因写入手机日志和 VS Code 调试控制台。无效尺寸、XML、标签、缺少权限或窗口数量超限不会使整个应用崩溃。坐标支持 -100000 到 100000 的整数，`-1` 坐标与查询失败返回值有歧义，建议使用非负坐标。

Android 12+ 会按窗口遮挡和透明度决定是否允许触摸穿透到其他应用。多个窗口重叠时即使各自不可触摸，系统仍可能拦截触摸；`touchable(false)` 保证本窗口不接收触摸，不承诺所有系统版本下都能操作下层应用。

XpAuto 首页和脚本启动入口本身已有必要权限检查。此 API 提供脚本中的检查和授权等待，不绕过系统授权，也不需要 Root。

## 视图句柄

为避免在 V8 工作线程直接修改 Android View，XpAuto 返回的是 **`XpAutoFloatView` 线程安全句柄**，内部关联真实原生控件，而不是暴露任意 Android 方法的对象。支持以下同步操作，实际访问均切换到主线程：

| 类别 | 方法 |
| --- | --- |
| 查找 | `findViewWithTag(tag)`、`getChildCount()`、`getChildAt(index)`；找不到返回 `null` |
| 标签 | `getTag()`、`setTag(tag)` |
| 文本 | `getText()`、`setText(text)`、`setTextColor(color)`、`setTextSize(sp)` |
| 外观 | `setBackgroundColor(color)`、`setEnabled(value)`、`isEnabled()`、`setVisibility(0/4/8)`、`getVisibility()` |
| 输入 | `requestFocus()`、`clearFocus()` |
| 勾选 | `setChecked(value)`、`isChecked()`，用于 CheckBox 和 Switch |
| 测量 | `measure(widthSpec, heightSpec)`、`getMeasuredWidth()`、`getMeasuredHeight()`、`getWidth()`、`getHeight()` |

颜色接受 `#RRGGBB`、`#AARRGGBB` 字符串或 Android 整数颜色。`measure()` 接收 Android MeasureSpec；`measure(0, 0)` 表示不约束测量。句柄的 `getWidth/getHeight` 读取实际布局尺寸，首帧前可能为 0，查询窗口尺寸时优先用 `floaty.getWidth/getHeight`。

对不支持文本的控件调用 `setText` 等方法会抛出明确异常。当前没有点击事件回调、任意原生反射、`importPackage` 或完整 EasyClick UI DSL；示例使用 XML 创建控件，通过句柄读取输入和更新状态。

复用窗口 View 的例子：

```javascript
const view = floaty.showFloatXml("first", '<TextView text="可复用" padding="12dp"/>', 20, 200);
if (view) {
    floaty.close("first");
    floaty.showFloatView("second", view, 100, 400);
    sleep(2000);
    floaty.close("second");
}
```

同一个 View 不能同时挂在两个窗口中；子控件仍属于原布局时也不能另挂为窗口。不同脚本会话的句柄不能混用。

## XML 支持范围

支持 `LinearLayout`、`FrameLayout`、`RelativeLayout`、`ScrollView`、`HorizontalScrollView`、`TextView`、`Button`、`EditText`、`CheckBox`、`Switch`、`ProgressBar`、`View`、`Space`，也支持它们的 `android.widget.*` / `android.view.*` 完整类名。

属性可以写成 `android:text="内容"` 或 `text="内容"`；可声明标准 android 命名空间。主要属性：

- 布局：`layout_width/height`、`layout_weight`、`layout_gravity`、`layout_margin` 和四边 margin；尺寸支持 `dp`、`dip`、`sp`、`px`、`match_parent`、`fill_parent`、`wrap_content`。
- 容器：`orientation`、`gravity`；RelativeLayout 支持 `layout_centerInParent`、`layout_centerHorizontal/Vertical`、`layout_alignParentTop/Bottom/Left/Right`。
- 公共：`tag`、`id`、`padding` 及四边 padding、`background/backgroundColor`、`cornerRadius`、`enabled`、`clickable`、`focusable`、`visibility`、`alpha`、`contentDescription`、`minWidth/Height`。
- 文本：`text`、`textColor`、`textSize`、`textStyle`、`hint`、`singleLine`、`maxLines`。
- 输入与勾选：`checked`；`inputType` 支持 `text`、`number`、`numberDecimal`、`phone`、`textPassword`、`textMultiLine`。

`id="@+id/status"` 自动生成原生数字 ID，并默认使用 `status` 作为查找标签；显式 `tag` 会覆盖该默认标签。ScrollView 只能有一个直接子控件。XML 不支持 `@layout`、`@color` 等编译资源引用、未知控件/属性、DTD 和外部实体。文本请写在 `text` 属性中，XML 内容中的 `&`、`<`、引号按 XML 规则转义。

XML 上限 64 KiB、200 个控件、20 层嵌套。相对文件路径先查手机应用私有 `files` 目录，再查 APK 的 assets；绝对路径按应用实际文件权限读取。插件当前仅同步 JS，电脑项目中的 XML 不会自动上传，优先使用内联 XML。

## 开发结构与验证

代码位于 `app/src/main/java/com/vixmobi/auto/floaty`：

- `FloatyApi`：13 个接口、权限等待、按标签管理窗口、参数校验和会话清理。
- `FloatyLayout`：先解析为布局描述，再在主线程创建控件；不引入第三方 XML 渲染框架。
- `FloatyView`：常用视图方法的主线程转发。
- `UiDispatcher`：可取消的同步主线程调用，5 秒超时，并移除尚未开始的任务。

`DeveloperServer.Session` 和旧版 `TaskBase` 均注入全局 `floaty`。窗口表只在主线程访问；停止请求先设置取消标志，再异步移除窗口，不等待 V8 锁。调试控制台正在等待权限时，停止入口也会终止该次 JS 求值，避免继续执行后续语句。

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest --offline
npm --prefix vscode-extension test
# 先安装新版 APK、打开运行环境并授权 USB 调试：
node scripts/verify-floaty.cjs
# 临时撤回并恢复本应用悬浮窗权限，验证超时和取消：
node scripts/verify-floaty-permissions.cjs
node scripts/verify-vscode-debug.mjs
```

单元测试覆盖 XML 解析、命名空间、中文与转义、未知输入、DTD/实体拒绝、字节/节点/深度上限及 floaty 代码推断。真机测试覆盖 13 个接口、子控件更新、真实触摸开关、焦点、XML 文件、同标签失败保留、View 复用、16 个窗口上限、结束/异常/停止/断开清理、权限超时与取消，以及后续脚本恢复。
