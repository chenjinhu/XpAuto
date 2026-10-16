// 真机界面回归：直接连接已安装的应用，不构建或重新安装 XpAuto。
const assert = require('node:assert/strict');
const { execFileSync } = require('node:child_process');
const fs = require('node:fs');
const { pathToFileURL } = require('node:url');
const root = require('node:path').resolve(__dirname, '..');
const { DeviceManager } = require(root + '/vscode-extension/src/device');
const { ProtocolClient } = require(root + '/vscode-extension/src/protocol');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const adb = args => execFileSync(process.env.XPAUTO_ADB || 'adb',
 [...(manager.serial || process.env.ANDROID_SERIAL ? ['-s', manager.serial || process.env.ANDROID_SERIAL] : []), ...args],
 { encoding: 'utf8', timeout: 15000, maxBuffer: 3 * 1024 * 1024 });
const manager = new DeviceManager({adbPath:()=> process.env.XPAUTO_ADB || 'adb',devicePort:()=>9229,log:()=>{},pickDevice:async()=>undefined});
let client;
const events = [];
async function event(name, match = () => true) {
 const deadline = Date.now()+10000;
 while (Date.now()<deadline) {
  const i=events.findIndex(e=>e.name===name && match(e.params));
  if(i>=0) return events.splice(i,1)[0].params;
  await sleep(20);
 }
 throw Error('Timeout: '+name);
}
async function state(name) {
 for(let i=0;i<500;i++) {
  if(manager.state.state===name) return;
  await sleep(20);
 }
 throw Error('Expected '+name+', got '+JSON.stringify(manager.state));
}
function ui() {
 // 悬浮窗不抢焦点，必须查询所有窗口，不能只读取当前焦点窗口。
 adb(['shell','uiautomator','dump','--windows','/sdcard/xpauto-ui-test.xml']);
 const xml=adb(['shell','cat','/sdcard/xpauto-ui-test.xml']);
 return [...xml.matchAll(/<node\b[^>]*>/g)].map(([tag])=>{
  const a=Object.fromEntries([...tag.matchAll(/([\w-]+)="([^"]*)"/g)].map(m=>[m[1],m[2]]));
  a.rect=(a.bounds.match(/\d+/g)||[]).map(Number);
  return a;
 }).filter(n=>n.package==='com.vixmobi.auto');
}
const desc=(nodes,value)=>nodes.find(n=>n['content-desc']===value);
const text=(nodes,value)=>nodes.find(n=>n.text===value);
function tapNode(node) {
 assert.ok(node,'UI element missing');
 assert.notEqual(node.enabled,'false','UI element disabled: '+node['content-desc']);
 const [l,t,r,b]=node.rect;
 adb(['shell','input','tap',String(Math.round((l+r)/2)),String(Math.round((t+b)/2))]);
}
async function openMenu() {
 let nodes=ui();
 const collapsed=desc(nodes,'展开悬浮菜单');
 if(collapsed) { tapNode(collapsed); await sleep(150); nodes=ui(); }
 assert.ok(desc(nodes,'收起悬浮菜单'));
 return nodes;
}
async function tapControl(label) {
 const nodes=await openMenu();
 // 系统 UI 查询会短暂挂起其他无障碍服务，等待它恢复后再触发脚本权限检查。
 await sleep(500);
 tapNode(desc(nodes,'悬浮窗'+label));
 await sleep(150);
}
async function connectClient() {
 client=new ProtocolClient(manager.endpoint+'/debug');
 client.on('notification',(name,params)=>events.push({name,params}));
 await client.connect();
 await client.request('Runtime.enable');
 await client.request('Debugger.enable');
}
async function run(source, name='controls.js') {
 return client.request('XpAuto.run',{source,resourceName:'xpauto://verification/'+name});
}
(async()=>{
 try {
  adb(['shell','am','start','-f','0x14000000','-n','com.vixmobi.auto/com.vixmobi.auto.devtools.DeveloperActivity']);
  let nodes=ui();
  if(text(nodes,'本机 IP')) { adb(['shell','input','keyevent','4']); await sleep(250); }
  nodes=ui(); tapNode(desc(nodes,'打开菜单')); await sleep(250);
  nodes=ui();
  assert.ok(text(nodes,'本机 IP'));
  assert.ok(nodes.some(n=>/^\d+\.\d+\.\d+\.\d+$/.test(n.text) || n.text==='未连接网络' || /^[0-9a-f]*:[0-9a-f:]+$/i.test(n.text)));
  assert.ok(text(nodes,'1.0 (1)'));
  assert.ok(text(nodes,'com.vixmobi.auto'));
  assert.equal(desc(nodes,'系统设置，暂未开放').enabled,'false');
  assert.equal(desc(nodes,'导出运行日志，暂未开放').enabled,'false');
  adb(['shell','input','keyevent','4']); await sleep(250);
  assert.ok(desc(ui(),'打开菜单'));
  console.log('PASS: home drawer shows real device metadata, disabled placeholders and Back closes drawer');
  // 已授予悬浮窗权限时，打开首页就应该自动启动，无需再点击开启按钮。
  nodes=ui();
  assert.ok(text(nodes,'关闭运行环境'),'Runtime should start automatically with overlay permission');
  if(desc(nodes,'收起悬浮菜单')) {
   tapNode(desc(nodes,'收起悬浮菜单')); await sleep(150); nodes=ui();
  }
  const bubble=desc(nodes,'展开悬浮菜单');
  assert.ok(bubble,'Collapsed app icon missing');
  const density=Number(adb(['shell','wm','density']).match(/(?:Override|Physical) density: (\d+)/)?.[1])/160;
  assert.ok((bubble.rect[2]-bubble.rect[0])/density<=50,'Bubble must stay compact');
  nodes=await openMenu();
  const controls=['运行','暂停','停止','日志','面板','结束'].map(s=>desc(nodes,'悬浮窗'+s));
  assert.ok(controls.every(Boolean));
  for(let i=1;i<controls.length;i++) assert.ok(controls[i].rect[1]>controls[i-1].rect[3]);
  const expandedIcon=desc(nodes,'收起悬浮菜单');
  tapNode(expandedIcon); await sleep(150);
  const screen=adb(['shell','wm','size']).match(/(\d+)x(\d+)/).slice(1).map(Number);
  // 位置会跨运行保存，先移到屏幕中部，确保后面的拖动距离超过触摸阈值。
  let icon=desc(ui(),'展开悬浮菜单');
  let [l,t,r,b]=icon.rect;
  const middleY=Math.round(screen[1]/2);
  if(Math.abs((t+b)/2-middleY)>100) {
   adb(['shell','input','swipe',String((l+r)/2),String((t+b)/2),'150',String(middleY),'450']);
   await sleep(200);
   icon=desc(ui(),'展开悬浮菜单');
   assert.ok(icon,'Dragging to the middle must not expand');
   [l,t,r,b]=icon.rect;
  }
  adb(['shell','input','swipe',String((l+r)/2),String((t+b)/2),String(screen[0]-100),String(screen[1]-180),'450']);
  await sleep(200);
  nodes=ui();
  assert.ok(desc(nodes,'展开悬浮菜单'),'Dragging must not expand');
  nodes=await openMenu();
  assert.ok(desc(nodes,'悬浮窗结束').rect[3]<desc(nodes,'收起悬浮菜单').rect[1],'Bottom menu must expand upward');
  for(const control of ['运行','暂停','停止','日志','面板','结束']) {
   const rect=desc(nodes,'悬浮窗'+control).rect;
   assert.ok(rect[0]>=0 && rect[2]<=screen[0] && rect[1]>=0 && rect[3]<=screen[1]);
  }
  // 点击悬浮窗以外的位置应收起菜单，并将点击继续交给下方应用。
  adb(['shell','input','tap','500','200']); await sleep(200);
  assert.ok(desc(ui(),'展开悬浮菜单'));
  console.log('PASS: 48dp app icon, six vertical controls, drag/tap distinction, upward expansion and outside dismissal');

  await manager.connect();
  const pid=adb(['shell','pidof','com.vixmobi.auto']).trim();
  await connectClient();
  await tapControl('日志');
  nodes=ui();
  assert.equal(nodes.filter(n=>n['content-desc']==='日志悬浮窗').length,1);
  assert.ok(desc(nodes,'日志自动滚动已开启'));
  const logRun=await run('console.log("悬浮日志中文验证"); print("悬浮日志 print 验证"); console.error("悬浮日志错误验证");','floating-logs.js');
  assert.equal((await event('XpAuto.finished',e=>e.runId===logRun.runId)).success,true);
  nodes=ui();
  const logText=desc(nodes,'日志内容').text;
  for(const message of ['悬浮日志中文验证','悬浮日志 print 验证','悬浮日志错误验证']) assert.ok(logText.includes(message));
  assert.match(logText,/\d{2}:\d{2}:\d{2}/);
  // 拖动标题仅移动日志窗，不能影响脚本会话或悬浮球。
  const title=desc(nodes,'拖动日志悬浮窗');
  const original=desc(nodes,'日志悬浮窗').rect;
  const [tl,tt,tr,tb]=title.rect;
  adb(['shell','input','swipe',String((tl+tr)/2),String((tt+tb)/2),String((tl+tr)/2),String(Math.round(screen[1]/3)),'450']);
  nodes=ui();
  const moved=desc(nodes,'日志悬浮窗').rect;
  assert.notEqual(moved[1],original[1]);
  assert.ok(moved[0]>=0 && moved[2]<=screen[0] && moved[1]>=0 && moved[3]<=screen[1]);
  tapNode(desc(nodes,'日志自动滚动已开启')); await sleep(200);
  assert.ok(desc(ui(),'日志自动滚动已关闭'));
  await client.request('Runtime.evaluate',{expression:'console.log("关闭跟随仍实时更新")'});
  nodes=ui(); assert.ok(desc(nodes,'日志内容').text.includes('关闭跟随仍实时更新'));
  tapNode(desc(nodes,'日志自动滚动已关闭'));
  nodes=ui(); tapNode(desc(nodes,'清空运行日志')); await sleep(300);
  assert.equal(desc(ui(),'日志内容').text,'暂无日志，运行脚本后会实时显示。');
  // 菜单可收起日志窗，再次打开不会重复创建，也不会丢失新日志。
  await tapControl('日志'); assert.ok(!desc(ui(),'日志悬浮窗'));
  await client.request('Runtime.evaluate',{expression:'console.log("关闭后继续收集日志")'});
  await tapControl('日志');
  nodes=ui();
  assert.equal(nodes.filter(n=>n['content-desc']==='日志悬浮窗').length,1);
  assert.ok(desc(nodes,'日志内容').text.includes('关闭后继续收集日志'));
  tapNode(desc(nodes,'关闭日志悬浮窗'));
  assert.ok(!desc(ui(),'日志悬浮窗'));
  assert.equal(adb(['shell','pidof','com.vixmobi.auto']).trim(),pid);
  console.log('PASS: live floating logs, timestamps, drag, follow toggle, shared clear, close and reopen');
  let current=await run('debugger; globalThis.floatingResult=24;','resume.js');
  await event('Debugger.paused');
  await tapControl('继续');
  assert.equal((await event('XpAuto.finished',e=>e.runId===current.runId)).success,true);
  assert.equal((await client.request('Runtime.evaluate',{expression:'floatingResult',returnByValue:true})).result.value,24);

  current=await run('globalThis.keepRunning=true; globalThis.counter=0; while(keepRunning){counter++;}','pause.js');
  await event('XpAuto.running',e=>e.runId===current.runId);
  await tapControl('日志');
  nodes=ui(); tapNode(desc(nodes,'关闭日志悬浮窗'));
  assert.equal(manager.state.state,'running','Closing logs must not stop the script');
  await tapControl('暂停');
  const paused=await event('Debugger.paused');
  await state('paused');
  const frame=paused.callFrames[0].callFrameId;
  const first=await client.request('Debugger.evaluateOnCallFrame',{callFrameId:frame,expression:'counter',returnByValue:true});
  await sleep(150);
  const second=await client.request('Debugger.evaluateOnCallFrame',{callFrameId:frame,expression:'counter',returnByValue:true});
  assert.equal(first.result.value,second.result.value,'JS must actually stop while paused');
  await client.request('Debugger.evaluateOnCallFrame',{callFrameId:frame,expression:'keepRunning=false'});
  await tapControl('继续');
  assert.equal((await event('XpAuto.finished',e=>e.runId===current.runId)).success,true);
  console.log('PASS: floating Pause suspends actual JavaScript, Continue resumes it and breakpoints');

  current=await run('while(true){}','stop.js');
  await event('XpAuto.running',e=>e.runId===current.runId);
  await tapControl('停止');
  assert.equal((await event('XpAuto.finished',e=>e.runId===current.runId)).cancelled,true);
  client.close(); client=null; await state('idle');
  // 断开调试器后，从手机运行缓存脚本，再验证独立运行时的暂停、继续和停止。
  await tapControl('运行'); await state('running');
  await tapControl('暂停'); await state('paused');
  await tapControl('继续'); await state('running');
  await tapControl('停止'); await state('stopped');
  console.log('PASS: local cached scripts can run, pause, resume and stop with VS Code disconnected');

  adb(['shell','input','keyevent','3']); await sleep(300);
  await tapControl('日志');
  assert.ok(desc(ui(),'日志悬浮窗'),'Logs must be available over other apps');
  await tapControl('面板');
  assert.ok(desc(ui(),'打开菜单'));
  // 恢复一个可正常完成的示例，避免把无限循环留给用户的“运行”按钮。
  await connectClient();
  const file=root+'/examples/inspector-probe/main.js';
  current=await client.request('XpAuto.run',{source:fs.readFileSync(file,'utf8'),resourceName:pathToFileURL(file).href});
  assert.equal((await event('XpAuto.finished',e=>e.runId===current.runId)).success,true);
  client.close(); client=null; await state('idle');
  await tapControl('结束');
  await state('disconnected');
  nodes=ui();
  assert.ok(!desc(nodes,'展开悬浮菜单') && !desc(nodes,'收起悬浮菜单'));
  assert.ok(!desc(nodes,'日志悬浮窗'),'End must remove the log window too');
  assert.ok(text(nodes,'开启运行环境与悬浮窗'));
  assert.equal(adb(['shell','pidof','com.vixmobi.auto']).trim(),pid);
  console.log('PASS: Panel opens home; End closes the runtime and removes the overlay without killing the app');
  await sleep(500); tapNode(text(nodes,'开启运行环境与悬浮窗')); await sleep(600);
  await manager.connect();
  assert.ok(desc(ui(),'展开悬浮菜单'));
  assert.ok(!desc(ui(),'日志悬浮窗'),'Logs stay closed after restarting the runtime');
  console.log('PASS: runtime can restart after End; floating icon returns collapsed');
 } finally {
  client?.close(); await manager.disconnect();
  adb(['shell','rm','-f','/sdcard/xpauto-ui-test.xml']);
 }
})().catch(e=>{console.error(e); process.exitCode=1;});
