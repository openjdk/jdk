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
        testOneCastKernel(ByteVector.SPECIES_64, ShortVector.SPECIES_128);
        verifyResult(ByteVector.SPECIES_64.length());

        testTwoCastsKernel(ByteVector.SPECIES_64, ShortVector.SPECIES_128, ByteVector.SPECIES_64);
        verifyResult(ByteVector.SPECIES_64.length());

        testThreeCastsKernel(ByteVector.SPECIES_64, ShortVector.SPECIES_128, ByteVector.SPECIES_64, ShortVector.SPECIES_128);
        verifyResult(ByteVector.SPECIES_64.length());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_LOAD_MASK, "= 0",
                   IRNode.VECTOR_STORE_MASK, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 0" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx2", "true" },
        applyIf = { "MaxVectorSize", "> 16" })
    public static void testVectorMaskStoreIdentityByte256() {
        testOneCastKernel(ByteVector.SPECIES_64, IntVector.SPECIES_256);
        verifyResult(ByteVector.SPECIES_64.length());

        testTwoCastsKernel(ByteVector.SPECIES_64, ShortVector.SPECIES_128, IntVector.SPECIES_256);
        verifyResult(ByteVector.SPECIES_64.length());

        testThreeCastsKernel(ByteVector.SPECIES_64, ShortVector.SPECIES_128, FloatVector.SPECIES_256, IntVector.SPECIES_256);
        verifyResult(ByteVector.SPECIES_64.length());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_LOAD_MASK, "= 0",
                   IRNode.VECTOR_STORE_MASK, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 0" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx", "true" },
        applyIf = { "MaxVectorSize", ">= 16" })
    public static void testVectorMaskStoreIdentityShort() {
        testOneCastKernel(ShortVector.SPECIES_128, ByteVector.SPECIES_64);
        verifyResult(ShortVector.SPECIES_128.length());

        testTwoCastsKernel(ShortVector.SPECIES_64, IntVector.SPECIES_128, ShortVector.SPECIES_64);
        verifyResult(ShortVector.SPECIES_64.length());

        testThreeCastsKernel(ShortVector.SPECIES_128, ByteVector.SPECIES_64, ShortVector.SPECIES_128, ByteVector.SPECIES_64);
        verifyResult(ShortVector.SPECIES_128.length());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_LOAD_MASK, "= 0",
                   IRNode.VECTOR_STORE_MASK, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 0" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx2", "true" },
        applyIf = { "MaxVectorSize", "> 16" })
    public static void testVectorMaskStoreIdentityShort256() {
        testOneCastKernel(ShortVector.SPECIES_128, IntVector.SPECIES_256);
        verifyResult(ShortVector.SPECIES_128.length());

        testTwoCastsKernel(ShortVector.SPECIES_64, IntVector.SPECIES_128, LongVector.SPECIES_256);
        verifyResult(ShortVector.SPECIES_64.length());

        testThreeCastsKernel(ShortVector.SPECIES_128, ByteVector.SPECIES_64, FloatVector.SPECIES_256, IntVector.SPECIES_256);
        verifyResult(ShortVector.SPECIES_128.length());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_LOAD_MASK, "= 0",
                   IRNode.VECTOR_STORE_MASK, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 0" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx", "true" },
        applyIf = { "MaxVectorSize", ">= 16" })
    public static void testVectorMaskStoreIdentityInt() {
        testOneCastKernel(IntVector.SPECIES_MAX, FloatVector.SPECIES_MAX);
        verifyResult(IntVector.SPECIES_MAX.length());

        testTwoCastsKernel(IntVector.SPECIES_128, ShortVector.SPECIES_64, FloatVector.SPECIES_128);
        verifyResult(IntVector.SPECIES_128.length());

        testThreeCastsKernel(IntVector.SPECIES_128, ShortVector.SPECIES_64, FloatVector.SPECIES_128, ShortVector.SPECIES_64);
        verifyResult(IntVector.SPECIES_128.length());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_LOAD_MASK, "= 0",
                   IRNode.VECTOR_STORE_MASK, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 0" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx2", "true" },
        applyIf = { "MaxVectorSize", "> 16" })
    public static void testVectorMaskStoreIdentityInt256() {
        testOneCastKernel(IntVector.SPECIES_MAX, FloatVector.SPECIES_MAX);
        verifyResult(IntVector.SPECIES_MAX.length());

        testTwoCastsKernel(IntVector.SPECIES_128, ShortVector.SPECIES_64, LongVector.SPECIES_256);
        verifyResult(IntVector.SPECIES_128.length());

        testThreeCastsKernel(IntVector.SPECIES_128, ShortVector.SPECIES_64, FloatVector.SPECIES_128, LongVector.SPECIES_256);
        verifyResult(IntVector.SPECIES_128.length());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_LOAD_MASK, "= 0",
                   IRNode.VECTOR_STORE_MASK, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 0" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx", "true" },
        applyIf = { "MaxVectorSize", ">= 16" })
    public static void testVectorMaskStoreIdentityLong() {
        testOneCastKernel(LongVector.SPECIES_MAX, DoubleVector.SPECIES_MAX);
        verifyResult(LongVector.SPECIES_MAX.length());

        testTwoCastsKernel(LongVector.SPECIES_128, IntVector.SPECIES_64, DoubleVector.SPECIES_128);
        verifyResult(LongVector.SPECIES_128.length());

        testThreeCastsKernel(LongVector.SPECIES_128, IntVector.SPECIES_64, DoubleVector.SPECIES_128, IntVector.SPECIES_64);
        verifyResult(LongVector.SPECIES_128.length());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_LOAD_MASK, "= 0",
                   IRNode.VECTOR_STORE_MASK, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 0" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx2", "true" },
        applyIf = { "MaxVectorSize", "> 16" })
    public static void testVectorMaskStoreIdentityLong256() {
        testOneCastKernel(LongVector.SPECIES_MAX, DoubleVector.SPECIES_MAX);
        verifyResult(LongVector.SPECIES_MAX.length());

        testTwoCastsKernel(LongVector.SPECIES_256, IntVector.SPECIES_128, ShortVector.SPECIES_64);
        verifyResult(LongVector.SPECIES_256.length());

        testThreeCastsKernel(LongVector.SPECIES_256, IntVector.SPECIES_128, FloatVector.SPECIES_128, ShortVector.SPECIES_64);
        verifyResult(LongVector.SPECIES_256.length());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_LOAD_MASK, "= 0",
                   IRNode.VECTOR_STORE_MASK, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 0" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx", "true" },
        applyIf = { "MaxVectorSize", ">= 16" })
    public static void testVectorMaskStoreIdentityFloat() {
        testOneCastKernel(FloatVector.SPECIES_MAX, IntVector.SPECIES_MAX);
        verifyResult(FloatVector.SPECIES_MAX.length());

        testTwoCastsKernel(FloatVector.SPECIES_128, ShortVector.SPECIES_64, IntVector.SPECIES_128);
        verifyResult(FloatVector.SPECIES_128.length());

        testThreeCastsKernel(FloatVector.SPECIES_128, ShortVector.SPECIES_64, IntVector.SPECIES_128, ShortVector.SPECIES_64);
        verifyResult(FloatVector.SPECIES_128.length());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_LOAD_MASK, "= 0",
                   IRNode.VECTOR_STORE_MASK, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 0" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx2", "true" },
        applyIf = { "MaxVectorSize", "> 16" })
    public static void testVectorMaskStoreIdentityFloat256() {
        testOneCastKernel(FloatVector.SPECIES_MAX, IntVector.SPECIES_MAX);
        verifyResult(FloatVector.SPECIES_MAX.length());

        testTwoCastsKernel(FloatVector.SPECIES_128, ShortVector.SPECIES_64, LongVector.SPECIES_256);
        verifyResult(FloatVector.SPECIES_128.length());

        testThreeCastsKernel(FloatVector.SPECIES_128, ShortVector.SPECIES_64, IntVector.SPECIES_128, LongVector.SPECIES_256);
        verifyResult(FloatVector.SPECIES_128.length());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_LOAD_MASK, "= 0",
                   IRNode.VECTOR_STORE_MASK, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 0" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx", "true" },
        applyIf = { "MaxVectorSize", ">= 16" })
    public static void testVectorMaskStoreIdentityDouble() {
        testOneCastKernel(DoubleVector.SPECIES_MAX, LongVector.SPECIES_MAX);
        verifyResult(DoubleVector.SPECIES_MAX.length());

        testTwoCastsKernel(DoubleVector.SPECIES_128, IntVector.SPECIES_64, LongVector.SPECIES_128);
        verifyResult(DoubleVector.SPECIES_128.length());

        testThreeCastsKernel(DoubleVector.SPECIES_128, IntVector.SPECIES_64, LongVector.SPECIES_128, IntVector.SPECIES_64);
        verifyResult(DoubleVector.SPECIES_128.length());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_LOAD_MASK, "= 0",
                   IRNode.VECTOR_STORE_MASK, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 0" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx2", "true" },
        applyIf = { "MaxVectorSize", "> 16" })
    public static void testVectorMaskStoreIdentityDouble256() {
        testOneCastKernel(DoubleVector.SPECIES_MAX, LongVector.SPECIES_MAX);
        verifyResult(DoubleVector.SPECIES_MAX.length());

        testTwoCastsKernel(DoubleVector.SPECIES_256, ShortVector.SPECIES_64, IntVector.SPECIES_128);
        verifyResult(DoubleVector.SPECIES_256.length());

        testThreeCastsKernel(DoubleVector.SPECIES_256, ShortVector.SPECIES_64, LongVector.SPECIES_256, IntVector.SPECIES_128);
        verifyResult(DoubleVector.SPECIES_256.length());
    }

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.setDefaultWarmup(10000)
                     .addFlags("--add-modules=jdk.incubator.vector")
                     .start();
    }
}