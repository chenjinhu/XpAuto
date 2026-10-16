// Optional CLI connection check. F5 uses the XpAuto extension directly.
// This command never builds, installs, starts or restarts the Android application.
import { createRequire } from 'node:module';
import { pathToFileURL } from 'node:url';
import { resolve } from 'node:path';
const require = createRequire(import.meta.url);
const { DeviceManager } = require('../vscode-extension/src/device.js');

export async function prepareDebug() {
    const manager = new DeviceManager({
        adbPath: () => process.env.XPAUTO_ADB || 'adb', devicePort: () => 9229,
        log: message => console.log(message), pickDevice: async () => undefined,
    });
    try { await manager.connect(); console.log(manager.state.detail); }
    finally { await manager.disconnect(); }
}
if (process.argv[1] && pathToFileURL(resolve(process.argv[1])).href === import.meta.url) {
    prepareDebug().catch(error => { console.error(error.message); process.exitCode = 1; });
}
