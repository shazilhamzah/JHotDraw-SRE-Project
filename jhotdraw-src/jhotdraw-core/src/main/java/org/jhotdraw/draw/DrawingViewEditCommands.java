package org.jhotdraw.draw;

import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import org.jhotdraw.draw.figure.Figure;
import org.jhotdraw.utils.util.ResourceBundleUtil;

/**
 * Encapsulates {@code delete} and {@code duplicate} editing commands and their {@code UndoableEdit}
 * objects for {@link DefaultDrawingView}.
 */
final class DrawingViewEditCommands {

  private static final int DUPLICATE_OFFSET_X = 5;
  private static final int DUPLICATE_OFFSET_Y = 5;
  private static final String LABELS_BUNDLE = "org.jhotdraw.draw.Labels";

  private DrawingViewEditCommands() {}

  static void delete(DrawingView view, Runnable onNotRemovable) {
    Drawing targetDrawing = view.getDrawing();
    if (targetDrawing == null || view.getSelectionCount() == 0) {
      return;
    }

    List<Figure> deletedFigures = targetDrawing.sort(view.getSelectedFigures());
    for (Figure f : deletedFigures) {
      if (!f.isRemovable()) {
        onNotRemovable.run();
        return;
      }
    }

    int[] deletedFigureIndices = new int[deletedFigures.size()];
    for (int i = 0; i < deletedFigureIndices.length; i++) {
      deletedFigureIndices[i] = targetDrawing.indexOf(deletedFigures.get(i));
    }

    view.clearSelection();
    targetDrawing.removeAll(deletedFigures);
    targetDrawing.fireUndoableEditHappened(
        new DeleteFiguresEdit(view, targetDrawing, deletedFigures, deletedFigureIndices));
  }

  static void duplicate(DrawingView view) {
    Drawing targetDrawing = view.getDrawing();
    if (targetDrawing == null || view.getSelectionCount() == 0) {
      return;
    }

    Collection<Figure> sorted = targetDrawing.sort(view.getSelectedFigures());
    Map<Figure, Figure> originalToDuplicateMap = new HashMap<>(sorted.size());
    view.clearSelection();

    List<Figure> duplicates = new ArrayList<>(sorted.size());
    AffineTransform tx =
        AffineTransform.getTranslateInstance(DUPLICATE_OFFSET_X, DUPLICATE_OFFSET_Y);
    for (Figure f : sorted) {
      Figure duplicate = f.clone();
      duplicate.transform(tx);
      duplicates.add(duplicate);
      originalToDuplicateMap.put(f, duplicate);
      targetDrawing.add(duplicate);
    }
    for (Figure f : duplicates) {
      f.remap(originalToDuplicateMap, false);
    }

    view.addToSelection(duplicates);
    targetDrawing.fireUndoableEditHappened(new DuplicateFiguresEdit(targetDrawing, duplicates));
  }

  private static final class DeleteFiguresEdit extends AbstractUndoableEdit {

    private static final long serialVersionUID = 1L;
    private final DrawingView view;
    private final Drawing targetDrawing;
    private final List<Figure> deletedFigures;
    private final int[] deletedFigureIndices;

    DeleteFiguresEdit(
        DrawingView view,
        Drawing targetDrawing,
        List<Figure> deletedFigures,
        int[] deletedFigureIndices) {
      this.view = view;
      this.targetDrawing = targetDrawing;
      this.deletedFigures = deletedFigures;
      this.deletedFigureIndices = deletedFigureIndices;
    }

    @Override
    public String getPresentationName() {
      ResourceBundleUtil labels = ResourceBundleUtil.getBundle(LABELS_BUNDLE);
      return labels.getString("edit.delete.text");
    }

    @Override
    public void undo() throws CannotUndoException {
      super.undo();
      view.clearSelection();
      for (int i = 0; i < deletedFigureIndices.length; i++) {
        targetDrawing.add(deletedFigureIndices[i], deletedFigures.get(i));
      }
      view.addToSelection(deletedFigures);
    }

    @Override
    public void redo() throws CannotRedoException {
      super.redo();
      for (Figure figure : deletedFigures) {
        targetDrawing.remove(figure);
      }
    }
  }

  private static final class DuplicateFiguresEdit extends AbstractUndoableEdit {

    private static final long serialVersionUID = 1L;
    private final Drawing targetDrawing;
    private final List<Figure> duplicates;

    DuplicateFiguresEdit(Drawing targetDrawing, List<Figure> duplicates) {
      this.targetDrawing = targetDrawing;
      this.duplicates = duplicates;
    }

    @Override
    public String getPresentationName() {
      ResourceBundleUtil labels = ResourceBundleUtil.getBundle(LABELS_BUNDLE);
      return labels.getString("edit.duplicate.text");
    }

    @Override
    public void undo() throws CannotUndoException {
      super.undo();
      targetDrawing.removeAll(duplicates);
    }

    @Override
    public void redo() throws CannotRedoException {
      super.redo();
      targetDrawing.addAll(duplicates);
    }
  }
}
