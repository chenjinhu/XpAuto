package com.vixmobi.auto.node;

import org.junit.Test;
import static org.junit.Assert.*;

public class SelectorConditionTest {
    private boolean match(String operation, Object expected, Object actual) {
        return new SelectorCondition("text", operation, expected).matches(key -> actual);
    }
    @Test public void exactDoesNotMatchSubstringOrLiteralNull() {
        assertFalse(match("equal", "设置", "系统设置"));
        assertTrue(match("contains", "设置", "系统设置"));
        assertTrue(match("equal", "", ""));
        assertFalse(match("equal", "null", ""));
    }
    @Test public void supportsPrefixSuffixAndCompiledWholeRegex() {
        assertTrue(match("starts", "按钮", "按钮甲"));
        assertTrue(match("ends", "甲", "按钮甲"));
        SelectorCondition regex = new SelectorCondition("text", "match", "按钮[甲乙]");
        assertTrue(regex.matches(key -> "按钮甲")); assertTrue(regex.matches(key -> "按钮乙"));
        assertFalse(regex.matches(key -> "前缀按钮甲"));
    }
    @Test public void stateAndNumbersDoNotCoerceTypes() {
        assertTrue(match("equal", false, false)); assertFalse(match("equal", false, "false"));
        assertTrue(match("equal", 0, 0)); assertFalse(match("equal", 0, -1));
    }
    @Test public void rectanglesDistinguishExactInsideAndContains() {
        int[] outer = {0, 0, 100, 100}, inner = {10, 20, 40, 50};
        assertFalse(match("rect", outer, inner)); assertTrue(match("rect", inner, inner.clone()));
        assertTrue(match("inside", outer, inner)); assertFalse(match("inside", inner, outer));
        assertTrue(match("encloses", inner, outer)); assertFalse(match("encloses", outer, inner));
    }
    @Test public void rejectsInvalidArgumentsBeforeReadingNodes() {
        assertThrows(NullPointerException.class, () -> new SelectorCondition("text", "equal", null));
        assertThrows(java.util.regex.PatternSyntaxException.class, () -> new SelectorCondition("text", "match", "["));
        assertThrows(IllegalArgumentException.class, () -> SelectorCondition.rectangle(20, 0, 10, 30));
    }
}
