package org.jhotdraw.samples.svg.io;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.datatransfer.DataFlavor;
import java.awt.geom.AffineTransform;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class SVGInputFormatTest {
  @Test
  void commaSeparatedValues() throws IOException {
    assertArrayEquals(new String[] {"a", "b", "c"}, SVGInputFormat.toCommaSeparatedArray("a, b,c"));
  }

  @Test
  void whitespaceAndCommaSeparatedValues() throws IOException {
    assertArrayEquals(new String[] {"1", "2", "3"}, SVGInputFormat.toWSOrCommaSeparatedArray("1 2,3"));
    assertArrayEquals(new String[0], SVGInputFormat.toWSOrCommaSeparatedArray(""));
  }

  @Test
  void quotedFontNames() throws IOException {
    assertArrayEquals(new String[] {"Times New Roman", "Arial"}, SVGInputFormat.toQuotedAndCommaSeparatedArray("'Times New Roman', Arial"));
  }

  @Test
  void svgFlavorAndFileFilter() {
    SVGInputFormat format = new SVGInputFormat();
    assertTrue(format.isDataFlavorSupported(new DataFlavor("image/svg+xml", "SVG")));
    assertFalse(format.isDataFlavorSupported(DataFlavor.stringFlavor));
    assertTrue(format.getFileFilter().accept(new java.io.File("drawing.svg")));
    assertFalse(format.getFileFilter().accept(new java.io.File("drawing.txt")));
  }

  @Test
  void chainedTransform() throws IOException {
    AffineTransform expected = new AffineTransform();
    expected.translate(10, 20);
    expected.scale(2, 3);
    assertEquals(expected, SVGInputFormat.toTransform(null, "translate(10,20) scale(2,3)"));
  }

  @Test
  void malformedTransform() {
    assertThrows(IOException.class, () -> SVGInputFormat.toTransform(null, "matrix(1,2)"));
  }
}
