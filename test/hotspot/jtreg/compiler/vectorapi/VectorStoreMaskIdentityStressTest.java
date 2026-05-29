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

/*
* @test
* @bug 8380424
* @library /test/lib /
* @summary VectorStoreMaskNode Identity optimization stress tests
* @modules jdk.incubator.vector
*
* @run driver ${test.main.class}
*/

// Tests that VectorStoreMaskNode::Identity is not missed because of
// incremental inlining order and vector boxing/unboxing.

package compiler.vectorapi;

import compiler.lib.ir_framework.*;
import jdk.incubator.vector.*;

public class VectorStoreMaskIdentityStressTest {
    private static final VectorSpecies<Byte> B64 = ByteVector.SPECIES_64;
    private static final VectorSpecies<Short> S64 = ShortVector.SPECIES_64;
    private static final VectorSpecies<Short> S128 = ShortVector.SPECIES_128;
    private static final VectorSpecies<Integer> I64 = IntVector.SPECIES_64;
    private static final VectorSpecies<Integer> I128 = IntVector.SPECIES_128;
    private static final VectorSpecies<Long> L128 = LongVector.SPECIES_128;
    private static final VectorSpecies<Long> L256 = LongVector.SPECIES_256;
    private static final VectorSpecies<Float> F128 = FloatVector.SPECIES_128;
    private static final VectorSpecies<Double> D128 = DoubleVector.SPECIES_128;

    private static final int LENGTH = 256; // large enough
    private static boolean[] mask_in;
    private static boolean[] mask_out;

    static {
        mask_in = new boolean[LENGTH];
        mask_out = new boolean[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            mask_in[i] = (i & 3) == 0;
        }
    }

    @ForceInline
    private static void testThreeCastsKernel(VectorSpecies<?> from_species,
                                             VectorSpecies<?> to_species1,
                                             VectorSpecies<?> to_species2,
                                             VectorSpecies<?> to_species3) {
        VectorMask.fromArray(from_species, mask_in, 0)
                  .cast(to_species1)
                  .cast(to_species2)
                  .cast(to_species3).intoArray(mask_out, 0);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_Z, IRNode.VECTOR_SIZE_8, ">= 1",
                   IRNode.LOAD_VECTOR_Z, IRNode.VECTOR_SIZE_4, ">= 1",
                   IRNode.VECTOR_LOAD_MASK, "= 0",
                   IRNode.VECTOR_STORE_MASK, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 0" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx", "true" },
        applyIf = { "MaxVectorSize", ">= 16" })
    public static void testVectorMaskStoreIdentity1() {
        testThreeCastsKernel(B64, S128, B64, S128);
        testThreeCastsKernel(I128, S64, F128, S64);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_Z, IRNode.VECTOR_SIZE_2, ">= 1",
                   IRNode.VECTOR_LOAD_MASK, "= 0",
                   IRNode.VECTOR_STORE_MASK, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 0" },
        applyIfCPUFeatureOr = { "asimd", "true" },
        applyIf = { "MaxVectorSize", ">= 16" })
    public static void testVectorMaskStoreIdentity2() {
        testThreeCastsKernel(L128, I64, D128, I64);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_Z, IRNode.VECTOR_SIZE_4, ">= 1",
                   IRNode.VECTOR_LOAD_MASK, "= 0",
                   IRNode.VECTOR_STORE_MASK, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 0" },
        applyIfCPUFeatureOr = { "sve", "true", "avx2", "true" },
        applyIf = { "MaxVectorSize", "> 16" })
    public static void testVectorMaskStoreIdentity3() {
        testThreeCastsKernel(L256, I128, F128, S64);
    }

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.addFlags("--add-modules=jdk.incubator.vector",
                               "-ea",
                               "-esa",
                               "-XX:CompileThreshold=100",
                               "-XX:-TieredCompilation",
                               "-XX:VerifyIterativeGVN=1110")
                     .start();
    }
}
