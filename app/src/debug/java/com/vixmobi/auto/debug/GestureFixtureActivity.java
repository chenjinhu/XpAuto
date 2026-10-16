package com.vixmobi.auto.debug;

import android.app.Activity;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vixmobi.auto.R;
import org.json.JSONObject;

/** 仅用于 debug：记录应用实际收到的触摸，验证多指数量和长按拖拽，而非仅检查返回值。 */
public final class GestureFixtureActivity extends Activity {
  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    TextView status = new TextView(this);
    status.setId(R.id.selector_status);
    status.setText("等待手势");
    root.addView(status);
    View surface =
        new View(this) {
          int maxPointers, moves, downs, ups;
          long started;
          float firstX, lastX;

          @Override
          public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
              maxPointers = moves = downs = ups = 0;
              started = event.getEventTime();
              firstX = event.getX();
            }
            maxPointers = Math.max(maxPointers, event.getPointerCount());
            lastX = event.getX();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN)
              downs++;
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) ups++;
            if (action == MotionEvent.ACTION_MOVE) moves++;
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
              try {
                JSONObject result = new JSONObject();
                result.put("maxPointers", maxPointers);
                result.put("moves", moves);
                result.put("downs", downs);
                result.put("ups", ups);
                result.put("elapsed", event.getEventTime() - started);
                result.put("firstX", firstX);
                result.put("lastX", lastX);
                result.put("cancelled", action == MotionEvent.ACTION_CANCEL);
                status.setText(result.toString());
              } catch (Exception error) {
                throw new IllegalStateException(error);
              }
            }
            return true;
          }
        };
    surface.setContentDescription("手势测试区域");
    surface.setBackgroundColor(0xffdbeafe);
    root.addView(surface, new LinearLayout.LayoutParams(-1, 0, 1));
    setContentView(root);
  }
}
