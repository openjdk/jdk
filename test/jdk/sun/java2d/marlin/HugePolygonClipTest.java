/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Clip rendering test
 *
 * @test
 * @bug 8297230
 * @summary verify that huge polygon is properly rasterized
 */
public class HugePolygonClipTest {

    private static final double LARGE_X_COORDINATE = 4194304.250;
    private static final int SCENE_WIDTH = 600;
    private static final int SCENE_HEIGHT = 400;

    private static final float WIDTH = 2.73f;

    private static final int G_MASK = 0x0000ff00;
    private static final int R_MASK = 0x00ff0000;
    private static final int RGB_MASK = 0x00ffffff;

    static final boolean SAVE_IMAGE = false;

    public static void main(final String[] args) {

        // First display which renderer is tested:
        // JDK9 only:
        System.setProperty("sun.java2d.renderer.verbose", "true");

        // enable Marlin logging:
        System.setProperty("sun.java2d.renderer.log", "true");
        System.setProperty("sun.java2d.renderer.clip", "true");
        System.setProperty("sun.java2d.renderer.subPixel_log2_X", "8");

        System.out.println("HugePolygonClipTest: size = " + SCENE_WIDTH + " x " + SCENE_HEIGHT);

        final BufferedImage image = new BufferedImage(SCENE_WIDTH, SCENE_HEIGHT, BufferedImage.TYPE_INT_ARGB);

        final Graphics2D g2d = (Graphics2D) image.getGraphics();
        try {
            g2d.setBackground(Color.BLACK);
            g2d.clearRect(0, 0, SCENE_WIDTH, SCENE_HEIGHT);

            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            // original test case => large moveTo in Filler but no bug in Stroker:
            double longWidth = LARGE_X_COORDINATE + SCENE_WIDTH + 0.001;

            final Path2D veryWidePolygon = new Path2D.Double();
            veryWidePolygon.moveTo(longWidth, 50.0);
            veryWidePolygon.lineTo(longWidth, 100.0);
            veryWidePolygon.lineTo(0.0, 100.0);
            veryWidePolygon.lineTo(0.0, 0.0);
            veryWidePolygon.closePath();

            g2d.translate(-longWidth + SCENE_WIDTH, 100.0);

            g2d.setPaint(Color.RED);
            g2d.fill(veryWidePolygon);

            g2d.setPaint(Color.GREEN);
            g2d.setStroke(new BasicStroke(WIDTH));
            g2d.draw(veryWidePolygon);

            if (SAVE_IMAGE) {
                try {
                    final File file = new File("TestHugePolygonCoords.png");

                    System.out.println("Writing file: " + file.getAbsolutePath());
                    ImageIO.write(image, "PNG", file);
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }

            // Check image on few pixels:
            final int x = SCENE_WIDTH / 2;
            checkColumn(image, x, SCENE_HEIGHT);

        } finally {
            g2d.dispose();
        }
    }

    private static void checkColumn(final BufferedImage image, final int x, final int maxY) {
        boolean trigger = false;
        boolean inside = false;

        for (int y = 0; y < maxY; y++) {
            final int rgb = image.getRGB(x, y);
            // System.out.println("pixel at (" + x + ", " + y + ") = " + rgb);

            if ((rgb & G_MASK) != 0) {
                if (!trigger) {
                    trigger = true;
                    inside = !inside;
                    // System.out.println("inside: "+inside);
                }
            } else {
                trigger = false;

                final int mask = (inside) ? R_MASK : RGB_MASK;

                final int expected = (rgb & mask);

                // System.out.println("pix[" + y + "] = " + expected + " inside: " + inside);
                if ((inside && (expected == 0))
                        || (!inside && (expected != 0))) {
                    throw new IllegalStateException("bad pixel at (" + x + ", " + y
                            + ") = " + expected + " inside: " + inside);
                }
            }
        }
    }
}
