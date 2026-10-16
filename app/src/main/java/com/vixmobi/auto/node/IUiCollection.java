package com.vixmobi.auto.node;

import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;

/** 节点 API 的 Java 接口，与脚本类型定义同步维护。 */
public interface IUiCollection {
    int size();
    UiObject get(int index);
    void foreachPrint();
    UiObject filterOne(UiCollection.FilterCondition<AccessibilityNodeInfo> condition);
    List<AccessibilityNodeInfo> filter(UiCollection.FilterCondition<AccessibilityNodeInfo> condition);
    UiObject[] toArray();
    void recycle();
}
