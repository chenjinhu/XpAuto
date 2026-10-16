// 真机截图验证：仅显示自有纯色测试界面，系统授权由测试驱动确认，结束后恢复旋转设置。
'use strict';
const assert = require('node:assert/strict');
const { DeviceManager } = require('../vscode-extension/src/device');
const { ProtocolClient } = require('../vscode-extension/src/protocol');
const manager = new DeviceManager({ adbPath: () => process.env.XPAUTO_ADB || 'adb', devicePort: () => 9229, log: () => {}, pickDevice: async () => undefined });
const adb = args => manager.adb(['-s', manager.serial, ...args]);
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
const app = 'com.vixmobi.auto';
let client, rotation, autoRotate, pid;
const events = [];
async function evaluate(expression) {
    const response = await client.request('Runtime.evaluate', { expression, returnByValue: true });
    assert.equal(response.exceptionDetails, undefined, JSON.stringify(response.exceptionDetails));
    return response.result.value;
}
async function until(action, timeout = 8000) {
    const end = Date.now() + timeout;
    do { const result = await action(); if (result) return result; await delay(100); } while (Date.now() < end);
    throw Error('等待截图测试状态超时');
}
async function consent(accept) {
    await until(async () => {
        await adb(['shell', 'uiautomator', 'dump', '/data/local/tmp/xpauto-capture-ui.xml']);
        const xml = await adb(['shell', 'cat', '/data/local/tmp/xpauto-capture-ui.xml']);
        if (!xml.includes('screen_share_permission_dialog')) return false;
        const node = [...xml.matchAll(/<node\b[^>]*>/g)].map(match => match[0]).find(value => value.includes(`resource-id="android:id/button${accept ? 1 : 2}"`));
        const bounds = node?.match(/bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/);
        if (!bounds) return false;
        await adb(['shell', 'input', 'tap', String((+bounds[1] + +bounds[3]) >> 1), String((+bounds[2] + +bounds[4]) >> 1)]);
        return true;
    }, 12000);
}
async function grant() {
    const requested = evaluate('image.requestScreenCapture(20000,1)');
    await consent(true);
    assert.equal(await requested, true);
}
async function fixture(secure = false) {
    await adb(['shell', 'am', 'start', '-f', '0x14000000', '-n', `${app}/${app}.debug.ScreenshotFixtureActivity`, '--ez', 'secure', String(secure)]);
}
async function open() {
    client = new ProtocolClient(manager.endpoint + '/debug', 30000);
    client.on('notification', (name, params) => events.push({ name, params }));
    await client.connect(); await client.request('Runtime.enable');
}
const finished = id => until(() => events.find(event => event.name === 'XpAuto.finished' && event.params.runId === id)?.params);
async function projectionOff() {
    return until(async () => !(await adb(['shell', 'dumpsys', 'media_projection'])).includes(app));
}
(async () => {
    try {
        await manager.connect(); await open();
        pid = await adb(['shell', 'pidof', app]);
        rotation = await adb(['shell', 'settings', 'get', 'system', 'user_rotation']);
        autoRotate = await adb(['shell', 'settings', 'get', 'system', 'accelerometer_rotation']);
        await adb(['shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0']);
        await adb(['shell', 'settings', 'put', 'system', 'user_rotation', '0']);
        assert.equal(await evaluate('image.captureFullScreen()===null && image.getErrorMsg().includes("requestScreenCapture")'), true);
        assert.equal(await evaluate('!image.requestScreenCapture(1000,2) && image.getErrorMsg().includes("代理")'), true);
        const denied = evaluate('image.requestScreenCapture(20000,1)'); await consent(false); assert.equal(await denied, false);
        assert.equal(await evaluate('image.requestScreenCapture(300,1)'), false);
        await delay(500);
        console.log('PASS 未授权、代理模式明确失败、拒绝授权和授权超时');
        await grant();
        assert.equal(await evaluate('image.requestScreenCapture(1000,0)'), true);
        await fixture();
        await until(() => evaluate('(()=>{let p=image.captureFullScreen();if(!p)return false;try{return image.pixel(p,200,400)===-65536;}finally{image.recycle(p);}})()'));
        const frame = await evaluate(`(()=>{const p=image.captureFullScreen(),w=image.getWidth(p),h=image.getHeight(p);try{return {w,h,colors:[[w/4,h/4],[w*3/4,h/4],[w/4,h*3/4],[w*3/4,h*3/4]].map(([x,y])=>image.pixel(p,Math.floor(x),Math.floor(y))),description:String(p),saved:image.saveTo(p,'verify/full.png')};}finally{image.recycle(p);}})()`);
        assert.ok(frame.w < frame.h); assert.deepEqual(frame.colors, [-65536, -16711936, -16776961, -1]); assert.equal(frame.saved, true); assert.match(frame.description, /AutoImage/);
        console.log('PASS 全屏尺寸与红绿蓝白像素', JSON.stringify(frame));
        assert.equal(await evaluate(`(()=>{let p=image.captureScreen(3,100,200,400,600);try{return image.getWidth(p)===300&&image.getHeight(p)===400&&image.pixel(p,0,0)===-65536;}finally{image.recycle(p);}})()`), true);
        assert.equal(await evaluate('image.captureScreen(1,0,0,99999,99999)===null && image.getErrorMsg().includes("超出")'), true);
        assert.equal(await evaluate(`(()=>{for(const method of ['captureFullScreenEx','captureScreenshot','captureScreenSurface','captureScreenBitmapEx']){const p=image[method]();if(!p)throw Error(method+image.getErrorMsg());image.recycle(p);}const b=image.captureScreenBitmap('jpg',100,200,400,600,80);try{return b.getWidth()===300&&b.getHeight()===400;}finally{image.recycle(b);}})()`), true);
        assert.equal(await evaluate(`(()=>{const p=image.readImage('verify/full.png');const c=image.clip(p,100,200,400,600),b=image.imageToBitmap(c),d=image.bitmapToImage(b);image.recycle(c);image.recycle(b);const good=!image.isRecycled(d)&&image.getWidth(d)===300&&image.pixel(d,0,0)===-65536;image.recycle(p);image.recycle(d);return good;})()`), true);
        assert.equal(await evaluate(`(()=>{const b=image.captureScreenBitmapEx();try{return image.saveBitmap(b,'jpg',80,'verify/full.jpg')&&image.saveBitmap(b,'webp',80,'verify/full.webp')&&image.bitmapBase64(b,'png',100).startsWith('iVBORw0KGgo')&&image.bitmapBase64(b,'jpg',80).startsWith('/9j/')&&!image.saveTo(b,'../outside.png')&&image.getErrorMsg().includes('越出');}finally{image.recycle(b);}})()`), true);
        assert.equal(await evaluate(`image.captureToFile(3,100,200,400,600,'verify/region.png') && image.captureToFile('verify/screen.png')`), true);
        assert.equal(await evaluate(`(()=>{let p=image.readImage('verify/region.png');try{return image.getWidth(p)===300&&image.getHeight(p)===400;}finally{image.recycle(p);}})()`), true);
        assert.equal(await evaluate(`(()=>{for(let i=0;i<20;i++){let p=image.captureScreen(1,0,0,100,100);if(!p)throw Error(image.getErrorMsg());image.recycle(p);}let list=[];for(let i=0;i<33;i++)list.push(image.captureScreen(1,0,0,10,10));let good=list.slice(0,32).every(Boolean)&&list[32]===null;image.recycleAllImage();return good&&list.slice(0,32).every(p=>image.isRecycled(p));})()`), true);
        console.log('PASS 区域终点坐标、Bitmap、PNG/JPEG/WebP、Base64、独立回收、连续截图和内存数量上限');
        await adb(['shell', 'settings', 'put', 'system', 'user_rotation', '1']);
        await delay(1500);
        await until(() => evaluate('(()=>{let p=image.captureFullScreen();if(!p)return false;try{return image.getWidth(p)>image.getHeight(p);}finally{image.recycle(p);}})()'));
        await adb(['shell', 'settings', 'put', 'system', 'user_rotation', '0']);
        await until(() => evaluate('(()=>{let p=image.captureFullScreen();if(!p)return false;try{return image.getWidth(p)<image.getHeight(p);}finally{image.recycle(p);}})()'));
        // 安全窗口内容应由系统遮蔽，不尝试绕过 FLAG_SECURE。
        await fixture(true); await delay(700);
        assert.equal(await evaluate('(()=>{let p=image.captureFullScreen();if(!p)return true;try{return image.pixel(p,200,400)!==-65281;}finally{image.recycle(p);}})()'), true);
        await fixture(); await delay(500);
        assert.equal(await evaluate('(()=>{globalThis.retained=image.captureFullScreen();image.releaseScreenCapture();return !image.isRecycled(retained)&&image.captureFullScreen()===null;})()'), true);
        await projectionOff(); await grant();
        assert.equal(await evaluate('image.captureToFile("verify/second-session.png")'), true);
        await evaluate('image.releaseScreenCapture();image.recycleAllImage()'); await projectionOff();
        console.log('PASS 横竖屏、受保护窗口、释放后图片仍有效、重新授权和第二次截图');
        const rootResult = await evaluate('(()=>{let p=image.screencapImage(true);if(!p)return {available:false,error:image.getErrorMsg().split("\\n")[0]};image.recycle(p);return {available:true};})()');
        console.log('Root 截图实际结果', JSON.stringify(rootResult));
        assert.equal(await evaluate('image.screencapBitmap(false)===null && image.getErrorMsg().includes("代理")'), true);
        // 走真实脚本入口，验证正常结束会回收图片并结束系统共享。
        events.length = 0;
        let run = await client.request('XpAuto.run', { source: 'if(!image.requestScreenCapture(20000,1))throw Error(image.getErrorMsg());globalThis.autoRecycled=image.captureFullScreen();if(!autoRecycled)throw Error(image.getErrorMsg());', resourceName: 'xpauto://verification/screenshot.js', noDebug: true });
        await consent(true); assert.equal((await finished(run.runId)).success, true);
        await projectionOff(); assert.equal(await evaluate('image.isRecycled(autoRecycled)'), true);
        // 等待授权时停止，迟到的系统结果也不得重新启动服务。
        events.length = 0;
        run = await client.request('XpAuto.run', { source: 'image.requestScreenCapture(60000,1);', resourceName: 'xpauto://verification/screenshot-stop.js', noDebug: true });
        await delay(700); await client.request('XpAuto.stop'); assert.equal((await finished(run.runId)).cancelled, true); await projectionOff();
        client.close(); await delay(300); await open();
        events.length = 0;
        run = await client.request('XpAuto.run', { source: 'if(!image.requestScreenCapture(20000,1))throw Error(image.getErrorMsg());while(true){const p=image.captureFullScreen();image.recycle(p);sleep(100);}', resourceName: 'xpauto://verification/screenshot-running-stop.js', noDebug: true });
        await consent(true); await delay(300); await client.request('XpAuto.stop'); assert.equal((await finished(run.runId)).cancelled, true); await projectionOff();
        assert.equal(await adb(['shell', 'pidof', app]), pid);
        console.log('PASS 正常结束自动清理、授权中停止、连续截图中停止及重新连接，应用 PID 不变');
    } finally {
        client?.close();
        if (manager.connected) {
            if (rotation != null) await adb(['shell', 'settings', 'put', 'system', 'user_rotation', rotation]);
            if (autoRotate != null) await adb(['shell', 'settings', 'put', 'system', 'accelerometer_rotation', autoRotate]);
            await adb(['shell', 'run-as', app, 'rm', '-rf', 'files/images/verify', 'files/images/probe.png']);
            await adb(['shell', 'rm', '-f', '/data/local/tmp/xpauto-capture-ui.xml']);
            await adb(['shell', 'am', 'start', '-f', '0x14000000', '-n', `${app}/${app}.devtools.DeveloperActivity`]);
        }
        await manager.disconnect();
    }
})().catch(error => { console.error(error); process.exitCode = 1; });
