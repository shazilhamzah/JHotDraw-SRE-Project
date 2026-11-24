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

  private static String svg(String body) {
    return "<svg xmlns='http://www.w3.org/2000/svg' width='200' height='200'>" + body + "</svg>";
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {
      "<rect x='10' y='20' width='30' height='40'/>",
      "<circle cx='50' cy='50' r='20'/>",
      "<ellipse cx='50' cy='50' rx='20' ry='10'/>",
      "<line x1='10' y1='10' x2='50' y2='50'/>",
      "<polyline points='10,10 50,50 80,10'/>",
      "<polygon points='10,10 50,50 80,10'/>",
      "<path d='M10 10 L50 50 L80 10 Z'/>"
  })
  void importsBasicShapes(String body) throws IOException {
    DefaultDrawing drawing = new DefaultDrawing();
    read(svg(body), drawing, true);
    assertEquals(1, drawing.getChildren().size());
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {
      "M10 10 L50 50 H80 V90 Z", "m10 10 l40 40 h30 v40 z",
      "M10 10 C20 30 40 30 50 50 S70 80 90 90",
      "m10 10 c10 20 30 20 40 40 s20 30 40 40",
      "M10 10 Q30 40 50 50 T90 90", "m10 10 q20 30 40 40 t40 40",
      "M10 10 A20 20 0 0 1 50 50", "m10 10 a20 20 0 0 1 40 40"
  })
  void importsPathCommands(String path) throws IOException {
    DefaultDrawing drawing = new DefaultDrawing();
    read(svg("<path d='" + path + "'/>"), drawing, true);
    assertEquals(1, drawing.getChildren().size());
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {
      "matrix(1,0,0,1,10,20)", "translate(10,20)", "scale(2,3)",
      "rotate(30)", "skewX(15)", "skewY(15)", "translate(10,20) scale(2)"
  })
  void importsTransforms(String transform) throws IOException {
    DefaultDrawing drawing = new DefaultDrawing();
    read(svg("<rect width='20' height='30' transform='" + transform + "'/>"), drawing, true);
    assertEquals(1, drawing.getChildren().size());
  }

  @Test
  void importsGroupAndSkipsHiddenShapes() throws IOException {
    DefaultDrawing drawing = new DefaultDrawing();
    read(svg("<g><rect width='20' height='30'/></g>"
        + "<rect width='20' height='30' display='none'/>"), drawing, true);
    assertEquals(1, drawing.getChildren().size());
  }

  @Test
  void importsStyledRectangle() throws IOException {
    DefaultDrawing drawing = new DefaultDrawing();
    read(svg("<rect width='20' height='30' style='fill:red;stroke:blue;opacity:0.5'/>"), drawing, true);
    assertEquals(1, drawing.getChildren().size());
  }

  @Test
  void importsPrefixedSvg() throws IOException {
    DefaultDrawing drawing = new DefaultDrawing();
    read("<s:svg xmlns:s='http://www.w3.org/2000/svg'><s:rect width='20' height='30'/></s:svg>", drawing, true);
    assertEquals(1, drawing.getChildren().size());
  }

  @Test
  void readsFileAndUrl(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws IOException {
    java.nio.file.Path file = dir.resolve("drawing.svg");
    java.nio.file.Files.writeString(file, svg("<rect width='20' height='30'/>"));
    SVGInputFormat format = new SVGInputFormat();
    DefaultDrawing drawing = new DefaultDrawing();
    format.read(file.toFile(), drawing, true);
    assertEquals(1, drawing.getChildren().size());
    format.read(file.toUri().toURL(), drawing, false);
    assertEquals(2, drawing.getChildren().size());
  }

  @Test
  void reusesReaderAndReplacesDrawing() throws IOException {
    SVGInputFormat format = new SVGInputFormat();
    DefaultDrawing drawing = new DefaultDrawing();
    byte[] data = svg("<rect width='20' height='30'/>").getBytes(StandardCharsets.UTF_8);
    format.read(new ByteArrayInputStream(data), drawing, true);
    format.read(new ByteArrayInputStream(data), drawing, false);
    assertEquals(2, drawing.getChildren().size());
    format.read(new ByteArrayInputStream(data), drawing, true);
    assertEquals(1, drawing.getChildren().size());
  }

  @Test
  void rejectsMalformedXml() {
    assertThrows(IOException.class, () -> read("<svg>", new DefaultDrawing(), true));
  }

  @Test
  void rectangleGeometryIsPreserved() throws IOException {
    DefaultDrawing drawing = new DefaultDrawing();
    read(svg("<rect x='10' y='20' width='30' height='40'/>"), drawing, true);
    java.awt.geom.Rectangle2D bounds = drawing.getChildren().get(0).getBounds();
    assertEquals(10, bounds.getX(), 0.001);
    assertEquals(20, bounds.getY(), 0.001);
    assertEquals(30, bounds.getWidth(), 0.001);
    assertEquals(40, bounds.getHeight(), 0.001);
  }
}
