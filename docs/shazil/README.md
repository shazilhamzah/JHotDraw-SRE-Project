# Shazil — Core View Re-engineering (`DefaultDrawingView`)

## Scope

This folder documents the re-engineering work for `jhotdraw-core`'s `DefaultDrawingView` (`org.jhotdraw.draw.DefaultDrawingView`), covering:

1. **Baseline static & CK metrics** (AST-based Chidamber-Kemerer + McCabe cyclomatic complexity + PMD 7.7.0).
2. **Bad code smells and 12 concrete defects** with exact original file, method, and line evidence.
3. **Characterization and integration tests** written against the unmodified class before any production code edits.
4. **Incremental catalog refactorings** decomposing `DefaultDrawingView` into five focused package-private collaborators while preserving the entire `public` and `protected` API contract.
5. **Regression tests for all 12 defect fixes**, unit tests for every extracted class, and full multi-module verification.
6. **Post-refactoring metrics** confirming complexity, size, coupling, cohesion, and PMD violation improvements.

> Note: Per `TASKS.md`, final presentation slides and the combined group report are excluded from this folder; all engineering artifacts and measurements for Shazil's code module are documented here.

## Architecture Summary

`DefaultDrawingView` originally combined six distinct responsibilities inside a single 1,550-line class (1,476 class LOC, 81 methods, WMC = 237, MaxCC = 22). It has been decomposed into `DefaultDrawingView` (Swing `JComponent` facade, rendering coordination, `EventHandler`, bound properties) plus five focused package-private collaborators in `org.jhotdraw.draw`:

- `DrawingViewBuffer` — Volatile and nonvolatile offscreen double-buffering, dirty-region shifting, headless/zero-dimension fallbacks.
- `DrawingViewViewport` — Scale factor validation, coordinate transforms (`drawingToView` / `viewToDrawing`), view translation centering/clamping, canvas bounds, preferred size & drawing area caching.
- `DrawingViewHandleManager` — Selection & secondary handle lifecycle, detail-level fallback, handle invalidation/disposal, reverse Z-order hit-testing, compatible handle resolution.
- `DrawingViewSelectionManager` — Ordered selection set, unmodifiable exposure, `FigureListener` attachment/detachment, selection delta calculation.
- `DrawingViewEditCommands` — `delete` and `duplicate` commands with named `DeleteFiguresEdit` and `DuplicateFiguresEdit` undoable edits.

## Headline Scorecard (`DefaultDrawingView`)

| Metric | Before | After | Delta |
|---|---:|---:|---:|
| File Lines | 1,550 | 932 | -618 (-39.9%) |
| Class LOC (`DefaultDrawingView`) | 1,476 | 876 | -600 (-40.7%) |
| Class SLOC (`DefaultDrawingView`) | 1,185 | 675 | -510 (-43.0%) |
| Fields (`NOF`) | 30 | 25 | -5 (-16.7%) |
| Weighted Methods per Class (`WMC`) | 237 | 138 | -99 (-41.8%) |
| Max Cyclomatic Complexity (`MaxCC`) | 22 | 5 | -17 (-77.3%) |
| Mean Cyclomatic Complexity (`AvgCC`) | 2.93 | 1.60 | -1.33 (-45.4%) |
| Coupling Between Objects (`CBO`) | 52 | 45 | -7 (-13.5%) |
| `LCOM1` | 2,814 | 2,722 | -92 (-3.3%) |
| `LCOM*` | 0.9611 | 0.9349 | -0.0262 (-2.7%) |
| PMD Violations (`DefaultDrawingView` + helpers) | 5 | 0 | -5 (-100.0%) |
| `jhotdraw-core` Automated Tests | 7 | 50 | +43 (+614%) |

*(Note: `DefaultDrawingView` retains 86 methods because all `DrawingView` / `EditableComponent` / `protected` methods remain on the facade as thin 1-3 line delegators, which explains why `LCOM1` remains dominated by facade delegation while `WMC` dropped 41.8% and `MaxCC` dropped 77.3% from 22 to 5.)*

## Document Index

- [metrics.md](file:///C:/Users/MuhammadShazil/Documents/SRE%20Project/docs/shazil/metrics.md) — Measurement methodology, before/after tables, per-collaborator metrics, top complex methods, and links to raw data.
- [code-smells-and-defects.md](file:///C:/Users/MuhammadShazil/Documents/SRE%20Project/docs/shazil/code-smells-and-defects.md) — Code smell taxonomy and all 12 documented defects with line evidence, root causes, and fixes.
- [refactoring-log.md](file:///C:/Users/MuhammadShazil/Documents/SRE%20Project/docs/shazil/refactoring-log.md) — Step-by-step Martin Fowler catalog refactorings, Mermaid class diagram, and API compatibility notes.
- [testing.md](file:///C:/Users/MuhammadShazil/Documents/SRE%20Project/docs/shazil/testing.md) — Characterization, unit, integration, and defect-regression test suites and verification results.

## Reproduction Commands

Run all commands in PowerShell using the portable JDK 17 and Maven 3.9.9 included under `tools/`:

```powershell
$env:JAVA_HOME = "C:\Users\MuhammadShazil\Documents\SRE Project\tools\jdk\jdk-17.0.21.1+2"
$env:PATH = "$env:JAVA_HOME\bin;C:\Users\MuhammadShazil\Documents\SRE Project\tools\maven\apache-maven-3.9.9\bin;$env:PATH"

# Full multi-module build, Spotless formatting, Checkstyle, and all unit/integration tests
cd "C:\Users\MuhammadShazil\Documents\SRE Project\jhotdraw-src"
mvn test

# Run PMD 7.7.0 on jhotdraw-core
mvn -pl jhotdraw-core -am test-compile org.apache.maven.plugins:maven-pmd-plugin:3.26.0:pmd

# Run AST CK & complexity calculator (compiled to temp dir)
$tmpOut = "$env:TEMP\metrics_calc_classes"
New-Item -ItemType Directory -Force -Path $tmpOut | Out-Null
javac --add-exports jdk.compiler/com.sun.source.tree=ALL-UNNAMED --add-exports jdk.compiler/com.sun.source.util=ALL-UNNAMED -d $tmpOut "C:\Users\MuhammadShazil\Documents\SRE Project\docs\shazil\metrics\MetricsCalculator.java"
$base = "C:\Users\MuhammadShazil\Documents\SRE Project\jhotdraw-src\jhotdraw-core\src\main\java\org\jhotdraw\draw"
java --add-exports jdk.compiler/com.sun.source.tree=ALL-UNNAMED --add-exports jdk.compiler/com.sun.source.util=ALL-UNNAMED -cp $tmpOut MetricsCalculator `
  "C:\Users\MuhammadShazil\Documents\SRE Project\docs\shazil\metrics\after\ck-metrics.json" `
  "C:\Users\MuhammadShazil\Documents\SRE Project\docs\shazil\metrics\after\ck-metrics.txt" `
  "$base\DefaultDrawingView.java" "$base\DrawingViewBuffer.java" "$base\DrawingViewViewport.java" `
  "$base\DrawingViewHandleManager.java" "$base\DrawingViewSelectionManager.java" "$base\DrawingViewEditCommands.java"
```
