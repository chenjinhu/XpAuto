/* 节点快照的校验、命中测试和代码生成由扩展与网页共用，避免两端规则不同。 */
(function (root, factory) {
    if (typeof module === 'object' && module.exports) module.exports = factory();
    else root.XpAutoUi = factory();
})(typeof globalThis === 'object' ? globalThis : this, function () {
    'use strict';
    const strings = ['id', 'text', 'desc', 'clz', 'pkg'];
    const flags = ['clickable', 'longClickable', 'checkable', 'checked', 'enabled', 'focusable', 'focused',
        'selected', 'scrollable', 'editable', 'visible', 'password', 'multiLine', 'textTruncated', 'descTruncated'];
    const selectorFields = ['id', 'text', 'desc', 'clz', 'pkg', 'bounds', 'clickable', 'longClickable',
        'checkable', 'checked', 'selected', 'enabled', 'scrollable', 'editable', 'multiLine',
        'visible', 'focusable', 'focused', 'password', 'drawingOrder', 'depth', 'index'];
    const fail = message => { throw new Error('节点快照无效：' + message); };
    const integer = (value, min, max) => Number.isInteger(value) && value >= min && value <= max;
    const boundedString = (value, max) => typeof value === 'string' && value.length <= max;
    function rectangle(value) {
        return Array.isArray(value) && value.length === 4 && value.every(n => integer(n, -1000000, 1000000))
            && value[2] >= value[0] && value[3] >= value[1];
    }
    function validateSnapshot(input) {
        if (!input || input.format !== 'xpauto.ui-snapshot' || input.version !== 1) fail('不支持的文件格式或版本');
        if (!boundedString(input.id, 128) || !boundedString(input.serial, 256) || !Number.isFinite(Date.parse(input.capturedAt))) fail('缺少抓取信息');
        const screen = input.screen;
        if (!screen || !integer(screen.width, 1, 16000) || !integer(screen.height, 1, 16000)
            || screen.width * screen.height > 40000000 || !integer(screen.rotation, 0, 3)) fail('屏幕尺寸不正确');
        if (!Array.isArray(input.nodes) || input.nodes.length < 1 || input.nodes.length > 5000
            || !Array.isArray(input.windows) || input.windows.length < 1 || input.windows.length > 128) fail('节点或窗口数量不正确');
        const byId = new Map(); let textSize = 0;
        const nodes = input.nodes.map(raw => {
            if (!raw || !boundedString(raw.nodeId, 80) || !raw.nodeId || byId.has(raw.nodeId)) fail('节点标识重复或缺失');
            if (raw.parentId !== null && !boundedString(raw.parentId, 80)) fail('父节点标识错误');
            if (!rectangle(raw.bounds) || !integer(raw.depth, 0, 80) || !integer(raw.index, 0, 100000)
                || !integer(raw.windowId, -1, 1000000000) || !integer(raw.drawingOrder, 0, 100000)) fail('节点位置或层级错误');
            if (!Array.isArray(raw.children) || raw.children.length > 5000 || !raw.children.every(id => boundedString(id, 80))) fail('子节点列表错误');
            const node = { nodeId: raw.nodeId, parentId: raw.parentId, windowId: raw.windowId, index: raw.index,
                depth: raw.depth, drawingOrder: raw.drawingOrder, bounds: [...raw.bounds], children: [...raw.children] };
            for (const field of strings) {
                if (!boundedString(raw[field], 4096)) fail('节点属性过长或类型错误');
                textSize += raw[field].length; node[field] = raw[field];
            }
            for (const field of flags) {
                if (raw[field] !== undefined && typeof raw[field] !== 'boolean') fail('布尔属性类型错误');
                node[field] = raw[field] === true;
            }
            byId.set(node.nodeId, node);
            return node;
        });
        if (textSize > 500000) fail('属性文本超过容量限制');
        const windowIds = new Set();
        const windows = input.windows.map(raw => {
            if (!raw || !integer(raw.id, -1, 1000000000) || windowIds.has(raw.id) || !boundedString(raw.title, 4096)
                || !rectangle(raw.bounds) || !integer(raw.layer, -100000, 100000) || !integer(raw.type, 0, 100)
                || typeof raw.active !== 'boolean' || typeof raw.focused !== 'boolean') fail('窗口属性错误');
            const root = byId.get(raw.rootId);
            if (!root || root.parentId !== null || root.windowId !== raw.id) fail('窗口根节点错误');
            windowIds.add(raw.id);
            return { id: raw.id, title: raw.title, layer: raw.layer, type: raw.type, active: raw.active,
                focused: raw.focused, rootId: root.nodeId, bounds: [...raw.bounds] };
        });
        const visited = new Set();
        function visit(id, parent, depth, windowId) {
            const node = byId.get(id);
            if (!node || visited.has(id) || depth > 80 || node.parentId !== parent || node.depth !== depth || node.windowId !== windowId) fail('节点树包含循环、重复引用或断开的父子关系');
            visited.add(id);
            for (const child of node.children) visit(child, id, depth + 1, windowId);
        }
        for (const window of windows) visit(window.rootId, null, 0, window.id);
        if (visited.size !== nodes.length) fail('存在没有归属窗口的节点');
        const screenshot = input.screenshot;
        if (!screenshot || screenshot.mimeType !== 'image/png' || typeof screenshot.data !== 'string'
            || screenshot.data.length > 22 * 1024 * 1024 || !/^iVBORw0KGgo[A-Za-z0-9+/]*={0,2}$/.test(screenshot.data)) fail('截图必须是容量限制内的 PNG');
        return { format: input.format, version: 1, id: input.id, serial: input.serial, capturedAt: input.capturedAt,
            screen: { width: screen.width, height: screen.height, rotation: screen.rotation },
            durationMs: integer(input.durationMs, 0, 120000) ? input.durationMs : 0, truncated: input.truncated === true,
            nodes, windows, screenshot: { mimeType: 'image/png', data: screenshot.data } };
    }
    function index(snapshot) { return new Map(snapshot.nodes.map(node => [node.nodeId, node])); }
    function hitTest(snapshot, x, y) {
        const layers = new Map(snapshot.windows.map(window => [window.id, window.layer]));
        const area = node => (node.bounds[2] - node.bounds[0]) * (node.bounds[3] - node.bounds[1]);
        return snapshot.nodes.filter(node => node.visible && node.bounds[0] <= x && x < node.bounds[2]
            && node.bounds[1] <= y && y < node.bounds[3]).sort((a, b) =>
            layers.get(b.windowId) - layers.get(a.windowId) || b.depth - a.depth || b.drawingOrder - a.drawingOrder || area(a) - area(b));
    }
    function matches(node, target, fields) {
        return fields.every(field => field === 'bounds' ? node.bounds.every((value, i) => value === target.bounds[i]) : node[field] === target[field]);
    }
    function normalizedFields(node, fields) {
        const result = selectorFields.filter(field => fields.includes(field) && !node[field + 'Truncated']);
        if (!['id', 'text', 'desc', 'clz'].some(field => result.includes(field))) result.unshift('clz');
        return result;
    }
    function matchingNodes(snapshot, node, fields) {
        return snapshot.nodes.filter(value => matches(value, node, normalizedFields(node, fields)));
    }
    function defaultFields(snapshot, node) {
        const fields = [];
        for (const field of ['id', 'text', 'desc', 'clz']) {
            if (!node[field] || node[field + 'Truncated']) continue;
            fields.push(field);
            if (matchingNodes(snapshot, node, fields).length === 1) break;
        }
        if (!fields.length) fields.push('clz');
        if (node.pkg) fields.push('pkg');
        if (matchingNodes(snapshot, node, fields).length > 1) fields.push('bounds');
        return fields;
    }
    const literal = value => JSON.stringify(value).replace(/\u2028/g, '\\u2028').replace(/\u2029/g, '\\u2029');
    function selector(node, fields) {
        fields = normalizedFields(node, fields);
        let first = ['id', 'text', 'desc', 'clz'].find(field => fields.includes(field));
        // 为纯状态或边界条件补上类名，避免生成过于宽泛的查询。
        if (!first) { first = 'clz'; fields.unshift(first); }
        const ordered = [first, ...fields.filter(field => field !== first)];
        return ordered.map(field => (field === 'pkg' ? 'packageName' : field) + '(' +
            (field === 'bounds' ? node.bounds.join(', ') : literal(node[field])) + ')').join('.');
    }
    function generateCode(snapshot, node, kind, fields = defaultFields(snapshot, node)) {
        const expression = selector(node, fields);
        if (kind === 'selector') return expression;
        if (kind === 'single') return `const node = ${expression}.getOneNodeInfo();\nif (node && node.exists()) {\n    logd(node.text());\n}`;
        if (kind === 'multiple') return `const nodes = ${expression}.find();\nfor (let i = 0; i < nodes.size(); i++) {\n    const node = nodes.get(i);\n    logd(node.text());\n}`;
        throw new Error('未知的代码生成方式');
    }
    function xpathLiteral(value) {
        if (!value.includes("'")) return "'" + value + "'";
        if (!value.includes('"')) return '"' + value + '"';
        return 'concat(' + value.split("'").map(part => "'" + part + "'").join(', "\'", ') + ')';
    }
    function absoluteXPath(snapshot, node) {
        const byId = index(snapshot), segments = [];
        let current = node;
        while (current.parentId !== null) {
            const parent = byId.get(current.parentId);
            segments.unshift('node[' + (parent.children.indexOf(current.nodeId) + 1) + ']');
            current = parent;
        }
        return '/hierarchy/window[@id=' + xpathLiteral(String(node.windowId)) + ']/node[1]' + (segments.length ? '/' + segments.join('/') : '');
    }
    function relativeXPath(snapshot, node) {
        const fields = ['id', 'text', 'desc', 'clz', 'pkg'].filter(field => node[field] && !node[field + 'Truncated']);
        const expression = '//node' + (fields.length ? '[' + fields.map(field => '@' + field + '=' + xpathLiteral(node[field])).join(' and ') + ']' : '');
        const matches = snapshot.nodes.filter(value => fields.every(field => value[field] === node[field]));
        return matches.length > 1 ? '(' + expression + ')[' + (matches.findIndex(value => value.nodeId === node.nodeId) + 1) + ']' : expression;
    }
    function properties(snapshot, node) {
        const [left, top, right, bottom] = node.bounds;
        return { xpath: absoluteXPath(snapshot, node), relativeXpath: relativeXPath(snapshot, node), nid: node.nodeId,
            index: node.index, depth: node.depth, drawingOrder: node.drawingOrder,
            text: node.text, id: node.id, clz: node.clz, pkg: node.pkg, desc: node.desc,
            bounds: `[${left},${top}][${right},${bottom}]`, center: `${Math.floor((left + right) / 2)},${Math.floor((top + bottom) / 2)}`,
            width: right - left, height: bottom - top, windowId: node.windowId,
            ...Object.fromEntries(flags.map(field => [field, node[field]])) };
    }
    return { validateSnapshot, index, hitTest, selectorFields, defaultFields, matchingNodes,
        generateCode, xpathLiteral, absoluteXPath, relativeXPath, properties };
});
