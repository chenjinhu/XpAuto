package com.vixmobi.auto.tools;

import static com.vixmobi.auto.Constant.tag;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;
import android.view.accessibility.AccessibilityManager;

import androidx.core.content.ContextCompat;

import java.util.List;

public class Enviroment {
    public static String TAG = tag;
    public static boolean checkAppInstalled(Context context, String pkgName){

        if (pkgName.isEmpty()) {
            return false;
        }
        PackageManager packageManager = context.getPackageManager();
        List<PackageInfo> pkgInfos = packageManager.getInstalledPackages(0);
        if (pkgInfos != null) {
            for (int i = 0; i < pkgInfos.size(); i++) {
                String pkg = pkgInfos.get(i).packageName;
                Log.d(TAG, "checkAppInstalled: " + pkg );

                if (pkg.equals(pkgName)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean checkAccessibilityEnabled(Context context){
        if (context == null) {
            return false;
        }

        // 检查AccessibilityService是否开启
        AccessibilityManager am = (AccessibilityManager) context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE);
        boolean isAccessibilityEnabled_flag = am.isEnabled();
        return isAccessibilityEnabled_flag;

    }




}
