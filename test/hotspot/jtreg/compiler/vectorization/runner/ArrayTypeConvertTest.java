/*
 * Copyright (c) 2022, 2023, Arm Limited. All rights reserved.
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @requires vm.compiler2.enabled
 *
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm -Xbootclasspath/a:.
 *                   -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI
 *                   compiler.vectorization.runner.ArrayTypeConvertTest nCOH_nAV
 *
 * @run main/othervm -Xbootclasspath/a:.
 *                   -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI
 *                   compiler.vectorization.runner.ArrayTypeConvertTest nCOH_yAV
 *
 * @run main/othervm -Xbootclasspath/a:.
 *                   -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI
 *                   compiler.vectorization.runner.ArrayTypeConvertTest yCOH_nAV
 *
 * @run main/othervm -Xbootclasspath/a:.
 *                   -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI
 *                   compiler.vectorization.runner.ArrayTypeConvertTest yCOH_yAV
 */

package compiler.vectorization.runner;

import compiler.lib.ir_framework.*;

// Explanation about AlignVector: we require 8-byte alignment of all addresses.
// But the array base offset changes with UseCompactObjectHeaders.
// This means it affects the alignment constraints.

public class ArrayTypeConvertTest extends VectorizationTestRunner {

    // We must pass the flags directly to the test-VM, and not the driver vm in the @run above.
    @Override
    protected String[] testVMFlags(String[] args) {
        return switch (args[0]) {
            case "nCOH_nAV" -> new String[]{"-XX:-UseCompactObjectHeaders", "-XX:-AlignVector"};
            case "nCOH_yAV" -> new String[]{"-XX:-UseCompactObjectHeaders", "-XX:+AlignVector"};
            case "yCOH_nAV" -> new String[]{"-XX:+UseCompactObjectHeaders", "-XX:-AlignVector"};
            case "yCOH_yAV" -> new String[]{"-XX:+UseCompactObjectHeaders", "-XX:+AlignVector"};
            default -> { throw new RuntimeException("Test argument not recognized: " + args[0]); }
        };
    }

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
    @IR(failOn = {IRNode.STORE_VECTOR})
    // Subword vector casts do not work currently, see JDK-8342095.
    // Assert the vectorization failure so that we are reminded to update
    // the test when this limitation is addressed in the future.
    public int[] signExtension() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = shorts[i];
        }
        return res;
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    // Subword vector casts do not work currently, see JDK-8342095.
    // Assert the vectorization failure so that we are reminded to update
    // the test when this limitation is addressed in the future.
    public int[] zeroExtension() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = chars[i];
        }
        return res;
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    // Subword vector casts do not work currently, see JDK-8342095.
    // Assert the vectorization failure so that we are reminded to update
    // the test when this limitation is addressed in the future.
    public int[] signExtensionFromByte() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = bytes[i];
        }
        return res;
    }

    // ---------------- Integer Narrow ----------------
    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    // Subword vector casts do not work currently, see JDK-8342095.
    // Assert the vectorization failure so that we are reminded to update
    // the test when this limitation is addressed in the future.
    public short[] narrowToSigned() {
        short[] res = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (short) ints[i];
        }
        return res;
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    // Subword vector casts do not work currently, see JDK-8342095.
    // Assert the vectorization failure so that we are reminded to update
    // the test when this limitation is addressed in the future.
    public char[] narrowToUnsigned() {
        char[] res = new char[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (char) ints[i];
        }
        return res;
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    // Subword vector casts do not work currently, see JDK-8342095.
    // Assert the vectorization failure so that we are reminded to update
    // the test when this limitation is addressed in the future.
    public byte[] NarrowToByte() {
        byte[] res = new byte[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (byte) ints[i];
        }
        return res;
    }

    // ---------------- Convert I/L to F/D ----------------
    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"},
        counts = {IRNode.VECTOR_CAST_I2F, IRNode.VECTOR_SIZE + "min(max_int, max_float)", ">0"})
    public float[] convertIntToFloat() {
        float[] res = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (float) ints[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"},
        counts = {IRNode.VECTOR_CAST_I2D, IRNode.VECTOR_SIZE + "min(max_int, max_double)", ">0"})
    public double[] convertIntToDouble() {
        double[] res = new double[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (double) ints[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"sve", "true", "avx512dq", "true", "rvv", "true"},
        counts = {IRNode.VECTOR_CAST_L2F, IRNode.VECTOR_SIZE + "min(max_long, max_float)", ">0"})
    public float[] convertLongToFloat() {
        float[] res = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (float) longs[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "avx512dq", "true", "rvv", "true"},
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
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "avx2", "true", "rvv", "true"},
        applyIfOr = {"AlignVector", "false", "UseCompactObjectHeaders", "false"},
        counts = {IRNode.VECTOR_CAST_S2F, IRNode.VECTOR_SIZE + "min(max_short, max_float)", ">0"})
    public float[] convertShortToFloat() {
        float[] res = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (float) shorts[i];
            // AlignVector=true requires that all vector load/store are 8-byte aligned.
            // F_adr = base + UNSAFE.ARRAY_FLOAT_BASE_OFFSET + 4*i
            //                = 16 (UseCompactObjectHeaders=false)    -> i % 2 = 0
            //                = 12 (UseCompactObjectHeaders=true )    -> i % 2 = 1
            // S_adr = base + UNSAFE.ARRAY_SHORT_BASE_OFFSET + 2*i
            //                = 16 (UseCompactObjectHeaders=false)    -> i % 4 = 0  -> can align both
            //                = 12 (UseCompactObjectHeaders=true )    -> i % 4 = 2  -> cannot align both
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeature = {"rvv", "true"},
        applyIf = {"MaxVectorSize", ">=32"},
        counts = {IRNode.VECTOR_CAST_S2D, IRNode.VECTOR_SIZE + "min(max_short, max_double)", ">0"})
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "avx", "true"},
        applyIf = {"MaxVectorSize", ">=16"},
        counts = {IRNode.VECTOR_CAST_S2D, IRNode.VECTOR_SIZE + "min(max_short, max_double)", ">0"})
    public double[] convertShortToDouble() {
        double[] res = new double[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (double) shorts[i];
        }
        return res;
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    // Subword vector casts do not work currently, see JDK-8342095.
    // Assert the vectorization failure so that we are reminded to update
    // the test when this limitation is addressed in the future.
    public float[] convertCharToFloat() {
        float[] res = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (float) chars[i];
        }
        return res;
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    // Subword vector casts do not work currently, see JDK-8342095.
    // Assert the vectorization failure so that we are reminded to update
    // the test when this limitation is addressed in the future.
    public double[] convertCharToDouble() {
        double[] res = new double[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (double) chars[i];
        }
        return res;
    }

    // ---------------- Convert F/D to I/L ----------------
    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"},
        counts = {IRNode.VECTOR_CAST_F2I, IRNode.VECTOR_SIZE + "min(max_float, max_int)", ">0"})
    public int[] convertFloatToInt() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (int) floats[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "avx512dq", "true", "rvv", "true"},
        counts = {IRNode.VECTOR_CAST_F2L, IRNode.VECTOR_SIZE + "min(max_float, max_long)", ">0"})
    public long[] convertFloatToLong() {
        long[] res = new long[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (long) floats[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"sve", "true", "avx", "true", "rvv", "true"},
        counts = {IRNode.VECTOR_CAST_D2I, IRNode.VECTOR_SIZE + "min(max_double, max_int)", ">0"})
    public int[] convertDoubleToInt() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (int) doubles[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "avx512dq", "true", "rvv", "true"},
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
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "avx2", "true", "rvv", "true"},
        applyIfOr = {"AlignVector", "false", "UseCompactObjectHeaders", "false"},
        counts = {IRNode.VECTOR_CAST_F2S, IRNode.VECTOR_SIZE + "min(max_float, max_short)", ">0"})
    public short[] convertFloatToShort() {
        short[] res = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (short) floats[i];
            // AlignVector=true requires that all vector load/store are 8-byte aligned.
            // F_adr = base + UNSAFE.ARRAY_FLOAT_BASE_OFFSET + 4*i
            //                = 16 (UseCompactObjectHeaders=false)    -> i % 2 = 0
            //                = 12 (UseCompactObjectHeaders=true )    -> i % 2 = 1
            // S_adr = base + UNSAFE.ARRAY_SHORT_BASE_OFFSET + 2*i
            //                = 16 (UseCompactObjectHeaders=false)    -> i % 4 = 0  -> can align both
            //                = 12 (UseCompactObjectHeaders=true )    -> i % 4 = 2  -> cannot align both
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "avx2", "true", "rvv", "true"},
        applyIfOr = {"AlignVector", "false", "UseCompactObjectHeaders", "false"},
        counts = {IRNode.VECTOR_CAST_F2S, IRNode.VECTOR_SIZE + "min(max_float, max_char)", ">0"})
    public char[] convertFloatToChar() {
        char[] res = new char[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (char) floats[i];
            // AlignVector=true requires that all vector load/store are 8-byte aligned.
            // F_adr = base + UNSAFE.ARRAY_FLOAT_BASE_OFFSET + 4*i
            //                = 16 (UseCompactObjectHeaders=false)    -> i % 2 = 0
            //                = 12 (UseCompactObjectHeaders=true )    -> i % 2 = 1
            // S_adr = base + UNSAFE.ARRAY_SHORT_BASE_OFFSET + 2*i
            //                = 16 (UseCompactObjectHeaders=false)    -> i % 4 = 0  -> can align both
            //                = 12 (UseCompactObjectHeaders=true )    -> i % 4 = 2  -> cannot align both
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeature = {"rvv", "true"},
        applyIf = {"MaxVectorSize", ">=32"},
        counts = {IRNode.VECTOR_CAST_D2S, IRNode.VECTOR_SIZE + "min(max_double, max_short)", ">0"})
    @IR(applyIfCPUFeatureOr = {"sve", "true", "avx", "true"},
        applyIf = {"MaxVectorSize", ">=16"},
        counts = {IRNode.VECTOR_CAST_D2S, IRNode.VECTOR_SIZE + "min(max_double, max_short)", ">0"})
    public short[] convertDoubleToShort() {
        short[] res = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (short) doubles[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeature = {"rvv", "true"},
        applyIf = {"MaxVectorSize", ">=32"},
        counts = {IRNode.VECTOR_CAST_D2S, IRNode.VECTOR_SIZE + "min(max_double, max_char)", ">0"})
    @IR(applyIfCPUFeatureOr = {"sve", "true", "avx", "true"},
        applyIf = {"MaxVectorSize", ">=16"},
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
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"},
        counts = {IRNode.VECTOR_CAST_F2D, IRNode.VECTOR_SIZE + "min(max_float, max_double)", ">0"})
    public double[] convertFloatToDouble() {
        double[] res = new double[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (double) floats[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"},
        counts = {IRNode.VECTOR_CAST_D2F, IRNode.VECTOR_SIZE + "min(max_double, max_float)", ">0"})
    public float[] convertDoubleToFloat() {
        float[] res = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (float) doubles[i];
        }
        return res;
    }
}
