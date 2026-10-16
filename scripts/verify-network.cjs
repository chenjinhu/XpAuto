// 本地服务通过 adb reverse 提供测试数据；不依赖公网，不在调试中重新安装应用。
const assert = require('node:assert/strict');
const { execFileSync } = require('node:child_process');
const http = require('node:http');
const { WebSocketServer } = require('../vscode-extension/node_modules/ws');
const { DeviceManager } = require('../vscode-extension/src/device');
const { ProtocolClient } = require('../vscode-extension/src/protocol');
const manager = new DeviceManager({ adbPath: () => process.env.XPAUTO_ADB || 'adb', devicePort: () => 9229, log: text => { if (text.includes("[http.")) console.log(text); }, pickDevice: async () => undefined });
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
const events = [], ranges = [], connections = new Set();
const payload = Buffer.alloc(200000); for (let i = 0; i < payload.length; i++) payload[i] = i % 251;
let downloads = 0, client, port, slowStarted = 0;
const server = http.createServer(async (request, response) => {
    const url = new URL(request.url, 'http://localhost');
    if (url.pathname === '/slow') { slowStarted++; const timer = setTimeout(() => response.end('slow'), 30000); response.on('close', () => clearTimeout(timer)); return; }
    if (url.pathname === '/error') { response.writeHead(409); return response.end('conflict'); }
    if (url.pathname === '/download') {
        const start = Number(request.headers.range?.match(/bytes=(\d+)-/)?.[1] || 0); ranges.push(start);
        const headers = { 'Content-Length': payload.length - start, 'ETag': '"v1"' };
        if (start) headers['Content-Range'] = `bytes ${start}-${payload.length - 1}/${payload.length}`;
        response.writeHead(start ? 206 : 200, headers);
        if (downloads++ === 0) { response.write(payload.subarray(0, 40000)); return setTimeout(() => response.destroy(), 50); }
        return response.end(payload.subarray(start));
    }
    if (url.pathname === '/binary') { response.writeHead(200, { 'Content-Length': payload.length }); return response.end(payload); }
    const chunks = []; for await (const chunk of request) chunks.push(chunk);
    response.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8', 'Set-Cookie': 'test=yes; Path=/' });
    response.end(JSON.stringify({ method: request.method, query: Object.fromEntries(url.searchParams), headers: request.headers, body: Buffer.concat(chunks).toString('utf8') }));
});
const websockets = new WebSocketServer({ server });
websockets.on('connection', socket => {
    connections.add(socket); socket.on('close', () => connections.delete(socket));
    socket.on('message', (data, binary) => { if (!binary && data.toString() === 'drop') socket.close(1012, 'reconnect test'); else socket.send(data, { binary }); });
});
async function until(check, title, timeout = 10000) { const end = Date.now() + timeout; do { const value = await check(); if (value) return value; await delay(30); } while (Date.now() < end); throw Error('超时：' + title); }
async function evaluate(source) { const result = await client.request('Runtime.evaluate', { expression: source, returnByValue: true }); assert.ok(!result.exceptionDetails, JSON.stringify(result.exceptionDetails)); return result.result.value; }
const adb = args => manager.adb(['-s', manager.serial, ...args]);
async function run(source) { return client.request('XpAuto.run', { source, resourceName: 'xpauto://verification/network.js', noDebug: true }); }
const finished = id => until(() => events.find(event => event.name === 'XpAuto.finished' && event.params.runId === id)?.params, '脚本结束', 20000);
(async () => {
    try {
        await new Promise(resolve => server.listen(0, '127.0.0.1', resolve)); port = server.address().port;
        await manager.connect(); await adb(['reverse', 'tcp:' + port, 'tcp:' + port]);
        client = new ProtocolClient(manager.endpoint + '/debug', 60000); client.on('notification', (name, params) => events.push({ name, params }));
        await client.connect(); await client.request('Runtime.enable');
        const pid = await adb(['shell', 'pidof', 'com.vixmobi.auto']);
        const update = (await adb(['shell', 'dumpsys', 'package', 'com.vixmobi.auto'])).match(/lastUpdateTime=(.*)/)?.[1];
        const base = 'http://127.0.0.1:' + port;
        await evaluate('globalThis.networkBase=' + JSON.stringify(base));
        let response = await evaluate('http.request({url:networkBase+"/echo", data:{name:"中文 +&"}, cookie:{a:1}, userAgent:"XpAuto-test"})');
        assert.ok(response, "HTTP 响应应成功"); assert.equal(response.statusCode, 200); assert.equal(response.cookie.test, 'yes');
        assert.equal(JSON.parse(response.body).query.name, '中文 +&'); assert.equal(JSON.parse(response.body).headers.cookie, 'a=1');
        assert.equal(JSON.parse(await evaluate('http.httpGet(networkBase+"/echo","a=hello%20world",2000,null)')).query.a, 'hello world');
        assert.equal(JSON.parse(await evaluate('http.httpGetDefault(networkBase+"/echo",2000,null)')).method, 'GET');
        assert.equal(JSON.parse(await evaluate('http.get(networkBase+"/echo")')).method, 'GET');
        assert.equal(JSON.parse(await evaluate('http.httpPost(networkBase+"/echo",{a:"中文 +"},null,2000,null)')).body, 'a=%E4%B8%AD%E6%96%87%20%2B');
        assert.equal(JSON.parse(await evaluate('http.postJSON(networkBase+"/echo",{a:"中文"},2000,null)')).body, '{"a":"中文"}');
        assert.equal(JSON.parse(await evaluate('http.post(networkBase+"/echo", "{\\"a\\":1}")')).body, '{"a":1}');
        assert.equal(await evaluate('http.request({url:networkBase+"/error"})'), null);
        assert.equal((await evaluate('http.requestEx({url:networkBase+"/error",ignoreHttpErrors:true})')).statusCode, 409);
        assert.equal(await evaluate('http.request({url:networkBase+"/binary",maxBodySize:1024})'), null);
        const file = '/data/user/0/com.vixmobi.auto/files/network-test.txt';
        assert.equal(await evaluate('file.writeFile("上传内容",' + JSON.stringify(file) + ')'), true);
        response = JSON.parse(await evaluate('http.httpPostEx(networkBase+"/echo",{name:"upload"},{asset:' + JSON.stringify(file) + '},2000,null)'));
        assert.match(response.body, /上传内容/); assert.match(response.body, /name="asset"/);
        console.log('PASS 真机 HTTP: GET/POST/JSON/表单/上传/响应元信息/错误/大小限制');
        assert.equal(await evaluate('http.downloadFile(networkBase+"/download","network-test.bin",2000,null)'), false);
        assert.equal(await evaluate('http.downloadFileDefault(networkBase+"/download","network-test.bin",null)'), true);
        assert.ok(ranges[1] > 0);
        const downloaded = execFileSync(process.env.XPAUTO_ADB || 'adb', ['-s', manager.serial, 'exec-out', 'run-as', 'com.vixmobi.auto', 'cat', 'files/network-test.bin']);
        assert.deepEqual(downloaded, payload);
        console.log('PASS 真机下载: 中断续传，完整二进制内容校验');
        for (const type of [1, 2]) {
            const { runId } = await run(`(() => {
                const ws=http.newWebsocket(${JSON.stringify(base.replace('http:', 'ws:'))}, null, ${type});
                const received=[], errors=[]; let opens=0, closed=0, binary;
                ws.onOpen(()=>opens++); ws.onText((socket,text)=>received.push(text)); ws.onBinary((socket,bytes)=>binary=Array.from(bytes));
                ws.onClose(()=>closed++); ws.onError((socket,message)=>errors.push(message));
                ws.setCallTimeout(5); ws.setReadTimeout(0); ws.setWriteTimeout(5); ws.setPingInterval(0); ws.setConnectionLostTimeout(0);
                if(!ws.connect(3000)) throw Error('connect failed '+errors);
                ws.sendText('中文回声'); ws.sendBinary(new Uint8Array([0,127,128,255]));
                ws.startHeartBeat(null,()=> 'heartbeat',100,true);
                let until=Date.now()+3000; while((!binary || !received.includes('heartbeat')) && Date.now()<until) sleep(30);
                ws.stopHeartBeat(); ws.setAutoReconnect(true); ws.sendText('drop');
                until=Date.now()+7000; while(opens<2 && Date.now()<until) sleep(40);
                ws.close(); sleep(60);
                globalThis.socketResult={received,errors,opens,closed,binary,isClosed:ws.isClosed()};
            })();`);
            const result = await finished(runId); assert.equal(result.success, true, result.error);
            response = await evaluate('socketResult'); assert.deepEqual(response.errors, []); assert.deepEqual(response.binary, [0,127,128,255]);
            assert.ok(response.received.includes('中文回声')); assert.ok(response.received.includes('heartbeat')); assert.equal(response.opens, 2); assert.ok(response.isClosed);
            await until(() => connections.size === 0, 'WebSocket 清理');
            console.log('PASS 真机 WebSocket type=' + type + ': 回调、文本、二进制、心跳、断线重连和结束清理');
        }
        const before = slowStarted;
        const { runId } = await run('http.httpGetDefault(networkBase+"/slow",60000); globalThis.shouldNotContinue=true;');
        await until(() => slowStarted > before, '慢请求开始');
        const stoppedAt = Date.now(); await client.request('XpAuto.stop'); assert.equal((await finished(runId)).cancelled, true);
        assert.ok(Date.now() - stoppedAt < 2000); assert.equal(await evaluate('typeof shouldNotContinue'), 'undefined');
        const recovery = await run('globalThis.recovered=JSON.parse(http.get(networkBase+"/echo")).method;');
        assert.equal((await finished(recovery.runId)).success, true); assert.equal(await evaluate('recovered'), 'GET');
        const live = await run(`globalThis.cleanupSocket=http.newWebsocket(${JSON.stringify(base.replace('http:', 'ws:'))}); cleanupSocket.connect(3000);`);
        assert.equal((await finished(live.runId)).success, true); await until(() => connections.size === 0, '脚本完成时关闭连接');
        assert.equal(await evaluate('sleep(10); http.pollEvents(); "console-ready"'), 'console-ready');
        assert.equal(await adb(['shell', 'pidof', 'com.vixmobi.auto']), pid);
        assert.equal((await adb(['shell', 'dumpsys', 'package', 'com.vixmobi.auto'])).match(/lastUpdateTime=(.*)/)?.[1], update);
        console.log('PASS 停止取消网络请求、下一轮恢复、结束自动清理；Android PID 和安装时间未改变');
    } catch (error) { console.error(error, JSON.stringify(events.filter(event => event.name === "Runtime.consoleAPICalled").slice(-5))); throw error; } finally {
        client?.close();
        if (manager.serial) {
            if (port) await adb(['reverse', '--remove', 'tcp:' + port]).catch(() => {});
            await adb(['shell', 'run-as', 'com.vixmobi.auto', 'rm', '-f', 'files/network-test.txt', 'files/network-test.bin', 'files/network-test.bin.xpauto.part', 'files/network-test.bin.xpauto.part.json']).catch(() => {});
        }
        await manager.disconnect(); for (const socket of connections) socket.terminate(); websockets.close(); server.closeAllConnections(); server.close();
    }
})().catch(error => { console.error(error); process.exitCode=1; });
