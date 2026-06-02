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

/**
 * @test
 * @bug 8383605
 * @summary test some out of bounds parameters for createInterleavedRaster()
 */

import java.awt.image.DataBuffer;
import java.awt.image.Raster;

public class InterleavedRasterTest {

    public static void main(String[] args) {

        int w = 1;
        int h = 1;
        int b = -1;
        test(w, h, b);

        w = 100_000;
        h = 1;
        b = 100_000;
        test(w, h, b);

        w = 10_000;
        h = 10_000;
        b = 10_000;
        test(w, h, b);
    }

    static void test(int w, int h, int b) {
        try {
            Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE, w, h, b, null);
            throw new RuntimeException("No IllegalArgumentException");
        } catch (IllegalArgumentException e) {
        }
    }
}
