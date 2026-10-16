package com.vixmobi.auto.utils;

import com.vixmobi.auto.node.AccUtils;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** 保留原有 Java 静态调用入口；每个脚本会话使用自己的 ShellApi 处理取消。 */
public final class ShellUtils {
    private static final ShellApi.RootConfig ROOT_CONFIG = new ShellApi.RootConfig();
    private static final ShellApi DEFAULT = createSession(AccUtils::printLogMsg, () -> false);

    private ShellUtils() { }

    public static ShellApi createSession(Consumer<String> log, BooleanSupplier cancelled) {
        return new ShellApi(ROOT_CONFIG, log, cancelled);
    }

    public static boolean addSuBin(String path) { return DEFAULT.addSuBin(path); }
    public static boolean su() { return DEFAULT.su(); }
    public static String execCommand(String command) { return DEFAULT.execCommand(command); }
    public static String execCommand(String command, boolean root) { return DEFAULT.execCommand(command, root); }
    public static String execCommand(String command, boolean root, long timeoutMs) {
        return DEFAULT.execCommand(command, root, timeoutMs);
    }
    public static String sudo(String command) { return DEFAULT.sudo(command); }
    public static String ROOT(String command) { return DEFAULT.ROOT(command); }
    public static boolean installApp(String path) { return DEFAULT.installApp(path); }
    public static boolean uninstallApp(String packageName) { return DEFAULT.uninstallApp(packageName); }
    public static boolean stopApp(String packageName) { return DEFAULT.stopApp(packageName); }
    public static AbstractShell.Result getLastResult() { return DEFAULT.getLastResult(); }
}
