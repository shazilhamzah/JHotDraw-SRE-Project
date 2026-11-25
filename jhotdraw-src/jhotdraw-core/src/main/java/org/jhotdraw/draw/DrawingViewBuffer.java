package org.jhotdraw.draw;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.awt.image.VolatileImage;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Manages offscreen double-buffering (both volatile and nonvolatile) and dirty-region tracking for
 * {@link DefaultDrawingView}.
 */
class DrawingViewBuffer {

  private static final Color TRANSPARENT_CLEAR = new Color(0x0, true);
  private static final boolean IS_WINDOWS = detectWindowsOs();

  private boolean doubleBuffered = true;
  private VolatileImage volatileBuffer;
  private BufferedImage nonvolatileBuffer;
  private final Rectangle bufferedArea = new Rectangle();
  private final Rectangle dirtyArea = new Rectangle(0, 0, -1, -1);

  private static boolean detectWindowsOs() {
    try {
      String osName = System.getProperty("os.name");
      return osName != null && osName.toLowerCase(Locale.ROOT).startsWith("win");
    } catch (SecurityException e) {
      // Fall back to non-Windows volatile buffering when system property access is restricted.
      return false;
    }
  }

  boolean isDoubleBuffered() {
    return doubleBuffered;
  }

  void setDoubleBuffered(boolean newValue) {
    this.doubleBuffered = newValue;
    if (!newValue) {
      flush();
    }
  }

  void flush() {
    flushVolatile();
    flushNonvolatile();
  }

  private void flushVolatile() {
    if (volatileBuffer != null) {
      volatileBuffer.flush();
      volatileBuffer = null;
    }
  }

  private void flushNonvolatile() {
    if (nonvolatileBuffer != null) {
      nonvolatileBuffer.flush();
      nonvolatileBuffer = null;
    }
  }

  void markDirty(Rectangle area) {
    if (area != null) {
      dirtyArea.add(area);
    }
  }

  void markBufferedAreaDirty() {
    dirtyArea.add(bufferedArea);
  }

  void resetDirtyToBufferedArea() {
    dirtyArea.setBounds(bufferedArea);
  }

  void translateBufferedArea(int dx, int dy) {
    bufferedArea.translate(dx, dy);
  }

  Rectangle getBufferedArea() {
    return new Rectangle(bufferedArea);
  }

  Rectangle getDirtyArea() {
    return new Rectangle(dirtyArea);
  }

  /**
   * Returns true if the nonvolatile ({@link BufferedImage}) buffer should be used instead of the
   * volatile buffer. Nonvolatile buffering is preferred on Windows.
   */
  boolean prefersNonvolatileBuffer() {
    return IS_WINDOWS;
  }

  void drawVolatile(
      Graphics2D g,
      Rectangle visibleRect,
      GraphicsConfiguration gc,
      Consumer<Graphics2D> hintsApplier,
      Consumer<Graphics2D> drawingRenderer) {
    if (visibleRect.width <= 0 || visibleRect.height <= 0) {
      return;
    }
    if (gc == null) {
      drawingRenderer.accept(g);
      return;
    }

    Point shift = prepareBufferedArea(visibleRect, true);
    while (true) {
      int valid =
          (volatileBuffer == null) ? VolatileImage.IMAGE_INCOMPATIBLE : volatileBuffer.validate(gc);
      if (valid == VolatileImage.IMAGE_INCOMPATIBLE) {
        try {
          volatileBuffer = gc.createCompatibleVolatileImage(
              visibleRect.width, visibleRect.height, Transparency.TRANSLUCENT);
        } catch (OutOfMemoryError e) {
          volatileBuffer = null;
        }
        dirtyArea.setBounds(bufferedArea);
      } else if (valid == VolatileImage.IMAGE_RESTORED) {
        dirtyArea.setBounds(bufferedArea);
      }

      if (volatileBuffer == null) {
        drawingRenderer.accept(g);
        return;
      }

      if (!dirtyArea.isEmpty()) {
        Graphics2D gBuf = volatileBuffer.createGraphics();
        renderDirtyRegion(
            gBuf,
            volatileBuffer.getWidth(),
            volatileBuffer.getHeight(),
            shift,
            hintsApplier,
            drawingRenderer);
        gBuf.dispose();
      }

      if (!volatileBuffer.contentsLost()) {
        g.drawImage(volatileBuffer, bufferedArea.x, bufferedArea.y, null);
        dirtyArea.setSize(-1, -1);
        return;
      }
      dirtyArea.setBounds(bufferedArea);
    }
  }

  void drawNonvolatile(
      Graphics2D g,
      Rectangle visibleRect,
      GraphicsConfiguration gc,
      Consumer<Graphics2D> hintsApplier,
      Consumer<Graphics2D> drawingRenderer) {
    if (visibleRect.width <= 0 || visibleRect.height <= 0) {
      return;
    }
    if (gc == null) {
      drawingRenderer.accept(g);
      return;
    }

    Point shift = prepareBufferedArea(visibleRect, false);
    if (nonvolatileBuffer == null) {
      try {
        nonvolatileBuffer = gc.createCompatibleImage(
            visibleRect.width, visibleRect.height, Transparency.TRANSLUCENT);
      } catch (OutOfMemoryError e) {
        nonvolatileBuffer = null;
      }
      dirtyArea.setBounds(bufferedArea);
    }

    if (nonvolatileBuffer == null) {
      drawingRenderer.accept(g);
      return;
    }

    if (!dirtyArea.isEmpty()) {
      Graphics2D gBuf = nonvolatileBuffer.createGraphics();
      renderDirtyRegion(
          gBuf,
          nonvolatileBuffer.getWidth(),
          nonvolatileBuffer.getHeight(),
          shift,
          hintsApplier,
          drawingRenderer);
      gBuf.dispose();
    }

    g.drawImage(nonvolatileBuffer, bufferedArea.x, bufferedArea.y, null);
    dirtyArea.setSize(-1, -1);
  }

  private Point prepareBufferedArea(Rectangle vr, boolean isVolatile) {
    Point shift = new Point(0, 0);
    if (bufferedArea.contains(vr)
        || (bufferedArea.width >= vr.width && bufferedArea.height >= vr.height)) {
      shift.x = bufferedArea.x - vr.x;
      shift.y = bufferedArea.y - vr.y;
      if (shift.x > 0) {
        dirtyArea.add(new Rectangle(
            bufferedArea.x - shift.x,
            vr.y,
            shift.x + bufferedArea.width - vr.width,
            bufferedArea.height));
      } else if (shift.x < 0) {
        dirtyArea.add(new Rectangle(
            bufferedArea.x + vr.width,
            vr.y,
            -shift.x + bufferedArea.width - vr.width,
            bufferedArea.height));
      }
      if (shift.y > 0) {
        dirtyArea.add(new Rectangle(
            vr.x,
            bufferedArea.y - shift.y,
            bufferedArea.width,
            shift.y + bufferedArea.height - vr.height));
      } else if (shift.y < 0) {
        dirtyArea.add(new Rectangle(
            vr.x,
            bufferedArea.y + vr.height,
            bufferedArea.width,
            -shift.y + bufferedArea.height - vr.height));
      }
      bufferedArea.x = vr.x;
      bufferedArea.y = vr.y;
    } else {
      bufferedArea.setBounds(vr);
      dirtyArea.setBounds(vr);
      Image existing = isVolatile ? volatileBuffer : nonvolatileBuffer;
      if (existing != null
          && (existing.getWidth(null) != vr.width || existing.getHeight(null) != vr.height)) {
        if (isVolatile) {
          flushVolatile();
        } else {
          flushNonvolatile();
        }
      }
    }
    return shift;
  }

  private void renderDirtyRegion(
      Graphics2D gBuf,
      int bufferWidth,
      int bufferHeight,
      Point shift,
      Consumer<Graphics2D> hintsApplier,
      Consumer<Graphics2D> drawingRenderer) {
    hintsApplier.accept(gBuf);
    gBuf.setComposite(AlphaComposite.Src);
    if (shift.x != 0 || shift.y != 0) {
      gBuf.copyArea(
          Math.max(0, -shift.x),
          Math.max(0, -shift.y),
          bufferWidth - Math.abs(shift.x),
          bufferHeight - Math.abs(shift.y),
          shift.x,
          shift.y);
      shift.x = 0;
      shift.y = 0;
    }
    gBuf.translate(-bufferedArea.x, -bufferedArea.y);
    gBuf.clip(dirtyArea);
    gBuf.setBackground(TRANSPARENT_CLEAR);
    gBuf.clearRect(dirtyArea.x, dirtyArea.y, dirtyArea.width, dirtyArea.height);
    gBuf.setComposite(AlphaComposite.SrcOver);
    drawingRenderer.accept(gBuf);
  }
}
