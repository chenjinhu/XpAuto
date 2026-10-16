package com.vixmobi.auto.toolbox;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.media.MediaPlayer;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.vixmobi.auto.runtime.ScriptBlocking;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.EncryptionMethod;
import org.json.JSONArray;
import org.json.JSONObject;

/** Android 工具入口；文件和媒体资源归当前脚本会话所有。 */
public final class UtilityApi implements AutoCloseable {
  private final Context context;
  private final Consumer<String> errors;
  private final Handler main = new Handler(Looper.getMainLooper());
  private MediaPlayer player;

  private static final class Playback {
    final CountDownLatch done = new CountDownLatch(1);
    volatile boolean ok = true;
  }

  private Playback playback;

  public UtilityApi(Context context, Consumer<String> errors) {
    this.context = context;
    this.errors = errors;
  }

  private boolean failed(Exception error) {
    errors.accept("utils: " + error.getMessage());
    return false;
  }

  private <T> T onMain(Callable<T> action) {
    return ScriptBlocking.call(
        () -> {
          if (Looper.myLooper() == Looper.getMainLooper()) return action.call();
          FutureTask<T> task = new FutureTask<>(action);
          main.post(task);
          try {
            return task.get(10, TimeUnit.SECONDS);
          } finally {
            main.removeCallbacks(task);
            task.cancel(false);
          }
        });
  }

  private Intent launch(String pkg) {
    return context.getPackageManager().getLaunchIntentForPackage(pkg);
  }

  public boolean openApp(String pkg) {
    try {
      Intent intent = launch(pkg);
      if (intent == null) return false;
      context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      return true;
    } catch (Exception error) {
      return failed(error);
    }
  }

  public String getStartAppCmd(String pkg) {
    Intent intent = launch(pkg);
    return intent == null || intent.getComponent() == null
        ? ""
        : "am start -n " + quote(intent.getComponent().flattenToString());
  }

  public boolean openAppByName(String name) {
    Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
    for (ResolveInfo item : context.getPackageManager().queryIntentActivities(intent, 0)) {
      if (name.equals(item.loadLabel(context.getPackageManager()).toString()))
        return openApp(item.activityInfo.packageName);
    }
    return false;
  }

  private Intent intent(JSONObject options) throws Exception {
    Intent intent = new Intent(options.optString("action", Intent.ACTION_VIEW));
    String pkg = options.optString("pkg"), clz = options.optString("className");
    if (!clz.isEmpty()) {
      if (pkg.isEmpty()) throw new IllegalArgumentException("className 需要同时提供 pkg");
      intent.setComponent(new ComponentName(pkg, clz.startsWith(".") ? pkg + clz : clz));
    } else if (!pkg.isEmpty()) intent.setPackage(pkg);
    Uri uri = options.has("uri") ? Uri.parse(options.getString("uri")) : null;
    intent.setDataAndType(uri, options.optString("type", null));
    intent.addFlags(options.optInt("flag", 0) | Intent.FLAG_ACTIVITY_NEW_TASK);
    Iterator<String> keys = options.keys();
    while (keys.hasNext()) {
      String key = keys.next();
      if (Arrays.asList("action", "uri", "type", "pkg", "className", "flag").contains(key))
        continue;
      Object value = options.get(key);
      if (value instanceof Boolean) intent.putExtra(key, (Boolean) value);
      else if (value instanceof Integer) intent.putExtra(key, (Integer) value);
      else if (value instanceof Long) intent.putExtra(key, (Long) value);
      else if (value instanceof Number) intent.putExtra(key, ((Number) value).doubleValue());
      else if (value != JSONObject.NULL) intent.putExtra(key, value.toString());
    }
    return intent;
  }

  public boolean openActivityJson(String json) {
    try {
      context.startActivity(intent(new JSONObject(json)));
      return true;
    } catch (Exception error) {
      return failed(error);
    }
  }

  public String getStartActivityCmdJson(String json) {
    try {
      return "am start " + quote(intent(new JSONObject(json)).toUri(Intent.URI_INTENT_SCHEME));
    } catch (Exception error) {
      failed(error);
      return "";
    }
  }

  public boolean openIntentAction(String action) {
    try {
      context.startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      return true;
    } catch (Exception error) {
      return failed(error);
    }
  }

  public static String quote(String text) {
    return "'" + text.replace("'", "'\"'\"'") + "'";
  }

  private boolean scan(String path) {
    if (!new File(path).isFile()) return false;
    return ScriptBlocking.call(
        () -> {
          CountDownLatch done = new CountDownLatch(1);
          boolean[] ok = {false};
          MediaScannerConnection.scanFile(
              context,
              new String[] {path},
              null,
              (file, uri) -> {
                ok[0] = uri != null;
                done.countDown();
              });
          long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
          while (!done.await(25, TimeUnit.MILLISECONDS)) {
            ScriptBlocking.checkCancelled();
            if (System.nanoTime() > end) return false;
          }
          return ok[0];
        });
  }

  public boolean insertImageToAlbum(String path) {
    return scan(path);
  }

  public boolean insertVideoToAlbum(String path) {
    return scan(path);
  }

  public boolean zipJson(String target, String password, String filesJson) {
    return ScriptBlocking.call(
        () -> {
          File output = new File(target), temporary = null;
          try {
            JSONArray files = new JSONArray(filesJson);
            if (files.length() == 0) throw new IllegalArgumentException("压缩文件列表不能为空");
            File parent = output.getAbsoluteFile().getParentFile();
            if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("无法创建压缩目录");
            temporary = File.createTempFile("xpauto-zip-", ".zip", parent);
            temporary.delete();
            try (ZipFile zip = new ZipFile(temporary, password.toCharArray())) {
              zip.setRunInThread(true);
              ZipParameters parameters = new ZipParameters();
              if (!password.isEmpty()) {
                parameters.setEncryptFiles(true);
                parameters.setEncryptionMethod(EncryptionMethod.AES);
              }
              for (int i = 0; i < files.length(); i++) {
                ScriptBlocking.checkCancelled();
                File input = new File(files.getString(i));
                if (!input.exists()) throw new FileNotFoundException(input.toString());
                if (input.isDirectory()
                    && output
                        .getCanonicalPath()
                        .startsWith(input.getCanonicalPath() + File.separator))
                  throw new IllegalArgumentException("ZIP 输出不能位于输入目录中");
                if (input.isDirectory()) zip.addFolder(input, parameters);
                else zip.addFile(input, parameters);
                awaitZip(zip);
              }
            }
            java.nio.file.Files.move(
                temporary.toPath(),
                output.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return true;
          } catch (Exception error) {
            return failed(error);
          } finally {
            if (temporary != null) temporary.delete();
          }
        });
  }

  public boolean unzip(String file, String password, String destination) {
    return unzipWithEncode(file, password, destination, "UTF-8");
  }

  /** Zip4j 在复制大文件时检查取消标志，等待它释放文件后才删除临时 ZIP。 */
  private void awaitZip(ZipFile zip) throws Exception {
    net.lingala.zip4j.progress.ProgressMonitor monitor = zip.getProgressMonitor();
    boolean cancelled = false;
    while (monitor.getState() == net.lingala.zip4j.progress.ProgressMonitor.State.BUSY) {
      if (com.vixmobi.auto.Constant.killThread || Thread.interrupted()) {
        cancelled = true;
        monitor.setCancelAllTasks(true);
      }
      try {
        Thread.sleep(20);
      } catch (InterruptedException error) {
        cancelled = true;
        monitor.setCancelAllTasks(true);
      }
    }
    if (cancelled) {
      Thread.currentThread().interrupt();
      throw new java.util.concurrent.CancellationException("压缩已取消");
    }
    if (monitor.getResult() != net.lingala.zip4j.progress.ProgressMonitor.Result.SUCCESS) {
      throw new IOException("压缩失败", monitor.getException());
    }
  }

  public boolean unzipWithEncode(
      String file, String password, String destination, String encoding) {
    return ScriptBlocking.call(
        () -> {
          try (ZipFile zip = new ZipFile(file, password.toCharArray())) {
            zip.setCharset(Charset.forName(encoding));
            File root = new File(destination).getCanonicalFile();
            long total = 0;
            int count = 0;
            // 先完整校验路径和大小，再写出，防止穿越目录或压缩炸弹。
            for (FileHeader header : zip.getFileHeaders()) {
              String path = new File(root, header.getFileName()).getCanonicalPath();
              if (!path.startsWith(root.getPath() + File.separator))
                throw new IOException("ZIP 含越界路径");
              byte[] attributes = header.getExternalFileAttributes();
              if (attributes != null && attributes.length > 3 && (attributes[3] & 0xf0) == 0xa0)
                throw new IOException("ZIP 不允许符号链接");
              total += header.getUncompressedSize();
              if (++count > 100000
                  || total > 1024L * 1024 * 1024
                  || header.getUncompressedSize() < 0)
                throw new IOException("ZIP 超过 1 GB 或 100000 文件限制");
            }
            long written = 0;
            for (FileHeader header : zip.getFileHeaders()) {
              ScriptBlocking.checkCancelled();
              File target = new File(root, header.getFileName()).getCanonicalFile();
              if (!target.getPath().startsWith(root.getPath() + File.separator))
                throw new IOException("ZIP 含越界路径");
              if (header.isDirectory()) {
                if (!target.isDirectory() && !target.mkdirs()) throw new IOException("无法创建解压目录");
                continue;
              }
              File parent = target.getParentFile();
              if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("无法创建解压目录");
              File temporary = File.createTempFile("xpauto-unzip-", ".tmp", parent);
              try {
                try (InputStream input = zip.getInputStream(header);
                    OutputStream output = new FileOutputStream(temporary)) {
                  byte[] buffer = new byte[65536];
                  int size;
                  while ((size = input.read(buffer)) != -1) {
                    ScriptBlocking.checkCancelled();
                    written += size;
                    if (written > 1024L * 1024 * 1024) throw new IOException("ZIP 实际内容超过 1 GB");
                    output.write(buffer, 0, size);
                  }
                }
                java.nio.file.Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
              } finally {
                temporary.delete();
              }
            }
            return true;
          } catch (Exception error) {
            return failed(error);
          }
        });
  }

  public String readFileInZip(String file, String password, String entry) {
    return ScriptBlocking.call(
        () -> {
          try (ZipFile zip = new ZipFile(file, password.toCharArray())) {
            FileHeader header = zip.getFileHeader(entry);
            if (header == null || header.isDirectory()) return null;
            try (InputStream input = zip.getInputStream(header);
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
              byte[] buffer = new byte[8192];
              int count;
              while ((count = input.read(buffer)) >= 0) {
                ScriptBlocking.checkCancelled();
                if (out.size() + count > 16 * 1024 * 1024) throw new IOException("文本超过 16 MB");
                out.write(buffer, 0, count);
              }
              return out.toString("UTF-8");
            }
          } catch (Exception error) {
            failed(error);
            return null;
          }
        });
  }

  public Bitmap createQRCode(String content, int width, int height) {
    return createQRCode(content, width, height, null);
  }

  public Bitmap createQRCode(String content, int width, int height, Bitmap logo) {
    try {
      if (width < 32 || height < 32 || width > 4096 || height > 4096)
        throw new IllegalArgumentException("二维码尺寸应为 32～4096 像素");
      Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
      hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
      hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
      BitMatrix matrix =
          new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, width, height, hints);
      Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
      int[] pixels = new int[width * height];
      for (int y = 0; y < height; y++)
        for (int x = 0; x < width; x++)
          pixels[y * width + x] = matrix.get(x, y) ? 0xff000000 : 0xffffffff;
      bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
      if (logo != null && !logo.isRecycled()) {
        int size = Math.min(width, height) / 5;
        new Canvas(bitmap)
            .drawBitmap(
                logo,
                null,
                new Rect(
                    (width - size) / 2,
                    (height - size) / 2,
                    (width + size) / 2,
                    (height + size) / 2),
                null);
      }
      return bitmap;
    } catch (Exception error) {
      failed(error);
      return null;
    }
  }

  public String decodeQRCode(Bitmap source) {
    try {
      int w = source.getWidth(), h = source.getHeight();
      if ((long) w * h > 16777216) throw new IllegalArgumentException("图像过大");
      int[] pixels = new int[w * h];
      source.getPixels(pixels, 0, w, 0, 0, w, h);
      return new MultiFormatReader()
          .decode(new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(w, h, pixels))))
          .getText();
    } catch (Exception error) {
      failed(error);
      return null;
    }
  }

  /** 额外提供 PNG 保存，二维码无需另一个尚未实现的图像模块即可使用。 */
  public boolean saveBitmap(Bitmap bitmap, String path) {
    try (OutputStream output = new FileOutputStream(path)) {
      return bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
    } catch (Exception error) {
      return failed(error);
    }
  }

  public String dataMd5(String data) {
    try {
      return hex(MessageDigest.getInstance("MD5").digest(data.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception error) {
      failed(error);
      return null;
    }
  }

  public String fileMd5(String path) {
    return ScriptBlocking.call(
        () -> {
          try (InputStream input = new FileInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[65536];
            int count;
            while ((count = input.read(buffer)) >= 0) {
              ScriptBlocking.checkCancelled();
              digest.update(buffer, 0, count);
            }
            return hex(digest.digest());
          } catch (Exception error) {
            failed(error);
            return null;
          }
        });
  }

  private static String hex(byte[] bytes) {
    StringBuilder out = new StringBuilder();
    for (byte b : bytes) out.append(String.format(Locale.ROOT, "%02x", b & 255));
    return out.toString();
  }

  private PackageInfo packageInfo(String pkg) {
    try {
      return context.getPackageManager().getPackageInfo(pkg, 0);
    } catch (Exception ignored) {
      return null;
    }
  }

  public String getApkPkgName(String path) {
    PackageInfo info = context.getPackageManager().getPackageArchiveInfo(path, 0);
    return info == null ? "" : info.packageName;
  }

  public boolean isAppExist(String pkg) {
    return packageInfo(pkg) != null;
  }

  public long getAppVersionCode(String pkg) {
    PackageInfo info = packageInfo(pkg);
    return info == null ? 0 : info.getLongVersionCode();
  }

  public String getAppVersionName(String pkg) {
    PackageInfo info = packageInfo(pkg);
    return info == null || info.versionName == null ? "" : info.versionName;
  }

  public boolean setClipboardText(String text) {
    return onMain(
        () -> {
          context
              .getSystemService(ClipboardManager.class)
              .setPrimaryClip(ClipData.newPlainText("XpAuto", text));
          return true;
        });
  }

  public String getClipboardText() {
    return onMain(
        () -> {
          ClipData clip = context.getSystemService(ClipboardManager.class).getPrimaryClip();
          return clip == null || clip.getItemCount() == 0
              ? ""
              : String.valueOf(clip.getItemAt(0).coerceToText(context));
        });
  }

  public boolean playMp3(String path, boolean loop) {
    return startPlayback(path, loop) != null;
  }

  private Playback startPlayback(String path, boolean loop) {
    try {
      return onMain(
          () -> {
            ScriptBlocking.checkCancelled();
            stopOnMain();
            if (!new File(path).isFile()) {
              failed(new FileNotFoundException(path));
              return null;
            }
            Playback request = new Playback();
            playback = request;
            MediaPlayer next = new MediaPlayer();
            player = next;
            try {
              next.setDataSource(path);
              next.setLooping(loop);
              next.setOnCompletionListener(
                  value -> {
                    if (player == value) stopOnMain();
                  });
              next.setOnErrorListener(
                  (value, what, extra) -> {
                    request.ok = false;
                    if (player == value) stopOnMain();
                    return true;
                  });
              next.prepare();
              next.start();
              return request;
            } catch (Exception error) {
              stopOnMain();
              failed(error);
              return null;
            }
          });
    } catch (Exception error) {
      failed(error);
      return null;
    }
  }

  public boolean playMp3WaitEnd(String path, boolean loop) {
    Playback current = startPlayback(path, loop);
    if (current == null) return false;
    return ScriptBlocking.call(
        () -> {
          while (!current.done.await(25, TimeUnit.MILLISECONDS)) ScriptBlocking.checkCancelled();
          return current.ok;
        });
  }

  private void stopOnMain() {
    if (player != null) {
      player.release();
      player = null;
    }
    if (playback != null) {
      playback.done.countDown();
      playback = null;
    }
  }

  public boolean stopMp3() {
    return onMain(
        () -> {
          stopOnMain();
          return true;
        });
  }

  @Override
  public void close() {
    main.post(this::stopOnMain);
  }
}
