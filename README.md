# JHotDraw-Refactoring

Software re-engineering project on [JHotDraw](https://github.com/wumpz/jhotdraw) 10.5-SNAPSHOT (Java 17, Maven). Each team member takes one or more of the largest classes in the code base, measures them, documents code smells and defects, writes characterization tests, refactors in small catalog steps, and measures again.

The task split, test plan and Git workflow are in [TASKS.md](TASKS.md).

## Repository layout

| Path | Contents |
|---|---|
| `jhotdraw-src/` | JHotDraw multi-module Maven project (production code and tests) |
| `docs/shazil/` | `DefaultDrawingView` re-engineering: metrics, smells and defects, refactoring log, testing |
| `docs/hassan/` | `SVGInputFormat` re-engineering: architecture, defects and coverage, refactorings, metrics, report |
| `.github/workflows/` | CI workflow used for the SVG importer tests and CK/PMD metrics |
| `TASKS.md` | Task split, per-member test plan, deadlines |

## Status

| Member | Target | Status | Documentation |
|---|---|---|---|
| Shazil | `jhotdraw-core` `DefaultDrawingView` | Done, on `main` | [docs/shazil](docs/shazil/README.md) |
| Hassan | `jhotdraw-samples-misc` `SVGInputFormat` | Done, merged via PR #1 | [docs/hassan](docs/hassan/hassan-svg-report.md) |
| Muhammad | `SVGOutputFormat`, `ImageMapOutputFormat`, `ButtonFactory` | In progress | — |

### Results so far

**`DefaultDrawingView`** (Shazil): split into a Swing facade plus five package-private collaborators (`DrawingViewBuffer`, `DrawingViewViewport`, `DrawingViewHandleManager`, `DrawingViewSelectionManager`, `DrawingViewEditCommands`). WMC 237 → 138, max cyclomatic complexity 22 → 5, class SLOC 1,185 → 675, PMD violations 5 → 0. 12 defects fixed, each with a regression test that fails on the original class. `jhotdraw-core` tests: 7 → 50.

**`SVGInputFormat`** (Hassan): path parsing extracted into `SVGPathParser`, element dispatch replaced by a reader map, and duplicated fill/stroke handling consolidated. WMC 466 → 350, CK LOC 1,963 → 1,566, PMD design violations 124 → 67. Fixes include the DOM bootstrap defect that stopped any SVG from loading on JDK 17, clipboard `replace`, unprefixed `href`, case-insensitive language matching, circular `<use>` references, and a null `inherit` lookup on the root element. SVG module tests: 0 → 110.

## Building and testing

Requires JDK 17 and Maven 3.9+. A portable JDK and Maven can be unpacked into `tools/` (ignored by Git).

```bash
cd jhotdraw-src
mvn test -Djava.awt.headless=true
```

The build runs Spotless formatting and Checkstyle on main and test sources. To run a single module and its dependencies:

```bash
mvn -pl jhotdraw-samples/jhotdraw-samples-misc -am test -Djava.awt.headless=true
```

Current full build: 178 tests, 0 failures, 0 Checkstyle violations.

## Git workflow

Work on a feature branch and open a pull request into `main`. Only Shazil pushes directly to `main`.

## Deadlines

- Code and report: 30 Nov
- Presentation: last week of classes
