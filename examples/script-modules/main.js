// F5 只上传脚本；首次使用新原生 API 前需要安装新版 XpAuto。
function main() {
    let ticks = 0;
    const timer = setInterval(() => { ticks++; logd("计时 {}", ticks); }, 200);
    const completed = execSync(() => ticks >= 3, 2000);
    cancelInterval(timer);
    logi("线程等待完成：{}", completed);

    if (!sqlite.connectOrCreateDb("example.db")) throw Error(sqlite.getErrorMsg());
    try {
        sqlite.createTable("runs", ["time", "value"]);
        sqlite.insert("runs", {time: String(Date.now()), value: String(random(1,100))});
        logd(sqlite.query("SELECT * FROM runs ORDER BY id DESC LIMIT 3"));
    } finally { sqlite.close(); }
    logd("当前应用：{}", getCurrentRunningPkg());
    logd("本应用版本：{}", utils.getAppVersionName("com.vixmobi.auto"));
}
main();
