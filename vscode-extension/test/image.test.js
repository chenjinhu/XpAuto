'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const { SdkLanguageService } = require('../src/language-service');

test('截图提示覆盖授权、返回对象和文件保存参数', () => {
    const language = new SdkLanguageService(path.join(__dirname, '../types/xpauto.d.ts'));
    try {
        for (const [source, expected] of [
            ['image.', 'requestScreenCapture'], ['image.capture', 'captureToFile'],
            ['const img=image.captureFullScreen();img.', 'isRecycled'],
            ['const bitmap=image.captureScreenBitmapEx();bitmap.', 'getWidth'],
        ]) {
            language.setDocument('/tmp/xpauto-image-hints.js', source, 1);
            assert.ok(language.completions(source.length).some(item => item.name === expected), source);
        }
    } finally { language.dispose(); }
});

test('截图失败保留错误、保存失败仍回收临时图片、非法参数不触发授权', () => {
    const bitmap = {}, calls = [];
    const context = vm.createContext({ __xpautoImage: {
        requestScreenCapture: (...args) => { calls.push(args); return true; },
        captureBitmap: (...args) => { calls.push(args); return bitmap; },
        saveBitmap: () => false,
        recycle: value => calls.push(value),
    } });
    vm.runInContext(fs.readFileSync(path.join(__dirname, '../../app/src/main/assets/image.js'), 'utf8'), context);
    const run = source => vm.runInContext(source, context);
    assert.throws(() => run('image.requestScreenCapture(-1)'));
    assert.throws(() => run('image.requestScreenCapture(1000,3)'));
    assert.throws(() => run('image.captureScreenBitmap("png",0,0,100,100,101)'));
    assert.throws(() => run('image.captureScreen(3,NaN,0,100,100)'));
    assert.equal(calls.length, 0);
    assert.equal(run('image.requestScreenCapture()'), true);
    assert.deepEqual(calls.shift(), [10000, 0]);
    assert.equal(run('image.captureToFile("shot.png")'), false);
    assert.deepEqual(calls.shift(), [3, -1, -1, -1, -1]);
    assert.equal(calls.shift(), bitmap);
});
