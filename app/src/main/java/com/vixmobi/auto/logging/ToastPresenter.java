package com.vixmobi.auto.logging;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;
import com.google.gson.JsonObject;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** 全应用只展示一条临时提示；所有 View 和定时清理都在主线程完成。 */
public final class ToastPresenter {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static ToastPresenter current;
    private final Context context;
    private final Object owner;
    private final WindowManager windows;
    private final Runnable dismiss = this::remove;
    private Toast systemToast;
    private TextView view;
    private WindowManager.LayoutParams params;

    private ToastPresenter(Context context, Object owner) {
        this.context = context.getApplicationContext();
        this.owner = owner;
        windows = this.context.getSystemService(WindowManager.class);
    }

    public static void show(Context context, Object owner, String message, JsonObject extra,
                            int defaultDuration, BooleanSupplier valid, Consumer<String> errors) {
        // 入队前验证参数，脚本可以立即捕获格式错误，不会在 UI 线程崩溃。
        Options options = new Options(extra, defaultDuration);
        MAIN.post(() -> {
            if (!valid.getAsBoolean()) return;
            if (current != null) current.remove();
            ToastPresenter presenter = new ToastPresenter(context, owner);
            current = presenter;
            try {
                if (extra != null && Settings.canDrawOverlays(context)) presenter.overlay(message, options);
                else presenter.system(message, defaultDuration);
            } catch (RuntimeException error) {
                presenter.remove();
                errors.accept("Toast 显示失败：" + error.getMessage());
            }
        });
    }

    public static void cancel(Object owner) {
        MAIN.post(() -> { if (current != null && current.owner == owner) current.remove(); });
    }

    public static void cancelAll() {
        MAIN.post(() -> { if (current != null) current.remove(); });
    }

    private void system(String message, int duration) {
        systemToast = Toast.makeText(context, message, duration > 2000 ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT);
        systemToast.show();
        // 系统决定实际显示时间；稍后只释放引用，避免应用长期持有 Toast。
        MAIN.postDelayed(dismiss, duration > 2000 ? 4500 : 3000);
    }

    private void overlay(String message, Options options) {
        view = new TextView(context);
        view.setText(message);
        view.setTextColor(options.color);
        view.setTextSize(15);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(16), dp(10), dp(16), dp(10));
        view.setContentDescription("XpAuto Toast：" + message);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(35, 40, 49));
        background.setCornerRadius(dp(12));
        view.setBackground(background);
        Rect area = usableArea();
        view.setMaxWidth(Math.max(1, area.width() - dp(32)));
        int width = size(options.width, area.width()), height = size(options.height, area.height());
        view.measure(View.MeasureSpec.makeMeasureSpec(width > 0 ? width : Math.max(1, area.width() - dp(32)),
                        width > 0 ? View.MeasureSpec.EXACTLY : View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(height > 0 ? height : area.height(),
                        height > 0 ? View.MeasureSpec.EXACTLY : View.MeasureSpec.AT_MOST));
        params = new WindowManager.LayoutParams(view.getMeasuredWidth(), view.getMeasuredHeight(),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        // 使用整屏坐标，再由 clamp 排除系统栏，避免 y 被状态栏高度重复偏移。
        params.setFitInsetsTypes(0);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.setTitle("XpAuto toast");
        params.x = options.x == null ? area.left + (area.width() - params.width) / 2 : options.x;
        params.y = options.y == null ? area.bottom - params.height - dp(64) : options.y;
        if (!options.draggable) {
            params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            // Android 12+ 对不可触摸的覆盖层有透明度限制，避免挡住下层应用点击。
            params.alpha = .8f;
        } else enableDragging();
        clamp();
        windows.addView(view, params);
        MAIN.postDelayed(dismiss, options.duration);
    }

    private void enableDragging() {
        view.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY;
            int startX, startY;
            boolean dragging;
            final int slop = ViewConfiguration.get(context).getScaledTouchSlop();
            @Override public boolean onTouch(View target, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX(); downY = event.getRawY();
                        startX = params.x; startY = params.y; dragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - downX, dy = event.getRawY() - downY;
                        if (Math.hypot(dx, dy) > slop) dragging = true;
                        if (dragging) {
                            params.x = startX + Math.round(dx); params.y = startY + Math.round(dy);
                            clamp(); windows.updateViewLayout(view, params);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!dragging) target.performClick();
                        return true;
                    case MotionEvent.ACTION_CANCEL: return true;
                    default: return false;
                }
            }
        });
    }

    private Rect usableArea() {
        Rect area = new Rect(windows.getCurrentWindowMetrics().getBounds());
        Insets inset = windows.getCurrentWindowMetrics().getWindowInsets().getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        area.left += inset.left; area.top += inset.top; area.right -= inset.right; area.bottom -= inset.bottom;
        return area;
    }

    private void clamp() {
        Rect area = usableArea();
        params.x = Math.max(area.left, Math.min(params.x, area.right - params.width));
        params.y = Math.max(area.top, Math.min(params.y, area.bottom - params.height));
    }

    private int size(int value, int available) { return value == -1 ? available : value == -2 ? -2 : Math.min(value, available); }
    private int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }

    private void remove() {
        MAIN.removeCallbacks(dismiss);
        if (systemToast != null) { systemToast.cancel(); systemToast = null; }
        if (view != null && view.isAttachedToWindow()) windows.removeView(view);
        view = null;
        if (current == this) current = null;
    }

    private static final class Options {
        final Integer x, y;
        final int width, height, duration, color;
        final boolean draggable;
        Options(JsonObject extra, int defaultDuration) {
            JsonObject values = extra == null ? new JsonObject() : extra;
            x = values.has("x") ? integer(values, "x", 0, -100000, 100000) : null;
            y = values.has("y") ? integer(values, "y", 0, -100000, 100000) : null;
            width = dimension(values, "width"); height = dimension(values, "height");
            duration = integer(values, "duration", defaultDuration, 1, 60000);
            color = values.has("textColor") ? Color.parseColor(values.get("textColor").getAsString()) : Color.WHITE;
            draggable = values.has("draggable") && values.get("draggable").getAsBoolean();
        }
        private int dimension(JsonObject values, String key) {
            int value = integer(values, key, -2, -2, 16000);
            if (value == 0) throw new IllegalArgumentException(key + " 必须为正整数、-1 或 -2");
            return value;
        }
        private int integer(JsonObject values, String key, int fallback, int min, int max) {
            if (!values.has(key)) return fallback;
            double value = values.get(key).getAsDouble();
            if (!Double.isFinite(value) || value != Math.rint(value) || value < min || value > max)
                throw new IllegalArgumentException(key + " 必须是 " + min + "～" + max + " 范围内的整数");
            return (int) value;
        }
    }
}
