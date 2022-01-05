/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8240756
 * @summary Non-English characters are printed with wrong glyphs on MacOS
 * @modules java.desktop/sun.java2d java.desktop/sun.java2d.loops java.desktop/sun.font
 * @requires os.family == "mac"
 * @run main MultiSlotFontTest
 */

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import sun.font.StandardGlyphVector;
import sun.java2d.OSXOffScreenSurfaceData;
import sun.java2d.SunGraphics2D;
import sun.java2d.SurfaceData;
import sun.java2d.loops.SurfaceType;

public class MultiSlotFontTest {

    private static final int WIDTH = 100;
    private static final int HEIGHT = 60;

    private static final String TEST_STR = "\u3042\u3044\u3046\u3048\u304Aabc";
    private static final int EXPECTED_HEIGHT = 10;
    private static final int EXPECTED_WIDTH = 77;
    private static final int LIMIT_DIFF_HEIGHT = 3;
    private static final int LIMIT_DIFF_WIDTH = 15;

    public static void main(String[] args) throws Exception {
        MultiSlotFontTest test = new MultiSlotFontTest();
    }

    public MultiSlotFontTest() {
        BufferedImage img = createImage();

        SurfaceData sd = OSXOffScreenSurfaceData.createDataIC(img,
                             SurfaceType.IntRgb);
        SunGraphics2D g2d = new SunGraphics2D(sd,
                                    Color.BLACK, Color.WHITE, null);
        Font font = g2d.getFont();

        if (font.canDisplayUpTo(TEST_STR) != -1) {
            System.out.println("There is no capable font. Skipping the test.");
            System.out.println("Font: " + font);
            return;
        }

        FontRenderContext frc = new FontRenderContext(null, false, false);
        StandardGlyphVector gv = new StandardGlyphVector(font, TEST_STR, frc);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                             RenderingHints.VALUE_ANTIALIAS_OFF);
        g2d.drawGlyphVector(gv, 0.0f, (float)(HEIGHT - 5));
        g2d.dispose();

        Dimension d = getBounds(img);

        if (Math.abs(d.height - EXPECTED_HEIGHT) > LIMIT_DIFF_HEIGHT ||
            Math.abs(d.width  - EXPECTED_WIDTH)  > LIMIT_DIFF_WIDTH) {
            debugOut(img);
            throw new RuntimeException(
                "Incorrect GlyphVector shape " + d + "," + gv);
        }
    }

    private static BufferedImage createImage() {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT,
                                      BufferedImage.TYPE_INT_RGB);
        Graphics g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);
        g.dispose();
        return image;
    }

    private Dimension getBounds(BufferedImage img) {
        int top = HEIGHT;
        int left = WIDTH;
        int right = 0;
        int bottom = 0;
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                if ((img.getRGB(x, y) & 0xFFFFFF) == 0) {
                    if (top    > y) top = y;
                    if (bottom < y) bottom = y;
                    if (left   > x) left = x;
                    if (right  < x) right = x;
                }
            }
        }
        return new Dimension(right - left, bottom - top);
    }

    private void debugOut(BufferedImage img) {
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int c = img.getRGB(x, y) & 0xFFFFFF;
                if (c == 0) {
                    System.out.print("*");
                } else {
                    System.out.print(" ");
                }
            }
            System.out.println();
        }
    }
}
