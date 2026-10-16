// 示例只读取系统信息；修改属性时请明确指定需要修改的属性名与值。
function main() {
    logi("设备型号：", getprop("ro.product.model", "未知"));
    logi("Android：", getprop("ro.build.version.release", "未知"));
    logi("SDK：", getprop("ro.build.version.sdk", "未知"));

    // 写入示例（需要当前应用拥有 Root，且系统允许修改该属性）：
    // if (!setprop("debug.xpauto.example", "hello")) {
    //     loge("写入失败：", shell.getLastResult().error);
    // }
}
main();
