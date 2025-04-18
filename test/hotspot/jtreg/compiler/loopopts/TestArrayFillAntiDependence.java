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

package compiler.loopopts;

import jdk.test.lib.Asserts;

/**
 * @test
 * @bug 8351468
 * @summary Test that loads anti-dependent on array fill intrinsics are
 *          scheduled correctly, for different load and array fill types.
 *          See detailed comments in testShort() below.
 * @library /test/lib
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions
 *                   -Xbatch -XX:-TieredCompilation
 *                   -XX:CompileOnly=compiler.loopopts.TestArrayFillAntiDependence::test*
 *                   -XX:CompileCommand=quiet -XX:LoopUnrollLimit=0 -XX:+OptimizeFill
 *                   compiler.loopopts.TestArrayFillAntiDependence
 * @run main/othervm compiler.loopopts.TestArrayFillAntiDependence
 */

public class TestArrayFillAntiDependence {

    static int N = 10;
    static short M = 4;
    static boolean BOOLEAN_VAL = true;
    static char CHAR_VAL = 42;
    static float FLOAT_VAL = 42.0f;
    static double DOUBLE_VAL = 42.0;
    static byte BYTE_VAL = 42;
    static short SHORT_VAL = 42;
    static int INT_VAL = 42;
    static long LONG_VAL = 42;

    static boolean testBoolean(int pos, int samePos) {
        assert pos == samePos;
        boolean total = false;
        boolean[] array = new boolean[N];
        array[pos] = BOOLEAN_VAL;
        for (int i = 0; i < M; i++) {
            total |= array[samePos];
            for (int t = 0; t < array.length; t++) {
                array[t] = false;
            }
        }
        return total;
    }

    static char testChar(int pos, int samePos) {
        assert pos == samePos;
        char total = 0;
        char[] array = new char[N];
        array[pos] = CHAR_VAL;
        for (int i = 0; i < M; i++) {
            total += array[samePos];
            for (int t = 0; t < array.length; t++) {
                array[t] = 0;
            }
        }
        return total;
    }

    static float testFloat(int pos, int samePos) {
        assert pos == samePos;
        float total = 0.0f;
        float[] array = new float[N];
        array[pos] = FLOAT_VAL;
        for (int i = 0; i < M; i++) {
            total += array[samePos];
            for (int t = 0; t < array.length; t++) {
                array[t] = 0.0f;
            }
        }
        return total;
    }

    static double testDouble(int pos, int samePos) {
        assert pos == samePos;
        double total = 0.0;
        double[] array = new double[N];
        array[pos] = DOUBLE_VAL;
        for (int i = 0; i < M; i++) {
            total += array[samePos];
            for (int t = 0; t < array.length; t++) {
                array[t] = 0.0;
            }
        }
        return total;
    }

    static byte testByte(int pos, int samePos) {
        assert pos == samePos;
        byte total = 0;
        byte[] array = new byte[N];
        array[pos] = BYTE_VAL;
        for (int i = 0; i < M; i++) {
            total += array[samePos];
            for (int t = 0; t < array.length; t++) {
                array[t] = 0;
            }
        }
        return total;
    }

    static short testShort(int pos, int samePos) {
        // This pre-condition is necessary to reproduce the miscompilation, but
        // should not be exploited by C2 for optimization.
        assert pos == samePos;
        short total = 0;
        short[] array = new short[N];
        array[pos] = SHORT_VAL;
        for (int i = 0; i < M; i++) {
            // This load is wrongly scheduled after the loop below, which is
            // transformed into a call to arrayof_jshort_fill and clears the
            // entire array. As a consequence, the function returns 0 instead of
            // the expected SHORT_VAL.
            // The load is wrongly allowed to be moved beyond the loop
            // (arrayof_jshort_fill call) because their anti-dependence is
            // missed. This is because the call operates on a different memory
            // slice (char[] instead of the expected short[]).
            total += array[samePos];
            for (int t = 0; t < array.length; t++) {
                array[t] = 0;
            }
        }
        return total;
    }

    static int testInt(int pos, int samePos) {
        assert pos == samePos;
        int total = 0;
        int[] array = new int[N];
        array[pos] = INT_VAL;
        for (int i = 0; i < M; i++) {
            total += array[samePos];
            for (int t = 0; t < array.length; t++) {
                array[t] = 0;
            }
        }
        return total;
    }

    static long testLong(int pos, int samePos) {
        assert pos == samePos;
        long total = 0;
        long[] array = new long[N];
        array[pos] = LONG_VAL;
        for (int i = 0; i < M; i++) {
            total += array[samePos];
            for (int t = 0; t < array.length; t++) {
                array[t] = 0;
            }
        }
        return total;
    }

    public static void main(String[] args) {
        for (int i = 0; i < 10_000; i++) {
            boolean result = testBoolean(0, 0);
            Asserts.assertEquals(BOOLEAN_VAL, result);
        }
        for (int i = 0; i < 10_000; i++) {
            char result = testChar(0, 0);
            Asserts.assertEquals(CHAR_VAL, result);
        }
        for (int i = 0; i < 10_000; i++) {
            float result = testFloat(0, 0);
            Asserts.assertEquals(FLOAT_VAL, result);
        }
        for (int i = 0; i < 10_000; i++) {
            double result = testDouble(0, 0);
            Asserts.assertEquals(DOUBLE_VAL, result);
        }
        for (int i = 0; i < 10_000; i++) {
            byte result = testByte(0, 0);
            Asserts.assertEquals(BYTE_VAL, result);
        }
        for (int i = 0; i < 10_000; i++) {
            short result = testShort(0, 0);
            Asserts.assertEquals(SHORT_VAL, result);
        }
        for (int i = 0; i < 10_000; i++) {
            int result = testInt(0, 0);
            Asserts.assertEquals(INT_VAL, result);
        }
        for (int i = 0; i < 10_000; i++) {
            long result = testLong(0, 0);
            Asserts.assertEquals(LONG_VAL, result);
        }
    }
}
