package com.vixmobi.auto.activitys;

import static com.vixmobi.auto.Constant.*;
import static com.vixmobi.auto.node.AccUtils.startApplication;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;

import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.vixmobi.auto.Constant;
import static com.vixmobi.auto.node.AccUtils.*;

import com.vixmobi.auto.devplugin.LogWebsocketServer;
import com.vixmobi.auto.R;
import com.vixmobi.auto.activitys.FloatingButton;
import com.vixmobi.auto.activitys.FloatingWindow;
import com.vixmobi.auto.config.WindowPermission;
import com.vixmobi.auto.databinding.ActivityMainBinding;
import com.vixmobi.auto.service.MyService;
import com.vixmobi.auto.tools.Enviroment;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    public static String TAG = tag;
    private ActivityMainBinding binding;
//    public static CronTaskManager cronTaskManager;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActionBar actionBar = getSupportActionBar();
        Window window = getWindow();
        window.setStatusBarColor(ContextCompat.getColor(this, R.color.status_color));
        // 状态栏文字颜色
        if (actionBar != null) {
            actionBar.setBackgroundDrawable(new ColorDrawable(Color.argb(165,0,0,0)));
        }


        context = getApplicationContext();
        mainActivity = this;
        // 开启前台服务 未适配低版本安卓
        openFloatWindow();
//        openForwardService();


        com.vixmobi.auto.databinding.ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 底部导航栏
        BottomNavigationView navView = findViewById(R.id.nav_view);
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications)
               .build();
        // 导航栏点击事件
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(binding.navView, navController);


        if (__mHeight == -1) {
            initDisplay();
        }

//        DevicesOAID(); // OAID/AAID
        reviewConfig(); // 回显 config 数据

        // 测试功能
        // 线程

        LOGSERVER =new LogWebsocketServer(); // 开启日志服务
        LOGSERVER.runServer(); // 开启日志服务

    }

    private void openFloatWindow() {
        // 在其他应用上层显示
        boolean permission = WindowPermission.checkPermission(this);
        if (permission) {
            printLogMsg("onCreate: permission true => " + permission, 0);
            // 打开日志悬浮窗
            startService(new Intent(context, FloatingWindow.class));
            // 打开控制悬浮窗
            startService(new Intent(context, FloatingButton.class));
        }
    }


    public void initDisplay() {
        DisplayMetrics dm = new DisplayMetrics();//屏幕度量
        Display defaultDisplay = getWindowManager().getDefaultDisplay(); //获取屏幕宽高
        defaultDisplay.getRealMetrics(dm); //获取真实的宽高
        mWidth = dm.widthPixels;//宽度
        mHeight = dm.heightPixels;//高度
        DisplayMetrics __dm = new DisplayMetrics();//屏幕度量
        getWindowManager().getDefaultDisplay().getMetrics(__dm);
        __mHeight = __dm.heightPixels;//去掉导航栏和状态栏的高度
        statusBarHeight = getStatusBarHeight(); //状态栏的高度
        navigationBarHeight = getNavigationBarHeight(); //导航栏的高度
        if (__mHeight + navigationBarHeight == mHeight) { // 屏幕内是否有导航栏的高度
            navigationBarOpen = true;
            return;
        }
        if (__mHeight + statusBarHeight + navigationBarHeight == mHeight) {
            navigationBarOpen = true;
            return;
        }
        if (__mHeight + statusBarHeight == mHeight) {
            navigationBarOpen = false;
            return;
        }
        if (__mHeight + navigationBarHeight > mHeight) {
            navigationBarOpen = false;
            return;
        }
        navigationBarOpen = false;


    }

    /**
     * @return 导航栏的高度
     */
    public int getNavigationBarHeight() {
        int navigationBarHeight = 0;
        int resourceId = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        if (resourceId > 0) {
            navigationBarHeight = getResources().getDimensionPixelSize(resourceId);
        }
        return navigationBarHeight;
    }

    /**
     * @return 状态栏的高度
     */
    public int getStatusBarHeight() {
        int statusBarHeight = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            statusBarHeight = getResources().getDimensionPixelSize(resourceId);
        }
        return statusBarHeight;
    }

    private void openForwardService() {
        Intent intent = new Intent(this, MyService.class);
        startService(intent);
    }

    protected void work(){
        startApplication(getApplicationContext(),Constant.idleFishPackageName);
        String acctivityName = currentActivityName();
        Log.d(TAG, "acctivityName: " + acctivityName);
    }
}