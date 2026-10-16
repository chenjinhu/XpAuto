package com.vixmobi.auto.node;

import com.google.gson.JsonPrimitive;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;

/** 一条不可变筛选条件；正则只在创建条件时编译，查询时复用。 */
final class SelectorCondition {
    final String attribute;
    final String operation;
    final Object expected;
    private final Pattern pattern;

    SelectorCondition(String attribute, String operation, Object expected) {
        this.attribute = attribute;
        this.operation = operation;
        this.expected = Objects.requireNonNull(expected, "选择器参数不能为 null");
        pattern = operation.equals("match") ? Pattern.compile((String) expected) : null;
    }

    boolean matches(Function<String, Object> values) {
        Object actual = values.apply(attribute);
        if (actual == null) return false;
        switch (operation) {
            case "equal": return expected.equals(actual);
            case "contains": return ((String) actual).contains((String) expected);
            case "starts": return ((String) actual).startsWith((String) expected);
            case "ends": return ((String) actual).endsWith((String) expected);
            case "match": return pattern.matcher((String) actual).matches();
            case "rect": return Arrays.equals((int[]) actual, (int[]) expected);
            case "inside": return encloses((int[]) expected, (int[]) actual);
            case "encloses": return encloses((int[]) actual, (int[]) expected);
            default: throw new IllegalStateException("未知筛选方式：" + operation);
        }
    }

    /** 只描述已有条件，不读取节点；字符串使用 JSON 转义，保留正则反斜杠等内容。 */
    @Override public String toString() {
        String suffix;
        switch (operation) {
            case "equal": case "rect": suffix = ""; break;
            case "contains": suffix = "Contains"; break;
            case "starts": suffix = "StartsWith"; break;
            case "ends": suffix = "EndsWith"; break;
            case "match": suffix = "Match"; break;
            case "inside": suffix = "Inside"; break;
            case "encloses": suffix = "Contains"; break;
            default: throw new IllegalStateException("未知筛选方式：" + operation);
        }
        String argument;
        if (expected instanceof int[]) {
            int[] rect = (int[]) expected;
            argument = rect[0] + ", " + rect[1] + ", " + rect[2] + ", " + rect[3];
        } else if (expected instanceof String) {
            argument = new JsonPrimitive((String) expected).toString();
        } else {
            argument = expected.toString();
        }
        return attribute + suffix + "(" + argument + ")";
    }

    static int[] rectangle(int left, int top, int right, int bottom) {
        if (right < left || bottom < top) throw new IllegalArgumentException("矩形右下边界不能小于左上边界");
        return new int[] {left, top, right, bottom};
    }
    private static boolean encloses(int[] outer, int[] inner) {
        return outer[0] <= inner[0] && outer[1] <= inner[1] && outer[2] >= inner[2] && outer[3] >= inner[3];
    }
}
