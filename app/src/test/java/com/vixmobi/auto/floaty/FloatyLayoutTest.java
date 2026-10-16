package com.vixmobi.auto.floaty;

import org.junit.Test;
import static org.junit.Assert.*;

/** 在 JVM 上验证布局输入与边界，实际 View / WindowManager 行为由真机脚本验证。 */
public class FloatyLayoutTest {
    @Test public void readsAndroidAttributesAndEscapedText() throws Exception {
        FloatyLayout layout = FloatyLayout.parse("<LinearLayout android:orientation='vertical'><TextView android:tag='status' text='中文 &amp; &quot;引号&quot;'/></LinearLayout>");
        assertEquals("LinearLayout", layout.type);
        assertEquals("vertical", layout.attributes.get("orientation"));
        assertEquals("status", layout.children.get(0).attributes.get("tag"));
        assertEquals("中文 & \"引号\"", layout.children.get(0).attributes.get("text"));
    }
    @Test public void supportsQualifiedNamesAndNamespace() throws Exception {
        FloatyLayout layout = FloatyLayout.parse("<android.widget.TextView xmlns:android='http://schemas.android.com/apk/res/android' android:layout_width='100dp'/>");
        assertEquals("TextView", layout.type);
        assertEquals("100dp", layout.attributes.get("layout_width"));
    }
    @Test public void rejectsExternalEntitiesUnknownWidgetsAndInvalidChildren() {
        String[] invalid = {"<!DOCTYPE x [<!ENTITY secret SYSTEM 'file:///etc/passwd'>]><TextView text='&secret;'/>",
                "<Unknown/>", "<TextView mystery='x'/>", "<TextView><Button/></TextView>",
                "<ScrollView><TextView/><TextView/></ScrollView>", "<TextView>未声明属性的正文</TextView>", "<LinearLayout>"};
        for (String xml : invalid) assertThrows(Exception.class, () -> FloatyLayout.parse(xml));
    }
    @Test public void boundsBytesCountAndDepth() {
        assertThrows(Exception.class, () -> FloatyLayout.parse("<TextView text='" + repeat("中", 22000) + "'/>"));
        assertThrows(Exception.class, () -> FloatyLayout.parse("<LinearLayout>" + repeat("<TextView/>", 200) + "</LinearLayout>"));
        assertThrows(Exception.class, () -> FloatyLayout.parse(repeat("<FrameLayout>", 22) + repeat("</FrameLayout>", 22)));
    }
    private static String repeat(String value, int count) {
        StringBuilder text = new StringBuilder(); for (int i = 0; i < count; i++) text.append(value); return text.toString();
    }
}
