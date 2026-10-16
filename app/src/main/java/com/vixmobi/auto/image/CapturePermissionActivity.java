package com.vixmobi.auto.image;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/** 仅承载系统截图授权，授权结果只供当前请求使用，不保存或复用令牌。 */
public final class CapturePermissionActivity extends Activity {
  static final class Request {
    final String id = java.util.UUID.randomUUID().toString();
    final ProjectionCapture owner;
    final CountDownLatch done = new CountDownLatch(1);
    volatile boolean cancelled, granted;
    volatile String error = "截图授权被拒绝";
    volatile CapturePermissionActivity activity;

    Request(ProjectionCapture owner) {
      this.owner = owner;
    }

    void complete(boolean success, String message) {
      granted = success;
      error = message;
      done.countDown();
      CapturePermissionActivity current = activity;
      if (current != null)
        current.runOnUiThread(
            () -> {
              // 仅 finish 自己可能留下尚未响应的系统授权子页面，导致下一次授权叠出旧弹窗。
              current.finishActivity(1);
              current.finish();
            });
    }
  }

  static final AtomicReference<Request> pending = new AtomicReference<>();
  private Request request;
  private boolean handedToService;

  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    request = pending.get();
    if (request == null
        || request.cancelled
        || !request.id.equals(getIntent().getStringExtra("requestId"))) {
      finish();
      return;
    }
    request.activity = this;
    if (state != null) return;
    try {
      MediaProjectionManager manager = getSystemService(MediaProjectionManager.class);
      // 自动化坐标按整屏计算；Android 14 起显式请求默认屏幕，最终范围仍由系统决定。
      Intent intent =
          Build.VERSION.SDK_INT >= 34
              ? manager.createScreenCaptureIntent(
                  MediaProjectionConfig.createConfigForDefaultDisplay())
              : manager.createScreenCaptureIntent();
      startActivityForResult(intent, 1);
    } catch (RuntimeException error) {
      request.complete(false, error.getMessage());
    }
  }

  @Override
  protected void onActivityResult(int code, int result, Intent data) {
    super.onActivityResult(code, result, data);
    if (code != 1 || request == null) return;
    if (request.cancelled || pending.get() != request || result != RESULT_OK || data == null) {
      request.complete(false, "截图授权被拒绝或已取消");
      return;
    }
    try {
      // 先由服务进入 mediaProjection 前台状态，再使用授权令牌创建投影。
      startForegroundService(
          new Intent(this, CaptureService.class)
              .putExtra("requestId", request.id)
              .putExtra("resultCode", result)
              .putExtra("data", data));
      handedToService = true;
    } catch (RuntimeException error) {
      request.complete(false, error.getMessage());
    }
  }

  @Override
  protected void onDestroy() {
    if (request != null && request.activity == this) {
      request.activity = null;
      if (!isChangingConfigurations() && !handedToService && request.done.getCount() != 0)
        request.complete(false, "截图授权页面已关闭");
    }
    super.onDestroy();
  }
}
