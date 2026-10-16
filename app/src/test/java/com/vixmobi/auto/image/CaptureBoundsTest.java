package com.vixmobi.auto.image;

import static org.junit.Assert.*;

import org.junit.Test;

/** 防止把终点误当宽高，以及越界坐标被静默裁剪。 */
public class CaptureBoundsTest {
  @Test
  public void squareSurfaceKeepsNativePixelsAcrossRotation() {
    CaptureBounds portrait = CaptureBounds.onSurface(1080, 2400, 2400);
    assertEquals(660, portrait.x);
    assertEquals(0, portrait.y);
    assertEquals(1080, portrait.width);
    assertEquals(2400, portrait.height);
    CaptureBounds landscape = CaptureBounds.onSurface(2400, 1080, 2400);
    assertEquals(0, landscape.x);
    assertEquals(660, landscape.y);
    assertEquals(2400, landscape.width);
    assertEquals(1080, landscape.height);
    CaptureBounds window = CaptureBounds.onSurface(500, 1000, 2400);
    assertEquals(600, window.x);
    assertEquals(1200, window.width);
    assertEquals(2400, window.height);
  }

  @Test
  public void endpointsBecomeSize() {
    CaptureBounds bounds = CaptureBounds.of(1080, 2400, 100, 200, 400, 600);
    assertEquals(100, bounds.x);
    assertEquals(200, bounds.y);
    assertEquals(300, bounds.width);
    assertEquals(400, bounds.height);
  }

  @Test
  public void emptyOrNegativeMeansFullScreen() {
    for (int[] coordinates : new int[][] {{-1, 20, 100, 100}, {0, 0, 0, 0}, {100, 200, 50, 250}}) {
      CaptureBounds bounds =
          CaptureBounds.of(
              1080, 2400, coordinates[0], coordinates[1], coordinates[2], coordinates[3]);
      assertEquals(0, bounds.x);
      assertEquals(0, bounds.y);
      assertEquals(1080, bounds.width);
      assertEquals(2400, bounds.height);
    }
  }

  @Test
  public void validButOutOfBoundsIsRejected() {
    assertThrows(
        IllegalArgumentException.class, () -> CaptureBounds.of(1080, 2400, 100, 200, 1081, 500));
    assertThrows(
        IllegalArgumentException.class, () -> CaptureBounds.of(1080, 2400, 100, 200, 500, 2401));
    assertThrows(IllegalArgumentException.class, () -> CaptureBounds.of(0, 0, 0, 0, 1, 1));
  }
}
