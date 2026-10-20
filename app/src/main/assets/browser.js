// 官方浏览器 vixBrowser 的同步 JS 客户端；连接、超时和停止复用现有 HTTP 模块。
const browser = (() => {
    const DEFAULT_URL = 'http://127.0.0.1:8765';
    const PACKAGE = 'com.vixmobi.browser';
    const LOAD_STATUSES = ['idle', 'loading', 'complete', 'error'];
    const has = (value, key) => Object.prototype.hasOwnProperty.call(value, key);
    const isObject = value => value !== null && typeof value === 'object' && !Array.isArray(value);
    const isProgress = value => Number.isInteger(value) && value >= 0 && value <= 100;
    const isNullableText = value => value === null || typeof value === 'string';

    function options(value, allowed, label) {
        if (!isObject(value)) throw new TypeError(label + ' 必须为对象');
        for (const key of Object.keys(value)) {
            if (!allowed.includes(key)) throw new TypeError(label + ' 包含未知字段：' + key);
        }
    }
    function timeout(value) {
        if (!Number.isInteger(value) || value < 1 || value > 3600000) {
            throw new RangeError('超时必须为 1..3600000 毫秒的整数');
        }
        return value;
    }
    function interval(value) {
        if (!Number.isInteger(value) || value < 50 || value > 60000) {
            throw new RangeError('轮询间隔必须为 50..60000 毫秒的整数');
        }
        return value;
    }
    function text(value, label) {
        if (typeof value !== 'string' || !value.trim()) throw new TypeError(label + ' 必须为非空字符串');
        return value;
    }
    function settings(value) {
        const keys = ['referer', 'ua', 'history_length'];
        options(value, keys, '浏览器设置');
        if (!Object.keys(value).length) throw new TypeError('至少提供一个浏览器设置');
        const result = {};
        for (const key of keys) {
            if (!has(value, key)) continue;
            const item = value[key];
            if (key === 'history_length') {
                if (item !== null && (!Number.isInteger(item) || item < 0 || item > 2147483647)) {
                    throw new RangeError('history_length 必须为 null 或 0..2147483647 的整数');
                }
            } else if (item !== null && typeof item !== 'string') {
                throw new TypeError(key + ' 必须为字符串或 null');
            }
            result[key] = item;
        }
        // URL、Referer、UA 的规范化及系统支持情况由浏览器统一校验，避免两份规则不一致。
        return result;
    }
    function error(code, message, statusCode = 0, response = null) {
        const result = new Error(message);
        result.name = 'VixBrowserError';
        result.code = code;
        result.statusCode = statusCode;
        result.response = response;
        return result;
    }
    function bodyBytes(value) {
        // V8 没有浏览器的 TextEncoder；按码点计算实际 UTF-8 字节数。
        let count = 0;
        for (const character of value) {
            const code = character.codePointAt(0);
            count += code <= 0x7f ? 1 : code <= 0x7ff ? 2 : code <= 0xffff ? 3 : 4;
        }
        return count;
    }

    function createClient(config = {}) {
        options(config, ['baseUrl', 'timeout', 'pollInterval'], '客户端配置');
        const baseUrl = text(config.baseUrl ?? DEFAULT_URL, 'baseUrl').replace(/\/$/, '');
        const address = /^(https?):\/\/(\[[0-9a-f:.]+\]|[a-z0-9.-]+)(?::([0-9]+))?$/i.exec(baseUrl);
        if (!address || address[0] !== baseUrl || (address[3] && (Number(address[3]) < 1 || Number(address[3]) > 65535))) {
            throw new TypeError('baseUrl 必须是 HTTP/HTTPS 服务地址，不能包含路径、账号、查询参数或片段');
        }
        const requestTimeout = timeout(config.timeout ?? 12000);
        const pollInterval = interval(config.pollInterval ?? 200);

        function request(path, payload, key, limit = requestTimeout) {
            const requestOptions = {
                url: baseUrl + path,
                method: payload === undefined ? 'GET' : 'POST',
                timeout: limit,
                proxy: false,
                followRedirects: false,
                retryOnConnectionFailure: false,
                ignoreHttpErrors: true,
            };
            if (payload !== undefined) {
                const body = JSON.stringify(payload);
                if (bodyBytes(body) > 65536) throw new RangeError('浏览器 JSON 请求体不能超过 64 KiB');
                requestOptions.requestBody = body;
                requestOptions.header = { 'Content-Type': 'application/json; charset=utf-8' };
            }
            // 不捕获网络层的取消异常，用户停止后不允许继续执行后面的脚本。
            const response = http.request(requestOptions);
            if (!response) throw error('transport_error', '无法连接 vixBrowser 或请求超时：' + baseUrl + '。请确认浏览器已启动；详见 HTTP 日志。');
            let data;
            try { data = JSON.parse(response.body); }
            catch (_) { throw error('invalid_response', 'vixBrowser 返回了非 JSON 响应', response.statusCode); }
            if (!isObject(data) || typeof data.ok !== 'boolean') {
                throw error('invalid_response', 'vixBrowser 响应缺少 ok 字段', response.statusCode, data);
            }
            if (response.statusCode < 200 || response.statusCode >= 300 || !data.ok) {
                throw error(typeof data.error === 'string' ? data.error : 'http_error',
                    data.message || data.error || ('浏览器请求失败：HTTP ' + response.statusCode), response.statusCode, data);
            }
            // loading 接口的字段直接位于响应根对象，其余接口提取指定字段。
            if (key === undefined) return data;
            if (!has(data, key)) throw error('invalid_response', 'vixBrowser 响应缺少 ' + key, response.statusCode, data);
            return data[key];
        }
        function state(limit = requestTimeout) {
            const result = request('/api/state', undefined, 'state', limit);
            if (!isObject(result) || typeof result.loading !== 'boolean'
                || (has(result, 'load_status') && !LOAD_STATUSES.includes(result.load_status))
                || (has(result, 'progress') && !isProgress(result.progress))) {
                throw error('invalid_response', 'vixBrowser state 格式不正确', 200, result);
            }
            return result;
        }
        function loading() {
            const result = request('/api/loading');
            if (!LOAD_STATUSES.includes(result.status) || typeof result.loading !== 'boolean'
                || !isProgress(result.progress) || !isNullableText(result.url)
                || !isNullableText(result.title) || !isNullableText(result.page_error)) {
                throw error('invalid_response', 'vixBrowser loading 格式不正确', 200, result);
            }
            // 查询成功与页面成功是两回事；保留 error 状态，便于脚本展示加载错误。
            return result;
        }
        function remaining(deadline, code) {
            const value = deadline - Date.now();
            if (value <= 0) throw error(code, '等待 vixBrowser 超时');
            return Math.min(requestTimeout, value);
        }
        function pause(deadline, period) {
            const value = deadline - Date.now();
            if (value > 0) sleep(Math.min(period, value));
        }
        function waitPage(deadline, period) {
            let previousUrl = null, idleCount = 0;
            while (true) {
                const current = state(remaining(deadline, 'page_load_timeout'));
                if (current.page_error != null || current.load_status === 'error') {
                    throw error('page_load_failed', '页面加载失败：' + (current.page_error ?? '浏览器报告加载失败'), 0, { state: current });
                }
                // 1.4 使用明确的完成状态；旧版缺少该字段时继续按 loading 判断。
                // progress 达到 100 也可能加载失败，不能据此判断成功。
                const complete = !current.loading && (current.load_status === undefined || current.load_status === 'complete');
                // 连续两次同一 URL 空闲，避免重定向过程中的短暂空闲被当成加载完成。
                idleCount = current.url && complete ? (current.url === previousUrl ? idleCount + 1 : 1) : 0;
                if (idleCount >= 2) return current;
                previousUrl = current.url;
                pause(deadline, period);
            }
        }
        function navigation(url, supplied = {}) {
            text(url, 'url');
            options(supplied, ['referer', 'ua', 'history_length'], '导航设置');
            return { url, ...(Object.keys(supplied).length ? settings(supplied) : {}) };
        }

        return Object.freeze({
            baseUrl,
            packageName: PACKAGE,
            start(waitTimeout = 10000) {
                const deadline = Date.now() + timeout(waitTimeout);
                if (baseUrl !== DEFAULT_URL) throw new TypeError('start 仅启动本机默认地址的浏览器；自定义地址请先自行启动服务');
                if (!utils.openApp(PACKAGE)) throw error('app_launch_failed', '无法启动 vixBrowser，请确认已安装 com.vixmobi.browser');
                let nextLaunch = Date.now() + 1000, foregroundReady = false;
                while (true) {
                    // 后台浏览器的 HTTP 服务仍可响应，必须先确认界面已切到前台。
                    // 与运行脚本共用无障碍权限；不用固定延时猜测 Activity 切换完成。
                    if (getCurrentRunningPkg() !== PACKAGE) {
                        foregroundReady = false;
                        if (Date.now() >= deadline) {
                            throw error('foreground_timeout', 'vixBrowser 未能切到前台，请确认屏幕已解锁、XpAuto 无障碍服务已开启，并允许后台弹出界面');
                        }
                        // HOME 等界面切换可能覆盖首次启动；只重发无 URL 的启动请求，不重新导航页面。
                        if (Date.now() >= nextLaunch) {
                            if (!utils.openApp(PACKAGE)) throw error('app_launch_failed', '无法将 vixBrowser 切到前台');
                            nextLaunch = Date.now() + 1000;
                        }
                        pause(deadline, pollInterval);
                        continue;
                    }
                    try {
                        const current = state(Math.min(1000, remaining(deadline, 'start_timeout')));
                        // 连续两轮前台且接口就绪，避免 HOME 动画期间旧窗口的短暂状态造成误判。
                        if (getCurrentRunningPkg() === PACKAGE) {
                            if (foregroundReady) return current;
                            foregroundReady = true;
                        } else foregroundReady = false;
                    }
                    catch (failure) {
                        foregroundReady = false;
                        // 启动只重试无副作用的状态读取，页面操作及页面 JS 从不自动重放。
                        if (failure.name !== 'VixBrowserError' || (failure.code !== 'transport_error' && failure.statusCode !== 503)) throw failure;
                    }
                    pause(deadline, pollInterval);
                }
            },
            getState: () => state(),
            getLoading: () => loading(),
            navigate: (url, supplied = {}) => request('/api/navigate', navigation(url, supplied), 'state'),
            open(url, supplied = {}) {
                options(supplied, ['referer', 'ua', 'history_length', 'timeout', 'pollInterval'], '打开页面选项');
                const { timeout: waitTimeout = 30000, pollInterval: period = pollInterval, ...pageSettings } = supplied;
                const deadline = Date.now() + timeout(waitTimeout);
                interval(period);
                request('/api/navigate', navigation(url, pageSettings), 'state', remaining(deadline, 'page_load_timeout'));
                return waitPage(deadline, period);
            },
            waitForPage(waitTimeout = 30000, period = pollInterval) {
                return waitPage(Date.now() + timeout(waitTimeout), interval(period));
            },
            executeJs: script => request('/api/js/execute', { script: text(script, 'script') }, 'result'),
            waitFor(script, waitTimeout = 30000, period = pollInterval) {
                text(script, 'script');
                const deadline = Date.now() + timeout(waitTimeout);
                interval(period);
                while (true) {
                    const result = request('/api/js/execute', { script }, 'result', remaining(deadline, 'condition_timeout'));
                    if (result) return result;
                    pause(deadline, period);
                }
            },
            back: () => request('/api/back', {}, 'state'),
            forward: () => request('/api/forward', {}, 'state'),
            refresh: () => request('/api/refresh', {}, 'state'),
            clearCache: () => request('/api/cache/clear', {}, 'cache_cleared'),
            getSettings: () => request('/api/settings', undefined, 'settings'),
            setSettings: value => request('/api/settings', settings(value), 'settings'),
            getUa: () => request('/api/ua', undefined, 'ua'),
            setUa: value => request('/api/ua', settings({ ua: value }), 'ua'),
            getReferer: () => request('/api/referer', undefined, 'referer'),
            setReferer: value => request('/api/referer', settings({ referer: value }), 'referer'),
            getHistoryLength: () => request('/api/history-length', undefined, 'history_length'),
            setHistoryLength: value => request('/api/history-length', settings({ history_length: value }), 'history_length'),
        });
    }
    return Object.freeze({ ...createClient(), createClient });
})();
