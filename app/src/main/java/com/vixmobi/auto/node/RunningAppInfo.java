package com.vixmobi.auto.node;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.List;

/** 获取无障碍可见的前台应用信息，不执行 Shell，不依赖 Root 或使用情况访问权限。 */
public final class RunningAppInfo {
    private static final RunningActivityCache ACTIVITIES = new RunningActivityCache();
    private RunningAppInfo() { }

    private static final class Foreground {
        final int windowId;
        final String packageName;
        Foreground(int windowId, String packageName) { this.windowId = windowId; this.packageName = packageName; }
    }

    /** 未连接服务或没有可读取的应用窗口时返回空字符串。 */
    public static String getCurrentRunningPkg() {
        Foreground foreground = readForeground();
        return foreground == null ? "" : foreground.packageName;
    }

    /** 只返回当前窗口经过校验的 Activity，不用上一应用或根控件的类名补值。 */
    public static String getRunningActivity() {
        Foreground foreground = readForeground();
        return foreground == null ? null : ACTIVITIES.get(foreground.windowId, foreground.packageName);
    }

    static void clear() { ACTIVITIES.clear(); }

    static void onEvent(AccessibilityService service, AccessibilityEvent event) {
        if (event == null) return;
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            if ((event.getWindowChanges() & AccessibilityEvent.WINDOWS_CHANGE_REMOVED) != 0)
                ACTIVITIES.remove(event.getWindowId());
            return;
        }
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        String packageName = text(event.getPackageName());
        String className = RunningActivityCache.fullClassName(packageName, text(event.getClassName()));
        if (className == null || event.getWindowId() < 0) return;
        try {
            // 窗口事件也可能来自 Dialog、View 或 Fragment；只接受清单中注册的 Activity。
            service.getPackageManager().getActivityInfo(new ComponentName(packageName, className), 0);
            ACTIVITIES.put(event.getWindowId(), packageName, className);
            trace(event.getWindowId(), packageName, className, true);
        } catch (PackageManager.NameNotFoundException | SecurityException ignored) {
            // 类名不是 Activity，或该应用不在包可见范围内，保留已有有效记录。
            trace(event.getWindowId(), packageName, className, false);
        }
    }
    /** 按需开启 Logcat 的 XpAutoForeground/DEBUG，定位第三方应用未暴露 Activity 的情况。 */
    private static void trace(int windowId, String packageName, String className, boolean accepted) {
        if (Log.isLoggable("XpAutoForeground", Log.DEBUG))
            Log.d("XpAutoForeground", "window=" + windowId + " " + packageName + "/" + className + " accepted=" + accepted);
    }

    private static Foreground readForeground() {
        AccessibilityService service = AccUtils.AccessibilityHelper;
        if (service == null) return null;
        try {
            List<AccessibilityWindowInfo> windows = service.getWindows();
            try {
                AccessibilityWindowInfo selected = null;
                for (AccessibilityWindowInfo window : windows) {
                    // 输入法、系统浮层和自动化悬浮窗不作为正在操作的应用。
                    if (window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
                    if (selected == null || priority(window) > priority(selected)
                            || (priority(window) == priority(selected) && window.getLayer() > selected.getLayer())) selected = window;
                }
                if (selected != null) return fromRoot(selected.getRoot());
                // 个别系统不提供窗口列表时，仍允许读取活动根节点。
                return windows.isEmpty() ? fromRoot(service.getRootInActiveWindow()) : null;
            } finally {
                for (AccessibilityWindowInfo window : windows) window.recycle();
            }
        } catch (IllegalStateException | SecurityException ignored) {
            // 页面消失或服务正在断开属于正常的短暂空结果。
            return null;
        }
    }
    private static int priority(AccessibilityWindowInfo window) { return window.isFocused() ? 2 : window.isActive() ? 1 : 0; }
    private static Foreground fromRoot(AccessibilityNodeInfo root) {
        if (root == null) return null;
        try {
            String packageName = text(root.getPackageName());
            return packageName.isEmpty() ? null : new Foreground(root.getWindowId(), packageName);
        } finally { root.recycle(); }
    }
    private static String text(CharSequence value) { return value == null ? "" : value.toString(); }
}
