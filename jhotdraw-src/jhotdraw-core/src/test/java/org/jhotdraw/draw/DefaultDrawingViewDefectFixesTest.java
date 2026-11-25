package org.jhotdraw.draw;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.jhotdraw.draw.AttributeKeys.CANVAS_FILL_COLOR;
import static org.jhotdraw.draw.AttributeKeys.CANVAS_FILL_OPACITY;
import static org.jhotdraw.draw.AttributeKeys.CANVAS_HEIGHT;
import static org.jhotdraw.draw.AttributeKeys.CANVAS_WIDTH;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.SwingUtilities;
import javax.swing.undo.UndoManager;
import org.jhotdraw.draw.TestDrawingEditorStub.StubFigure;
import org.jhotdraw.draw.TestDrawingEditorStub.StubHandle;
import org.jhotdraw.draw.event.DrawingEvent;
import org.jhotdraw.draw.figure.RectangleFigure;
import org.junit.jupiter.api.Test;

/** Explicit regression tests verifying all 12 defects fixed in {@link DefaultDrawingView}. */
public class DefaultDrawingViewDefectFixesTest {

  @Test
  void defect01TranslucentRedBackgroundPreservesFullRedChannel() {
    DefaultDrawingView view = new DefaultDrawingView();
    view.setDrawingDoubleBuffered(false);
    view.setBounds(0, 0, 40, 40);

    DefaultDrawing drawing = new DefaultDrawing();
    drawing.attr().set(CANVAS_FILL_COLOR, new Color(255, 0, 0));
    drawing.attr().set(CANVAS_FILL_OPACITY, 0.5);
    view.setDrawing(drawing);

    BufferedImage img = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = img.createGraphics();
    g.setClip(0, 0, 40, 40);
    view.drawBackground(g);
    g.dispose();

    // Checkerboard tile at (1, 1) is 0xDFDFDF (223) and at (9, 1) is 0xFFFFFF (255).
    // With 50% red overlay, red channel must be > 235 (buggy 0xfffff masked out top red bits ->
    // ~111).
    int rgbOnDarkTile = img.getRGB(1, 1);
    int red = (rgbOnDarkTile >> 16) & 0xff;
    int green = (rgbOnDarkTile >> 8) & 0xff;
    assertThat(red).isGreaterThan(230);
    assertThat(green).isLessThan(130);
  }

  @Test
  void defect02FixedCanvasResetsStaleCenteringOffsetWhenZoomedLargerThanView() {
    DefaultDrawingView view = new DefaultDrawingView();
    view.setBorder(BorderFactory.createEmptyBorder(10, 30, 5, 15));
    DefaultDrawing drawing = new DefaultDrawing();
    drawing.attr().set(CANVAS_WIDTH, 100.0);
    drawing.attr().set(CANVAS_HEIGHT, 100.0);
    view.setDrawing(drawing);
    view.setBounds(0, 0, 300, 200);
    // The canvas is smaller than the view, so it is centered (non-zero centering offset).
    assertThat(view.drawingToView(new Point2D.Double(0, 0))).isNotEqualTo(new Point(30, 10));

    // Zoomed in, the 500x500 canvas no longer fits: its origin must snap back to the insets
    // instead of keeping the stale centering offset from the previous scale factor.
    view.setScaleFactor(5.0);

    assertThat(view.drawingToView(new Point2D.Double(0, 0))).isEqualTo(new Point(30, 10));
  }

  @Test
  void defect03AddNotifyAndRemoveNotifyFireAccurateEditorPropertyChanges() {
    DefaultDrawingView view = new DefaultDrawingView();
    TestDrawingEditorStub e1 = new TestDrawingEditorStub();
    TestDrawingEditorStub e2 = new TestDrawingEditorStub();
    List<PropertyChangeEvent> events = new ArrayList<>();
    view.addPropertyChangeListener(e -> {
      if ("editor".equals(e.getPropertyName())) {
        events.add(e);
      }
    });

    view.addNotify(e1);
    view.addNotify(e2);
    view.removeNotify(e2);

    assertThat(events).hasSize(3);
    assertThat(events.get(0).getOldValue()).isNull();
    assertThat(events.get(0).getNewValue()).isSameAs(e1);
    assertThat(events.get(1).getOldValue()).isSameAs(e1);
    assertThat(events.get(1).getNewValue()).isSameAs(e2);
    assertThat(events.get(2).getOldValue()).isSameAs(e2);
    assertThat(events.get(2).getNewValue()).isNull();
  }

  @Test
  void defect04ClearSelectionAndSelectAllDetachAndAttachFigureListeners() {
    DefaultDrawingView view = new DefaultDrawingView();
    DefaultDrawing drawing = new DefaultDrawing();
    StubFigure f1 = new StubFigure(0, 0, 20, 20);
    StubFigure f2 = new StubFigure(30, 0, 20, 20);
    drawing.addAll(List.of(f1, f2));
    view.setDrawing(drawing);

    int base1 = f1.getFigureListenerCount();
    int base2 = f2.getFigureListenerCount();

    view.addToSelection(f1);
    assertThat(f1.getFigureListenerCount()).isEqualTo(base1 + 1);

    view.clearSelection();
    assertThat(f1.getFigureListenerCount()).isEqualTo(base1);

    view.selectAll();
    assertThat(f1.getFigureListenerCount()).isEqualTo(base1 + 1);
    assertThat(f2.getFigureListenerCount()).isEqualTo(base2 + 1);

    view.clearSelection();
    assertThat(f1.getFigureListenerCount()).isEqualTo(base1);
    assertThat(f2.getFigureListenerCount()).isEqualTo(base2);
  }

  @Test
  void defect05NullDrawingSafeOperationsDoNotThrowNpe() {
    DefaultDrawingView view = new DefaultDrawingView();
    assertThat(view.getDrawing()).isNull();

    view.selectAll();
    assertThat(view.isSelectionEmpty()).isTrue();
    assertThat(view.findFigure(new Point(10, 10))).isNull();
    assertThat(view.findFigures(new Rectangle(0, 0, 50, 50))).isEmpty();
    assertThat(view.findFiguresWithin(new Rectangle(0, 0, 50, 50))).isEmpty();
    view.delete();
    view.duplicate();
  }

  @Test
  void defect06DrawingAttributeChangedWithInvalidatedAreaDoesNotThrowNpe() {
    DefaultDrawingView view = new DefaultDrawingView();
    DefaultDrawing drawing = new DefaultDrawing();
    view.setDrawing(drawing);

    DefaultDrawingView.EventHandler handler = view.createEventHandler();
    DrawingEvent attrEvent = new DrawingEvent(drawing, new Rectangle2D.Double(0, 0, 50, 50));
    handler.drawingAttributeChanged(attrEvent);
  }

  @Test
  void defect07SetHandleDetailLevelFiresPropertyChange() {
    DefaultDrawingView view = new DefaultDrawingView();
    DefaultDrawing drawing = new DefaultDrawing();
    TestDrawingEditorStub editor = new TestDrawingEditorStub();
    StubFigure fig = new StubFigure(0, 0, 20, 20);
    StubHandle h1 = new StubHandle(fig, new Rectangle(0, 0, 8, 8));
    fig.setHandlesForDetailLevel(1, List.of(h1));
    drawing.add(fig);
    editor.add(view);
    view.setDrawing(drawing);
    view.addToSelection(fig);

    List<PropertyChangeEvent> events = new ArrayList<>();
    view.addPropertyChangeListener(e -> {
      if (DrawingView.HANDLE_DETAIL_LEVEL_PROPERTY.equals(e.getPropertyName())) {
        events.add(e);
      }
    });

    view.setHandleDetailLevel(1);
    assertThat(view.getHandleDetailLevel()).isEqualTo(1);
    assertThat(events).hasSize(1);
    assertThat(events.get(0).getOldValue()).isEqualTo(0);
    assertThat(events.get(0).getNewValue()).isEqualTo(1);
  }

  @Test
  void defect08SecondaryHandleReplacementAndRemoveDetachListenersAndDispose() {
    DefaultDrawingView view = new DefaultDrawingView();
    StubFigure fig = new StubFigure(0, 0, 40, 40);
    StubHandle owner1 = new StubHandle(fig, new Rectangle(0, 0, 10, 10));
    StubHandle sec1 = new StubHandle(fig, new Rectangle(2, 2, 5, 5));
    owner1.addSecondaryHandle(sec1);

    StubHandle owner2 = new StubHandle(fig, new Rectangle(20, 20, 10, 10));
    StubHandle sec2 = new StubHandle(fig, new Rectangle(22, 22, 5, 5));
    owner2.addSecondaryHandle(sec2);

    DefaultDrawing drawing = new DefaultDrawing();
    drawing.add(fig);
    TestDrawingEditorStub editor = new TestDrawingEditorStub();
    editor.add(view);
    view.setDrawing(drawing);

    fig.setHandlesForDetailLevel(0, List.of(owner1, owner2));
    view.addToSelection(fig);
    view.findHandle(new Point(5, 5)); // validate handles

    owner1.triggerSecondaryHandles();
    assertThat(sec1.getHandleListenerCount()).isEqualTo(1);

    owner2.triggerSecondaryHandles();
    assertThat(sec1.getDisposeCalls()).isEqualTo(1);
    assertThat(sec1.getHandleListenerCount()).isZero();
    assertThat(sec2.getHandleListenerCount()).isEqualTo(1);

    owner1.triggerRequestRemove(new Rectangle(0, 0, 10, 10));
    assertThat(owner1.getDisposeCalls()).isGreaterThanOrEqualTo(1);
    assertThat(owner1.getHandleListenerCount()).isZero();
  }

  @Test
  void defect09EmptySelectionDeleteAndDuplicateDoNotFireUndoableEdits() {
    DefaultDrawingView view = new DefaultDrawingView();
    DefaultDrawing drawing = new DefaultDrawing();
    UndoManager undoManager = new UndoManager();
    drawing.addUndoableEditListener(undoManager);
    view.setDrawing(drawing);

    view.delete();
    view.duplicate();
    assertThat(undoManager.canUndo()).isFalse();
  }

  @Test
  void defect10HeadlessDoubleBufferedPaintDoesNotThrowNullPointerException() {
    DefaultDrawingView view = new DefaultDrawingView();
    assertThat(view.isDrawingDoubleBuffered()).isTrue();

    // Zero-size visible rect
    BufferedImage img = new BufferedImage(50, 50, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = img.createGraphics();
    g.setClip(0, 0, 50, 50);
    view.paintComponent(g);

    // Non-zero bounds on non-displayable headless component (getGraphicsConfiguration() == null)
    view.setBounds(0, 0, 50, 50);
    DefaultDrawing drawing = new DefaultDrawing();
    drawing.add(new RectangleFigure(5, 5, 20, 20));
    view.setDrawing(drawing);
    view.paintComponent(g);
    g.dispose();
  }

  @Test
  void defect11SetDrawingInvalidatesCachedDimensionImmediatelyAndCancelsOldTimer()
      throws Exception {
    DefaultDrawingView view = new DefaultDrawingView();
    view.setBounds(0, 0, 200, 200);

    DefaultDrawing d1 = new DefaultDrawing();
    d1.attr().set(CANVAS_WIDTH, 300.0);
    d1.attr().set(CANVAS_HEIGHT, 200.0);
    DefaultDrawing d2 = new DefaultDrawing();
    d2.attr().set(CANVAS_WIDTH, 120.0);
    d2.attr().set(CANVAS_HEIGHT, 80.0);

    SwingUtilities.invokeAndWait(() -> {
      view.setDrawing(d1);
      assertThat(view.getPreferredSize()).isEqualTo(new Dimension(300, 200));
      view.setDrawing(d2);
      assertThat(view.getPreferredSize()).isEqualTo(new Dimension(120, 80));
    });
  }

  @Test
  void defect12SetScaleFactorValidatesInputAndUpdatesPreferredSizeImmediately() {
    DefaultDrawingView view = new DefaultDrawingView();
    DefaultDrawing drawing = new DefaultDrawing();
    drawing.attr().set(CANVAS_WIDTH, 100.0);
    drawing.attr().set(CANVAS_HEIGHT, 50.0);
    view.setDrawing(drawing);
    assertThat(view.getPreferredSize()).isEqualTo(new Dimension(100, 50));

    assertThatThrownBy(() -> view.setScaleFactor(0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> view.setScaleFactor(-2.0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> view.setScaleFactor(Double.NaN))
        .isInstanceOf(IllegalArgumentException.class);

    view.setScaleFactor(2.0);
    assertThat(view.getPreferredSize()).isEqualTo(new Dimension(200, 100));
  }
}
