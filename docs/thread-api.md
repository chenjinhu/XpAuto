# 线程与定时器

参考 [EasyClick 线程函数](https://ieasyclick.com/docs/funcs/thread-api)。XpAuto 在同一个 V8 环境中执行真实 Java 子线程，保留 JS 闭包、全局变量及选择器对象。所有时间单位为毫秒。

| 函数 | 返回与行为 |
| --- | --- |
| `thread.execAsync(callback)` | 返回数字线程 ID，立即安排子线程 |
| `setTimeout(callback, timeout=0)` | 延迟一次执行，返回线程 ID |
| `cancelTimeout(id)` | 取消任务，存在时返回 true |
| `setInterval(callback, interval)` | 周期执行，每轮完成后再等待 interval，最短 1ms |
| `cancelInterval(id)` | 取消周期任务 |
| `thread.cancelThread(id)` | 协作取消指定线程，存在时返回 true |
| `thread.isCancelled(id)` | 已取消、已结束或不存在返回 true |
| `thread.stopAll()` | 取消当前会话所有子线程，不停止主脚本 |
| `execSync(condition, timeout)` / `thread.execSync(...)` | 立即检查一次，再最多每 50ms 检查，严格返回 true 才成功；到期返回 false |

```js
let ticks = 0;
const timer = setInterval(() => { ticks++; logd("次数 {}", ticks); }, 500);
const ready = thread.execSync(() => ticks >= 3, 5000);
cancelInterval(timer);
logi("完成：{}，次数：{}", ready, ticks);
```

V8 同时只运行一个 JS 线程。`sleep()`、等待节点、手势、输入法、部分文件和媒体阻塞操作会释放 V8 锁，其他线程才有机会执行。因此长期循环应包含 `sleep()`；纯 JS 忙循环会占用整个运行环境。`cancelThread()` 是协作取消，不能抢占纯计算循环；工具栏“停止”会终止整个脚本。不要在捕获取消异常后继续无限循环。

最多同时 32 个任务；时间必须是 0～2147483647 的整数。子线程异常写入错误日志。主脚本返回、发生错误、用户停止或断开调试连接后，子线程与定时器全部取消，数据库、媒体和节点快照同时清理。需要等待后台任务时，让主脚本通过 `execSync` 或 `sleep` 保持运行。

开发实现位于 `runtime/ThreadApi.java`、`runtime/ScriptBlocking.java` 和 `cpp/script_blocking.cpp`。阻塞段只能执行 Java/系统操作，不能调用 JS 或访问 V8 句柄。工作线程先取得 V8 Locker，再调用 Javet，保持 Java 句柄锁与 V8 锁的统一顺序。原生桥接绑定 Javet 5.0.11 / V8 15.2.124.17 的公开 ABI；升级时须同步头文件并执行共享闭包、取消、调试暂停、断连回归。
