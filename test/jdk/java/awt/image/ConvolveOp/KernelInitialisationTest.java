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

/*
 * @test
 * @bug     8368729
 * @summary Tests that passing invalid values to Kernel constructor
 *          throws only IllegalArgumentException or NullPointerException
 */

import java.awt.image.Kernel;

public class KernelInitialisationTest {

    private static void test(int width, int height, float[] data,
                             Class<?> expected)
    {
        System.out.printf("Testing for width: %d, height: %d, data: %s%n",
                          width, height, data == null ? "null" : "not null");
        Class<?> actual = null;
        try {
            new Kernel(width, height, data);
        } catch (Exception e) {
            actual = e.getClass();
        }
        if (actual != expected) {
            System.err.println("Expected: " + expected);
            System.err.println("Actual: " + actual);
            throw new RuntimeException("Test failed");
        }
    }

    private static void testIAE(int width, int height, int len) {
        test(width, height, new float[len], IllegalArgumentException.class);
    }

    private static void testNPE(int width, int height) {
        test(width, height, null, NullPointerException.class);
    }

    public static void main(String[] args) {
        int[][] sizes = {{-1, 1}, {1, -1}, {-1, -1}, {50, Integer.MAX_VALUE}};
        int[] lens = {1, 100};
        for (int[] kernelSize : sizes) {
            for (int len : lens) {
                testIAE(kernelSize[0], kernelSize[1], len);
            }
            testNPE(kernelSize[0], kernelSize[1]);
        }
        testNPE(10, 10);     // NPE on valid width and height
        testIAE(10, 10, 10); // IAE on valid width and height but small data
    }
}
