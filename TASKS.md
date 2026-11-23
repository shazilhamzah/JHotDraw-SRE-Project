# JHotDraw Re-engineering - Task Split

Project: `wumpz/jhotdraw` (10.5-SNAPSHOT, Java 17, Maven). Scope about 9-10k lines.
Build: portable JDK 17 + Maven in `tools/`. `mvn -DskipTests compile` passes.
Existing tests: 6 files, none cover our classes. We write characterization tests first.

## Everyone (each on own classes)
1. Run metrics before (SonarQube / PMD / CK): WMC, CBO, LCOM, LOC, complexity.
2. List 5+ defects with file and line evidence.
3. Write characterization tests (same input gives same output).
4. Refactor using named catalog refactorings. Re-run tests.
5. Re-run metrics after. Write before/after section of report.

## Hassan - SVG import
- `svg/io/SVGInputFormat.java` (3,185 lines, biggest class in repo)
- Likely: God Class, Long Method, conditional chains.
- Refactors: Extract Class, Decompose Conditional, Replace Conditional with Strategy.
- Also: architecture diagram + patterns found (Phase 1).

## Muhammad - SVG export + GUI action
- `svg/io/SVGOutputFormat.java` (1,375), `ImageMapOutputFormat.java` (500)
- `gui/action/ButtonFactory.java` (1,926)
- Refactors: Extract Class, Replace Constructor with Factory, Pull Up Method.

## Shazil - Core view + report
- `core/draw/DefaultDrawingView.java` (1,550)
- Refactors: Extract Class, Replace Type Code with State/Strategy, Remove Middle Man.
- Also: final report, slides, metrics tables, presentation (10 min).

## Deadlines
- Code and report: 30 Nov
- Presentation: last week of classes
