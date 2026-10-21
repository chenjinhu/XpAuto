'use strict';
const { execFile } = require('node:child_process');
const { promisify } = require('node:util');
const { EventEmitter } = require('node:events');
const { ProtocolClient } = require('./protocol');
const execute = promisify(execFile);

const labels = {
    disconnected: '未连接', connecting: '正在连接', idle: '已连接 · 空闲', ready: '已连接 · 就绪',
    running: '运行中', pausing: '正在暂停', paused: '已暂停', completed: '已完成', stopped: '已停止', error: '运行失败',
};
const remoteText = args => (args ?? []).map(value => value.value !== undefined
    ? typeof value.value === 'string' ? value.value : JSON.stringify(value.value)
    : value.unserializableValue ?? value.description ?? value.type).join(' ');

class DeviceManager extends EventEmitter {
    constructor(options) {
        super();
        this.options = options;
        this.state = { state: 'disconnected', detail: '未连接设备' };
    }

    async adb(args, timeout = 15000) {
        const { stdout } = await execute(this.options.adbPath(), args, { timeout, maxBuffer: 1024 * 1024 });
        return stdout.trim();
    }

    // 安装只需要 ADB，不依赖手机已经安装或开启开发服务。
    async selectDevice(serial) {
        const devices = (await this.adb(['devices', '-l'])).split('\n').slice(1)
            .map(line => line.trim().split(/\s+/)).filter(([, state]) => state === 'device');
        serial ??= process.env.ANDROID_SERIAL;
        if (!serial) {
            if (devices.length === 1) serial = devices[0][0];
            else if (devices.length > 1) serial = await this.options.pickDevice(devices);
            if (!serial && devices.length > 1) return undefined;
        }
        if (!serial || !devices.some(([id]) => id === serial)) throw new Error('没有可用设备，请连接 USB 并在手机上授权调试。');
        return serial;
    }

    async readDeviceState(serial) {
        if (this.control && this.serial === serial) return this.control.request('XpAuto.status');
        let port, client, opened = false;
        try {
            port = Number(await this.adb(['-s', serial, 'forward', 'tcp:0', `tcp:${this.options.devicePort()}`]));
            client = new ProtocolClient(`ws://127.0.0.1:${port}/control`, 2000);
            await client.connect();
            opened = true;
            return await client.request('XpAuto.status');
        } catch (error) {
            // 未安装应用或未开启服务时，端口会拒绝或重置连接，可继续首次安装。
            if (!opened && /ECONNREFUSED|ECONNRESET|socket hang up|已断开/.test(error.message)) return null;
            throw error;
        } finally {
            client?.close();
            if (port) await this.adb(['-s', serial, 'forward', '--remove', `tcp:${port}`]).catch(() => {});
        }
    }

    async installApk(apkPath, serial, report = () => {}) {
        if (this.installing || this.connecting) throw new Error('设备操作进行中，请稍后再安装。');
        this.installing = true;
        try {
            serial = await this.selectDevice(serial);
            if (!serial) return undefined;
            report(`检查设备 ${serial}`);
            const sdk = Number(await this.adb(['-s', serial, 'shell', 'getprop', 'ro.build.version.sdk']));
            if (!Number.isInteger(sdk) || sdk < 30) throw new Error('XpAuto 需要 Android 11（API 30）或更高版本。');
            const state = await this.readDeviceState(serial);
            if (state && ['running', 'pausing', 'paused', 'ready'].includes(state.state))
                throw new Error('设备存在运行或调试会话，请先停止会话再安装 XpAuto。');
            if (this.control && this.serial === serial) await this.disconnect();
            report('正在传输并安装 XpAuto，请保持设备连接');
            this.options.log(`正在向 ${serial} 安装 ${apkPath}`);
            // 覆盖安装保留数据；禁止失败后自动卸载或强制降级。
            const result = await this.adb(['-s', serial, 'install', '-r', apkPath], 300000);
            if (!/^Success\s*$/m.test(result)) throw new Error('安装失败：' + result);
            report('安装完成，正在打开手机上的 XpAuto');
            let launchError;
            try {
                const launched = await this.adb(['-s', serial, 'shell', 'am', 'start', '-W', '-n',
                    'com.vixmobi.auto/com.vixmobi.auto.devtools.DeveloperActivity']);
                if (/Error:|Error type|Exception/.test(launched)) throw new Error(launched);
            } catch (error) { launchError = error.message; }
            this.options.log(`XpAuto 已安装到 ${serial}。请在手机完成权限授权并开启运行环境。`);
            return { serial, launchError };
        } catch (error) {
            const detail = error.stderr?.trim() || error.message;
            throw new Error(detail);
        } finally { this.installing = false; }
    }

    update(state) { this.state = state; this.emit('state', state); }
    get connected() { return !!this.control; }
    get endpoint() { return `ws://127.0.0.1:${this.port}`; }

    async connect(serial) {
        if (this.installing) throw new Error('正在安装 XpAuto，请等待安装完成。');
        if (this.control && (!serial || this.serial === serial)) return this;
        if (this.connecting) return this.connecting;
        this.connecting = this.open(serial).finally(() => { this.connecting = null; });
        return this.connecting;
    }

    async open(serial) {
        if (this.control) await this.disconnect();
        this.update({ state: 'connecting', detail: '正在通过 ADB 连接手机' });
        let port;
        let client;
        try {
            serial = await this.selectDevice(serial);
            if (!serial) { this.update({state: 'disconnected', detail: '已取消选择设备'}); return this; }
            port = Number(await this.adb(['-s', serial, 'forward', 'tcp:0', `tcp:${this.options.devicePort()}`]));
            client = new ProtocolClient(`ws://127.0.0.1:${port}/control`);
            client.on('notification', (method, params) => {
                if (method === 'XpAuto.state') this.update(params);
                else if (method === 'XpAuto.log') this.options.log(remoteText(params.args));
            });
            const hello = await client.connect();
            this.serial = serial;
            this.port = port;
            this.control = client;
            this.capabilities = hello.capabilities || [];
            client.once('closed', () => {
                if (this.control !== client) return;
                this.control = null;
                this.update({ state: 'disconnected', detail: '手机连接已断开' });
                this.adb(['-s', serial, 'forward', '--remove', `tcp:${port}`]).catch(() => {});
            });
            this.update(await client.request('XpAuto.status'));
            this.options.log(`已连接 ${serial}。F5 只同步脚本，不会构建或安装 APK。`);
            return this;
        } catch (error) {
            client?.close();
            if (port) await this.adb(['-s', serial, 'forward', '--remove', `tcp:${port}`]).catch(() => {});
            this.control = null;
            this.update({ state: 'disconnected', detail: error.message });
            throw new Error(`${error.message}\n请打开手机 XpAuto，完成权限授权并开启运行环境。`);
        }
    }

    async stop() { if (this.control) await this.control.request('XpAuto.stop'); }
    async captureNodes() {
        await this.connect();
        if (!this.capabilities.includes('captureNodes')) throw new Error('手机版本尚不支持节点抓取，请先更新一次 XpAuto APK。');
        return this.control.request('XpAuto.captureNodes');
    }
    async screenshot(serial = this.serial) {
        // PNG 必须使用二进制 exec-out；不能经过字符串解码或 adb shell 的终端换行转换。
        const { stdout } = await execute(this.options.adbPath(), ['-s', serial, 'exec-out', 'screencap', '-p'],
            { encoding: 'buffer', timeout: 15000, maxBuffer: 16 * 1024 * 1024 });
        return stdout;
    }
    async disconnect() {
        const client = this.control;
        const serial = this.serial;
        const port = this.port;
        this.control = null;
        client?.close();
        if (port) await this.adb(['-s', serial, 'forward', '--remove', `tcp:${port}`]).catch(() => {});
        this.port = undefined;
        this.update({ state: 'disconnected', detail: '设备已断开' });
    }
}

module.exports = { DeviceManager, labels, remoteText };
