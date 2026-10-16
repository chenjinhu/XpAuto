'use strict';
// 直接加载随 APK 发布的 JS，验证占位符、对象和边界值的实际输出。
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
function sdk() {
    const writes = [], toasts = [], clears = [];
    let enabled = true;
    const context = vm.createContext({ __xpautoLog: {
        enabled: () => enabled,
        write: (level, value) => writes.push({ level, value }),
        toast: (message, extra, duration) => toasts.push({ message, extra: JSON.parse(extra), duration }),
        setLogLevel: level => { enabled = level !== 'off'; return true; },
        clearLog: lines => clears.push(lines),
    } });
    vm.runInContext(fs.readFileSync(path.join(__dirname, '../../app/src/main/assets/logging.js'), 'utf8'), context);
    return { run: script => vm.runInContext(script, context), writes, toasts, clears };
}
test('四种等级、占位符、缺少与多余参数保持可读', () => {
    const { run, writes } = sdk();
    run('logd("进度 {}/{}", 1, 3); logi("{} {}", "a"); logw("警告", 7, false); loge("错误 {}", null);');
    assert.deepEqual(writes, [
        { level: 'debug', value: '进度 1/3' }, { level: 'info', value: 'a {}' },
        { level: 'warn', value: '警告 7 false' }, { level: 'error', value: '错误 null' },
    ]);
});
test('对象、循环引用、BigInt 和 Error 不会让日志中断业务', () => {
    const { run, writes } = sdk();
    run('const value={name:"中文", count:1n}; value.self=value; logd(value); logi(undefined, NaN); loge(new Error("测试错误")); logw({literal:"{}"},42);');
    assert.deepEqual(JSON.parse(writes[0].value), { name: '中文', count: '1n', self: '[Circular]' });
    assert.equal(writes[1].value, 'undefined NaN'); assert.match(writes[2].value, /Error: 测试错误/);
    assert.equal(writes[3].value, '{"literal":"{}"} 42');
});
test('关闭等级后不读取消息对象，Toast 参数和清除行数正确传入桥接', () => {
    const { run, writes, toasts, clears } = sdk();
    run('setLogLevel("off", false); logd({get danger(){throw Error("不应读取");}}); toast("定制",{x:10,duration:800}); toast1("短"); toast2("长"); clearLog(2); clearLog();');
    assert.deepEqual(writes, []);
    assert.deepEqual(toasts, [ { message: '定制', extra: {x:10,duration:800}, duration:2000 }, { message: '短', extra:null, duration:2000 }, { message:'长',extra:null,duration:3500 } ]);
    assert.deepEqual(clears, [2,-1]);
    assert.throws(() => run('clearLog(-2)'), /行数/); assert.throws(() => run('clearLog(1.5)'), /行数/);
});
test('选择器说明在顶层直接显示，嵌套对象与数组保留 JSON 结构', () => {
    const { run, writes, toasts } = sdk();
    run(`const selected = {toJSON: () => 'text("搜索").enabled(true)'};
        logd(selected); logi('选择器：{}', selected); logw('条件', selected); loge(selected);
        logd({selected}); logd([selected]); toast1(selected);
        logd({toJSON: () => undefined, toString: () => '回退说明'});`);
    const description = 'text("搜索").enabled(true)';
    assert.deepEqual(writes.slice(0, 4), [
        {level:'debug', value:description}, {level:'info', value:'选择器：'+description},
        {level:'warn', value:'条件 '+description}, {level:'error', value:description},
    ]);
    assert.deepEqual(JSON.parse(writes[4].value), {selected:description});
    assert.deepEqual(JSON.parse(writes[5].value), [description]);
    assert.equal(toasts[0].message, description);
    assert.equal(writes[6].value, '回退说明');
});
