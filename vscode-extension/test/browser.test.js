'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const { SdkLanguageService } = require('../src/language-service');
const source = fs.readFileSync(path.join(__dirname, '../../app/src/main/assets/browser.js'), 'utf8');
const response = (data, statusCode = 200) => ({ statusCode, body: JSON.stringify(data) });
const page = (values = {}) => ({ url: 'https://example.test/', loading: false, page_error: null, ...values });
function fixture(handler) {
    let now = 0, launches = 0;
    const calls = [], pauses = [];
    const context = vm.createContext({
        http: { request: value => { calls.push(value); return handler(value, calls.length); } },
        utils: { openApp: pkg => { assert.equal(pkg, 'com.vixmobi.browser'); launches++; return true; } },
        getCurrentRunningPkg: () => 'com.vixmobi.browser',
        sleep: value => { pauses.push(value); now += value; },
        Date: { now: () => now },
    });
    vm.runInContext(source, context);
    return { run: expression => vm.runInContext(expression, context), calls, pauses,
        launches: () => launches, advance: value => { now += value; }, context };
}

test('browser 提示覆盖客户端、导航选项、状态与设置返回类型', () => {
    const language = new SdkLanguageService(path.join(__dirname, '../types/xpauto.d.ts'));
    try {
        for (const [source, expected] of [
            ['brow', 'browser'], ['browser.', 'executeJs'], ['browser.open("https://example.test", {', 'history_length'],
            ['const s=browser.getState();s.', 'page_error'], ['browser.getSettings().', 'effective_ua'],
            ['browser.', 'getLoading'], ['browser.getLoading().', 'status'], ['browser.getLoading().', 'progress'],
            ['browser.getState().', 'load_status'], ['browser.getState().', 'progress'],
            ['const b=browser.createClient();b.', 'waitForPage'],
        ]) {
            language.setDocument('/tmp/xpauto-browser-hints.js', source, 1);
            assert.ok(language.completions(source.length).some(value => value.name === expected), source);
        }
        language.setDocument('/tmp/xpauto-browser-hints.js', 'browser.open(', 2);
        assert.match(language.signatures(13).items[0].documentation, /导航/);
    } finally { language.dispose(); }
});

test('所有 HTTP 接口映射、202 接受状态、真实 JS 值和配置清除语义', () => {
    const f = fixture(request => {
        const endpoint = request.url.split(':8765')[1];
        const body = request.requestBody === undefined ? undefined : JSON.parse(request.requestBody);
        assert.equal(request.proxy, false);
        assert.equal(request.followRedirects, false);
        assert.equal(request.retryOnConnectionFailure, false);
        assert.equal(request.ignoreHttpErrors, true);
        assert.equal(request.method, body ? 'POST' : 'GET');
        if (body) assert.match(request.header['Content-Type'], /application\/json; charset=utf-8/);
        if (endpoint === '/api/js/execute') return response({ ok: true, result: JSON.parse(body.script) });
        if (endpoint === '/api/cache/clear') return response({ ok: true, cache_cleared: true });
        if (endpoint === '/api/settings') return response({ ok: true, settings: body || { ua: null } });
        for (const [route, key] of [['ua','ua'], ['referer','referer'], ['history-length','history_length']]) {
            if (endpoint === '/api/' + route) return response({ ok: true, [key]: body ? body[key] : null });
        }
        return response({ ok: true, state: page({ loading: !!body }) }, body ? 202 : 200);
    });
    assert.equal(f.run('browser.getState().loading'), false);
    assert.equal(f.run('browser.navigate("https://example.test", {ua:null, history_length:0}).loading'), true);
    assert.equal(f.run('browser.back().loading'), true);
    assert.equal(f.run('browser.forward().loading'), true);
    assert.equal(f.run('browser.refresh().loading'), true);
    assert.equal(f.run('browser.clearCache()'), true);
    assert.equal(f.run('browser.getSettings().ua'), null);
    assert.equal(f.run('browser.setSettings({ua:"Agent",referer:null}).ua'), 'Agent');
    assert.equal(f.run('browser.getUa()'), null);
    assert.equal(f.run('browser.setUa("")'), '');
    assert.equal(f.run('browser.getReferer()'), null);
    assert.equal(f.run('browser.setReferer(null)'), null);
    assert.equal(f.run('browser.getHistoryLength()'), null);
    assert.equal(f.run('browser.setHistoryLength(0)'), 0);
    for (const value of [null, false, 0, '', '中文', [1, true], { a: '中' }]) {
        const result = f.run('browser.executeJs(' + JSON.stringify(JSON.stringify(value)) + ')');
        assert.equal(JSON.stringify(result), JSON.stringify(value));
    }
    assert.equal(f.launches(), 0);
});

test('加载查询保留四种状态和原始字段，页面失败不会变成查询失败', () => {
    for (const status of ['idle', 'loading', 'complete', 'error']) {
        const result = { ok: true, status, loading: status === 'loading',
            progress: status === 'idle' ? 0 : status === 'loading' ? 45 : 100,
            url: status === 'idle' ? null : 'https://example.test/', title: null,
            page_error: status === 'error' ? 'HTTP 503' : null };
        const f = fixture(() => response(result));
        assert.equal(JSON.stringify(f.run('browser.getLoading()')), JSON.stringify(result));
        assert.equal(f.calls[0].url, 'http://127.0.0.1:8765/api/loading');
        assert.equal(f.calls[0].method, 'GET');
        assert.equal(f.calls[0].requestBody, undefined);
        assert.equal(f.launches(), 0);
    }
    const custom = fixture(() => response({ok: false, error: 'browser_not_ready'}, 503));
    assert.throws(() => custom.run('browser.createClient({baseUrl:"http://localhost:9876",timeout:123}).getLoading()'),
        e => e.code === 'browser_not_ready' && e.statusCode === 503);
    assert.equal(custom.calls[0].url, 'http://localhost:9876/api/loading');
    assert.equal(custom.calls[0].timeout, 123);
    const legacy = fixture(() => response({ok:false,error:'not_found'},404));
    assert.throws(() => legacy.run('browser.getLoading()'), e => e.code === 'not_found' && e.statusCode === 404);
    assert.equal(legacy.calls.length, 1, '不伪造旧版未提供的进度');
    const valid = {ok:true,status:'complete',loading:false,progress:100,url:'https://example.test/',title:null,page_error:null};
    for (const patch of [{status:'unknown'}, {loading:0}, {progress:-1}, {progress:101},
        {progress:0.5}, {progress:'100'}, {progress:undefined}, {url:undefined}, {title:1}, {page_error:{}}]) {
        const f = fixture(() => response({...valid,...patch}));
        assert.throws(() => f.run('browser.getLoading()'), e => e.code === 'invalid_response');
    }
    const cancelled = new Error('cancelled');
    const f = fixture(() => { throw cancelled; });
    assert.throws(() => f.run('browser.getLoading()'), e => e === cancelled);
});

test('1.4 页面等待以完成状态为准，idle 和进度 100 不会提前成功', () => {
    const states = [page({url:null,load_status:'idle',progress:0}),
        page({load_status:'idle',progress:100}), page({load_status:'idle',progress:100}),
        page({load_status:'loading',progress:100}), page({loading:true,load_status:'loading',progress:100}),
        page({load_status:'complete',progress:100}), page({load_status:'complete',progress:100})];
    const f = fixture(() => response({ok:true,state:states.shift()}));
    assert.equal(f.run('browser.waitForPage().load_status'), 'complete');
    assert.equal(f.calls.length, 7);
    for (const page_error of [null, 'HTTP 503']) {
        const failed = fixture(() => response({ok:true,state:page({load_status:'error',progress:100,page_error})}));
        assert.throws(() => failed.run('browser.waitForPage()'), e => e.code === 'page_load_failed' && e.response.state.progress === 100);
    }
    for (const patch of [{load_status:'unknown'}, {load_status:null}, {progress:101}]) {
        const invalid = fixture(() => response({ok:true,state:page(patch)}));
        assert.throws(() => invalid.run('browser.waitForPage()'), e => e.code === 'invalid_response');
    }
});

test('浏览器错误、非 JSON、断线及取消都不会重放页面命令', () => {
    for (const reply of [response({ok:false,error:'js_error',message:'boom'},400),
        response({ok:false,error:'no_history'},409), response({ok:false,error:'history_override_unavailable'},409),
        response({ok:false,error:'browser_not_ready'},503), response({ok:false,error:'timeout'},504)]) {
        const f = fixture(() => reply);
        assert.throws(() => f.run('browser.executeJs("sideEffect()")'), e => e.code === JSON.parse(reply.body).error && e.statusCode === reply.statusCode && e.response.ok === false);
        assert.equal(f.calls.length, 1);
    }
    for (const reply of [null, {statusCode:200,body:'<html/>'}, response({ok:true}), response({ok:'true',result:1})]) {
        const f = fixture(() => reply);
        assert.throws(() => f.run('browser.executeJs("sideEffect()")'), e => e.name === 'VixBrowserError');
        assert.equal(f.calls.length, 1);
    }
    const cancellation = new Error('cancelled');
    const f = fixture(() => { throw cancellation; });
    assert.throws(() => f.run('browser.start()'), e => e === cancellation);
    assert.equal(f.calls.length, 1);
});

test('等待覆盖重定向空闲间隙、页面错误、SPA 真值和总超时预算', () => {
    const states = [page({loading:true}), page(), page({url:'https://final.test',loading:true}),
        page({url:'https://final.test'}), page({url:'https://final.test'})];
    const f = fixture(() => response({ok:true,state:states.shift()}));
    assert.equal(f.run('browser.waitForPage().url'), 'https://final.test');
    assert.equal(f.pauses.length, 4);
    const failed = fixture(() => response({ok:true,state:page({page_error:'ERR_NAME_NOT_RESOLVED'})}));
    assert.throws(() => failed.run('browser.waitForPage()'), e => e.code === 'page_load_failed' && !!e.response.state);
    const values = [false, null, 0, {ready:true}];
    const spa = fixture(() => response({ok:true,result:values.shift()}));
    assert.equal(spa.run('browser.waitFor("window.ready").ready'), true);
    const slow = fixture(() => response({ok:true,state:page({loading:true})}));
    assert.throws(() => slow.run('browser.waitForPage(350,200)'), e => e.code === 'page_load_timeout');
    assert.deepEqual(slow.pauses, [200,150]);
    assert.deepEqual(slow.calls.map(value => value.timeout), [350,150]);
    const condition = fixture(() => response({ok:true,result:false}));
    assert.throws(() => condition.run('browser.waitFor("false",100,50)'), e => e.code === 'condition_timeout');
    const open = fixture((request, number) => {
        if (number === 1) open.advance(150);
        return response({ok:true,state:page({loading:true})}, number === 1 ? 202 : 200);
    });
    assert.throws(() => open.run('browser.open("https://example.test",{timeout:300,pollInterval:100})'), e => e.code === 'page_load_timeout');
    assert.deepEqual(open.calls.map(value => value.timeout), [300,150,50]);
});

test('启动等待就绪、启动超时与独立客户端配置', () => {
    const f = fixture((request, number) => number === 1 ? null : number === 2
        ? response({ok:false,error:'browser_not_ready'},503) : response({ok:true,state:page()}));
    assert.equal(f.run('browser.start().loading'), false);
    assert.equal(f.launches(), 1);
    const unavailable = fixture(() => null);
    assert.throws(() => unavailable.run('browser.start(100)'), e => e.code === 'start_timeout');
    assert.equal(unavailable.calls.length, 1);
    const custom = fixture(() => response({ok:true,state:page()}));
    custom.run('const client=browser.createClient({baseUrl:"http://localhost:9876/",timeout:234}); client.getState(); browser.getState();');
    assert.deepEqual(custom.calls.map(value => [value.url,value.timeout]), [
        ['http://localhost:9876/api/state',234], ['http://127.0.0.1:8765/api/state',12000]]);
    assert.throws(() => custom.run('client.start()'));
    assert.equal(custom.launches(), 0);
});

test('start 必须等到浏览器进入前台，后台 HTTP 可用不能提前成功', () => {
    const f = fixture(() => response({ok:true,state:page()}));
    f.context.getCurrentRunningPkg = () => f.pauses.length < 3 ? 'com.example.other' : 'com.vixmobi.browser';
    assert.equal(f.run('browser.start().loading'), false);
    assert.deepEqual(f.pauses, [200,200,200,200]);
    assert.equal(f.calls.length, 2);
    assert.equal(f.launches(), 1);

    const blocked = fixture(() => response({ok:true,state:page()}));
    blocked.context.getCurrentRunningPkg = () => 'com.example.other';
    assert.throws(() => blocked.run('browser.start(350)'), e => e.code === 'foreground_timeout');
    assert.deepEqual(blocked.pauses, [200,150]);
    assert.equal(blocked.calls.length, 0);

    // 首次启动被 HOME 切换覆盖时，允许重发纯启动请求；不发送页面导航。
    const covered = fixture(() => response({ok:true,state:page()}));
    covered.context.getCurrentRunningPkg = () => covered.launches() >= 2 ? 'com.vixmobi.browser' : 'com.example.home';
    covered.run('browser.start(2000)');
    assert.equal(covered.launches(), 2);
    assert.ok(covered.calls.every(call => call.method === 'GET' && call.url.endsWith('/api/state')));

    const switched = fixture(() => {
        switched.context.getCurrentRunningPkg = () => 'com.example.other';
        return response({ok:true,state:page()});
    });
    assert.throws(() => switched.run('browser.start(350)'), e => e.code === 'foreground_timeout');
    assert.equal(switched.calls.length, 1);
    const cancelled = fixture(() => response({ok:true,state:page()}));
    cancelled.context.getCurrentRunningPkg = () => '';
    const cancellation = new Error('cancelled');
    cancelled.context.sleep = () => { throw cancellation; };
    assert.throws(() => cancelled.run('browser.start()'), e => e === cancellation);
});

test('非法参数先拒绝，JSON 请求体按 UTF-8 精确限制 64 KiB', () => {
    const f = fixture(() => response({ok:true,result:null}));
    for (const expression of [
        'browser.createClient({baseUrl:"http://example.test/extra"})', 'browser.createClient({baseUrl:"http://localhost:99999"})',
        'browser.createClient({baseUrl:"http://localhost\\n"})', 'browser.createClient({timeout:0})',
        'browser.createClient({pollInterval:1})', 'browser.setSettings({})', 'browser.setSettings({historyLength:7})',
        'browser.setHistoryLength(true)', 'browser.setHistoryLength(-1)', 'browser.setHistoryLength(2147483648)',
        'browser.setUa()', 'browser.setReferer(1)', 'browser.navigate("")', 'browser.executeJs(null)',
        'browser.open("https://example.test", {timeout:0})', 'browser.waitFor("true",100,0)',
    ]) assert.throws(() => f.run(expression), expression);
    assert.equal(f.calls.length, 0);
    const limit = '😀'.repeat(16380) + 'aaa'; // JSON 外壳 13 字节，合计正好 65536。
    assert.equal(Buffer.byteLength(JSON.stringify({script:limit})), 65536);
    f.run('browser.executeJs(' + JSON.stringify(limit) + ')');
    assert.equal(f.calls.length, 1);
    assert.throws(() => f.run('browser.executeJs(' + JSON.stringify(limit+'a') + ')'), /64 KiB/);
    assert.equal(f.calls.length, 1);
});
