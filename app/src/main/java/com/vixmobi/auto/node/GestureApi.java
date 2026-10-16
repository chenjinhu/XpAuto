package com.vixmobi.auto.node;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import com.vixmobi.auto.runtime.ScriptBlocking;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONArray;
import org.json.JSONObject;

/** 用无障碍手势执行坐标操作；逐段续接触点，保留按下状态和各事件的延迟。 */
public final class GestureApi {
  private GestureDescription.StrokeDescription held;
  private float heldX, heldY;
  private final List<GestureDescription.StrokeDescription> pendingTouches = new ArrayList<>();
  private final java.util.concurrent.locks.ReentrantLock lock =
      new java.util.concurrent.locks.ReentrantLock();

  private boolean dispatch(GestureDescription gesture, long timeout) throws InterruptedException {
    ScriptBlocking.checkCancelled();
    AccessibilityService service = AccUtils.AccessibilityHelper;
    if (service == null) return false;
    CountDownLatch done = new CountDownLatch(1);
    boolean[] ok = {false};
    long started = System.nanoTime();
    long duration = 0;
    pendingTouches.clear();
    for (int i = 0; i < gesture.getStrokeCount(); i++) {
      GestureDescription.StrokeDescription stroke = gesture.getStroke(i);
      duration = Math.max(duration, stroke.getStartTime() + stroke.getDuration());
      if (stroke.willContinue()) pendingTouches.add(stroke);
    }
    boolean accepted =
        service.dispatchGesture(
            gesture,
            new AccessibilityService.GestureResultCallback() {
              @Override
              public void onCompleted(GestureDescription value) {
                ok[0] = true;
                done.countDown();
              }

              @Override
              public void onCancelled(GestureDescription value) {
                done.countDown();
              }
            },
            new Handler(Looper.getMainLooper()));
    if (!accepted) {
      pendingTouches.clear();
      return false;
    }
    long end = System.nanoTime() + Math.max(1, timeout) * 1000000L;
    while (!done.await(20, TimeUnit.MILLISECONDS)) {
      ScriptBlocking.checkCancelled();
      if (System.nanoTime() > end) {
        releaseHeld();
        return false;
      }
    }
    // 静止且保持按下的段可能只注入 DOWN，系统会提前回调；仍须等满本段时长再续接。
    long scheduledEnd = started + duration * 1000000L;
    while (ok[0] && System.nanoTime() < scheduledEnd) {
      ScriptBlocking.checkCancelled();
      if (System.nanoTime() >= end) {
        releaseHeld();
        return false;
      }
      Thread.sleep(Math.max(1, Math.min(20, (scheduledEnd - System.nanoTime()) / 1000000L)));
    }
    if (!ok[0]) pendingTouches.clear();
    return ok[0];
  }

  private static void point(float x, float y) {
    if (!Float.isFinite(x) || !Float.isFinite(y) || x < 0 || y < 0)
      throw new IllegalArgumentException("坐标必须为非负有限数值");
  }

  private static void duration(long value) {
    if (value < 1 || value > 60000) throw new IllegalArgumentException("手势时间应为 1～60000 毫秒");
  }

  private boolean serial(Callable<Boolean> action) {
    return ScriptBlocking.call(
        () -> {
          lock.lockInterruptibly();
          try {
            return action.call();
          } catch (Exception error) {
            releaseHeld();
            throw error;
          } finally {
            lock.unlock();
          }
        });
  }

  public boolean press(float x, float y, long millis) {
    return swipe(x, y, x, y, millis);
  }

  public boolean swipe(float x1, float y1, float x2, float y2, long millis) {
    point(x1, y1);
    point(x2, y2);
    duration(millis);
    return serial(
        () -> {
          held = null;
          Path path = new Path();
          path.moveTo(x1, y1);
          path.lineTo(x2, y2);
          return dispatch(
              new GestureDescription.Builder()
                  .addStroke(new GestureDescription.StrokeDescription(path, 0, millis))
                  .build(),
              millis + 2000);
        });
  }

  public boolean drag(float x1, float y1, float x2, float y2, long millis) {
    point(x1, y1);
    point(x2, y2);
    duration(millis);
    return serial(
        () -> {
          Path start = new Path();
          start.moveTo(x1, y1);
          GestureDescription.StrokeDescription down =
              new GestureDescription.StrokeDescription(start, 0, 600, true);
          if (!dispatch(new GestureDescription.Builder().addStroke(down).build(), 2600))
            return false;
          Path path = new Path();
          path.moveTo(x1, y1);
          path.lineTo(x2, y2);
          return dispatch(
              new GestureDescription.Builder()
                  .addStroke(down.continueStroke(path, 0, millis, false))
                  .build(),
              millis + 2000);
        });
  }

  public boolean touch(int action, float x, float y) {
    point(x, y);
    return serial(
        () -> {
          if (action != 0 && held == null) return false;
          Path path = new Path();
          path.moveTo(action == 0 ? x : heldX, action == 0 ? y : heldY);
          path.lineTo(x, y);
          GestureDescription.StrokeDescription next =
              action == 0
                  ? new GestureDescription.StrokeDescription(path, 0, 1, true)
                  : held.continueStroke(path, 0, 16, action != 1);
          boolean ok = dispatch(new GestureDescription.Builder().addStroke(next).build(), 2016);
          held = ok && action != 1 ? next : null;
          heldX = x;
          heldY = y;
          return ok;
        });
  }

  /** 空手势不可派发，用保存的触点续接抬起；没有保存触点时无需操作。 */
  public boolean resetAllTouchState() {
    return serial(
        () -> {
          releaseHeld();
          return true;
        });
  }

  private void releaseHeld() {
    if (pendingTouches.isEmpty()) {
      held = null;
      return;
    }
    AccessibilityService service = AccUtils.AccessibilityHelper;
    try {
      GestureDescription.Builder up = new GestureDescription.Builder();
      for (GestureDescription.StrokeDescription stroke : pendingTouches) {
        android.graphics.PathMeasure measure =
            new android.graphics.PathMeasure(stroke.getPath(), false);
        float[] position = new float[2];
        if (!measure.getPosTan(measure.getLength(), position, null)) {
          android.graphics.RectF bounds = new android.graphics.RectF();
          stroke.getPath().computeBounds(bounds, true);
          position[0] = bounds.left;
          position[1] = bounds.top;
        }
        Path path = new Path();
        path.moveTo(position[0], position[1]);
        up.addStroke(stroke.continueStroke(path, 0, 1, false));
      }
      if (service != null) service.dispatchGesture(up.build(), null, null);
    } finally {
      held = null;
      pendingTouches.clear();
    }
  }

  public void close() {
    lock.lock();
    try {
      releaseHeld();
    } finally {
      lock.unlock();
    }
  }

  private static final class Finger {
    float x, y;
    GestureDescription.StrokeDescription stroke;

    Finger(float x, float y) {
      this.x = x;
      this.y = y;
    }
  }

  public boolean multiTouchJson(String json, long timeout) {
    duration(timeout);
    return serial(
        () -> {
          JSONArray fingers = new JSONArray(json);
          TreeMap<Long, Map<Integer, JSONObject>> events = new TreeMap<>();
          for (int i = 0; i < fingers.length(); i++) {
            JSONArray points = fingers.optJSONArray(i);
            if (points == null || points.length() == 0) continue;
            if (points.length() > 1000) throw new IllegalArgumentException("每个触点最多 1000 个事件");
            long time = 0;
            for (int j = 0; j < points.length(); j++) {
              JSONObject p = points.getJSONObject(j);
              int action = p.getInt("action");
              long delay = p.optLong("delay", 0);
              if (delay < 0 || delay > 60000)
                throw new IllegalArgumentException("触摸延迟应为 0～60000 毫秒");
              if (j == 0 && action != 0
                  || j > 0 && action == 0
                  || j < points.length() - 1 && action == 1
                  || action < 0
                  || action > 2) throw new IllegalArgumentException("触摸序列应按下、移动、弹起排列");
              point((float) p.getDouble("x"), (float) p.getDouble("y"));
              time += Math.max(j == 0 ? 0 : 1, delay);
              if (time > timeout) throw new IllegalArgumentException("触摸事件时间超过 timeout");
              events.computeIfAbsent(time, k -> new LinkedHashMap<>()).put(i, p);
            }
          }
          if (events.isEmpty()) return false;
          long deadline = System.nanoTime() + timeout * 1000000L, previous = events.firstKey();
          if (previous > 0) Thread.sleep(previous);
          Map<Integer, Finger> active = new LinkedHashMap<>();
          for (Map.Entry<Long, Map<Integer, JSONObject>> step : events.entrySet()) {
            ScriptBlocking.checkCancelled();
            long span = Math.max(1, step.getKey() - previous);
            GestureDescription.Builder builder = new GestureDescription.Builder();
            Map<Integer, Finger> next = new LinkedHashMap<>();
            for (Map.Entry<Integer, Finger> entry : active.entrySet()) {
              Finger f = entry.getValue();
              JSONObject event = step.getValue().get(entry.getKey());
              float x = event == null ? f.x : (float) event.getDouble("x"),
                  y = event == null ? f.y : (float) event.getDouble("y");
              boolean keep = event == null || event.getInt("action") != 1;
              Path path = new Path();
              path.moveTo(f.x, f.y);
              path.lineTo(x, y);
              f.stroke = f.stroke.continueStroke(path, 0, span, keep);
              f.x = x;
              f.y = y;
              builder.addStroke(f.stroke);
              if (keep) next.put(entry.getKey(), f);
            }
            for (Map.Entry<Integer, JSONObject> entry : step.getValue().entrySet())
              if (entry.getValue().getInt("action") == 0) {
                JSONObject p = entry.getValue();
                Finger f = new Finger((float) p.getDouble("x"), (float) p.getDouble("y"));
                Path path = new Path();
                path.moveTo(f.x, f.y);
                f.stroke =
                    new GestureDescription.StrokeDescription(
                        path, active.isEmpty() ? 0 : span, 1, true);
                builder.addStroke(f.stroke);
                next.put(entry.getKey(), f);
              }
            if (!dispatch(builder.build(), Math.max(1, (deadline - System.nanoTime()) / 1000000L)))
              return false;
            active = next;
            previous = step.getKey();
          }
          if (!active.isEmpty()) {
            GestureDescription.Builder up = new GestureDescription.Builder();
            for (Finger f : active.values()) {
              Path path = new Path();
              path.moveTo(f.x, f.y);
              up.addStroke(f.stroke.continueStroke(path, 0, 1, false));
            }
            return dispatch(up.build(), Math.max(1, (deadline - System.nanoTime()) / 1000000L));
          }
          return true;
        });
  }
}
