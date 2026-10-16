package com.vixmobi.auto.devtools;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.vixmobi.auto.R;

import java.util.function.Consumer;

/** 只使用一个悬浮窗口；收起时同时缩小触摸区域，避免透明菜单挡住其他应用。 */
final class FloatingControls {
    private final Context context;
    private final WindowManager windows;
    private final Consumer<String> action;
    private final LinearLayout root, menu;
    private final ScrollView menuScroll;
    private final ImageView bubble;
    private final TextView run, pause, stop, logs;
    private boolean logsVisible;
    private final WindowManager.LayoutParams params;
    private boolean expanded, above;
    // 记录收起状态下圆球外框的位置，展开菜单不改变这个锚点。
    private int anchorX, anchorY;
    private int visibleMenuHeight;
    private String lastState, lastDetail;
    private boolean lastCanRun;

    FloatingControls(Context context, Consumer<String> action) {
        this.context = context;
        this.action = action;
        windows = context.getSystemService(WindowManager.class);
        root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(4), dp(4), dp(4), dp(4));
        root.setClipChildren(false);
        root.setClipToPadding(false);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        bubble = UiStyle.appIcon(context, 48);
        bubble.setElevation(dp(3));
        bubble.setContentDescription("展开悬浮菜单");
        bubble.setClickable(true);
        bubble.setOnClickListener(view -> setExpanded(!expanded));
        root.addView(bubble, new LinearLayout.LayoutParams(dp(48), dp(48)));
        menu = new LinearLayout(context);
        menu.setOrientation(LinearLayout.VERTICAL);
        run = button("运行", R.drawable.xpauto_ic_play, UiStyle.BLUE, "run");
        pause = button("暂停", R.drawable.xpauto_ic_pause, Color.rgb(185, 123, 23), "pause");
        stop = button("停止", R.drawable.xpauto_ic_stop, Color.rgb(210, 68, 81), "stop");
        logs = button("日志", R.drawable.xpauto_ic_logs, Color.rgb(36, 132, 117), "logs");
        button("面板", R.drawable.xpauto_ic_panel, UiStyle.INK, "panel");
        button("结束", R.drawable.xpauto_ic_power, UiStyle.MUTED, "end");
        menuScroll = new ScrollView(context);
        menuScroll.setClipToPadding(false);
        menuScroll.addView(menu);
        menuScroll.setVisibility(View.GONE);
        root.addView(menuScroll);
        params = new WindowManager.LayoutParams(dp(56), dp(56),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.setTitle("XpAuto floating controls");
        anchorX = context.getSharedPreferences("floating-controls", Context.MODE_PRIVATE).getInt("x", dp(12));
        anchorY = context.getSharedPreferences("floating-controls", Context.MODE_PRIVATE).getInt("y", dp(160));
        bubble.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY;
            int startX, startY;
            boolean dragging;
            final int slop = ViewConfiguration.get(context).getScaledTouchSlop();
            @Override public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX(); downY = event.getRawY();
                        startX = params.x + (expanded ? (dp(84) - dp(48)) / 2 : 0);
                        startY = params.y + (above ? menuHeight() : 0);
                        dragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - downX, dy = event.getRawY() - downY;
                        if (!dragging && Math.hypot(dx, dy) > slop) {
                            dragging = true;
                            setExpanded(false);
                        }
                        if (dragging) {
                            anchorX = startX + Math.round(dx); anchorY = startY + Math.round(dy);
                            layout();
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (dragging) savePosition(); else view.performClick();
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        if (dragging) savePosition();
                        return true;
                    default: return false;
                }
            }
        });
        root.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_OUTSIDE) {
                setExpanded(false);
                return true;
            }
            return false;
        });
        layout();
        windows.addView(root, params);
    }

    private TextView button(String label, int drawable, int color, String command) {
        TextView button = new TextView(context);
        button.setText(label);
        button.setTextSize(13);
        button.setTextColor(color);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(10), 0, dp(10), 0);
        setIcon(button, drawable, color);
        button.setCompoundDrawablePadding(dp(6));
        button.setBackground(UiStyle.ripple(context, Color.WHITE, 14));
        button.setElevation(dp(2));
        button.setContentDescription("悬浮窗" + label);
        button.setFocusable(true);
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(dp(84), dp(44));
        size.topMargin = dp(6);
        menu.addView(button, size);
        button.setOnClickListener(view -> {
            setExpanded(false);
            action.accept(command);
        });
        return button;
    }

    private void setIcon(TextView button, int resource, int color) {
        android.graphics.drawable.Drawable icon = context.getDrawable(resource).mutate();
        icon.setTint(color);
        icon.setBounds(0, 0, dp(18), dp(18));
        button.setCompoundDrawables(icon, null, null, null);
    }

    /** 日志显示状态独立于脚本状态，关闭日志窗也能立即同步按钮。 */
    void setLogsVisible(boolean visible) {
        if (logsVisible == visible) return;
        logsVisible = visible;
        logs.setBackground(UiStyle.ripple(context, visible ? Color.rgb(218, 244, 238) : Color.WHITE, 14));
        logs.setTooltipText(visible ? "隐藏日志悬浮窗" : "显示日志悬浮窗");
        logs.setSelected(visible);
    }

    void update(String state, boolean canRun, String detail) {
        if (state.equals(lastState) && detail.equals(lastDetail) && canRun == lastCanRun) return;
        lastState = state; lastDetail = detail; lastCanRun = canRun;
        boolean paused = "paused".equals(state);
        boolean busy = "running".equals(state) || paused || "pausing".equals(state);
        pause.setText(paused ? "继续" : "暂停");
        pause.setContentDescription(paused ? "悬浮窗继续" : "悬浮窗暂停");
        setIcon(pause, paused ? R.drawable.xpauto_ic_play : R.drawable.xpauto_ic_pause, Color.rgb(185, 123, 23));
        enable(run, canRun);
        enable(pause, busy && !"pausing".equals(state));
        enable(stop, busy);
        bubble.setTooltipText("XpAuto · " + detail + "（拖动可移动）");
    }
    private void enable(View view, boolean enabled) {
        view.setEnabled(enabled);
        view.setAlpha(enabled ? 1f : .38f);
    }
    void setExpanded(boolean value) {
        expanded = value;
        menuScroll.setVisibility(value ? View.VISIBLE : View.GONE);
        bubble.setContentDescription(value ? "收起悬浮菜单" : "展开悬浮菜单");
        layout();
    }
    private void layout() {
        Rect bounds = windows.getCurrentWindowMetrics().getBounds();
        Insets insets = windows.getCurrentWindowMetrics().getWindowInsets().getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        int width = bounds.width() - insets.left - insets.right;
        int height = bounds.height() - insets.top - insets.bottom;
        // 每个子项分别换算像素后再相加，避免非整数屏幕密度导致底部按钮被裁切。
        int collapsed = dp(48) + dp(4) * 2;
        // 按实际按钮数计算；横屏高度不足时允许滚动菜单，避免“结束”等按钮被裁掉。
        visibleMenuHeight = Math.min(menu.getChildCount() * (dp(44) + dp(6)), Math.max(0, height - collapsed));
        menuScroll.setLayoutParams(new LinearLayout.LayoutParams(-1, visibleMenuHeight));
        anchorX = clamp(anchorX, 0, Math.max(0, width - collapsed));
        anchorY = clamp(anchorY, 0, Math.max(0, height - collapsed));
        params.width = expanded ? dp(84) + dp(4) * 2 : collapsed;
        params.height = collapsed + (expanded ? menuHeight() : 0);
        above = expanded && anchorY + params.height > height;
        // 底部空间不足时把菜单放到圆球上方，圆球本身尽量保持在拖动后的锚点。
        root.removeView(menuScroll);
        root.addView(menuScroll, above ? 0 : root.getChildCount());
        menu.setPadding(0, 0, 0, above ? dp(6) : 0);
        ((LinearLayout.LayoutParams) run.getLayoutParams()).topMargin = above ? 0 : dp(6);
        params.x = clamp(anchorX - (expanded ? (dp(84) - dp(48)) / 2 : 0), 0, Math.max(0, width - params.width));
        params.y = clamp(anchorY - (above ? menuHeight() : 0), 0, Math.max(0, height - params.height));
        if (root.isAttachedToWindow()) windows.updateViewLayout(root, params);
    }
    void onConfigurationChanged(Configuration configuration) { layout(); }
    void bringToFront() {
        if (!root.isAttachedToWindow()) return;
        windows.removeViewImmediate(root);
        windows.addView(root, params);
    }
    private void savePosition() {
        context.getSharedPreferences("floating-controls", Context.MODE_PRIVATE).edit()
                .putInt("x", anchorX).putInt("y", anchorY).apply();
    }
    void close() {
        if (root.isAttachedToWindow()) windows.removeView(root);
    }
    private int dp(int value) { return UiStyle.dp(context, value); }
    private int menuHeight() { return visibleMenuHeight; }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
