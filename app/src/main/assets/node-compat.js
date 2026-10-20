// EasyClick 属性模式使用独立的 JS 外观对象，原生节点与方法模式仍共用同一套查询和动作。
const __xpautoNodeCompat = (() => {
    let mode = 'xpauto';
    const originals = new WeakMap(), wrappers = new WeakMap();
    const nodeProperties = new Set(['id','text','desc','clz','pkg','bounds','visibleBounds','boundsInParent',
        'visible','checkable','checked','clickable','longClickable','scrollable','focusable','focused',
        'enabled','selected','editable','multiLine','password','depth','index','drawingOrder',
        'row','column','rowSpan','columnSpan','rowCount','columnCount']);
    function S() {}
    function NodeInfo() {}
    const unwrap = value => value && (typeof value === 'object' || typeof value === 'function') ? originals.get(value) || value : value;
    function wrap(value, force = false) {
        if (value == null || (!force && mode !== 'easyclick')) return value;
        if (Array.isArray(value)) return value.map(item => wrap(item, true));
        if (typeof value !== 'object') return value;
        if (originals.has(value)) return value;
        if (wrappers.has(value)) return wrappers.get(value);
        const node = typeof value.info === 'function' && typeof value.bounds === 'function';
        const selector = !node && typeof value.getOneNodeInfo === 'function';
        const collection = !node && !selector && typeof value.size === 'function' && typeof value.get === 'function';
        if (!node && !selector && !collection) return value;
        const target = node ? new NodeInfo() : selector ? new S() : {};
        const proxy = new Proxy(target, {
            get(_, key) {
                if (key === 'constructor') return node ? NodeInfo : selector ? S : Object;
                if (selector && key === 'attr') return {expression:String(value)};
                if (node && key === 'windowId') return value.info()?.windowId ?? -1;
                if (node && nodeProperties.has(key)) {
                    const result = value[key]();
                    // 矩形转为普通 JS 数据，方便复制、JSON 和区域点击。
                    return key.toLowerCase().includes('bounds') && result ? {left:result.left,top:result.top,right:result.right,bottom:result.bottom} : result;
                }
                if (key === 'toJSON') return () => node ? value.info() : selector ? String(value) : Array.from({length:value.size()}, (_, i) => wrap(value.get(i), true));
                if (key === 'toString') return () => String(value);
                if (key === Symbol.toStringTag) return node ? 'NodeInfo' : selector ? 'S' : 'UiCollection';
                const member = value[key];
                return typeof member === 'function' ? (...args) => wrap(member.apply(value, args.map(unwrap)), true) : member;
            },
            set() { throw new TypeError('节点与选择器属性为只读，请使用节点动作修改界面'); },
        });
        originals.set(proxy, value); wrappers.set(value, proxy);
        return proxy;
    }
    return {wrap, unwrap, set(value) {
        if (value !== 'easyclick' && value !== 'xpauto') throw new TypeError('节点模式为 easyclick 或 xpauto');
        mode = value; return true;
    }};
})();
const setNodeCompatibility = mode => __xpautoNodeCompat.set(mode);
const __xpautoUnwrapNode = value => __xpautoNodeCompat.unwrap(value);

// 显式转换也提供准确的属性类型提示，不要求切换整个脚本的查询模式。
const asEasyClick = value => __xpautoNodeCompat.wrap(value, true);
function Rect(left = 0, top = 0, right = 0, bottom = 0) {
    this.left = left; this.top = top; this.right = right; this.bottom = bottom;
}
Rect.prototype.width = function() { return this.right - this.left; };
Rect.prototype.height = function() { return this.bottom - this.top; };
Rect.prototype.centerX = function() { return (this.left + this.right) / 2; };
Rect.prototype.centerY = function() { return (this.top + this.bottom) / 2; };
