package com.vixmobi.auto.devtools;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.vixmobi.auto.Constant;
import com.vixmobi.auto.R;

/** 自动化首页：设备信息抽屉、启动权限检查、运行控制和实时日志。 */
public final class DeveloperActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView status, accessibility, overlay, console, ipAddress;
    private Button toggle, pause;
    private DrawerLayout drawer;
    private String consoleSnapshot = "";
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            accessibility.setText(AutomationPermissions.accessibility(DeveloperActivity.this)
                    ? "✓ 无障碍服务已开启" : "○ 无障碍服务未开启 · 用于查找控件、点击和手势");
            overlay.setText(AutomationPermissions.overlay(DeveloperActivity.this)
                    ? "✓ 悬浮窗已授权" : "○ 悬浮窗未授权 · 用于在其他应用上控制脚本");
            status.setText(DeveloperService.status);
            toggle.setText(DeveloperService.enabled ? "关闭运行环境" : "开启运行环境与悬浮窗");
            String state = DeveloperService.executionState;
            pause.setText("paused".equals(state) ? "继续" : "暂停");
            pause.setEnabled(DeveloperService.enabled && ("running".equals(state) || "paused".equals(state)));
            String text = DeveloperService.consoleText();
            if (!text.equals(consoleSnapshot)) {
                consoleSnapshot = text;
                console.setText(text.isEmpty() ? "脚本日志、错误和运行结果将显示在这里。" : text);
            }
            handler.postDelayed(this, 500);
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Constant.context = getApplicationContext();
        setTitle("XpAuto");
        getWindow().setStatusBarColor(UiStyle.BLUE);
        getWindow().setNavigationBarColor(UiStyle.SURFACE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        drawer = new DrawerLayout(this);
        drawer.setScrimColor(0x660F1D33);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(UiStyle.SURFACE);
        drawer.addView(page, new DrawerLayout.LayoutParams(-1, -1));
        buildToolbar(page);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(6), dp(20), dp(24));
        scroll.addView(root);

        LinearLayout permissions = card(root);
        text(permissions, "启动准备", 20).setTypeface(null, Typeface.BOLD);
        accessibility = text(permissions, "检测无障碍服务…", 14);
        button(permissions, "设置无障碍服务", this::openAccessibility);
        overlay = text(permissions, "检测悬浮窗权限…", 14);
        button(permissions, "设置悬浮窗权限", this::openOverlayPermission);
        text(permissions, "授予悬浮窗权限后自动显示悬浮球，运行脚本前检查无障碍权限。", 12).setTextColor(UiStyle.MUTED);

        LinearLayout execution = card(root);
        text(execution, "运行与连接", 20).setTypeface(null, Typeface.BOLD);
        status = text(execution, DeveloperService.status, 15);
        toggle = button(execution, "开启运行环境与悬浮窗", () -> {
            Intent service = new Intent(this, DeveloperService.class);
            if (DeveloperService.enabled) stopService(service);
            else if (!AutomationPermissions.overlay(this)) openOverlayPermission();
            else {
                if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 23);
                }
                startForegroundService(service);
            }
        });
        toggle.setBackgroundTintList(android.content.res.ColorStateList.valueOf(UiStyle.BLUE));
        toggle.setTextColor(Color.WHITE);
        button(execution, "运行上次同步的脚本", () -> action("run"));
        LinearLayout actions = new LinearLayout(this);
        execution.addView(actions);
        pause = button(actions, "暂停", () -> action("pause"));
        button(actions, "停止脚本", () -> action("stop"));
        text(execution, "点击悬浮球展开控制，拖动可移动位置。USB 连接后，在 VS Code 中按 F5 运行与调试脚本。", 13)
                .setTextColor(UiStyle.MUTED);

        LinearLayout logCard = card(root);
        text(logCard, "运行控制台", 20).setTypeface(null, Typeface.BOLD);
        console = text(logCard, "脚本日志、错误和运行结果将显示在这里。", 12);
        console.setTypeface(Typeface.MONOSPACE);
        console.setTextIsSelectable(true);
        buildDrawer();
        setContentView(drawer);
        if (savedInstanceState != null && savedInstanceState.getBoolean("drawerOpen")) {
            drawer.openDrawer(GravityCompat.START, false);
        }
    }

    private void buildToolbar(LinearLayout page) {
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(8), 0, dp(20), 0);
        toolbar.setBackgroundColor(UiStyle.BLUE);
        page.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(64)));
        ImageButton menu = new ImageButton(this);
        menu.setImageResource(R.drawable.xpauto_ic_menu);
        menu.setImageTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        menu.setBackgroundColor(Color.TRANSPARENT);
        menu.setPadding(dp(12), dp(12), dp(12), dp(12));
        menu.setContentDescription("打开菜单");
        toolbar.addView(menu, new LinearLayout.LayoutParams(dp(48), dp(48)));
        menu.setOnClickListener(view -> drawer.openDrawer(GravityCompat.START));
        LinearLayout title = new LinearLayout(this);
        title.setOrientation(LinearLayout.VERTICAL);
        title.setPadding(dp(10), 0, 0, 0);
        toolbar.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView name = text(title, "XpAuto", 21);
        name.setTypeface(null, Typeface.BOLD);
        name.setTextColor(Color.WHITE);
        name.setPadding(0, 0, 0, 0);
        TextView subtitle = text(title, "自动化工作台", 11);
        subtitle.setTextColor(0xDDE8EFFF);
        subtitle.setPadding(0, dp(1), 0, 0);
    }

    private void buildDrawer() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.WHITE);
        int width = Math.min(dp(320), getResources().getDisplayMetrics().widthPixels - dp(56));
        DrawerLayout.LayoutParams size = new DrawerLayout.LayoutParams(width, -1);
        size.gravity = GravityCompat.START;
        drawer.addView(scroll, size);
        drawer.setDrawerElevation(dp(16));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(28), dp(24), dp(24));
        scroll.addView(content);
        ImageView icon = UiStyle.appIcon(this, 64);
        content.addView(icon, new LinearLayout.LayoutParams(dp(64), dp(64)));
        TextView name = text(content, "XpAuto", 25);
        name.setPadding(0, dp(14), 0, dp(2));
        name.setTypeface(null, Typeface.BOLD);
        text(content, "设备与应用", 13).setTextColor(UiStyle.MUTED);
        divider(content);
        ipAddress = info(content, "本机 IP", DeviceInfo.localIp(this));
        info(content, "应用版本", DeviceInfo.version(this));
        TextView packageName = info(content, "包名 · 长按复制", getPackageName());
        packageName.setOnLongClickListener(view -> {
            getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("包名", getPackageName()));
            Toast.makeText(this, "包名已复制", Toast.LENGTH_SHORT).show();
            return true;
        });
        divider(content);
        placeholder(content, "系统设置", R.drawable.xpauto_ic_settings);
        placeholder(content, "导出运行日志", R.drawable.xpauto_ic_export);
        drawer.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override public void onDrawerOpened(View drawerView) {
                ipAddress.setText(DeviceInfo.localIp(DeveloperActivity.this));
            }
        });
    }

    private TextView info(LinearLayout parent, String label, String value) {
        TextView title = text(parent, label, 12);
        title.setTextColor(UiStyle.MUTED);
        title.setPadding(0, dp(12), 0, dp(2));
        TextView detail = text(parent, value, 15);
        detail.setPadding(0, 0, 0, dp(8));
        detail.setTextIsSelectable(true);
        return detail;
    }
    private void divider(LinearLayout parent) {
        View line = new View(this);
        line.setBackgroundColor(UiStyle.BORDER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(1));
        params.topMargin = dp(18); params.bottomMargin = dp(12);
        parent.addView(line, params);
    }
    /** 仅绘制待开放入口，不绑定设置或导出行为。 */
    private void placeholder(LinearLayout parent, String title, int resource) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setContentDescription(title + "，暂未开放");
        row.setEnabled(false);
        parent.addView(row, new LinearLayout.LayoutParams(-1, dp(58)));
        ImageView icon = new ImageView(this);
        icon.setImageResource(resource);
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(UiStyle.MUTED));
        row.addView(icon, new LinearLayout.LayoutParams(dp(22), dp(22)));
        TextView label = new TextView(this);
        label.setText(title);
        label.setTextSize(15);
        label.setTextColor(UiStyle.MUTED);
        label.setPadding(dp(14), 0, 0, 0);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        TextView badge = new TextView(this);
        badge.setText("待开放");
        badge.setTextSize(10);
        badge.setTextColor(UiStyle.MUTED);
        badge.setBackground(UiStyle.shape(this, UiStyle.SURFACE, 5, false));
        badge.setPadding(dp(5), dp(3), dp(5), dp(3));
        row.addView(badge);
    }

    private void action(String action) {
        // 只有开始脚本需要完整权限，暂停和停止不能被权限撤销拦住。
        if ("run".equals(action) && !checkPermissions()) return;
        if (!"run".equals(action) && !DeveloperService.enabled) return;
        startForegroundService(new Intent(this, DeveloperService.class).setAction(action));
    }
    private boolean checkPermissions() {
        String missing = AutomationPermissions.missing(this);
        if (missing.isEmpty()) return true;
        new AlertDialog.Builder(this).setTitle("运行脚本前需要授权")
                .setMessage("尚未开启：" + missing + "。\n授权后返回 XpAuto，再点击运行。")
                .setPositiveButton("去设置", (dialog, which) -> {
                    if (!AutomationPermissions.accessibility(this)) openAccessibility();
                    else openOverlayPermission();
                }).setNegativeButton("暂不启动", null).show();
        return false;
    }
    private void openAccessibility() { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
    private void openOverlayPermission() {
        startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
    }
    private int dp(int value) { return UiStyle.dp(this, value); }
    private LinearLayout card(LinearLayout parent) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(UiStyle.shape(this, Color.WHITE, 18, false));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(16);
        parent.addView(card, params);
        return card;
    }
    private TextView text(LinearLayout parent, String label, int size) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextColor(UiStyle.INK);
        view.setTextSize(size);
        view.setPadding(0, dp(4), 0, dp(6));
        parent.addView(view);
        return view;
    }
    private Button button(LinearLayout parent, String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        parent.addView(button);
        button.setOnClickListener(view -> action.run());
        return button;
    }
    @Override public void onBackPressed() {
        if (drawer.isDrawerOpen(GravityCompat.START)) drawer.closeDrawer(GravityCompat.START);
        else super.onBackPressed();
    }
    @Override protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean("drawerOpen", drawer.isDrawerOpen(GravityCompat.START));
        super.onSaveInstanceState(outState);
    }
    @Override public void onResume() {
        super.onResume();
        // 首次打开和从授权页返回都自动显示；不放进定时刷新，避免“结束”后立即重启。
        if (AutomationPermissions.overlay(this) && !DeveloperService.enabled) {
            startForegroundService(new Intent(this, DeveloperService.class));
        }
        if (drawer.isDrawerOpen(GravityCompat.START)) ipAddress.setText(DeviceInfo.localIp(this));
        handler.post(refresh);
    }
    @Override public void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
}
