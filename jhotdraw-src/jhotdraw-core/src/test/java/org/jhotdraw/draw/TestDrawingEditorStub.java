package org.jhotdraw.draw;

import java.awt.Color;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.event.EventListenerList;
import org.jhotdraw.draw.event.ToolListener;
import org.jhotdraw.draw.figure.Figure;
import org.jhotdraw.draw.figure.RectangleFigure;
import org.jhotdraw.draw.handle.AbstractHandle;
import org.jhotdraw.draw.handle.Handle;
import org.jhotdraw.draw.tool.Tool;

/** Lightweight DrawingEditor stub and test helpers for DefaultDrawingView tests. */
class TestDrawingEditorStub implements DrawingEditor {

  private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
  private final List<DrawingView> views = new ArrayList<>();
  private final Map<AttributeKey<?>, Object> defaultAttributes = new HashMap<>();
  private final Map<AttributeKey<?>, Object> handleAttributes = new HashMap<>();
  private DrawingView activeView;
  private Tool tool;
  private InputMap inputMap = new InputMap();
  private ActionMap actionMap = new ActionMap();
  private boolean enabled = true;
  private int setActiveViewCalls;
  private DrawingView lastActiveView;

  @Override
  public void add(DrawingView view) {
    views.add(view);
    view.addNotify(this);
    if (activeView == null) {
      setActiveView(view);
    }
  }

  @Override
  public void remove(DrawingView view) {
    views.remove(view);
    view.removeNotify(this);
    if (activeView == view) {
      setActiveView(views.isEmpty() ? null : views.get(0));
    }
  }

  @Override
  public Collection<DrawingView> getDrawingViews() {
    return Collections.unmodifiableList(views);
  }

  @Override
  public DrawingView getActiveView() {
    return activeView;
  }

  @Override
  public void setActiveView(DrawingView newValue) {
    DrawingView oldValue = this.activeView;
    this.activeView = newValue;
    this.lastActiveView = newValue;
    this.setActiveViewCalls++;
    pcs.firePropertyChange(ACTIVE_VIEW_PROPERTY, oldValue, newValue);
  }

  public int getSetActiveViewCalls() {
    return setActiveViewCalls;
  }

  public DrawingView getLastActiveView() {
    return lastActiveView;
  }

  @Override
  public void setTool(Tool t) {
    Tool oldValue = this.tool;
    if (oldValue != null) {
      oldValue.deactivate(this);
    }
    this.tool = t;
    if (t != null) {
      t.activate(this);
    }
    pcs.firePropertyChange(TOOL_PROPERTY, oldValue, t);
  }

  @Override
  public Tool getTool() {
    return tool;
  }

  @Override
  public void setCursor(Cursor c) {
    for (DrawingView v : views) {
      v.setCursor(c);
    }
  }

  @Override
  public DrawingView findView(Container c) {
    return activeView;
  }

  @Override
  public <T> void setDefaultAttribute(AttributeKey<T> key, T value) {
    Object oldValue = defaultAttributes.put(key, value);
    pcs.firePropertyChange(DEFAULT_ATTRIBUTE_PROPERTY_PREFIX + key.getKey(), oldValue, value);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T getDefaultAttribute(AttributeKey<T> key) {
    if (defaultAttributes.containsKey(key)) {
      return (T) defaultAttributes.get(key);
    }
    return key.getDefaultValue();
  }

  @Override
  public void applyDefaultAttributesTo(Figure f) {
    for (Map.Entry<AttributeKey<?>, Object> entry : defaultAttributes.entrySet()) {
      f.attr().set((AttributeKey<Object>) entry.getKey(), entry.getValue());
    }
  }

  @Override
  public Map<AttributeKey<?>, Object> getDefaultAttributes() {
    return Collections.unmodifiableMap(defaultAttributes);
  }

  @Override
  public void resetDefaultAttributes(Map<AttributeKey<?>, Object> attributes) {
    defaultAttributes.clear();
    defaultAttributes.putAll(attributes);
  }

  @Override
  @SuppressWarnings("rawtypes")
  public void removeDefaultAttribute(AttributeKey key) {
    defaultAttributes.remove(key);
  }

  @Override
  public void removeAllDefaultAttributes() {
    defaultAttributes.clear();
  }

  @Override
  public <T> void setHandleAttribute(AttributeKey<T> key, T value) {
    handleAttributes.put(key, value);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T getHandleAttribute(AttributeKey<T> key) {
    if (handleAttributes.containsKey(key)) {
      return (T) handleAttributes.get(key);
    }
    return key.getDefaultValue();
  }

  @Override
  public void setInputMap(InputMap newValue) {
    InputMap oldValue = this.inputMap;
    this.inputMap = newValue;
    pcs.firePropertyChange(INPUT_MAP_PROPERTY, oldValue, newValue);
  }

  @Override
  public InputMap getInputMap() {
    return inputMap;
  }

  @Override
  public void setActionMap(ActionMap newValue) {
    ActionMap oldValue = this.actionMap;
    this.actionMap = newValue;
    pcs.firePropertyChange(ACTION_MAP_PROPERTY, oldValue, newValue);
  }

  @Override
  public ActionMap getActionMap() {
    return actionMap;
  }

  @Override
  public void setEnabled(boolean newValue) {
    boolean oldValue = this.enabled;
    this.enabled = newValue;
    pcs.firePropertyChange("enabled", oldValue, newValue);
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  @Override
  public void addPropertyChangeListener(PropertyChangeListener listener) {
    pcs.addPropertyChangeListener(listener);
  }

  @Override
  public void removePropertyChangeListener(PropertyChangeListener listener) {
    pcs.removePropertyChangeListener(listener);
  }

  /** Tool stub recording draw invocations and providing configurable tooltip text. */
  static class StubTool implements Tool {

    private final EventListenerList listeners = new EventListenerList();
    private String toolTipText = "StubToolTip";
    private int drawCalls;
    private int activateCalls;
    private int deactivateCalls;

    @Override
    public void activate(DrawingEditor editor) {
      activateCalls++;
    }

    @Override
    public void deactivate(DrawingEditor editor) {
      deactivateCalls++;
    }

    @Override
    public void addToolListener(ToolListener l) {
      listeners.add(ToolListener.class, l);
    }

    @Override
    public void removeToolListener(ToolListener l) {
      listeners.remove(ToolListener.class, l);
    }

    @Override
    public void draw(Graphics2D g) {
      drawCalls++;
      g.setColor(Color.MAGENTA);
      g.fillRect(0, 0, 4, 4);
    }

    @Override
    public void editDelete() {}

    @Override
    public void editCut() {}

    @Override
    public void editCopy() {}

    @Override
    public void editDuplicate() {}

    @Override
    public void editPaste() {}

    @Override
    public String getToolTipText(DrawingView view, MouseEvent evt) {
      return toolTipText;
    }

    public void setToolTipText(String toolTipText) {
      this.toolTipText = toolTipText;
    }

    @Override
    public boolean supportsHandleInteraction() {
      return true;
    }

    public int getDrawCalls() {
      return drawCalls;
    }

    public int getActivateCalls() {
      return activateCalls;
    }

    public int getDeactivateCalls() {
      return deactivateCalls;
    }

    @Override
    public void mouseClicked(MouseEvent e) {}

    @Override
    public void mousePressed(MouseEvent e) {}

    @Override
    public void mouseReleased(MouseEvent e) {}

    @Override
    public void mouseEntered(MouseEvent e) {}

    @Override
    public void mouseExited(MouseEvent e) {}

    @Override
    public void mouseDragged(MouseEvent e) {}

    @Override
    public void mouseMoved(MouseEvent e) {}

    @Override
    public void keyTyped(KeyEvent e) {}

    @Override
    public void keyPressed(KeyEvent e) {}

    @Override
    public void keyReleased(KeyEvent e) {}

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {}
  }

  /** Handle stub with controllable bounds, combinability, secondary handles, and lifecycle counters. */
  static class StubHandle extends AbstractHandle {

    private final Rectangle fixedBounds;
    private final boolean combinable;
    private final List<Handle> secondaryHandles = new ArrayList<>();
    private int disposeCalls;
    private int viewTransformChangedCalls;
    private int drawCalls;

    StubHandle(Figure owner, Rectangle fixedBounds) {
      this(owner, fixedBounds, true);
    }

    StubHandle(Figure owner, Rectangle fixedBounds, boolean combinable) {
      super(owner);
      this.fixedBounds = new Rectangle(fixedBounds);
      this.combinable = combinable;
    }

    public void addSecondaryHandle(Handle h) {
      secondaryHandles.add(h);
    }

    @Override
    public Collection<Handle> createSecondaryHandles() {
      return new ArrayList<>(secondaryHandles);
    }

    @Override
    protected Rectangle basicGetBounds() {
      return new Rectangle(fixedBounds);
    }

    @Override
    public boolean isCombinableWith(Handle handle) {
      return combinable && super.isCombinableWith(handle);
    }

    @Override
    public void draw(Graphics2D g) {
      drawCalls++;
      g.setColor(Color.GREEN);
      g.fillRect(fixedBounds.x, fixedBounds.y, fixedBounds.width, fixedBounds.height);
    }

    @Override
    public void dispose() {
      disposeCalls++;
      super.dispose();
    }

    @Override
    public void viewTransformChanged() {
      viewTransformChangedCalls++;
      super.viewTransformChanged();
    }

    public void triggerSecondaryHandles() {
      fireHandleRequestSecondaryHandles();
    }

    public void triggerRequestRemove(Rectangle area) {
      fireHandleRequestRemove(area);
    }

    public void triggerAreaInvalidated(Rectangle area) {
      fireAreaInvalidated(area);
    }

    public int getDisposeCalls() {
      return disposeCalls;
    }

    public int getViewTransformChangedCalls() {
      return viewTransformChangedCalls;
    }

    public int getDrawCalls() {
      return drawCalls;
    }

    public int getHandleListenerCount() {
      return listenerList.getListenerCount(org.jhotdraw.draw.event.HandleListener.class);
    }
  }

  /** RectangleFigure allowing custom selectable/removable flags, handle generation, and remap tracking. */
  static class StubFigure extends RectangleFigure {

    private static final long serialVersionUID = 1L;
    private boolean customSelectable = true;
    private boolean customRemovable = true;
    private boolean remapCalled;
    private final Map<Integer, List<Handle>> customHandlesByDetail = new HashMap<>();

    StubFigure(double x, double y, double w, double h) {
      super(x, y, w, h);
    }

    public void setCustomSelectable(boolean selectable) {
      this.customSelectable = selectable;
    }

    public void setCustomRemovable(boolean removable) {
      this.customRemovable = removable;
    }

    public void setHandlesForDetailLevel(int detailLevel, List<Handle> handles) {
      customHandlesByDetail.put(detailLevel, handles);
    }

    @Override
    public boolean isSelectable() {
      return customSelectable;
    }

    @Override
    public boolean isRemovable() {
      return customRemovable;
    }

    @Override
    public Collection<Handle> createHandles(int detailLevel) {
      if (customHandlesByDetail.containsKey(detailLevel)) {
        return new ArrayList<>(customHandlesByDetail.get(detailLevel));
      }
      if (!customHandlesByDetail.isEmpty()) {
        return Collections.emptyList();
      }
      return super.createHandles(detailLevel);
    }

    @Override
    public void remap(Map<Figure, Figure> oldToNew, boolean disconnectIfNotInMap) {
      remapCalled = true;
      super.remap(oldToNew, disconnectIfNotInMap);
    }

    public boolean isRemapCalled() {
      return remapCalled;
    }

    public void triggerHandlesChanged() {
      fireFigureHandlesChanged();
    }

    public int getFigureListenerCount() {
      return listenerList.getListenerCount(org.jhotdraw.draw.event.FigureListener.class);
    }

    @Override
    public StubFigure clone() {
      StubFigure clone = (StubFigure) super.clone();
      clone.remapCalled = false;
      return clone;
    }
  }
}
