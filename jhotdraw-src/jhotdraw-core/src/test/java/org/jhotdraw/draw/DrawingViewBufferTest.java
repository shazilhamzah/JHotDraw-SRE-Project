package org.jhotdraw.draw;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link DrawingViewBuffer}. */
public class DrawingViewBufferTest {

  @Test
  void doubleBufferedFlagAndFlushLifecycle() {
    DrawingViewBuffer buffer = new DrawingViewBuffer();
    assertThat(buffer.isDoubleBuffered()).isTrue();

    buffer.markDirty(new Rectangle(10, 20, 30, 40));
    assertThat(buffer.getDirtyArea().contains(new Rectangle(10, 20, 30, 40))).isTrue();

    buffer.translateBufferedArea(5, -5);
    assertThat(buffer.getBufferedArea().getLocation()).isEqualTo(new java.awt.Point(5, -5));

    buffer.setDoubleBuffered(false);
    assertThat(buffer.isDoubleBuffered()).isFalse();
    buffer.setDoubleBuffered(true);
    assertThat(buffer.isDoubleBuffered()).isTrue();
  }

  @Test
  void zeroDimensionVisibleRectSkipsRenderingSafely() {
    DrawingViewBuffer buffer = new DrawingViewBuffer();
    BufferedImage target = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = target.createGraphics();
    AtomicInteger renderCalls = new AtomicInteger();

    buffer.drawNonvolatile(
        g, new Rectangle(0, 0, 0, 20), null, gr -> {}, gr -> renderCalls.incrementAndGet());
    buffer.drawVolatile(
        g, new Rectangle(0, 0, 20, 0), null, gr -> {}, gr -> renderCalls.incrementAndGet());
    g.dispose();

    assertThat(renderCalls.get()).isZero();
  }

  @Test
  void nullGraphicsConfigurationFallsBackToDirectRendering() {
    DrawingViewBuffer buffer = new DrawingViewBuffer();
    BufferedImage target = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = target.createGraphics();
    AtomicInteger renderCalls = new AtomicInteger();

    buffer.drawNonvolatile(g, new Rectangle(0, 0, 40, 40), null, gr -> {}, gr -> {
      renderCalls.incrementAndGet();
      gr.setColor(Color.GREEN);
      gr.fillRect(0, 0, 40, 40);
    });
    buffer.drawVolatile(
        g, new Rectangle(0, 0, 40, 40), null, gr -> {}, gr -> renderCalls.incrementAndGet());
    g.dispose();

    assertThat(renderCalls.get()).isEqualTo(2);
    assertThat(target.getRGB(10, 10)).isEqualTo(Color.GREEN.getRGB());
  }

  @Test
  void bufferedRenderingHandlesShiftAndResizeWhenGraphicsConfigAvailable() {
    if (GraphicsEnvironment.isHeadless()) {
      return;
    }
    GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment()
        .getDefaultScreenDevice()
        .getDefaultConfiguration();
    DrawingViewBuffer buffer = new DrawingViewBuffer();
    BufferedImage target = new BufferedImage(60, 60, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = target.createGraphics();
    AtomicInteger renders = new AtomicInteger();

    buffer.drawNonvolatile(
        g, new Rectangle(0, 0, 40, 40), gc, gr -> {}, gr -> renders.incrementAndGet());
    assertThat(renders.get()).isEqualTo(1);

    // Same rect with clean dirty area should reuse buffer without redrawing
    buffer.drawNonvolatile(
        g, new Rectangle(0, 0, 40, 40), gc, gr -> {}, gr -> renders.incrementAndGet());
    assertThat(renders.get()).isEqualTo(1);

    // Shifted rect within same buffer size triggers partial dirty redraw
    buffer.drawNonvolatile(
        g, new Rectangle(5, 5, 40, 40), gc, gr -> {}, gr -> renders.incrementAndGet());
    assertThat(renders.get()).isEqualTo(2);

    // Larger rect flushes and reallocates
    buffer.drawNonvolatile(
        g, new Rectangle(0, 0, 50, 50), gc, gr -> {}, gr -> renders.incrementAndGet());
    assertThat(renders.get()).isEqualTo(3);

    buffer.flush();
    g.dispose();
  }
}
