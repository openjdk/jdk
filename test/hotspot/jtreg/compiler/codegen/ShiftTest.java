/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4093292 8280511 8394343
 * @summary Test for correct code generation by the JIT
 * @library /test/lib
 * @run main compiler.codegen.ShiftTest
 * @run main/othervm -XX:-TieredCompilation compiler.codegen.ShiftTest
 */

package compiler.codegen;

import jdk.test.lib.Asserts;

public class ShiftTest {
    static final int w = 32;

    private static void doTest(long ct) throws Exception {
        int S22 = 0xc46cf7c2;
        int S23 = 0xcfda9162;
        int S24 = 0xd029aa4c;
        int S25 = 0x17cf1801;
        int A = (int)(ct & 0xffffffffL);
        int B = (int)(ct >>> 32);
        int x, y;
        x = B - S25;
        y = A & (w-1);
        B = ((x >>> y) | (x << (w-y))) ^ A;
        x = A - S24;
        y = B & (w-1);
        A = ((x >>> y) | (x << (w-y))) ^ B;
        x = B - S23;
        y = A & (w-1);
        B = ((x >>> y) | (x << (w-y))) ^ A;
        x = A - S22;
        y = B & (w-1);
        A = ((x >>> y) | (x << (w-y))) ^ B;
        String astr = Integer.toHexString(A);
        String bstr = Integer.toHexString(B);
        System.err.println("A = " + astr + " B = " + bstr);
        if ((!astr.equals("dcb38144")) ||
            (!bstr.equals("1916de73"))) {
            throw new RuntimeException("Unexpected shift results!");
        }
        System.err.println("Test passed");
    }

    private static int[] ispecial = {
        0, Integer.MAX_VALUE, -Integer.MAX_VALUE, Integer.MIN_VALUE, -42, 42, -1, 1
    };

    private static long[] lspecial = {
        0, Long.MAX_VALUE, -Long.MAX_VALUE, Long.MIN_VALUE, Integer.MAX_VALUE, -Integer.MAX_VALUE, Integer.MIN_VALUE, -42, 42, -1, 1
    };

    private static int[] ispecial_LeftShift_expected = {
        0, 32, -32, 0, 1344, -1344, 32, -32
    };

    private static int[] ispecial_UnsignedRightShift_expected = {
        0, -33554431, -33554432, -33554432 ,-67108863, 0, -67108863, 0
    };

    private static int[] ispecial_SignedRightShift_expected = {
        0, -16777215, 16777216, 16777216, 1, 0, 1, 0
    };

    private static int[] ispecial_LeftShiftCorner_expected = {
        0, -2147483647, 2147483647, -2147483648, 42, -42, 1, -1
    };

    private static int[] ispecial_UnsignedRightShiftCorner_expected = {
        0, -1073741823, -1073741824, -1073741824, -2147483627, -21, -2147483647, 0
    };

    private static int[] ispecial_SignedRightShiftCorner_expected = {
        0, -536870911, 536870912, 536870912, 11, -10, 1, 0
    };

    private static long[] lspecial_LeftShift_expected = {
        0, 256, -256, 0, -549755813632L, 549755813632L, 549755813888L, 10752, -10752, 256, -256
    };

    private static long[] lspecial_UnsignedRightShift_expected = {
        0, -18014398509481983L, -18014398509481984L, -18014398509481984L, -4194303, -36028797014769664L, -36028797014769664L, -36028797018963967L, 0, -36028797018963967L, 0
    };

    private static long[] lspecial_SignedRightShift_expected = {
        0, -9007199254740991L, 9007199254740992L, 9007199254740992L, -2097151, 2097152, 2097152, 1, 0, 1, 0
    };

    private static long[] lspecial_LeftShiftCorner_expected = {
        0, -9223372036854775807L, 9223372036854775807L, -9223372036854775808L, -2147483647, 2147483647, 2147483648L, 42, -42, 1, -1
    };

    private static long[] lspecial_UnsignedRightShiftCorner_expected = {
        0, -4611686018427387903L, -4611686018427387904L, -4611686018427387904L, -1073741823, -9223372035781033984L, -9223372035781033984L, -9223372036854775787L, -21, -9223372036854775807L, 0
    };

    private static long[] lspecial_SignedRightShiftCorner_expected = {
        0, -2305843009213693951L, 2305843009213693952L, 2305843009213693952L, -536870911, 536870912, 536870912, 11, -10, 1, 0
    };

    private static int negLeftShiftInt(int input) {
        return -(input << 5);
    }

    private static int negUnsignedRightShiftInt(int input) {
        return -(input >>> 6);
    }

    private static int negSignedRightShiftInt(int input) {
        return -(input >> 7);
    }

    private static int negLeftShiftICorner(int input) {
        return -(input << 32);
    }

    private static int negUnsignedRightShiftICorner(int input) {
        return -(input >>> 33);
    }

    private static int negSignedRightShiftICorner(int input) {
        return -(input >> 34);
    }

    private static long negLeftShiftLong(long input) {
        return -(input << 8);
    }

    private static long negUnsignedRightShiftLong(long input) {
        return -(input >>> 9);
    }

    private static long negSignedRightShiftLong(long input) {
        return -(input >> 10);
    }

    private static long negLeftShiftLCorner(long input) {
        return -(input << 64);
    }

    private static long negUnsignedRightShiftLCorner(long input) {
        return -(input >>> 65);
    }

    private static long negSignedRightShiftLCorner(long input) {
        return -(input >> 66);
    }

    private static int[] posInt = {
        0, 1, 2, 3, (1<<16) - 1, 1<<16, Integer.MAX_VALUE
    };

    private static long[] posInt_LeftShift20_expected = {
        0, 1048576, 2097152, 3145728, 68718428160L, 68719476736L, 2251799812636672L
    };

    private static long[] posInt_LeftShift31_expected = {
        0, 2147483648L, 4294967296L, 6442450944L, 140735340871680L, 140737488355328L, 4611686016279904256L
    };

    private static long[] posInt_LeftShift32_expected = {
        0, 4294967296L, 8589934592L, 12884901888L, 281470681743360L, 281474976710656L, 9223372032559808512L
    };

    private static long[] posInt_LeftShift33_expected = {
        0, 8589934592L, 17179869184L, 25769803776L, 562941363486720L, 562949953421312L, -8589934592L
    };

    private static long[] posInt_LeftShift63_expected = {
        0, -9223372036854775808L, 0, -9223372036854775808L, -9223372036854775808L, 0, -9223372036854775808L
    };

    private static long leftShiftPosI2L_20(int input) {
        return (long)(input & Integer.MAX_VALUE) << 20;
    }

    private static long leftShiftPosI2L_31(int input) {
        return (long)(input & Integer.MAX_VALUE) << 31;
    }

    private static long leftShiftPosI2L_32(int input) {
        return (long)(input & Integer.MAX_VALUE) << 32;
    }

    private static long leftShiftPosI2L_33(int input) {
        return (long)(input & Integer.MAX_VALUE) << 33;
    }

    private static long leftShiftPosI2L_63(int input) {
        return (long)(input & Integer.MAX_VALUE) << 63;
    }

    private static void testNegShift() {
        for (int i = 0; i < 20_000; i++) {
            for (int j = 0; j < ispecial.length; j++) {
                Asserts.assertEquals(negLeftShiftInt(ispecial[j]), ispecial_LeftShift_expected[j]);
                Asserts.assertEquals(negUnsignedRightShiftInt(ispecial[j]), ispecial_UnsignedRightShift_expected[j]);
                Asserts.assertEquals(negSignedRightShiftInt(ispecial[j]), ispecial_SignedRightShift_expected[j]);
                Asserts.assertEquals(negLeftShiftICorner(ispecial[j]), ispecial_LeftShiftCorner_expected[j]);
                Asserts.assertEquals(negUnsignedRightShiftICorner(ispecial[j]), ispecial_UnsignedRightShiftCorner_expected[j]);
                Asserts.assertEquals(negSignedRightShiftICorner(ispecial[j]), ispecial_SignedRightShiftCorner_expected[j]);
            }
            for (int j = 0; j < lspecial.length; j++) {
                Asserts.assertEquals(negLeftShiftLong(lspecial[j]), lspecial_LeftShift_expected[j]);
                Asserts.assertEquals(negUnsignedRightShiftLong(lspecial[j]), lspecial_UnsignedRightShift_expected[j]);
                Asserts.assertEquals(negSignedRightShiftLong(lspecial[j]), lspecial_SignedRightShift_expected[j]);
                Asserts.assertEquals(negLeftShiftLCorner(lspecial[j]), lspecial_LeftShiftCorner_expected[j]);
                Asserts.assertEquals(negUnsignedRightShiftLCorner(lspecial[j]), lspecial_UnsignedRightShiftCorner_expected[j]);
                Asserts.assertEquals(negSignedRightShiftLCorner(lspecial[j]), lspecial_SignedRightShiftCorner_expected[j]);
            }
            for (int j = 0; j < posInt.length; j++) {
                Asserts.assertEquals(leftShiftPosI2L_20(posInt[j]), posInt_LeftShift20_expected[j]);
                Asserts.assertEquals(leftShiftPosI2L_31(posInt[j]), posInt_LeftShift31_expected[j]);
                Asserts.assertEquals(leftShiftPosI2L_32(posInt[j]), posInt_LeftShift32_expected[j]);
                Asserts.assertEquals(leftShiftPosI2L_33(posInt[j]), posInt_LeftShift33_expected[j]);
                Asserts.assertEquals(leftShiftPosI2L_63(posInt[j]), posInt_LeftShift63_expected[j]);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        doTest(0x496def29b74be041L);
        testNegShift();
    }
}
