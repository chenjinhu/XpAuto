// Root 权限 API 验证：保留原服务列表、权限和 app-op，结束后恢复，不读取其他应用数据。
const assert=require('node:assert/strict');
const {DeviceManager}=require('../vscode-extension/src/device');const {ProtocolClient}=require('../vscode-extension/src/protocol');
const manager=new DeviceManager({adbPath:()=>process.env.XPAUTO_ADB||'adb',devicePort:()=>9229,log:()=>{},pickDevice:async()=>undefined});
const adb=args=>manager.adb(['-s',manager.serial,...args]);const quote=value=>"'"+value.replaceAll("'","'\"'\"'")+"'";
let client,services,secureGranted,projectionMode;const app='com.vixmobi.auto';
async function evaluate(expression){const r=await client.request('Runtime.evaluate',{expression,returnByValue:true});assert.equal(r.exceptionDetails,undefined,JSON.stringify(r.exceptionDetails));return r.result.value;}
(async()=>{try{
 await manager.connect();client=new ProtocolClient(manager.endpoint+'/debug',20000);await client.connect();await client.request('Runtime.enable');
 services=await adb(['shell','settings','get','secure','enabled_accessibility_services']);
 secureGranted=(await adb(['shell','dumpsys','package',app])).includes('android.permission.WRITE_SECURE_SETTINGS: granted=true');
 const projection=await adb(['shell','appops','get',app,'PROJECT_MEDIA']);projectionMode=/PROJECT_MEDIA: (\w+)/.exec(projection)?.[1]||'default';
 const granted=await evaluate('tryGetAccStartupPermission()');
 if(!granted){
  const detail=await evaluate('(()=>{const r=shell.getLastResult();return {code:r.code,error:r.error}})()');
  assert.ok(detail.code!==0,JSON.stringify(detail));
  console.log('PASS 当前应用无 Root 时返回 false；成功路径改用 ADB 预授权验证。',detail.error.split('\n')[0]);
  await adb(['shell','pm','grant',app,'android.permission.WRITE_SECURE_SETTINGS']);
  assert.equal(await evaluate('tryGetAccStartupPermission()'),true);
 }
 assert.equal(await adb(['shell','settings','get','secure','enabled_accessibility_services']),services);
 assert.ok((await adb(['shell','dumpsys','package',app])).includes('android.permission.WRITE_SECURE_SETTINGS: granted=true'));
 // 第二次操作应直接走已授予的系统权限，不再执行 Root 命令。
 assert.equal(await evaluate(`(()=>{shell.execCommand('printf root-free-marker',false,1000);return tryGetAccStartupPermission() && shell.getLastResult().result==='root-free-marker'})()`),true);
 if(!await evaluate('tryGetProjectionPermission()')){await adb(['shell','appops','set',app,'PROJECT_MEDIA','allow']);assert.equal(await evaluate('tryGetProjectionPermission()'),true);}
 console.log('PASS 已授权无障碍启用、重复调用不使用 Root、保留其他服务、截图 app-op 检查');
}finally{
 client?.close();if(manager.connected){
  if(services)await adb(['shell','settings','put','secure','enabled_accessibility_services',quote(services)]);
  if(secureGranted===false)await adb(['shell','pm','revoke',app,'android.permission.WRITE_SECURE_SETTINGS']);
  if(projectionMode)await adb(['shell','appops','set',app,'PROJECT_MEDIA',projectionMode]);
 }await manager.disconnect();
}})().catch(error=>{console.error(error);process.exitCode=1});
