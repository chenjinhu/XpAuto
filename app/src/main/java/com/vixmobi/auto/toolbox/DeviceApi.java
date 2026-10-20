package com.vixmobi.auto.toolbox;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.WindowManager;
import com.vixmobi.auto.runtime.ScriptBlocking;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.*;
import java.util.*;

/** 读取当前设备真实信息；应用列表通过系统包管理器查询，不修改设备设置。 */
public final class DeviceApi {
    private final Context context;
    public DeviceApi(Context context) { this.context = context.getApplicationContext(); }
    private Rect screen() { return context.getSystemService(WindowManager.class).getMaximumWindowMetrics().getBounds(); }
    public int getScreenWidth() { return screen().width(); }
    public int getScreenHeight() { return screen().height(); }
    public float getDensity() { return context.getResources().getDisplayMetrics().density; }
    public int getDensityDpi() { return context.getResources().getDisplayMetrics().densityDpi; }
    public String getBrand() { return Build.BRAND; }
    public String getModel() { return Build.MODEL; }
    public String getManufacturer() { return Build.MANUFACTURER; }
    public int getSdkInt() { return Build.VERSION.SDK_INT; }
    public String getOSVersion() { return Build.VERSION.RELEASE; }
    public String getAndroidId() { return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID); }
    public boolean isScreenOn() { return context.getSystemService(PowerManager.class).isInteractive(); }
    public int getBrightness() { return Settings.System.getInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, -1); }
    public int getBattery() { return context.getSystemService(BatteryManager.class).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY); }
    public boolean isCharging() {
        Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        return battery != null && battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
    }
    public String getIpAddress() {
        try {
            for (NetworkInterface iface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!iface.isUp() || iface.isLoopback()) continue;
                for (InetAddress address : Collections.list(iface.getInetAddresses())) {
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) return address.getHostAddress();
                }
            }
        } catch (Exception ignored) { }
        return "";
    }
    /** 全量枚举在阻塞段执行，避免应用较多时占住 V8，结果按包名稳定排序。 */
    public String installedAppsJson(boolean includeSystem) {
        return ScriptBlocking.call(() -> {
            PackageManager pm = context.getPackageManager();
            List<ApplicationInfo> installed = pm.getInstalledApplications(0);
            installed.sort(Comparator.comparing(info -> info.packageName));
            JSONArray result = new JSONArray();
            for (ApplicationInfo info : installed) {
                ScriptBlocking.checkCancelled();
                boolean system = (info.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
                if (system && !includeSystem) continue;
                try {
                    PackageInfo pkg = pm.getPackageInfo(info.packageName, 0);
                    result.put(new JSONObject().put("packageName", info.packageName)
                        .put("appName", String.valueOf(pm.getApplicationLabel(info)))
                        .put("versionName", pkg.versionName == null ? "" : pkg.versionName)
                        .put("versionCode", pkg.getLongVersionCode()).put("system", system)
                        .put("enabled", info.enabled).put("launchable", pm.getLaunchIntentForPackage(info.packageName) != null)
                        .put("firstInstallTime", pkg.firstInstallTime).put("lastUpdateTime", pkg.lastUpdateTime));
                } catch (PackageManager.NameNotFoundException ignored) { /* 查询期间被卸载的包跳过。 */ }
            }
            return result.toString();
        });
    }
}
