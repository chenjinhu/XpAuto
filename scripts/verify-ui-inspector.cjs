// 使用真实抓取快照在 Chromium 中验证网页交互；只启动独立测试浏览器。
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const { chromium } = require(process.env.XPAUTO_PLAYWRIGHT || 'playwright');
const root = path.resolve(__dirname, '../vscode-extension');
const snapshot = JSON.parse(fs.readFileSync(process.env.XPAUTO_SNAPSHOT || '/tmp/xpauto-capture.json', 'utf8'));
const sandbox = { module: { exports: {} }, require: () => ({ Uri: { joinPath: (_, folder, name) => '/' + folder + '/' + name } }) };
vm.runInNewContext(fs.readFileSync(path.join(root, 'src/inspector-html.js'), 'utf8'), sandbox);
const html = sandbox.module.exports.inspectorHtml({ cspSource: "'self'", asWebviewUri: value => value }, '/', 'test-nonce');
const server = http.createServer((req, res) => {
    if (req.url === '/') { res.setHeader('content-type', 'text/html'); return res.end(html); }
    if (!/^\/media\/(inspector(-model)?\.js|inspector\.css)$/.test(req.url)) { res.writeHead(404); return res.end(); }
    res.setHeader('content-type', req.url.endsWith('.css') ? 'text/css' : 'application/javascript');
    res.end(fs.readFileSync(path.join(root, req.url)));
});
(async () => {
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    const browser = await chromium.launch({ channel: 'chrome', headless: true });
    try {
        const page = await browser.newPage({ viewport: { width: 1380, height: 920 } });
        const errors = []; page.on('pageerror', error => errors.push(error.message));
        await page.addInitScript(({ snapshot }) => {
            window.requests = [];
            window.acquireVsCodeApi = () => ({ postMessage(message) {
                window.requests.push(message);
                if (message.type === 'ready') setTimeout(() => window.postMessage({ type: 'state', snapshot, busy: false,
                    history: [{ id: snapshot.id, title: 'com.vixmobi.auto', capturedAt: snapshot.capturedAt, count: snapshot.nodes.length, serial: snapshot.serial }] }, '*'), 0);
            } });
        }, { snapshot });
        await page.goto('http://127.0.0.1:' + server.address().port);
        await page.waitForFunction(() => document.getElementById('screen').naturalWidth > 0);
        await page.locator('#xpath').fill("//node[@text='自动化工作台']");
        await page.locator('#test-xpath').click();
        assert.match(await page.locator('#notice').textContent(), /匹配 1 个节点/);
        assert.ok(await page.locator('.box.selected').count());
        const row = page.locator('[data-property="text"]');
        assert.match(await row.textContent(), /自动化工作台/);
        await row.click({ button: 'right' });
        await page.locator('[data-copy="multiple"]').click();
        assert.equal(await page.evaluate(() => window.requests.at(-1).kind), 'multiple');
        await page.locator('#code-kind').selectOption('multiple');
        assert.match(await page.locator('#code').inputValue(), /nodes.size\(\)/);
        await page.locator('#xpath').fill('invalid['); await page.locator('#test-xpath').click();
        assert.match(await page.locator('#notice').textContent(), /查询失败/);
        // 绝对与相对路径均应返回当前选中的同一个节点。
        const paths = await page.locator('[data-property="xpath"] .value, [data-property="relativeXpath"] .value').allTextContents();
        for (const expression of paths) {
            await page.locator('#xpath').fill(expression); await page.locator('#test-xpath').click();
            assert.match(await page.locator('#notice').textContent(), /匹配 1 个节点/);
        }
        const target = snapshot.nodes.find(node => node.text === '自动化工作台');
        const bounds = await page.locator('#stage').boundingBox();
        const x = bounds.x + (target.bounds[0] + target.bounds[2]) / 2 / snapshot.screen.width * bounds.width;
        const y = bounds.y + (target.bounds[1] + target.bounds[3]) / 2 / snapshot.screen.height * bounds.height;
        await page.mouse.move(x, y); await page.mouse.click(x, y);
        assert.match(await page.locator('[data-property="text"] .value').textContent(), /自动化工作台/);
        assert.match(await page.locator('#coordinates').textContent(), /颜色:-?\d+ hex:#[0-9A-F]{6}/);
        assert.equal(await page.locator('#properties th').allTextContents().then(v => v.join(',')), '属性,值');
        await page.locator('#history-filter').fill('missing-package');
        assert.equal(await page.locator('.history-item').count(), 0);
        await page.locator('#history-filter').fill('vixmobi');
        assert.equal(await page.locator('.history-item').count(), 1);
        await page.locator('#single-tab').check();
        assert.equal(await page.evaluate(() => window.requests.at(-1).type), 'singleTab');
        assert.equal(await page.evaluate(() => window.requests.at(-1).value), true);
        assert.ok(await page.locator('#hits option').count() > 1);
        await page.locator('#save').click(); assert.equal(await page.evaluate(() => window.requests.at(-1).type), 'save');
        await page.locator('#capture').click(); assert.equal(await page.evaluate(() => window.requests.at(-1).type), 'capture');
        await page.locator('#load').click(); assert.equal(await page.evaluate(() => window.requests.at(-1).type), 'load');
        await page.screenshot({ path: '/tmp/xpauto-node-inspector.png' });
        await page.setViewportSize({ width: 850, height: 700 });
        assert.equal(await page.locator('#copy-code').isVisible(), true);
        await page.screenshot({ path: '/tmp/xpauto-node-inspector-compact.png' });
        assert.deepEqual(errors, []);
        console.log('PASS UI: screenshot hit testing, pixel color, tree/property selection, XPath, context menu, code modes, toolbar and compact layout');
    } finally { await browser.close(); server.close(); }
})().catch(error => { console.error(error); server.close(); process.exitCode = 1; });
