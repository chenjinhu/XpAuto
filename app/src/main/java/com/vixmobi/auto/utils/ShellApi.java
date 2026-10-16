package com.vixmobi.auto.utils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** 同步的 JavaScript Shell 接口；Root 授权针对当前应用的 UID。 */
public final class ShellApi implements AutoCloseable {
    private static final long COMMAND_TIMEOUT = 60_000;
    private static final long INSTALL_TIMEOUT = 180_000;
    private static final String ANDROID_SH = "/system/bin/sh";
    private static final String ROOT_GUARD = "[ \"$(id -u)\" = 0 ] || { echo 'XpAuto: Root permission unavailable' >&2; exit 126; };\n";
    // 监督进程监听 Java 保持打开的输入管道，关闭管道表示停止、超时或断开连接。
    // setsid 创建独立进程组，取消时只终止本次命令及其组内子进程。
    private static final String SUPERVISOR = "owner=$$\nexec 3<&0\n"
            + "( IFS= read -r cancel <&3; kill -KILL -- -\"$owner\" ) &\n"
            + "watcher=$!\n"
            + ANDROID_SH + " -c \"$1\" </dev/null &\n"
            + "worker=$!\nwait \"$worker\"\nstatus=$?\n"
            // Android 的 mksh 等待被信号终止的子进程时会覆盖退出码，直接保留业务命令退出码。
            + "kill \"$watcher\" 2>/dev/null\nexit \"$status\"";

    static final class RootConfig {
        final List<String> candidates = new ArrayList<>(Arrays.asList(
                "su", "/system/bin/su", "/system/xbin/su", "/sbin/su"));
        volatile String selected;
        synchronized void add(String path) {
            candidates.remove(path);
            candidates.add(0, path);
            selected = null;
        }
        synchronized List<String> candidates() { return new ArrayList<>(candidates); }
    }

    private final RootConfig roots;
    private final Consumer<String> logger;
    private final BooleanSupplier externalCancellation;
    private final ShellProcess process = new ShellProcess();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicBoolean executing = new AtomicBoolean();
    private volatile AbstractShell.Result lastResult = new AbstractShell.Result();
    private final boolean android = new File(ANDROID_SH).isFile();

    ShellApi(RootConfig roots, Consumer<String> logger, BooleanSupplier externalCancellation) {
        this.roots = roots;
        this.logger = logger;
        this.externalCancellation = externalCancellation;
    }

    public boolean addSuBin(String path) {
        if (!validText(path) || (!path.startsWith("/") && !path.matches("[A-Za-z0-9_.-]+"))) {
            fail("addSuBin requires an absolute executable path or a command name");
            return false;
        }
        roots.add(path);
        AbstractShell.Result result = new AbstractShell.Result();
        result.code = 0;
        save(result);
        return true;
    }

    public boolean su() { return su(COMMAND_TIMEOUT); }

    public boolean su(long timeoutMs) {
        validateTimeout(timeoutMs);
        roots.selected = null;
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        AbstractShell.Result result = new AbstractShell.Result();
        StringBuilder failures = new StringBuilder();
        for (String executable : roots.candidates()) {
            long remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
            if (remaining <= 0) { result.timedOut = true; break; }
            result = invoke(Arrays.asList(executable, "-c", "id -u"), remaining);
            if (ok(result) && "0".equals(result.result.trim())) {
                roots.selected = executable;
                save(result);
                return true;
            }
            failures.append(executable).append(": ").append(result.error).append('\n');
            if (result.cancelled || result.timedOut) break;
            // 只有无法启动的路径才继续尝试。已启动的 su 拒绝授权时立即结束，
            // 避免同一 su 的不同路径反复弹出授权请求。
            if (result.code != -1) break;
        }
        if (result.code == 0) result.code = 126;
        result.error = failures.toString();
        result.error += "\nRoot authorization failed: su must return UID 0";
        if (!result.result.isEmpty()) result.error += "\nAuthorization output: " + result.result;
        result.result = "";
        save(result);
        return false;
    }

    public String execCommand(String command) { return execCommand(command, roots.selected != null); }
    public String execCommand(String command, boolean root) { return execCommand(command, root, COMMAND_TIMEOUT); }
    public String execCommand(String command, boolean root, long timeoutMs) {
        return execute(command, root, timeoutMs).result;
    }
    public String sudo(String command) { return sudo(command, COMMAND_TIMEOUT); }
    public String sudo(String command, long timeoutMs) { return execCommand(command, true, timeoutMs); }
    public String ROOT(String command) { return sudo(command); }
    public String ROOT(String command, long timeoutMs) { return sudo(command, timeoutMs); }

    public boolean installApp(String path) {
        if (!validText(path) || !path.startsWith("/")) {
            fail("installApp requires an absolute APK path on the phone");
            return false;
        }
        String file = quote(path);
        // 把 APK 字节流交给包管理器，避免 system_server 无法直接读取共享存储或私有路径。
        String command = "[ -f " + file + " ] && [ -r " + file + " ] || { echo 'APK is not a readable file' >&2; exit 1; };\n"
                + "size=$(stat -c %s " + file + ") || exit $?;\n"
                + "pm install -r --user current -S \"$size\" -- - < " + file;
        return packageSuccess(execute(command, true, INSTALL_TIMEOUT));
    }

    public boolean uninstallApp(String packageName) {
        if (!validPackage(packageName)) return false;
        return packageSuccess(execute("pm uninstall --user current " + quote(packageName), true, COMMAND_TIMEOUT));
    }

    public boolean stopApp(String packageName) {
        if (!validPackage(packageName)) return false;
        String name = quote(packageName);
        return ok(execute("installed=$(pm path --user current " + name + ") || exit $?;\n"
                + "case \"$installed\" in package:*) ;; *) echo 'Package not installed for current user' >&2; exit 1;; esac;\n"
                + "am force-stop --user current " + name, true, COMMAND_TIMEOUT));
    }

    public AbstractShell.Result getLastResult() {
        AbstractShell.Result source = lastResult;
        AbstractShell.Result copy = new AbstractShell.Result();
        copy.code = source.code;
        copy.result = source.result;
        copy.error = source.error;
        copy.timedOut = source.timedOut;
        copy.cancelled = source.cancelled;
        copy.truncated = source.truncated;
        return copy;
    }

    public void cancel() { cancelled.set(true); }
    public void resetCancellation() { cancelled.set(false); }
    public boolean isExecuting() { return executing.get(); }
    @Override public void close() { cancel(); }

    private AbstractShell.Result execute(String command, boolean root, long timeoutMs) {
        validateTimeout(timeoutMs);
        if (command == null || command.indexOf('\0') >= 0) return fail("Command must be a string without NUL bytes");
        if (isCancelled()) {
            AbstractShell.Result result = new AbstractShell.Result();
            result.cancelled = true;
            result.error = "Shell command cancelled";
            return save(result);
        }
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        if (root && roots.selected == null && !su(timeoutMs)) return lastResult;
        String executable = root ? roots.selected : (android ? ANDROID_SH : "/bin/sh");
        if (executable == null) return fail("Root configuration changed; call su() again");
        String guarded = root ? ROOT_GUARD + command : command;
        String script = android ? "exec /system/bin/setsid -w " + ANDROID_SH + " -c "
                + quote(SUPERVISOR) + " xpauto-shell " + quote(guarded) : guarded;
        long remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
        if (remaining <= 0) {
            AbstractShell.Result result = new AbstractShell.Result();
            result.timedOut = true;
            result.error = "Shell command timed out during root authorization";
            return save(result);
        }
        AbstractShell.Result result = invoke(Arrays.asList(executable, "-c", script), remaining);
        // 用户命令可能已经产生副作用，失败时不能自动换路径重试。
        if (root && result.code == 126) roots.selected = null;
        return save(result);
    }

    private AbstractShell.Result invoke(List<String> args, long timeoutMs) {
        executing.set(true);
        try { return process.run(args, timeoutMs, this::isCancelled); }
        finally { executing.set(false); }
    }

    private boolean isCancelled() { return cancelled.get() || externalCancellation.getAsBoolean(); }
    private static boolean ok(AbstractShell.Result result) { return result.code == 0 && !result.timedOut && !result.cancelled; }
    private boolean packageSuccess(AbstractShell.Result result) {
        if (!ok(result)) return false;
        for (String line : result.result.split("\\r?\\n")) if ("Success".equals(line.trim())) return true;
        result.error += "\nPackageManager did not report Success";
        save(result);
        return false;
    }
    private boolean validPackage(String name) {
        if (name != null && name.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*")) return true;
        fail("Invalid Android package name");
        return false;
    }
    private static boolean validText(String value) {
        return value != null && !value.trim().isEmpty() && value.indexOf('\0') < 0
                && value.indexOf('\n') < 0 && value.indexOf('\r') < 0;
    }
    static String quote(String value) { return "'" + value.replace("'", "'\\''") + "'"; }
    private static void validateTimeout(long timeoutMs) {
        if (timeoutMs < 1 || timeoutMs > 3_600_000) throw new IllegalArgumentException("timeoutMs must be 1..3600000");
    }
    private AbstractShell.Result fail(String error) {
        AbstractShell.Result result = new AbstractShell.Result();
        result.error = error;
        return save(result);
    }
    private AbstractShell.Result save(AbstractShell.Result result) {
        lastResult = result;
        // 取消必须通过异常中断 Java 回调；正常返回可能让 V8 在检查终止标志前，
        // 继续执行下一条 JS 语句。
        if (result.cancelled) throw new CancellationException("Shell execution cancelled");
        if (!ok(result) || !result.error.isEmpty() || result.truncated) {
            logger.accept("[shell] exit=" + result.code + (result.truncated ? " (output truncated)" : "")
                    + (result.error.isEmpty() ? "" : "\n" + logText(result.error))
                    + (!ok(result) && !result.result.isEmpty() ? "\n" + logText(result.result) : ""));
        }
        return result;
    }
    private static String logText(String text) {
        return text.length() <= 4096 ? text : text.substring(0, 4096) + "\n…see shell.getLastResult()";
    }
}
