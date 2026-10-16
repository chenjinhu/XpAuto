package com.vixmobi.auto.node;

import android.graphics.Rect;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import com.vixmobi.auto.Constant;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/** 条件可链式追加，也可重复查询；每个全局入口都会创建独立选择器。 */
public class UiSelector implements IUiSelector {
    static final class Stage {
        final List<SelectorCondition> conditions = new ArrayList<>();
        NodeXPath xpath;
        boolean direct;
        Stage copy() { Stage s = new Stage(); s.conditions.addAll(conditions); s.xpath = xpath; s.direct = direct; return s; }
    }
    final List<Stage> stages = new ArrayList<>();
    public UiSelector() { stages.add(new Stage()); }
    /** 输出完整条件链；不查询界面，也不修改或消耗条件。 */
    @Override public String toString() {
        StringJoiner result = new StringJoiner(".");
        for (int i = 0; i < stages.size(); i++) {
            Stage stage = stages.get(i);
            if (i > 0) result.add(stage.direct ? "directChild()" : "child()");
            if (stage.xpath != null) result.add(stage.xpath.toString());
            for (SelectorCondition condition : stage.conditions) result.add(condition.toString());
            if (i == 0 && stage.xpath == null && stage.conditions.isEmpty()) result.add("selector()");
        }
        return result.toString();
    }
    /** 让选择器嵌入对象或数组时也能被 JSON.stringify 正确序列化。 */
    public String toJSON() { return toString(); }
    /** JSON.stringify 会传入属性名；保留带参数重载供 Javet 代理调用。 */
    public String toJSON(String key) { return toString(); }

    private Stage current() { return stages.get(stages.size() - 1); }
    private UiSelector condition(String name, String operation, Object value) {
        current().conditions.add(new SelectorCondition(name, operation, value));
        return this;
    }
    private UiSelector number(String name, int value) {
        if (value < 0) throw new IllegalArgumentException(name + " 不能为负数");
        return condition(name, "equal", value);
    }
    /** text 完全相等，空字符串也是有效条件。 */
    public UiSelector text(String value) { return condition("text", "equal", value); }
    /** text 包含，空字符串也是有效条件。 */
    public UiSelector textContains(String value) { return condition("text", "contains", value); }
    /** text 前缀，空字符串也是有效条件。 */
    public UiSelector textStartsWith(String value) { return condition("text", "starts", value); }
    /** text 后缀，空字符串也是有效条件。 */
    public UiSelector textEndsWith(String value) { return condition("text", "ends", value); }
    /** text Java 正则全文匹配，空字符串也是有效条件。 */
    public UiSelector textMatch(String value) { return condition("text", "match", value); }
    /** id 完全相等，空字符串也是有效条件。 */
    public UiSelector id(String value) { return condition("id", "equal", value); }
    /** id 包含，空字符串也是有效条件。 */
    public UiSelector idContains(String value) { return condition("id", "contains", value); }
    /** id 前缀，空字符串也是有效条件。 */
    public UiSelector idStartsWith(String value) { return condition("id", "starts", value); }
    /** id 后缀，空字符串也是有效条件。 */
    public UiSelector idEndsWith(String value) { return condition("id", "ends", value); }
    /** id Java 正则全文匹配，空字符串也是有效条件。 */
    public UiSelector idMatch(String value) { return condition("id", "match", value); }
    /** desc 完全相等，空字符串也是有效条件。 */
    public UiSelector desc(String value) { return condition("desc", "equal", value); }
    /** desc 包含，空字符串也是有效条件。 */
    public UiSelector descContains(String value) { return condition("desc", "contains", value); }
    /** desc 前缀，空字符串也是有效条件。 */
    public UiSelector descStartsWith(String value) { return condition("desc", "starts", value); }
    /** desc 后缀，空字符串也是有效条件。 */
    public UiSelector descEndsWith(String value) { return condition("desc", "ends", value); }
    /** desc Java 正则全文匹配，空字符串也是有效条件。 */
    public UiSelector descMatch(String value) { return condition("desc", "match", value); }
    /** clz 完全相等，空字符串也是有效条件。 */
    public UiSelector clz(String value) { return condition("clz", "equal", value); }
    /** clz 包含，空字符串也是有效条件。 */
    public UiSelector clzContains(String value) { return condition("clz", "contains", value); }
    /** clz 前缀，空字符串也是有效条件。 */
    public UiSelector clzStartsWith(String value) { return condition("clz", "starts", value); }
    /** clz 后缀，空字符串也是有效条件。 */
    public UiSelector clzEndsWith(String value) { return condition("clz", "ends", value); }
    /** clz Java 正则全文匹配，空字符串也是有效条件。 */
    public UiSelector clzMatch(String value) { return condition("clz", "match", value); }
    /** pkg 完全相等，空字符串也是有效条件。 */
    public UiSelector pkg(String value) { return condition("pkg", "equal", value); }
    /** pkg 包含，空字符串也是有效条件。 */
    public UiSelector pkgContains(String value) { return condition("pkg", "contains", value); }
    /** pkg 前缀，空字符串也是有效条件。 */
    public UiSelector pkgStartsWith(String value) { return condition("pkg", "starts", value); }
    /** pkg 后缀，空字符串也是有效条件。 */
    public UiSelector pkgEndsWith(String value) { return condition("pkg", "ends", value); }
    /** pkg Java 正则全文匹配，空字符串也是有效条件。 */
    public UiSelector pkgMatch(String value) { return condition("pkg", "match", value); }
    public UiSelector packageName(String value) { return pkg(value); }
    public UiSelector packageNameContains(String value) { return pkgContains(value); }
    public UiSelector packageNameStartsWith(String value) { return pkgStartsWith(value); }
    public UiSelector packageNameEndsWith(String value) { return pkgEndsWith(value); }
    public UiSelector packageNameMatch(String value) { return pkgMatch(value); }
    public UiSelector className(String value) { return clz(value); }
    public UiSelector classNameContains(String value) { return clzContains(value); }
    public UiSelector classNameStartsWith(String value) { return clzStartsWith(value); }
    public UiSelector classNameEndsWith(String value) { return clzEndsWith(value); }
    public UiSelector classNameMatch(String value) { return clzMatch(value); }
    /** 按 visible 布尔状态筛选。 */
    public UiSelector visible(boolean value) { return condition("visible", "equal", value); }
    /** 按 checkable 布尔状态筛选。 */
    public UiSelector checkable(boolean value) { return condition("checkable", "equal", value); }
    /** 按 checked 布尔状态筛选。 */
    public UiSelector checked(boolean value) { return condition("checked", "equal", value); }
    /** 按 clickable 布尔状态筛选。 */
    public UiSelector clickable(boolean value) { return condition("clickable", "equal", value); }
    /** 按 longClickable 布尔状态筛选。 */
    public UiSelector longClickable(boolean value) { return condition("longClickable", "equal", value); }
    /** 按 scrollable 布尔状态筛选。 */
    public UiSelector scrollable(boolean value) { return condition("scrollable", "equal", value); }
    /** 按 focusable 布尔状态筛选。 */
    public UiSelector focusable(boolean value) { return condition("focusable", "equal", value); }
    /** 按 enabled 布尔状态筛选。 */
    public UiSelector enabled(boolean value) { return condition("enabled", "equal", value); }
    /** 按 focused 布尔状态筛选。 */
    public UiSelector focused(boolean value) { return condition("focused", "equal", value); }
    /** 按 selected 布尔状态筛选。 */
    public UiSelector selected(boolean value) { return condition("selected", "equal", value); }
    /** 按 editable 布尔状态筛选。 */
    public UiSelector editable(boolean value) { return condition("editable", "equal", value); }
    /** 按 multiLine 布尔状态筛选。 */
    public UiSelector multiLine(boolean value) { return condition("multiLine", "equal", value); }
    /** 按 password 布尔状态筛选。 */
    public UiSelector password(boolean value) { return condition("password", "equal", value); }
    /** 按 depth 数值筛选，索引从 0 开始。 */
    public UiSelector depth(int value) { return number("depth", value); }
    /** 按 index 数值筛选，索引从 0 开始。 */
    public UiSelector index(int value) { return number("index", value); }
    /** 按 drawingOrder 数值筛选，索引从 0 开始。 */
    public UiSelector drawingOrder(int value) { return number("drawingOrder", value); }
    /** 按 childCount 数值筛选，索引从 0 开始。 */
    public UiSelector childCount(int value) { return number("childCount", value); }
    /** 按 row 数值筛选，索引从 0 开始。 */
    public UiSelector row(int value) { return number("row", value); }
    /** 按 column 数值筛选，索引从 0 开始。 */
    public UiSelector column(int value) { return number("column", value); }
    /** 按 rowSpan 数值筛选，索引从 0 开始。 */
    public UiSelector rowSpan(int value) { return number("rowSpan", value); }
    /** 按 columnSpan 数值筛选，索引从 0 开始。 */
    public UiSelector columnSpan(int value) { return number("columnSpan", value); }
    /** 按 rowCount 数值筛选，索引从 0 开始。 */
    public UiSelector rowCount(int value) { return number("rowCount", value); }
    /** 按 columnCount 数值筛选，索引从 0 开始。 */
    public UiSelector columnCount(int value) { return number("columnCount", value); }

    public UiSelector bounds(int left, int top, int right, int bottom) { return rectangle("rect", left, top, right, bottom); }
    public UiSelector boundsInside(int left, int top, int right, int bottom) { return rectangle("inside", left, top, right, bottom); }
    public UiSelector boundsContains(int left, int top, int right, int bottom) { return rectangle("encloses", left, top, right, bottom); }
    private UiSelector rectangle(String operation, int l, int t, int r, int b) {
        return condition("bounds", operation, SelectorCondition.rectangle(l, t, r, b));
    }
    public UiSelector boundsInScreen() {
        Rect screen = Constant.context.getSystemService(WindowManager.class).getMaximumWindowMetrics().getBounds();
        return boundsInside(screen.left, screen.top, screen.right, screen.bottom);
    }
    public UiSelector xpath(String expression) { current().xpath = new NodeXPath(expression); return this; }
    /** child 在已匹配节点的后代中筛选；directChild 只筛选直接子节点。 */
    public UiSelector child() { return nextStage(false); }
    public UiSelector directChild() { return nextStage(true); }
    private UiSelector nextStage(boolean direct) {
        UiSelector result = new UiSelector(); result.stages.clear();
        for (Stage stage : stages) result.stages.add(stage.copy());
        Stage next = new Stage(); next.direct = direct; result.stages.add(next);
        if (result.stages.size() > 32) throw new IllegalArgumentException("级联选择器最多 32 层");
        return result;
    }
    public UiObject getOneNodeInfo() { return getOneNodeInfo(0); }
    /** timeout 为毫秒；0 查询一次，未找到返回 null。 */
    public UiObject getOneNodeInfo(int timeout) { checkTimeout(timeout); return first(NodeQuery.find(this, null, timeout, true)); }
    public UiObject getOneNodeInfo(AccessibilityNodeInfo root) {
        return first(NodeQuery.find(this, new UiObject(root), 0, true));
    }
    public UiObject[] getNodeInfo() { return getNodeInfo(0); }
    public UiObject[] getNodeInfo(int timeout) { checkTimeout(timeout); return NodeQuery.find(this, null, timeout, false).toArray(new UiObject[0]); }
    public UiCollection find() { return UiCollection.fromObjects(NodeQuery.find(this, null, 0, false)); }
    /** 旧等待接口仍返回可调用 exists() 的对象。 */
    public UiObject untilFindOne() { return legacyFirst(NodeQuery.find(this, null, -1, true)); }
    public UiObject untilFindOne(int timeout) { checkTimeout(timeout); return legacyFirst(NodeQuery.find(this, null, timeout, true)); }
    static void checkTimeout(int timeout) { if (timeout < 0) throw new IllegalArgumentException("等待毫秒数不能为负数"); }
    public boolean exists() {
        UiObject found = getOneNodeInfo();
        if (found == null) return false;
        found.recycle(); return true;
    }
    public void waitFor() { UiObject found = untilFindOne(); found.recycle(); }
    static UiObject first(List<UiObject> result) { return result.isEmpty() ? null : result.get(0); }
    private static UiObject legacyFirst(List<UiObject> result) { UiObject value = first(result); return value == null ? new UiObject() : value; }
}
