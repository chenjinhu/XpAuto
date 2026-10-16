// 验证真实 Javet 代理的字符串和 JSON 转换；只输出条件，不点击或查询其他应用。
const assert = require('node:assert/strict');
const { DeviceManager } = require('../vscode-extension/src/device');
const { ProtocolClient } = require('../vscode-extension/src/protocol');
const deviceLogs = [], consoleLogs = [];
const manager = new DeviceManager({ adbPath: () => process.env.XPAUTO_ADB || 'adb', devicePort: () => 9229,
    log: value => deviceLogs.push(value), pickDevice: async () => undefined });
const adb = args => manager.adb(['-s', manager.serial, ...args]);
let client;
async function evaluate(expression) {
    const response = await client.request('Runtime.evaluate', { expression, returnByValue: true });
    assert.equal(response.exceptionDetails, undefined, JSON.stringify(response.exceptionDetails));
    return response.result.value;
}
(async () => {
    try {
        await manager.connect();
        const pid = await adb(['shell', 'pidof', 'com.vixmobi.auto']);
        const updated = (await adb(['shell', 'dumpsys', 'package', 'com.vixmobi.auto'])).match(/lastUpdateTime=(.*)/)?.[1];
        client = new ProtocolClient(manager.endpoint + '/debug', 15000);
        client.on('notification', (name, params) => {
            if (name === 'Runtime.consoleAPICalled') consoleLogs.push(params.args.map(arg => arg.value).join(' '));
        });
        await client.connect(); await client.request('Runtime.enable');
        for (const [expression, expected] of [
            ['selector()', 'selector()'],
            ['text("搜索")', 'text("搜索")'],
            ['textMatch(".*Play.*").visible(true)', 'textMatch(".*Play.*").visible(true)'],
            ['idMatch(".*fake_search_box_top.*")', 'idMatch(".*fake_search_box_top.*")'],
            ['selector().child().checked(false).directChild()', 'selector().child().checked(false).directChild()'],
            ['xpath("//node[@text=\'搜索\']").enabled(true)', 'xpath("//node[@text=\'搜索\']").enabled(true)'],
        ]) {
            assert.deepEqual(await evaluate(`(() => { const s=${expression}; return [String(s), ''+s, \`\${s}\`, s.toString(), s.toJSON(), JSON.parse(JSON.stringify(s))]; })()`), Array(6).fill(expected));
        }
        const special = '中文"\\\n\r\t\u0000.*\\d+😀';
        const described = await evaluate('String(text(' + JSON.stringify(special) + '))');
        assert.equal(JSON.parse(described.slice(5, -1)), special);
        assert.deepEqual(JSON.parse(await evaluate('JSON.stringify({target:text("搜索"), list:[textMatch(".*Play.*")]})')),
            {target:'text("搜索")', list:['textMatch(".*Play.*")']});
        console.log('PASS Javet：String、拼接、模板字符串、toString、toJSON、嵌套 JSON、特殊字符及级联 XPath');

        await evaluate(`(() => {
            const s=text("搜索");
            print(s); logd("条件：{}",s); logi(s); logw("条件",s); loge(s);
            logd({target:s}); logd([s]);
            const original=text("容器"), child=original.child().text("目标");
            logd(child); original.enabled(true);
            if (String(original)!=='text("容器").enabled(true)' || String(child)!=='text("容器").child().text("目标")') throw Error("打印改变了条件");
            logi("选择器输出验证完成");
        })()`);
        const deadline = Date.now() + 5000;
        while (!deviceLogs.includes('[INFO] 选择器输出验证完成') && Date.now() < deadline)
            await new Promise(resolve => setTimeout(resolve, 50));
        for (const message of ['[DEBUG] text("搜索")', '[DEBUG] 条件：text("搜索")', '[INFO] text("搜索")',
            '[WARN] 条件 text("搜索")', '[ERROR] text("搜索")', '[DEBUG] '+JSON.stringify({target:'text("搜索")'}),
            '[DEBUG] '+JSON.stringify(['text("搜索")']), '[DEBUG] text("容器").child().text("目标")']) {
            assert.ok(consoleLogs.includes(message), '调试控制台缺少：'+message);
            assert.ok(deviceLogs.includes(message), '设备日志缺少：'+message);
        }
        assert.ok(!consoleLogs.some(value => /com\.vixmobi\.auto\.node\.(TaskBase|UiSelector)@/.test(value)));
        assert.equal(await adb(['shell','pidof','com.vixmobi.auto']), pid);
        assert.equal((await adb(['shell','dumpsys','package','com.vixmobi.auto'])).match(/lastUpdateTime=(.*)/)?.[1], updated);
        console.log('PASS print、四级日志、占位符、嵌套对象与数组、条件复用；调试及设备通道一致，未重启或安装');
    } finally {
        client?.close();
        await manager.disconnect();
    }
})().catch(error => { console.error(error); process.exitCode = 1; });
