package com.vixmobi.auto.devtools;

import java.util.ArrayDeque;

/** 首页和日志悬浮窗共用的内存缓存；限制条数和文本大小，避免长日志拖慢界面。 */
final class LogBuffer {
    static final int MAX_ENTRIES = 150;
    static final int MAX_ENTRY_CHARS = 4000;
    static final int MAX_TOTAL_CHARS = 64000;
    private final ArrayDeque<String> entries = new ArrayDeque<>();
    private int characters;
    private String cachedText = "";

    synchronized void append(String message) {
        String entry = String.valueOf(message);
        if (entry.length() > MAX_ENTRY_CHARS) {
            String suffix = "…（显示已截断）";
            int end = MAX_ENTRY_CHARS - suffix.length();
            // 不从 UTF-16 代理对中间截断，避免把表情等字符显示成乱码。
            if (Character.isHighSurrogate(entry.charAt(end - 1))) end--;
            entry = entry.substring(0, end) + suffix;
        }
        entries.addLast(entry);
        characters += entry.length() + 1;
        while (entries.size() > MAX_ENTRIES || characters > MAX_TOTAL_CHARS) {
            characters -= entries.removeFirst().length() + 1;
        }
        cachedText = null;
    }

    synchronized String text() {
        // 两个界面都定时读取；没有新内容时复用快照，不重复拼接整段日志。
        if (cachedText == null) cachedText = String.join("\n", entries);
        return cachedText;
    }

    /** 从最早的日志开始清除换行分隔的行，界面自动折行不额外计数。 */
    synchronized void clear(int lines) {
        if (lines == -1) { clear(); return; }
        if (lines < -1) throw new IllegalArgumentException("行数不能小于 -1");
        if (lines == 0) return;
        while (lines-- > 0 && !entries.isEmpty()) {
            String entry = entries.removeFirst();
            characters -= entry.length() + 1;
            int newline = entry.indexOf('\n');
            if (newline >= 0) {
                String remaining = entry.substring(newline + 1);
                entries.addFirst(remaining);
                characters += remaining.length() + 1;
            }
        }
        cachedText = null;
    }

    synchronized void clear() {
        entries.clear();
        characters = 0;
        cachedText = "";
    }
}
