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
 * @bug 6562639
 * @summary Verify correct getPixelBounds() behavior regardless of text color.
 */

import java.awt.Color;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;
import java.util.HashMap;
import java.util.Map;

public class TestGetPixelBoundsWithColors {

    private static final Color TRANSPARENT_BLACK = new Color(0, 0, 0, 0);
    private static final Color TRANSPARENT_WHITE = new Color(255, 255, 255, 0);

    public static void main(String[] args) throws Exception {

        Color[] colors = new Color[] {
            Color.WHITE, Color.BLACK, Color.YELLOW, Color.RED, Color.GREEN,
            Color.GRAY, Color.LIGHT_GRAY, Color.DARK_GRAY, Color.PINK,
            Color.CYAN, Color.MAGENTA, Color.BLUE, null
        };

        for (Color color : colors) {
            test(color);
        }

        testTransparent(TRANSPARENT_BLACK);
        testTransparent(TRANSPARENT_WHITE);
    }

    private static void test(Color c) {
        Map< TextAttribute, Object > underline = new HashMap<>();
        underline.put(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
        Font font1 = new Font(Font.DIALOG, Font.PLAIN, 60).deriveFont(underline);
        Map< TextAttribute, Object > foreground = new HashMap<>();
        foreground.put(TextAttribute.FOREGROUND, c);
        Font font2 = font1.deriveFont(foreground);
        FontRenderContext frc = new FontRenderContext(null, true, true);
        TextLayout layout1 = new TextLayout("TEST", font1, frc);
        TextLayout layout2 = new TextLayout("TEST", font2, frc);
        Rectangle r1 = layout1.getPixelBounds(frc, 0, 0);
        Rectangle r2 = layout2.getPixelBounds(frc, 0, 0);
        if (!r1.equals(r2)) {
            throw new RuntimeException("For color " + c + ", " + r1 + " != " + r2);
        }
        Rectangle2D bounds = layout1.getBounds();
        if (Math.abs(bounds.getX() - r1.x) > 3 ||
            Math.abs(bounds.getY() - r1.y) > 3 ||
            Math.abs(bounds.getWidth() - r1.width) > 6 ||
            Math.abs(bounds.getHeight() - r1.height) > 6) {
            throw new RuntimeException("For color " + c + ", pixel bounds " +
                r1 + " not similar to " + bounds);
        }
    }

    private static void testTransparent(Color c) {
        Font font1 = new Font(Font.DIALOG, Font.PLAIN, 60);
        Map< TextAttribute, Object > attributes = new HashMap<>();
        attributes.put(TextAttribute.FOREGROUND, c);
        Font font2 = font1.deriveFont(attributes);
        FontRenderContext frc = new FontRenderContext(null, true, true);
        TextLayout layout = new TextLayout("TEST", font2, frc);
        Rectangle r = layout.getPixelBounds(frc, 0, 0);
        if (!r.isEmpty()) {
            throw new RuntimeException("Expected empty pixel bounds for " + c + " but got " + r);
        }
    }
}
