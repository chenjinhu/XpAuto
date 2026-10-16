// 网络接口使用普通 JS 对象传参，Java 侧只负责 I/O 和可取消的连接资源。
const http = (() => {
    const sockets = new Set(), sleeper = new engines();
    let dispatching = false;
    const request = options => JSON.parse(__xpautoHttp.requestJson(JSON.stringify(options)));
    const body = options => { const response = request(options); return response ? response.body : null; };
    const post = (url, params, files, timeout = 30000, headers = {}) => {
        const options = { url, method: 'POST', data: params, timeout, header: headers };
        if (files && Object.keys(files).length) options.file = Object.entries(files).map(([key, filePath]) => ({ key, filePath }));
        return body(options);
    };
    function pollEvents() {
        if (dispatching) return;
        dispatching = true;
        try { for (const socket of sockets) socket.poll(); }
        finally { dispatching = false; }
    }
    function newWebsocket(url, header = {}, type = 1) {
        const native = __xpautoHttp.newSocketJson(JSON.stringify({ url, header, type }));
        const handlers = {}, hearts = [];
        let auto = false, reconnectAt = 0, manuallyClosed = false;
        const invoke = (kind, ...args) => { if (handlers[kind]) handlers[kind](socket, ...args); };
        const socket = {
            connect(timeout = 10000) { manuallyClosed = false; const result = native.connect(timeout); pollEvents(); return result; },
            close() { manuallyClosed = true; hearts.length = 0; native.close(); pollEvents(); },
            reset() { manuallyClosed = false; return native.reset(); },
            isConnected: () => native.isConnected(), isClosed: () => native.isClosed(),
            sendText: text => native.sendText(String(text)),
            sendBinary(bytes) { const values = Array.from(bytes); if (values.some(value => !Number.isInteger(value) || value < -128 || value > 255)) throw new TypeError('sendBinary 需要字节数组'); return native.sendBinaryJson(JSON.stringify(values)); },
            setAutoReconnect(value) { auto = Boolean(value); },
            setCallTimeout: seconds => native.setCallTimeout(seconds),
            setReadTimeout: seconds => native.setReadTimeout(seconds),
            setWriteTimeout: seconds => native.setWriteTimeout(seconds),
            setPingInterval: seconds => native.setPingInterval(seconds),
            setConnectionLostTimeout: seconds => native.setConnectionLostTimeout(seconds),
            startHeartBeat(binaryCallback, textCallback, period, cancelOld = true) {
                if (!Number.isFinite(period) || period < 50) throw new RangeError('心跳周期至少为 50 毫秒');
                if (cancelOld) hearts.length = 0;
                if (hearts.length >= 16) throw new RangeError('每个连接最多保留 16 个心跳任务');
                hearts.push({ binaryCallback, textCallback, period, next: Date.now() + period });
            },
            stopHeartBeat() { hearts.length = 0; },
        };
        for (const kind of ['Open', 'Text', 'Binary', 'Close', 'Error']) socket['on' + kind] = callback => {
            if (callback !== null && typeof callback !== 'function') throw new TypeError('监听器必须为函数或 null');
            handlers[kind.toLowerCase()] = callback;
        };
        // 只在脚本主动让出执行权时触发回调；断点暂停时不会跨线程执行 JS。
        const poller = { poll() {
            for (const event of JSON.parse(native.pollJson())) {
                if (event.kind === 'open' || event.kind === 'close') invoke(event.kind, event.code, event.data);
                else if (event.kind === 'binary') {
                    const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/', values = [];
                    let bits = 0, count = 0;
                    for (const char of event.data) { const digit = alphabet.indexOf(char); if (digit < 0) break; bits = (bits << 6) | digit; count += 6; if (count >= 8) { count -= 8; values.push((bits >>> count) & 255); } }
                    invoke('binary', new Uint8Array(values));
                } else invoke(event.kind, event.data);
            }
            const now = Date.now();
            if (auto && !manuallyClosed && native.isClosed() && now >= reconnectAt) { reconnectAt = now + 3000; native.startConnect(10000); }
            if (!native.isConnected()) return;
            for (const heart of [...hearts]) if (now >= heart.next) {
                heart.next = now + heart.period;
                const binary = heart.binaryCallback ? heart.binaryCallback() : null;
                const text = heart.textCallback ? heart.textCallback() : null;
                if (binary != null) socket.sendBinary(binary);
                if (text != null) socket.sendText(text);
            }
        } };
        sockets.add(poller);
        return socket;
    }
    // 新一轮脚本丢弃上一轮的监听器，避免闭包和失效 Java 句柄残留。
    globalThis.__xpautoResetNetwork = () => sockets.clear();
    globalThis.__xpautoNetworkSleep = milliseconds => {
        if (!sockets.size) return sleeper._sleep(milliseconds);
        const end = Date.now() + Math.max(0, Number(milliseconds) || 0);
        do { pollEvents(); const remaining = end - Date.now(); if (remaining <= 0) break; sleeper._sleep(Math.min(50, remaining)); } while (true);
    };
    return {
        request, requestEx: request,
        httpGetDefault: (url, timeout = 30000, header = {}) => body({ url, timeout, header }),
        httpGet: (url, data = null, timeout = 30000, header = {}) => body({ url, data, timeout, header }),
        httpPost: post, httpPostEx: post,
        postJSON: (url, json, timeout = 30000, header = {}) => body({ url, method: 'POST', requestBody: typeof json === 'string' ? json : JSON.stringify(json), timeout, header: { 'Content-Type': 'application/json; charset=utf-8', ...header } }),
        downloadFile: (url, path, timeout = 30000, header = {}) => __xpautoHttp.downloadJson(JSON.stringify({ url, path: String(path), timeout, header })),
        downloadFileDefault: (url, path, header = {}) => __xpautoHttp.downloadJson(JSON.stringify({ url, path: String(path), timeout: 30000, header })),
        get: url => body({ url }),
        post: (url, json) => body({ url, method: 'POST', requestBody: typeof json === 'string' ? json : JSON.stringify(json), header: { 'Content-Type': 'application/json; charset=utf-8' } }),
        newWebsocket, pollEvents,
    };
})();
