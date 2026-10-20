package com.vixmobi.auto.node;

import static com.vixmobi.auto.Constant.*;
import static com.vixmobi.auto.node.AccUtils.AccessibilityHelper;
import static com.vixmobi.auto.node.AccUtils.back;
import static com.vixmobi.auto.node.AccUtils.home;
import static com.vixmobi.auto.node.AccUtils.loadScriptFromAssets;
import static com.vixmobi.auto.node.AccUtils.moveFloatWindow;
import static com.vixmobi.auto.node.AccUtils.openQuickSettings;
import static com.vixmobi.auto.node.AccUtils.printLogMsg;
import static com.vixmobi.auto.node.AccUtils.recentApps;
import static com.vixmobi.auto.node.AccUtils.timeSleep;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import com.caoccao.javet.exceptions.JavetException;
import com.caoccao.javet.interop.V8Host;
import com.caoccao.javet.interop.V8Inspector;
import com.caoccao.javet.interop.V8Runtime;
import com.caoccao.javet.interop.converters.JavetProxyConverter;
import com.vixmobi.auto.network.HttpApi;
import com.vixmobi.auto.utils.FileUtils;
import com.vixmobi.auto.utils.ShellUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class TaskBase extends UiSelector implements ITaskBase{
    public static HashMap<String, Object> hashMapBuffer;
    public Map<String, Object> _getHashMapBuffer() {
        return hashMapBuffer;
    }

    /** 全局选择器入口不能复用 TaskBase 自身的条件。 */
    public UiSelector newSelector() { return new UiSelector(); }

    /** 获取前台应用窗口的包名；无结果返回空字符串。 */
    public String _getCurrentRunningPkg() { return RunningAppInfo.getCurrentRunningPkg(); }
    /** 获取当前窗口的 Activity 完整类名；无可靠结果返回 null。 */
    public String _getRunningActivity() { return RunningAppInfo.getRunningActivity(); }

    private static String base;
    // ----------------  全局快捷事件
    // ---- 选择器
    // ------- 文本选择器
    public UiSelector _text(String str){
        return new UiSelector().text(str);
    }

    public UiSelector _textMatch(String regex){
        return new UiSelector().textMatch(regex);
    }
    // ------- id选择器
    public UiSelector _id(String id){
        return new UiSelector().id(id);
    }

    public UiSelector _idMatch(String str){
        return new UiSelector().idMatch(str);

    }
    // ------- clz类名选择器
    public UiSelector _clz(String str){
        return new UiSelector().clz(str);
    }

    public UiSelector _clzMatch(String regex){
        return new UiSelector().clzMatch(regex);
    }
    // ------- desc描述选择器
    public UiSelector _desc(String str){
        return new UiSelector().desc(str);
    }
    public UiSelector _descMatch(String regex){
        return new UiSelector().descMatch(regex);
    }


    // ---- 点击
    /**
     * 点击坐标
     * @param x1
     * @param y1
     * @param duration
     */
    public static boolean clickPoint(float x1, float y1, long duration) {
        Path path = new Path();
        x1 = x1 + new Random().nextInt(9) - 4;
        y1 = y1 + new Random().nextInt(9) - 4;
        printLogMsg("[x => " + x1 + ", y => " + y1 + "]", 0);
        if (x1 > mWidth || y1 > mHeight || x1 < 0 || y1 < 0) {
            printLogMsg("mWidth: " + mWidth, 0);
            printLogMsg("mHeight: " + mHeight, 0);
            printLogMsg("超出了点击范围", 0);
            return false;
        }
        path.moveTo(x1, y1);
        GestureDescription.Builder builder=new GestureDescription.Builder();
        GestureDescription gestureDescription=builder
                .addStroke(new GestureDescription.StrokeDescription(path,0,duration))
                .build();

        return AccessibilityHelper.dispatchGesture(gestureDescription,new AccessibilityService.GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription1) {
                super.onCompleted(gestureDescription1);
                Log.e(tag,"点击结束..."+ gestureDescription1.getStrokeCount());
            }
            @Override
            public void onCancelled(GestureDescription gestureDescription1) {
                super.onCancelled(gestureDescription1);
                Log.e(tag,"点击取消");
            }
        },null);
    }
    /**
     * 点击父级能点击的节点
     * @param nodeInfo
     * @return
     */
    public static Boolean clickParentCanClick(AccessibilityNodeInfo nodeInfo) {
        try {
            if (nodeInfo != null) {
                boolean action1 = nodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                if (action1) {
                    // 回收
                    nodeInfo.recycle();
                    return true;
                }

                while (!nodeInfo.isClickable()) {
                    nodeInfo = nodeInfo.getParent();
                }
                if (nodeInfo.isClickable()) {
                    boolean action = nodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    // 回收
                    nodeInfo.recycle();
                    return action;
                }
            }
        }catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * @param x x坐标
     * @param y y坐标
     * @return {boolean|布尔型}
     */
    public boolean _clickPoint(int x, int y) {
        return clickPoint(x,y,75 + new Random().nextInt(80));
    }

    /**
     * @param nodeInfo 选择器对象
     * @return {boolean|布尔型}
     */
    public static boolean click(AccessibilityNodeInfo nodeInfo){
        return clickParentCanClick(nodeInfo);
    }

    public static boolean _clickNode(AccessibilityNodeInfo nodeInfo) {
        return click(nodeInfo);
    }

    public static boolean _clickNode(UiObject node) { return node != null && node.click(); }

    public static boolean _clickNode(UiSelector selector) {
        UiObject obj = selector.getOneNodeInfo();
        if (obj!= null) {
            try { return obj.click(); } finally { obj.recycle(); }
        }
        return false;
    }


    // ---- 滑动
    /**
     * 手势滑动
     * @param x1
     * @param y1
     * @param x2
     * @param y2
     * @param duration
     */
    public static Boolean swipe(float x1,float y1,float x2,float y2,long duration) {
        try {
            // Path路径
            Path path=new Path();
            path.moveTo(x1 + new Random().nextInt(10) - 5,y1 + new Random().nextInt(10) - 5); // 点击 (x1,y1) 做为起点
            path.lineTo(x2 + new Random().nextInt(10) - 5,y2 + new Random().nextInt(10) - 5); // 水平滑动到 (x2,y2)

            GestureDescription.Builder builder=new GestureDescription.Builder();
            GestureDescription gestureDescription=builder
                    .addStroke(new GestureDescription.StrokeDescription(path,0,duration))
                    .build();
            return AccessibilityHelper.dispatchGesture(gestureDescription,new AccessibilityService.GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription1) {
                    super.onCompleted(gestureDescription1);
                    Log.e(tag,"滑动结束..."+ gestureDescription1.getStrokeCount());
                }
                @Override
                public void onCancelled(GestureDescription gestureDescription1) {
                    super.onCancelled(gestureDescription1);
                    Log.e(tag,"滑动取消");
                }
            },null);
        }catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean _swipe(float x1, float y1, float x2, float y2, long duration) {
        return swipe(x1, y1, x2, y2, duration);
    }


    // *************************************************************************************************
    // ----------------  日志&悬浮窗类
    public void _print(String msg) {
        printLogMsg(msg);
    }
    public void _showLog() {
        try {
            moveFloatWindow("打开");
        }catch (Exception e) {}
    }
    public void _hideLog() {
        try {
            moveFloatWindow("隐藏");
        }catch (Exception e) {}
    }
    public void _fullScreenLog() {
        try {
            moveFloatWindow("全屏");
        }catch (Exception e) {}
    }
    public void _clearLog() {
        Intent intent = new Intent();
        intent.setAction("com.msg");
        intent.putExtra("msg", "screen_log_clear");
        context.sendBroadcast(intent);
    }

    // ----------------    基础按键
    public void keyevent(int code){
        ShellUtils.sudo("input keyevent " + code);


    }


    public boolean _back() {
        return back();
    }
    public boolean _home() {
        return home();
    }
    public Boolean _menu(){
        return recentApps();
    }
    public void _power(){
        keyevent(26);
    }
    public void _openNotification(){
        keyevent(83);
    }

    public Boolean _openQuickSettings(){
        return openQuickSettings();
    }

    public Boolean _recentApps(){
        return recentApps();
    }

    // ----------------    时间
    public void _sleep(int time) {
        com.vixmobi.auto.runtime.ScriptBlocking.sleep(time);
    }


    public void initJavet(String script_path) {
        com.vixmobi.auto.utils.ShellApi shell = ShellUtils.createSession(
                message -> printLogMsg(message), () -> killThread);
        com.vixmobi.auto.floaty.FloatyApi floaty = new com.vixmobi.auto.floaty.FloatyApi(
                com.vixmobi.auto.Constant.context, message -> printLogMsg(message), () -> killThread);
        HttpApi http = new HttpApi(com.vixmobi.auto.Constant.context.getFilesDir(),
                message -> printLogMsg(message), () -> killThread);
        com.vixmobi.auto.logging.ScriptLogApi scriptLog = new com.vixmobi.auto.logging.ScriptLogApi(
                com.vixmobi.auto.Constant.context, (level, message) -> printLogMsg(message),
                com.vixmobi.auto.devtools.DeveloperService::clearLogs, () -> killThread);
        com.vixmobi.auto.runtime.ScriptModules modules = new com.vixmobi.auto.runtime.ScriptModules(com.vixmobi.auto.Constant.context, shell, message -> printLogMsg(message));
        boolean success = false;
        // V8 解析核心
        try {
            // 创建 v8 runtime
            v8Runtime = V8Host.getV8Instance().createV8Runtime();

            isRunning = true;
            isStop = false;
            killThread = false;
            // 加载脚本
            String script = FileUtils.readFile(script_path);
            if (script == null) throw new java.io.IOException(FileUtils.getErrorMsg());

            // 加载基础脚本
            if (base == null) {
                base = loadScriptFromAssets("base.js");
            }

            v8Runtime.setConverter(new JavetProxyConverter());
            // 设置全局变量 engines
            v8Runtime.getGlobalObject().set("engines", TaskBase.class);
            // 设置全局变量 http
            v8Runtime.getGlobalObject().set("__xpautoHttp", http);
            v8Runtime.getGlobalObject().set("__xpautoLog", scriptLog);
            modules.install(v8Runtime);
            // 文件类
            v8Runtime.getGlobalObject().set("file", FileUtils.class);

            v8Runtime.getGlobalObject().set("shell", shell);
            v8Runtime.getGlobalObject().set("floaty", floaty);
            for (String asset : com.vixmobi.auto.runtime.ScriptModules.ASSETS) {
                v8Runtime.getExecutor(loadScriptFromAssets(asset)).setResourceName("xpauto://sdk/" + asset).executeVoid();
            }
            java.io.File entryFile = new java.io.File(script_path).getAbsoluteFile();
            modules.execute(v8Runtime, script, script_path, entryFile.getParentFile(), entryFile.getName(),
                    entryFile.getParentFile().toURI().toString(), "xpauto", true);
            success = true;
        } catch (Exception e) {
            killThread = false;
            printLogMsg(e.toString());
        } finally {
            if (v8Runtime != null) modules.finish(v8Runtime);
            scriptLog.finish(success && !killThread);
            scriptLog.stop(true);
            shell.close();
            floaty.dispose();
            http.dispose();
            isRunning = false;
            isStop = true;
            killThread = true;
        }
    }




}
