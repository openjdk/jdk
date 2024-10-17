/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4286726
 * @summary Java2D raster printing: large text may overflow glyph cache.
 *          Draw a large glyphvector, the 'A' glyph should appear and not get flushed.
*/

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;

/**
 * Draw a very large glyphvector on a surface.
 * If the cache was flushed the first glyph is not rendered.
 * Note: the implementation no longer uses glyphs for rendering large text,
 * but in principle the test is still useful.
 */
public class CacheFlushTest {

    static final int WIDTH = 400, HEIGHT = 600;
    static final int FONTSIZE = 250;
    static final String TEST = "ABCDEFGHIJKLMNOP";
    static final HashMap<RenderingHints.Key, Object> HINTS = new HashMap<>();

    static {
      HINTS.put(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
      HINTS.put(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      HINTS.put(RenderingHints.KEY_FRACTIONALMETRICS,
                RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }

    public static void main(String args[]) {
        BufferedImage bi = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);

        Graphics2D g2d = bi.createGraphics();
        g2d.addRenderingHints(HINTS);
        g2d.setColor(Color.white);
        g2d.fillRect(0, 0, WIDTH, HEIGHT);
        g2d.setColor(Color.black);

        FontRenderContext frc = g2d.getFontRenderContext();
        Font font = new Font(Font.DIALOG, Font.PLAIN, 250);
        GlyphVector gv = font.createGlyphVector(frc, TEST);

        /* Set the positions of all but the first glyph to be offset vertically but
         * FONTSIZE pixels. So if the first glyph "A" is not flushed we can tell this
         * by checking for non-white pixels in the range for the default y offset of 0
         * from the specified y location.
         */
        Point2D.Float pt = new Point2D.Float(20f, FONTSIZE);
        for (int i = 1; i < gv.getNumGlyphs(); ++i) {
            gv.setGlyphPosition(i, pt);
            pt.x += 25f;
            pt.y = FONTSIZE;
        }
        g2d.drawGlyphVector(gv, 20, FONTSIZE);
        /* Now expect to find at least one black pixel in the rect (0,0) -> (WIDTH, FONTSIZE) */
        boolean found = false;
        int blackPixel = Color.black.getRGB();
        for (int y = 0; y < FONTSIZE; y++) {
            for (int x = 0; x < WIDTH; x++) {
                if (bi.getRGB(x, y) == blackPixel) {
                    found = true;
                    break;
                }
            }
            if (found == true) {
                break;
            }
        }
        if (!found) {
            throw new RuntimeException("NO BLACK PIXELS");
        }
    }
}
