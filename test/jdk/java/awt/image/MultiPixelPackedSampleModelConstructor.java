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

import static java.awt.image.DataBuffer.TYPE_BYTE;
import static java.awt.image.DataBuffer.TYPE_INT;
import static java.awt.image.DataBuffer.TYPE_SHORT;
import static java.awt.image.DataBuffer.TYPE_USHORT;

import java.awt.image.MultiPixelPackedSampleModel;
import java.awt.image.RasterFormatException;

/*
 * @test
 * @bug 8381007
 * @summary test MultiPixelPackedSampleModel Constructors
 */

public class MultiPixelPackedSampleModelConstructor {

    public static void main(String[] args) {
        for (Args4 a : args4) {
            test4(a);
        }
        for (Args6 a : args6) {
            test6(a);
        }

       // Also verify createSubsetSampleModel ignores bands.
       MultiPixelPackedSampleModel m =
            new MultiPixelPackedSampleModel(TYPE_BYTE, 1, 1, 1);
       int[] bands = new int[5];
       m.createSubsetSampleModel(bands);
    }

    static record Args4(int dType, int w, int h, int bits, Class eType) { }

    static final Args4[] args4 = {
        new Args4(TYPE_BYTE, 1, 1, -1, RasterFormatException.class),
        new Args4(TYPE_BYTE, 1, 1, 0, RasterFormatException.class),
        new Args4(TYPE_BYTE, 1, 1, 1, null),
        new Args4(TYPE_BYTE, 1, 1, 3, RasterFormatException.class),
        new Args4(TYPE_BYTE, 1, 1, 4, null),
        new Args4(TYPE_BYTE, 1, 1, 16, RasterFormatException.class),
        new Args4(TYPE_BYTE, -1, 1, 1, IllegalArgumentException.class),
        new Args4(TYPE_SHORT, -1, 1, 1, IllegalArgumentException.class),
        new Args4(TYPE_SHORT, 1, 1, 16, IllegalArgumentException.class),
        new Args4(TYPE_USHORT, 1, 1, 16, null),
        new Args4(TYPE_INT, 1, 1, 16, null),
        new Args4(TYPE_BYTE, 1, 30, 0, RasterFormatException.class),
        new Args4(TYPE_BYTE, 0, 1, 4, IllegalArgumentException.class),
        new Args4(TYPE_BYTE, 1<<29, 1, 4, null),
        new Args4(TYPE_BYTE, 1<<30, 1, 16, RasterFormatException.class),
        new Args4(TYPE_BYTE, 32, 1, 1<<30, RasterFormatException.class),
        new Args4(99, 8, 1, 1, IllegalArgumentException.class),
    };

    static record Args6(int dType, int w, int h, int bits, int stride, int bitOffset, Class eType) { }

    static final Args6[] args6 = {
        new Args6(TYPE_BYTE, 1, 1, -1, 1, 0, RasterFormatException.class),
        new Args6(TYPE_BYTE, 1, 1, 0, 1, 0, RasterFormatException.class),
        new Args6(TYPE_BYTE, 1, 1, 1, 1, 0, null),
        new Args6(TYPE_BYTE, 1, 1, 3, 1, 0, RasterFormatException.class),
        new Args6(TYPE_BYTE, 1, 1, 4, 1, 0, null),
        new Args6(TYPE_BYTE, 1, 1, 16, 2, 0, RasterFormatException.class),
        new Args6(TYPE_BYTE, 9, 1, 1, 1, 0, RasterFormatException.class),
        new Args6(TYPE_SHORT, 1, 1, 16, 2, 0, IllegalArgumentException.class),
        new Args6(TYPE_USHORT, 1, 1, 16, 2, 0, null),
        new Args6(TYPE_INT, 1, 1, 16, 1, 0, null),
        new Args6(TYPE_BYTE, 1, 30, 0, 4, 0, RasterFormatException.class),
        new Args6(TYPE_BYTE, 0, 1, 4, 1, 0, IllegalArgumentException.class),
        new Args6(TYPE_BYTE, 1, 1, 2, 1, 0, null),
        new Args6(TYPE_BYTE, 4, 1, 4, 1, 0, RasterFormatException.class),
        new Args6(TYPE_BYTE, 1, 1, 2, 1, 1, IllegalArgumentException.class),
        new Args6(TYPE_BYTE, 1, 1, 2, 1, -1, IllegalArgumentException.class),
        new Args6(TYPE_BYTE, 1, 1, 1, -1, 1, IllegalArgumentException.class),
        new Args6(TYPE_BYTE, -1, 1, 1, 1, 1, IllegalArgumentException.class),
        new Args6(TYPE_BYTE, 1, 1, 1, 1, 1, null),
        new Args6(TYPE_INT, 77777777, 2, 32, 1, 0, RasterFormatException.class),
        new Args6(TYPE_BYTE, 1<<29, 1, 4, 1<<28, 0, null),
        new Args6(99, 8, 1, 1, 1, 0, IllegalArgumentException.class),
    };

    static void test4(Args4 a) {
        try {
             new MultiPixelPackedSampleModel(a.dType, a.w, a.h, a.bits);
        } catch (Exception e) {
             if (a.eType == null || !(a.eType.isInstance(e))) {
               throw new RuntimeException("failed for " + a + " got " + e);
           } else {
               return;
           }
        }
        if (a.eType != null) {
            throw new RuntimeException("No exception for " + a);
        }
    }


    static void test6(Args6 a) {
        try {
             new MultiPixelPackedSampleModel(a.dType, a.w, a.h, a.bits, a.stride, a.bitOffset);
        } catch (Exception e) {
             if (a.eType == null || !(a.eType.isInstance(e))) {
               throw new RuntimeException("failed for " + a + " got " + e);
           } else {
               return;
           }
        }
        if (a.eType != null) {
            throw new RuntimeException("No exception for " + a);
        }
    }
}
