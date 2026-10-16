package com.vixmobi.auto.debug;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.vixmobi.auto.R;

/** 仅用于 debug 构建的可重复节点测试页，不操作其他应用的数据。 */
public final class SelectorFixtureActivity extends Activity {
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = column(); root.setPadding(24, 24, 24, 24);
        LinearLayout group = column(); group.setId(R.id.selector_group); group.setContentDescription("测试分组");
        group.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View view, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(view, info);
                info.setCollectionInfo(AccessibilityNodeInfo.CollectionInfo.obtain(2, 3, false));
            }
        });
        TextView exact = label("测试项"); exact.setId(R.id.selector_exact); exact.setContentDescription("说明"); group.addView(exact);
        TextView longer = label("测试项扩展"); longer.setId(R.id.selector_longer); longer.setContentDescription("说明扩展"); group.addView(longer);
        CheckBox check = new CheckBox(this); check.setText("勾选项"); check.setId(R.id.selector_check);
        check.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View view, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(view, info);
                info.setCollectionItemInfo(AccessibilityNodeInfo.CollectionItemInfo.obtain(1, 1, 2, 1, false));
            }
        });
        group.addView(check); root.addView(group);
        EditText input = new EditText(this); input.setId(R.id.selector_input); input.setSingleLine(true); input.setText("初始输入"); root.addView(input);
        TextView status = label("尚未操作"); status.setId(R.id.selector_status); root.addView(status);
        Button action = new Button(this); action.setText("测试点击"); action.setOnClickListener(view -> status.setText("点击成功"));
        action.setOnLongClickListener(view -> { status.setText("长按成功"); return true; }); root.addView(action);
        Button delayed = new Button(this); delayed.setText("延迟显示"); delayed.setOnClickListener(view -> handler.postDelayed(() -> status.setText("等待目标已出现"), 600)); root.addView(delayed);
        ScrollView scroll = new ScrollView(this); scroll.setId(R.id.selector_scroll);
        // 屏外节点的无障碍坐标可能被系统缓存，用实际 scrollY 验证滚动方向。
        scroll.setOnScrollChangeListener((view, x, y, oldX, oldY) -> status.setText("滚动位置：" + y));
        LinearLayout rows = column(); for (int i = 0; i < 40; i++) rows.addView(label("滚动行 " + i));
        scroll.addView(rows); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }
    private LinearLayout column() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); return layout; }
    private TextView label(String text) { TextView view = new TextView(this); view.setText(text); view.setTextSize(18); view.setPadding(6, 14, 6, 14); return view; }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy(); }
}
