package com.vixmobi.auto.devtools;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;

import com.vixmobi.auto.MyAccessibilityService;

/** 显示悬浮球单独检查悬浮窗权限；脚本运行检查必要权限，Root 由对应 API 按需申请。 */
public final class AutomationPermissions {
    private AutomationPermissions() { }

    public static boolean accessibility(Context context) {
        ComponentName expected = new ComponentName(context, MyAccessibilityService.class);
        AccessibilityManager manager = context.getSystemService(AccessibilityManager.class);
        for (AccessibilityServiceInfo info : manager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            if (expected.equals(ComponentName.unflattenFromString(info.getId()))) return true;
        }
        return false;
    }

    public static boolean overlay(Context context) { return Settings.canDrawOverlays(context); }

    public static String missing(Context context) {
        String result = accessibility(context) ? "" : "无障碍服务";
        if (!overlay(context)) result += result.isEmpty() ? "悬浮窗权限" : "、悬浮窗权限";
        return result;
    }
}
