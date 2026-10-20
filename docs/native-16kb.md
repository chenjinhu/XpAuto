# 16 KB 原生库兼容性

XpAuto 包含 Javet V8 引擎和自研 `xpauto-inspector` 动态库。旧包在启动时显示 Android 应用兼容性对话框，原因是自研库使用 NDK r27 默认的 4 KB LOAD 段对齐，RELRO 保护区域也可能覆盖后续可写数据。

## 修正方式

- `app/src/main/cpp/CMakeLists.txt` 对所有架构设置 `-Wl,-z,max-page-size=16384` 和 `-Wl,-z,common-page-size=16384`，同时处理 LOAD 与 RELRO 的页面边界。
- Android Gradle Plugin 固定为 8.9.2，Gradle Wrapper 固定为 8.11.1；保留 JDK 17、NDK 27.0.12077973、CMake 3.22.1。AGP 更新使 AAB 声明 `PAGE_ALIGNMENT_16K`，覆盖由应用商店重新生成 APK 的路径。
- Javet 保持 5.0.11。实际预编译库的 LOAD 对齐已经是 `0x4000`，RELRO 占用独立 LOAD 段，不与后续可写数据共用 16 KB 页；无需修改二进制或变更 V8 ABI。

原生库同时适用于 4 KB 和 16 KB 页设备。此修正不改变包名、脚本接口或调试协议。没有添加屏蔽兼容性警告的 Manifest 属性，也没有修改手机系统的兼容性开关。

依据：[Android 16 KB 适配指南](https://developer.android.com/guide/practices/page-sizes)、[AGP 8.9 的构建环境要求](https://developer.android.com/build/releases/agp-8-9-0-release-notes)、[Javet 的 16 KB 支持说明](https://www.caoccao.com/Javet/tutorial/basic/installation.html#k-page-size)。

## 构建与检查

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:bundleRelease
python3 scripts/verify-native-alignment.py \
  app/build/outputs/apk/debug/app-debug.apk \
  app/build/outputs/bundle/release/app-release.aab

# 使用 SDK Build Tools 35.0.0 或更新版本再次核对 APK ZIP 对齐。
"$ANDROID_HOME/build-tools/35.0.0/zipalign" -c -P 16 4 \
  app/build/outputs/apk/debug/app-debug.apk
```

`verify-native-alignment.py` 只依赖 Python 3 标准库，检查最终产物中的全部 `.so`：

1. LOAD 对齐至少为 16 KB，文件偏移与虚拟地址满足 16 KB 同余。
2. RELRO 向上取整后的保护范围不覆盖后续可写数据。独占 LOAD 段的 RELRO 允许末尾不对齐，避免误判 Javet。
3. APK 内未压缩 `.so` 的实际 ZIP 数据偏移按 16 KB 对齐。

检查失败返回非零退出码。升级原生依赖、NDK 或打包工具时，需要对 Debug APK 与 Release AAB 都执行检查。AAB 自身不会被直接加载，需进一步核对其配置及生成的 APK：

```bash
java -jar /path/to/bundletool-all.jar dump config \
  --bundle=app/build/outputs/bundle/release/app-release.aab
# optimizations.uncompressNativeLibraries.alignment 应为 PAGE_ALIGNMENT_16K。

java -jar /path/to/bundletool-all.jar build-apks \
  --bundle=app/build/outputs/bundle/release/app-release.aab \
  --output=/tmp/xpauto-release.apks --mode=universal --overwrite
unzip -p /tmp/xpauto-release.apks universal.apk > /tmp/xpauto-release-universal.apk
python3 scripts/verify-native-alignment.py /tmp/xpauto-release-universal.apk
```

上述转换仅用于本地验证，默认调试签名不能作为正式市场发布签名。

## 运行验证

先通过 `adb -s 设备序列号 shell getconf PAGE_SIZE` 确认真实页大小；4096 表示 4 KB，16384 表示 16 KB。文件对齐通过不等同于在 16 KB 内核上执行过测试。普通设备上应验证冷启动不再出现对话框，并回归脚本、断点、忙循环暂停/继续/停止及子线程锁；正式发布前还应在 16 KB 系统上执行同样的回归。

当前调试功能验证脚本为 `scripts/verify-inspector.mjs` 和 `scripts/verify-thread-debug.cjs`。它们连接已运行的 XpAuto，验证过程中不会编译或安装 APK。

## 2026-10-16 验证记录

- Debug APK、Release AAB、由 AAB 转换的 universal APK，四种 ABI 共 8 个库全部通过 ELF/RELRO 检查；两种 APK 均通过 SDK `zipalign -c -P 16 4`，AAB 配置为 `PAGE_ALIGNMENT_16K`。
- 旧 APK 可稳定检出四种架构的 `xpauto-inspector` 对齐错误，新产物全部通过。63 项 Android 单元测试通过，Release 构建及其 lint 检查通过。
- 已覆盖安装到 Pixel 7a（Android 16，实际内存页大小 4096）。安装后核对手机 APK 与本地产物 SHA-256 一致；确认遗留对话框后连续冷启动，新包不再产生兼容性提示，没有使用“不再显示”或清除应用数据。
- 真机验证了 V8 断点、变量求值、单步、异常暂停、原生中断暂停忙循环、继续/停止、停止后重新执行，以及子线程断点、停止和断连恢复。强制停止测试后已恢复此前授权的无障碍服务。

本轮未在 16 KB 内核设备上运行，不能将文件对齐检查和 4 KB 真机回归当作 16 KB 内核运行验证。
