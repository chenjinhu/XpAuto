// Android 系统属性快捷入口；统一校验参数，具体执行及取消沿用 Shell 模块。
const __xpautoPropertyArgs = (name, root, timeoutMs) => {
    if (typeof name !== 'string' || /[\r\n]/.test(name) || !/^[A-Za-z0-9_-]+(?:\.[A-Za-z0-9_-]+)*$/.test(name)) {
        throw new TypeError('属性名只能包含字母、数字、下划线、连字符及分隔用的单个点');
    }
    if (typeof root !== 'boolean') throw new TypeError('root 必须为布尔值');
    if (!Number.isInteger(timeoutMs) || timeoutMs < 1 || timeoutMs > 3600000) {
        throw new RangeError('timeoutMs 必须为 1..3600000 的整数');
    }
};

// 即使之前调用过 shell.su()，默认读取仍使用普通应用权限。
const getprop = (name, defaultValue = '', root = false, timeoutMs = 60000) => {
    __xpautoPropertyArgs(name, root, timeoutMs);
    if (typeof defaultValue !== 'string') throw new TypeError('defaultValue 必须为字符串');
    return shell.getprop(name, defaultValue, root, timeoutMs);
};

// Android 属性最终存储为字符串；允许直接传入有限数字和布尔值。
const setprop = (name, value, root = true, timeoutMs = 60000) => {
    __xpautoPropertyArgs(name, root, timeoutMs);
    if (!['string', 'number', 'boolean'].includes(typeof value) ||
        (typeof value === 'number' && !Number.isFinite(value))) {
        throw new TypeError('value 必须为字符串、有限数字或布尔值');
    }
    value = String(value);
    if (value.includes('\0')) throw new TypeError('属性值不能包含 NUL 字符');
    return shell.setprop(name, value, root, timeoutMs);
};
