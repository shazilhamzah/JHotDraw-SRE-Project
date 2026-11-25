# Code Smells and Defects Catalog — `DefaultDrawingView`

Target file: `jhotdraw-src/jhotdraw-core/src/main/java/org/jhotdraw/draw/DefaultDrawingView.java` (original baseline: 1,550 lines).

---

## Part 1: Bad Code Smells Identified

### 1. Large Class / God Class (Divergent Change)
- **Location:** `DefaultDrawingView.java:75-1550` (1,476 class LOC, 81 methods, 30 fields, `WMC = 237`, `CBO = 52`, `LCOM* = 0.9611`).
- **Evidence:** A single Swing `JComponent` subclass mixed at least six unrelated responsibilities:
  1. Offscreen volatile/nonvolatile double buffering (`L115-145`, `L401-618`, `L793-803`, `L1487-1504`).
  2. Viewport coordinate transforms, scale factor, translation clamping, and dimension caching (`L98-111`, `L662-678`, `L1123-1327`).
  3. Selection & secondary handle lifecycle (`L90-97`, `L148-168`, `L947-1065`, `L1298-1319`, `L1534-1549`).
  4. Figure selection state & listener management (`L85-89`, `L806-944`, `L1088-1121`).
  5. Undoable editing commands (`delete` `L1330-1375`, `duplicate` `L1378-1417`).
  6. Swing component painting, constrainer management, and `EventHandler` (`L171-398`, `L625-777`, `L1420-1529`).
- **Resolution:** Extracted five dedicated package-private collaborators (`DrawingViewBuffer`, `DrawingViewViewport`, `DrawingViewHandleManager`, `DrawingViewSelectionManager`, `DrawingViewEditCommands`).

### 2. Severe Code Duplication
- **Volatile vs. Nonvolatile Double Buffering (`L401-515` vs. `L518-618`):** Nearly 100 lines of identical visible-rect containment checks, directional `shift` calculations, dirty-rectangle expansion, `AlphaComposite.Src` copyArea shifting, clipping, clearing, and rendering hints setup were copy-pasted across `drawDrawingVolatileBuffered` and `drawDrawingNonvolatileBuffered`.
- **`addToSelection(Figure)` vs. `addToSelection(Collection<Figure>)` (`L807-830` vs. `L834-864`):** Duplicate handle creation, listener registration, and invalidated-area union logic.
- **`drawCanvas(Graphics2D)` vs. `drawDrawing(Graphics2D)` (`L685-696` vs. `L714-722`):** Duplicated `Graphics2D` creation, `AffineTransform` translation/scaling, `FontRenderContext` propagation, and `dispose()`.
- **`printComponent(Graphics)` vs. `setViewRenderingHints(Graphics2D)` (`L625-640` vs. `L642-655`):** Duplicated 7 rendering hint calls differing only in interpolation and rendering speed/quality hints.
- **Handle disposal loops (`L963-980`):** Two nearly identical loops over `selectionHandles` and `secondaryHandles` inside `invalidateHandles()`.
- **Resolution:** Consolidated into `DrawingViewBuffer` (`prepareBufferedArea`, `renderDirtyRegion`), unified `addToSelection(Figure)` to delegate to `addToSelection(Collection<Figure>)`, extracted `drawWithViewTransform`, `applyRenderingHints`, and `DrawingViewHandleManager.disposeHandles`.

### 3. Long Methods & High Cyclomatic Complexity
- `drawDrawingVolatileBuffered` (`L401-515`, 115 LOC, `CC = 22`)
- `drawDrawingNonvolatileBuffered` (`L518-618`, 101 LOC, `CC = 18`)
- `validateViewTranslation` (`L1187-1242`, 56 LOC, `CC = 11`)
- `delete` (`L1330-1375`, 47 LOC, `CC = 6`) and `duplicate` (`L1378-1417`, 41 LOC, `CC = 3`)
- `drawBackground` (`L171-207`, 37 LOC, `CC = 6`)
- `validateHandles` (`L991-1023`, 33 LOC, `CC = 10`) using an awkward `while (true) { ... break; }` loop.

### 4. Dead Code, Commented-Out Blocks, and Naming/Style Smells
- **Dead field:** `private Handle secondaryHandleOwner;` (`L94`) was only assigned in `EventHandler.handleRequestSecondaryHandles` (`L255`) and never read anywhere else.
- **Commented-out code & obsolete boilerplate:** NetBeans form generator guards (`L329-345`, `L1531-1532`), obsolete `FIXME` comment (`L72-73`: `"Use double buffering for the drawing to improve performance"`, already implemented), commented-out `repaintHandles()` calls in `focusGained`/`focusLost` (`L267, L275`), and commented-out `else` body in `drawingAttributeChanged` (`L300-304`, triggering PMD `EmptyControlStatement`).
- **Naming convention violation:** `SELECTED_FIGURES` and `UNMODIFIABLE_SELECTED_FIGURES` (`L85-88`) were `private final` instance fields styled in `ALL_CAPS` as if they were static constants.
- **Catching `Throwable` & Empty Catch Block:** `static` initializer (`L141-143`) caught `Throwable` with an empty block.
- **Magic numbers & raw property strings:** `16`, `8`, `0xdfdfdf`, `0xb0b0b0`, `2`, `10`, `5`, `"scaleFactor"` instead of `DrawingView.SCALE_FACTOR_PROPERTY`.

---

## Part 2: Concrete Defects Found and Fixed

### Defect #1: Bitmask Typo Drops Top 4 Bits of Red Channel in Translucent Canvas Color
- **Location:** `DefaultDrawingView.java` → `drawBackground(Graphics2D g)` (`L189-190`)
- **Original Code:**
  ```java
  g.setColor(new Color(
      canvasColor.getRGB() & 0xfffff | ((int) (canvasOpacity * 256) << 24), true));
  ```
- **Root Cause & Impact:** `0xfffff` has only 5 hex `f`s (20 bits) instead of 6 (`0xffffff`, 24 bits). This clears bits 20–23 (the upper nibble of the red channel!), turning pure red `0xFFFF0000` into `0x0F0000` (almost black). Additionally, `(int) (canvasOpacity * 256)` overflows the 8-bit alpha channel (`256` wraps to `0`!) when `canvasOpacity` approaches `1.0` (e.g., `0.999`).
- **Fix Applied:** Replaced with `createTranslucentCanvasColor`: masks with `RGB_COLOR_MASK = 0xffffff` and computes `alpha = Math.max(0, Math.min(255, (int) Math.round(canvasOpacity * 255)))`.
- **Verification:** `DefaultDrawingViewDefectFixesTest.defect01TranslucentRedBackgroundPreservesFullRedChannel`.

### Defect #2: Stale Fixed-Canvas Centering Offset After Zooming In (plus Swapped Infinite-Canvas Insets)
- **Location:** `DefaultDrawingView.java` → `validateViewTranslation()` (`L1204-1221`)
- **Original Code:**
  ```java
  if (cwd == null || chd == null) {
    translation.x = insets.top;
    translation.y = insets.left;
  } else {
    ...
    if (cw < width) {
      translation.x = insets.left + (width - insets.left - insets.right - cw) / -2;
    }
    if (ch < height) {
      translation.y = insets.top + (height - insets.top - insets.bottom - ch) / -2;
    }
  }
  ```
- **Root Cause & Impact (observable defect):** For a fixed-size canvas, `translation.x` / `translation.y` are only assigned when the scaled canvas is *smaller* than the view (`cw < width`, `ch < height`). When the canvas becomes larger than the view (e.g., after zooming in), neither branch runs and the previous centering offset is silently kept. Reproduction: border insets `(top=10, left=30, bottom=5, right=15)`, view `300x200`, canvas `100x100`; after `setScaleFactor(5.0)` the drawing origin is still drawn at view point `(47, 32)` instead of `(30, 10)`, so the oversized canvas is shifted right/down and part of it becomes unreachable.
- **Related code smell (not observable):** For an infinite canvas, `insets.top` was assigned to `translation.x` and `insets.left` to `translation.y` (wrong axes and wrong sign, since `drawingToView` subtracts `translation`). This is masked in practice: the subsequent clamping step (`Math.min(0, r.x) - translation.x < insets.left`) always overrides the initial value, so the original code still placed the origin at `(insets.left, insets.top)`. It was corrected as a readability fix only.
- **Fix Applied:** In `DrawingViewViewport.computeInitialTranslation`, the fixed-canvas branch explicitly resets `translation.x = -insets.left` / `translation.y = -insets.top` whenever `scaledCanvasWidth >= viewWidth` or `scaledCanvasHeight >= viewHeight`; the infinite-canvas branch uses `translation.x = -insets.left; translation.y = -insets.top;`.
- **Verification:** `DefaultDrawingViewDefectFixesTest.defect02FixedCanvasResetsStaleCenteringOffsetWhenZoomedLargerThanView` (fails on the original class, passes after the fix) and `DrawingViewViewportTest.fixedCanvasCentersWhenSmallerAndResetsWhenLarger`.

### Defect #3: `addNotify(DrawingEditor)` Fires Wrong `oldValue` and `removeNotify(DrawingEditor)` Omits Property Change & Handle Invalidation
- **Location:** `DefaultDrawingView.java` → `removeNotify(DrawingEditor)` (`L1420-1423`) and `addNotify(DrawingEditor)` (`L1426-1432`)
- **Original Code:**
  ```java
  public void removeNotify(DrawingEditor editor) {
    this.editor = null;
    repaint();
  }
  public void addNotify(DrawingEditor editor) {
    DrawingEditor oldValue = editor; // shadows this.editor!
    this.editor = editor;
    firePropertyChange("editor", oldValue, editor);
    ...
  }
  ```
- **Root Cause & Impact:** `DrawingEditor oldValue = editor;` read the method parameter instead of `this.editor`, so `oldValue == newValue` and Swing's `PropertyChangeSupport` silently suppressed the event! Meanwhile `removeNotify(DrawingEditor)` never fired the `"editor"` property change or invalidated stale handles.
- **Fix Applied:** Capture `DrawingEditor oldValue = this.editor;` in both methods; `removeNotify(DrawingEditor)` now fires `firePropertyChange("editor", oldValue, null)` and calls `invalidateHandles()`.
- **Verification:** `DefaultDrawingViewDefectFixesTest.defect03AddNotifyAndRemoveNotifyFireAccurateEditorPropertyChanges`.

### Defect #4: `FigureListener` (`handleInvalidator`) Memory Leak in `clearSelection()` and `selectAll()`
- **Location:** `DefaultDrawingView.java` → `selectAll()` (`L900-912`) and `clearSelection()` (`L916-924`)
- **Root Cause & Impact:** `addToSelection` attached `handleInvalidator` to each selected figure and `removeFromSelection` detached it, but `clearSelection()` cleared `SELECTED_FIGURES` without removing `handleInvalidator` from any figure, and `selectAll()` neither detached listeners from previously selected figures nor attached `handleInvalidator` to newly selected figures.
- **Fix Applied:** `DrawingViewSelectionManager.clear()` and `selectAll(Drawing)` call `detachAllFigureListeners()` before clearing `selectedFigures`, and `selectAll(Drawing)` attaches `handleInvalidator` to every newly selected figure.
- **Verification:** `DefaultDrawingViewDefectFixesTest.defect04ClearSelectionAndSelectAllDetachAndAttachFigureListeners` and `DrawingViewSelectionManagerTest`.

### Defect #5: `NullPointerException` When `drawing == null` in Queries, `selectAll()`, `delete()`, and `duplicate()`
- **Location:** `DefaultDrawingView.java` → `selectAll()` (`L903`), `findFigure(Point)` (`L1074`), `findFigures(Rectangle)` (`L1079`), `findFiguresWithin(Rectangle)` (`L1084`), `delete()` (`L1331`), `duplicate()` (`L1379`)
- **Root Cause & Impact:** Calling any of these methods before `setDrawing(...)` (or after `setDrawing(null)`) threw an immediate `NullPointerException`.
- **Fix Applied:** Guarded `drawing == null` in `DrawingViewSelectionManager.selectAll`, `DrawingViewEditCommands.delete`/`duplicate`, `findFigure` (returns `null`), and `findFigures`/`findFiguresWithin` (returns `Collections.emptyList()`).
- **Verification:** `DefaultDrawingViewDefectFixesTest.defect05NullDrawingSafeOperationsDoNotThrowNpe`.

### Defect #6: `NullPointerException` in `EventHandler.drawingAttributeChanged`
- **Location:** `DefaultDrawingView.java` → `EventHandler.drawingAttributeChanged(DrawingEvent e)` (`L287-305`)
- **Original Code:**
  ```java
  AttributeKey<?> a = e.getAttribute();
  if (a.equals(CANVAS_HEIGHT) || a.equals(CANVAS_WIDTH)) { ... }
  if (e.getInvalidatedArea() != null) {
    repaintDrawingArea(e.getFigure()
        .getDrawingArea(AttributeKeys.getScaleFactor(getDrawingToViewTransform())));
  }
  ```
- **Root Cause & Impact:** `DrawingEvent` created for a `Drawing` has `e.getFigure() == null` (see `DrawingEvent(Drawing, Rectangle2D.Double)` and `DrawingEvent(Drawing, AttributeKey<?>, Object, Object)`). Calling `e.getFigure().getDrawingArea(...)` threw NPE whenever `e.getInvalidatedArea() != null`, and `a.equals(...)` threw NPE when `e.getAttribute()` was null.
- **Fix Applied:** Null-safe `CANVAS_HEIGHT.equals(a) || CANVAS_WIDTH.equals(a)`, and pass `e.getInvalidatedArea()` directly to `repaintDrawingArea(e.getInvalidatedArea())`.
- **Verification:** `DefaultDrawingViewDefectFixesTest.defect06DrawingAttributeChangedWithInvalidatedAreaDoesNotThrowNpe`.

### Defect #7: `setHandleDetailLevel` Never Fires Bound Property `HANDLE_DETAIL_LEVEL_PROPERTY`
- **Location:** `DefaultDrawingView.java` → `setHandleDetailLevel(int newValue)` (`L1308-1314`)
- **Root Cause & Impact:** `DrawingView.java:137, 333` documents `HANDLE_DETAIL_LEVEL_PROPERTY` (`"handleDetailLevel"`) as a bound property, yet `setHandleDetailLevel` never called `firePropertyChange(...)`.
- **Fix Applied:** `DefaultDrawingView.setHandleDetailLevel` fires `HANDLE_DETAIL_LEVEL_PROPERTY` on change (and again if `validateHandles` falls back to `0`).
- **Verification:** `DefaultDrawingViewDefectFixesTest.defect07SetHandleDetailLevelFiresPropertyChange`.

### Defect #8: Handle Listener & Disposal Leaks in Secondary Handles and Handle Removal
- **Location:** `DefaultDrawingView.java` → `EventHandler.handleRequestSecondaryHandles` (`L254-263`) and `EventHandler.handleRequestRemove` (`L279-284`)
- **Root Cause & Impact:** `handleRequestSecondaryHandles` called `secondaryHandles.clear()` without removing `eventHandler` or calling `dispose()` on prior secondary handles (and did not null-check `createSecondaryHandles()`). `handleRequestRemove` called `e.getHandle().dispose()` without removing `eventHandler` from the removed handle first.
- **Fix Applied:** `DrawingViewHandleManager.setSecondaryHandles` disposes and detaches listeners from all existing secondary handles first; `removeHandle` calls `handle.removeHandleListener(listener)` before `handle.dispose()`.
- **Verification:** `DefaultDrawingViewDefectFixesTest.defect08SecondaryHandleReplacementAndRemoveDetachListenersAndDispose`.

### Defect #9: Empty-Selection `delete()` / `duplicate()` Fire Spurious `UndoableEdit`s & Mixed Drawing References
- **Location:** `DefaultDrawingView.java` → `delete()` (`L1330-1375`) and `duplicate()` (`L1378-1417`)
- **Root Cause & Impact:** Invoking `delete()` or `duplicate()` with 0 selected figures pushed no-op `"Delete"` / `"Duplicate"` entries onto the `UndoManager` stack. Moreover, the anonymous `AbstractUndoableEdit` classes inconsistently referenced `getDrawing()` in `undo()` and outer `this.drawing` in `redo()`, failing if the view switched drawings.
- **Fix Applied:** `DrawingViewEditCommands` returns early when `view.getSelectionCount() == 0` or `targetDrawing == null`, and both `DeleteFiguresEdit` and `DuplicateFiguresEdit` capture `targetDrawing` consistently.
- **Verification:** `DefaultDrawingViewDefectFixesTest.defect09EmptySelectionDeleteAndDuplicateDoNotFireUndoableEdits`.

### Defect #10: Headless / Zero-Size `NullPointerException` & `IllegalArgumentException` in Double-Buffered Painting
- **Location:** `DefaultDrawingView.java` → `drawDrawingVolatileBuffered` (`L401-515`) and `drawDrawingNonvolatileBuffered` (`L518-618`)
- **Root Cause & Impact:** When `paintComponent` was called with `isDrawingDoubleBuffered() == true` (the default) on a non-displayable or headless component (`getGraphicsConfiguration() == null`), or with `vr.width == 0 || vr.height == 0`, `getGraphicsConfiguration().createCompatibleImage(...)` threw `NullPointerException` or `IllegalArgumentException`.
- **Fix Applied:** `DrawingViewBuffer` returns early if `visibleRect.width <= 0 || visibleRect.height <= 0` and falls back to direct rendering when `gc == null`.
- **Verification:** `DefaultDrawingViewDefectFixesTest.defect10HeadlessDoubleBufferedPaintDoesNotThrowNullPointerException` and `DrawingViewBufferTest`.

### Defect #11: Stale `cachedDrawingArea` & Overlapping `setDrawing` Repaint Timers
- **Location:** `DefaultDrawingView.java` → `setDrawing(Drawing)` (`L745-770`)
- **Root Cause & Impact:**
  1. `setDrawing()` did not call `invalidateDimension()` before `validateViewTranslation()`, causing `validateViewTranslation()` and `getPreferredSize()` to use the previous drawing's cached bounds/size.
  2. Each `setDrawing()` call created an unreferenced `javax.swing.Timer(10, ...)`, so rapid `setDrawing` calls left overlapping timers firing out of order.
- **Fix Applied:** `setDrawing()` calls `invalidateDimension()` before `validateViewTranslation()` and stops any prior `repaintTimer` before starting the new one. (The timer callback now also sets `paintEnabled = true` before calling `repaint()`; this is only a readability reordering, not a defect fix, because `repaint()` merely schedules a later paint on the same Event Dispatch Thread.)
- **Verification:** `DefaultDrawingViewDefectFixesTest.defect11SetDrawingInvalidatesCachedDimensionImmediatelyAndCancelsOldTimer`.

### Defect #12: `setScaleFactor` Accepts Invalid Values and Fails to Invalidate Cached Dimensions
- **Location:** `DefaultDrawingView.java` → `setScaleFactor(double)` (`L1287-1296`)
- **Root Cause & Impact:**
  1. `setScaleFactor(0)`, negative numbers, `NaN`, or `Infinity` were accepted without validation, causing division by zero or corrupted transforms in `viewToDrawing`.
  2. `invalidateDimension()` was only triggered asynchronously via `revalidate()`, so calling `getPreferredSize()` or `getDrawingArea()` immediately after `setScaleFactor(...)` returned stale values for the old scale factor.
- **Fix Applied:** `DrawingViewViewport.setScaleFactor` validates `Double.isFinite(newValue) && newValue > 0` (throwing `IllegalArgumentException` otherwise) and calls `invalidateDimension()` synchronously before `validateViewTranslation()`.
- **Verification:** `DefaultDrawingViewDefectFixesTest.defect12SetScaleFactorValidatesInputAndUpdatesPreferredSizeImmediately` and `DrawingViewViewportTest`.
