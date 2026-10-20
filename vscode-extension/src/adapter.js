'use strict';
const { EventEmitter } = require('node:events');
const { readFile } = require('node:fs/promises');
const path = require('node:path');
const { pathToFileURL, fileURLToPath } = require('node:url');
const { ProtocolClient } = require('./protocol');
const { remoteText } = require('./device');
const { collectProject } = require('./project');

// One DAP session and one device runtime. Script execution starts only after
// configurationDone, once every source breakpoint has been installed in V8.
class XpAutoDebugAdapter {
    constructor(manager) {
        this.manager = manager;
        this.messages = new EventEmitter();
        this.onDidSendMessage = listener => {
            this.messages.on('message', listener);
            return { dispose: () => this.messages.removeListener('message', listener) };
        };
        this.sequence = 0;
        this.nextReference = 1;
        this.nextBreakpoint = 1;
        this.references = new Map();
        this.breakpoints = new Map();
        this.sourceBreakpoints = new Map();
        this.scriptUrls = new Map();
        this.sourceReferences = new Map();
        this.callFrames = [];
        this.filters = [];
        this.started = false;
        this.ended = false;
    }

    send(message) { this.messages.emit('message', { seq: ++this.sequence, ...message }); }
    event(event, body = {}) { this.send({ type: 'event', event, body }); }
    response(request, body = {}, error) {
        this.send({ type: 'response', request_seq: request.seq, command: request.command,
            success: !error, ...(error ? { message: error.message } : { body }) });
    }
    output(text, category = 'console') { this.event('output', { category, output: text + '\n' }); }

    handleMessage(request) {
        if (request.type !== 'request') return;
        this.dispatch(request).catch(error => {
            this.response(request, {}, error);
            if (request.command === 'launch') {
                this.output(error.message, 'stderr');
                this.finish();
            }
        });
    }

    async dispatch(request) {
        const args = request.arguments ?? {};
        switch (request.command) {
            case 'initialize':
                this.response(request, {
                    supportsConfigurationDoneRequest: true, supportsEvaluateForHovers: true,
                    supportsConditionalBreakpoints: true, supportsExceptionInfoRequest: true,
                    supportsTerminateRequest: true,
                    exceptionBreakpointFilters: [
                        { filter: 'all', label: '所有异常', default: false },
                    ],
                });
                return;
            case 'launch':
                this.config = args;
                this.program = path.resolve(args.program);
                this.sourceUrl = pathToFileURL(this.program).href;
                this.source = await readFile(this.program, 'utf8');
                if (Buffer.byteLength(this.source) > 16 * 1024 * 1024) throw new Error('脚本超过 16 MB，无法同步。');
                this.project = await collectProject(this.program, this.source, args);
                await this.manager.connect(args.device);
                if (!this.manager.capabilities?.includes('projectModules')) throw new Error('手机版本尚不支持项目同步，请先更新一次 XpAuto APK。');
                this.client = new ProtocolClient(`${this.manager.endpoint}/debug`);
                this.client.on('notification', (method, params) => this.notification(method, params));
                this.client.on('closed', error => {
                    if (!this.ended) this.output(error.message, 'stderr');
                    this.finish();
                });
                await this.client.connect();
                await this.client.request('Runtime.enable');
                if (!args.noDebug) await this.client.request('Debugger.enable');
                this.output(`[XpAuto] 已连接 ${this.manager.serial}，脚本已同步，等待断点配置。`);
                this.response(request);
                this.event('initialized');
                return;
            case 'setBreakpoints':
                return this.setBreakpoints(request, args);
            case 'setExceptionBreakpoints':
                this.filters = args.filters ?? [];
                if (!this.config.noDebug) await this.client.request('Debugger.setPauseOnExceptions', {
                    state: this.filters.includes('all') ? 'all' : 'none',
                });
                this.response(request);
                return;
            case 'configurationDone':
                if (!this.started) {
                    this.started = true;
                    if (this.config.stopOnEntry && !this.config.noDebug) {
                        this.pauseOnEntry = true;
                        const entry = await this.client.request('Debugger.setInstrumentationBreakpoint', { instrumentation: 'beforeScriptExecution' });
                        this.entryBreakpoint = entry.breakpointId;
                    }
                    await this.client.request('XpAuto.run', {
                        source: this.source, resourceName: this.sourceUrl, noDebug: !!this.config.noDebug, project: this.project,
                    });
                }
                this.response(request);
                return;
            case 'threads':
                this.response(request, { threads: [{ id: 1, name: 'Android JavaScript' }] });
                return;
            case 'stackTrace': {
                const start = args.startFrame ?? 0;
                const frames = this.callFrames.slice(start, args.levels ? start + args.levels : undefined);
                this.response(request, { stackFrames: frames.map((frame, index) => ({
                    id: start + index + 1, name: frame.functionName || '<main>',
                    source: this.sourceFor(frame.url || this.scriptUrls.get(frame.location.scriptId), frame.location.scriptId),
                    line: frame.location.lineNumber + 1, column: frame.location.columnNumber + 1,
                })), totalFrames: this.callFrames.length });
                return;
            }
            case 'scopes': {
                const frame = this.frame(args.frameId);
                this.response(request, { scopes: frame.scopeChain.map(scope => ({
                    name: ({ local: 'Local', global: 'Global', closure: 'Closure', script: 'Script', block: 'Block' })[scope.type] ?? scope.type,
                    presentationHint: scope.type === 'local' ? 'locals' : undefined,
                    expensive: scope.type === 'global',
                    variablesReference: this.reference(scope.object.objectId),
                })) });
                return;
            }
            case 'variables': {
                const objectId = this.references.get(args.variablesReference);
                if (!objectId) throw new Error('变量引用已失效，请在暂停时重新展开。');
                const { result } = await this.client.request('Runtime.getProperties', { objectId, ownProperties: true, generatePreview: true });
                const variables = result.filter(property => property.value).map(property => this.variable(property.name, property.value));
                this.response(request, { variables: variables.slice(args.start ?? 0,
                    args.count ? (args.start ?? 0) + args.count : undefined) });
                return;
            }
            case 'evaluate': {
                const expression = { expression: args.expression, generatePreview: true };
                const value = args.frameId != null
                    ? await this.client.request('Debugger.evaluateOnCallFrame', { ...expression, callFrameId: this.frame(args.frameId).callFrameId })
                    : await this.client.request('Runtime.evaluate', { ...expression, includeCommandLineAPI: true, replMode: args.context === 'repl' });
                if (value.exceptionDetails) throw new Error(value.exceptionDetails.exception?.description ?? value.exceptionDetails.text);
                const variable = this.variable('', value.result);
                this.response(request, { result: variable.value, type: variable.type, variablesReference: variable.variablesReference });
                return;
            }
            case 'continue': case 'next': case 'stepIn': case 'stepOut': case 'pause': {
                const method = { continue: 'resume', next: 'stepOver', stepIn: 'stepInto', stepOut: 'stepOut', pause: 'pause' }[request.command];
                this.stepping = ['next', 'stepIn', 'stepOut'].includes(request.command);
                await this.client.request(`Debugger.${method}`);
                this.response(request, request.command === 'continue' ? { allThreadsContinued: true } : {});
                return;
            }
            case 'exceptionInfo':
                this.response(request, { exceptionId: this.exception?.className ?? 'JavaScript Error',
                    description: this.exception?.description ?? 'JavaScript exception', breakMode: 'always' });
                return;
            case 'source': {
                const scriptId = this.sourceReferences.get(args.sourceReference);
                if (!scriptId) throw new Error('源码引用已失效');
                const { scriptSource } = await this.client.request('Debugger.getScriptSource', { scriptId });
                this.response(request, { content: scriptSource, mimeType: 'text/javascript' });
                return;
            }
            case 'disconnect': case 'terminate':
                await this.manager.stop().catch(error => this.output(error.message, 'stderr'));
                this.response(request);
                this.finish();
                this.client?.close();
                return;
            case 'setFunctionBreakpoints': this.response(request, { breakpoints: [] }); return;
            default: throw new Error(`暂不支持调试请求：${request.command}`);
        }
    }

    async setBreakpoints(request, args) {
        const sourcePath = path.resolve(args.source.path);
        for (const breakpointId of this.sourceBreakpoints.get(sourcePath) ?? []) {
            await this.client.request('Debugger.removeBreakpoint', { breakpointId });
            this.breakpoints.delete(breakpointId);
        }
        const ids = [];
        const breakpoints = [];
        for (const breakpoint of args.breakpoints ?? []) {
            const id = this.nextBreakpoint++;
            if (this.config.noDebug) { breakpoints.push({ id, verified: false, line: breakpoint.line }); continue; }
            const { breakpointId, locations } = await this.client.request('Debugger.setBreakpointByUrl', {
                url: pathToFileURL(sourcePath).href, lineNumber: breakpoint.line - 1,
                columnNumber: (breakpoint.column ?? 1) - 1, condition: breakpoint.condition ?? '',
            });
            const stored = { id, sourcePath, line: breakpoint.line };
            this.breakpoints.set(breakpointId, stored);
            ids.push(breakpointId);
            breakpoints.push({ id, verified: locations.length > 0,
                line: locations[0] ? locations[0].lineNumber + 1 : breakpoint.line,
                source: { name: path.basename(sourcePath), path: sourcePath } });
        }
        this.sourceBreakpoints.set(sourcePath, ids);
        this.response(request, { breakpoints });
    }

    notification(method, params) {
        switch (method) {
            case 'Debugger.scriptParsed': this.scriptUrls.set(params.scriptId, params.url); break;
            case 'Debugger.breakpointResolved': {
                const breakpoint = this.breakpoints.get(params.breakpointId);
                if (breakpoint) this.event('breakpoint', { reason: 'changed', breakpoint: {
                    id: breakpoint.id, verified: true, line: params.location.lineNumber + 1,
                    source: { name: path.basename(breakpoint.sourcePath), path: breakpoint.sourcePath },
                } });
                break;
            }
            case 'Debugger.paused': {
                if (this.pauseOnEntry && params.reason !== 'exception') {
                    const top = params.callFrames[0];
                    const url = top?.url || this.scriptUrls.get(top?.location.scriptId);
                    if (url !== this.sourceUrl) {
                        this.client.request('Debugger.resume').catch(error => this.output(error.message, 'stderr'));
                        return;
                    }
                }
                this.callFrames = params.callFrames;
                this.references.clear();
                this.exception = params.data;
                const reason = this.pauseOnEntry ? 'entry' : params.reason === 'exception' || params.reason === 'promiseRejection'
                    ? 'exception' : params.hitBreakpoints?.length ? 'breakpoint' : this.stepping ? 'step' : 'pause';
                this.pauseOnEntry = false;
                if (this.entryBreakpoint) {
                    this.client.request('Debugger.removeBreakpoint', { breakpointId: this.entryBreakpoint })
                        .catch(error => this.output(error.message, 'stderr'));
                    this.entryBreakpoint = undefined;
                }
                this.stepping = false;
                this.event('stopped', { reason, threadId: 1, allThreadsStopped: true,
                    ...(reason === 'exception' ? { text: params.data?.description } : {}) });
                const frame = params.callFrames[0];
                if (frame) this.output(`[XpAuto] 已暂停 ${frame.url || this.scriptUrls.get(frame.location.scriptId)}:${frame.location.lineNumber + 1}`);
                break;
            }
            case 'Debugger.resumed': this.event('continued', { threadId: 1, allThreadsContinued: true }); break;
            case 'Runtime.consoleAPICalled':
                this.output(remoteText(params.args), params.type === 'error' ? 'stderr' : 'console');
                break;
            case 'XpAuto.finished':
                this.output(`[XpAuto] ${params.cancelled ? '已停止' : params.success ? '已完成' : '运行失败'} (${params.elapsedMs} ms)`);
                if (this.config.noDebug) { this.finish(); this.client?.close(); }
                break;
            case 'XpAuto.error': this.output(params.message, 'stderr'); this.finish(); break;
        }
    }

    frame(id) {
        const frame = this.callFrames[id - 1];
        if (!frame) throw new Error('调用栈已失效，请重新暂停脚本。');
        return frame;
    }
    reference(objectId) {
        if (!objectId) return 0;
        const reference = this.nextReference++;
        this.references.set(reference, objectId);
        return reference;
    }
    variable(name, value) {
        return { name, type: value.type,
            value: value.type === 'string' ? JSON.stringify(value.value)
                : value.unserializableValue ?? value.description ?? String(value.value),
            variablesReference: this.reference(value.objectId) };
    }
    sourceFor(url, scriptId) {
        if (url?.startsWith('file:')) {
            const sourcePath = fileURLToPath(url);
            return { name: path.basename(sourcePath), path: sourcePath };
        }
        if (!scriptId) return undefined;
        const reference = 50000 + Number(scriptId);
        this.sourceReferences.set(reference, scriptId);
        return { name: url || '<anonymous>', sourceReference: reference };
    }
    finish() { if (!this.ended) { this.ended = true; this.event('terminated'); } }
    dispose() { this.client?.close(); this.messages.removeAllListeners(); }
}

module.exports = { XpAutoDebugAdapter };
