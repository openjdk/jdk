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
 * @bug 8388918
 * @key randomness
 * @library /test/lib /
 * @summary Test vector ((A & B) ^ B) canonicalization to (~A & B)
 * @modules jdk.incubator.vector
 *
 * @run driver ${test.main.class}
 */

package compiler.vectorapi;

import compiler.lib.generators.*;
import compiler.lib.ir_framework.*;
import jdk.incubator.vector.*;
import jdk.test.lib.Asserts;

public class VectorLogicTest {
    private static final Generators RD = Generators.G;

    private static final VectorSpecies<Byte> B_SPECIES = ByteVector.SPECIES_MAX;
    private static final VectorSpecies<Short> S_SPECIES = ShortVector.SPECIES_MAX;
    private static final VectorSpecies<Integer> I_SPECIES = IntVector.SPECIES_MAX;
    private static final VectorSpecies<Long> L_SPECIES = LongVector.SPECIES_MAX;

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

    static {
        Generator<Integer> iGen = RD.ints();
        Generator<Long> lGen = RD.longs();

        for (int i = 0; i < BUF_LEN; i++) {
            ba[i] = iGen.next().byteValue();
            bb[i] = iGen.next().byteValue();
            sa[i] = iGen.next().shortValue();
            sb[i] = iGen.next().shortValue();
        }
        RD.fill(iGen, ia);
        RD.fill(iGen, ib);
        RD.fill(lGen, la);
        RD.fill(lGen, lb);
    }

    @DontInline
    private static void verifyByte() {
        for (int i = 0; i < B_SPECIES.length(); i++) {
            Asserts.assertEquals((byte) ((ba[i] & bb[i]) ^ bb[i]), br[i]);
        }
    }

    @DontInline
    private static void verifyShort() {
        for (int i = 0; i < S_SPECIES.length(); i++) {
            Asserts.assertEquals((short) ((sa[i] & sb[i]) ^ sb[i]), sr[i]);
        }
    }

    @DontInline
    private static void verifyInt() {
        for (int i = 0; i < I_SPECIES.length(); i++) {
            Asserts.assertEquals((ia[i] & ib[i]) ^ ib[i], ir[i]);
        }
    }

    @DontInline
    private static void verifyLong() {
        for (int i = 0; i < L_SPECIES.length(); i++) {
            Asserts.assertEquals((la[i] & lb[i]) ^ lb[i], lr[i]);
        }
    }

    // not_and: (A & B) ^ B is canonicalized to ~A & B and then matched by the
    // existing AArch64 and_not rules.

    @Test
    @IR(counts = { IRNode.VAND_NOT_I, ">= 1" },
        applyIfCPUFeature = { "asimd", "true" })
    public static void testNotAndByte() {
        ByteVector va = ByteVector.fromArray(B_SPECIES, ba, 0);
        ByteVector vb = ByteVector.fromArray(B_SPECIES, bb, 0);
        va.and(vb).lanewise(VectorOperators.XOR, vb).intoArray(br, 0);
        verifyByte();
    }

    @Test
    @IR(counts = { IRNode.VAND_NOT_I, ">= 1" },
        applyIfCPUFeature = { "asimd", "true" })
    public static void testNotAndShort() {
        ShortVector va = ShortVector.fromArray(S_SPECIES, sa, 0);
        ShortVector vb = ShortVector.fromArray(S_SPECIES, sb, 0);
        va.and(vb).lanewise(VectorOperators.XOR, vb).intoArray(sr, 0);
        verifyShort();
    }

    @Test
    @IR(counts = { IRNode.VAND_NOT_I, ">= 1" },
        applyIfCPUFeature = { "asimd", "true" })
    public static void testNotAndInt() {
        IntVector va = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector vb = IntVector.fromArray(I_SPECIES, ib, 0);
        va.and(vb).lanewise(VectorOperators.XOR, vb).intoArray(ir, 0);
        verifyInt();
    }

    @Test
    @IR(counts = { IRNode.VAND_NOT_L, ">= 1" },
        applyIfCPUFeature = { "asimd", "true" })
    public static void testNotAndLong() {
        LongVector va = LongVector.fromArray(L_SPECIES, la, 0);
        LongVector vb = LongVector.fromArray(L_SPECIES, lb, 0);
        va.and(vb).lanewise(VectorOperators.XOR, vb).intoArray(lr, 0);
        verifyLong();
    }

    // Exercise canonicalization when the shared operand is the first input of
    // the inner AndV: XorV(AndV(b, a), b).

    @Test
    @IR(counts = { IRNode.VAND_NOT_I, ">= 1" },
        applyIfCPUFeature = { "asimd", "true" })
    public static void testNotAndCommutativeByte() {
        ByteVector va = ByteVector.fromArray(B_SPECIES, ba, 0);
        ByteVector vb = ByteVector.fromArray(B_SPECIES, bb, 0);
        vb.and(va).lanewise(VectorOperators.XOR, vb).intoArray(br, 0);
        verifyByte();
    }

    @Test
    @IR(counts = { IRNode.VAND_NOT_I, ">= 1" },
        applyIfCPUFeature = { "asimd", "true" })
    public static void testNotAndCommutativeShort() {
        ShortVector va = ShortVector.fromArray(S_SPECIES, sa, 0);
        ShortVector vb = ShortVector.fromArray(S_SPECIES, sb, 0);
        vb.and(va).lanewise(VectorOperators.XOR, vb).intoArray(sr, 0);
        verifyShort();
    }

    @Test
    @IR(counts = { IRNode.VAND_NOT_I, ">= 1" },
        applyIfCPUFeature = { "asimd", "true" })
    public static void testNotAndCommutativeInt() {
        IntVector va = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector vb = IntVector.fromArray(I_SPECIES, ib, 0);
        vb.and(va).lanewise(VectorOperators.XOR, vb).intoArray(ir, 0);
        verifyInt();
    }

    @Test
    @IR(counts = { IRNode.VAND_NOT_L, ">= 1" },
        applyIfCPUFeature = { "asimd", "true" })
    public static void testNotAndCommutativeLong() {
        LongVector va = LongVector.fromArray(L_SPECIES, la, 0);
        LongVector vb = LongVector.fromArray(L_SPECIES, lb, 0);
        vb.and(va).lanewise(VectorOperators.XOR, vb).intoArray(lr, 0);
        verifyLong();
    }

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.setDefaultWarmup(10000)
                     .addFlags("--add-modules=jdk.incubator.vector")
                     .start();
    }
}
