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

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DirectColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.util.Arrays;

import static java.awt.image.BufferedImage.TYPE_4BYTE_ABGR_PRE;
import static java.awt.image.BufferedImage.TYPE_INT_ARGB_PRE;

/**
 * @test
 * @bug 8279216
 * @summary Verifies implementation of premultiplied alpha
 */
public final class PremultipliedAlpha {

    private static final int SIZE = 256;

    private static final int COLOR_TOLERANCE = 2;

    private enum Type {
        TYPE_ARGB_PRE,
        TYPE_4BYTE_ABGR_PRE,
        TYPE_CUSTOM_ARGB_PRE,
        TYPE_CUSTOM_GABR_PRE,
        TYPE_CUSTOM_4BYTE_ABGR_PRE,
        TYPE_CUSTOM_4BYTE_ARGB_PRE,
        TYPE_CUSTOM_4BYTE_RGBA_PRE,
        TYPE_CUSTOM_4BYTE_GABR_PRE,
        TYPE_CUSTOM_4USHORT_8bit_ARGB_PRE,
        TYPE_CUSTOM_4INT_8bit_ARGB_PRE,
    }

    private static final ColorSpace[] CSs = {
            ColorSpace.getInstance(ColorSpace.CS_CIEXYZ),
            ColorSpace.getInstance(ColorSpace.CS_GRAY),
            ColorSpace.getInstance(ColorSpace.CS_LINEAR_RGB),
            ColorSpace.getInstance(ColorSpace.CS_PYCC),
            ColorSpace.getInstance(ColorSpace.CS_sRGB)
    };

    public static void main(String[] args) {
        for (ColorSpace cs : CSs) {
            for (Type dst : Type.values()) {
                BufferedImage gold = null;
                for (Type src : Type.values()) {
                    BufferedImage from = createSrc(src);
                    BufferedImage to = createDst(dst);
                    ColorConvertOp op = new ColorConvertOp(cs, null);
                    op.filter(from, to);
                    if (gold == null) {
                        gold = to;
                    } else {
                        validate(gold, to);
                    }
                }
            }
        }
    }

    private static void validate(BufferedImage img1, BufferedImage img2) {
        for (int a = 0; a < SIZE; a++) {
            for (int c = 0; c < SIZE; c++) {
                int[] pixel1 = img1.getRaster().getPixel(c, a, (int[]) null);
                int[] pixel2 = img2.getRaster().getPixel(c, a, (int[]) null);
                if (pixel1.length != pixel2.length) {
                    throw new RuntimeException();
                }
                for (int i = 0 ; i < pixel1.length; ++i) {
                    if (Math.abs(pixel1[i] - pixel2[i]) >= COLOR_TOLERANCE) {
                        System.out.println("c = " + c);
                        System.out.println("a = " + a);
                        System.err.println("rgb1 = " + Arrays.toString(pixel1));
                        System.err.println("rgb2 = " + Arrays.toString(pixel2));
                        throw new RuntimeException();
                    }
                }
            }
        }
    }

    private static BufferedImage createDst(Type type) {
        BufferedImage img = createSrc(type);
        Graphics2D g = img.createGraphics();
        g.setComposite(AlphaComposite.Clear);
        g.fillRect(0, 0, SIZE, SIZE);
        g.dispose();
        return img;
    }

    private static BufferedImage createSrc(Type type) {
        BufferedImage bi = switch (type) {
            case TYPE_ARGB_PRE -> new BufferedImage(SIZE, SIZE, TYPE_INT_ARGB_PRE);
            case TYPE_CUSTOM_ARGB_PRE -> TYPE_ARGB_PRE();
            case TYPE_CUSTOM_GABR_PRE -> TYPE_GABR_PRE();
            case TYPE_4BYTE_ABGR_PRE -> new BufferedImage(SIZE, SIZE, TYPE_4BYTE_ABGR_PRE);
            case TYPE_CUSTOM_4BYTE_ARGB_PRE -> TYPE_4BYTE_ARGB_PRE();
            case TYPE_CUSTOM_4BYTE_ABGR_PRE -> TYPE_4BYTE_ABGR_PRE();
            case TYPE_CUSTOM_4BYTE_RGBA_PRE -> TYPE_4BYTE_RGBA_PRE();
            case TYPE_CUSTOM_4BYTE_GABR_PRE -> TYPE_4BYTE_GABR_PRE();
            case TYPE_CUSTOM_4USHORT_8bit_ARGB_PRE -> TYPE_4USHORT_ARGB_8bit_PRE();
            case TYPE_CUSTOM_4INT_8bit_ARGB_PRE -> TYPE_4INT_ARGB_8bit_PRE();
        };
        fill(bi);
        return bi;
    }

    private static BufferedImage TYPE_ARGB_PRE() {
        ColorModel colorModel = new DirectColorModel(
                ColorSpace.getInstance(ColorSpace.CS_sRGB),
                32,
                0x00ff0000, // Red
                0x0000ff00, // Green
                0x000000ff, // Blue
                0xff000000, // Alpha
                true,       // Alpha Premultiplied
                DataBuffer.TYPE_INT
        );
        WritableRaster raster = colorModel.createCompatibleWritableRaster(SIZE,
                                                                          SIZE);
        return new BufferedImage(colorModel, raster, true, null);
    }

    private static BufferedImage TYPE_GABR_PRE() {
        ColorModel colorModel = new DirectColorModel(
                ColorSpace.getInstance(ColorSpace.CS_sRGB),
                32,
                0x000000ff, // Red
                0xff000000, // Green
                0x0000ff00, // Blue
                0x00ff0000, // Alpha
                true,       // Alpha Premultiplied
                DataBuffer.TYPE_INT
        );
        WritableRaster raster = colorModel.createCompatibleWritableRaster(SIZE,
                                                                          SIZE);
        return new BufferedImage(colorModel, raster, true, null);
    }

    private static BufferedImage TYPE_4BYTE_RGBA_PRE() {
        ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_sRGB);
        int[] nBits = {8, 8, 8, 8};
        int[] bOffs = {0, 1, 2, 3};
        ColorModel colorModel = new ComponentColorModel(cs, nBits, true, true,
                                                        Transparency.TRANSLUCENT,
                                                        DataBuffer.TYPE_BYTE);
        WritableRaster raster = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE,
                                                               SIZE, SIZE,
                                                               SIZE * 4, 4,
                                                               bOffs, null);
        return new BufferedImage(colorModel, raster, true, null);
    }

    private static BufferedImage TYPE_4BYTE_ABGR_PRE() {
        ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_sRGB);
        int[] nBits = {8, 8, 8, 8};
        int[] bOffs = {3, 2, 1, 0};
        ColorModel colorModel = new ComponentColorModel(cs, nBits, true, true,
                                                        Transparency.TRANSLUCENT,
                                                        DataBuffer.TYPE_BYTE);
        WritableRaster raster = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE,
                                                               SIZE, SIZE,
                                                               SIZE * 4, 4,
                                                               bOffs, null);
        return new BufferedImage(colorModel, raster, true, null);
    }

    private static BufferedImage TYPE_4BYTE_ARGB_PRE() {
        ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_sRGB);
        int[] nBits = {8, 8, 8, 8};
        int[] bOffs = {1, 2, 3, 0};
        ColorModel colorModel = new ComponentColorModel(cs, nBits, true, true,
                                                        Transparency.TRANSLUCENT,
                                                        DataBuffer.TYPE_BYTE);
        WritableRaster raster = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE,
                                                               SIZE, SIZE,
                                                               SIZE * 4, 4,
                                                               bOffs, null);
        return new BufferedImage(colorModel, raster, true, null);
    }

    private static BufferedImage TYPE_4BYTE_GABR_PRE() {
        ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_sRGB);
        int[] nBits = {8, 8, 8, 8};
        int[] bOffs = {3, 0, 2, 1};
        ColorModel colorModel = new ComponentColorModel(cs, nBits, true, false,
                                                        Transparency.TRANSLUCENT,
                                                        DataBuffer.TYPE_BYTE);
        WritableRaster raster = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE,
                                                               SIZE, SIZE,
                                                               SIZE * 4, 4,
                                                               bOffs, null);
        return new BufferedImage(colorModel, raster, true, null);
    }

    private static BufferedImage TYPE_4INT_ARGB_8bit_PRE() {
        ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_sRGB);
        int[] nBits = {8, 8, 8, 8};
        ColorModel colorModel = new ComponentColorModel(cs, nBits, true, true,
                                                        Transparency.TRANSLUCENT,
                                                        DataBuffer.TYPE_INT);
        WritableRaster raster = colorModel.createCompatibleWritableRaster(SIZE,
                                                                          SIZE);
        return new BufferedImage(colorModel, raster, true, null);
    }

    private static BufferedImage TYPE_4USHORT_ARGB_8bit_PRE() {
        ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_sRGB);
        int[] nBits = {8, 8, 8, 8};
        ColorModel colorModel = new ComponentColorModel(cs, nBits, true, true,
                                                        Transparency.TRANSLUCENT,
                                                        DataBuffer.TYPE_USHORT);
        WritableRaster raster = colorModel.createCompatibleWritableRaster(SIZE,
                                                                          SIZE);
        return new BufferedImage(colorModel, raster, true, null);
    }

    private static void fill(BufferedImage image) {
//        Graphics2D g = image.createGraphics();
//        g.setComposite(AlphaComposite.Src);
        for (int a = 0; a < SIZE; ++a) {
            for (int c = 0; c < SIZE; ++c) {
                Color c1 = new Color(a, c, c, a);
//                g.setColor(c1);
//TODO CANNOT USE fillrect, does not work for custom types!
//                g.fillRect(c, a, 1, 1);
                image.setRGB(c,a , c1.getRGB());
            }
        }
//        g.dispose();
    }
}
