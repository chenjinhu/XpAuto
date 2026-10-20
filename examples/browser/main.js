// 先在同一手机安装 vixBrowser；无需 Root，也无需配置电脑端 ADB 转发。
function main() {
    try {
        browser.start();
        const state = browser.open("https://example.com", { timeout: 30000 });
        logi("页面加载完成：", state.title, state.url);
        // vixBrowser 1.4 提供加载进度；成功请判断 status，不能只看 progress=100。
        const loading = browser.getLoading();
        logi("加载状态：", loading.status, "进度：", loading.progress);

        // 返回普通 JS 对象，可直接读取字段或打印。
        const info = browser.executeJs("({title: document.title, url: location.href, heading: document.querySelector('h1')?.textContent})");
        logi(info);

        // SPA 页面可等待特定元素或页面变量，不使用固定 sleep 猜测完成时间。
        const heading = browser.waitFor("document.querySelector('h1')?.textContent", 10000);
        logi("页面标题元素：", heading);

        // 浏览器配置会持久保存，按需启用，null 表示恢复默认。
        // browser.setSettings({ referer: "https://source.example/", ua: "XpAuto/0.7", history_length: 7 });
        // browser.refresh();
        // browser.waitForPage();
        // browser.setSettings({ referer: null, ua: null, history_length: null });
    } catch (error) {
        loge("浏览器调用失败：", error.code, error.statusCode, error.message);
        throw error;
    }
}
main();
