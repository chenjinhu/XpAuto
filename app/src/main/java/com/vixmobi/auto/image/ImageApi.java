package com.vixmobi.auto.image;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.AtomicFile;
import android.util.Base64;
import com.vixmobi.auto.runtime.ScriptBlocking;
import com.vixmobi.auto.utils.AbstractShell;
import com.vixmobi.auto.utils.ShellApi;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;

/** 截图、图片存取和内存管理；所有阻塞工作让出 V8，停止脚本可取消授权和等待首帧。 */
public final class ImageApi {
  private final ProjectionCapture capture;
  private final ShellApi shell;
  private final File directory;
  private final Set<Bitmap> images = Collections.newSetFromMap(new IdentityHashMap<>());
  private final ThreadLocal<String> error = ThreadLocal.withInitial(() -> "");
  private volatile boolean cancelled;
  private static final long MAX_IMAGE_BYTES = 128L * 1024 * 1024;

  public ImageApi(Context context, ShellApi shell) {
    capture = new ProjectionCapture(context);
    directory = new File(context.getFilesDir(), "images");
    this.shell = shell;
  }

  public String getErrorMsg() {
    return error.get();
  }

  public String getSaveDir() {
    return directory.getAbsolutePath();
  }

  public void reset() {
    cancelled = false;
    capture.reset();
  }

  public void cancel() {
    cancelled = true;
    capture.cancel();
  }

  public void finish() {
    capture.release();
    recycleAllImage();
  }

  private <T> T operation(T failed, Callable<T> action) {
    return ScriptBlocking.call(
        () -> {
          try {
            ScriptBlocking.checkCancelled();
            if (cancelled) throw new CancellationException("图片操作已取消");
            error.set("");
            return action.call();
          } catch (CancellationException stopped) {
            throw stopped;
          } catch (OutOfMemoryError exhausted) {
            error.set("图片内存不足，请降低图片尺寸并及时回收");
            return failed;
          } catch (Exception failure) {
            error.set(failure.getMessage() == null ? failure.toString() : failure.getMessage());
            return failed;
          }
        });
  }

  public boolean requestScreenCapture(long timeout, int type) {
    return operation(
        false,
        () -> {
          boolean success = capture.request(timeout, type);
          if (!success) error.set(capture.getError());
          return success;
        });
  }

  public void releaseScreenCapture() {
    capture.release();
  }

  public Bitmap captureBitmap(int retries, int x, int y, int ex, int ey) {
    return operation(
        null,
        () -> {
          Bitmap bitmap = null;
          for (int i = 0; i < Math.max(1, retries) && bitmap == null; i++)
            bitmap = capture.capture(1000);
          if (bitmap == null) {
            error.set(capture.getError());
            return null;
          }
          Bitmap result = null;
          try {
            CaptureBounds bounds =
                CaptureBounds.of(bitmap.getWidth(), bitmap.getHeight(), x, y, ex, ey);
            result = Bitmap.createBitmap(bitmap, bounds.x, bounds.y, bounds.width, bounds.height);
            return track(result);
          } finally {
            if (result != bitmap) bitmap.recycle();
          }
        });
  }

  public AutoImage wrap(Bitmap bitmap) {
    return bitmap == null ? null : new AutoImage(bitmap);
  }

  /** Root 命令写入临时 PNG，避免把二进制输出按字符串解码；复用自定义 su 路径和取消机制。 */
  public Bitmap screencapBitmap(boolean root) {
    return operation(
        null,
        () -> {
          if (!root) throw new IllegalStateException("当前没有代理服务，请传 true 使用 Root 截图");
          if (!directory.isDirectory() && !directory.mkdirs())
            throw new IllegalStateException("无法创建图片目录");
          File temporary = File.createTempFile("screencap-", ".png", directory);
          try {
            String quoted = "'" + temporary.getAbsolutePath().replace("'", "'\\''") + "'";
            shell.ROOT("/system/bin/screencap -p > " + quoted, 10000);
            AbstractShell.Result result = shell.getLastResult();
            if (result.code != 0 || result.cancelled || result.timedOut)
              throw new IllegalStateException("Root 截图失败：" + result.error);
            return decode(temporary);
          } finally {
            temporary.delete();
          }
        });
  }

  private Bitmap track(Bitmap bitmap) {
    if (bitmap == null) throw new IllegalArgumentException("无法解码图片");
    synchronized (images) {
      images.removeIf(Bitmap::isRecycled);
      long bytes = 0;
      for (Bitmap item : images) bytes += item.getAllocationByteCount();
      if (cancelled
          || images.size() >= 32
          || bytes + bitmap.getAllocationByteCount() > MAX_IMAGE_BYTES) {
        bitmap.recycle();
        throw new IllegalStateException("图片内存已达上限或脚本已停止，请及时 image.recycle()");
      }
      images.add(bitmap);
    }
    return bitmap;
  }

  private Bitmap bitmap(Object value) {
    Bitmap bitmap =
        value instanceof AutoImage
            ? ((AutoImage) value).bitmap
            : value instanceof Bitmap ? (Bitmap) value : null;
    if (bitmap == null || bitmap.isRecycled()) throw new IllegalArgumentException("图片为空或已经回收");
    return bitmap;
  }

  public int getWidth(Object value) {
    return operation(0, () -> bitmap(value).getWidth());
  }

  public int getHeight(Object value) {
    return operation(0, () -> bitmap(value).getHeight());
  }

  public int pixel(Object value, int x, int y) {
    return operation(0, () -> bitmap(value).getPixel(x, y));
  }

  public boolean isRecycled(Object value) {
    if (value instanceof AutoImage) return ((AutoImage) value).isRecycled();
    return !(value instanceof Bitmap) || ((Bitmap) value).isRecycled();
  }

  public void recycle(Object value) {
    synchronized (images) {
      Bitmap bitmap =
          value instanceof AutoImage
              ? ((AutoImage) value).bitmap
              : value instanceof Bitmap ? (Bitmap) value : null;
      if (bitmap != null) {
        images.remove(bitmap);
        bitmap.recycle();
      }
    }
  }

  public boolean recycleAllImage() {
    synchronized (images) {
      for (Bitmap bitmap : images) bitmap.recycle();
      images.clear();
    }
    return true;
  }

  public Bitmap copyBitmap(Object value) {
    return operation(
        null,
        () -> {
          synchronized (images) {
            return track(bitmap(value).copy(Bitmap.Config.ARGB_8888, false));
          }
        });
  }

  public Bitmap clipBitmap(Object value, int x, int y, int width, int height) {
    return operation(
        null,
        () -> {
          synchronized (images) {
            Bitmap source = bitmap(value);
            Bitmap result = Bitmap.createBitmap(source, x, y, width, height);
            // 裁剪整张图时 Android 可能返回原对象，仍复制一份以保持独立回收语义。
            return track(result == source ? source.copy(Bitmap.Config.ARGB_8888, false) : result);
          }
        });
  }

  private File resolve(String path) throws Exception {
    if (path == null || path.trim().isEmpty()) throw new IllegalArgumentException("图片路径不能为空");
    File file = new File(path);
    if (file.isAbsolute()) return file;
    File resolved = new File(directory, path).getCanonicalFile();
    if (!resolved.getPath().startsWith(directory.getCanonicalPath() + File.separator))
      throw new IllegalArgumentException("相对路径不能越出图片目录");
    return resolved;
  }

  private Bitmap decode(File file) {
    BitmapFactory.Options size = new BitmapFactory.Options();
    size.inJustDecodeBounds = true;
    BitmapFactory.decodeFile(file.getAbsolutePath(), size);
    if (size.outWidth <= 0
        || size.outHeight <= 0
        || (long) size.outWidth * size.outHeight * 4 > MAX_IMAGE_BYTES)
      throw new IllegalArgumentException("图片不存在、无法解码或尺寸过大");
    return track(BitmapFactory.decodeFile(file.getAbsolutePath()));
  }

  public Bitmap readBitmap(String path) {
    return operation(null, () -> decode(resolve(path)));
  }

  private Bitmap.CompressFormat format(String format) {
    if ("png".equalsIgnoreCase(format)) return Bitmap.CompressFormat.PNG;
    if ("jpg".equalsIgnoreCase(format) || "jpeg".equalsIgnoreCase(format))
      return Bitmap.CompressFormat.JPEG;
    if ("webp".equalsIgnoreCase(format)) return Bitmap.CompressFormat.WEBP_LOSSY;
    throw new IllegalArgumentException("图片格式仅支持 png、jpg、webp");
  }

  public boolean saveBitmap(Object value, String format, int quality, String path) {
    return operation(
        false,
        () -> {
          File file = resolve(path), parent = file.getParentFile();
          if (!parent.isDirectory() && !parent.mkdirs())
            throw new IllegalStateException("无法创建图片保存目录");
          AtomicFile target = new AtomicFile(file);
          FileOutputStream output = null;
          try {
            Bitmap.CompressFormat encoding = format(format);
            output = target.startWrite();
            synchronized (images) {
              if (!bitmap(value).compress(encoding, quality, output))
                throw new IllegalStateException("图片编码失败");
            }
            ScriptBlocking.checkCancelled();
            target.finishWrite(output);
            return true;
          } catch (Exception failure) {
            target.failWrite(output);
            throw failure;
          }
        });
  }

  public String bitmapBase64(Object value, String format, int quality) {
    return operation(
        null,
        () -> {
          try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            synchronized (images) {
              if (!bitmap(value).compress(format(format), quality, output))
                throw new IllegalStateException("图片编码失败");
            }
            return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP);
          }
        });
  }
}
