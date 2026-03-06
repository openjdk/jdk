/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8375688
 * @key randomness
 * @library /test/lib /
 * @summary VectorMaskToLong constant folding through VectorStoreMask must work under StressIncrementalInlining
 * @modules jdk.incubator.vector
 *
 * @run driver ${test.main.class}
 */

package compiler.vectorapi;

import compiler.lib.ir_framework.*;
import jdk.incubator.vector.*;
import jdk.test.lib.Asserts;

/**
 * Tests that VectorMaskToLongNode::Ideal_MaskAll folds constant masks even
 * when StressIncrementalInlining randomizes the IGVN worklist order.
 *
 * Each test method does {@code VectorMask.fromLong(SPECIES, constant).toLong()}
 * and asserts that VectorMaskToLong is folded away entirely (count = 0).
 *
 * Ideal_MaskAll looks through VectorStoreMask to inspect its input. Without the worklist
 * propagation fix in PhaseIterGVN::add_users_of_use_to_worklist (JDK-8375688), VectorMaskToLong
 * is not re-visited after VectorStoreMask's input becomes a recognized constant,
 * leaving the fold opportunity missed.
 *
 * IR rules cover three hardware paths:
 * - AVX-512/SVE/RVV: masks fold through MaskAll (correctness check only, VectorStoreMask
 *   is not involved on these platforms)
 * - AVX2 without AVX-512: masks go through VectorStoreMask, directly exercising the worklist fix
 * - ASIMD without SVE: same VectorStoreMask path, on AArch64
 *
 * {@code @Check} methods verify correctness on all platforms, including those where no IR rule applies.
 * Float/Double species are excluded because VectorMaskToLong fails to fold their masks
 * due to an intervening VectorMaskCast (JDK-8377588).
 */
public class TestVectorMaskToLongStress {
    static final VectorSpecies<Byte> B_SPECIES = ByteVector.SPECIES_MAX;
    static final VectorSpecies<Short> S_SPECIES = ShortVector.SPECIES_MAX;
    static final VectorSpecies<Integer> I_SPECIES = IntVector.SPECIES_MAX;
    static final VectorSpecies<Long> L_SPECIES = LongVector.SPECIES_MAX;

    // --- All-ones mask: fromLong(-1).toLong() should fold to a constant ---

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_TO_LONG, "= 0" },
        applyIfCPUFeatureOr = { "avx512", "true", "sve", "true", "rvv", "true" })
    @IR(counts = { IRNode.VECTOR_MASK_TO_LONG, "= 0" },
        applyIfCPUFeatureAnd = { "avx2", "true", "avx512", "false" })
    @IR(counts = { IRNode.VECTOR_MASK_TO_LONG, "= 0" },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve", "false" })
    public static long testAllOnesByte() {
        return VectorMask.fromLong(B_SPECIES, -1L).toLong();
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_TO_LONG, "= 0" },
        applyIfCPUFeatureOr = { "avx512", "true", "sve", "true", "rvv", "true" })
    @IR(counts = { IRNode.VECTOR_MASK_TO_LONG, "= 0" },
        applyIfCPUFeatureAnd = { "avx2", "true", "avx512", "false" })
    @IR(counts = { IRNode.VECTOR_MASK_TO_LONG, "= 0" },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve", "false" })
    public static long testAllOnesShort() {
        return VectorMask.fromLong(S_SPECIES, -1L).toLong();
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_TO_LONG, "= 0" },
        applyIfCPUFeatureOr = { "avx512", "true", "sve", "true", "rvv", "true" })
    @IR(counts = { IRNode.VECTOR_MASK_TO_LONG, "= 0" },
        applyIfCPUFeatureAnd = { "avx2", "true", "avx512", "false" })
    @IR(counts = { IRNode.VECTOR_MASK_TO_LONG, "= 0" },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve", "false" })
    public static long testAllOnesInt() {
        return VectorMask.fromLong(I_SPECIES, -1L).toLong();
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_TO_LONG, "= 0" },
        applyIfCPUFeatureOr = { "avx512", "true", "sve", "true", "rvv", "true" })
    @IR(counts = { IRNode.VECTOR_MASK_TO_LONG, "= 0" },
        applyIfCPUFeatureAnd = { "avx2", "true", "avx512", "false" })
    @IR(counts = { IRNode.VECTOR_MASK_TO_LONG, "= 0" },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve", "false" })
    public static long testAllOnesLong() {
        return VectorMask.fromLong(L_SPECIES, -1L).toLong();
    }

    // --- All-zeros mask: fromLong(0).toLong() should fold to a constant ---

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_TO_LONG, "= 0" },
        applyIfCPUFeatureOr = { "avx512", "true", "sve", "true", "rvv", "true" })
    @IR(counts = { IRNode.VECTOR_MASK_TO_LONG, "= 0" },
        applyIfCPUFeatureAnd = { "avx2", "true", "avx512", "false" })
    @IR(counts = { IRNode.VECTOR_MASK_TO_LONG, "= 0" },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve", "false" })
    public static long testAllZerosByte() {
        return VectorMask.fromLong(B_SPECIES, 0L).toLong();
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_TO_LONG, "= 0" },
        applyIfCPUFeatureOr = { "avx512", "true", "sve", "true", "rvv", "true" })
    @IR(counts = { IRNode.VECTOR_MASK_TO_LONG, "= 0" },
        applyIfCPUFeatureAnd = { "avx2", "true", "avx512", "false" })
    @IR(counts = { IRNode.VECTOR_MASK_TO_LONG, "= 0" },
        applyIfCPUFeatureAnd = { "asimd", "true", "sve", "false" })
    public static long testAllZerosInt() {
        return VectorMask.fromLong(I_SPECIES, 0L).toLong();
    }

    // --- Verification ---

    @Check(test = "testAllOnesByte")
    public static void checkAllOnesByte(long result) {
        Asserts.assertEquals(-1L >>> (64 - B_SPECIES.length()), result);
    }

    @Check(test = "testAllOnesShort")
    public static void checkAllOnesShort(long result) {
        Asserts.assertEquals(-1L >>> (64 - S_SPECIES.length()), result);
    }

    @Check(test = "testAllOnesInt")
    public static void checkAllOnesInt(long result) {
        Asserts.assertEquals(-1L >>> (64 - I_SPECIES.length()), result);
    }

    @Check(test = "testAllOnesLong")
    public static void checkAllOnesLong(long result) {
        Asserts.assertEquals(-1L >>> (64 - L_SPECIES.length()), result);
    }

    @Check(test = "testAllZerosByte")
    public static void checkAllZerosByte(long result) {
        Asserts.assertEquals(0L, result);
    }

    @Check(test = "testAllZerosInt")
    public static void checkAllZerosInt(long result) {
        Asserts.assertEquals(0L, result);
    }

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.addFlags("--add-modules=jdk.incubator.vector",
                               "-XX:+IgnoreUnrecognizedVMOptions",
                               "-XX:+StressIncrementalInlining",
                               "-XX:CompileCommand=compileonly,compiler.vectorapi.TestVectorMaskToLongStress::*",
                               "-XX:VerifyIterativeGVN=1110")
                     .start();
    }
}
