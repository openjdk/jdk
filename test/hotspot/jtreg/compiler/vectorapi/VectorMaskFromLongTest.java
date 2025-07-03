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
        int vlen = B_SPECIES.length();
        long inputLong = 0L;
        maskFromLongKernel(B_SPECIES, inputLong);
        verifyMaskFromLong(B_SPECIES, inputLong, false);

        inputLong = vlen >= 64 ? 0 : (0x1L << vlen);
        maskFromLongKernel(B_SPECIES, inputLong);
        verifyMaskFromLong(B_SPECIES, inputLong, false);

        inputLong = -1L;
        maskFromLongKernel(B_SPECIES, inputLong);
        verifyMaskFromLong(B_SPECIES, inputLong, true);

        inputLong = (0xFFFFFFFFFFFFFFFFL >>> (64 - vlen));
        maskFromLongKernel(B_SPECIES, inputLong);
        verifyMaskFromLong(B_SPECIES, inputLong, true);
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
        int vlen = S_SPECIES.length();
        long inputLong = 0L;
        maskFromLongKernel(S_SPECIES, inputLong);
        verifyMaskFromLong(S_SPECIES, inputLong, false);

        inputLong = vlen >= 64 ? 0 : (0x1L << vlen);
        maskFromLongKernel(S_SPECIES, inputLong);
        verifyMaskFromLong(S_SPECIES, inputLong, false);

        inputLong = -1L;
        maskFromLongKernel(S_SPECIES, inputLong);
        verifyMaskFromLong(S_SPECIES, inputLong, true);

        inputLong = (0xFFFFFFFFFFFFFFFFL >>> (64 - vlen));
        maskFromLongKernel(S_SPECIES, inputLong);
        verifyMaskFromLong(S_SPECIES, inputLong, true);
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
        int vlen = I_SPECIES.length();
        long inputLong = 0L;
        maskFromLongKernel(I_SPECIES, inputLong);
        verifyMaskFromLong(I_SPECIES, inputLong, false);

        inputLong = vlen >= 64 ? 0 : (0x1L << vlen);
        maskFromLongKernel(I_SPECIES, inputLong);
        verifyMaskFromLong(I_SPECIES, inputLong, false);

        inputLong = -1L;
        maskFromLongKernel(I_SPECIES, inputLong);
        verifyMaskFromLong(I_SPECIES, inputLong, true);

        inputLong = (0xFFFFFFFFFFFFFFFFL >>> (64 - vlen));
        maskFromLongKernel(I_SPECIES, inputLong);
        verifyMaskFromLong(I_SPECIES, inputLong, true);
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
        int vlen = L_SPECIES.length();
        long inputLong = 0L;
        maskFromLongKernel(L_SPECIES, inputLong);
        verifyMaskFromLong(L_SPECIES, inputLong, false);

        inputLong = vlen >= 64 ? 0 : (0x1L << vlen);
        maskFromLongKernel(L_SPECIES, inputLong);
        verifyMaskFromLong(L_SPECIES, inputLong, false);

        inputLong = -1L;
        maskFromLongKernel(L_SPECIES, inputLong);
        verifyMaskFromLong(L_SPECIES, inputLong, true);

        inputLong = (0xFFFFFFFFFFFFFFFFL >>> (64 - vlen));
        maskFromLongKernel(L_SPECIES, inputLong);
        verifyMaskFromLong(L_SPECIES, inputLong, true);
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
        int vlen = F_SPECIES.length();
        long inputLong = 0L;
        maskFromLongKernel(F_SPECIES, inputLong);
        verifyMaskFromLong(F_SPECIES, inputLong, false);

        inputLong = vlen >= 64 ? 0 : (0x1L << vlen);
        maskFromLongKernel(F_SPECIES, inputLong);
        verifyMaskFromLong(F_SPECIES, inputLong, false);

        inputLong = -1L;
        maskFromLongKernel(F_SPECIES, inputLong);
        verifyMaskFromLong(F_SPECIES, inputLong, true);

        inputLong = (0xFFFFFFFFFFFFFFFFL >>> (64 - vlen));
        maskFromLongKernel(F_SPECIES, inputLong);
        verifyMaskFromLong(F_SPECIES, inputLong, true);
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
        int vlen = D_SPECIES.length();
        long inputLong = 0L;
        maskFromLongKernel(D_SPECIES, inputLong);
        verifyMaskFromLong(D_SPECIES, inputLong, false);

        inputLong = vlen >= 64 ? 0 : (0x1L << vlen);
        maskFromLongKernel(D_SPECIES, inputLong);
        verifyMaskFromLong(D_SPECIES, inputLong, false);

        inputLong = -1L;
        maskFromLongKernel(D_SPECIES, inputLong);
        verifyMaskFromLong(D_SPECIES, inputLong, true);

        inputLong = (0xFFFFFFFFFFFFFFFFL >>> (64 - vlen));
        maskFromLongKernel(D_SPECIES, inputLong);
        verifyMaskFromLong(D_SPECIES, inputLong, true);
    }

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.addFlags("--add-modules=jdk.incubator.vector");
        testFramework.start();
    }
}
