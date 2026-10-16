// 使用 debug 专用页面验证真实无障碍节点，不读取或修改其他应用的数据。
const assert = require('node:assert/strict');
const fs = require('node:fs');
const { DeviceManager } = require('../vscode-extension/src/device');
const { ProtocolClient } = require('../vscode-extension/src/protocol');
const model = require('../vscode-extension/media/inspector-model');
const manager = new DeviceManager({ adbPath: () => process.env.XPAUTO_ADB || 'adb', devicePort: () => 9229,
    log: () => {}, pickDevice: async () => undefined });
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
const adb = args => manager.adb(['-s', manager.serial, ...args]);
const events = [];
let client;
async function until(check, label, timeout = 10000) {
    const end = Date.now() + timeout;
    do { const value = await check(); if (value) return value; await delay(50); } while (Date.now() < end);
    throw Error('等待超时：' + label);
}
async function evaluate(expression) {
    const response = await client.request('Runtime.evaluate', { expression, returnByValue: true });
    assert.equal(response.exceptionDetails, undefined, JSON.stringify(response.exceptionDetails));
    return response.result.value;
}
async function check(expression, expected = true) { assert.deepEqual(await evaluate(expression), expected, expression); }
async function run(source) { return client.request('XpAuto.run', { source, resourceName: 'xpauto://verification/selectors.js', noDebug: true }); }
const finished = id => until(() => events.find(e => e.name === 'XpAuto.finished' && e.params.runId === id)?.params, '脚本结束');
async function open() {
    events.length = 0;
    client = new ProtocolClient(manager.endpoint + '/debug', 20000);
    client.on('notification', (name, params) => events.push({ name, params }));
    await client.connect(); await client.request('Runtime.enable');
}
(async () => {
    try {
        await manager.connect();
        await adb(['shell','am','start','-f','0x14000000','-n','com.vixmobi.auto/com.vixmobi.auto.debug.SelectorFixtureActivity']);
        await open();
        const pid = await adb(['shell','pidof','com.vixmobi.auto']);
        const update = (await adb(['shell','dumpsys','package','com.vixmobi.auto'])).match(/lastUpdateTime=(.*)/)?.[1];
        await until(async () => (await manager.captureNodes()).nodes.some(n => n.text === '测试项'), '测试页面');
        await evaluate('globalThis.testPrefix="com.vixmobi.auto:id/selector_";');
        await check('text("测试项").getNodeInfo().length', 1);
        await check('textContains("测试项").getNodeInfo().length', 2);
        await check('desc("说明").getNodeInfo().length', 1);
        await check('descContains("说明").getNodeInfo().length', 2);
        await check('id("selector_exact").exists()', false);
        await check('id(testPrefix+"exact").exists()');
        await check('clz("TextView").exists()', false);
        await check('clzContains("TextView").exists()');
        await check('pkg("com.vixmobi").exists()', false);
        await check('pkgMatch("com[.]vixmobi[.]auto").text("测试项").exists()');
        await check('pkgContains("vixmobi").pkgStartsWith("com.").pkgEndsWith("auto").textStartsWith("测试").textEndsWith("扩展").textMatch("测试项.*").exists()');
        await check('idContains("selector_").idStartsWith("com.vixmobi").idEndsWith("exact").idMatch(".*selector_exact").descStartsWith("说").descEndsWith("明").descMatch("说.*").exists()');
        await check('clzStartsWith("android.").clzEndsWith("TextView").clzMatch("android[.]widget[.]TextView").packageNameMatch("com[.]vixmobi[.]auto").text("测试项").exists()');
        await check('(()=>{const a=text("测试项"), b=text("测试项扩展"); return a.exists()&&b.exists()&&a.getOneNodeInfo().text()==="测试项"&&b.exists();})()');
        await check('text("").exists()');
        await check('(()=>{let errors=0; for(const f of [()=>textMatch("["),()=>depth(-1),()=>bounds(20,0,10,50),()=>selector().getOneNodeInfo(-1),()=>xpath("//node[")]){try{f();}catch(e){errors++;}}return errors;})()', 5);
        console.log('PASS 匹配：精确/包含/前后缀/正则、包名别名、空文本、选择器独立复用和非法参数');

        const capture = await manager.captureNodes();
        const target = capture.nodes.find(n => n.id === 'com.vixmobi.auto:id/selector_check');
        assert.ok(target);
        const conditions = ['checked','checkable','clickable','longClickable','scrollable','focusable','enabled','focused','selected','editable','multiLine','visible','password']
            .map(name => `.${name}(${target[name]})`).join('');
        await check(`id(testPrefix+"check")${conditions}.depth(${target.depth}).index(${target.index}).drawingOrder(${target.drawingOrder}).childCount(0).exists()`);
        await check('id(testPrefix+"check").row(1).column(2).rowSpan(1).columnSpan(1).exists()');
        await check('id(testPrefix+"group").rowCount(2).columnCount(3).exists()');
        await check(`id(testPrefix+"check").bounds(${target.bounds.join(',')}).boundsInScreen().exists()`);
        const [l,t,r,b] = target.bounds;
        await check(`id(testPrefix+"check").boundsInside(${l-2},${t-2},${r+2},${b+2}).boundsContains(${l+2},${t+2},${r-2},${b-2}).exists()`);
        await check('id(testPrefix+"group").child().textContains("测试项").getNodeInfo().length', 2);
        await check('id(testPrefix+"group").directChild().text("测试项").exists()');
        await check('id(testPrefix+"group").child().text("测试点击").exists()', false);
        await check('selector().child().id(testPrefix+"group").child().checked(false).clz("android.widget.CheckBox").exists()');
        await evaluate('globalThis.groupNode=id(testPrefix+"group").getOneNodeInfo();globalThis.exactNode=id(testPrefix+"exact").getOneNodeInfo();');
        await check('groupNode.getNodeInfo(textContains("测试项")).map(n=>n.text())', ['测试项','测试项扩展']);
        await check('groupNode.getOneNodeInfo(id(testPrefix+"group"))', null);
        await check('groupNode.allChildren().length', 3);
        await check('groupNode.child(-1) === null && groupNode.child(99) === null');
        await check('exactNode.parent().id()', 'com.vixmobi.auto:id/selector_group');
        await check('exactNode.siblings().map(n=>n.text())', ['测试项扩展','勾选项']);
        await check('exactNode.previousSiblings().length', 0);
        await check('exactNode.nextSiblings().length', 2);
        await check('id(testPrefix+"check").getOneNodeInfo().previousSiblings().length', 2);
        await check('(()=>{const c=textContains("测试项").find(), a=c.toArray();c.recycle();return a[0].text()+":"+a[1].text();})()', '测试项:测试项扩展');
        await check('(()=>{const p=exactNode.getPoint();return p.length===2&&exactNode.text()==="测试项"&&exactNode.isValid();})()');
        const info = JSON.parse(await evaluate('JSON.stringify(exactNode)'));
        assert.equal(info.text, '测试项'); assert.ok(info.bounds.right > info.bounds.left);
        console.log('PASS 结构：状态、深度/下标、列表行列、边界、级联、局部查询、兄弟节点、数组、JSON 和节点复用');

        for (const expression of [model.absoluteXPath(capture, target), model.relativeXPath(capture, target)]) {
            await check('xpath(' + JSON.stringify(expression) + ').getOneNodeInfo().id()', target.id);
        }
        await check('xpath("//node[@text=\'测试项\']/following-sibling::node").getNodeInfo().length', 2);
        await check('xpath("//node[contains(@text,\'测试项\')]").pkg("com.vixmobi.auto").getNodeInfo().length', 2);
        await check('xpath("//node[@desc=\'测试分组\']").child().text("测试项").exists()');
        await check('id(testPrefix+"group").child().xpath("//node[@text=\'测试项\']").exists()');
        console.log('PASS XPath：分析器绝对/相对路径、轴、函数、属性条件与级联组合');

        await evaluate('globalThis.checkNode=id(testPrefix+"check").getOneNodeInfo();');
        await check('checkNode.clickEx()');
        await until(() => evaluate('checkNode.refresh()&&checkNode.checked()'), '勾选状态');
        await check('checkNode.clickCenter()');
        await until(() => evaluate('checkNode.refresh()&&!checkNode.checked()'), '中心点击');
        await check('click(text("测试点击"))');
        await until(() => evaluate('text("点击成功").exists()'), '点击操作');
        await check('text("测试点击").getOneNodeInfo().longClickEx()');
        await until(() => evaluate('text("长按成功").exists()'), '长按操作');
        await evaluate('globalThis.inputNode=id(testPrefix+"input").getOneNodeInfo();');
        const content = '中文 a\'b" <tag>';
        await check('inputNode.inputText('+JSON.stringify(content)+')');
        await until(async () => await evaluate('inputNode.refresh();inputNode.text()') === content, '输入文本');
        await check('xpath('+JSON.stringify('//node[@text='+model.xpathLiteral(content)+']')+').exists()');
        await check('inputNode.clearText()');
        await until(() => evaluate('inputNode.refresh();inputNode.text()===""'), '清空文本');
        await evaluate('globalThis.scrollNode=id(testPrefix+"scroll").getOneNodeInfo();');
        await check('scrollNode.scrollForward()'); await delay(350);
        await until(() => evaluate('textMatch("滚动位置：[1-9][0-9]*").exists()'), '正向滚动');
        await check('scrollNode.scrollBackward()'); await delay(350);
        await until(() => evaluate('text("滚动位置：0").exists()'), '反向滚动');
        fs.writeFileSync('/tmp/xpauto-selector-fixture.png', await manager.screenshot());
        await evaluate('floaty.showFloatXml("selector-live", ' + JSON.stringify('<TextView text="临时节点验证"/>') + ',50,300);');
        await until(() => evaluate('text("临时节点验证").exists()'), '跨窗口查询');
        await evaluate('globalThis.temporaryNode=text("临时节点验证").getOneNodeInfo();floaty.close("selector-live");');
        await until(() => evaluate('!temporaryNode.isValid()'), '失效节点');
        console.log('PASS 节点操作：点击/手势/长按、输入/清除、正反滚动、多窗口、刷新与失效判断');

        const started = Date.now(); await check('text("不存在的节点验证").getOneNodeInfo(250)', null);
        assert.ok(Date.now()-started >= 220 && Date.now()-started < 3000);
        await check('text("不存在的节点验证").getNodeInfo(0).length', 0);
        await check('click(text("延迟显示"))');
        await check('text("等待目标已出现").getOneNodeInfo(2000).text()', '等待目标已出现');
        let response = await run('text("不存在的节点验证").getOneNodeInfo(60000);globalThis.unexpectedContinuation=true;');
        await until(() => manager.state.state === 'running', '等待中运行状态'); await delay(120);
        const stopTime = Date.now(); await manager.stop(); assert.equal((await finished(response.runId)).cancelled, true);
        assert.ok(Date.now()-stopTime < 2000); await check('typeof unexpectedContinuation', 'undefined');
        response = await run('globalThis.selectorRecovered=text("测试项").exists();logi("选择器恢复：{}",selectorRecovered);');
        assert.equal((await finished(response.runId)).success, true); await check('selectorRecovered');
        const pending = client.request('Runtime.evaluate', {expression:'text("不存在的节点验证").untilFindOne();globalThis.unexpectedConsoleContinuation=true;',returnByValue:true}).catch(error => error);
        await delay(150); await manager.stop();
        const interrupted = await pending;
        assert.ok(interrupted instanceof Error ? /terminated|停止|cancel/i.test(interrupted.message) : interrupted.exceptionDetails);
        await check('typeof unexpectedConsoleContinuation', 'undefined');
        client.close(); await until(() => manager.state.state === 'idle', '控制台停止后断开'); await open();
        await check('text("测试项").exists()');
        assert.equal(await adb(['shell','pidof','com.vixmobi.auto']), pid);
        assert.equal((await adb(['shell','dumpsys','package','com.vixmobi.auto'])).match(/lastUpdateTime=(.*)/)?.[1], update);
        console.log('PASS 等待：0/超时/延迟出现、脚本与控制台等待停止、下轮恢复，全程未重启或重新安装');
    } finally {
        client?.close();
        if (manager.connected) await adb(['shell','am','start','-f','0x14000000','-n','com.vixmobi.auto/com.vixmobi.auto.devtools.DeveloperActivity']);
        await manager.disconnect();
    }
})().catch(error => { console.error(error); process.exitCode=1; });
