package org.jhotdraw.gui.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.jhotdraw.draw.AttributeKeys.FILL_COLOR;
import static org.jhotdraw.draw.AttributeKeys.FONT_BOLD;
import static org.jhotdraw.draw.AttributeKeys.FONT_FACE;
import static org.jhotdraw.draw.AttributeKeys.STROKE_CAP;
import static org.jhotdraw.draw.AttributeKeys.STROKE_JOIN;
import static org.jhotdraw.draw.AttributeKeys.STROKE_WIDTH;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import org.jhotdraw.api.app.Disposable;
import org.jhotdraw.draw.DrawingEditor;
import org.jhotdraw.draw.action.ColorIcon;
import org.jhotdraw.draw.action.DrawingColorIcon;
import org.jhotdraw.draw.figure.RectangleFigure;
import org.jhotdraw.draw.figure.TextFigure;
import org.jhotdraw.draw.tool.CreationTool;
import org.jhotdraw.draw.tool.DelegationSelectionTool;
import org.jhotdraw.gui.JPopupButton;
import org.jhotdraw.utils.util.ResourceBundleUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit-level characterization tests for {@link ButtonFactory}. */
class ButtonFactoryTest {

  private ButtonFactoryTestSupport fx;
  private DrawingEditor editor;
  private final ResourceBundleUtil labels =
      ResourceBundleUtil.getBundle("org.jhotdraw.draw.Labels");

  @BeforeEach
  void setUp() {
    fx = new ButtonFactoryTestSupport();
    editor = fx.editor;
  }

  // ---------- palettes ----------
  @Test
  void defaultPalette() {
    assertThat(ButtonFactory.DEFAULT_COLORS).hasSize(48);
    assertThat(ButtonFactory.DEFAULT_COLORS_COLUMN_COUNT).isEqualTo(8);
    assertThat(ButtonFactory.DEFAULT_COLORS.size() % ButtonFactory.DEFAULT_COLORS_COLUMN_COUNT)
        .isZero();
    assertThatThrownBy(() -> ButtonFactory.DEFAULT_COLORS.add(new ColorIcon(0)))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void websavePalette() {
    assertThat(ButtonFactory.WEBSAVE_COLORS_COLUMN_COUNT).isEqualTo(19);
    assertThat(ButtonFactory.WEBSAVE_COLORS).isNotEmpty();
    assertThatThrownBy(() -> ButtonFactory.WEBSAVE_COLORS.add(new ColorIcon(0)))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void hsbPalettes() {
    assertThat(ButtonFactory.HSB_COLORS_COLUMN_COUNT).isEqualTo(12);
    assertThat(ButtonFactory.HSB_COLORS_AS_RGB_COLUMN_COUNT).isEqualTo(12);
    assertThat(ButtonFactory.HSB_COLORS).hasSize(120);
    assertThat(ButtonFactory.HSB_COLORS_AS_RGB).hasSize(120);
    assertThatThrownBy(() -> ButtonFactory.HSB_COLORS.add(new ColorIcon(0)))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  // ---------- action collections ----------
  @Test
  void drawingActionsTrackOnlySelectSameAsDisposable() {
    List<Disposable> dsp = new ArrayList<>();
    Collection<Action> actions = ButtonFactory.createDrawingActions(editor, dsp);
    assertThat(actions).hasSize(4).doesNotContainNull();
    assertThat(dsp).hasSize(1);
    assertThat(ButtonFactory.createDrawingActions(editor)).hasSize(4);
  }

  @Test
  void selectionActionsContainSeparators() {
    List<Action> a = new ArrayList<>(ButtonFactory.createSelectionActions(editor));
    assertThat(a).hasSize(7);
    assertThat(a.get(1)).isNull();
    assertThat(a.get(4)).isNull();
    assertThat(a.get(0)).isNotNull();
  }

  // ---------- zoom ----------
  @Test
  void zoomButtonForViewHasElevenItemsAndTracksScale() {
    AbstractButton b = ButtonFactory.createZoomButton(fx.view);
    assertThat(b.getText()).isEqualTo("100 %");
    assertThat(ButtonFactoryTestSupport.actions((JPopupButton) b)).hasSize(11);
    fx.view.setScaleFactor(2.0);
    assertThat(b.getText()).isEqualTo("200 %");
  }

  @Test
  void zoomButtonForViewWithCustomFactors() {
    AbstractButton b = ButtonFactory.createZoomButton(fx.view, new double[] {1, 2});
    assertThat(ButtonFactoryTestSupport.actions((JPopupButton) b)).hasSize(2);
  }

  @Test
  void zoomActionChangesViewScale() {
    JPopupButton b = (JPopupButton) ButtonFactory.createZoomButton(fx.view);
    ButtonFactoryTestSupport.fire(ButtonFactoryTestSupport.actions(b).get(3)); // factor 2
    assertThat(fx.view.getScaleFactor()).isEqualTo(2.0);
    assertThat(b.getText()).isEqualTo("200 %");
  }

  @Test
  void zoomButtonForEditorHasThirteenItems() {
    JPopupButton b = (JPopupButton) ButtonFactory.createZoomButton(editor);
    assertThat(ButtonFactoryTestSupport.actions(b)).hasSize(13);
    assertThat(b.getText()).isEqualTo("100 %");
  }

  // ---------- grid ----------
  @Test
  void toggleGridButtonSyncsBothWays() {
    AbstractButton b = ButtonFactory.createToggleGridButton(fx.view);
    assertThat(b).isInstanceOf(JToggleButton.class);
    assertThat(fx.view.isConstrainerVisible()).isFalse();
    b.setSelected(true);
    assertThat(fx.view.isConstrainerVisible()).isTrue();
    fx.view.setConstrainerVisible(false);
    assertThat(b.isSelected()).isFalse();
  }

  // ---------- color buttons ----------
  @Test
  void colorButtonsAreCreated() {
    assertThat(ButtonFactory.createColorButtonFill(editor)).isNotNull();
    assertThat(ButtonFactory.createColorButtonStroke(editor)).isNotNull();
    assertThat(ButtonFactory.createColorButtonText(editor)).isNotNull();
    JPopupButton b = ButtonFactory.createColorButtonFill(editor, ButtonFactory.DEFAULT_COLORS, 8);
    assertThat(b.getPopupMenu()).isNotNull();
    assertThat(b.isFocusable()).isFalse();
  }

  @Test
  void editorSelectionAndDrawingColorButtonsUseSwatches() {
    JPopupButton e = ButtonFactory.createEditorColorButton(
        editor, FILL_COLOR, ButtonFactory.DEFAULT_COLORS, 8, "attribute.fillColor", labels);
    JPopupButton s = ButtonFactory.createSelectionColorButton(
        editor, FILL_COLOR, ButtonFactory.DEFAULT_COLORS, 8, "attribute.fillColor", labels);
    JPopupButton d = ButtonFactory.createDrawingColorButton(
        editor, FILL_COLOR, ButtonFactory.DEFAULT_COLORS, 8, "attribute.fillColor", labels);
    for (JPopupButton b : List.of(e, s, d)) {
      assertThat(b).isNotNull();
      assertThat(b.getPopupMenu()).isNotNull();
      assertThat(b.isFocusable()).isFalse();
    }
  }

  @Test
  void selectionColorChooserTracksRepainterAsDisposable() {
    List<Disposable> dsp = new ArrayList<>();
    JPopupButton b = ButtonFactory.createSelectionColorChooserButton(
        editor,
        FILL_COLOR,
        "attribute.fillColor",
        labels,
        new java.util.HashMap<>(),
        new Rectangle(1, 17, 20, 4),
        dsp);
    assertThat(b).isNotNull();
    assertThat(dsp).hasSize(1);
  }

  @Test
  void drawingColorChooserDelegatesToSelectionChooserDefect() {
    // Characterizes defect: createDrawingColorChooserButton(7 args) calls the *Selection* variant.
    List<Disposable> dsp = new ArrayList<>();
    JPopupButton b = ButtonFactory.createDrawingColorChooserButton(
        editor,
        FILL_COLOR,
        "attribute.fillColor",
        labels,
        new java.util.HashMap<>(),
        new Rectangle(1, 17, 20, 4),
        dsp);
    assertThat(b.getIcon()).isInstanceOf(DrawingColorIcon.class);
  }

  // ---------- stroke ----------
  @Test
  void strokeWidthButtonItemsAndIgnoredWidthsParameterDefect() {
    JPopupButton def = ButtonFactory.createStrokeWidthButton(editor);
    assertThat(ButtonFactoryTestSupport.actions(def)).hasSize(8);
    // Characterizes defect: the double[] overload ignores its argument.
    JPopupButton custom = ButtonFactory.createStrokeWidthButton(editor, new double[] {1, 2});
    assertThat(ButtonFactoryTestSupport.actions(custom)).hasSize(2);
    JPopupButton withLabels = ButtonFactory.createStrokeWidthButton(editor, labels);
    assertThat(ButtonFactoryTestSupport.actions(withLabels)).hasSize(7);
    JPopupButton explicit =
        ButtonFactory.createStrokeWidthButton(editor, new double[] {1, 2}, labels);
    assertThat(ButtonFactoryTestSupport.actions(explicit)).hasSize(2);
  }

  @Test
  void strokeWidthActionAppliesToSelection() {
    RectangleFigure r = new RectangleFigure(0, 0, 10, 10);
    fx.view.getDrawing().add(r);
    fx.view.addToSelection(r);
    JPopupButton b = ButtonFactory.createStrokeWidthButton(editor);
    ButtonFactoryTestSupport.fire(ButtonFactoryTestSupport.actions(b).get(5)); // 5.0
    assertThat(STROKE_WIDTH.get(r)).isEqualTo(5d);
  }

  @Test
  void otherStrokeButtonsHaveExpectedItemCounts() {
    assertThat(ButtonFactoryTestSupport.actions(ButtonFactory.createStrokeDashesButton(editor)))
        .hasSize(7);
    assertThat(ButtonFactoryTestSupport.actions(ButtonFactory.createStrokeCapButton(editor)))
        .hasSize(3);
    assertThat(ButtonFactoryTestSupport.actions(ButtonFactory.createStrokeJoinButton(editor)))
        .hasSize(3);
    assertThat(ButtonFactory.createStrokeDecorationButton(editor)).isNotNull();
    assertThat(ButtonFactory.createStrokeTypeButton(editor)).isNotNull();
    assertThat(ButtonFactory.createStrokePlacementButton(editor)).isNotNull();
  }

  @Test
  void strokeCapAndJoinTrackDisposablesAndApply() {
    RectangleFigure r = new RectangleFigure(0, 0, 10, 10);
    fx.view.getDrawing().add(r);
    fx.view.addToSelection(r);
    List<Disposable> dsp = new ArrayList<>();
    JPopupButton cap = ButtonFactory.createStrokeCapButton(editor, labels, dsp);
    JPopupButton join = ButtonFactory.createStrokeJoinButton(editor, labels, dsp);
    assertThat(dsp).hasSize(6);
    ButtonFactoryTestSupport.fire(ButtonFactoryTestSupport.actions(cap).get(1));
    ButtonFactoryTestSupport.fire(ButtonFactoryTestSupport.actions(join).get(0));
    assertThat(STROKE_CAP.get(r)).isEqualTo(BasicStroke.CAP_ROUND);
    assertThat(STROKE_JOIN.get(r)).isEqualTo(BasicStroke.JOIN_BEVEL);
  }

  // ---------- font ----------
  @Test
  void fontButtonTracksHandlerAsDisposable() {
    List<Disposable> dsp = new ArrayList<>();
    JPopupButton b = ButtonFactory.createFontButton(editor, FONT_FACE, labels, dsp);
    assertThat(b.getPopupMenu()).isNotNull();
    assertThat(dsp).hasSize(1);
    assertThat(ButtonFactory.createFontButton(editor)).isNotNull();
    assertThat(ButtonFactory.createFontButton(editor, labels)).isNotNull();
  }

  @Test
  void fontStyleButtonsHaveOneListenerAndIgnoreDisposableList() {
    List<Disposable> dsp = new ArrayList<>();
    JButton bold = ButtonFactory.createFontStyleBoldButton(editor, labels, dsp);
    JButton italic = ButtonFactory.createFontStyleItalicButton(editor, labels, dsp);
    JButton underline = ButtonFactory.createFontStyleUnderlineButton(editor, labels, dsp);
    for (JButton b : List.of(bold, italic, underline)) {
      assertThat(b.getActionListeners()).hasSize(1);
      assertThat(b.isFocusable()).isFalse();
    }
    // Characterizes defect: the dsp parameter is accepted but never used.
    assertThat(dsp).isEmpty();
  }

  @Test
  void boldButtonTogglesFontBoldOnSelectedText() {
    TextFigure t = new TextFigure("x");
    fx.view.getDrawing().add(t);
    fx.view.addToSelection(t);
    JButton bold = ButtonFactory.createFontStyleBoldButton(editor);
    bold.doClick();
    assertThat(FONT_BOLD.get(t)).isTrue();
  }

  // ---------- pick / apply ----------
  @Test
  void pickAndApplyButtons() {
    List<Disposable> dsp = new ArrayList<>();
    JButton pick = ButtonFactory.createPickAttributesButton(editor, dsp);
    JButton apply = ButtonFactory.createApplyAttributesButton(editor, dsp);
    assertThat(dsp).hasSize(2);
    for (JButton b : List.of(pick, apply)) {
      assertThat(b.getText()).isNull();
      assertThat(b.isFocusable()).isFalse();
    }
  }

  @Test
  void pickThenApplyCopiesFillColor() {
    RectangleFigure src = new RectangleFigure(0, 0, 10, 10);
    RectangleFigure dst = new RectangleFigure(20, 0, 10, 10);
    FILL_COLOR.set(src, Color.RED);
    fx.view.getDrawing().add(src);
    fx.view.getDrawing().add(dst);
    JButton pick = ButtonFactory.createPickAttributesButton(editor);
    JButton apply = ButtonFactory.createApplyAttributesButton(editor);
    fx.view.addToSelection(src);
    pick.doClick();
    assertThat(editor.getDefaultAttribute(FILL_COLOR)).isEqualTo(Color.RED);
    fx.view.clearSelection();
    fx.view.addToSelection(dst);
    apply.doClick();
    assertThat(FILL_COLOR.get(dst)).isEqualTo(Color.RED);
  }

  // ---------- tool buttons ----------
  @Test
  void selectionToolButtonIsSelectedAndGrouped() {
    JToolBar bar = new JToolBar();
    JToggleButton b = ButtonFactory.addSelectionToolTo(bar, editor);
    assertThat(b.isSelected()).isTrue();
    assertThat(bar.getComponentCount()).isEqualTo(1);
    assertThat(editor.getTool()).isInstanceOf(DelegationSelectionTool.class);
    assertThat(bar.getClientProperty("toolButtonGroup")).isNotNull();
    assertThat(bar.getClientProperty("toolHandler")).isNotNull();
  }

  @Test
  void addToolToWiresButtonGroupAndEditorTool() {
    JToolBar bar = new JToolBar();
    JToggleButton sel = ButtonFactory.addSelectionToolTo(bar, editor);
    CreationTool tool = new CreationTool(new RectangleFigure());
    JToggleButton rect = ButtonFactory.addToolTo(bar, editor, tool, "createRectangle", labels);
    assertThat(bar.getComponentCount()).isEqualTo(2);
    rect.setSelected(true);
    assertThat(editor.getTool()).isSameAs(tool);
    assertThat(sel.isSelected()).isFalse();
  }
}
