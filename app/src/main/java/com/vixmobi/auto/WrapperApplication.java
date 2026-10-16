package com.vixmobi.auto;

import android.app.Application;
import android.content.Context;

public class WrapperApplication extends Application {
    private boolean privacyPolicyAgreed = true;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        privacyPolicyAgreed = true;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // Accessibility may be started by Android before any Activity or developer service.
        com.vixmobi.auto.Constant.context = getApplicationContext();
        //注意APP合规性，若最终用户未同意隐私政策则不要调用
        if (privacyPolicyAgreed) {

        }
    }

}
