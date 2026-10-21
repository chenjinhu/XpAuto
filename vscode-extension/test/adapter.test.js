'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const {XpAutoDebugAdapter} = require('../src/adapter');

function fixture(error) {
    const adapter = new XpAutoDebugAdapter({});
    const messages = [], calls = [];
    let closed = false;
    adapter.config = {};
    adapter.program = '/tmp/main.js';
    adapter.source = 'logi("example");';
    adapter.sourceUrl = 'file:///tmp/main.js';
    adapter.project = {entry: 'main.js', files: {}};
    adapter.client = {request: async (method, params) => {
        calls.push({method, params});
        if (error) throw error;
        return {runId: 1};
    }, close: () => {closed = true;}};
    adapter.onDidSendMessage(message => messages.push(message));
    async function request(command, args = {}) {
        const seq = messages.length + 1;
        const done = new Promise(resolve => {
            const subscription = adapter.onDidSendMessage(message => {
                if (message.type === 'response' && message.request_seq === seq) {
                    subscription.dispose();resolve(message);
                }
            });
        });
        adapter.handleMessage({type: 'request', seq, command, arguments: args});
        return done;
    }
    return {adapter, messages, calls, request, get closed() {return closed;}};
}

test('手机拒绝启动时显示真实原因、结束会话并释放连接', async () => {
    const reason = '运行脚本前请先授权：无障碍服务';
    const f = fixture(new Error(reason));
    const response = await f.request('configurationDone');
    assert.equal(response.success, false);
    assert.equal(response.message, reason);
    assert.ok(f.messages.some(message => message.event === 'output'
        && message.body.category === 'stderr' && message.body.output.includes('启动失败：' + reason)));
    assert.equal(f.messages.filter(message => message.event === 'terminated').length, 1);
    assert.equal(f.closed, true);
    assert.equal(f.calls[0].method, 'XpAuto.run');
});

test('断点初始化失败会退出，已运行会话修改断点失败时保留会话', async () => {
    const args = {source: {path: '/tmp/main.js'}, breakpoints: [{line: 1}]};
    for (const command of ['setBreakpoints','setExceptionBreakpoints']) {
        const f = fixture(new Error('断点配置失败'));
        await f.request(command, args);
        assert.equal(f.closed, true);
        assert.equal(f.adapter.ended, true);
        assert.equal(f.calls.some(call => call.method === 'XpAuto.run'), false);
    }
    const running = fixture(new Error('断点修改失败'));
    running.adapter.started = true;
    await running.request('setBreakpoints', args);
    assert.equal(running.closed, false);
    assert.equal(running.adapter.ended, false);
});

test('配置完成只启动一次，运行日志来自手机的实际运行通知', async () => {
    const f = fixture();
    assert.equal((await f.request('configurationDone')).success, true);
    assert.equal((await f.request('configurationDone')).success, true);
    assert.equal(f.calls.length, 1);
    assert.equal(f.closed, false);
    assert.equal(f.messages.some(message => message.event === 'terminated'), false);
    assert.equal(f.messages.some(message => message.body?.output?.includes('开始运行')), false);
    f.adapter.notification('XpAuto.running', {runId: 1});
    assert.ok(f.messages.some(message => message.body?.output?.includes('开始运行 main.js')));
});

test('已连接手机但运行时初始化被拒绝时，启动失败会释放 WebSocket', async t => {
    const fs = require('node:fs/promises');
    const path = require('node:path');
    const os = require('node:os');
    const {once} = require('node:events');
    const {WebSocketServer} = require('ws');
    const directory = await fs.mkdtemp(path.join(os.tmpdir(),'xpauto-startup-'));
    const program = path.join(directory,'main.js');
    await fs.writeFile(program,'logi("test");');
    const server = new WebSocketServer({port:0,host:'127.0.0.1'});
    await once(server,'listening');
    t.after(async () => {for(const socket of server.clients)socket.terminate();server.close();await fs.rm(directory,{recursive:true,force:true});});
    let disconnected;
    const closed = new Promise(resolve => {disconnected=resolve;});
    server.on('connection', socket => {
        socket.on('close',disconnected);
        socket.send(JSON.stringify({method:'XpAuto.ready',params:{protocolVersion:1}}));
        socket.on('message', data => {
            const request = JSON.parse(data);
            socket.send(JSON.stringify({id:request.id,error:{message:'初始化被拒绝'}}));
        });
    });
    const f=fixture();
    f.adapter.manager={connect:async()=>{},capabilities:['projectModules'],endpoint:`ws://127.0.0.1:${server.address().port}`};
    await f.request('launch',{program});
    await closed;
    assert.equal(f.adapter.ended,true);
    assert.equal(f.messages.filter(message=>message.event==='terminated').length,1);
    assert.ok(f.messages.some(message=>message.body?.output?.includes('启动失败：初始化被拒绝')));
});
