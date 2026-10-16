// 验证已安装应用的 JavaScript 桥接，不构建或重新安装 XpAuto。
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { DeviceManager } = require('../vscode-extension/src/device');
const { ProtocolClient } = require('../vscode-extension/src/protocol');
const manager = new DeviceManager({ adbPath: () => process.env.XPAUTO_ADB || 'adb',
  devicePort: () => 9229, log: () => {}, pickDevice: async () => undefined });
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const events = [];
let client;
async function event(name, match = () => true) {
  const end = Date.now() + 10000;
  while (Date.now() < end) {
    const index = events.findIndex(e => e.name === name && match(e.params));
    if (index >= 0) return events.splice(index, 1)[0].params;
    await sleep(20);
  }
  throw Error('Timeout: ' + name);
}
async function evaluate(expression) {
  const response = await client.request('Runtime.evaluate', { expression, returnByValue: true });
  assert.equal(response.exceptionDetails, undefined, JSON.stringify(response.exceptionDetails));
  return response.result.value;
}
const snapshot = 'function snapshot() { const r = shell.getLastResult();' +
  ' return {code:r.code, result:r.result, error:r.error, timedOut:r.timedOut,' +
  ' cancelled:r.cancelled, truncated:r.truncated}; }';
async function probe(body) { return evaluate('(() => { ' + snapshot + '\n' + body + '\n })()'); }
async function run(source) {
  const result = await client.request('XpAuto.run', { source, resourceName: 'xpauto://verification/shell.js' });
  return event('XpAuto.finished', e => e.runId === result.runId);
}

(async () => {
  try {
    await manager.connect();
    const adb = args => manager.adb(['-s', manager.serial, ...args]);
    const beforePid = await adb(['shell', 'pidof', 'com.vixmobi.auto']);
    const beforeUpdate = (await adb(['shell', 'dumpsys', 'package', 'com.vixmobi.auto']))
      .match(/lastUpdateTime=(.*)/)?.[1];
    client = new ProtocolClient(manager.endpoint + '/debug', 70000);
    client.on('notification', (name, params) => events.push({ name, params }));
    await client.connect();
    await client.request('Runtime.enable');

    const plain = await probe('shell.execCommand("printf \'中文\\\\n\'; echo error >&2; exit 7", false); return snapshot();');
    assert.equal(plain.result, '中文\n');
    assert.equal(plain.error, 'error\n');
    assert.equal(plain.code, 7);
    const uid = await evaluate('shell.execCommand("id -u", false).trim()');
    assert.notEqual(uid, '0');
    assert.notEqual(uid, '2000');
    assert.ok(events.some(e => e.name === 'Runtime.consoleAPICalled' &&
      e.params.args?.some(a => a.value?.includes('[shell] exit=7'))));
    console.log('PASS: actual app UID', uid, ', UTF-8/stdout/stderr/exit code and debug console');

    const large = await probe('shell.execCommand("head -c 1100000 /dev/zero; head -c 1100000 /dev/zero >&2", false);' +
      'const r = snapshot(); return {code:r.code, out:r.result.length, err:r.error.length, truncated:r.truncated};');
    assert.deepEqual(large, { code: 0, out: 1048576, err: 1048576, truncated: true });
    console.log('PASS: large output on both pipes is drained and bounded');

    assert.equal(await evaluate('shell.addSuBin("/system/bin/su")'), true);
    const root = await probe('const allowed = shell.su(15000); return {allowed, detail:snapshot()};');
    if (root.allowed) {
      assert.equal(await evaluate('shell.ROOT("id -u").trim()'), '0');
      assert.equal(await evaluate('shell.sudo("id -u").trim()'), '0');
      assert.equal(await evaluate('shell.execCommand("id -u").trim()'), '0');
      console.log('PASS: custom su, real app Root authorization, ROOT/sudo/automatic selection');
      assert.equal(await evaluate('shell.installApp("/data/local/tmp/xpauto-no-such-apk.apk")'), false);
      assert.equal(await evaluate('shell.stopApp("com.xpauto.shellfixture.missing")'), false);
      assert.equal(await evaluate('shell.uninstallApp("com.xpauto.shellfixture.missing")'), false);
      console.log('PASS: missing APK and missing package report failure');
    } else {
      assert.notEqual(root.detail.code, 0);
      console.log('ROOT UNAVAILABLE for XpAuto:', root.detail.error);
    }
    assert.equal(await evaluate('shell.uninstallApp("com.example; echo injected")'), false);

    for (const useRoot of root.allowed ? [false, true] : [false]) {
      const marker = '/data/user/0/com.vixmobi.auto/files/shell-test-' + Date.now();
      const command = "sleep 2; echo survived > '" + marker + "'";
      const check = 'shell.execCommand(' + JSON.stringify("test ! -e '" + marker + "'; echo $?") + ', false).trim()';
      const timed = await probe('shell.execCommand(' + JSON.stringify(command) + ', ' + useRoot + ', 150); return snapshot();');
      assert.equal(timed.timedOut, true);
      await sleep(2200);
      assert.equal(await evaluate(check), '0');
      const started = await client.request('XpAuto.run', {
        source: 'shell.execCommand(' + JSON.stringify(command) + ', ' + useRoot + '); globalThis.mustNotRun = true;',
        resourceName: 'xpauto://verification/shell-stop.js' });
      await event('XpAuto.running', e => e.runId === started.runId);
      await sleep(150);
      await manager.stop();
      const finished = await event('XpAuto.finished', e => e.runId === started.runId);
      assert.equal(finished.cancelled, true);
      assert.equal(await evaluate('typeof mustNotRun'), 'undefined');
      assert.equal((await run('shell.execCommand("printf resumed", false);')).success, true);
      await sleep(2200);
      assert.equal(await evaluate(check), '0');
      console.log('PASS:', useRoot ? 'Root' : 'ordinary', 'timeout/Stop kills descendants; next run works');
    }

    // 停止可能返回求值异常或 Inspector 的中止错误；立即接住拒绝，避免未处理 Promise。
    const consoleCommand = client.request('Runtime.evaluate', {
      expression: 'shell.execCommand("sleep 10", false); globalThis.consoleAfterStop = true;',
      returnByValue: true }).catch(error => error);
    await sleep(200);
    await manager.stop();
    const consoleStopped = await consoleCommand;
    if (consoleStopped instanceof Error) assert.equal(consoleStopped.message, 'Execution was terminated');
    else assert.ok(consoleStopped.exceptionDetails, 'Stopping console Shell must interrupt evaluation');
    assert.equal(await evaluate('typeof consoleAfterStop'), 'undefined');
    assert.equal((await run('shell.execCommand("printf resumed", false);')).success, true);
    console.log('PASS: long Shell command in Debug Console can be stopped');

    // 可选的一次性测试 APK；固定测试包名必须尚未安装，避免覆盖已有应用。
    if (process.env.XPAUTO_SHELL_FIXTURE_APK) {
      assert.equal(root.allowed, true, 'Fixture lifecycle requires XpAuto Root permission');
      const fixture = 'com.xpauto.shellfixture';
      assert.equal(await adb(['shell', 'pm', 'path', fixture]).catch(() => ''), '', 'Refusing to replace an existing fixture');
      const remote = "/data/local/tmp/xpauto shell ' fixture.apk";
      await adb(['push', path.resolve(process.env.XPAUTO_SHELL_FIXTURE_APK), remote]);
      try {
        const installed = await probe('const ok = shell.installApp(' + JSON.stringify(remote) + '); return {ok, detail:snapshot()};');
        assert.equal(installed.ok, true, JSON.stringify(installed.detail));
        await adb(['shell', 'am', 'start', '-W', '-n', fixture + '/android.app.Activity']);
        assert.ok(await adb(['shell', 'pidof', fixture]));
        assert.equal(await evaluate('shell.stopApp("' + fixture + '")'), true);
        assert.equal(await adb(['shell', 'pidof', fixture]).catch(() => ''), '');
        assert.equal(await evaluate('shell.uninstallApp("' + fixture + '")'), true);
        assert.equal(await adb(['shell', 'pm', 'path', fixture]).catch(() => ''), '');
        console.log('PASS: real APK with spaces/quotes installed, launched, stopped and uninstalled');
      } finally {
        await adb(['uninstall', fixture]).catch(() => {});
        await adb(['shell', 'rm', '-f', '"' + remote + '"']).catch(() => {});
      }
    }
    // 最后缓存一个只读示例，方便用户通过悬浮窗的“运行”按钮再次执行。
    assert.equal((await run(fs.readFileSync(path.join(__dirname, '../examples/shell/main.js'), 'utf8'))).success, true);
    assert.equal(await adb(['shell', 'pidof', 'com.vixmobi.auto']), beforePid);
    assert.equal((await adb(['shell', 'dumpsys', 'package', 'com.vixmobi.auto']))
      .match(/lastUpdateTime=(.*)/)?.[1], beforeUpdate);
    console.log('PASS: XpAuto PID/install time unchanged throughout shell tests');
  } finally {
    client?.close();
    await manager.disconnect();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
