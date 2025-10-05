/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;
import static java.awt.image.BufferedImage.*;

/**
 * @test
 * @bug 4617681
 * @summary Verify BufferedImage Constructor behaviour with
 *          invalid image size and type parameters.
 */

public class CreateBufferedImageTest {

    static byte[] s = new byte[16];
    static IndexColorModel icm = new IndexColorModel(8, 16, s, s, s);

    public static void main(String args[]) {

        test(TYPE_CUSTOM, 10); // TYPE_CUSTOM is not a valid parameter.
        test(-1, 10);
        test(10001, 10);

        for (int t = TYPE_INT_RGB; t <= TYPE_BYTE_INDEXED; t++) {
           test(t, 50_000); // 50_000 ^ 2 will overflow int.
        }
        test(TYPE_3BYTE_BGR, 30_000); // 3 * (30_000 ^ 2) will overflow int
        test(TYPE_4BYTE_ABGR, 25_000); // 4 * (25_000 ^ 2) will overflow int
        test(TYPE_4BYTE_ABGR_PRE, 25_000);

        testIndexed(TYPE_INT_RGB, 10);
        testIndexed(TYPE_CUSTOM, 10);
        testIndexed(-1, 10);
        testIndexed(10001, 10);
        testIndexed(TYPE_BYTE_BINARY, 50_000);
        testIndexed(TYPE_BYTE_INDEXED, 50_000);

        // Verify that IAE is thrown if constructing using a raster with x/y != 0
        BufferedImage bi = new BufferedImage(TYPE_INT_RGB, 10, 10);
        WritableRaster raster = bi.getRaster().createCompatibleWritableRaster(20, 20, 1, 1);
        try {
            bi = new BufferedImage(bi.getColorModel(), raster, true, null);
            throw new RuntimeException("No expected exception for invalid min x/y");
        } catch (IllegalArgumentException e) {
           System.out.println("Expected exception thrown for invalid raster min x/y");
           System.out.println(e);
        }
    }

    static void test(int t, int sz) {
        try {
            new BufferedImage(sz, sz, t);
            throw new RuntimeException("No expected exception for type = " + t);
        } catch (IllegalArgumentException e) {
            System.out.println("Expected exception thrown");
            System.out.println(e);
        } catch (NegativeArraySizeException n) {
            checkIsOldVersion(26, n);
        }
    }

    static void testIndexed(int t, int sz) {
        try {
            new BufferedImage(sz, sz, t, icm);
            throw new RuntimeException("No expected exception for type = " + t);
        } catch (IllegalArgumentException e) {
            System.out.println("Expected exception thrown");
            System.out.println(e);
        }
    }

    /**
      * If running on a JDK of the targetVersion or later, throw
      * a RuntimeException becuase the exception argument
      * should not have occured. However it is expected on
      * prior versions because that was the previous behaviour.
      * @param targetVersion to check
      * @param t the thrown exception to print
      */
    static void checkIsOldVersion(int targetVersion, Throwable t) {
        String version = System.getProperty("java.version");
        version = version.split("\\D")[0];
        int v = Integer.parseInt(version);
        if (v >= targetVersion) {
            t.printStackTrace();
            throw new RuntimeException(
                           "Unexpected exception for version " + v);
        }
    }

}
