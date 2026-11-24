package org.jhotdraw.draw;

import static org.jhotdraw.draw.AttributeKeys.CANVAS_HEIGHT;
import static org.jhotdraw.draw.AttributeKeys.CANVAS_WIDTH;

import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/**
 * Manages coordinate conversions, scale factor, view translation, canvas bounds, and preferred
 * dimension caching for {@link DefaultDrawingView}.
 */
class DrawingViewViewport {

  private double scaleFactor = 1.0;
  private final Point translation = new Point(0, 0);
  private Dimension cachedPreferredSize;
  private Rectangle2D.Double cachedDrawingArea;

  double getScaleFactor() {
    return scaleFactor;
  }

  double setScaleFactor(double newValue) {
    if (!Double.isFinite(newValue) || newValue <= 0) {
      throw new IllegalArgumentException("scaleFactor must be positive and finite: " + newValue);
    }
    double oldValue = scaleFactor;
    scaleFactor = newValue;
    invalidateDimension();
    return oldValue;
  }

  void invalidateDimension() {
    cachedPreferredSize = null;
    cachedDrawingArea = null;
  }

  Rectangle2D.Double getDrawingArea(Drawing drawing) {
    if (cachedDrawingArea == null) {
      cachedDrawingArea =
          (drawing != null) ? drawing.getDrawingArea(scaleFactor) : new Rectangle2D.Double();
    }
    return (Rectangle2D.Double) cachedDrawingArea.clone();
  }

  Dimension getPreferredSize(Drawing drawing, Insets insets) {
    if (cachedPreferredSize == null) {
      Rectangle2D.Double area = getDrawingArea(drawing);
      Double canvasWidth = drawing == null ? null : drawing.attr().get(CANVAS_WIDTH);
      Double canvasHeight = drawing == null ? null : drawing.attr().get(CANVAS_HEIGHT);
      if (canvasWidth == null || canvasHeight == null) {
        cachedPreferredSize = computeInfiniteCanvasPreferredSize(area, insets);
      } else {
        cachedPreferredSize =
            computeFixedCanvasPreferredSize(area, canvasWidth, canvasHeight, insets);
      }
    }
    return (Dimension) cachedPreferredSize.clone();
  }

  private Dimension computeInfiniteCanvasPreferredSize(Rectangle2D.Double area, Insets insets) {
    int width = (int) Math.ceil((Math.max(0, area.x) + area.width) * scaleFactor)
        + insets.left
        + insets.right;
    int height = (int) Math.ceil((Math.max(0, area.y) + area.height) * scaleFactor)
        + insets.top
        + insets.bottom;
    return new Dimension(width, height);
  }

  private Dimension computeFixedCanvasPreferredSize(
      Rectangle2D.Double area, double canvasWidth, double canvasHeight, Insets insets) {
    double minX = Math.min(0, area.x);
    double minY = Math.min(0, area.y);
    double maxX = Math.max(area.x + area.width, canvasWidth);
    double maxY = Math.max(area.y + area.height, canvasHeight);
    int width = (int) Math.ceil((-minX + maxX) * scaleFactor) + insets.left + insets.right;
    int height = (int) Math.ceil((-minY + maxY) * scaleFactor) + insets.top + insets.bottom;
    return new Dimension(width, height);
  }

  /**
   * Recomputes view translation from the view bounds, insets, drawing area, and canvas size.
   *
   * @return the translation delta {@code (oldX - newX, oldY - newY)} if translation changed, or
   *     {@code null} if unchanged
   */
  Point updateTranslation(Drawing drawing, int viewWidth, int viewHeight, Insets insets) {
    if (drawing == null) {
      Point old = (Point) translation.clone();
      translation.setLocation(0, 0);
      return old.equals(translation) ? null : new Point(old.x, old.y);
    }

    Point oldTranslation = (Point) translation.clone();
    Rectangle2D.Double da = getDrawingArea(drawing);
    Rectangle scaledBounds = new Rectangle(
        (int) (da.x * scaleFactor),
        (int) (da.y * scaleFactor),
        (int) (da.width * scaleFactor),
        (int) (da.height * scaleFactor));

    Double canvasWidth = drawing.attr().get(CANVAS_WIDTH);
    Double canvasHeight = drawing.attr().get(CANVAS_HEIGHT);
    computeInitialTranslation(viewWidth, viewHeight, insets, canvasWidth, canvasHeight);
    clampTranslationToDrawing(viewWidth, viewHeight, insets, scaledBounds);

    if (!oldTranslation.equals(translation)) {
      return new Point(oldTranslation.x - translation.x, oldTranslation.y - translation.y);
    }
    return null;
  }

  private void computeInitialTranslation(
      int viewWidth, int viewHeight, Insets insets, Double canvasWidth, Double canvasHeight) {
    if (canvasWidth == null || canvasHeight == null) {
      translation.x = -insets.left;
      translation.y = -insets.top;
      return;
    }

    int scaledCanvasWidth = (int) (canvasWidth * scaleFactor);
    int scaledCanvasHeight = (int) (canvasHeight * scaleFactor);
    if (scaledCanvasWidth < viewWidth) {
      translation.x =
          insets.left + (viewWidth - insets.left - insets.right - scaledCanvasWidth) / -2;
    } else {
      translation.x = -insets.left;
    }
    if (scaledCanvasHeight < viewHeight) {
      translation.y =
          insets.top + (viewHeight - insets.top - insets.bottom - scaledCanvasHeight) / -2;
    } else {
      translation.y = -insets.top;
    }
  }

  private void clampTranslationToDrawing(
      int viewWidth, int viewHeight, Insets insets, Rectangle r) {
    if (r.y + r.height - translation.y > (viewHeight - insets.bottom)) {
      translation.y = r.y + r.height - (viewHeight - insets.bottom);
    }
    if (Math.min(0, r.y) - translation.y < insets.top) {
      translation.y = Math.min(0, r.y) - insets.top;
    }
    if (r.x + r.width - translation.x > (viewWidth - insets.right)) {
      translation.x = r.x + r.width - (viewWidth - insets.right);
    }
    if (Math.min(0, r.x) - translation.x < insets.left) {
      translation.x = Math.min(0, r.x) - insets.left;
    }
  }

  Rectangle getCanvasViewBounds(Drawing drawing, int viewWidth, int viewHeight) {
    int x = -translation.x;
    int y = -translation.y;
    int w = viewWidth;
    int h = viewHeight;
    if (drawing != null) {
      Double cw = drawing.attr().get(CANVAS_WIDTH);
      Double ch = drawing.attr().get(CANVAS_HEIGHT);
      if (cw != null && ch != null) {
        Point lowerRight = drawingToView(new Point2D.Double(cw, ch));
        w = lowerRight.x - x;
        h = lowerRight.y - y;
      }
    }
    return new Rectangle(x, y, w, h);
  }

  Point drawingToView(Point2D.Double p) {
    return new Point(
        (int) (p.x * scaleFactor) - translation.x, (int) (p.y * scaleFactor) - translation.y);
  }

  Rectangle drawingToView(Rectangle2D.Double r) {
    return new Rectangle(
        (int) (r.x * scaleFactor) - translation.x,
        (int) (r.y * scaleFactor) - translation.y,
        (int) (r.width * scaleFactor),
        (int) (r.height * scaleFactor));
  }

  Point2D.Double viewToDrawing(Point p) {
    return new Point2D.Double(
        (p.x + translation.x) / scaleFactor, (p.y + translation.y) / scaleFactor);
  }

  Rectangle2D.Double viewToDrawing(Rectangle r) {
    return new Rectangle2D.Double(
        (r.x + translation.x) / scaleFactor,
        (r.y + translation.y) / scaleFactor,
        r.width / scaleFactor,
        r.height / scaleFactor);
  }

  AffineTransform getDrawingToViewTransform() {
    AffineTransform t = new AffineTransform();
    t.translate(-translation.x, -translation.y);
    t.scale(scaleFactor, scaleFactor);
    return t;
  }

  void applyToTransform(AffineTransform tx) {
    tx.translate(-translation.x, -translation.y);
    tx.scale(scaleFactor, scaleFactor);
  }
}
