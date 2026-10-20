'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const os = require('node:os');
const vm = require('node:vm');
const {collectProject} = require('../src/project');
const {SdkLanguageService} = require('../src/language-service');
const asset = name => fs.readFile(path.join(__dirname, '../../app/src/main/assets', name), 'utf8');

test('项目同步保留二进制，仅显式包含资源，拒绝越界及符号链接', async () => {
    const root = await fs.mkdtemp(path.join(os.tmpdir(), 'xpauto-project-'));
    try {
        await fs.writeFile(path.join(root, 'main.js'), 'old');
        await fs.mkdir(path.join(root, 'assets'));
        await fs.writeFile(path.join(root, 'assets/data.bin'), Buffer.from([0, 128, 255]));
        await fs.writeFile(path.join(root, 'secret.txt'), 'excluded');
        await fs.writeFile(path.join(root, '.env'), 'excluded');
        await fs.writeFile(path.join(root, 'helper.js'), 'exports.value=1;');
        let project = await collectProject(path.join(root, 'main.js'), 'new');
        assert.deepEqual(Object.keys(project.files).sort(), ['helper.js', 'main.js']);
        project = await collectProject(path.join(root, 'main.js'), 'new', {resources:['assets'], nodeMode:'easyclick'});
        assert.deepEqual(Buffer.from(project.files['assets/data.bin'], 'base64'), Buffer.from([0,128,255]));
        assert.equal(Buffer.from(project.files['main.js'], 'base64').toString(), 'new');
        await assert.rejects(collectProject(path.join(root, 'main.js'), '', {resources:['../outside']}), /projectRoot/);
        await fs.symlink(os.tmpdir(), path.join(root, 'linked'));
        await assert.rejects(collectProject(path.join(root, 'main.js'), ''), /符号链接/);
    } finally { await fs.rm(root, {recursive:true, force:true}); }
});

test('CommonJS 缓存、循环依赖、JSON、失败重试、每轮重置及模块作用域', async () => {
    const sources = {
        '/p/a.js': 'exports.value=2; exports.b=require("./b");',
        '/p/b.js': 'exports.a=require("./a").value;',
        '/p/conf.json': '\ufeff{"answer":42}',
        '/p/bad.js': 'throw new Error("broken");',
    };
    let reads = 0;
    const describe = filename => ({filename,dirname:'/p',url:'file://' + filename,json:filename.endsWith('.json')});
    const context = vm.createContext({__xpautoModuleFiles:{
        entryJson: () => JSON.stringify(describe('/p/main.js')),
        resolveJson: (name, parent) => {
            let filename=path.resolve(path.dirname(parent), name);
            if (!path.extname(filename)) filename += '.js';
            return JSON.stringify(sources[filename] === undefined ? {error:'missing',code:'MODULE_NOT_FOUND'} : describe(filename));
        },
        readJson: name => { reads++; return JSON.stringify({...describe(name),source:sources[name]}); },
    }});
    vm.runInContext(await asset('modules.js'), context);
    const run = source => vm.runInContext(source, context);
    assert.equal(run('require("./a").b.a'), 2);
    assert.equal(run('require("./a")===require("./a")'), true);
    assert.equal(reads, 2);
    assert.equal(run('require("./conf.json").answer'), 42);
    assert.throws(() => run('require("./missing")'), error => error.code==='MODULE_NOT_FOUND');
    assert.throws(() => run('require("./bad")'), /broken/);
    sources['/p/bad.js']='module.exports=9;';
    assert.equal(run('require("./bad")'), 9);
    run('__xpautoModules.run("var task=1; function getprop(){return 3}; module.exports=getprop()+task;")');
    assert.equal(run('require.main.exports'), 4);
    assert.equal(run('typeof task'), 'undefined');
    run('__xpautoModules.reset()');
    assert.equal(run('Object.keys(require.cache).length'), 0);
});

test('EasyClick 使用原始布尔值和节点属性，关系查询与动作透传原生对象', async () => {
    const node = {text:()=> 'hello',clickable:()=>false,bounds:()=>({left:1,top:2,right:3,bottom:4}),info:()=>({text:'hello'}),parent(){return this;},findOne(selector){assert.equal(selector, nativeSelector);return this;}};
    const nativeSelector={getOneNodeInfo:()=>node,toString:()=> 'text("hello")',text(){return this;},getNodeInfo:()=>[node]};
    const context=vm.createContext({node,nativeSelector});
    vm.runInContext(await asset('node-compat.js'), context);
    const run = source => vm.runInContext(source, context);
    assert.equal(run('__xpautoNodeCompat.wrap(node).text()'), 'hello');
    assert.equal(run('asEasyClick(node).clickable === false'), true);
    assert.equal(run('asEasyClick(node).parent().text'), 'hello');
    assert.equal(run('asEasyClick(node).findOne(asEasyClick(nativeSelector)).text'), 'hello');
    assert.equal(run('asEasyClick(nativeSelector).constructor.name'), 'S');
    assert.equal(run('asEasyClick(node).constructor.name'), 'NodeInfo');
    assert.equal(run('asEasyClick(nativeSelector).attr.expression'), 'text("hello")');
    run('setNodeCompatibility("easyclick")');
    assert.equal(run('__xpautoNodeCompat.wrap(nativeSelector).getNodeInfo()[0].bounds.right'), 3);
    assert.equal(run('new Rect(1,2,5,6).centerX()'), 3);
});

test('新 API 的中文代码提示及属性节点推断', () => {
    const language=new SdkLanguageService(path.join(__dirname, '../types/xpauto.d.ts'));
    try {
        for (const [source, name] of [['device.', 'getScreenWidth'],['utils.', 'getInstalledApps'],['file.', 'readFileBytes'],['setCtrl','setCtrlViewSizeEx'],['require.', 'resolve'],['const n=asEasyClick(text("a").getOneNodeInfo()); n.', 'text']]) {
            language.setDocument('/tmp/xpauto-new-hints.js', source);
            assert.ok(language.completions(source.length).some(item=>item.name===name),source);
        }
        const source='const n=asEasyClick(text("a").getOneNodeInfo()); n.text';
        language.setDocument('/tmp/xpauto-new-hints.js', source);
        assert.match(language.hover(source.length-2).signature, /text: string/);
        language.setDocument('/tmp/xpauto-easyclick-check.ts', `
            const n=asEasyClick(text('a').getOneNodeInfo());
            if(n) { click(n); scrollForward(n); const count:number=n.childCount(); }
            const selector=asEasyClick(text('a'));
            click(selector); const nodes=getNodeInfo(selector);
            const textValue:string=nodes[0].text;
        `);
        assert.deepEqual(language.service.getSemanticDiagnostics('/tmp/xpauto-easyclick-check.ts').map(item=>item.messageText), []);
    } finally { language.dispose(); }
});
