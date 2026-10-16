package com.vixmobi.auto.runtime;

import android.content.Context;
import com.caoccao.javet.exceptions.JavetException;
import com.caoccao.javet.interop.V8Runtime;
import com.caoccao.javet.values.reference.V8ValueGlobalObject;
import com.vixmobi.auto.image.ImageApi;
import com.vixmobi.auto.input.InputApi;
import com.vixmobi.auto.node.GestureApi;
import com.vixmobi.auto.node.NodeSettings;
import com.vixmobi.auto.permissions.PermissionApi;
import com.vixmobi.auto.storage.SqliteApi;
import com.vixmobi.auto.toolbox.EncodeDecoder;
import com.vixmobi.auto.toolbox.UtilityApi;
import com.vixmobi.auto.utils.ShellApi;
import java.util.function.Consumer;

/** 调试执行和本地执行共用的模块注册及资源生命周期。 */
public final class ScriptModules {
  public static final String[] ASSETS = {
    "logging.js",
    "http.js",
    "utils.js",
    "sqlite.js",
    "thread.js",
    "base.js",
    "shortcuts.js",
    "properties.js",
    "image.js"
  };
  private final ThreadApi threads;
  private final SqliteApi sqlite;
  private final UtilityApi utils;
  private final ImageApi image;
  private final GestureApi gestures = new GestureApi();
  private final NodeSettings nodes = new NodeSettings();
  private final Context context;
  private final ShellApi shell;

  public ScriptModules(Context context, ShellApi shell, Consumer<String> errors) {
    this.context = context;
    this.shell = shell;
    threads = new ThreadApi(errors);
    sqlite = new SqliteApi(context.getDatabasePath("xpauto.db").getParentFile());
    utils = new UtilityApi(context, errors);
    image = new ImageApi(context, shell);
  }

  public void install(V8Runtime runtime) throws JavetException {
    try (V8ValueGlobalObject global = runtime.getGlobalObject()) {
      global.set("__xpautoSqlite", sqlite);
      global.set("__xpautoUtils", utils);
      global.set("__xpautoImage", image);
      global.set("encodeDecoder", new EncodeDecoder());
      global.set("__xpautoGesture", gestures);
      global.set("__xpautoNodes", nodes);
      global.set("__xpautoInput", new InputApi(context));
      global.set("__xpautoPermissions", new PermissionApi(context, shell));
    }
    threads.install(runtime);
  }

  public void reset() {
    threads.reset();
    nodes.reset();
    image.reset();
  }

  public void cancel() {
    threads.stopAll();
    sqlite.cancel();
    utils.close();
    image.cancel();
  }

  public boolean isExecuting() {
    return threads.isExecuting();
  }

  /** 必须在主脚本退出 V8 后清理，再允许新一轮执行或销毁运行环境。 */
  public void finish(V8Runtime runtime) {
    threads.finish(runtime);
    image.finish();
    sqlite.close();
    utils.close();
    gestures.close();
    nodes.reset();
    threads.reset();
  }
}
