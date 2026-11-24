# Hassan — SVG defects and requirements audit

Target: `jhotdraw-src/jhotdraw-samples/jhotdraw-samples-misc/src/main/java/org/jhotdraw/samples/svg/io/SVGInputFormat.java`. Line numbers below refer to the parent of each fix commit (before) and the current source at 93a2051 (after), not a single shared baseline.

| Defect / method | Before (line and code) | After (line and code) | Regression evidence |
|---|---|---|---|
| DOM bootstrap — read(InputStream, Drawing, boolean) | 195: `document = (Element) builder.parse(in);` | 187,196: namespace-aware factory; `document = builder.parse(in).getDocumentElement();` | [fix 2df7ddd](https://github.com/shazilhamzah/JHotDraw-SRE-Project/commit/2df7ddd); [failed run 3](https://github.com/shazilhamzah/JHotDraw-SRE-Project/actions/runs/45385486692), [passed run 4](https://github.com/shazilhamzah/JHotDraw-SRE-Project/actions/runs/45385841543) |
| Clipboard replacement — read(Transferable, Drawing, boolean) | 2467: `read(in, drawing, false);` | 2482: `read(in, drawing, replace);` | [fix f5a93a3](https://github.com/shazilhamzah/JHotDraw-SRE-Project/commit/f5a93a3); [run 12](https://github.com/shazilhamzah/JHotDraw-SRE-Project/actions/runs/45388381146) → [13](https://github.com/shazilhamzah/JHotDraw-SRE-Project/actions/runs/45388684169) |
| Unprefixed href — readUseElement | 961: only reads xlink:href | 965–967: fallback to href when null | [fix e824fc8](https://github.com/shazilhamzah/JHotDraw-SRE-Project/commit/e824fc8); [run 14](https://github.com/shazilhamzah/JHotDraw-SRE-Project/actions/runs/45389088326) → [15](https://github.com/shazilhamzah/JHotDraw-SRE-Project/actions/runs/45389370140) |
| Case-sensitive language — readSwitchElement | 925,930–931: equals and lowercased country compared to original tag | 925,930–931: equalsIgnoreCase for language and country | [fix a30ef57](https://github.com/shazilhamzah/JHotDraw-SRE-Project/commit/a30ef57); [run 16](https://github.com/shazilhamzah/JHotDraw-SRE-Project/actions/runs/45389777890) → [17](https://github.com/shazilhamzah/JHotDraw-SRE-Project/actions/runs/45390102147) |
| Circular use — readUseElement | 970: `Figure obj = readElement(refElem);` without cycle guard | 951–952,973–982: identity set tracks active references; skip cycles and remove in finally | same fix and runs 16–17; [run 18](https://github.com/shazilhamzah/JHotDraw-SRE-Project/actions/runs/45390412719) adds indirect cycles and reuse |

DOM parsing returns a Document, not an Element; the fix also avoids unsafe casts of whitespace/comment nodes and uses namespace URI rather than prefix. Clipboard import previously appended even when replacement was requested. Missing href fallback prevented modern unprefixed references from resolving. Language matching selected the fallback rectangle instead of the expected one. Circular references produced StackOverflowError; the active set prevents recursion while permitting repeated valid references.

Regression tests are in SVGImportIntegrationTest.java: importsEmptySvgDocument (18), importsRectangleWithWhitespaceAndComment (25), clipboardHonorsReplace (180), useResolvesUnprefixedHref (254), switchMatchesLanguageTagIgnoringCase (262), circularUseDoesNotPreventOtherFigures (278), indirectCircularUseDoesNotPreventOtherFigures (287), repeatedValidReferencesAreNotTreatedAsCycles (296), circularReferenceDoesNotBlockLaterValidReference (304). Final [run 20](https://github.com/shazilhamzah/JHotDraw-SRE-Project/actions/runs/45391062840): 80 SVG tests, 105 executed overall, all passed.

## TASKS.md audit — verified at f75c2f5

[CI run 33](https://github.com/shazilhamzah/JHotDraw-SRE-Project/actions/runs/45398839764): 84 integration, 6 importer unit, 4 parser unit and 25 existing tests; 119 total, zero failures, errors or skips. Both jobs succeeded after retrying the Maven Central HTTP 429 failure.

| Requirement | Evidence / remaining limitation |
|---|---|
| Tokenizers, filter, flavor | All named helpers exercised in SVGInputFormatTest. |
| Input modes, replace, reuse, invalid XML/root | Stream, file, URL, clipboard append and replacement, reuse and malformed/missing root tested. |
| Viewport, units, nested SVG | Uniform and independent scaling asserted; px and five physical units asserted exactly using legacy 90 DPI. Percent and em only checked for positive width. |
| CSS and inheritance | Inline paint, currentcolor and inherited font size asserted; stylesheet and inherited fill exercised but not exactly asserted. |
| Shapes and path commands | All named shapes and commands exercised; rectangle, circle and ellipse bounds asserted. Detailed geometry for other shapes and commands remains incomplete. |
| Transforms | All six functions and chained transforms exercised; exact coordinates not asserted for all. |
| Paint, gradients, links, visibility, switch, references | Stroke width, fill/stroke opacity, gradient resolution, hidden exclusion, switch selection and reference cycles asserted. Gradient stop values, dash properties, link values and solidColor values not fully asserted. |
| Text and images | All named text/image features exercised; explicit/inherited font and relative URL raster pixels asserted. Text whitespace and nested SVG image content not fully asserted. |
| Extracted parser | Four focused tests. |
| Metrics | Expanded CK/PMD results in docs/hassan-svg-report.md, run 29. |
| Architecture, patterns, refactoring catalog | Documented in docs/hassan-svg-architecture.md and docs/hassan-svg-refactorings.md. |
| PR | Pending review submission. |

All named feature categories have tests, but exhaustive behavioral and edge-case coverage is not established. No coverage percentage is claimed. The earlier run 20 evidence above is historical.
