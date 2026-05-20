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

package compiler.vectorapi;

import compiler.lib.ir_framework.*;
import compiler.lib.generators.*;
import jdk.incubator.vector.*;
import jdk.test.lib.Asserts;

/**
 * @test
 * @bug 8384571
 * @key randomness
 * @library /test/lib /
 * @summary Test Ideal/Identity transformations for VectorBlendNode
 * @modules jdk.incubator.vector
 *
 * @run driver ${test.main.class}
 */

public class VectorBlendTest {
    private static final VectorSpecies<Integer> I_SPECIES = IntVector.SPECIES_MAX;
    private static final VectorSpecies<Long> L_SPECIES = LongVector.SPECIES_MAX;

    private static final int LENGTH = 128;
    private static final Generators RD = Generators.G;

    private static int[] ia;
    private static int[] ib;
    private static int[] ic;
    private static int[] ir;
    private static long[] la;
    private static long[] lb;
    private static long[] lc;
    private static long[] lr;
    private static boolean[] m;

    static {
        ia = new int[LENGTH];
        ib = new int[LENGTH];
        ic = new int[LENGTH];
        ir = new int[LENGTH];
        la = new long[LENGTH];
        lb = new long[LENGTH];
        lc = new long[LENGTH];
        lr = new long[LENGTH];
        m = new boolean[LENGTH];

        Generator<Integer> iGen = RD.ints();
        Generator<Long> lGen = RD.longs();
        RD.fill(iGen, ia);
        RD.fill(iGen, ib);
        RD.fill(iGen, ic);
        RD.fill(lGen, la);
        RD.fill(lGen, lb);
        RD.fill(lGen, lc);

        for (int i = 0; i < LENGTH; i++) {
            m[i] = (i & 1) == 1;
        }
    }

    @DontInline
    public static void verifyInt(int[] trueValues, int[] falseValues) {
        for (int i = 0; i < I_SPECIES.length(); i++) {
            Asserts.assertEquals(m[i] ? trueValues[i] : falseValues[i], ir[i]);
        }
    }

    @DontInline
    public static void verifyLong(long[] trueValues, long[] falseValues) {
        for (int i = 0; i < L_SPECIES.length(); i++) {
            Asserts.assertEquals(m[i] ? trueValues[i] : falseValues[i], lr[i]);
        }
    }

    @DontInline
    public static void verifyAllInt(int[] expected) {
        for (int i = 0; i < I_SPECIES.length(); i++) {
            Asserts.assertEquals(expected[i], ir[i]);
        }
    }

    @DontInline
    public static void verifyAllLong(long[] expected) {
        for (int i = 0; i < L_SPECIES.length(); i++) {
            Asserts.assertEquals(expected[i], lr[i]);
        }
    }

    // VectorBlend(VectorBlend(a, b, m), c, m) => VectorBlend(a, c, m)
    @Test
    @IR(counts = {IRNode.VECTOR_BLEND_I, "1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testVectorBlendSameMask1() {
        VectorMask<Integer> mask = VectorMask.fromArray(I_SPECIES, m, 0);
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector bv = IntVector.fromArray(I_SPECIES, ib, 0);
        IntVector cv = IntVector.fromArray(I_SPECIES, ic, 0);
        av.blend(bv, mask).blend(cv, mask).intoArray(ir, 0);

        verifyInt(ic, ia);
    }

    // VectorBlend(a, VectorBlend(b, c, m), m) => VectorBlend(a, c, m)
    @Test
    @IR(counts = {IRNode.VECTOR_BLEND_L, "1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx2", "true", "rvv", "true"})
    public static void testVectorBlendSameMask2() {
        VectorMask<Long> mask = VectorMask.fromArray(L_SPECIES, m, 0);
        LongVector av = LongVector.fromArray(L_SPECIES, la, 0);
        LongVector bv = LongVector.fromArray(L_SPECIES, lb, 0);
        LongVector cv = LongVector.fromArray(L_SPECIES, lc, 0);
        av.blend(bv.blend(cv, mask), mask).intoArray(lr, 0);

        verifyLong(lc, la);
    }

    // VectorBlend(a, b, AllOnesMask) => b
    @Test
    @IR(failOn = {IRNode.VECTOR_BLEND_I},
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testBlendMaskAllTrueInt() {
        VectorMask<Integer> mask = I_SPECIES.maskAll(true);
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector bv = IntVector.fromArray(I_SPECIES, ib, 0);
        av.blend(bv, mask).intoArray(ir, 0);

        verifyAllInt(ib);
    }

    // VectorBlend(a, b, AllZerosMask) => a
    @Test
    @IR(failOn = {IRNode.VECTOR_BLEND_L},
        counts = {IRNode.STORE_VECTOR, ">=1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx2", "true", "rvv", "true"})
    public static void testBlendMaskAllFalseLong() {
        VectorMask<Long> mask = L_SPECIES.maskAll(false);
        LongVector av = LongVector.fromArray(L_SPECIES, la, 0);
        LongVector bv = LongVector.fromArray(L_SPECIES, lb, 0);
        av.blend(bv, mask).intoArray(lr, 0);

        verifyAllLong(la);
    }

    // VectorBlend(a, b, NOT(m)) => VectorBlend(b, a, m)
    @Test
    @IR(failOn = {IRNode.XOR_V_MASK, IRNode.XOR_V},
        counts = {IRNode.VECTOR_BLEND_I, "1"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testBlendNegatedMaskInt() {
        VectorMask<Integer> mask = VectorMask.fromArray(I_SPECIES, m, 0);
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector bv = IntVector.fromArray(I_SPECIES, ib, 0);
        av.blend(bv, mask.not()).intoArray(ir, 0);

        // mask.not()[i] is true when m[i] is false, and selects bv on those
        // lanes. After the swap, the equivalent VectorBlend selects:
        // m=true -> av, m=false -> bv.
        verifyInt(ia, ib);
    }

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.setDefaultWarmup(10000)
                     .addFlags("--add-modules=jdk.incubator.vector")
                     .start();
    }
}
