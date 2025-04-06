/*
 * Copyright (c) 2001, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4516037
 * @summary verify that scaled Polygons honor the transform
 */

import java.awt.Color;
import static java.awt.Color.*;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;

public class ScaledPolyTest {

    public static void main(String[] args) {

        Polygon poly = new Polygon();
        poly.addPoint(20, 10);
        poly.addPoint(30, 30);
        poly.addPoint(10, 30);
        poly.addPoint(20, 10);

        int height = 300;
        int width = 300;
        BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = bi.createGraphics();
        g2d.setColor(Color.white);
        g2d.fillRect(0, 0, bi.getWidth(), bi.getHeight());

        g2d.translate(10, 10);
        g2d.scale(2, 2);
        g2d.setColor(Color.yellow);
        g2d.fill(poly);
        g2d.setColor(Color.blue);
        g2d.draw(poly);

        /*
         * Examine each row of the image.
         * If the stroked polygon is correctly aligned on the filled polygon,
         * if there is anything except white on the line,
         * the transition will always be white+->blue+->yellow*->blue*->white+
         */
        int bluePix = blue.getRGB();
        int yellowPix = yellow.getRGB();
        int whitePix = white.getRGB();
        for (int y = 0; y < height; y++ ) {
            int x = 0;
            int pix = whitePix;

            while (pix == whitePix && x < width) pix = bi.getRGB(x++, y);
            if (pix == whitePix && x == width) continue; // all white row.

            if (pix != bluePix) throw new RuntimeException("Expected blue");

            while (pix == bluePix) pix = bi.getRGB(x++, y);

            if (pix == yellowPix) {
               while (pix == yellowPix) pix = bi.getRGB(x++, y);
               if (pix != bluePix) throw new RuntimeException("Expected blue");
               while (pix == bluePix) pix = bi.getRGB(x++, y);
               if (pix != whitePix) throw new RuntimeException("Expected white");
            }

            while (pix == whitePix && x < width) pix = bi.getRGB(x++, y);
            if (pix == whitePix && x == width) {
                continue;
            } else {
                throw new RuntimeException("Expected white to finish the row");
            }
        }
    }
}
