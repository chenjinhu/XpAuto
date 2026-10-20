'use strict';
// 使用同一手机上的真实 vixBrowser 和本机测试网页；结束时恢复配置、页面并清理转发。
const assert = require('node:assert/strict');
const http = require('node:http');
const { DeviceManager } = require('../vscode-extension/src/device');
const { ProtocolClient } = require('../vscode-extension/src/protocol');
const manager = new DeviceManager({ adbPath: () => process.env.XPAUTO_ADB || 'adb',
    devicePort: () => 9229, log: () => {}, pickDevice: async () => undefined });
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
const requests = [], events = [];
let client, port, original, started = false, slowRequests = 0;
const fixture = http.createServer((request, response) => {
    const url = new URL(request.url, 'http://localhost');
    requests.push({ path: url.pathname, headers: request.headers });
    if (url.pathname === '/api/state') {
        slowRequests++;
        const timer = setTimeout(() => response.end('{}'), 30000);
        response.on('close', () => clearTimeout(timer));
        return;
    }
    if (url.pathname === '/redirect') { response.writeHead(302, {Location:'/one'}); response.end(); return; }
    if (url.pathname === '/error') { response.writeHead(503); response.end('test page failed'); return; }
    if (url.pathname === '/slow') {
        const timer = setTimeout(() => response.end('late page'), 15000);
        response.on('close', () => clearTimeout(timer));
        return;
    }
    const name = url.pathname === '/two' ? 'two' : 'one';
    response.writeHead(200, {'Content-Type':'text/html; charset=utf-8'});
    response.end(`<!doctype html><html><head><meta name="viewport" content="width=device-width"><title>XpAuto ${name}</title>
      <script>window.initialHistory=history.length;window.initialUa=navigator.userAgent;</script></head>
      <body><h1>浏览器验证 ${name}</h1><input id="input"><a href="/two">下一页</a></body></html>`);
});
const adb = args => manager.adb(['-s', manager.serial, ...args]);
async function until(predicate, label, timeout = 15000) {
    const end = Date.now() + timeout;
    do { const result = await predicate(); if (result) return result; await delay(30); } while (Date.now() < end);
    throw Error('超时：' + label);
}
async function evaluate(expression) {
    const result = await client.request('Runtime.evaluate', {expression,returnByValue:true});
    assert.equal(result.exceptionDetails, undefined, JSON.stringify(result.exceptionDetails));
    return result.result.value;
}
const pageJs = script => evaluate('browser.executeJs(' + JSON.stringify(script) + ')');
async function startInForeground() {
    // 在同一次手机求值中检查返回时机，避免第二次请求的延迟掩盖提前返回问题。
    const result = await evaluate('(() => { const state = browser.start(); return {state, pkg:getCurrentRunningPkg()}; })()');
    assert.equal(result.pkg, 'com.vixmobi.browser', 'start 返回时浏览器必须已在前台');
    const activities = await adb(['shell','dumpsys','activity','activities']);
    assert.match(activities, /topResumedActivity=ActivityRecord\{[^\n]*com\.vixmobi\.browser\//);
    return result.state;
}
async function expectedError(expression, code, statusCode) {
    const result = await evaluate(`(() => { try { ${expression}; return null; }
      catch(e) { return {name:e.name,code:e.code,statusCode:e.statusCode}; } })()`);
    assert.equal(result?.name, 'VixBrowserError', JSON.stringify(result));
    assert.equal(result.code, code);
    if (statusCode !== undefined) assert.equal(result.statusCode, statusCode);
}
const run = source => client.request('XpAuto.run', {source,resourceName:'xpauto://verification/browser.js',noDebug:true});
const finished = runId => until(() => events.find(e => e.name === 'XpAuto.finished' && e.params.runId === runId)?.params, '脚本结束');
(async () => {
    try {
        await new Promise(resolve => fixture.listen(0,'127.0.0.1',resolve));
        port = fixture.address().port;
        await manager.connect();
        await adb(['reverse','tcp:'+port,'tcp:'+port]);
        client = new ProtocolClient(manager.endpoint+'/debug',45000);
        client.on('notification',(name,params)=>events.push({name,params}));
        await client.connect(); await client.request('Runtime.enable');
        const pid = await adb(['shell','pidof','com.vixmobi.auto']);
        const update = (await adb(['shell','dumpsys','package','com.vixmobi.auto'])).match(/lastUpdateTime=(.*)/)?.[1];
        await evaluate('home(); sleep(600);');
        original = await startInForeground(); started = true;
        assert.equal(typeof (await evaluate('browser.getState()')).loading, 'boolean');
        console.log('PASS 从桌面唤起后台浏览器；返回时已在前台');
        const base = 'http://127.0.0.1:'+port;
        await evaluate('globalThis.browserTestBase='+JSON.stringify(base));
        const settings = await evaluate('browser.getSettings()');
        assert.equal(typeof settings.effective_ua,'string');
        const history = settings.history_length_supported ? 7 : null;
        const configured = await evaluate('browser.setSettings('+JSON.stringify({ua:'XpAutoBrowserVerify/0.7',referer:base+'/source',history_length:history})+')');
        assert.equal(configured.ua,'XpAutoBrowserVerify/0.7');
        assert.equal(await evaluate('browser.getUa()'),configured.ua);
        assert.equal(await evaluate('browser.getReferer()'),base+'/source');
        assert.equal(await evaluate('browser.getHistoryLength()'),history);
        await expectedError('browser.setSettings({ua:"bad\\n",history_length:8})','invalid_ua',400);
        assert.deepEqual(await evaluate('browser.getSettings()'),configured,'无效设置不能部分保存');
        const first = await evaluate('browser.open(browserTestBase+"/redirect")');
        assert.equal(first.url,base+'/one'); assert.equal(first.title,'XpAuto one');
        assert.equal(first.loading,false); assert.equal(first.page_error,null);
        assert.equal(first.load_status,'complete'); assert.equal(first.progress,100);
        assert.deepEqual(await evaluate('browser.getLoading()'), {ok:true,status:'complete',loading:false,
            progress:100,url:first.url,title:first.title,page_error:null});
        assert.equal((await startInForeground()).url,first.url,'重复启动保留当前页面');
        await evaluate('home();');
        assert.equal((await startInForeground()).url,first.url,'HOME 后立即启动保留当前页面');
        assert.equal(await evaluate('sleep(800); getCurrentRunningPkg()'),'com.vixmobi.browser','启动后不会被尚未结束的 HOME 切换覆盖');
        console.log('PASS 重复启动及 HOME 后立即启动，浏览器在前台且页面保留');
        const uaRequest = requests.find(r=>r.path==='/redirect');
        assert.equal(uaRequest.headers['user-agent'],'XpAutoBrowserVerify/0.7');
        assert.equal(uaRequest.headers.referer,base+'/source');
        if (history !== null) assert.equal(await evaluate('browser.executeJs("window.initialHistory")'),7);
        assert.equal(await evaluate('browser.executeJs("window.initialUa")'),'XpAutoBrowserVerify/0.7');
        console.log('PASS 真实浏览器启动、配置原子更新、UA/Referer 请求头、文档开始 history 覆盖、重定向与页面等待');

        for (const value of [null,false,0,'','中文 " \\',[1,true],{title:'中文'}]) {
            const pageScript = '('+JSON.stringify(value)+')';
            assert.deepEqual(await evaluate('browser.executeJs('+JSON.stringify(pageScript)+')'),value);
        }
        assert.equal(await evaluate('browser.executeJs("undefined")'),null);
        for (const script of ['throw new Error("test")','Promise.resolve(1)','1n','(()=>{let a={};a.a=a;return a})()']) {
            await expectedError('browser.executeJs('+JSON.stringify(script)+')','js_error',400);
        }
        await evaluate('browser.executeJs("window.xpautoDone=false;setTimeout(()=>{window.xpautoDone={done:true}},400);null")');
        assert.deepEqual(await evaluate('browser.waitFor("window.xpautoDone",5000)'),{done:true});
        await expectedError('browser.waitFor("false",200,50)','condition_timeout',0);
        assert.equal(await evaluate('browser.waitFor(' + JSON.stringify('document.querySelector("h1")?.textContent') + ',1000)'), '浏览器验证 one');
        await pageJs('document.cookie="xpauto_verify=1;SameSite=Lax";localStorage.setItem("xpauto_verify","saved");null');
        assert.equal(await evaluate('browser.clearCache()'),true);
        assert.equal(await pageJs('document.cookie.includes("xpauto_verify=1") && localStorage.getItem("xpauto_verify")==="saved"'),true);
        await pageJs('document.cookie="xpauto_verify=;Max-Age=0";localStorage.removeItem("xpauto_verify");null');
        console.log('PASS 页面 JS 实际类型、异常与不支持类型、SPA 条件等待、清缓存保留 Cookie/DOM Storage');

        await evaluate('browser.open(browserTestBase+"/two")');
        await evaluate('browser.back()');
        assert.equal((await evaluate('browser.waitForPage()')).url,base+'/one');
        await evaluate('browser.forward()');
        assert.equal((await evaluate('browser.waitForPage()')).url,base+'/two');
        await evaluate('browser.refresh()');
        assert.equal((await evaluate('browser.waitForPage()')).title,'XpAuto two');
        assert.equal(await evaluate('browser.setUa(null)'),null);
        assert.equal(await evaluate('browser.setReferer("")'),null);
        if (history !== null) assert.equal(await evaluate('browser.setHistoryLength(0)'),0);
        assert.equal(await evaluate('browser.setHistoryLength(null)'),null);
        assert.equal(await evaluate('browser.getUa()'),null);
        assert.equal(await evaluate('browser.getReferer()'),null);
        assert.equal(await evaluate('browser.getHistoryLength()'),null);
        await expectedError('browser.open(browserTestBase+"/error",{timeout:5000})','page_load_failed',0);
        const failedPage = await evaluate('browser.getLoading()');
        assert.equal(failedPage.ok,true); assert.equal(failedPage.status,'error');
        assert.equal(failedPage.loading,false); assert.ok(failedPage.page_error);
        await evaluate('browser.navigate(browserTestBase+"/slow")');
        const loading = await evaluate('browser.getLoading()');
        assert.equal(loading.status,'loading'); assert.equal(loading.loading,true);
        assert.ok(Number.isInteger(loading.progress) && loading.progress >= 0 && loading.progress <= 100);
        await expectedError('browser.waitForPage(250,50)','page_load_timeout',0);
        await evaluate('browser.open(browserTestBase+"/one")');
        console.log('PASS 前进后退、刷新、配置清除；1.4 加载状态、进度、页面错误与等待超时');

        const beforeSlow = slowRequests;
        const active = await run('browser.createClient({baseUrl:browserTestBase,timeout:30000}).getState();globalThis.browserShouldNotContinue=true;');
        await until(()=>slowRequests>beforeSlow,'慢请求启动');
        const stopTime = Date.now(); await client.request('XpAuto.stop');
        assert.equal((await finished(active.runId)).cancelled,true);
        assert.ok(Date.now()-stopTime<2500,'HTTP 停止及时返回');
        assert.equal(await evaluate('typeof browserShouldNotContinue'),'undefined');
        const recovery = await run('globalThis.browserRecovered=browser.executeJs("1+1");');
        assert.equal((await finished(recovery.runId)).success,true);
        assert.equal(await evaluate('browserRecovered'),2);
        // 浏览器已处于测试页，此时结束进程验证冷启动，不触碰用户原页面的运行状态。
        await adb(['shell','am','force-stop','com.vixmobi.browser']);
        await startInForeground();
        const idle = await evaluate('browser.getLoading()');
        assert.equal(idle.status,'idle'); assert.equal(idle.progress,0); assert.equal(idle.url,null);
        console.log('PASS 浏览器进程未运行时，start 启动界面并等待前台与 HTTP 就绪');
        assert.equal(await adb(['shell','pidof','com.vixmobi.auto']),pid);
        assert.equal((await adb(['shell','dumpsys','package','com.vixmobi.auto'])).match(/lastUpdateTime=(.*)/)?.[1],update);
        console.log('PASS HTTP 等待中停止、下一轮恢复；XpAuto PID 与安装时间保持不变');
    } finally {
        try {
            if (started && client) {
                await evaluate('browser.setSettings('+JSON.stringify({referer:original.referer,ua:original.ua,history_length:original.history_length})+')');
                if (original.url && /^https?:\/\//i.test(original.url)) await evaluate('browser.navigate('+JSON.stringify(original.url)+')');
                console.log('已恢复浏览器原配置和可恢复的原页面地址');
            }
        } finally {
            client?.close();
            if (manager.serial && port) await adb(['reverse','--remove','tcp:'+port]).catch(()=>{});
            if (manager.serial) await adb(['shell','am','start','-f','0x14000000','-n','com.vixmobi.auto/com.vixmobi.auto.devtools.DeveloperActivity']).catch(()=>{});
            await manager.disconnect(); fixture.closeAllConnections(); fixture.close();
        }
    }
})().catch(error=>{ console.error(error);process.exitCode=1; });
