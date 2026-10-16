package com.vixmobi.auto.node;

import static com.vixmobi.auto.Constant.context;
import static com.vixmobi.auto.node.AccUtils.home;
import static com.vixmobi.auto.node.AccUtils.timeSleep;

public class Utils {

    /**
     * 打开APP
     * @param packageName 包名
     * @return 布尔型 true 代表成功，false 代表失败
     */
    public static boolean openApp(String packageName) {
        return AccUtils.startApplication(context,packageName);
    }

    /**
     *  拼接打开APP命令
     * @param packageName 包名
     * @return 命令字符串
     */
    public static String getStartAppCmd(String packageName) {
        String command = "am start -n " + packageName;
        return command;
    }

    /**
     * @param appName 应用名
     * @return 布尔型 true 代表成功，false 代表失败
     */
    public static boolean openAppByName (String appName) {
        return new UiSelector().textMatch(appName).getOneNodeInfo().click();
    }

}
