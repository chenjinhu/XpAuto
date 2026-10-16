package com.vixmobi.auto.node;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.util.Xml;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.StringWriter;
import java.util.*;
import org.json.JSONObject;
import org.xmlpull.v1.XmlSerializer;

/** 当前自动化会话的抓取设置。脚本结束恢复服务 flags，锁定的节点副本随会话释放。 */
public final class NodeSettings {
  static boolean invisible = true, notImportant = true;
  static int mode = 1, maxDepth = 80, dumpMethod = 1;
  static String algorithm = "dsf";
  static Set<String> blocked = Collections.emptySet();
  static List<UiObject> locked;
  private static Integer originalFlags;
  private static volatile long lastEvent;

  public static void onEvent(AccessibilityEvent event) {
    if (event != null) lastEvent = System.currentTimeMillis();
  }

  public static long lastNodeEventTime() {
    return lastEvent;
  }

  public boolean setFetchNodeMode(
      int value, boolean hidden, boolean unimportant, String traversal) {
    if (value != 1 && value != 2) throw new IllegalArgumentException("mode 只能为 1 或 2");
    if (!Arrays.asList("nsf", "bsf", "dsf").contains(traversal))
      throw new IllegalArgumentException("algorithm 只能为 nsf、bsf 或 dsf");
    mode = value;
    invisible = hidden;
    notImportant = unimportant;
    algorithm = traversal;
    return changeFlag(AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS, unimportant);
  }

  public boolean setNodeDumpParamJson(String json) throws Exception {
    JSONObject value = new JSONObject(json);
    int depth = value.optInt("maxDepth", maxDepth), method = value.optInt("dumpMethod", dumpMethod);
    if (depth < 0 || depth > 80 || method < 1 || method > 3)
      throw new IllegalArgumentException("maxDepth 为 0～80，dumpMethod 为 1～3");
    maxDepth = depth;
    dumpMethod = method;
    return true;
  }

  public boolean setBlockNodeAttr(String csv) {
    Set<String> names = new HashSet<>();
    for (String name : csv.split(",")) {
      name = name.trim();
      if (name.isEmpty()) continue;
      if (!Arrays.asList(NodeAttributes.NAMES).contains(name))
        throw new IllegalArgumentException("未知节点属性：" + name);
      names.add(name);
    }
    blocked = names;
    return true;
  }

  public boolean addNodeFlag(int flag) {
    return changeFlag(flag, true);
  }

  public boolean removeNodeFlag(int flag) {
    return changeFlag(flag, false);
  }

  private boolean changeFlag(int flag, boolean add) {
    AccessibilityService service = AccUtils.AccessibilityHelper;
    if (service == null) return false;
    AccessibilityServiceInfo info = service.getServiceInfo();
    if (originalFlags == null) originalFlags = info.flags;
    info.flags = add ? info.flags | flag : info.flags & ~flag;
    service.setServiceInfo(info);
    if (flag == 0) {
      AccessibilityNodeInfo root = service.getRootInActiveWindow();
      if (root != null) {
        root.refresh();
        root.recycle();
      }
    }
    return true;
  }

  public boolean lockNode() {
    releaseNode();
    boolean oldHidden = invisible, oldImportant = notImportant;
    String oldAlgorithm = algorithm;
    try {
      invisible = true;
      notImportant = true;
      algorithm = "dsf";
      locked = NodeQuery.find(new UiSelector(), null, 0, false);
      return !locked.isEmpty();
    } finally {
      invisible = oldHidden;
      notImportant = oldImportant;
      algorithm = oldAlgorithm;
    }
  }

  public void releaseNode() {
    if (locked != null) {
      for (UiObject node : locked) node.recycle();
      locked = null;
    }
  }

  public String dumpXml() {
    String oldAlgorithm = algorithm;
    boolean oldHidden = invisible, oldImportant = notImportant;
    List<UiObject> nodes;
    try {
      algorithm = "dsf";
      invisible = true;
      notImportant = true;
      nodes = NodeQuery.find(new UiSelector(), null, 0, false);
    } finally {
      algorithm = oldAlgorithm;
      invisible = oldHidden;
      notImportant = oldImportant;
    }
    try {
      XmlSerializer xml = Xml.newSerializer();
      StringWriter out = new StringWriter();
      xml.setOutput(out);
      xml.startDocument("UTF-8", true);
      xml.startTag(null, "hierarchy");
      int open = 0;
      int window = Integer.MIN_VALUE;
      for (UiObject node : nodes) {
        // XML 始终使用完整深度优先结构，保留隐藏父节点，避免层次错乱。
        int depth = node.depth();
        int id = node.node.getWindowId();
        if (window != id) {
          while (open > 0) {
            xml.endTag(null, "node");
            open--;
          }
          if (window != Integer.MIN_VALUE) xml.endTag(null, "window");
          xml.startTag(null, "window");
          xml.attribute(null, "id", String.valueOf(id));
          window = id;
        }
        while (open > depth) {
          xml.endTag(null, "node");
          open--;
        }
        xml.startTag(null, "node");
        open++;
        for (Map.Entry<String, Object> entry :
            new NodeAttributes(node.node, depth, node.index()).all().entrySet()) {
          Object value = entry.getValue();
          String text =
              value instanceof int[] ? Arrays.toString((int[]) value) : String.valueOf(value);
          xml.attribute(null, entry.getKey(), text);
        }
      }
      while (open > 0) {
        xml.endTag(null, "node");
        open--;
      }
      if (window != Integer.MIN_VALUE) xml.endTag(null, "window");
      xml.endTag(null, "hierarchy");
      xml.endDocument();
      return out.toString();
    } catch (Exception error) {
      throw new IllegalStateException("无法输出节点 XML", error);
    } finally {
      for (UiObject node : nodes) node.recycle();
    }
  }

  public void reset() {
    releaseNode();
    invisible = true;
    notImportant = true;
    mode = 1;
    maxDepth = 80;
    dumpMethod = 1;
    algorithm = "dsf";
    blocked = Collections.emptySet();
    AccessibilityService service = AccUtils.AccessibilityHelper;
    if (originalFlags != null && service != null) {
      AccessibilityServiceInfo info = service.getServiceInfo();
      info.flags = originalFlags;
      service.setServiceInfo(info);
    }
    originalFlags = null;
  }
}
