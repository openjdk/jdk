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
public class NonICCFilterTest {

    private static class TestColorSpace extends ColorSpace {

        private final ColorSpace csRGB;

        protected TestColorSpace(boolean bSrc) {
            super(ColorSpace.getInstance(ColorSpace.CS_sRGB).getType(),
                    ColorSpace.getInstance(ColorSpace.CS_sRGB).getNumComponents());
            csRGB = ColorSpace.getInstance(bSrc ? ColorSpace.CS_LINEAR_RGB :
                    ColorSpace.CS_sRGB);
        }

        public float[] toRGB(float[] colorvalue) {
            return colorvalue;
        }

        public float[] fromRGB(float[] rgbvalue) {
            return rgbvalue;
        }

        public float[] toCIEXYZ(float[] colorvalue) {
            return csRGB.toCIEXYZ(csRGB.toRGB(colorvalue));
        }

        public float[] fromCIEXYZ(float[] xyzvalue) {
            return csRGB.fromRGB(csRGB.fromCIEXYZ(xyzvalue));
        }
    }

    private static BufferedImage createTestImage(boolean isSrc) {
        ColorSpace cs = new TestColorSpace(isSrc);
        ComponentColorModel cm = new ComponentColorModel(cs, false, false,
                Transparency.OPAQUE, DataBuffer.TYPE_FLOAT);
        WritableRaster raster = cm.createCompatibleWritableRaster(50, 50);
        BufferedImage img = new BufferedImage(cm, raster, false, null);

        Graphics2D g = img.createGraphics();
        GradientPaint gp = new GradientPaint(0, 0, Color.GREEN,
                raster.getWidth(), raster.getHeight(), Color.BLUE);
        g.setPaint(gp);
        g.fillRect(0, 0, raster.getWidth(), raster.getHeight());
        g.dispose();

        return img;
    }

    private static boolean compareImages(BufferedImage src, BufferedImage dest) {
        for (int x = 0; x < src.getWidth(); x++) {
            for (int y = 0; y < src.getHeight(); y++) {
                if (src.getRGB(x, y) != dest.getRGB(x, y)) {
                    return false;
                }
            }
        }
        return true;
    }

    public static void main(String[] args) {
        BufferedImage src, dest;
        src = createTestImage(true);
        dest = createTestImage(false);

        ColorConvertOp ccop =
                new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_sRGB), null);
        ccop.filter(src, dest);

        if (compareImages(src, dest)) {
            throw new RuntimeException("Test failed: Source equal to Destination");
        }
    }
}
