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

package compiler.vectorapi;

import compiler.lib.generators.*;
import compiler.lib.ir_framework.*;

import jdk.incubator.vector.*;
import jdk.test.lib.Asserts;

import java.util.function.BinaryOperator;

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
    private static final VectorSpecies<Byte> B_SPECIES = ByteVector.SPECIES_MAX;
    private static final VectorSpecies<Short> S_SPECIES = ShortVector.SPECIES_MAX;
    private static final VectorSpecies<Integer> I_SPECIES = IntVector.SPECIES_MAX;
    private static final VectorSpecies<Float> F_SPECIES = FloatVector.SPECIES_MAX;
    private static final VectorSpecies<Long> L_SPECIES = LongVector.SPECIES_MAX;
    private static final VectorSpecies<Double> D_SPECIES = DoubleVector.SPECIES_MAX;
    private static final int LENGTH = 256;
    private static final Generators RD = Generators.G;

    // Identity values for unsigned min/max operations
    private static final byte BYTE_UMIN_IDENTITY = (byte) -1;
    private static final byte BYTE_UMAX_IDENTITY = (byte) 0;
    private static final short SHORT_UMIN_IDENTITY = (short) -1;
    private static final short SHORT_UMAX_IDENTITY = (short) 0;
    private static final int INT_UMIN_IDENTITY = -1;
    private static final int INT_UMAX_IDENTITY = 0;
    private static final long LONG_UMIN_IDENTITY = -1L;
    private static final long LONG_UMAX_IDENTITY = 0L;

    private static byte[] ba;
    private static short[] sa;
    private static int[] ia;
    private static long[] la;
    private static boolean[] ma;

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

    // ==================== Helper Functions ====================

    @DontInline
    private static void verifyByte(VectorSpecies<Byte> species, byte got, byte init,
                                   BinaryOperator<Byte> op, boolean isMasked) {
        byte expected = init;
        for (int i = 0; i < species.length(); i++) {
            if (!isMasked || ma[i]) {
                expected = op.apply(expected, ba[i]);
            }
        }
        Asserts.assertEquals(expected, got);
    }

    @DontInline
    private static void verifyShort(VectorSpecies<Short> species, short got, short init,
                                    BinaryOperator<Short> op, boolean isMasked) {
        short expected = init;
        for (int i = 0; i < species.length(); i++) {
            if (!isMasked || ma[i]) {
                expected = op.apply(expected, sa[i]);
            }
        }
        Asserts.assertEquals(expected, got);
    }

    @DontInline
    private static void verifyInt(VectorSpecies<Integer> species, int got, int init,
                                  BinaryOperator<Integer> op, boolean isMasked) {
        int expected = init;
        for (int i = 0; i < species.length(); i++) {
            if (!isMasked || ma[i]) {
                expected = op.apply(expected, ia[i]);
            }
        }
        Asserts.assertEquals(expected, got);
    }

    @DontInline
    private static void verifyLong(VectorSpecies<Long> species, long got, long init,
                                   BinaryOperator<Long> op, boolean isMasked) {
        long expected = init;
        for (int i = 0; i < species.length(); i++) {
            if (!isMasked || ma[i]) {
                expected = op.apply(expected, la[i]);
            }
        }
        Asserts.assertEquals(expected, got);
    }

    // ==================== Byte Tests ====================

    @Test
    @IR(counts = {IRNode.UMIN_REDUCTION_V, "= 1"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testByteUMin() {
        byte got = ByteVector.fromArray(B_SPECIES, ba, 0).reduceLanes(VectorOperators.UMIN);
        verifyByte(B_SPECIES, got, BYTE_UMIN_IDENTITY, VectorMath::minUnsigned, false);
    }

    @Test
    @IR(counts = {IRNode.UMAX_REDUCTION_V, "= 1"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testByteUMax() {
        byte got = ByteVector.fromArray(B_SPECIES, ba, 0).reduceLanes(VectorOperators.UMAX);
        verifyByte(B_SPECIES, got, BYTE_UMAX_IDENTITY, VectorMath::maxUnsigned, false);
    }

    @Test
    @IR(counts = {IRNode.UMIN_REDUCTION_V, "= 1"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testByteUMinMasked() {
        byte got = ByteVector.fromArray(B_SPECIES, ba, 0)
                             .reduceLanes(VectorOperators.UMIN,
                                          VectorMask.fromArray(B_SPECIES, ma, 0));
        verifyByte(B_SPECIES, got, BYTE_UMIN_IDENTITY, VectorMath::minUnsigned, true);
    }

    @Test
    @IR(counts = {IRNode.UMAX_REDUCTION_V, "= 1"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testByteUMaxMasked() {
        byte got = ByteVector.fromArray(B_SPECIES, ba, 0)
                             .reduceLanes(VectorOperators.UMAX,
                                          VectorMask.fromArray(B_SPECIES, ma, 0));
        verifyByte(B_SPECIES, got, BYTE_UMAX_IDENTITY, VectorMath::maxUnsigned, true);
    }

    // ==================== Short Tests ====================

    @Test
    @IR(counts = {IRNode.UMIN_REDUCTION_V, "= 1"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testShortUMin() {
        short got = ShortVector.fromArray(S_SPECIES, sa, 0).reduceLanes(VectorOperators.UMIN);
        verifyShort(S_SPECIES, got, SHORT_UMIN_IDENTITY, VectorMath::minUnsigned, false);
    }

    @Test
    @IR(counts = {IRNode.UMAX_REDUCTION_V, "= 1"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testShortUMax() {
        short got = ShortVector.fromArray(S_SPECIES, sa, 0).reduceLanes(VectorOperators.UMAX);
        verifyShort(S_SPECIES, got, SHORT_UMAX_IDENTITY, VectorMath::maxUnsigned, false);
    }

    @Test
    @IR(counts = {IRNode.UMIN_REDUCTION_V, "= 1"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testShortUMinMasked() {
        short got = ShortVector.fromArray(S_SPECIES, sa, 0)
                               .reduceLanes(VectorOperators.UMIN,
                                            VectorMask.fromArray(S_SPECIES, ma, 0));
        verifyShort(S_SPECIES, got, SHORT_UMIN_IDENTITY, VectorMath::minUnsigned, true);
    }

    @Test
    @IR(counts = {IRNode.UMAX_REDUCTION_V, "= 1"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testShortUMaxMasked() {
        short got = ShortVector.fromArray(S_SPECIES, sa, 0)
                               .reduceLanes(VectorOperators.UMAX,
                                            VectorMask.fromArray(S_SPECIES, ma, 0));
        verifyShort(S_SPECIES, got, SHORT_UMAX_IDENTITY, VectorMath::maxUnsigned, true);
    }

    // ==================== Int Tests ====================

    @Test
    @IR(counts = {IRNode.UMIN_REDUCTION_V, "= 1"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testIntUMin() {
        int got = IntVector.fromArray(I_SPECIES, ia, 0).reduceLanes(VectorOperators.UMIN);
        verifyInt(I_SPECIES, got, INT_UMIN_IDENTITY, VectorMath::minUnsigned, false);
    }

    @Test
    @IR(counts = {IRNode.UMAX_REDUCTION_V, "= 1"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testIntUMax() {
        int got = IntVector.fromArray(I_SPECIES, ia, 0).reduceLanes(VectorOperators.UMAX);
        verifyInt(I_SPECIES, got, INT_UMAX_IDENTITY, VectorMath::maxUnsigned, false);
    }

    @Test
    @IR(counts = {IRNode.UMIN_REDUCTION_V, "= 1"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testIntUMinMasked() {
        int got = IntVector.fromArray(I_SPECIES, ia, 0)
                           .reduceLanes(VectorOperators.UMIN,
                                        VectorMask.fromArray(I_SPECIES, ma, 0));
        verifyInt(I_SPECIES, got, INT_UMIN_IDENTITY, VectorMath::minUnsigned, true);
    }

    @Test
    @IR(counts = {IRNode.UMAX_REDUCTION_V, "= 1"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testIntUMaxMasked() {
        int got = IntVector.fromArray(I_SPECIES, ia, 0)
                           .reduceLanes(VectorOperators.UMAX,
                                        VectorMask.fromArray(I_SPECIES, ma, 0));
        verifyInt(I_SPECIES, got, INT_UMAX_IDENTITY, VectorMath::maxUnsigned, true);
    }

    // ==================== Long Tests ====================

    @Test
    @IR(counts = {IRNode.UMIN_REDUCTION_V, "= 1"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testLongUMin() {
        long got = LongVector.fromArray(L_SPECIES, la, 0).reduceLanes(VectorOperators.UMIN);
        verifyLong(L_SPECIES, got, LONG_UMIN_IDENTITY, VectorMath::minUnsigned, false);
    }

    @Test
    @IR(counts = {IRNode.UMAX_REDUCTION_V, "= 1"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testLongUMax() {
        long got = LongVector.fromArray(L_SPECIES, la, 0).reduceLanes(VectorOperators.UMAX);
        verifyLong(L_SPECIES, got, LONG_UMAX_IDENTITY, VectorMath::maxUnsigned, false);
    }

    @Test
    @IR(counts = {IRNode.UMIN_REDUCTION_V, "= 1"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testLongUMinMasked() {
        long got = LongVector.fromArray(L_SPECIES, la, 0)
                             .reduceLanes(VectorOperators.UMIN,
                                          VectorMask.fromArray(L_SPECIES, ma, 0));
        verifyLong(L_SPECIES, got, LONG_UMIN_IDENTITY, VectorMath::minUnsigned, true);
    }

    @Test
    @IR(counts = {IRNode.UMAX_REDUCTION_V, "= 1"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testLongUMaxMasked() {
        long got = LongVector.fromArray(L_SPECIES, la, 0)
                             .reduceLanes(VectorOperators.UMAX,
                                          VectorMask.fromArray(L_SPECIES, ma, 0));
        verifyLong(L_SPECIES, got, LONG_UMAX_IDENTITY, VectorMath::maxUnsigned, true);
    }

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.setDefaultWarmup(10000)
                     .addFlags("--add-modules=jdk.incubator.vector")
                     .start();
    }
}
