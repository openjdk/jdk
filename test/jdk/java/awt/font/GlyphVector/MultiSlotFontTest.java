/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import sun.font.StandardGlyphVector;
import sun.java2d.OSXOffScreenSurfaceData;
import sun.java2d.OSXSurfaceData;
import sun.java2d.SunGraphics2D;
import sun.java2d.SurfaceData;
import sun.java2d.loops.SurfaceType;

public class MultiSlotFontTest {

    private static final int width = 100;
    private static final int height = 60;
    private static final int LIMIT = 5;
    private StandardGlyphVector gv;

    private static final String[] TEST_STRINGS = {
        "\u3042\u3044\u3046\u3048\u304A",
        "a\u3042b\u3044c\u3046d",
        "\u3042abcd",
        "abcd\u3042",
    };

    public static void main(String[] args) throws Exception {
        MultiSlotFontTest test = new MultiSlotFontTest();
    }

    public MultiSlotFontTest() {
        BufferedImage img1, img2;

        for (String str: TEST_STRINGS) {
            img1 = createImage();
            img2 = createImage();

            callDrawGlyphVector(img1, str);
            callDrawString(img2, str);

            int diff = compareImages(img1, img2);
            if (diff > LIMIT) {
                debugOut(img1, img2);
                throw new RuntimeException(
                    "Incorrect GlyphVector shape " +
                    diff + "," + str + "," + gv);
            }
        }
    }

    private void callDrawGlyphVector(BufferedImage image, String str) {
        SurfaceData sd = OSXOffScreenSurfaceData.createDataIC(image,
                             SurfaceType.IntRgb);
        SunGraphics2D g2d = new SunGraphics2D(sd,
                                    Color.BLACK, Color.WHITE, null);
        FontRenderContext frc = new FontRenderContext(null, false, false);
        Font font = g2d.getFont();
        gv = new StandardGlyphVector(font, str, frc);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                             RenderingHints.VALUE_ANTIALIAS_OFF);
        g2d.drawGlyphVector(gv, 0.0f, (float)(height - 5));
        g2d.dispose();
    }

    private void callDrawString(BufferedImage image, String str) {
        SurfaceData sd = OSXOffScreenSurfaceData.createDataIC(image,
                             SurfaceType.IntRgb);
        SunGraphics2D g2d = new SunGraphics2D(sd,
                                    Color.BLACK, Color.WHITE, null);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                             RenderingHints.VALUE_ANTIALIAS_OFF);
        g2d.drawString(str, 0.0f, (float)(height - 5));
        g2d.dispose();
    }

    private static BufferedImage createImage() {
        BufferedImage image = new BufferedImage(width, height,
                                      BufferedImage.TYPE_INT_RGB);
        Graphics g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.dispose();
        return image;
    }

    private int getPixcelCount(BufferedImage img) {
        int count = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if ((img.getRGB(x, y) & 0xFFFFFF) == 0) {
                    count++;
                }
            }
        }
        return count;
    }

    private int compareImages(BufferedImage img1, BufferedImage img2) {
        // Since positions can be shifted, check pixcel count.
        int count1 = getPixcelCount(img1);
        int count2 = getPixcelCount(img2);
        return Math.abs(count1-count2);
    }

    private void debugOut(BufferedImage img1, BufferedImage img2) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int c1 = img1.getRGB(x, y) & 0xFFFFFF;
                int c2 = img2.getRGB(x, y) & 0xFFFFFF;
                if (c1 != c2) {
                    if (c1==0) {
                        System.out.print("+");
                    } else {
                        System.out.print("*");
                    }
                } else {
                    if (c1==0) {
                        System.out.print(".");
                    } else {
                        System.out.print(" ");
                    }
               }
            }
            System.out.println();
        }
    }
}
