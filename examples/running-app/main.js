// 开启无障碍服务后，在任意应用中运行；只读取当前页面，不切换应用。
function main() {
    const packageName = getCurrentRunningPkg();
    const activity = getRunningActivity();
    logi("当前包名：{}", packageName || "没有可读取的应用窗口");
    logi("当前 Activity：{}", activity || "尚未获取到有效 Activity");
}
main();
