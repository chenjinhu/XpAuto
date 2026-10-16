// 暂时调整本应用悬浮窗权限，验证授权等待与停止；finally 恢复原权限和首页。
const assert = require('node:assert/strict');
const { DeviceManager } = require('../vscode-extension/src/device');
const { ProtocolClient } = require('../vscode-extension/src/protocol');
const manager = new DeviceManager({ adbPath: () => process.env.XPAUTO_ADB || 'adb', devicePort: () => 9229,
    log: () => {}, pickDevice: async () => undefined });
const wait = ms => new Promise(resolve => setTimeout(resolve, ms));
const adb = args => manager.adb(['-s', manager.serial, ...args]);
let client, original;
async function open() { client = new ProtocolClient(manager.endpoint + '/debug', 15000); await client.connect(); }
async function evaluate(expression) {
    const result = await client.request('Runtime.evaluate', { expression, returnByValue: true });
    assert.equal(result.exceptionDetails, undefined, JSON.stringify(result)); return result.result.value;
}
(async () => {
    try {
        await manager.connect(); await open();
        original = /SYSTEM_ALERT_WINDOW:\s*(\w+)/.exec(await adb(['shell', 'appops', 'get', 'com.vixmobi.auto', 'SYSTEM_ALERT_WINDOW']))?.[1] || 'default';
        await adb(['shell', 'appops', 'set', 'com.vixmobi.auto', 'SYSTEM_ALERT_WINDOW', 'deny']);
        assert.equal(await evaluate('floaty.showFloatXml("denied", "<TextView/>", 0, 0)'), null);
        assert.equal(await evaluate('floaty.requestFloatViewPermission(150)'), false);
        assert.equal(await evaluate('floaty.getWidth("denied")'), -1);
        const pending = client.request('Runtime.evaluate', { expression: 'floaty.requestFloatViewPermission(60000); globalThis.afterFloatyStop = true;', returnByValue: true }).catch(error => error);
        await wait(200);
        const start = Date.now(); await manager.stop();
        const stopped = await pending;
        assert.ok(stopped instanceof Error || stopped.exceptionDetails, '停止等待应中止 JS，而不是继续下一条语句');
        assert.ok(Date.now() - start < 3000);
        assert.equal(await evaluate('typeof afterFloatyStop'), 'undefined');
        client.close(); await wait(300); await open();
        const allowed = evaluate('floaty.requestFloatViewPermission(5000)');
        await wait(200);
        await adb(['shell', 'appops', 'set', 'com.vixmobi.auto', 'SYSTEM_ALERT_WINDOW', 'allow']);
        assert.equal(await allowed, true);
        console.log('PASS floaty permissions: denied creation, authorization timeout, Stop cancels console wait, granting permission resolves pending request');
    } finally {
        if (original !== undefined) await adb(['shell', 'appops', 'set', 'com.vixmobi.auto', 'SYSTEM_ALERT_WINDOW', original]);
        client?.close();
        if (manager.connected) await adb(['shell', 'am', 'start', '-n', 'com.vixmobi.auto/com.vixmobi.auto.devtools.DeveloperActivity']);
        await manager.disconnect();
    }
})().catch(error => { console.error(error); process.exitCode = 1; });
