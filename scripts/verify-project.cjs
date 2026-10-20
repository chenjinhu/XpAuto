'use strict';
// 在本地授权设备上检查真实 V8/Java 桥接、项目同步与依赖断点，不构建或安装应用。
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const os = require('node:os');
const {pathToFileURL} = require('node:url');
const {DeviceManager} = require('../vscode-extension/src/device');
const {ProtocolClient} = require('../vscode-extension/src/protocol');
const {collectProject} = require('../vscode-extension/src/project');
const manager = new DeviceManager({adbPath:()=>process.env.XPAUTO_ADB || 'adb', devicePort:()=>9229, log:()=>{}, pickDevice:async()=>undefined});
const delay = ms => new Promise(resolve=>setTimeout(resolve,ms));
let client;
const events=[];
async function until(predicate,label) {
    for(let i=0;i<500;i++){const value=predicate();if(value)return value;await delay(30);}
    throw new Error('超时：'+label+' '+JSON.stringify(events.filter(e=>['XpAuto.finished','Runtime.consoleAPICalled','Debugger.scriptParsed'].includes(e.method)).map(e=>e.method==='Debugger.scriptParsed'?{method:e.method,url:e.params.url}:e)));
}
async function evaluate(expression) {
    const value=await client.request('Runtime.evaluate',{expression,returnByValue:true});
    assert.equal(value.exceptionDetails,undefined,JSON.stringify(value.exceptionDetails));
    return value.result.value;
}
(async()=>{
    const root=await fs.mkdtemp(path.join(os.tmpdir(),'xpauto-project-device-'));
    try {
        await manager.connect();
        assert.equal(manager.state.state,'idle','请先结束其他调试会话');
        client=new ProtocolClient(manager.endpoint+'/debug');
        client.on('notification',(method,params)=>events.push({method,params}));
        await client.connect();await client.request('Runtime.enable');await client.request('Debugger.enable');
        const source=`
function assert(ok,message){if(!ok)throw new Error(message);}
var task = {local:true};
function getprop(){return 'local';}
const helper=require('./lib/helper');
assert(helper.value===require('./config.json').expected,'模块与 JSON');
assert(require('./lib/helper')===helper,'模块缓存');
assert(require('./lib/cycle-a').fromB==='a','循环依赖');
assert(task.local && getprop()==='local','入口作用域');
const output=__dirname+'/output/data.txt';
const exact='\\ufeff中文\\r\\nsecond\\rlast';
assert(file.writeFile(exact,output),'写文本：'+file.getErrorMsg());
assert(file.readFile(output)===exact,'换行和 BOM');
assert(file.writeToTxt(output,'x') && file.readFile(output)==='x','短覆盖');
assert(file.appendFile(output,'\\r\\ny') && file.readFile(output)==='x\\r\\ny','追加');
const binary=file.readFileBytes(__dirname+'/assets/data.bin');
assert(binary && binary.length===4 && (binary[2]&255)===255,'字节读取');
assert(file.writeFileBytes(__dirname+'/output/copy.bin',binary),'字节写回：'+file.getErrorMsg());
assert(file.writeFileBytes(__dirname+'/output/copy.bin',new Int8Array([0,-128,-1,127]),true),'字节追加');
assert(file.readFileBytes(__dirname+'/output/copy.bin').length===8,'二进制长度');
assert(file.readFile(__dirname+'/missing')===null && file.getErrorMsg().length>0,'失败与空文件区分');
assert(file.writeFile('',output) && file.readFile(output)==='' && file.getErrorMsg()==='','空文件');
assert(file.deleteLine(__dirname+'/missing',0,null)===false && !file.exists(__dirname+'/missing'),'删除失败保护');
const apps=utils.getInstalledApps();
assert(apps.some(app=>app.packageName==='com.vixmobi.auto'),'应用列表');
assert(device.getScreenWidth()>0 && device.getModel().length>0,'设备信息');
const sel=clzMatch('.*');
assert(sel.constructor.name==='S' && sel.attr.expression.includes('clz'),'选择器兼容');
const n=sel.getOneNodeInfo(1000);
assert(n && n.constructor.name==='NodeInfo' && typeof n.text==='string' && typeof n.clickable==='boolean','节点属性');
assert(n.bounds && typeof n.bounds.left==='number','节点矩形');
const collection=sel.find();
assert(collection.size()>0 && typeof collection.get(0).text==='string','集合兼容');
collection.recycle();n.recycle();
setNodeCompatibility('xpauto');
const old=clzMatch('.*').getOneNodeInfo();assert(typeof old.text==='function','旧方法模式');old.recycle();
assert(hasFloatViewPermission() && setLogViewSizeEx({w:600,h:500,textSize:12,title:'API 验证',showCloseBtn:true}),'配置日志窗');
assert(showLogWindow() && closeLogWindow() && closeCtrlWindow() && showCtrlWindow(),'内置窗口控制');
globalThis.__projectResult={value:helper.value,model:device.getModel(),files:binary.length};
`;
        await fs.mkdir(path.join(root,'lib'));await fs.mkdir(path.join(root,'assets'));
        await fs.writeFile(path.join(root,'main.js'),source);
        await fs.writeFile(path.join(root,'lib/helper.js'),'exports.value = 42;\nexports.ready = true;\n');
        await fs.writeFile(path.join(root,'lib/cycle-a.js'),"exports.name='a';exports.fromB=require('./cycle-b').name;");
        await fs.writeFile(path.join(root,'lib/cycle-b.js'),"exports.name=require('./cycle-a').name;");
        await fs.writeFile(path.join(root,'config.json'),'{"expected":42}');
        await fs.writeFile(path.join(root,'assets/data.bin'),Buffer.from([0,128,255,127]));
        const program=path.join(root,'main.js');
        const project=await collectProject(program,source,{resources:['assets'],nodeMode:'easyclick'});
        const breakpoint=await client.request('Debugger.setBreakpointByUrl',{url:pathToFileURL(path.join(root,'lib/helper.js')).href,lineNumber:1});
        await client.request('XpAuto.run',{source,resourceName:pathToFileURL(program).href,project});
        const paused=await until(()=>events.find(e=>e.method==='Debugger.paused'),'依赖断点');
        assert.equal(paused.params.callFrames[0].location.lineNumber,1);
        const local=await client.request('Debugger.evaluateOnCallFrame',{callFrameId:paused.params.callFrames[0].callFrameId,expression:'exports.value',returnByValue:true});
        assert.equal(local.result.value,42);
        await client.request('Debugger.removeBreakpoint',{breakpointId:breakpoint.breakpointId});
        await client.request('Debugger.resume');
        const done=await until(()=>events.find(e=>e.method==='XpAuto.finished'),'项目完成');
        assert.equal(done.params.success,true,done.params.error);
        assert.equal((await evaluate('__projectResult')).value,42);
        console.log('PASS: UTF-8/binary IO, device/apps, overlay controls, EasyClick nodes, CommonJS/JSON/cycles, dependency breakpoint');
        events.length=0;
        await fs.writeFile(path.join(root,'lib/helper.js'),'exports.value=43;');
        const second='globalThis.__projectResult=require("./lib/helper").value;';
        const updated=await collectProject(program,second);
        await client.request('XpAuto.run',{source:second,resourceName:pathToFileURL(program).href,project:updated});
        const next=await until(()=>events.find(e=>e.method==='XpAuto.finished'),'更新依赖');
        assert.equal(next.params.success,true,next.params.error);
        assert.equal(await evaluate('__projectResult'),43);
        assert.equal(await evaluate('file.exists(__dirname+"/assets/data.bin")'),false,'新快照不能残留已移除的资源');
        console.log('PASS: updated dependency reloaded and removed resources absent on next run');
    } finally {
        client?.close();await manager.disconnect();await fs.rm(root,{recursive:true,force:true});
    }
})().catch(error=>{console.error(error);process.exitCode=1;});
