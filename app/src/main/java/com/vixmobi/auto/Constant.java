package com.vixmobi.auto;

import static com.vixmobi.auto.node.AccUtils.printLogMsg;

import android.content.Context;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.caoccao.javet.interop.V8Runtime;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vixmobi.auto.devplugin.LogWebsocketServer;
import com.vixmobi.auto.activitys.MainActivity;
import com.vixmobi.auto.utils.FileUtils;
import com.vixmobi.auto.utils.StringUtils;

import org.java_websocket.server.WebSocketServer;


public class Constant {
    public static boolean CRON_TASK = false; // 定时任务是否开启
    public static String CRON_TASK_FILE = "定时任务配置.txt"; // 定时任务配置
    public static String CRON_TASK_FILE_TEST = "test_demo.js"; // 定时任务测试

    public static boolean DEV_MODE = false; // 开发者模式
    public static V8Runtime v8Runtime;

    public static String XIAOPANG_INFO = "小胖";
    public static String XIAOPANG_INFO_HOME = "小胖 XIAOPANG_INFO_HOME";
    public static final String ABSOLUTE_PATH = "/sdcard/XIAOPANG_DIR/";
    public static final String PATH = "/XIAOPANG_DIR/";
    // 开发者服务
    public static LogWebsocketServer LOGSERVER;
    // 悬浮球位置
    public static int float_x = 0;
    public static int float_y = 0;

    public static String tag = "xiaopang";
    // 停顿时长
    public static final int waitHrefOfSecond  =   500;
    public static final int waitOneSecond     =   1000;
    public static final int waitTwoSecond     =   2000;
    public static final int waitThreeSecond   =   3000;
    public static final int waitFourSecond    =   4000;
    public static final int waitFiveSecond    =   5000;
    public static final int waitSixSecond     =   6000;
    // 上下文
    public static Context context = null;
    public static MainActivity mainActivity = null;

    // 屏幕宽高
    public static int text_size = 11;
    public static int mWidth = 1440;
    public static int mHeight = 3040;
    public static int __mHeight = -1; //去掉导航栏和状态栏的高度
    public static int statusBarHeight = -1; //状态栏的高度
    public static int navigationBarHeight = -1; //导航栏的高度
    public static boolean navigationBarOpen = true; //导航栏是否开启
    // 中控服务端接口, 预留
    public static String API = "http://192.168.1.37:5000";

    // 闲鱼包名
    public static String idleFishPackageName = "com.taobao.idlefish";

    public static String ActivityPrimary = "com.taobao.idlefish.ui.alert.base.container.FishDialog";

    // 是否已经打开过闲鱼, 一般情况下，只默认打开一次闲鱼进程
    public static boolean shouldLaunchIdleFish = true;

    public static boolean isClickMe = false;
    // 是否强制关闭线程
    public static volatile boolean killThread = false;
    // 暂停标志
    public static volatile boolean isStop = false;

    public static volatile boolean isRunning = false; // 是否有任务在运行
    public static boolean isOpenFloatWin = false; // 悬浮窗是否打开
    public static String checkedFileName = ""; // 当前选中的脚本文件名称
    // 悬浮窗展示的文字
    public static TextView btnTextView = null;
    // ll
    public static LinearLayout ll = null;

    public static void saveConfig() {

        // 使用Gson的JsonObject来构建和填充数据
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("DEV_MODE", DEV_MODE);
        jsonObject.addProperty("CHECKED_FILE_NAME", checkedFileName);

        // 将JsonObject转换为字符串
        String configString = jsonObject.toString();
        printLogMsg(configString, 0);
        FileUtils.writeToTxt("/sdcard/FATJS_DIR/config.json", configString);
    }

    public static void reviewConfig() {
        String readFile = FileUtils.readFile("/sdcard/FATJS_DIR/config.json");
        if (StringUtils.isEmpty(readFile)) {
            printLogMsg("reviewConfig empty", 0);
            saveConfig();
            return;
        }
        // 使用Gson的JsonParser来解析字符串
        JsonObject parseObject = JsonParser.parseString(readFile).getAsJsonObject();

        // 从解析后的JsonObject中获取特定的值
        DEV_MODE = parseObject.get("DEV_MODE").getAsBoolean();
        checkedFileName = parseObject.get("CHECKED_FILE_NAME").getAsString();
    }
}
