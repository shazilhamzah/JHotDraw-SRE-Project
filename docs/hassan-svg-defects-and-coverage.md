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

## TASKS.md audit

| Requirement | Evidence / limitation |
|---|---|
| Tokenizers, filter, flavor | SVGInputFormatTest covers all named helpers; chained and malformed transform tests also present. |
| Input modes, replace, reuse, invalid XML/root | Integration tests cover stream, file, URL, clipboard replacement, stream append and reuse, malformed and missing root. Clipboard append not explicitly asserted. |
| Viewport, units, nested SVG | Nested SVG with viewBox and all eight requested units exercised; pixel geometry asserted exactly. Explicit preserveAspectRatio and exact non-pixel conversions remain gaps. |
| CSS and inheritance | Stylesheet, inline style, inherit, currentcolor exercised; inline paint and currentcolor asserted. Exact inherited font and stylesheet values remain gaps. |
| Shapes and path commands | All seven shape types and all listed uppercase/lowercase path commands exercised; rectangle geometry asserted. Most other assertions count figures rather than verify geometry. |
| Transforms | All six functions and chained transforms exercised; not all exact transformed coordinates asserted. |
| Paint, gradients, links, visibility, switch, references | Gradient types, solidColor, opacity, stroke dash, links, display, switch feature/language, id/xml:id and both href forms exercised. Exact gradient/stroke/link properties and visibility=hidden not explicitly asserted. |
| Text and images | text, textArea, tspan, xml:space, explicit font size, Base64, relative raster/nested SVG, missing raster exercised. Image content and text whitespace not asserted; successful relative URL image loading not explicitly checked. |
| Extracted parser | SVGPathParserTest covers separate subpaths, empty input, missing coordinate and reuse. |
| Metrics | docs/hassan-svg-metrics.md; PMD scope limitation documented. |
| Architecture, patterns, refactoring catalog, PR | Pending. |

Passing tests are not proof of exhaustive behavioral coverage. Several feature tests assert only figure counts; remaining gaps should be addressed or disclosed before final submission.
