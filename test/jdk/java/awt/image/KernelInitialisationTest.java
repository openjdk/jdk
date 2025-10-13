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
 * @bug  8368729
 * @summary Test that passing invalid values to Kernel constructor
 *          throws appropriate exception
 */

import java.awt.image.Kernel;

public class KernelInitialisationTest {
    private static boolean failed;

    private static void expectException(Class<? extends Exception>
                                            expectedException,
                                        Runnable code) {
        try {
            code.run();
            System.out.println("Expected " + expectedException.getName() +
                " but no exception was thrown");
            failed = true;
        } catch (Exception e) {
            if (!expectedException.isInstance(e)) {
                System.out.println("Expected " + expectedException.getName() +
                    " but got " + e.getClass().getName());
                failed = true;
            }
        }
    }

    private static void testKernel(int width, int height, float[] data,
                                   Class<? extends Exception>
                                       expectedException) {
        System.out.println("Testing for width: " + width + ", height: "
            + height + ", data: " + (data == null ? "null" : "not null"));
        expectException(expectedException,
            () -> new Kernel(width, height, data));
    }

    public static void main(String[] args) {
        testKernel(-1, 1, new float[100], IllegalArgumentException.class);
        testKernel(1, -1, new float[100], IllegalArgumentException.class);
        testKernel(-1, -1, new float[100], IllegalArgumentException.class);
        testKernel(1, 1, null, IllegalArgumentException.class);

        int width = 5;
        int height = Integer.MAX_VALUE;
        testKernel(width, height, new float[100], ArithmeticException.class);

        if (failed) {
            throw new RuntimeException("Didn't receive expected Exception in" +
                " one or more tests");
        }
    }
}
