package com.vixmobi.auto.permissions;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.provider.Settings;
import com.vixmobi.auto.MyAccessibilityService;
import com.vixmobi.auto.runtime.ScriptBlocking;
import com.vixmobi.auto.toolbox.UtilityApi;
import com.vixmobi.auto.utils.ShellApi;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;

/** 普通权限交给系统弹窗；两个自允许接口只在脚本显式调用时使用 Root。 */
public final class PermissionApi {
  private final Context context;
  private final ShellApi shell;

  public PermissionApi(Context context, ShellApi shell) {
    this.context = context;
    this.shell = shell;
  }

  public boolean requestRuntimePermissionJson(String json, long timeout) throws Exception {
    if (timeout < 0 || timeout > Integer.MAX_VALUE)
      throw new IllegalArgumentException("权限超时必须为非负毫秒数");
    JSONArray input = new JSONArray(json);
    Set<String> missing = new LinkedHashSet<>();
    Set<String> declared =
        new HashSet<>(
            Arrays.asList(
                context
                    .getPackageManager()
                    .getPackageInfo(context.getPackageName(), PackageManager.GET_PERMISSIONS)
                    .requestedPermissions));
    for (int i = 0; i < input.length(); i++) {
      String name = input.getString(i);
      if (!declared.contains(name)) return false;
      if (context.checkSelfPermission(name) == PackageManager.PERMISSION_GRANTED) continue;
      PermissionInfo info = context.getPackageManager().getPermissionInfo(name, 0);
      if ((info.protectionLevel & PermissionInfo.PROTECTION_MASK_BASE)
          != PermissionInfo.PROTECTION_DANGEROUS) return false;
      missing.add(name);
    }
    if (missing.isEmpty()) return true;
    if (timeout == 0) return false;
    return ScriptBlocking.call(
        () -> {
          RuntimePermissionActivity.Request request =
              new RuntimePermissionActivity.Request(missing.toArray(new String[0]));
          if (!RuntimePermissionActivity.pending.compareAndSet(null, request)) return false;
          try {
            context.startActivity(
                new Intent(context, RuntimePermissionActivity.class)
                    .putExtra("requestId", request.id)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            long end = System.nanoTime() + timeout * 1000000L;
            while (!request.done.await(25, TimeUnit.MILLISECONDS)) {
              ScriptBlocking.checkCancelled();
              if (System.nanoTime() > end) return false;
            }
            return request.granted;
          } finally {
            RuntimePermissionActivity.pending.compareAndSet(request, null);
            RuntimePermissionActivity activity = request.activity;
            if (activity != null) activity.runOnUiThread(activity::finish);
          }
        });
  }

  public boolean tryGetProjectionPermission() {
    if (projectionAllowed()) return true;
    shell.ROOT("appops set " + UtilityApi.quote(context.getPackageName()) + " PROJECT_MEDIA allow");
    if (shell.getLastResult().code != 0) return false;
    return projectionAllowed();
  }

  private boolean projectionAllowed() {
    try {
      return context.getSystemService(android.app.AppOpsManager.class)
          .unsafeCheckOpNoThrow("android:project_media", android.os.Process.myUid(), context.getPackageName())
          == android.app.AppOpsManager.MODE_ALLOWED;
    } catch (IllegalArgumentException | SecurityException ignored) {
      return false;
    }
  }

  public boolean tryGetAccStartupPermission() {
    String permission = "android.permission.WRITE_SECURE_SETTINGS";
    if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
      shell.ROOT("pm grant " + UtilityApi.quote(context.getPackageName()) + " " + permission);
      if (shell.getLastResult().code != 0
          || context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) return false;
    }
    String service = new ComponentName(context, MyAccessibilityService.class).flattenToString();
    // 合并已有服务，不能覆盖用户启用的其他无障碍服务。
    String enabled =
        Settings.Secure.getString(
            context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
    Set<String> services = new LinkedHashSet<>();
    if (enabled != null && !enabled.isEmpty()) services.addAll(Arrays.asList(enabled.split(":")));
    services.add(service);
    // 授权保留后可直接写入；下一次调用不再要求 Root，并尊重其他已启用服务。
    return Settings.Secure.putString(context.getContentResolver(),
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, String.join(":", services))
        && Settings.Secure.putInt(context.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
  }
}
