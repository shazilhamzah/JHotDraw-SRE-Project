package org.jhotdraw.draw;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import org.jhotdraw.draw.TestDrawingEditorStub.StubFigure;
import org.jhotdraw.draw.event.FigureEvent;
import org.jhotdraw.draw.event.FigureListenerAdapter;
import org.jhotdraw.draw.event.HandleEvent;
import org.jhotdraw.draw.event.HandleListener;
import org.jhotdraw.draw.figure.Figure;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link DrawingViewSelectionManager}. */
public class DrawingViewSelectionManagerTest {

  private static final HandleListener NOOP_HANDLE_LISTENER = new HandleListener() {
    @Override
    public void areaInvalidated(HandleEvent e) {}

    @Override
    public void handleRequestRemove(HandleEvent e) {}

    @Override
    public void handleRequestSecondaryHandles(HandleEvent e) {}
  };

  @Test
  void addRemoveClearAndSelectAllMaintainListenerLifecycle() {
    DefaultDrawingView view = new DefaultDrawingView();
    DrawingViewHandleManager handleManager = new DrawingViewHandleManager();
    DrawingViewSelectionManager selection =
        new DrawingViewSelectionManager(new FigureListenerAdapter() {
          @Override
          public void figureHandlesChanged(FigureEvent e) {}
        });

    StubFigure f1 = new StubFigure(0, 0, 20, 20);
    StubFigure f2 = new StubFigure(30, 0, 20, 20);
    StubFigure nonSelectable = new StubFigure(60, 0, 20, 20);
    nonSelectable.setCustomSelectable(false);

    DefaultDrawing drawing = new DefaultDrawing();
    drawing.addAll(List.of(f1, f2, nonSelectable));
    int baseCount1 = f1.getFigureListenerCount();
    int baseCount2 = f2.getFigureListenerCount();
    int baseNonSel = nonSelectable.getFigureListenerCount();

    var addDelta = selection.addAll(List.of(f1, f2), view, handleManager, NOOP_HANDLE_LISTENER);
    assertThat(addDelta.changed()).isTrue();
    assertThat(selection.getSelectionCount()).isEqualTo(2);
    assertThat(f1.getFigureListenerCount()).isEqualTo(baseCount1 + 1);
    assertThat(f2.getFigureListenerCount()).isEqualTo(baseCount2 + 1);

    Set<Figure> unmodifiable = selection.getSelectedFigures();
    assertThatThrownBy(() -> unmodifiable.add(f1))
        .isInstanceOf(UnsupportedOperationException.class);

    // Clear detaches all handleInvalidator listeners
    var clearDelta = selection.clear();
    assertThat(clearDelta.changed()).isTrue();
    assertThat(selection.isSelectionEmpty()).isTrue();
    assertThat(f1.getFigureListenerCount()).isEqualTo(baseCount1);
    assertThat(f2.getFigureListenerCount()).isEqualTo(baseCount2);

    // SelectAll attaches listener to selectable figures only
    selection.selectAll(drawing);
    assertThat(selection.getSelectedFigures()).containsExactly(f1, f2);
    assertThat(f1.getFigureListenerCount()).isEqualTo(baseCount1 + 1);
    assertThat(f2.getFigureListenerCount()).isEqualTo(baseCount2 + 1);
    assertThat(nonSelectable.getFigureListenerCount()).isEqualTo(baseNonSel);

    // SelectAll on null drawing clears selection and detaches listeners without NPE
    selection.selectAll(null);
    assertThat(selection.isSelectionEmpty()).isTrue();
    assertThat(f1.getFigureListenerCount()).isEqualTo(baseCount1);
    assertThat(f2.getFigureListenerCount()).isEqualTo(baseCount2);
  }
}
