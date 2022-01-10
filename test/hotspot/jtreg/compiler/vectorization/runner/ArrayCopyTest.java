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
 * @summary Vectorization test on array copy
 * @library /test/lib /
 *
 * @build sun.hotspot.WhiteBox
 *        compiler.vectorization.runner.VectorizationTestRunner
 *
 * @run driver jdk.test.lib.helpers.ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -Xbootclasspath/a:.
 *                   -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI
 *                   compiler.vectorization.runner.ArrayCopyTest
 *
 * @requires vm.compiler2.enabled & vm.flagless
 */

package compiler.vectorization.runner;

public class ArrayCopyTest extends VectorizationTestRunner {

    private static final int SIZE = 2345;

    private   byte[] bytes;
    private  short[] shorts;
    private   char[] chars;
    private    int[] ints;
    private   long[] longs;
    private  float[] floats;
    private double[] doubles;

    public ArrayCopyTest() {
        bytes   = new   byte[SIZE];
        shorts  = new  short[SIZE];
        chars   = new   char[SIZE];
        ints    = new    int[SIZE];
        longs   = new   long[SIZE];
        floats  = new  float[SIZE];
        doubles = new double[SIZE];
        for (int i = 0; i < SIZE; i++) {
            bytes[i]   = (byte)  (-i / 100);
            shorts[i]  = (short) (30 * i - 12345);
            chars[i]   = (char)  (i * 55);
            ints[i]    = -4444 * i;
            longs[i]   = -999999999L * i + 99999999999L;
            floats[i]  = (float) (i * 2.3e7f);
            doubles[i] = -3e30 * i * i;
        }
    }

    // ---------------- Simple Copy ----------------
    @Test
    public byte[] copyByteArray() {
        byte[] res = new byte[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = bytes[i];
        }
        return res;
    }

    @Test
    public short[] copyShortArray() {
        short[] res = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = shorts[i];
        }
        return res;
    }

    @Test
    public char[] copyCharArray() {
        char[] res = new char[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = chars[i];
        }
        return res;
    }

    @Test
    public int[] copyIntArray() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = ints[i];
        }
        return res;
    }

    @Test
    public long[] copyLongArray() {
        long[] res = new long[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = longs[i];
        }
        return res;
    }

    @Test
    public float[] copyFloatArray() {
        float[] res = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = floats[i];
        }
        return res;
    }

    @Test
    public double[] copyDoubleArray() {
        double[] res = new double[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = doubles[i];
        }
        return res;
    }

    // ---------------- Multiple Copies ----------------
    @Test
    public float[] chainedCopy() {
        float[] res1 = new float[SIZE];
        float[] res2 = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res2[i] = res1[i] = floats[i];
        }
        return res2;
    }

    @Test
    public int[] copy2ArraysSameSize() {
        int[] res1 = new int[SIZE];
        float[] res2 = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res1[i] = ints[i];
            res2[i] = floats[i];
        }
        return res1;
    }

    @Test
    public double[] copy2ArraysDifferentSizes() {
        int[] res1 = new int[SIZE];
        double[] res2 = new double[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res1[i] = ints[i];
            res2[i] = doubles[i];
        }
        return res2;
    }

    // ---------------- Copy Between Signed & Unsigned ----------------
    @Test
    public char[] copyFromSignedToUnsigned() {
        char[] res = new char[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (char) shorts[i];
        }
        return res;
    }

    @Test
    public short[] copyFromUnsignedToSigned() {
        short[] res = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (short) chars[i];
        }
        return res;
    }
}

