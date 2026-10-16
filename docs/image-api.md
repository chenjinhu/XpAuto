# 截图与图片 API

接口前缀为 `image`，按 [EasyClick 图色文档的截图部分](https://ieasyclick.com/docs/funcs/image-api/#imagerequestscreencapture-申请截图权限)设计。本次包含屏幕授权、全屏/区域截图、图片存取、像素读取和内存回收；找图、找色、OCR、OpenCV 不在本次实现范围内。

## 快速开始

```javascript
function main() {
    // 手机确认系统屏幕共享提示；普通截图不需要 Root。
    if (!image.requestScreenCapture(30000, 1)) {
        loge(image.getErrorMsg());
        return;
    }
    try {
        // 自动等待首帧，输出 PNG；内部自动回收临时图片。
        if (image.captureToFile('screen.png')) {
            logi('已保存到：' + image.getSaveDir() + '/screen.png');
        } else {
            loge(image.getErrorMsg());
        }
    } finally {
        image.releaseScreenCapture();
    }
}
main();
```

完整示例：[examples/screenshot/main.js](../examples/screenshot/main.js)。VS Code 插件 **0.6.0** 提供中文补全、悬停说明、参数提示和图片返回类型推断。本次原生更新需安装一次 APK，之后 F5 仍然只连接和同步 JS。

## 授权与生命周期

| 接口 | 行为 |
| --- | --- |
| `requestScreenCapture(timeout = 10000, type = 0)` | 等待系统截图授权，成功返回 `true`，拒绝、超时或失败返回 `false` |
| `releaseScreenCapture()` | 停止投影、移除截图前台通知，保留已经返回的图片 |
| `getErrorMsg()` | 当前脚本线程最近一次失败原因 |

`timeout` 单位毫秒，范围 1～300000。`type=0` 自动模式和 `type=1` 授权模式目前均使用系统 MediaProjection。**尚未实现代理服务，`type=2` 明确返回 false**，不会静默更换成其他模式。

同一次有效投影中重复申请直接返回 true。释放、系统停止共享、锁屏等导致投影失效后，需要重新申请。Android 14+ 的授权令牌只消费一次；普通旋转保持同一个虚拟显示和 Surface，以屏幕长边为边长保留方形帧，返回图片时去掉系统居中的留白；不复用令牌创建第二个投影。参考 [Android MediaProjection](https://developer.android.com/media/grow/media-projection)。

系统提示由用户确认，应用不会自动点击授权。Android 14+ 请求共享默认屏幕，设备厂商仍可能允许用户选择单个应用；单应用共享时坐标与尺寸以实际捕获内容为准。系统保护内容（如 `FLAG_SECURE`）可能显示黑色或无法捕获，不绕过系统保护。

截图期间有独立前台通知，可点击“停止截图”；系统屏幕共享指示器也能停止共享。脚本正常完成、异常、主动停止或调试断开时，会释放本会话投影并回收图片。需要持续截图的脚本应保持主函数运行。授权等待和首帧等待会让出 V8，脚本线程可继续运行，停止操作可中断等待。

`tryGetProjectionPermission()` 只是尝试设置系统 app-op，不等于获取 MediaProjection 实例；实际截图仍应调用 `image.requestScreenCapture()`。

## 截图接口

| 接口 | 返回值与说明 |
| --- | --- |
| `captureScreen(retryNumber = 3, x = -1, y = -1, ex = -1, ey = -1)` | `AutoImage` 或 `null`，参数为起点、终点 |
| `captureFullScreen()` | 全屏 `AutoImage` 或 `null` |
| `captureFullScreenEx()` | 当前没有代理服务，与全屏截图相同 |
| `captureScreenshot()` | 按参考文档的非代理行为回退到授权全屏截图 |
| `captureScreenSurface()` | 按参考文档的非代理行为回退到授权全屏截图 |
| `captureScreenBitmap(format = 'png', x = -1, y = -1, ex = -1, ey = -1, q = 100)` | Android `Bitmap` 或 `null` |
| `captureScreenBitmapEx()` | 全屏 Android `Bitmap` 或 `null` |
| `captureToFile(retryNumber, x, y, ex, ey, path)` | PNG 文件，成功 `true`，失败 `false` |
| `captureToFile(path)` | XpAuto 便利重载，保存全屏 PNG |
| `screencapImage(root = true)` | Root 系统命令截图，返回 `AutoImage` 或 `null` |
| `screencapBitmap(root = true)` | Root 系统命令截图，返回 `Bitmap` 或 `null` |

坐标单位为实际像素，`(100, 200, 400, 600)` 产生 **300×400** 图片。区域参数缺省、有负数或区域为空时取全屏；非空区域超出屏幕则失败，不静默截断。`retryNumber` 范围 0～20，0 仍尝试一次；每次最多等待首帧 1 秒。尚未授权时直接返回 null。

授权模式直接取得无损像素，`captureScreenBitmap` 的 format/q 作为兼容参数进行校验，不对截图做有损压缩。需要 JPG/WebP 时调用保存或 Base64 编码接口。支持 png/jpg/jpeg/webp；q 为 1～100，PNG 忽略质量。

连续截图可能来自同一帧，但每次返回独立图片，可以分别回收。截图缓冲只保留最新原始帧，按需转换 Bitmap，不持续编码屏幕。固定方形 Surface 会比单一方向占用更多原生缓冲，但避免部分 Android 14+ 系统旋转后停止送帧；普通全屏截图不缩放，返回图片仍是屏幕原始尺寸。

Root 截图不依赖 MediaProjection，复用 `shell.addSuBin()` 的配置和 Root 授权机制。当前应用必须实际取得 Root；缺少 su、拒绝授权或命令失败返回 null。`root=false` 因无代理服务而明确失败。不会把电脑 ADB 的截图能力冒充为手机脚本能力。

## 图片使用与文件路径

| 接口 | 说明 |
| --- | --- |
| `getWidth(img)` / `getHeight(img)` | 宽高，失败为 0 |
| `saveTo(img, path)` | 保存 PNG，已存在则原子覆盖 |
| `saveBitmap(bitmap, format, q, path)` | 按指定格式保存 |
| `readImage(path)` / `readBitmap(path)` | 读取手机文件，失败为 null |
| `toBase64Format(img, format = 'png', q = 100)` | 无换行 Base64 字符串，失败为 null |
| `bitmapBase64(bitmap, format = 'png', q = 100)` | 同上，参数为 Bitmap |
| `clip(img, x, y, ex, ey)` | 按起点/终点裁剪新图片 |
| `clipBitmap(bitmap, x, y, w, h)` | 按起点/宽高裁剪新 Bitmap |
| `imageToBitmap(img)` / `bitmapToImage(bitmap)` | 转换并复制，返回对象独立持有像素 |
| `pixel(img, x, y)` / `pixelInImage(img, x, y)` / `getPixelBitmap(bitmap, x, y)` | 有符号 32 位 ARGB，失败返回 0 并记录错误 |
| `isRecycled(img)` | null 或已经回收为 true |
| `recycle(img)` | 释放单张图片，允许重复调用 |
| `recycleAllImage()` | 回收本会话由 image 模块创建的图片，返回 true |
| `getSaveDir()` | XpAuto 扩展，返回图片默认保存目录 |

相对路径统一以应用私有 `files/images` 为基准，不以电脑项目目录或脚本 URL 为基准。写入时创建父目录，禁止相对路径越出该目录；绝对路径必须位于 Android 允许当前应用访问的位置。该能力不意味着已有项目资源自动同步。保存到应用私有目录不额外申请存储权限，也不会自动添加到系统相册。

`AutoImage` 支持 `getWidth/getHeight/isRecycled/recycle/toString`。日志显示例如 `AutoImage(1080×2400, recycled=false)`，不显示无意义的 Java 对象地址。Bitmap 保留 Android 原生对象，可用于已有 Bitmap 接口。

每个会话最多同时保留 32 张图片、合计 128 MiB 像素内存；达到限制时返回 null 并提示回收，不会擅自回收脚本仍在使用的图片。转换和裁剪得到的图片也计入限制。`utils.createQRCode()` 创建的 Bitmap 不计入 image 会话清单，除非通过 `image.bitmapToImage()` 转换复制。

```javascript
const screen = image.captureFullScreen();
if (screen) {
    try {
        logd(screen);
        const color = image.pixel(screen, 100, 200);
        logd('ARGB: ' + (color >>> 0).toString(16));
    } finally {
        image.recycle(screen);
    }
}
```

JS 参数类型/范围错误抛异常；系统授权、抓帧、读写等运行错误返回 false/null（数值接口为 0），用 `getErrorMsg()` 获取原因。成功的处理操作清空错误；`recycle`、释放权限和查询错误本身不会覆盖失败原因。不要在其他线程操作同一张图片时回收它。

## 开发结构与验证

- `CapturePermissionActivity`：系统授权页面，用请求 ID 匹配结果，丢弃迟到结果。
- `CaptureService`：MediaProjection 前台服务和通知，不与普通运行环境通知混用。
- `ProjectionCapture`：一次性令牌、虚拟显示、最新帧、行对齐、尺寸变化和释放。
- `ImageApi/AutoImage`：图片读写、格式转换、独立所有权、内存上限和错误信息。
- `image.js`：参数校验、默认值和兼容别名。
- `ScriptModules`：接入调试与手机本地运行的统一生命周期。

单元测试覆盖起终点、负数/空区域、越界和提示类型；真机脚本 `scripts/verify-screenshot.cjs` 验证授权拒绝/超时、首帧、纯色像素、裁剪、保存与编码、图片独立回收、上限、横竖屏、安全窗口、再次授权、正常结束与主动停止清理。测试恢复原旋转设置并删除临时图片。

2026-10-16：上述系统授权截图测试已在 Pixel 7a / Android 16 全部通过，应用 PID 保持不变。当前应用进程无法找到 su，因此 Root 截图只验证了明确失败及错误信息，未验证 Root 截图成功路径。

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
cd vscode-extension && npm test
# 返回工程根目录后，连接已经更新 APK 的测试手机执行：
XPAUTO_ADB=/path/to/adb node scripts/verify-screenshot.cjs
```
