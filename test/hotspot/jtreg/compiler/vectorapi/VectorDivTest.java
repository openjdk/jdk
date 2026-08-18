/*
 * Copyright (c) 2026, NVIDIA CORPORATION & AFFILIATES. All rights reserved.
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
 * @bug 8387594
 * @key randomness
 * @library /test/lib /
 * @summary IR tests for Vector API lanewise DIV
 * @modules jdk.incubator.vector
 *
 * @run driver ${test.main.class}
 */

package compiler.vectorapi;

import compiler.lib.generators.*;
import compiler.lib.ir_framework.*;
import jdk.incubator.vector.*;

public class VectorDivTest {
    private static final Generators RD = Generators.G;

    private static final VectorSpecies<Byte> B_SPECIES = ByteVector.SPECIES_MAX;
    private static final VectorSpecies<Short> S_SPECIES = ShortVector.SPECIES_MAX;
    private static final VectorSpecies<Integer> I_SPECIES = IntVector.SPECIES_MAX;
    private static final VectorSpecies<Long> L_SPECIES = LongVector.SPECIES_MAX;
    private static final VectorSpecies<Float> F_SPECIES = FloatVector.SPECIES_MAX;
    private static final VectorSpecies<Double> D_SPECIES = DoubleVector.SPECIES_MAX;

    private static final int BUF_LEN = 256;

    private static final byte[] ba = new byte[BUF_LEN];
    private static final byte[] bb = new byte[BUF_LEN];
    private static final byte[] br = new byte[BUF_LEN];

    private static final short[] sa = new short[BUF_LEN];
    private static final short[] sb = new short[BUF_LEN];
    private static final short[] sr = new short[BUF_LEN];

    private static final int[] ia = new int[BUF_LEN];
    private static final int[] ib = new int[BUF_LEN];
    private static final int[] ir = new int[BUF_LEN];

    private static final long[] la = new long[BUF_LEN];
    private static final long[] lb = new long[BUF_LEN];
    private static final long[] lr = new long[BUF_LEN];

    private static final float[] fa = new float[BUF_LEN];
    private static final float[] fb = new float[BUF_LEN];
    private static final float[] fr = new float[BUF_LEN];

    private static final double[] da = new double[BUF_LEN];
    private static final double[] db = new double[BUF_LEN];
    private static final double[] dr = new double[BUF_LEN];

    private static final boolean[] mask_arr = new boolean[BUF_LEN];

    static {
        Generator<Integer> iGen = RD.ints();
        Generator<Long> lGen = RD.longs();
        Generator<Float> fGen = RD.floats();
        Generator<Double> dGen = RD.doubles();

        for (int i = 0; i < BUF_LEN; i++) {
            mask_arr[i] = (i & 1) != 0;
            ba[i] = iGen.next().byteValue();
            // Integer divisors must be non-zero, otherwise lanewise DIV throws.
            bb[i] = nonZeroByte(iGen.next().byteValue());
            sa[i] = iGen.next().shortValue();
            sb[i] = nonZeroShort(iGen.next().shortValue());
            ib[i] = nonZeroInt(iGen.next());
            lb[i] = nonZeroLong(lGen.next());
        }

        RD.fill(iGen, ia);
        RD.fill(lGen, la);
        RD.fill(fGen, fa);
        // Floating-point division has no divide-by-zero exception.
        RD.fill(fGen, fb);
        RD.fill(dGen, da);
        RD.fill(dGen, db);
    }

    private static byte nonZeroByte(byte v)   { return v == 0 ? (byte) 1 : v; }
    private static short nonZeroShort(short v) { return v == 0 ? (short) 1 : v; }
    private static int nonZeroInt(int v)   { return v == 0 ? 1 : v; }
    private static long nonZeroLong(long v) { return v == 0 ? 1L : v; }

    // Unmasked lanewise DIV.

    @Test
    @IR(counts = { IRNode.DIV_VB, ">= 1" },
        applyIfCPUFeature = { "sve", "true" })
    public static void testDivByte() {
        ByteVector va = ByteVector.fromArray(B_SPECIES, ba, 0);
        ByteVector vb = ByteVector.fromArray(B_SPECIES, bb, 0);
        va.lanewise(VectorOperators.DIV, vb).intoArray(br, 0);
    }

    @Test
    @IR(counts = { IRNode.DIV_VS, ">= 1" },
        applyIfCPUFeature = { "sve", "true" })
    public static void testDivShort() {
        ShortVector va = ShortVector.fromArray(S_SPECIES, sa, 0);
        ShortVector vb = ShortVector.fromArray(S_SPECIES, sb, 0);
        va.lanewise(VectorOperators.DIV, vb).intoArray(sr, 0);
    }

    @Test
    @IR(counts = { IRNode.DIV_VI, ">= 1" },
        applyIfCPUFeature = { "sve", "true" })
    public static void testDivInt() {
        IntVector va = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector vb = IntVector.fromArray(I_SPECIES, ib, 0);
        va.lanewise(VectorOperators.DIV, vb).intoArray(ir, 0);
    }

    @Test
    @IR(counts = { IRNode.DIV_VL, ">= 1" },
        applyIfCPUFeature = { "sve", "true" })
    public static void testDivLong() {
        LongVector va = LongVector.fromArray(L_SPECIES, la, 0);
        LongVector vb = LongVector.fromArray(L_SPECIES, lb, 0);
        va.lanewise(VectorOperators.DIV, vb).intoArray(lr, 0);
    }

    @Test
    @IR(counts = { IRNode.DIV_VF, ">= 1" },
        applyIfCPUFeature = { "asimd", "true" })
    public static void testDivFloat() {
        FloatVector va = FloatVector.fromArray(F_SPECIES, fa, 0);
        FloatVector vb = FloatVector.fromArray(F_SPECIES, fb, 0);
        va.lanewise(VectorOperators.DIV, vb).intoArray(fr, 0);
    }

    @Test
    @IR(counts = { IRNode.DIV_VD, ">= 1" },
        applyIfCPUFeature = { "asimd", "true" })
    public static void testDivDouble() {
        DoubleVector va = DoubleVector.fromArray(D_SPECIES, da, 0);
        DoubleVector vb = DoubleVector.fromArray(D_SPECIES, db, 0);
        va.lanewise(VectorOperators.DIV, vb).intoArray(dr, 0);
    }

    // Masked lanewise DIV. On AArch64, BYTE/SHORT have no native predicated
    // divide, so they are lowered to an unpredicated divide combined with a
    // VectorBlend.

    @Test
    @IR(counts = { IRNode.DIV_VB, ">= 1",
                   IRNode.VECTOR_BLEND_B, ">= 1" },
        applyIfCPUFeature = { "sve", "true" })
    public static void testMaskedDivByte() {
        VectorMask<Byte> mask = VectorMask.fromArray(B_SPECIES, mask_arr, 0);
        ByteVector va = ByteVector.fromArray(B_SPECIES, ba, 0);
        ByteVector vb = ByteVector.fromArray(B_SPECIES, bb, 0);
        va.lanewise(VectorOperators.DIV, vb, mask).intoArray(br, 0);
    }

    @Test
    @IR(counts = { IRNode.DIV_VS, ">= 1",
                   IRNode.VECTOR_BLEND_S, ">= 1" },
        applyIfCPUFeature = { "sve", "true" })
    public static void testMaskedDivShort() {
        VectorMask<Short> mask = VectorMask.fromArray(S_SPECIES, mask_arr, 0);
        ShortVector va = ShortVector.fromArray(S_SPECIES, sa, 0);
        ShortVector vb = ShortVector.fromArray(S_SPECIES, sb, 0);
        va.lanewise(VectorOperators.DIV, vb, mask).intoArray(sr, 0);
    }

    @Test
    @IR(counts = { IRNode.DIV_VI, ">= 1" },
        applyIfCPUFeature = { "sve", "true" })
    public static void testMaskedDivInt() {
        VectorMask<Integer> mask = VectorMask.fromArray(I_SPECIES, mask_arr, 0);
        IntVector va = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector vb = IntVector.fromArray(I_SPECIES, ib, 0);
        va.lanewise(VectorOperators.DIV, vb, mask).intoArray(ir, 0);
    }

    @Test
    @IR(counts = { IRNode.DIV_VL, ">= 1" },
        applyIfCPUFeature = { "sve", "true" })
    public static void testMaskedDivLong() {
        VectorMask<Long> mask = VectorMask.fromArray(L_SPECIES, mask_arr, 0);
        LongVector va = LongVector.fromArray(L_SPECIES, la, 0);
        LongVector vb = LongVector.fromArray(L_SPECIES, lb, 0);
        va.lanewise(VectorOperators.DIV, vb, mask).intoArray(lr, 0);
    }

    @Test
    @IR(counts = { IRNode.DIV_VF, ">= 1" },
        applyIfCPUFeature = { "sve", "true" })
    public static void testMaskedDivFloat() {
        VectorMask<Float> mask = VectorMask.fromArray(F_SPECIES, mask_arr, 0);
        FloatVector va = FloatVector.fromArray(F_SPECIES, fa, 0);
        FloatVector vb = FloatVector.fromArray(F_SPECIES, fb, 0);
        va.lanewise(VectorOperators.DIV, vb, mask).intoArray(fr, 0);
    }

    @Test
    @IR(counts = { IRNode.DIV_VD, ">= 1" },
        applyIfCPUFeature = { "sve", "true" })
    public static void testMaskedDivDouble() {
        VectorMask<Double> mask = VectorMask.fromArray(D_SPECIES, mask_arr, 0);
        DoubleVector va = DoubleVector.fromArray(D_SPECIES, da, 0);
        DoubleVector vb = DoubleVector.fromArray(D_SPECIES, db, 0);
        va.lanewise(VectorOperators.DIV, vb, mask).intoArray(dr, 0);
    }

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.setDefaultWarmup(10000)
                     .addFlags("--add-modules=jdk.incubator.vector")
                     .start();
    }
}
