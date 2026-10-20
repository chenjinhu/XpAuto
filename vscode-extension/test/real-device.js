'use strict';
// 在真实 VS Code 扩展宿主中，连接已经启动运行环境的手机进行回归。
const vscode = require('vscode');
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const os = require('node:os');
const path = require('node:path');
const model = require('../media/inspector-model');
const traces = new Map();
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
async function until(predicate, label) {
    const deadline = Date.now() + 15000;
    while (Date.now() < deadline) { const result = predicate(); if (result) return result; await delay(30); }
    throw new Error(`Timed out: ${label}\n${JSON.stringify([...traces.values()]).slice(-8000)}`);
}
exports.run = async function run() {
    const tracker = vscode.debug.registerDebugAdapterTrackerFactory('xpauto', {
        createDebugAdapterTracker(session) {
            const trace = { events: [], output: '', ended: false };
            traces.set(session.id, trace);
            return { onDidSendMessage(message) {
                if (message.type !== 'event') return;
                trace.events.push(message);
                if (message.event === 'output') trace.output += message.body.output;
                if (message.event === 'terminated') trace.ended = true;
            } };
        },
    });
    const extension = vscode.extensions.getExtension('xpauto.xpauto');
    assert.ok(extension, 'XpAuto extension is installed in the development host');
    const api = await extension.activate();
    const root = vscode.workspace.workspaceFolders[0].uri.fsPath;
    const sample = path.join(root, 'examples/inspector-probe/main.js');
    const temp = await fs.mkdtemp(path.join(os.tmpdir(), 'xpauto-fixtures-'));
    const originalBreakpoints = [...vscode.debug.breakpoints];
    async function launch(program, options = {}) {
        let session;
        const started = vscode.debug.onDidStartDebugSession(value => { if (value.type === 'xpauto') session = value; });
        try {
            assert.equal(await vscode.debug.startDebugging(vscode.workspace.workspaceFolders[0], {
                type: 'xpauto', request: 'launch', name: 'XpAuto integration', program,
                internalConsoleOptions: 'openOnSessionStart', ...options,
            }, { noDebug: !!options.noDebug }), true);
            return await until(() => session, 'debug session start');
        } finally { started.dispose(); }
    }
    async function event(session, name, from = 0) {
        return until(() => traces.get(session.id)?.events.slice(from).find(value => value.event === name), name);
    }
    async function stop(session) {
        await vscode.debug.stopDebugging(session);
        await until(() => traces.get(session.id)?.ended && !vscode.debug.activeDebugSession, 'session terminated');
        await until(() => api.getState().state === 'idle', 'phone released runtime');
    }
    try {
        const hints = await vscode.workspace.openTextDocument({ language: 'javascript', content:
            'const button = text("确定").getOneNodeInfo();\nbutton.' });
        const completion = await vscode.commands.executeCommand('vscode.executeCompletionItemProvider', hints.uri, new vscode.Position(1, 7));
        assert.ok(completion.items.some(item => (item.label.label || item.label) === 'click' && item.detail?.includes('XpAutoNode')));
        const shellHints = await vscode.workspace.openTextDocument({ language: 'javascript', content: 'shell.execCommand(' });
        const signature = await vscode.commands.executeCommand('vscode.executeSignatureHelpProvider', shellHints.uri, new vscode.Position(0, 18));
        assert.ok(signature.signatures.some(item => item.label.includes('command: string')));
        const hover = await vscode.commands.executeCommand('vscode.executeHoverProvider', shellHints.uri, new vscode.Position(0, 9));
        assert.ok(hover.some(item => item.contents.some(content => /执行.*Shell/.test(content.value))), JSON.stringify(hover));
        const floatyHints = await vscode.workspace.openTextDocument({ language: 'javascript', content: 'floaty.' });
        const floatyCompletion = await vscode.commands.executeCommand('vscode.executeCompletionItemProvider', floatyHints.uri, new vscode.Position(0, 7));
        assert.ok(floatyCompletion.items.some(item => (item.label.label || item.label) === 'showFloatXml'));
        const networkHints = await vscode.workspace.openTextDocument({ language: 'javascript', content: 'const ws=http.newWebsocket("ws://localhost"); ws.' });
        const networkCompletion = await vscode.commands.executeCommand('vscode.executeCompletionItemProvider', networkHints.uri, networkHints.positionAt(networkHints.getText().length));
        assert.ok(networkCompletion.items.some(item => (item.label.label || item.label) === 'startHeartBeat'));
        console.log('PASS real VS Code: SDK completions including HTTP/WebSocket, inferred node variable, Chinese hover and signature help');
        for (const [source, expected] of [['getp', 'getprop'], ['setp', 'setprop'], ['image.', 'requestScreenCapture'], ['browser.', 'executeJs'], ['browser.getState().', 'page_error'], ['browser.', 'getLoading'], ['browser.getLoading().', 'status'], ['browser.getLoading().', 'progress'], ['browser.getState().', 'load_status'], ['device.', 'getModel'], ['utils.', 'getInstalledApps'], ['file.', 'readFileBytes'], ['setLogView', 'setLogViewSizeEx'], ['require.', 'resolve'], ['const n=asEasyClick(text("a").getOneNodeInfo()); n.', 'text']]) {
            const doc = await vscode.workspace.openTextDocument({ language: 'javascript', content: source });
            const items = await vscode.commands.executeCommand('vscode.executeCompletionItemProvider', doc.uri, new vscode.Position(0, source.length));
            assert.ok(items.items.some(item => (item.label.label || item.label) === expected));
        }
        const logHints = await vscode.workspace.openTextDocument({ language: 'javascript', content: 'log' });
        const logCompletion = await vscode.commands.executeCommand('vscode.executeCompletionItemProvider', logHints.uri, new vscode.Position(0,3));
        for (const name of ['logd','logi','logw','loge']) assert.ok(logCompletion.items.some(item => (item.label.label || item.label) === name));
        const runningHints = await vscode.workspace.openTextDocument({ language: 'javascript', content: 'get' });
        const runningCompletion = await vscode.commands.executeCommand('vscode.executeCompletionItemProvider', runningHints.uri, new vscode.Position(0,3));
        for (const name of ['getCurrentRunningPkg', 'getRunningActivity']) assert.ok(runningCompletion.items.some(item => (item.label.label || item.label) === name));
        const toastHints = await vscode.workspace.openTextDocument({ language: 'javascript', content: 'toast("完成", {' });
        const toastCompletion = await vscode.commands.executeCommand('vscode.executeCompletionItemProvider', toastHints.uri, toastHints.positionAt(toastHints.getText().length));
        assert.ok(toastCompletion.items.some(item => (item.label.label || item.label) === 'duration'));
        const nodeHints = await vscode.workspace.openTextDocument({ language: 'javascript', content: 'const nodes=pkg("com.vixmobi.auto").checked(true).getNodeInfo(100); nodes[0].' });
        const nodeCompletion = await vscode.commands.executeCommand('vscode.executeCompletionItemProvider', nodeHints.uri, nodeHints.positionAt(nodeHints.getText().length));
        for (const name of ['siblings','refresh','inputText','getOneNodeInfo']) assert.ok(nodeCompletion.items.some(item => (item.label.label || item.label) === name));
        await api.connect();
        vscode.debug.removeBreakpoints(vscode.debug.breakpoints);
        vscode.debug.addBreakpoints([new vscode.SourceBreakpoint(new vscode.Location(vscode.Uri.file(sample), new vscode.Position(7, 0)))]);
        const session = await launch(sample);
        assert.equal((await event(session, 'stopped')).body.reason, 'breakpoint');
        assert.equal(api.getState().state, 'paused');
        let stack = await session.customRequest('stackTrace', { threadId: 1 });
        const frame = stack.stackFrames[0];
        const captured = await api.inspector.capture();
        assert.ok(captured.nodes.length > 0 && captured.screenshot.data.startsWith('iVBOR'));
        assert.equal(api.getState().state, 'paused', '抓取不能改变断点状态');
        const saved = vscode.Uri.file(path.join(temp, 'capture.xpauto-ui.json'));
        await api.inspector.save(saved);
        const restored = await api.inspector.load(saved);
        assert.deepEqual(restored, captured);
        const activeWindow = captured.windows.find(window => window.active);
        const target = captured.nodes.find(node => node.windowId === activeWindow.id && node.text && node.clz === 'android.widget.TextView' && !node.textTruncated);
        assert.ok(target, '活动窗口有可读的文本节点');
        const clipboard = await vscode.env.clipboard.readText();
        try {
            for (const kind of ['value', 'selector', 'single', 'multiple', 'absolute', 'relative']) {
                const copied = await api.inspector.copy({ snapshotId: captured.id, nodeId: target.nodeId, property: 'text', kind });
                assert.equal(await vscode.env.clipboard.readText(), copied);
                assert.ok(copied.length > 0);
            }
        } finally { await vscode.env.clipboard.writeText(clipboard); }
        const generated = model.generateCode(captured, target, 'selector');
        const nodeExists = await session.customRequest('evaluate', { frameId: frame.id, expression: generated + '.getOneNodeInfo().exists()', context: 'repl' });
        assert.equal(nodeExists.result, 'true', '生成的选择器在真机上找到所选节点');
        const generatedMultiple = model.generateCode(captured, target, 'multiple');
        await session.customRequest('evaluate', { frameId: frame.id, expression: '(() => { ' + generatedMultiple + ' })()', context: 'repl' });
        for (const expression of [model.absoluteXPath(captured, target), model.relativeXPath(captured, target)]) {
            const xpathResult = await session.customRequest('evaluate', { frameId: frame.id,
                expression: 'xpath(' + JSON.stringify(expression) + ').getOneNodeInfo() !== null', context: 'repl' });
            assert.equal(xpathResult.result, 'true', '分析器复制的 XPath 能在断点暂停时查询真机节点');
        }
        assert.equal(api.getState().state, 'paused');
        console.log('PASS real VS Code: capture while paused, PNG, save/load, six clipboard actions, generated selectors and collection code on Android');
        const floatyPreview = await session.customRequest('evaluate', { frameId: frame.id, context: 'repl',
            expression: '(() => { const view = floaty.showFloatXml("debug-preview", "<TextView tag=\'label\' text=\'断点浮窗\'/>", 60, 400); if (!view) return false; view.setText("调试更新成功"); const ok = view.getText() === "调试更新成功" && floaty.getWidth("debug-preview") > 0; floaty.close("debug-preview"); return ok; })()' });
        assert.equal(floatyPreview.result, 'true');
        assert.equal(api.getState().state, 'paused');
        console.log('PASS real VS Code: floaty completion and main-thread view updates while paused at a breakpoint');
        const propertyPreview = await session.customRequest('evaluate', { frameId: frame.id, context: 'repl',
            expression: 'getprop("ro.product.model").length > 0 && typeof setprop === "function" && typeof image.requestScreenCapture === "function" && typeof browser.executeJs === "function" && typeof browser.getLoading === "function"' });
        assert.equal(propertyPreview.result, 'true');
        assert.equal(api.getState().state, 'paused');
        console.log('PASS real VS Code: screenshot/property completions and getprop in paused console');
        assert.equal(frame.source.path, sample);
        assert.equal(frame.line, 8);
        assert.equal(frame.name, 'calculateTotal');
        const scopes = await session.customRequest('scopes', { frameId: frame.id });
        const locals = await session.customRequest('variables', { variablesReference: scopes.scopes.find(scope => scope.name === 'Local').variablesReference });
        assert.equal(locals.variables.find(value => value.name === 'start').value, '5');
        assert.equal((await session.customRequest('evaluate', { frameId: frame.id, expression: 'start + increment', context: 'repl' })).result, '12');
        let cursor = traces.get(session.id).events.length;
        await session.customRequest('next', { threadId: 1 });
        assert.equal((await event(session, 'stopped', cursor)).body.reason, 'step');
        stack = await session.customRequest('stackTrace', { threadId: 1 });
        assert.equal((await session.customRequest('evaluate', { frameId: stack.stackFrames[0].id, expression: 'total', context: 'watch' })).result, '12');
        cursor = traces.get(session.id).events.length;
        await session.customRequest('stepIn', { threadId: 1 });
        await event(session, 'stopped', cursor);
        assert.equal((await session.customRequest('stackTrace', { threadId: 1 })).stackFrames[0].name, 'double');
        cursor = traces.get(session.id).events.length;
        await session.customRequest('stepOut', { threadId: 1 });
        await event(session, 'stopped', cursor);
        await session.customRequest('continue', { threadId: 1 });
        await until(() => api.getState().state === 'completed', 'completed state');
        assert.equal((await session.customRequest('evaluate', { expression: 'probeResult', context: 'repl' })).result, '24');
        assert.match(traces.get(session.id).output, /probeResult = 24/);
        assert.equal((await session.customRequest('evaluate', { expression: 'typeof text + ":" + typeof sleep', context: 'repl' })).result, '"function:function"');
        await session.customRequest('evaluate', { expression: 'print("SDK print works"); logw("SDK warning {}", 7); loge("SDK error {}", 9);', context: 'repl' });
        assert.match(traces.get(session.id).output, /\[WARN\] SDK warning 7/);
        assert.ok(traces.get(session.id).events.some(event => event.event === 'output' && event.body.category === 'stderr' && event.body.output.includes('[ERROR] SDK error 9')));
        await until(() => traces.get(session.id).output.includes('SDK print works'), 'SDK print in Debug Console');
        await session.customRequest('evaluate', { expression: 'print(text("搜索")); logi("选择器：{}", textMatch(".*Play.*").visible(true));', context: 'repl' });
        await until(() => traces.get(session.id).output.includes('[INFO] 选择器：textMatch(".*Play.*").visible(true)'), '选择器条件显示在调试控制台');
        assert.ok(traces.get(session.id).output.includes('[DEBUG] text("搜索")'));
        console.log('PASS real VS Code: breakpoint, local source lines, scopes, evaluate, F10/F11/step out, console, completed state');
        await stop(session);
        vscode.debug.removeBreakpoints(vscode.debug.breakpoints);

        const invalid = path.join(temp, 'error.js');
        await fs.writeFile(invalid, "console.log('before error');\ndebug;\n");
        const failed = await launch(invalid);
        await until(() => api.getState().state === 'error', 'error state');
        await until(() => /ReferenceError: debug is not defined/.test(traces.get(failed.id).output), 'error in Debug Console');
        assert.match(traces.get(failed.id).output, /error\.js:2/);
        await stop(failed);
        console.log('PASS real VS Code: script errors and line numbers visible in Debug Console');

        const throwing = path.join(temp, 'exception.js');
        await fs.writeFile(throwing, "debugger;\nthrow new Error('pause-test');\n");
        const exception = await launch(throwing);
        await event(exception, 'stopped');
        await exception.customRequest('setExceptionBreakpoints', { filters: ['all'] });
        cursor = traces.get(exception.id).events.length;
        await exception.customRequest('continue', { threadId: 1 });
        assert.equal((await event(exception, 'stopped', cursor)).body.reason, 'exception');
        assert.match((await exception.customRequest('exceptionInfo', { threadId: 1 })).description, /pause-test/);
        await exception.customRequest('continue', { threadId: 1 });
        await until(() => api.getState().state === 'error', 'exception completion');
        await stop(exception);
        console.log('PASS real VS Code: debugger statement and exception pause');

        const infinite = path.join(temp, 'loop.js');
        await fs.writeFile(infinite, 'while (true) {}');
        const busy = await launch(infinite);
        await until(() => api.getState().state === 'running', 'running infinite loop');
        // 暂停必须真正打断忙循环，并保留可查看的调用栈。
        await busy.customRequest('pause', { threadId: 1 });
        assert.equal((await event(busy, 'stopped')).body.reason, 'pause');
        assert.equal(api.getState().state, 'paused');
        assert.equal((await busy.customRequest('stackTrace', { threadId: 1 })).stackFrames[0].source.path, infinite);
        await busy.customRequest('continue', { threadId: 1 });
        await until(() => api.getState().state === 'running', 'resume infinite loop');
        cursor = traces.get(busy.id).events.length;
        await busy.customRequest('pause', { threadId: 1 });
        await event(busy, 'stopped', cursor);
        await stop(busy);
        console.log('PASS real VS Code: pause, inspect, resume and stop a busy V8 loop');

        const runOnly = await launch(sample, { noDebug: true });
        await event(runOnly, 'terminated');
        assert.match(traces.get(runOnly.id).output, /probeResult = 24/);
        await until(() => !vscode.debug.activeDebugSession && api.getState().state === 'idle', 'run-only cleanup');
        const runOnlySource = path.join(temp, 'run-only.js');
        await fs.writeFile(runOnlySource, "debugger; console.log('run-only ignores debugger');");
        const runOnlyDebugger = await launch(runOnlySource, { noDebug: true });
        await event(runOnlyDebugger, 'terminated');
        assert.match(traces.get(runOnlyDebugger.id).output, /run-only ignores debugger/);
        assert.ok(!traces.get(runOnlyDebugger.id).events.some(value => value.event === 'stopped'));
        await until(() => !vscode.debug.activeDebugSession && api.getState().state === 'idle', 'run-only debugger cleanup');
        const restart = await launch(sample, { stopOnEntry: true });
        assert.equal((await event(restart, 'stopped')).body.reason, 'entry');
        assert.equal((await restart.customRequest('stackTrace', {threadId:1})).stackFrames[0].source.path, sample);
        await stop(restart);
        console.log('PASS real VS Code: run without debugging, stop on entry, and repeated connections');
        for (let index = 0; index < 3; index++) {
            const paused = await launch(sample, { stopOnEntry: true });
            await event(paused, 'stopped');
            await stop(paused);
        }
        console.log('PASS real VS Code: repeated disconnects while V8 is paused keep the phone alive');
    } finally {
        await vscode.commands.executeCommand('xpauto.stop');
        await api.disconnect();
        vscode.debug.removeBreakpoints(vscode.debug.breakpoints);
        vscode.debug.addBreakpoints(originalBreakpoints);
        tracker.dispose();
        await fs.rm(temp, { recursive: true, force: true });
    }
};
