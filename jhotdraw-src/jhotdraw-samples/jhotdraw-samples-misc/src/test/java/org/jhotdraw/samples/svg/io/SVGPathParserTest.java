package org.jhotdraw.samples.svg.io;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import org.jhotdraw.utils.geom.path.BezierPath;
import org.junit.jupiter.api.Test;

class SVGPathParserTest {
  private static BezierPath.Node last(BezierPath path) {
    return path.nodes().get(path.size() - 1);
  }

  @Test
  void parsesSeparateSubpaths() throws IOException {
    assertEquals(2, new SVGPathParser().parse("M0 0 L10 10 M20 20 L30 30").length);
  }

  @Test
  void acceptsEmptyPath() throws IOException {
    assertEquals(0, new SVGPathParser().parse("").length);
  }

  @Test
  void rejectsMissingCoordinate() {
    assertThrows(IOException.class, () -> new SVGPathParser().parse("M10"));
  }

  @Test
  void parserCanBeReused() throws IOException {
    SVGPathParser parser = new SVGPathParser();
    assertEquals(1, parser.parse("M0 0 L10 10").length);
    assertEquals(2, parser.parse("M0 0 L1 1 M2 2 L3 3").length);
  }

  @Test
  void absoluteLineToSetsExactCoordinates() throws IOException {
    BezierPath path = new SVGPathParser().parse("M10 20 L30 40")[0];
    assertEquals(2, path.size());
    assertEquals(30, last(path).x[0]);
    assertEquals(40, last(path).y[0]);
  }

  @Test
  void relativeCommandsAccumulateFromCurrentPoint() throws IOException {
    BezierPath path = new SVGPathParser().parse("m10 20 l5 5 h10 v-5")[0];
    assertEquals(4, path.size());
    assertEquals(25, last(path).x[0]);
    assertEquals(20, last(path).y[0]);
  }

  @Test
  void implicitCoordinatesAfterMoveAreLineTo() throws IOException {
    BezierPath path = new SVGPathParser().parse("M0 0 10 10 20 0")[0];
    assertEquals(3, path.size());
    assertEquals(20, last(path).x[0]);
  }

  @Test
  void closePathMergesDuplicateEndPoint() throws IOException {
    BezierPath path = new SVGPathParser().parse("M0 0 L10 0 L10 10 L0 0 Z")[0];
    assertTrue(path.isClosed());
    assertEquals(3, path.size());
  }

  @Test
  void cubicCurveSetsIncomingControlPoint() throws IOException {
    BezierPath path = new SVGPathParser().parse("M0 0 C10 0 20 10 30 10")[0];
    assertEquals(2, path.size());
    BezierPath.Node end = last(path);
    assertNotEquals(0, end.mask & BezierPath.C1_MASK);
    assertEquals(20, end.x[1]);
    assertEquals(10, end.y[1]);
    assertEquals(30, end.x[0]);
  }

  @Test
  void arcEndsAtTargetPoint() throws IOException {
    BezierPath path = new SVGPathParser().parse("M0 0 A10 10 0 0 1 20 0")[0];
    assertEquals(20, last(path).x[0], 1e-9);
    assertEquals(0, last(path).y[0], 1e-9);
  }
}
