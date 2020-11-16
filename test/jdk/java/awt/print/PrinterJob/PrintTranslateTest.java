/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @test PrintTranslateTest
 * @bug 8255387
 * @summary Virtial mirrored characters should be drawn correctly
 * @run main PrintTranslateTest
 */

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

public class PrintTranslateTest{
    static String target = "\u3042";
    static final int SIZE = 50;
    static final int LIMIT = 40;

    static BufferedImage drawNormal(Font font) {
        BufferedImage image = new BufferedImage(SIZE, SIZE,
                                      BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(Color.white);
        g2d.fillRect(0, 0, image.getWidth(), image.getHeight());
        g2d.setColor(Color.black);
        //Set antialias on not to use embedded bitmap for reference
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                             RenderingHints.VALUE_ANTIALIAS_ON);

        g2d.setFont(font);
        FontMetrics fm = g2d.getFontMetrics();
        g2d.drawString(target, 5, fm.getAscent());
        g2d.dispose();
        return image;
    }

    static BufferedImage drawMirror(Font font) {
        BufferedImage image = new BufferedImage(SIZE, SIZE,
                                      BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(Color.white);
        g2d.fillRect(0, 0, image.getWidth(), image.getHeight());
        g2d.setColor(Color.black);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                             RenderingHints.VALUE_ANTIALIAS_OFF);

        AffineTransform base = g2d.getTransform();
        AffineTransform trans = new AffineTransform(1.0, 0, 0, -1.0, 0, 0);
        trans.concatenate(base);
        g2d.setTransform(trans);

        g2d.setFont(font);

        FontMetrics fm = g2d.getFontMetrics();
        g2d.drawString(target, 5, -image.getHeight()+fm.getAscent());
        g2d.dispose();
        return image;
    }

    public static void main(String[] args) {
        GraphicsEnvironment ge =
            GraphicsEnvironment.getLocalGraphicsEnvironment();
        Font fonts[] = ge.getAllFonts();

        for (Font font: fonts) {
            if (!font.canDisplay(target.charAt(0))) {
                continue;
            }
            font = font.deriveFont(12.0f);
            BufferedImage img1 = drawNormal(font);
            BufferedImage img2 = drawMirror(font);
            int errorCount = 0;
            for (int j = 0; j < SIZE; j++) {
                for (int i = 0; i < SIZE; i++) {
                    int c1 = img1.getRGB(i, j) & 0xFFFFFF;
                    int c2 = img2.getRGB(i, SIZE-j-1) & 0xFFFFFF;
                    if (c1 != c2) {
                        errorCount++;
                    }
                }
                sb.append("\n");
            }
            if (errorCount > LIMIT) {
                System.out.println("ErrorCount="+errorCount);
                System.out.println("Font="+font);
                throw new RuntimeException(
                    "Incorrect mirrored character with " + font);
            }
        }
    }
}

