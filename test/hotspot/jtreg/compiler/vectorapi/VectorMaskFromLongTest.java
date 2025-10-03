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
* @bug 8356760 8367391
* @library /test/lib /
* @summary Optimize VectorMask.fromLong for all-true/all-false cases
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

    @ForceInline
    public static void maskFromLongKernel(VectorSpecies<?> species, long inputLong) {
        VectorMask.fromLong(species, inputLong).intoArray(mr, 0);
    }

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

    @ForceInline
    public static void testMaskFromLong(VectorSpecies<?> species, long inputLong ) {
        maskFromLongKernel(species, inputLong);
        verifyMaskFromLong(species, inputLong);
    }

    @ForceInline
    public static void testMaskFromLongMaskAll(VectorSpecies<?> species) {
        int vlen = species.length();
        long inputLong = 0L;
        testMaskFromLong(species, inputLong);

        inputLong = vlen >= 64 ? 0L : (0x1L << vlen);
        testMaskFromLong(species, inputLong);

        inputLong = -1L;
        testMaskFromLong(species, inputLong);

        inputLong = (-1L >>> (64 - vlen));
        testMaskFromLong(species, inputLong);
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

    // Tests for general input long values

    @Test
    @IR(counts = { IRNode.MASK_ALL, "= 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 2" },
        applyIfCPUFeatureOr = { "sve2", "true", "avx512", "true", "rvv", "true" })
    @IR(counts = { IRNode.REPLICATE_B, "= 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 0" },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve", "false" })
    @IR(counts = { IRNode.REPLICATE_B, "= 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 2" },
        applyIfCPUFeatureAnd = { "avx2", "true", "avx512", "false" })
    public static void testMaskFromLongByte() {
        // Test cases where some but not all bits are set.
        testMaskFromLong(B_SPECIES, (-1L >>> (64 - B_SPECIES.length())) - 1);
        testMaskFromLong(B_SPECIES, (-1L >>> (64 - B_SPECIES.length())) >>> 1);
    }

    @Test
    @IR(counts = { IRNode.MASK_ALL, "= 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 2" },
        applyIfCPUFeatureOr = { "sve2", "true", "avx512", "true", "rvv", "true" })
    @IR(counts = { IRNode.REPLICATE_S, "= 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 0" },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve", "false" })
    @IR(counts = { IRNode.REPLICATE_S, "= 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 2" },
        applyIfCPUFeatureAnd = { "avx2", "true", "avx512", "false" })
    public static void testMaskFromLongShort() {
        // Test cases where some but not all bits are set.
        testMaskFromLong(S_SPECIES, (-1L >>> (64 - S_SPECIES.length())) - 1);
        testMaskFromLong(S_SPECIES, (-1L >>> (64 - S_SPECIES.length())) >>> 1);
    }

    @Test
    @IR(counts = { IRNode.MASK_ALL, "= 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 2" },
        applyIfCPUFeatureOr = { "sve2", "true", "avx512", "true", "rvv", "true" })
    @IR(counts = { IRNode.REPLICATE_I, "= 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 0" },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve", "false" })
    @IR(counts = { IRNode.REPLICATE_I, "= 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 2" },
        applyIfCPUFeatureAnd = { "avx2", "true", "avx512", "false" })
    public static void testMaskFromLongInt() {
        // Test cases where some but not all bits are set.
        testMaskFromLong(I_SPECIES, (-1L >>> (64 - I_SPECIES.length())) - 1);
        testMaskFromLong(I_SPECIES, (-1L >>> (64 - I_SPECIES.length())) >>> 1);
    }

    @Test
    @IR(counts = { IRNode.MASK_ALL, "= 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 2" },
        applyIfCPUFeatureOr = { "sve2", "true", "avx512", "true", "rvv", "true" })
    @IR(counts = { IRNode.REPLICATE_L, "= 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 0" },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve", "false" })
    @IR(counts = { IRNode.REPLICATE_L, "= 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 2" },
        applyIfCPUFeatureAnd = { "avx2", "true", "avx512", "false" })
    public static void testMaskFromLongLong() {
        // Test cases where some but not all bits are set.
        testMaskFromLong(L_SPECIES, (-1L >>> (64 - L_SPECIES.length())) - 1);
        testMaskFromLong(L_SPECIES, (-1L >>> (64 - L_SPECIES.length())) >>> 1);
    }

    @Test
    @IR(counts = { IRNode.MASK_ALL, "= 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 2" },
        applyIfCPUFeatureOr = { "sve2", "true", "avx512", "true", "rvv", "true" })
    @IR(counts = { IRNode.REPLICATE_I, "= 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 0" },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve", "false" })
    @IR(counts = { IRNode.REPLICATE_I, "= 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 2" },
        applyIfCPUFeatureAnd = { "avx2", "true", "avx512", "false" })
    public static void testMaskFromLongFloat() {
        // Test cases where some but not all bits are set.
        testMaskFromLong(F_SPECIES, (-1L >>> (64 - F_SPECIES.length())) - 1);
        testMaskFromLong(F_SPECIES, (-1L >>> (64 - F_SPECIES.length())) >>> 1);
    }

    @Test
    @IR(counts = { IRNode.MASK_ALL, "= 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 2" },
        applyIfCPUFeatureOr = { "sve2", "true", "avx512", "true", "rvv", "true" })
    @IR(counts = { IRNode.REPLICATE_L, "= 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 0" },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve", "false" })
    @IR(counts = { IRNode.REPLICATE_L, "= 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 2" },
        applyIfCPUFeatureAnd = { "avx2", "true", "avx512", "false" })
    public static void testMaskFromLongDouble() {
        // Test cases where some but not all bits are set.
        testMaskFromLong(D_SPECIES, (-1L >>> (64 - D_SPECIES.length())) - 1);
        testMaskFromLong(D_SPECIES, (-1L >>> (64 - D_SPECIES.length())) >>> 1);
    }

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.setDefaultWarmup(10000)
                     .addFlags("--add-modules=jdk.incubator.vector")
                     .start();
    }
}
