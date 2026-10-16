package com.vixmobi.auto.input;

import android.content.ComponentName;
import android.content.Context;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import com.vixmobi.auto.runtime.ScriptBlocking;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/** 用户在系统设置手动启用和选择的脚本输入法，不记录输入内容。 */
public final class ScriptInputMethod extends InputMethodService {
  private static volatile ScriptInputMethod instance;
  private boolean showView = true;

  @Override
  public void onCreate() {
    super.onCreate();
    instance = this;
  }

  @Override
  public void onDestroy() {
    if (instance == this) instance = null;
    super.onDestroy();
  }

  @Override
  public View onCreateInputView() {
    Button button = new Button(this);
    button.setText("XpAuto 脚本输入法 · 点击切换键盘");
    button.setOnClickListener(
        view ->
            getSystemService(android.view.inputmethod.InputMethodManager.class)
                .showInputMethodPicker());
    return button;
  }

  @Override
  public boolean onEvaluateInputViewShown() {
    return showView;
  }

  public static boolean current(Context context) {
    String selected =
        Settings.Secure.getString(
            context.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
    ComponentName name = ComponentName.unflattenFromString(selected == null ? "" : selected);
    return new ComponentName(context, ScriptInputMethod.class).equals(name);
  }

  public static boolean shown() {
    return instance != null && instance.isInputViewShown();
  }

  public static boolean input(String text, Integer code) {
    return ScriptBlocking.call(
        () -> {
          FutureTask<Boolean> task =
              new FutureTask<>(
                  () -> {
                    ScriptInputMethod ime = instance;
                    if (ime == null || !current(ime)) return false;
                    if (code == null) {
                      switch (text) {
                        case "—ec_close_input—":
                          ime.requestHideSelf(0);
                          return true;
                        case "—ec_open_input—":
                          ime.requestShowSelf(0);
                          return true;
                        case "—ec_show_input_view—":
                          ime.showView = true;
                          ime.updateInputViewShown();
                          return true;
                        case "—ec_hide_input_view—":
                          ime.showView = false;
                          ime.updateInputViewShown();
                          return true;
                        default:
                          break;
                      }
                    }
                    InputConnection connection = ime.getCurrentInputConnection();
                    if (connection == null) return false;
                    if (code == null) return connection.commitText(text, 1);
                    int[] actions = {
                      EditorInfo.IME_ACTION_SEARCH,
                      EditorInfo.IME_ACTION_DONE,
                      EditorInfo.IME_ACTION_GO,
                      EditorInfo.IME_ACTION_NEXT,
                      EditorInfo.IME_ACTION_PREVIOUS,
                      EditorInfo.IME_ACTION_SEND
                    };
                    if (code >= 1000 && code <= 1005)
                      return connection.performEditorAction(actions[code - 1000]);
                    if (code < 0 || code > KeyEvent.getMaxKeyCode())
                      throw new IllegalArgumentException("无效按键代码");
                    boolean down =
                        connection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
                    return connection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code)) && down;
                  });
          Handler main = new Handler(Looper.getMainLooper());
          main.post(task);
          try {
            return task.get(5, TimeUnit.SECONDS);
          } finally {
            main.removeCallbacks(task);
            task.cancel(false);
          }
        });
  }
}
