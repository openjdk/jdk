/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8350203 8356966
 * @summary Confirm that a few special whitespace characters are ignored.
 */

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.text.AttributedString;
import java.util.Map;

public class IgnoredWhitespaceTest {

    public static void main(String[] args) throws Exception {
        BufferedImage image = new BufferedImage(600, 600, BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D g2d = image.createGraphics();

        Font font = new Font(Font.DIALOG, Font.PLAIN, 40);
        test(image, g2d, font);

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        test(image, g2d, font);

        g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        test(image, g2d, font);

        Font kerningFont = font.deriveFont(Map.of(TextAttribute.KERNING, TextAttribute.KERNING_ON));
        test(image, g2d, kerningFont);

        Font physicalFont = getPhysicalFont(40);
        if (physicalFont != null) {
            test(image, g2d, physicalFont);
        }

        g2d.dispose();
    }

    private static void test(BufferedImage image, Graphics2D g2d, Font font) {
        test(image, g2d, font, "XXXXX", "\t\t\t\t\tXXXXX");
        test(image, g2d, font, "XXXXX", "\tX\tX\tX\tX\tX\t");
        test(image, g2d, font, "XXXXX", "\r\r\r\r\rXXXXX");
        test(image, g2d, font, "XXXXX", "\rX\rX\rX\rX\rX\r");
        test(image, g2d, font, "XXXXX", "\n\n\n\n\nXXXXX");
        test(image, g2d, font, "XXXXX", "\nX\nX\nX\nX\nX\n");
    }

    private static void test(BufferedImage image, Graphics2D g2d, Font font, String reference, String text) {
        g2d.setFont(font);
        FontRenderContext frc = g2d.getFontRenderContext();
        int w = image.getWidth();
        int h = image.getHeight();
        int x = w / 2;
        int y = h / 2;

        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, w, h);
        g2d.setColor(Color.BLACK);
        g2d.drawString(reference, x, y);
        Rectangle expected = findTextBoundingBox(image);

        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, w, h);
        g2d.setColor(Color.BLACK);
        g2d.drawString(text, x, y);
        Rectangle actual = findTextBoundingBox(image);
        assertEqual(expected, actual, text, font);

        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, w, h);
        g2d.setColor(Color.BLACK);
        g2d.drawString(new AttributedString(text, Map.of(TextAttribute.FONT, font)).getIterator(), x, y);
        actual = findTextBoundingBox(image);
        assertEqual(expected, actual, text, font);

        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, w, h);
        g2d.setColor(Color.BLACK);
        g2d.drawChars(text.toCharArray(), 0, text.length(), x, y);
        actual = findTextBoundingBox(image);
        assertEqual(expected, actual, text, font);

        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, w, h);
        g2d.setColor(Color.BLACK);
        g2d.drawGlyphVector(font.createGlyphVector(frc, text), x, y);
        actual = findTextBoundingBox(image);
        assertEqual(expected, actual, text, font);
    }

    private static void assertEqual(Rectangle r1, Rectangle r2, String text, Font font) {
        if (!r1.equals(r2)) {
            String escaped = text.replace("\r", "\\r")
                                 .replace("\n", "\\n")
                                 .replace("\t", "\\t");
            String msg = String.format("for text '%s' with font %s: %s != %s",
                escaped, font.toString(), r1.toString(), r2.toString());
            throw new RuntimeException(msg);
        }
    }

    private static Font getPhysicalFont(int size) {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        String[] names = ge.getAvailableFontFamilyNames();
        for (String n : names) {
            switch (n) {
                case Font.DIALOG:
                case Font.DIALOG_INPUT:
                case Font.SERIF:
                case Font.SANS_SERIF:
                case Font.MONOSPACED:
                     continue;
                default:
                    Font f = new Font(n, Font.PLAIN, size);
                    if (f.canDisplayUpTo("AZaz09") == -1) {
                        return f;
                    }
            }
        }
        return null;
    }

    private static Rectangle findTextBoundingBox(BufferedImage image) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int width = image.getWidth();
        int height = image.getHeight();

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

        if (minX != Integer.MAX_VALUE) {
            return new Rectangle(minX, minY, maxX - minX, maxY - minY);
        } else {
            return null;
        }
    }
}
