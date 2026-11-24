package org.jhotdraw.draw;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.jhotdraw.draw.AttributeKeys.CANVAS_FILL_COLOR;
import static org.jhotdraw.draw.AttributeKeys.CANVAS_FILL_OPACITY;
import static org.jhotdraw.draw.AttributeKeys.CANVAS_HEIGHT;
import static org.jhotdraw.draw.AttributeKeys.CANVAS_WIDTH;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Paint;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.TexturePaint;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.undo.UndoManager;
import org.jhotdraw.api.gui.EditableComponent;
import org.jhotdraw.draw.TestDrawingEditorStub.StubFigure;
import org.jhotdraw.draw.TestDrawingEditorStub.StubHandle;
import org.jhotdraw.draw.TestDrawingEditorStub.StubTool;
import org.jhotdraw.draw.constrainer.Constrainer;
import org.jhotdraw.draw.constrainer.GridConstrainer;
import org.jhotdraw.draw.event.DrawingEvent;
import org.jhotdraw.draw.event.FigureSelectionEvent;
import org.jhotdraw.draw.event.FigureSelectionListener;
import org.jhotdraw.draw.figure.Figure;
import org.jhotdraw.draw.figure.RectangleFigure;
import org.jhotdraw.draw.handle.Handle;
import org.jhotdraw.draw.io.DefaultDrawingViewTransferHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Characterization unit tests for {@link DefaultDrawingView}. */
public class DefaultDrawingViewUnitTest {

  private DefaultDrawingView view;
  private DefaultDrawing drawing;
  private TestDrawingEditorStub editor;

  @BeforeEach
  void setUp() {
    view = new DefaultDrawingView();
    drawing = new DefaultDrawing();
    editor = new TestDrawingEditorStub();
    // Avoid Swing 10ms paint suppression in synchronous rendering tests.
    view.setDrawingDoubleBuffered(false);
  }

  @Test
  void constructorSetsExpectedDefaults() {
    DefaultDrawingView fresh = new DefaultDrawingView();
    assertThat(fresh.getDrawing()).isNull();
    assertThat(fresh.getEditor()).isNull();
    assertThat(fresh.getScaleFactor()).isEqualTo(1.0);
    assertThat(fresh.isSelectionEmpty()).isTrue();
    assertThat(fresh.getSelectionCount()).isZero();
    assertThat(fresh.getSelectedFigures()).isEmpty();
    assertThat(fresh.isConstrainerVisible()).isFalse();
    assertThat(fresh.getConstrainer()).isSameAs(fresh.getInvisibleConstrainer());
    assertThat(fresh.getVisibleConstrainer()).isInstanceOf(GridConstrainer.class);
    assertThat(fresh.getInvisibleConstrainer()).isInstanceOf(GridConstrainer.class);
    assertThat(fresh.isDrawingDoubleBuffered()).isTrue();
    assertThat(fresh.isFocusable()).isTrue();
    assertThat(fresh.isOpaque()).isTrue();
    assertThat(fresh.getLayout()).isNull();
    assertThat(fresh.getTransferHandler()).isInstanceOf(DefaultDrawingViewTransferHandler.class);
    assertThat(fresh.getComponent()).isSameAs(fresh);
    assertThat(fresh.getHandleDetailLevel()).isZero();
    assertThat(fresh.getActiveHandle()).isNull();
    assertThat(fresh.getEmptyDrawingMessage()).isNull();
    assertThat(fresh.getBackground()).isEqualTo(new Color(0xb0b0b0));
  }

  @Test
  void emptyDrawingMessagePropertyRoundTrip() {
    List<PropertyChangeEvent> events = new ArrayList<>();
    view.addPropertyChangeListener(e -> {
      if ("emptyDrawingMessage".equals(e.getPropertyName())) {
        events.add(e);
      }
    });

    view.setEmptyDrawingMessage("Drop shapes here");
    assertThat(view.getEmptyDrawingMessage()).isEqualTo("Drop shapes here");
    assertThat(events).hasSize(1);
    assertThat(events.get(0).getOldValue()).isNull();
    assertThat(events.get(0).getNewValue()).isEqualTo("Drop shapes here");

    view.setEmptyDrawingMessage(null);
    assertThat(view.getEmptyDrawingMessage()).isNull();
    assertThat(events).hasSize(2);
    assertThat(events.get(1).getOldValue()).isEqualTo("Drop shapes here");
    assertThat(events.get(1).getNewValue()).isNull();
  }

  @Test
  void setEnabledSwitchesCursorBetweenDefaultAndWait() {
    view.setEnabled(false);
    assertThat(view.isEnabled()).isFalse();
    assertThat(view.getCursor().getType()).isEqualTo(Cursor.WAIT_CURSOR);

    view.setEnabled(true);
    assertThat(view.isEnabled()).isTrue();
    assertThat(view.getCursor().getType()).isEqualTo(Cursor.DEFAULT_CURSOR);
  }

  @Test
  void selectionManagementPreservesOrderUnmodifiabilityAndListenerOrder() {
    Figure f1 = new RectangleFigure(0, 0, 20, 20);
    Figure f2 = new RectangleFigure(30, 0, 20, 20);
    Figure f3 = new RectangleFigure(60, 0, 20, 20);
    drawing.addAll(List.of(f1, f2, f3));
    view.setDrawing(drawing);

    List<String> callOrder = new ArrayList<>();
    List<FigureSelectionEvent> selectionEvents = new ArrayList<>();
    List<PropertyChangeEvent> emptyEvents = new ArrayList<>();

    FigureSelectionListener first = evt -> {
      callOrder.add("first");
      selectionEvents.add(evt);
    };
    FigureSelectionListener second = evt -> callOrder.add("second");

    view.addFigureSelectionListener(first);
    view.addFigureSelectionListener(second);
    view.addPropertyChangeListener(e -> {
      if (EditableComponent.SELECTION_EMPTY_PROPERTY.equals(e.getPropertyName())) {
        emptyEvents.add(e);
      }
    });

    // Add single figure
    view.addToSelection(f1);
    assertThat(view.isSelectionEmpty()).isFalse();
    assertThat(view.getSelectionCount()).isEqualTo(1);
    assertThat(view.isFigureSelected(f1)).isTrue();
    assertThat(callOrder).containsExactly("second", "first");
    assertThat(selectionEvents).hasSize(1);
    assertThat(selectionEvents.get(0).getOldSelection()).isEmpty();
    assertThat(selectionEvents.get(0).getNewSelection()).containsExactly(f1);
    assertThat(emptyEvents).hasSize(1);
    assertThat(emptyEvents.get(0).getOldValue()).isEqualTo(true);
    assertThat(emptyEvents.get(0).getNewValue()).isEqualTo(false);

    // Duplicate single add is a no-op
    view.addToSelection(f1);
    assertThat(selectionEvents).hasSize(1);

    // Unmodifiable set contract
    Set<Figure> selected = view.getSelectedFigures();
    assertThatThrownBy(() -> selected.add(f2)).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> selected.remove(f1)).isInstanceOf(UnsupportedOperationException.class);

    // Batch add fires a single event and preserves insertion order
    view.addToSelection(Arrays.asList(f1, f3, f2));
    assertThat(view.getSelectedFigures()).containsExactly(f1, f3, f2);
    assertThat(selectionEvents).hasSize(2);
    assertThat(emptyEvents).hasSize(1); // false -> false does not fire

    // Batch add with only already-selected figures fires nothing
    view.addToSelection(List.of(f1, f2));
    assertThat(selectionEvents).hasSize(2);

    // Remove listener and verify
    view.removeFigureSelectionListener(second);
    callOrder.clear();
    view.removeFromSelection(f3);
    assertThat(callOrder).containsExactly("first");
    assertThat(view.isFigureSelected(f3)).isFalse();
    assertThat(view.getSelectionCount()).isEqualTo(2);

    // Removing an unselected figure is a no-op
    view.removeFromSelection(f3);
    assertThat(selectionEvents).hasSize(3);

    // Toggle selection
    view.toggleSelection(f3);
    assertThat(view.isFigureSelected(f3)).isTrue();
    view.toggleSelection(f3);
    assertThat(view.isFigureSelected(f3)).isFalse();

    // Clear selection fires SELECTION_EMPTY_PROPERTY false -> true
    view.clearSelection();
    assertThat(view.isSelectionEmpty()).isTrue();
    assertThat(view.getSelectionCount()).isZero();
    assertThat(emptyEvents).hasSize(2);
    assertThat(emptyEvents.get(1).getOldValue()).isEqualTo(false);
    assertThat(emptyEvents.get(1).getNewValue()).isEqualTo(true);

    // Clear when already empty is a no-op
    view.clearSelection();
    assertThat(emptyEvents).hasSize(2);
  }

  @Test
  void selectAllSkipsNonSelectableFigures() {
    StubFigure selectable = new StubFigure(0, 0, 20, 20);
    StubFigure nonSelectable = new StubFigure(30, 0, 20, 20);
    nonSelectable.setCustomSelectable(false);
    drawing.addAll(List.of(selectable, nonSelectable));
    view.setDrawing(drawing);

    view.selectAll();

    assertThat(view.getSelectedFigures()).containsExactly(selectable);
    assertThat(view.isFigureSelected(nonSelectable)).isFalse();
  }

  @Test
  void handleLifecycleValidationFallbackLookupCompatibilityAndSecondaryHandles() {
    StubFigure fig1 = new StubFigure(10, 10, 40, 40);
    StubFigure fig2 = new StubFigure(80, 10, 40, 40);
    StubHandle h1Detail0 = new StubHandle(fig1, new Rectangle(10, 10, 10, 10), true);
    StubHandle h1Overlap = new StubHandle(fig1, new Rectangle(10, 10, 10, 10), false);
    StubHandle h2Detail0 = new StubHandle(fig2, new Rectangle(80, 10, 10, 10), true);
    StubHandle h2SecondOnSameOwner = new StubHandle(fig2, new Rectangle(95, 10, 10, 10), true);
    StubHandle secondary = new StubHandle(fig1, new Rectangle(12, 12, 6, 6), false);
    h1Overlap.addSecondaryHandle(secondary);

    fig1.setHandlesForDetailLevel(0, List.of(h1Detail0, h1Overlap));
    fig2.setHandlesForDetailLevel(0, List.of(h2Detail0, h2SecondOnSameOwner));

    drawing.addAll(List.of(fig1, fig2));
    view.setDrawing(drawing);
    view.addToSelection(List.of(fig1, fig2));

    // Without editor, handles are not created yet
    assertThat(view.findHandle(new Point(15, 15))).isNull();

    // Attach editor -> handles created on demand
    editor.add(view);
    assertThat(view.findHandle(new Point(15, 15)))
        .isSameAs(h1Overlap); // reverse order picks last added

    // Secondary handles take precedence over selection handles
    h1Overlap.triggerSecondaryHandles();
    assertThat(view.findHandle(new Point(15, 15))).isSameAs(secondary);

    // Compatible handles include master + one compatible handle per other owner
    Collection<Handle> compatible = view.getCompatibleHandles(h1Detail0);
    assertThat(compatible).containsExactly(h1Detail0, h2Detail0);

    // Active handle tracking + property change
    List<PropertyChangeEvent> activeEvents = new ArrayList<>();
    view.addPropertyChangeListener(e -> {
      if (DrawingView.ACTIVE_HANDLE_PROPERTY.equals(e.getPropertyName())) {
        activeEvents.add(e);
      }
    });
    view.setActiveHandle(h1Detail0);
    assertThat(view.getActiveHandle()).isSameAs(h1Detail0);
    assertThat(activeEvents).hasSize(1);

    // Request remove disposes handle and clears activeHandle via invalidation
    h1Detail0.triggerRequestRemove(new Rectangle(10, 10, 10, 10));
    assertThat(h1Detail0.getDisposeCalls()).isGreaterThanOrEqualTo(1);
    assertThat(view.getActiveHandle()).isNull();

    // Detail level fallback: request detailLevel 2 (returns empty) -> falls back to 0
    view.setHandleDetailLevel(2);
    assertThat(view.getHandleDetailLevel()).isZero();
    assertThat(view.findHandle(new Point(85, 15))).isNotNull();

    // FigureHandlesChanged invalidates handles
    int prevDispose = h2Detail0.getDisposeCalls();
    fig2.triggerHandlesChanged();
    assertThat(h2Detail0.getDisposeCalls()).isGreaterThan(prevDispose);
  }

  @Test
  void coordinateConversionsScaleFactorTranslationAndDimensionCaching() {
    drawing.attr().set(CANVAS_WIDTH, 200.0);
    drawing.attr().set(CANVAS_HEIGHT, 100.0);
    view.setDrawing(drawing);
    view.setBounds(0, 0, 400, 300);

    // Fixed canvas 200x100 inside 400x300 view is centered -> translation = (-100, -100)
    Point origin = view.drawingToView(new Point2D.Double(0, 0));
    assertThat(origin).isEqualTo(new Point(100, 100));
    Point2D.Double back = view.viewToDrawing(new Point(100, 100));
    assertThat(back.x).isCloseTo(0.0, within(1e-6));
    assertThat(back.y).isCloseTo(0.0, within(1e-6));

    Rectangle viewRect = view.drawingToView(new Rectangle2D.Double(10, 20, 30, 40));
    assertThat(viewRect).isEqualTo(new Rectangle(110, 120, 30, 40));
    Rectangle2D.Double drawingRect = view.viewToDrawing(viewRect);
    assertThat(drawingRect).isEqualTo(new Rectangle2D.Double(10, 20, 30, 40));

    // Scale factor 2.0 doubles canvas size to 400x200 inside 400x300 -> translation.y = -50
    List<PropertyChangeEvent> scaleEvents = new ArrayList<>();
    view.addPropertyChangeListener(e -> {
      if (DrawingView.SCALE_FACTOR_PROPERTY.equals(e.getPropertyName())) {
        scaleEvents.add(e);
      }
    });
    StubFigure fig = new StubFigure(0, 0, 50, 50);
    StubHandle handle = new StubHandle(fig, new Rectangle(0, 0, 8, 8));
    fig.setHandlesForDetailLevel(0, List.of(handle));
    drawing.add(fig);
    editor.add(view);
    view.addToSelection(fig);
    view.findHandle(new Point(4, 4)); // ensure handles validated

    view.setScaleFactor(2.0);
    assertThat(view.getScaleFactor()).isEqualTo(2.0);
    assertThat(scaleEvents).hasSize(1);
    assertThat(scaleEvents.get(0).getOldValue()).isEqualTo(1.0);
    assertThat(scaleEvents.get(0).getNewValue()).isEqualTo(2.0);
    assertThat(handle.getDisposeCalls()).isGreaterThanOrEqualTo(1);

    AffineTransform tx = view.getDrawingToViewTransform();
    Point2D dst = tx.transform(new Point2D.Double(10, 20), null);
    Point expected = view.drawingToView(new Point2D.Double(10, 20));
    assertThat(dst.getX()).isCloseTo(expected.x, within(1e-6));
    assertThat(dst.getY()).isCloseTo(expected.y, within(1e-6));

    // PreferredSize & DrawingArea caching return defensive clones
    // Note: getDrawingArea() caches the first figure's drawing area at scale 1.0 before
    // setScaleFactor(2.0),
    // or at scale 2.0 when invalidated. At scale 2.0, fig (0,0,50,50) drawing area has stroke/hit
    // growth.
    // Test with an empty canvas first for exact 400x200, then verify figure expansion & defensive
    // cloning.
    drawing.remove(fig);
    view.invalidateDimension();
    Dimension pref1 = view.getPreferredSize();
    assertThat(pref1).isEqualTo(new Dimension(400, 200));
    pref1.width = 9999;
    assertThat(view.getPreferredSize()).isEqualTo(new Dimension(400, 200));

    drawing.add(fig);
    view.invalidateDimension();
    Dimension prefWithFig = view.getPreferredSize();
    assertThat(prefWithFig.width).isGreaterThanOrEqualTo(400);

    Rectangle2D.Double area1 = view.getDrawingArea();
    area1.width = 9999;
    assertThat(view.getDrawingArea().width).isNotEqualTo(9999);

    // Insets accounted for in preferred size
    drawing.remove(fig);
    view.setBorder(BorderFactory.createEmptyBorder(5, 10, 15, 20));
    view.invalidateDimension();
    Insets insets = view.getInsets();
    assertThat(insets).isEqualTo(new Insets(5, 10, 15, 20));
    assertThat(view.getPreferredSize()).isEqualTo(new Dimension(400 + 30, 200 + 20));

    // Infinite canvas preferred size and clamping when figure is at negative coordinates
    drawing.attr().set(CANVAS_WIDTH, null);
    drawing.attr().set(CANVAS_HEIGHT, null);
    view.setBorder(null);
    view.invalidateDimension();
    view.setScaleFactor(1.0);
    StubFigure largeFig = new StubFigure(0, 0, 500, 400);
    drawing.add(largeFig);
    view.invalidateDimension();
    assertThat(view.getPreferredSize().width).isGreaterThanOrEqualTo(500);
    view.setBounds(0, 0, 300, 250);
    // Clamping checks right edge then enforces left edge >= insets.left, keeping (0,0) visible at
    // >= 0
    assertThat(view.drawingToView(new Point2D.Double(0, 0)).x).isGreaterThanOrEqualTo(0);

    // Figure at negative coordinates triggers left/top clamping so it remains visible inside view
    drawing.remove(largeFig);
    drawing.add(new StubFigure(-50, -40, 20, 20));
    view.invalidateDimension();
    view.setBounds(0, 0, 300, 250);
    assertThat(view.drawingToView(new Point2D.Double(-50, -40)).x).isGreaterThanOrEqualTo(0);
    assertThat(view.drawingToView(new Point2D.Double(-50, -40)).y).isGreaterThanOrEqualTo(0);

    // Null drawing resets translation to (0, 0)
    view.setDrawing(null);
    view.invalidateDimension();
    assertThat(view.drawingToView(new Point2D.Double(10, 10))).isEqualTo(new Point(10, 10));
    assertThat(view.getPreferredSize()).isEqualTo(new Dimension(0, 0));
  }

  @Test
  void constrainerSwitchingFiresPropertyChanges() {
    Constrainer customVisible = new GridConstrainer(16, 16);
    Constrainer customInvisible = new GridConstrainer(2, 2, false);
    List<String> props = new ArrayList<>();
    view.addPropertyChangeListener(e -> props.add(e.getPropertyName()));

    assertThat(view.isConstrainerVisible()).isFalse();
    assertThat(view.getConstrainer()).isSameAs(view.getInvisibleConstrainer());

    view.setVisibleConstrainer(customVisible);
    view.setInvisibleConstrainer(customInvisible);
    view.setConstrainerVisible(true);

    assertThat(view.isConstrainerVisible()).isTrue();
    assertThat(view.getConstrainer()).isSameAs(customVisible);

    view.setConstrainerVisible(false);
    assertThat(view.getConstrainer()).isSameAs(customInvisible);
    assertThat(props)
        .containsExactly(
            DrawingView.VISIBLE_CONSTRAINER_PROPERTY,
            DrawingView.INVISIBLE_CONSTRAINER_PROPERTY,
            DrawingView.CONSTRAINER_VISIBLE_PROPERTY,
            DrawingView.CONSTRAINER_VISIBLE_PROPERTY);
  }

  @Test
  void eventHandlerHandlesDrawingAndHandleEvents() {
    RectangleFigure f = new RectangleFigure(10, 10, 30, 30);
    drawing.add(f);
    view.setDrawing(drawing);
    view.setBounds(0, 0, 200, 200);
    view.addToSelection(f);

    DefaultDrawingView.EventHandler handler = view.createEventHandler();
    // drawingChanged invalidates cached dimension
    Dimension before = view.getPreferredSize();
    assertThat(before).isNotNull();
    handler.drawingChanged(new DrawingEvent(drawing, new Rectangle2D.Double(0, 0, 200, 200)));

    // figureRemoved removes figure from selection
    drawing.remove(f);
    assertThat(view.isSelectionEmpty()).isTrue();

    // drawingAttributeChanged for CANVAS_WIDTH with null invalidatedArea works without error
    drawing.attr().set(CANVAS_WIDTH, 100.0);
    drawing.attr().set(CANVAS_HEIGHT, 100.0);
    handler.drawingAttributeChanged(new DrawingEvent(drawing, CANVAS_WIDTH, null, 100.0));

    // Foreign drawing event is safely ignored
    DefaultDrawing foreign = new DefaultDrawing();
    handler.drawingAttributeChanged(new DrawingEvent(foreign, CANVAS_WIDTH, null, 50.0));
  }

  @Test
  void findFigureMethodsDelegateWithCoordinateConversion() {
    RectangleFigure f1 = new RectangleFigure(10, 10, 20, 20);
    RectangleFigure f2 = new RectangleFigure(100, 100, 20, 20);
    drawing.addAll(List.of(f1, f2));
    view.setDrawing(drawing);
    view.setBounds(0, 0, 200, 200);
    view.setScaleFactor(2.0);

    Point viewPoint = view.drawingToView(new Point2D.Double(20, 20));
    assertThat(view.findFigure(viewPoint)).isSameAs(f1);

    Rectangle viewBox = view.drawingToView(new Rectangle2D.Double(5, 5, 30, 30));
    assertThat(view.findFigures(viewBox)).containsExactly(f1);
    assertThat(view.findFiguresWithin(viewBox)).containsExactly(f1);
  }

  @Test
  void offscreenRenderingPaintsBackgroundCanvasConstrainerDrawingHandlesAndTool() {
    view.setBounds(0, 0, 120, 80);

    // 1. Null drawing paints component background
    BufferedImage imgNull = new BufferedImage(120, 80, BufferedImage.TYPE_INT_ARGB);
    Graphics2D gNull = imgNull.createGraphics();
    gNull.setClip(0, 0, 120, 80);
    view.paintComponent(gNull);
    gNull.dispose();
    assertThat(imgNull.getRGB(5, 5)).isEqualTo(view.getBackground().getRGB());

    // 2. Infinite canvas with opaque fill color
    drawing.attr().set(CANVAS_FILL_COLOR, Color.BLUE);
    drawing.attr().set(CANVAS_FILL_OPACITY, 1.0);
    view.setDrawing(drawing);
    BufferedImage imgBlue = new BufferedImage(120, 80, BufferedImage.TYPE_INT_ARGB);
    Graphics2D gBlue = imgBlue.createGraphics();
    gBlue.setClip(0, 0, 120, 80);
    view.drawBackground(gBlue);
    gBlue.dispose();
    assertThat(imgBlue.getRGB(10, 10)).isEqualTo(Color.BLUE.getRGB());

    // 3. Infinite canvas with null fill color uses checkerboard TexturePaint
    drawing.attr().set(CANVAS_FILL_COLOR, null);
    Paint p1 = view.getBackgroundPaint(0, 0);
    Paint p2 = view.getBackgroundPaint(4, 4);
    assertThat(p1).isInstanceOf(TexturePaint.class);
    assertThat(p2).isInstanceOf(TexturePaint.class);
    assertThat(view.backgroundTile).isNotNull();

    BufferedImage imgChecker = new BufferedImage(120, 80, BufferedImage.TYPE_INT_ARGB);
    Graphics2D gChecker = imgChecker.createGraphics();
    gChecker.setClip(0, 0, 120, 80);
    view.drawBackground(gChecker);
    gChecker.dispose();
    assertThat(imgChecker.getRGB(1, 1)).isEqualTo(new Color(0xdfdfdf).getRGB());
    assertThat(imgChecker.getRGB(9, 1)).isEqualTo(Color.WHITE.getRGB());

    // 4. Empty drawing message renders when drawing has 0 children
    view.setEmptyDrawingMessage("Empty");
    BufferedImage imgEmptyMsg = new BufferedImage(120, 80, BufferedImage.TYPE_INT_ARGB);
    Graphics2D gEmptyMsg = imgEmptyMsg.createGraphics();
    gEmptyMsg.setClip(0, 0, 120, 80);
    view.paintComponent(gEmptyMsg);
    gEmptyMsg.dispose();

    // 5. Fixed canvas + active view draws handles & tool; inactive view does not; printComponent
    // omits handles & tool
    drawing.attr().set(CANVAS_WIDTH, 80.0);
    drawing.attr().set(CANVAS_HEIGHT, 60.0);
    drawing.attr().set(CANVAS_FILL_COLOR, Color.WHITE);
    StubFigure rect = new StubFigure(10, 10, 30, 30);
    rect.attr().set(AttributeKeys.FILL_COLOR, Color.CYAN);
    StubHandle handle = new StubHandle(rect, new Rectangle(10, 10, 8, 8));
    rect.setHandlesForDetailLevel(0, List.of(handle));
    drawing.add(rect);

    StubTool tool = new StubTool();
    editor.setTool(tool);
    editor.add(view);
    view.addToSelection(rect);
    view.setConstrainerVisible(true);

    BufferedImage imgActive = new BufferedImage(120, 80, BufferedImage.TYPE_INT_ARGB);
    Graphics2D gActive = imgActive.createGraphics();
    gActive.setClip(0, 0, 120, 80);
    view.paintComponent(gActive);
    gActive.dispose();
    assertThat(handle.getDrawCalls()).isEqualTo(1);
    assertThat(tool.getDrawCalls()).isEqualTo(1);

    // Inactive view should not draw handles or tool
    DefaultDrawingView otherView = new DefaultDrawingView();
    editor.add(otherView);
    editor.setActiveView(otherView);
    BufferedImage imgInactive = new BufferedImage(120, 80, BufferedImage.TYPE_INT_ARGB);
    Graphics2D gInactive = imgInactive.createGraphics();
    gInactive.setClip(0, 0, 120, 80);
    view.paintComponent(gInactive);
    gInactive.dispose();
    assertThat(handle.getDrawCalls()).isEqualTo(1);
    assertThat(tool.getDrawCalls()).isEqualTo(1);

    // printComponent omits handles & tool even when view is active
    editor.setActiveView(view);
    BufferedImage imgPrint = new BufferedImage(120, 80, BufferedImage.TYPE_INT_ARGB);
    Graphics2D gPrint = imgPrint.createGraphics();
    gPrint.setClip(0, 0, 120, 80);
    view.printComponent(gPrint);
    gPrint.dispose();
    assertThat(handle.getDrawCalls()).isEqualTo(1);
    assertThat(tool.getDrawCalls()).isEqualTo(1);
  }

  @Test
  void deleteAndDuplicateSupportUndoRedoAndProtectNonRemovableFigures() {
    UndoManager undoManager = new UndoManager();
    drawing.addUndoableEditListener(undoManager);
    view.setDrawing(drawing);

    StubFigure f1 = new StubFigure(10, 10, 20, 20);
    StubFigure f2 = new StubFigure(40, 40, 20, 20);
    StubFigure locked = new StubFigure(70, 70, 20, 20);
    locked.setCustomRemovable(false);
    drawing.addAll(List.of(f1, f2, locked));

    // Non-removable selection aborts delete cleanly
    view.addToSelection(List.of(f1, locked));
    view.delete();
    assertThat(drawing.getChildCount()).isEqualTo(3);
    assertThat(view.getSelectedFigures()).containsExactly(f1, locked);

    // Duplicate creates offset clones, calls remap, selects duplicates, and supports undo/redo
    view.clearSelection();
    view.addToSelection(f1);
    view.duplicate();

    assertThat(drawing.getChildCount()).isEqualTo(4);
    assertThat(view.getSelectionCount()).isEqualTo(1);
    Figure dup = view.getSelectedFigures().iterator().next();
    assertThat(dup).isNotSameAs(f1);
    assertThat(dup.getBounds()).isEqualTo(new Rectangle2D.Double(15, 15, 20, 20));
    assertThat(((StubFigure) dup).isRemapCalled()).isTrue();
    assertThat(undoManager.getUndoPresentationName()).isNotEmpty();

    undoManager.undo();
    assertThat(drawing.getChildCount()).isEqualTo(3);
    undoManager.redo();
    assertThat(drawing.getChildCount()).isEqualTo(4);

    // Delete removable figures and verify z-index restoration on undo and removal on redo
    view.clearSelection();
    view.addToSelection(List.of(f1, f2));
    view.delete();
    assertThat(drawing.getChildren()).containsExactly(locked, dup);
    assertThat(view.isSelectionEmpty()).isTrue();

    undoManager.undo();
    assertThat(drawing.getChildren()).containsExactly(f1, f2, locked, dup);
    assertThat(view.getSelectedFigures()).containsExactlyInAnyOrder(f1, f2);

    undoManager.redo();
    assertThat(drawing.getChildren()).containsExactly(locked, dup);
  }
}
