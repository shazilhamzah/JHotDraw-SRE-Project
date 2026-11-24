package org.jhotdraw.draw;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.undo.UndoManager;
import org.jhotdraw.draw.TestDrawingEditorStub.StubFigure;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link DrawingViewEditCommands}. */
public class DrawingViewEditCommandsTest {

  @Test
  void emptySelectionAndNullDrawingAreSafeNoOps() {
    DefaultDrawingView view = new DefaultDrawingView();
    UndoManager undoManager = new UndoManager();

    // Null drawing
    AtomicInteger beepCount = new AtomicInteger();
    DrawingViewEditCommands.delete(view, beepCount::incrementAndGet);
    DrawingViewEditCommands.duplicate(view);
    assertThat(beepCount.get()).isZero();

    // Empty selection with drawing installed must not fire spurious UndoableEdits
    DefaultDrawing drawing = new DefaultDrawing();
    drawing.addUndoableEditListener(undoManager);
    view.setDrawing(drawing);
    DrawingViewEditCommands.delete(view, beepCount::incrementAndGet);
    DrawingViewEditCommands.duplicate(view);
    assertThat(undoManager.canUndo()).isFalse();
  }

  @Test
  void deleteAbortsWhenAnyFigureIsNotRemovableAndRestoresZOrderOnUndo() {
    DefaultDrawingView view = new DefaultDrawingView();
    DefaultDrawing drawing = new DefaultDrawing();
    UndoManager undoManager = new UndoManager();
    drawing.addUndoableEditListener(undoManager);
    view.setDrawing(drawing);

    StubFigure f1 = new StubFigure(0, 0, 10, 10);
    StubFigure f2 = new StubFigure(20, 0, 10, 10);
    StubFigure locked = new StubFigure(40, 0, 10, 10);
    locked.setCustomRemovable(false);
    drawing.addAll(List.of(f1, f2, locked));

    view.addToSelection(List.of(f1, locked));
    AtomicInteger beeps = new AtomicInteger();
    DrawingViewEditCommands.delete(view, beeps::incrementAndGet);
    assertThat(beeps.get()).isEqualTo(1);
    assertThat(drawing.getChildCount()).isEqualTo(3);

    view.clearSelection();
    view.addToSelection(f1);
    DrawingViewEditCommands.delete(view, beeps::incrementAndGet);
    assertThat(drawing.getChildren()).containsExactly(f2, locked);

    undoManager.undo();
    assertThat(drawing.getChildren()).containsExactly(f1, f2, locked);
    assertThat(view.getSelectedFigures()).containsExactly(f1);

    undoManager.redo();
    assertThat(drawing.getChildren()).containsExactly(f2, locked);
  }

  @Test
  void duplicateOffsetsClonesRemapsAndSupportsUndoRedo() {
    DefaultDrawingView view = new DefaultDrawingView();
    DefaultDrawing drawing = new DefaultDrawing();
    UndoManager undoManager = new UndoManager();
    drawing.addUndoableEditListener(undoManager);
    view.setDrawing(drawing);

    StubFigure f1 = new StubFigure(10, 20, 30, 40);
    drawing.add(f1);
    view.addToSelection(f1);

    DrawingViewEditCommands.duplicate(view);
    assertThat(drawing.getChildCount()).isEqualTo(2);
    StubFigure clone = (StubFigure) view.getSelectedFigures().iterator().next();
    assertThat(clone.getBounds()).isEqualTo(new Rectangle2D.Double(15, 25, 30, 40));
    assertThat(clone.isRemapCalled()).isTrue();

    undoManager.undo();
    assertThat(drawing.getChildren()).containsExactly(f1);
    undoManager.redo();
    assertThat(drawing.getChildren()).containsExactly(f1, clone);
  }
}
