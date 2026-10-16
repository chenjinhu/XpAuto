// 真机回归：临时调整本应用的权限，验证悬浮窗默认开启，并在退出时恢复原配置。
const assert = require('node:assert/strict');
const { execFileSync } = require('node:child_process');
const { DeviceManager } = require('../vscode-extension/src/device');
const { ProtocolClient } = require('../vscode-extension/src/protocol');

const packageName = 'com.vixmobi.auto';
const activity = packageName + '/com.vixmobi.auto.devtools.DeveloperActivity';
const dumpFile = '/sdcard/xpauto-autostart-test.xml';
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const adbPath = process.env.XPAUTO_ADB || 'adb';
const serial = process.env.ANDROID_SERIAL || execFileSync(adbPath, ['get-serialno'], { encoding: 'utf8' }).trim();
const adb = args => execFileSync(adbPath, ['-s', serial, ...args], {
    encoding: 'utf8', timeout: 15000, maxBuffer: 3 * 1024 * 1024,
}).trim();
const manager = new DeviceManager({
    adbPath: () => adbPath, devicePort: () => 9229, log: () => {}, pickDevice: async () => serial,
});
let client;

function ui() {
    adb(['shell', 'uiautomator', 'dump', '--windows', dumpFile]);
    const xml = adb(['shell', 'cat', dumpFile]);
    return [...xml.matchAll(/<node\b[^>]*>/g)].map(([tag]) =>
        Object.fromEntries([...tag.matchAll(/([\w-]+)="([^"]*)"/g)].map(match => [match[1], match[2]]))
    ).filter(node => node.package === packageName);
}
function tap(node) {
    assert.ok(node, 'Missing UI element');
    const [left, top, right, bottom] = node.bounds.match(/\d+/g).map(Number);
    adb(['shell', 'input', 'tap', String(Math.round((left + right) / 2)), String(Math.round((top + bottom) / 2))]);
}
function hasBubble(nodes) {
    return nodes.filter(node => ['展开悬浮菜单', '收起悬浮菜单'].includes(node['content-desc']));
}
async function openHome() {
    adb(['shell', 'am', 'start', '-n', activity]);
    await sleep(500);
}
function setOverlay(mode) {
    adb(['shell', 'appops', 'set', packageName, 'SYSTEM_ALERT_WINDOW', mode]);
}
function setAccessibility(value) {
    if (value === 'null') adb(['shell', 'settings', 'delete', 'secure', 'enabled_accessibility_services']);
    else {
        // adb shell 会再次解析参数，完整引用原值，兼容空列表及类名中的特殊字符。
        const quoted = "'" + value.replace(/'/g, "'\\''") + "'";
        adb(['shell', 'settings', 'put', 'secure', 'enabled_accessibility_services', quoted]);
    }
}

(async () => {
    const originalServices = adb(['shell', 'settings', 'get', 'secure', 'enabled_accessibility_services']);
    const originalMode = /SYSTEM_ALERT_WINDOW:\s*(\w+)/.exec(
        adb(['shell', 'appops', 'get', packageName, 'SYSTEM_ALERT_WINDOW'])
    )?.[1] || 'default';
    try {
        // 只移除 XpAuto 的无障碍服务，不改变手机上其他服务的配置。
        const otherServices = originalServices.split(':').filter(value => !value.startsWith(packageName + '/')).join(':');
        setAccessibility(otherServices);
        adb(['shell', 'am', 'force-stop', packageName]);
        setOverlay('allow');
        await openHome();
        let nodes = ui();
        assert.equal(hasBubble(nodes).length, 1, 'Overlay permission alone must show one bubble');
        assert.ok(nodes.some(node => node.text === '关闭运行环境'));
        await sleep(500);
        await manager.connect(serial);
        client = new ProtocolClient(manager.endpoint + '/debug');
        await client.connect();
        await assert.rejects(client.request('XpAuto.run', {
            source: 'globalThis.autostartProbe = true;', resourceName: 'xpauto://autostart-check.js',
        }), /无障碍/);
        client.close(); client = null;
        await manager.disconnect();
        console.log('PASS: overlay starts without accessibility permission; scripts still require it');

        adb(['shell', 'input', 'keyevent', '3']);
        await openHome();
        nodes = ui();
        assert.equal(hasBubble(nodes).length, 1, 'Returning home must not create duplicate overlays');
        tap(hasBubble(nodes)[0]);
        nodes = ui();
        tap(nodes.find(node => node['content-desc'] === '悬浮窗结束'));
        await sleep(1000);
        nodes = ui();
        assert.equal(hasBubble(nodes).length, 0, 'End must remain closed while staying on the page');
        assert.ok(nodes.some(node => node.text === '开启运行环境与悬浮窗'));
        console.log('PASS: returning home is idempotent; End does not immediately restart the overlay');

        adb(['shell', 'am', 'force-stop', packageName]);
        setOverlay('deny');
        await openHome();
        nodes = ui();
        assert.equal(hasBubble(nodes).length, 0, 'No overlay permission must mean no bubble');
        assert.ok(nodes.some(node => node.text === '开启运行环境与悬浮窗'));
        tap(nodes.find(node => node.text === '设置悬浮窗权限'));
        await sleep(500);
        setOverlay('allow');
        // 在授权页授予权限后返回原 Activity，覆盖 onResume 而不是仅覆盖冷启动。
        adb(['shell', 'input', 'keyevent', '4']);
        await sleep(500);
        nodes = ui();
        assert.equal(hasBubble(nodes).length, 1, 'Returning from permission settings must start the overlay');
        assert.ok(nodes.some(node => node['content-desc'] === '打开菜单'));
        console.log('PASS: missing overlay permission prevents startup; granting it and returning starts automatically');
    } finally {
        client?.close();
        await manager.disconnect();
        setOverlay(originalMode);
        setAccessibility(originalServices);
        adb(['shell', 'rm', '-f', dumpFile]);
        await openHome();
    }
})().catch(error => { console.error(error); process.exitCode = 1; });
