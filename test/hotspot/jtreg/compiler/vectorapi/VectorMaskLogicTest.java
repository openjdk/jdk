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
 * @summary Test vector mask ((A & B) ^ B) canonicalization to (~A & B)
 * @modules jdk.incubator.vector
 *
 * @run driver ${test.main.class}
 */

package compiler.vectorapi;

import compiler.lib.ir_framework.*;
import jdk.incubator.vector.*;
import java.util.Random;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

public class VectorMaskLogicTest {
    private static final VectorSpecies<Byte> B_SPECIES = ByteVector.SPECIES_MAX;
    private static final VectorSpecies<Short> S_SPECIES = ShortVector.SPECIES_MAX;
    private static final VectorSpecies<Integer> I_SPECIES = IntVector.SPECIES_MAX;
    private static final VectorSpecies<Long> L_SPECIES = LongVector.SPECIES_MAX;

    private static final int BUF_LEN = 256;
    private static final Random RD = Utils.getRandomInstance();

    private static final boolean[] ma = new boolean[BUF_LEN];
    private static final boolean[] mb = new boolean[BUF_LEN];
    private static final boolean[] mr = new boolean[BUF_LEN];

    private interface BoolAt { boolean at(int i); }

    static {
        for (int i = 0; i < BUF_LEN; i++) {
            ma[i] = RD.nextBoolean();
            mb[i] = RD.nextBoolean();
        }
    }

    @DontInline
    private static void verifyMask(boolean[] result, int len, BoolAt expected) {
        for (int i = 0; i < len; i++) {
            Asserts.assertEquals(expected.at(i), result[i]);
        }
    }

    // mask not_and: (A & B) ^ B is canonicalized to ~A & B. On SVE it is
    // matched by VMASK_AND_NOT; on NEON masks use the vector VAND_NOT rule.

    @Test
    @IR(counts = { IRNode.VAND_NOT_I, ">= 1" },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve", "false" })
    @IR(counts = { IRNode.VMASK_AND_NOT_I, ">= 1" },
        applyIfCPUFeature = { "sve", "true" })
    public static void testMaskNotAndByte() {
        VectorMask<Byte> avm = VectorMask.fromArray(B_SPECIES, ma, 0);
        VectorMask<Byte> bvm = VectorMask.fromArray(B_SPECIES, mb, 0);
        avm.and(bvm).xor(bvm).intoArray(mr, 0);
        verifyMask(mr, B_SPECIES.length(), i -> (ma[i] & mb[i]) ^ mb[i]);
    }

    @Test
    @IR(counts = { IRNode.VAND_NOT_I, ">= 1" },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve", "false" })
    @IR(counts = { IRNode.VMASK_AND_NOT_I, ">= 1" },
        applyIfCPUFeature = { "sve", "true" })
    public static void testMaskNotAndShort() {
        VectorMask<Short> avm = VectorMask.fromArray(S_SPECIES, ma, 0);
        VectorMask<Short> bvm = VectorMask.fromArray(S_SPECIES, mb, 0);
        avm.and(bvm).xor(bvm).intoArray(mr, 0);
        verifyMask(mr, S_SPECIES.length(), i -> (ma[i] & mb[i]) ^ mb[i]);
    }

    @Test
    @IR(counts = { IRNode.VAND_NOT_I, ">= 1" },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve", "false" })
    @IR(counts = { IRNode.VMASK_AND_NOT_I, ">= 1" },
        applyIfCPUFeature = { "sve", "true" })
    public static void testMaskNotAndInt() {
        VectorMask<Integer> avm = VectorMask.fromArray(I_SPECIES, ma, 0);
        VectorMask<Integer> bvm = VectorMask.fromArray(I_SPECIES, mb, 0);
        avm.and(bvm).xor(bvm).intoArray(mr, 0);
        verifyMask(mr, I_SPECIES.length(), i -> (ma[i] & mb[i]) ^ mb[i]);
    }

    @Test
    @IR(counts = { IRNode.VAND_NOT_L, ">= 1" },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve", "false" })
    @IR(counts = { IRNode.VMASK_AND_NOT_L, ">= 1" },
        applyIfCPUFeature = { "sve", "true" })
    public static void testMaskNotAndLong() {
        VectorMask<Long> avm = VectorMask.fromArray(L_SPECIES, ma, 0);
        VectorMask<Long> bvm = VectorMask.fromArray(L_SPECIES, mb, 0);
        avm.and(bvm).xor(bvm).intoArray(mr, 0);
        verifyMask(mr, L_SPECIES.length(), i -> (ma[i] & mb[i]) ^ mb[i]);
    }

    // Exercise canonicalization when the shared operand is the first input of
    // the inner AndVMask: XorVMask(AndVMask(b, a), b).

    @Test
    @IR(counts = { IRNode.VAND_NOT_I, ">= 1" },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve", "false" })
    @IR(counts = { IRNode.VMASK_AND_NOT_I, ">= 1" },
        applyIfCPUFeature = { "sve", "true" })
    public static void testMaskNotAndCommutativeByte() {
        VectorMask<Byte> avm = VectorMask.fromArray(B_SPECIES, ma, 0);
        VectorMask<Byte> bvm = VectorMask.fromArray(B_SPECIES, mb, 0);
        bvm.and(avm).xor(bvm).intoArray(mr, 0);
        verifyMask(mr, B_SPECIES.length(), i -> (ma[i] & mb[i]) ^ mb[i]);
    }

    @Test
    @IR(counts = { IRNode.VAND_NOT_I, ">= 1" },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve", "false" })
    @IR(counts = { IRNode.VMASK_AND_NOT_I, ">= 1" },
        applyIfCPUFeature = { "sve", "true" })
    public static void testMaskNotAndCommutativeShort() {
        VectorMask<Short> avm = VectorMask.fromArray(S_SPECIES, ma, 0);
        VectorMask<Short> bvm = VectorMask.fromArray(S_SPECIES, mb, 0);
        bvm.and(avm).xor(bvm).intoArray(mr, 0);
        verifyMask(mr, S_SPECIES.length(), i -> (ma[i] & mb[i]) ^ mb[i]);
    }

    @Test
    @IR(counts = { IRNode.VAND_NOT_I, ">= 1" },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve", "false" })
    @IR(counts = { IRNode.VMASK_AND_NOT_I, ">= 1" },
        applyIfCPUFeature = { "sve", "true" })
    public static void testMaskNotAndCommutativeInt() {
        VectorMask<Integer> avm = VectorMask.fromArray(I_SPECIES, ma, 0);
        VectorMask<Integer> bvm = VectorMask.fromArray(I_SPECIES, mb, 0);
        bvm.and(avm).xor(bvm).intoArray(mr, 0);
        verifyMask(mr, I_SPECIES.length(), i -> (ma[i] & mb[i]) ^ mb[i]);
    }

    @Test
    @IR(counts = { IRNode.VAND_NOT_L, ">= 1" },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve", "false" })
    @IR(counts = { IRNode.VMASK_AND_NOT_L, ">= 1" },
        applyIfCPUFeature = { "sve", "true" })
    public static void testMaskNotAndCommutativeLong() {
        VectorMask<Long> avm = VectorMask.fromArray(L_SPECIES, ma, 0);
        VectorMask<Long> bvm = VectorMask.fromArray(L_SPECIES, mb, 0);
        bvm.and(avm).xor(bvm).intoArray(mr, 0);
        verifyMask(mr, L_SPECIES.length(), i -> (ma[i] & mb[i]) ^ mb[i]);
    }

    // Exercise canonicalization when the shared operand is the first input of
    // the XorVMask: XorVMask(b, AndVMask(a, b)), i.e. B ^ (A & B).

    @Test
    @IR(counts = { IRNode.VAND_NOT_I, ">= 1" },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve", "false" })
    @IR(counts = { IRNode.VMASK_AND_NOT_I, ">= 1" },
        applyIfCPUFeature = { "sve", "true" })
    public static void testMaskNotAndXorCommutativeByte() {
        VectorMask<Byte> avm = VectorMask.fromArray(B_SPECIES, ma, 0);
        VectorMask<Byte> bvm = VectorMask.fromArray(B_SPECIES, mb, 0);
        bvm.xor(avm.and(bvm)).intoArray(mr, 0);
        verifyMask(mr, B_SPECIES.length(), i -> (ma[i] & mb[i]) ^ mb[i]);
    }

    @Test
    @IR(counts = { IRNode.VAND_NOT_I, ">= 1" },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve", "false" })
    @IR(counts = { IRNode.VMASK_AND_NOT_I, ">= 1" },
        applyIfCPUFeature = { "sve", "true" })
    public static void testMaskNotAndXorCommutativeShort() {
        VectorMask<Short> avm = VectorMask.fromArray(S_SPECIES, ma, 0);
        VectorMask<Short> bvm = VectorMask.fromArray(S_SPECIES, mb, 0);
        bvm.xor(avm.and(bvm)).intoArray(mr, 0);
        verifyMask(mr, S_SPECIES.length(), i -> (ma[i] & mb[i]) ^ mb[i]);
    }

    @Test
    @IR(counts = { IRNode.VAND_NOT_I, ">= 1" },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve", "false" })
    @IR(counts = { IRNode.VMASK_AND_NOT_I, ">= 1" },
        applyIfCPUFeature = { "sve", "true" })
    public static void testMaskNotAndXorCommutativeInt() {
        VectorMask<Integer> avm = VectorMask.fromArray(I_SPECIES, ma, 0);
        VectorMask<Integer> bvm = VectorMask.fromArray(I_SPECIES, mb, 0);
        bvm.xor(avm.and(bvm)).intoArray(mr, 0);
        verifyMask(mr, I_SPECIES.length(), i -> (ma[i] & mb[i]) ^ mb[i]);
    }

    @Test
    @IR(counts = { IRNode.VAND_NOT_L, ">= 1" },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve", "false" })
    @IR(counts = { IRNode.VMASK_AND_NOT_L, ">= 1" },
        applyIfCPUFeature = { "sve", "true" })
    public static void testMaskNotAndXorCommutativeLong() {
        VectorMask<Long> avm = VectorMask.fromArray(L_SPECIES, ma, 0);
        VectorMask<Long> bvm = VectorMask.fromArray(L_SPECIES, mb, 0);
        bvm.xor(avm.and(bvm)).intoArray(mr, 0);
        verifyMask(mr, L_SPECIES.length(), i -> (ma[i] & mb[i]) ^ mb[i]);
    }

    // AndVMask has another use, so the rewrite must not fire.

    @Test
    @IR(failOn = { IRNode.VAND_NOT_I },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve", "false" })
    @IR(failOn = { IRNode.VMASK_AND_NOT_I },
        applyIfCPUFeature = { "sve", "true" })
    public static void testMaskNotAndMultiUseByte() {
        VectorMask<Byte> avm = VectorMask.fromArray(B_SPECIES, ma, 0);
        VectorMask<Byte> bvm = VectorMask.fromArray(B_SPECIES, mb, 0);
        VectorMask<Byte> mand = avm.and(bvm);
        mand.xor(bvm).intoArray(mr, 0);
        verifyMask(mr, B_SPECIES.length(), i -> (ma[i] & mb[i]) ^ mb[i]);
        mand.intoArray(mr, 0);
        verifyMask(mr, B_SPECIES.length(), i -> ma[i] & mb[i]);
    }

    @Test
    @IR(failOn = { IRNode.VAND_NOT_I },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve", "false" })
    @IR(failOn = { IRNode.VMASK_AND_NOT_I },
        applyIfCPUFeature = { "sve", "true" })
    public static void testMaskNotAndMultiUseShort() {
        VectorMask<Short> avm = VectorMask.fromArray(S_SPECIES, ma, 0);
        VectorMask<Short> bvm = VectorMask.fromArray(S_SPECIES, mb, 0);
        VectorMask<Short> mand = avm.and(bvm);
        mand.xor(bvm).intoArray(mr, 0);
        verifyMask(mr, S_SPECIES.length(), i -> (ma[i] & mb[i]) ^ mb[i]);
        mand.intoArray(mr, 0);
        verifyMask(mr, S_SPECIES.length(), i -> ma[i] & mb[i]);
    }

    @Test
    @IR(failOn = { IRNode.VAND_NOT_I },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve", "false" })
    @IR(failOn = { IRNode.VMASK_AND_NOT_I },
        applyIfCPUFeature = { "sve", "true" })
    public static void testMaskNotAndMultiUseInt() {
        VectorMask<Integer> avm = VectorMask.fromArray(I_SPECIES, ma, 0);
        VectorMask<Integer> bvm = VectorMask.fromArray(I_SPECIES, mb, 0);
        VectorMask<Integer> mand = avm.and(bvm);
        mand.xor(bvm).intoArray(mr, 0);
        verifyMask(mr, I_SPECIES.length(), i -> (ma[i] & mb[i]) ^ mb[i]);
        mand.intoArray(mr, 0);
        verifyMask(mr, I_SPECIES.length(), i -> ma[i] & mb[i]);
    }

    @Test
    @IR(failOn = { IRNode.VAND_NOT_L },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve", "false" })
    @IR(failOn = { IRNode.VMASK_AND_NOT_L },
        applyIfCPUFeature = { "sve", "true" })
    public static void testMaskNotAndMultiUseLong() {
        VectorMask<Long> avm = VectorMask.fromArray(L_SPECIES, ma, 0);
        VectorMask<Long> bvm = VectorMask.fromArray(L_SPECIES, mb, 0);
        VectorMask<Long> mand = avm.and(bvm);
        mand.xor(bvm).intoArray(mr, 0);
        verifyMask(mr, L_SPECIES.length(), i -> (ma[i] & mb[i]) ^ mb[i]);
        mand.intoArray(mr, 0);
        verifyMask(mr, L_SPECIES.length(), i -> ma[i] & mb[i]);
    }

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.setDefaultWarmup(10000)
                     .addFlags("--add-modules=jdk.incubator.vector")
                     .start();
    }
}
