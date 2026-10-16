package com.vixmobi.auto.node;

import java.util.LinkedHashMap;
import java.util.Map;

/** 按窗口保存已经校验过的 Activity；脚本线程读取与无障碍线程更新使用同一把锁。 */
final class RunningActivityCache {
    private static final int MAX_WINDOWS = 64;
    private final Map<Integer, Entry> entries = new LinkedHashMap<>();
    private static final class Entry {
        final String packageName, activityName;
        Entry(String packageName, String activityName) { this.packageName = packageName; this.activityName = activityName; }
    }
    synchronized void put(int windowId, String packageName, String activityName) {
        if (windowId < 0 || packageName == null || packageName.isEmpty() || activityName == null || activityName.isEmpty()) return;
        entries.remove(windowId);
        entries.put(windowId, new Entry(packageName, activityName));
        if (entries.size() > MAX_WINDOWS) entries.remove(entries.keySet().iterator().next());
    }
    synchronized String get(int windowId, String packageName) {
        Entry entry = entries.get(windowId);
        return entry != null && entry.packageName.equals(packageName) ? entry.activityName : null;
    }
    synchronized void remove(int windowId) { entries.remove(windowId); }
    synchronized void clear() { entries.clear(); }
    static String fullClassName(String packageName, String className) {
        if (packageName == null || packageName.isEmpty() || className == null || className.isEmpty()) return null;
        if (className.startsWith(".")) return packageName + className;
        return className.contains(".") ? className : packageName + "." + className;
    }
}
