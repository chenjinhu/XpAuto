package com.vixmobi.auto.floaty;

import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

/** View 只在主线程访问；等待期间检查取消，停止脚本不会被 UI 请求卡住。 */
public final class UiDispatcher {
    private final Handler handler = new Handler(Looper.getMainLooper());

    public <T> T call(Callable<T> action, BooleanSupplier cancelled) throws Exception {
        if (cancelled.getAsBoolean()) throw new CancellationException("悬浮窗操作已取消");
        if (Looper.myLooper() == Looper.getMainLooper()) return action.call();
        FutureTask<T> task = new FutureTask<>(() -> {
            if (cancelled.getAsBoolean()) throw new CancellationException("悬浮窗操作已取消");
            return action.call();
        });
        handler.post(task);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        try {
            while (true) {
                if (cancelled.getAsBoolean()) throw new CancellationException("悬浮窗操作已取消");
                try { return task.get(50, TimeUnit.MILLISECONDS); }
                catch (TimeoutException error) {
                    if (System.nanoTime() >= deadline) throw new IllegalStateException("主线程响应超时");
                }
            }
        } catch (ExecutionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            throw new IllegalStateException(cause);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new CancellationException("悬浮窗操作已中断");
        } finally {
            // 尚未开始的任务必须移除，防止停止后迟到的窗口又显示出来。
            handler.removeCallbacks(task);
            task.cancel(false);
        }
    }
    void post(Runnable action) { handler.post(action); }
}
