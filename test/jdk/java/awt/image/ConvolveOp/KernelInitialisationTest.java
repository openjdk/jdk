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
 *          throws only IllegalArgumentException
 */

import java.awt.image.Kernel;

public class KernelInitialisationTest {
    private static void expectIllegalArgumentException(Runnable code) {
        try {
            code.run();
            throw new RuntimeException("Expected IllegalArgumentException" +
                " but no exception was thrown");
        } catch (IllegalArgumentException e) {
            // we expect IllegalArgumentException
        }
    }

    private static void testKernel(int width, int height, float[] data) {
        System.out.println("Testing for width: " + width + ", height: "
            + height + ", data: " + (data == null ? "null" : "not null"));
        expectIllegalArgumentException(() -> new Kernel(width, height, data));
    }

    public static void main(String[] args) {
        testKernel(-1, 1, new float[100]);
        testKernel(1, -1, new float[100]);
        testKernel(-1, -1, new float[100]);
        testKernel(1, 1, null);

        int width = 50;
        int height = Integer.MAX_VALUE;
        testKernel(width, height, new float[100]);
    }
}
