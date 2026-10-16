package com.vixmobi.auto.image;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.IBinder;
import com.vixmobi.auto.devtools.DeveloperActivity;

/** 截图期间显示系统前台通知；通知中的停止操作会立即释放投影。 */
public final class CaptureService extends Service {
  private ProjectionCapture owner;

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent != null && "stop".equals(intent.getAction())) {
      if (owner != null) owner.release();
      stopSelf();
      return START_NOT_STICKY;
    }
    CapturePermissionActivity.Request request = CapturePermissionActivity.pending.get();
    if (intent == null
        || request == null
        || request.cancelled
        || !request.id.equals(intent.getStringExtra("requestId"))) {
      // 先核对请求归属，迟到的旧 Intent 不能影响下一次授权。
      if (owner == null) stopSelf(startId);
      return START_NOT_STICKY;
    }
    try {
      NotificationManager manager = getSystemService(NotificationManager.class);
      manager.createNotificationChannel(
          new NotificationChannel(
              "xpauto-capture", "XpAuto 屏幕截图", NotificationManager.IMPORTANCE_LOW));
      PendingIntent open =
          PendingIntent.getActivity(
              this, 0, new Intent(this, DeveloperActivity.class), PendingIntent.FLAG_IMMUTABLE);
      PendingIntent stop =
          PendingIntent.getService(
              this,
              1,
              new Intent(this, CaptureService.class).setAction("stop"),
              PendingIntent.FLAG_IMMUTABLE);
      Notification notification =
          new Notification.Builder(this, "xpauto-capture")
              .setSmallIcon(android.R.drawable.ic_menu_camera)
              .setContentTitle("XpAuto 正在获取屏幕截图")
              .setContentText("可在这里停止屏幕共享")
              .setContentIntent(open)
              .setOngoing(true)
              .addAction(new Notification.Action.Builder(null, "停止截图", stop).build())
              .build();
      startForeground(9230, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
      owner = request.owner;
      boolean success =
          owner.start(
              request,
              intent.getIntExtra("resultCode", 0),
              intent.getParcelableExtra("data"),
              this);
      request.complete(success, success ? "" : owner.getError());
      if (!success) stopSelf();
    } catch (RuntimeException error) {
      if (request != null) request.complete(false, error.toString());
      if (owner != null) owner.release();
      stopSelf();
    }
    return START_NOT_STICKY;
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public void onDestroy() {
    if (owner != null) owner.serviceDestroyed(this);
    stopForeground(STOP_FOREGROUND_REMOVE);
    super.onDestroy();
  }
}
