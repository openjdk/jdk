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
* @run driver compiler.vectorapi.VectorMaskToLongTest
*/

package compiler.vectorapi;

import compiler.lib.ir_framework.*;
import jdk.incubator.vector.*;
import jdk.test.lib.Asserts;

public class VectorMaskToLongTest {
    static final VectorSpecies<Byte> B_SPECIES = ByteVector.SPECIES_MAX;
    static final VectorSpecies<Short> S_SPECIES = ShortVector.SPECIES_MAX;
    static final VectorSpecies<Integer> I_SPECIES = IntVector.SPECIES_MAX;
    static final VectorSpecies<Float> F_SPECIES = FloatVector.SPECIES_MAX;
    static final VectorSpecies<Long> L_SPECIES = LongVector.SPECIES_MAX;
    static final VectorSpecies<Double> D_SPECIES = DoubleVector.SPECIES_MAX;

    @ForceInline
    static long maskFromLongToLong(VectorSpecies<?> species, long inputLong) {
        var vmask = VectorMask.fromLong(species, inputLong);
        return vmask.toLong();
    }

    @DontInline
    static void verifyMaskFromLongToLong(VectorSpecies<?> species, long inputLong, long got) {
        long expected = inputLong & (-1L >>> (64 - species.length()));
        Asserts.assertEquals(expected, got, "for input long " + inputLong);
    }

    @Test
    @IR(counts = { IRNode.MASK_ALL, "= 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 0",
                   IRNode.VECTOR_MASK_TO_LONG, "= 0" },
        applyIfCPUFeatureOr = { "sve", "true", "avx512", "true", "rvv", "true" })
    @IR(counts = { IRNode.REPLICATE_B, "= 0",
                   IRNode.VECTOR_MASK_TO_LONG, "= 0" },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve", "false" })
    @IR(counts = { IRNode.REPLICATE_B, "= 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 0",
                   IRNode.VECTOR_MASK_TO_LONG, "= 0" },
        applyIfCPUFeatureAnd = { "avx2", "true", "avx512", "false" })
    public static void testMaskFromLongToLongByte() {
        int vlen = B_SPECIES.length();
        long inputLong = 0L;
        long got = maskFromLongToLong(B_SPECIES, inputLong);
        verifyMaskFromLongToLong(B_SPECIES, inputLong, got);

        inputLong = vlen >= 64 ? 0 : (0x1L << vlen);
        got = maskFromLongToLong(B_SPECIES, inputLong);
        verifyMaskFromLongToLong(B_SPECIES, inputLong, got);

        inputLong = -1L;
        got = maskFromLongToLong(B_SPECIES, inputLong);
        verifyMaskFromLongToLong(B_SPECIES, inputLong, got);

        inputLong = (0xFFFFFFFFFFFFFFFFL >>> (64 - vlen));
        got = maskFromLongToLong(B_SPECIES, inputLong);
        verifyMaskFromLongToLong(B_SPECIES, inputLong, got);
    }

    @Test
    @IR(counts = { IRNode.MASK_ALL, "= 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 0",
                   IRNode.VECTOR_MASK_TO_LONG, "= 0" },
        applyIfCPUFeatureOr = { "sve", "true", "avx512", "true", "rvv", "true" })
    @IR(counts = { IRNode.REPLICATE_S, "= 0",
                   IRNode.VECTOR_MASK_TO_LONG, "= 0" },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve", "false" })
    @IR(counts = { IRNode.REPLICATE_S, "= 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 0",
                   IRNode.VECTOR_MASK_TO_LONG, "= 0" },
        applyIfCPUFeatureAnd = { "avx2", "true", "avx512", "false" })
    public static void testMaskFromLongToLongShort() {
        int vlen = S_SPECIES.length();
        long inputLong = 0L;
        long got = maskFromLongToLong(S_SPECIES, inputLong);
        verifyMaskFromLongToLong(S_SPECIES, inputLong, got);

        inputLong = vlen >= 64 ? 0 : (0x1L << vlen);
        got = maskFromLongToLong(S_SPECIES, inputLong);
        verifyMaskFromLongToLong(S_SPECIES, inputLong, got);

        inputLong = -1L;
        got = maskFromLongToLong(S_SPECIES, inputLong);
        verifyMaskFromLongToLong(S_SPECIES, inputLong, got);

        inputLong = (0xFFFFFFFFFFFFFFFFL >>> (64 - vlen));
        got = maskFromLongToLong(S_SPECIES, inputLong);
        verifyMaskFromLongToLong(S_SPECIES, inputLong, got);
    }

    @Test
    @IR(counts = { IRNode.MASK_ALL, "= 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 0",
                   IRNode.VECTOR_MASK_TO_LONG, "= 0" },
        applyIfCPUFeatureOr = { "sve", "true", "avx512", "true", "rvv", "true" })
    @IR(counts = { IRNode.REPLICATE_I, "= 0",
                   IRNode.VECTOR_MASK_TO_LONG, "= 0" },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve", "false" })
    @IR(counts = { IRNode.REPLICATE_I, "= 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 0",
                   IRNode.VECTOR_MASK_TO_LONG, "= 0" },
        applyIfCPUFeatureAnd = { "avx2", "true", "avx512", "false" })
    public static void testMaskFromLongToLongInt() {
        int vlen = I_SPECIES.length();
        long inputLong = 0L;
        long got = maskFromLongToLong(I_SPECIES, inputLong);
        verifyMaskFromLongToLong(I_SPECIES, inputLong, got);

        inputLong = vlen >= 64 ? 0 : (0x1L << vlen);
        got = maskFromLongToLong(I_SPECIES, inputLong);
        verifyMaskFromLongToLong(I_SPECIES, inputLong, got);

        inputLong = -1L;
        got = maskFromLongToLong(I_SPECIES, inputLong);
        verifyMaskFromLongToLong(I_SPECIES, inputLong, got);

        inputLong = (0xFFFFFFFFFFFFFFFFL >>> (64 - vlen));
        got = maskFromLongToLong(I_SPECIES, inputLong);
        verifyMaskFromLongToLong(I_SPECIES, inputLong, got);
    }

    @Test
    @IR(counts = { IRNode.MASK_ALL, "= 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 0",
                   IRNode.VECTOR_MASK_TO_LONG, "= 0" },
        applyIfCPUFeatureOr = { "sve", "true", "avx512", "true", "rvv", "true" })
    @IR(counts = { IRNode.REPLICATE_L, "= 0",
                   IRNode.VECTOR_MASK_TO_LONG, "= 0" },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve", "false" })
    @IR(counts = { IRNode.REPLICATE_L, "= 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 0",
                   IRNode.VECTOR_MASK_TO_LONG, "= 0" },
        applyIfCPUFeatureAnd = { "avx2", "true", "avx512", "false" })
    public static void testMaskFromLongToLongLong() {
        int vlen = L_SPECIES.length();
        long inputLong = 0L;
        long got = maskFromLongToLong(L_SPECIES, inputLong);
        verifyMaskFromLongToLong(L_SPECIES, inputLong, got);

        inputLong = vlen >= 64 ? 0 : (0x1L << vlen);
        got = maskFromLongToLong(L_SPECIES, inputLong);
        verifyMaskFromLongToLong(L_SPECIES, inputLong, got);

        inputLong = -1L;
        got = maskFromLongToLong(L_SPECIES, inputLong);
        verifyMaskFromLongToLong(L_SPECIES, inputLong, got);

        inputLong = (0xFFFFFFFFFFFFFFFFL >>> (64 - vlen));
        got = maskFromLongToLong(L_SPECIES, inputLong);
        verifyMaskFromLongToLong(L_SPECIES, inputLong, got);
    }

    @Test
    @IR(counts = { IRNode.MASK_ALL, "= 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 0",
                   IRNode.VECTOR_MASK_TO_LONG, "= 0" },
        applyIfCPUFeatureOr = { "sve", "true", "avx512", "true", "rvv", "true" })
    @IR(counts = { IRNode.REPLICATE_I, "= 0",
                   IRNode.VECTOR_MASK_TO_LONG, "= 0" },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve", "false" })
    @IR(counts = { IRNode.REPLICATE_I, "= 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 0",
                   IRNode.VECTOR_MASK_TO_LONG, "= 0" },
        applyIfCPUFeatureAnd = { "avx2", "true", "avx512", "false" })
    public static void testMaskFromLongToLongFloat() {
        int vlen = F_SPECIES.length();
        long inputLong = 0L;
        long got = maskFromLongToLong(F_SPECIES, inputLong);
        verifyMaskFromLongToLong(F_SPECIES, inputLong, got);

        inputLong = vlen >= 64 ? 0 : (0x1L << vlen);
        got = maskFromLongToLong(F_SPECIES, inputLong);
        verifyMaskFromLongToLong(F_SPECIES, inputLong, got);

        inputLong = -1L;
        got = maskFromLongToLong(F_SPECIES, inputLong);
        verifyMaskFromLongToLong(F_SPECIES, inputLong, got);

        inputLong = (0xFFFFFFFFFFFFFFFFL >>> (64 - vlen));
        got = maskFromLongToLong(F_SPECIES, inputLong);
        verifyMaskFromLongToLong(F_SPECIES, inputLong, got);
    }

    @Test
    @IR(counts = { IRNode.MASK_ALL, "= 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 0",
                   IRNode.VECTOR_MASK_TO_LONG, "= 0" },
        applyIfCPUFeatureOr = { "sve", "true", "avx512", "true", "rvv", "true" })
    @IR(counts = { IRNode.REPLICATE_L, "= 0",
                   IRNode.VECTOR_MASK_TO_LONG, "= 0" },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve", "false" })
    @IR(counts = { IRNode.REPLICATE_L, "= 0",
                   IRNode.VECTOR_LONG_TO_MASK, "= 0",
                   IRNode.VECTOR_MASK_TO_LONG, "= 0" },
        applyIfCPUFeatureAnd = { "avx2", "true", "avx512", "false" })
    public static void testMaskFromLongToLongDouble() {
        int vlen = D_SPECIES.length();
        long inputLong = 0L;
        long got = maskFromLongToLong(D_SPECIES, inputLong);
        verifyMaskFromLongToLong(D_SPECIES, inputLong, got);

        inputLong = vlen >= 64 ? 0 : (0x1L << vlen);
        got = maskFromLongToLong(D_SPECIES, inputLong);
        verifyMaskFromLongToLong(D_SPECIES, inputLong, got);

        inputLong = -1L;
        got = maskFromLongToLong(D_SPECIES, inputLong);
        verifyMaskFromLongToLong(D_SPECIES, inputLong, got);

        inputLong = (0xFFFFFFFFFFFFFFFFL >>> (64 - vlen));
        got = maskFromLongToLong(D_SPECIES, inputLong);
        verifyMaskFromLongToLong(D_SPECIES, inputLong, got);
    }

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.addFlags("--add-modules=jdk.incubator.vector");
        testFramework.start();
    }
}