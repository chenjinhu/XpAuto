# Android 系统属性

全局 `getprop`、`setprop` 封装 Android 的同名命令，直接在脚本或调试控制台调用，无需 import。VS Code 插件提供补全、中文说明和参数提示。

```javascript
logi(getprop("ro.product.model"));
logi(getprop("ro.build.version.release", "未知"));

// 写入需要当前应用的 Root 授权，并受 Android 属性策略限制。
// 自定义 su 可先通过 shell.addSuBin("/自定义路径/su") 注册。
if (!setprop("debug.xpauto.example", "hello")) {
    loge(shell.getLastResult().error);
}
```

## 接口

| 函数 | 返回值与默认参数 |
| --- | --- |
| `getprop(name, defaultValue = "", root = false, timeoutMs = 60000)` | 返回字符串；属性不存在、为空或命令失败时返回默认值 |
| `setprop(name, value, root = true, timeoutMs = 60000)` | 命令退出码为 0 时返回 true，否则 false |

`name` 为非空字符串，可包含字母、数字、下划线、连字符及点；点不能连续或位于首尾。`value` 接受字符串、有限数字或布尔值，数字和布尔值转为字符串；传入 `""` 清空值，不代表删除属性。NUL 字符不允许。`defaultValue` 必须是字符串，`root` 必须是布尔值，超时是 `1..3600000` 的整数毫秒；非法参数抛出异常，不启动命令。

读取只去掉命令额外输出的最后一个换行，保留属性值本身的空格和换行。默认读取明确使用普通应用权限，不受之前 `shell.su()` 是否成功影响。读取受限属性时可显式传入 `root = true`。

写入默认使用 Root，复用 `shell.addSuBin()` 注册路径和当前应用的授权；首次使用可能触发 Root 管理器授权。可显式传入 `false` 使用普通权限。属性的可见性、可写性和长度仍由 Android 决定：Root 不保证能写入已有 `ro.*` 属性，也不会绕过 SELinux 策略。详见 [Android 系统属性文档](https://source.android.com/docs/core/architecture/configuration/add-system-properties)。

## 错误、停止与开发实现

### su 报 No such file or directory

如果错误列出 `su`、`/system/bin/su` 等路径均为 `error=2`，失败发生在 Root 启动阶段，`setprop` 还没有执行。`persist.acp.instance_id` 是合法属性名，不能通过修改属性名解决这类错误。

先在 Root 管理器中按**包名**找到 `com.vixmobi.auto` 并检查超级用户授权。改包名后的应用有独立 UID，旧包 `com.xiaopang.xianyu` 的授权不适用于新包；两个应用可能都显示为 XpAuto。KernelSU/SukiSU 的应用配置还会影响模块挂载可见性，ADB 能找到 su 不代表应用也能找到。参见 [KernelSU 应用配置](https://github.com/tiann/KernelSU/blob/main/website/docs/guide/app-profile.md)。

授权后完全退出并重新打开 XpAuto，再验证：

```javascript
if (!shell.su()) {
    loge(shell.getLastResult().error);
} else {
    logi("Root UID：", shell.ROOT("id -u").trim()); // 应为 0
    // 将下面的字符串替换为实际实例 ID，再取消注释运行。
    // const ok = setprop("persist.acp.instance_id", "实际实例ID");
    // logi("写入结果：", ok);
    // logi("Root 读回：", getprop("persist.acp.instance_id", "", true));
}
```

只有确定应用可见的 su 位于其他路径时，才使用 `shell.addSuBin(实际路径)`；重复注册默认路径无法代替授权。若 Root 已成功但写入仍失败，再查看 `shell.getLastResult()` 中的属性服务或 SELinux 错误。

### 返回结果和生命周期

失败详情读取 `shell.getLastResult()`，包含退出码、标准输出、错误、超时及取消状态。空属性返回默认值且退出码通常为 0；启动或执行失败返回默认值且退出码非 0。Root 失败不回退到普通权限，不自动重复写入。停止脚本或断开调试会中断命令，并抛出取消异常，避免后续语句继续执行。多线程共享 Shell 的最近结果，应紧接调用读取；函数自身的返回值使用本次命令结果判断。

`properties.js` 校验脚本参数，`ShellApi` 构造单参数引用并调用现有的进程监督、超时、取消和 Root 实现。读取、写入不查询 UI 节点，也不依赖无障碍服务。调试与本地运行通过 `ScriptModules` 加载同一份实现。

原生 API 需安装一次新版 APK；此后 F5 仍只连接手机并同步脚本。安全的只读示例见 [examples/system-properties/main.js](../examples/system-properties/main.js)。JVM 测试用受控的命令和 su 模拟程序验证引号、空值、错误、超时及取消，不申请电脑 Root。真机验证脚本为 `scripts/verify-system-properties.cjs`。
