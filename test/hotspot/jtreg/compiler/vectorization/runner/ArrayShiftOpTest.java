/*
 * Copyright (c) 2022, Arm Limited. All rights reserved.
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
 * @summary Vectorization test on bug-prone shift operation
 * @library /test/lib /
 *
 * @build sun.hotspot.WhiteBox
 *        compiler.vectorization.runner.VectorizationTestRunner
 *
 * @run driver jdk.test.lib.helpers.ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -Xbootclasspath/a:.
 *                   -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI
 *                   compiler.vectorization.runner.ArrayShiftOpTest
 *
 * @requires vm.compiler2.enabled & vm.flagless
 */

package compiler.vectorization.runner;

import java.util.Random;

public class ArrayShiftOpTest extends VectorizationTestRunner {

    private static final int SIZE = 2345;

    private int[] ints;
    private long[] longs;
    private short[] shorts1;
    private short[] shorts2;
    private int largeDist;

    public ArrayShiftOpTest() {
        ints = new int[SIZE];
        longs = new long[SIZE];
        shorts1 = new short[SIZE];
        shorts2 = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            ints[i] = -888999 * i;
            longs[i] = 999998888800000L * i;
            shorts1[i] = (short) (4 * i);
            shorts2[i] = (short) (-3 * i);
        }
        Random ran = new Random(999);
        largeDist = 123;
    }

    @Test
    public int[] intCombinedRotateShift() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (ints[i] << 14) | (ints[i] >>> 18);
        }
        return res;
    }

    @Test
    public long[] longCombinedRotateShift() {
        long[] res = new long[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (longs[i] << 55) | (longs[i] >>> 9);
        }
        return res;
    }

    @Test
    public int[] intShiftLargeDistConstant() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = ints[i] >> 35;
        }
        return res;
    }

    @Test
    public int[] intShiftLargeDistInvariant() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = ints[i] >> largeDist;
        }
        return res;
    }

    @Test
    public long[] longShiftLargeDistConstant() {
        long[] res = new long[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = longs[i] << 77;
        }
        return res;
    }

    @Test
    public long[] longShiftLargeDistInvariant() {
        long[] res = new long[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = longs[i] >>> largeDist;
        }
        return res;
    }

    @Test
    // Note that any shift operation with distance value from another array
    // cannot be vectorized since C2 vector shift node doesn't support it.
    public long[] variantShiftDistance() {
        long[] res = new long[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = longs[i] >> ints[i];
        }
        return res;
    }

    @Test
    // Note that unsigned shift right on subword signed integer types can't
    // be vectorized since the sign extension bits would be lost.
    public short[] vectorUnsignedShiftRight() {
        short[] res = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (short) (shorts2[i] >>> 3);
        }
        return res;
    }

    @Test
    // Note that right shift operations on subword expressions cannot be
    // vectorized since precise type info about signness is missing.
    public short[] subwordExpressionRightShift() {
        short[] res = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (short) ((shorts1[i] + shorts2[i]) >> 4);
        }
        return res;
    }
}

