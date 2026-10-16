'use strict';
// 真机验证只读信息，并使用专用临时属性检查写入；不修改现有系统配置。
const assert = require('node:assert/strict');
const { DeviceManager } = require('../vscode-extension/src/device');
const { ProtocolClient } = require('../vscode-extension/src/protocol');
const manager = new DeviceManager({ adbPath: () => process.env.XPAUTO_ADB || 'adb',
    devicePort: () => 9229, log: () => {}, pickDevice: async () => undefined });
let client;
async function evaluate(expression) {
    const response = await client.request('Runtime.evaluate', { expression, returnByValue: true });
    assert.equal(response.exceptionDetails, undefined, JSON.stringify(response.exceptionDetails));
    return response.result.value;
}
async function write(name, value, root) {
    return evaluate(`(() => { const ok = setprop(${JSON.stringify(name)}, ${JSON.stringify(value)}, ${root}, 5000);
        const r = shell.getLastResult(); return {ok, code:r.code, error:r.error}; })()`);
}
(async () => {
    try {
        await manager.connect();
        client = new ProtocolClient(manager.endpoint + '/debug', 20000);
        await client.connect();
        await client.request('Runtime.enable');
        const adb = args => manager.adb(['-s', manager.serial, ...args]);
        const beforePid = await adb(['shell', 'pidof', 'com.vixmobi.auto']);
        for (const name of ['ro.product.model', 'ro.build.version.release', 'ro.build.version.sdk']) {
            const expected = (await adb(['shell', 'getprop', name])).trim();
            assert.equal(await evaluate(`getprop(${JSON.stringify(name)})`), expected);
        }
        assert.equal(await evaluate('getprop("xpauto.verify.missing.property", "不存在")'), '不存在');
        assert.equal(await evaluate('shell.getLastResult().code'), 0);
        const invalid = await evaluate(`(() => { let count = 0;
            for (const call of [() => getprop('bad;id'), () => setprop('a..b', 1),
                () => setprop('debug.xpauto.test', null), () => getprop('valid', '', false, 0)]) {
                try { call(); } catch (e) { count++; }
            } return count; })()`);
        assert.equal(invalid, 4);
        console.log('PASS 系统属性读取与 ADB 一致、默认值、非法参数');

        const allowed = await evaluate('shell.su(5000)');
        const name = 'debug.xpauto.verify.property';
        const original = await evaluate(`getprop(${JSON.stringify(name)}, '', ${allowed}, 5000)`);
        let changed = false;
        try {
            const value = '中文 \' " ; $(echo literal)';
            const result = await write(name, value, true);
            changed = result.ok;
            if (!allowed) assert.equal(result.ok, false);
            if (result.ok) {
                assert.equal(await evaluate(`getprop(${JSON.stringify(name)}, '', true, 5000)`), value);
                assert.equal((await write(name, '', true)).ok, true);
                assert.equal(await evaluate(`getprop(${JSON.stringify(name)}, '空值', true, 5000)`), '空值');
                console.log('PASS Root 属性写入、特殊字符与空值');
            } else {
                assert.notEqual(result.code, 0);
                assert.ok(result.error);
                console.log('PASS 属性写入被拒绝时返回 false，实际结果', JSON.stringify({allowed, ...result}));
            }
        } finally {
            if (changed) assert.equal((await write(name, original, true)).ok, true, '恢复临时属性');
        }
        // 已申请 Root 后默认 getprop 仍应使用普通权限。
        assert.equal(await evaluate('getprop("ro.product.model")'), (await adb(['shell', 'getprop', 'ro.product.model'])).trim());
        assert.equal(await adb(['shell', 'pidof', 'com.vixmobi.auto']), beforePid);
        console.log('PASS 默认读取权限不受 su 影响，应用进程保持不变');
    } finally {
        client?.close();
        await manager.disconnect();
    }
})().catch(error => { console.error(error); process.exitCode = 1; });
