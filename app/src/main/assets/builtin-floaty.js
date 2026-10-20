// 内置窗口与 floaty.showFloatXml 的业务窗口共存，配置不会创建第二套日志缓存。
const __xpautoFloatOptions = (value, log) => {
    if (!value || typeof value !== 'object' || Array.isArray(value)) throw new TypeError('窗口配置必须为对象');
    const allowed = log ? ['x','y','w','h','textSize','backgroundColor','title','showTitle','showCloseBtn'] : ['x','y','backgroundColor'];
    for (const key of Object.keys(value)) {
        if (!allowed.includes(key)) throw new TypeError('不支持的窗口配置：' + key);
        const item = value[key];
        if (['x','y','w','h','textSize'].includes(key)) {
            if (!Number.isFinite(item) || Math.abs(item) > 100000) throw new RangeError(key + ' 必须为有效数值');
            if (['w','h','textSize'].includes(key) && item <= 0) throw new RangeError(key + ' 必须大于 0');
            if (key === 'textSize' && item > 100) throw new RangeError('textSize 不能超过 100sp');
        } else if (key === 'backgroundColor') {
            if (typeof item !== 'string' || !/^#(?:[a-f\d]{6}|[a-f\d]{8})$/i.test(item)) throw new TypeError('颜色使用 #RRGGBB 或 #AARRGGBB');
        } else if (key === 'title') { if (typeof item !== 'string') throw new TypeError('title 必须为字符串'); }
        else if (typeof item !== 'boolean') throw new TypeError(key + ' 必须为布尔值');
    }
    return JSON.stringify(value);
};
const hasFloatViewPermission = () => __xpautoBuiltinFloat.hasPermission();
const showLogWindow = () => __xpautoBuiltinFloat.command('showLog', '{}');
const closeLogWindow = () => __xpautoBuiltinFloat.command('hideLog', '{}');
const showCtrlWindow = () => __xpautoBuiltinFloat.command('showCtrl', '{}');
const closeCtrlWindow = () => __xpautoBuiltinFloat.command('hideCtrl', '{}');
const setCtrlViewSizeEx = value => __xpautoBuiltinFloat.command('configureCtrl', __xpautoFloatOptions(value, false));
const setLogViewSizeEx = value => __xpautoBuiltinFloat.command('configureLog', __xpautoFloatOptions(value, true));
const setLogViewSize = (w,h,textSize=12,backgroundColor='#ffffff') => setLogViewSizeEx({w,h,textSize,backgroundColor});
