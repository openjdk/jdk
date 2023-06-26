/*
 * Copyright (c) 2022, 2023, Arm Limited. All rights reserved.
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
 * @build jdk.test.whitebox.WhiteBox
 *        compiler.vectorization.runner.VectorizationTestRunner
 *
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:.
 *                   -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI
 *                   compiler.vectorization.runner.ArrayTypeConvertTest
 *
 * @requires vm.compiler2.enabled & vm.flagless
 */

package compiler.vectorization.runner;

import compiler.lib.ir_framework.*;

public class ArrayTypeConvertTest extends VectorizationTestRunner {

    private static final int SIZE = 543;

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
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "avx", "true"},
        counts = {IRNode.VECTOR_CAST_I2F, IRNode.VECTOR_SIZE + "min(max_int, max_float)", ">0"})
    public float[] convertIntToFloat() {
        float[] res = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (float) ints[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "avx", "true"},
        // The vectorization of some conversions may fail when `+AlignVector`.
        // We can remove the condition after JDK-8303827.
        applyIf = {"AlignVector", "false"},
        counts = {IRNode.VECTOR_CAST_I2D, IRNode.VECTOR_SIZE + "min(max_int, max_double)", ">0"})
    public double[] convertIntToDouble() {
        double[] res = new double[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (double) ints[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"sve", "true", "avx512dq", "true"},
        counts = {IRNode.VECTOR_CAST_L2F, IRNode.VECTOR_SIZE + "min(max_long, max_float)", ">0"})
    public float[] convertLongToFloat() {
        float[] res = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (float) longs[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "avx512dq", "true"},
        counts = {IRNode.VECTOR_CAST_L2D, IRNode.VECTOR_SIZE + "min(max_long, max_double)", ">0"})
    public double[] convertLongToDouble() {
        double[] res = new double[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (double) longs[i];
        }
        return res;
    }

    // ---------------- Convert Subword-I to F/D ----------------
    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "avx2", "true"},
        counts = {IRNode.VECTOR_CAST_S2F, IRNode.VECTOR_SIZE + "min(max_short, max_float)", ">0"})
    public float[] convertShortToFloat() {
        float[] res = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (float) shorts[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"sve", "true", "avx2", "true"},
        applyIf = {"MaxVectorSize", ">=32"},
        counts = {IRNode.VECTOR_CAST_S2D, IRNode.VECTOR_SIZE + "min(max_short, max_double)", ">0"})
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
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "avx", "true"},
        counts = {IRNode.VECTOR_CAST_F2I, IRNode.VECTOR_SIZE + "min(max_float, max_int)", ">0"})
    public int[] convertFloatToInt() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (int) floats[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "avx512dq", "true"},
        // The vectorization of some conversions may fail when `+AlignVector`.
        // We can remove the condition after JDK-8303827.
        applyIf = {"AlignVector", "false"},
        counts = {IRNode.VECTOR_CAST_F2L, IRNode.VECTOR_SIZE + "min(max_float, max_long)", ">0"})
    public long[] convertFloatToLong() {
        long[] res = new long[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (long) floats[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"sve", "true", "avx", "true"},
        counts = {IRNode.VECTOR_CAST_D2I, IRNode.VECTOR_SIZE + "min(max_double, max_int)", ">0"})
    public int[] convertDoubleToInt() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (int) doubles[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "avx512dq", "true"},
        counts = {IRNode.VECTOR_CAST_D2L, IRNode.VECTOR_SIZE + "min(max_double, max_long)", ">0"})
    public long[] convertDoubleToLong() {
        long[] res = new long[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (long) doubles[i];
        }
        return res;
    }

    // ---------------- Convert F/D to Subword-I ----------------
    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "avx2", "true"},
        counts = {IRNode.VECTOR_CAST_F2S, IRNode.VECTOR_SIZE + "min(max_float, max_short)", ">0"})
    public short[] convertFloatToShort() {
        short[] res = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (short) floats[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "avx2", "true"},
        counts = {IRNode.VECTOR_CAST_F2S, IRNode.VECTOR_SIZE + "min(max_float, max_char)", ">0"})
    public char[] convertFloatToChar() {
        char[] res = new char[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (char) floats[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"sve", "true", "avx", "true"},
        applyIf = {"MaxVectorSize", ">=32"},
        counts = {IRNode.VECTOR_CAST_D2S, IRNode.VECTOR_SIZE + "min(max_double, max_short)", ">0"})
    public short[] convertDoubleToShort() {
        short[] res = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (short) doubles[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"sve", "true", "avx", "true"},
        applyIf = {"MaxVectorSize", ">=32"},
        counts = {IRNode.VECTOR_CAST_D2S, IRNode.VECTOR_SIZE + "min(max_double, max_char)", ">0"})
    public char[] convertDoubleToChar() {
        char[] res = new char[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (char) doubles[i];
        }
        return res;
    }

    // ---------------- Convert Between F & D ----------------
    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "avx", "true"},
        // The vectorization of some conversions may fail when `+AlignVector`.
        // We can remove the condition after JDK-8303827.
        applyIf = {"AlignVector", "false"},
        counts = {IRNode.VECTOR_CAST_F2D, IRNode.VECTOR_SIZE + "min(max_float, max_double)", ">0"})
    public double[] convertFloatToDouble() {
        double[] res = new double[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (double) floats[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "avx", "true"},
        counts = {IRNode.VECTOR_CAST_D2F, IRNode.VECTOR_SIZE + "min(max_double, max_float)", ">0"})
    public float[] convertDoubleToFloat() {
        float[] res = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (float) doubles[i];
        }
        return res;
    }
}
