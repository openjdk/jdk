/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.Image;
import java.awt.Paint;
import java.awt.PaintContext;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;

import java.util.function.Consumer;

/**
 * @test
 * @bug 8355078
 * @summary Checks if different image types (BufferedImage and VolatileImage)
 *          produce the same results when using different ways to fill the image
 *          (setColor, setPaint, and custom Paint).
 */
public final class ColorPaintContextBasicTest {

    private static final int SIZE = 100;

    private static final int[] TYPES = new int[]{
            BufferedImage.TYPE_INT_RGB,
            BufferedImage.TYPE_INT_ARGB,
            BufferedImage.TYPE_INT_ARGB_PRE,
            BufferedImage.TYPE_INT_BGR,
            BufferedImage.TYPE_3BYTE_BGR,
            BufferedImage.TYPE_4BYTE_ABGR,
            BufferedImage.TYPE_4BYTE_ABGR_PRE,
    };

    private static final Color[] COLORS = {
            Color.RED,
            Color.GREEN,
            Color.BLUE,
            Color.YELLOW,
            Color.MAGENTA,
            Color.CYAN,
            Color.BLACK,
            Color.WHITE,
            new Color(255, 165, 0),
            new Color(128, 0, 128),
            new Color(255, 0, 0, 128)
    };

    private static final class CustomPaint implements Paint {

        private final Color color;

        public CustomPaint(Color color) {
            this.color = color;
        }

        @Override
        public PaintContext createContext(ColorModel cm, Rectangle deviceBounds,
                                          Rectangle2D userBounds,
                                          AffineTransform xform,
                                          RenderingHints hints)
        {
            return color.createContext(cm, deviceBounds, userBounds, xform,
                                       hints);
        }

        @Override
        public int getTransparency() {
            return color.getTransparency();
        }
    }

    public static void main(String[] args) {
        GraphicsConfiguration gc = null;
        try {
            gc = GraphicsEnvironment.getLocalGraphicsEnvironment()
                                    .getDefaultScreenDevice()
                                    .getDefaultConfiguration();
        } catch (HeadlessException ignore) {
            // skip VolatileImage validation
        }

        for (Color color : COLORS) {
            int rgb = color.getRGB();
            System.err.println("Test color: " + Integer.toHexString(rgb));
            for (int type : TYPES) {
                var goldBI = new BufferedImage(SIZE, SIZE, type);
                var setPaintBI = new BufferedImage(SIZE, SIZE, type);
                var setCustomBI = new BufferedImage(SIZE, SIZE, type);

                fill(goldBI, g -> g.setColor(color));
                fill(setPaintBI, g -> g.setPaint(color));
                fill(setCustomBI, g -> g.setPaint(new CustomPaint(color)));

                if (!verify(setPaintBI, goldBI)) {
                    throw new RuntimeException("setPaintBI != goldBI");
                }

                if (!verify(setCustomBI, goldBI)) {
                    throw new RuntimeException("setCustomBI != goldBI");
                }

                if (gc == null) {
                    continue;
                }

                var goldVI = gc.createCompatibleVolatileImage(SIZE, SIZE,
                        goldBI.getTransparency());
                var setPaintVI = gc.createCompatibleVolatileImage(SIZE, SIZE,
                        goldBI.getTransparency());
                var setCustomVI = gc.createCompatibleVolatileImage(SIZE, SIZE,
                        goldBI.getTransparency());

                fill(goldVI, g -> g.setColor(color));
                fill(setPaintVI, g -> g.setPaint(color));
                fill(setCustomVI, g -> g.setPaint(new CustomPaint(color)));

                BufferedImage goldVI2BI = goldVI.getSnapshot();

                if (color.getAlpha() == 255 && !verify(goldBI, goldVI2BI)) {
                    throw new RuntimeException("goldBI != goldVI2BI");
                }

                if (!verify(setPaintVI.getSnapshot(), goldVI2BI)) {
                    throw new RuntimeException("setPaintVI != goldVI2BI");
                }

                if (!verify(setCustomVI.getSnapshot(), goldVI2BI)) {
                    throw new RuntimeException("setCustomVI != goldVI2BI");
                }
            }
        }
    }

    private static void fill(Image img, Consumer<Graphics2D> action) {
        Graphics2D g2d = (Graphics2D) img.getGraphics();
        action.accept(g2d);
        g2d.fillRect(0, 0, SIZE, SIZE);
        g2d.dispose();
    }

    private static boolean verify(BufferedImage img1, BufferedImage img2) {
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int rgb1 = img1.getRGB(x, y);
                int rgb2 = img2.getRGB(x, y);
                if (rgb1 != rgb2) {
                    System.err.println("rgb1: " + Integer.toHexString(rgb1));
                    System.err.println("rgb2: " + Integer.toHexString(rgb2));
                    return false;
                }
            }
        }
        return true;
    }
}
