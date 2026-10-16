package com.vixmobi.auto.runtime;

import androidx.annotation.Keep;
import com.vixmobi.auto.Constant;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;

/** 阻塞 Java 操作时让出 V8，其他脚本线程可继续执行；回到 JS 前自动重新加锁。 */
@Keep
public final class ScriptBlocking {
  static {
    System.loadLibrary("xpauto-inspector");
  }

  private static final java.util.concurrent.atomic.AtomicInteger EXECUTING =
      new java.util.concurrent.atomic.AtomicInteger();

  public static boolean isExecuting() {
    return EXECUTING.get() > 0;
  }

  private ScriptBlocking() {}

  public static native long capture();

  public static native void locked(long isolate, Runnable action);

  private static native void invoke(Runnable action);

  public static void run(Runnable action) {
    EXECUTING.incrementAndGet();
    try {
      invoke(action);
    } finally {
      EXECUTING.decrementAndGet();
    }
  }

  public static <T> T call(Callable<T> action) {
    Object[] result = new Object[1];
    run(
        () -> {
          try {
            result[0] = action.call();
          } catch (RuntimeException error) {
            throw error;
          } catch (Exception error) {
            throw new IllegalStateException(error.getMessage(), error);
          }
        });
    @SuppressWarnings("unchecked")
    T value = (T) result[0];
    return value;
  }

  public static void checkCancelled() {
    if (Constant.killThread || Thread.currentThread().isInterrupted())
      throw new CancellationException("脚本操作已取消");
  }

  public static void sleep(long millis) {
    if (millis < 0) throw new IllegalArgumentException("等待时间不能为负数");
    run(
        () -> {
          long end = System.nanoTime() + Math.min(millis, Integer.MAX_VALUE) * 1000000L;
          do {
            checkCancelled();
            long remaining = end - System.nanoTime();
            if (!Constant.isStop && remaining <= 0) return;
            try {
              Thread.sleep(Constant.isStop ? 25 : Math.max(1, Math.min(25, remaining / 1000000L)));
            } catch (InterruptedException error) {
              Thread.currentThread().interrupt();
              checkCancelled();
            }
          } while (true);
        });
  }
}
