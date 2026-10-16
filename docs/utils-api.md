# 工具函数与加解密

参考 [EasyClick 工具函数](https://ieasyclick.com/docs/funcs/utils-api)。工具对象为 `utils`，加解密对象为 `encodeDecoder`。Android 权限、后台启动限制及包可见性仍适用，查询范围包括本应用与可见桌面应用。

| 函数 | 返回/说明 |
| --- | --- |
| `openApp(packageName)` / `openAppByName(appName)` | boolean，按包名/完整桌面名称启动 |
| `getStartAppCmd(packageName)` | 包含实际 Activity 组件的 `am start` 命令；没有入口时空串 |
| `openActivity(map)` | boolean；map 支持 action、uri、type、pkg、className、flag，其他键作为 Intent extras |
| `getStartActivityCmd(map)` | 转义后的 `am start` 命令字符串 |
| `openIntentAction(action)` | boolean，例如打开 `android.settings.INPUT_METHOD_SETTINGS` |
| `insertImageToAlbum(path)` / `insertVideoToAlbum(path)` | 扫描现有文件，等待结果返回 boolean；建议文件位于 DCIM 等共享目录，私有文件不会自动复制到图库 |
| `zip(zipFile,password,files)` | 压缩文件/目录，空密码不加密，否则 ZIP AES；成功才替换目标文件 |
| `unzip(zipFile,password,destDir)` | 按 UTF-8 文件名解压，返回 boolean |
| `unzipWithEncode(zipFile,password,destDir,encoding)` | 指定文件名编码，如 GBK |
| `readFileInZip(zipFile,password,entry)` | UTF-8 文本或 null，最多 16MB |
| `createQRCode(content,width,height,logo?)` | Android Bitmap 或 null，尺寸 32～4096，可附 Bitmap 图标 |
| `decodeQRCode(bitmap)` | 二维码内容或 null |
| `saveBitmap(bitmap,path)` | XpAuto 扩展：保存为 PNG，返回 boolean |
| `readConfigInt(json,key)` | 缺失/无效返回 0，数值截断小数 |
| `readJSONString(json,key)` | 缺失返回空串 |
| `isObjectNull(value)` / `isObjectNotNull(value)` | 检查 null/undefined |
| `isTrue(value)` | 严格检查 `value === true` |
| `getRatio(ratio)` | 0～100 的百分比概率；0 恒 false，100 恒 true |
| `getRangeInt(min,max)` / 全局 `random(min,max)` | 包含两端的随机安全整数 |
| `randomInt(length)` | 1～15 位整数；长度大于 1 时无前导零 |
| `randomCharNumber(length)` | 随机大小写英文字母和数字，长度 0～1000000 |
| `fileMd5(path)` / `dataMd5(data)` | 小写十六进制 MD5 或 null，字符串使用 UTF-8 |
| `getApkPkgName(path)` | APK 包名，无法解析为空串 |
| `isAppExist(packageName)` | boolean |
| `getAppVersionCode(packageName)` / `getAppVersionName(packageName)` | 未找到分别返回 0 / 空串 |
| `setClipboardText(text)` / `getClipboardText()` | 写入返回 boolean；读取无内容/无读取权限返回空串 |
| `playMp3(path,loop=false)` | 开始播放后返回 boolean |
| `playMp3WaitEnd(path,loop=false)` | 等待播放结束；循环模式须从其他线程调用 stopMp3 或停止脚本 |
| `stopMp3()` | 停止并释放播放器 |

每次只播放一个音频，新播放会停止旧播放。脚本完成或停止会释放播放器。Android 10+ 通常只允许前台应用和当前默认输入法读取剪贴板。文件方法使用本应用的文件访问权限，不因设备已 Root 而自动提升权限。

ZIP 解压先检查目录越界与声明大小，上限 1GB / 100000 项；已有同名文件可能被覆盖。失败写入错误日志，返回 false 或 null。二维码 Bitmap 用完调用 `recycle()`：

```js
const bitmap = utils.createQRCode("https://example.com", 320, 320);
if (bitmap) {
    try { logd(utils.decodeQRCode(bitmap)); }
    finally { bitmap.recycle(); }
}
logd(utils.getStartAppCmd("com.vixmobi.auto"));
```

## 加解密规则

`encodeDecoder.aesEncrypt/aesDecrypt`、`desEncrypt/desDecrypt`、`des3Encrypt/des3Decrypt` 的参数均为 `(data,password)`，加密输出 Base64，解密输出 UTF-8 文本；失败返回 null，`getErrorMsg()` 读取当前线程错误。密码至少 8 个 Unicode 字符。

| 算法 | 密钥 | 模式 |
| --- | --- | --- |
| AES | UTF-8 长度为 16/24/32 字节时直接使用，否则取 SHA-256 前 16 字节 | AES/ECB/PKCS5Padding |
| DES | UTF-8 长度为 8 字节时直接使用，否则取 SHA-256 前 8 字节 | DES/ECB/PKCS5Padding |
| 3DES | UTF-8 长度为 24 字节时直接使用，否则取 SHA-256 前 24 字节 | DESede/CBC/PKCS5Padding，IV 为 8 个零字节 |

参考页面未定义密钥派生与 IV，因此这里只承诺以上明确规则，不承诺与其他产品相同密码产生相同密文。DES/3DES、ECB 和固定 IV 用于兼容已有脚本；新协议应使用经认证的现代加密方案。MD5 仅用于普通校验，不用于密码保存。

开发实现按职责分为 `toolbox/UtilityApi.java`、`toolbox/EncodeDecoder.java` 和 `assets/utils.js`；ZIP 使用 Zip4j 2.11.5，二维码使用 ZXing 3.5.3。
