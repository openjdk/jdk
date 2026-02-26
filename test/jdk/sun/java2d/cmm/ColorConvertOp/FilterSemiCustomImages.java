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
import java.awt.Point;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.ColorModel;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.io.File;

import javax.imageio.ImageIO;

import static java.awt.image.BufferedImage.TYPE_3BYTE_BGR;
import static java.awt.image.BufferedImage.TYPE_4BYTE_ABGR;
import static java.awt.image.BufferedImage.TYPE_4BYTE_ABGR_PRE;
import static java.awt.image.BufferedImage.TYPE_INT_ARGB;
import static java.awt.image.BufferedImage.TYPE_INT_ARGB_PRE;
import static java.awt.image.BufferedImage.TYPE_INT_BGR;
import static java.awt.image.BufferedImage.TYPE_INT_RGB;
import static java.awt.image.BufferedImage.TYPE_USHORT_GRAY;

/**
 * @test
 * @bug 8366208
 * @summary Verifies ColorConvertOp works correctly with BufferedImage and
 *          semi-custom raster
 */
public final class FilterSemiCustomImages {

    private static final int W = 144;
    private static final int H = 123;

    private static final int[] TYPES = {
            TYPE_INT_RGB, TYPE_INT_ARGB, TYPE_INT_ARGB_PRE, TYPE_INT_BGR,
            TYPE_3BYTE_BGR, TYPE_4BYTE_ABGR, TYPE_4BYTE_ABGR_PRE,
            TYPE_USHORT_GRAY
    };

    private static final int[] CSS = {
            ColorSpace.CS_CIEXYZ, ColorSpace.CS_GRAY, ColorSpace.CS_LINEAR_RGB,
            ColorSpace.CS_PYCC, ColorSpace.CS_sRGB
    };

    private static final class CustomRaster extends WritableRaster {
        CustomRaster(SampleModel sampleModel, Point origin) {
            super(sampleModel, origin);
        }
    }

    public static void main(String[] args) throws Exception {
        for (int fromIndex : CSS) {
            for (int toIndex : CSS) {
                if (fromIndex != toIndex) {
                    for (int type : TYPES) {
                        test(fromIndex, toIndex, type);
                    }
                }
            }
        }
    }

    private static void test(int fromIndex, int toIndex, int type)
            throws Exception
    {
        ColorSpace fromCS = ColorSpace.getInstance(fromIndex);
        ColorSpace toCS = ColorSpace.getInstance(toIndex);
        ColorConvertOp op = new ColorConvertOp(fromCS, toCS, null);

        // standard source -> standard dst
        BufferedImage srcGold = new BufferedImage(W, H, type);
        fill(srcGold);
        BufferedImage dstGold = new BufferedImage(W, H, type);
        op.filter(srcGold, dstGold);

        // custom source -> standard dst
        BufferedImage srcCustom = makeCustomBI(srcGold);
        fill(srcCustom);
        BufferedImage dst = new BufferedImage(W, H, type);
        op.filter(srcCustom, dst);
        verify(dstGold, dst);

        // standard source -> custom dst
        BufferedImage src = new BufferedImage(W, H, type);
        fill(src);
        BufferedImage dstCustom = makeCustomBI(dstGold);
        op.filter(src, dstCustom);
        verify(dstGold, dstCustom);

        // custom source -> custom dst
        srcCustom = makeCustomBI(srcGold);
        fill(srcCustom);
        dstCustom = makeCustomBI(dstGold);
        op.filter(srcCustom, dstCustom);
        verify(dstGold, dstCustom);
    }

    private static BufferedImage makeCustomBI(BufferedImage bi) {
        ColorModel cm = bi.getColorModel();
        SampleModel sm = bi.getSampleModel();
        CustomRaster cr = new CustomRaster(sm, new Point());
        return new BufferedImage(cm, cr, bi.isAlphaPremultiplied(), null) {
            @Override
            public int getType() {
                return bi.getType();
            }
        };
    }

    private static void fill(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        for (int x = 0; x < width; ++x) {
            for (int y = 0; y < height; ++y) {
                // alpha channel may be calculated slightly differently on
                // different code paths, so only check fully transparent and
                // fully opaque pixels
                Color c = new Color(y * 255 / (height - 1),
                                    x * 255 / (width - 1),
                                    x % 255,
                                    (x % 2 == 0) ? 0 : 255);
                image.setRGB(x, y, c.getRGB());
            }
        }
    }

    private static void verify(BufferedImage dstGold, BufferedImage dst)
            throws Exception
    {
        for (int x = 0; x < W; ++x) {
            for (int y = 0; y < H; ++y) {
                if (dst.getRGB(x, y) != dstGold.getRGB(x, y)) {
                    ImageIO.write(dst, "png", new File("custom.png"));
                    ImageIO.write(dstGold, "png", new File("gold.png"));
                    throw new RuntimeException("Test failed.");
                }
            }
        }
    }
}
