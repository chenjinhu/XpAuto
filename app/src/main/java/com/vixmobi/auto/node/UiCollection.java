package com.vixmobi.auto.node;

import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.List;

/** 兼容 size/get 的节点集合；get 和筛选返回副本，不破坏原集合。 */
public class UiCollection implements IUiCollection {
    public List<AccessibilityNodeInfo> nodeList = new ArrayList<>();
    public UiCollection() { }
    public UiCollection(List<AccessibilityNodeInfo> nodes) { nodeList = nodes; }
    static UiCollection fromObjects(List<UiObject> nodes) {
        UiCollection result = new UiCollection();
        for (UiObject node : nodes) { result.nodeList.add(node.node); node.node = null; }
        return result;
    }
    public int size() { return nodeList.size(); }
    public UiObject get(int index) {
        if (index < 0 || index >= size()) return new UiObject();
        return new UiObject(AccessibilityNodeInfo.obtain(nodeList.get(index)));
    }
    public void foreachPrint() { for (AccessibilityNodeInfo node : nodeList) AccUtils.printLogMsg(String.valueOf(node)); }
    public interface FilterCondition<T> { boolean shouldKeep(T item); }
    public UiObject filterOne(FilterCondition<AccessibilityNodeInfo> condition) {
        for (AccessibilityNodeInfo node : nodeList) if (condition.shouldKeep(node)) return new UiObject(AccessibilityNodeInfo.obtain(node));
        return new UiObject();
    }
    public List<AccessibilityNodeInfo> filter(FilterCondition<AccessibilityNodeInfo> condition) {
        List<AccessibilityNodeInfo> result = new ArrayList<>();
        for (AccessibilityNodeInfo node : nodeList) if (condition.shouldKeep(node)) result.add(AccessibilityNodeInfo.obtain(node));
        return result;
    }
    public UiObject[] toArray() {
        UiObject[] result = new UiObject[size()];
        for (int i = 0; i < result.length; i++) result[i] = get(i);
        return result;
    }
    public void recycle() { for (AccessibilityNodeInfo node : nodeList) node.recycle(); nodeList.clear(); }
}
