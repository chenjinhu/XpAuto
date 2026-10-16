package com.vixmobi.auto.image;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.view.Display;
import android.view.WindowManager;
import com.vixmobi.auto.runtime.ScriptBlocking;
import java.nio.ByteBuffer;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/** 一次授权对应一个投影；只保留最新原始帧，真正截图时才转换 Bitmap。 */
final class ProjectionCapture {
  private final Context context;
  private final ReentrantLock requestLock = new ReentrantLock();
  private volatile CapturePermissionActivity.Request request;
  private volatile boolean cancelled;
  private volatile String error = "";
  private MediaProjection projection;
  private VirtualDisplay display;
  private ImageReader reader;
  private Image latest;
  private HandlerThread thread;
  private CaptureService service;
  private int width, height, frameSide;
  private ByteBuffer pixels;
  private DisplayManager.DisplayListener displayListener;
  private MediaProjection.Callback callback;

  ProjectionCapture(Context context) {
    this.context = context.getApplicationContext();
  }

  String getError() {
    return error;
  }

  void reset() {
    cancelled = false;
  }

  boolean request(long timeout, int type) {
    if (type == 2) {
      error = "尚未提供代理截图模式；请使用 type=0/1 授权截图，或 screencapImage(true) 使用 Root";
      return false;
    }
    if (!requestLock.tryLock()) {
      error = "已有截图授权请求正在处理";
      return false;
    }
    CapturePermissionActivity.Request current = null;
    try {
      checkCancelled();
      synchronized (this) {
        if (projection != null) {
          error = "";
          return true;
        }
      }
      current = new CapturePermissionActivity.Request(this);
      if (!CapturePermissionActivity.pending.compareAndSet(null, current)) {
        error = "已有截图授权页面打开";
        return false;
      }
      request = current;
      context.startActivity(
          new Intent(context, CapturePermissionActivity.class)
              .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
              .putExtra("requestId", current.id));
      long end = SystemClock.elapsedRealtime() + timeout;
      while (!current.done.await(25, TimeUnit.MILLISECONDS)) {
        checkCancelled();
        if (SystemClock.elapsedRealtime() >= end) {
          error = "等待截图授权超时";
          return false;
        }
      }
      checkCancelled();
      error = current.error;
      return current.granted;
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new CancellationException("截图授权已中断");
    } finally {
      if (current != null) {
        current.cancelled = true;
        if (!current.granted) release();
        current.complete(current.granted, current.error);
        CapturePermissionActivity.pending.compareAndSet(current, null);
        request = null;
      }
      requestLock.unlock();
    }
  }

  synchronized boolean start(
      CapturePermissionActivity.Request current,
      int result,
      Intent data,
      CaptureService captureService) {
    if (cancelled || current.cancelled || request != current) {
      error = "截图请求已结束";
      return false;
    }
    try {
      service = captureService;
      thread = new HandlerThread("XpAuto-capture");
      thread.start();
      Handler handler = new Handler(thread.getLooper());
      projection =
          context.getSystemService(MediaProjectionManager.class).getMediaProjection(result, data);
      if (projection == null) throw new IllegalStateException("系统未授予截图权限");
      MediaProjection ownedProjection = projection;
      callback =
          new MediaProjection.Callback() {
            @Override
            public void onStop() {
              synchronized (ProjectionCapture.this) {
                if (projection != ownedProjection) return;
                error = "系统已停止屏幕共享，请重新申请截图权限";
                release();
              }
            }

            @Override
            public void onCapturedContentResize(int w, int h) {
              synchronized (ProjectionCapture.this) {
                if (projection != ownedProjection || display == null) return;
                try {
                  resize(w, h, handler);
                } catch (RuntimeException failure) {
                  error = failure.toString();
                  release();
                }
              }
            }
          };
      projection.registerCallback(callback, handler);
      Rect bounds =
          context.getSystemService(WindowManager.class).getMaximumWindowMetrics().getBounds();
      setReader(bounds.width(), bounds.height(), handler);
      display =
          projection.createVirtualDisplay(
              "XpAuto screenshot",
              frameSide,
              frameSide,
              context.getResources().getConfiguration().densityDpi,
              DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
              reader.getSurface(),
              null,
              handler);
      if (Build.VERSION.SDK_INT < 34) {
        displayListener =
            new DisplayManager.DisplayListener() {
              @Override
              public void onDisplayAdded(int id) {}

              @Override
              public void onDisplayRemoved(int id) {}

              @Override
              public void onDisplayChanged(int id) {
                if (id != Display.DEFAULT_DISPLAY) return;
                synchronized (ProjectionCapture.this) {
                  if (projection != ownedProjection || display == null) return;
                  Rect size =
                      context
                          .getSystemService(WindowManager.class)
                          .getMaximumWindowMetrics()
                          .getBounds();
                  try {
                    resize(size.width(), size.height(), handler);
                  } catch (RuntimeException failure) {
                    error = failure.toString();
                    release();
                  }
                }
              }
            };
        context
            .getSystemService(DisplayManager.class)
            .registerDisplayListener(displayListener, handler);
      }
      error = "";
      return true;
    } catch (RuntimeException failure) {
      error = failure.toString();
      release();
      return false;
    }
  }

  private void resize(int w, int h, Handler handler) {
    if (w == width && h == height) return;
    if (w <= 0 || h <= 0) return;
    if (w <= frameSide && h <= frameSide) {
      // 保持 Surface 稳定。系统按原始像素居中绘制，截图时去掉留白，横竖屏无需换 Surface。
      width = w;
      height = h;
      return;
    }
    // 折叠屏展开或分辨率增大才扩容，普通旋转不会走到这里。
    display.setSurface(null);
    setReader(w, h, handler);
    display.resize(frameSide, frameSide, context.getResources().getConfiguration().densityDpi);
    display.setSurface(reader.getSurface());
  }

  private void setReader(int w, int h, Handler handler) {
    int side = Math.max(w, h);
    if (w <= 0 || h <= 0 || (long) side * side > 32_000_000)
      throw new IllegalArgumentException("屏幕尺寸过大或无效");
    if (latest != null) {
      latest.close();
      latest = null;
    }
    if (reader != null) {
      reader.close();
      reader = null;
    }
    width = w;
    height = h;
    frameSide = side;
    pixels = ByteBuffer.allocateDirect(w * h * 4);
    // 固定正方形帧容纳两个方向，避免 Android 14+ 切回旧尺寸时 ImageReader 不再送帧。
    // 长边始终为实际屏幕长边，系统居中绘制时比例为 1，不缩小截图像素。
    reader = ImageReader.newInstance(side, side, PixelFormat.RGBA_8888, 3);
    reader.setOnImageAvailableListener(
        source -> {
          synchronized (ProjectionCapture.this) {
            if (source != reader) return;
            Image next = source.acquireLatestImage();
            if (next != null) {
              if (latest != null) latest.close();
              latest = next;
              ProjectionCapture.this.notifyAll();
            }
          }
        },
        handler);
  }

  synchronized Bitmap capture(long timeout) {
    long end = SystemClock.elapsedRealtime() + timeout;
    while (true) {
      checkCancelled();
      if (projection == null) {
        if (error.isEmpty()) error = "请先调用 image.requestScreenCapture()";
        return null;
      }
      if (latest != null) {
        // 行末可能有对齐填充，逐行拷贝到紧凑缓冲，避免黑边及读到最后一行之外。
        Image.Plane plane = latest.getPlanes()[0];
        if (plane.getPixelStride() != 4) throw new IllegalStateException("不支持的截图像素格式");
        ByteBuffer source = plane.getBuffer().duplicate();
        CaptureBounds area = CaptureBounds.onSurface(width, height, frameSide);
        // 复用行对齐缓冲；单应用共享被系统放大时先去除留白，再还原内容尺寸。
        if (pixels.capacity() != area.width * area.height * 4)
          pixels = ByteBuffer.allocateDirect(area.width * area.height * 4);
        ByteBuffer packed = pixels;
        packed.clear();
        int base = source.position() + area.y * plane.getRowStride() + area.x * 4;
        for (int y = 0; y < area.height; y++) {
          source.limit(source.capacity());
          source.position(base + y * plane.getRowStride());
          source.limit(source.position() + area.width * 4);
          packed.put(source);
        }
        packed.flip();
        Bitmap result = Bitmap.createBitmap(area.width, area.height, Bitmap.Config.ARGB_8888);
        result.copyPixelsFromBuffer(packed);
        if (area.width != width || area.height != height) {
          Bitmap original = result;
          try {
            result = Bitmap.createScaledBitmap(original, width, height, true);
          } finally {
            original.recycle();
          }
        }
        error = "";
        return result;
      }
      if (SystemClock.elapsedRealtime() >= end) {
        error = "等待屏幕首帧超时";
        return null;
      }
      try {
        wait(25);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw new CancellationException("截图已中断");
      }
    }
  }

  void cancel() {
    cancelled = true;
    release();
  }

  private void checkCancelled() {
    ScriptBlocking.checkCancelled();
    if (cancelled) throw new CancellationException("截图操作已取消");
  }

  synchronized void release() {
    CapturePermissionActivity.Request current = request;
    if (current != null && current.done.getCount() != 0) {
      current.cancelled = true;
      current.complete(false, "截图授权请求已取消");
    }
    MediaProjection old = projection;
    projection = null;
    if (old != null && callback != null) old.unregisterCallback(callback);
    callback = null;
    if (display != null) {
      display.release();
      display = null;
    }
    if (latest != null) {
      latest.close();
      latest = null;
    }
    if (reader != null) {
      reader.close();
      reader = null;
    }
    pixels = null;
    if (displayListener != null) {
      context.getSystemService(DisplayManager.class).unregisterDisplayListener(displayListener);
      displayListener = null;
    }
    if (old != null) old.stop();
    if (thread != null) {
      thread.quitSafely();
      thread = null;
    }
    CaptureService oldService = service;
    service = null;
    if (oldService != null) oldService.stopSelf();
    notifyAll();
  }

  synchronized void serviceDestroyed(CaptureService destroyed) {
    if (service == destroyed) {
      error = "截图服务已结束";
      release();
    }
  }
}
