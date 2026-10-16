package com.vixmobi.auto.floaty;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NamedNodeMap;
import org.xml.sax.InputSource;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;

/** 将有限的、可读的 XML 布局转换成原生控件，不依赖编译后的 Android XML 资源。 */
final class FloatyLayout {
    static final int MAX_XML_BYTES = 65536;
    private static final Set<String> TYPES = new HashSet<>(Arrays.asList("LinearLayout", "FrameLayout", "RelativeLayout",
            "ScrollView", "HorizontalScrollView", "TextView", "Button", "EditText", "CheckBox", "Switch", "ProgressBar", "View", "Space"));
    private static final Set<String> ATTRIBUTES = new HashSet<>(Arrays.asList("layout_width", "layout_height", "layout_weight", "layout_gravity",
            "layout_margin", "layout_marginLeft", "layout_marginTop", "layout_marginRight", "layout_marginBottom",
            "id", "tag", "orientation", "gravity", "padding", "paddingLeft", "paddingTop", "paddingRight", "paddingBottom",
            "background", "backgroundColor", "cornerRadius", "text", "textColor", "textSize", "textStyle", "hint", "singleLine",
            "maxLines", "inputType", "checked", "enabled", "clickable", "focusable", "visibility", "alpha", "contentDescription",
            "minWidth", "minHeight", "layout_centerInParent", "layout_centerHorizontal", "layout_centerVertical",
            "layout_alignParentTop", "layout_alignParentBottom", "layout_alignParentLeft", "layout_alignParentRight"));
    final String type;
    final Map<String, String> attributes = new LinkedHashMap<>();
    final List<FloatyLayout> children = new ArrayList<>();
    private FloatyLayout(String type) { this.type = type; }

    static FloatyLayout parse(String xml) throws Exception {
        if (xml.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_XML_BYTES) throw new IllegalArgumentException("XML 不能超过 64 KiB");
        // 浮窗布局无需 DTD 或外部实体，解析前明确拒绝，避免读取布局以外的文件。
        if (java.util.regex.Pattern.compile("<!\\s*(DOCTYPE|ENTITY)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xml).find()) {
            throw new IllegalArgumentException("XML 不支持 DTD 和实体声明");
        }
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false); factory.setExpandEntityReferences(false);
        javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
            @Override public void error(org.xml.sax.SAXParseException error) throws org.xml.sax.SAXException { throw error; }
            @Override public void fatalError(org.xml.sax.SAXParseException error) throws org.xml.sax.SAXException { throw error; }
        });
        Element root = builder.parse(new InputSource(new StringReader(xml))).getDocumentElement();
        return read(root, 0, new int[]{0});
    }
    private static FloatyLayout read(Element element, int depth, int[] count) {
        if (depth > 20 || ++count[0] > 200) throw new IllegalArgumentException("XML 最多 200 个控件、20 层嵌套");
        String name = element.getTagName().replaceFirst("^android\\.(widget|view)\\.", "");
        if (!TYPES.contains(name)) throw new IllegalArgumentException("不支持的 XML 控件：" + name);
        FloatyLayout result = new FloatyLayout(name);
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node item = attributes.item(i);
            String key = item.getNodeName();
            if (key.equals("xmlns") || key.startsWith("xmlns:")) continue;
            key = key.replaceFirst("^android:", "");
            if (!ATTRIBUTES.contains(key)) throw new IllegalArgumentException("不支持的 XML 属性：" + key);
            result.attributes.put(key, item.getNodeValue());
        }
        for (Node item = element.getFirstChild(); item != null; item = item.getNextSibling()) {
            if (item instanceof Element) result.children.add(read((Element) item, depth + 1, count));
            else if (item.getNodeType() == Node.TEXT_NODE && !item.getTextContent().trim().isEmpty()) {
                throw new IllegalArgumentException("控件文本请写在 text 属性中");
            }
        }
        if (!result.children.isEmpty() && !Arrays.asList("LinearLayout", "FrameLayout", "RelativeLayout", "ScrollView", "HorizontalScrollView").contains(name)) {
            throw new IllegalArgumentException(name + " 不能包含子控件");
        }
        if ((name.equals("ScrollView") || name.equals("HorizontalScrollView")) && result.children.size() > 1) {
            throw new IllegalArgumentException(name + " 只能包含一个直接子控件");
        }
        return result;
    }
    View build(Context context) {
        View view;
        switch (type) {
            case "LinearLayout": view = new LinearLayout(context); break;
            case "FrameLayout": view = new FrameLayout(context); break;
            case "RelativeLayout": view = new RelativeLayout(context); break;
            case "ScrollView": view = new ScrollView(context); break;
            case "HorizontalScrollView": view = new HorizontalScrollView(context); break;
            case "TextView": view = new TextView(context); break;
            case "Button": view = new Button(context); break;
            case "EditText": view = new EditText(context); break;
            case "CheckBox": view = new CheckBox(context); break;
            case "Switch": view = new Switch(context); break;
            case "ProgressBar": view = new ProgressBar(context); break;
            case "Space": view = new Space(context); break;
            default: view = new View(context);
        }
        view.setLayoutParams(new ViewGroup.LayoutParams(size(context, "layout_width", -2), size(context, "layout_height", -2)));
        if (attributes.containsKey("id")) {
            view.setId(View.generateViewId());
            view.setTag(attributes.get("id").replaceFirst("^@\\+?id/", ""));
        }
        if (attributes.containsKey("tag")) view.setTag(attributes.get("tag"));
        if (view instanceof LinearLayout) {
            String orientation = attributes.getOrDefault("orientation", "vertical");
            if (!orientation.equals("vertical") && !orientation.equals("horizontal")) throw new IllegalArgumentException("orientation 只能为 vertical / horizontal");
            ((LinearLayout) view).setOrientation(orientation.equals("horizontal") ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        }
        int padding = size(context, "padding", 0);
        view.setPadding(size(context, "paddingLeft", padding), size(context, "paddingTop", padding), size(context, "paddingRight", padding), size(context, "paddingBottom", padding));
        if (attributes.containsKey("background") || attributes.containsKey("backgroundColor")) {
            GradientDrawable background = new GradientDrawable();
            background.setColor(Color.parseColor(attributes.getOrDefault("backgroundColor", attributes.get("background"))));
            background.setCornerRadius(size(context, "cornerRadius", 0)); view.setBackground(background);
        }
        if (attributes.containsKey("gravity")) {
            int gravity = gravity(attributes.get("gravity"));
            if (view instanceof LinearLayout) ((LinearLayout) view).setGravity(gravity);
            else if (view instanceof TextView) ((TextView) view).setGravity(gravity);
            else throw new IllegalArgumentException(type + " 不支持 gravity");
        }
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            if (attributes.containsKey("inputType")) text.setInputType(inputType(attributes.get("inputType")));
            if (attributes.containsKey("text")) text.setText(attributes.get("text"));
            if (attributes.containsKey("hint")) text.setHint(attributes.get("hint"));
            if (attributes.containsKey("textColor")) text.setTextColor(Color.parseColor(attributes.get("textColor")));
            if (attributes.containsKey("textSize")) text.setTextSize(TypedValue.COMPLEX_UNIT_PX, dimension(context, attributes.get("textSize"), true));
            if (attributes.containsKey("textStyle")) {
                int style = 0;
                for (String part : attributes.get("textStyle").split("\\|")) {
                    if (part.equals("bold")) style |= Typeface.BOLD;
                    else if (part.equals("italic")) style |= Typeface.ITALIC;
                    else if (!part.equals("normal")) throw new IllegalArgumentException("未知 textStyle：" + part);
                }
                text.setTypeface(text.getTypeface(), style);
            }
            if (attributes.containsKey("singleLine")) text.setSingleLine(bool("singleLine"));
            if (attributes.containsKey("maxLines")) text.setMaxLines(Integer.parseInt(attributes.get("maxLines")));
        }
        if (view instanceof CompoundButton && attributes.containsKey("checked")) ((CompoundButton) view).setChecked(bool("checked"));
        if (attributes.containsKey("enabled")) view.setEnabled(bool("enabled"));
        if (attributes.containsKey("clickable")) view.setClickable(bool("clickable"));
        if (attributes.containsKey("focusable")) view.setFocusable(bool("focusable"));
        if (attributes.containsKey("contentDescription")) view.setContentDescription(attributes.get("contentDescription"));
        if (attributes.containsKey("minWidth")) view.setMinimumWidth(size(context, "minWidth", 0));
        if (attributes.containsKey("minHeight")) view.setMinimumHeight(size(context, "minHeight", 0));
        if (attributes.containsKey("alpha")) {
            float alpha = Float.parseFloat(attributes.get("alpha"));
            if (!Float.isFinite(alpha) || alpha < 0 || alpha > 1) throw new IllegalArgumentException("alpha 必须在 0 到 1 之间");
            view.setAlpha(alpha);
        }
        if (attributes.containsKey("visibility")) {
            String value = attributes.get("visibility");
            if (!Arrays.asList("visible", "invisible", "gone").contains(value)) throw new IllegalArgumentException("未知 visibility：" + value);
            view.setVisibility(value.equals("gone") ? View.GONE : value.equals("invisible") ? View.INVISIBLE : View.VISIBLE);
        }
        for (FloatyLayout child : children) {
            View childView = child.build(context);
            ((ViewGroup) view).addView(childView, child.parameters(context, view));
        }
        return view;
    }
    private ViewGroup.LayoutParams parameters(Context context, View parent) {
        int width = size(context, "layout_width", -2), height = size(context, "layout_height", -2);
        ViewGroup.MarginLayoutParams params;
        if (parent instanceof LinearLayout) {
            LinearLayout.LayoutParams linear = new LinearLayout.LayoutParams(width, height);
            if (attributes.containsKey("layout_weight")) {
                linear.weight = Float.parseFloat(attributes.get("layout_weight"));
                if (!Float.isFinite(linear.weight) || linear.weight < 0) throw new IllegalArgumentException("layout_weight 必须为非负数");
            }
            if (attributes.containsKey("layout_gravity")) linear.gravity = gravity(attributes.get("layout_gravity"));
            params = linear;
        } else if (parent instanceof RelativeLayout) {
            RelativeLayout.LayoutParams relative = new RelativeLayout.LayoutParams(width, height);
            String[] names = {"layout_centerInParent", "layout_centerHorizontal", "layout_centerVertical", "layout_alignParentTop", "layout_alignParentBottom", "layout_alignParentLeft", "layout_alignParentRight"};
            int[] rules = {RelativeLayout.CENTER_IN_PARENT, RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.CENTER_VERTICAL, RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.ALIGN_PARENT_RIGHT};
            for (int i = 0; i < names.length; i++) if (attributes.containsKey(names[i]) && bool(names[i])) relative.addRule(rules[i]);
            params = relative;
        } else {
            FrameLayout.LayoutParams frame = new FrameLayout.LayoutParams(width, height);
            if (attributes.containsKey("layout_gravity")) frame.gravity = gravity(attributes.get("layout_gravity"));
            params = frame;
        }
        int margin = size(context, "layout_margin", 0);
        params.setMargins(size(context, "layout_marginLeft", margin), size(context, "layout_marginTop", margin), size(context, "layout_marginRight", margin), size(context, "layout_marginBottom", margin));
        return params;
    }
    private boolean bool(String name) {
        String value = attributes.get(name);
        if (!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException(name + " 必须为 true / false");
        return Boolean.parseBoolean(value);
    }
    private int size(Context context, String name, int fallback) {
        if (!attributes.containsKey(name)) return fallback;
        String value = attributes.get(name);
        if (name.equals("layout_width") || name.equals("layout_height")) {
            if (value.equals("match_parent") || value.equals("fill_parent")) return -1;
            if (value.equals("wrap_content")) return -2;
        }
        return Math.round(dimension(context, value, false));
    }
    static float dimension(Context context, String value, boolean text) {
        java.util.regex.Matcher match = java.util.regex.Pattern.compile("(-?\\d+(?:\\.\\d+)?)(dp|dip|sp|px)?").matcher(value.trim());
        if (!match.matches()) throw new IllegalArgumentException("无效尺寸：" + value);
        float number = Float.parseFloat(match.group(1));
        if (!Float.isFinite(number) || number < 0 || number > 10000) throw new IllegalArgumentException("尺寸必须在 0 到 10000 之间");
        String unit = match.group(2);
        int type = "px".equals(unit) ? TypedValue.COMPLEX_UNIT_PX : "sp".equals(unit) || (unit == null && text) ? TypedValue.COMPLEX_UNIT_SP : TypedValue.COMPLEX_UNIT_DIP;
        return TypedValue.applyDimension(type, number, context.getResources().getDisplayMetrics());
    }
    private static int gravity(String value) {
        int gravity = 0;
        for (String part : value.split("\\|")) {
            switch (part.trim()) {
                case "top": gravity |= Gravity.TOP; break; case "bottom": gravity |= Gravity.BOTTOM; break;
                case "left": gravity |= Gravity.LEFT; break; case "right": gravity |= Gravity.RIGHT; break;
                case "start": gravity |= Gravity.START; break; case "end": gravity |= Gravity.END; break;
                case "center": gravity |= Gravity.CENTER; break;
                case "center_horizontal": gravity |= Gravity.CENTER_HORIZONTAL; break;
                case "center_vertical": gravity |= Gravity.CENTER_VERTICAL; break;
                default: throw new IllegalArgumentException("未知 gravity：" + part);
            }
        }
        return gravity;
    }
    private static int inputType(String value) {
        switch (value) {
            case "text": return InputType.TYPE_CLASS_TEXT;
            case "number": return InputType.TYPE_CLASS_NUMBER;
            case "numberDecimal": return InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;
            case "phone": return InputType.TYPE_CLASS_PHONE;
            case "textPassword": return InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;
            case "textMultiLine": return InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE;
            default: throw new IllegalArgumentException("不支持的 inputType：" + value);
        }
    }
}
