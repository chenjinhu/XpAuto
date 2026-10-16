# Shell API

直接读取和写入 Android 系统属性可使用全局 `getprop()`、`setprop()`，详见 [系统属性 API](system-properties-api.md)。

脚本中使用全局对象 `shell`，无需导入。接口命名和基础返回值参考 [EasyClick Shell API](https://ieasyclick.com/docs/funcs/shell-api)。EasyClick 的 Root 命令接口名是 `sudo`，XpAuto 同时提供 `ROOT` 别名。

## 权限与运行方式

普通命令以 **XpAuto 应用自身 UID** 执行，不具有 ADB shell 的权限。当前版本没有代理模式或 Shizuku 后端。静默安装、卸载、停止其他应用需要设备已配置 Root，并在 Root 管理器中允许 **XpAuto**。`su()` 只能申请已有 Root 环境的授权，不能给未 Root 的手机获取 Root。

所有方法同步返回。脚本运行时，VS Code 和手机显示运行状态；失败详情同时写入调试控制台和手机运行控制台。检查命令是否成功应读取 `getLastResult().code`，不能只看返回字符串是否为空。Root 权限按需申请，不是启动 XpAuto 的必需权限。

改包名后需在 Root 管理器中核对 `com.vixmobi.auto` 的授权，不能沿用同名旧应用 `com.xiaopang.xianyu` 的授权。默认 su 路径全部报 `No such file or directory` 时，按 [Root 启动失败排查](system-properties-api.md#su-报-no-such-file-or-directory) 检查当前应用的权限与路径可见性。

## 接口一览

| 接口 | 返回值 | 行为 |
| --- | --- | --- |
| `shell.addSuBin(path)` | boolean | 注册 su 可执行文件的绝对路径或 PATH 中的命令名 |
| `shell.su()` | boolean | 请求 Root 授权，并确认 `id -u` 返回 `0` |
| `shell.execCommand(command)` | string | 使用已验证的 Root；尚未授权时使用普通应用权限 |
| `shell.execCommand(command, root)` | string | `true` 强制 Root，`false` 强制普通权限 |
| `shell.sudo(command)` | string | 强制 Root，必要时先申请授权 |
| `shell.ROOT(command)` | string | `sudo` 的等价别名 |
| `shell.installApp(path)` | boolean | 为当前 Android 用户安装或覆盖升级一个 APK |
| `shell.uninstallApp(packageName)` | boolean | 为当前 Android 用户卸载指定包，不保留其用户数据 |
| `shell.stopApp(packageName)` | boolean | 强制停止当前 Android 用户下的指定包 |
| `shell.getLastResult()` | ShellResult | 上一次操作结果的快照 |

### addSuBin：自定义 su

```javascript
shell.addSuBin("/data/local/my-root/su");
// 也可以使用 PATH 中的自定义名称，例如 shell.addSuBin("222ddd")。
if (!shell.su()) {
    loge(shell.getLastResult().error);
}
```

- 路径按一个可执行文件参数传递，支持绝对路径中的空格和单引号；不要填写 `su -c ...` 或整段命令。
- 可执行文件应支持标准 `su -c <command>` 调用约定。仅改变名称或路径不能保证某个 Root 实现与 Android SELinux 策略兼容。
- 新注册项优先，重复注册会移到最前。默认依次尝试 `su`、`/system/bin/su`、`/system/xbin/su`、`/sbin/su`。
- 返回 `true` 只表示配置已接受，不表示文件存在、可执行或已授权。通过 `su()` 验证。
- 无法启动的路径会继续尝试下一项；已启动的 su 拒绝授权、超时或返回非零 UID 时停止本次申请，避免反复弹授权窗口。
- 配置和已验证路径保存在应用进程内，跨调试会话可用；应用重启后恢复默认。建议在脚本入口注册自定义路径。新增路径后需重新验证授权。

### su / sudo / ROOT / execCommand

```javascript
logi(shell.execCommand("id", false)); // 始终是 XpAuto 普通应用权限

if (shell.su()) {
    logi(shell.sudo("id"));
    logi(shell.ROOT("id -u"));        // 0
    logi(shell.execCommand("id -u")); // 已授权后自动使用 Root
}

const output = shell.execCommand("printf 'hello\\n'; echo diagnostic >&2; exit 7", false);
const result = shell.getLastResult();
logi(output, result.code, result.error); // hello\n、7、diagnostic\n
```

`execCommand`、`sudo`、`ROOT` 返回标准输出，保留换行；错误输出通过 `getLastResult().error` 获取。Root 授权失败时不执行用户命令，返回空字符串。实际命令非零退出时可能仍有部分标准输出。

`execCommand(command)` 不会为普通命令主动弹 Root 授权；先调用 `su()`，或使用强制 Root 的调用。每次 Root 命令执行前都检查实际 UID，防止授权失效后以错误身份执行。命令失败不会自动换 su 重试，避免安装、写文件等操作重复执行。

每次调用使用独立 Shell，不保留上次的 `cd`、变量或 `export`。需要连续状态时，在同一命令中使用 `cd /path && ...`。支持管道、重定向和多行命令。标准输入默认是 `/dev/null`，不支持交互式终端；可用命令内的重定向或管道提供输入。

### installApp / uninstallApp / stopApp

```javascript
function manageTestApp() {
    if (!shell.su()) return false;
    if (!shell.installApp("/sdcard/Download/example.apk")) {
        loge(shell.getLastResult().error);
        return false;
    }
    logi(shell.stopApp("com.example.testapp"));
    // 确认要删除该应用及其当前用户数据后再调用：
    // logi(shell.uninstallApp("com.example.testapp"));
    return true;
}
```

- 安装路径是**手机上的绝对路径**，不是电脑路径。文件必须存在且能被 Root 进程读取。安装使用 APK 字节流，避免 PackageManager 直接读取共享存储路径的限制；不需要给 XpAuto 申请全盘存储权限。
- 当前支持单个 APK，覆盖升级使用 `-r`，签名不一致、降级限制、损坏文件或系统策略拒绝均返回 `false`；不绕过 Android 的包校验。APKS/XAPK/split APK 集合不在此接口范围内。
- 安装和卸载只有命令成功退出且 PackageManager 报告 `Success` 才返回 `true`。
- 停止先检查包是否安装，未安装返回 `false`；已安装但未运行时，成功执行 `force-stop` 也返回 `true`。
- 操作目标是 Android 当前前台用户（`--user current`）。多用户设备上，卸载不等同于从所有用户删除。
- 包名按 Android 标识符校验，路径按 Shell 参数引用。停止或卸载 XpAuto 自身会终止当前运行环境，无法可靠返回结果。

## 超时、停止与结果

默认授权和普通命令超时 60 秒，安装超时 180 秒。强制 Root 命令的超时包含首次授权的等待时间。需要指定时长时使用以下重载，单位毫秒，范围 `1..3600000`：

```javascript
shell.su(30000);
shell.execCommand("sleep 10", false, 1000);
logi(shell.getLastResult().timedOut); // true
shell.sudo("id", 5000);
shell.ROOT("id", 5000);
```

| ShellResult 字段 | 含义 |
| --- | --- |
| `code` | 进程退出码；`0` 成功，`-1` 未启动或没有正常退出；Root UID 校验失败为 `126` |
| `result` | 标准输出，默认空字符串 |
| `error` | 标准错误、参数错误或超时/取消原因，默认空字符串 |
| `timedOut` | 达到超时限制 |
| `cancelled` | 被停止、断开连接或运行环境关闭取消 |
| `truncated` | 输出超过保存上限，或后台进程持有输出管道导致无法完整收集 |

输出使用 UTF-8；stdout、stderr 分别最多保存 1 MiB，超出仍持续排空管道，避免大输出把命令卡住。控制台错误摘要最多显示每段 4096 个字符，完整已保存内容由 `getLastResult()` 获取。非法超时抛出参数异常；其他已校验的参数错误返回 `false` 或空字符串，并记录结果。

VS Code 停止、手机悬浮窗停止和关闭连接都会取消当前 Shell 等待。取消会中断当前 JS 调用，避免继续执行后续语句；超时则正常返回，并设置结果中的超时标志。Android 使用独立进程组监督前台命令，超时或取消时终止该组；已提交给系统的安装等操作可能继续完成，另建会话脱离进程组的守护进程也不保证终止。停止不是事务回滚。

## 示例与验证

- 可直接 F5 运行 [`examples/shell/main.js`](../examples/shell/main.js)，示例只查询信息，不安装或卸载应用。
- 原生接口更新后需构建、安装一次新版 APK；日常修改 JS 后 F5 仍只连接并同步脚本。
- JVM 回归：`./gradlew :app:testDebugUnitTest`，覆盖输出排空、超时、取消、授权拒绝、自定义路径、参数引用和应用管理结果。
- 真机回归：`node scripts/verify-shell.cjs`，要求手机已开启运行环境；默认验证普通权限、Root 授权结果、超时和停止。Root 成功时额外验证 Root 命令。测试不会修改 Root 管理器策略。

安装、启动、停止和卸载的完整真机回归使用独立测试包，不操作其他应用。先设置本机的 Android SDK 路径，再执行（需要 SDK Platform 34、Build Tools 34.0.0 和构建 debug APK 时生成的调试签名）：

~~~sh
export ANDROID_SDK_ROOT=/path/to/Android/sdk
XPAUTO_TEST_DIR="$(mktemp -d)"
XPAUTO_BUILD_TOOLS="$ANDROID_SDK_ROOT/build-tools/34.0.0"
"$XPAUTO_BUILD_TOOLS/aapt2" link \
  -I "$ANDROID_SDK_ROOT/platforms/android-34/android.jar" \
  --manifest scripts/fixtures/shell/AndroidManifest.xml \
  -o "$XPAUTO_TEST_DIR/unsigned.apk"
"$XPAUTO_BUILD_TOOLS/apksigner" sign \
  --ks "$HOME/.android/debug.keystore" --ks-key-alias androiddebugkey \
  --ks-pass pass:android --key-pass pass:android \
  --out "$XPAUTO_TEST_DIR/fixture.apk" "$XPAUTO_TEST_DIR/unsigned.apk"
XPAUTO_SHELL_FIXTURE_APK="$XPAUTO_TEST_DIR/fixture.apk" node scripts/verify-shell.cjs
~~~

测试包固定为 com.xpauto.shellfixture；检测到已有同名包会停止，防止覆盖。测试结束清理本次创建的测试包和手机上的 APK，最后同步只读 Shell 示例供悬浮窗运行。整个验证过程检查 XpAuto 的进程 PID 和安装更新时间保持不变。

### 本次验证记录

2026-10-16：Pixel 7a / Android 16 / KernelSU 4.1.3，已手动授予 XpAuto Root：

以下成功记录对应旧包 `com.xiaopang.xianyu`（UID 10314）。改包后的 `com.vixmobi.auto` 使用 UID 10319，需要独立授权，不能沿用这份成功记录。

- 14 项 Shell JVM 测试通过，3 项 VS Code 协议测试通过，debug APK 构建通过。
- 应用实际 UID 为 10314；Root 命令 UID 为 0；自定义 su 路径和两种 Root 命令别名均通过真实 JS 桥接调用。
- 双输出管道各超过 1 MiB 时不会阻塞；普通和 Root 命令超时、停止后不再执行后续标记文件写入，停止后可再次运行。
- 调试控制台中的长 Shell 命令可停止；独立测试 APK（路径含空格、单引号）安装、启动、停止、卸载成功。
- 所有脚本调用期间 XpAuto PID 和安装更新时间不变。其他 Root 管理器及 Android 版本仍需对应设备验证。
