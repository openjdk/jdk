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

    @Test
    @Warmup(10000)
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
    @Warmup(10000)
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
    @Warmup(10000)
    @IR(counts = { IRNode.VAND_NOT_I_MASKED, " >= 1" }, applyIfPlatform = {"aarch64", "true"}, applyIf = {"UseSVE", "> 0"})
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
    @Warmup(10000)
    @IR(counts = { IRNode.VAND_NOT_L_MASKED, " >= 1" }, applyIfPlatform = {"aarch64", "true"}, applyIf = {"UseSVE", "> 0"})
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
    @Warmup(10000)
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
    @Warmup(10000)
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
    @Warmup(10000)
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
    @Warmup(10000)
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

    @Test
    @Warmup(10000)
    @IR(counts = { IRNode.VAND_NOT_L, " >= 1" }, applyIfPlatform = {"aarch64", "true"}, applyIf = {"UseSVE", "0"})
    @IR(counts = { IRNode.VMASK_AND_NOT_L, " >= 1" }, applyIfPlatform = {"aarch64", "true"}, applyIf = {"UseSVE", "> 0"})
    public static void testAllBitsSetMask() {
        VectorMask<Long> avm = VectorMask.fromArray(L_SPECIES, ma, 0);
        VectorMask<Long> bvm = VectorMask.fromArray(L_SPECIES, mb, 0);
        VectorMask<Long> cvm = VectorMask.fromArray(L_SPECIES, mc, 0);
        avm.andNot(bvm).andNot(cvm).intoArray(mr, 0);

        // Verify results
        for (int i = 0; i < L_SPECIES.length(); i++) {
            Asserts.assertEquals((ma[i] & (!mb[i])) & (!mc[i]), mr[i]);
        }
    }

    public static void main(String[] args) {
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector");
    }
}
