package com.vixmobi.auto.debug;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

/** 仅用于 debug：四个纯色区域用于验证截图色值、裁剪和旋转；可切换安全窗口。 */
public final class ScreenshotFixtureActivity extends Activity {
  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    updateSecureWindow();
  }

  private void updateSecureWindow() {
    if (getIntent().getBooleanExtra("secure", false))
      getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
    getWindow().getDecorView().invalidate();
  }

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    updateSecureWindow();
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    setContentView(
        new View(this) {
          final Paint paint = new Paint();

          @Override
          protected void onDraw(Canvas canvas) {
            if (getIntent().getBooleanExtra("secure", false)) {
              canvas.drawColor(0xffff00ff);
              return;
            }
            int w = getWidth(), h = getHeight();
            paint.setColor(0xffff0000);
            canvas.drawRect(0, 0, w / 2f, h / 2f, paint);
            paint.setColor(0xff00ff00);
            canvas.drawRect(w / 2f, 0, w, h / 2f, paint);
            paint.setColor(0xff0000ff);
            canvas.drawRect(0, h / 2f, w / 2f, h, paint);
            paint.setColor(0xffffffff);
            canvas.drawRect(w / 2f, h / 2f, w, h, paint);
          }
        });
  }
}
