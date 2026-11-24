package org.jhotdraw.draw;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.jhotdraw.draw.AttributeKeys.CANVAS_HEIGHT;
import static org.jhotdraw.draw.AttributeKeys.CANVAS_WIDTH;

import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import org.jhotdraw.draw.figure.RectangleFigure;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link DrawingViewViewport}. */
public class DrawingViewViewportTest {

  @Test
  void scaleFactorValidationRejectsNonPositiveAndNonFiniteValues() {
    DrawingViewViewport viewport = new DrawingViewViewport();
    assertThat(viewport.getScaleFactor()).isEqualTo(1.0);

    assertThatThrownBy(() -> viewport.setScaleFactor(0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> viewport.setScaleFactor(-1.5))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> viewport.setScaleFactor(Double.NaN))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> viewport.setScaleFactor(Double.POSITIVE_INFINITY))
        .isInstanceOf(IllegalArgumentException.class);

    double old = viewport.setScaleFactor(2.5);
    assertThat(old).isEqualTo(1.0);
    assertThat(viewport.getScaleFactor()).isEqualTo(2.5);
  }

  @Test
  void infiniteCanvasPlacesOriginUsingCorrectLeftAndTopInsets() {
    DrawingViewViewport viewport = new DrawingViewViewport();
    DefaultDrawing drawing = new DefaultDrawing();
    Insets asymmetric = new Insets(10, 30, 5, 15);

    Point delta = viewport.updateTranslation(drawing, 400, 300, asymmetric);
    assertThat(delta).isNotNull();
    assertThat(viewport.drawingToView(new Point2D.Double(0, 0))).isEqualTo(new Point(30, 10));
    assertThat(viewport.viewToDrawing(new Point(30, 10))).isEqualTo(new Point2D.Double(0, 0));
  }

  @Test
  void fixedCanvasCentersWhenSmallerAndResetsWhenLarger() {
    DrawingViewViewport viewport = new DrawingViewViewport();
    DefaultDrawing drawing = new DefaultDrawing();
    drawing.attr().set(CANVAS_WIDTH, 200.0);
    drawing.attr().set(CANVAS_HEIGHT, 100.0);
    Insets none = new Insets(0, 0, 0, 0);

    viewport.updateTranslation(drawing, 400, 300, none);
    assertThat(viewport.drawingToView(new Point2D.Double(0, 0))).isEqualTo(new Point(100, 100));
    assertThat(viewport.getCanvasViewBounds(drawing, 400, 300))
        .isEqualTo(new Rectangle(100, 100, 200, 100));

    // Scale up so canvas (400x200) >= viewWidth (400): horizontal centering resets cleanly
    viewport.setScaleFactor(2.0);
    viewport.updateTranslation(drawing, 400, 300, none);
    assertThat(viewport.drawingToView(new Point2D.Double(0, 0))).isEqualTo(new Point(0, 50));

    AffineTransform tx = viewport.getDrawingToViewTransform();
    Point2D transformed = tx.transform(new Point2D.Double(10, 20), null);
    assertThat(transformed.getX()).isCloseTo(20.0, within(1e-6));
    assertThat(transformed.getY()).isCloseTo(90.0, within(1e-6));
  }

  @Test
  void preferredSizeAccountsForFixedCanvasNegativeCoordinatesAndDefensiveCloning() {
    DrawingViewViewport viewport = new DrawingViewViewport();
    DefaultDrawing drawing = new DefaultDrawing();
    drawing.attr().set(CANVAS_WIDTH, 100.0);
    drawing.attr().set(CANVAS_HEIGHT, 80.0);
    drawing.add(new RectangleFigure(-20, -10, 30, 30));

    Dimension pref = viewport.getPreferredSize(drawing, new Insets(2, 4, 6, 8));
    Rectangle2D.Double da = viewport.getDrawingArea(drawing);
    int expectedW = (int) Math.ceil((-Math.min(0, da.x) + Math.max(da.x + da.width, 100.0))) + 12;
    int expectedH = (int) Math.ceil((-Math.min(0, da.y) + Math.max(da.y + da.height, 80.0))) + 8;
    assertThat(pref).isEqualTo(new Dimension(expectedW, expectedH));

    pref.width = -1;
    assertThat(viewport.getPreferredSize(drawing, new Insets(2, 4, 6, 8)).width)
        .isEqualTo(expectedW);

    viewport.updateTranslation(null, 100, 100, new Insets(0, 0, 0, 0));
    assertThat(viewport.drawingToView(new Point2D.Double(5, 5))).isEqualTo(new Point(5, 5));
  }
}
