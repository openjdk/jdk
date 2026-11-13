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
 * @summary Reject inconsistent convolution kernel dimensions in medialib
 * @modules java.desktop/sun.awt.image
 *          java.desktop/java.awt.image:+open
 * @run main/othervm InvalidKernelDimensions
 */

import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.lang.reflect.Field;
import java.util.Arrays;
import sun.awt.image.ImagingLib;

public class InvalidKernelDimensions {

    private static final int EDGE = ConvolveOp.EDGE_NO_OP;
    private static final BufferedImage SRC =
            new BufferedImage(16, 16, BufferedImage.TYPE_BYTE_GRAY);
    private static final BufferedImage DST =
            new BufferedImage(16, 16, BufferedImage.TYPE_BYTE_GRAY);

    private static Kernel kernel(int width, int height, int length)
            throws Exception {
        Kernel result = new Kernel(1, 1, new float[] {1});

        Field field = Kernel.class.getDeclaredField("width");
        field.setAccessible(true);
        field.setInt(result, width);

        field = Kernel.class.getDeclaredField("height");
        field.setAccessible(true);
        field.setInt(result, height);

        field = Kernel.class.getDeclaredField("data");
        field.setAccessible(true);
        float[] data = new float[length];
        Arrays.fill(data, 1.0f);
        field.set(result, data);
        return result;
    }

    private static void check(Kernel kernel, int expected) {
        int imageResult = ImagingLib.convolveBI(SRC, DST, kernel, EDGE);
        if (imageResult != expected) {
            throw new AssertionError("convolveBI returned " + imageResult
                    + ", expected " + expected);
        }

        int rasterResult = ImagingLib.convolveRaster(
                SRC.getRaster(), DST.getRaster(), kernel, EDGE);
        if (rasterResult != expected) {
            throw new AssertionError("convolveRaster returned " + rasterResult
                    + ", expected " + expected);
        }
    }

    public static void main(String[] args) throws Exception {
        check(new Kernel(1, 1, new float[] {1}), 1);
        check(kernel(1, 1, 2), 0);
        check(kernel(3, 3, 8), 0);
        check(kernel(1, 1, 0), 0);
        check(kernel(0, 1, 1), 0);
        check(kernel(1, 0, 1), 0);
        check(kernel(-1, 1, 1), 0);
        check(kernel(1, -1, 1), 0);
        check(kernel(Integer.MAX_VALUE, 2, 1), 0);
    }
}
