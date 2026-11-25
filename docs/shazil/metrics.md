# Metrics Report — Before vs. After Refactoring

## Methodology

Two complementary static measurement tools were executed identically before any refactoring and after the final verified build:

1. **AST CK & Complexity Analyzer** ([MetricsCalculator.java](../../docs/shazil/metrics/MetricsCalculator.java))
   Built on JDK 17's `com.sun.source.util.JavacTask` and `TreeScanner` so results are deterministic, reproducible, and require no external binary setup:
   - **LOC / SLOC**: Total physical lines in the class declaration, and non-empty non-comment source lines.
   - **NOM / NOF**: Direct methods + constructors (`NOM`), and declared fields (`NOF`).
   - **McCabe Cyclomatic Complexity (`CC`) / `WMC`**: Starts at `1` per method/constructor and increments on `if`, `for`, enhanced `for`, `while`, `do-while`, non-default `case`, `catch`, ternary `?:`, `&&`, and `||`. `WMC` is the sum across all methods in the class.
   - **CBO**: Distinct non-primitive, non-`java.lang`, non-self reference types referenced in inheritance clauses, field types, method signatures, `throws`, local variables, `new` expressions, casts, `instanceof`, `catch` parameters, and static type selects.
   - **LCOM1**: Chidamber-Kemerer $\max(0, P - Q)$ over pairs of concrete instance methods (`P` = pairs sharing no instance fields, `Q` = pairs sharing at least one instance field).
   - **LCOM\***: Henderson-Sellers normalized lack of cohesion in $[0, 1]$: $\frac{m - \frac{1}{a}\sum_k \mu(a_k)}{m - 1}$ (0 if $m \le 1$ or $a = 0$).

2. **Apache Maven PMD Plugin `3.26.0` (PMD `7.7.0`)**
   Executed with `mvn -pl jhotdraw-core -am test-compile org.apache.maven.plugins:maven-pmd-plugin:3.26.0:pmd` against both the unmodified baseline and the final refactored code.

Raw outputs are archived in:
- Before: [before/ck-metrics.txt](../../docs/shazil/metrics/before/ck-metrics.txt), [before/ck-metrics.json](../../docs/shazil/metrics/before/ck-metrics.json), [before/pmd-summary.txt](../../docs/shazil/metrics/before/pmd-summary.txt), [before/pmd-report.xml](../../docs/shazil/metrics/before/pmd-report.xml)
- After: [after/ck-metrics.txt](../../docs/shazil/metrics/after/ck-metrics.txt), [after/ck-metrics.json](../../docs/shazil/metrics/after/ck-metrics.json), [after/pmd-summary.txt](../../docs/shazil/metrics/after/pmd-summary.txt), [after/pmd-report.xml](../../docs/shazil/metrics/after/pmd-report.xml)

---

## `DefaultDrawingView` — Before vs. After

| Metric | Before (`DefaultDrawingView`) | After (`DefaultDrawingView`) | Change | % Delta |
|---|---:|---:|---:|---:|
| Total File Lines | 1,550 | 932 | -618 | -39.9% |
| Class `LOC` | 1,476 | 876 | -600 | -40.7% |
| Class `SLOC` | 1,185 | 675 | -510 | -43.0% |
| `NOM` (Methods + Ctors) | 81 | 86 | +5 | +6.2% |
| `NOF` (Fields) | 30 | 25 | -5 | -16.7% |
| `WMC` (Weighted Methods/Class) | 237 | 138 | -99 | -41.8% |
| `MaxCC` | 22 | 5 | -17 | -77.3% |
| `AvgCC` | 2.93 | 1.60 | -1.33 | -45.4% |
| `CBO` | 52 | 45 | -7 | -13.5% |
| `LCOM1` | 2,814 | 2,722 | -92 | -3.3% |
| `LCOM*` | 0.9611 | 0.9349 | -0.0262 | -2.7% |
| PMD Violations | 5 | 0 | -5 | -100.0% |

### Interpretation of `DefaultDrawingView` Metrics

- **`MaxCC` dropped 77.3% (22 → 5) and `WMC` dropped 41.8% (237 → 138):** Every high-complexity algorithm (`drawDrawingVolatileBuffered` CC=22, `drawDrawingNonvolatileBuffered` CC=18, `validateViewTranslation` CC=11, `validateHandles` CC=10, `addToSelection` CC=9, `invalidateHandles` CC=7, `delete` CC=6) was extracted into focused collaborators or decomposed into small single-purpose helpers. No method left in `DefaultDrawingView` or `EventHandler` has `CC > 5`.
- **`LOC` dropped 40.7% and `SLOC` dropped 43.0%:** About 510 source lines of tangled Swing buffering, coordinate geometry, handle management, selection tracking, and undoable command logic were removed from `DefaultDrawingView`.
- **Why `NOM` increased slightly (81 → 86) and `LCOM` decreased modestly:** `DefaultDrawingView` implements two large framework interfaces (`DrawingView` and `EditableComponent`) and exposes `protected` Swing/JHotDraw hooks (`drawBackground`, `drawCanvas`, `drawDrawing`, `drawHandles`, `drawTool`, `getDrawingArea`, etc.). Preserving full binary and source backward compatibility kept all facade methods on `DefaultDrawingView` while decomposing rendering helpers (`drawInfiniteCanvasBackground`, `fillFullViewWithBackgroundTile`, `createTranslucentCanvasColor`, `applyRenderingHints`, `drawWithViewTransform`). The extracted collaborators themselves exhibit dramatically higher cohesion (`LCOM1` = 0 on `DrawingViewViewport`, `DrawingViewSelectionManager`, and `DrawingViewEditCommands`; `LCOM1` = 2 on `DrawingViewHandleManager`).

---

## Post-Refactoring Module Breakdown (All Classes)

| Class | File | LOC | SLOC | NOM | NOF | WMC | MaxCC | AvgCC | CBO | LCOM1 | LCOM* | PMD |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| `DefaultDrawingView` | `DefaultDrawingView.java` | 876 | 675 | 86 | 25 | 138 | 5 | 1.60 | 45 | 2,722 | 0.9349 | 0 |
| `EventHandler` | `DefaultDrawingView.java` (inner) | 81 | 71 | 10 | 0 | 20 | 5 | 2.00 | 11 | 45 | 0.0000 | 0 |
| `DrawingViewBuffer` | `DrawingViewBuffer.java` | 265 | 233 | 17 | 7 | 55 | 13 | 3.24 | 12 | 28 | 0.7467 | 0 |
| `DrawingViewViewport` | `DrawingViewViewport.java` | 197 | 169 | 17 | 4 | 40 | 6 | 2.35 | 8 | 0 | 0.6406 | 0 |
| `DrawingViewHandleManager` | `DrawingViewHandleManager.java` | 222 | 202 | 19 | 5 | 46 | 6 | 2.42 | 15 | 2 | 0.7125 | 0 |
| `DrawingViewSelectionManager` | `DrawingViewSelectionManager.java` | 103 | 89 | 11 | 3 | 19 | 4 | 1.73 | 13 | 0 | 0.6000 | 0 |
| `SelectionDelta` | `DrawingViewSelectionManager.java` (record) | 10 | 9 | 1 | 4 | 1 | 1 | 1.00 | 4 | 0 | 0.0000 | 0 |
| `DrawingViewEditCommands` | `DrawingViewEditCommands.java` | 135 | 113 | 3 | 3 | 12 | 6 | 4.00 | 11 | 0 | 0.0000 | 0 |
| `DeleteFiguresEdit` | `DrawingViewEditCommands.java` (inner) | 43 | 38 | 4 | 5 | 6 | 2 | 1.50 | 8 | 0 | 0.5000 | 0 |
| `DuplicateFiguresEdit` | `DrawingViewEditCommands.java` (inner) | 29 | 24 | 4 | 3 | 4 | 1 | 1.00 | 7 | 0 | 0.3333 | 0 |

---

## Top Complex Methods — Before vs. After

### Before (`DefaultDrawingView.java`)

| Rank | Method | Original Lines | LOC | Cyclomatic Complexity (`CC`) |
|---:|---|---:|---:|---:|
| 1 | `drawDrawingVolatileBuffered(Graphics2D)` | L401–L515 | 115 | 22 |
| 2 | `drawDrawingNonvolatileBuffered(Graphics2D)` | L518–L618 | 101 | 18 |
| 3 | `validateViewTranslation()` | L1187–L1242 | 56 | 11 |
| 4 | `validateHandles()` | L991–L1023 | 33 | 10 |
| 5 | `addToSelection(Collection<Figure>)` | L833–L864 | 32 | 9 |
| 6 | `addToSelection(Figure)` | L806–L830 | 25 | 7 |
| 7 | `invalidateHandles()` | L959–L988 | 30 | 7 |
| 8 | `delete()` | L1329–L1375 | 47 | 6 |
| 9 | `drawBackground(Graphics2D)` | L171–L207 | 37 | 6 |
| 10 | `getPreferredSize()` | L1133–L1163 | 31 | 6 |

### After (Across `DefaultDrawingView` + Collaborators)

- Highest method in `DefaultDrawingView`: `fireSelectionChanged` (`CC=5`, 20 LOC) and `drawHandles` (`CC=5`, 11 LOC).
- Highest method across all extracted collaborators: `DrawingViewBuffer.prepareBufferedArea` (`CC=13`, 49 LOC — consolidated from the two duplicated 115-line and 101-line buffering methods) and `DrawingViewBuffer.drawVolatile` (`CC=12`, 55 LOC).
- Every method in `DrawingViewViewport`, `DrawingViewHandleManager`, `DrawingViewSelectionManager`, and `DrawingViewEditCommands` has `CC <= 6`.

## PMD Violations Summary

- **Before (`DefaultDrawingView.java` — 5 violations):**
  1. `L141-143` `[Error Prone/EmptyCatchBlock]` (p=3): Empty `catch (Throwable t)` block in `static` initializer.
  2. `L300-304` `[Code Style/EmptyControlStatement]` (p=3): Empty `else` block in `EventHandler.drawingAttributeChanged`.
  3. `L726` `[Code Style/UnnecessaryFullyQualifiedName]` (p=4): Unnecessary `java.awt.Graphics2D` qualifier in `drawHandles`.
  4. `L1017` `[Error Prone/AvoidBranchingStatementAsLastInLoop]` (p=2): `break` as last statement in `while (true)` loop in `validateHandles`.
  5. `L1120` `[Code Style/UnnecessaryFullyQualifiedName]` (p=4): Unnecessary `EditableComponent.` qualifier on `SELECTION_EMPTY_PROPERTY` in `fireSelectionChanged`.
- **After (`DefaultDrawingView.java` + all 5 extracted classes):** **0 PMD violations**.
