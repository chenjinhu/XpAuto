package com.vixmobi.auto.runtime;

import com.caoccao.javet.exceptions.JavetException;
import com.caoccao.javet.interop.V8Runtime;
import com.caoccao.javet.interop.callback.IJavetDirectCallable;
import com.caoccao.javet.interop.callback.JavetCallbackContext;
import com.caoccao.javet.interop.callback.JavetCallbackType;
import com.caoccao.javet.values.reference.V8ValueFunction;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** 每个运行环境独立管理线程，回调保留原始闭包；V8 Locker 保证 JS 同时只有一个线程执行。 */
public final class ThreadApi {
  private final ConcurrentHashMap<Long, Worker> workers = new ConcurrentHashMap<>();
  private final AtomicLong sequence = new AtomicLong();
  private final Consumer<String> errors;
  private volatile boolean closing;
  private long isolate;

  private final class Worker {
    final long id;
    final V8ValueFunction callback;
    final long delay, interval;
    volatile boolean cancelled;
    Thread thread;

    Worker(long id, V8ValueFunction callback, long delay, long interval) {
      this.id = id;
      this.callback = callback;
      this.delay = delay;
      this.interval = interval;
    }

    void run() {
      try {
        if (delay > 0) Thread.sleep(delay);
        do {
          if (cancelled || closing) break;
          ScriptBlocking.locked(
              isolate,
              () -> {
                try {
                  if (!cancelled && !closing) callback.callVoid(null);
                } catch (JavetException error) {
                  if (!cancelled && !closing) errors.accept("子线程 " + id + "：" + error);
                }
              });
          if (interval <= 0) break;
          Thread.sleep(interval);
        } while (!cancelled && !closing);
      } catch (Exception error) {
        if (!cancelled && !closing) errors.accept("子线程 " + id + "：" + error);
      } finally {
        // 关闭回调需要重新获取 V8 锁，不能由持锁的调用者等待本线程。
        ScriptBlocking.locked(
            isolate,
            () -> {
              try {
                callback.close();
              } catch (JavetException ignored) {
              }
            });
        workers.remove(id);
      }
    }
  }

  public ThreadApi(Consumer<String> errors) {
    this.errors = errors;
  }

  public void install(V8Runtime runtime) throws JavetException {
    JavetCallbackContext spawn =
        new JavetCallbackContext(
            "__xpautoSpawn",
            JavetCallbackType.DirectCallNoThisAndResult,
            (IJavetDirectCallable.NoThisAndResult<Exception>)
                args -> {
                  isolate = ScriptBlocking.capture();
                  if (closing) throw new IllegalStateException("脚本线程已关闭");
                  if (workers.size() >= 32) throw new IllegalStateException("最多同时运行 32 个脚本线程");
                  if (!(args[0] instanceof V8ValueFunction))
                    throw new IllegalArgumentException("线程回调必须是函数");
                  long delay = args[1].asLong(), interval = args[2].asLong();
                  if (delay < 0 || interval < 0) throw new IllegalArgumentException("线程时间不能为负数");
                  long id = sequence.incrementAndGet();
                  Worker worker =
                      new Worker(id, ((V8ValueFunction) args[0]).toClone(), delay, interval);
                  worker.thread = new Thread(worker::run, "XpAuto-worker-" + id);
                  workers.put(id, worker);
                  worker.thread.start();
                  return runtime.createV8ValueLong(id);
                });
    try (V8ValueFunction function = runtime.createV8ValueFunction(spawn)) {
      runtime.getGlobalObject().set("__xpautoSpawn", function);
    }
    runtime.getGlobalObject().set("__xpautoThread", this);
  }

  public boolean cancelThread(long id) {
    Worker worker = workers.get(id);
    if (worker == null) return false;
    worker.cancelled = true;
    worker.thread.interrupt();
    return true;
  }

  public boolean isCancelled(long id) {
    Worker worker = workers.get(id);
    return worker == null || worker.cancelled;
  }

  public void stopAll() {
    for (Long id : workers.keySet()) cancelThread(id);
  }

  public void sleep(long milliseconds) {
    ScriptBlocking.sleep(milliseconds);
  }

  public void reset() {
    closing = false;
  }

  /** 在主脚本已退出 V8 后调用。忙循环必须由整个运行环境的 terminateExecution 终止。 */
  public void finish(V8Runtime runtime) {
    closing = true;
    stopAll();
    long warningAt = System.nanoTime() + 2000000000L;
    boolean interrupted = Thread.interrupted(), warned = false;
    try {
      while (!workers.isEmpty()) {
        runtime.terminateExecution();
        for (Worker worker : workers.values()) {
          try {
            worker.thread.join(25);
          } catch (InterruptedException error) {
            interrupted = true;
          }
        }
        if (!warned && System.nanoTime() > warningAt) {
          warned = true;
          errors.accept("正在等待后台操作退出；退出前不会重用或销毁 V8 运行环境");
        }
      }
    } finally {
      // 调用线程被打断也必须等工作线程释放句柄，随后再恢复中断状态。
      if (interrupted) Thread.currentThread().interrupt();
    }
  }

  public boolean isExecuting() {
    return !workers.isEmpty();
  }
}
