package com.vixmobi.auto.floaty;

import android.content.Context;
import android.provider.Settings;
import com.vixmobi.auto.Constant;
import com.vixmobi.auto.devtools.DeveloperService;
import com.vixmobi.auto.runtime.ScriptBlocking;

/** 现有悬浮球、日志窗的脚本控制入口，所有 View 操作都在主线程执行。 */
public final class BuiltinFloatApi {
    private final Context context;
    private final UiDispatcher ui = new UiDispatcher();
    public BuiltinFloatApi(Context context) { this.context = context; }
    public boolean hasPermission() { return Settings.canDrawOverlays(context); }
    public boolean command(String action, String json) {
        return ScriptBlocking.call(() -> ui.call(
            () -> DeveloperService.floatingCommand(action, json), () -> Constant.killThread));
    }
}
