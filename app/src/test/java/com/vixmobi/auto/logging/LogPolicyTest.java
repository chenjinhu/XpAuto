package com.vixmobi.auto.logging;

import org.junit.Test;
import static org.junit.Assert.*;

public class LogPolicyTest {
    @Test public void filtersByMinimumLevelAndControlsToastSeparately() {
        LogPolicy policy = new LogPolicy();
        String[] levels = { "debug", "info", "warn", "error", "off" };
        for (int min = 0; min < levels.length; min++) {
            assertTrue(policy.set(levels[min], min % 2 == 0));
            for (int level = 0; level < 4; level++) assertEquals(level >= min, policy.enabled(levels[level]));
            assertEquals(min % 2 == 0, policy.displayToast());
            assertFalse(policy.enabled("off"));
        }
    }
    @Test public void invalidLevelDoesNotChangeCurrentSettings() {
        LogPolicy policy = new LogPolicy();
        policy.set("WARN", false);
        assertFalse(policy.set("invalid", true));
        assertFalse(policy.set(null, true));
        assertFalse(policy.enabled("info"));
        assertTrue(policy.enabled("warn"));
        assertFalse(policy.displayToast());
    }
    @Test public void sessionsAreIndependentAndResetRestoresDefaults() {
        LogPolicy first = new LogPolicy(), second = new LogPolicy();
        first.set("off", false);
        assertTrue(second.enabled("debug"));
        first.reset(); assertTrue(first.enabled("debug")); assertTrue(first.displayToast());
    }
}
