package com.vixmobi.auto.image;

/** 截图使用终点坐标；负数、空区域表示全屏，越界的有效区域明确报错。 */
public final class CaptureBounds {
  public final int x, y, width, height;

  private CaptureBounds(int x, int y, int width, int height) {
    this.x = x;
    this.y = y;
    this.width = width;
    this.height = height;
  }

  public static CaptureBounds of(int width, int height, int x, int y, int ex, int ey) {
    if (width <= 0 || height <= 0) throw new IllegalArgumentException("图片尺寸无效");
    if (x < 0 || y < 0 || ex < 0 || ey < 0 || ex <= x || ey <= y)
      return new CaptureBounds(0, 0, width, height);
    if (ex > width || ey > height) throw new IllegalArgumentException("截图区域超出图片范围");
    return new CaptureBounds(x, y, ex - x, ey - y);
  }

  /** 系统把内容等比居中到方形 Surface；返回真实像素所在的区域。 */
  public static CaptureBounds onSurface(int width, int height, int side) {
    if (width <= 0 || height <= 0 || side <= 0) throw new IllegalArgumentException("图片尺寸无效");
    float scale = (float) side / Math.max(width, height);
    int renderedWidth = Math.round(width * scale), renderedHeight = Math.round(height * scale);
    return new CaptureBounds(
        (side - renderedWidth) / 2, (side - renderedHeight) / 2, renderedWidth, renderedHeight);
  }
}
