/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

/*
 * @test
 * @bug 8328896
 * @summary test that using very large font sizes used don't break later uses
 */

public class ExtremeFontSizeTest {

    static BufferedImage bi = new BufferedImage(1,1,1);
    static Graphics2D g2d = bi.createGraphics();
    static String testString = "M";
    static Font font = new Font("SansSerif", Font.PLAIN, 12);
    static int fontSize = 0;
    static boolean failed = false;
    static int[] fontSizes = { 10, 12, 1000, 2000, 20000, 100000, 8 };
    static double[] scales = { 1.0, 900.0};
    static boolean[] fms = { false, true };

    public static void main(String[] args) {

        /* run tests validating bounds etc are non-zero
         * then run with extreme scales for which zero is allowed - but not required
         * then run the first tests again to be sure they are still reasonable.
        */
        runTests();
        test(5_000_000, 10_000, false, testString, false);
        test(5_000_000, 10_000, true, testString, false);
        test(0, 0.00000001, false, testString, false);
        runTests();

        if (failed) {
            throw new RuntimeException("Test failed. Check stdout log.");
        }
    }

    static void runTests() {
        for (int fontSize : fontSizes) {
            for (double scale : scales) {
                for (boolean fm : fms) {
                    test(fontSize, scale, fm, testString, true);
                }
            }
        }
    }

    static void test(int size, double scale, boolean fm, String str, boolean checkAll) {

        AffineTransform at = AffineTransform.getScaleInstance(scale, scale);
        FontRenderContext frc = new FontRenderContext(at, false, fm);
        font = font.deriveFont((float)size);
        g2d.setTransform(at);
        g2d.setFont(font);
        FontMetrics metrics = g2d.getFontMetrics();
        int height = metrics.getHeight();
        double width = font.getStringBounds(str, frc).getWidth();

        GlyphVector gv = font.createGlyphVector(frc, str.toCharArray());
        Rectangle pixelBounds = gv.getPixelBounds(frc, 0, 0);
        Rectangle2D visualBounds = gv.getVisualBounds();

        System.out.println("Test parameters: size="+size+" scale="+scale+" fm="+fm+" str="+str);
        System.out.println("font height="+metrics.getHeight());
        System.out.println("string bounds width="+width);
        System.out.println("GlyphVector Pixel Bounds="+ pixelBounds);
        System.out.println("GlyphVector Visual Bounds="+ visualBounds);


        if (height < 0 || width < 0 || pixelBounds.getWidth() < 0 || visualBounds.getWidth() < 0) {
            failed = true;
            System.out.println(" *** Unexpected negative size reported  *** ");
         }
         if (!checkAll) {
            System.out.println();
            return;
        }

        if (height == 0 || width == 0 || (pixelBounds.isEmpty()) || visualBounds.isEmpty() ) {
            failed = true;
            System.out.println("Pixel bounds empty="+pixelBounds.isEmpty());
            System.out.println("Visual bounds empty="+visualBounds.isEmpty());
            System.out.println(" *** RESULTS NOT AS EXPECTED  *** ");
        }
        System.out.println();
    }
}
