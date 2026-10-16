'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const { once } = require('node:events');
const { WebSocketServer } = require('ws');
const { ProtocolClient } = require('../src/protocol');

async function fixture(t, handle) {
    const server = new WebSocketServer({ port: 0, host: '127.0.0.1' });
    await once(server, 'listening');
    const client = new ProtocolClient(`ws://127.0.0.1:${server.address().port}`, 500);
    server.on('connection', handle);
    t.after(() => { client.close(); for (const socket of server.clients) socket.terminate(); server.close(); });
    return client;
}
const ready = socket => socket.send(JSON.stringify({ method: 'XpAuto.ready', params: { protocolVersion: 1 } }));

test('matches out-of-order replies and exposes device errors', async t => {
    const requests = [];
    const client = await fixture(t, socket => {
        ready(socket);
        socket.on('message', data => {
            requests.push(JSON.parse(data));
            if (requests.length === 2) {
                socket.send(JSON.stringify({ id: requests[1].id, error: { message: '权限未授权' } }));
                socket.send(JSON.stringify({ id: requests[0].id, result: { value: 24 } }));
            }
        });
    });
    await client.connect();
    const result = client.request('evaluate');
    const failure = assert.rejects(client.request('run'), /权限未授权/);
    assert.deepEqual(await result, { value: 24 });
    await failure;
});

test('rejects an incompatible phone handshake with an actionable message', async t => {
    const client = await fixture(t, socket => socket.send(JSON.stringify({ method: 'XpAuto.ready', params: { protocolVersion: 99 } })));
    await assert.rejects(client.connect(), /版本不兼容/);
});

test('disconnect rejects pending operations immediately', async t => {
    const client = await fixture(t, socket => {
        ready(socket);
        socket.once('message', () => socket.close(1000, 'device stopped'));
    });
    await client.connect();
    await assert.rejects(client.request('waiting'), /device stopped/);
    assert.equal(client.pending.size, 0);
});
