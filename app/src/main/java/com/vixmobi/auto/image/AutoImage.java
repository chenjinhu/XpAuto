package com.vixmobi.auto.image;

import android.graphics.Bitmap;
import java.util.LinkedHashMap;
import java.util.Map;

/** 脚本图片对象；每次截图独立持有像素，回收一张图不会使其他截图失效。 */
public final class AutoImage {
  final Bitmap bitmap;

  AutoImage(Bitmap bitmap) {
    this.bitmap = bitmap;
  }

  public int getWidth() {
    return bitmap.getWidth();
  }

  public int getHeight() {
    return bitmap.getHeight();
  }

  public boolean isRecycled() {
    return bitmap.isRecycled();
  }

  public void recycle() {
    bitmap.recycle();
  }

  public Map<String, Object> toJSON(String key) {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("width", getWidth());
    value.put("height", getHeight());
    value.put("recycled", isRecycled());
    return value;
  }

  @Override
  public String toString() {
    return "AutoImage(" + getWidth() + "×" + getHeight() + ", recycled=" + isRecycled() + ")";
  }
}
