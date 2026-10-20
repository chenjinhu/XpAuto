// 模块通过导出与入口通信，局部变量不会占用 SDK 全局名称。
exports.process = function (config, message) {
    logi('{}：{}', config.name, message);
    logi('{}，屏幕 {} × {}', device.getModel(), device.getScreenWidth(), device.getScreenHeight());
    logd('第三方应用数量：{}', utils.getInstalledApps().length);
    const node = asEasyClick(clzMatch('.*').getOneNodeInfo(1000));
    if (node) {
        try { logd({text: node.text, bounds: node.bounds, clickable: node.clickable}); }
        finally { node.recycle(); }
    }
};
