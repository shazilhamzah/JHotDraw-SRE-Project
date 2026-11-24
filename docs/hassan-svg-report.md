# SVG Import Re-engineering — Hassan

## Scope and objective
JHotDraw 10.5-SNAPSHOT is a Java 17 drawing framework built with Maven. My assigned class is SVGInputFormat.java in org.jhotdraw.samples.svg.io, identified in TASKS.md as a 3,185 physical-line class. The objective is to improve maintainability and correct import defects while preserving supported behavior.

## Reverse engineering and architecture
The importer reads SVG from streams, files, URLs and clipboard transfers, parses XML and CSS, constructs figures using SVGFigureFactory and adds them to Drawing. DefaultDrawingView observes model changes; DefaultDrawingEditor coordinates editing tools. Existing patterns include Factory, Composite, Observer and Prototype. The architecture is MVC-like. See [architecture and original/refactored class diagrams](hassan-svg-architecture.md).

## Code smells and corrective maintenance
Long Method (path parsing), Large Class/God Class (importer), duplicated code (line and shape attributes), complex conditional dispatch and nested CSS conditions were identified. Five functional defects were addressed separately from behavior-preserving refactoring.

| Defect | Method | Fix commit |
|---|---|---|
| DOM bootstrap and unsafe traversal | read(InputStream, Drawing, boolean) | 2df7ddd |
| Clipboard replacement ignored | read(Transferable, Drawing, boolean) | f5a93a3 |
| Unprefixed href unsupported | readUseElement | e824fc8 |
| Case-sensitive language matching | readSwitchElement | a30ef57 |
| Circular reference recursion | readUseElement | a30ef57 |

See [exact source lines, code changes and failing/passing CI evidence](hassan-svg-defects-and-coverage.md).

## Structural refactoring
Extract Class and Move Method separated SVGPathParser. Strategy-style functional dispatch replaced the element conditional chain. Extract Method separated fill, stroke and style processing. Consolidate Duplicate Conditional Fragments removed redundant paint branches. Parameterize Method unified line/shape defaults. Decompose Conditional and Replace Nested Conditional with Guard Clauses simplified CSS processing. These eight overlapping techniques were implemented across five structural commits. See [catalog and code excerpts](hassan-svg-refactorings.md).

## Quantitative evaluation
CK and PMD 7.17.0 measured baseline 4533afd and refactored 93a2051 in [CI run 21](https://github.com/shazilhamzah/JHotDraw-SRE-Project/actions/runs/45391572736).

| SVGInputFormat metric | Before | After |
|---|---:|---:|
| CK LOC | 1963 | 1566 |
| WMC | 466 | 350 |
| CBO | 24 | 27 |
| LCOM | 1435 | 1971 |
| RFC | 243 | 209 |
| DIT | 1 | 1 |
| NOC | 1 | 1 |
| PMD design violations | 124 | 67 |
| PMD CyclomaticComplexity violations | 17 | 13 |

Importer WMC fell 24.9%, LOC 20.2% and PMD design violations 46.0%. Including extracted SVGPathParser, the two top-level classes total WMC 440 and LOC 1934, reductions of 5.6% and 1.5%. CBO and LCOM worsened in the importer, and its GodClass warning remains. The importer-only PMD counts above exclude the extracted parser. The expanded comparison below includes it. See [metrics details](hassan-svg-metrics.md).

## Testing
Characterization tests were added incrementally; the bootstrap defect had to be fixed before extensive integration testing. Regression tests reproduced defects before fixes. [CI run 25](https://github.com/shazilhamzah/JHotDraw-SRE-Project/actions/runs/45392542307) verified 75 SVG integration tests, 6 importer unit tests, 4 parser unit tests and 25 other module tests: 110 executed, zero failures, errors or skips. This is the tested CI scope, not a claim of full repository coverage. Later tests verified viewport scaling, aspect ratio, inherited font size, visibility and decoded raster pixels, superseding those particular gaps in the earlier audit.

## Limitations and handoff
Exact non-pixel unit conversions, some gradient/stroke/link properties, text whitespace, clipboard append and exact geometry for some shapes/transforms remain incompletely asserted. Method-level CK complexity and PMD comparison across both assigned top-level classes are now verified in run 29. The report is a contribution for the team report maintained by Shazil, not the combined report. Final TASKS.md verification and pull request remain pending. Work is isolated on hassan-svg-refactoring; no direct main push is intended.

## Final expanded metrics verification
[CI run 29](https://github.com/shazilhamzah/JHotDraw-SRE-Project/actions/runs/45393549688), commit cf1632c, verified 110 tests with zero failures, errors or skips. CK method-level WMC (cyclomatic complexity): toPath 90 -> 1, readElement 28 -> 7, flattenStyles 12 -> 5. The extracted parser retains WMC 90. Combined top-level class WMC 466 -> 440 (5.6% reduction), LOC 1963 -> 1934 (1.5%). PMD design violations across the original importer versus both refactored files: 124 -> 116 (6.5%), CyclomaticComplexity violations 17 -> 15 (11.8%), CognitiveComplexity violations 17 -> 13, GodClass warnings 1 -> 2. Extraction redistributes complexity and does not eliminate the complex parsing algorithm. These are subsystem measurements, not whole-repository metrics. Remaining behavioral coverage gaps listed above are not resolved by this metrics run.
