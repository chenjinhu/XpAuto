// 真机验证日志与 Toast；复用已安装应用，不构建或重启，权限在 finally 中恢复。
const assert = require('node:assert/strict');
const fs = require('node:fs');
const { DeviceManager } = require('../vscode-extension/src/device');
const { ProtocolClient } = require('../vscode-extension/src/protocol');
const output = [], events = [];
const manager = new DeviceManager({ adbPath: () => process.env.XPAUTO_ADB || 'adb', devicePort: () => 9229,
    log: text => output.push(text), pickDevice: async () => undefined });
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
const adb = args => manager.adb(['-s', manager.serial, ...args]);
let client, originalPermission;
async function until(check, label, timeout = 6000) {
    const end = Date.now() + timeout;
    do { const value = await check(); if (value) return value; await delay(50); } while (Date.now() < end);
    throw Error('等待超时：' + label);
}
async function open() {
    // runId 只在当前会话内唯一，断连重开后不能复用旧事件。
    events.length = 0;
    client = new ProtocolClient(manager.endpoint + '/debug', 15000);
    client.on('notification', (name, params) => events.push({ name, params }));
    await client.connect(); await client.request('Runtime.enable');
}
async function evaluate(expression) {
    const result = await client.request('Runtime.evaluate', { expression, returnByValue: true });
    assert.equal(result.exceptionDetails, undefined, JSON.stringify(result.exceptionDetails));
    return result.result.value;
}
async function run(source) { return client.request('XpAuto.run', { source, resourceName: 'xpauto://verification/logging.js', noDebug: true }); }
const finished = id => until(() => events.find(e => e.name === 'XpAuto.finished' && e.params.runId === id)?.params, '脚本结束');
const consoleEvents = () => events.filter(e => e.name === 'Runtime.consoleAPICalled').map(e => ({ type: e.params.type, text: e.params.args.map(a => a.value).join(' ') }));
const nodes = async () => (await manager.captureNodes()).nodes;
const toastNode = async text => (await nodes()).find(n => n.text === text);
const noToast = () => until(async () => !(await adb(['shell', 'dumpsys', 'window', 'windows'])).includes('XpAuto toast'), 'Toast 浮层移除');
const show = (text, extra = {}) => 'toast(' + JSON.stringify(text) + ',' + JSON.stringify({ x: 70, y: 350, width: 500, height: 160, duration: 5000, draggable: true, ...extra }) + ');';
async function tap(node) {
    assert.ok(node, '缺少可点击节点'); const [l,t,r,b] = node.bounds;
    await adb(['shell','input','tap',String(Math.round((l+r)/2)),String(Math.round((t+b)/2))]);
}
(async () => {
    try {
        await manager.connect(); await open();
        await adb(['shell','am','start','-f','0x14000000','-n','com.vixmobi.auto/com.vixmobi.auto.devtools.DeveloperActivity']);
        const pid = await adb(['shell','pidof','com.vixmobi.auto']);
        const update = (await adb(['shell','dumpsys','package','com.vixmobi.auto'])).match(/lastUpdateTime=(.*)/)?.[1];
        await evaluate('clearLog(); logd("验证调试 {}",7); logi("验证信息 {}",{ok:true}); logw("验证警告"); loge("验证错误"); print("验证兼容");');
        await until(() => output.some(t => t.includes('验证兼容')), '控制通道输出');
        for (const [type, text] of [['debug','[DEBUG] 验证调试 7'],['info','[INFO] 验证信息 {"ok":true}'],['warning','[WARN] 验证警告'],['error','[ERROR] 验证错误'],['debug','[DEBUG] 验证兼容']]) {
            assert.equal(consoleEvents().filter(e => e.type === type && e.text === text).length, 1);
            assert.equal(output.filter(t => t === text).length, 1);
            await until(async () => (await nodes()).some(n => n.text.includes(text)), '首页日志 ' + type);
        }
        assert.equal(await evaluate('setLogLevel("warn",false)'), true);
        await evaluate('logd("隐藏调试"); logi("隐藏信息"); logw("保留警告"); loge("保留错误");');
        assert.equal(await evaluate('setLogLevel("invalid",true)'), false);
        await evaluate('logi("仍然隐藏");');
        assert.ok(!consoleEvents().some(e => /隐藏/.test(e.text)));
        assert.ok(consoleEvents().some(e => e.text === '[WARN] 保留警告'));
        await evaluate('setLogLevel("debug",true); clearLog(); logi(' + JSON.stringify('清除第一行\n清除第二行\n保留第三行') + '); clearLog(2);');
        await until(async () => (await nodes()).some(n => n.text.includes('保留第三行') && !n.text.includes('清除第一行')), '按行清除');
        assert.ok(!(await nodes()).some(n => n.text.includes('清除第二行')));
        console.log('PASS 日志：四种等级、占位符、对象、print 兼容、过滤、无重复推送、首页与按行清除');

        // 使用节点抓取读取全部窗口，检查日志悬浮窗也收到相同内容。
        let all = await nodes();
        if (!all.some(n => n.desc === '关闭日志悬浮窗')) {
            const collapsed = all.find(n => n.desc === '展开悬浮菜单');
            if (collapsed) { await tap(collapsed); await delay(500); }
            await tap((await nodes()).find(n => n.desc === '悬浮窗日志'));
        }
        await until(async () => (await nodes()).find(n => n.desc === '关闭日志悬浮窗'), '日志悬浮窗打开');
        await evaluate('logw("悬浮等级校验 {}","通过");');
        await until(async () => (await nodes()).filter(n => n.text.includes('[WARN] 悬浮等级校验 通过')).length >= 2, '首页和悬浮日志同步');
        await tap((await nodes()).find(n => n.desc === '关闭日志悬浮窗'));

        await evaluate(show('拖动提示', { draggable: true, textColor: '#A7F3D0' }));
        let node = await until(() => toastNode('拖动提示'), '自定义 Toast');
        assert.deepEqual(node.bounds, [70,350,570,510]);
        fs.writeFileSync('/tmp/xpauto-toast-custom.png', await manager.screenshot());
        await adb(['shell','input','swipe','300','430','440','600','400']);
        node = await until(async () => { const n = await toastNode('拖动提示'); return n && n.bounds[0] > 100 && n; }, '拖动移动');
        assert.ok(node.bounds[1] > 400);
        await evaluate(show('替换提示', { duration: 1800 }));
        await until(() => toastNode('替换提示'), '替换 Toast');
        assert.equal(await toastNode('拖动提示'), undefined);
        await noToast();
        // 不可触摸浮层不出现在 Android 的可交互窗口树中，改查窗口管理器。
        await evaluate(show('点击穿透提示', { duration: 800, draggable: false }));
        await until(async () => (await adb(['shell','dumpsys','window','windows'])).includes('XpAuto toast'), '不可触摸提示');
        assert.match(await adb(['shell','dumpsys','window','windows']), /NOT_TOUCHABLE/);
        await noToast();
        await evaluate('setLogLevel("debug",false);' + show('禁止提示'));
        await delay(100); await noToast();
        await evaluate('setLogLevel("off",true); loge("关闭日志");' + show('日志关闭仍可提示'));
        await until(() => toastNode('日志关闭仍可提示'), '独立 Toast 开关');
        assert.ok(!consoleEvents().some(e => e.text.includes('关闭日志')));
        await evaluate('setLogLevel("off",false)'); await noToast();
        console.log('PASS 自定义 Toast：坐标、尺寸、颜色、拖动、替换、定时消失和独立开关');

        let result = await run('logd("下轮恢复默认");' + show('正常完成提示', { duration: 3000 }));
        assert.equal((await finished(result.runId)).success, true);
        assert.ok(consoleEvents().some(e => e.text === '[DEBUG] 下轮恢复默认'));
        client.close(); await delay(200);
        await until(() => toastNode('正常完成提示'), '正常完成断连后仍显示');
        await noToast(); await open();
        result = await run(show('停止清理') + 'while(true) sleep(50);');
        await until(() => toastNode('停止清理'), '运行提示');
        await manager.stop(); assert.equal((await finished(result.runId)).cancelled, true); await noToast();
        result = await run(show('异常清理') + 'sleep(150);throw Error("预期异常");');
        assert.equal((await finished(result.runId)).success, false); await noToast();
        result = await run(show('断连清理') + 'while(true) sleep(50);');
        await until(() => toastNode('断连清理'), '断连前提示'); client.close(); await noToast();
        await until(() => manager.state.state === 'idle', '释放会话'); await open();
        console.log('PASS 生命周期：下轮重置、正常结束自动断连保留时长、停止/异常/运行中断连清理');

        // 系统 Toast 由 Android 决定时长，通过系统队列确认三种入口和无权限回退。
        for (const [method, title] of [['toast','系统普通提示'],['toast1','系统短提示'],['toast2','系统长提示']]) {
            await evaluate(`${method}(${JSON.stringify(title)});`);
            await until(async () => (await adb(['shell','dumpsys','notification','--noredact'])).includes(title), title);
            if (method === 'toast2') fs.writeFileSync('/tmp/xpauto-toast-system.png', await manager.screenshot());
            await delay(method === 'toast2' ? 4000 : 2500);
        }
        originalPermission = /SYSTEM_ALERT_WINDOW:\s*(\w+)/.exec(await adb(['shell','appops','get','com.vixmobi.auto','SYSTEM_ALERT_WINDOW']))?.[1] || 'default';
        await adb(['shell','appops','set','com.vixmobi.auto','SYSTEM_ALERT_WINDOW','deny']);
        await evaluate(show('权限回退提示'));
        await until(async () => (await adb(['shell','dumpsys','notification','--noredact'])).includes('权限回退提示'), '回退系统 Toast');
        await noToast();
        assert.equal(await adb(['shell','pidof','com.vixmobi.auto']), pid);
        assert.equal((await adb(['shell','dumpsys','package','com.vixmobi.auto'])).match(/lastUpdateTime=(.*)/)?.[1], update);
        console.log('PASS 系统 Toast / toast1 / toast2、无悬浮权限回退，全程未重启或重新安装应用');
    } finally {
        if (originalPermission !== undefined) await adb(['shell','appops','set','com.vixmobi.auto','SYSTEM_ALERT_WINDOW',originalPermission]);
        client?.close();
        if (manager.connected) await adb(['shell','am','start','-f','0x14000000','-n','com.vixmobi.auto/com.vixmobi.auto.devtools.DeveloperActivity']);
        await manager.disconnect();
    }
})().catch(error => { console.error(error); process.exitCode = 1; });
