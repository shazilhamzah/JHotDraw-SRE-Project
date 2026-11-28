package org.jhotdraw.gui.action;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.JPopupMenu;
import org.jhotdraw.draw.DefaultDrawing;
import org.jhotdraw.draw.DefaultDrawingView;
import org.jhotdraw.draw.DrawingEditor;
import org.jhotdraw.editor.DefaultDrawingEditor;
import org.jhotdraw.gui.JPopupButton;

/** Headless fixtures for ButtonFactory tests. */
final class ButtonFactoryTestSupport {

  static {
    System.setProperty("java.awt.headless", "true");
  }

  final DrawingEditor editor = new DefaultDrawingEditor();
  final DefaultDrawingView view = new DefaultDrawingView();

  ButtonFactoryTestSupport() {
    view.setDrawing(new DefaultDrawing());
    editor.add(view);
    editor.setActiveView(view);
  }

  /** Actions of the menu items of a popup button, in order. */
  static List<Action> actions(JPopupButton button) {
    JPopupMenu menu = button.getPopupMenu();
    List<Action> list = new ArrayList<>();
    for (Component c : menu.getComponents()) {
      if (c instanceof AbstractButton && ((AbstractButton) c).getAction() != null) {
        list.add(((AbstractButton) c).getAction());
      }
    }
    return list;
  }

  static void fire(Action a) {
    a.actionPerformed(new java.awt.event.ActionEvent(a, 0, "test"));
  }
}
