'use strict';
const fs = require('node:fs/promises');
const path = require('node:path');
const os = require('node:os');
const { execFileSync } = require('node:child_process');
const { checkApk, sha256 } = require('../src/apk');

// 发布插件时收录已经构建的 APK；用户点击安装以及 F5 均不触发 Gradle。
async function main() {
    const apk = await checkApk(path.resolve(process.env.XPAUTO_APK_PATH ||
        path.join(__dirname, '../../app/build/outputs/apk/debug/app-debug.apk')));
    let aapt = process.env.XPAUTO_AAPT;
    if (!aapt) {
        const sdk = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT || path.join(os.homedir(), 'Library/Android/sdk');
        const tools = path.join(sdk, 'build-tools');
        const versions = (await fs.readdir(tools)).filter(value => /^\d+(\.\d+)*$/.test(value))
            .sort((a, b) => b.localeCompare(a, undefined, {numeric: true}));
        if (!versions.length) throw new Error('找不到 Android build-tools，请设置 XPAUTO_AAPT。');
        aapt = path.join(tools, versions[0], process.platform === 'win32' ? 'aapt.exe' : 'aapt');
    }
    const badging = execFileSync(aapt, ['dump', 'badging', apk], {encoding: 'utf8'});
    const pkg = /package: name='([^']+)' versionCode='([^']+)' versionName='([^']*)'/.exec(badging);
    if (pkg?.[1] !== 'com.vixmobi.auto') throw new Error('只允许将 com.vixmobi.auto APK 打包进插件。');
    const directory = path.join(__dirname, '../resources/mobile');
    await fs.mkdir(directory, {recursive: true});
    const target = path.join(directory, 'xpauto.apk');
    await fs.copyFile(apk, target);
    await fs.writeFile(path.join(directory, 'xpauto.json'), JSON.stringify({packageName: pkg[1],
        versionCode: Number(pkg[2]), versionName: pkg[3], sha256: await sha256(target)}, null, 2) + '\n');
    console.log(`已收录 XpAuto ${pkg[3]} APK，覆盖安装保留数据。`);
}
main().catch(error => { console.error(error.message); process.exitCode = 1; });
