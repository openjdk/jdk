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
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.WritableRaster;

/*
 * @test
 * @bug 8316497
 * @summary Verifies Color filter on Non ICC profile
 */
public final class NonICCFilterTest {

    private static final class TestColorSpace extends ColorSpace {

        private final ColorSpace cs;

        TestColorSpace(ColorSpace cs) {
            super(cs.getType(), cs.getNumComponents());
            this.cs = cs;
        }

        @Override
        public float[] toRGB(float[] colorvalue) {
            return cs.toRGB(colorvalue);
        }

        @Override
        public float[] fromRGB(float[] rgbvalue) {
            return cs.fromRGB(rgbvalue);
        }

        @Override
        public float[] toCIEXYZ(float[] colorvalue) {
            return cs.toCIEXYZ(colorvalue);
        }

        @Override
        public float[] fromCIEXYZ(float[] xyzvalue) {
            return cs.fromCIEXYZ(xyzvalue);
        }
    }

    private static BufferedImage createTestImage(boolean isSrc, boolean plain) {
        ColorSpace cs = createCS(isSrc, plain);
        ComponentColorModel cm = new ComponentColorModel(cs, false, false,
                Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
        WritableRaster raster = cm.createCompatibleWritableRaster(100, 100);
        BufferedImage img = new BufferedImage(cm, raster, false, null);

        Graphics2D g = img.createGraphics();
        GradientPaint gp = new GradientPaint(0, 0, Color.GREEN,
                raster.getWidth(), raster.getHeight(), Color.BLUE);
        g.setPaint(gp);
        g.fillRect(0, 0, raster.getWidth(), raster.getHeight());
        g.dispose();

        return img;
    }

    private static ColorSpace createCS(boolean isSrc, boolean plain) {
        ColorSpace cs = ColorSpace.getInstance(
                isSrc ? ColorSpace.CS_GRAY : ColorSpace.CS_sRGB);
        if (plain) {
            return cs;
        }
        return new TestColorSpace(cs);
    }

    private static boolean compareImages(BufferedImage destTest, BufferedImage destGold) {
        for (int x = 0; x < destTest.getWidth(); x++) {
            for (int y = 0; y < destTest.getHeight(); y++) {
                int rgb1 = destTest.getRGB(x, y);
                int rgb2 = destGold.getRGB(x, y);
                if (rgb1 != rgb2) {
                    System.err.println("rgb1 = " + Integer.toHexString(rgb1));
                    System.err.println("rgb2 = " + Integer.toHexString(rgb2));
                    return true;
                }
            }
        }
        return false;
    }

    public static void main(String[] args) {
        BufferedImage srcTest = createTestImage(true, false);
        BufferedImage destTest = createTestImage(false, false);

        BufferedImage srcGold = createTestImage(true, true);
        BufferedImage destGold = createTestImage(false, true);

        ColorSpace mid = ColorSpace.getInstance(ColorSpace.CS_PYCC);
        ColorConvertOp test = new ColorConvertOp(mid, null);
        test.filter(srcTest, destTest);

        ColorConvertOp gold = new ColorConvertOp(mid, null);
        gold.filter(srcGold, destGold);

        if (compareImages(destTest, destGold)) {
            throw new RuntimeException("Test failed");
        }
    }
}
