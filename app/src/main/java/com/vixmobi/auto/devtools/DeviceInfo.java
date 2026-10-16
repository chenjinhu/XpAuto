package com.vixmobi.auto.devtools;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;

import java.net.Inet4Address;
import java.net.InetAddress;

/** 从系统读取菜单信息；不缓存 IP，打开抽屉时重新查询当前网络。 */
final class DeviceInfo {
    static String version(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.versionName + " (" + info.getLongVersionCode() + ")";
        } catch (PackageManager.NameNotFoundException ignored) { return "未知版本"; }
    }

    static String localIp(Context context) {
        ConnectivityManager manager = context.getSystemService(ConnectivityManager.class);
        if (manager == null) return "未连接网络";
        try {
            // 优先显示可用于局域网连接的真实地址，避免把 VPN 隧道或回环地址当成本机 IP。
            for (Network network : manager.getAllNetworks()) {
                NetworkCapabilities caps = manager.getNetworkCapabilities(network);
                if (caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))) {
                    String address = address(manager.getLinkProperties(network));
                    if (address != null) return address;
                }
            }
            Network active = manager.getActiveNetwork();
            String address = active == null ? null : address(manager.getLinkProperties(active));
            return address == null ? "未连接网络" : address;
        } catch (SecurityException ignored) { return "暂无法读取"; }
    }
    private static String address(LinkProperties properties) {
        if (properties == null) return null;
        String ipv6 = null;
        for (LinkAddress link : properties.getLinkAddresses()) {
            InetAddress address = link.getAddress();
            if (address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isAnyLocalAddress()) continue;
            if (address instanceof Inet4Address) return address.getHostAddress();
            if (ipv6 == null) ipv6 = address.getHostAddress();
        }
        return ipv6;
    }
    private DeviceInfo() { }
}
