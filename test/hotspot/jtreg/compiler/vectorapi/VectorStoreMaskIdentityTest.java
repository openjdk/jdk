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
* @bug 8370863
* @library /test/lib /
* @summary VectorStoreMaskNode Identity optimization tests
* @modules jdk.incubator.vector
*
* @run driver ${test.main.class}
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

    @DontInline
    private static void verifyResult(int vlen) {
        for (int i = 0; i < vlen; i++) {
            Asserts.assertEquals(mask_in[i], mask_out[i], "index " + i);
        }
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_Z, IRNode.VECTOR_SIZE_8, ">= 1",
                   IRNode.VECTOR_LOAD_MASK, "= 0",
                   IRNode.VECTOR_STORE_MASK, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 0" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx", "true", "rvv", "true" },
        applyIf = { "MaxVectorSize", ">= 16" })
    public static void testVectorMaskStoreIdentityByte() {
        VectorMask<Byte> mask_byte_64 = VectorMask.fromArray(B64, mask_in, 0);

        mask_byte_64.cast(S128).intoArray(mask_out, 0);
        verifyResult(B64.length());

        mask_byte_64.cast(S128).cast(B64).intoArray(mask_out, 0);
        verifyResult(B64.length());

        mask_byte_64.cast(S128).cast(B64).cast(S128).intoArray(mask_out, 0);
        verifyResult(B64.length());
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_Z, IRNode.VECTOR_SIZE_8, ">= 1",
                   IRNode.VECTOR_LOAD_MASK, "= 0",
                   IRNode.VECTOR_STORE_MASK, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 0" },
        applyIfCPUFeatureOr = { "sve", "true", "avx2", "true", "rvv", "true" },
        applyIf = { "MaxVectorSize", "> 16" })
    public static void testVectorMaskStoreIdentityByte256() {
        VectorMask<Byte> mask_byte_64 = VectorMask.fromArray(B64, mask_in, 0);

        mask_byte_64.cast(I256).intoArray(mask_out, 0);
        verifyResult(B64.length());

        mask_byte_64.cast(S128).cast(F256).intoArray(mask_out, 0);
        verifyResult(B64.length());

        mask_byte_64.cast(F256).cast(S128).cast(I256).intoArray(mask_out, 0);
        verifyResult(B64.length());
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_Z, IRNode.VECTOR_SIZE_8, ">= 1",
                   IRNode.VECTOR_LOAD_MASK, "= 0",
                   IRNode.VECTOR_STORE_MASK, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 0" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx", "true", "rvv", "true" },
        applyIf = { "MaxVectorSize", ">= 16" })
    public static void testVectorMaskStoreIdentityShort() {
        VectorMask<Short> mask_short_128 = VectorMask.fromArray(S128, mask_in, 0);

        mask_short_128.cast(B64).intoArray(mask_out, 0);
        verifyResult(S128.length());

        mask_short_128.cast(B64).cast(S128).intoArray(mask_out, 0);
        verifyResult(S128.length());

        mask_short_128.cast(B64).cast(S128).cast(B64).intoArray(mask_out, 0);
        verifyResult(S128.length());
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_Z, IRNode.VECTOR_SIZE_8, ">= 1",
                   IRNode.VECTOR_LOAD_MASK, "= 0",
                   IRNode.VECTOR_STORE_MASK, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 0" },
        applyIfCPUFeatureOr = { "sve", "true", "avx2", "true", "rvv", "true" },
        applyIf = { "MaxVectorSize", "> 16" })
    public static void testVectorMaskStoreIdentityShort256() {
        VectorMask<Short> mask_short_128 = VectorMask.fromArray(S128, mask_in, 0);

        mask_short_128.cast(I256).intoArray(mask_out, 0);
        verifyResult(S128.length());

        mask_short_128.cast(B64).cast(I256).intoArray(mask_out, 0);
        verifyResult(S128.length());

        mask_short_128.cast(F256).cast(B64).cast(I256).intoArray(mask_out, 0);
        verifyResult(S128.length());
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_Z, IRNode.VECTOR_SIZE_4, ">= 1",
                   IRNode.VECTOR_LOAD_MASK, "= 0",
                   IRNode.VECTOR_STORE_MASK, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 0" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx", "true", "rvv", "true" },
        applyIf = { "MaxVectorSize", ">= 16" })
    public static void testVectorMaskStoreIdentityInt() {
        VectorMask<Integer> mask_int_128 = VectorMask.fromArray(I128, mask_in, 0);

        mask_int_128.cast(F128).intoArray(mask_out, 0);
        verifyResult(I128.length());

        mask_int_128.cast(S64).cast(F128).intoArray(mask_out, 0);
        verifyResult(I128.length());

        mask_int_128.cast(F128).cast(I128).cast(S64).intoArray(mask_out, 0);
        verifyResult(I128.length());
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_Z, IRNode.VECTOR_SIZE_4, ">= 1",
                   IRNode.VECTOR_LOAD_MASK, "= 0",
                   IRNode.VECTOR_STORE_MASK, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 0" },
        applyIfCPUFeatureOr = { "sve", "true", "avx2", "true", "rvv", "true" },
        applyIf = { "MaxVectorSize", "> 16" })
    public static void testVectorMaskStoreIdentityInt256() {
        VectorMask<Integer> mask_int_128 = VectorMask.fromArray(I128, mask_in, 0);

        mask_int_128.cast(F128).intoArray(mask_out, 0);
        verifyResult(I128.length());

        mask_int_128.cast(S64).cast(L256).intoArray(mask_out, 0);
        verifyResult(I128.length());

        mask_int_128.cast(L256).cast(S64).cast(F128).intoArray(mask_out, 0);
        verifyResult(I128.length());
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_Z, IRNode.VECTOR_SIZE_2, ">= 1",
                   IRNode.VECTOR_LOAD_MASK, "= 0",
                   IRNode.VECTOR_STORE_MASK, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 0" },
        applyIfCPUFeatureOr = { "asimd", "true", "rvv", "true" },
        applyIf = { "MaxVectorSize", ">= 16" })
    public static void testVectorMaskStoreIdentityLong() {
        VectorMask<Long> mask_long_128 = VectorMask.fromArray(L128, mask_in, 0);

        mask_long_128.cast(D128).intoArray(mask_out, 0);
        verifyResult(L128.length());

        mask_long_128.cast(I64).cast(D128).intoArray(mask_out, 0);
        verifyResult(L128.length());

        mask_long_128.cast(I64).cast(D128).cast(I64).intoArray(mask_out, 0);
        verifyResult(L128.length());
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_Z, IRNode.VECTOR_SIZE_4, ">= 1",
                   IRNode.VECTOR_LOAD_MASK, "= 0",
                   IRNode.VECTOR_STORE_MASK, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 0" },
        applyIfCPUFeatureOr = { "sve", "true", "avx2", "true", "rvv", "true" },
        applyIf = { "MaxVectorSize", "> 16" })
    public static void testVectorMaskStoreIdentityLong256() {
        VectorMask<Long> mask_long_256 = VectorMask.fromArray(L256, mask_in, 0);

        mask_long_256.cast(I128).intoArray(mask_out, 0);
        verifyResult(L256.length());

        mask_long_256.cast(S64).cast(I128).intoArray(mask_out, 0);
        verifyResult(L256.length());

        mask_long_256.cast(F128).cast(I128).cast(S64).intoArray(mask_out, 0);
        verifyResult(L256.length());
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_Z, IRNode.VECTOR_SIZE_4, ">= 1",
                   IRNode.VECTOR_LOAD_MASK, "= 0",
                   IRNode.VECTOR_STORE_MASK, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 0" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx", "true", "rvv", "true" },
        applyIf = { "MaxVectorSize", ">= 16" })
    public static void testVectorMaskStoreIdentityFloat() {
        VectorMask<Float> mask_float_128 = VectorMask.fromArray(F128, mask_in, 0);

        mask_float_128.cast(I128).intoArray(mask_out, 0);
        verifyResult(F128.length());

        mask_float_128.cast(S64).cast(I128).intoArray(mask_out, 0);
        verifyResult(F128.length());

        mask_float_128.cast(S64).cast(I128).cast(S64).intoArray(mask_out, 0);
        verifyResult(F128.length());
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_Z, IRNode.VECTOR_SIZE_4, ">= 1",
                   IRNode.VECTOR_LOAD_MASK, "= 0",
                   IRNode.VECTOR_STORE_MASK, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 0" },
        applyIfCPUFeatureOr = { "sve", "true", "avx2", "true", "rvv", "true" },
        applyIf = { "MaxVectorSize", "> 16" })
    public static void testVectorMaskStoreIdentityFloat256() {
        VectorMask<Float> mask_float_128 = VectorMask.fromArray(F128, mask_in, 0);

        mask_float_128.cast(I128).intoArray(mask_out, 0);
        verifyResult(F128.length());

        mask_float_128.cast(S64).cast(L256).intoArray(mask_out, 0);
        verifyResult(F128.length());

        mask_float_128.cast(L256).cast(S64).cast(I128).intoArray(mask_out, 0);
        verifyResult(F128.length());
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_Z, IRNode.VECTOR_SIZE_2, ">= 1",
                   IRNode.VECTOR_LOAD_MASK, "= 0",
                   IRNode.VECTOR_STORE_MASK, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 0" },
        applyIfCPUFeatureOr = { "asimd", "true", "rvv", "true" },
        applyIf = { "MaxVectorSize", ">= 16" })
    public static void testVectorMaskStoreIdentityDouble() {
        VectorMask<Double> mask_double_128 = VectorMask.fromArray(D128, mask_in, 0);

        mask_double_128.cast(L128).intoArray(mask_out, 0);
        verifyResult(D128.length());

        mask_double_128.cast(I64).cast(L128).intoArray(mask_out, 0);
        verifyResult(D128.length());

        mask_double_128.cast(I64).cast(L128).cast(I64).intoArray(mask_out, 0);
        verifyResult(D128.length());
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_Z, IRNode.VECTOR_SIZE_4, ">= 1",
                   IRNode.VECTOR_LOAD_MASK, "= 0",
                   IRNode.VECTOR_STORE_MASK, "= 0",
                   IRNode.VECTOR_MASK_CAST, "= 0" },
        applyIfCPUFeatureOr = { "sve", "true", "avx2", "true", "rvv", "true" },
        applyIf = { "MaxVectorSize", "> 16" })
    public static void testVectorMaskStoreIdentityDouble256() {
        VectorMask<Double> mask_double_256 = VectorMask.fromArray(D256, mask_in, 0);

        mask_double_256.cast(F128).intoArray(mask_out, 0);
        verifyResult(D256.length());

        mask_double_256.cast(S64).cast(I128).intoArray(mask_out, 0);
        verifyResult(D256.length());

        mask_double_256.cast(I128).cast(S64).cast(L256).intoArray(mask_out, 0);
        verifyResult(D256.length());
    }

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.setDefaultWarmup(10000)
                     .addFlags("--add-modules=jdk.incubator.vector")
                     .start();
    }
}
