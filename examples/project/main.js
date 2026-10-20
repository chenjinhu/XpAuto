// F5 配置 projectRoot 为本目录，resources 添加 ["assets"]；不需要重新编译 APK。
const task = require('./lib/task');
const config = require('./config.json');
const message = file.readFile(__dirname + '/assets/message.txt');
if (message === null) throw new Error(file.getErrorMsg());
task.process(config, message);
