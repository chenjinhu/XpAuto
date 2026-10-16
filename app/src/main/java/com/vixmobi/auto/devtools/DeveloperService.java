package com.vixmobi.auto.devtools;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import com.vixmobi.auto.Constant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** 首页在悬浮窗已授权时开启此服务，统一管理调试连接、悬浮球和运行控制。 */
public final class DeveloperService extends Service {
    private static final String CHANNEL = "xpauto-development";
    public static volatile boolean enabled;
    public static volatile String status = "运行环境未开启";
    public static volatile String executionState = "idle";
    private static final LogBuffer logs = new LogBuffer();
    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final Handler handler = new Handler(Looper.getMainLooper());
    private DeveloperServer server;
    private FloatingControls overlay;
    private FloatingLogWindow logWindow;
    // 会话释放可能晚于完成通知；轻量检查保证“运行”按钮在会话释放后及时恢复。
    private final Runnable refreshControls = new Runnable() {
        @Override public void run() {
            if (server != null && overlay != null && enabled) {
                executionState = server.stateName();
                overlay.setLogsVisible(logWindow != null);
                overlay.update(executionState, server.canRunLastScript(), status);
                if (logWindow != null) logWindow.update(consoleText());
                handler.postDelayed(this, 250);
            }
        }
    };

    public static String consoleText() { return logs.text(); }
    public static void clearLogs(int lines) { logs.clear(lines); }
    private static void appendLog(String message) { logs.append(LocalTime.now().format(LOG_TIME) + "  " + message); }

    private void closeLogs() {
        if (logWindow != null) { logWindow.close(); logWindow = null; }
        if (overlay != null) overlay.setLogsVisible(false);
    }

    private void toggleLogs() {
        if (logWindow != null) { closeLogs(); return; }
        try {
            logWindow = new FloatingLogWindow(this, logs::clear, this::closeLogs);
            logWindow.update(consoleText());
            if (overlay != null) overlay.setLogsVisible(true);
            // 退出当前按钮的触摸分发后再调整层级，让悬浮球始终位于日志窗口上方。
            handler.post(() -> { if (enabled && overlay != null && logWindow != null) overlay.bringToFront(); });
        } catch (RuntimeException error) {
            closeLogs();
            appendLog("日志悬浮窗无法显示，请检查权限：" + error.getMessage());
        }
    }

    @Override public void onCreate() {
        super.onCreate();
        Constant.context = getApplicationContext();
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "XpAuto 运行环境", NotificationManager.IMPORTANCE_LOW));
        startForeground(9229, notification("正在启动"));
        // 显示悬浮球只需要悬浮窗权限，脚本运行入口仍检查全部必要权限。
        if (!AutomationPermissions.overlay(this)) {
            status = "请先授予悬浮窗权限";
            appendLog(status);
            stopSelf();
            return;
        }
        enabled = true;
        status = "正在启动运行环境";
        server = new DeveloperServer(this, message -> {
            status = message;
            handler.post(() -> {
                if (!enabled) return;
                executionState = server.stateName();
                manager.notify(9229, notification(message));
                if (overlay != null) overlay.update(executionState, server.canRunLastScript(), message);
            });
        }, DeveloperService::appendLog);
        server.start();
        try {
            overlay = new FloatingControls(this, this::action);
            handler.post(refreshControls);
        } catch (RuntimeException error) {
            appendLog("悬浮窗无法显示，请检查权限：" + error.getMessage());
            stopSelf();
        }
    }

    private Notification notification(String text) {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, DeveloperActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_upload_done)
                .setContentTitle("XpAuto").setContentText(text).setContentIntent(open).setOngoing(true).build();
    }

    // UI 线程只分发操作；Inspector 请求交给工作线程，避免暂停时阻塞界面。
    private void action(String action) {
        if ("end".equals(action)) { stopSelf(); return; }
        if ("logs".equals(action)) { toggleLogs(); return; }
        if ("panel".equals(action)) {
            startActivity(new Intent(this, DeveloperActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            return;
        }
        if (server == null) return;
        if ("run".equals(action)) new Thread(server::runLastScript, "XpAuto-local-run").start();
        if ("pause".equals(action)) new Thread(server::pauseOrResumeScript, "XpAuto-pause-resume").start();
        if ("resume".equals(action)) new Thread(server::resumeScript, "XpAuto-resume").start();
        if ("stop".equals(action)) new Thread(server::stopScript, "XpAuto-stop").start();
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) action(intent.getAction());
        return START_NOT_STICKY;
    }
    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        if (overlay != null) overlay.onConfigurationChanged(configuration);
        if (logWindow != null) logWindow.layout();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() {
        com.vixmobi.auto.logging.ToastPresenter.cancelAll();
        enabled = false;
        status = "运行环境已关闭";
        executionState = "idle";
        if (overlay != null) overlay.close();
        closeLogs();
        handler.removeCallbacksAndMessages(null);
        DeveloperServer closing = server;
        if (closing != null) new Thread(closing::shutdown, "XpAuto-shutdown").start();
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
}
