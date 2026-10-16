// 连接已运行的手机验证 floaty API；不构建、不安装，结束时清理测试窗口和文件。
const assert = require('node:assert/strict');
const fs = require('node:fs');
const { DeviceManager } = require('../vscode-extension/src/device');
const { ProtocolClient } = require('../vscode-extension/src/protocol');
const manager = new DeviceManager({ adbPath: () => process.env.XPAUTO_ADB || 'adb', devicePort: () => 9229,
    log: () => {}, pickDevice: async () => undefined });
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
let client;
const events = [];
const xml = `<LinearLayout android:layout_width="220dp" android:layout_height="wrap_content" orientation="vertical"
    background="#F020293B" cornerRadius="14dp" padding="14dp">
    <TextView text="XpAuto 浮窗测试" textColor="#FFFFFF" textSize="18sp"/>
    <TextView tag="status" text="准备运行" textColor="#BBCCEE" layout_marginTop="8dp"/>
    <EditText tag="input" hint="输入备注" textColor="#FFFFFF" layout_width="match_parent" singleLine="true"/>
    <CheckBox tag="check" text="详细日志" textColor="#FFFFFF"/>
</LinearLayout>`;
const adb = args => manager.adb(['-s', manager.serial, ...args]);
async function until(predicate, label) {
    for (let i = 0; i < 100; i++) { const value = await predicate(); if (value) return value; await delay(50); }
    throw new Error('等待超时：' + label);
}
async function evaluate(expression) {
    const response = await client.request('Runtime.evaluate', { expression, returnByValue: true });
    assert.equal(response.exceptionDetails, undefined, JSON.stringify(response.exceptionDetails));
    return response.result.value;
}
async function open() {
    client = new ProtocolClient(manager.endpoint + '/debug', 15000);
    client.on('notification', (name, params) => events.push({ name, params }));
    await client.connect(); await client.request('Runtime.enable');
}
async function finished(runId) {
    return until(() => events.find(event => event.name === 'XpAuto.finished' && event.params.runId === runId)?.params, '脚本结束');
}
async function noWindows() {
    await until(async () => !(await adb(['shell', 'dumpsys', 'window', 'windows'])).includes('XpAuto-script:'), '悬浮窗移除');
}
async function run(source) { return client.request('XpAuto.run', { source, resourceName: 'xpauto://verification/floaty.js', noDebug: true }); }
(async () => {
    try {
        await manager.connect(); await open();
        const pid = await adb(['shell', 'pidof', 'com.vixmobi.auto']);
        const update = (await adb(['shell', 'dumpsys', 'package', 'com.vixmobi.auto'])).match(/lastUpdateTime=(.*)/)?.[1];
        assert.equal(await evaluate('typeof floaty'), 'object');
        assert.equal(await evaluate('floaty.requestFloatViewPermission(0)'), true);
        assert.equal(await evaluate('globalThis.panel = floaty.showFloatXml("probe", ' + JSON.stringify(xml) + ', 70, 400); panel !== null'), true);
        assert.equal(await evaluate('panel.findViewWithTag("status").getText()'), '准备运行');
        assert.equal(await evaluate('panel.findViewWithTag("missing")'), null);
        assert.equal(await evaluate('panel.getChildCount()'), 4);
        assert.equal(await evaluate('panel.getChildAt(0).getText()'), 'XpAuto 浮窗测试');
        assert.equal(await evaluate('panel.findViewWithTag("status").setText("已完成 3 / 5"); panel.findViewWithTag("status").getText()'), '已完成 3 / 5');
        await evaluate('panel.findViewWithTag("status").setTextColor("#88DDFF"); panel.findViewWithTag("status").setTextSize(16);');
        assert.equal(await evaluate('panel.findViewWithTag("check").setChecked(true); panel.findViewWithTag("check").isChecked()'), true);
        assert.equal(await evaluate('panel.findViewWithTag("input").setText("备注中文"); panel.findViewWithTag("input").getText()'), '备注中文');
        assert.equal(await evaluate('floaty.focusable("probe", true)'), true);
        assert.equal(await evaluate('panel.findViewWithTag("input").requestFocus()'), true);
        assert.equal(await evaluate('floaty.focusable("probe", false)'), true);
        assert.ok(await evaluate('floaty.getWidth("probe")') > 0);
        assert.ok(await evaluate('floaty.getHeight("probe")') > 0);
        assert.deepEqual(await evaluate('[floaty.getX("probe"), floaty.getY("probe")]'), [70, 400]);
        assert.equal(await evaluate('floaty.updateX("probe", 100) && floaty.updateY("probe", 500) && floaty.updateSize("probe", 640, 520)'), true);
        assert.deepEqual(await evaluate('[floaty.getX("probe"), floaty.getY("probe"), floaty.getWidth("probe"), floaty.getHeight("probe")]'), [100, 500, 640, 520]);
        assert.equal(await evaluate('floaty.touchable("probe", false)'), true);
        assert.match(await adb(['shell', 'dumpsys', 'window', 'windows']), /NOT_TOUCHABLE/);
        assert.equal(await evaluate('floaty.touchable("probe", true)'), true);
        fs.writeFileSync('/tmp/xpauto-floaty-preview.png', await manager.screenshot());
        await until(async () => (await manager.captureNodes()).nodes.some(node => node.text === '已完成 3 / 5'), '无障碍窗口树更新');
        const checkbox = (await manager.captureNodes()).nodes.find(node => node.text === '详细日志');
        assert.ok(checkbox);
        const [left, top, right, bottom] = checkbox.bounds;
        const tap = () => adb(['shell', 'input', 'tap', String(Math.round((left + right) / 2)), String(Math.round((top + bottom) / 2))]);
        await tap();
        await until(async () => !(await evaluate('panel.findViewWithTag("check").isChecked()')), '可触摸窗口响应勾选');
        await evaluate('floaty.touchable("probe", false)');
        await tap();
        assert.equal(await evaluate('panel.findViewWithTag("check").isChecked()'), false, '关闭触摸后不能再改变复选框');
        await evaluate('floaty.touchable("probe", true)');
        console.log('PASS floaty: permission, XML, main-thread View methods, focus, touch, coordinates, sizes and real accessibility nodes');

        assert.equal(await evaluate('floaty.showFloatXml("probe", "<Unknown/>", 0, 0)'), null);
        assert.equal(await evaluate('floaty.getWidth("probe")'), 640, '创建失败保留旧窗口');
        assert.equal(await evaluate('floaty.updateSize("probe", -9, 10)'), false);
        assert.equal(await evaluate('floaty.updateX("probe", NaN)'), false);
        assert.equal(await evaluate('floaty.close("missing")'), false);
        assert.equal(await evaluate('floaty.getX("missing")'), -1);
        assert.equal(await evaluate('floaty.showFloatView("child", panel.findViewWithTag("status"), 0, 0)'), null);
        assert.equal(await evaluate('floaty.close("probe"); floaty.showFloatView(123, panel, 100, 300) !== null'), true);
        assert.equal(await evaluate('floaty.getX("123")'), 100);
        assert.equal(await evaluate('floaty.close(123)'), true);
        const file = '/data/user/0/com.vixmobi.auto/files/floaty-verification.xml';
        assert.equal(await evaluate('file.writeFile(' + JSON.stringify(xml) + ', ' + JSON.stringify(file) + ')'), true);
        assert.equal(await evaluate('floaty.showFloatXml("file", "floaty-verification.xml", 10, 200) !== null'), true);
        await evaluate('floaty.close("file"); file.deleteFile(' + JSON.stringify(file) + ')');
        assert.equal(await evaluate(`(()=>{for(let i=0;i<16;i++){if(!floaty.showFloatXml("limit"+i, "<TextView text='limit'/>", 0, 200)) return false;}return floaty.showFloatXml("limit17", "<View/>",0,0)===null;})()`), true);
        await evaluate('for(let i=0;i<16;i++) floaty.close("limit"+i);');
        await noWindows();
        console.log('PASS floaty: invalid inputs, duplicate-tag preservation, numeric tags, native View reuse, file layouts and window count limit');

        const show = 'floaty.showFloatXml("lifecycle", ' + JSON.stringify('<TextView text="生命周期"/>') + ', 0, 300);';
        let result = await run(show); assert.equal((await finished(result.runId)).success, true); await noWindows();
        result = await run(show + 'throw new Error("预期测试异常");'); assert.equal((await finished(result.runId)).success, false); await noWindows();
        result = await run(show + 'while(true) {}');
        await until(async () => (await adb(['shell', 'dumpsys', 'window', 'windows'])).includes('XpAuto-script:lifecycle'), '运行中的浮窗');
        await manager.stop(); assert.equal((await finished(result.runId)).cancelled, true); await noWindows();
        result = await run(show); assert.equal((await finished(result.runId)).success, true); await noWindows();
        await evaluate(show); client.close();
        await noWindows(); await until(() => manager.state.state === 'idle', '会话释放');
        await open();
        assert.equal(await evaluate('floaty.getWidth("lifecycle")'), -1);
        result = await run('print("floaty 验证完成");'); assert.equal((await finished(result.runId)).success, true);
        assert.equal(await adb(['shell', 'pidof', 'com.vixmobi.auto']), pid);
        assert.equal((await adb(['shell', 'dumpsys', 'package', 'com.vixmobi.auto'])).match(/lastUpdateTime=(.*)/)?.[1], update);
        console.log('PASS floaty: completion/error/Stop/disconnect cleanup, next-run recovery, no app restart or reinstall');
    } finally {
        if (client) client.close();
        await manager.disconnect();
    }
})().catch(error => { console.error(error); process.exitCode = 1; });
