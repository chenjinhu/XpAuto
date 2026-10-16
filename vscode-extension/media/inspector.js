/* 网页只负责展示与选择，抓取、文件读写和剪贴板均交给插件主进程。 */
(function () {
    'use strict';
    const api = acquireVsCodeApi(), model = window.XpAutoUi;
    const $ = id => document.getElementById(id);
    const post = (type, data = {}) => api.postMessage({ type, ...data });
    let snapshot, byId = new Map(), selected, fields = [], expanded = new Set(), queryMatches = new Set();
    let xml, property = 'text', canvas, pendingPointer, historyEntries = [], historySelected;
    const make = (tag, className, text) => {
        const element = document.createElement(tag);
        if (className) element.className = className;
        if (text !== undefined) element.textContent = text;
        return element;
    };
    const notice = (message, error = false) => { $('notice').textContent = message; $('notice').classList.toggle('error', error); };
    const label = node => (node.text || node.desc || node.id || node.clz || '(无名称)').slice(0, 100);
    const selectedNode = () => byId.get(selected);
    function busy(value) { $('capture').disabled = value; $('capture').textContent = value ? '正在抓取…' : '获取节点'; }
    function copy(kind) {
        if (selected) post('copy', { snapshotId: snapshot.id, nodeId: selected, kind, property, fields });
        $('context-menu').hidden = true;
    }
    function select(nodeId, scroll = true) {
        const node = byId.get(nodeId);
        if (!node) return;
        selected = nodeId; fields = model.defaultFields(snapshot, node);
        let parent = byId.get(node.parentId);
        while (parent) { expanded.add(parent.nodeId); parent = byId.get(parent.parentId); }
        renderTree(); renderProperties(); renderCode(); renderBoxes();
        if (scroll) $('tree').querySelector('[aria-selected="true"]')?.scrollIntoView({ block: 'nearest' });
        $('parent').disabled = node.parentId === null; $('child').disabled = !node.children.length;
        notice(snapshot.truncated ? '本次节点树已截断，匹配数量可能不完整。' :
            '已选择节点。选择器可搜索当前无障碍窗口；勾选属性调整条件，右键复制代码与可执行 XPath。');
    }
    function renderTree() {
        const filter = $('filter').value.trim().toLowerCase(), visible = new Set();
        if (filter) {
            for (const node of snapshot.nodes) {
                if (!['text', 'id', 'clz', 'pkg', 'desc'].some(key => node[key].toLowerCase().includes(filter))) continue;
                let current = node;
                while (current && !visible.has(current.nodeId)) { visible.add(current.nodeId); current = byId.get(current.parentId); }
            }
        }
        const fragment = document.createDocumentFragment();
        function append(id) {
            const node = byId.get(id);
            if (filter && !visible.has(id)) return;
            const row = make('div', 'tree-row'); row.dataset.nodeId = id;
            row.style.paddingLeft = (8 + node.depth * 14) + 'px';
            row.classList.toggle('selected', id === selected); row.classList.toggle('match', queryMatches.has(id));
            row.setAttribute('role', 'treeitem'); row.setAttribute('aria-selected', String(id === selected));
            row.setAttribute('aria-level', String(node.depth + 1)); row.tabIndex = id === selected ? 0 : -1;
            const open = filter || expanded.has(id);
            if (node.children.length) row.setAttribute('aria-expanded', String(Boolean(open)));
            const toggle = make('button', 'toggle', node.children.length ? (open ? '▾' : '▸') : '·');
            toggle.tabIndex = -1; toggle.setAttribute('aria-label', open ? '收起子节点' : '展开子节点');
            toggle.onclick = event => { event.stopPropagation(); if (expanded.has(id)) expanded.delete(id); else expanded.add(id); renderTree(); };
            const bounds = node.bounds;
            row.append(toggle, make('span', 'tree-label', node.clz.split('.').pop() + ' · ' + label(node) + ` [${bounds.join(',')}]`));
            row.title = `${node.clz}\n${node.id}\n${node.text || node.desc}`;
            row.onclick = () => select(id, false);
            row.onkeydown = event => {
                if (event.key === 'ArrowRight') { expanded.add(id); renderTree(); }
                else if (event.key === 'ArrowLeft') { if (expanded.has(id)) { expanded.delete(id); renderTree(); } else if (node.parentId) select(node.parentId); }
                else if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
                    const next = event.key === 'ArrowDown' ? row.nextElementSibling : row.previousElementSibling;
                    if (next) select(next.dataset.nodeId);
                } else return;
                event.preventDefault(); $('tree').querySelector('[aria-selected="true"]')?.focus();
            };
            fragment.append(row);
            if (open) node.children.forEach(append);
        }
        snapshot.windows.forEach(window => append(window.rootId));
        $('tree').replaceChildren(fragment);
    }
    function renderProperties() {
        const node = selectedNode(), table = make('table');
        const head = make('thead'), heading = make('tr');
        heading.append(make('th', '', '属性'), make('th', '', '值')); head.append(heading); table.append(head);
        for (const [key, value] of Object.entries(model.properties(snapshot, node))) {
            const row = make('tr', 'property-row'), name = make('td'), cell = make('td', 'value', String(value));
            row.dataset.property = key;
            if (model.selectorFields.includes(key)) {
                const check = make('input'); check.type = 'checkbox'; check.checked = fields.includes(key);
                check.disabled = node[key + 'Truncated'] === true;
                check.setAttribute('aria-label', '使用 ' + key + ' 作为选择条件');
                check.title = check.disabled ? '此属性已截断，不能用于精确匹配' : '加入代码选择条件';
                check.onchange = () => { fields = check.checked ? [...fields, key] : fields.filter(field => field !== key); renderCode(); };
                name.append(check);
            }
            name.append(document.createTextNode(key)); row.append(name, cell);
            cell.ondblclick = () => { property = key; copy('value'); };
            row.oncontextmenu = event => {
                event.preventDefault(); property = key;
                table.querySelector('.selected')?.classList.remove('selected'); row.classList.add('selected');
                const menu = $('context-menu'); menu.hidden = false;
                menu.style.left = Math.max(0, Math.min(event.clientX, innerWidth - menu.offsetWidth - 8)) + 'px';
                menu.style.top = Math.max(0, Math.min(event.clientY, innerHeight - menu.offsetHeight - 8)) + 'px';
                menu.querySelector('button').focus();
            };
            table.append(row);
        }
        $('properties').replaceChildren(table); $('properties').scrollTop = 0;
    }
    function renderCode() {
        const node = selectedNode(); if (!node) return;
        $('code').value = model.generateCode(snapshot, node, $('code-kind').value, fields);
        $('copy-code').disabled = false;
        const count = model.matchingNodes(snapshot, node, fields).length;
        $('match-count').textContent = `当前窗口匹配 ${count} 个`;
        $('match-count').title = '基于本次快照估算；界面变化后需重新抓取。';
    }
    function renderBoxes() {
        $('boxes').replaceChildren(); if (!snapshot) return;
        const ids = [...queryMatches].slice(0, 300); if (selected) ids.push(selected);
        for (const id of new Set(ids)) {
            const node = byId.get(id); if (!node) continue;
            const [l, t, r, b] = node.bounds, w = snapshot.screen.width, h = snapshot.screen.height;
            const left = Math.max(0, l), top = Math.max(0, t), right = Math.min(w, r), bottom = Math.min(h, b);
            if (right <= left || bottom <= top) continue;
            const box = make('div', 'box' + (id === selected ? ' selected' : ''));
            box.style.left = (left / w * 100) + '%'; box.style.top = (top / h * 100) + '%';
            box.style.width = ((right - left) / w * 100) + '%'; box.style.height = ((bottom - top) / h * 100) + '%';
            $('boxes').append(box);
        }
    }
    function resize() {
        if (!snapshot) return;
        const { width, height } = snapshot.screen, viewport = $('viewport');
        const scale = $('zoom').value === 'fit' ? Math.max(.01, Math.min((viewport.clientWidth - 32) / width, (viewport.clientHeight - 32) / height)) : Number($('zoom').value);
        $('stage').style.width = width * scale + 'px'; $('stage').style.height = height * scale + 'px';
    }
    function coordinates(event) {
        const rect = $('stage').getBoundingClientRect();
        return { x: Math.max(0, Math.min(snapshot.screen.width - 1, Math.floor((event.clientX - rect.left) / rect.width * snapshot.screen.width))),
            y: Math.max(0, Math.min(snapshot.screen.height - 1, Math.floor((event.clientY - rect.top) / rect.height * snapshot.screen.height))) };
    }
    // XPath 只查询本地 XML 快照，不会触发手机操作或执行 JavaScript。
    function xmlDocument() {
        const result = document.implementation.createDocument(null, 'hierarchy');
        function append(parent, id) {
            const node = byId.get(id), element = result.createElement('node');
            element.setAttribute('nid', node.nodeId);
            for (const [key, value] of Object.entries(node)) {
                if (['string', 'number', 'boolean'].includes(typeof value)) element.setAttribute(key, String(value));
            }
            const [left, top, right, bottom] = node.bounds;
            element.setAttribute('bounds', `[${left},${top}][${right},${bottom}]`);
            element.setAttribute('width', String(right - left)); element.setAttribute('height', String(bottom - top));
            parent.appendChild(element); node.children.forEach(child => append(element, child));
        }
        for (const window of snapshot.windows) {
            const element = result.createElement('window'); element.setAttribute('id', String(window.id));
            result.documentElement.appendChild(element); append(element, window.rootId);
        }
        return result;
    }
    function query() {
        if (!snapshot) return notice('请先获取节点或载入快照');
        const expression = $('xpath').value.trim(); if (!expression) return notice('请输入 XPath 表达式');
        try {
            xml ??= xmlDocument();
            const result = xml.evaluate(expression, xml, null, XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null);
            queryMatches = new Set();
            for (let i = 0; i < result.snapshotLength; i++) {
                const item = result.snapshotItem(i), id = item.nodeType === 1 ? item.getAttribute('nid') : item.ownerElement?.getAttribute('nid');
                if (byId.has(id)) queryMatches.add(id);
            }
            if (queryMatches.size) select(queryMatches.values().next().value);
            renderTree(); renderBoxes();
            notice(`XPath 匹配 ${queryMatches.size} 个节点` + (queryMatches.size > 300 ? '，截图最多同时标记 300 个' : ''));
        } catch (error) { notice('XPath 查询失败：' + error.message, true); }
    }
    function renderHistory() {
        const filter = $('history-filter').value.trim().toLowerCase();
        const entries = historyEntries.filter(entry => [entry.title, entry.serial, entry.capturedAt, new Date(entry.capturedAt).toLocaleString()].join(' ').toLowerCase().includes(filter));
        $('history').replaceChildren(...entries.map(entry => {
            const button = make('button', 'history-item' + (entry.id === historySelected ? ' active' : ''));
            const date = new Date(entry.capturedAt).toLocaleString();
            button.append(make('strong', '', entry.title), make('small', '', date + ` · ${entry.count} 个节点`));
            button.title = entry.title + '\n' + date + '\n' + entry.serial;
            button.onclick = () => post('selectSnapshot', { id: entry.id });
            return button;
        }));
        if (!entries.length) $('history').append(make('div', 'empty', filter ? '没有匹配的快照' : '暂无快照'));
    }
    function state(message) {
        busy(message.busy); $('history-count').textContent = `${message.history.length} / 10`;
        $('single-tab').checked = message.singleTab === true;
        historyEntries = message.history; historySelected = message.snapshot?.id; renderHistory();
        if (!message.snapshot || snapshot?.id === message.snapshot.id) return;
        snapshot = message.snapshot; byId = model.index(snapshot); selected = undefined; queryMatches.clear(); xml = undefined; canvas = undefined;
        expanded = new Set(snapshot.nodes.filter(node => node.depth < 2).map(node => node.nodeId));
        $('filter').value = ''; $('hits').replaceChildren(make('option', '', '点击截图后选择'));
        $('empty-screen').hidden = true; $('stage').hidden = false; $('save').disabled = false;
        const active = snapshot.windows.find(window => window.active) || snapshot.windows[0];
        $('screen-title').textContent = byId.get(active.rootId).pkg || '屏幕预览';
        $('capture-info').textContent = `${snapshot.screen.width} × ${snapshot.screen.height} · ${snapshot.nodes.length} 个节点 · ${snapshot.windows.length} 个窗口`;
        $('screen').src = 'data:image/png;base64,' + snapshot.screenshot.data;
        resize(); select(active.rootId);
        notice(snapshot.truncated ? '本次节点树达到数量、文本或时间上限，已保留部分节点；匹配数量可能不完整。' : '抓取完成。点击截图或节点树查看属性；右键属性可复制值、代码与 XPath。');
    }
    $('screen').onload = () => {
        if (!snapshot) return;
        canvas = document.createElement('canvas'); canvas.width = snapshot.screen.width; canvas.height = snapshot.screen.height;
        canvas.getContext('2d', { willReadFrequently: true }).drawImage($('screen'), 0, 0); resize();
    };
    $('screen').onerror = () => notice('PNG 截图无法解码，请重新抓取或检查快照文件。', true);
    $('stage').onclick = event => {
        if (!snapshot) return;
        const point = coordinates(event), hits = model.hitTest(snapshot, point.x, point.y);
        $('hits').replaceChildren(...hits.map(node => { const option = make('option', '', label(node)); option.value = node.nodeId; return option; }));
        if (hits.length) select(hits[0].nodeId); else notice('此坐标没有可见的无障碍节点。');
    };
    $('stage').onmousemove = event => {
        if (!snapshot) return;
        pendingPointer = coordinates(event);
        if ($('stage').dataset.frame) return;
        $('stage').dataset.frame = 'pending';
        requestAnimationFrame(() => {
            delete $('stage').dataset.frame;
            const { x, y } = pendingPointer;
            const rgb = canvas?.getContext('2d').getImageData(x, y, 1, 1).data;
            const color = rgb ? (rgb[3] << 24) | (rgb[0] << 16) | (rgb[1] << 8) | rgb[2] : 0;
            $('coordinates').textContent = `坐标:${x},${y}` + (rgb ? ` 颜色:${color} hex:#` + [...rgb].slice(0, 3).map(n => n.toString(16).padStart(2, '0')).join('').toUpperCase() : '');
        });
    };
    $('history-filter').oninput = renderHistory;
    $('single-tab').onchange = () => post('singleTab', { value: $('single-tab').checked });
    $('hits').onchange = () => select($('hits').value);
    $('parent').onclick = () => select(selectedNode()?.parentId); $('child').onclick = () => select(selectedNode()?.children[0]);
    $('filter').oninput = () => { if (snapshot) renderTree(); };
    $('zoom').onchange = resize; new ResizeObserver(resize).observe($('viewport'));
    $('capture').onclick = () => post('capture'); $('save').onclick = () => post('save'); $('load').onclick = () => post('load');
    $('code-kind').onchange = renderCode; $('copy-code').onclick = () => copy($('code-kind').value);
    $('test-xpath').onclick = query; $('xpath').onkeydown = event => { if (event.key === 'Enter') query(); };
    $('clear-xpath').onclick = () => { queryMatches.clear(); if (snapshot) renderTree(); renderBoxes(); notice('已清除 XPath 标记'); };
    $('context-menu').querySelectorAll('button').forEach(button => { button.onclick = () => copy(button.dataset.copy); });
    document.addEventListener('pointerdown', event => { if (!$('context-menu').contains(event.target)) $('context-menu').hidden = true; });
    document.addEventListener('keydown', event => { if (event.key === 'Escape') $('context-menu').hidden = true; });
    window.addEventListener('message', ({ data }) => {
        if (data.type === 'state') state(data);
        else if (data.type === 'busy') busy(data.value);
        else if (data.type === 'error' || data.type === 'notice') notice(data.message, data.type === 'error');
    });
    post('ready');
})();
