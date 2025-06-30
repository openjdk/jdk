/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import sun.awt.image.SurfaceManager;
import sun.java2d.SurfaceData;

import java.awt.color.ColorSpace;
import java.awt.image.*;
import java.util.Objects;

import static java.awt.image.BufferedImage.*;
import static java.awt.image.DataBuffer.*;

/*
 * @test
 * @bug 8353542
 * @modules java.desktop/sun.java2d java.desktop/sun.awt.image
 * @summary Test initialization of native raster data.
 */
public class NativeRasterDataTest {

    public static void main(String[] args) {

        // Test built-in types.
        check(TYPE_INT_RGB);
        check(TYPE_INT_ARGB);
        check(TYPE_INT_ARGB_PRE);
        check(TYPE_INT_BGR);
        check(TYPE_3BYTE_BGR);
        check(TYPE_4BYTE_ABGR);
        check(TYPE_4BYTE_ABGR_PRE);
        check(TYPE_USHORT_565_RGB);
        check(TYPE_USHORT_555_RGB);
        check(TYPE_BYTE_GRAY);
        check(TYPE_USHORT_GRAY);

        // Test INT_DCM.
        ColorSpace srgb = ColorSpace.getInstance(ColorSpace.CS_sRGB);
        check(new DirectColorModel(srgb, 32, 0xff0000, 0xff00, 0xff, 0xff000000, true, TYPE_INT), null);
        check(new DirectColorModel(srgb, 32, 0xff, 0xff00, 0xff0000, 0xff000000, true, TYPE_INT), null);
        check(new DirectColorModel(srgb, 32, 0xff000000, 0xff0000, 0xff00, 0xff, true, TYPE_INT), null);
        check(new DirectColorModel(srgb, 32, 0xff00, 0xff0000, 0xff000000, 0xff, true, TYPE_INT), null);
        check(new DirectColorModel(srgb, 24, 0xff0000, 0xff00, 0xff,       0, false, TYPE_INT), null);
        check(new DirectColorModel(srgb, 24, 0xff, 0xff00, 0xff0000,       0, false, TYPE_INT), null);
        check(new DirectColorModel(srgb, 32, 0xff000000, 0xff0000, 0xff00, 0, false, TYPE_INT), null);
        check(new DirectColorModel(srgb, 32, 0xff00, 0xff0000, 0xff000000, 0, false, TYPE_INT), null);

        // Test pixel-interleaved models.
        int[] RGBA = {0, 1, 2, 3};
        int[] BGRA = {2, 1, 0, 3};
        int[] ARGB = {1, 2, 3, 0};
        int[] ABGR = {3, 2, 1, 0};
        int[] RGB = {0, 1, 2};
        int[] BGR = {2, 1, 0};

        check(new ComponentColorModel(srgb, true, true, TRANSLUCENT, TYPE_USHORT),
                Raster.createInterleavedRaster(TYPE_USHORT, 10, 10, 40, 4, RGBA, null));
        check(new ComponentColorModel(srgb, true, true, TRANSLUCENT, TYPE_USHORT),
                Raster.createInterleavedRaster(TYPE_USHORT, 10, 10, 40, 4, BGRA, null));
        check(new ComponentColorModel(srgb, true, true, TRANSLUCENT, TYPE_USHORT),
                Raster.createInterleavedRaster(TYPE_USHORT, 10, 10, 40, 4, ARGB, null));
        check(new ComponentColorModel(srgb, true, true, TRANSLUCENT, TYPE_USHORT),
                Raster.createInterleavedRaster(TYPE_USHORT, 10, 10, 40, 4, ABGR, null));
        check(new ComponentColorModel(srgb, false, false, OPAQUE, TYPE_USHORT),
                Raster.createInterleavedRaster(TYPE_USHORT, 10, 10, 40, 4, RGB, null));
        check(new ComponentColorModel(srgb, false, false, OPAQUE, TYPE_USHORT),
                Raster.createInterleavedRaster(TYPE_USHORT, 10, 10, 40, 4, BGR, null));
        check(new ComponentColorModel(srgb, false, false, OPAQUE, TYPE_USHORT),
                Raster.createInterleavedRaster(TYPE_USHORT, 10, 10, 30, 3, RGB, null));
        check(new ComponentColorModel(srgb, false, false, OPAQUE, TYPE_USHORT),
                Raster.createInterleavedRaster(TYPE_USHORT, 10, 10, 30, 3, BGR, null));

        check(new ComponentColorModel(srgb, true, true, TRANSLUCENT, TYPE_BYTE),
                Raster.createInterleavedRaster(TYPE_BYTE, 10, 10, 40, 4, RGBA, null));
        check(new ComponentColorModel(srgb, true, true, TRANSLUCENT, TYPE_BYTE),
                Raster.createInterleavedRaster(TYPE_BYTE, 10, 10, 40, 4, BGRA, null));
        check(new ComponentColorModel(srgb, true, true, TRANSLUCENT, TYPE_BYTE),
                Raster.createInterleavedRaster(TYPE_BYTE, 10, 10, 40, 4, ARGB, null));
        check(new ComponentColorModel(srgb, true, true, TRANSLUCENT, TYPE_BYTE),
                Raster.createInterleavedRaster(TYPE_BYTE, 10, 10, 40, 4, ABGR, null));
        check(new ComponentColorModel(srgb, false, false, OPAQUE, TYPE_BYTE),
                Raster.createInterleavedRaster(TYPE_BYTE, 10, 10, 40, 4, RGB, null));
        check(new ComponentColorModel(srgb, false, false, OPAQUE, TYPE_BYTE),
                Raster.createInterleavedRaster(TYPE_BYTE, 10, 10, 40, 4, BGR, null));
        check(new ComponentColorModel(srgb, false, false, OPAQUE, TYPE_BYTE),
                Raster.createInterleavedRaster(TYPE_BYTE, 10, 10, 30, 3, RGB, null));
        check(new ComponentColorModel(srgb, false, false, OPAQUE, TYPE_BYTE),
                Raster.createInterleavedRaster(TYPE_BYTE, 10, 10, 30, 3, BGR, null));
    }

    private static void check(ColorModel colorModel, WritableRaster raster) {
        check(new BufferedImage(colorModel, raster != null ? raster : colorModel.createCompatibleWritableRaster(10, 10),
                colorModel.isAlphaPremultiplied(), null));
    }

    private static void check(int imageType) {
        check(new BufferedImage(10, 10, imageType));
    }

    private static void check(BufferedImage image) {
        SurfaceData sd = Objects.requireNonNull(SurfaceManager.getManager(image).getPrimarySurfaceData());
        if (sd.getNativeOps() == 0) throw new Error("getNativeOps() == 0");
    }
}
