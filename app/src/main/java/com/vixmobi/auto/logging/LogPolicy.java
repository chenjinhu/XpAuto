package com.vixmobi.auto.logging;

import java.util.Locale;

/** 每次脚本运行独享过滤设置，不影响引擎错误和运行状态通知。 */
public final class LogPolicy {
    private volatile int threshold;
    private volatile boolean displayToast = true;

    public boolean set(String level, boolean toast) {
        int value = rank(level);
        if (value < 0) return false;
        threshold = value;
        displayToast = toast;
        return true;
    }

    public boolean enabled(String level) {
        int value = rank(level);
        return value >= threshold && value >= 0 && value < 4;
    }

    public boolean displayToast() { return displayToast; }
    public void reset() { threshold = 0; displayToast = true; }

    private int rank(String level) {
        if (level == null) return -1;
        switch (level.toLowerCase(Locale.ROOT)) {
            case "debug": return 0;
            case "info": return 1;
            case "warn": return 2;
            case "error": return 3;
            case "off": return 4;
            default: return -1;
        }
    }
}
