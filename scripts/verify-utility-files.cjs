// 文件与媒体测试使用独立临时目录；所有测试文件在 finally 中删除。
const assert=require('node:assert/strict'),fs=require('node:fs'),os=require('node:os'),path=require('node:path');
const {DeviceManager}=require('../vscode-extension/src/device');const {ProtocolClient}=require('../vscode-extension/src/protocol');
const manager=new DeviceManager({adbPath:()=>process.env.XPAUTO_ADB||'adb',devicePort:()=>9229,log:()=>{},pickDevice:async()=>undefined});
const adb=args=>manager.adb(['-s',manager.serial,...args]);let client;
const root='/data/user/0/com.vixmobi.auto/files/module-test-files';
const temporary=fs.mkdtempSync(path.join(os.tmpdir(),'xpauto-media-'));
async function evaluate(expression){const r=await client.request('Runtime.evaluate',{expression,returnByValue:true});assert.equal(r.exceptionDetails,undefined,JSON.stringify(r.exceptionDetails));return r.result.value;}
(async()=>{try{
 await manager.connect();client=new ProtocolClient(manager.endpoint+'/debug',20000);await client.connect();await client.request('Runtime.enable');
 await evaluate(`globalThis.testDir=${JSON.stringify(root)};file.mkdirs(testDir);file.writeFile('中文 ZIP 内容',testDir+'/sample.txt')`);
 assert.equal(await evaluate(`utils.zip(testDir+'/encrypted.zip','password',[testDir+'/sample.txt'])`),true);
 assert.equal(await evaluate(`utils.readFileInZip(testDir+'/encrypted.zip','password','sample.txt')`),'中文 ZIP 内容');
 assert.equal(await evaluate(`utils.unzip(testDir+'/encrypted.zip','bad-password',testDir+'/wrong')`),false);
 assert.equal(await evaluate(`utils.unzip(testDir+'/encrypted.zip','password',testDir+'/result') && utils.fileMd5(testDir+'/result/sample.txt')===utils.dataMd5('中文 ZIP 内容')`),true);
 assert.equal(await evaluate(`utils.fileMd5(testDir+'/sample.txt')===utils.dataMd5('中文 ZIP 内容')`),true);
 console.log('PASS 加密 ZIP、正确/错误密码、解压、文件 MD5');
 // 短静音 WAV 同样经过 Android MediaPlayer 解码，避免测试时发出声音。
 const pcm=Buffer.alloc(8000*2/4),wav=Buffer.alloc(44+pcm.length);wav.write('RIFF');wav.writeUInt32LE(wav.length-8,4);wav.write('WAVEfmt ',8);wav.writeUInt32LE(16,16);wav.writeUInt16LE(1,20);wav.writeUInt16LE(1,22);wav.writeUInt32LE(8000,24);wav.writeUInt32LE(16000,28);wav.writeUInt16LE(2,32);wav.writeUInt16LE(16,34);wav.write('data',36);wav.writeUInt32LE(pcm.length,40);pcm.copy(wav,44);fs.writeFileSync(path.join(temporary,'quiet.wav'),wav);
 await adb(['push',path.join(temporary,'quiet.wav'),'/data/local/tmp/xpauto-quiet.wav']);await adb(['shell','run-as','com.vixmobi.auto','cp','/data/local/tmp/xpauto-quiet.wav',root+'/quiet.wav']);
 assert.equal(await evaluate(`utils.playMp3WaitEnd(testDir+'/quiet.wav',false)`),true);
 assert.equal(await evaluate(`(()=>{setTimeout(()=>utils.stopMp3(),200);return utils.playMp3WaitEnd(testDir+'/quiet.wav',true)})()`),true);
 assert.equal(await evaluate(`utils.playMp3(testDir+'/missing.mp3',false)`),false);
 console.log('PASS 音频完成等待、跨线程停止循环播放和缺失文件失败');
}finally{
 client?.close();if(manager.connected){await adb(['shell','run-as','com.vixmobi.auto','rm','-rf',root]);await adb(['shell','rm','-f','/data/local/tmp/xpauto-quiet.wav']);}await manager.disconnect();fs.rmSync(temporary,{recursive:true,force:true});
}})().catch(error=>{console.error(error);process.exitCode=1});
