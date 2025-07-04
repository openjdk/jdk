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
* @bug 8356760
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
    static void maskFromLongKernel(VectorSpecies<?> species, long inputLong) {
        var vmask = VectorMask.fromLong(species, inputLong);
        vmask.intoArray(mr, 0);
    }

    @DontInline
    static void verifyMaskFromLong(VectorSpecies<?> species, long inputLong, boolean expectedValue) {
        for (int i = 0; i < species.length(); i++) {
            if (mr[i] != expectedValue) {
                Asserts.fail("Mask bit " + i + " is expected to be " + expectedValue +
                        " but was " + mr[i] + " for long " + inputLong);
            }
        }
    }

    @ForceInline
    public static void testMaskFromLong(VectorSpecies<?> species) {
        int vlen = species.length();
        long inputLong = 0L;
        maskFromLongKernel(species, inputLong);
        verifyMaskFromLong(species, inputLong, false);

        inputLong = vlen >= 64 ? 0L : (0x1L << vlen);
        maskFromLongKernel(species, inputLong);
        verifyMaskFromLong(species, inputLong, false);

        inputLong = -1L;
        maskFromLongKernel(species, inputLong);
        verifyMaskFromLong(species, inputLong, true);

        inputLong = (-1L >>> (64 - vlen));
        maskFromLongKernel(species, inputLong);
        verifyMaskFromLong(species, inputLong, true);
    }

    @Test
    @IR(counts = { IRNode.MASK_ALL, "> 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 0" },
        applyIfCPUFeatureOr = { "sve", "true", "avx512", "true", "rvv", "true" })
    @IR(counts = { IRNode.REPLICATE_B, "> 0" },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve", "false" })
    @IR(counts = { IRNode.REPLICATE_B, "> 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 0" },
        applyIfCPUFeatureAnd = { "avx2", "true", "avx512", "false" })
    public static void testMaskFromLongByte() {
        testMaskFromLong(B_SPECIES);
    }

    @Test
    @IR(counts = { IRNode.MASK_ALL, "> 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 0" },
        applyIfCPUFeatureOr = { "sve", "true", "avx512", "true", "rvv", "true" })
    @IR(counts = { IRNode.REPLICATE_S, "> 0" },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve", "false" })
    @IR(counts = { IRNode.REPLICATE_S, "> 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 0" },
        applyIfCPUFeatureAnd = { "avx2", "true", "avx512", "false" })
    public static void testMaskFromLongShort() {
        testMaskFromLong(S_SPECIES);
    }

    @Test
    @IR(counts = { IRNode.MASK_ALL, "> 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 0" },
        applyIfCPUFeatureOr = { "sve", "true", "avx512", "true", "rvv", "true" })
    @IR(counts = { IRNode.REPLICATE_I, "> 0" },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve", "false" })
    @IR(counts = { IRNode.REPLICATE_I, "> 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 0" },
        applyIfCPUFeatureAnd = { "avx2", "true", "avx512", "false" })
    public static void testMaskFromLongInt() {
        testMaskFromLong(I_SPECIES);
    }

    @Test
    @IR(counts = { IRNode.MASK_ALL, "> 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 0" },
        applyIfCPUFeatureOr = { "sve", "true", "avx512", "true", "rvv", "true" })
    @IR(counts = { IRNode.REPLICATE_L, "> 0" },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve", "false" })
    @IR(counts = { IRNode.REPLICATE_L, "> 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 0" },
        applyIfCPUFeatureAnd = { "avx2", "true", "avx512", "false" })
    public static void testMaskFromLongLong() {
        testMaskFromLong(L_SPECIES);
    }

    @Test
    @IR(counts = { IRNode.MASK_ALL, "> 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 0" },
        applyIfCPUFeatureOr = { "sve", "true", "avx512", "true", "rvv", "true" })
    @IR(counts = { IRNode.REPLICATE_I, "> 0" },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve", "false" })
    @IR(counts = { IRNode.REPLICATE_I, "> 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 0" },
        applyIfCPUFeatureAnd = { "avx2", "true", "avx512", "false" })
    public static void testMaskFromLongFloat() {
        testMaskFromLong(F_SPECIES);
    }

    @Test
    @IR(counts = { IRNode.MASK_ALL, "> 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 0" },
        applyIfCPUFeatureOr = { "sve", "true", "avx512", "true", "rvv", "true" })
    @IR(counts = { IRNode.REPLICATE_L, "> 0" },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve", "false" })
    @IR(counts = { IRNode.REPLICATE_L, "> 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 0" },
        applyIfCPUFeatureAnd = { "avx2", "true", "avx512", "false" })
    public static void testMaskFromLongDouble() {
        testMaskFromLong(D_SPECIES);
    }

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.addFlags("--add-modules=jdk.incubator.vector");
        testFramework.start();
    }
}
