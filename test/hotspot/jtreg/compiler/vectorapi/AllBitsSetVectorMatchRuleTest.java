/*
 * Copyright (c) 2022, Arm Limited. All rights reserved.
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

package compiler.vectorapi;

import compiler.lib.ir_framework.*;

import java.util.Random;

import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.LongVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

/**
 * @test
 * @bug 8287984
 * @key randomness
 * @library /test/lib /
 * @requires vm.compiler2.enabled
 * @requires (os.simpleArch == "aarch64" & vm.cpu.features ~= ".*asimd.*") | (os.simpleArch == "riscv64" & vm.cpu.features ~= ".*zvbb.*")
 * @summary AArch64: [vector] Make all bits set vector sharable for match rules
 * @modules jdk.incubator.vector
 *
 * @run driver compiler.vectorapi.AllBitsSetVectorMatchRuleTest
 */

public class AllBitsSetVectorMatchRuleTest {
    private static final VectorSpecies<Integer> I_SPECIES = IntVector.SPECIES_MAX;
    private static final VectorSpecies<Long> L_SPECIES = LongVector.SPECIES_MAX;

    private static int LENGTH = 128;
    private static final Random RD = Utils.getRandomInstance();

    private static int[] ia;
    private static int[] ib;
    private static int[] ir;
    private static long[] la;
    private static long[] lb;
    private static long[] lr;
    private static boolean[] ma;
    private static boolean[] mb;
    private static boolean[] mc;
    private static boolean[] mr;

    static {
        ia = new int[LENGTH];
        ib = new int[LENGTH];
        ir = new int[LENGTH];
        la = new long[LENGTH];
        lb = new long[LENGTH];
        lr = new long[LENGTH];
        ma = new boolean[LENGTH];
        mb = new boolean[LENGTH];
        mc = new boolean[LENGTH];
        mr = new boolean[LENGTH];

        for (int i = 0; i < LENGTH; i++) {
            ia[i] = RD.nextInt(25);
            ib[i] = RD.nextInt(25);
            la[i] = RD.nextLong(25);
            lb[i] = RD.nextLong(25);
            ma[i] = RD.nextBoolean();
            mb[i] = RD.nextBoolean();
            mc[i] = RD.nextBoolean();
        }
    }

    // Tests of C2 match rules for vector ops containing an all-bits-set vector operand.

    @Test
    @IR(counts = { IRNode.VAND_NOT_I, " >= 1" })
    public static void testAllBitsSetVector() {
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector bv = IntVector.fromArray(I_SPECIES, ib, 0);
        av.not().lanewise(VectorOperators.AND_NOT, bv).intoArray(ir, 0);

        // Verify results
        for (int i = 0; i < I_SPECIES.length(); i++) {
            Asserts.assertEquals((~ia[i]) & (~ib[i]), ir[i]);
        }
    }

    @Test
    @IR(counts = { IRNode.VAND_NOT_L, " >= 1" })
    public static void testVectorVAndNotL() {
        LongVector av = LongVector.fromArray(L_SPECIES, la, 0);
        LongVector bv = LongVector.fromArray(L_SPECIES, lb, 0);
        av.not().lanewise(VectorOperators.AND_NOT, bv).intoArray(lr, 0);

        // Verify results
        for (int i = 0; i < L_SPECIES.length(); i++) {
            Asserts.assertEquals((~la[i]) & (~lb[i]), lr[i]);
        }
    }

    @Test
    @IR(counts = { IRNode.VAND_NOT_I_MASKED, " >= 1" }, applyIfCPUFeature = {"sve", "true"})
    @IR(counts = { IRNode.VAND_NOT_I_MASKED, " >= 1" }, applyIfPlatform = {"riscv64", "true"})
    public static void testVectorVAndNotIMasked() {
        VectorMask<Integer> avm = VectorMask.fromArray(I_SPECIES, ma, 0);
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector bv = IntVector.fromArray(I_SPECIES, ib, 0);
        av.not().lanewise(VectorOperators.AND_NOT, bv, avm).intoArray(ir, 0);

        // Verify results
        for (int i = 0; i < I_SPECIES.length(); i++) {
            if (ma[i] == true) {
                Asserts.assertEquals((~ia[i]) & (~ib[i]), ir[i]);
            }
        }
    }

    @Test
    @IR(counts = { IRNode.VAND_NOT_L_MASKED, " >= 1" }, applyIfCPUFeature = {"sve", "true"})
    @IR(counts = { IRNode.VAND_NOT_L_MASKED, " >= 1" }, applyIfPlatform = {"riscv64", "true"})
    public static void testVectorVAndNotLMasked() {
        VectorMask<Long> avm = VectorMask.fromArray(L_SPECIES, ma, 0);
        LongVector av = LongVector.fromArray(L_SPECIES, la, 0);
        LongVector bv = LongVector.fromArray(L_SPECIES, lb, 0);
        av.not().lanewise(VectorOperators.AND_NOT, bv, avm).intoArray(lr, 0);

        // Verify results
        for (int i = 0; i < L_SPECIES.length(); i++) {
            if (ma[i] == true) {
                Asserts.assertEquals((~la[i]) & (~lb[i]), lr[i]);
            }
        }
    }

    @Test
    @IR(counts = { IRNode.RISCV_VAND_NOTI_VX, " >= 1" }, applyIfPlatform = {"riscv64", "true"})
    public static void testAllBitsSetVectorRegI() {
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        int bs = ib[0];
        av.not().lanewise(VectorOperators.AND_NOT, bs).intoArray(ir, 0);

        // Verify results
        for (int i = 0; i < I_SPECIES.length(); i++) {
            Asserts.assertEquals((~ia[i]) & (~bs), ir[i]);
        }
    }

    @Test
    @IR(counts = { IRNode.RISCV_VAND_NOTL_VX, " >= 1" }, applyIfPlatform = {"riscv64", "true"})
    public static void testAllBitsSetVectorRegL() {
        LongVector av = LongVector.fromArray(L_SPECIES, la, 0);
        long bs = lb[0];
        av.not().lanewise(VectorOperators.AND_NOT, bs).intoArray(lr, 0);

        // Verify results
        for (int i = 0; i < L_SPECIES.length(); i++) {
            Asserts.assertEquals((~la[i]) & (~bs), lr[i]);
        }
    }

    @Test
    @IR(counts = { IRNode.RISCV_VAND_NOTI_VX_MASKED, " >= 1" }, applyIfPlatform = {"riscv64", "true"})
    public static void testAllBitsSetVectorRegIMask() {
        VectorMask<Integer> avm = VectorMask.fromArray(I_SPECIES, ma, 0);
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        int bs = ib[0];
        av.not().lanewise(VectorOperators.AND_NOT, bs, avm).intoArray(ir, 0);

        // Verify results
        for (int i = 0; i < I_SPECIES.length(); i++) {
            if (ma[i] == true) {
                Asserts.assertEquals((~ia[i]) & (~bs), ir[i]);
            }
        }
    }

    @Test
    @IR(counts = { IRNode.RISCV_VAND_NOTL_VX_MASKED, " >= 1" }, applyIfPlatform = {"riscv64", "true"})
    public static void testAllBitsSetVectorRegLMask() {
        VectorMask<Long> avm = VectorMask.fromArray(L_SPECIES, ma, 0);
        LongVector av = LongVector.fromArray(L_SPECIES, la, 0);
        long bs = lb[0];
        av.not().lanewise(VectorOperators.AND_NOT, bs, avm).intoArray(lr, 0);

        // Verify results
        for (int i = 0; i < L_SPECIES.length(); i++) {
            if (ma[i] == true) {
                Asserts.assertEquals((~la[i]) & (~bs), lr[i]);
            }
        }
    }

    // Tests that VectorMask.andNot() chains match to VMASK_AND_NOT / VAND_NOT (two andNot ops).
    @Test
    @IR(counts = { IRNode.VAND_NOT_I, "2" }, applyIfCPUFeatureAnd = {"asimd", "true", "sve", "false"})
    @IR(counts = { IRNode.VMASK_AND_NOT_I, "2" }, applyIfCPUFeature = {"sve", "true"})
    public static void testMaskAndNotI() {
        VectorMask<Integer> avm = VectorMask.fromArray(I_SPECIES, ma, 0);
        VectorMask<Integer> bvm = VectorMask.fromArray(I_SPECIES, mb, 0);
        VectorMask<Integer> cvm = VectorMask.fromArray(I_SPECIES, mc, 0);
        avm.andNot(bvm).andNot(cvm).intoArray(mr, 0);

        // Verify results
        for (int i = 0; i < I_SPECIES.length(); i++) {
            Asserts.assertEquals((ma[i] & (!mb[i])) & (!mc[i]), mr[i]);
        }
    }

    @Test
    @IR(counts = { IRNode.VAND_NOT_L, "2" }, applyIfCPUFeatureAnd = {"asimd", "true", "sve", "false"})
    @IR(counts = { IRNode.VMASK_AND_NOT_L, "2" }, applyIfCPUFeature = {"sve", "true"})
    public static void testMaskAndNotL() {
        VectorMask<Long> avm = VectorMask.fromArray(L_SPECIES, ma, 0);
        VectorMask<Long> bvm = VectorMask.fromArray(L_SPECIES, mb, 0);
        VectorMask<Long> cvm = VectorMask.fromArray(L_SPECIES, mc, 0);
        avm.andNot(bvm).andNot(cvm).intoArray(mr, 0);

        // Verify results
        for (int i = 0; i < L_SPECIES.length(); i++) {
            Asserts.assertEquals((ma[i] & (!mb[i])) & (!mc[i]), mr[i]);
        }
    }

    // Tests that mask.not().and(other) matches to VMASK_AND_NOT (AndVMask commutative rule).
    @Test
    @IR(counts = { IRNode.VMASK_AND_NOT_I, "1" }, applyIfCPUFeature = {"sve", "true"})
    public static void testCommutativeAndVMaskI() {
        VectorMask<Integer> avm = VectorMask.fromArray(I_SPECIES, ma, 0);
        VectorMask<Integer> bvm = VectorMask.fromArray(I_SPECIES, mb, 0);
        avm.not().and(bvm).intoArray(mr, 0);

        // Verify results
        for (int i = 0; i < I_SPECIES.length(); i++) {
            Asserts.assertEquals((!ma[i]) & (mb[i]), mr[i]);
        }
    }

    @Test
    @IR(counts = { IRNode.VMASK_AND_NOT_L, "1" }, applyIfCPUFeature = {"sve", "true"})
    public static void testCommutativeAndVMaskL() {
        VectorMask<Long> avm = VectorMask.fromArray(L_SPECIES, ma, 0);
        VectorMask<Long> bvm = VectorMask.fromArray(L_SPECIES, mb, 0);
        avm.not().and(bvm).intoArray(mr, 0);

        // Verify results
        for (int i = 0; i < L_SPECIES.length(); i++) {
            Asserts.assertEquals((!ma[i]) & (mb[i]), mr[i]);
        }
    }

    // Tests that mask.and(allTrue.xor(other)) matches to VMASK_AND_NOT (XorVMask commutative rule).
    @Test
    @IR(counts = { IRNode.VMASK_AND_NOT_I, "1" }, applyIfCPUFeature = {"sve", "true"})
    public static void testCommutativeXorVMaskI() {
        VectorMask<Integer> avm = VectorMask.fromArray(I_SPECIES, ma, 0);
        VectorMask<Integer> bvm = VectorMask.fromArray(I_SPECIES, mb, 0);
        VectorMask<Integer> alltrue = I_SPECIES.maskAll(true);
        bvm.and(alltrue.xor(avm)).intoArray(mr, 0);

        // Verify results
        for (int i = 0; i < I_SPECIES.length(); i++) {
            Asserts.assertEquals((mb[i]) & (!ma[i]), mr[i]);
        }
    }

    @Test
    @IR(counts = { IRNode.VMASK_AND_NOT_L, "1" }, applyIfCPUFeature = {"sve", "true"})
    public static void testCommutativeXorVMaskL() {
        VectorMask<Long> avm = VectorMask.fromArray(L_SPECIES, ma, 0);
        VectorMask<Long> bvm = VectorMask.fromArray(L_SPECIES, mb, 0);
        VectorMask<Long> alltrue = L_SPECIES.maskAll(true);
        bvm.and(alltrue.xor(avm)).intoArray(mr, 0);

        // Verify results
        for (int i = 0; i < L_SPECIES.length(); i++) {
            Asserts.assertEquals((mb[i]) & (!ma[i]), mr[i]);
        }
    }

    // Tests that only one MaskAll machine node is generated (no duplicate "maskall" nodes).
    @Test
    @IR(counts = { IRNode.AARCH64_VMASK_ALL_IMM_I, "1" }, applyIfCPUFeature = {"sve", "true"})
    public static void testSingleMaskAllI() {
        VectorMask<Integer> avm = VectorMask.fromArray(I_SPECIES, ma, 0);
        VectorMask<Integer> bvm = VectorMask.fromArray(I_SPECIES, mb, 0);
        avm.not().or(bvm.not()).intoArray(mr, 0);

        // Verify results
        for (int i = 0; i < I_SPECIES.length(); i++) {
            Asserts.assertEquals(!ma[i] | !mb[i], mr[i]);
        }
    }

    @Test
    @IR(counts = { IRNode.AARCH64_VMASK_ALL_IMM_L, "1" }, applyIfCPUFeature = {"sve", "true"})
    public static void testSingleMaskAllL() {
        VectorMask<Long> avm = VectorMask.fromArray(L_SPECIES, ma, 0);
        VectorMask<Long> bvm = VectorMask.fromArray(L_SPECIES, mb, 0);
        avm.not().or(bvm.not()).intoArray(mr, 0);

        // Verify results
        for (int i = 0; i < L_SPECIES.length(); i++) {
            Asserts.assertEquals(!ma[i] | !mb[i], mr[i]);
        }
    }

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.setDefaultWarmup(10000)
                     .addFlags("--add-modules=jdk.incubator.vector")
                     .start();
    }
}
