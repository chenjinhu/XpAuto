import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { fileURLToPath, pathToFileURL } from 'node:url';

const endpoint = process.argv[2] ?? 'ws://127.0.0.1:19229';
const sourceUrl = new URL('../examples/inspector-probe/main.js', import.meta.url);
const source = await readFile(sourceUrl, 'utf8');
const resourceName = pathToFileURL(fileURLToPath(sourceUrl)).href;
const timeoutMs = 10_000;

class InspectorClient {
    pending = new Map();
    events = [];
    waiters = [];
    nextId = 0;

    async connect() {
        this.socket = new WebSocket(endpoint);
        this.socket.addEventListener('message', event => this.receive(JSON.parse(event.data)));
        this.socket.addEventListener('close', () => this.rejectAll(new Error('Inspector disconnected')));
        await new Promise((resolve, reject) => {
            const timer = setTimeout(() => reject(new Error(`Connection timed out: ${endpoint}`)), timeoutMs);
            this.socket.addEventListener('open', () => {
                clearTimeout(timer);
                resolve();
            }, { once: true });
            this.socket.addEventListener('error', event => {
                clearTimeout(timer);
                reject(new Error(`Cannot connect to ${endpoint}: ${event.message ?? 'WebSocket error'}`));
            }, { once: true });
        });
        await this.event('XpAuto.ready');
    }

    receive(message) {
        if (message.id != null) {
            const request = this.pending.get(message.id);
            if (request) {
                clearTimeout(request.timer);
                this.pending.delete(message.id);
                if (message.error) request.reject(new Error(JSON.stringify(message.error)));
                else request.resolve(message.result);
            }
            return;
        }
        const index = this.waiters.findIndex(waiter => waiter.matches(message));
        if (index !== -1) {
            const waiter = this.waiters.splice(index, 1)[0];
            clearTimeout(waiter.timer);
            waiter.resolve(message.params);
        } else {
            this.events.push(message);
        }
    }

    request(method, params = {}) {
        const id = ++this.nextId;
        return new Promise((resolve, reject) => {
            const timer = setTimeout(() => {
                this.pending.delete(id);
                reject(new Error(`Timed out: ${method}`));
            }, timeoutMs);
            this.pending.set(id, { resolve, reject, timer });
            this.socket.send(JSON.stringify({ id, method, params }));
        });
    }

    event(method, predicate = () => true) {
        const matches = message => message.method === method && predicate(message.params);
        const index = this.events.findIndex(matches);
        if (index !== -1) return Promise.resolve(this.events.splice(index, 1)[0].params);
        return new Promise((resolve, reject) => {
            const waiter = { matches, resolve, reject };
            waiter.timer = setTimeout(() => {
                this.waiters = this.waiters.filter(item => item !== waiter);
                reject(new Error(`Timed out waiting for ${method}`));
            }, timeoutMs);
            this.waiters.push(waiter);
        });
    }

    rejectAll(error) {
        for (const request of this.pending.values()) {
            clearTimeout(request.timer);
            request.reject(error);
        }
        this.pending.clear();
        for (const waiter of this.waiters) {
            clearTimeout(waiter.timer);
            waiter.reject(error);
        }
        this.waiters = [];
    }

    async close() {
        if (!this.socket || this.socket.readyState === WebSocket.CLOSED) return;
        const closed = new Promise(resolve => {
            const timer = setTimeout(resolve, 2000);
            this.socket.addEventListener('close', () => {
                clearTimeout(timer);
                resolve();
            }, { once: true });
        });
        this.socket.close();
        await closed;
    }
}

const client = new InspectorClient();
try {
    await client.connect();
    console.log('PASS: connected to the on-device V8 Inspector');
    await client.request('Runtime.enable');
    await client.request('Debugger.enable');
    const lineNumber = source.split('\n').findIndex(line => line.includes('// breakpoint'));
    assert.ok(lineNumber >= 0);
    await client.request('Debugger.setBreakpointByUrl', { url: resourceName, lineNumber });
    const { runId } = await client.request('XpAuto.run', { source, resourceName });
    let paused = await client.event('Debugger.paused');
    assert.equal(paused.callFrames[0].functionName, 'calculateTotal');
    assert.equal(paused.callFrames[0].location.lineNumber, lineNumber);
    const localValue = await client.request('Debugger.evaluateOnCallFrame', {
        callFrameId: paused.callFrames[0].callFrameId,
        expression: 'start + increment',
        returnByValue: true,
    });
    assert.equal(localValue.result.value, 12);
    console.log('PASS: source breakpoint, call stack, and local variable evaluation');

    await client.request('Debugger.stepOver');
    paused = await client.event('Debugger.paused');
    const total = await client.request('Debugger.evaluateOnCallFrame', {
        callFrameId: paused.callFrames[0].callFrameId,
        expression: 'total',
        returnByValue: true,
    });
    assert.equal(total.result.value, 12);
    await client.request('Debugger.stepInto');
    paused = await client.event('Debugger.paused');
    assert.equal(paused.callFrames[0].functionName, 'double');
    await client.request('Debugger.stepOut');
    paused = await client.event('Debugger.paused');
    assert.equal(paused.callFrames[0].functionName, 'calculateTotal');
    console.log('PASS: step over, step into, and step out');
    await client.request('Debugger.resume');
    const completed = await client.event('XpAuto.finished', event => event.runId === runId);
    assert.equal(completed.success, true, completed.error);
    const value = await client.request('Runtime.evaluate', { expression: 'probeResult', returnByValue: true });
    assert.equal(value.result.value, 24);
    console.log('PASS: resume and completed script result');

    await client.request('Debugger.setPauseOnExceptions', { state: 'all' });
    const failedRun = await client.request('XpAuto.run', {
        source: 'throw new Error("XpAuto probe exception");',
        resourceName: 'xpauto://probe/exception.js',
    });
    paused = await client.event('Debugger.paused');
    assert.equal(paused.reason, 'exception');
    await client.request('Debugger.resume');
    const failed = await client.event('XpAuto.finished', event => event.runId === failedRun.runId);
    assert.equal(failed.success, false);
    assert.match(failed.error, /XpAuto probe exception/);
    await client.request('Debugger.setPauseOnExceptions', { state: 'none' });
    console.log('PASS: pause on JS exception and report the source failure');

    const stoppedRun = await client.request('XpAuto.run', {
        source: 'debugger; while (true) {}',
        resourceName: 'xpauto://probe/cancel.js',
    });
    await client.event('Debugger.paused');
    await client.request('XpAuto.stop');
    const stopped = await client.event('XpAuto.finished', event => event.runId === stoppedRun.runId);
    assert.equal(stopped.cancelled, true);
    console.log('PASS: stop while the debugger is paused');

    const loopingRun = await client.request('XpAuto.run', {
        source: 'debugger; while (true) {}',
        resourceName: 'xpauto://probe/running-cancel.js',
    });
    await client.event('Debugger.paused');
    await client.request('Debugger.resume');
    await client.event('Debugger.resumed');
    await client.request('XpAuto.stop');
    const interrupted = await client.event('XpAuto.finished', event => event.runId === loopingRun.runId);
    assert.equal(interrupted.cancelled, true);
    assert.equal(interrupted.success, false);
    console.log('PASS: terminate a running JavaScript infinite loop');

    const restartedRun = await client.request('XpAuto.run', {
        source: 'globalThis.probeResult = 42;',
        resourceName: 'xpauto://probe/restart.js',
    });
    const restarted = await client.event('XpAuto.finished', event => event.runId === restartedRun.runId);
    assert.equal(restarted.success, true, restarted.error);
    const restartedValue = await client.request('Runtime.evaluate', {
        expression: 'probeResult', returnByValue: true,
    });
    assert.equal(restartedValue.result.value, 42);
    console.log('PASS: execute another script after cancellation');
} finally {
    await client.close();
}
