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
 * @summary Vectorization test on loop array index computation
 * @library /test/lib /
 *
 * @build sun.hotspot.WhiteBox
 *        compiler.vectorization.runner.VectorizationTestRunner
 *
 * @run driver jdk.test.lib.helpers.ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -Xbootclasspath/a:.
 *                   -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI
 *                   compiler.vectorization.runner.LoopArrayIndexComputeTest
 *
 * @requires vm.compiler2.enabled & vm.flagless
 */

package compiler.vectorization.runner;

import java.util.Random;

public class LoopArrayIndexComputeTest extends VectorizationTestRunner {

    private static final int SIZE = 2345;

    private int[] ints;
    private short[] shorts;
    private char[] chars;
    private byte[] bytes;
    private boolean[] booleans;

    private int inv1;
    private int inv2;

    public LoopArrayIndexComputeTest() {
        ints = new int[SIZE];
        shorts = new short[SIZE];
        chars = new char[SIZE];
        bytes = new byte[SIZE];
        booleans = new boolean[SIZE];
        for (int i = 0; i < SIZE; i++) {
            ints[i] = 499 * i;
            shorts[i] = (short) (-13 * i + 5);
            chars[i] = (char) (i << 3);
            bytes[i] = (byte) (i >> 2 + 3);
            booleans[i] = (i % 5 == 0);
        }
        Random ran = new Random(10);
        inv1 = Math.abs(ran.nextInt() % 10) + 1;
        inv2 = Math.abs(ran.nextInt() % 10) + 1;
    }

    // ---------------- Linear Indexes ----------------
    @Test
    public int[] indexPlusConstant() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE / 2; i++) {
            res[i + 1] = ints[i + 1] + 999;
        }
        return res;
    }

    @Test
    public int[] indexMinusConstant() {
        int[] res = new int[SIZE];
        for (int i = SIZE / 2; i < SIZE; i++) {
            res[i - 49] = ints[i - 49] * i;
        }
        return res;
    }

    @Test
    public int[] indexPlusInvariant() {
        int[] res = new int[SIZE];
        System.arraycopy(ints, 0, res, 0, SIZE);
        for (int i = 0; i < SIZE / 4; i++) {
            res[i + inv1] *= ints[i + inv1];
        }
        return res;
    }

    @Test
    public int[] indexMinusInvariant() {
        int[] res = new int[SIZE];
        System.arraycopy(ints, 0, res, 0, SIZE);
        for (int i = SIZE / 3; i < SIZE / 2; i++) {
            res[i - inv2] *= (ints[i - inv2] + (i >> 2));
        }
        return res;
    }

    @Test
    public int[] indexWithInvariantAndConstant() {
        int[] res = new int[SIZE];
        System.arraycopy(ints, 0, res, 0, SIZE);
        for (int i = 10; i < SIZE / 4; i++) {
            res[i + inv1 - 1] *= (ints[i + inv1 - 1] + 1);
        }
        return res;
    }

    @Test
    public int[] indexWithTwoInvariants() {
        int[] res = new int[SIZE];
        System.arraycopy(ints, 0, res, 0, SIZE);
        for (int i = 10; i < SIZE / 4; i++) {
            res[i + inv1 + inv2] -= ints[i + inv1 + inv2];
        }
        return res;
    }

    @Test
    // Note that this case cannot be vectorized due to data dependence
    public int[] indexWithDifferentConstants() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE / 4; i++) {
            res[i] = ints[i + 1];
        }
        return res;
    }

    @Test
    // Note that this case cannot be vectorized due to data dependence
    public int[] indexWithDifferentInvariants() {
        int[] res = new int[SIZE];
        for (int i = SIZE / 4; i < SIZE / 2; i++) {
            res[i + inv1] = ints[i - inv2];
        }
        return res;
    }

    @Test
    public int indexWithDifferentConstantsLoadOnly() {
        int res1 = 0;
        int res2 = 0;
        for (int i = 0; i < SIZE / 4; i++) {
            res1 += ints[i + 2];
            res2 += ints[i + 15];
        }
        return res1 * res2;
    }

    @Test
    public int indexWithDifferentInvariantsLoadOnly() {
        int res1 = 0;
        int res2 = 0;
        for (int i = SIZE / 4; i < SIZE / 2; i++) {
            res1 += ints[i + inv1];
            res2 += ints[i - inv2];
        }
        return res1 * res2;
    }

    @Test
    public int[] scaledIndex() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE / 3; i++) {
            res[2 * i] = ints[2 * i];
        }
        return res;
    }

    @Test
    public int[] scaledIndexWithConstantOffset() {
        int[] res = new int[SIZE];
        System.arraycopy(ints, 0, res, 0, SIZE);
        for (int i = 0; i < SIZE / 4; i++) {
            res[2 * i + 3] *= ints[2 * i + 3];
        }
        return res;
    }

    @Test
    public int[] scaledIndexWithInvariantOffset() {
        int[] res = new int[SIZE];
        System.arraycopy(ints, 0, res, 0, SIZE);
        for (int i = 0; i < SIZE / 4; i++) {
            res[2 * i + inv1] *= ints[2 * i + inv1];
        }
        return res;
    }

    @Test
    // Note that this case cannot be vectorized due to data dependence
    // between src and dest of the assignment.
    public int[] sameArrayWithDifferentIndex() {
        int[] res = new int[SIZE];
        System.arraycopy(ints, 0, res, 0, SIZE);
        for (int i = 1, j = 0; i < 100; i++, j++) {
            res[i] += res[j];
        }
        return res;
    }

    // ---------------- Subword Type Arrays ----------------
    @Test
    // Note that this case cannot be vectorized due to data dependence
    public short[] shortArrayWithDependence() {
        short[] res = new short[SIZE];
        System.arraycopy(shorts, 0, res, 0, SIZE);
        for (int i = 0; i < SIZE / 2; i++) {
            res[i] *= shorts[i + 1];
        }
        return res;
    }

    @Test
    // Note that this case cannot be vectorized due to data dependence
    public char[] charArrayWithDependence() {
        char[] res = new char[SIZE];
        System.arraycopy(chars, 0, res, 0, SIZE);
        for (int i = 0; i < SIZE / 2; i++) {
            res[i] *= chars[i + 2];
        }
        return res;
    }

    @Test
    // Note that this case cannot be vectorized due to data dependence
    public byte[] byteArrayWithDependence() {
        byte[] res = new byte[SIZE];
        System.arraycopy(bytes, 0, res, 0, SIZE);
        for (int i = 0; i < SIZE / 2; i++) {
            res[i] *= bytes[i + 3];
        }
        return res;
    }

    @Test
    // Note that this case cannot be vectorized due to data dependence
    public boolean[] booleanArrayWithDependence() {
        boolean[] res = new boolean[SIZE];
        System.arraycopy(booleans, 0, res, 0, SIZE);
        for (int i = 0; i < SIZE / 2; i++) {
            res[i] |= booleans[i + 4];
        }
        return res;
    }

    // ---------------- Multiple Operations ----------------
    @Test
    public int[] differentIndexWithDifferentTypes() {
        int[] res1 = new int[SIZE];
        short[] res2 = new short[SIZE];
        for (int i = 0; i < SIZE / 2; i++) {
            res1[i + 1] = ints[i + 1];
            res2[i + inv2] = shorts[i + inv2];
        }
        return res1;
    }

    @Test
    // Note that this case cannot be vectorized due to data dependence
    public int[] differentIndexWithSameType() {
        int[] res1 = new int[SIZE];
        int[] res2 = new int[SIZE];
        for (int i = 0; i < SIZE / 2; i++) {
            res1[i + 3] = ints[i + 3];
            res2[i + inv1] = ints[i + inv1];
        }
        return res2;
    }
}

