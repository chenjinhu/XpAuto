// 验证真实子线程的断点、继续、停止和断连，保持同一应用进程。
const assert=require('node:assert/strict');const {DeviceManager}=require('../vscode-extension/src/device');const {ProtocolClient}=require('../vscode-extension/src/protocol');
const manager=new DeviceManager({adbPath:()=>process.env.XPAUTO_ADB||'adb',devicePort:()=>9229,log:()=>{},pickDevice:async()=>undefined});
let client;const events=[];const delay=ms=>new Promise(r=>setTimeout(r,ms));
async function until(check){const end=Date.now()+7000;do{const value=check();if(value)return value;await delay(20);}while(Date.now()<end);throw Error('等待调试事件超时');}
async function open(){events.length=0;client=new ProtocolClient(manager.endpoint+'/debug',15000);client.on('notification',(name,params)=>events.push({name,params}));await client.connect();await client.request('Runtime.enable');}
const finished=id=>until(()=>events.find(e=>e.name==='XpAuto.finished'&&e.params.runId===id)?.params);
(async()=>{try{
 await manager.connect();await open();const pid=await manager.adb(['-s',manager.serial,'shell','pidof','com.vixmobi.auto']);
 const source=`globalThis.workerFinished=false;\nthread.execAsync(()=>{\n globalThis.workerValue=1;\n debugger;\n globalThis.workerValue=2;\n globalThis.workerFinished=true;\n});\nexecSync(()=>workerFinished,5000);`;
 let run=await client.request('XpAuto.run',{source,resourceName:'xpauto://verification/worker-debug.js'});
 const paused=await until(()=>events.find(e=>e.name==='Debugger.paused')?.params);assert.ok(paused.callFrames.length);
 const inspected=await client.request('Runtime.evaluate',{expression:'workerValue',returnByValue:true});assert.equal(inspected.result.value,1);
 await client.request('Debugger.resume');assert.equal((await finished(run.runId)).success,true);
 assert.equal((await client.request('Runtime.evaluate',{expression:'workerValue',returnByValue:true})).result.value,2);
 events.length=0;run=await client.request('XpAuto.run',{source,resourceName:'xpauto://verification/worker-debug.js'});await until(()=>events.find(e=>e.name==='Debugger.paused'));
 await client.request('XpAuto.stop');assert.equal((await finished(run.runId)).cancelled,true);
 events.length=0;run=await client.request('XpAuto.run',{source,resourceName:'xpauto://verification/worker-debug.js'});await until(()=>events.find(e=>e.name==='Debugger.paused'));
 client.close();await delay(300);await open();
 assert.equal((await client.request('Runtime.evaluate',{expression:'1+1',returnByValue:true})).result.value,2);
 assert.equal(await manager.adb(['-s',manager.serial,'shell','pidof','com.vixmobi.auto']),pid);
 console.log('PASS 子线程断点检查、继续、暂停时停止、暂停时断连并重新连接，应用进程不变');
}finally{client?.close();await manager.disconnect();}})().catch(error=>{console.error(error);process.exitCode=1});
