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

    async adb(args) {
        const { stdout } = await execute(this.options.adbPath(), args, { timeout: 15000, maxBuffer: 1024 * 1024 });
        return stdout.trim();
    }

    update(state) { this.state = state; this.emit('state', state); }
    get connected() { return !!this.control; }
    get endpoint() { return `ws://127.0.0.1:${this.port}`; }

    async connect(serial) {
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
            const devices = (await this.adb(['devices', '-l'])).split('\n').slice(1)
                .map(line => line.trim().split(/\s+/)).filter(([, state]) => state === 'device');
            serial ??= process.env.ANDROID_SERIAL;
            if (!serial) {
                if (devices.length === 1) serial = devices[0][0];
                else if (devices.length > 1) serial = await this.options.pickDevice(devices);
            }
            if (!serial || !devices.some(([id]) => id === serial)) throw new Error('没有可用设备，请连接 USB 并在手机上授权调试。');
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
