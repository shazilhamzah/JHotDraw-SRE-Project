package org.jhotdraw.draw;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.jhotdraw.draw.event.HandleListener;
import org.jhotdraw.draw.figure.Figure;
import org.jhotdraw.draw.handle.Handle;
import org.jhotdraw.utils.util.ReversedList;

/**
 * Manages selection handles, secondary handles, handle detail level, active handle state, and
 * handle lookup for {@link DefaultDrawingView}.
 */
class DrawingViewHandleManager {

  private final List<Handle> selectionHandles = new ArrayList<>();
  private final List<Handle> secondaryHandles = new ArrayList<>();
  private Handle activeHandle;
  private boolean handlesAreValid = true;
  private int detailLevel;

  boolean areHandlesValid() {
    return handlesAreValid;
  }

  int getDetailLevel() {
    return detailLevel;
  }

  void setDetailLevel(int detailLevel) {
    this.detailLevel = detailLevel;
  }

  Handle getActiveHandle() {
    return activeHandle;
  }

  Handle replaceActiveHandle(Handle newValue) {
    Handle oldValue = activeHandle;
    activeHandle = newValue;
    return oldValue;
  }

  List<Handle> getSelectionHandles(
      DrawingView view,
      DrawingEditor editor,
      Set<Figure> selectedFigures,
      HandleListener listener,
      Consumer<Rectangle> repainter) {
    validate(view, editor, selectedFigures, listener, repainter);
    return Collections.unmodifiableList(selectionHandles);
  }

  List<Handle> getSecondaryHandles(
      DrawingView view,
      DrawingEditor editor,
      Set<Figure> selectedFigures,
      HandleListener listener,
      Consumer<Rectangle> repainter) {
    validate(view, editor, selectedFigures, listener, repainter);
    return Collections.unmodifiableList(secondaryHandles);
  }

  Rectangle createHandlesForFigure(
      Figure figure, DrawingView view, HandleListener listener, Rectangle invalidatedArea) {
    if (!handlesAreValid || view.getEditor() == null) {
      return invalidatedArea;
    }
    Rectangle area = invalidatedArea;
    for (Handle h : figure.createHandles(detailLevel)) {
      h.setView(view);
      selectionHandles.add(h);
      h.addHandleListener(listener);
      area = unionArea(area, h.getDrawingArea());
    }
    return area;
  }

  Rectangle invalidate(HandleListener listener, Runnable activeHandleClearer) {
    if (!handlesAreValid) {
      return null;
    }
    handlesAreValid = false;
    Rectangle invalidatedArea = disposeHandles(selectionHandles, listener, null);
    invalidatedArea = disposeHandles(secondaryHandles, listener, invalidatedArea);
    activeHandleClearer.run();
    return invalidatedArea;
  }

  void validate(
      DrawingView view,
      DrawingEditor editor,
      Set<Figure> selectedFigures,
      HandleListener listener,
      Consumer<Rectangle> repainter) {
    if (handlesAreValid || editor == null) {
      return;
    }
    handlesAreValid = true;
    selectionHandles.clear();
    Rectangle invalidatedArea = populateSelectionHandles(view, selectedFigures, listener);
    if (selectionHandles.isEmpty() && detailLevel != 0) {
      detailLevel = 0;
      Rectangle fallbackArea = populateSelectionHandles(view, selectedFigures, listener);
      invalidatedArea = unionArea(invalidatedArea, fallbackArea);
    }
    if (invalidatedArea != null) {
      repainter.accept(invalidatedArea);
    }
  }

  private Rectangle populateSelectionHandles(
      DrawingView view, Set<Figure> selectedFigures, HandleListener listener) {
    Rectangle invalidatedArea = null;
    for (Figure figure : selectedFigures) {
      for (Handle handle : figure.createHandles(detailLevel)) {
        handle.setView(view);
        selectionHandles.add(handle);
        handle.addHandleListener(listener);
        invalidatedArea = unionArea(invalidatedArea, handle.getDrawingArea());
      }
    }
    return invalidatedArea;
  }

  void setSecondaryHandles(Handle owner, DrawingView view, HandleListener listener) {
    disposeHandles(secondaryHandles, listener, null);
    Collection<Handle> created = owner.createSecondaryHandles();
    if (created != null) {
      for (Handle h : created) {
        h.setView(view);
        h.addHandleListener(listener);
        secondaryHandles.add(h);
      }
    }
  }

  void removeHandle(Handle handle, HandleListener listener) {
    handle.removeHandleListener(listener);
    selectionHandles.remove(handle);
    secondaryHandles.remove(handle);
    handle.dispose();
  }

  Handle findHandle(
      Point p,
      DrawingView view,
      DrawingEditor editor,
      Set<Figure> selectedFigures,
      HandleListener listener,
      Consumer<Rectangle> repainter) {
    validate(view, editor, selectedFigures, listener, repainter);
    for (Handle handle : new ReversedList<>(secondaryHandles)) {
      if (handle.contains(p)) {
        return handle;
      }
    }
    for (Handle handle : new ReversedList<>(selectionHandles)) {
      if (handle.contains(p)) {
        return handle;
      }
    }
    return null;
  }

  Collection<Handle> getCompatibleHandles(
      Handle master,
      DrawingView view,
      DrawingEditor editor,
      Set<Figure> selectedFigures,
      HandleListener listener,
      Consumer<Rectangle> repainter) {
    validate(view, editor, selectedFigures, listener, repainter);
    Set<Figure> owners = new HashSet<>();
    List<Handle> compatibleHandles = new ArrayList<>();
    owners.add(master.getOwner());
    compatibleHandles.add(master);
    for (Handle handle : selectionHandles) {
      if (!owners.contains(handle.getOwner()) && handle.isCombinableWith(master)) {
        owners.add(handle.getOwner());
        compatibleHandles.add(handle);
      }
    }
    return compatibleHandles;
  }

  Rectangle computeAllHandlesArea(
      DrawingView view,
      DrawingEditor editor,
      Set<Figure> selectedFigures,
      HandleListener listener,
      Consumer<Rectangle> repainter) {
    validate(view, editor, selectedFigures, listener, repainter);
    Rectangle area = null;
    for (Handle h : selectionHandles) {
      area = unionArea(area, h.getDrawingArea());
    }
    for (Handle h : secondaryHandles) {
      area = unionArea(area, h.getDrawingArea());
    }
    return area;
  }

  void fireViewTransformChanged() {
    for (Handle handle : selectionHandles) {
      handle.viewTransformChanged();
    }
    for (Handle handle : secondaryHandles) {
      handle.viewTransformChanged();
    }
  }

  private static Rectangle disposeHandles(
      List<Handle> handles, HandleListener listener, Rectangle area) {
    Rectangle result = area;
    for (Handle handle : handles) {
      handle.removeHandleListener(listener);
      result = unionArea(result, handle.getDrawingArea());
      handle.dispose();
    }
    handles.clear();
    return result;
  }

  static Rectangle unionArea(Rectangle current, Rectangle addition) {
    if (addition == null) {
      return current;
    }
    if (current == null) {
      return new Rectangle(addition);
    }
    current.add(addition);
    return current;
  }
}
