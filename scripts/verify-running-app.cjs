// 真机验证前台包名与 Activity；使用测试页、系统设置和桌面，结束后恢复无障碍配置与首页。
const assert = require('node:assert/strict');
const { DeviceManager } = require('../vscode-extension/src/device');
const { ProtocolClient } = require('../vscode-extension/src/protocol');
const manager = new DeviceManager({ adbPath: () => process.env.XPAUTO_ADB || 'adb', devicePort: () => 9229,
    log: () => {}, pickDevice: async () => undefined });
const adb = args => manager.adb(['-s', manager.serial, ...args]);
const app = 'com.vixmobi.auto';
const home = app + '.devtools.DeveloperActivity', fixture = app + '.debug.SelectorFixtureActivity';
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
let client, originalServices;
async function evaluate(expression) {
    const result = await client.request('Runtime.evaluate', {expression, returnByValue:true});
    assert.equal(result.exceptionDetails, undefined, JSON.stringify(result.exceptionDetails));
    return result.result.value;
}
const current = () => evaluate('[getCurrentRunningPkg(), getRunningActivity()]');
async function until(check, message, timeout = 8000) {
    const end = Date.now() + timeout;
    do { const value = await check(); if (value) return value; await delay(100); } while (Date.now() < end);
    throw Error(message + '，当前读取=' + JSON.stringify(await current()));
}
async function expect(packageName, activity) {
    await until(async () => JSON.stringify(await current()) === JSON.stringify([packageName, activity]), '前台信息不匹配：'+activity);
}
async function start(activity) {
    await adb(['shell','am','start','-f','0x14000000','-n',app+'/'+activity]);
    await expect(app, activity);
}
(async () => {
    try {
        await manager.connect();
        client = new ProtocolClient(manager.endpoint + '/debug', 15000);
        await client.connect(); await client.request('Runtime.enable');
        const pid = await adb(['shell','pidof',app]);
        const updated = (await adb(['shell','dumpsys','package',app])).match(/lastUpdateTime=(.*)/)?.[1];
        await start(fixture); await start(home); await start(fixture);
        await evaluate('click(text("测试窗口事件"));'); await delay(400); await expect(app, fixture);
        await evaluate('click(text("显示测试弹窗"));'); await delay(400);
        const dialog = await current();
        assert.equal(dialog[0], app); assert.ok(dialog[1] === null || dialog[1] === fixture, JSON.stringify(dialog));
        await evaluate('click(text("关闭测试弹窗"));'); await expect(app, fixture);
        await evaluate('click(id("com.vixmobi.auto:id/selector_input"));'); await delay(600); await expect(app, fixture);
        await adb(['shell','input','keyevent','4']);
        console.log('PASS 同包 Activity 切换、控件窗口事件过滤、弹窗不冒充 Activity、键盘不冒充前台应用');

        await adb(['shell','am','start','-a','android.settings.SETTINGS']);
        await until(async () => (await current())[0] === 'com.android.settings', '系统设置包名');
        const resumed = await adb(['shell','dumpsys','activity','activities']);
        const component = resumed.match(/(?:topResumedActivity=|mResumedActivity:).*?\bu\d+\s+(com\.android\.settings\/[^\s}]+)/)?.[1];
        assert.ok(component, '找不到系统设置前台 Activity');
        const [pkg, cls] = component.split('/');
        await expect(pkg, cls.startsWith('.') ? pkg + cls : cls);
        await evaluate('floaty.showFloatXml("running-info",\'<TextView text="前台信息浮层"/>\', 50, 250);');
        await delay(300); await expect(pkg, cls.startsWith('.') ? pkg + cls : cls);
        await evaluate('floaty.close("running-info"); home();');
        const launcher = (await adb(['shell','cmd','package','resolve-activity','--brief','-a','android.intent.action.MAIN','-c','android.intent.category.HOME'])).split('\n').at(-1);
        const [launcherPkg, launcherCls] = launcher.split('/'); assert.ok(launcherCls);
        await expect(launcherPkg, launcherCls.startsWith('.') ? launcherPkg + launcherCls : launcherCls);
        console.log('PASS 跨应用系统设置、桌面、悬浮窗；Activity 与系统实际前台组件一致');

        originalServices = await adb(['shell','settings','get','secure','enabled_accessibility_services']);
        const remaining = originalServices.split(':').filter(value => !value.startsWith(app+'/')).join(':');
        await adb(['shell','settings','put','secure','enabled_accessibility_services', remaining || '""']);
        await expect('', null);
        await adb(['shell','settings','put','secure','enabled_accessibility_services', originalServices]);
        originalServices = undefined;
        await until(async () => (await current())[0] !== '', '无障碍服务重新连接');
        await start(fixture); await start(home);
        assert.equal(await adb(['shell','pidof',app]), pid);
        assert.equal((await adb(['shell','dumpsys','package',app])).match(/lastUpdateTime=(.*)/)?.[1], updated);
        console.log('PASS 无障碍关闭时返回空值，恢复后更新信息；验证期间未重启或重新安装');
    } finally {
        if (originalServices !== undefined) await adb(['shell','settings','put','secure','enabled_accessibility_services', originalServices]);
        client?.close();
        if (manager.connected) await adb(['shell','am','start','-f','0x14000000','-n',app+'/'+home]);
        await manager.disconnect();
    }
})().catch(error => { console.error(error); process.exitCode=1; });
