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
 * @bug 8382052
 * @key randomness
 * @library /test/lib /
 * @summary IR tests for AArch64 BITWISE_BLEND optimization match rules
 * @modules jdk.incubator.vector
 *
 * @run driver ${test.main.class}
 */

package compiler.vectorapi;

import compiler.lib.generators.*;
import compiler.lib.ir_framework.*;
import jdk.incubator.vector.*;

public class VectorBitwiseBlendTest {

    private static final Generators RD = Generators.G;

    private static final VectorSpecies<Byte> B_SPECIES = ByteVector.SPECIES_MAX;
    private static final VectorSpecies<Short> S_SPECIES = ShortVector.SPECIES_MAX;
    private static final VectorSpecies<Integer> I_SPECIES = IntVector.SPECIES_MAX;
    private static final VectorSpecies<Long> L_SPECIES = LongVector.SPECIES_MAX;

    private static final int BUF_LEN = 256;

    private static final byte[] ba = new byte[BUF_LEN];
    private static final byte[] bb = new byte[BUF_LEN];
    private static final byte[] bc = new byte[BUF_LEN];
    private static final byte[] br = new byte[BUF_LEN];

    private static final short[] sa = new short[BUF_LEN];
    private static final short[] sb = new short[BUF_LEN];
    private static final short[] sc = new short[BUF_LEN];
    private static final short[] sr = new short[BUF_LEN];

    private static final int[] ia = new int[BUF_LEN];
    private static final int[] ib = new int[BUF_LEN];
    private static final int[] ic = new int[BUF_LEN];
    private static final int[] ir = new int[BUF_LEN];

    private static final long[] la = new long[BUF_LEN];
    private static final long[] lb = new long[BUF_LEN];
    private static final long[] lc = new long[BUF_LEN];
    private static final long[] lr = new long[BUF_LEN];

    private static final boolean[] mask_arr = new boolean[BUF_LEN];

    static {
        Generator<Integer> iGen = RD.ints();
        Generator<Long> lGen = RD.longs();

        for (int i = 0; i < BUF_LEN; i++) {
            mask_arr[i] = (i & 1) != 0;
            ba[i] = iGen.next().byteValue();
            bb[i] = iGen.next().byteValue();
            bc[i] = iGen.next().byteValue();
            sa[i] = iGen.next().shortValue();
            sb[i] = iGen.next().shortValue();
            sc[i] = iGen.next().shortValue();
        }
        RD.fill(iGen, ia);
        RD.fill(iGen, ib);
        RD.fill(iGen, ic);
        RD.fill(lGen, la);
        RD.fill(lGen, lb);
        RD.fill(lGen, lc);
    }

    @Test
    @IR(counts = { IRNode.VBITWISE_BLEND_NEON_SVE1, "= 1" },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve2", "false" },
        applyIf = { "MaxVectorSize", "<= 16" })
    @IR(counts = { IRNode.VBITWISE_BLEND_SVE2, "= 1" },
        applyIfCPUFeature = { "sve2", "true" })
    public static void testUnmaskedBlendByte() {
        ByteVector va = ByteVector.fromArray(B_SPECIES, ba, 0);
        ByteVector vb = ByteVector.fromArray(B_SPECIES, bb, 0);
        ByteVector vc = ByteVector.fromArray(B_SPECIES, bc, 0);
        va.lanewise(VectorOperators.BITWISE_BLEND, vb, vc).intoArray(br, 0);
    }

    @Test
    @IR(counts = { IRNode.VBITWISE_BLEND_NEON_SVE1, "= 1" },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve2", "false" },
        applyIf = { "MaxVectorSize", "<= 16" })
    @IR(counts = { IRNode.VBITWISE_BLEND_SVE2, "= 1" },
        applyIfCPUFeature = { "sve2", "true" })
    public static void testUnmaskedBlendShort() {
        ShortVector va = ShortVector.fromArray(S_SPECIES, sa, 0);
        ShortVector vb = ShortVector.fromArray(S_SPECIES, sb, 0);
        ShortVector vc = ShortVector.fromArray(S_SPECIES, sc, 0);
        va.lanewise(VectorOperators.BITWISE_BLEND, vb, vc).intoArray(sr, 0);
    }

    @Test
    @IR(counts = { IRNode.VBITWISE_BLEND_NEON_SVE1, "= 1" },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve2", "false" },
        applyIf = { "MaxVectorSize", "<= 16" })
    @IR(counts = { IRNode.VBITWISE_BLEND_SVE2, "= 1" },
        applyIfCPUFeature = { "sve2", "true" })
    public static void testUnmaskedBlendInt() {
        IntVector va = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector vb = IntVector.fromArray(I_SPECIES, ib, 0);
        IntVector vc = IntVector.fromArray(I_SPECIES, ic, 0);
        va.lanewise(VectorOperators.BITWISE_BLEND, vb, vc).intoArray(ir, 0);
    }

    @Test
    @IR(counts = { IRNode.VBITWISE_BLEND_NEON_SVE1, "= 1" },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve2", "false" },
        applyIf = { "MaxVectorSize", "<= 16" })
    @IR(counts = { IRNode.VBITWISE_BLEND_SVE2, "= 1" },
        applyIfCPUFeature = { "sve2", "true" })
    public static void testUnmaskedBlendLong() {
        LongVector va = LongVector.fromArray(L_SPECIES, la, 0);
        LongVector vb = LongVector.fromArray(L_SPECIES, lb, 0);
        LongVector vc = LongVector.fromArray(L_SPECIES, lc, 0);
        va.lanewise(VectorOperators.BITWISE_BLEND, vb, vc).intoArray(lr, 0);
    }

    @Test
    @IR(counts = { IRNode.VBITWISE_BLEND_NEON_SVE1, "= 1" },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve", "false" },
        applyIf = { "MaxVectorSize", "<= 16" })
    @IR(counts = { IRNode.VBITWISE_BLEND_MASKED_SVE1, "= 1" },
        applyIfCPUFeatureAnd = { "sve", "true", "sve2", "false" },
        applyIf = { "MaxVectorSize", "<= 16" })
    @IR(counts = { IRNode.VBITWISE_BLEND_MASKED_SVE2, "= 1" },
        applyIfCPUFeature = { "sve2", "true" })
    public static void testMaskedBlendByte() {
        VectorMask<Byte> mask = VectorMask.fromArray(B_SPECIES, mask_arr, 0);
        ByteVector va = ByteVector.fromArray(B_SPECIES, ba, 0);
        ByteVector vb = ByteVector.fromArray(B_SPECIES, bb, 0);
        ByteVector vc = ByteVector.fromArray(B_SPECIES, bc, 0);
        va.lanewise(VectorOperators.BITWISE_BLEND, vb, vc, mask).intoArray(br, 0);
    }

    @Test
    @IR(counts = { IRNode.VBITWISE_BLEND_NEON_SVE1, "= 1" },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve", "false" },
        applyIf = { "MaxVectorSize", "<= 16" })
    @IR(counts = { IRNode.VBITWISE_BLEND_MASKED_SVE1, "= 1" },
        applyIfCPUFeatureAnd = { "sve", "true", "sve2", "false" },
        applyIf = { "MaxVectorSize", "<= 16" })
    @IR(counts = { IRNode.VBITWISE_BLEND_MASKED_SVE2, "= 1" },
        applyIfCPUFeature = { "sve2", "true" })
    public static void testMaskedBlendShort() {
        VectorMask<Short> mask = VectorMask.fromArray(S_SPECIES, mask_arr, 0);
        ShortVector va = ShortVector.fromArray(S_SPECIES, sa, 0);
        ShortVector vb = ShortVector.fromArray(S_SPECIES, sb, 0);
        ShortVector vc = ShortVector.fromArray(S_SPECIES, sc, 0);
        va.lanewise(VectorOperators.BITWISE_BLEND, vb, vc, mask).intoArray(sr, 0);
    }

    @Test
    @IR(counts = { IRNode.VBITWISE_BLEND_NEON_SVE1, "= 1" },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve", "false" },
        applyIf = { "MaxVectorSize", "<= 16" })
    @IR(counts = { IRNode.VBITWISE_BLEND_MASKED_SVE1, "= 1" },
        applyIfCPUFeatureAnd = { "sve", "true", "sve2", "false" },
        applyIf = { "MaxVectorSize", "<= 16" })
    @IR(counts = { IRNode.VBITWISE_BLEND_MASKED_SVE2, "= 1" },
        applyIfCPUFeature = { "sve2", "true" })
    public static void testMaskedBlendInt() {
        VectorMask<Integer> mask = VectorMask.fromArray(I_SPECIES, mask_arr, 0);
        IntVector va = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector vb = IntVector.fromArray(I_SPECIES, ib, 0);
        IntVector vc = IntVector.fromArray(I_SPECIES, ic, 0);
        va.lanewise(VectorOperators.BITWISE_BLEND, vb, vc, mask).intoArray(ir, 0);
    }

    @Test
    @IR(counts = { IRNode.VBITWISE_BLEND_NEON_SVE1, "= 1" },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve", "false" },
        applyIf = { "MaxVectorSize", "<= 16" })
    @IR(counts = { IRNode.VBITWISE_BLEND_MASKED_SVE1, "= 1" },
        applyIfCPUFeatureAnd = { "sve", "true", "sve2", "false" },
        applyIf = { "MaxVectorSize", "<= 16" })
    @IR(counts = { IRNode.VBITWISE_BLEND_MASKED_SVE2, "= 1" },
        applyIfCPUFeature = { "sve2", "true" })
    public static void testMaskedBlendLong() {
        VectorMask<Long> mask = VectorMask.fromArray(L_SPECIES, mask_arr, 0);
        LongVector va = LongVector.fromArray(L_SPECIES, la, 0);
        LongVector vb = LongVector.fromArray(L_SPECIES, lb, 0);
        LongVector vc = LongVector.fromArray(L_SPECIES, lc, 0);
        va.lanewise(VectorOperators.BITWISE_BLEND, vb, vc, mask).intoArray(lr, 0);
    }

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.setDefaultWarmup(10000)
                     .addFlags("--add-modules=jdk.incubator.vector")
                     .start();
    }
}
