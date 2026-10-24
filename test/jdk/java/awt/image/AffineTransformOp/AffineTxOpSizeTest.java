/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 4690476
 * @summary Verify behaviour with transform which creates too large an image.
 */

import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import static java.awt.image.AffineTransformOp.*;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.awt.image.RasterFormatException;

public class AffineTxOpSizeTest {

    static final int W = 2552, H = 3300;
    // This transform will require an approx 60_000 x 60_000 raster which is too large
    static final AffineTransform AT = new AffineTransform(0.2, 23, 18, 0.24, -70.0, -90.0);

    public static void main(String[] args) {
        BufferedImage src = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        testAOP(src, TYPE_BICUBIC);
        testAOP(src, TYPE_BILINEAR);
        testAOP(src, TYPE_NEAREST_NEIGHBOR);
    }

    static void testAOP(BufferedImage src, int iType) {
        AffineTransformOp aop = new AffineTransformOp(AT, iType);
        System.out.println("Bounds=" + aop.getBounds2D(src));

        aop.filter(src, null);
        aop.filter(src.getRaster(), null);
        try {
             aop.createCompatibleDestImage(src, src.getColorModel());
             throw new RuntimeException("No exception for image");
        } catch (RasterFormatException e) {
        }
        try {
             aop.createCompatibleDestRaster(src.getRaster());
             throw new RuntimeException("No exception for raster");
        } catch (RasterFormatException e) {
        }
  }

}
