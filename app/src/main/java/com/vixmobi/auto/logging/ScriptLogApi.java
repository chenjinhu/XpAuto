package com.vixmobi.auto.logging;

import android.content.Context;
import android.util.Log;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/** JS 负责格式化，Java 负责等级过滤、输出和主线程 Toast。 */
public final class ScriptLogApi {
    private final Context context;
    private final BiConsumer<String, String> output;
    private final IntConsumer clear;
    private final BooleanSupplier externalCancelled;
    private final LogPolicy policy = new LogPolicy();
    private final Object toastOwner = new Object();
    private volatile boolean cancelled, completed;

    public ScriptLogApi(Context context, BiConsumer<String, String> output, IntConsumer clear, BooleanSupplier cancelled) {
        this.context = context.getApplicationContext();
        this.output = output;
        this.clear = clear;
        this.externalCancelled = cancelled;
    }

    public boolean enabled(String level) { return policy.enabled(level); }

    public void write(String level, String message) {
        check();
        if (!policy.enabled(level)) return;
        String normalized = level.toLowerCase(Locale.ROOT);
        int priority = normalized.equals("error") ? Log.ERROR : normalized.equals("warn") ? Log.WARN
                : normalized.equals("info") ? Log.INFO : Log.DEBUG;
        Log.println(priority, "XpAutoScript", message);
        String type = normalized.equals("warn") ? "warning" : normalized;
        output.accept(type, "[" + normalized.toUpperCase(Locale.ROOT) + "] " + message);
    }

    public boolean setLogLevel(String level, boolean displayToast) {
        check();
        if (!policy.set(level, displayToast)) return false;
        if (!displayToast) ToastPresenter.cancel(toastOwner);
        return true;
    }

    public void toast(String message, String json, int duration) {
        check();
        if (!policy.displayToast()) return;
        JsonElement parsed = JsonParser.parseString(json);
        if (!parsed.isJsonNull() && !parsed.isJsonObject()) throw new IllegalArgumentException("toast extra 必须为对象或 null");
        JsonObject extra = parsed.isJsonNull() ? null : parsed.getAsJsonObject();
        // 不让上一次“已完成”的状态覆盖本次控制台调用的取消状态。
        completed = false;
        ToastPresenter.show(context, toastOwner, message, extra, duration,
                () -> !cancelled && policy.displayToast(), error -> output.accept("error", "[ERROR] " + error));
    }

    public void clearLog(int lines) {
        check();
        if (lines < -1) throw new IllegalArgumentException("clearLog 行数必须为 -1 或非负整数");
        clear.accept(lines);
    }

    public void reset() {
        ToastPresenter.cancel(toastOwner);
        cancelled = false; completed = false; policy.reset();
    }

    public void finish(boolean success) {
        completed = success;
        if (!success) stop(false);
    }

    public void stop(boolean closing) {
        // 正常完成后的提示按时长显示，不能因为“不调试运行”自动断连而闪退。
        if (closing && completed) return;
        cancelled = true;
        ToastPresenter.cancel(toastOwner);
    }

    private void check() {
        if (cancelled || externalCancelled.getAsBoolean()) throw new CancellationException("日志或 Toast 操作已取消");
    }
}
