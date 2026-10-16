// 使用真实 VS Code 扩展宿主验证调试，全程不构建或安装手机应用。
import assert from 'node:assert/strict';
import { execFileSync, spawn } from 'node:child_process';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
const root = fileURLToPath(new URL('../', import.meta.url));
const adb = args => execFileSync(process.env.XPAUTO_ADB || 'adb',
    [...(process.env.ANDROID_SERIAL ? ['-s', process.env.ANDROID_SERIAL] : []), ...args], { encoding: 'utf8' }).trim();
const snapshot = () => ({ pid: adb(['shell', 'pidof', 'com.vixmobi.auto']),
    update: /lastUpdateTime=(.*)/.exec(adb(['shell', 'dumpsys', 'package', 'com.vixmobi.auto']))?.[1] });
const before = snapshot();
const profile = await mkdtemp(join(tmpdir(), 'xpauto-vscode-test-'));
try {
    const executable = process.env.XPAUTO_CODE || (process.platform === 'darwin'
        ? '/Applications/Visual Studio Code.app/Contents/MacOS/Code' : 'code');
    const child = spawn(executable, [
        '--user-data-dir', join(profile, 'user'), '--extensions-dir', join(profile, 'extensions'),
        '--extensionDevelopmentPath=' + join(root, 'vscode-extension'),
        '--extensionTestsPath=' + join(root, 'vscode-extension/test/real-device.js'),
        '--disable-workspace-trust', '--skip-welcome', '--skip-release-notes', '--new-window', root,
    ], { stdio: 'inherit' });
    const timeout = setTimeout(() => child.kill('SIGTERM'), 120000);
    const code = await new Promise((resolve, reject) => { child.once('error', reject); child.once('exit', resolve); });
    clearTimeout(timeout);
    assert.equal(code, 0, 'VS Code extension integration tests failed');
    assert.deepEqual(snapshot(), before, 'app process or installation changed during debugging');
    console.log('PASS: Android PID and package lastUpdateTime unchanged across every debug run');
} finally { await rm(profile, { recursive: true, force: true }); }
