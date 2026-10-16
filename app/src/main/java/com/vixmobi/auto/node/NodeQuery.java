package com.vixmobi.auto.node;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import com.vixmobi.auto.Constant;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import javax.xml.parsers.DocumentBuilderFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

/** 每轮重新读取节点树。普通查询找到首项即停止；只有 XPath 才构建完整内存树。 */
public final class NodeQuery {
    private static final AtomicInteger EXECUTING = new AtomicInteger();
    private final UiSelector selector;
    private final boolean firstOnly;
    private final List<UiObject> result = new ArrayList<>();
    private final List<Entry> snapshot = new ArrayList<>();
    private final Map<UiSelector.Stage, Set<Entry>> xpathMatches = new IdentityHashMap<>();
    private final long deadline;
    private int visited;

    private static final class Entry {
        final AccessibilityNodeInfo node;
        final Entry parent;
        final int depth, index;
        final boolean[] matched, ancestors;
        final NodeAttributes attributes;
        Entry(AccessibilityNodeInfo node, Entry parent, int depth, int index, int count) {
            this.node = node; this.parent = parent; this.depth = depth; this.index = index;
            matched = new boolean[count]; ancestors = new boolean[count];
            attributes = new NodeAttributes(node, depth, index);
        }
    }
    private NodeQuery(UiSelector selector, boolean firstOnly, long deadline) {
        this.selector = selector; this.firstOnly = firstOnly; this.deadline = deadline;
    }
    public static boolean isExecuting() { return EXECUTING.get() > 0; }
    static void checkCancelled() {
        if (Constant.killThread || Thread.currentThread().isInterrupted()) throw new CancellationException("节点查询已停止");
    }
    static List<UiObject> find(UiSelector selector, UiObject scope, int timeout, boolean firstOnly) {
        if (timeout < -1) throw new IllegalArgumentException("等待时间不能为负数");
        long end = timeout == -1 ? Long.MAX_VALUE : System.nanoTime() + timeout * 1000000L;
        EXECUTING.incrementAndGet();
        try {
            do {
                checkCancelled();
                // 一轮最多读取 5000 个节点/80 层，避免异常无障碍树长时间阻塞脚本。
                NodeQuery query = new NodeQuery(selector, firstOnly, System.nanoTime() + 4000000000L);
                List<UiObject> found = query.search(scope);
                if (!found.isEmpty() || timeout == 0 || System.nanoTime() >= end) return found;
                long remaining = Math.min(50, Math.max(1, (end - System.nanoTime()) / 1000000L));
                try { Thread.sleep(remaining); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new CancellationException("节点等待已中断"); }
            } while (true);
        } finally { EXECUTING.decrementAndGet(); }
    }
    private List<UiObject> search(UiObject scope) {
        boolean xpath = selector.stages.stream().anyMatch(stage -> stage.xpath != null);
        try {
            if (scope != null) {
                if (scope.node == null || !scope.node.refresh()) return result;
                read(AccessibilityNodeInfo.obtain(scope.node), null, scope.depth(), scope.index(), xpath, true);
            } else {
                AccessibilityService service = AccUtils.AccessibilityHelper;
                if (service == null) throw new IllegalStateException("查询节点前请开启 XpAuto 无障碍服务");
                List<AccessibilityWindowInfo> windows = new ArrayList<>(service.getWindows());
                try {
                    // 普通查询优先当前活动窗口，XPath 保持与节点分析器相同的窗口顺序。
                    if (!xpath) windows.sort((a, b) -> Boolean.compare(b.isActive(), a.isActive()));
                    boolean hasRoot = false;
                    for (AccessibilityWindowInfo window : windows) {
                        checkCancelled();
                        AccessibilityNodeInfo root = window.getRoot();
                        if (root == null) continue;
                        hasRoot = true;
                        if (read(root, null, 0, 0, xpath, false)) break;
                    }
                    if (!hasRoot) {
                        AccessibilityNodeInfo root = service.getRootInActiveWindow();
                        if (root != null) read(root, null, 0, 0, xpath, false);
                    }
                } finally { for (AccessibilityWindowInfo window : windows) window.recycle(); }
            }
            if (xpath) {
                buildXPath();
                for (Entry entry : snapshot) {
                    checkCancelled();
                    if (scope != null && entry.parent == null) continue;
                    if (matches(entry)) { result.add(copy(entry)); if (firstOnly) break; }
                }
            }
            return result;
        } catch (RuntimeException error) {
            for (UiObject node : result) node.recycle();
            throw error;
        } finally {
            // 返回值始终是独立副本；释放遍历节点不会让脚本中的节点失效。
            for (Entry entry : snapshot) entry.node.recycle();
        }
    }
    private boolean read(AccessibilityNodeInfo node, Entry parent, int depth, int index, boolean snapshotMode, boolean skipSelf) {
        boolean retained = false;
        try {
            checkCancelled();
            if (++visited > 5000 || depth > 80 || System.nanoTime() > deadline)
                throw new IllegalStateException("节点树超过查询上限（5000 节点、80 层或单轮 4 秒），请缩小查询范围");
            Entry entry = new Entry(node, parent, depth, index, selector.stages.size());
            if (snapshotMode) { snapshot.add(entry); retained = true; }
            else if (!skipSelf && matches(entry)) {
                result.add(copy(entry));
                if (firstOnly) return true;
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null && read(child, entry, depth + 1, i, snapshotMode, false)) return true;
            }
            return false;
        } finally { if (!retained) node.recycle(); }
    }
    private boolean matches(Entry entry) {
        for (int i = 0; i < selector.stages.size(); i++) {
            UiSelector.Stage stage = selector.stages.get(i);
            boolean ancestor = entry.parent != null && (entry.parent.matched[i] || entry.parent.ancestors[i]);
            entry.ancestors[i] = ancestor;
            boolean eligible = i == 0 || entry.parent != null && (stage.direct
                    ? entry.parent.matched[i - 1] : entry.parent.matched[i - 1] || entry.parent.ancestors[i - 1]);
            if (!eligible || stage.xpath != null && !xpathMatches.get(stage).contains(entry)) continue;
            boolean matched = true;
            for (SelectorCondition condition : stage.conditions) {
                if (!condition.matches(entry.attributes::get)) { matched = false; break; }
            }
            entry.matched[i] = matched;
        }
        return entry.matched[entry.matched.length - 1];
    }
    private UiObject copy(Entry entry) { return new UiObject(AccessibilityNodeInfo.obtain(entry.node), entry.depth, entry.index); }

    private void buildXPath() {
        try {
            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            Element hierarchy = document.createElement("hierarchy"); document.appendChild(hierarchy);
            Map<Entry, Element> elements = new IdentityHashMap<>();
            Map<org.w3c.dom.Node, Entry> entries = new HashMap<>();
            for (Entry entry : snapshot) {
                checkCancelled();
                Element node = document.createElement("node");
                for (Map.Entry<String, Object> attribute : entry.attributes.all().entrySet()) {
                    Object value = attribute.getValue();
                    String text;
                    if (value instanceof int[]) {
                        int[] r = (int[]) value; text = "[" + r[0] + "," + r[1] + "][" + r[2] + "," + r[3] + "]";
                    } else text = String.valueOf(value);
                    node.setAttribute(attribute.getKey(), text);
                }
                if (entry.parent == null) {
                    Element window = document.createElement("window");
                    window.setAttribute("id", String.valueOf(entry.node.getWindowId())); hierarchy.appendChild(window); window.appendChild(node);
                } else elements.get(entry.parent).appendChild(node);
                elements.put(entry, node); entries.put(node, entry);
            }
            for (UiSelector.Stage stage : selector.stages) {
                if (stage.xpath == null) continue;
                checkCancelled();
                NodeList selected = stage.xpath.select(document);
                Set<Entry> matches = new HashSet<>();
                for (int i = 0; i < selected.getLength(); i++) {
                    Entry entry = entries.get(selected.item(i));
                    if (entry != null) matches.add(entry);
                }
                xpathMatches.put(stage, matches);
            }
        } catch (javax.xml.parsers.ParserConfigurationException error) { throw new IllegalStateException("无法创建 XPath 节点树", error); }
    }
}
