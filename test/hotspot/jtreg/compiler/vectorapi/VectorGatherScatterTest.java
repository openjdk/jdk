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

import jdk.incubator.vector.DoubleVector;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.LongVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorShape;
import jdk.incubator.vector.VectorSpecies;

import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

/**
 * @test
 * @bug 8288397
 * @key randomness
 * @library /test/lib /
 * @requires vm.cpu.features ~= ".*sve.*"
 * @summary AArch64: Fix register issues in SVE backend match rules
 * @modules jdk.incubator.vector
 *
 * @run driver compiler.vectorapi.VectorGatherScatterTest
 */

public class VectorGatherScatterTest {
    private static final VectorSpecies<Double> D_SPECIES = DoubleVector.SPECIES_MAX;
    private static final VectorSpecies<Long> L_SPECIES = LongVector.SPECIES_MAX;
    private static final VectorSpecies<Integer> I_SPECIES =
        VectorSpecies.of(int.class, VectorShape.forBitSize(L_SPECIES.vectorBitSize() / 2));

    private static int LENGTH = 128;
    private static final Random RD = Utils.getRandomInstance();

    private static int[] ia;
    private static int[] ir;
    private static long[] la;
    private static long[] lr;
    private static double[] da;
    private static double[] dr;
    private static boolean[] m;

    static {
        ia = new int[LENGTH];
        ir = new int[LENGTH];
        la = new long[LENGTH];
        lr = new long[LENGTH];
        da = new double[LENGTH];
        dr = new double[LENGTH];
        m = new boolean[LENGTH];

        for (int i = 0; i < LENGTH; i++) {
            ia[i] = i;
            la[i] = RD.nextLong(25);
            da[i] = RD.nextDouble(25.0);
            m[i] = RD.nextBoolean();
        }
    }

    @Test
    @Warmup(10000)
    @IR(counts = { "LoadVectorGather", ">= 1" })
    public static void testLoadGather() {
        LongVector av = LongVector.fromArray(L_SPECIES, la, 0, ia, 0);
        av.intoArray(lr, 0);
        IntVector bv = IntVector.fromArray(I_SPECIES, ia, 0);
        bv.add(0).intoArray(ir, 0);

        for(int i = 0; i < I_SPECIES.length(); i++) {
            Asserts.assertEquals(ia[i], ir[i]);
        }
    }

    @Test
    @Warmup(10000)
    @IR(counts = { "LoadVectorGatherMasked", ">= 1" })
    public static void testLoadGatherMasked() {
        VectorMask<Long> mask = VectorMask.fromArray(L_SPECIES, m, 0);
        LongVector av = LongVector.fromArray(L_SPECIES, la, 0, ia, 0, mask);
        av.intoArray(lr, 0);
        IntVector bv = IntVector.fromArray(I_SPECIES, ia, 0);
        bv.add(0).intoArray(ir, 0);

        for(int i = 0; i < I_SPECIES.length(); i++) {
            Asserts.assertEquals(ia[i], ir[i]);
        }
    }

    @Test
    @Warmup(10000)
    @IR(counts = { "StoreVectorScatter", ">= 1" })
    public static void testStoreScatter() {
        DoubleVector av = DoubleVector.fromArray(D_SPECIES, da, 0);
        av.intoArray(dr, 0, ia, 0);
        IntVector bv = IntVector.fromArray(I_SPECIES, ia, 0);
        bv.add(0).intoArray(ir, 0);

        for(int i = 0; i < I_SPECIES.length(); i++) {
            Asserts.assertEquals(ia[i], ir[i]);
        }
    }

    @Test
    @Warmup(10000)
    @IR(counts = { "StoreVectorScatterMasked", ">= 1" })
    public static void testStoreScatterMasked() {
        VectorMask<Double> mask = VectorMask.fromArray(D_SPECIES, m, 0);
        DoubleVector av = DoubleVector.fromArray(D_SPECIES, da, 0);
        av.intoArray(dr, 0, ia, 0, mask);
        IntVector bv = IntVector.fromArray(I_SPECIES, ia, 0);
        bv.add(0).intoArray(ir, 0);

        for(int i = 0; i < I_SPECIES.length(); i++) {
            Asserts.assertEquals(ia[i], ir[i]);
        }
    }

    public static void main(String[] args) {
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector",
                                   "-XX:UseSVE=1");
    }
}
