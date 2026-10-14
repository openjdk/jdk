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

import java.awt.Color;
import java.awt.FontFormatException;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.awt.image.VolatileImage;
import java.io.File;
import java.io.IOException;

/*
 * @test
 * @key headful
 * @bug 8349701
 * @summary Checks that LCD glyphs generated using FreeType scaler for a
 *          TrueTypeFont are rendered properly.
 * @run main/othervm -Dsun.java2d.uiScale=1 FreeTypeLCDGlyphTest
 */

public class FreeTypeLCDGlyphTest {

    private static final int WIDTH  = 50;
    private static final int HEIGHT = 50;

    public static void main(final String[] args) throws IOException, FontFormatException {
        File fontFile = new File(System.getProperty("test.src", "."), "A.ttf");
        Font baseFont = Font.createFont(Font.TRUETYPE_FONT, fontFile);
        Font font = baseFont.deriveFont(Font.PLAIN, 50);

        GraphicsEnvironment ge =
            GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsConfiguration gc =
            ge.getDefaultScreenDevice().getDefaultConfiguration();
        VolatileImage vi = gc.createCompatibleVolatileImage(WIDTH, HEIGHT);

        while (true) {
            vi.validate(gc);
            Graphics2D g2d = vi.createGraphics();
            g2d.setColor(Color.white);
            g2d.fillRect(0, 0, WIDTH, HEIGHT);

            g2d.setColor(Color.black);
            g2d.setFont(font);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

            FontMetrics fm = g2d.getFontMetrics();
            g2d.drawString("A", 0, fm.getAscent());
            g2d.dispose();

            if (vi.validate(gc) != VolatileImage.IMAGE_OK) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException _) {}
                continue;
            }

            if (vi.contentsLost()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException _) {}
                continue;
            }

            break;
        }

        BufferedImage bi = vi.getSnapshot();
        /*
         * We have fixed image size, font, fontSize, text origin and uiScale.
         * Horizontal line in letter 'A' differs completely with and without
         * product bug, and we are verifying the center pixel of this line.
         */
        if (bi.getRGB(9, 25) != Color.BLACK.getRGB()) {
            throw new RuntimeException("LCD Glyph is not rendered correctly");
        }
    }
}
