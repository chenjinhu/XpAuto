// F5 直接连接已开启运行环境的 XpAuto；不需要每次构建 APK。
function main() {
    // 如需自定义 su，取消下一行注释并替换为手机上的实际路径。
    // shell.addSuBin("/data/local/my-root/su");
    logi("普通应用权限：", shell.execCommand("id", false));

    if (!shell.su()) {
        loge("Root 尚未授权：", shell.getLastResult().error);
        return;
    }
    logi("Root UID：", shell.ROOT("id -u"));
    logi("Android 版本：", shell.execCommand("getprop ro.build.version.release"));
    logi("Root 命令退出码：", shell.getLastResult().code);
}
main();
