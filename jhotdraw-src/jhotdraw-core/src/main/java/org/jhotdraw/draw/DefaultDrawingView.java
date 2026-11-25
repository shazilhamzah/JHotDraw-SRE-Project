/*
 * @(#)DefaultDrawingView.java
 *
 * Copyright (c) 1996-2010 The authors and contributors of JHotDraw.
 * You may not use, copy or modify this file, except in compliance with the
 * accompanying license terms.
 */
package org.jhotdraw.draw;

import static org.jhotdraw.draw.AttributeKeys.*;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.TexturePaint;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.Timer;
import org.jhotdraw.api.gui.EditableComponent;
import org.jhotdraw.draw.constrainer.Constrainer;
import org.jhotdraw.draw.constrainer.GridConstrainer;
import org.jhotdraw.draw.event.DrawingEvent;
import org.jhotdraw.draw.event.DrawingListener;
import org.jhotdraw.draw.event.FigureEvent;
import org.jhotdraw.draw.event.FigureListener;
import org.jhotdraw.draw.event.FigureListenerAdapter;
import org.jhotdraw.draw.event.FigureSelectionEvent;
import org.jhotdraw.draw.event.FigureSelectionListener;
import org.jhotdraw.draw.event.HandleEvent;
import org.jhotdraw.draw.event.HandleListener;
import org.jhotdraw.draw.figure.Figure;
import org.jhotdraw.draw.handle.Handle;
import org.jhotdraw.draw.io.DefaultDrawingViewTransferHandler;

/**
 * A default implementation of {@link DrawingView} suited for viewing drawings with a small number
 * of figures.
 */
public class DefaultDrawingView extends JComponent implements DrawingView, EditableComponent {

  private static final long serialVersionUID = 1L;
  private static final Color DEFAULT_BACKGROUND_COLOR = new Color(0xb0b0b0);
  private static final Color CHECKER_DARK_COLOR = new Color(0xdfdfdf);
  private static final int CHECKER_TILE_SIZE = 16;
  private static final int CHECKER_CELL_SIZE = 8;
  private static final int REPAINT_MARGIN = 2;
  private static final int SET_DRAWING_REPAINT_DELAY_MS = 10;
  private static final int RGB_COLOR_MASK = 0xffffff;
  private static final int MAX_ALPHA = 255;

  private Drawing drawing;

  private final DrawingViewHandleManager handleManager = new DrawingViewHandleManager();
  private boolean isConstrainerVisible = false;
  private Constrainer visibleConstrainer = new GridConstrainer(8, 8);
  private Constrainer invisibleConstrainer = new GridConstrainer();
  private final DrawingViewViewport viewport = new DrawingViewViewport();
  private DrawingEditor editor;
  private JLabel emptyDrawingLabel;
  protected BufferedImage backgroundTile;
  private final FigureListener handleInvalidator = new FigureListenerAdapter() {
    @Override
    public void figureHandlesChanged(FigureEvent e) {
      invalidateHandles();
    }
  };
  private final DrawingViewSelectionManager selectionManager =
      new DrawingViewSelectionManager(handleInvalidator);
  public static final String DRAWING_DOUBLE_BUFFERED_PROPERTY = "drawingDoubleBuffered";

  private final DrawingViewBuffer drawingBuffer = new DrawingViewBuffer();
  private Timer repaintTimer;
  private boolean paintEnabled = true;

  @Override
  public void repaintHandles() {
    Rectangle r = handleManager.computeAllHandlesArea(
        this, editor, selectionManager.getMutableSelection(), eventHandler, this::repaint);
    if (r != null) {
      repaint(r);
    }
  }

  /** Draws the background of the drawing view. */
  protected void drawBackground(Graphics2D g) {
    if (drawing == null) {
      g.setColor(getBackground());
      g.fillRect(0, 0, getWidth(), getHeight());
      return;
    }
    if (drawing.attr().get(CANVAS_WIDTH) == null || drawing.attr().get(CANVAS_HEIGHT) == null) {
      drawInfiniteCanvasBackground(g);
    } else {
      g.setColor(getBackground());
      g.fillRect(0, 0, getWidth(), getHeight());
      Rectangle r = drawingToView(new Rectangle2D.Double(
          0, 0, drawing.attr().get(CANVAS_WIDTH), drawing.attr().get(CANVAS_HEIGHT)));
      g.setPaint(getBackgroundPaint(r.x, r.y));
      g.fillRect(r.x, r.y, r.width, r.height);
    }
  }

  private void drawInfiniteCanvasBackground(Graphics2D g) {
    Color canvasColor = drawing.attr().get(CANVAS_FILL_COLOR);
    double canvasOpacity = drawing.attr().get(CANVAS_FILL_OPACITY);
    if (canvasColor == null) {
      Point origin = drawingToView(new Point2D.Double(0, 0));
      fillFullViewWithBackgroundTile(g, origin.x, origin.y);
      return;
    }
    if (canvasOpacity >= 1.0) {
      g.setColor(new Color(canvasColor.getRGB()));
      g.fillRect(0, 0, getWidth(), getHeight());
      return;
    }
    Point origin = drawingToView(new Point2D.Double(0, 0));
    fillFullViewWithBackgroundTile(g, origin.x, origin.y);
    g.setColor(createTranslucentCanvasColor(canvasColor, canvasOpacity));
    g.fillRect(0, 0, getWidth(), getHeight());
  }

  private void fillFullViewWithBackgroundTile(Graphics2D g, int tileOriginX, int tileOriginY) {
    g.setPaint(getBackgroundPaint(tileOriginX, tileOriginY));
    g.fillRect(0, 0, getWidth(), getHeight());
  }

  private static Color createTranslucentCanvasColor(Color canvasColor, double canvasOpacity) {
    int alpha = Math.max(0, Math.min(MAX_ALPHA, (int) Math.round(canvasOpacity * MAX_ALPHA)));
    return new Color((canvasColor.getRGB() & RGB_COLOR_MASK) | (alpha << 24), true);
  }

  @Override
  public boolean isSelectionEmpty() {
    return selectionManager.isSelectionEmpty();
  }

  protected class EventHandler implements DrawingListener, HandleListener, FocusListener {

    @Override
    public void drawingChanged(DrawingEvent e) {
      if (e.getInvalidatedArea() != null) {
        repaintDrawingArea(e.getInvalidatedArea());
        invalidateDimension();
      }
    }

    @Override
    public void figureAdded(DrawingEvent evt) {
      if (drawing.getChildCount() == 1 && getEmptyDrawingMessage() != null) {
        repaint();
      } else {
        repaintDrawingArea(evt.getFigure()
            .getDrawingArea(AttributeKeys.getScaleFactor(getDrawingToViewTransform())));
      }
      invalidateDimension();
    }

    @Override
    public void figureRemoved(DrawingEvent evt) {
      if (drawing.getChildCount() == 0 && getEmptyDrawingMessage() != null) {
        repaint();
      } else {
        repaintDrawingArea(evt.getFigure()
            .getDrawingArea(AttributeKeys.getScaleFactor(getDrawingToViewTransform())));
      }
      removeFromSelection(evt.getFigure());
      invalidateDimension();
    }

    @Override
    public void areaInvalidated(HandleEvent evt) {
      repaint(evt.getInvalidatedArea());
      invalidateDimension();
    }

    @Override
    public void handleRequestSecondaryHandles(HandleEvent e) {
      handleManager.setSecondaryHandles(e.getHandle(), DefaultDrawingView.this, eventHandler);
      repaint();
    }

    @Override
    public void focusGained(FocusEvent e) {
      if (editor != null) {
        editor.setActiveView(DefaultDrawingView.this);
      }
    }

    @Override
    public void focusLost(FocusEvent e) {}

    @Override
    public void handleRequestRemove(HandleEvent e) {
      handleManager.removeHandle(e.getHandle(), eventHandler);
      invalidateHandles();
      repaint(e.getInvalidatedArea());
    }

    @Override
    public void drawingAttributeChanged(DrawingEvent e) {
      if (e.getSource() == drawing) {
        AttributeKey<?> a = e.getAttribute();
        if (CANVAS_HEIGHT.equals(a) || CANVAS_WIDTH.equals(a)) {
          validateViewTranslation();
          repaint();
        }
        if (e.getInvalidatedArea() != null) {
          repaintDrawingArea(e.getInvalidatedArea());
        } else {
          repaintDrawingArea(viewToDrawing(getCanvasViewBounds()));
        }
      }
    }

    @Override
    public void figureChanged(DrawingEvent e) {}
  }

  private EventHandler eventHandler;

  /** Creates new instance. */
  public DefaultDrawingView() {
    initComponents();
    eventHandler = createEventHandler();
    setToolTipText("dummy");
    setFocusable(true);
    addFocusListener(eventHandler);
    setTransferHandler(new DefaultDrawingViewTransferHandler());
    setBackground(DEFAULT_BACKGROUND_COLOR);
    setOpaque(true);
  }

  protected EventHandler createEventHandler() {
    return new EventHandler();
  }

  /**
   * Initializes the component without a layout manager to prevent undesired layout effects when
   * floating text fields are displayed.
   */
  private void initComponents() {
    setLayout(null);
  }

  @Override
  public Drawing getDrawing() {
    return drawing;
  }

  @Override
  public String getToolTipText(MouseEvent evt) {
    if (getEditor() != null && getEditor().getTool() != null) {
      return getEditor().getTool().getToolTipText(this, evt);
    }
    return null;
  }

  public void setEmptyDrawingMessage(String newValue) {
    String oldValue = (emptyDrawingLabel == null) ? null : emptyDrawingLabel.getText();
    if (newValue == null) {
      emptyDrawingLabel = null;
    } else {
      emptyDrawingLabel = new JLabel(newValue);
      emptyDrawingLabel.setHorizontalAlignment(JLabel.CENTER);
    }
    firePropertyChange("emptyDrawingMessage", oldValue, newValue);
    repaint();
  }

  public String getEmptyDrawingMessage() {
    return (emptyDrawingLabel == null) ? null : emptyDrawingLabel.getText();
  }

  /**
   * Paints the drawing view. Uses rendering hints for fast painting. Paints the canvasColor, the
   * grid, the drawing, the handles and the current tool.
   */
  @Override
  public void paintComponent(Graphics gr) {
    Graphics2D g = (Graphics2D) gr;
    setViewRenderingHints(g);
    drawBackground(g);
    drawCanvas(g);
    drawConstrainer(g);
    if (isDrawingDoubleBuffered()) {
      // Dispatch through the protected hooks so that subclasses overriding them are still called.
      if (drawingBuffer.prefersNonvolatileBuffer()) {
        drawDrawingNonvolatileBuffered(g);
      } else {
        drawDrawingVolatileBuffered(g);
      }
    } else {
      drawDrawing(g);
    }
    drawHandles(g);
    drawTool(g);
  }

  /** Draws the drawing double buffered using a volatile image. */
  protected void drawDrawingVolatileBuffered(Graphics2D g) {
    drawingBuffer.drawVolatile(
        g,
        getVisibleRect(),
        getGraphicsConfiguration(),
        this::setViewRenderingHints,
        this::drawDrawing);
  }

  /** Draws the drawing double buffered using a buffered image. */
  protected void drawDrawingNonvolatileBuffered(Graphics2D g) {
    drawingBuffer.drawNonvolatile(
        g,
        getVisibleRect(),
        getGraphicsConfiguration(),
        this::setViewRenderingHints,
        this::drawDrawing);
  }

  /**
   * Prints the drawing view. Uses high quality rendering hints for printing. Only prints the
   * drawing. Doesn't print the canvasColor, the grid, the handles and the tool.
   */
  @Override
  public void printComponent(Graphics gr) {
    Graphics2D g = (Graphics2D) gr;
    applyRenderingHints(g, true);
    drawDrawing(g);
  }

  protected void setViewRenderingHints(Graphics2D g) {
    applyRenderingHints(g, false);
  }

  private static void applyRenderingHints(Graphics2D g, boolean highQuality) {
    g.setRenderingHint(
        RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
    g.setRenderingHint(
        RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    g.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION,
        highQuality
            ? RenderingHints.VALUE_INTERPOLATION_BICUBIC
            : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
    g.setRenderingHint(
        RenderingHints.KEY_RENDERING,
        highQuality ? RenderingHints.VALUE_RENDER_QUALITY : RenderingHints.VALUE_RENDER_SPEED);
    g.setRenderingHint(
        RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
  }

  /**
   * Returns the bounds of the canvas on the drawing view.
   *
   * @return The current bounds of the canvas on the drawing view.
   */
  protected Rectangle getCanvasViewBounds() {
    return viewport.getCanvasViewBounds(getDrawing(), getWidth(), getHeight());
  }

  /**
   * Draws the canvas. If the {@code AttributeKeys.CANVAS_FILL_OPACITY} is not fully opaque, the
   * canvas area is filled with the background paint before the {@code
   * AttributeKeys.CANVAS_FILL_COLOR} is drawn.
   */
  protected void drawCanvas(Graphics2D gr) {
    if (drawing != null) {
      drawWithViewTransform(gr, g -> drawing.drawCanvas(g));
    }
  }

  protected void drawConstrainer(Graphics2D g) {
    if (getConstrainer() != null) {
      Shape clip = g.getClip();
      Rectangle r = getCanvasViewBounds();
      g.clipRect(r.x, r.y, r.width, r.height);
      getConstrainer().draw(g, this);
      g.setClip(clip);
    }
  }

  protected void drawDrawing(Graphics2D gr) {
    if (drawing != null) {
      if (drawing.getChildCount() == 0 && emptyDrawingLabel != null) {
        emptyDrawingLabel.setBounds(0, 0, getWidth(), getHeight());
        emptyDrawingLabel.paint(gr);
      } else {
        drawWithViewTransform(gr, g -> drawing.draw(g));
      }
    }
  }

  private void drawWithViewTransform(Graphics2D gr, Consumer<Graphics2D> drawer) {
    Graphics2D g = (Graphics2D) gr.create();
    AffineTransform tx = g.getTransform();
    viewport.applyToTransform(tx);
    g.setTransform(tx);
    drawing.setFontRenderContext(g.getFontRenderContext());
    drawer.accept(g);
    g.dispose();
  }

  protected void drawHandles(Graphics2D g) {
    if (editor != null && editor.getActiveView() == this) {
      validateHandles();
      for (Handle h : getSelectionHandles()) {
        h.draw(g);
      }
      for (Handle h : getSecondaryHandles()) {
        h.draw(g);
      }
    }
  }

  protected void drawTool(Graphics2D g) {
    if (editor != null && editor.getActiveView() == this && editor.getTool() != null) {
      editor.getTool().draw(g);
    }
  }

  @Override
  public void setDrawing(Drawing newValue) {
    Drawing oldValue = drawing;
    if (this.drawing != null) {
      this.drawing.removeDrawingListener(eventHandler);
      clearSelection();
    }
    this.drawing = newValue;
    if (this.drawing != null) {
      this.drawing.addDrawingListener(eventHandler);
    }
    drawingBuffer.markBufferedAreaDirty();
    firePropertyChange(DRAWING_PROPERTY, oldValue, newValue);
    // Revalidate without flickering
    invalidateDimension();
    revalidate();
    validateViewTranslation();
    if (repaintTimer != null) {
      repaintTimer.stop();
    }
    paintEnabled = false;
    repaintTimer = new Timer(SET_DRAWING_REPAINT_DELAY_MS, e -> {
      paintEnabled = true;
      repaint();
    });
    repaintTimer.setRepeats(false);
    repaintTimer.start();
  }

  @Override
  public void paint(Graphics g) {
    if (paintEnabled) {
      super.paint(g);
    }
  }

  protected void repaintDrawingArea(Rectangle2D.Double r) {
    Rectangle vr = drawingToView(r);
    vr.grow(REPAINT_MARGIN, REPAINT_MARGIN);
    drawingBuffer.markDirty(vr);
    repaint(vr);
  }

  @Override
  public void invalidate() {
    invalidateDimension();
    super.invalidate();
  }

  @Override
  public void removeNotify() {
    super.removeNotify();
    drawingBuffer.flush();
  }

  /** Adds a figure to the current selection. */
  @Override
  public void addToSelection(Figure figure) {
    addToSelection(Collections.singletonList(figure));
  }

  /** Adds a collection of figures to the current selection. */
  @Override
  public void addToSelection(Collection<Figure> figures) {
    DrawingViewSelectionManager.SelectionDelta delta =
        selectionManager.addAll(figures, this, handleManager, eventHandler);
    if (delta.changed()) {
      fireSelectionChanged(delta.oldSelection(), delta.newSelection());
      if (delta.invalidatedArea() != null) {
        repaint(delta.invalidatedArea());
      }
    }
  }

  /** Removes a figure from the selection. */
  @Override
  public void removeFromSelection(Figure figure) {
    DrawingViewSelectionManager.SelectionDelta delta = selectionManager.remove(figure);
    if (delta.changed()) {
      invalidateHandles();
      fireSelectionChanged(delta.oldSelection(), delta.newSelection());
      repaint();
    }
  }

  /**
   * If a figure isn't selected it is added to the selection. Otherwise it is removed from the
   * selection.
   */
  @Override
  public void toggleSelection(Figure figure) {
    if (selectionManager.isFigureSelected(figure)) {
      removeFromSelection(figure);
    } else {
      addToSelection(figure);
    }
  }

  @Override
  public void setEnabled(boolean b) {
    super.setEnabled(b);
    setCursor(Cursor.getPredefinedCursor(b ? Cursor.DEFAULT_CURSOR : Cursor.WAIT_CURSOR));
  }

  /** Selects all selectable figures. */
  @Override
  public void selectAll() {
    DrawingViewSelectionManager.SelectionDelta delta = selectionManager.selectAll(drawing);
    invalidateHandles();
    fireSelectionChanged(delta.oldSelection(), delta.newSelection());
    repaint();
  }

  /** Clears the current selection. */
  @Override
  public void clearSelection() {
    DrawingViewSelectionManager.SelectionDelta delta = selectionManager.clear();
    if (delta.changed()) {
      invalidateHandles();
      fireSelectionChanged(delta.oldSelection(), delta.newSelection());
    }
  }

  /** Test whether a given figure is selected. */
  @Override
  public boolean isFigureSelected(Figure checkFigure) {
    return selectionManager.isFigureSelected(checkFigure);
  }

  /**
   * Gets the current selection as a FigureSelection. A FigureSelection can be cut, copied, pasted.
   */
  @Override
  public Set<Figure> getSelectedFigures() {
    return selectionManager.getSelectedFigures();
  }

  /** Gets the number of selected figures. */
  @Override
  public int getSelectionCount() {
    return selectionManager.getSelectionCount();
  }

  /** Gets the currently active selection handles. */
  private java.util.List<Handle> getSelectionHandles() {
    return handleManager.getSelectionHandles(
        this, editor, selectionManager.getMutableSelection(), eventHandler, this::repaint);
  }

  /** Gets the currently active secondary handles. */
  private java.util.List<Handle> getSecondaryHandles() {
    return handleManager.getSecondaryHandles(
        this, editor, selectionManager.getMutableSelection(), eventHandler, this::repaint);
  }

  /** Invalidates the handles. */
  private void invalidateHandles() {
    Rectangle invalidatedArea = handleManager.invalidate(eventHandler, () -> setActiveHandle(null));
    if (invalidatedArea != null) {
      repaint(invalidatedArea);
    }
  }

  /** Validates the handles. */
  private void validateHandles() {
    handleManager.validate(
        this, editor, selectionManager.getMutableSelection(), eventHandler, this::repaint);
  }

  /**
   * Finds a handle at a given coordinates.
   *
   * @return A handle, null if no handle is found.
   */
  @Override
  public Handle findHandle(Point p) {
    return handleManager.findHandle(
        p, this, editor, selectionManager.getMutableSelection(), eventHandler, this::repaint);
  }

  /**
   * Gets compatible handles.
   *
   * @return A collection containing the handle and all compatible handles.
   */
  @Override
  public Collection<Handle> getCompatibleHandles(Handle master) {
    return handleManager.getCompatibleHandles(
        master, this, editor, selectionManager.getMutableSelection(), eventHandler, this::repaint);
  }

  /**
   * Finds a figure at a given coordinates.
   *
   * @return A figure, null if no figure is found.
   */
  @Override
  public Figure findFigure(Point p) {
    Drawing d = getDrawing();
    return d == null ? null : d.findFigure(viewToDrawing(p), getScaleFactor());
  }

  @Override
  public Collection<Figure> findFigures(Rectangle r) {
    Drawing d = getDrawing();
    return d == null ? Collections.emptyList() : d.findFigures(viewToDrawing(r));
  }

  @Override
  public Collection<Figure> findFiguresWithin(Rectangle r) {
    Drawing d = getDrawing();
    return d == null ? Collections.emptyList() : d.findFiguresWithin(viewToDrawing(r));
  }

  @Override
  public void addFigureSelectionListener(FigureSelectionListener fsl) {
    listenerList.add(FigureSelectionListener.class, fsl);
  }

  @Override
  public void removeFigureSelectionListener(FigureSelectionListener fsl) {
    listenerList.remove(FigureSelectionListener.class, fsl);
  }

  /**
   * Notify all listenerList that have registered interest for notification on this event type. Also
   * notify listeners who listen for {@link EditableComponent#SELECTION_EMPTY_PROPERTY}.
   */
  protected void fireSelectionChanged(Set<Figure> oldValue, Set<Figure> newValue) {
    if (listenerList.getListenerCount() > 0) {
      FigureSelectionEvent event = null;
      // Notify all listeners that have registered interest for
      // Guaranteed to return a non-null array
      Object[] listeners = listenerList.getListenerList();
      // Process the listeners last to first, notifying
      // those that are interested in this event
      for (int i = listeners.length - 2; i >= 0; i -= 2) {
        if (listeners[i] == FigureSelectionListener.class) {
          // Lazily create the event:
          if (event == null) {
            event = new FigureSelectionEvent(this, oldValue, newValue);
          }
          ((FigureSelectionListener) listeners[i + 1]).selectionChanged(event);
        }
      }
    }
    firePropertyChange(SELECTION_EMPTY_PROPERTY, oldValue.isEmpty(), newValue.isEmpty());
  }

  protected void invalidateDimension() {
    viewport.invalidateDimension();
  }

  @Override
  public Constrainer getConstrainer() {
    return isConstrainerVisible() ? visibleConstrainer : invisibleConstrainer;
  }

  @Override
  public Dimension getPreferredSize() {
    return viewport.getPreferredSize(getDrawing(), getInsets());
  }

  protected Rectangle2D.Double getDrawingArea() {
    return viewport.getDrawingArea(getDrawing());
  }

  /** Side effect: Changes view Translation. */
  @Override
  public void setBounds(int x, int y, int width, int height) {
    super.setBounds(x, y, width, height);
    validateViewTranslation();
  }

  /**
   * Updates the view translation taking into account the current dimension of the view JComponent,
   * the size of the drawing, and the scale factor.
   */
  private void validateViewTranslation() {
    Point delta = viewport.updateTranslation(getDrawing(), getWidth(), getHeight(), getInsets());
    if (delta != null) {
      drawingBuffer.translateBufferedArea(delta.x, delta.y);
      fireViewTransformChanged();
    }
  }

  /** Converts drawing coordinates to view coordinates. */
  @Override
  public Point drawingToView(Point2D.Double p) {
    return viewport.drawingToView(p);
  }

  @Override
  public Rectangle drawingToView(Rectangle2D.Double r) {
    return viewport.drawingToView(r);
  }

  /** Converts view coordinates to drawing coordinates. */
  @Override
  public Point2D.Double viewToDrawing(Point p) {
    return viewport.viewToDrawing(p);
  }

  @Override
  public Rectangle2D.Double viewToDrawing(Rectangle r) {
    return viewport.viewToDrawing(r);
  }

  @Override
  public JComponent getComponent() {
    return this;
  }

  @Override
  public double getScaleFactor() {
    return viewport.getScaleFactor();
  }

  @Override
  public void setScaleFactor(double newValue) {
    double oldValue = viewport.setScaleFactor(newValue);
    invalidateDimension();
    validateViewTranslation();
    drawingBuffer.resetDirtyToBufferedArea();
    invalidateHandles();
    revalidate();
    repaint();
    firePropertyChange(SCALE_FACTOR_PROPERTY, oldValue, newValue);
  }

  protected void fireViewTransformChanged() {
    handleManager.fireViewTransformChanged();
  }

  @Override
  public void setHandleDetailLevel(int newValue) {
    int oldValue = handleManager.getDetailLevel();
    if (newValue != oldValue) {
      handleManager.setDetailLevel(newValue);
      firePropertyChange(HANDLE_DETAIL_LEVEL_PROPERTY, oldValue, newValue);
      invalidateHandles();
      validateHandles();
      if (handleManager.getDetailLevel() != newValue) {
        firePropertyChange(HANDLE_DETAIL_LEVEL_PROPERTY, newValue, handleManager.getDetailLevel());
      }
    }
  }

  @Override
  public int getHandleDetailLevel() {
    return handleManager.getDetailLevel();
  }

  @Override
  public AffineTransform getDrawingToViewTransform() {
    return viewport.getDrawingToViewTransform();
  }

  @Override
  public void delete() {
    DrawingViewEditCommands.delete(this, () -> getToolkit().beep());
  }

  @Override
  public void duplicate() {
    DrawingViewEditCommands.duplicate(this);
  }

  @Override
  public void removeNotify(DrawingEditor editor) {
    DrawingEditor oldValue = this.editor;
    this.editor = null;
    firePropertyChange("editor", oldValue, null);
    invalidateHandles();
    repaint();
  }

  @Override
  public void addNotify(DrawingEditor editor) {
    DrawingEditor oldValue = this.editor;
    this.editor = editor;
    firePropertyChange("editor", oldValue, editor);
    invalidateHandles();
    repaint();
  }

  @Override
  public void setVisibleConstrainer(Constrainer newValue) {
    Constrainer oldValue = visibleConstrainer;
    visibleConstrainer = newValue;
    firePropertyChange(VISIBLE_CONSTRAINER_PROPERTY, oldValue, newValue);
  }

  @Override
  public Constrainer getVisibleConstrainer() {
    return visibleConstrainer;
  }

  @Override
  public void setInvisibleConstrainer(Constrainer newValue) {
    Constrainer oldValue = invisibleConstrainer;
    invisibleConstrainer = newValue;
    firePropertyChange(INVISIBLE_CONSTRAINER_PROPERTY, oldValue, newValue);
  }

  @Override
  public Constrainer getInvisibleConstrainer() {
    return invisibleConstrainer;
  }

  @Override
  public void setConstrainerVisible(boolean newValue) {
    boolean oldValue = isConstrainerVisible;
    isConstrainerVisible = newValue;
    firePropertyChange(CONSTRAINER_VISIBLE_PROPERTY, oldValue, newValue);
    repaint();
  }

  @Override
  public boolean isConstrainerVisible() {
    return isConstrainerVisible;
  }

  /**
   * Sets whether the drawing is double buffered.
   *
   * <p>
   *
   * <p>The default value is true.
   *
   * <p>
   *
   * <p>This is a bound property.
   *
   * <p>
   *
   * <p>If the drawing view is used for editing, you should leave this to true. If the drawing view
   * is used for viewing only, you should set this to false.
   */
  public void setDrawingDoubleBuffered(boolean newValue) {
    boolean oldValue = drawingBuffer.isDoubleBuffered();
    drawingBuffer.setDoubleBuffered(newValue);
    firePropertyChange(DRAWING_DOUBLE_BUFFERED_PROPERTY, oldValue, newValue);
  }

  /** Returns true, if the drawing is double buffered. */
  public boolean isDrawingDoubleBuffered() {
    return drawingBuffer.isDoubleBuffered();
  }

  /**
   * Returns a paint for drawing the background of the drawing area.
   *
   * @return Paint.
   */
  protected Paint getBackgroundPaint(int x, int y) {
    if (backgroundTile == null) {
      backgroundTile =
          new BufferedImage(CHECKER_TILE_SIZE, CHECKER_TILE_SIZE, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = backgroundTile.createGraphics();
      g.setColor(Color.white);
      g.fillRect(0, 0, CHECKER_TILE_SIZE, CHECKER_TILE_SIZE);
      g.setColor(CHECKER_DARK_COLOR);
      g.fillRect(0, 0, CHECKER_CELL_SIZE, CHECKER_CELL_SIZE);
      g.fillRect(CHECKER_CELL_SIZE, CHECKER_CELL_SIZE, CHECKER_CELL_SIZE, CHECKER_CELL_SIZE);
      g.dispose();
    }
    return new TexturePaint(
        backgroundTile, new Rectangle(x, y, backgroundTile.getWidth(), backgroundTile.getHeight()));
  }

  @Override
  public DrawingEditor getEditor() {
    return editor;
  }

  @Override
  public void setActiveHandle(Handle newValue) {
    Handle oldValue = handleManager.replaceActiveHandle(newValue);
    if (oldValue != null) {
      repaint(oldValue.getDrawingArea());
    }
    if (newValue != null) {
      repaint(newValue.getDrawingArea());
    }
    firePropertyChange(ACTIVE_HANDLE_PROPERTY, oldValue, newValue);
  }

  @Override
  public Handle getActiveHandle() {
    return handleManager.getActiveHandle();
  }
}
