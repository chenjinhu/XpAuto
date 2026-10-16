package com.vixmobi.auto.node;

import org.junit.Test;
import static org.junit.Assert.*;

/** 验证页面切换、服务断开与包名隔离，防止把旧页面当成当前页面。 */
public class RunningActivityCacheTest {
    @Test public void isolatesWindowsAndPackagesAndUpdatesSameWindow() {
        RunningActivityCache cache = new RunningActivityCache();
        cache.put(1, "app.a", "app.a.First"); cache.put(2, "app.a", "app.a.Second");
        assertEquals("app.a.First", cache.get(1, "app.a"));
        assertEquals("app.a.Second", cache.get(2, "app.a"));
        assertNull(cache.get(1, "app.b")); assertNull(cache.get(3, "app.a"));
        cache.put(1, "app.b", "app.b.Main");
        assertNull(cache.get(1, "app.a")); assertEquals("app.b.Main", cache.get(1, "app.b"));
    }
    @Test public void removedWindowsAndDisconnectedServicesLoseTheirRecords() {
        RunningActivityCache cache = new RunningActivityCache();
        cache.put(1, "app", "app.First"); cache.put(2, "app", "app.Second");
        cache.remove(1); assertNull(cache.get(1, "app")); assertEquals("app.Second", cache.get(2, "app"));
        cache.clear(); assertNull(cache.get(2, "app"));
    }
    @Test public void rejectsMissingFieldsAndBoundsHistorySize() {
        RunningActivityCache cache = new RunningActivityCache();
        cache.put(-1, "app", "app.Main"); cache.put(1, null, "app.Main");
        cache.put(2, "app", ""); cache.put(3, "", "app.Main");
        for (int id = -1; id <= 3; id++) assertNull(cache.get(id, "app"));
        for (int id = 0; id <= 64; id++) cache.put(id, "app", "app.Page" + id);
        assertNull(cache.get(0, "app")); assertEquals("app.Page64", cache.get(64, "app"));
    }
    @Test public void normalizesActivityNamesWithoutManufacturingNullStrings() {
        assertEquals("app.Main", RunningActivityCache.fullClassName("app", ".Main"));
        assertEquals("app.Main", RunningActivityCache.fullClassName("app", "Main"));
        assertEquals("other.Screen", RunningActivityCache.fullClassName("app", "other.Screen"));
        assertNull(RunningActivityCache.fullClassName(null, "Main"));
        assertNull(RunningActivityCache.fullClassName("app", null));
        assertNull(RunningActivityCache.fullClassName("", "Main"));
    }
    @Test public void unavailableServiceReturnsDocumentedEmptyResults() {
        assertEquals("", RunningAppInfo.getCurrentRunningPkg());
        assertNull(RunningAppInfo.getRunningActivity());
    }
}
