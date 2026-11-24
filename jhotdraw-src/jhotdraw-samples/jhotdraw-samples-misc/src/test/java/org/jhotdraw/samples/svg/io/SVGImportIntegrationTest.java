package org.jhotdraw.samples.svg.io;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.jhotdraw.draw.DefaultDrawing;
import org.junit.jupiter.api.Test;

class SVGImportIntegrationTest {
  private void read(String svg, DefaultDrawing drawing, boolean replace) throws IOException {
    new SVGInputFormat().read(
        new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8)), drawing, replace);
  }

  @Test
  void importsEmptySvgDocument() throws IOException {
    DefaultDrawing drawing = new DefaultDrawing();
    read("<svg xmlns='http://www.w3.org/2000/svg'/>", drawing, true);
    assertTrue(drawing.getChildren().isEmpty());
  }

  @Test
  void importsRectangleWithWhitespaceAndComment() throws IOException {
    DefaultDrawing drawing = new DefaultDrawing();
    read("<svg xmlns='http://www.w3.org/2000/svg'>\n<!-- rectangle -->\n"
        + "<rect x='10' y='20' width='30' height='40'/>\n</svg>", drawing, true);
    assertEquals(1, drawing.getChildren().size());
  }

  @Test
  void rejectsMissingSvgRoot() {
    assertThrows(IOException.class, () -> read("<root/>", new DefaultDrawing(), true));
  }
}
