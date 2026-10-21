// 显式验证安装命令；与保持 PID/安装时间不变的日常调试回归分开运行。
import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {mkdtemp, rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {fileURLToPath} from 'node:url';
if (!process.env.ANDROID_SERIAL) throw new Error('请通过 ANDROID_SERIAL 显式指定安装测试设备。');
const root = fileURLToPath(new URL('../', import.meta.url));
const profile = await mkdtemp(join(tmpdir(), 'xpauto-install-test-'));
try {
    const executable = process.env.XPAUTO_CODE || (process.platform === 'darwin'
        ? '/Applications/Visual Studio Code.app/Contents/MacOS/Code' : 'code');
    const child = spawn(executable, ['--user-data-dir', join(profile, 'user'), '--extensions-dir', join(profile, 'extensions'),
        '--extensionDevelopmentPath=' + join(root, 'vscode-extension'),
        '--extensionTestsPath=' + join(root, 'vscode-extension/test/install-device.js'),
        '--disable-workspace-trust', '--skip-welcome', '--skip-release-notes', '--new-window', root], {stdio: 'inherit'});
    const timer = setTimeout(() => child.kill('SIGTERM'), 360000);
    try {
        const code = await new Promise((resolve, reject) => {child.once('error', reject);child.once('exit', resolve);});
        assert.equal(code, 0, 'VS Code 一键安装测试失败');
    } finally {clearTimeout(timer);}
} finally {await rm(profile, {recursive: true, force: true});}
