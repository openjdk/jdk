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

package compiler.vectorapi;

import compiler.lib.generators.*;
import compiler.lib.ir_framework.*;

import jdk.incubator.vector.*;
import jdk.test.lib.Asserts;

/**
 * @test
 * @bug 8372980
 * @key randomness
 * @summary Add intrinsic support for unsigned min/max reduction operations
 * @library /test/lib /
 * @modules jdk.incubator.vector
 * @run driver compiler.vectorapi.VectorUMinMaxReductionTest
 */

public class VectorUMinMaxReductionTest {
    static final int LENGTH = 256;
    static final Generators RD = Generators.G;

    static byte[] ba;
    static short[] sa;
    static int[] ia;
    static long[] la;
    static boolean[] ma;

    static {
        ba = new byte[LENGTH];
        sa = new short[LENGTH];
        ia = new int[LENGTH];
        la = new long[LENGTH];
        ma = new boolean[LENGTH];

        Generator<Integer> iGen = RD.ints();
        Generator<Long> lGen = RD.longs();

        for (int i = 0; i < LENGTH; i++) {
            ba[i] = iGen.next().byteValue();
            sa[i] = iGen.next().shortValue();
            ma[i] = iGen.next() % 2 == 0;
        }
        RD.fill(iGen, ia);
        RD.fill(lGen, la);
    }

    // ==================== Byte Tests ====================

    @DontInline
    public static void verifyByteUMin(VectorSpecies<Byte> species, byte result) {
        byte expected = (byte) -1;
        for (int i = 0; i < species.length(); i++) {
            expected = VectorMath.minUnsigned(expected, ba[i]);
        }
        Asserts.assertEquals(expected, result);
    }

    @DontInline
    public static void verifyByteUMax(VectorSpecies<Byte> species, byte result) {
        byte expected = 0;
        for (int i = 0; i < species.length(); i++) {
            expected = VectorMath.maxUnsigned(expected, ba[i]);
        }
        Asserts.assertEquals(expected, result);
    }

    @DontInline
    public static void verifyByteUMinMasked(VectorSpecies<Byte> species, byte result) {
        byte expected = (byte) -1;
        for (int i = 0; i < species.length(); i++) {
            if (ma[i]) {
                expected = VectorMath.minUnsigned(expected, ba[i]);
            }
        }
        Asserts.assertEquals(expected, result);
    }

    @DontInline
    public static void verifyByteUMaxMasked(VectorSpecies<Byte> species, byte result) {
        byte expected = 0;
        for (int i = 0; i < species.length(); i++) {
            if (ma[i]) {
                expected = VectorMath.maxUnsigned(expected, ba[i]);
            }
        }
        Asserts.assertEquals(expected, result);
    }

    @ForceInline
    public static byte byteUMinKernel(VectorSpecies<Byte> species) {
        return ByteVector.fromArray(species, ba, 0).reduceLanes(VectorOperators.UMIN);
    }

    @ForceInline
    public static byte byteUMaxKernel(VectorSpecies<Byte> species) {
        return ByteVector.fromArray(species, ba, 0).reduceLanes(VectorOperators.UMAX);
    }

    @ForceInline
    public static byte byteUMinMaskedKernel(VectorSpecies<Byte> species) {
        return ByteVector.fromArray(species, ba, 0)
                         .reduceLanes(VectorOperators.UMIN, VectorMask.fromArray(species, ma, 0));
    }

    @ForceInline
    public static byte byteUMaxMaskedKernel(VectorSpecies<Byte> species) {
        return ByteVector.fromArray(species, ba, 0)
                         .reduceLanes(VectorOperators.UMAX, VectorMask.fromArray(species, ma, 0));
    }

    @Test
    @IR(counts = {IRNode.UMIN_REDUCTION_V, "= 1"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testByteUMin_Max() {
        verifyByteUMin(ByteVector.SPECIES_MAX, byteUMinKernel(ByteVector.SPECIES_MAX));
    }

    @Test
    @IR(counts = {IRNode.UMAX_REDUCTION_V, "= 1"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testByteUMax_Max() {
        verifyByteUMax(ByteVector.SPECIES_MAX, byteUMaxKernel(ByteVector.SPECIES_MAX));
    }

    @Test
    @IR(counts = {IRNode.UMIN_REDUCTION_V, "= 1"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testByteUMinMasked_Max() {
        verifyByteUMinMasked(ByteVector.SPECIES_MAX, byteUMinMaskedKernel(ByteVector.SPECIES_MAX));
    }

    @Test
    @IR(counts = {IRNode.UMAX_REDUCTION_V, "= 1"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testByteUMaxMasked_Max() {
        verifyByteUMaxMasked(ByteVector.SPECIES_MAX, byteUMaxMaskedKernel(ByteVector.SPECIES_MAX));
    }

    // ==================== Short Tests ====================

    @DontInline
    public static void verifyShortUMin(VectorSpecies<Short> species, short result) {
        short expected = (short) -1;
        for (int i = 0; i < species.length(); i++) {
            expected = VectorMath.minUnsigned(expected, sa[i]);
        }
        Asserts.assertEquals(expected, result);
    }

    @DontInline
    public static void verifyShortUMax(VectorSpecies<Short> species, short result) {
        short expected = 0;
        for (int i = 0; i < species.length(); i++) {
            expected = VectorMath.maxUnsigned(expected, sa[i]);
        }
        Asserts.assertEquals(expected, result);
    }

    @DontInline
    public static void verifyShortUMinMasked(VectorSpecies<Short> species, short result) {
        short expected = (short) -1;
        for (int i = 0; i < species.length(); i++) {
            if (ma[i]) {
                expected = VectorMath.minUnsigned(expected, sa[i]);
            }
        }
        Asserts.assertEquals(expected, result);
    }

    @DontInline
    public static void verifyShortUMaxMasked(VectorSpecies<Short> species, short result) {
        short expected = 0;
        for (int i = 0; i < species.length(); i++) {
            if (ma[i]) {
                expected = VectorMath.maxUnsigned(expected, sa[i]);
            }
        }
        Asserts.assertEquals(expected, result);
    }

    @ForceInline
    public static short shortUMinKernel(VectorSpecies<Short> species) {
        return ShortVector.fromArray(species, sa, 0).reduceLanes(VectorOperators.UMIN);
    }

    @ForceInline
    public static short shortUMaxKernel(VectorSpecies<Short> species) {
        return ShortVector.fromArray(species, sa, 0).reduceLanes(VectorOperators.UMAX);
    }

    @ForceInline
    public static short shortUMinMaskedKernel(VectorSpecies<Short> species) {
        return ShortVector.fromArray(species, sa, 0)
                          .reduceLanes(VectorOperators.UMIN, VectorMask.fromArray(species, ma, 0));
    }

    @ForceInline
    public static short shortUMaxMaskedKernel(VectorSpecies<Short> species) {
        return ShortVector.fromArray(species, sa, 0)
                          .reduceLanes(VectorOperators.UMAX, VectorMask.fromArray(species, ma, 0));
    }

    @Test
    @IR(counts = {IRNode.UMIN_REDUCTION_V, "= 1"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testShortUMin_Max() {
        verifyShortUMin(ShortVector.SPECIES_MAX, shortUMinKernel(ShortVector.SPECIES_MAX));
    }

    @Test
    @IR(counts = {IRNode.UMAX_REDUCTION_V, "= 1"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testShortUMax_Max() {
        verifyShortUMax(ShortVector.SPECIES_MAX, shortUMaxKernel(ShortVector.SPECIES_MAX));
    }

    @Test
    @IR(counts = {IRNode.UMIN_REDUCTION_V, "= 1"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testShortUMinMasked_Max() {
        verifyShortUMinMasked(ShortVector.SPECIES_MAX, shortUMinMaskedKernel(ShortVector.SPECIES_MAX));
    }

    @Test
    @IR(counts = {IRNode.UMAX_REDUCTION_V, "= 1"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testShortUMaxMasked_Max() {
        verifyShortUMaxMasked(ShortVector.SPECIES_MAX, shortUMaxMaskedKernel(ShortVector.SPECIES_MAX));
    }

    // ==================== Int Tests ====================

    @DontInline
    public static void verifyIntUMin(VectorSpecies<Integer> species, int result) {
        int expected = -1;
        for (int i = 0; i < species.length(); i++) {
            expected = VectorMath.minUnsigned(expected, ia[i]);
        }
        Asserts.assertEquals(expected, result);
    }

    @DontInline
    public static void verifyIntUMax(VectorSpecies<Integer> species, int result) {
        int expected = 0;
        for (int i = 0; i < species.length(); i++) {
            expected = VectorMath.maxUnsigned(expected, ia[i]);
        }
        Asserts.assertEquals(expected, result);
    }

    @DontInline
    public static void verifyIntUMinMasked(VectorSpecies<Integer> species, int result) {
        int expected = -1;
        for (int i = 0; i < species.length(); i++) {
            if (ma[i]) {
                expected = VectorMath.minUnsigned(expected, ia[i]);
            }
        }
        Asserts.assertEquals(expected, result);
    }

    @DontInline
    public static void verifyIntUMaxMasked(VectorSpecies<Integer> species, int result) {
        int expected = 0;
        for (int i = 0; i < species.length(); i++) {
            if (ma[i]) {
                expected = VectorMath.maxUnsigned(expected, ia[i]);
            }
        }
        Asserts.assertEquals(expected, result);
    }

    @ForceInline
    public static int intUMinKernel(VectorSpecies<Integer> species) {
        return IntVector.fromArray(species, ia, 0).reduceLanes(VectorOperators.UMIN);
    }

    @ForceInline
    public static int intUMaxKernel(VectorSpecies<Integer> species) {
        return IntVector.fromArray(species, ia, 0).reduceLanes(VectorOperators.UMAX);
    }

    @ForceInline
    public static int intUMinMaskedKernel(VectorSpecies<Integer> species) {
        return IntVector.fromArray(species, ia, 0)
                        .reduceLanes(VectorOperators.UMIN, VectorMask.fromArray(species, ma, 0));
    }

    @ForceInline
    public static int intUMaxMaskedKernel(VectorSpecies<Integer> species) {
        return IntVector.fromArray(species, ia, 0)
                        .reduceLanes(VectorOperators.UMAX, VectorMask.fromArray(species, ma, 0));
    }

    @Test
    @IR(counts = {IRNode.UMIN_REDUCTION_V, "= 1"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testIntUMin_Max() {
        verifyIntUMin(IntVector.SPECIES_MAX, intUMinKernel(IntVector.SPECIES_MAX));
    }

    @Test
    @IR(counts = {IRNode.UMAX_REDUCTION_V, "= 1"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testIntUMax_Max() {
        verifyIntUMax(IntVector.SPECIES_MAX, intUMaxKernel(IntVector.SPECIES_MAX));
    }

    @Test
    @IR(counts = {IRNode.UMIN_REDUCTION_V, "= 1"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testIntUMinMasked_Max() {
        verifyIntUMinMasked(IntVector.SPECIES_MAX, intUMinMaskedKernel(IntVector.SPECIES_MAX));
    }

    @Test
    @IR(counts = {IRNode.UMAX_REDUCTION_V, "= 1"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testIntUMaxMasked_Max() {
        verifyIntUMaxMasked(IntVector.SPECIES_MAX, intUMaxMaskedKernel(IntVector.SPECIES_MAX));
    }

    // ==================== Long Tests ====================

    @DontInline
    public static void verifyLongUMin(VectorSpecies<Long> species, long result) {
        long expected = -1L;
        for (int i = 0; i < species.length(); i++) {
            expected = VectorMath.minUnsigned(expected, la[i]);
        }
        Asserts.assertEquals(expected, result);
    }

    @DontInline
    public static void verifyLongUMax(VectorSpecies<Long> species, long result) {
        long expected = 0L;
        for (int i = 0; i < species.length(); i++) {
            expected = VectorMath.maxUnsigned(expected, la[i]);
        }
        Asserts.assertEquals(expected, result);
    }

    @DontInline
    public static void verifyLongUMinMasked(VectorSpecies<Long> species, long result) {
        long expected = -1L;
        for (int i = 0; i < species.length(); i++) {
            if (ma[i]) {
                expected = VectorMath.minUnsigned(expected, la[i]);
            }
        }
        Asserts.assertEquals(expected, result);
    }

    @DontInline
    public static void verifyLongUMaxMasked(VectorSpecies<Long> species, long result) {
        long expected = 0L;
        for (int i = 0; i < species.length(); i++) {
            if (ma[i]) {
                expected = VectorMath.maxUnsigned(expected, la[i]);
            }
        }
        Asserts.assertEquals(expected, result);
    }

    @ForceInline
    public static long longUMinKernel(VectorSpecies<Long> species) {
        return LongVector.fromArray(species, la, 0).reduceLanes(VectorOperators.UMIN);
    }

    @ForceInline
    public static long longUMaxKernel(VectorSpecies<Long> species) {
        return LongVector.fromArray(species, la, 0).reduceLanes(VectorOperators.UMAX);
    }

    @ForceInline
    public static long longUMinMaskedKernel(VectorSpecies<Long> species) {
        return LongVector.fromArray(species, la, 0)
                         .reduceLanes(VectorOperators.UMIN, VectorMask.fromArray(species, ma, 0));
    }

    @ForceInline
    public static long longUMaxMaskedKernel(VectorSpecies<Long> species) {
        return LongVector.fromArray(species, la, 0)
                         .reduceLanes(VectorOperators.UMAX, VectorMask.fromArray(species, ma, 0));
    }

    @Test
    @IR(counts = {IRNode.UMIN_REDUCTION_V, "= 1"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testLongUMin_Max() {
        verifyLongUMin(LongVector.SPECIES_MAX, longUMinKernel(LongVector.SPECIES_MAX));
    }

    @Test
    @IR(counts = {IRNode.UMAX_REDUCTION_V, "= 1"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testLongUMax_Max() {
        verifyLongUMax(LongVector.SPECIES_MAX, longUMaxKernel(LongVector.SPECIES_MAX));
    }

    @Test
    @IR(counts = {IRNode.UMIN_REDUCTION_V, "= 1"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testLongUMinMasked_Max() {
        verifyLongUMinMasked(LongVector.SPECIES_MAX, longUMinMaskedKernel(LongVector.SPECIES_MAX));
    }

    @Test
    @IR(counts = {IRNode.UMAX_REDUCTION_V, "= 1"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testLongUMaxMasked_Max() {
        verifyLongUMaxMasked(LongVector.SPECIES_MAX, longUMaxMaskedKernel(LongVector.SPECIES_MAX));
    }

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.setDefaultWarmup(10000)
                     .addFlags("--add-modules=jdk.incubator.vector")
                     .start();
    }
}