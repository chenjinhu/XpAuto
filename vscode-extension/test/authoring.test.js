'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const vm = require('node:vm');
const { SdkLanguageService } = require('../src/language-service');
const model = require('../media/inspector-model');
const { captureUi, parseSnapshot } = require('../src/ui-capture');
const png = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=';
function fixture() {
    const node = (nodeId, parentId, children, index) => ({ nodeId, parentId, children, index,
        windowId: 1, depth: parentId ? 1 : 0, drawingOrder: 0, bounds: [0, 0, 1, 1],
        id: 'test:id/button', text: 'He said "it\'s <script>"\n你好', desc: '', clz: 'android.widget.TextView', pkg: 'test', visible: true, enabled: true });
    return { format: 'xpauto.ui-snapshot', version: 1, id: 'test', serial: 'test', capturedAt: new Date().toISOString(),
        screen: { width: 1, height: 1, rotation: 0 }, screenshot: { mimeType: 'image/png', data: png },
        nodes: [node('n0', null, ['n1', 'n2'], 0), node('n1', 'n0', [], 0), node('n2', 'n0', [], 1)],
        windows: [{ id: 1, title: 'test', layer: 0, type: 1, active: true, focused: true, bounds: [0, 0, 1, 1], rootId: 'n0' }] };
}
test('提示正确推断全局、链式选择器、节点变量和集合，避免普通对象和字符串误报', () => {
    const language = new SdkLanguageService(path.join(__dirname, '../types/xpauto.d.ts'));
    const names = source => { language.setDocument('/tmp/xpauto-hints.js', source, 1); return language.completions(source.length).map(value => value.name); };
    try {
        assert.ok(names('she').includes('shell'));
        for (const name of ['logd','logi','logw','loge']) assert.ok(names('log').includes(name));
        assert.ok(names('toast("done", { dur').includes('duration'));
        assert.ok(names('setLog').includes('setLogLevel'));
        assert.ok(names('getCurrent').includes('getCurrentRunningPkg'));
        assert.ok(names('getRunning').includes('getRunningActivity'));
        assert.ok(names('shell.').includes('execCommand'));
        assert.ok(names('text("确定").').includes('getOneNodeInfo'));
        assert.ok(names('pkg').includes('pkgMatch'));
        assert.ok(names('xpa').includes('xpath'));
        for (const method of ['checked', 'depth', 'index', 'row', 'columnCount', 'child', 'directChild', 'getNodeInfo', 'toString', 'toJSON'])
            assert.ok(names('text("确定").').includes(method));
        assert.ok(names('const ns=selector().checked(true).getNodeInfo(500); ns[0].').includes('siblings'));
        assert.ok(names('const n=xpath("//node").getOneNodeInfo(); n.').includes('refresh'));
        assert.ok(names('const n=text("确定").getOneNodeInfo(); n.info().').includes('checked'));
        assert.ok(names('const n=text("确定").getOneNodeInfo(); n.').includes('click'));
        assert.ok(names('const ns=clz("Button").find(); ns.').includes('size'));
        assert.ok(!names('const ns=clz("Button").find(); ns.').includes('getOneNodeInfo'));
        assert.deepEqual(names('const shell={unrelated:1}; shell.'), []);
        assert.deepEqual(names('"shell.'), []);
        assert.ok(names('shell.').includes('ROOT'), '相同文档版本但不同内容应更新');
        assert.ok(names('floaty.').includes('showFloatXml'));
        assert.ok(names('http.').includes('downloadFile'));
        assert.ok(names('http.request({url: "http://localhost"}).').includes('statusCode'));
        assert.ok(names('const ws=http.newWebsocket("ws://localhost"); ws.').includes('startHeartBeat'));
        assert.ok(names('const ws=http.newWebsocket("ws://localhost"); ws.').includes('onText'));
        assert.ok(names('const view=floaty.showFloatXml("a", "<TextView/>", 0, 0); view.').includes('findViewWithTag'));
        assert.ok(names('const view=floaty.showFloatXml("a", "<TextView/>", 0, 0); const label=view.findViewWithTag("status"); label.').includes('setText'));
        language.setDocument('/tmp/xpauto-hints.js', 'shell.execCommand(', 1);
        assert.match(language.signatures(18).items[0].parameters[0].label, /command/);
        assert.match(language.hover(9).documentation, /Shell/);
    } finally { language.dispose(); }
});
test('快照往返、父子关系校验、数量上限与 PNG 尺寸校验', () => {
    const snapshot = fixture(); assert.equal(parseSnapshot(JSON.stringify(snapshot)).nodes.length, 3);
    for (const mutate of [s => s.nodes[1].children.push('n0'), s => s.nodes[1].parentId = 'missing',
        s => s.nodes.push(s.nodes[0]), s => s.screen.width = 2, s => s.nodes[1].text = 'a'.repeat(4097),
        s => s.screenshot.data = '<svg onload="alert(1)">', s => s.nodes[1].depth = 81]) {
        const bad = fixture(); mutate(bad); assert.throws(() => parseSnapshot(JSON.stringify(bad)));
    }
});
test('代码生成转义引号换行、遵守集合 SDK，XPath 能处理双引号和单引号', () => {
    const snapshot = model.validateSnapshot(fixture()), node = snapshot.nodes[1];
    const source = model.generateCode(snapshot, node, 'selector', ['text']);
    assert.equal(vm.runInNewContext(source, { text: value => value }), node.text);
    assert.match(model.generateCode(snapshot, node, 'multiple'), /nodes.size\(\)/);
    assert.match(model.generateCode(snapshot, node, 'single'), /node.exists\(\)/);
    assert.equal(model.absoluteXPath(snapshot, node), "/hierarchy/window[@id='1']/node[1]/node[1]");
    assert.match(model.relativeXPath(snapshot, node), /concat\(/);
    assert.ok(model.relativeXPath(snapshot, node).endsWith(')[2]'));
    node.textTruncated = true;
    assert.ok(!model.generateCode(snapshot, node, 'selector', ['text']).startsWith('text('));
});
test('命中优先选择深层节点，匹配数考虑生成器补上的类名入口', () => {
    const snapshot = model.validateSnapshot(fixture()), node = snapshot.nodes[1];
    snapshot.nodes[0].clz = 'android.widget.FrameLayout';
    assert.equal(model.hitTest(snapshot, 0, 0)[0].nodeId, 'n1');
    assert.equal(model.hitTest(snapshot, 1, 1).length, 0);
    assert.equal(model.matchingNodes(snapshot, node, ['enabled']).length, 2);
});
test('抓取保持二进制截图，拒绝方向不一致及设备切换', async () => {
    const snapshot = fixture();
    const manager = { serial: 'test', connected: true, connect: async () => {},
        captureNodes: async () => ({ ...snapshot, capturedAt: Date.now() }), screenshot: async () => Buffer.from(png, 'base64') };
    assert.equal((await captureUi(manager)).nodes.length, 3);
    snapshot.screen.width = 2;
    await assert.rejects(() => captureUi(manager), /方向或尺寸/);
    snapshot.screen.width = 1;
    manager.screenshot = async () => { manager.serial = 'other'; return Buffer.from(png, 'base64'); };
    await assert.rejects(() => captureUi(manager), /设备连接/);
});

test('单 Tab 替换当前记录并保留其他历史，普通模式继续新增', () => {
    const fs = require('node:fs');
    const sandbox = { module: { exports: {} }, require(name) {
        if (name === 'vscode') return {};
        if (name === './ui-capture' || name === './inspector-html') return {};
        if (name === '../media/inspector-model') return model;
        return require(name);
    }, Buffer };
    vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../src/inspector.js'), 'utf8'), sandbox);
    const inspector = new sandbox.module.exports.UiInspector({}, {}, {});
    const first = fixture(), second = { ...fixture(), id: 'second' }, third = { ...fixture(), id: 'third' };
    inspector.add(first); inspector.add(second);
    inspector.selected = first.id; inspector.singleTab = true; inspector.add(third);
    assert.equal(inspector.history.length, 2);
    assert.equal(inspector.current.id, 'third');
    assert.ok(inspector.history.some(entry => entry.snapshot.id === 'second'));
    assert.ok(!inspector.history.some(entry => entry.snapshot.id === first.id));
    inspector.singleTab = false; inspector.add(first); assert.equal(inspector.history.length, 3);
});
