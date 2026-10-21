'use strict';
const assert = require('node:assert/strict');
const {execFileSync} = require('node:child_process');
const vscode = require('vscode');

// 此测试显式安装应用，必须由 verify-vscode-install.mjs 单独启动。
async function run() {
    const serial = process.env.ANDROID_SERIAL;
    if (!serial) throw new Error('安装测试必须显式指定 ANDROID_SERIAL。');
    const adb = args => execFileSync(process.env.XPAUTO_ADB || 'adb', ['-s', serial, ...args], {encoding: 'utf8'});
    const info = () => adb(['shell', 'dumpsys', 'package', 'com.vixmobi.auto']);
    const before = info();
    const extension = vscode.extensions.getExtension('xpauto.xpauto');
    await extension.activate();
    assert.ok((await vscode.commands.getCommands()).includes('xpauto.install'));
    await vscode.commands.executeCommand('xpauto.install', serial);
    const after = info();
    assert.equal(/firstInstallTime=(.*)/.exec(after)?.[1], /firstInstallTime=(.*)/.exec(before)?.[1], '覆盖安装应保留初次安装时间');
    assert.notEqual(/lastUpdateTime=(.*)/.exec(after)?.[1], /lastUpdateTime=(.*)/.exec(before)?.[1], '安装命令必须实际执行');
    assert.match(adb(['shell', 'dumpsys', 'activity', 'activities']), /topResumedActivity=.*com\.vixmobi\.auto/);
    console.log('PASS real VS Code: xpauto.install 覆盖安装、保留数据并打开 XpAuto');
}
module.exports = {run};
