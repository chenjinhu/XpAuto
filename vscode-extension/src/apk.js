'use strict';
const fs = require('node:fs/promises');
const { createReadStream } = require('node:fs');
const { createHash } = require('node:crypto');
const path = require('node:path');

async function sha256(file) {
    const digest = createHash('sha256');
    for await (const chunk of createReadStream(file)) digest.update(chunk);
    return digest.digest('hex');
}

async function checkApk(file) {
    if (!path.isAbsolute(file) || path.extname(file).toLowerCase() !== '.apk')
        throw new Error('请指定 APK 文件的绝对路径。');
    const stat = await fs.stat(file);
    if (!stat.isFile() || stat.size === 0) throw new Error('APK 文件为空或不是普通文件。');
    return file;
}

async function bundledApk(extensionPath) {
    const directory = path.join(extensionPath, 'resources/mobile');
    let metadata;
    try { metadata = JSON.parse(await fs.readFile(path.join(directory, 'xpauto.json'), 'utf8')); }
    catch (error) { if (error.code === 'ENOENT') return undefined; throw error; }
    const file = path.join(directory, 'xpauto.apk');
    if (metadata.packageName !== 'com.vixmobi.auto' || metadata.sha256 !== await sha256(await checkApk(file)))
        throw new Error('插件内置 XpAuto 安装包校验失败，请重新安装插件。');
    return file;
}

module.exports = { bundledApk, checkApk, sha256 };
