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

import java.awt.image.BufferedImage;

/**
 * @test
 * @bug 8318850
 * @summary Checks the total number of bands of image data
 */
public final class VerifyNumBands {

    public static void main(String[] args) {
        test(BufferedImage.TYPE_INT_RGB, 3);
        test(BufferedImage.TYPE_INT_ARGB, 4);
        test(BufferedImage.TYPE_INT_ARGB_PRE, 4);
        test(BufferedImage.TYPE_INT_BGR, 3);
        test(BufferedImage.TYPE_3BYTE_BGR, 3);
        test(BufferedImage.TYPE_4BYTE_ABGR, 4);
        test(BufferedImage.TYPE_4BYTE_ABGR_PRE, 4);
        test(BufferedImage.TYPE_USHORT_565_RGB, 3);
        test(BufferedImage.TYPE_USHORT_555_RGB, 3);
        test(BufferedImage.TYPE_BYTE_GRAY, 1);
        test(BufferedImage.TYPE_USHORT_GRAY, 1);
    }

    private static void test(int type, int expected) {
        BufferedImage bi = new BufferedImage(1, 1, type);
        int numBands = bi.getRaster().getSampleModel().getNumBands();
        if (numBands != expected) {
            System.err.println("Expected: " + expected);
            System.err.println("Actual: " + numBands);
            throw new RuntimeException("wrong number of bands");
        }
    }
}
