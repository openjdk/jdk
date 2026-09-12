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

package compiler.vectorapi;

import compiler.lib.ir_framework.*;

import java.util.Arrays;
import java.util.Random;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.LongVector;
import jdk.incubator.vector.ShortVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorSpecies;

import compiler.lib.verify.Verify;

import jdk.test.lib.Utils;

/**
 * @test
 * @bug 8390750
 * @key randomness
 * @library /test/lib /
 * @summary [VectorAPI] Optimize VectorMask.andNot operation for AVX512 targets
 * @requires vm.compiler2.enabled
 * @modules jdk.incubator.vector
 *
 * @run driver compiler.vectorapi.VectorOpmaskInstructionANDNOTTest
 */
public class VectorOpmaskInstructionANDNOTTest {
    private static final VectorSpecies<Byte> B_SPECIES = ByteVector.SPECIES_MAX;
    private static final VectorSpecies<Short> S_SPECIES = ShortVector.SPECIES_MAX;
    private static final VectorSpecies<Integer> I_SPECIES = IntVector.SPECIES_MAX;
    private static final VectorSpecies<Long> L_SPECIES = LongVector.SPECIES_MAX;

    private static final int LENGTH = 128;
    private static final Random RD = Utils.getRandomInstance();

    private static boolean[] ma;
    private static boolean[] mb;
    private static boolean[] mr;

    static {
        ma = new boolean[LENGTH];
        mb = new boolean[LENGTH];
        mr = new boolean[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            ma[i] = RD.nextBoolean();
            mb[i] = RD.nextBoolean();
        }
    }

    private static boolean bandNot(boolean a, boolean b) {
        return a & !b;
    }

    @Test
    @IR(counts = {IRNode.X86_MASK_AND_NOT, "= 1"},
        phase = CompilePhase.FINAL_CODE,
        applyIfCPUFeature = {"avx512", "true"})
    public static void testKandnAndNotByte() {
        VectorMask<Byte> m1 = VectorMask.fromArray(B_SPECIES, ma, 0);
        VectorMask<Byte> m2 = VectorMask.fromArray(B_SPECIES, mb, 0);
        m1.andNot(m2).intoArray(mr, 0);
        verifyBinary(B_SPECIES, ma, mb, mr, VectorOpmaskInstructionANDNOTTest::bandNot);
    }

    @Test
    @IR(counts = {IRNode.X86_MASK_AND_NOT, "= 1"},
        phase = CompilePhase.FINAL_CODE,
        applyIfCPUFeature = {"avx512", "true"})
    public static void testKandnAndNotShort() {
        VectorMask<Short> m1 = VectorMask.fromArray(S_SPECIES, ma, 0);
        VectorMask<Short> m2 = VectorMask.fromArray(S_SPECIES, mb, 0);
        m1.andNot(m2).intoArray(mr, 0);
        verifyBinary(S_SPECIES, ma, mb, mr, VectorOpmaskInstructionANDNOTTest::bandNot);
    }

    @Test
    @IR(counts = {IRNode.X86_MASK_AND_NOT, "= 1"},
        phase = CompilePhase.FINAL_CODE,
        applyIfCPUFeature = {"avx512", "true"})
    public static void testKandnAndNotInt() {
        VectorMask<Integer> m1 = VectorMask.fromArray(I_SPECIES, ma, 0);
        VectorMask<Integer> m2 = VectorMask.fromArray(I_SPECIES, mb, 0);
        m1.andNot(m2).intoArray(mr, 0);
        verifyBinary(I_SPECIES, ma, mb, mr, VectorOpmaskInstructionANDNOTTest::bandNot);
    }

    @Test
    @IR(counts = {IRNode.X86_MASK_AND_NOT, "= 1"},
        phase = CompilePhase.FINAL_CODE,
        applyIfCPUFeature = {"avx512", "true"})
    public static void testKandnAndNotLong() {
        VectorMask<Long> m1 = VectorMask.fromArray(L_SPECIES, ma, 0);
        VectorMask<Long> m2 = VectorMask.fromArray(L_SPECIES, mb, 0);
        m1.andNot(m2).intoArray(mr, 0);
        verifyBinary(L_SPECIES, ma, mb, mr, VectorOpmaskInstructionANDNOTTest::bandNot);
    }


    @Test
    @IR(counts = {IRNode.X86_MASK_AND_NOT, "= 1"},
        phase = CompilePhase.FINAL_CODE,
        applyIfCPUFeature = {"avx512", "true"})
    public static void testKandnAndMXorMaskAllByte() {
        VectorMask<Byte> m1 = VectorMask.fromArray(B_SPECIES, ma, 0);
        VectorMask<Byte> m2 = VectorMask.fromArray(B_SPECIES, mb, 0);
        VectorMask<Byte> all = B_SPECIES.maskAll(true);
        m1.and(m2.xor(all)).intoArray(mr, 0);
        verifyBinary(B_SPECIES, ma, mb, mr, VectorOpmaskInstructionANDNOTTest::bandNot);
    }

    @Test
    @IR(counts = {IRNode.X86_MASK_AND_NOT, "= 1"},
        phase = CompilePhase.FINAL_CODE,
        applyIfCPUFeature = {"avx512", "true"})
    public static void testKandnAndMaskAllXorMByte() {
        VectorMask<Byte> m1 = VectorMask.fromArray(B_SPECIES, ma, 0);
        VectorMask<Byte> m2 = VectorMask.fromArray(B_SPECIES, mb, 0);
        VectorMask<Byte> all = B_SPECIES.maskAll(true);
        m1.and(all.xor(m2)).intoArray(mr, 0);
        verifyBinary(B_SPECIES, ma, mb, mr, VectorOpmaskInstructionANDNOTTest::bandNot);
    }

    @Test
    @IR(counts = {IRNode.X86_MASK_AND_NOT, "= 1"},
        phase = CompilePhase.FINAL_CODE,
        applyIfCPUFeature = {"avx512", "true"})
    public static void testKandnAndMXorMaskAllShort() {
        VectorMask<Short> m1 = VectorMask.fromArray(S_SPECIES, ma, 0);
        VectorMask<Short> m2 = VectorMask.fromArray(S_SPECIES, mb, 0);
        VectorMask<Short> all = S_SPECIES.maskAll(true);
        m1.and(m2.xor(all)).intoArray(mr, 0);
        verifyBinary(S_SPECIES, ma, mb, mr, VectorOpmaskInstructionANDNOTTest::bandNot);
    }

    @Test
    @IR(counts = {IRNode.X86_MASK_AND_NOT, "= 1"},
        phase = CompilePhase.FINAL_CODE,
        applyIfCPUFeature = {"avx512", "true"})
    public static void testKandnAndMaskAllXorMShort() {
        VectorMask<Short> m1 = VectorMask.fromArray(S_SPECIES, ma, 0);
        VectorMask<Short> m2 = VectorMask.fromArray(S_SPECIES, mb, 0);
        VectorMask<Short> all = S_SPECIES.maskAll(true);
        m1.and(all.xor(m2)).intoArray(mr, 0);
        verifyBinary(S_SPECIES, ma, mb, mr, VectorOpmaskInstructionANDNOTTest::bandNot);
    }

    @Test
    @IR(counts = {IRNode.X86_MASK_AND_NOT, "= 1"},
        phase = CompilePhase.FINAL_CODE,
        applyIfCPUFeature = {"avx512", "true"})
    public static void testKandnAndMXorMaskAllInt() {
        VectorMask<Integer> m1 = VectorMask.fromArray(I_SPECIES, ma, 0);
        VectorMask<Integer> m2 = VectorMask.fromArray(I_SPECIES, mb, 0);
        VectorMask<Integer> all = I_SPECIES.maskAll(true);
        m1.and(m2.xor(all)).intoArray(mr, 0);
        verifyBinary(I_SPECIES, ma, mb, mr, VectorOpmaskInstructionANDNOTTest::bandNot);
    }

    @Test
    @IR(counts = {IRNode.X86_MASK_AND_NOT, "= 1"},
        phase = CompilePhase.FINAL_CODE,
        applyIfCPUFeature = {"avx512", "true"})
    public static void testKandnAndMaskAllXorMInt() {
        VectorMask<Integer> m1 = VectorMask.fromArray(I_SPECIES, ma, 0);
        VectorMask<Integer> m2 = VectorMask.fromArray(I_SPECIES, mb, 0);
        VectorMask<Integer> all = I_SPECIES.maskAll(true);
        m1.and(all.xor(m2)).intoArray(mr, 0);
        verifyBinary(I_SPECIES, ma, mb, mr, VectorOpmaskInstructionANDNOTTest::bandNot);
    }

    @Test
    @IR(counts = {IRNode.X86_MASK_AND_NOT, "= 1"},
        phase = CompilePhase.FINAL_CODE,
        applyIfCPUFeature = {"avx512", "true"})
    public static void testKandnAndMXorMaskAllLong() {
        VectorMask<Long> m1 = VectorMask.fromArray(L_SPECIES, ma, 0);
        VectorMask<Long> m2 = VectorMask.fromArray(L_SPECIES, mb, 0);
        VectorMask<Long> all = L_SPECIES.maskAll(true);
        m1.and(m2.xor(all)).intoArray(mr, 0);
        verifyBinary(L_SPECIES, ma, mb, mr, VectorOpmaskInstructionANDNOTTest::bandNot);
    }

    @Test
    @IR(counts = {IRNode.X86_MASK_AND_NOT, "= 1"},
        phase = CompilePhase.FINAL_CODE,
        applyIfCPUFeature = {"avx512", "true"})
    public static void testKandnAndMaskAllXorMLong() {
        VectorMask<Long> m1 = VectorMask.fromArray(L_SPECIES, ma, 0);
        VectorMask<Long> m2 = VectorMask.fromArray(L_SPECIES, mb, 0);
        VectorMask<Long> all = L_SPECIES.maskAll(true);
        m1.and(all.xor(m2)).intoArray(mr, 0);
        verifyBinary(L_SPECIES, ma, mb, mr, VectorOpmaskInstructionANDNOTTest::bandNot);
    }

    @FunctionalInterface
    interface BoolBinaryOp {
        boolean apply(boolean a, boolean b);
    }

    private static void verifyBinary(VectorSpecies<?> species, boolean[] a, boolean[] b,
                                     boolean[] r, BoolBinaryOp op) {
        int len = species.length();
        boolean[] expected = new boolean[len];
        for (int i = 0; i < len; i++) {
            expected[i] = op.apply(a[i], b[i]);
        }
        Verify.checkEQ(expected, Arrays.copyOfRange(r, 0, len));
    }

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.setDefaultWarmup(10000)
                     .addFlags("--add-modules=jdk.incubator.vector")
                     .start();
    }
}
