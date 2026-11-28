package org.jhotdraw.samples.svg.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jhotdraw.samples.svg.SVGAttributeKeys.*;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.jhotdraw.draw.AttributeKeys.WindingRule;
import org.jhotdraw.draw.DefaultDrawing;
import org.jhotdraw.draw.Drawing;
import org.jhotdraw.draw.figure.Figure;
import org.jhotdraw.draw.figure.RectangleFigure;
import org.jhotdraw.samples.svg.LinearGradient;
import org.jhotdraw.samples.svg.figures.SVGBezierFigure;
import org.jhotdraw.samples.svg.figures.SVGEllipseFigure;
import org.jhotdraw.samples.svg.figures.SVGGroupFigure;
import org.jhotdraw.samples.svg.figures.SVGPathFigure;
import org.jhotdraw.samples.svg.figures.SVGRectFigure;
import org.jhotdraw.samples.svg.figures.SVGTextAreaFigure;
import org.jhotdraw.samples.svg.figures.SVGTextFigure;
import org.jhotdraw.utils.geom.path.BezierPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/** Characterization tests for {@link SVGOutputFormat}. */
class SVGOutputFormatTest {

  // ---------- helpers ----------
  private static String write(Drawing d) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    new SVGOutputFormat().write(out, d);
    return out.toString(StandardCharsets.UTF_8);
  }

  private static Drawing drawingOf(Figure... figures) {
    Drawing d = new DefaultDrawing();
    for (Figure f : figures) {
      d.add(f);
    }
    return d;
  }

  /** Parses the SVG part of the output (the output has a stray trailing XML prolog). */
  private static Document parse(String xml) throws Exception {
    String svg = xml.substring(0, xml.indexOf("</svg>") + "</svg>".length());
    return DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .parse(new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8)));
  }

  private static Element first(Document doc, String tag) {
    assertThat(doc.getElementsByTagName(tag).getLength()).as("<%s> count", tag).isPositive();
    return (Element) doc.getElementsByTagName(tag).item(0);
  }

  private static BezierPath.Node node(double x, double y) {
    return new BezierPath.Node(x, y);
  }

  private static SVGBezierFigure bezier(boolean closed, BezierPath.Node... nodes) {
    SVGBezierFigure b = new SVGBezierFigure(closed);
    for (BezierPath.Node n : nodes) {
      b.addNode(n);
    }
    return b;
  }

  private static BezierPath[] paths(SVGBezierFigure... figs) {
    BezierPath[] p = new BezierPath[figs.length];
    for (int i = 0; i < p.length; i++) {
      p[i] = figs[i].getBezierPath();
    }
    return p;
  }

  private static SVGPathFigure pathFigure(SVGBezierFigure... children) {
    SVGPathFigure p = new SVGPathFigure();
    while (p.getChildCount() > 0) {
      p.removeChild(0);
    }
    for (SVGBezierFigure c : children) {
      p.add(c);
    }
    return p;
  }

  // ---------- toNumber ----------
  @Test
  void toNumberStripsTrailingZero() {
    assertThat(SVGOutputFormat.toNumber(1.0)).isEqualTo("1");
    assertThat(SVGOutputFormat.toNumber(0)).isEqualTo("0");
    assertThat(SVGOutputFormat.toNumber(-3.0)).isEqualTo("-3");
    assertThat(SVGOutputFormat.toNumber(1.5)).isEqualTo("1.5");
    assertThat(SVGOutputFormat.toNumber(0.1)).isEqualTo("0.1");
  }

  @Test
  void toNumberUsesFloatPrecisionAndScientificNotation() {
    assertThat(SVGOutputFormat.toNumber(1.23456789012345)).isEqualTo("1.2345679");
    assertThat(SVGOutputFormat.toNumber(1e-5)).isEqualTo("1.0E-5");
    assertThat(SVGOutputFormat.toNumber(1e8)).isEqualTo("1.0E8");
    assertThat(SVGOutputFormat.toNumber(-0.0)).isEqualTo("-0");
  }

  // ---------- toColor ----------
  @Test
  void toColorCases() {
    assertThat(SVGOutputFormat.toColor(null)).isEqualTo("none");
    assertThat(SVGOutputFormat.toColor(Color.RED)).isEqualTo("#f00");
    assertThat(SVGOutputFormat.toColor(Color.BLACK)).isEqualTo("#000");
    assertThat(SVGOutputFormat.toColor(Color.WHITE)).isEqualTo("#fff");
    assertThat(SVGOutputFormat.toColor(new Color(0x12, 0x34, 0x56))).isEqualTo("#123456");
    assertThat(SVGOutputFormat.toColor(new Color(0x11, 0x22, 0x33))).isEqualTo("#123");
    assertThat(SVGOutputFormat.toColor(new Color(0, 0, 0x0a))).isEqualTo("#00000a");
  }

  @Test
  void toColorIgnoresAlpha() {
    assertThat(SVGOutputFormat.toColor(new Color(255, 0, 0, 10))).isEqualTo("#f00");
  }

  // ---------- toPoints ----------
  @Test
  void toPointsCases() throws IOException {
    assertThat(SVGOutputFormat.toPoints(new Point2D.Double[0])).isEmpty();
    assertThat(SVGOutputFormat.toPoints(new Point2D.Double[] {new Point2D.Double(1, 2)}))
        .isEqualTo("1,2");
    assertThat(SVGOutputFormat.toPoints(
            new Point2D.Double[] {new Point2D.Double(1, 2), new Point2D.Double(3.5, -4)}))
        .isEqualTo("1,2, 3.5,-4");
  }

  // ---------- toTransform ----------
  @Test
  void toTransformCases() throws IOException {
    assertThat(SVGOutputFormat.toTransform(new AffineTransform())).isEqualTo("none");
    assertThat(SVGOutputFormat.toTransform(AffineTransform.getTranslateInstance(5, 0)))
        .isEqualTo("translate(5)");
    assertThat(SVGOutputFormat.toTransform(AffineTransform.getTranslateInstance(5, 6)))
        .isEqualTo("translate(5 6)");
    assertThat(SVGOutputFormat.toTransform(AffineTransform.getScaleInstance(2, 2)))
        .isEqualTo("scale(2)");
    assertThat(SVGOutputFormat.toTransform(AffineTransform.getScaleInstance(2, 3)))
        .isEqualTo("scale(2 3)");
    assertThat(SVGOutputFormat.toTransform(AffineTransform.getRotateInstance(Math.PI / 2)))
        .isEqualTo("matrix(0 1 -1 0 0 0)");
    AffineTransform st = AffineTransform.getTranslateInstance(3, 4);
    st.scale(2, 2);
    assertThat(SVGOutputFormat.toTransform(st)).isEqualTo("matrix(2 0 0 2 3 4)");
  }

  // ---------- toPath ----------
  @Test
  void toPathEmptyAndNoPaths() {
    assertThat(SVGOutputFormat.toPath(new BezierPath[0])).isEmpty();
    assertThat(SVGOutputFormat.toPath(paths(bezier(false)))).isEmpty();
  }

  @Test
  void toPathSingleNodeHasMissingSeparatorDefect() {
    // Characterizes defect: the " L " separator is commented out, so coordinates run together.
    assertThat(SVGOutputFormat.toPath(paths(bezier(false, node(5, 6))))).isEqualTo("M 5 6 L 5 7");
  }

  @Test
  void toPathLinesOpenAndClosed() {
    assertThat(SVGOutputFormat.toPath(paths(bezier(false, node(0, 0), node(10, 0)))))
        .isEqualTo("M 0 0 10 0");
    assertThat(SVGOutputFormat.toPath(paths(bezier(true, node(0, 0), node(10, 0), node(10, 10)))))
        .isEqualTo("M 0 0 10 0 10 10");
  }

  @Test
  void toPathQuadraticAndCubicSegments() {
    BezierPath.Node q = new BezierPath.Node(BezierPath.C1_MASK, 10, 10, 5, 0, 10, 10);
    assertThat(SVGOutputFormat.toPath(paths(bezier(false, node(0, 0), q))))
        .isEqualTo("M 0 0 Q 5 0 10 10");
    BezierPath.Node a = new BezierPath.Node(BezierPath.C2_MASK, 0, 0, 0, 0, 3, 0);
    BezierPath.Node b = new BezierPath.Node(BezierPath.C1_MASK, 10, 10, 7, 10, 10, 10);
    assertThat(SVGOutputFormat.toPath(paths(bezier(false, a, b))))
        .isEqualTo("M 0 0 C 3 0 7 10 10 10");
    BezierPath.Node c1 = new BezierPath.Node(BezierPath.C2_MASK, 0, 0, 0, 0, 3, 0);
    assertThat(SVGOutputFormat.toPath(paths(bezier(false, c1, node(10, 10)))))
        .isEqualTo("M 0 0 Q 3 0 10 10");
  }

  @Test
  void toPathMultipleSubpaths() {
    String s = SVGOutputFormat.toPath(paths(
        bezier(true, node(0, 0), node(1, 0), node(1, 1)), bezier(false, node(5, 5), node(6, 6))));
    assertThat(s).isEqualTo("M 0 0 1 0 1 1M 5 5 6 6");
  }

  // ---------- metadata ----------
  @Test
  void metadata() {
    SVGOutputFormat f = new SVGOutputFormat();
    assertThat(f.getFileExtension()).isEqualTo("svg");
    assertThat(f.getFileFilter().getDescription()).isEqualTo("Scalable Vector Graphics (SVG)");
    assertThat(f.getFileFilter().accept(new File("a.svg"))).isTrue();
    assertThat(f.getFileFilter().accept(new File("a.png"))).isFalse();
    assertThat(f.getOutputFormatAccessory()).isNull();
    assertThat(f.isPrettyPrint()).isFalse();
    f.setPrettyPrint(true);
    assertThat(f.isPrettyPrint()).isTrue();
  }

  @Test
  void prettyPrintAddsIndentation() throws IOException {
    SVGOutputFormat f = new SVGOutputFormat();
    Drawing d = drawingOf(new SVGRectFigure(0, 0, 10, 10));
    ByteArrayOutputStream plain = new ByteArrayOutputStream();
    f.write(plain, d);
    f.setPrettyPrint(true);
    ByteArrayOutputStream pretty = new ByteArrayOutputStream();
    f.write(pretty, d);
    assertThat(pretty.size()).isGreaterThan(plain.size());
  }

  // ---------- write overloads ----------
  @Test
  void writeStreamProducesSvgRoot() throws Exception {
    String xml = write(drawingOf());
    Element svg = parse(xml).getDocumentElement();
    assertThat(svg.getTagName()).isEqualTo("svg");
    assertThat(svg.getAttribute("version")).isEqualTo("1.2");
    assertThat(svg.getAttribute("baseProfile")).isEqualTo("tiny");
    assertThat(svg.getAttribute("xmlns:xlink")).isEqualTo("http://www.w3.org/1999/xlink");
    assertThat(svg.getElementsByTagName("defs").getLength()).isEqualTo(1);
  }

  @Test
  void writeEmitsPrologTwiceAndTrailingPrologDefect() throws IOException {
    // Characterizes defect: PrintWriter prolog is flushed AFTER the transformer output.
    String xml = write(drawingOf());
    assertThat(xml).startsWith("<?xml");
    assertThat(xml).endsWith("</svg>");
    assertThat(xml.split("<\\?xml", -1)).hasSize(2);
  }

  @Test
  void writeFileAndUri(@TempDir File dir) throws Exception {
    Drawing d = drawingOf(new SVGRectFigure(1, 2, 3, 4));
    File f1 = new File(dir, "a.svg");
    new SVGOutputFormat().write(f1, d);
    assertThat(Files.readString(f1.toPath())).contains("<rect");
    File f2 = new File(dir, "b.svg");
    new SVGOutputFormat().write(f2.toURI(), d);
    assertThat(Files.readString(f2.toPath())).contains("<rect");
  }

  @Test
  void writeFigureSubsetOnlyWritesGivenFigures() throws Exception {
    SVGRectFigure r1 = new SVGRectFigure(0, 0, 10, 10);
    SVGEllipseFigure e = new SVGEllipseFigure(0, 0, 10, 20);
    Drawing d = drawingOf(r1, e);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    new SVGOutputFormat().write(out, d, List.<Figure>of(e));
    String xml = out.toString(StandardCharsets.UTF_8);
    assertThat(xml).contains("<ellipse").doesNotContain("<rect");
  }

  @Test
  void createTransferableContainsSvg() throws Exception {
    SVGRectFigure r = new SVGRectFigure(0, 0, 10, 10);
    Drawing d = drawingOf(r);
    Transferable t = new SVGOutputFormat().createTransferable(d, List.<Figure>of(r), 1.0);
    DataFlavor[] flavors = t.getTransferDataFlavors();
    assertThat(flavors).hasSize(1);
    assertThat(flavors[0].getHumanPresentableName()).isEqualTo("Image SVG");
    String data = new String(
        ((InputStream) t.getTransferData(flavors[0])).readAllBytes(), StandardCharsets.UTF_8);
    assertThat(data).contains("<rect");
  }

  // ---------- figure types ----------
  @Test
  void rectElementAndDefaultOmission() throws Exception {
    Element e = first(parse(write(drawingOf(new SVGRectFigure(10, 20, 30, 40)))), "rect");
    assertThat(e.getAttribute("x")).isEqualTo("10");
    assertThat(e.getAttribute("y")).isEqualTo("20");
    assertThat(e.getAttribute("width")).isEqualTo("30");
    assertThat(e.getAttribute("height")).isEqualTo("40");
    for (String a : List.of("rx", "ry", "opacity", "transform", "stroke-width", "fill-opacity")) {
      assertThat(e.hasAttribute(a)).as(a).isFalse();
    }
  }

  @Test
  void rectOriginIsOmitted() throws Exception {
    Element e = first(parse(write(drawingOf(new SVGRectFigure(0, 0, 5, 5)))), "rect");
    assertThat(e.hasAttribute("x")).isFalse();
    assertThat(e.hasAttribute("y")).isFalse();
  }

  @Test
  void rectRoundedCorners() throws Exception {
    Element e = first(parse(write(drawingOf(new SVGRectFigure(0, 0, 10, 10, 3, 4)))), "rect");
    assertThat(e.getAttribute("rx")).isEqualTo("3");
    assertThat(e.getAttribute("ry")).isEqualTo("4");
  }

  @Test
  void shapeAttributesWrittenWhenNonDefault() throws Exception {
    SVGRectFigure r = new SVGRectFigure(0, 0, 10, 10);
    FILL_COLOR.set(r, Color.RED);
    STROKE_COLOR.set(r, Color.BLUE);
    STROKE_WIDTH.set(r, 2d);
    STROKE_OPACITY.set(r, 0.5);
    FILL_OPACITY.set(r, 0.25);
    STROKE_DASHES.set(r, new double[] {4, 2});
    STROKE_CAP.set(r, BasicStroke.CAP_ROUND);
    STROKE_JOIN.set(r, BasicStroke.JOIN_BEVEL);
    WINDING_RULE.set(r, WindingRule.EVEN_ODD);
    OPACITY.set(r, 0.5);
    TRANSFORM.set(r, AffineTransform.getTranslateInstance(5, 6));
    Element e = first(parse(write(drawingOf(r))), "rect");
    assertThat(e.getAttribute("fill")).isEqualTo("#f00");
    assertThat(e.getAttribute("stroke")).isEqualTo("#00f");
    assertThat(e.getAttribute("stroke-width")).isEqualTo("2");
    assertThat(e.getAttribute("stroke-opacity")).isEqualTo("0.5");
    assertThat(e.getAttribute("fill-opacity")).isEqualTo("0.25");
    assertThat(e.getAttribute("stroke-dasharray")).isEqualTo("4,2");
    assertThat(e.getAttribute("stroke-linecap")).isEqualTo("round");
    assertThat(e.getAttribute("stroke-linejoin")).isEqualTo("bevel");
    assertThat(e.getAttribute("fill-rule")).isEqualTo("evenodd");
    assertThat(e.getAttribute("opacity")).isEqualTo("0.5");
    assertThat(e.getAttribute("transform")).isEqualTo("translate(5 6)");
  }

  @Test
  void ellipseWithEqualSidesBecomesCircle() throws Exception {
    Document doc = parse(write(drawingOf(new SVGEllipseFigure(10, 20, 30, 30))));
    Element c = first(doc, "circle");
    assertThat(c.getAttribute("cx")).isEqualTo("25");
    assertThat(c.getAttribute("cy")).isEqualTo("35");
    assertThat(c.getAttribute("r")).isEqualTo("15");
    assertThat(doc.getElementsByTagName("ellipse").getLength()).isZero();
  }

  @Test
  void ellipseWithDifferentSides() throws Exception {
    Element e = first(parse(write(drawingOf(new SVGEllipseFigure(0, 0, 40, 20)))), "ellipse");
    assertThat(e.getAttribute("cx")).isEqualTo("20");
    assertThat(e.getAttribute("cy")).isEqualTo("10");
    assertThat(e.getAttribute("rx")).isEqualTo("20");
    assertThat(e.getAttribute("ry")).isEqualTo("10");
  }

  @Test
  void pathFigureVariants() throws Exception {
    Document line = parse(write(drawingOf(pathFigure(bezier(false, node(0, 0), node(10, 5))))));
    Element l = first(line, "line");
    assertThat(l.getAttribute("x2")).isEqualTo("10");
    assertThat(l.getAttribute("y2")).isEqualTo("5");

    Document polyline =
        parse(write(drawingOf(pathFigure(bezier(false, node(0, 0), node(10, 0), node(10, 10))))));
    assertThat(first(polyline, "polyline").getAttribute("points")).isEqualTo("0,0, 10,0, 10,10");

    Document polygon =
        parse(write(drawingOf(pathFigure(bezier(true, node(0, 0), node(10, 0), node(10, 10))))));
    assertThat(first(polygon, "polygon").getAttribute("points")).isEqualTo("0,0, 10,0, 10,10");

    BezierPath.Node q = new BezierPath.Node(BezierPath.C1_MASK, 10, 10, 5, 0, 10, 10);
    Document curve = parse(write(drawingOf(pathFigure(bezier(false, node(0, 0), q)))));
    assertThat(first(curve, "path").getAttribute("d")).isEqualTo("M 0 0 Q 5 0 10 10");

    Document multi = parse(write(drawingOf(
        pathFigure(bezier(false, node(0, 0), node(1, 1)), bezier(false, node(5, 5), node(6, 6))))));
    assertThat(first(multi, "path").getAttribute("d")).isEqualTo("M 0 0 1 1M 5 5 6 6");
  }

  @Test
  void textElement() throws Exception {
    SVGTextFigure t = new SVGTextFigure();
    t.setText("hello");
    Element e = first(parse(write(drawingOf(t))), "text");
    assertThat(e.getTextContent()).isEqualTo("hello");
  }

  @Test
  void textAreaIsWritten() throws Exception {
    // Characterizes defect: createTextArea calls createElement(null).
    SVGTextAreaFigure t = new SVGTextAreaFigure();
    t.setText("a\nb");
    t.setBounds(new Point2D.Double(0, 0), new Point2D.Double(50, 50));
    assertThat(write(drawingOf(t))).contains("<textArea");
  }

  @Test
  void groupWritesChildrenInsideG() throws Exception {
    SVGGroupFigure g = new SVGGroupFigure();
    g.add(new SVGRectFigure(0, 0, 5, 5));
    g.add(new SVGEllipseFigure(0, 0, 4, 8));
    OPACITY.set(g, 0.5);
    Element ge = first(parse(write(drawingOf(g))), "g");
    assertThat(ge.getAttribute("opacity")).isEqualTo("0.5");
    assertThat(ge.getElementsByTagName("rect").getLength()).isEqualTo(1);
    assertThat(ge.getElementsByTagName("ellipse").getLength()).isEqualTo(1);
  }

  @Test
  void unsupportedFigureIsSkipped() throws Exception {
    Document doc = parse(write(drawingOf(new RectangleFigure(0, 0, 5, 5))));
    assertThat(doc.getElementsByTagName("rect").getLength()).isZero();
  }

  // ---------- links ----------
  @Test
  void linkWrapsElementInAnchor() throws Exception {
    SVGRectFigure r = new SVGRectFigure(0, 0, 5, 5);
    LINK.set(r, "http://example.org");
    LINK_TARGET.set(r, "_blank");
    Element a = first(parse(write(drawingOf(r))), "a");
    assertThat(a.getAttribute("xlink:href")).isEqualTo("http://example.org");
    assertThat(a.getAttribute("target")).isEqualTo("_blank");
    assertThat(a.getElementsByTagName("rect").getLength()).isEqualTo(1);
  }

  @Test
  void blankLinkIsIgnored() throws Exception {
    SVGRectFigure r = new SVGRectFigure(0, 0, 5, 5);
    LINK.set(r, "   ");
    assertThat(parse(write(drawingOf(r))).getElementsByTagName("a").getLength()).isZero();
  }

  // ---------- viewport ----------
  @Test
  void viewportAttributes() throws Exception {
    Drawing d = drawingOf();
    d.attr().set(VIEWPORT_WIDTH, 200d);
    d.attr().set(VIEWPORT_HEIGHT, 100d);
    d.attr().set(VIEWPORT_FILL, Color.RED);
    d.attr().set(VIEWPORT_FILL_OPACITY, 0.5);
    Element svg = parse(write(d)).getDocumentElement();
    assertThat(svg.getAttribute("width")).isEqualTo("200");
    assertThat(svg.getAttribute("height")).isEqualTo("100");
    assertThat(svg.getAttribute("viewport-fill")).isEqualTo("#f00");
    assertThat(svg.getAttribute("viewport-fill-opacity")).isEqualTo("0.5");
  }

  // ---------- gradients ----------
  private static LinearGradient gradient() {
    return new LinearGradient(
        0,
        0,
        1,
        0,
        new double[] {0, 1},
        new Color[] {Color.RED, Color.BLUE},
        new double[] {1, 1},
        true,
        new AffineTransform());
  }

  @Test
  void sameGradientIsStoredOnceInDefs() throws Exception {
    LinearGradient g = gradient();
    SVGRectFigure r1 = new SVGRectFigure(0, 0, 5, 5);
    SVGRectFigure r2 = new SVGRectFigure(10, 0, 5, 5);
    FILL_GRADIENT.set(r1, g);
    FILL_GRADIENT.set(r2, g);
    Document doc = parse(write(drawingOf(r1, r2)));
    assertThat(doc.getElementsByTagName("linearGradient").getLength()).isEqualTo(1);
    assertThat(doc.getElementsByTagName("stop").getLength()).isEqualTo(2);
    assertThat(((Element) doc.getElementsByTagName("rect").item(0)).getAttribute("fill"))
        .isEqualTo("url(#0)");
    assertThat(((Element) doc.getElementsByTagName("rect").item(1)).getAttribute("fill"))
        .isEqualTo("url(#0)");
  }

  @Test
  void idCounterIsNotResetBetweenWrites() throws Exception {
    // Characterizes defect: nextId is never reset in initStorageContext.
    LinearGradient g = gradient();
    SVGRectFigure r = new SVGRectFigure(0, 0, 5, 5);
    FILL_GRADIENT.set(r, g);
    SVGOutputFormat f = new SVGOutputFormat();
    ByteArrayOutputStream o1 = new ByteArrayOutputStream();
    f.write(o1, drawingOf(r));
    ByteArrayOutputStream o2 = new ByteArrayOutputStream();
    f.write(o2, drawingOf(r));
    assertThat(o1.toString(StandardCharsets.UTF_8)).contains("url(#0)");
    assertThat(o2.toString(StandardCharsets.UTF_8)).contains("url(#0)");
  }

  @Test
  void strokeGradientUsesDefs() throws Exception {
    SVGRectFigure r = new SVGRectFigure(0, 0, 5, 5);
    STROKE_GRADIENT.set(r, gradient());
    Document doc = parse(write(drawingOf(r)));
    assertThat(first(doc, "rect").getAttribute("stroke")).startsWith("url(#");
  }
}
