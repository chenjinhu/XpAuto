package com.vixmobi.auto.node;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.LinkedHashMap;
import java.util.Map;

/** 查询、节点信息与 XPath 使用同一套属性，防止命名和布尔值语义不一致。 */
final class NodeAttributes {
    static final String[] NAMES = {"id", "text", "desc", "clz", "pkg", "visible", "checkable", "checked",
            "clickable", "longClickable", "scrollable", "focusable", "enabled", "focused", "selected",
            "editable", "multiLine", "password", "drawingOrder", "depth", "index", "childCount",
            "row", "column", "rowSpan", "columnSpan", "rowCount", "columnCount", "bounds", "windowId"};
    private final AccessibilityNodeInfo node;
    private final int depth, index;
    NodeAttributes(AccessibilityNodeInfo node, int depth, int index) { this.node = node; this.depth = depth; this.index = index; }

    Object get(String name) {
        switch (name) {
            case "id": return text(node.getViewIdResourceName());
            case "text": return text(node.getText());
            case "desc": return text(node.getContentDescription());
            case "clz": return text(node.getClassName());
            case "pkg": return text(node.getPackageName());
            case "visible": return node.isVisibleToUser();
            case "checkable": return node.isCheckable();
            case "checked": return node.isChecked();
            case "clickable": return node.isClickable();
            case "longClickable": return node.isLongClickable();
            case "scrollable": return node.isScrollable();
            case "focusable": return node.isFocusable();
            case "enabled": return node.isEnabled();
            case "focused": return node.isFocused();
            case "selected": return node.isSelected();
            case "editable": return node.isEditable();
            case "multiLine": return node.isMultiLine();
            case "password": return node.isPassword();
            case "depth": return depth;
            case "index": return index;
            case "drawingOrder": return node.getDrawingOrder();
            case "childCount": return node.getChildCount();
            case "windowId": return node.getWindowId();
            case "bounds":
                Rect rect = new Rect(); node.getBoundsInScreen(rect);
                return new int[] {rect.left, rect.top, rect.right, rect.bottom};
            case "row": case "column": case "rowSpan": case "columnSpan":
                AccessibilityNodeInfo.CollectionItemInfo item = node.getCollectionItemInfo();
                if (item == null) return -1;
                switch (name) {
                    case "row": return item.getRowIndex();
                    case "column": return item.getColumnIndex();
                    case "rowSpan": return item.getRowSpan();
                    default: return item.getColumnSpan();
                }
            case "rowCount": case "columnCount":
                AccessibilityNodeInfo.CollectionInfo collection = node.getCollectionInfo();
                if (collection == null) return -1;
                return name.equals("rowCount") ? collection.getRowCount() : collection.getColumnCount();
            default: throw new IllegalArgumentException("未知节点属性：" + name);
        }
    }
    Map<String, Object> all() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String name : NAMES) result.put(name, get(name));
        return result;
    }
    static String text(CharSequence value) { return value == null ? "" : value.toString(); }
}
