package com.vixmobi.auto.floaty;

import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** 每个脚本会话独享的悬浮窗 API，与应用悬浮球、日志窗口互不共用标签。 */
public final class FloatyApi {
    private final Context context;
    private final WindowManager manager;
    private final UiDispatcher ui = new UiDispatcher();
    private final Consumer<String> logger;
    private final BooleanSupplier externalCancellation;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicBoolean disposed = new AtomicBoolean();
    private final AtomicInteger executing = new AtomicInteger();
    // 仅由主线程访问，避免锁住 UI 或在脚本终止时等待 V8 锁。
    private final Map<String, Window> windows = new LinkedHashMap<>();
    private static final class Window {
        final View view;
        final WindowManager.LayoutParams params;
        Window(View view, WindowManager.LayoutParams params) { this.view = view; this.params = params; }
    }
    public FloatyApi(Context context, Consumer<String> logger, BooleanSupplier externalCancellation) {
        this.context = new ContextThemeWrapper(context.getApplicationContext(), android.R.style.Theme_Material_Light_NoActionBar);
        this.manager = context.getApplicationContext().getSystemService(WindowManager.class);
        this.logger = logger; this.externalCancellation = externalCancellation;
    }
    private boolean cancelled() { return disposed.get() || cancelled.get() || externalCancellation.getAsBoolean(); }
    private void checkCancellation() {
        if (cancelled() || Thread.currentThread().isInterrupted()) throw new CancellationException("悬浮窗操作已取消");
    }
    private <T> T operation(String name, T fallback, Callable<T> action) {
        executing.incrementAndGet();
        try { checkCancellation(); return action.call(); }
        catch (CancellationException error) { throw error; }
        catch (InterruptedException error) {
            Thread.currentThread().interrupt(); throw new CancellationException("悬浮窗操作已中断");
        } catch (Exception error) {
            logger.accept("[floaty." + name + "] " + error.getMessage()); return fallback;
        } finally { executing.decrementAndGet(); }
    }
    <T> T accessView(Callable<T> action) {
        // View 方法的误用直接抛给脚本，避免 setText 等操作失败后仍被误认为成功。
        executing.incrementAndGet();
        try { checkCancellation(); return ui.call(action, this::cancelled); }
        catch (CancellationException error) { throw error; }
        catch (Exception error) { throw new IllegalStateException(error.getMessage(), error); }
        finally { executing.decrementAndGet(); }
    }
    public boolean isExecuting() { return executing.get() > 0; }

    public boolean requestFloatViewPermission() { return requestFloatViewPermission(10000); }
    public boolean requestFloatViewPermission(long timeoutMs) {
        return operation("requestFloatViewPermission", false, () -> {
            if (timeoutMs < 0 || timeoutMs > 120000) throw new IllegalArgumentException("等待时间必须为 0 到 120000 毫秒");
            if (Settings.canDrawOverlays(context)) return true;
            ui.call(() -> {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.getPackageName()))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent); return null;
            }, this::cancelled);
            // Java 调用方若在主线程申请权限，只打开设置页，不能阻塞系统的授权界面。
            if (Looper.myLooper() == Looper.getMainLooper()) return Settings.canDrawOverlays(context);
            long deadline = SystemClock.uptimeMillis() + timeoutMs;
            while (!Settings.canDrawOverlays(context) && SystemClock.uptimeMillis() < deadline) {
                checkCancellation(); Thread.sleep(Math.min(50, Math.max(1, deadline - SystemClock.uptimeMillis())));
            }
            checkCancellation(); return Settings.canDrawOverlays(context);
        });
    }
    public FloatyView showFloatXml(Object tag, String xml, double x, double y) {
        return operation("showFloatXml", null, () -> {
            String key = tag(tag); int left = coordinate(x), top = coordinate(y);
            FloatyLayout layout = FloatyLayout.parse(readXml(xml));
            return ui.call(() -> show(key, layout.build(context), left, top), this::cancelled);
        });
    }
    public FloatyView showFloatView(Object tag, Object view, double x, double y) {
        return operation("showFloatView", null, () -> {
            String key = tag(tag); int left = coordinate(x), top = coordinate(y);
            View nativeView;
            if (view instanceof FloatyView) {
                FloatyView handle = (FloatyView) view;
                if (handle.owner != this) throw new IllegalArgumentException("不能使用其他脚本会话的 View");
                nativeView = handle.view;
            } else if (view instanceof View) nativeView = (View) view;
            else throw new IllegalArgumentException("view 必须是 floaty 返回的视图或 Android 原生 View");
            return ui.call(() -> show(key, nativeView, left, top), this::cancelled);
        });
    }
    private FloatyView show(String tag, View view, int x, int y) {
        if (!Settings.canDrawOverlays(context)) throw new IllegalStateException("没有悬浮窗权限，请先申请授权");
        Window previous = windows.get(tag);
        if (previous != null && previous.view == view) {
            previous.params.x = x; previous.params.y = y; manager.updateViewLayout(view, previous.params);
            return new FloatyView(this, view);
        }
        if (view.getParent() != null) throw new IllegalArgumentException("View 已经属于其他窗口或布局，请先关闭原窗口");
        if (previous == null && windows.size() >= 16) throw new IllegalStateException("每个会话最多 16 个悬浮窗");
        ViewGroup.LayoutParams size = view.getLayoutParams();
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(size == null ? -2 : size.width, size == null ? -2 : size.height,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT; params.x = x; params.y = y;
        params.setFitInsetsTypes(0); params.setTitle("XpAuto-script:" + tag);
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
        // 先成功添加新窗口，再移除旧窗口；坏 XML 或 addView 失败不会丢失原来的窗口。
        manager.addView(view, params);
        if (previous != null) remove(previous);
        windows.put(tag, new Window(view, params));
        return new FloatyView(this, view);
    }
    private Window window(String tag) {
        if (!Settings.canDrawOverlays(context)) { removeAll(); return null; }
        return windows.get(tag);
    }
    private interface Change { void apply(Window window); }
    private boolean update(String name, Object tag, Change change) {
        return operation(name, false, () -> {
            String key = tag(tag);
            return ui.call(() -> {
                Window window = window(key); if (window == null) return false;
                change.apply(window); manager.updateViewLayout(window.view, window.params); return true;
            }, this::cancelled);
        });
    }
    public boolean updateX(Object tag, double x) { return update("updateX", tag, window -> window.params.x = coordinate(x)); }
    public boolean updateY(Object tag, double y) { return update("updateY", tag, window -> window.params.y = coordinate(y)); }
    public boolean updateSize(Object tag, double width, double height) {
        return update("updateSize", tag, window -> {
            int w = size(width), h = size(height); window.params.width = w; window.params.height = h;
        });
    }
    public boolean touchable(Object tag, boolean touchable) {
        return update("touchable", tag, window -> {
            window.params.flags = flag(window.params.flags, WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, !touchable);
            // Android 12+ 对穿透到其他应用的触摸有遮挡透明度限制。
            window.params.alpha = touchable ? 1f : .7f;
        });
    }
    public boolean focusable(Object tag, boolean focusable) {
        return update("focusable", tag, window -> {
            window.params.flags = flag(window.params.flags, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, !focusable);
            if (!focusable) context.getSystemService(InputMethodManager.class).hideSoftInputFromWindow(window.view.getWindowToken(), 0);
        });
    }
    public boolean close(Object tag) {
        return operation("close", false, () -> {
            String key = tag(tag);
            return ui.call(() -> { Window value = windows.remove(key); if (value == null) return false; remove(value); return true; }, this::cancelled);
        });
    }
    private int value(Object tag, String name) {
        return operation(name, -1, () -> {
            String key = tag(tag);
            return ui.call(() -> {
                Window window = window(key); if (window == null) return -1;
                if (name.equals("getX")) return window.params.x;
                if (name.equals("getY")) return window.params.y;
                // addView 后还未走完首帧布局时，主动测量，保证立即读取尺寸也有意义。
                android.graphics.Rect screen = manager.getMaximumWindowMetrics().getBounds();
                int width = window.params.width, height = window.params.height;
                int wSpec = View.MeasureSpec.makeMeasureSpec(width >= 0 ? width : screen.width(), width == -2 ? View.MeasureSpec.AT_MOST : View.MeasureSpec.EXACTLY);
                int hSpec = View.MeasureSpec.makeMeasureSpec(height >= 0 ? height : screen.height(), height == -2 ? View.MeasureSpec.AT_MOST : View.MeasureSpec.EXACTLY);
                window.view.measure(wSpec, hSpec);
                return name.equals("getWidth") ? window.view.getMeasuredWidth() : window.view.getMeasuredHeight();
            }, this::cancelled);
        });
    }
    public int getX(Object tag) { return value(tag, "getX"); }
    public int getY(Object tag) { return value(tag, "getY"); }
    public int getWidth(Object tag) { return value(tag, "getWidth"); }
    public int getHeight(Object tag) { return value(tag, "getHeight"); }

    private String readXml(String xml) throws Exception {
        if (xml == null || xml.trim().isEmpty()) throw new IllegalArgumentException("XML 内容或路径不能为空");
        if (xml.trim().startsWith("<")) return xml;
        File path = new File(xml);
        // 相对路径先查手机应用私有 files 目录，再查 APK assets；不会读取电脑工作区。
        if (!path.isAbsolute()) path = new File(context.getFilesDir(), xml);
        try (InputStream input = path.isFile() ? new FileInputStream(path) : context.getAssets().open(xml);
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int count;
            while ((count = input.read(buffer)) != -1) {
                checkCancellation();
                if (bytes.size() + count > FloatyLayout.MAX_XML_BYTES) throw new IllegalArgumentException("XML 文件不能超过 64 KiB");
                bytes.write(buffer, 0, count);
            }
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }
    }
    private static String tag(Object value) {
        if (!(value instanceof String) && !(value instanceof Number)) throw new IllegalArgumentException("tag 必须为字符串或数字");
        if (value instanceof Number && !Double.isFinite(((Number) value).doubleValue())) throw new IllegalArgumentException("tag 数字必须有限");
        String tag = value.toString();
        if (tag.trim().isEmpty() || tag.length() > 128) throw new IllegalArgumentException("tag 必须为 1 到 128 个字符");
        return tag;
    }
    private static int coordinate(double value) {
        if (!Double.isFinite(value) || value != Math.rint(value) || Math.abs(value) > 100000) throw new IllegalArgumentException("坐标必须为 -100000 到 100000 之间的整数像素");
        return (int) value;
    }
    private static int size(double value) {
        if (!Double.isFinite(value) || value != Math.rint(value) || value < -2 || value > 16000) throw new IllegalArgumentException("尺寸必须为 0 到 16000 像素，或 -1 / -2");
        return (int) value;
    }
    private static int flag(int flags, int flag, boolean enabled) { return enabled ? flags | flag : flags & ~flag; }
    private void remove(Window window) {
        try { manager.removeViewImmediate(window.view); }
        catch (IllegalArgumentException ignored) { /* 权限撤回或系统已移除窗口时仍完成本地清理。 */ }
    }
    private void removeAll() { for (Window window : windows.values()) remove(window); windows.clear(); }
    /** 正常结束或异常结束都清理本次脚本窗口；控制台仍能继续创建预览窗口。 */
    public void clearWindows() { ui.post(this::removeAll); }
    public void cancel() { cancelled.set(true); clearWindows(); }
    public void resetCancellation() { if (!disposed.get()) cancelled.set(false); }
    public void dispose() { disposed.set(true); cancel(); }
}
