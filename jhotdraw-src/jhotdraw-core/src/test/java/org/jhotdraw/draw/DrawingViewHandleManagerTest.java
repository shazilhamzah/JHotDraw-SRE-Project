package org.jhotdraw.draw;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.jhotdraw.draw.TestDrawingEditorStub.StubFigure;
import org.jhotdraw.draw.TestDrawingEditorStub.StubHandle;
import org.jhotdraw.draw.event.HandleEvent;
import org.jhotdraw.draw.event.HandleListener;
import org.jhotdraw.draw.figure.Figure;
import org.jhotdraw.draw.handle.Handle;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link DrawingViewHandleManager}. */
public class DrawingViewHandleManagerTest {

  private static final HandleListener NOOP_LISTENER = new HandleListener() {
    @Override
    public void areaInvalidated(HandleEvent e) {}

    @Override
    public void handleRequestRemove(HandleEvent e) {}

    @Override
    public void handleRequestSecondaryHandles(HandleEvent e) {}
  };

  @Test
  void validateFallsBackToDetailZeroAndDisposesOnInvalidate() {
    DefaultDrawingView view = new DefaultDrawingView();
    TestDrawingEditorStub editor = new TestDrawingEditorStub();
    view.addNotify(editor);

    StubFigure fig = new StubFigure(0, 0, 40, 40);
    StubHandle h0 = new StubHandle(fig, new Rectangle(5, 5, 10, 10));
    fig.setHandlesForDetailLevel(0, List.of(h0));

    DrawingViewHandleManager manager = new DrawingViewHandleManager();
    manager.setDetailLevel(3);
    manager.invalidate(NOOP_LISTENER, () -> {});
    Set<Figure> selected = new LinkedHashSet<>(List.of(fig));

    AtomicInteger repaints = new AtomicInteger();
    manager.validate(view, editor, selected, NOOP_LISTENER, r -> repaints.incrementAndGet());

    assertThat(manager.getDetailLevel()).isZero();
    assertThat(manager.getSelectionHandles(view, editor, selected, NOOP_LISTENER, r -> {}))
        .containsExactly(h0);
    assertThat(repaints.get()).isEqualTo(1);
    assertThat(h0.getHandleListenerCount()).isEqualTo(1);

    manager.fireViewTransformChanged();
    assertThat(h0.getViewTransformChangedCalls()).isEqualTo(1);

    manager.invalidate(NOOP_LISTENER, () -> {});
    assertThat(h0.getDisposeCalls()).isEqualTo(1);
    assertThat(h0.getHandleListenerCount()).isZero();
  }

  @Test
  void secondaryHandlesReplacePreviousWithoutLeakingListeners() {
    DefaultDrawingView view = new DefaultDrawingView();
    StubFigure fig = new StubFigure(0, 0, 50, 50);
    StubHandle owner1 = new StubHandle(fig, new Rectangle(0, 0, 10, 10));
    StubHandle sec1 = new StubHandle(fig, new Rectangle(2, 2, 6, 6));
    owner1.addSecondaryHandle(sec1);

    StubHandle owner2 = new StubHandle(fig, new Rectangle(20, 20, 10, 10));
    StubHandle sec2 = new StubHandle(fig, new Rectangle(22, 22, 6, 6));
    owner2.addSecondaryHandle(sec2);

    DrawingViewHandleManager manager = new DrawingViewHandleManager();
    manager.setSecondaryHandles(owner1, view, NOOP_LISTENER);
    assertThat(sec1.getHandleListenerCount()).isEqualTo(1);

    manager.setSecondaryHandles(owner2, view, NOOP_LISTENER);
    assertThat(sec1.getDisposeCalls()).isEqualTo(1);
    assertThat(sec1.getHandleListenerCount()).isZero();
    assertThat(sec2.getHandleListenerCount()).isEqualTo(1);

    manager.removeHandle(sec2, NOOP_LISTENER);
    assertThat(sec2.getDisposeCalls()).isEqualTo(1);
    assertThat(sec2.getHandleListenerCount()).isZero();
  }

  @Test
  void findHandleAndCompatibleHandlesRespectPriorityAndOwnerUniqueness() {
    DefaultDrawingView view = new DefaultDrawingView();
    TestDrawingEditorStub editor = new TestDrawingEditorStub();
    view.addNotify(editor);

    StubFigure f1 = new StubFigure(0, 0, 20, 20);
    StubFigure f2 = new StubFigure(30, 0, 20, 20);
    StubHandle h1 = new StubHandle(f1, new Rectangle(0, 0, 10, 10), true);
    StubHandle h2a = new StubHandle(f2, new Rectangle(30, 0, 10, 10), true);
    StubHandle h2b = new StubHandle(f2, new Rectangle(40, 0, 10, 10), true);
    f1.setHandlesForDetailLevel(0, List.of(h1));
    f2.setHandlesForDetailLevel(0, List.of(h2a, h2b));

    Set<Figure> selected = new LinkedHashSet<>(List.of(f1, f2));
    DrawingViewHandleManager manager = new DrawingViewHandleManager();
    manager.invalidate(NOOP_LISTENER, () -> {});

    assertThat(manager.findHandle(new Point(35, 5), view, editor, selected, NOOP_LISTENER, r -> {}))
        .isSameAs(h2a);
    Collection<Handle> compat =
        manager.getCompatibleHandles(h1, view, editor, selected, NOOP_LISTENER, r -> {});
    assertThat(compat).containsExactly(h1, h2a);
  }
}
