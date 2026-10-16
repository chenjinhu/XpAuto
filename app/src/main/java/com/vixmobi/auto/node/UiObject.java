package com.vixmobi.auto.node;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import com.vixmobi.auto.Constant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;

/** 可重复读取和操作的节点句柄；普通方法不回收自身，使用结束时可主动 recycle。 */
public class UiObject implements IUiObject {
    public AccessibilityNodeInfo node;
    private int knownDepth = -1, knownIndex = -1;
    public UiObject() { }
    public UiObject(AccessibilityNodeInfo node) { this.node = node; }
    UiObject(AccessibilityNodeInfo node, int depth, int index) { this.node = node; knownDepth = depth; knownIndex = index; }
    public boolean exists() { return node != null; }
    public boolean refresh() {
        if (node == null) return false;
        boolean valid = node.refresh();
        knownDepth = knownIndex = -1;
        return valid;
    }
    public boolean isValid() { return refresh(); }
    public void recycle() { if (node != null) { node.recycle(); node = null; } }

    private boolean action(int action) { return action(action, null); }
    private boolean action(int action, Bundle values) {
        NodeQuery.checkCancelled();
        return node != null && node.performAction(action, values);
    }
    /** 保留旧 click 行为：先点击当前节点，再尝试最近的可点击父节点。 */
    public boolean click() {
        if (clickEx()) return true;
        AccessibilityNodeInfo parent = node == null ? null : node.getParent();
        int count = 0;
        try {
            while (parent != null && count++ < 80) {
                NodeQuery.checkCancelled();
                if (parent.isClickable()) return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                AccessibilityNodeInfo next = parent.getParent(); parent.recycle(); parent = next;
            }
            return false;
        } finally { if (parent != null) parent.recycle(); }
    }
    public boolean clickEx() { return action(AccessibilityNodeInfo.ACTION_CLICK); }
    public boolean longClickEx() { return action(AccessibilityNodeInfo.ACTION_LONG_CLICK); }
    public boolean longClick() { return longClickEx(); }
    public boolean longClick(long duration) { return gesture(duration, false); }
    public boolean clickCenter() { return gesture(80, false); }
    public boolean clickPoint() { return gesture(80, true); }
    public boolean clickExactPoint(long duration) { return gesture(duration, false); }
    public float[] getPoint() { Rect r = bounds(); return r == null ? null : new float[] {r.exactCenterX(), r.exactCenterY()}; }

    /** 等待系统确认手势完成，取消或执行失败返回 false，停止脚本可中断等待。 */
    private boolean gesture(long duration, boolean random) {
        if (duration < 1 || duration > 60000) throw new IllegalArgumentException("手势时长应为 1～60000 毫秒");
        NodeQuery.checkCancelled();
        AccessibilityService service = AccUtils.AccessibilityHelper;
        Rect area = visibleBounds();
        if (service == null || area == null || area.isEmpty()) return false;
        float x = random ? area.left + ThreadLocalRandom.current().nextInt(area.width()) : area.exactCenterX();
        float y = random ? area.top + ThreadLocalRandom.current().nextInt(area.height()) : area.exactCenterY();
        Path path = new Path(); path.moveTo(x, y);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, duration)).build();
        CountDownLatch completed = new CountDownLatch(1);
        boolean[] success = {false};
        boolean accepted = service.dispatchGesture(gesture, new AccessibilityService.GestureResultCallback() {
            @Override public void onCompleted(GestureDescription ignored) { success[0] = true; completed.countDown(); }
            @Override public void onCancelled(GestureDescription ignored) { completed.countDown(); }
        }, new Handler(Looper.getMainLooper()));
        if (!accepted || Looper.myLooper() == Looper.getMainLooper()) return accepted;
        long end = System.nanoTime() + (duration + 2000) * 1000000L;
        try {
            while (!completed.await(25, TimeUnit.MILLISECONDS)) {
                NodeQuery.checkCancelled();
                if (System.nanoTime() >= end) return false;
            }
            return success[0];
        } catch (InterruptedException error) { Thread.currentThread().interrupt(); NodeQuery.checkCancelled(); return false; }
    }
    public boolean inputText(String text) { return setText(text); }
    public boolean clearText() { return setText(""); }
    public boolean setText(String text) {
        Bundle args = new Bundle(); args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        return action(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
    }
    public boolean setSelection(int start, int end) {
        if (start < 0 || end < start) throw new IllegalArgumentException("选区必须满足 0 <= start <= end");
        Bundle args = new Bundle(); args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, start);
        args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, end);
        return action(AccessibilityNodeInfo.ACTION_SET_SELECTION, args);
    }
    public boolean copy() { return action(AccessibilityNodeInfo.ACTION_COPY); }
    public boolean cut() { return action(AccessibilityNodeInfo.ACTION_CUT); }
    public boolean paste() { return action(AccessibilityNodeInfo.ACTION_PASTE); }
    public boolean select() { return action(AccessibilityNodeInfo.ACTION_SELECT); }
    public boolean collapse() { return action(AccessibilityNodeInfo.ACTION_COLLAPSE); }
    public boolean expand() { return action(AccessibilityNodeInfo.ACTION_EXPAND); }
    public boolean scrollForward() { return action(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD); }
    public boolean scrollBackward() { return action(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD); }
    public boolean scrollUp() { return action(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.getId()); }
    public boolean scrollDown() { return action(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.getId()); }
    public boolean scrollLeft() { return action(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.getId()); }
    public boolean scrollRight() { return action(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.getId()); }
    public boolean show() { return action(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId()); }

    public UiObject getOneNodeInfo(UiSelector selector, int timeout) { UiSelector.checkTimeout(timeout); return UiSelector.first(NodeQuery.find(selector, this, timeout, true)); }
    public UiObject getOneNodeInfo(UiSelector selector) { return getOneNodeInfo(selector, 0); }
    public UiObject[] getNodeInfo(UiSelector selector, int timeout) { UiSelector.checkTimeout(timeout); return NodeQuery.find(selector, this, timeout, false).toArray(new UiObject[0]); }
    public UiObject[] getNodeInfo(UiSelector selector) { return getNodeInfo(selector, 0); }
    public UiObject findOne(UiSelector selector) { return getOneNodeInfo(selector); }
    public UiCollection find(UiSelector selector) { return UiCollection.fromObjects(NodeQuery.find(selector, this, 0, false)); }
    public UiCollection findByText(String text) {
        // 兼容旧的文本/描述包含查询，集合保留 size/get 用法。
        List<UiObject> all = NodeQuery.find(new UiSelector(), this, 0, false), matches = new ArrayList<>();
        for (UiObject item : all) {
            if (item.text().contains(text) || item.desc().contains(text)) matches.add(item); else item.recycle();
        }
        return UiCollection.fromObjects(matches);
    }
    public int childCount() { return node == null ? 0 : node.getChildCount(); }
    public UiObject child(int index) {
        if (node == null || index < 0 || index >= node.getChildCount()) return null;
        AccessibilityNodeInfo child = node.getChild(index);
        return child == null ? null : new UiObject(child, depth() + 1, index);
    }
    public UiObject parent() {
        AccessibilityNodeInfo parent = node == null ? null : node.getParent();
        return parent == null ? null : new UiObject(parent);
    }
    public UiObject[] allChildren() {
        List<UiObject> children = new ArrayList<>();
        for (int i = 0; i < childCount(); i++) { UiObject child = child(i); if (child != null) children.add(child); }
        return children.toArray(new UiObject[0]);
    }
    public UiCollection children() { return UiCollection.fromObjects(java.util.Arrays.asList(allChildren())); }
    public UiObject[] siblings() { return siblings(0); }
    public UiObject[] previousSiblings() { return siblings(-1); }
    public UiObject[] nextSiblings() { return siblings(1); }
    private UiObject[] siblings(int direction) {
        UiObject parent = parent(); if (parent == null) return new UiObject[0];
        List<UiObject> result = new ArrayList<>();
        try {
            int own = index();
            if (own < 0) return new UiObject[0];
            for (int i = 0; i < parent.childCount(); i++) {
                if (i == own || direction < 0 && i > own || direction > 0 && i < own) continue;
                UiObject sibling = parent.child(i); if (sibling != null) result.add(sibling);
            }
            return result.toArray(new UiObject[0]);
        } finally { parent.recycle(); }
    }
    public int depth() {
        if (node == null) return -1;
        if (knownDepth >= 0) return knownDepth;
        int depth = 0; AccessibilityNodeInfo parent = node.getParent();
        try {
            while (parent != null && depth < 80) {
                depth++; AccessibilityNodeInfo next = parent.getParent(); parent.recycle(); parent = next;
            }
        } finally { if (parent != null) parent.recycle(); }
        return knownDepth = depth;
    }
    public int index() {
        if (node == null) return -1;
        if (knownIndex >= 0) return knownIndex;
        AccessibilityNodeInfo parent = node.getParent(); if (parent == null) return knownIndex = 0;
        try {
            for (int i = 0; i < parent.getChildCount(); i++) {
                AccessibilityNodeInfo child = parent.getChild(i); if (child == null) continue;
                boolean same = child.equals(node); child.recycle(); if (same) return knownIndex = i;
            }
            return -1;
        } finally { parent.recycle(); }
    }
    public Rect bounds() { if (node == null) return null; Rect r = new Rect(); node.getBoundsInScreen(r); return r; }
    public Rect boundsInParent() { if (node == null) return null; Rect r = new Rect(); node.getBoundsInParent(r); return r; }
    public Rect visibleBounds() {
        Rect area = bounds(); if (area == null) return null;
        if (!node.isVisibleToUser()) { area.setEmpty(); return area; }
        Rect screen = Constant.context.getSystemService(WindowManager.class).getMaximumWindowMetrics().getBounds();
        if (!area.intersect(screen)) area.setEmpty();
        return area;
    }
    private Object value(String name) { return node == null ? null : new NodeAttributes(node, depth(), index()).get(name); }
    public String id() { Object v = value("id"); return v == null ? "" : (String) v; }
    public String text() { Object v = value("text"); return v == null ? "" : (String) v; }
    public String desc() { Object v = value("desc"); return v == null ? "" : (String) v; }
    public String clz() { Object v = value("clz"); return v == null ? "" : (String) v; }
    public String pkg() { Object v = value("pkg"); return v == null ? "" : (String) v; }
    public String className() { return clz(); }
    public String packageName() { return pkg(); }
    public boolean visible() { return Boolean.TRUE.equals(value("visible")); }
    public boolean checkable() { return Boolean.TRUE.equals(value("checkable")); }
    public boolean checked() { return Boolean.TRUE.equals(value("checked")); }
    public boolean clickable() { return Boolean.TRUE.equals(value("clickable")); }
    public boolean longClickable() { return Boolean.TRUE.equals(value("longClickable")); }
    public boolean scrollable() { return Boolean.TRUE.equals(value("scrollable")); }
    public boolean focusable() { return Boolean.TRUE.equals(value("focusable")); }
    public boolean enabled() { return Boolean.TRUE.equals(value("enabled")); }
    public boolean focused() { return Boolean.TRUE.equals(value("focused")); }
    public boolean selected() { return Boolean.TRUE.equals(value("selected")); }
    public boolean editable() { return Boolean.TRUE.equals(value("editable")); }
    public boolean multiLine() { return Boolean.TRUE.equals(value("multiLine")); }
    public boolean password() { return Boolean.TRUE.equals(value("password")); }
    public int drawingOrder() { Object v = value("drawingOrder"); return v == null ? -1 : (Integer) v; }
    public int row() { Object v = value("row"); return v == null ? -1 : (Integer) v; }
    public int column() { Object v = value("column"); return v == null ? -1 : (Integer) v; }
    public int rowSpan() { Object v = value("rowSpan"); return v == null ? -1 : (Integer) v; }
    public int columnSpan() { Object v = value("columnSpan"); return v == null ? -1 : (Integer) v; }
    public int rowCount() { Object v = value("rowCount"); return v == null ? -1 : (Integer) v; }
    public int columnCount() { Object v = value("columnCount"); return v == null ? -1 : (Integer) v; }

    /** 可读快照用于日志/JSON；保持 text() 等旧方法，避免字段和方法重名。 */
    public Map<String, Object> info() {
        if (node == null) return null;
        Map<String, Object> result = new NodeAttributes(node, depth(), index()).all();
        result.put("bounds", rectangle(bounds())); result.put("visibleBounds", rectangle(visibleBounds()));
        return result;
    }
    public Map<String, Object> toJSON() { return info(); }
    /** JSON.stringify 会传入属性名；保留此重载供 Java/JS 桥接正确选择方法。 */
    public Map<String, Object> toJSON(String key) { return info(); }
    private Map<String, Integer> rectangle(Rect r) {
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("left", r.left); result.put("top", r.top); result.put("right", r.right); result.put("bottom", r.bottom);
        return result;
    }
    public void printInfo() { AccUtils.printLogMsg(String.valueOf(info())); }
    public void printText() {
        UiCollection children = find(new UiSelector());
        try { for (int i = 0; i < children.size(); i++) { UiObject child = children.get(i); try { AccUtils.printLogMsg(child.text()); } finally { child.recycle(); } } }
        finally { children.recycle(); }
    }
}
