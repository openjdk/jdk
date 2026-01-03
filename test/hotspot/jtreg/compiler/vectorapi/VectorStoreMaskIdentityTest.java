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
* @bug 8370863
* @library /test/lib /
* @summary VectorStoreMaskNode Identity optimization tests
* @modules jdk.incubator.vector
*
* @run driver compiler.vectorapi.VectorStoreMaskIdentityTest
*/

package compiler.vectorapi;

import compiler.lib.ir_framework.*;
import jdk.incubator.vector.*;
import jdk.test.lib.Asserts;

public class VectorStoreMaskIdentityTest {
    private static final VectorSpecies<Byte> B64 = ByteVector.SPECIES_64;
    private static final VectorSpecies<Short> S64 = ShortVector.SPECIES_64;
    private static final VectorSpecies<Short> S128 = ShortVector.SPECIES_128;
    private static final VectorSpecies<Integer> I64 = IntVector.SPECIES_64;
    private static final VectorSpecies<Integer> I128 = IntVector.SPECIES_128;
    private static final VectorSpecies<Integer> I256 = IntVector.SPECIES_256;
    private static final VectorSpecies<Long> L128 = LongVector.SPECIES_128;
    private static final VectorSpecies<Long> L256 = LongVector.SPECIES_256;
    private static final VectorSpecies<Float> F128 = FloatVector.SPECIES_128;
    private static final VectorSpecies<Float> F256 = FloatVector.SPECIES_256;
    private static final VectorSpecies<Double> D128 = DoubleVector.SPECIES_128;
    private static final VectorSpecies<Double> D256 = DoubleVector.SPECIES_256;
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
    private static void testOneCastKernel(VectorSpecies<?> from_species,
                                          VectorSpecies<?> to_species) {
        VectorMask.fromArray(from_species, mask_in, 0)
                  .cast(to_species).intoArray(mask_out, 0);
    }

    @ForceInline
    private static void testTwoCastsKernel(VectorSpecies<?> from_species,
                                           VectorSpecies<?> to_species1,
                                           VectorSpecies<?> to_species2) {
        VectorMask.fromArray(from_species, mask_in, 0)
                  .cast(to_species1)
                  .cast(to_species2).intoArray(mask_out, 0);
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

    @DontInline
    private static void verifyResult(int vlen) {
        for (int i = 0; i < vlen; i++) {
            Asserts.assertEquals(mask_in[i], mask_out[i], "index " + i);
        }
    }

    @Test
    @IR(counts = { IRNode.VECTOR_LOAD_MASK, "= 0",
                   IRNode.VECTOR_STORE_MASK, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 0" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx", "true" },
        applyIf = { "MaxVectorSize", ">= 16" })
    public static void testVectorMaskStoreIdentityByte() {
        testOneCastKernel(B64, S128);
        verifyResult(B64.length());

        testTwoCastsKernel(B64, S128, B64);
        verifyResult(B64.length());

        testThreeCastsKernel(B64, S128, B64, S128);
        verifyResult(B64.length());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_LOAD_MASK, "= 0",
                   IRNode.VECTOR_STORE_MASK, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 0" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx2", "true" },
        applyIf = { "MaxVectorSize", "> 16" })
    public static void testVectorMaskStoreIdentityByte256() {
        testOneCastKernel(B64, I256);
        verifyResult(B64.length());

        testTwoCastsKernel(B64, S128, I256);
        verifyResult(B64.length());

        testThreeCastsKernel(B64, S128, F256, I256);
        verifyResult(B64.length());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_LOAD_MASK, "= 0",
                   IRNode.VECTOR_STORE_MASK, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 0" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx", "true" },
        applyIf = { "MaxVectorSize", ">= 16" })
    public static void testVectorMaskStoreIdentityShort() {
        testOneCastKernel(S128, B64);
        verifyResult(S128.length());

        testTwoCastsKernel(S64, I128, S64);
        verifyResult(S64.length());

        testThreeCastsKernel(S128, B64, S128, B64);
        verifyResult(S128.length());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_LOAD_MASK, "= 0",
                   IRNode.VECTOR_STORE_MASK, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 0" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx2", "true" },
        applyIf = { "MaxVectorSize", "> 16" })
    public static void testVectorMaskStoreIdentityShort256() {
        testOneCastKernel(S128, I256);
        verifyResult(S128.length());

        testTwoCastsKernel(S64, I128, L256);
        verifyResult(S64.length());

        testThreeCastsKernel(S128, B64, F256, I256);
        verifyResult(S128.length());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_LOAD_MASK, "= 0",
                   IRNode.VECTOR_STORE_MASK, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 0" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx", "true" },
        applyIf = { "MaxVectorSize", ">= 16" })
    public static void testVectorMaskStoreIdentityInt() {
        testOneCastKernel(I128, F128);
        verifyResult(I128.length());

        testTwoCastsKernel(I128, S64, F128);
        verifyResult(I128.length());

        testThreeCastsKernel(I128, S64, F128, S64);
        verifyResult(I128.length());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_LOAD_MASK, "= 0",
                   IRNode.VECTOR_STORE_MASK, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 0" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx2", "true" },
        applyIf = { "MaxVectorSize", "> 16" })
    public static void testVectorMaskStoreIdentityInt256() {
        testOneCastKernel(I128, F128);
        verifyResult(I128.length());

        testTwoCastsKernel(I128, S64, L256);
        verifyResult(I128.length());

        testThreeCastsKernel(I128, S64, F128, L256);
        verifyResult(I128.length());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_LOAD_MASK, "= 0",
                   IRNode.VECTOR_STORE_MASK, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 0" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx", "true" },
        applyIf = { "MaxVectorSize", ">= 16" })
    public static void testVectorMaskStoreIdentityLong() {
        testOneCastKernel(L128, D128);
        verifyResult(L128.length());

        testTwoCastsKernel(L128, I64, D128);
        verifyResult(L128.length());

        testThreeCastsKernel(L128, I64, D128, I64);
        verifyResult(L128.length());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_LOAD_MASK, "= 0",
                   IRNode.VECTOR_STORE_MASK, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 0" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx2", "true" },
        applyIf = { "MaxVectorSize", "> 16" })
    public static void testVectorMaskStoreIdentityLong256() {
        testOneCastKernel(L128, D128);
        verifyResult(L128.length());

        testTwoCastsKernel(L256, I128, S64);
        verifyResult(L256.length());

        testThreeCastsKernel(L256, I128, F128, S64);
        verifyResult(L256.length());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_LOAD_MASK, "= 0",
                   IRNode.VECTOR_STORE_MASK, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 0" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx", "true" },
        applyIf = { "MaxVectorSize", ">= 16" })
    public static void testVectorMaskStoreIdentityFloat() {
        testOneCastKernel(F128, I128);
        verifyResult(F128.length());

        testTwoCastsKernel(F128, S64, I128);
        verifyResult(F128.length());

        testThreeCastsKernel(F128, S64, I128, S64);
        verifyResult(F128.length());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_LOAD_MASK, "= 0",
                   IRNode.VECTOR_STORE_MASK, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 0" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx2", "true" },
        applyIf = { "MaxVectorSize", "> 16" })
    public static void testVectorMaskStoreIdentityFloat256() {
        testOneCastKernel(F128, I128);
        verifyResult(F128.length());

        testTwoCastsKernel(F128, S64, L256);
        verifyResult(F128.length());

        testThreeCastsKernel(F128, S64, I128, L256);
        verifyResult(F128.length());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_LOAD_MASK, "= 0",
                   IRNode.VECTOR_STORE_MASK, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 0" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx", "true" },
        applyIf = { "MaxVectorSize", ">= 16" })
    public static void testVectorMaskStoreIdentityDouble() {
        testOneCastKernel(D128, L128);
        verifyResult(D128.length());

        testTwoCastsKernel(D128, I64, L128);
        verifyResult(D128.length());

        testThreeCastsKernel(D128, I64, L128, I64);
        verifyResult(D128.length());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_LOAD_MASK, "= 0",
                   IRNode.VECTOR_STORE_MASK, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 0" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx2", "true" },
        applyIf = { "MaxVectorSize", "> 16" })
    public static void testVectorMaskStoreIdentityDouble256() {
        testOneCastKernel(D128, L128);
        verifyResult(D128.length());

        testTwoCastsKernel(D256, S64, I128);
        verifyResult(D256.length());

        testThreeCastsKernel(D256, S64, L256, I128);
        verifyResult(D256.length());
    }

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.setDefaultWarmup(10000)
                     .addFlags("--add-modules=jdk.incubator.vector")
                     .start();
    }
}