/*
 * Copyright (c) 2005, 2026, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @summary verify TextLayout.getBounds() return visual bounds
 * @bug 6323611 6761856
 */

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;

public class TextLayoutBounds {

    public static void main(String args[]) {
        FontRenderContext frc = new FontRenderContext(null, false, false);
        Font f = new Font("SansSerif", Font.BOLD, 32);
        String s = new String("JAVA");

        GlyphVector gv = f.createGlyphVector(frc, s);
        Rectangle2D gvvBounds = gv.getVisualBounds();
        Rectangle2D gvoBounds = gv.getOutline().getBounds2D();

        TextLayout tl = new TextLayout(s, f, frc);
        Rectangle2D tlBounds = tl.getBounds();
        Rectangle2D oBounds = tl.getOutline(null).getBounds2D();

        if (!gvvBounds.equals(gvoBounds) ||
            !tlBounds.equals(oBounds) ||
            !gvvBounds.equals(tlBounds))
        {
            // Log any that are not exact
            System.out.println();
            System.out.println("Font = " + f);
            System.out.println("GV Visual Bounds="+gvvBounds);
            System.out.println("GV Outline Bounds="+gvoBounds);
            System.out.println("TL Bounds="+tlBounds);
            System.out.println("TL Outline bounds="+tlBounds);

            // Fail the test only if the difference is significant.

            if (!compare(gvvBounds, gvoBounds)) {
                throw new RuntimeException("Bounds differ [gv visual != gv outline]");
            }
            if (!compare(tlBounds, oBounds)) {
                throw new RuntimeException("Bounds differ [tl visual != tl outline]");
            }
            if (!compare(gvvBounds, tlBounds)) {
                throw new RuntimeException("Bounds differ [tl visual != gv visual]");
            }
        }
    }

    private static final double MAX_DIFF = 1/32.0;
    private static boolean compare(double d1, double d2) {
         return Math.abs(d1 - d2)  < MAX_DIFF;
    }

    private static boolean compare(Rectangle2D r1, Rectangle2D r2) {
        return
          compare(r1.getMinX(), r2. getMinX()) &&
          compare(r1.getMinY(), r2. getMinY()) &&
          compare(r1.getMaxX(), r2. getMaxX()) &&
          compare(r1.getMaxY(), r2. getMaxY());
    }

}
