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
 * @bug 8148334 8377937
 * @summary Checks behavior of GlyphVector.getGlyphMetrics(int) with rotated fonts.
 */

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphMetrics;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;

public class GlyphMetricsRotatedFontTest {

    public static void main(String[] args) {

        String text = "The quick brown \r\n fox JUMPS over \t the lazy dog.";
        FontRenderContext frc = new FontRenderContext(null, true, true);

        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        String[] names = ge.getAvailableFontFamilyNames();

        for (String name : names) {

            Font normal = new Font(name, Font.PLAIN, 60);
            if (normal.canDisplayUpTo("AZaz09") != -1) {
                continue;
            }

            double theta = 0.5; // about 30 degrees
            AffineTransform tx = AffineTransform.getRotateInstance(theta);
            Font rotated = normal.deriveFont(tx);

            GlyphVector gv1 = normal.createGlyphVector(frc, text);
            GlyphVector gv2 = rotated.createGlyphVector(frc, text);

            for (int i = 0; i < gv1.getNumGlyphs(); i++) {
                GlyphMetrics gm1 = gv1.getGlyphMetrics(i);
                GlyphMetrics gm2 = gv2.getGlyphMetrics(i);
                assertEqual(0, gm1.getAdvanceY(), 0, name + " normal y", i);
                double expectedX = Math.cos(theta) * gm1.getAdvanceX();
                double expectedY = Math.sin(theta) * gm1.getAdvanceX();
                assertEqual(expectedX, gm2.getAdvanceX(), 1, name + " rotated x", i);
                assertEqual(expectedY, gm2.getAdvanceY(), 1, name + " rotated y", i);
            }
        }
    }

    private static void assertEqual(double d1, double d2, double variance,
                                    String scenario, int index) {
        if (Math.abs(d1 - d2) > variance) {
            String msg = String.format("%s for index %d: %f != %f", scenario, index, d1, d2);
            throw new RuntimeException(msg);
        }
    }
}
