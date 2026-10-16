'use strict';
const { EventEmitter } = require('node:events');
const WebSocket = require('ws');

class ProtocolClient extends EventEmitter {
    constructor(url, timeout = 10000) {
        super();
        this.url = url;
        this.timeout = timeout;
        this.nextId = 0;
        this.pending = new Map();
    }

    async connect() {
        this.socket = new WebSocket(this.url);
        this.socket.on('message', data => {
            let message;
            try { message = JSON.parse(data.toString()); }
            catch { return; }
            if (message.id != null) {
                const request = this.pending.get(message.id);
                if (!request) return;
                this.pending.delete(message.id);
                clearTimeout(request.timer);
                if (message.error) request.reject(new Error(message.error.message));
                else request.resolve(message.result);
            } else {
                this.emit('notification', message.method, message.params ?? {});
            }
        });
        this.socket.on('close', (code, reason) => {
            const error = new Error(`手机连接已断开 (${code}) ${reason.toString()}`);
            this.rejectPending(error);
            this.emit('closed', error);
        });
        // Always consume transport errors, including those emitted after connect().
        this.socket.on('error', error => this.rejectPending(error));
        return new Promise((resolve, reject) => {
            let settled = false;
            const timer = setTimeout(() => done(new Error('连接超时。请在手机上开启 XpAuto 开发服务。')), this.timeout);
            const onReady = (method, params) => {
                if (method === 'XpAuto.ready') {
                    if (params.protocolVersion !== 1) done(new Error('手机开发服务版本不兼容，请更新一次 XpAuto 应用。'));
                    else done(null, params);
                }
            };
            const onError = error => done(error);
            const done = (error, value) => {
                if (settled) return;
                settled = true;
                clearTimeout(timer);
                this.removeListener('notification', onReady);
                this.removeListener('closed', onError);
                this.socket.removeListener('error', onError);
                if (error) { this.close(); reject(error); }
                else resolve(value);
            };
            this.on('notification', onReady);
            this.once('closed', onError);
            this.socket.once('error', onError);
        });
    }

    request(method, params = {}) {
        if (!this.socket || this.socket.readyState !== WebSocket.OPEN) return Promise.reject(new Error('设备未连接'));
        const id = ++this.nextId;
        return new Promise((resolve, reject) => {
            const timer = setTimeout(() => {
                this.pending.delete(id);
                reject(new Error(`手机请求超时：${method}`));
            }, this.timeout);
            this.pending.set(id, { resolve, reject, timer });
            this.socket.send(JSON.stringify({ id, method, params }));
        });
    }

    rejectPending(error) {
        for (const request of this.pending.values()) { clearTimeout(request.timer); request.reject(error); }
        this.pending.clear();
    }

    close() {
        this.rejectPending(new Error('连接已关闭'));
        if (this.socket) this.socket.terminate();
    }
}

module.exports = { ProtocolClient };
