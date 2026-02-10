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
 * @bug 8356760 8367292
 * @library /test/lib /
 * @summary IR test for VectorMask.toLong()
 * @modules jdk.incubator.vector
 *
 * @run driver compiler.vectorapi.VectorMaskToLongTest
 */

package compiler.vectorapi;

import compiler.lib.ir_framework.*;
import java.util.Arrays;
import jdk.incubator.vector.*;
import jdk.test.lib.Asserts;

public class VectorMaskToLongTest {
    static final VectorSpecies<Byte> B_SPECIES = ByteVector.SPECIES_MAX;
    static final VectorSpecies<Short> S_SPECIES = ShortVector.SPECIES_MAX;
    static final VectorSpecies<Integer> I_SPECIES = IntVector.SPECIES_MAX;
    static final VectorSpecies<Float> F_SPECIES = FloatVector.SPECIES_MAX;
    static final VectorSpecies<Long> L_SPECIES = LongVector.SPECIES_MAX;
    static final VectorSpecies<Double> D_SPECIES = DoubleVector.SPECIES_MAX;

    private static boolean[] m;

    static {
        m = new boolean[B_SPECIES.length()];
        Arrays.fill(m, true);
    }

    @DontInline
    public static void verifyMaskToLong(VectorSpecies<?> species, long inputLong, long got) {
        long expected = inputLong & (-1L >>> (64 - species.length()));
        Asserts.assertEquals(expected, got, "for input long " + inputLong);
    }

    // Tests for "VectorMaskToLong(MaskAll(0/-1)) => ((0/-1) & (-1ULL >> (64 - vlen)))"

    @ForceInline
    public static void testMaskAllToLong(VectorSpecies<?> species) {
        int vlen = species.length();
        long inputLong = 0L;
        // fromLong is expected to be converted to maskAll.
        long got = VectorMask.fromLong(species, inputLong).toLong();
        verifyMaskToLong(species, inputLong, got);

        inputLong = vlen >= 64 ? 0 : (0x1L << vlen);
        got = VectorMask.fromLong(species, inputLong).toLong();
        verifyMaskToLong(species, inputLong, got);

        inputLong = -1L;
        got = VectorMask.fromLong(species, inputLong).toLong();
        verifyMaskToLong(species, inputLong, got);

        inputLong = (-1L >>> (64 - vlen));
        got = VectorMask.fromLong(species, inputLong).toLong();
        verifyMaskToLong(species, inputLong, got);
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
    public static void testMaskAllToLongByte() {
        testMaskAllToLong(B_SPECIES);
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
    public static void testMaskAllToLongShort() {
        testMaskAllToLong(S_SPECIES);
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
    public static void testMaskAllToLongInt() {
        testMaskAllToLong(I_SPECIES);
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
    public static void testMaskAllToLongLong() {
        testMaskAllToLong(L_SPECIES);
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
    public static void testMaskAllToLongFloat() {
        testMaskAllToLong(F_SPECIES);
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
    public static void testMaskAllToLongDouble() {
        testMaskAllToLong(D_SPECIES);
    }

    // General cases for (VectorMaskToLong (VectorLongToMask (x))) => x.

    @Test
    @IR(counts = { IRNode.VECTOR_LONG_TO_MASK, "= 0",
                   IRNode.VECTOR_MASK_TO_LONG, "= 0" },
        applyIfCPUFeatureOr = { "svebitperm", "true", "avx2", "true", "rvv", "true" })
    @IR(counts = { IRNode.VECTOR_LONG_TO_MASK, "= 0",
                   IRNode.VECTOR_MASK_TO_LONG, "= 1" },
        applyIfCPUFeatureAnd = { "asimd", "true", "svebitperm", "false" })
    public static void testFromLongToLongByte() {
       // Test the case where some but not all bits are set.
       long inputLong = (-1L >>> (64 - B_SPECIES.length()))-1;
       long got = VectorMask.fromLong(B_SPECIES, inputLong).toLong();
       verifyMaskToLong(B_SPECIES, inputLong, got);
    }

    @Test
    @IR(counts = { IRNode.VECTOR_LONG_TO_MASK, "= 0",
                   IRNode.VECTOR_MASK_TO_LONG, "= 0" },
        applyIfCPUFeatureOr = { "svebitperm", "true", "avx2", "true", "rvv", "true" })
    @IR(counts = { IRNode.VECTOR_LONG_TO_MASK, "= 0",
                   IRNode.VECTOR_MASK_TO_LONG, "= 1" },
        applyIfCPUFeatureAnd = { "asimd", "true", "svebitperm", "false" })
    public static void testFromLongToLongShort() {
        // Test the case where some but not all bits are set.
        long inputLong = (-1L >>> (64 - S_SPECIES.length()))-1;
        long got = VectorMask.fromLong(S_SPECIES, inputLong).toLong();
        verifyMaskToLong(S_SPECIES, inputLong, got);
    }

    @Test
    @IR(counts = { IRNode.VECTOR_LONG_TO_MASK, "= 0",
                   IRNode.VECTOR_MASK_TO_LONG, "= 0" },
        applyIfCPUFeatureOr = { "svebitperm", "true", "avx2", "true", "rvv", "true" })
    @IR(counts = { IRNode.VECTOR_LONG_TO_MASK, "= 0",
                   IRNode.VECTOR_MASK_TO_LONG, "= 1" },
        applyIfCPUFeatureAnd = { "asimd", "true", "svebitperm", "false" })
    public static void testFromLongToLongInt() {
        // Test the case where some but not all bits are set.
        long inputLong = (-1L >>> (64 - I_SPECIES.length()))-1;
        long got = VectorMask.fromLong(I_SPECIES, inputLong).toLong();
        verifyMaskToLong(I_SPECIES, inputLong, got);
    }

    @Test
    @IR(counts = { IRNode.VECTOR_LONG_TO_MASK, "= 0",
                   IRNode.VECTOR_MASK_TO_LONG, "= 0" },
        applyIfCPUFeatureOr = { "svebitperm", "true", "avx2", "true", "rvv", "true" })
    @IR(counts = { IRNode.VECTOR_LONG_TO_MASK, "= 0",
                   IRNode.VECTOR_MASK_TO_LONG, "= 1" },
        applyIfCPUFeatureAnd = { "asimd", "true", "svebitperm", "false" })
    public static void testFromLongToLongLong() {
        // Test the case where some but not all bits are set.
        long inputLong = (-1L >>> (64 - L_SPECIES.length()))-1;
        long got = VectorMask.fromLong(L_SPECIES, inputLong).toLong();
        verifyMaskToLong(L_SPECIES, inputLong, got);
    }

    @Test
    @IR(counts = { IRNode.VECTOR_LONG_TO_MASK, "= 1",
                   IRNode.VECTOR_MASK_TO_LONG, "= 1" },
        applyIfCPUFeatureOr = { "svebitperm", "true", "avx2", "true", "rvv", "true" })
    @IR(counts = { IRNode.VECTOR_LONG_TO_MASK, "= 0",
                   IRNode.VECTOR_MASK_TO_LONG, "= 1" },
        applyIfCPUFeatureAnd = { "asimd", "true", "svebitperm", "false" })
    public static void testFromLongToLongFloat() {
        // Test the case where some but not all bits are set.
        long inputLong = (-1L >>> (64 - F_SPECIES.length()))-1;
        long got = VectorMask.fromLong(F_SPECIES, inputLong).toLong();
        verifyMaskToLong(F_SPECIES, inputLong, got);
    }

    @Test
    @IR(counts = { IRNode.VECTOR_LONG_TO_MASK, "= 1",
                   IRNode.VECTOR_MASK_TO_LONG, "= 1" },
        applyIfCPUFeatureOr = { "svebitperm", "true", "avx2", "true", "rvv", "true" })
    @IR(counts = { IRNode.VECTOR_LONG_TO_MASK, "= 0",
                   IRNode.VECTOR_MASK_TO_LONG, "= 1" },
        applyIfCPUFeatureAnd = { "asimd", "true", "svebitperm", "false" })
    public static void testFromLongToLongDouble() {
        // Test the case where some but not all bits are set.
        long inputLong = (-1L >>> (64 - D_SPECIES.length()))-1;
        long got = VectorMask.fromLong(D_SPECIES, inputLong).toLong();
        verifyMaskToLong(D_SPECIES, inputLong, got);
    }

    // General cases for VectorMask.toLong(). The main purpose is to test the IRs
    // for API VectorMask.toLong(). To avoid the IRs being optimized out by compiler,
    // we insert a VectorMask.not() before toLong().

    @ForceInline
    public static void testToLongGeneral(VectorSpecies<?> species) {
        long got = VectorMask.fromArray(species, m, 0).not().toLong();
        verifyMaskToLong(species, 0, got);
    }

    @Test
    @IR(counts = { IRNode.VECTOR_STORE_MASK, "= 0",
                   IRNode.VECTOR_MASK_TO_LONG, "= 1" },
        applyIfCPUFeatureOr = { "avx512", "true", "rvv", "true" })
    @IR(counts = { IRNode.VECTOR_STORE_MASK, "= 1",
                   IRNode.VECTOR_MASK_TO_LONG, "= 1" },
        applyIfCPUFeatureAnd = { "avx2", "true", "avx512", "false" })
    @IR(counts = { IRNode.VECTOR_STORE_MASK, "= 1",
                   IRNode.VECTOR_MASK_TO_LONG, "= 1" },
        applyIfCPUFeature = { "asimd", "true" })
    public static void testToLongByte() {
        testToLongGeneral(B_SPECIES);
    }

    @Test
    @IR(counts = { IRNode.VECTOR_STORE_MASK, "= 0",
                   IRNode.VECTOR_MASK_TO_LONG, "= 1" },
        applyIfCPUFeatureOr = { "avx512", "true", "rvv", "true" })
    @IR(counts = { IRNode.VECTOR_STORE_MASK, "= 1",
                   IRNode.VECTOR_MASK_TO_LONG, "= 1" },
        applyIfCPUFeatureAnd = { "avx2", "true", "avx512", "false" })
    @IR(counts = { IRNode.VECTOR_STORE_MASK, "= 1",
                   IRNode.VECTOR_MASK_TO_LONG, "= 1" },
        applyIfCPUFeature = { "asimd", "true" })
    public static void testToLongShort() {
        testToLongGeneral(S_SPECIES);
    }

    @Test
    @IR(counts = { IRNode.VECTOR_STORE_MASK, "= 0",
                   IRNode.VECTOR_MASK_TO_LONG, "= 1" },
        applyIfCPUFeatureOr = { "avx512", "true", "rvv", "true" })
    @IR(counts = { IRNode.VECTOR_STORE_MASK, "= 1",
                   IRNode.VECTOR_MASK_TO_LONG, "= 1" },
        applyIfCPUFeatureAnd = { "avx2", "true", "avx512", "false" })
    @IR(counts = { IRNode.VECTOR_STORE_MASK, "= 1",
                   IRNode.VECTOR_MASK_TO_LONG, "= 1" },
        applyIfCPUFeature = { "asimd", "true" })
    public static void testToLongInt() {
        testToLongGeneral(I_SPECIES);
    }

    @Test
    @IR(counts = { IRNode.VECTOR_STORE_MASK, "= 0",
                   IRNode.VECTOR_MASK_TO_LONG, "= 1" },
        applyIfCPUFeatureOr = { "avx512", "true", "rvv", "true" })
    @IR(counts = { IRNode.VECTOR_STORE_MASK, "= 1",
                   IRNode.VECTOR_MASK_TO_LONG, "= 1" },
        applyIfCPUFeatureAnd = { "avx2", "true", "avx512", "false" })
    @IR(counts = { IRNode.VECTOR_STORE_MASK, "= 1",
                   IRNode.VECTOR_MASK_TO_LONG, "= 1" },
        applyIfCPUFeature = { "asimd", "true" })
    public static void testToLongLong() {
        testToLongGeneral(L_SPECIES);
    }

    @Test
    @IR(counts = { IRNode.VECTOR_STORE_MASK, "= 0",
                   IRNode.VECTOR_MASK_TO_LONG, "= 1" },
        applyIfCPUFeatureOr = { "avx512", "true", "rvv", "true" })
    @IR(counts = { IRNode.VECTOR_STORE_MASK, "= 1",
                   IRNode.VECTOR_MASK_TO_LONG, "= 1" },
        applyIfCPUFeature = { "asimd", "true" })
    @IR(counts = { IRNode.VECTOR_STORE_MASK, "= 1",
                   IRNode.VECTOR_MASK_TO_LONG, "= 1" },
        applyIfCPUFeatureAnd = { "avx2", "true", "avx512", "false" })
    public static void testToLongFloat() {
        testToLongGeneral(F_SPECIES);
    }

    @Test
    @IR(counts = { IRNode.VECTOR_STORE_MASK, "= 0",
                   IRNode.VECTOR_MASK_TO_LONG, "= 1" },
        applyIfCPUFeatureOr = { "avx512", "true", "rvv", "true" })
    @IR(counts = { IRNode.VECTOR_STORE_MASK, "= 1",
                   IRNode.VECTOR_MASK_TO_LONG, "= 1" },
        applyIfCPUFeatureAnd = { "avx2", "true", "avx512", "false" })
    @IR(counts = { IRNode.VECTOR_STORE_MASK, "= 1",
                   IRNode.VECTOR_MASK_TO_LONG, "= 1" },
        applyIfCPUFeature = { "asimd", "true" })
    public static void testToLongDouble() {
        testToLongGeneral(D_SPECIES);
    }

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.setDefaultWarmup(10000)
                     .addFlags("--add-modules=jdk.incubator.vector")
                     .start();
    }
}