// 回调在同一 V8 环境中保留闭包，sleep 释放运行锁；纯 JS 忙循环不会自动让出运行权。
const thread = (() => {
    const duration = value => {
        if (!Number.isSafeInteger(value) || value < 0 || value > 2147483647) throw new RangeError('时间必须是 0～2147483647 毫秒的整数');
        return value;
    };
    const spawn = (callback, delay, interval) => {
        if (typeof callback !== 'function') throw new TypeError('线程回调必须是函数');
        return Number(__xpautoSpawn(callback, duration(delay), duration(interval)));
    };
    const execSync = (condition, timeout) => {
        if (typeof condition !== 'function') throw new TypeError('条件必须是函数');
        const end = Date.now() + duration(timeout);
        do {
            if (condition() === true) return true;
            const remaining = end - Date.now();
            if (remaining <= 0) return false;
            sleep(Math.min(50, remaining));
        } while (true);
    };
    globalThis.setTimeout = (callback, timeout = 0) => spawn(callback, timeout, 0);
    globalThis.cancelTimeout = id => __xpautoThread.cancelThread(id);
    globalThis.setInterval = (callback, interval) => spawn(callback, Math.max(1, duration(interval)), Math.max(1, interval));
    globalThis.cancelInterval = globalThis.cancelTimeout;
    globalThis.execSync = execSync;
    return { execAsync: callback => spawn(callback, 0, 0), execSync,
        cancelThread: id => __xpautoThread.cancelThread(id), isCancelled: id => __xpautoThread.isCancelled(id),
        stopAll: () => __xpautoThread.stopAll() };
})();
