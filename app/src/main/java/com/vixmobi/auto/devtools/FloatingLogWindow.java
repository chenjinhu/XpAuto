package com.vixmobi.auto.devtools;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** 独立的日志窗口，只在自己的矩形内接收触摸，不抢下方应用的键盘焦点。 */
final class FloatingLogWindow {
    private final Context context;
    private final WindowManager windows;
    private final LinearLayout root;
    private final ScrollView scroll;
    private final TextView content, follow;
    private final WindowManager.LayoutParams params;
    private boolean following = true;
    private String lastText;

    FloatingLogWindow(Context context, Runnable clear, Runnable dismiss) {
        this.context = context;
        windows = context.getSystemService(WindowManager.class);
        root = new LinearLayout(context);
        root.setPadding(dp(4), dp(4), dp(4), dp(4));
        root.setContentDescription("日志悬浮窗");
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(UiStyle.shape(context, Color.WHITE, 16, true));
        card.setClipToOutline(true);
        card.setElevation(dp(3));
        root.addView(card, new LinearLayout.LayoutParams(-1, -1));

        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(UiStyle.SURFACE);
        card.addView(header, new LinearLayout.LayoutParams(-1, dp(48)));
        TextView title = new TextView(context);
        title.setText("运行日志");
        title.setTextSize(15);
        title.setTextColor(UiStyle.INK);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(14), 0, 0, 0);
        title.setContentDescription("拖动日志悬浮窗");
        title.setTooltipText("拖动标题可移动窗口");
        title.setOnClickListener(view -> { });
        header.addView(title, new LinearLayout.LayoutParams(0, -1, 1));
        TextView close = button("×", "关闭日志悬浮窗", dismiss);
        close.setTextSize(24);
        header.addView(close, new LinearLayout.LayoutParams(dp(48), -1));

        scroll = new ScrollView(context);
        scroll.setContentDescription("日志滚动区域");
        card.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        content = new TextView(context);
        content.setTextSize(12);
        content.setTextColor(UiStyle.INK);
        content.setTypeface(Typeface.MONOSPACE);
        content.setPadding(dp(12), dp(10), dp(12), dp(10));
        content.setContentDescription("日志内容");
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));

        LinearLayout footer = new LinearLayout(context);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setBackgroundColor(UiStyle.SURFACE);
        footer.addView(button("清空", "清空运行日志", () -> {
            clear.run();
            update("");
        }), new LinearLayout.LayoutParams(0, dp(44), 1));
        follow = button("", "", () -> setFollowing(!following));
        footer.addView(follow, new LinearLayout.LayoutParams(0, dp(44), 1));
        card.addView(footer);
        setFollowing(true);
        // 手动浏览时停止自动滚到底部；日志仍实时更新，用户可随时重新开启跟随。
        scroll.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) setFollowing(false);
            return false;
        });

        params = new WindowManager.LayoutParams(1, 1,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.setTitle("XpAuto floating logs");
        params.x = context.getSharedPreferences("floating-logs", Context.MODE_PRIVATE).getInt("x", dp(12));
        params.y = context.getSharedPreferences("floating-logs", Context.MODE_PRIVATE).getInt("y", Integer.MAX_VALUE);
        title.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY;
            int startX, startY;
            boolean dragging;
            final int slop = ViewConfiguration.get(context).getScaledTouchSlop();
            @Override public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX(); downY = event.getRawY();
                        startX = params.x; startY = params.y;
                        dragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - downX, dy = event.getRawY() - downY;
                        if (Math.hypot(dx, dy) > slop) dragging = true;
                        if (dragging) {
                            params.x = startX + Math.round(dx); params.y = startY + Math.round(dy);
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
        layout();
        windows.addView(root, params);
    }

    private TextView button(String label, String description, Runnable action) {
        TextView button = new TextView(context);
        button.setText(label);
        button.setTextSize(13);
        button.setTextColor(UiStyle.BLUE);
        button.setGravity(Gravity.CENTER);
        button.setBackground(UiStyle.ripple(context, UiStyle.SURFACE, 8));
        button.setContentDescription(description);
        button.setFocusable(true);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    void update(String text) {
        if (text.equals(lastText)) return;
        lastText = text;
        int previousY = scroll.getScrollY();
        content.setText(text.isEmpty() ? "暂无日志，运行脚本后会实时显示。" : text);
        // 等文本完成布局再定位；关闭窗口后排队的回调不得继续操作它。
        scroll.post(() -> {
            if (!root.isAttachedToWindow()) return;
            if (following) scroll.fullScroll(View.FOCUS_DOWN);
            else scroll.scrollTo(0, previousY);
        });
    }

    private void setFollowing(boolean value) {
        following = value;
        follow.setText(value ? "跟随：开" : "跟随：关");
        follow.setContentDescription(value ? "日志自动滚动已开启" : "日志自动滚动已关闭");
        if (value) scroll.post(() -> {
            if (following && root.isAttachedToWindow()) scroll.fullScroll(View.FOCUS_DOWN);
        });
    }

    void layout() {
        Rect bounds = windows.getCurrentWindowMetrics().getBounds();
        Insets insets = windows.getCurrentWindowMetrics().getWindowInsets().getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        int width = bounds.width() - insets.left - insets.right;
        int height = bounds.height() - insets.top - insets.bottom;
        params.width = Math.min(dp(328), Math.max(1, width - dp(16)));
        params.height = Math.min(dp(288), Math.max(1, height - dp(16)));
        params.x = Math.max(0, Math.min(params.x, width - params.width));
        params.y = Math.max(0, Math.min(params.y, height - params.height));
        if (root.isAttachedToWindow()) windows.updateViewLayout(root, params);
    }

    private void savePosition() {
        context.getSharedPreferences("floating-logs", Context.MODE_PRIVATE).edit()
                .putInt("x", params.x).putInt("y", params.y).apply();
    }

    void close() {
        savePosition();
        if (root.isAttachedToWindow()) windows.removeView(root);
    }
    private int dp(int value) { return UiStyle.dp(context, value); }
}
