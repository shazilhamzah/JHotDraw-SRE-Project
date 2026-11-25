# Hassan — SVG import refactoring catalog

Target: SVGInputFormat.java in org.jhotdraw.samples.svg.io. The following eight catalog techniques overlap across five structural commits; they are not eight independent commits. Functional defect fixes are documented separately.

| Technique | Before | After | Evidence |
|---|---|---|---|
| Extract Class | Path parsing inside SVGInputFormat.toPath | SVGPathParser owns path parsing and tokenizer | [d3c4e1d](https://github.com/shazilhamzah/JHotDraw-SRE-Project/commit/d3c4e1d) |
| Move Method | Parsing algorithm in toPath | Algorithm moved to SVGPathParser.parse; importer delegates | d3c4e1d |
| Replace Conditional with Polymorphism (strategy dispatch variant) | readElement uses long if/else chain | ElementReader functional interface and map of method references | [08825fa](https://github.com/shazilhamzah/JHotDraw-SRE-Project/commit/08825fa) |
| Extract Method | readShapeAttributes handles fill, stroke paint and style | readFillAttributes, readStrokePaint, readStrokeStyleAttributes | [ec548b2](https://github.com/shazilhamzah/JHotDraw-SRE-Project/commit/ec548b2) |
| Consolidate Conditional Expression / duplicate conditional fragments | Null and fallback branches both set paint color to null | Single fallback else branch (more precisely Consolidate Duplicate Conditional Fragments) | ec548b2 |
| Parameterize Method | Separate line and shape paint defaults in duplicated code | Shared helpers accept defaultFill and defaultStroke | [8d1f584](https://github.com/shazilhamzah/JHotDraw-SRE-Project/commit/8d1f584) |
| Decompose Conditional | Inline compound CSS style condition | isCssStyleElement(elem), applyInlineStyle(elem) | [089f308](https://github.com/shazilhamzah/JHotDraw-SRE-Project/commit/089f308) |
| Replace Nested Conditional with Guard Clauses | Nested SVG and style checks | Early returns in flattenStyles and applyInlineStyle | 089f308 |

## Before and after excerpts

### Extract Class / Move Method
Before: toPath contains tokenizer setup, command switch and geometry construction. After, the actual delegation is:
```java
private BezierPath[] toPath(Element elem, String str) throws IOException {
  return pathParser.parse(str);
}
```
The parser remains internally complex; extraction separates responsibilities rather than eliminating all complexity.

### Strategy dispatch
Before:
```java
} else if ("circle".equals(name)) {
  f = readCircleElement(elem);
```
After (selected lines):
```java
ElementReader reader = elementReaders.get(name);
if (reader == null) {
  LOG.info("SVGInputFormat not implemented for <" + name + ">");
  return null;
}
Figure f = reader.read(elem);
```
This uses functional strategies, not a hierarchy of concrete strategy classes.

### Extract Method / Parameterize Method / remove duplication
Before: readLineAttributes and readShapeAttributes each parse paint, opacity and stroke styles independently. After:
```java
readFillAttributes(elem, a, "none");
readStrokePaint(elem, a, "black");
readStrokeStyleAttributes(elem, a);
```
Shape defaults remain black fill and no stroke. Line defaults remain no fill and black stroke.

### Consolidate Duplicate Conditional Fragments
Before:
```java
} else if (objectValue == null) {
  FILL_COLOR.put(a, null);
} else {
  FILL_COLOR.put(a, null);
}
```
After:
```java
} else {
  FILL_COLOR.put(a, null);
}
```

### Decompose Conditional / Guard Clauses
Before: flattenStyles checks local name, type and text content inline, then nests SVG and style checks. After (selected lines):
```java
if (isCssStyleElement(elem)) {
  CSSParser cssParser = new CSSParser();
  cssParser.parse(elem.getTextContent(), styleManager);
  return;
}
if (!isSvgElement(elem)) {
  return;
}
applyInlineStyle(elem);
```

## Verification
[CI run 25](https://github.com/shazilhamzah/JHotDraw-SRE-Project/actions/runs/45392542307): 75 integration tests, 6 importer unit tests, 4 parser tests, 25 other module tests. All 110 executed tests passed with no failures, errors or skips. The five new tests assert viewport scaling, independent aspect ratio scaling, font inheritance, hidden element exclusion and raster pixels. Run 24's two geometry assertion failures were corrected by applying the figure transform to local bounds; expected dimensions were unchanged. Exact unit conversions and clipboard append were asserted in later runs (see the final verification in hassan-svg-defects-and-coverage.md); remaining gaps include some gradient/stroke properties and text whitespace. See metrics and defects documents for scope and limitations.
