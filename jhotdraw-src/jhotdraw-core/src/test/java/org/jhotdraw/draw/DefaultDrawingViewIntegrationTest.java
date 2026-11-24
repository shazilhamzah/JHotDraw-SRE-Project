package org.jhotdraw.draw;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.FocusEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;
import javax.swing.undo.UndoManager;
import org.jhotdraw.draw.TestDrawingEditorStub.StubTool;
import org.jhotdraw.draw.figure.BezierFigure;
import org.jhotdraw.draw.figure.GroupFigure;
import org.jhotdraw.draw.figure.RectangleFigure;
import org.jhotdraw.draw.io.DefaultDrawingViewTransferHandler;
import org.jhotdraw.utils.geom.path.BezierPath;
import org.junit.jupiter.api.Test;

/** Integration tests for {@link DefaultDrawingView} with real drawings, figures, undo, and EDT timer. */
public class DefaultDrawingViewIntegrationTest {

  @Test
  void setDrawingSwapsListenersAndReenablesPaintingViaEdtTimer() throws Exception {
    DefaultDrawingView view = new DefaultDrawingView();
    view.setDrawingDoubleBuffered(false);
    view.setBounds(0, 0, 100, 100);

    DefaultDrawing d1 = new DefaultDrawing();
    RectangleFigure r1 = new RectangleFigure(10, 10, 20, 20);
    d1.add(r1);

    DefaultDrawing d2 = new DefaultDrawing();
    RectangleFigure r2 = new RectangleFigure(20, 20, 30, 30);
    r2.attr().set(AttributeKeys.FILL_COLOR, Color.RED);
    d2.add(r2);

    List<PropertyChangeEvent> drawingEvents = new ArrayList<>();
    view.addPropertyChangeListener(e -> {
      if (DrawingView.DRAWING_PROPERTY.equals(e.getPropertyName())) {
        drawingEvents.add(e);
      }
    });

    SwingUtilities.invokeAndWait(() -> {
      view.setDrawing(d1);
      view.addToSelection(r1);
      view.setDrawing(d2);
    });

    assertThat(view.getDrawing()).isSameAs(d2);
    assertThat(view.isSelectionEmpty()).isTrue();
    assertThat(drawingEvents).hasSize(2);
    assertThat(drawingEvents.get(1).getOldValue()).isSameAs(d1);
    assertThat(drawingEvents.get(1).getNewValue()).isSameAs(d2);

    // Wait up to 1000ms for the 10ms Swing Timer to set paintEnabled = true on the EDT.
    AtomicBoolean paintedRed = new AtomicBoolean(false);
    long deadline = System.currentTimeMillis() + 1000;
    while (System.currentTimeMillis() < deadline && !paintedRed.get()) {
      SwingUtilities.invokeAndWait(() -> {
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setClip(0, 0, 100, 100);
        view.paint(g);
        g.dispose();
        if (img.getRGB(35, 35) == Color.RED.getRGB()) {
          paintedRed.set(true);
        }
      });
      if (!paintedRed.get()) {
        Thread.sleep(15);
      }
    }
    assertThat(paintedRed.get()).isTrue();

    // Mutating old drawing d1 must no longer clear or affect selection in view
    view.addToSelection(r2);
    d1.remove(r1);
    assertThat(view.getSelectedFigures()).containsExactly(r2);
  }

  @Test
  void editorNotifyFocusTooltipAndTransferHandlerIntegration() {
    DefaultDrawingView view = new DefaultDrawingView();
    TestDrawingEditorStub editor = new TestDrawingEditorStub();
    StubTool tool = new StubTool();
    tool.setToolTipText("CustomViewTooltip");

    MouseEvent mouseEvt = new MouseEvent(view, MouseEvent.MOUSE_MOVED, 0, 0, 10, 10, 0, false);
    assertThat(view.getToolTipText(mouseEvt)).isNull();

    // Focus gained without editor is safe
    DefaultDrawingView.EventHandler handler = view.createEventHandler();
    handler.focusGained(new FocusEvent(view, FocusEvent.FOCUS_GAINED));
    assertThat(editor.getSetActiveViewCalls()).isZero();

    view.addNotify(editor);
    assertThat(view.getEditor()).isSameAs(editor);
    assertThat(view.getToolTipText(mouseEvt)).isNull(); // tool still null

    editor.setTool(tool);
    assertThat(view.getToolTipText(mouseEvt)).isEqualTo("CustomViewTooltip");

    handler.focusGained(new FocusEvent(view, FocusEvent.FOCUS_GAINED));
    assertThat(editor.getSetActiveViewCalls()).isEqualTo(1);
    assertThat(editor.getLastActiveView()).isSameAs(view);

    assertThat(view.getTransferHandler()).isInstanceOf(DefaultDrawingViewTransferHandler.class);
    DefaultDrawingViewTransferHandler th =
        (DefaultDrawingViewTransferHandler) view.getTransferHandler();
    assertThat(th.extractDrawingView(view)).isSameAs(view);

    view.removeNotify(editor);
    assertThat(view.getEditor()).isNull();

    // Swing removeNotify hierarchy cleanup
    view.removeNotify();
  }

  @Test
  void endToEndSelectionHandleDuplicateDeleteUndoRedoAndRenderFlow() {
    DefaultDrawingView view = new DefaultDrawingView();
    view.setDrawingDoubleBuffered(false);
    view.setBounds(0, 0, 300, 300);

    DefaultDrawing drawing = new DefaultDrawing();
    UndoManager undoManager = new UndoManager();
    drawing.addUndoableEditListener(undoManager);

    RectangleFigure rect = new RectangleFigure(10, 10, 40, 30);
    BezierFigure bezier = new BezierFigure(true);
    bezier.addNode(new BezierPath.Node(70, 10));
    bezier.addNode(new BezierPath.Node(110, 40));
    bezier.addNode(new BezierPath.Node(70, 50));

    GroupFigure group = new GroupFigure();
    group.add(new RectangleFigure(130, 10, 20, 20));
    group.add(new RectangleFigure(155, 10, 20, 20));

    drawing.addAll(List.of(rect, bezier, group));

    TestDrawingEditorStub editor = new TestDrawingEditorStub();
    editor.setTool(new StubTool());
    editor.add(view);
    view.setDrawing(drawing);

    // Select all and verify handles created on real figures
    view.selectAll();
    assertThat(view.getSelectionCount()).isEqualTo(3);
    Rectangle2D.Double rectBounds = rect.getBounds();
    Point rectCorner = view.drawingToView(new Point2D.Double(rectBounds.x, rectBounds.y));
    assertThat(view.findHandle(rectCorner)).isNotNull();

    // Render with handles
    renderToImage(view);

    // Duplicate all 3 figures
    view.duplicate();
    assertThat(drawing.getChildCount()).isEqualTo(6);
    assertThat(view.getSelectionCount()).isEqualTo(3);
    assertThat(view.getSelectedFigures()).doesNotContain(rect, bezier, group);
    renderToImage(view);

    // Undo and redo duplicate
    undoManager.undo();
    assertThat(drawing.getChildCount()).isEqualTo(3);
    undoManager.redo();
    assertThat(drawing.getChildCount()).isEqualTo(6);

    // Delete duplicated figures and undo/redo
    view.clearSelection();
    view.addToSelection(List.of(rect, group));
    view.delete();
    assertThat(drawing.getChildCount()).isEqualTo(4);
    assertThat(view.isSelectionEmpty()).isTrue();
    renderToImage(view);

    undoManager.undo();
    assertThat(drawing.getChildCount()).isEqualTo(6);
    assertThat(drawing.indexOf(rect)).isZero();
    assertThat(drawing.indexOf(group)).isEqualTo(2);
    assertThat(view.getSelectedFigures()).containsExactlyInAnyOrder(rect, group);

    undoManager.redo();
    assertThat(drawing.getChildCount()).isEqualTo(4);
    assertThat(drawing.getChildren()).doesNotContain(rect, group);
    renderToImage(view);

    // Verify bounding area sanity
    assertThat(view.getDrawingArea()).isInstanceOf(Rectangle2D.Double.class);
  }

  private void renderToImage(DefaultDrawingView view) {
    BufferedImage image = new BufferedImage(300, 300, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    g.setClip(0, 0, 300, 300);
    view.paintComponent(g);
    g.dispose();
  }
}
