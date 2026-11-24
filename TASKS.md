# JHotDraw Re-engineering - Task Split

Project: `wumpz/jhotdraw` (10.5-SNAPSHOT, Java 17, Maven, root `jhotdraw-src/`). Scope: 8,536 lines across 5 target classes.
Build: portable JDK 17 + Maven in `tools/`. `mvn test` passes (6 existing test files, none cover our classes). We write characterization tests before refactoring.

## Git Workflow
- Do **not** push directly to `main`: create a feature branch for your work and open a Pull Request (PR) to merge into `main`.
- Exception: Shazil may push directly to `main`.

## Everyone (each on own classes)
1. Run metrics before (SonarQube / PMD / CK): WMC, CBO, LCOM, LOC, cyclomatic complexity.
2. Identify bad code smells and 5+ defects with exact file, method, and line evidence.
3. Set up tests in your module:
   - `jhotdraw-core/pom.xml` already has `junit-jupiter` and `assertj-core`.
   - `jhotdraw-gui/pom.xml` needs `junit-jupiter` and `assertj-core` (`<scope>test</scope>`) added.
   - `jhotdraw-samples/jhotdraw-samples-misc/pom.xml` needs `junit-jupiter`, `assertj-core`, and `xmlunit-assertj` `2.8.2` (`<scope>test</scope>`) added.
   - Place tests in the same package as the class under test (`org.jhotdraw.samples.svg.io`, `org.jhotdraw.gui.action`, `org.jhotdraw.draw`).
   - Run `mvn test` (Spotless formatting + Checkstyle run automatically on build and check test sources too).
4. Write extensive characterization tests (unit + integration) covering every process and edge case in your classes (lock down current behavior before changing production code).
5. Apply appropriate named catalog refactorings in small steps, re-running your full test suite after each step. Add unit tests for any newly extracted classes.
6. Re-run metrics after. Write before/after section of report.

## Hassan - SVG import
- Target: `jhotdraw-samples/jhotdraw-samples-misc/.../svg/io/SVGInputFormat.java` (3,185 lines, biggest class in repo)
- Identify bad code smells and 5+ defects with file, method, and line evidence.
- Apply appropriate named catalog refactorings and re-run tests.
- Tests to write (before refactoring, most parsers are `private`, so **integration tests fit best**, plus **unit tests** for public helpers):
  - **Unit tests**: public static tokenizers (`toCommaSeparatedArray`, `toWSOrCommaSeparatedArray`, `toQuotedAndCommaSeparatedArray`), `getFileFilter`, `isDataFlavorSupported`.
  - **Integration tests** (through `read(InputStream/File/URL/Transferable, Drawing, boolean)`; note: first verify/fix any DOM parser bootstrap defect in `read(...)` that prevents XML input from loading on JDK 17 before expanding full input coverage):
    - Input modes & `replace=true/false`, sequential `read` reuse on one instance, invalid/missing `<svg>` error cases.
    - Viewport, `viewBox`, `preserveAspectRatio`, unit conversions (`px`, `pt`, `pc`, `mm`, `cm`, `in`, `%`, `em`), nested `<svg>`.
    - CSS `<style>` and inline `style` flattening + attribute inheritance (`inherit`, `currentcolor`, font sizes).
    - All SVG shapes (`<rect>`, `<circle>`, `<ellipse>`, `<line>`, `<polyline>`, `<polygon>`, `<path>` covering every `d` command `M/m/Z/z/L/l/H/h/V/v/C/c/S/s/Q/q/T/t/A/a`).
    - All `transform` functions (`matrix`, `translate`, `scale`, `rotate`, `skewX`, `skewY`, chained).
    - Colors, opacity, stroke styles, `<linearGradient>`, `<radialGradient>`, `<solidColor>`, `<defs>`, `<use>` (`id`/`xml:id`), `<a>` links, `<g>` visibility/display, `<switch>` feature/language selection.
    - `<text>`, `<textArea>`, `<tspan>`, font attributes, `xml:space`, and `<image>` (Base64 data URI, relative file/URL, nested `.svg`, missing file).
  - After refactoring, add unit tests for any extracted parser/helper classes.
- Also: architecture diagram + patterns found (Phase 1).

## Muhammad - SVG export + GUI action
- Targets:
  - `jhotdraw-samples/jhotdraw-samples-misc/.../svg/io/SVGOutputFormat.java` (1,375)
  - `jhotdraw-samples/jhotdraw-samples-misc/.../svg/io/ImageMapOutputFormat.java` (500)
  - `jhotdraw-gui/.../gui/action/ButtonFactory.java` (1,926)
- Identify bad code smells and 5+ defects (both intra-file and cross-file) with file, method, and line evidence.
- Apply appropriate named catalog refactorings and re-run tests.
- Tests to write (**unit + integration** depending on class):
  - **`SVGOutputFormat` (Unit + Integration)**:
    - **Unit tests**: `public static` formatters (`toNumber`, `toColor`, `toPoints`, `toTransform`, `toPath` across all segment/subpath cases) and metadata (`getFileFilter`, `getFileExtension`, `isPrettyPrint`/`setPrettyPrint`).
    - **Integration tests**: `write(OutputStream/File/URI, Drawing)` (full drawing and figure-subset overloads), `createTransferable`, every `SVG*Figure` type (`rect`, `circle`/`ellipse`, `line`/`polyline`/`polygon`/`path`, `text`, `textArea`, `image`, `g`), default-attribute omission, gradients/`<defs>` deduplication, `<a>` links, viewport attributes, plus `SVGOutputFormat` -> `SVGInputFormat` round-trip tests (once Hassan's `SVGInputFormat.read(...)` loads cleanly on JDK 17).
  - **`ImageMapOutputFormat` (Unit + Integration)**:
    - **Unit tests**: `getFileFilter`, `getFileExtension`.
    - **Integration tests**: `write(...)` overloads and `createTransferable` covering all `SVG*Figure` -> `<area>`/`<AREA>` conversions (`circle`, `rect`, `poly`), transforms + `drawingTransform`, `imageSize` bounds filtering, `href`/`title`/`alt`/`target`/`nohref`, and edge cases (empty list, rotated shapes, multi-subpath).
  - **`ButtonFactory` (Unit + Headless Swing Integration)**:
    - **Unit tests**: static color palettes, `createDrawingActions`, `createSelectionActions`, and each button factory (`createZoomButton`, editor/selection/drawing color & chooser buttons, all stroke buttons, font & style buttons, grid toggle, pick/apply attributes) checking button/popup counts, listeners, `ButtonGroup` wiring, and `Disposable` tracking.
    - **Integration tests**: composite toolbar builders (`addSelectionToolTo`, `addToolTo`, `addAttributesButtonsTo`, `addColorButtonsTo`, `addStrokeButtonsTo`, `addFontButtonsTo`, `addAlignmentButtonsTo`) wired to `DefaultDrawingEditor` + `DefaultDrawingView` + `DefaultDrawing`, invoking button/menu actions programmatically to verify tool, zoom, grid, and figure attribute updates. Keep tests headless-safe (inspect popup menus and trigger menu item actions directly instead of showing top-level windows or screen popups).

## Shazil - Core view + report
- Target: `jhotdraw-core/.../draw/DefaultDrawingView.java` (1,550)
- Identify bad code smells and 5+ defects with file, method, and line evidence.
- Apply appropriate named catalog refactorings and re-run tests.
- Tests to write (**Unit + Integration**, note `jhotdraw-core` does not depend on `jhotdraw-actions` so use a lightweight `DrawingEditor` test stub; set `setDrawingDoubleBuffered(false)` when painting non-displayable views in headless tests):
  - **Unit tests**:
    - Constructor defaults, empty-drawing message, cursor enabled/disabled state.
    - Selection management (`addToSelection` single/collection, `removeFromSelection`, `toggleSelection`, `selectAll`, `clearSelection`, unmodifiable set, `FigureSelectionListener` order and `SELECTION_EMPTY_PROPERTY`).
    - Handle lifecycle (`validateHandles`, detail-level fallback, `invalidateHandles`, `findHandle`, `getCompatibleHandles`, `setActiveHandle`, secondary handles, handle listener wiring).
    - Coordinate conversions (`drawingToView`, `viewToDrawing` for points & rectangles, `getDrawingToViewTransform`), `scaleFactor`, view translation behavior (tested indirectly via `setBounds`, `setScaleFactor`, and `setDrawing` for null drawing, infinite/fixed canvas, centering, clamping), `getPreferredSize`/`getDrawingArea` caching.
    - Constrainer switching (`visibleConstrainer` vs `invisibleConstrainer`), `EventHandler` (`DrawingListener` figure add/remove/attribute updates), `findFigure`/`findFigures`/`findFiguresWithin`.
    - Offscreen `BufferedImage` rendering (`paint`, `paintComponent`, `printComponent`, `drawBackground`, `getBackgroundPaint`, active vs inactive view handles/tool) and `delete()`/`duplicate()` with `UndoableEdit` undo/redo.
  - **Integration tests**:
    - Real `DefaultDrawing` + figures (`RectangleFigure`, `BezierFigure`, `GroupFigure`), `setDrawing(...)` listener swap and 10ms Swing `Timer` paint re-enablement on EDT, `addNotify`/`removeNotify`, `focusGained`, `getToolTipText`, `DefaultDrawingViewTransferHandler` setup, and full selection -> handle -> `duplicate`/`delete`/undo/redo -> repaint flow.
- Also: final report, slides, metrics tables, presentation (10 min).

## Deadlines
- Code and report: 30 Nov
- Presentation: last week of classes
