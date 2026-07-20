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

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

/*
 * @test
 * @bug 8339974
 * @summary Verifies that text draws correctly using scaled and rotated fonts.
 */
public class RotatedScaledFontTest {

    public static void main(String[] args) throws Exception {
        test(0);
        test(1);
        test(2);
        test(3);
        test(4);
    }

    private static void test(int quadrants) throws Exception {

        int size = 2000;
        int center = size / 2;
        Font base = new Font("SansSerif", Font.PLAIN, 10);
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D g2d = image.createGraphics();

        try {
            for (int scale = 1; scale <= 100; scale++) {
                AffineTransform at = AffineTransform.getQuadrantRotateInstance(quadrants);
                at.scale(scale, scale);
                Font font = base.deriveFont(at);
                g2d.setColor(Color.WHITE);
                g2d.fillRect(0, 0, image.getWidth(), image.getHeight());
                g2d.setColor(Color.BLACK);
                g2d.setFont(font);
                g2d.drawString("TEST", center, center);
                Rectangle bounds = findTextBoundingBox(image);
                if (bounds == null) {
                    saveImage("bounds", image);
                    throw new RuntimeException("Text missing: scale=" + scale
                        + ", quadrants=" + quadrants + ", center=" + center);
                }
                boolean horizontal = (bounds.width > bounds.height);
                boolean expectedHorizontal = (quadrants % 2 == 0);
                if (horizontal != expectedHorizontal) {
                    saveImage("orientation", image);
                    throw new RuntimeException("Wrong orientation: scale=" + scale
                        + ", quadrants=" + quadrants + ", center=" + center
                        + ", bounds=" + bounds + ", horizontal=" + horizontal
                        + ", expectedHorizontal=" + expectedHorizontal);
                }
                if (!roughlyEqual(center, bounds.x, scale) && !roughlyEqual(center, bounds.x + bounds.width, scale)) {
                    saveImage("xedge", image);
                    throw new RuntimeException("No x-edge at center: scale=" + scale
                        + ", quadrants=" + quadrants + ", center=" + center
                        + ", bounds=" + bounds);
                }
                if (!roughlyEqual(center, bounds.y, scale) && !roughlyEqual(center, bounds.y + bounds.height, scale)) {
                    saveImage("yedge", image);
                    throw new RuntimeException("No y-edge at center: scale=" + scale
                        + ", quadrants=" + quadrants + ", center=" + center
                        + ", bounds=" + bounds);
                }
            }
        } finally {
            g2d.dispose();
        }
    }

    private static Rectangle findTextBoundingBox(BufferedImage image) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int width = image.getWidth();
        int height = image.getHeight();
        int[] rowPixels = new int[width];
        for (int y = 0; y < height; y++) {
            image.getRGB(0, y, width, 1, rowPixels, 0, width);
            for (int x = 0; x < width; x++) {
                boolean white = (rowPixels[x] == -1);
                if (!white) {
                    if (x < minX) {
                        minX = x;
                    }
                    if (y < minY) {
                        minY = y;
                    }
                    if (x > maxX) {
                        maxX = x;
                    }
                    if (y > maxY) {
                        maxY = y;
                    }
                }
            }
        }
        if (minX != Integer.MAX_VALUE) {
            return new Rectangle(minX, minY, maxX - minX, maxY - minY);
        } else {
            return null;
        }
    }

    private static boolean roughlyEqual(int x1, int x2, int scale) {
        return Math.abs(x1 - x2) <= Math.ceil(scale / 2d) + 1; // higher scale = higher allowed variance
    }

    private static void saveImage(String name, BufferedImage image) {
        try {
            String dir = System.getProperty("test.classes", ".");
            String path = dir + File.separator + name + ".png";
            File file = new File(path);
            ImageIO.write(image, "png", file);
        } catch (Exception e) {
            // we tried, and that's enough
        }
    }
}
