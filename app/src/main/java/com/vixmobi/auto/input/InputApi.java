package com.vixmobi.auto.input;

import android.content.Context;

/** 供脚本查询输入法状态并提交文本，不暴露服务实例。 */
public final class InputApi {
  private final Context context;

  public InputApi(Context context) {
    this.context = context;
  }

  public boolean current() {
    return ScriptInputMethod.current(context);
  }

  public boolean shown() {
    return ScriptInputMethod.shown();
  }

  public boolean text(String value) {
    return ScriptInputMethod.input(value, null);
  }

  public boolean key(int value) {
    return ScriptInputMethod.input(null, value);
  }
}
