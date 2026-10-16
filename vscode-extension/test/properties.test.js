'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const { SdkLanguageService } = require('../src/language-service');

test('系统属性全局补全、中文说明和参数提示', () => {
    const language = new SdkLanguageService(path.join(__dirname, '../types/xpauto.d.ts'));
    try {
        for (const name of ['getprop', 'setprop']) {
            const source = name.slice(0, 4);
            language.setDocument('/tmp/xpauto-property-hints.js', source, 1);
            assert.ok(language.completions(source.length).some(item => item.name === name));
            language.setDocument('/tmp/xpauto-property-hints.js', name + '(', 2);
            const signature = language.signatures(name.length + 1).items[0];
            assert.equal(signature.parameters.length, 4);
            assert.match(signature.documentation, /Android 系统属性/);
        }
    } finally { language.dispose(); }
});

test('属性默认权限、值类型转换和非法参数在执行前被拒绝', () => {
    const calls = [];
    const context = vm.createContext({ shell: {
        getprop: (...args) => { calls.push(args); return 'Pixel'; },
        setprop: (...args) => { calls.push(args); return false; },
    } });
    vm.runInContext(fs.readFileSync(path.join(__dirname, '../../app/src/main/assets/properties.js'), 'utf8'), context);
    const run = source => vm.runInContext(source, context);
    assert.equal(run('getprop("ro.product.model")'), 'Pixel');
    assert.deepEqual(calls.shift(), ['ro.product.model', '', false, 60000]);
    assert.equal(run('setprop("debug.xpauto.test", true)'), false);
    assert.deepEqual(calls.shift(), ['debug.xpauto.test', 'true', true, 60000]);
    run('setprop("debug.xpauto.test", 0, false, 500)');
    assert.deepEqual(calls.shift(), ['debug.xpauto.test', '0', false, 500]);
    for (const source of ['getprop("")', 'getprop("bad;id")', 'getprop("a..b")', 'getprop("bad\\n")',
        'getprop("valid", 1)', 'getprop("valid", "", "false")', 'getprop("valid", "", false, 0)',
        'setprop("valid", null)', 'setprop("valid", {})', 'setprop("valid", NaN)', 'setprop("valid", "\\0")']) {
        assert.throws(() => run(source), source);
    }
    assert.equal(calls.length, 0);
});
