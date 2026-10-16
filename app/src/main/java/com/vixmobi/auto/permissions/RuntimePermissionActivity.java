package com.vixmobi.auto.permissions;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/** 只承载系统权限弹窗，不自动点击授权；每次请求用独立对象匹配结果。 */
public final class RuntimePermissionActivity extends Activity {
  static final class Request {
    final String id = java.util.UUID.randomUUID().toString();
    final String[] permissions;
    final CountDownLatch done = new CountDownLatch(1);
    volatile boolean granted;
    volatile RuntimePermissionActivity activity;

    Request(String[] permissions) {
      this.permissions = permissions;
    }
  }

  static final AtomicReference<Request> pending = new AtomicReference<>();
  private Request request;

  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    request = pending.get();
    if (request == null || !request.id.equals(getIntent().getStringExtra("requestId"))) {
      finish();
      return;
    }
    request.activity = this;
    if (state == null) requestPermissions(request.permissions, 1);
  }

  @Override
  public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
    super.onRequestPermissionsResult(code, permissions, results);
    if (request != null) {
      request.granted = results.length == request.permissions.length;
      for (int result : results) request.granted &= result == PackageManager.PERMISSION_GRANTED;
      request.done.countDown();
    }
    finish();
  }

  @Override
  protected void onDestroy() {
    if (request != null && !isChangingConfigurations()) request.done.countDown();
    super.onDestroy();
  }
}
