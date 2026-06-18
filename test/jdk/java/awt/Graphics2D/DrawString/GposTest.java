/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @bug 8269888
 * @summary Test for correct GPOS font table handling when drawing text.
 */

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.TextAttribute;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.Base64;
import java.util.Map;

import javax.imageio.ImageIO;

/**
 * Tests that fonts with GPOS tables are handled correctly when drawing text. The
 * GPOS table is an OpenType font table which can be used to adjust glyph positions
 * in certain contexts. This table is sometimes used in fonts which support Thai
 * script, in order to place vowel glyphs above or below consonant glyphs.
 *
 * @see <a href="https://learn.microsoft.com/en-us/typography/opentype/spec/gpos">GPOS table spec</a>
 */
public class GposTest {

    /**
     * <p>Font created for this test which contains a number of GPOS entries which
     * adjust glyph positions in ways which can be checked programmatically. The
     * glyphs render as different basic geometric shapes, so that the layout can
     * also be checked visually if necessary. These geometric shapes are different
     * for each character, but they have the same overall width and height.
     *
     * <p>Glyphs:
     *
     * <ul>
     *   <li> a : renders as a square
     *   <li> b : renders as a cross
     *   <li> c : renders as an X mark
     *   <li> d : renders as a tringale pointing up
     *   <li> e : renders as a triangle pointing down
     *   <li> f : renders as a triangle pointing left
     *   <li> g : renders as a triangle pointing right
     * </ul>
     *
     * <p>GPOS entries:
     *
     * <ul>
     *   <li> ab  : second glyph is moved back to the same space as the first glyph
     *   <li> ac  : second glyph is moved back to the same space as the first glyph
     *   <li> ad  : second glyph is moved back to the same space as the first glyph
     *   <li> ade : second and third glyphs are moved back to the same space as the first glyph
     *   <li> af  : second glyph is moved back and above the first glyph
     * </ul>
     *
     * p>The following FontForge Python script was used to generate this font:
     *
     * <pre>
     * import fontforge
     * import base64
     *
     * SIZE = 800
     * THICKNESS = 80
     * PAD = int(THICKNESS / 2)
     * WIDTH = SIZE + (2 * PAD)
     *
     * def square(font, char):
     *   glyph = font.createChar(ord(char))
     *   pen = glyph.glyphPen()
     *   pen.moveTo((PAD, PAD))
     *   pen.lineTo((PAD, PAD + SIZE))
     *   pen.lineTo((PAD + SIZE, PAD + SIZE))
     *   pen.lineTo((PAD + SIZE, PAD))
     *   pen.closePath()
     *   glyph.stroke('circular', THICKNESS)
     *   glyph.width = WIDTH
     *   pen = None
     *   return glyph
     *
     * def cross(font, char):
     *   glyph = font.createChar(ord(char))
     *   pen = glyph.glyphPen()
     *   pen.moveTo((PAD, PAD + SIZE/2))
     *   pen.lineTo((PAD + SIZE, PAD + SIZE/2))
     *   pen.closePath()
     *   pen.moveTo((PAD + SIZE/2, PAD))
     *   pen.lineTo((PAD + SIZE/2, PAD + SIZE))
     *   pen.closePath()
     *   glyph.stroke('circular', THICKNESS)
     *   glyph.width = WIDTH
     *   pen = None
     *   return glyph
     *
     * def x_mark(font, char):
     *   glyph = font.createChar(ord(char))
     *   pen = glyph.glyphPen()
     *   pen.moveTo((PAD, PAD))
     *   pen.lineTo((PAD + SIZE, PAD + SIZE))
     *   pen.closePath()
     *   pen.moveTo((PAD, PAD + SIZE))
     *   pen.lineTo((PAD + SIZE, PAD))
     *   pen.closePath()
     *   glyph.stroke('circular', THICKNESS)
     *   glyph.width = WIDTH
     *   pen = None
     *   return glyph
     *
     * def triangle_up(font, char):
     *   glyph = font.createChar(ord(char))
     *   pen = glyph.glyphPen()
     *   pen.moveTo((PAD, PAD))
     *   pen.lineTo((PAD + SIZE, PAD))
     *   pen.lineTo((PAD + SIZE/2, PAD + SIZE))
     *   pen.closePath()
     *   glyph.stroke('circular', THICKNESS)
     *   glyph.width = WIDTH
     *   pen = None
     *   return glyph
     *
     * def triangle_down(font, char):
     *   glyph = font.createChar(ord(char))
     *   pen = glyph.glyphPen()
     *   pen.moveTo((PAD, PAD + SIZE))
     *   pen.lineTo((PAD + SIZE, PAD + SIZE))
     *   pen.lineTo((PAD + SIZE/2, PAD))
     *   pen.closePath()
     *   glyph.stroke('circular', THICKNESS)
     *   glyph.width = WIDTH
     *   pen = None
     *   return glyph
     *
     * def triangle_left(font, char):
     *   glyph = font.createChar(ord(char))
     *   pen = glyph.glyphPen()
     *   pen.moveTo((PAD + SIZE, PAD + SIZE))
     *   pen.lineTo((PAD + SIZE, PAD))
     *   pen.lineTo((PAD, PAD + SIZE/2))
     *   pen.closePath()
     *   glyph.stroke('circular', THICKNESS)
     *   glyph.width = WIDTH
     *   pen = None
     *   return glyph
     *
     * def triangle_right(font, char):
     *   glyph = font.createChar(ord(char))
     *   pen = glyph.glyphPen()
     *   pen.moveTo((PAD, PAD))
     *   pen.lineTo((PAD, PAD + SIZE))
     *   pen.lineTo((PAD + SIZE, PAD + SIZE/2))
     *   pen.closePath()
     *   glyph.stroke('circular', THICKNESS)
     *   glyph.width = WIDTH
     *   pen = None
     *   return glyph
     *
     * # create font
     *
     * font = fontforge.font()
     * font.encoding = 'UnicodeFull'
     * font.design_size = 16
     * font.em = 2048
     * font.ascent = 1638
     * font.descent = 410
     * font.familyname = 'GPOSTest'
     * font.fontname = 'GPOSTest'
     * font.fullname = 'GPOSTest'
     * font.copyright = ''
     * font.autoWidth(0, 0, 2048)
     *
     * # create glyphs in font
     *
     * space = font.createChar(0x20)
     * space.width = WIDTH
     *
     * a = square(font, 'a')         # a -> renders as square
     * b = cross(font, 'b')          # b -> renders as cross
     * c = x_mark(font, 'c')         # c -> renders as X mark
     * d = triangle_up(font, 'd')    # d -> renders as triangle pointing up
     * e = triangle_down(font, 'e')  # e -> renders as triangle pointing down
     * f = triangle_left(font, 'f')  # f -> renders as triangle pointing left
     * g = triangle_right(font, 'g') # g -> renders as triangle pointing right
     *
     * # GPOS pair adjustment
     * # a b -> square cross, but the cross is moved back over the square
     * # therefore the drawn area for "a" should match the drawn area for "ab"
     *
     * font.addLookup('lu1', 'gpos_pair', (), (('kern',(('latn',('dflt')),)),))
     * font.addLookupSubtable('lu1', 'subtable1')
     * a.addPosSub('subtable1', 'b', 0, 0, -WIDTH, 0, 0, 0, 0, 0)
     *
     * # GPOS pair adjustment
     * # a f -> square leftware-triangle, but the triangle is moved back and above the square
     * # therefore the drawn area for "af" should be twice the height as for "a", but same width
     *
     * a.addPosSub('subtable1', 'f', 0, 0, 0, 0, -WIDTH, WIDTH, 0, 0)
     *
     * # GPOS cursive attachment
     * # a c -> square x-mark, but the x-mark is moved back over the square
     * # therefore the drawn area for "a" should match the drawn area for "ac"
     *
     * font.addLookup('lu2', 'gpos_cursive', (), (('curs',(('latn',('dflt')),)),))
     * font.addLookupSubtable('lu2', 'subtable2')
     * font.addAnchorClass('subtable2', 'class2')
     * a.addAnchorPoint('class2', 'exit', 0, 0)
     * c.addAnchorPoint('class2', 'entry', 0, 0)
     *
     * # GPOS mark-to-base attachment
     * # a d -> square upward-triangle, but the triangle is moved back over the square
     * # therefore the drawn area for "a" should match the drawn area for "ad"
     *
     * font.addLookup('lu3', 'gpos_mark2base', (), (('mark',(('latn',('dflt')),)),))
     * font.addLookupSubtable('lu3', 'subtable3')
     * font.addAnchorClass('subtable3', 'class3')
     * a.addAnchorPoint('class3', 'base', 0, 0)
     * d.addAnchorPoint('class3', 'mark', 0, 0)
     *
     * # GPOS mark-to-mark attachment
     * # a d e -> square upward-triangle downward-triangle, but all superimposed
     * # therefore the drawn area for "a" should match the drawn area for "ade"
     * # builds on the "ad" mark-to-base attachment defined above
     *
     * font.addLookup('lu4', 'gpos_mark2mark', (), (('mkmk',(('latn',('dflt')),)),))
     * font.addLookupSubtable('lu4', 'subtable4')
     * font.addAnchorClass('subtable4', 'class4')
     * d.addAnchorPoint('class4', 'basemark', 0, 0)
     * e.addAnchorPoint('class4', 'mark', 0, 0)
     *
     * # save font to file
     *
     * ttf = 'test.ttf'     # TrueType
     * t64 = 'test.ttf.txt' # TrueType Base64
     *
     * font.generate(ttf)
     *
     * with open(ttf, 'rb') as f1:
     *   encoded = base64.b64encode(f1.read())
     *   with open(t64, 'wb') as f2:
     *     f2.write(encoded)
     * </pre>
     */
    private static final String TTF_BYTES = "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";

    public static void main(String[] args) throws Exception {

        float size = 22;
        byte[] ttfBytes = Base64.getDecoder().decode(TTF_BYTES);
        ByteArrayInputStream ttfStream = new ByteArrayInputStream(ttfBytes);
        Font unscaled = Font.createFont(Font.TRUETYPE_FONT, ttfStream)
                            .deriveFont(size)
                            .deriveFont(Map.of(TextAttribute.KERNING, TextAttribute.KERNING_ON));

        BufferedImage image = new BufferedImage(2000, 2000, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHints(Map.of(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON));

        try {
            for (int scale = 1; scale <= 10; scale++) {

                // we're checking 4 fonts, verifying correct behavior:
                // 1. a font scaled uniformly via font size
                // 2. a font scaled uniformly via AffineTransform
                // 3. a font scaled 2x taller via AffineTransform
                // 4. a font without extra scaling

                AffineTransform at1 = AffineTransform.getScaleInstance(scale, scale);
                AffineTransform at2 = AffineTransform.getScaleInstance(scale, scale * 2);

                Font scaled1 = unscaled.deriveFont(size * scale);
                Font scaled2 = unscaled.deriveFont(at1);
                Font scaled3 = unscaled.deriveFont(at2);

                Dimension dim   = measure(image, g2d, scaled1, "a");
                Dimension tall2 = new Dimension(dim.width, dim.height * 2);
                Dimension tall4 = new Dimension(dim.width, dim.height * 4);
                Dimension tall8 = new Dimension(dim.width, dim.height * 8);

                Dimension orig  = measure(image, g2d, unscaled, "a");
                Dimension orig2 = new Dimension(orig.width, orig.height * 2);

                checkSizes(image, g2d, unscaled, orig, orig2, "unscaled");
                checkSizes(image, g2d, scaled1, dim, tall2, "scaled 1");
                checkSizes(image, g2d, scaled2, dim, tall2, "scaled 2");
                checkSizes(image, g2d, scaled3, tall2, tall4, "scaled 3");

                g2d.setTransform(at1);
                checkSizes(image, g2d, unscaled, dim, tall2, "unscaled with G2D 1");

                g2d.setTransform(at2);
                checkSizes(image, g2d, unscaled, tall2, tall4, "unscaled with G2D 2");

                g2d.setTransform(AffineTransform.getScaleInstance(1, 2));
                checkSizes(image, g2d, scaled1, tall2, tall4, "scaled 1 with G2D 3");
                checkSizes(image, g2d, scaled2, tall2, tall4, "scaled 2 with G2D 3");
                checkSizes(image, g2d, scaled3, tall4, tall8, "scaled 3 with G2D 3");

                g2d.setTransform(new AffineTransform()); // reset
            }
        } finally {
            g2d.dispose();
        }
    }

    private static void checkSizes(BufferedImage image, Graphics2D g2d,
                                   Font font, Dimension normal, Dimension tall,
                                   String scenario) {

        // individual glyphs
        checkSize(image, g2d, font, "a", normal, scenario);
        checkSize(image, g2d, font, "b", normal, scenario);
        checkSize(image, g2d, font, "c", normal, scenario);
        checkSize(image, g2d, font, "d", normal, scenario);
        checkSize(image, g2d, font, "e", normal, scenario);
        checkSize(image, g2d, font, "f", normal, scenario);
        checkSize(image, g2d, font, "g", normal, scenario);

        // GPOS combinations
        checkSize(image, g2d, font, "ab", normal, scenario);
        checkSize(image, g2d, font, "ac", normal, scenario);
        checkSize(image, g2d, font, "ad", normal, scenario);
        checkSize(image, g2d, font, "ade", normal, scenario);
        checkSize(image, g2d, font, "af", tall, scenario);
    }

    private static void checkSize(BufferedImage image, Graphics2D g2d,
                                  Font font, String text, Dimension expected,
                                  String scenario) {
        int maxWidthVariance = Math.max((int) Math.ceil(expected.width * 0.05), 1);
        int maxHeightVariance = Math.max((int) Math.ceil(expected.height * 0.05), 1);
        Dimension actual = measure(image, g2d, font, text);
        if (actual == null ||
            Math.abs(actual.width - expected.width) > maxWidthVariance ||
            Math.abs(actual.height - expected.height) > maxHeightVariance) {
            String id = scenario + " " + text;
            saveImage(id, image);
            throw new RuntimeException(id + ": " + actual + " != " + expected);
        }
    }

    private static Dimension measure(BufferedImage image, Graphics2D g2d,
                                     Font font, String text) {

        int width = image.getWidth();
        int height = image.getHeight();
        Point2D.Float center = new Point2D.Float(width / 2, height / 2);

        try {
            // ensure we draw in the center, even if G2D is scaled
            g2d.getTransform().createInverse().transform(center, center);
        } catch (NoninvertibleTransformException e) {
            throw new RuntimeException(e);
        }

        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, width, height);
        g2d.setColor(Color.BLACK);
        g2d.setFont(font);
        g2d.drawString(text, center.x, center.y);

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int[] rowPixels = new int[width];
        for (int y = 0; y < height; y++) {
            image.getRGB(0, y, width, 1, rowPixels, 0, width);
            for (int x = 0; x < width; x++) {
                boolean white = (rowPixels[x] == -1);
                if (!white) {
                    if (x < minX) {
                        minX = x;
                    }
                    if (y < minY) {
                        minY = y;
                    }
                    if (x > maxX) {
                        maxX = x;
                    }
                    if (y > maxY) {
                        maxY = y;
                    }
                }
            }
        }

        if (minX != Integer.MAX_VALUE &&
            minY != Integer.MAX_VALUE &&
            maxX != Integer.MIN_VALUE &&
            maxY != Integer.MIN_VALUE) {
            return new Dimension(maxX - minX + 1, maxY - minY + 1);
        } else {
            return null;
        }
    }

    private static void saveImage(String name, BufferedImage image) {
        try {
            String dir = System.getProperty("test.classes", ".");
            String path = dir + File.separator + name + ".png";
            File file = new File(path);
            ImageIO.write(image, "png", file);
        } catch (Exception e) {
            // we tried, and that's enough
        }
    }
}
