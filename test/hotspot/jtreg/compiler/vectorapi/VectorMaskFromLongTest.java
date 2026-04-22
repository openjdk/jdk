/*
 * Copyright (c) 2025, NVIDIA CORPORATION & AFFILIATES. All rights reserved.
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
 * @bug 8356760 8367391 8367292
 * @library /test/lib /
 * @summary IR test for VectorMask.fromLong()
 * @modules jdk.incubator.vector
 *
 * @run driver compiler.vectorapi.VectorMaskFromLongTest
 */

package compiler.vectorapi;

import compiler.lib.ir_framework.*;
import jdk.incubator.vector.*;
import jdk.test.lib.Asserts;

public class VectorMaskFromLongTest {
    static final VectorSpecies<Byte> B_SPECIES = ByteVector.SPECIES_MAX;
    static final VectorSpecies<Short> S_SPECIES = ShortVector.SPECIES_MAX;
    static final VectorSpecies<Integer> I_SPECIES = IntVector.SPECIES_MAX;
    static final VectorSpecies<Float> F_SPECIES = FloatVector.SPECIES_MAX;
    static final VectorSpecies<Long> L_SPECIES = LongVector.SPECIES_MAX;
    static final VectorSpecies<Double> D_SPECIES = DoubleVector.SPECIES_MAX;

    static boolean[] mr = new boolean[B_SPECIES.length()];

    @DontInline
    public static void verifyMaskFromLong(VectorSpecies<?> species, long inputLong) {
        for (int i = 0; i < species.length(); i++) {
            long expectedValue = (inputLong >>> i) & 1L;
            if (mr[i] != (expectedValue == 1L)) {
                Asserts.fail("Mask bit " + i + " is expected to be " + expectedValue +
                        " but was " + mr[i] + " for long " + inputLong);
            }
        }
    }

    // Tests for "VectorLongToMask(-1/0) => MaskAll(-1/0)"

    @ForceInline
    public static void fromLongMaskAllKernel(VectorSpecies<?> species, long inputLong ) {
        VectorMask.fromLong(species, inputLong).intoArray(mr, 0);
        verifyMaskFromLong(species, inputLong);
    }

    @ForceInline
    public static void testMaskFromLongMaskAll(VectorSpecies<?> species) {
        int vlen = species.length();
        long inputLong = 0L;
        fromLongMaskAllKernel(species, inputLong);

        inputLong = vlen >= 64 ? 0L : (0x1L << vlen);
        fromLongMaskAllKernel(species, inputLong);

        inputLong = -1L;
        fromLongMaskAllKernel(species, inputLong);

        inputLong = (-1L >>> (64 - vlen));
        fromLongMaskAllKernel(species, inputLong);
    }

    @Test
    @IR(counts = { IRNode.MASK_ALL, "> 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 0" },
        applyIfCPUFeatureOr = { "sve", "true", "avx512", "true", "rvv", "true" })
    @IR(counts = { IRNode.REPLICATE_B, "> 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 0" },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve", "false" })
    @IR(counts = { IRNode.REPLICATE_B, "> 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 0" },
        applyIfCPUFeatureAnd = { "avx2", "true", "avx512", "false" })
    public static void testMaskFromLongMaskAllByte() {
        testMaskFromLongMaskAll(B_SPECIES);
    }

    @Test
    @IR(counts = { IRNode.MASK_ALL, "> 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 0" },
        applyIfCPUFeatureOr = { "sve", "true", "avx512", "true", "rvv", "true" })
    @IR(counts = { IRNode.REPLICATE_S, "> 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 0" },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve", "false" })
    @IR(counts = { IRNode.REPLICATE_S, "> 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 0" },
        applyIfCPUFeatureAnd = { "avx2", "true", "avx512", "false" })
    public static void testMaskFromLongMaskAllShort() {
        testMaskFromLongMaskAll(S_SPECIES);
    }

    @Test
    @IR(counts = { IRNode.MASK_ALL, "> 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 0" },
        applyIfCPUFeatureOr = { "sve", "true", "avx512", "true", "rvv", "true" })
    @IR(counts = { IRNode.REPLICATE_I, "> 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 0" },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve", "false" })
    @IR(counts = { IRNode.REPLICATE_I, "> 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 0" },
        applyIfCPUFeatureAnd = { "avx2", "true", "avx512", "false" })
    public static void testMaskFromLongMaskAllInt() {
        testMaskFromLongMaskAll(I_SPECIES);
    }

    @Test
    @IR(counts = { IRNode.MASK_ALL, "> 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 0" },
        applyIfCPUFeatureOr = { "sve", "true", "avx512", "true", "rvv", "true" })
    @IR(counts = { IRNode.REPLICATE_L, "> 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 0" },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve", "false" })
    @IR(counts = { IRNode.REPLICATE_L, "> 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 0" },
        applyIfCPUFeatureAnd = { "avx2", "true", "avx512", "false" })
    public static void testMaskFromLongMaskAllLong() {
        testMaskFromLongMaskAll(L_SPECIES);
    }

    @Test
    @IR(counts = { IRNode.MASK_ALL, "> 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 0" },
        applyIfCPUFeatureOr = { "sve", "true", "avx512", "true", "rvv", "true" })
    @IR(counts = { IRNode.REPLICATE_I, "> 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 0" },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve", "false" })
    @IR(counts = { IRNode.REPLICATE_I, "> 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 0" },
        applyIfCPUFeatureAnd = { "avx2", "true", "avx512", "false" })
    public static void testMaskFromLongMaskAllFloat() {
        testMaskFromLongMaskAll(F_SPECIES);
    }

    @Test
    @IR(counts = { IRNode.MASK_ALL, "> 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 0" },
        applyIfCPUFeatureOr = { "sve", "true", "avx512", "true", "rvv", "true" })
    @IR(counts = { IRNode.REPLICATE_L, "> 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 0" },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve", "false" })
    @IR(counts = { IRNode.REPLICATE_L, "> 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 0" },
        applyIfCPUFeatureAnd = { "avx2", "true", "avx512", "false" })
    public static void testMaskFromLongMaskAllDouble() {
        testMaskFromLongMaskAll(D_SPECIES);
    }

    // Tests for general input long values. The purpose is to test the IRs
    // for API VectorMask.fromLong(). To avoid any IR being optimized out by
    // compiler, we insert a VectorMask.not() after fromLong().

    @ForceInline
    public static void fromLongGeneralKernel(VectorSpecies<?> species, long inputLong) {
        VectorMask.fromLong(species, inputLong).not().intoArray(mr, 0);
        verifyMaskFromLong(species, inputLong ^ -1L);
    }

    @ForceInline
    public static void testMaskFromLongGeneral(VectorSpecies<?> species) {
        fromLongGeneralKernel(species, (-1L >>> (64 - species.length())) - 1);
        fromLongGeneralKernel(species, (-1L >>> (64 - species.length())) >>> 1);
    }

    @Test
    @IR(counts = { IRNode.VECTOR_LOAD_MASK, "= 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 2" },
        applyIfCPUFeatureOr = { "avx512", "true", "rvv", "true" })
    @IR(counts = { IRNode.VECTOR_LOAD_MASK, "= 2",
                   IRNode.VECTOR_LONG_TO_MASK, "= 2" },
        applyIfCPUFeatureAnd = { "avx2", "true", "avx512", "false" })
    @IR(counts = { IRNode.VECTOR_LOAD_MASK, "= 2",
                   IRNode.VECTOR_LONG_TO_MASK, "= 2" },
        applyIfCPUFeature = { "svebitperm", "true" })
    public static void testMaskFromLongByte() {
        testMaskFromLongGeneral(B_SPECIES);
    }

    @Test
    @IR(counts = { IRNode.VECTOR_LOAD_MASK, "= 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 2" },
        applyIfCPUFeatureOr = { "avx512", "true", "rvv", "true" })
    @IR(counts = { IRNode.VECTOR_LOAD_MASK, "= 2",
                   IRNode.VECTOR_LONG_TO_MASK, "= 2" },
        applyIfCPUFeatureAnd = { "avx2", "true", "avx512", "false" })
    @IR(counts = { IRNode.VECTOR_LOAD_MASK, "= 2",
                   IRNode.VECTOR_LONG_TO_MASK, "= 2" },
        applyIfCPUFeature = { "svebitperm", "true" })
    public static void testMaskFromLongShort() {
        testMaskFromLongGeneral(S_SPECIES);
    }

    @Test
    @IR(counts = { IRNode.VECTOR_LOAD_MASK, "= 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 2" },
        applyIfCPUFeatureOr = { "avx512", "true", "rvv", "true" })
    @IR(counts = { IRNode.VECTOR_LOAD_MASK, "= 2",
                   IRNode.VECTOR_LONG_TO_MASK, "= 2" },
        applyIfCPUFeatureAnd = { "avx2", "true", "avx512", "false" })
    @IR(counts = { IRNode.VECTOR_LOAD_MASK, "= 2",
                   IRNode.VECTOR_LONG_TO_MASK, "= 2" },
        applyIfCPUFeature = { "svebitperm", "true" })
    public static void testMaskFromLongInt() {
        testMaskFromLongGeneral(I_SPECIES);
    }

    @Test
    @IR(counts = { IRNode.VECTOR_LOAD_MASK, "= 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 2" },
        applyIfCPUFeatureOr = { "avx512", "true", "rvv", "true" })
    @IR(counts = { IRNode.VECTOR_LOAD_MASK, "= 2",
                   IRNode.VECTOR_LONG_TO_MASK, "= 2" },
        applyIfCPUFeatureAnd = { "avx2", "true", "avx512", "false" })
    @IR(counts = { IRNode.VECTOR_LOAD_MASK, "= 2",
                   IRNode.VECTOR_LONG_TO_MASK, "= 2" },
        applyIfCPUFeature = { "svebitperm", "true" })
    public static void testMaskFromLongLong() {
        testMaskFromLongGeneral(L_SPECIES);
    }

    @Test
    @IR(counts = { IRNode.VECTOR_LOAD_MASK, "= 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 2" },
        applyIfCPUFeatureOr = { "avx512", "true", "rvv", "true" })
    @IR(counts = { IRNode.VECTOR_LOAD_MASK, "= 2",
                   IRNode.VECTOR_LONG_TO_MASK, "= 2" },
        applyIfCPUFeatureAnd = { "avx2", "true", "avx512", "false" })
    @IR(counts = { IRNode.VECTOR_LOAD_MASK, "= 2",
                   IRNode.VECTOR_LONG_TO_MASK, "= 2" },
        applyIfCPUFeature = { "svebitperm", "true" })
    public static void testMaskFromLongFloat() {
        testMaskFromLongGeneral(F_SPECIES);
    }

    @Test
    @IR(counts = { IRNode.VECTOR_LOAD_MASK, "= 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 2" },
        applyIfCPUFeatureOr = { "avx512", "true", "rvv", "true" })
    @IR(counts = { IRNode.VECTOR_LOAD_MASK, "= 2",
                   IRNode.VECTOR_LONG_TO_MASK, "= 2" },
        applyIfCPUFeatureAnd = { "avx2", "true", "avx512", "false" })
    @IR(counts = { IRNode.VECTOR_LOAD_MASK, "= 2",
                   IRNode.VECTOR_LONG_TO_MASK, "= 2" },
        applyIfCPUFeature = { "svebitperm", "true" })
    public static void testMaskFromLongDouble() {
        testMaskFromLongGeneral(D_SPECIES);
    }

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.setDefaultWarmup(10000)
                     .addFlags("--add-modules=jdk.incubator.vector")
                     .start();
    }
}
