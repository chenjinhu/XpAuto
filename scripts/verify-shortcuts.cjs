// 使用 XpAuto 自己的 debug 页面验证快捷操作；输入法和设置在 finally 中恢复。
const assert=require('node:assert/strict');
const fs=require('node:fs');
const {DeviceManager}=require('../vscode-extension/src/device');
const {ProtocolClient}=require('../vscode-extension/src/protocol');
const manager=new DeviceManager({adbPath:()=>process.env.XPAUTO_ADB||'adb',devicePort:()=>9229,log:()=>{},pickDevice:async()=>undefined});
const adb=args=>manager.adb(['-s',manager.serial,...args]);
const app='com.vixmobi.auto',events=[];
let client,oldIme,oldEnabled;
const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms));
async function until(check,label,timeout=7000){let end=Date.now()+timeout;do{let value=await check();if(value)return value;await delay(50)}while(Date.now()<end);throw Error('等待超时 '+label)}
async function evaluate(expression){const r=await client.request('Runtime.evaluate',{expression,returnByValue:true});assert.equal(r.exceptionDetails,undefined,JSON.stringify(r.exceptionDetails));return r.result.value;}
async function check(expression,expected=true){assert.deepEqual(await evaluate(expression),expected,expression)}
async function start(activity){await adb(['shell','am','start','-f','0x14000000','-n',app+'/'+app+'.'+activity]);await delay(300);}
async function open(){client=new ProtocolClient(manager.endpoint+'/debug',20000);client.on('notification',(name,params)=>events.push({name,params}));await client.connect();await client.request('Runtime.enable');}
async function run(source){return client.request('XpAuto.run',{source,resourceName:'xpauto://verification/modules.js',noDebug:true})}
const finished=id=>until(()=>events.find(e=>e.name==='XpAuto.finished'&&e.params.runId===id)?.params,'脚本结束');
(async()=>{try{
 await manager.connect();await open();const pid=await adb(['shell','pidof',app]);
 await start('debug.SelectorFixtureActivity');
 await check(`globalThis.p='com.vixmobi.auto:id/selector_'; has(text('测试项'))`);
 await check(`clickText('测试点击') && waitExistNode(text('点击成功'),1000)`);
 await check(`longClick(text('测试点击')) && waitExistNode(text('长按成功'),1000)`);
 await check(`clickRandomEx(text('测试点击')) && waitExistNode(text('点击成功'),1000)`);
 await check(`inputText(id(p+'input'),'快捷输入') && getText(id(p+'input'))[0]==='快捷输入'`);
 await check(`clearTextField(id(p+'input')) && getText(id(p+'input'))[0]===''`);
 await check(`setFocus(id(p+'input')) && pasteText(id(p+'input'),'剪贴板输入') && getText(id(p+'input'))[0]==='剪贴板输入'`);
 await check(`lastNodeEventTime()>0 && waitExistActivity('com.vixmobi.auto.debug.SelectorFixtureActivity',1000)`);
 await check(`clickText('测试点击');lockNode();clickEx(text('测试点击'));longClickEx(text('测试点击'));getText(id(p+'status'))[0]`,'点击成功');
 await check(`releaseNode();waitExistNode(text('长按成功'),1000)`);
 for(const algorithm of ['dsf','bsf','nsf'])for(const method of [1,2,3])await check(`setFetchNodeMode(2,true,true,'${algorithm}');setNodeDumpParam({dumpMethod:${method},maxDepth:50});has(text('测试项'))`);
 await check(`setFetchNodeMode(1,true,true,'dsf');setNodeDumpParam({maxDepth:0});has(text('测试项'))`,false);
 await check(`setNodeDumpParam({maxDepth:80});setBlockNodeAttr('text');has(text('测试项'))`,false);
 await check(`setBlockNodeAttr('');has(text('测试项'))`);
 const xml=await evaluate('dumpXml()');assert.ok(xml.includes('测试项')&&xml.includes('<hierarchy'));fs.writeFileSync('/tmp/xpauto-node-dump.xml',xml);
 await check(`scrollForward(id(p+'scroll'))`);await check(`waitExistNode(textStartsWith('滚动位置：'),1000)`);
 console.log('PASS 点击/长按/输入/粘贴/滚动、节点快照、三种遍历与深度/属性设置');
 oldIme=await adb(['shell','settings','get','secure','default_input_method']);oldEnabled=await adb(['shell','settings','get','secure','enabled_input_methods']);
 await adb(['shell','ime','enable',app+'/.input.ScriptInputMethod']);await adb(['shell','ime','set',app+'/.input.ScriptInputMethod']);
 await until(()=>evaluate('currentIsOurIme()'),'选择脚本输入法');
 await check(`clearTextField(id(p+'input'));clickEx(id(p+'input'));true`);await delay(400);
 await check(`imeInputText(id(p+'input'),'输入法中文')`);await until(()=>evaluate(`getText(id(p+'input'))[0]==='输入法中文'`),'输入法文本');
 await check(`imeInputKeyCode(null,67)`);await until(()=>evaluate(`getText(id(p+'input'))[0]==='输入法中'`),'输入法删除');
 await check(`imeInputText(null,'—ec_close_input—')`);
 console.log('PASS 自带输入法提交中文、删除按键与键盘控制');
 await start('debug.GestureFixtureActivity');
 const status=async()=>{await delay(150);const value=JSON.parse(await evaluate(`getText(id('com.vixmobi.auto:id/selector_status'))[0]`));console.log('TOUCH',value);return value;};
 await check('press(300,700,100)');let result=await status();assert.equal(result.downs,1);assert.equal(result.ups,1);assert.ok(result.elapsed>=80);
 await check('drag(300,700,650,1000,200)');result=await status();assert.ok(result.elapsed>=700);assert.ok(result.lastX-result.firstX>300);
 await check('touchDown(300,700) && touchMove(450,700) && touchUp(600,700)');result=await status();assert.equal(result.ups,1);assert.ok(result.lastX-result.firstX>250);
 const fingers=[1,2,3].map(i=>[{action:0,x:180*i,y:800,pointer:i,delay:0},{action:2,x:180*i+40,y:1000,pointer:i,delay:200},{action:1,x:180*i+40,y:1000,pointer:i,delay:50}]);
 await check(`multiTouch(${fingers.map(JSON.stringify).join(',')},3000)`);result=await status();assert.equal(result.maxPointers,3);assert.equal(result.downs,3);assert.equal(result.ups,3);assert.ok(result.moves>0);
 console.log('PASS 系统实际收到精确按压、长按拖拽、连续单指与三指触摸');
 await evaluate(`globalThis.finishedTicks=0;`);
 let running=await run(`(()=>{sqlite.connectOrCreateDb('cleanup-test.db');setInterval(()=>finishedTicks++,20);thread.execAsync(()=>{while(true)sleep(20)});sleep(100);})()`);
 assert.equal((await finished(running.runId)).success,true);const ticks=await evaluate('finishedTicks');await delay(150);await check('finishedTicks',ticks);await check(`sqlite.query('SELECT 1')===null`);
 running=await run(`thread.execAsync(()=>{while(true){}});sleep(30000);`);await delay(300);await client.request('XpAuto.stop');assert.equal((await finished(running.runId)).cancelled,true);
 running=await run(`logd('停止后仍可再次运行');`);assert.equal((await finished(running.runId)).success,true);
 assert.equal(await adb(['shell','pidof',app]),pid);
 console.log('PASS 脚本正常结束清理线程/数据库、忙循环停止、停止后重新运行且进程未重启');
}catch(error){console.error("TEST FAILURE",error);throw error;}finally{
 if(oldIme)await adb(['shell','ime','set',oldIme]);
 if(oldEnabled)await adb(['shell','settings','put','secure','enabled_input_methods',"'"+oldEnabled.replaceAll("'","'\"'\"'")+"'"]);
 client?.close();if(manager.connected){await start('devtools.DeveloperActivity');await adb(['shell','run-as',app,'rm','-f','databases/cleanup-test.db']);}await manager.disconnect();
}})().catch(error=>{console.error(error);process.exitCode=1});
