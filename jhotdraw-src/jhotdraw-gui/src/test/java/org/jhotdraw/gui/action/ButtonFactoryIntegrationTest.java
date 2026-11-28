package org.jhotdraw.gui.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jhotdraw.draw.AttributeKeys.STROKE_CAP;

import java.awt.BasicStroke;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.JToolBar;
import org.jhotdraw.api.app.Disposable;
import org.jhotdraw.draw.figure.RectangleFigure;
import org.jhotdraw.draw.tool.CreationTool;
import org.jhotdraw.gui.JPopupButton;
import org.jhotdraw.utils.util.ResourceBundleUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Headless integration tests for the composite toolbar builders of {@link ButtonFactory}. */
class ButtonFactoryIntegrationTest {

  private ButtonFactoryTestSupport fx;

  @BeforeEach
  void setUp() {
    fx = new ButtonFactoryTestSupport();
  }

  @Test
  void addZoomButtonsToAddsOneButton() {
    JToolBar bar = new JToolBar();
    ButtonFactory.addZoomButtonsTo(bar, fx.editor);
    assertThat(bar.getComponentCount()).isEqualTo(1);
  }

  @Test
  void addColorStrokeAndFontButtonsToCounts() {
    JToolBar colors = new JToolBar();
    ButtonFactory.addColorButtonsTo(colors, fx.editor);
    assertThat(colors.getComponentCount()).isEqualTo(3);
    JToolBar strokes = new JToolBar();
    ButtonFactory.addStrokeButtonsTo(strokes, fx.editor);
    assertThat(strokes.getComponentCount()).isEqualTo(7);
    JToolBar fonts = new JToolBar();
    ButtonFactory.addFontButtonsTo(fonts, fx.editor);
    assertThat(fonts.getComponentCount()).isEqualTo(4);
  }

  @Test
  void addAttributesButtonsToCombinesAllGroups() {
    JToolBar bar = new JToolBar();
    ButtonFactory.addAttributesButtonsTo(bar, fx.editor);
    // pick, apply, sep, 3 colors, sep, 7 strokes, sep, 4 fonts
    assertThat(bar.getComponentCount()).isEqualTo(19);
  }

  @Test
  void addAlignmentButtonsToTracksEachActionOnce() {
    JToolBar bar = new JToolBar();
    List<Disposable> dsp = new ArrayList<>();
    ButtonFactory.addAlignmentButtonsTo(bar, fx.editor, dsp);
    // 6 align + sep + 4 move + sep + 2 (front/back)
    assertThat(bar.getComponentCount()).isEqualTo(14);
    assertThat(dsp).hasSize(12);
    // Regression for the fixed defect: BringToFront/SendToBack used to be untracked while the
    // last move action was added three times.
    assertThat(dsp.get(10)).isNotSameAs(dsp.get(9));
    assertThat(dsp.get(11)).isNotSameAs(dsp.get(9));
  }

  @Test
  void fullToolbarFlowSelectionToolThenCreationTool() {
    JToolBar bar = new JToolBar();
    ResourceBundleUtil labels = ResourceBundleUtil.getBundle("org.jhotdraw.draw.Labels");
    ButtonFactory.addSelectionToolTo(bar, fx.editor);
    CreationTool tool = new CreationTool(new RectangleFigure());
    ButtonFactory.addToolTo(bar, fx.editor, tool, "createRectangle", labels);
    ((AbstractButton) bar.getComponent(1)).setSelected(true);
    assertThat(fx.editor.getTool()).isSameAs(tool);
    ((AbstractButton) bar.getComponent(0)).setSelected(true);
    assertThat(fx.editor.getTool()).isNotSameAs(tool);
  }

  @Test
  void zoomAndGridUpdateViewThroughToolbarButtons() {
    JToolBar bar = new JToolBar();
    ButtonFactory.addZoomButtonsTo(bar, fx.editor);
    JPopupButton zoom = (JPopupButton) bar.getComponent(0);
    ButtonFactoryTestSupport.fire(ButtonFactoryTestSupport.actions(zoom).get(5)); // factor 2
    assertThat(fx.view.getScaleFactor()).isEqualTo(2.0);
    assertThat(zoom.getText()).isEqualTo("200 %");
    AbstractButton grid = ButtonFactory.createToggleGridButton(fx.view);
    grid.doClick();
    assertThat(fx.view.isConstrainerVisible()).isTrue();
  }

  @Test
  void attributeToolbarChangesSelectedFigure() {
    RectangleFigure r = new RectangleFigure(0, 0, 10, 10);
    fx.view.getDrawing().add(r);
    fx.view.addToSelection(r);
    JToolBar bar = new JToolBar();
    ButtonFactory.addStrokeButtonsTo(bar, fx.editor);
    JPopupButton cap = null;
    for (Component c : bar.getComponents()) {
      if (c instanceof JPopupButton
          && ButtonFactoryTestSupport.actions((JPopupButton) c).size() == 3) {
        cap = (JPopupButton) c; // first 3-item popup is the cap button
        break;
      }
    }
    assertThat(cap).isNotNull();
    ButtonFactoryTestSupport.fire(ButtonFactoryTestSupport.actions(cap).get(1));
    assertThat(STROKE_CAP.get(r)).isEqualTo(BasicStroke.CAP_ROUND);
  }
}
