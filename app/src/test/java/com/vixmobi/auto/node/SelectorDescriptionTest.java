package com.vixmobi.auto.node;

import com.google.gson.JsonParser;
import org.junit.Test;
import static org.junit.Assert.*;

/** 无 Android 服务也能输出条件，防止打印意外触发查询或破坏可复用选择器。 */
public class SelectorDescriptionTest {
    @Test public void describesEmptySelectorsAndLegacyTaskBase() {
        assertEquals("selector()", new UiSelector().toString());
        assertEquals("text(\"搜索\")", new TaskBase().text("搜索").toString());
        UiSelector value = new UiSelector().textMatch(".*Play.*").idMatch(".*fake_search_box_top.*")
                .checked(false).index(0);
        String expected = "textMatch(\".*Play.*\").idMatch(\".*fake_search_box_top.*\").checked(false).index(0)";
        assertEquals(expected, value.toString());
        assertEquals(expected, value.toJSON());
        assertEquals(expected, value.toJSON("nested"));
    }

    @Test public void stringsRoundTripWithoutLosingQuotesNewlinesOrRegexEscapes() {
        for (String text : new String[]{"", "搜索", "a\"b\\c\n\r\t\u0000", "<>&='\u2028\u2029", "😀"}) {
            String description = new UiSelector().text(text).toString();
            assertEquals(text, JsonParser.parseString(description.substring(5, description.length() - 1)).getAsString());
            assertFalse(description.contains("\n"));
        }
        assertEquals("textMatch(\"\\\\d+\\\\.\\\\w+\")", new UiSelector().textMatch("\\d+\\.\\w+").toString());
    }

    @Test public void usesCanonicalAliasesAndAllConditionOperations() {
        UiSelector value = new UiSelector().textContains("a").idStartsWith("b").descEndsWith("c")
                .classNameMatch(".*Button").packageName("app").bounds(0, 1, 20, 30)
                .boundsInside(-1, -2, 40, 50).boundsContains(5, 6, 10, 11);
        assertEquals("textContains(\"a\").idStartsWith(\"b\").descEndsWith(\"c\")"
                + ".clzMatch(\".*Button\").pkg(\"app\").bounds(0, 1, 20, 30)"
                + ".boundsInside(-1, -2, 40, 50).boundsContains(5, 6, 10, 11)", value.toString());
    }

    @Test public void preservesCascadeBoundariesAndDoesNotModifyOriginalConditions() {
        UiSelector original = new UiSelector().text("容器");
        UiSelector child = original.child().checked(true).directChild().text("子节点");
        assertEquals("text(\"容器\").child().checked(true).directChild().text(\"子节点\")", child.toString());
        assertEquals("text(\"容器\")", original.toString());
        child.toJSON(); child.toString();
        assertEquals(3, child.stages.size());
        assertEquals(1, child.stages.get(1).conditions.size());
        original.enabled(true);
        assertEquals("text(\"容器\").enabled(true)", original.toString());
        assertEquals("selector().child().directChild()", new UiSelector().child().directChild().toString());
    }

    @Test public void describesEffectiveXPathAndItsStage() {
        UiSelector value = new UiSelector().text("容器").xpath("//node[@text='容器']")
                .directChild().xpath("//node[@enabled='true']").text("目标");
        assertEquals("xpath(\"//node[@text='容器']\").text(\"容器\").directChild()"
                + ".xpath(\"//node[@enabled='true']\").text(\"目标\")", value.toString());
        value.xpath("//node[@text='更新']");
        assertTrue(value.toString().contains("xpath(\"//node[@text='更新']\")"));
        assertFalse(value.toString().contains("@enabled"));
    }
}
