'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const vm=require('node:vm');
const fs=require('node:fs');
const path=require('node:path');
const {SdkLanguageService}=require('../src/language-service');
test('新增模块提示和普通返回类型可推断',()=>{
 const language=new SdkLanguageService(path.join(__dirname,'../types/xpauto.d.ts'));
 try{for(const [source,expected] of [['thread.','execAsync'],['sqlite.','connectOrCreateDb'],['utils.','createQRCode'],['encodeDecoder.','aesEncrypt'],['multiT','multiTouch'],['imeInput','imeInputText'],['setFetch','setFetchNodeMode'],['requestRuntime','requestRuntimePermission'],['const bitmap=utils.createQRCode("a",320,320);bitmap.','recycle']]){
 language.setDocument('/tmp/xpauto-modules-hints.js',source,1);assert.ok(language.completions(source.length).some(item=>item.name===expected),source);
 }}finally{language.dispose();}
});
test('随机函数边界、配置默认值和严格布尔语义',()=>{
 const source=fs.readFileSync(path.join(__dirname,'../../app/src/main/assets/utils.js'),'utf8');
 const context=vm.createContext({});vm.runInContext(source,context);
 const run=expression=>vm.runInContext(expression,context);
 assert.equal(run('random(7,7)'),7);assert.equal(run('utils.getRatio(0)'),false);assert.equal(run('utils.getRatio(100)'),true);
 assert.throws(()=>run('random(9,1)'));assert.throws(()=>run('utils.randomInt(16)'));assert.throws(()=>run('utils.randomCharNumber(-1)'));
 assert.equal(run('utils.readConfigInt({},"a")'),0);assert.equal(run('utils.readConfigInt({a:"12"},"a")'),12);
 assert.equal(run('utils.readJSONString({},"a")'),'');assert.equal(run('utils.isTrue("true")'),false);
 for(let i=0;i<100;i++){const number=run('utils.randomInt(15)');assert.ok(number>=1e14&&number<1e15);assert.match(run('utils.randomCharNumber(30)'),/^[a-zA-Z0-9]{30}$/);}
});
