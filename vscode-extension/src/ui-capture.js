'use strict';
const { randomUUID } = require('node:crypto');
const model = require('../media/inspector-model');

function pngSize(bytes) {
    if (bytes.length < 33 || bytes.subarray(0, 8).toString('hex') !== '89504e470d0a1a0a'
        || bytes.toString('ascii', 12, 16) !== 'IHDR') throw new Error('ADB 没有返回有效的 PNG 截图');
    return { width: bytes.readUInt32BE(16), height: bytes.readUInt32BE(20) };
}
async function captureUi(manager) {
    await manager.connect();
    const serial = manager.serial, started = Date.now();
    const tree = await manager.captureNodes();
    const bytes = await manager.screenshot(serial);
    if (manager.serial !== serial || !manager.connected) throw new Error('抓取期间设备连接已改变，请重新抓取');
    const size = pngSize(bytes);
    if (size.width !== tree.screen.width || size.height !== tree.screen.height) throw new Error('抓取期间屏幕方向或尺寸发生变化，请重试');
    return model.validateSnapshot({ format: 'xpauto.ui-snapshot', version: 1, id: randomUUID(),
        serial, capturedAt: new Date(tree.capturedAt).toISOString(), durationMs: Date.now() - started,
        screen: tree.screen, nodes: tree.nodes, windows: tree.windows, truncated: tree.truncated,
        screenshot: { mimeType: 'image/png', data: bytes.toString('base64') } });
}
function parseSnapshot(text) {
    if (Buffer.byteLength(text, 'utf8') > 24 * 1024 * 1024) throw new Error('快照文件不能超过 24 MB');
    const snapshot = model.validateSnapshot(JSON.parse(text));
    const size = pngSize(Buffer.from(snapshot.screenshot.data, 'base64'));
    if (size.width !== snapshot.screen.width || size.height !== snapshot.screen.height) throw new Error('快照的 PNG 尺寸与节点坐标不一致');
    return snapshot;
}
module.exports = { captureUi, parseSnapshot, pngSize };
