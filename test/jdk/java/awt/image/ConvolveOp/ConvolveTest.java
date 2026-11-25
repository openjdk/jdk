
/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8397252
 * @summary Verify convolution after passing kernel data to JNI
 * @run main ConvolveTest
 */

import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.awt.image.WritableRaster;

public class ConvolveTest {
    public static void main(String[] args) {
        BufferedImage src = new BufferedImage(
                10, 10, BufferedImage.TYPE_INT_RGB);

        src.setRGB(5, 5, 0x123456);

        Kernel kernel = new Kernel(1, 1, new float[] {1.0f});
        ConvolveOp op = new ConvolveOp(kernel);

        BufferedImage dst = op.filter(src, null);

        if (dst.getRGB(5, 5) != src.getRGB(5, 5)) {
            throw new RuntimeException(
                    "Unexpected BufferedImage convolution result");
        }

        WritableRaster srcRaster = src.getRaster();
        WritableRaster dstRaster = op.filter(srcRaster, null);

        for (int band = 0; band < srcRaster.getNumBands(); band++) {
            int expected = srcRaster.getSample(5, 5, band);
            int actual = dstRaster.getSample(5, 5, band);

            if (actual != expected) {
                throw new RuntimeException(
                        "Unexpected Raster convolution result");
            }
        }
    }
}
