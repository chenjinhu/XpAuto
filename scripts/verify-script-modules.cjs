// 真机模块回归：使用应用私有测试目录，结束后还原输入法并删除测试数据。
const assert=require('node:assert/strict');
const {DeviceManager}=require('../vscode-extension/src/device');
const {ProtocolClient}=require('../vscode-extension/src/protocol');
const manager=new DeviceManager({adbPath:()=>process.env.XPAUTO_ADB||'adb',devicePort:()=>9229,log:()=>{},pickDevice:async()=>undefined});
const adb=args=>manager.adb(['-s',manager.serial,...args]);
let client;
async function evaluate(expression){const r=await client.request('Runtime.evaluate',{expression,returnByValue:true});assert.equal(r.exceptionDetails,undefined,JSON.stringify(r.exceptionDetails));return r.result.value;}
(async()=>{try{
 await manager.connect();client=new ProtocolClient(manager.endpoint+'/debug',20000);await client.connect();await client.request('Runtime.enable');
 assert.equal(await evaluate('typeof thread+":"+typeof sqlite+":"+typeof utils.createQRCode'), 'object:object:function');
 assert.equal(await evaluate('(()=>{let count=0;const t=thread.execAsync(()=>{for(let i=0;i<5;i++){count++;sleep(20)}});sleep(250);return count})()'),5);
 console.log('PASS 真实子线程共享闭包并在 sleep 时交替运行');
 assert.equal(await evaluate('(()=>{let count=0;var t=thread.execAsync(()=>{while(!thread.isCancelled(t)){count++;sleep(20)}});sleep(100);thread.cancelThread(t);const old=count;sleep(100);return old>0 && old===count})()'),true);
 assert.equal(await evaluate('(()=>{let count=0;let t=setTimeout(()=>count++,200);cancelTimeout(t);let i=setInterval(()=>count++,20);sleep(110);cancelInterval(i);const old=count;sleep(80);return old>0&&old===count})()'),true);
 assert.equal(await evaluate('(()=>{let ready=false;setTimeout(()=>ready=true,40);return execSync(()=>ready,1000)&&!thread.execSync(()=>false,20)})()'),true);
 console.log('PASS 单线程取消、定时器取消、条件同步等待');
 assert.deepEqual(await evaluate(`(()=>{if(!sqlite.connectOrCreateDb('xpauto-module-test.db'))throw Error(sqlite.getErrorMsg());sqlite.execSql('DROP TABLE IF EXISTS sample');sqlite.execSql('CREATE TABLE sample(id INTEGER PRIMARY KEY,n TEXT,v INTEGER)');sqlite.insert('sample',{id:1,n:"中文 ' 引号",v:7});sqlite.update('sample',{v:9},'id=1');const rows=sqlite.query('SELECT * FROM sample');sqlite.dropDatabase();return rows})()`),[{id:1,n:"中文 ' 引号",v:9}]);
 assert.equal(await evaluate(`(()=>{sqlite.connectOrCreateDb('xpauto-module-test.db');sqlite.createTable('people',['name','pwd']);sqlite.insert('people',{name:'a',pwd:'b'});const row=sqlite.query('SELECT * FROM people')[0];sqlite.execSql('INVALID SQL');const error=sqlite.getErrorMsg();sqlite.dropDatabase();return row.id===1&&!!error})()`),true);
 console.log('PASS SQLite 建库建表、增改查、数据绑定、错误和删除');
 assert.equal(await evaluate(`utils.dataMd5('abc')`),'900150983cd24fb0d6963f7d28e17f72');
 assert.equal(await evaluate(`(()=>{for(const kind of ['aes','des','des3']){const value=encodeDecoder[kind+'Encrypt']('中文内容','12345678');if(encodeDecoder[kind+'Decrypt'](value,'12345678')!=='中文内容')return false;}return true})()`),true);
 assert.equal(await evaluate(`(()=>{const bitmap=utils.createQRCode('XpAuto 二维码',320,320);try{return utils.decodeQRCode(bitmap)}finally{bitmap.recycle()}})()`),'XpAuto 二维码');
 assert.equal(await evaluate(`utils.isAppExist('com.vixmobi.auto') && utils.getStartAppCmd('com.vixmobi.auto').includes('DeveloperActivity') && utils.getAppVersionCode('com.vixmobi.auto')>0`),true);
 assert.equal(await evaluate(`requestRuntimePermission(['android.permission.INTERNET'],100) && !requestRuntimePermission(['invalid.permission'],100)`),true);
 console.log('PASS 加解密、MD5、二维码、应用查询、动态权限检查');
}finally{client?.close();await manager.disconnect();}})().catch(e=>{console.error(e);process.exitCode=1});
