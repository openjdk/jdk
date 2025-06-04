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

import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;

/**
 * @test
 * @bug 8312191
 * @summary Standard color spaces should be reused for CompatibleDestImage
 */
public final class CompatibleColorSpace {

    private static final int[] spaces = {
            ColorSpace.CS_CIEXYZ, ColorSpace.CS_GRAY, ColorSpace.CS_LINEAR_RGB,
            ColorSpace.CS_PYCC, ColorSpace.CS_sRGB
    };

    public static void main(String[] args) {
        for (int from : spaces) {
            for (int to : spaces) {
                test(from, to);
            }
        }
    }

    private static void test(int from, int to) {
        ColorSpace srcCS = ColorSpace.getInstance(from);
        ColorSpace dstCS = ColorSpace.getInstance(to);
        ColorConvertOp op = new ColorConvertOp(srcCS, dstCS, null);
        BufferedImage src = new BufferedImage(10, 10,
                                              BufferedImage.TYPE_INT_ARGB);
        BufferedImage dst = op.filter(src, null);
        // dst image is not set and will be created automatically, the dstCS
        // should be reused
        if (dst.getColorModel().getColorSpace() != dstCS) {
            throw new RuntimeException("Wrong color space");
        }
    }
}
