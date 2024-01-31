/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import static java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment;
import static java.awt.RenderingHints.KEY_TEXT_ANTIALIASING;
import static java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB;
import static java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_OFF;
import static java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON;
import static java.awt.image.BufferedImage.TYPE_3BYTE_BGR;

/*
 * @test
 * @bug 8312555
 * @summary Verifies that hieroglyphs are stretched by AffineTransform.scale(2, 1)
 * @run main StretchedFontTest
 */
public final class StretchedFontTest {
    private static final String TEXT = "\u6F22";
    private static final int FONT_SIZE = 20;

    private static final Color BACKGROUND = Color.WHITE;
    private static final Color[] FOREGROUNDS = {
            new Color(0xFF000000, true),
            new Color(0x7F000000, true)
    };

    /** Locale for getting font names. */
    private static final Locale ENGLISH_LOCALE = Locale.ENGLISH;

    private static final AffineTransform STRETCH_TRANSFORM =
            AffineTransform.getScaleInstance(2.0, 1.0);

    public static void main(String[] args) {
        List<String> errors =
                Arrays.stream(getLocalGraphicsEnvironment()
                              .getAvailableFontFamilyNames(ENGLISH_LOCALE))
                      .map(family -> new Font(family, Font.PLAIN, FONT_SIZE))
                      .filter(font -> font.canDisplay(TEXT.codePointAt(0)))
                      .filter(font -> !isBrokenFont(font))
                      .map(font -> font.deriveFont(STRETCH_TRANSFORM))
                      .flatMap(StretchedFontTest::testFont)
                      .filter(Objects::nonNull)
                      .toList();

        if (!errors.isEmpty()) {
            errors.forEach(System.err::println);
            throw new Error(errors.size() + " failure(s) found;"
                            + " the first one: " + errors.get(0));
        }
    }

    /**
     * Checks whether the font renders the glyph in {@code TEXT} and
     * returns {@code true} if the glyph isn't rendered.
     *
     * @param font the font to test
     * @return {@code true} if the visual bounds of {@code TEXT} are empty, and
     *         {@code false} otherwise
     */
    private static boolean isBrokenFont(final Font font) {
        final boolean empty =
                font.createGlyphVector(new FontRenderContext(null, false, false),
                                       TEXT)
                    .getVisualBounds()
                    .isEmpty();
        if (empty) {
            System.err.println("Broken font: " + font.getFontName(ENGLISH_LOCALE));
        }
        return empty;
    }

    /**
     * Tests the font with a set of text antialiasing hints.
     *
     * @param font the font to test
     * @return a stream of test results
     * @see #testFont(Font, Object)
     */
    private static Stream<String> testFont(final Font font) {
        return Stream.of(VALUE_TEXT_ANTIALIAS_OFF,
                         VALUE_TEXT_ANTIALIAS_ON,
                         VALUE_TEXT_ANTIALIAS_LCD_HRGB)
                     .flatMap(hint -> testFont(font, hint));
    }

    /**
     * Tests the font with the specified text antialiasing hint and a set of
     * foreground colors.
     *
     * @param font the font to test
     * @param hint the text antialiasing hint to test
     * @return a stream of test results
     * @see #testFont(Font, Object, Color)
     */
    private static Stream<String> testFont(final Font font, final Object hint) {
        return Stream.of(FOREGROUNDS)
                     .map(foreground -> testFont(font, hint, foreground));
    }

    /**
     * Tests the font with the specified text antialiasing hint and
     * foreground color. In case of failure, it saves the rendered
     * image to a file.
     *
     * @param font the font to test
     * @param hint the text antialiasing hint to test
     * @param foreground the foreground color to use
     * @return {@code null} if the text rendered correctly; otherwise,
     *         a {@code String} with the font family name, the value of
     *         the rendering hint and the color in hex
     */
    private static String testFont(final Font font,
                                   final Object hint,
                                   final Color foreground) {
        final Dimension size = getTextSize(font);
        final BufferedImage image =
                new BufferedImage(size.width, size.height, TYPE_3BYTE_BGR);

        final Graphics2D g2d = image.createGraphics();
        try {
            g2d.setColor(BACKGROUND);
            g2d.fillRect(0, 0, size.width, size.height);

            g2d.setRenderingHint(KEY_TEXT_ANTIALIASING, hint);
            g2d.setColor(foreground);
            g2d.setFont(font);
            g2d.drawString(TEXT, 0, g2d.getFontMetrics(font).getAscent());
        } finally {
            g2d.dispose();
        }

        if (verifyImage(image)) {
            return null;
        }
        String fontName = font.getFontName(ENGLISH_LOCALE);
        String hintValue = getHintString(hint);
        String hexColor = String.format("0x%08x", foreground.getRGB());
        saveImage(image, fontName + "-" + hintValue + "-" + hexColor);
        return "Font: " + fontName + ", Hint: " + hintValue + ", Color: " + hexColor;
    }

    /**
     * Verifies the rendered image of the hieroglyph. The hieroglyph
     * should be stretched across the entire width of the image.
     * If the right half of the image contains only pixels of the background
     * color, the hieroglyph isn't stretched correctly
     * &mdash; it's a failure.
     *
     * @param image the image to verify
     * @return {@code true} if the hieroglyph is stretched correctly; or
     *         {@code false} if right half of the image contains only
     *         background-colored pixels, which means the hieroglyph isn't
     *         stretched.
     */
    private static boolean verifyImage(final BufferedImage image) {
        final int width = image.getWidth();
        final int height = image.getHeight();
        for (int x = width / 2; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (image.getRGB(x, y) != BACKGROUND.getRGB()) {
                    // Any other color but background means the glyph is stretched
                    return true;
                }
            }
        }

        // The right side of the image is filled with the background color only,
        // the glyph isn't stretched.
        return false;
    }

    private static String getHintString(final Object hint) {
        if (hint == VALUE_TEXT_ANTIALIAS_OFF) {
            return "off";
        } else if (hint == VALUE_TEXT_ANTIALIAS_ON) {
            return "on";
        } else if (hint == VALUE_TEXT_ANTIALIAS_LCD_HRGB) {
            return "lcd";
        } else {
            throw new IllegalArgumentException("Unexpected hint: " + hint);
        }
    }

    private static final BufferedImage dummyImage =
            new BufferedImage(5, 5, TYPE_3BYTE_BGR);

    private static Dimension getTextSize(final Font font) {
        final Graphics g = dummyImage.getGraphics();
        try {
            return g.getFontMetrics(font)
                    .getStringBounds(TEXT, g)
                    .getBounds()
                    .getSize();
        } finally {
            g.dispose();
        }
    }

    private static void saveImage(final BufferedImage image,
                                  final String fileName) {
        try {
            ImageIO.write(image,
                          "png",
                          new File(fileName + ".png"));
        } catch (IOException ignored) {
        }
    }
}
