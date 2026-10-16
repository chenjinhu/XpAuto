package com.vixmobi.auto.node;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import javax.xml.parsers.DocumentBuilderFactory;
import static org.junit.Assert.*;

public class NodeXPathTest {
    private Document tree() throws Exception {
        Document d = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = d.createElement("hierarchy"), window = d.createElement("window"), group = d.createElement("node");
        d.appendChild(root); root.appendChild(window); window.setAttribute("id", "7"); window.appendChild(group);
        for (int i = 0; i < 3; i++) {
            Element node = d.createElement("node"); node.setAttribute("text", i == 0 ? "中文'与\"引号" : "条目 " + i);
            node.setAttribute("checked", i == 1 ? "true" : "false"); group.appendChild(node);
        }
        return d;
    }
    @Test public void inspectorAbsoluteAndRelativePathsSelectSameNode() throws Exception {
        Document d = tree();
        assertSame(new NodeXPath("/hierarchy/window[@id='7']/node[1]/node[2]").select(d).item(0),
                new NodeXPath("//node[@text='条目 1']").select(d).item(0));
    }
    @Test public void supportsQuotesFunctionsAxesAndBooleanAttributes() throws Exception {
        Document d = tree();
        assertEquals(1, new NodeXPath("//node[@text=concat('中文', \"'\", '与\"引号')]").select(d).getLength());
        assertEquals(2, new NodeXPath("//node[contains(@text,'条目')]").select(d).getLength());
        assertEquals(1, new NodeXPath("//node[@checked='true']/following-sibling::node").select(d).getLength());
    }
    @Test public void rejectsSyntaxAndNonNodeResults() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new NodeXPath("//node["));
        Document d = tree();
        assertThrows(IllegalArgumentException.class, () -> new NodeXPath("count(//node)").select(d));
    }
}
