package com.vixmobi.auto.node;
import static com.vixmobi.auto.Constant.*;
import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Rect;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.content.pm.PackageManager;
import android.view.accessibility.AccessibilityNodeInfo;

import com.vixmobi.auto.Constant;
import com.vixmobi.auto.devplugin.LogWebsocketServer;
import com.vixmobi.auto.utils.ExceptionUtil;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class AccUtils extends AccessibilityService {
    public static AccessibilityService AccessibilityHelper;
    private static final String TAG = tag;

    public AccUtils() {
        AccessibilityHelper = this;
    }






    @Override
    public void onAccessibilityEvent(AccessibilityEvent accessibilityEvent) {

    }

    @Override
    public void onInterrupt() {

    }


    // ===============  工具类   ===============

    /**
     * 等待
     * @param loop_time
     */
    public static void timeSleep(int loop_time) {
        try {
            for (int i = 0; i < loop_time; i++) {
                Thread.sleep(1);

                if (killThread) { // 停止当前任务
                    int t = 1 / 0;
                }
                while (isStop) {
                    if (killThread) { // 停止当前任务
                        int t = 1 / 0;
                    }
                    Thread.sleep(10);
                }
            }
        } catch (InterruptedException e) {
            printLogMsg(ExceptionUtil.toString(e), 0);
        }
    }


    public static Boolean startApplication(Context ctx, String pkName) {
        PackageManager packageManager = ctx.getPackageManager();
        Intent resolveIntent = new Intent(Intent.ACTION_MAIN, null);
        resolveIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        resolveIntent.setPackage(pkName);
        List<ResolveInfo> apps = packageManager.queryIntentActivities(resolveIntent, 0);
        if (apps == null || apps.size() == 0) {
            printLogMsg("Exception startApplication failed: No matching activities found", 0);

            // 尝试直接启动应用（不指定具体Activity）
            Intent launchIntent = ctx.getPackageManager().getLaunchIntentForPackage(pkName);
            if (launchIntent != null) {
                ctx.startActivity(launchIntent);
                return true;
            }
            return false;
        }
        ResolveInfo ri = apps.get(0); // Get the first matching activity

        if (ri != null && ri.activityInfo != null) {
            String packageName = ri.activityInfo.packageName;
            String className = ri.activityInfo.name;
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ComponentName cn = new ComponentName(packageName, className);
            intent.setComponent(cn);
            try {
                ctx.startActivity(intent);
                return true;
            } catch (SecurityException e) {
                e.printStackTrace();
                printLogMsg("Exception startApplication failed: SecurityException", 0);
            }
        }
        return false;
    }

    /**
     * 获取当前Activity
     */
    public static String currentActivityName() {
        return currentActivityName;
    }
    /**
     * 刷新当前activity
     * @param event
     */
    protected void refreshCurrentActivity(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            String packageName = String.valueOf(event.getPackageName());
            String className = String.valueOf(event.getClassName());
// 获取到了当前活动的包名和类名，可以进行相应处理
             printLogMsg(packageName, 0);
            if (
                    className.startsWith("android.widget.") ||
                            className.startsWith("android.view.") ||
                            currentActivityName.equals(className)
            ) {
                return;
            }
            currentActivityName = className;
            Log.i(TAG, "refreshCurrentActivity: " + className);
        }
    }

    protected void systemClickListener(AccessibilityEvent event) {
        // 监听系统事件
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            // 当发生点击事件时执行相应的操作
            // 可以在这里处理系统点击事件的逻辑
            // printLogMsg("当发生点击事件时执行相应的操作", 0);
        }
    }
    // 判断本程序的无障碍服务是否已经开启
    public static Boolean isAccessibilityServiceOn() {
        try{
            String packageName = context.getPackageName();
            String service = packageName + "/" + packageName + ".MyAccessibilityService";
            int enabled = Settings.Secure.getInt(Constant.context.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED);
            TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
            if (enabled == 1) {
                String settingValue = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                if (settingValue != null) {
                    splitter.setString(settingValue);
                    while (splitter.hasNext()) {
                        String accessibilityService = splitter.next();
                        if (accessibilityService.equals(service)) {
                            return true;
                        }
                    }
                }
            }
        }catch (Exception ex){
            ex.printStackTrace();
            return false;
        }
        return false;
    }
    // 移动悬浮窗
    public static void moveFloatWindow(String val) {
        try {
            AccUtils.printLogMsg("移动悬浮窗 => " + val, 0);
            Intent intent = new Intent();
            intent.setAction("com.msg");
            switch (val) {
                case "打开":
                    intent.putExtra("msg", "show_max");
                    break;
                case "隐藏":
                    intent.putExtra("msg", "hide_mini");
                    break;
                case "全屏":
                    intent.putExtra("msg", "full_screen");
                    break;
            }
            context.sendBroadcast(intent);
        }catch (Exception e){
            printLogMsg(ExceptionUtil.toString(e), 0);
        }
    }

    // 日志打印
    public static void printLogMsg(String msg) {
        if (DEV_MODE){
            if (LOGSERVER == null) {
                LogWebsocketServer.start();
            }
        }
        Log.d(TAG, "printLogMsg: " + msg);
        Intent intent = new Intent();
        intent.setAction("com.msg");
        intent.putExtra("msg", msg);
        context.sendBroadcast(intent);

        if (killThread) { // 停止当前任务
//            int i = 1 / 0;
        }
        while (isStop) {
            if (killThread) { // 停止当前任务
//                int t = 1 / 0;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                printLogMsg(ExceptionUtil.toString(e), 0);
            }
        }
    }

    // 日志打印
    public static void printLogMsg(String msg, int type) {
        Intent intent = new Intent();
        intent.setAction("com.msg");
        intent.putExtra("msg", msg);
        context.sendBroadcast(intent);
    }


    // 从 assets 中加载 JavaScript 文件
    public static String loadScriptFromAssets(String fileName) throws IOException {
        InputStream inputStream = context.getAssets().open(fileName);
        byte[] buffer = new byte[inputStream.available()];
        inputStream.read(buffer);
        inputStream.close();
        return new String(buffer);
    }




    /**
     * *******************************************自带方法封装**************************************************
     */

    /**
     * getRootInActiveWindow
     * @return
     */

    static AccessibilityNodeInfo root;
    public static AccessibilityNodeInfo getRootInActiveMy() {
        for (int i = 0; i < 10; i++) {
            root = AccessibilityHelper.getRootInActiveWindow();
            if (root != null && root.getChildCount() != 0) {
                return root;
            }
            timeSleep(150);
        }
        printLogMsg( "Exception: do not find window", 0);
        return root;

    }


    @SuppressLint({"InvalidWakeLockTag", "NewApi"})
    public static Boolean lockScreenNow() {
        // 立即锁屏
        try {
            return AccessibilityHelper.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN);
        }catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public static Boolean back() {
        try {
            AccessibilityHelper.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
            return true;
        }catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public static Boolean home() {
        try {
            AccessibilityHelper.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
            return true;
        }catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public static Boolean openQuickSettings() {
        try {
            AccessibilityHelper.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS);
            return true;
        }catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
    public static Boolean recentApps() {
        try {
            AccessibilityHelper.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS);
            return true;
        }catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
    /**
     * 获取坐标
     * @param nodeInfo
     * @return
     */
    public static Rect getBounds(AccessibilityNodeInfo nodeInfo) {
        Rect rect = new Rect();
        nodeInfo.getBoundsInScreen(rect);
        // 回收
        return rect;
    }






    // 返回的节点
    private volatile static AccessibilityNodeInfo nodeInfoOut;
    private volatile static List<AccessibilityNodeInfo> nodeInfoOutList;
    private volatile static List<String> textList;


    static class StopMsgException extends RuntimeException {
    }
    // 递归找元素
    private static void recursionFindAllText(AccessibilityNodeInfo root) {
        String text_tmp = String.valueOf(root.getText());
        if (!text_tmp.isEmpty() && !"null".equals(text_tmp)) {
            textList.add("text\t" + text_tmp);
        }
        String desc_tmp = String.valueOf(root.getContentDescription());
        if (!desc_tmp.isEmpty() && !"null".equals(desc_tmp)) {
            textList.add("desc\t" + desc_tmp);
        }
        /*if (textList.size() >= 20) {
            return;
        }*/
        for (int i = 0; i < root.getChildCount(); i++) {
            recursionFindAllText(root.getChild(i));
        }
    }


    /**
     * 返回一个文字列表，包含root下所有文字内容
     * @param root
     * @return
     */
    public static List<String> findAllText(AccessibilityNodeInfo root) {
        try {
            textList = new ArrayList<>();
            recursionFindAllText(root);
            return textList;
        }catch (StopMsgException e) {
            e.printStackTrace();
        }
        return null;
    }
}
