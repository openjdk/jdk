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
 * @bug 8167268
 * @summary Checks behavior of GlyphVector.getGlyphMetrics(int).
 */

import java.awt.Font;
import java.awt.Rectangle;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphMetrics;
import java.awt.font.GlyphVector;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;

public class GlyphMetricsTest {

    public static void main(String[] args) {

        String text = "The quick brown \r\n fox JUMPS over \t the lazy dog.";
        Font font = new Font(Font.DIALOG, Font.PLAIN, 60);
        FontRenderContext frc = new FontRenderContext(null, true, true);
        GlyphVector gv = font.createGlyphVector(frc, text);

        for (int i = 0; i < gv.getNumGlyphs(); i++) {

            GlyphMetrics gm = gv.getGlyphMetrics(i);
            Rectangle2D bounds = gm.getBounds2D();
            assertEqual(gm.getAdvance(), gm.getAdvanceX(), 0, "advance x", i);
            assertEqual(0, gm.getAdvanceY(), 0, "advance y", i);

            // assumes one glyph per char in the test text
            String character = text.substring(i, i + 1);
            TextLayout layout = new TextLayout(character, font, frc);
            Rectangle pixelBounds = layout.getPixelBounds(frc, 0, 0);
            assertEqual(pixelBounds.getWidth(), bounds.getWidth(), 2, "width", i);
            assertEqual(pixelBounds.getHeight(), bounds.getHeight(), 2, "height", i);
            assertEqual(pixelBounds.getX(), bounds.getX(), 2, "x", i);
            assertEqual(pixelBounds.getY(), bounds.getY(), 2, "y", i);
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
