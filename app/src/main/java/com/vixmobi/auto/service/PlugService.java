package com.vixmobi.auto.service;

import static com.vixmobi.auto.Constant.*;

import android.content.ComponentName;
import android.content.Intent;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import com.vixmobi.auto.node.AccUtils;

import java.util.List;
import java.util.Random;

public class PlugService extends AccUtils {
    public static String TAG = tag;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent accessibilityEvent) {
        Log.d(TAG, "onAccessibilityEvent: ");
//        AccessibilityNodeInfo rootNodeInfo = getRootInActiveWindow();
//        Log.d(TAG, "onAccessibilityEvent: " + rootNodeInfo.getClassName());
//        if (rootNodeInfo == null) {
//            return;
//        }

//        handleIdleFish(rootNodeInfo);
        // 刷新当前 Activity()
        super.refreshCurrentActivity(accessibilityEvent);

        // 监听点击事件
        super.systemClickListener(accessibilityEvent);

    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.d(tag, "onServiceConnected: ");
        Toast.makeText(this, "闲鱼助手启动成功", Toast.LENGTH_LONG).show();
        String acctivityName = currentActivityName();
        Log.d(TAG, "acctivityName: " + acctivityName);
    }

    private void handleIdleFish(AccessibilityNodeInfo rootNodeInfo){
        // 判断当前应用是否为闲鱼
        String currentPkgName = (String) rootNodeInfo.getPackageName();
        if (!currentPkgName.equals(idleFishPackageName) && shouldLaunchIdleFish) {
            Log.d(tag, "IdleFish: 闲鱼应用没有启动, 开始启动闲鱼");
//            Toast.makeText(this, "闲鱼应用没有启动, 开始启动闲鱼", Toast.LENGTH_SHORT).show();
            shouldLaunchIdleFish = false;
            launchIdleFish();
            return;
        }
//        Toast.makeText(this, "闲鱼应用已在最前", Toast.LENGTH_SHORT).show();
        // 获取当前Activity
//        String currentActivity = getActivityName(rootNodeInfo);
//        Log.d(TAG, "IdleFish: " + currentActivity);
//        Toast.makeText(this, "当前activity:" + currentActivity, Toast.LENGTH_SHORT).show();

        List<AccessibilityNodeInfo> primaryRightNode = rootNodeInfo.findAccessibilityNodeInfosByViewId("com.taobao.idlefish:id/right_btn");
        if (checkNotEmpty(primaryRightNode)) {
            // 同意隐私政策.
            Toast.makeText(this, "同意隐私政策", Toast.LENGTH_SHORT).show();
            sleep(3000,0);
            AccessibilityNodeInfo childNode = primaryRightNode.get(0);
            childNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            sleep(3000,0);
            return;
        }
        // 检测登录按钮状态
        List<AccessibilityNodeInfo> loginBannerNode = rootNodeInfo.findAccessibilityNodeInfosByViewId("com.taobao.idlefish:id/login_guide_bar");
        if (checkNotEmpty(loginBannerNode)) {
            // 同意隐私政策.
            Toast.makeText(this, "同意隐私政策", Toast.LENGTH_SHORT).show();
            AccessibilityNodeInfo childNode = loginBannerNode.get(0);
            childNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            return;
        }
        // 没有其他选择点击按钮 “我的” ， 提取登录信息
        if (isClickMe == false) {
            List<AccessibilityNodeInfo> buttonMy = rootNodeInfo.findAccessibilityNodeInfosByText("我的");
            if (checkNotEmpty(buttonMy)) {
                // 点击 我的 按钮
                Toast.makeText(this, "点击 我的 按钮", Toast.LENGTH_SHORT).show();
                AccessibilityNodeInfo childNode = buttonMy.get(0);
                childNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                isClickMe = true;
            }
        }

//        Toast.makeText(this, "啥也没有", Toast.LENGTH_SHORT).show();

    }
    private void sleep(int sleepTime,int sleepRandomTime){
        try {
            int randomTime = 0;
            if (sleepRandomTime>0){
                randomTime = new Random().nextInt(sleepRandomTime);
            }
            long t = sleepTime +randomTime;
            Thread.sleep(t);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    private boolean checkNotEmpty(List list){
        return list!=null&& list.size()>0;
    }
    private String getActivityName(AccessibilityNodeInfo nodeInfo){
        if (nodeInfo == null){
            return null;
        }
        System.out.println(nodeInfo.getClassName());
        if (nodeInfo.getClassName().equals("android.widget.FrameLayout")) {
            return nodeInfo.getClassName().toString();
        }
        return getActivityName(nodeInfo.getParent());
    }
    private void launchIdleFish() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setFlags( Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setComponent(new ComponentName(idleFishPackageName, "com.taobao.fleamarket.home.activity.InitActivity"));
        startActivity(intent);

    }

    @Override
    public void onInterrupt() {

    }
}
