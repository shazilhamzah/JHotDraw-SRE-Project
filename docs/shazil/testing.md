# Testing Strategy and Suite Inventory

## Testing Strategy

Because `jhotdraw-core` originally contained only 7 unit tests (covering `AbstractFigure`, `Attributes`, and `TextFigure`) and zero tests for `DefaultDrawingView`, testing proceeded in three deliberate phases:

1. **Phase A — Characterization Tests (Pre-Refactoring Safety Net)**
   Written and verified against the *unmodified* `DefaultDrawingView.java` before changing a single line of production code. Because `jhotdraw-core` does not depend on `jhotdraw-actions`, `TestDrawingEditorStub` provides an isolated, dependency-free `DrawingEditor` implementation along with `StubTool`, `StubHandle`, and `StubFigure`.
2. **Phase B — Headless & EDT-Safe Swing Testing**
   - Non-displayable `DefaultDrawingView` components in unit/integration tests use `setDrawingDoubleBuffered(false)` when testing `paintComponent` / `printComponent` directly (avoiding platform `GraphicsConfiguration` assumptions), while headless fallback behavior *with* double buffering enabled is explicitly tested in `DrawingViewBufferTest` and `DefaultDrawingViewDefectFixesTest`.
   - `BufferedImage.createGraphics()` contexts set an explicit clip (`g.setClip(0, 0, w, h)`) because `DefaultDrawing.draw` and `GridConstrainer.draw` intersect with `g.getClipBounds()`.
   - `DefaultDrawingViewIntegrationTest` exercises the 10 ms Swing `Timer` inside `setDrawing()` on the Event Dispatch Thread (`SwingUtilities.invokeAndWait`) using a bounded polling loop rather than fixed sleeps.
3. **Phase C — Collaborator Unit Tests & Defect Regression Suite**
   - Each of the 5 extracted classes has its own dedicated JUnit 5 test class covering boundary conditions and internal invariants.
   - `DefaultDrawingViewDefectFixesTest` provides one deterministic regression test for each of the 12 defects fixed during refactoring.

---

## Test File Inventory (`jhotdraw-core/src/test/java/org/jhotdraw/draw/`)

| Test File | Kind | Tests | Coverage Focus |
|---|---|---:|---|
| [TestDrawingEditorStub.java](../../jhotdraw-src/jhotdraw-core/src/test/java/org/jhotdraw/draw/TestDrawingEditorStub.java) | Helper | — | `DrawingEditor`, `StubTool`, `StubHandle`, `StubFigure` test doubles |
| [DefaultDrawingViewUnitTest.java](../../jhotdraw-src/jhotdraw-core/src/test/java/org/jhotdraw/draw/DefaultDrawingViewUnitTest.java) | Characterization (Unit) | 13 | Defaults, empty message, cursor/enabled, selection order & listener precedence, `selectAll`, handle lifecycle & detail-level fallback, secondary & compatible handles, coordinate transforms, scale/translation/preferred-size caching, constrainer switching, `EventHandler`, `findFigure*`, offscreen `BufferedImage` rendering (`paintComponent`, `printComponent`, `drawBackground`, `getBackgroundPaint`), double-buffered `paintComponent` dispatch through the overridable `drawDrawingVolatileBuffered` / `drawDrawingNonvolatileBuffered` hooks, `delete`/`duplicate` undo/redo |
| [DefaultDrawingViewIntegrationTest.java](../../jhotdraw-src/jhotdraw-core/src/test/java/org/jhotdraw/draw/DefaultDrawingViewIntegrationTest.java) | Characterization (Integration) | 3 | `setDrawing` listener swap & 10 ms EDT timer re-enablement, `addNotify`/`removeNotify`/focus/tooltip/`TransferHandler`, full `DefaultDrawing` + `RectangleFigure` + `BezierFigure` + `GroupFigure` workflow |
| [DrawingViewBufferTest.java](../../jhotdraw-src/jhotdraw-core/src/test/java/org/jhotdraw/draw/DrawingViewBufferTest.java) | Unit (Extracted) | 4 | Double-buffer toggle/flush, zero-size `visibleRect` safety, `gc == null` direct fallback, shift & resize caching |
| [DrawingViewViewportTest.java](../../jhotdraw-src/jhotdraw-core/src/test/java/org/jhotdraw/draw/DrawingViewViewportTest.java) | Unit (Extracted) | 4 | `scaleFactor` validation, asymmetric inset placement, fixed-canvas centering & scaling reset, preferred size defensive clones |
| [DrawingViewHandleManagerTest.java](../../jhotdraw-src/jhotdraw-core/src/test/java/org/jhotdraw/draw/DrawingViewHandleManagerTest.java) | Unit (Extracted) | 3 | Detail-level 0 fallback, secondary handle replacement without listener/dispose leaks, reverse-order hit-testing & owner-unique compatible handles |
| [DrawingViewSelectionManagerTest.java](../../jhotdraw-src/jhotdraw-core/src/test/java/org/jhotdraw/draw/DrawingViewSelectionManagerTest.java) | Unit (Extracted) | 1 | Unmodifiable selection, `handleInvalidator` attach/detach on `addAll`, `clear`, `selectAll`, null drawing safety |
| [DrawingViewEditCommandsTest.java](../../jhotdraw-src/jhotdraw-core/src/test/java/org/jhotdraw/draw/DrawingViewEditCommandsTest.java) | Unit (Extracted) | 3 | Null/empty no-ops, non-removable figure abort + beep, z-order undo/redo, duplicate `(5, 5)` offset + `remap` |
| [DefaultDrawingViewDefectFixesTest.java](../../jhotdraw-src/jhotdraw-core/src/test/java/org/jhotdraw/draw/DefaultDrawingViewDefectFixesTest.java) | Regression | 12 | Explicit 1-to-1 verification for Defects #1 through #12 |
| Existing `jhotdraw-core` tests | Pre-existing Unit | 7 | `AbstractFigureTest` (2), `AttributesTest` (4), `TextFigureTest` (1) |
| **Total (`jhotdraw-core`)** | | **50** | **50 passed, 0 failed, 0 errors, 0 skipped** |

---

## Full Multi-Module Build Verification

Command executed after the final edit:

```powershell
mvn test
```

All 13 reactor modules in `jhotdraw-src/` completed with `SUCCESS`, 0 Spotless formatting issues, and 0 Checkstyle violations:

- `jhotdraw-utils`: 8/8 tests passed (`BezierPathTest`: 4, `MutablePath2DTest`: 4)
- `jhotdraw-core`: 50/50 tests passed (`DefaultDrawingViewDefectFixesTest`: 12, `DefaultDrawingViewIntegrationTest`: 3, `DefaultDrawingViewUnitTest`: 13, `DrawingViewBufferTest`: 4, `DrawingViewEditCommandsTest`: 3, `DrawingViewHandleManagerTest`: 3, `DrawingViewSelectionManagerTest`: 1, `DrawingViewViewportTest`: 4, `AbstractFigureTest`: 2, `AttributesTest`: 4, `TextFigureTest`: 1)
- Remaining modules (`jhotdraw`, `jhotdraw-datatransfer`, `jhotdraw-api`, `jhotdraw-actions`, `jhotdraw-gui`, `jhotdraw-app`, `jhotdraw-xml`, `jhotdraw-io`, `jhotdraw-samples`, `jhotdraw-samples-misc`, `jhotdraw-samples-mini`): compiled and passed Checkstyle cleanly.
