// 每次运行默认开启全部日志和 Toast，可以按需要调整过滤等级。
function main() {
    setLogLevel("debug", true);
    logd("调试参数：{}", { task: "示例", count: 3 });
    logi("已完成 {}/{}", 1, 3);
    logw("网络较慢，将在 {} 毫秒后重试", 1000);
    loge("错误日志演示：{}", "这条消息不会抛出异常");

    // 传入 extra 且有悬浮窗权限时，显示可定制的临时提示。
    toast("任务已完成 1 / 3", {
        x: 60, y: 400, width: 600, height: -2,
        duration: 2500, textColor: "#A7F3D0", draggable: true,
    });
    sleep(3000);
    toast1("系统短提示");
    sleep(2500);
    toast2("示例结束：系统长提示");
}
main();
