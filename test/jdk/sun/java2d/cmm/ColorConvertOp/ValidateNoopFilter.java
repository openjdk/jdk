/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.File;

import javax.imageio.ImageIO;

import static java.awt.image.BufferedImage.TYPE_3BYTE_BGR;
import static java.awt.image.BufferedImage.TYPE_4BYTE_ABGR;
import static java.awt.image.BufferedImage.TYPE_4BYTE_ABGR_PRE;
import static java.awt.image.BufferedImage.TYPE_BYTE_BINARY;
import static java.awt.image.BufferedImage.TYPE_BYTE_GRAY;
import static java.awt.image.BufferedImage.TYPE_BYTE_INDEXED;
import static java.awt.image.BufferedImage.TYPE_CUSTOM;
import static java.awt.image.BufferedImage.TYPE_INT_ARGB;
import static java.awt.image.BufferedImage.TYPE_INT_ARGB_PRE;
import static java.awt.image.BufferedImage.TYPE_INT_BGR;
import static java.awt.image.BufferedImage.TYPE_INT_RGB;
import static java.awt.image.BufferedImage.TYPE_USHORT_555_RGB;
import static java.awt.image.BufferedImage.TYPE_USHORT_565_RGB;
import static java.awt.image.BufferedImage.TYPE_USHORT_GRAY;

/**
 * @test
 * @bug 8295430
 * @summary Compare the SRGB->SRGB and SRGB->CUSTOM conversions when CUSTOM=SRGB
 */
public final class ValidateNoopFilter {

    private static final int[] TYPES = {
            TYPE_INT_RGB, TYPE_INT_ARGB, TYPE_INT_ARGB_PRE, TYPE_INT_BGR,
            TYPE_3BYTE_BGR, TYPE_4BYTE_ABGR, TYPE_4BYTE_ABGR_PRE,
            TYPE_USHORT_565_RGB, TYPE_USHORT_555_RGB, TYPE_BYTE_GRAY,
            TYPE_USHORT_GRAY, TYPE_BYTE_BINARY, TYPE_BYTE_INDEXED, TYPE_CUSTOM
    };

    private static final int W = 511;
    private static final int H = 255;

    public static void main(String[] args) throws Exception {
        byte[] data = ICC_Profile.getInstance(ColorSpace.CS_sRGB).getData();
        ColorSpace custom = new ICC_ColorSpace(ICC_Profile.getInstance(data));
        ColorSpace srgb = ColorSpace.getInstance(ColorSpace.CS_sRGB);

        ColorConvertOp opCustom = new ColorConvertOp(srgb, custom, null);
        ColorConvertOp opGold = new ColorConvertOp(srgb, srgb, null);

        for (int type : TYPES) {
            BufferedImage src = makeBi(type);
            BufferedImage dst = makeBi(type);
            BufferedImage dstGold = makeBi(type);

            fill(src);

            opCustom.filter(src, dst);
            opGold.filter(src, dstGold);
            // validate images
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

    private static BufferedImage makeBi(int type) {
        if (type == TYPE_CUSTOM) {
            return makeCustom3BYTE_BGR();
        } else {
            return new BufferedImage(W, H, type);
        }
    }

    /**
     * Returns the custom buffered image, which mostly identical to
     * BufferedImage.(w,h,TYPE_3BYTE_BGR), but uses the bigger scanlineStride.
     * This means that the raster will have gaps, between the rows.
     */
    private static BufferedImage makeCustom3BYTE_BGR() {
        ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_sRGB);
        int[] nBits = {8, 8, 8};
        int[] bOffs = {2, 1, 0};
        ColorModel colorModel = new ComponentColorModel(cs, nBits, false, false,
                                                        Transparency.OPAQUE,
                                                        DataBuffer.TYPE_BYTE);
        WritableRaster raster = Raster.createInterleavedRaster(
                DataBuffer.TYPE_BYTE, W, H, W * 3 + 2, 3, bOffs, null);
        return new BufferedImage(colorModel, raster, true, null);
    }

    private static void fill(Image image) {
        Graphics2D graphics = (Graphics2D) image.getGraphics();
        graphics.setComposite(AlphaComposite.Src);
        for (int i = 0; i < image.getHeight(null); ++i) {
            graphics.setColor(new Color(i, 0, 255 - i));
            graphics.fillRect(0, i, image.getWidth(null), 1);
        }
        graphics.dispose();
    }
}
