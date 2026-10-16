// 修改为自己的服务地址；请求失败的原因会写入运行日志和日志悬浮窗。
function main() {
    const response = http.request({
        url: "https://httpbin.org/get",
        data: { project: "XpAuto", message: "你好" },
        timeout: 10000,
    });
    if (!response) { loge("网络请求失败，请检查日志"); return; }
    logi("HTTP " + response.statusCode);
    logi(response.body);
}
main();
