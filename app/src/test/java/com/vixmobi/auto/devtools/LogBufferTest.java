package com.vixmobi.auto.devtools;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class LogBufferTest {
    @Test public void keepsRecentEntriesAndDropsOldest() {
        LogBuffer logs = new LogBuffer();
        for (int i = 0; i < 200; i++) logs.append("日志 " + i);
        String[] entries = logs.text().split("\n");
        assertEquals(150, entries.length);
        assertEquals("日志 50", entries[0]);
        assertEquals("日志 199", entries[149]);
    }

    @Test public void boundsLargeOutputAndKeepsLatestMessage() {
        LogBuffer logs = new LogBuffer();
        for (int i = 0; i < 40; i++) logs.append("消息 " + i + " " + repeated('x', 5000));
        String text = logs.text();
        assertTrue(text.length() <= LogBuffer.MAX_TOTAL_CHARS);
        assertFalse(text.contains("消息 0 "));
        assertTrue(text.contains("消息 39 "));
        for (String line : text.split("\n")) assertTrue(line.length() <= LogBuffer.MAX_ENTRY_CHARS);
        assertTrue(text.endsWith("…（显示已截断）"));
    }

    @Test public void truncationDoesNotSplitEmoji() {
        LogBuffer logs = new LogBuffer();
        int boundary = LogBuffer.MAX_ENTRY_CHARS - "…（显示已截断）".length();
        logs.append(repeated('中', boundary - 1) + "😀" + repeated('文', 100));
        // 截断边界落在表情中间时，结果不能包含孤立的高、低代理字符。
        assertFalse(logs.text().codePoints().anyMatch(value -> value >= 0xD800 && value <= 0xDFFF));
    }

    @Test public void clearUpdatesBothReadersAndAllowsNewLogs() {
        LogBuffer logs = new LogBuffer();
        logs.append("旧日志");
        String snapshot = logs.text();
        assertSame(snapshot, logs.text());
        logs.clear();
        assertEquals("", logs.text());
        logs.append("新日志");
        assertEquals("新日志", logs.text());
    }

    @Test public void clearsOldestPhysicalLinesIncludingMultilineEntries() {
        LogBuffer logs = new LogBuffer();
        logs.append("第一行\n第二行\n"); logs.append("最后一行");
        logs.clear(1); assertEquals("第二行\n\n最后一行", logs.text());
        logs.clear(2); assertEquals("最后一行", logs.text());
        String previous = logs.text(); logs.clear(0); assertSame(previous, logs.text());
        logs.clear(10000); assertEquals("", logs.text());
        logs.append("继续"); logs.clear(-1); assertEquals("", logs.text());
    }

    private static String repeated(char value, int count) {
        char[] text = new char[count];
        Arrays.fill(text, value);
        return new String(text);
    }
}
