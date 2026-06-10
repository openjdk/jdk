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
import compiler.lib.verify.*;
import java.util.Arrays;
import jdk.incubator.vector.*;

/**
 * @test
 * @bug 8384507 8385308
 * @library /test/lib /
 * @summary Incorrect vector reassociation for signed saturating addition
 * @modules jdk.incubator.vector
 *
 * @run driver compiler.vectorapi.TestIncorrectVectorReassociation
 */

public class TestIncorrectVectorReassociation {

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.setDefaultWarmup(10000)
                     .addFlags("--add-modules=jdk.incubator.vector")
                     .start();
    }

    /* =======================
     * BYTE: a=100, b=100, arr[i]=-50
     *   Correct: sat_add(100, sat_add(100, -50)) = sat_add(100, 50) = 127
     *   Wrong:   sat_add(sat_add(100, 100), -50) = sat_add(127, -50) = 77
     * ======================= */

    static final VectorSpecies<Byte> BSP = ByteVector.SPECIES_PREFERRED;
    static byte[] byteIn  = new byte[BSP.length()];
    static byte[] byteOut = new byte[BSP.length()];
    static final byte BA = 100, BB = 100;

    static {
        Arrays.fill(byteIn, (byte) -50);
    }

    @Test
    @IR(counts = {IRNode.SATURATING_ADD_VB, " 2 "},
        applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true", "rvv", "true"})
    static void test_byte_sadd(int index) {
        ByteVector.broadcast(BSP, BA)
                  .lanewise(VectorOperators.SADD,
                            ByteVector.broadcast(BSP, BB)
                                     .lanewise(VectorOperators.SADD,
                                               ByteVector.fromArray(BSP, byteIn, index)))
                  .intoArray(byteOut, index);
    }

    @Run(test = "test_byte_sadd")
    void run_byte_sadd() {
        for (int i = 0; i < BSP.loopBound(byteIn.length); i += BSP.length()) {
            test_byte_sadd(i);
        }
        for (int i = 0; i < BSP.loopBound(byteIn.length); i++) {
            Verify.checkEQ(byteOut[i], VectorMath.addSaturating(BA, VectorMath.addSaturating(BB, byteIn[i])));
        }
    }

    /* =======================
     * SHORT: a=30000, b=30000, arr[i]=-50
     *   Correct: sat_add(30000, sat_add(30000, -50)) = sat_add(30000, 29950) = 32767
     *   Wrong:   sat_add(sat_add(30000, 30000), -50) = sat_add(32767, -50) = 32717
     * ======================= */

    static final VectorSpecies<Short> SSP = ShortVector.SPECIES_PREFERRED;
    static short[] shortIn  = new short[SSP.length()];
    static short[] shortOut = new short[SSP.length()];
    static final short SA = 30000, SB = 30000;

    static {
        Arrays.fill(shortIn, (short) -50);
    }

    @Test
    @IR(counts = {IRNode.SATURATING_ADD_VS, " 2 "},
        applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true", "rvv", "true"})
    static void test_short_sadd(int index) {
        ShortVector.broadcast(SSP, SA)
                   .lanewise(VectorOperators.SADD,
                             ShortVector.broadcast(SSP, SB)
                                      .lanewise(VectorOperators.SADD,
                                                ShortVector.fromArray(SSP, shortIn, index)))
                   .intoArray(shortOut, index);
    }

    @Run(test = "test_short_sadd")
    void run_short_sadd() {
        for (int i = 0; i < SSP.loopBound(shortIn.length); i += SSP.length()) {
            test_short_sadd(i);
        }
        for (int i = 0; i < SSP.loopBound(shortIn.length); i++) {
            Verify.checkEQ(shortOut[i], VectorMath.addSaturating(SA, VectorMath.addSaturating(SB, shortIn[i])));
        }
    }

    /* =======================
     * INT: a=2_000_000_000, b=2_000_000_000, arr[i]=-50
     *   Correct: sat_add(2B, sat_add(2B, -50)) = sat_add(2B, 1_999_999_950) = MAX
     *   Wrong:   sat_add(sat_add(2B, 2B), -50) = sat_add(MAX, -50) = MAX-50
     * ======================= */

    static final VectorSpecies<Integer> ISP = IntVector.SPECIES_PREFERRED;
    static int[] intIn  = new int[ISP.length()];
    static int[] intOut = new int[ISP.length()];
    static final int IA = 2_000_000_000, IB = 2_000_000_000;

    static {
        Arrays.fill(intIn, -50);
    }

    @Test
    @IR(counts = {IRNode.SATURATING_ADD_VI, " 2 "},
        applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true", "rvv", "true"})
    static void test_int_sadd(int index) {
        IntVector.broadcast(ISP, IA)
                 .lanewise(VectorOperators.SADD,
                           IntVector.broadcast(ISP, IB)
                                    .lanewise(VectorOperators.SADD,
                                              IntVector.fromArray(ISP, intIn, index)))
                 .intoArray(intOut, index);
    }

    @Run(test = "test_int_sadd")
    void run_int_sadd() {
        for (int i = 0; i < ISP.loopBound(intIn.length); i += ISP.length()) {
            test_int_sadd(i);
        }
        for (int i = 0; i < ISP.loopBound(intIn.length); i++) {
            Verify.checkEQ(intOut[i], VectorMath.addSaturating(IA, VectorMath.addSaturating(IB, intIn[i])));
        }
    }

    /* =======================
     * LONG: a=8_000_000_000_000_000_000L, b=8_000_000_000_000_000_000L, arr[i]=-50
     *   Correct: sat_add(8e18, sat_add(8e18, -50)) = sat_add(8e18, 7_999_999_999_999_999_950) = MAX
     *   Wrong:   sat_add(sat_add(8e18, 8e18), -50) = sat_add(MAX, -50) = MAX-50
     * ======================= */

    static final VectorSpecies<Long> LSP = LongVector.SPECIES_PREFERRED;
    static long[] longIn  = new long[LSP.length()];
    static long[] longOut = new long[LSP.length()];
    static final long LA = 8_000_000_000_000_000_000L, LB = 8_000_000_000_000_000_000L;

    static {
        Arrays.fill(longIn, -50L);
    }

    @Test
    @IR(counts = {IRNode.SATURATING_ADD_VL, " 2 "},
        applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true", "rvv", "true"})
    static void test_long_sadd(int index) {
        LongVector.broadcast(LSP, LA)
                  .lanewise(VectorOperators.SADD,
                            LongVector.broadcast(LSP, LB)
                                     .lanewise(VectorOperators.SADD,
                                               LongVector.fromArray(LSP, longIn, index)))
                  .intoArray(longOut, index);
    }

    @Run(test = "test_long_sadd")
    void run_long_sadd() {
        for (int i = 0; i < LSP.loopBound(longIn.length); i += LSP.length()) {
            test_long_sadd(i);
        }
        for (int i = 0; i < LSP.loopBound(longIn.length); i++) {
            Verify.checkEQ(longOut[i], VectorMath.addSaturating(LA, VectorMath.addSaturating(LB, longIn[i])));
        }
    }

    @Test
    @IR(counts = {IRNode.SATURATING_ADD_VI, " 2 "},
        applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true", "rvv", "true"})
    static IntVector test_mixed_sadd_suadd() {
        IntVector v0 = IntVector.broadcast(ISP, 1);
        IntVector v1 = IntVector.broadcast(ISP, 0);
        IntVector v2 = v0.lanewise(VectorOperators.SADD, v1);
        return v2.lanewise(VectorOperators.SUADD, -1);
    }

    @Run(test = "test_mixed_sadd_suadd")
    void run_mixed_sadd_suadd() {
        IntVector result = test_mixed_sadd_suadd();
        int expected = VectorMath.addSaturatingUnsigned(
                           VectorMath.addSaturating(1, 0), -1);
        int[] res = result.toArray();
        for (int i = 0; i < res.length; i++) {
            Verify.checkEQ(res[i], expected);
        }
    }
}
