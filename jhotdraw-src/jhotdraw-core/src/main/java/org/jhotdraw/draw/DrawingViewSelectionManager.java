package org.jhotdraw.draw;

import java.awt.Rectangle;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import org.jhotdraw.draw.event.FigureListener;
import org.jhotdraw.draw.event.HandleListener;
import org.jhotdraw.draw.figure.Figure;

/**
 * Manages figure selection state, unmodifiable view exposure, and figure listener registration for
 * {@link DefaultDrawingView}.
 */
class DrawingViewSelectionManager {

  private final Set<Figure> selectedFigures = new LinkedHashSet<>();
  private final Set<Figure> unmodifiableSelectedFigures =
      Collections.unmodifiableSet(selectedFigures);
  private final FigureListener handleInvalidator;

  DrawingViewSelectionManager(FigureListener handleInvalidator) {
    this.handleInvalidator = handleInvalidator;
  }

  Set<Figure> getSelectedFigures() {
    return unmodifiableSelectedFigures;
  }

  Set<Figure> getMutableSelection() {
    return selectedFigures;
  }

  int getSelectionCount() {
    return selectedFigures.size();
  }

  boolean isSelectionEmpty() {
    return selectedFigures.isEmpty();
  }

  boolean isFigureSelected(Figure figure) {
    return selectedFigures.contains(figure);
  }

  SelectionDelta addAll(
      Collection<Figure> figures,
      DrawingView view,
      DrawingViewHandleManager handleManager,
      HandleListener handleListener) {
    Set<Figure> oldSelection = new HashSet<>(selectedFigures);
    Set<Figure> newSelection = new HashSet<>(selectedFigures);
    boolean changed = false;
    Rectangle invalidatedArea = null;
    for (Figure figure : figures) {
      if (selectedFigures.add(figure)) {
        changed = true;
        newSelection.add(figure);
        figure.addFigureListener(handleInvalidator);
        invalidatedArea =
            handleManager.createHandlesForFigure(figure, view, handleListener, invalidatedArea);
      }
    }
    return new SelectionDelta(changed, oldSelection, newSelection, invalidatedArea);
  }

  SelectionDelta remove(Figure figure) {
    Set<Figure> oldSelection = new HashSet<>(selectedFigures);
    if (!selectedFigures.remove(figure)) {
      return SelectionDelta.unchanged();
    }
    figure.removeFigureListener(handleInvalidator);
    return new SelectionDelta(true, oldSelection, new HashSet<>(selectedFigures), null);
  }

  SelectionDelta selectAll(Drawing drawing) {
    Set<Figure> oldSelection = new HashSet<>(selectedFigures);
    detachAllFigureListeners();
    selectedFigures.clear();
    if (drawing != null) {
      for (Figure figure : drawing.getChildren()) {
        if (figure.isSelectable()) {
          selectedFigures.add(figure);
          figure.addFigureListener(handleInvalidator);
        }
      }
    }
    return new SelectionDelta(true, oldSelection, new HashSet<>(selectedFigures), null);
  }

  SelectionDelta clear() {
    if (selectedFigures.isEmpty()) {
      return SelectionDelta.unchanged();
    }
    Set<Figure> oldSelection = new HashSet<>(selectedFigures);
    detachAllFigureListeners();
    selectedFigures.clear();
    return new SelectionDelta(true, oldSelection, new HashSet<>(selectedFigures), null);
  }

  private void detachAllFigureListeners() {
    for (Figure figure : selectedFigures) {
      figure.removeFigureListener(handleInvalidator);
    }
  }

  record SelectionDelta(
      boolean changed,
      Set<Figure> oldSelection,
      Set<Figure> newSelection,
      Rectangle invalidatedArea) {

    static SelectionDelta unchanged() {
      return new SelectionDelta(false, Collections.emptySet(), Collections.emptySet(), null);
    }
  }
}
