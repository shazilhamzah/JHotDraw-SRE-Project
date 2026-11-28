# Muhammad – SVG export + GUI action (JHotDraw re-engineering)

Branch: `muhammad-svg-export-gui`. Targets: `SVGOutputFormat`, `ImageMapOutputFormat`, `ButtonFactory`.

## 1. Metrics (CK 0.7.0, whole class)

| Class | WMC before → after | CBO | LCOM | LOC |
|---|---|---|---|---|
| SVGOutputFormat | 158 → 149 | 24 → 24 | 1160 → 1374 | 820 → 688 |
| ImageMapOutputFormat | 82 → 83 | 22 → 22 | 280 → 280 | 350 → 353 |
| ButtonFactory | 126 → 128 | 52 → 52 | 2838 → 2914 | 930 → 909 |

CK's WMC is the sum of per-method cyclomatic complexity. Reading the numbers honestly:
- SVGOutputFormat: WMC -9 and LOC -132 come from the `toPath` and gradient extractions.
- LCOM rose for SVGOutputFormat and ButtonFactory because the extractions added small static helper methods that touch no fields. CK counts those as unrelated method pairs. This is a metric artifact of Extract Method, not lost cohesion.
- ButtonFactory WMC +2: the new helper adds a method and the callers keep their own methods.
- ImageMapOutputFormat: only a bug fix (empty-list guard), no structural refactoring. Its WMC +1 comes from the new guard.

## 2. Defects (original line numbers)

| # | File / method | Line(s) | Evidence | Status |
|---|---|---|---|---|
| 1 | SVGOutputFormat.write | 1334–1348 | Prolog written through a PrintWriter flushed after the transformer: output has a trailing stray `<?xml?>` (not well-formed) | Fixed |
| 2 | SVGOutputFormat.createTextArea | 582 | `createElement(null)` – every textArea export fails | Fixed (round 2) |
| 3 | SVGOutputFormat.toPath | 1015–1018 | Single node path wrote `M 5 65 7` (missing ` L `) | Fixed (round 2) |
| 4 | SVGOutputFormat.initStorageContext | 1351–1354 | `nextId` never reset: ids differ per write | Fixed |
| 5 | SVGOutputFormat.writeElement | 125 | Checks `LINK` instead of `LINK_TARGET` for `target` | Fixed |
| 6 | SVGOutputFormat.write | 1318 | Logs under `ImageMapOutputFormat.class` (cross-file) | Fixed |
| 7 | SVGOutputFormat.toPath | 1093–1159 | Subpaths concatenated without separator (`1 1M 5 5`) | Documented, not fixed |
| 8 | ImageMapOutputFormat.write(OutputStream,List) | 163–176 | Empty list -> NPE (`drawingRect` null) | Fixed (round 2) |
| 9 | ImageMapOutputFormat | 61 | `isIncludeNohref` has no setter; nohref areas always stripped (dead feature) | Documented |
| 10 | ImageMapOutputFormat.writePathElement | 372–386 | Ignores in-bounds result; curved/multi paths never filtered | Documented |
| 11 | ButtonFactory.createStrokeWidthButton(editor,double[]) | 1430–1435 | Ignores `widths` argument | Fixed |
| 12 | ButtonFactory.createDrawingColorChooserButton | 1328ff | Delegates to the *Selection* variant (copy-paste) | Fixed |
| 13 | ButtonFactory.addAlignmentButtonsTo | 1778–1781 | Wrong object added to `dsp`; BringToFront/SendToBack untracked | Fixed |
| 14 | ButtonFactory.createFontStyle{Bold,Italic,Underline}Button | 1684, 1708, 1732 | `dsp` parameter never used | Documented |

Code smells: duplicated fill/stroke gradient code (SVGOutputFormat 632–751), duplicated linear/closed `toPath` segment logic (1031–1155), duplicated figure-type dispatch in SVGOutputFormat.writeElement and ImageMapOutputFormat.writeElement (cross-file), long methods, God class (ButtonFactory, 1,926 lines, CBO 52), `System.out.println` as error handling, unused `NumberFormat formatter` in createStrokeWidthButton.

## 3. Refactorings

| Refactoring | Problem | Change | Evidence |
|---|---|---|---|
| Extract Method + Remove Duplication | Gradient creation copy-pasted for fill and stroke | `SVGOutputFormat.getGradientId` | gradient dedupe tests |
| Extract Method + Remove Duplication | `toPath` repeated segment logic for loop and closing segment | `appendSubpath`, `appendSegment`, `appendPoint` | all `toPath*` tests |
| Extract Method + Remove Duplication | Cap and join buttons duplicated | `ButtonFactory.createIntAttributePopupButton` | stroke cap/join tests |
| Bug fixes (behavior changes, tests flipped) | Defects 1–6, 8, 11–13 | see table | former "Characterizes defect" tests now assert fixed behavior |

## 4. Tests

- Added: `SVGOutputFormatTest` (35), `ImageMapOutputFormatTest` (17), `ButtonFactoryTest` (25), `ButtonFactoryIntegrationTest` (7). Existing tests untouched.
- Full run after the `toPath` and `ButtonFactory` refactorings: BUILD SUCCESS (reported by the author; last counts seen: jhotdraw-gui 32 tests, jhotdraw-samples-misc 162 tests, 0 failures).
- Round-trip SVGOutputFormat -> SVGInputFormat tests: not written.

## 5. Reproduce

```
cd jhotdraw-src
mvn -pl jhotdraw-gui,jhotdraw-samples/jhotdraw-samples-misc -am test
java -jar ..\metrics\ck.jar <dir-with-the-3-java-files> false 0 false <out-dir>\
```

## 6. Incomplete

Round-trip tests; defects 7, 9, 10, 14 left documented only; ButtonFactory remains large (the colour and stroke buttons are still duplicated).
