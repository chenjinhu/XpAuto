// 使用普通 JS 值格式化日志，避免把对象显示为不可读的 Java 代理地址。
const { logd, logi, logw, loge, toast, toast1, toast2, setLogLevel, clearLog } = (() => {
    function text(value) {
        if (typeof value === 'string') return value;
        if (value instanceof Error) return value.stack || String(value);
        if (value === null || typeof value !== 'object') return String(value);
        try {
            const seen = new WeakSet();
            const json = JSON.stringify(value, (key, item) => {
                if (typeof item === 'bigint') return String(item) + 'n';
                if (item && typeof item === 'object') {
                    if (seen.has(item)) return '[Circular]';
                    seen.add(item);
                }
                return item;
            });
            // 选择器等对象可通过 toJSON 返回说明文字；顶层直接显示，嵌套时仍保留 JSON 结构。
            return typeof json === 'string' && json.startsWith('"') ? JSON.parse(json) : json ?? String(value);
        } catch (_) {
            try { return String(value); } catch (_) { return '[无法转换的值]'; }
        }
    }
    function format(args) {
        if (!args.length) return '';
        const [message, ...values] = args;
        let index = 0;
        const result = typeof message === 'string'
            ? message.replace(/\{\}/g, () => index < values.length ? text(values[index++]) : '{}') : text(message);
        return [result, ...values.slice(index).map(text)].join(' ');
    }
    const write = level => (...args) => {
        if (__xpautoLog.enabled(level)) __xpautoLog.write(level, format(args));
    };
    return {
        logd: write('debug'), logi: write('info'), logw: write('warn'), loge: write('error'),
        toast: (message, extra = null) => __xpautoLog.toast(text(message), JSON.stringify(extra), 2000),
        // 参考文档未区分两个扩展方法的外观；XpAuto 分别提供系统短提示和长提示。
        toast1: message => __xpautoLog.toast(text(message), 'null', 2000),
        toast2: message => __xpautoLog.toast(text(message), 'null', 3500),
        setLogLevel: (level, displayToast = true) => __xpautoLog.setLogLevel(String(level), Boolean(displayToast)),
        clearLog: (lines = -1) => {
            if (!Number.isInteger(lines) || lines < -1 || lines > 2147483647) throw new RangeError('clearLog 行数必须为 -1 或非负整数');
            __xpautoLog.clearLog(lines);
        },
    };
})();
