/*
 * Copyright (c) 2019, Red Hat, Inc.
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
 * @bug 8218854
 * @requires jdk.version.major >= 8
 * @run main/othervm MaxAdvanceIsMax
 */

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public class MaxAdvanceIsMax {

    private static boolean debug = true;

    private static final class AntialiasHint {
        private Object aaHint;
        private String asString = "";

        AntialiasHint(Object aaHint) {
            if (aaHint.equals(
                    RenderingHints.VALUE_TEXT_ANTIALIAS_OFF)) {
                asString += "FT_LOAD_TARGET_MONO";
            } else if (aaHint.equals(
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON)) {
                asString += "FT_LOAD_TARGET_NORMAL";
            } else if (aaHint.equals(
                    RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB)) {
                asString += "FT_LOAD_TARGET_LCD";
            } else if (aaHint.equals(
                    RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_VRGB)) {
                asString += "FT_LOAD_TARGET_LCD_V";
            }
            this.aaHint = aaHint;
        }

        public Object getHint() {
            return aaHint;
        }

        public String toString() {
            return asString;
        }
    }

    private static final AntialiasHint[] antialiasHints = {
            new AntialiasHint(RenderingHints.VALUE_TEXT_ANTIALIAS_OFF),
            new AntialiasHint(RenderingHints.VALUE_TEXT_ANTIALIAS_ON),
            new AntialiasHint(RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB),
            new AntialiasHint(RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_VRGB)
    };

    private static final class StyleAndSize {
        int style;
        float size;
        public StyleAndSize(int style, float size) {
            this.style = style;
            this.size = size;
        }
    };

    private static final StyleAndSize[] stylesAndSizes = new StyleAndSize[] {
        new StyleAndSize(Font.BOLD | Font.ITALIC, 10)
    };

    public static void main(String[] args) throws Exception {
        GraphicsEnvironment e =
                GraphicsEnvironment.getLocalGraphicsEnvironment();
        Font[] fonts = e.getAllFonts();
        BufferedImage bi = new BufferedImage(500, 500,
                BufferedImage.TYPE_INT_RGB);
        for (AntialiasHint antialiasHint : antialiasHints) {
            for (Font f : fonts) {
                for (StyleAndSize styleAndSize : stylesAndSizes) {
                    f = f.deriveFont(styleAndSize.style, styleAndSize.size);
                    Graphics2D g2d = bi.createGraphics();
                    g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                            antialiasHint.getHint());
                    FontMetrics fm = g2d.getFontMetrics(f);
                    int[] width;
                    int maxWidth = -1;
                    int maxAdvance = fm.getMaxAdvance();
                    if (debug) {
                        System.out.println("Testing " + f + " in " +
                                antialiasHint);
                        System.out.println("getMaxAdvance: " + maxAdvance);
                    }
                    if (maxAdvance != -1) {
                        String failureMessage = null;
                        width = fm.getWidths();
                        for (int j = 0; j < width.length; j++) {
                            if (width[j] > maxWidth) {
                                maxWidth = width[j];
                            }
                            if (width[j] > maxAdvance) {
                                failureMessage = "FAILED: getMaxAdvance is " +
                                                 "not max for font: " +
                                                 f.toString() +
                                                 " getMaxAdvance(): " +
                                                 maxAdvance +
                                                 " getWidths()[" + j + "]: " +
                                                 width[j];
                                throw new Exception(failureMessage);
                            }
                        }
                    }
                    if (debug) {
                        System.out.println("Max char width: " + maxWidth);
                        System.out.println("PASSED");
                        System.out.println(".........................");
                    }
                }
            }
        }
        System.out.println("TEST PASS - OK");
    }
}
