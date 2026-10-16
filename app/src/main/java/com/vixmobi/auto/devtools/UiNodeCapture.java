package com.vixmobi.auto.devtools;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.os.SystemClock;
import android.view.Display;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import com.vixmobi.auto.node.AccUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;

/** 只读抓取无障碍节点，不创建 V8 会话，也不通过 UIAutomator 暂停现有无障碍服务。 */
final class UiNodeCapture {
    private final JSONArray nodes = new JSONArray();
    private final long deadline = SystemClock.uptimeMillis() + 4000;
    private int textBudget = 300000;
    private boolean truncated;

    JSONObject capture(Context context) throws Exception {
        AccessibilityService service = AccUtils.AccessibilityHelper;
        if (service == null || !AutomationPermissions.accessibility(context)) {
            throw new IllegalStateException("抓取节点前请先开启 XpAuto 无障碍服务");
        }
        JSONArray windows = new JSONArray();
        List<AccessibilityWindowInfo> available = service.getWindows();
        try {
            for (AccessibilityWindowInfo window : available) {
                AccessibilityNodeInfo root = window.getRoot();
                if (root == null) continue;
                String rootId = read(root, null, window.getId(), 0, 0);
                if (rootId == null) continue;
                Rect bounds = new Rect();
                window.getBoundsInScreen(bounds);
                windows.put(DeveloperServer.object("id", window.getId(), "title", text(window.getTitle()),
                        "type", window.getType(), "layer", window.getLayer(), "active", window.isActive(),
                        "focused", window.isFocused(), "bounds", rectangle(bounds), "rootId", rootId));
            }
        } finally {
            for (AccessibilityWindowInfo window : available) window.recycle();
        }
        if (nodes.length() == 0) {
            AccessibilityNodeInfo root = service.getRootInActiveWindow();
            if (root == null) throw new IllegalStateException("当前界面没有可读取的无障碍节点，请切换页面后重试");
            int windowId = root.getWindowId();
            Rect bounds = new Rect(); root.getBoundsInScreen(bounds);
            String rootId = read(root, null, windowId, 0, 0);
            if (rootId == null) throw new IllegalStateException("节点抓取超时，请在界面稳定后重试");
            windows.put(DeveloperServer.object("id", windowId, "title", "当前窗口", "type", 1,
                    "layer", 0, "active", true, "focused", true, "bounds", rectangle(bounds), "rootId", rootId));
        }
        Rect screen = context.getSystemService(WindowManager.class).getMaximumWindowMetrics().getBounds();
        Display display = context.getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
        return DeveloperServer.object("nodes", nodes, "windows", windows, "truncated", truncated,
                "capturedAt", System.currentTimeMillis(), "screen", DeveloperServer.object(
                        "width", screen.width(), "height", screen.height(), "rotation", display.getRotation()));
    }

    private String read(AccessibilityNodeInfo node, String parent, int windowId, int index, int depth) throws Exception {
        try {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException("节点抓取已取消");
            if (nodes.length() >= 5000 || depth > 80 || textBudget < 6000 || SystemClock.uptimeMillis() > deadline) {
                truncated = true;
                return null;
            }
            String nodeId = "n" + nodes.length();
            Rect bounds = new Rect(); node.getBoundsInScreen(bounds);
            JSONArray children = new JSONArray();
            JSONObject value = DeveloperServer.object("nodeId", nodeId, "parentId", parent == null ? JSONObject.NULL : parent,
                    "windowId", windowId, "index", index, "depth", depth, "drawingOrder", node.getDrawingOrder(),
                    "id", text(node.getViewIdResourceName()), "text", text(node.getText()),
                    "desc", text(node.getContentDescription()), "clz", text(node.getClassName()),
                    "pkg", text(node.getPackageName()), "bounds", rectangle(bounds), "children", children,
                    "clickable", node.isClickable(), "longClickable", node.isLongClickable(),
                    "checkable", node.isCheckable(), "checked", node.isChecked(), "enabled", node.isEnabled(),
                    "focusable", node.isFocusable(), "focused", node.isFocused(), "selected", node.isSelected(),
                    "scrollable", node.isScrollable(), "editable", node.isEditable(),
                    "visible", node.isVisibleToUser(), "password", node.isPassword(), "multiLine", node.isMultiLine());
            value.put("textTruncated", node.getText() != null && node.getText().length() > 1024);
            value.put("descTruncated", node.getContentDescription() != null && node.getContentDescription().length() > 1024);
            nodes.put(value);
            for (int i = 0; i < node.getChildCount(); i++) {
                if (nodes.length() >= 5000 || SystemClock.uptimeMillis() > deadline) { truncated = true; break; }
                AccessibilityNodeInfo child = node.getChild(i);
                if (child == null) continue; // 页面正在变化时，系统可能返回空子节点。
                String childId = read(child, nodeId, windowId, i, depth + 1);
                if (childId != null) children.put(childId);
            }
            return nodeId;
        } finally { node.recycle(); }
    }

    private String text(CharSequence value) {
        if (value == null) return "";
        int length = Math.min(value.length(), Math.min(1024, textBudget));
        if (length < value.length()) truncated = true;
        textBudget -= length;
        return value.subSequence(0, length).toString();
    }
    private static JSONArray rectangle(Rect value) {
        return new JSONArray().put(value.left).put(value.top).put(value.right).put(value.bottom);
    }
}
