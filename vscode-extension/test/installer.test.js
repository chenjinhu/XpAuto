'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const os = require('node:os');
const path = require('node:path');
const { DeviceManager } = require('../src/device');
const { bundledApk, checkApk, sha256 } = require('../src/apk');

function fixture({devices = 'List of devices attached\nphone device model:Pixel\noffline offline\n', state = null} = {}) {
    const calls = [], progress = [];
    const manager = new DeviceManager({adbPath: () => 'adb', devicePort: () => 9229,
        pickDevice: async () => undefined, log: () => {}});
    manager.readDeviceState = async () => state;
    manager.adb = async (args, timeout) => {
        calls.push({args, timeout});
        if (args[0] === 'devices') return devices;
        if (args.includes('getprop')) return '36';
        if (args.includes('install')) return 'Performing Streamed Install\nSuccess';
        return 'Status: ok';
    };
    return {manager, calls, progress, run: () => manager.installApk('/tmp/XpAuto with space.apk', 'phone', text => progress.push(text))};
}

test('无需已安装应用即可安装，ADB 使用独立参数、长超时和覆盖模式，随后打开应用', async () => {
    const f = fixture();
    assert.deepEqual(await f.run(), {serial: 'phone', launchError: undefined});
    const install = f.calls.find(call => call.args.includes('install'));
    assert.deepEqual(install.args, ['-s', 'phone', 'install', '-r', '/tmp/XpAuto with space.apk']);
    assert.equal(install.timeout, 300000);
    assert.ok(f.calls.some(call => call.args.includes('com.vixmobi.auto/com.vixmobi.auto.devtools.DeveloperActivity')));
    assert.equal(f.progress.length, 3);
    assert.equal(f.manager.installing, false);
});

test('拒绝忙碌会话、旧系统和无授权设备；不执行安装', async () => {
    for (const state of ['running','paused','pausing','ready']) {
        const f = fixture({state: {state}});
        await assert.rejects(f.run(), /请先停止/);
        assert.equal(f.calls.some(call => call.args.includes('install')), false);
    }
    const old = fixture(), oldAdb = old.manager.adb;
    old.manager.adb = (args, timeout) => args.includes('getprop') ? '29' : oldAdb(args, timeout);
    await assert.rejects(old.run(), /Android 11/);
    const offline = fixture({devices: 'List of devices attached\nphone unauthorized\n'});
    await assert.rejects(offline.run(), /没有可用设备/);
});

test('多设备选择可取消，不误装到其他设备；安装中阻止重复安装和连接', async () => {
    const f = fixture({devices: 'List of devices attached\na device\nb device\n'});
    const before = process.env.ANDROID_SERIAL;
    delete process.env.ANDROID_SERIAL;
    try { assert.equal(await f.manager.installApk('/tmp/app.apk'), undefined); }
    finally { if (before !== undefined) process.env.ANDROID_SERIAL = before; }
    assert.equal(f.calls.length, 1);
    f.manager.installing = true;
    await assert.rejects(f.manager.connect(), /正在安装/);
    await assert.rejects(f.run(), /操作进行中/);
});

test('安装失败保留错误且不卸载；启动失败仍明确返回安装成功', async () => {
    const f = fixture(), adb = f.manager.adb;
    f.manager.adb = (args, timeout) => args.includes('install') ? 'Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE]' : adb(args, timeout);
    await assert.rejects(f.run(), /INSTALL_FAILED_UPDATE_INCOMPATIBLE/);
    assert.equal(f.manager.installing, false);
    assert.equal(f.calls.some(call => call.args.includes('uninstall')), false);
    const launch = fixture(), launchAdb = launch.manager.adb;
    launch.manager.adb = (args, timeout) => args.includes('start') ? 'Error type 3' : launchAdb(args, timeout);
    assert.match((await launch.run()).launchError, /Error type 3/);
});

test('插件内置包 SHA256 校验发现损坏；未捆绑时允许选择本地包', async t => {
    const root = await fs.mkdtemp(path.join(os.tmpdir(), 'xpauto-apk-'));
    t.after(() => fs.rm(root, {recursive: true, force: true}));
    assert.equal(await bundledApk(root), undefined);
    const directory = path.join(root, 'resources/mobile');
    await fs.mkdir(directory, {recursive: true});
    const apk = path.join(directory, 'xpauto.apk');
    await fs.writeFile(apk, 'fixture apk');
    await fs.writeFile(path.join(directory, 'xpauto.json'), JSON.stringify({packageName: 'com.vixmobi.auto', sha256: await sha256(apk)}));
    assert.equal(await bundledApk(root), apk);
    await fs.appendFile(apk, 'corrupt');
    await assert.rejects(bundledApk(root), /校验失败/);
    await assert.rejects(checkApk('relative.apk'), /绝对路径/);
    await assert.rejects(checkApk(path.join(root, 'file.txt')), /APK 文件/);
});
