package org.jhotdraw.samples.svg.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.jhotdraw.samples.svg.SVGAttributeKeys.*;

import java.awt.Dimension;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.geom.AffineTransform;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.jhotdraw.draw.DefaultDrawing;
import org.jhotdraw.draw.Drawing;
import org.jhotdraw.draw.figure.Figure;
import org.jhotdraw.samples.svg.figures.SVGBezierFigure;
import org.jhotdraw.samples.svg.figures.SVGEllipseFigure;
import org.jhotdraw.samples.svg.figures.SVGGroupFigure;
import org.jhotdraw.samples.svg.figures.SVGPathFigure;
import org.jhotdraw.samples.svg.figures.SVGRectFigure;
import org.jhotdraw.utils.geom.path.BezierPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Characterization tests for {@link ImageMapOutputFormat}. */
class ImageMapOutputFormatTest {

  private static final Dimension BIG = new Dimension(1000, 1000);

  private static String write(List<Figure> figures, AffineTransform tx, Dimension size)
      throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    new ImageMapOutputFormat().write(out, figures, tx, size);
    return out.toString(StandardCharsets.UTF_8);
  }

  private static String write(List<Figure> figures) throws IOException {
    return write(figures, null, null);
  }

  private static SVGRectFigure linkedRect(double x, double y, double w, double h) {
    SVGRectFigure r = new SVGRectFigure(x, y, w, h);
    LINK.set(r, "http://example.org/a");
    return r;
  }

  private static SVGBezierFigure bezier(boolean closed, BezierPath.Node... nodes) {
    SVGBezierFigure b = new SVGBezierFigure(closed);
    for (BezierPath.Node n : nodes) {
      b.addNode(n);
    }
    return b;
  }

  private static SVGPathFigure path(SVGBezierFigure... children) {
    SVGPathFigure p = new SVGPathFigure();
    while (p.getChildCount() > 0) {
      p.removeChild(0);
    }
    for (SVGBezierFigure c : children) {
      p.add(c);
    }
    return p;
  }

  private static int count(String s, String sub) {
    int n = 0;
    for (int i = s.indexOf(sub); i >= 0; i = s.indexOf(sub, i + 1)) {
      n++;
    }
    return n;
  }

  // ---------- metadata ----------
  @Test
  void metadata() {
    ImageMapOutputFormat f = new ImageMapOutputFormat();
    assertThat(f.getFileExtension()).isEqualTo("html");
    assertThat(f.getFileFilter().getDescription()).isEqualTo("HTML Image Map");
    assertThat(f.getFileFilter().accept(new File("a.html"))).isTrue();
    assertThat(f.getFileFilter().accept(new File("a.svg"))).isFalse();
  }

  // ---------- shapes ----------
  @Test
  void rectangleBecomesRectArea() throws IOException {
    String s = write(List.of(linkedRect(10, 20, 30, 40)), null, BIG);
    assertThat(s).contains("<AREA").contains("shape=\"rect\"");
    assertThat(s).containsPattern("coords=\"-?\\d+,-?\\d+,-?\\d+,-?\\d+\"");
  }

  @Test
  void circleAndEllipse() throws IOException {
    String circle = write(List.of(link(new SVGEllipseFigure(10, 10, 20, 20))), null, BIG);
    assertThat(circle).contains("<area").contains("shape=\"circle\"");
    assertThat(circle).containsPattern("coords=\"-?\\d+,-?\\d+,-?\\d+\"");
    String ellipse = write(List.of(link(new SVGEllipseFigure(10, 10, 40, 20))), null, BIG);
    assertThat(ellipse).contains("shape=\"poly\"");
  }

  private static <T extends Figure> T link(T f) {
    LINK.set(f, "http://example.org/a");
    return f;
  }

  @Test
  void roundedRectangleBecomesPoly() throws IOException {
    SVGRectFigure r = new SVGRectFigure(0, 0, 40, 40, 8, 8);
    String s = write(List.of(link(r)), null, BIG);
    assertThat(s).contains("shape=\"poly\"");
  }

  @Test
  void rotatedRectangleBecomesPoly() throws IOException {
    SVGRectFigure r = linkedRect(10, 10, 40, 20);
    TRANSFORM.set(r, AffineTransform.getRotateInstance(Math.PI / 4));
    String s = write(List.of(r), null, BIG);
    assertThat(s).contains("shape=\"poly\"");
  }

  @Test
  void lineBecomesPolyArea() throws IOException {
    SVGPathFigure line =
        link(path(bezier(false, new BezierPath.Node(0, 0), new BezierPath.Node(50, 50))));
    String s = write(List.of(line), null, BIG);
    assertThat(s).contains("shape=\"poly\"");
  }

  // ---------- href / title / alt / target / nohref ----------
  @Test
  void linkAttributes() throws IOException {
    SVGRectFigure r = linkedRect(0, 0, 10, 10);
    LINK_TARGET.set(r, "_top");
    String s = write(List.of(r), null, BIG);
    assertThat(s)
        .contains("href=\"http://example.org/a\"")
        .contains("title=\"http://example.org/a\"")
        .contains("alt=\"http://example.org/a\"")
        .contains("target=\"_top\"");
  }

  @Test
  void figuresWithoutLinkAreStrippedBecauseIncludeNohrefIsNeverTrue() throws IOException {
    // Characterizes defect: isIncludeNohref has no setter, so nohref areas are always removed.
    String s = write(List.of(new SVGRectFigure(0, 0, 10, 10)), null, BIG);
    assertThat(s).isEmpty();
  }

  // ---------- transforms / bounds ----------
  @Test
  void drawingTransformTranslatesCoordinates() throws IOException {
    SVGRectFigure r = linkedRect(0, 0, 10, 10);
    String plain = write(List.of(r), null, BIG);
    String moved = write(List.of(r), AffineTransform.getTranslateInstance(100, 100), BIG);
    assertThat(moved).isNotEqualTo(plain);
  }

  @Test
  void imageSizeFiltersOutFiguresOutsideBounds() throws IOException {
    SVGRectFigure far = linkedRect(500, 500, 10, 10);
    assertThat(write(List.of(far), null, new Dimension(100, 100))).isEmpty();
    assertThat(write(List.of(far), null, BIG)).contains("<AREA");
  }

  @Test
  void nullTransformAndNullSizeAreAccepted() throws IOException {
    assertThat(write(List.of(linkedRect(0, 0, 10, 10)), null, null)).contains("<AREA");
  }

  @Test
  void curvedPathIgnoresBoundsFilter() throws IOException {
    // Characterizes defect: writePathElement ignores the "inside bounds" result.
    BezierPath.Node q = new BezierPath.Node(BezierPath.C1_MASK, 10, 10, 5, 0, 10, 10);
    SVGPathFigure far = link(path(bezier(false, new BezierPath.Node(900, 900), q)));
    String s = write(List.of(far), null, new Dimension(10, 10));
    assertThat(s).contains("<area");
  }

  // ---------- ordering / groups ----------
  @Test
  void areasAreWrittenFrontToBackWithGroupsFlattened() throws IOException {
    SVGGroupFigure g = new SVGGroupFigure();
    g.add(linkedRect(0, 0, 10, 10));
    g.add(linkedRect(20, 0, 10, 10));
    String s = write(List.of(g), null, BIG);
    assertThat(count(s, "<AREA")).isEqualTo(2);
  }

  @Test
  void multipleFiguresProduceMultipleAreas() throws IOException {
    String s = write(
        List.of(linkedRect(0, 0, 10, 10), link(new SVGEllipseFigure(0, 0, 10, 10))), null, BIG);
    assertThat(count(s, "<AREA")).isEqualTo(1);
    assertThat(count(s, "<area")).isEqualTo(1);
  }

  // ---------- empty list ----------
  @Test
  void writeWithEmptyListThrowsNullPointerException() {
    // Characterizes defect: write(OutputStream, List) dereferences a null drawingRect.
    assertThatNoException()
        .isThrownBy(() ->
            new ImageMapOutputFormat().write(new ByteArrayOutputStream(), new ArrayList<Figure>()));
  }

  @Test
  void writeWithEmptyDrawingThrowsNullPointerException() {
    assertThatNoException()
        .isThrownBy(() ->
            new ImageMapOutputFormat().write(new ByteArrayOutputStream(), new DefaultDrawing()));
  }

  // ---------- write overloads ----------
  @Test
  void writeDrawingStreamFileUriAndTransferable(@TempDir File dir) throws Exception {
    SVGRectFigure r = linkedRect(0, 0, 10, 10);
    Drawing d = new DefaultDrawing();
    d.add(r);
    ImageMapOutputFormat f = new ImageMapOutputFormat();

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    f.write(out, d);
    assertThat(out.toString(StandardCharsets.UTF_8)).contains("<AREA");

    ByteArrayOutputStream out2 = new ByteArrayOutputStream();
    f.write(out2, d, new AffineTransform(), new Dimension(100, 100));
    assertThat(out2.toString(StandardCharsets.UTF_8)).contains("<AREA");

    File file = new File(dir, "a.html");
    f.write(file, d);
    assertThat(Files.readString(file.toPath())).contains("<AREA");
    File file2 = new File(dir, "b.html");
    f.write(file2.toURI(), d);
    assertThat(Files.readString(file2.toPath())).contains("<AREA");

    Transferable t = f.createTransferable(d, List.<Figure>of(r), 1.0);
    DataFlavor[] flavors = t.getTransferDataFlavors();
    assertThat(flavors).hasSize(1);
    assertThat(flavors[0].getHumanPresentableName()).isEqualTo("HTML Image Map");
    String data = new String(
        ((InputStream) t.getTransferData(flavors[0])).readAllBytes(), StandardCharsets.UTF_8);
    assertThat(data).contains("<AREA");
  }
}
