package com.vixmobi.auto.node;

import com.google.gson.JsonPrimitive;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

/** 仅查询内存中构建的节点树，不读取外部 XML 或网络资源。 */
final class NodeXPath {
    private final String source;
    private final XPathExpression expression;
    NodeXPath(String value) {
        if (value == null || value.isEmpty() || value.length() > 8192)
            throw new IllegalArgumentException("XPath 长度必须为 1～8192");
        source = value;
        try { expression = XPathFactory.newInstance().newXPath().compile(value); }
        catch (XPathExpressionException error) { throw new IllegalArgumentException("XPath 语法错误：" + error.getMessage(), error); }
    }
    /** 保存原始表达式用于日志，不重新编译或执行查询。 */
    @Override public String toString() { return "xpath(" + new JsonPrimitive(source) + ")"; }
    NodeList select(Document document) {
        try { return (NodeList) expression.evaluate(document, XPathConstants.NODESET); }
        catch (XPathExpressionException error) { throw new IllegalArgumentException("XPath 必须返回节点集合：" + error.getMessage(), error); }
    }
}
