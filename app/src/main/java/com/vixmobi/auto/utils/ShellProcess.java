package com.vixmobi.auto.utils;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** 同时读取标准输出和错误输出，限制内存与等待时间；不依赖 Android，便于 JVM 测试。 */
final class ShellProcess {
    static final int OUTPUT_LIMIT = 1024 * 1024;

    AbstractShell.Result run(List<String> args, long timeoutMs, BooleanSupplier cancelled) {
        AbstractShell.Result result = new AbstractShell.Result();
        Process process = null;
        Capture stdout = null, stderr = null;
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        try {
            if (cancelled.getAsBoolean()) {
                result.cancelled = true;
                return result;
            }
            process = new ProcessBuilder(args).start();
            stdout = new Capture(process.getInputStream());
            stderr = new Capture(process.getErrorStream());
            stdout.start();
            stderr.start();
            while (true) {
                if (cancelled.getAsBoolean()) { result.cancelled = true; break; }
                if (System.nanoTime() >= deadline) { result.timedOut = true; break; }
                if (process.waitFor(25, TimeUnit.MILLISECONDS)) {
                    result.code = process.exitValue();
                    break;
                }
            }
            if (result.cancelled || result.timedOut) stop(process);
            // 后台子进程可能持有管道，不能无限等待输出流结束。
            stdout.join(500);
            stderr.join(500);
        } catch (InterruptedException error) {
            result.cancelled = true;
            if (process != null) stop(process);
            Thread.currentThread().interrupt();
        } catch (IOException error) {
            result.error = error.toString();
        } finally {
            if (stdout != null) {
                result.result = stdout.text();
                result.error += stderr.text();
                result.truncated = stdout.truncated || stderr.truncated || stdout.isAlive() || stderr.isAlive();
            }
            if (process != null) {
                close(process.getOutputStream());
                close(process.getInputStream());
                close(process.getErrorStream());
                process.destroy();
            }
            if (result.cancelled) result.error += "\nShell command cancelled";
            if (result.timedOut) result.error += "\nShell command timed out after " + timeoutMs + " ms";
        }
        return result;
    }

    private static void stop(Process process) {
        // 关闭输入后，Android 监督进程会终止独立进程组，包括通过 Root 守护进程启动的命令。
        // 正常运行期间保持输入管道打开，避免被误判为取消。
        close(process.getOutputStream());
        try {
            if (!process.waitFor(300, TimeUnit.MILLISECONDS)) process.destroyForcibly();
        } catch (InterruptedException ignored) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }

    private static void close(Closeable stream) {
        try { stream.close(); } catch (IOException ignored) { }
    }

    private static final class Capture extends Thread {
        private final InputStream input;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        volatile boolean truncated;

        Capture(InputStream input) {
            super("XpAuto-shell-output");
            this.input = input;
            setDaemon(true);
        }

        @Override public void run() {
            byte[] buffer = new byte[8192];
            try {
                int count;
                while ((count = input.read(buffer)) != -1) {
                    synchronized (bytes) {
                        int keep = Math.min(count, OUTPUT_LIMIT - bytes.size());
                        bytes.write(buffer, 0, keep);
                        if (keep < count) truncated = true;
                    }
                }
            } catch (IOException ignored) { /* 取消时主动关闭流，读取线程随之退出。 */ }
        }

        String text() {
            synchronized (bytes) {
                return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
            }
        }
    }
}
