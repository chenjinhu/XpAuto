// 每轮脚本独立缓存；先登记 exports 再执行工厂函数，支持 CommonJS 循环依赖。
const __xpautoModules = (() => {
    let cache = Object.create(null), entry;
    function check(info) {
        if (info.error) { const error = new Error(info.error); error.code = info.code; throw error; }
        return info;
    }
    function localRequire(parent) {
        const resolve = name => {
            if (typeof name !== 'string' || !name) throw new TypeError('require 参数必须为非空字符串');
            return check(JSON.parse(__xpautoModuleFiles.resolveJson(name, parent)));
        };
        const require = name => load(resolve(name));
        require.resolve = name => resolve(name).filename;
        Object.defineProperty(require, 'cache', {get: () => cache});
        Object.defineProperty(require, 'main', {get: () => cache[entry.filename]});
        return require;
    }
    function load(info) {
        if (cache[info.filename]) return cache[info.filename].exports;
        const module = {id:info.filename, filename:info.filename, exports:{}, loaded:false};
        cache[info.filename] = module;
        try {
            let source = (info.source === undefined ? check(JSON.parse(__xpautoModuleFiles.readJson(info.filename))).source : info.source).replace(/^\uFEFF/, '');
            if (info.json) module.exports = JSON.parse(source);
            else {
                source = source.replace(/^#![^\r\n]*/, match => ' '.repeat(match.length));
                // 包装前缀与第一行同行，保持后续源码行号；独立 sourceURL 供 VS Code 定位依赖文件。
                const factory = (0, eval)('(function(require,module,exports,__filename,__dirname){' + source
                    + '\n})\n//# sourceURL=' + info.url.replace(/[\r\n]/g, ''));
                const require = localRequire(info.filename);
                module.require = require;
                factory.call(module.exports, require, module, module.exports, info.filename, info.dirname);
            }
            module.loaded = true;
            return module.exports;
        } finally {
            // 使用 finally 清缓存，让原异常直接向上传递，避免调试器在 SDK rethrow 处再次暂停。
            if (!module.loaded) delete cache[info.filename];
        }
    }
    return {
        reset() {
            cache = Object.create(null);
            entry = JSON.parse(__xpautoModuleFiles.entryJson());
            globalThis.require = localRequire(entry.filename);
            globalThis.__filename = entry.filename;
            globalThis.__dirname = entry.dirname;
        },
        run(source) { return load({...entry, source, json:false}); },
    };
})();
__xpautoModules.reset();
