package org.jhotdraw.samples.svg.io;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class SVGPathParserTest {
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
}
