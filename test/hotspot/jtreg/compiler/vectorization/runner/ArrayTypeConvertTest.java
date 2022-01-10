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
 * @summary Vectorization test on array type conversions
 * @library /test/lib /
 *
 * @build sun.hotspot.WhiteBox
 *        compiler.vectorization.runner.VectorizationTestRunner
 *
 * @run driver jdk.test.lib.helpers.ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -Xbootclasspath/a:.
 *                   -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI
 *                   compiler.vectorization.runner.ArrayTypeConvertTest
 *
 * @requires vm.compiler2.enabled & vm.flagless
 */

package compiler.vectorization.runner;

public class ArrayTypeConvertTest extends VectorizationTestRunner {

    private static final int SIZE = 2345;

    private   byte[] bytes;
    private  short[] shorts;
    private   char[] chars;
    private    int[] ints;
    private   long[] longs;
    private  float[] floats;
    private double[] doubles;

    public ArrayTypeConvertTest() {
        bytes   = new   byte[SIZE];
        shorts  = new  short[SIZE];
        chars   = new   char[SIZE];
        ints    = new    int[SIZE];
        longs   = new   long[SIZE];
        floats  = new  float[SIZE];
        doubles = new double[SIZE];
        for (int i = 0; i < SIZE; i++) {
            bytes[i]   = (byte)  (-i / 128);
            shorts[i]  = (short) (i / 3 - 12345);
            chars[i]   = (char)  (i * 2);
            ints[i]    = -22 * i;
            longs[i]   = -258L * i + 99L;
            floats[i]  = (float) (i * 2.498f);
            doubles[i] = -3 * i;
        }
    }

    // ---------------- Integer Extension ----------------
    @Test
    public int[] signExtension() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = shorts[i];
        }
        return res;
    }

    @Test
    public int[] zeroExtension() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = chars[i];
        }
        return res;
    }

    @Test
    public int[] signExtensionFromByte() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = bytes[i];
        }
        return res;
    }

    // ---------------- Integer Narrow ----------------
    @Test
    public short[] narrowToSigned() {
        short[] res = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (short) ints[i];
        }
        return res;
    }

    @Test
    public char[] narrowToUnsigned() {
        char[] res = new char[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (char) ints[i];
        }
        return res;
    }

    @Test
    public byte[] NarrowToByte() {
        byte[] res = new byte[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (byte) ints[i];
        }
        return res;
    }

    // ---------------- Convert I/L to F/D ----------------
    @Test
    public float[] convertIntToFloat() {
        float[] res = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (float) ints[i];
        }
        return res;
    }

    @Test
    public double[] convertIntToDouble() {
        double[] res = new double[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (double) ints[i];
        }
        return res;
    }

    @Test
    public float[] convertLongToFloat() {
        float[] res = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (float) longs[i];
        }
        return res;
    }

    @Test
    public double[] convertLongToDouble() {
        double[] res = new double[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (double) longs[i];
        }
        return res;
    }

    // ---------------- Convert Subword-I to F/D ----------------
    @Test
    public float[] convertShortToFloat() {
        float[] res = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (float) shorts[i];
        }
        return res;
    }

    @Test
    public double[] convertShortToDouble() {
        double[] res = new double[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (double) shorts[i];
        }
        return res;
    }

    @Test
    public float[] convertCharToFloat() {
        float[] res = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (float) chars[i];
        }
        return res;
    }

    @Test
    public double[] convertCharToDouble() {
        double[] res = new double[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (double) chars[i];
        }
        return res;
    }

    // ---------------- Convert F/D to I/L ----------------
    @Test
    public int[] convertFloatToInt() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (int) floats[i];
        }
        return res;
    }

    @Test
    public long[] convertFloatToLong() {
        long[] res = new long[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (long) floats[i];
        }
        return res;
    }

    @Test
    public int[] convertDoubleToInt() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (int) doubles[i];
        }
        return res;
    }

    @Test
    public long[] convertDoubleToLong() {
        long[] res = new long[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (long) doubles[i];
        }
        return res;
    }

    // ---------------- Convert F/D to Subword-I ----------------
    @Test
    public short[] convertFloatToShort() {
        short[] res = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (short) floats[i];
        }
        return res;
    }

    @Test
    public char[] convertFloatToChar() {
        char[] res = new char[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (char) floats[i];
        }
        return res;
    }

    @Test
    public short[] convertDoubleToShort() {
        short[] res = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (short) doubles[i];
        }
        return res;
    }

    @Test
    public char[] convertDoubleToChar() {
        char[] res = new char[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (char) doubles[i];
        }
        return res;
    }

    // ---------------- Convert Between F & D ----------------
    @Test
    public double[] convertFloatToDouble() {
        double[] res = new double[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (double) floats[i];
        }
        return res;
    }

    @Test
    public float[] convertDoubleToFloat() {
        float[] res = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (float) doubles[i];
        }
        return res;
    }
}

