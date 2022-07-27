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
 * @requires vm.cpu.features ~= ".*simd.*" | vm.cpu.features ~= ".*sve.*"
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
    private static boolean[] ma;
    private static boolean[] mb;
    private static boolean[] mc;
    private static boolean[] mr;

    static {
        ia = new int[LENGTH];
        ib = new int[LENGTH];
        ir = new int[LENGTH];
        ma = new boolean[LENGTH];
        mb = new boolean[LENGTH];
        mc = new boolean[LENGTH];
        mr = new boolean[LENGTH];

        for (int i = 0; i < LENGTH; i++) {
            ia[i] = RD.nextInt(25);
            ib[i] = RD.nextInt(25);
            ma[i] = RD.nextBoolean();
            mb[i] = RD.nextBoolean();
            mc[i] = RD.nextBoolean();
        }
    }

    @Test
    @Warmup(10000)
    @IR(counts = { "bic", " >= 1" })
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
    @IR(counts = { "bic", " >= 1" })
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
