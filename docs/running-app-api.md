# 当前应用与 Activity

接口名称参考 [EasyClick 全局快捷事件](https://ieasyclick.net/docs/funcs/global/global-shortcut)。这两个全局函数需要开启 XpAuto 无障碍服务，不需要 Root 或使用情况访问权限。

```javascript
logi("当前包名：{}", getCurrentRunningPkg());
logi("当前 Activity：{}", getRunningActivity());

if (getCurrentRunningPkg() === "com.example.app") {
    const activity = getRunningActivity();
    if (activity) logd("页面：{}", activity);
}
```

| 接口 | 返回值 |
| --- | --- |
| `getCurrentRunningPkg()` | 当前应用窗口根节点的包名字符串；服务未连接或没有可读窗口时为 `""` |
| `getRunningActivity()` | 当前窗口 Activity 的完整类名，例如 `com.vixmobi.auto.devtools.DeveloperActivity`；无法确定时为 `null` |

## 获取规则

优先使用有输入焦点的应用窗口，其次活动应用窗口，最后最上层应用窗口。输入法、系统浮层和悬浮控制窗口不作为目标应用；分屏时返回按该规则选出的一个应用。系统未提供窗口列表时，回退读取活动根节点。

Activity 来自 `TYPE_WINDOW_STATE_CHANGED` 事件，按窗口 ID 和包名记录，并通过 PackageManager 确认类名是已注册的 Activity。控件、Fragment 和 Dialog 的事件不会覆盖有效的 Activity。窗口移除、无障碍断开时清除对应记录，避免返回上一应用的数据。

事件异步到达：刚开启服务或页面切换中，包名可能已经可读而 Activity 仍为 `null`；等待界面稳定后重试即可。独立弹窗没有可验证的 Activity 记录时也返回 `null`，不会把 `android.app.AlertDialog` 或根布局类名当作 Activity。两次函数调用分别取值，跨调用恰好发生页面切换时，结果可能属于不同页面。

Android 11+ 使用有限的包可见性声明，校验有桌面入口的应用、桌面程序和系统自动可见应用；对不可见包或未暴露 Activity 事件的页面，Activity 可能为 `null`。接口不申请全部应用查询权限，不调用 dumpsys、不申请 Root。

## 等待特定页面

```javascript
const deadline = Date.now() + 5000;
while (getRunningActivity() !== "com.example.app.SearchActivity" && Date.now() < deadline) {
    sleep(100);
}
if (getRunningActivity() === "com.example.app.SearchActivity") {
    logi("搜索页已打开");
} else {
    logw("尚未识别到搜索页");
}
```

## 开发与验证

- `RunningAppInfo` 选择应用窗口、读取包名、处理无障碍事件和校验 Activity；读取后立即回收根节点及窗口句柄。
- `RunningActivityCache` 使用同步方法按窗口和包名隔离，最多保存 64 条记录，避免服务长时间运行时无限增长。
- `AccUtils` 在服务连接后公开引用，断开时清理；旧 `currentActivityName()` 转用同一实现。
- 无障碍 `notificationTimeout` 设为 0，防止同类事件在延迟合并时丢失 Activity 类名；事件回调只处理窗口状态与窗口移除，不遍历节点树。
- `base.js` 和 `TaskBase` 提供脚本入口，插件类型声明提供中文补全、返回值和空值说明。
- Android 事件语义见 [AccessibilityEvent](https://developer.android.com/reference/android/view/accessibility/AccessibilityEvent)，包可见性见 [声明包可见范围](https://developer.android.com/training/package-visibility/declaring)。

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
node --test vscode-extension/test/*.test.js
XPAUTO_ADB=/path/to/adb node scripts/verify-running-app.cjs
```

新增原生接口需要更新一次 APK；之后 F5 继续只连接应用并同步脚本。示例见 [examples/running-app/main.js](../examples/running-app/main.js)。
