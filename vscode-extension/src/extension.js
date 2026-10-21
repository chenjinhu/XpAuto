'use strict';
const vscode = require('vscode');
const path = require('node:path');
const { DeviceManager, labels } = require('./device');
const { XpAutoDebugAdapter } = require('./adapter');
const { registerCompletions } = require('./completions');
const { UiInspector } = require('./inspector');
const { bundledApk, checkApk } = require('./apk');
let manager;

function activate(context) {
    const output = vscode.window.createOutputChannel('XpAuto');
    const status = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Left, 80);
    status.command = 'xpauto.console';
    const changed = new vscode.EventEmitter();
    const config = () => vscode.workspace.getConfiguration('xpauto');
    manager = new DeviceManager({
        adbPath: () => config().get('adbPath', 'adb'),
        devicePort: () => config().get('devicePort', 9229),
        pickDevice: async devices => (await vscode.window.showQuickPick(devices.map(([serial, , ...rest]) => ({
            label: serial, description: rest.join(' '),
        })), { placeHolder: '选择 Android 设备' }))?.label,
        log: message => output.appendLine(`[${new Date().toLocaleTimeString()}] ${message}`),
    });
    registerCompletions(context);
    const inspector = new UiInspector(context, manager, output);
    context.subscriptions.push(inspector);
    const refresh = () => {
        const label = labels[manager.state.state] ?? manager.state.state;
        status.text = `$(device-mobile) XpAuto · ${label}`;
        status.tooltip = manager.state.detail;
        status.show();
        changed.fire();
        vscode.commands.executeCommand('setContext', 'xpauto.connected', manager.connected);
    };
    manager.on('state', refresh);
    refresh();
    context.subscriptions.push(output, status, changed,
        vscode.window.registerTreeDataProvider('xpauto.device', {
            onDidChangeTreeData: changed.event,
            getTreeItem: item => item,
            getChildren: () => {
                if (!manager.connected) return [];
                const device = new vscode.TreeItem(`设备：${manager.serial}`);
                device.iconPath = new vscode.ThemeIcon('device-mobile');
                const state = new vscode.TreeItem(`状态：${labels[manager.state.state] ?? manager.state.state}`);
                state.tooltip = manager.state.detail;
                const script = new vscode.TreeItem(`脚本：${manager.state.resourceName || '尚未运行'}`);
                return [device, state, script];
            },
        }),
        vscode.debug.registerDebugConfigurationProvider('xpauto', {
            provideDebugConfigurations: () => [{ type: 'xpauto', request: 'launch', name: 'XpAuto: 调试当前脚本', program: '${file}' }],
            async resolveDebugConfiguration(folder, configuration) {
                if (!configuration.type) configuration = { ...configuration, type: 'xpauto', request: 'launch', name: 'XpAuto: 调试当前脚本', program: '${file}' };
                configuration.internalConsoleOptions ??= 'openOnSessionStart';
                // 日常调试只保存脚本，不重新构建或安装手机应用。
                await vscode.workspace.saveAll(false);
                return configuration;
            },
            async resolveDebugConfigurationWithSubstitutedVariables(folder, configuration) {
                if (!configuration.program || path.extname(configuration.program).toLowerCase() !== '.js') {
                    vscode.window.showErrorMessage('请打开一个已保存的 .js 文件，或在 program 中指定 JS 入口。');
                    return undefined;
                }
                if ([...activeSessions.values()].length) {
                    vscode.window.showErrorMessage('请先停止当前 XpAuto 会话，再运行新脚本。');
                    return undefined;
                }
                return configuration;
            },
        }),
        vscode.debug.registerDebugAdapterDescriptorFactory('xpauto', {
            createDebugAdapterDescriptor: () => new vscode.DebugAdapterInlineImplementation(new XpAutoDebugAdapter(manager)),
        }),
    );
    const activeSessions = new Map();
    context.subscriptions.push(vscode.debug.onDidStartDebugSession(session => {
        if (session.type === 'xpauto') activeSessions.set(session.id, session);
    }), vscode.debug.onDidTerminateDebugSession(session => activeSessions.delete(session.id)));

    const command = (name, callback) => context.subscriptions.push(vscode.commands.registerCommand(name, async (...args) => {
        try { return await callback(...args); }
        catch (error) { output.appendLine(error.stack ?? error.message); output.show(true); vscode.window.showErrorMessage(error.message); }
    }));
    command('xpauto.connect', serial => vscode.window.withProgress({ location: vscode.ProgressLocation.Notification,
        title: 'XpAuto：连接手机' }, () => manager.connect(serial)));
    command('xpauto.disconnect', async () => {
        for (const session of activeSessions.values()) await vscode.debug.stopDebugging(session);
        await manager.disconnect();
    });
    command('xpauto.install', async serial => {
        if (activeSessions.size) throw new Error('请先停止当前 XpAuto 调试会话，再安装应用。');
        let apk = config().get('apkPath', '').trim();
        if (!apk) apk = await bundledApk(context.extensionPath);
        if (!apk) {
            const files = await vscode.window.showOpenDialog({canSelectMany: false,
                openLabel: '安装 XpAuto', filters: {'Android 安装包': ['apk']},
                title: '选择 XpAuto APK（当前开发版本未附带安装包）'});
            if (!files?.length) return;
            apk = files[0].fsPath;
        }
        await checkApk(apk);
        if (activeSessions.size) throw new Error('请先停止当前 XpAuto 调试会话，再安装应用。');
        const result = await vscode.window.withProgress({location: vscode.ProgressLocation.Notification,
            title: 'XpAuto：安装到设备', cancellable: false}, progress =>
            manager.installApk(apk, serial, message => progress.report({message})));
        if (!result) return;
        if (result.launchError) {
            vscode.window.showWarningMessage(`XpAuto 已安装到 ${result.serial}，自动打开失败，请在手机手动打开。${result.launchError}`);
        } else {
            vscode.window.showInformationMessage(`XpAuto 已安装到 ${result.serial}，请在手机完成权限授权并开启运行环境。`);
        }
    });
    const start = async noDebug => {
        const editor = vscode.window.activeTextEditor;
        if (!editor || editor.document.uri.scheme !== 'file' || path.extname(editor.document.fileName).toLowerCase() !== '.js') {
            throw new Error('请先打开一个本地 .js 文件。');
        }
        await editor.document.save();
        return vscode.debug.startDebugging(vscode.workspace.getWorkspaceFolder(editor.document.uri), {
            type: 'xpauto', request: 'launch', name: `XpAuto: ${path.basename(editor.document.fileName)}`,
            program: editor.document.fileName, internalConsoleOptions: 'openOnSessionStart',
        }, { noDebug });
    };
    command('xpauto.run', () => start(true));
    command('xpauto.debug', () => start(false));
    command('xpauto.stop', async () => {
        await manager.stop();
        for (const session of activeSessions.values()) await vscode.debug.stopDebugging(session);
    });
    command('xpauto.console', () => vscode.commands.executeCommand('workbench.debug.action.toggleRepl'));
    command('xpauto.logs', () => output.show(true));
    command('xpauto.inspect', () => inspector.show());
    command('xpauto.captureUi', () => inspector.capture());
    command('xpauto.loadUi', () => inspector.load());
    command('xpauto.saveUi', () => inspector.save());
    return { getState: () => ({ ...manager.state, connected: manager.connected, serial: manager.serial }),
        connect: serial => manager.connect(serial), disconnect: () => manager.disconnect(), inspector };
}

async function deactivate() { if (manager) await manager.disconnect(); }
module.exports = { activate, deactivate };
