'use strict';
const vscode = require('vscode');
const path = require('node:path');
const os = require('node:os');
const { randomBytes } = require('node:crypto');
const { captureUi, parseSnapshot } = require('./ui-capture');
const model = require('../media/inspector-model');
const { inspectorHtml } = require('./inspector-html');

/** 快照暂存在当前插件进程中；只有点击保存后才写入用户选择的文件。 */
class UiInspector {
    constructor(context, manager, output) {
        this.context = context; this.manager = manager; this.output = output;
        this.history = []; this.busy = false; this.singleTab = false;
    }
    show() {
        if (this.panel) { this.panel.reveal(); return; }
        this.panel = vscode.window.createWebviewPanel('xpauto.inspector', 'XpAuto 节点分析', vscode.ViewColumn.Active,
            { enableScripts: true, retainContextWhenHidden: true,
                localResourceRoots: [vscode.Uri.joinPath(this.context.extensionUri, 'media')] });
        this.panel.webview.html = inspectorHtml(this.panel.webview, this.context.extensionUri, randomBytes(18).toString('hex'));
        this.panel.onDidDispose(() => { this.panel = undefined; });
        this.panel.webview.onDidReceiveMessage(message => this.receive(message).catch(error => {
            this.output.appendLine(error.stack || error.message);
            this.panel?.webview.postMessage({ type: 'error', message: error.message });
        }));
    }
    get current() { return this.history.find(entry => entry.snapshot.id === this.selected)?.snapshot; }
    publish() {
        this.panel?.webview.postMessage({ type: 'state', snapshot: this.current, busy: this.busy, singleTab: this.singleTab,
            history: this.history.map(({ snapshot }) => {
                const active = snapshot.windows.find(window => window.active) || snapshot.windows[0];
                const root = snapshot.nodes.find(node => node.nodeId === active.rootId);
                return { id: snapshot.id, title: root?.pkg || active.title || '未知窗口',
                    capturedAt: snapshot.capturedAt, count: snapshot.nodes.length, serial: snapshot.serial };
            }) });
    }
    add(snapshot) {
        const bytes = Buffer.byteLength(JSON.stringify(snapshot), 'utf8');
        this.history = this.history.filter(entry => entry.snapshot.id !== snapshot.id && (!this.singleTab || entry.snapshot.id !== this.selected));
        this.history.unshift({ snapshot, bytes });
        while (this.history.length > 10 || this.history.reduce((sum, entry) => sum + entry.bytes, 0) > 64 * 1024 * 1024) this.history.pop();
        this.selected = snapshot.id;
        this.publish();
    }
    async capture() {
        this.show();
        if (this.busy) throw new Error('正在抓取节点，请稍后再试');
        this.busy = true;
        this.panel?.webview.postMessage({ type: 'busy', value: true });
        try {
            const snapshot = await captureUi(this.manager);
            this.add(snapshot);
            return snapshot;
        } finally { this.busy = false; this.panel?.webview.postMessage({ type: 'busy', value: false }); }
    }
    async save(uri) {
        const snapshot = this.current;
        if (!snapshot) throw new Error('请先抓取或载入一份节点快照');
        uri ??= await vscode.window.showSaveDialog({ title: '保存节点与截图', filters: { 'XpAuto 节点快照': ['xpauto-ui.json'] },
            defaultUri: vscode.Uri.file(path.join(vscode.workspace.workspaceFolders?.[0]?.uri.fsPath || os.homedir(),
                'xpauto-ui-' + snapshot.capturedAt.replace(/[:.]/g, '-') + '.xpauto-ui.json')) });
        if (!uri) return;
        await vscode.workspace.fs.writeFile(uri, Buffer.from(JSON.stringify(snapshot, null, 2), 'utf8'));
        this.panel?.webview.postMessage({ type: 'notice', message: '已保存节点与 PNG 截图：' + path.basename(uri.fsPath) });
    }
    async load(uri) {
        uri ??= (await vscode.window.showOpenDialog({ title: '载入节点与截图', canSelectMany: false,
            filters: { 'XpAuto 节点快照': ['json'] } }))?.[0];
        if (!uri) return;
        const stat = await vscode.workspace.fs.stat(uri);
        if (stat.size > 24 * 1024 * 1024) throw new Error('快照文件不能超过 24 MB');
        const snapshot = parseSnapshot(Buffer.from(await vscode.workspace.fs.readFile(uri)).toString('utf8'));
        this.show(); this.add(snapshot);
        return snapshot;
    }
    async copy(message) {
        const snapshot = this.current;
        if (!snapshot || snapshot.id !== message.snapshotId) throw new Error('当前快照已改变，请重新选择节点');
        const node = snapshot.nodes.find(value => value.nodeId === message.nodeId);
        if (!node) throw new Error('未找到选中的节点');
        let value;
        if (message.kind === 'value') {
            const properties = model.properties(snapshot, node);
            if (!Object.hasOwn(properties, message.property)) throw new Error('未知节点属性');
            value = String(properties[message.property]);
        } else if (message.kind === 'absolute') value = model.absoluteXPath(snapshot, node);
        else if (message.kind === 'relative') value = model.relativeXPath(snapshot, node);
        else {
            const fields = Array.isArray(message.fields) ? message.fields.filter(field => model.selectorFields.includes(field)) : undefined;
            value = model.generateCode(snapshot, node, message.kind, fields);
        }
        await vscode.env.clipboard.writeText(value);
        this.panel?.webview.postMessage({ type: 'notice', message: '已复制到剪贴板' });
        return value;
    }
    async receive(message) {
        if (!message || typeof message.type !== 'string') return;
        switch (message.type) {
            case 'ready': this.publish(); break;
            case 'singleTab': this.singleTab = message.value === true; this.publish(); break;
            case 'capture': await this.capture(); break;
            case 'save': await this.save(); break;
            case 'load': await this.load(); break;
            case 'selectSnapshot':
                if (this.history.some(entry => entry.snapshot.id === message.id)) { this.selected = message.id; this.publish(); }
                break;
            case 'copy': await this.copy(message); break;
        }
    }
    dispose() { this.panel?.dispose(); this.history = []; }
}
module.exports = { UiInspector };
