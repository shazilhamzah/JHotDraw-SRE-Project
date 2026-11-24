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

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {
      "<defs><linearGradient id='p'><stop offset='0' stop-color='red'/><stop offset='1' stop-color='blue'/></linearGradient></defs><rect width='20' height='30' fill='url(#p)'/>",
      "<defs><radialGradient id='p'><stop offset='0' stop-color='red'/><stop offset='1' stop-color='blue'/></radialGradient></defs><rect width='20' height='30' fill='url(#p)'/>",
      "<defs><solidColor id='p' solid-color='red'/></defs><rect width='20' height='30' fill='url(#p)'/>",
      "<style type='text/css'>rect { fill: red; }</style><rect width='20' height='30'/>",
      "<g fill='red'><rect width='20' height='30' fill='inherit'/></g>",
      "<rect width='20' height='30' color='red' fill='currentcolor'/>",
      "<rect width='20' height='30' stroke='blue' stroke-width='2' stroke-dasharray='2,3' opacity='0.5'/>",
      "<text x='10' y='30' font-size='12'>Hello</text>",
      "<text x='10' y='30' xml:space='preserve'>A <tspan>B</tspan></text>",
      "<textArea x='10' y='10' width='100' height='50'>Hello</textArea>",
      "<a href='https://example.com'><rect width='20' height='30'/></a>",
      "<switch><rect width='20' height='30'/></switch>",
      "<svg x='10' y='10' width='100' height='100' viewBox='0 0 50 50'><rect width='20' height='30'/></svg>",
      "<defs><rect id='r' width='20' height='30'/></defs><use xmlns:xlink='http://www.w3.org/1999/xlink' xlink:href='#r'/>",
      "<image width='20' height='20' href='data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jR1sAAAAASUVORK5CYII='/>"
  })
  void importsAdditionalSvgFeatures(String body) throws IOException {
    DefaultDrawing drawing = new DefaultDrawing();
    read(svg(body), drawing, true);
    assertEquals(1, drawing.getChildren().size());
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {
      "20px", "20pt", "2pc", "20mm", "2cm", "1in", "20%", "2em"
  })
  void importsLengthUnits(String width) throws IOException {
    DefaultDrawing drawing = new DefaultDrawing();
    read(svg("<rect width='" + width + "' height='30'/>"), drawing, true);
    assertEquals(1, drawing.getChildren().size());
    assertTrue(drawing.getChildren().get(0).getBounds().getWidth() > 0);
  }

  @Test
  void clipboardHonorsReplace() throws Exception {
    byte[] bytes = svg("<rect width='20' height='30'/>").getBytes(StandardCharsets.UTF_8);
    java.awt.datatransfer.DataFlavor flavor =
        new java.awt.datatransfer.DataFlavor("image/svg+xml", "Image SVG");
    java.awt.datatransfer.Transferable transferable = new java.awt.datatransfer.Transferable() {
      public java.awt.datatransfer.DataFlavor[] getTransferDataFlavors() {
        return new java.awt.datatransfer.DataFlavor[] {flavor};
      }
      public boolean isDataFlavorSupported(java.awt.datatransfer.DataFlavor candidate) {
        return flavor.equals(candidate);
      }
      public Object getTransferData(java.awt.datatransfer.DataFlavor candidate)
          throws java.awt.datatransfer.UnsupportedFlavorException {
        if (!isDataFlavorSupported(candidate)) {
          throw new java.awt.datatransfer.UnsupportedFlavorException(candidate);
        }
        return new ByteArrayInputStream(bytes);
      }
    };
    DefaultDrawing drawing = new DefaultDrawing();
    read(svg("<circle cx='20' cy='20' r='10'/>"), drawing, true);
    new SVGInputFormat().read(transferable, drawing, true);
    assertEquals(1, drawing.getChildren().size());
  }

  @Test
  void importsRelativeImages(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir)
      throws IOException {
    java.nio.file.Path png = dir.resolve("pixel.png");
    javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2,
        java.awt.image.BufferedImage.TYPE_INT_RGB), "png", png.toFile());
    java.nio.file.Files.writeString(dir.resolve("nested.svg"),
        svg("<rect width='20' height='30'/>"));
    java.nio.file.Path file = dir.resolve("drawing.svg");
    java.nio.file.Files.writeString(file, svg(
        "<image width='20' height='30' href='pixel.png'/>"
        + "<image x='30' width='20' height='30' href='nested.svg'/>"));
    DefaultDrawing drawing = new DefaultDrawing();
    new SVGInputFormat().read(file.toFile(), drawing, true);
    assertEquals(2, drawing.getChildren().size());
  }

  @Test
  void missingRasterDoesNotPreventOtherFigures(
      @org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws IOException {
    java.nio.file.Path file = dir.resolve("drawing.svg");
    java.nio.file.Files.writeString(file, svg(
        "<image width='20' height='30' href='missing.png'/>"
        + "<rect x='40' width='20' height='30'/>"));
    DefaultDrawing drawing = new DefaultDrawing();
    new SVGInputFormat().read(file.toUri().toURL(), drawing, true);
    assertTrue(drawing.getChildren().stream().anyMatch(
        figure -> Math.abs(figure.getBounds().getX() - 40) < 0.001));
  }

  @Test
  void switchSkipsUnsupportedFeatures() throws IOException {
    DefaultDrawing drawing = new DefaultDrawing();
    read(svg("<switch><rect requiredFeatures='urn:unsupported' width='10' height='10'/>"
        + "<rect x='40' width='20' height='30'/></switch>"), drawing, true);
    assertEquals(1, drawing.getChildren().size());
    assertEquals(40, drawing.getChildren().get(0).getBounds().getX(), 0.001);
  }

  @Test
  void useResolvesXmlId() throws IOException {
    DefaultDrawing drawing = new DefaultDrawing();
    read(svg("<defs><rect xml:id='r' width='20' height='30'/></defs>"
        + "<use xmlns:xlink='http://www.w3.org/1999/xlink' xlink:href='#r'/>"),
        drawing, true);
    assertEquals(1, drawing.getChildren().size());
  }

  @Test
  void useResolvesUnprefixedHref() throws IOException {
    DefaultDrawing drawing = new DefaultDrawing();
    read(svg("<defs><rect id='r' width='20' height='30'/></defs>"
        + "<use href='#r'/>"), drawing, true);
    assertEquals(1, drawing.getChildren().size());
  }

  @Test
  void switchMatchesLanguageTagIgnoringCase() throws IOException {
    java.util.Locale locale = org.jhotdraw.utils.util.LocaleUtil.getDefault();
    org.junit.jupiter.api.Assumptions.assumeFalse(locale.getLanguage().isEmpty());
    String tag = locale.getLanguage().toUpperCase(java.util.Locale.ROOT);
    if (!locale.getCountry().isEmpty()) {
      tag += "-" + locale.getCountry().toUpperCase(java.util.Locale.ROOT);
    }
    DefaultDrawing drawing = new DefaultDrawing();
    read(svg("<switch><rect systemLanguage='" + tag
        + "' x='40' width='20' height='30'/><rect width='10' height='10'/></switch>"),
        drawing, true);
    assertEquals(1, drawing.getChildren().size());
    assertEquals(40, drawing.getChildren().get(0).getBounds().getX(), 0.001);
  }

  @Test
  void circularUseDoesNotPreventOtherFigures() throws IOException {
    DefaultDrawing drawing = new DefaultDrawing();
    read(svg("<defs><use id='loop' href='#loop'/></defs><use href='#loop'/>"
        + "<rect x='40' width='20' height='30'/>"), drawing, true);
    assertEquals(1, drawing.getChildren().size());
    assertEquals(40, drawing.getChildren().get(0).getBounds().getX(), 0.001);
  }

  @Test
  void indirectCircularUseDoesNotPreventOtherFigures() throws IOException {
    DefaultDrawing drawing = new DefaultDrawing();
    read(svg("<defs><use id='a' href='#b'/><use id='b' href='#a'/></defs>"
        + "<use href='#a'/><rect x='40' width='20' height='30'/>"), drawing, true);
    assertEquals(1, drawing.getChildren().size());
    assertEquals(40, drawing.getChildren().get(0).getBounds().getX(), 0.001);
  }

  @Test
  void repeatedValidReferencesAreNotTreatedAsCycles() throws IOException {
    DefaultDrawing drawing = new DefaultDrawing();
    read(svg("<defs><rect id='r' width='20' height='30'/></defs>"
        + "<use href='#r'/><use href='#r' x='40'/>"), drawing, true);
    assertEquals(2, drawing.getChildren().size());
  }

  @Test
  void circularReferenceDoesNotBlockLaterValidReference() throws IOException {
    DefaultDrawing drawing = new DefaultDrawing();
    read(svg("<defs><use id='loop' href='#loop'/><rect id='r' width='20' height='30'/></defs>"
        + "<use href='#loop'/><use href='#r'/>"), drawing, true);
    assertEquals(1, drawing.getChildren().size());
    assertEquals(20, drawing.getChildren().get(0).getBounds().getWidth(), 0.001);
  }

  @Test
  void inlinePaintValuesArePreserved() throws IOException {
    DefaultDrawing drawing = new DefaultDrawing();
    read(svg("<rect width='20' height='30' style='fill:red;stroke:blue'/>"), drawing, true);
    var figure = drawing.getChildren().get(0);
    assertEquals(java.awt.Color.RED, figure.get(org.jhotdraw.samples.svg.SVGAttributeKeys.FILL_COLOR));
    assertEquals(java.awt.Color.BLUE, figure.get(org.jhotdraw.samples.svg.SVGAttributeKeys.STROKE_COLOR));
  }

  @Test
  void currentColorIsResolved() throws IOException {
    DefaultDrawing drawing = new DefaultDrawing();
    read(svg("<rect width='20' height='30' color='red' fill='currentcolor'/>"), drawing, true);
    assertEquals(java.awt.Color.RED, drawing.getChildren().get(0)
        .get(org.jhotdraw.samples.svg.SVGAttributeKeys.FILL_COLOR));
  }

  @Test
  void pixelDimensionsArePreserved() throws IOException {
    DefaultDrawing drawing = new DefaultDrawing();
    read(svg("<rect width='20px' height='30px'/>"), drawing, true);
    assertEquals(20, drawing.getChildren().get(0).getBounds().getWidth(), 0.001);
    assertEquals(30, drawing.getChildren().get(0).getBounds().getHeight(), 0.001);
  }

  @Test
  void explicitFontSizeIsPreserved() throws IOException {
    DefaultDrawing drawing = new DefaultDrawing();
    read(svg("<text x='10' y='30' font-size='18'>Hello</text>"), drawing, true);
    assertEquals(18.0, drawing.getChildren().get(0)
        .get(org.jhotdraw.samples.svg.SVGAttributeKeys.FONT_SIZE), 0.001);
  }
}
