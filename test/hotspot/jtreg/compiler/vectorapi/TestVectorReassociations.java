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
 * @bug 8358521
 * @summary Test reassociation of broadcasted inputs across vector operations
 * @modules jdk.incubator.vector
 * @library /test/lib /
 * @run driver compiler.vectorapi.TestVectorReassociations
 */

package compiler.vectorapi;

import compiler.lib.ir_framework.*;
import jdk.incubator.vector.*;
import java.util.stream.IntStream;

/**
 * Tests for the reassociation transform:
 *   VectorOp(broadcast(a), VectorOp(broadcast(b), array))
 *     => VectorOp(broadcast(ScalarOp(a, b)), array)
 */
public class TestVectorReassociations {

    public static void main(String[] args) {
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector");
    }

    /* =======================
     * INT
     * ======================= */

    static final VectorSpecies<Integer> ISP = IntVector.SPECIES_PREFERRED;
    static int[] intIn  = IntStream.range(0, IntVector.SPECIES_PREFERRED.length()).toArray();
    static int[] intOut = new int[IntVector.SPECIES_PREFERRED.length()];
    static int ia = 17, ib = 9;

    // --- INT ADD ---

    // bcast(a) ADD (bcast(b) ADD array)
    @Test
    @IR(applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.ADD_VI, IRNode.VECTOR_SIZE_ANY, " 1 ", IRNode.ADD_I, ">= 1",
                   IRNode.REPLICATE_I, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    @Warmup(value = 10000)
    static void test_int_add_reassociation_pattern1() {
        IntVector.broadcast(ISP, ia)
                 .lanewise(VectorOperators.ADD,
                           IntVector.broadcast(ISP, ib)
                                    .lanewise(VectorOperators.ADD,
                                              IntVector.fromArray(ISP, intIn, 0)))
                 .intoArray(intOut, 0);
    }

    // bcast(a) ADD (array ADD bcast(b))
    @Test
    @IR(applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.ADD_VI, IRNode.VECTOR_SIZE_ANY, " 1 ", IRNode.ADD_I, ">= 1",
                   IRNode.REPLICATE_I, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    @Warmup(value = 10000)
    static void test_int_add_reassociation_pattern2() {
        IntVector.broadcast(ISP, ia)
                 .lanewise(VectorOperators.ADD,
                           IntVector.fromArray(ISP, intIn, 0)
                                    .lanewise(VectorOperators.ADD,
                                              IntVector.broadcast(ISP, ib)))
                 .intoArray(intOut, 0);
    }

    // (bcast(a) ADD array) ADD bcast(b)
    @Test
    @IR(applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.ADD_VI, IRNode.VECTOR_SIZE_ANY, " 1 ", IRNode.ADD_I, ">= 1",
                   IRNode.REPLICATE_I, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    @Warmup(value = 10000)
    static void test_int_add_reassociation_pattern3() {
        IntVector.broadcast(ISP, ia)
                 .lanewise(VectorOperators.ADD,
                           IntVector.fromArray(ISP, intIn, 0))
                 .lanewise(VectorOperators.ADD,
                           IntVector.broadcast(ISP, ib))
                 .intoArray(intOut, 0);
    }

    // (array ADD bcast(a)) ADD bcast(b)
    @Test
    @IR(applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.ADD_VI, IRNode.VECTOR_SIZE_ANY, " 1 ", IRNode.ADD_I, ">= 1",
                   IRNode.REPLICATE_I, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    @Warmup(value = 10000)
    static void test_int_add_reassociation_pattern4() {
        IntVector.fromArray(ISP, intIn, 0)
                 .lanewise(VectorOperators.ADD,
                           IntVector.broadcast(ISP, ia))
                 .lanewise(VectorOperators.ADD,
                           IntVector.broadcast(ISP, ib))
                 .intoArray(intOut, 0);
    }

    // --- INT MUL ---

    // bcast(a) MUL (bcast(b) MUL array)
    @Test
    @IR(applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.MUL_VI, IRNode.VECTOR_SIZE_ANY, " 1 ", IRNode.MUL_I, ">= 1",
                   IRNode.REPLICATE_I, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    @Warmup(value = 10000)
    static void test_int_mul_reassociation_pattern1() {
        IntVector.broadcast(ISP, ia)
                 .lanewise(VectorOperators.MUL,
                           IntVector.broadcast(ISP, ib)
                                    .lanewise(VectorOperators.MUL,
                                              IntVector.fromArray(ISP, intIn, 0)))
                 .intoArray(intOut, 0);
    }

    // bcast(a) MUL (array MUL bcast(b))
    @Test
    @IR(applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.MUL_VI, IRNode.VECTOR_SIZE_ANY, " 1 ", IRNode.MUL_I, ">= 1",
                   IRNode.REPLICATE_I, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    @Warmup(value = 10000)
    static void test_int_mul_reassociation_pattern2() {
        IntVector.broadcast(ISP, ia)
                 .lanewise(VectorOperators.MUL,
                           IntVector.fromArray(ISP, intIn, 0)
                                    .lanewise(VectorOperators.MUL,
                                              IntVector.broadcast(ISP, ib)))
                 .intoArray(intOut, 0);
    }

    // (bcast(a) MUL array) MUL bcast(b)
    @Test
    @IR(applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.MUL_VI, IRNode.VECTOR_SIZE_ANY, " 1 ", IRNode.MUL_I, ">= 1",
                   IRNode.REPLICATE_I, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    @Warmup(value = 10000)
    static void test_int_mul_reassociation_pattern3() {
        IntVector.broadcast(ISP, ia)
                 .lanewise(VectorOperators.MUL,
                           IntVector.fromArray(ISP, intIn, 0))
                 .lanewise(VectorOperators.MUL,
                           IntVector.broadcast(ISP, ib))
                 .intoArray(intOut, 0);
    }

    // (array MUL bcast(a)) MUL bcast(b)
    @Test
    @IR(applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.MUL_VI, IRNode.VECTOR_SIZE_ANY, " 1 ", IRNode.MUL_I, ">= 1",
                   IRNode.REPLICATE_I, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    @Warmup(value = 10000)
    static void test_int_mul_reassociation_pattern4() {
        IntVector.fromArray(ISP, intIn, 0)
                 .lanewise(VectorOperators.MUL,
                           IntVector.broadcast(ISP, ia))
                 .lanewise(VectorOperators.MUL,
                           IntVector.broadcast(ISP, ib))
                 .intoArray(intOut, 0);
    }

    /* =======================
     * LONG
     * ======================= */

    static final VectorSpecies<Long> LSP = LongVector.SPECIES_PREFERRED;
    static long[] longIn;
    static long[] longOut;
    static long la = 17L, lb = 9L;

    static {
        longIn = new long[LSP.length()];
        longOut = new long[LSP.length()];
        for (int i = 0; i < LSP.length(); i++) {
            longIn[i] = (long) i;
        }
    }

    // --- LONG ADD ---

    // bcast(a) ADD (bcast(b) ADD array)
    @Test
    @IR(applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.ADD_VL, IRNode.VECTOR_SIZE_ANY, " 1 ", IRNode.ADD_L, ">= 1",
                   IRNode.REPLICATE_L, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    @Warmup(value = 10000)
    static void test_long_add_reassociation_pattern1() {
        LongVector.broadcast(LSP, la)
                  .lanewise(VectorOperators.ADD,
                            LongVector.broadcast(LSP, lb)
                                     .lanewise(VectorOperators.ADD,
                                               LongVector.fromArray(LSP, longIn, 0)))
                  .intoArray(longOut, 0);
    }

    // bcast(a) ADD (array ADD bcast(b))
    @Test
    @IR(applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.ADD_VL, IRNode.VECTOR_SIZE_ANY, " 1 ", IRNode.ADD_L, ">= 1",
                   IRNode.REPLICATE_L, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    @Warmup(value = 10000)
    static void test_long_add_reassociation_pattern2() {
        LongVector.broadcast(LSP, la)
                  .lanewise(VectorOperators.ADD,
                            LongVector.fromArray(LSP, longIn, 0)
                                     .lanewise(VectorOperators.ADD,
                                               LongVector.broadcast(LSP, lb)))
                  .intoArray(longOut, 0);
    }

    // (bcast(a) ADD array) ADD bcast(b)
    @Test
    @IR(applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.ADD_VL, IRNode.VECTOR_SIZE_ANY, " 1 ", IRNode.ADD_L, ">= 1",
                   IRNode.REPLICATE_L, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    @Warmup(value = 10000)
    static void test_long_add_reassociation_pattern3() {
        LongVector.broadcast(LSP, la)
                  .lanewise(VectorOperators.ADD,
                            LongVector.fromArray(LSP, longIn, 0))
                  .lanewise(VectorOperators.ADD,
                            LongVector.broadcast(LSP, lb))
                  .intoArray(longOut, 0);
    }

    // (array ADD bcast(a)) ADD bcast(b)
    @Test
    @IR(applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.ADD_VL, IRNode.VECTOR_SIZE_ANY, " 1 ", IRNode.ADD_L, ">= 1",
                   IRNode.REPLICATE_L, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    @Warmup(value = 10000)
    static void test_long_add_reassociation_pattern4() {
        LongVector.fromArray(LSP, longIn, 0)
                  .lanewise(VectorOperators.ADD,
                            LongVector.broadcast(LSP, la))
                  .lanewise(VectorOperators.ADD,
                            LongVector.broadcast(LSP, lb))
                  .intoArray(longOut, 0);
    }

    // --- LONG MUL ---

    // bcast(a) MUL (bcast(b) MUL array)
    @Test
    @IR(applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.MUL_VL, IRNode.VECTOR_SIZE_ANY, " 1 ", IRNode.MUL_L, ">= 1",
                   IRNode.REPLICATE_L, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    @Warmup(value = 10000)
    static void test_long_mul_reassociation_pattern1() {
        LongVector.broadcast(LSP, la)
                  .lanewise(VectorOperators.MUL,
                            LongVector.broadcast(LSP, lb)
                                     .lanewise(VectorOperators.MUL,
                                               LongVector.fromArray(LSP, longIn, 0)))
                  .intoArray(longOut, 0);
    }

    // bcast(a) MUL (array MUL bcast(b))
    @Test
    @IR(applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.MUL_VL, IRNode.VECTOR_SIZE_ANY, " 1 ", IRNode.MUL_L, ">= 1",
                   IRNode.REPLICATE_L, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    @Warmup(value = 10000)
    static void test_long_mul_reassociation_pattern2() {
        LongVector.broadcast(LSP, la)
                  .lanewise(VectorOperators.MUL,
                            LongVector.fromArray(LSP, longIn, 0)
                                     .lanewise(VectorOperators.MUL,
                                               LongVector.broadcast(LSP, lb)))
                  .intoArray(longOut, 0);
    }

    // (bcast(a) MUL array) MUL bcast(b)
    @Test
    @IR(applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.MUL_VL, IRNode.VECTOR_SIZE_ANY, " 1 ", IRNode.MUL_L, ">= 1",
                   IRNode.REPLICATE_L, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    @Warmup(value = 10000)
    static void test_long_mul_reassociation_pattern3() {
        LongVector.broadcast(LSP, la)
                  .lanewise(VectorOperators.MUL,
                            LongVector.fromArray(LSP, longIn, 0))
                  .lanewise(VectorOperators.MUL,
                            LongVector.broadcast(LSP, lb))
                  .intoArray(longOut, 0);
    }

    // (array MUL bcast(a)) MUL bcast(b)
    @Test
    @IR(applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.MUL_VL, IRNode.VECTOR_SIZE_ANY, " 1 ", IRNode.MUL_L, ">= 1",
                   IRNode.REPLICATE_L, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    @Warmup(value = 10000)
    static void test_long_mul_reassociation_pattern4() {
        LongVector.fromArray(LSP, longIn, 0)
                  .lanewise(VectorOperators.MUL,
                            LongVector.broadcast(LSP, la))
                  .lanewise(VectorOperators.MUL,
                            LongVector.broadcast(LSP, lb))
                  .intoArray(longOut, 0);
    }

    /* =======================
     * SHORT
     * ======================= */

    static final VectorSpecies<Short> SSP = ShortVector.SPECIES_PREFERRED;
    static short[] shortIn;
    static short[] shortOut;
    static short sa = 17, sb = 9;

    static {
        shortIn = new short[SSP.length()];
        shortOut = new short[SSP.length()];
        for (int i = 0; i < SSP.length(); i++) {
            shortIn[i] = (short) i;
        }
    }

    // --- SHORT ADD ---

    // bcast(a) ADD (bcast(b) ADD array)
    @Test
    @IR(applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.ADD_VS, IRNode.VECTOR_SIZE_ANY, " 1 ", IRNode.ADD_I, ">= 1",
                   IRNode.REPLICATE_S, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    @Warmup(value = 10000)
    static void test_short_add_reassociation_pattern1() {
        ShortVector.broadcast(SSP, sa)
                   .lanewise(VectorOperators.ADD,
                             ShortVector.broadcast(SSP, sb)
                                      .lanewise(VectorOperators.ADD,
                                                ShortVector.fromArray(SSP, shortIn, 0)))
                   .intoArray(shortOut, 0);
    }

    // bcast(a) ADD (array ADD bcast(b))
    @Test
    @IR(applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.ADD_VS, IRNode.VECTOR_SIZE_ANY, " 1 ", IRNode.ADD_I, ">= 1",
                   IRNode.REPLICATE_S, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    @Warmup(value = 10000)
    static void test_short_add_reassociation_pattern2() {
        ShortVector.broadcast(SSP, sa)
                   .lanewise(VectorOperators.ADD,
                             ShortVector.fromArray(SSP, shortIn, 0)
                                      .lanewise(VectorOperators.ADD,
                                                ShortVector.broadcast(SSP, sb)))
                   .intoArray(shortOut, 0);
    }

    // (bcast(a) ADD array) ADD bcast(b)
    @Test
    @IR(applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.ADD_VS, IRNode.VECTOR_SIZE_ANY, " 1 ", IRNode.ADD_I, ">= 1",
                   IRNode.REPLICATE_S, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    @Warmup(value = 10000)
    static void test_short_add_reassociation_pattern3() {
        ShortVector.broadcast(SSP, sa)
                   .lanewise(VectorOperators.ADD,
                             ShortVector.fromArray(SSP, shortIn, 0))
                   .lanewise(VectorOperators.ADD,
                             ShortVector.broadcast(SSP, sb))
                   .intoArray(shortOut, 0);
    }

    // (array ADD bcast(a)) ADD bcast(b)
    @Test
    @IR(applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.ADD_VS, IRNode.VECTOR_SIZE_ANY, " 1 ", IRNode.ADD_I, ">= 1",
                   IRNode.REPLICATE_S, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    @Warmup(value = 10000)
    static void test_short_add_reassociation_pattern4() {
        ShortVector.fromArray(SSP, shortIn, 0)
                   .lanewise(VectorOperators.ADD,
                             ShortVector.broadcast(SSP, sa))
                   .lanewise(VectorOperators.ADD,
                             ShortVector.broadcast(SSP, sb))
                   .intoArray(shortOut, 0);
    }

    // --- SHORT MUL ---

    // bcast(a) MUL (bcast(b) MUL array)
    @Test
    @IR(applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.MUL_VS, IRNode.VECTOR_SIZE_ANY, " 1 ", IRNode.MUL_I, ">= 1",
                   IRNode.REPLICATE_S, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    @Warmup(value = 10000)
    static void test_short_mul_reassociation_pattern1() {
        ShortVector.broadcast(SSP, sa)
                   .lanewise(VectorOperators.MUL,
                             ShortVector.broadcast(SSP, sb)
                                      .lanewise(VectorOperators.MUL,
                                                ShortVector.fromArray(SSP, shortIn, 0)))
                   .intoArray(shortOut, 0);
    }

    // bcast(a) MUL (array MUL bcast(b))
    @Test
    @IR(applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.MUL_VS, IRNode.VECTOR_SIZE_ANY, " 1 ", IRNode.MUL_I, ">= 1",
                   IRNode.REPLICATE_S, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    @Warmup(value = 10000)
    static void test_short_mul_reassociation_pattern2() {
        ShortVector.broadcast(SSP, sa)
                   .lanewise(VectorOperators.MUL,
                             ShortVector.fromArray(SSP, shortIn, 0)
                                      .lanewise(VectorOperators.MUL,
                                                ShortVector.broadcast(SSP, sb)))
                   .intoArray(shortOut, 0);
    }

    // (bcast(a) MUL array) MUL bcast(b)
    @Test
    @IR(applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.MUL_VS, IRNode.VECTOR_SIZE_ANY, " 1 ", IRNode.MUL_I, ">= 1",
                   IRNode.REPLICATE_S, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    @Warmup(value = 10000)
    static void test_short_mul_reassociation_pattern3() {
        ShortVector.broadcast(SSP, sa)
                   .lanewise(VectorOperators.MUL,
                             ShortVector.fromArray(SSP, shortIn, 0))
                   .lanewise(VectorOperators.MUL,
                             ShortVector.broadcast(SSP, sb))
                   .intoArray(shortOut, 0);
    }

    // (array MUL bcast(a)) MUL bcast(b)
    @Test
    @IR(applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.MUL_VS, IRNode.VECTOR_SIZE_ANY, " 1 ", IRNode.MUL_I, ">= 1",
                   IRNode.REPLICATE_S, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    @Warmup(value = 10000)
    static void test_short_mul_reassociation_pattern4() {
        ShortVector.fromArray(SSP, shortIn, 0)
                   .lanewise(VectorOperators.MUL,
                             ShortVector.broadcast(SSP, sa))
                   .lanewise(VectorOperators.MUL,
                             ShortVector.broadcast(SSP, sb))
                   .intoArray(shortOut, 0);
    }

    /* =======================
     * BYTE
     * ======================= */

    static final VectorSpecies<Byte> BSP = ByteVector.SPECIES_PREFERRED;
    static byte[] byteIn;
    static byte[] byteOut;
    static byte ba = 17, bb = 9;

    static {
        byteIn = new byte[BSP.length()];
        byteOut = new byte[BSP.length()];
        for (int i = 0; i < BSP.length(); i++) {
            byteIn[i] = (byte) i;
        }
    }

    // --- BYTE ADD ---

    // bcast(a) ADD (bcast(b) ADD array)
    @Test
    @IR(applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.ADD_VB, IRNode.VECTOR_SIZE_ANY, " 1 ", IRNode.ADD_I, ">= 1",
                   IRNode.REPLICATE_B, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    @Warmup(value = 10000)
    static void test_byte_add_reassociation_pattern1() {
        ByteVector.broadcast(BSP, ba)
                  .lanewise(VectorOperators.ADD,
                            ByteVector.broadcast(BSP, bb)
                                     .lanewise(VectorOperators.ADD,
                                               ByteVector.fromArray(BSP, byteIn, 0)))
                  .intoArray(byteOut, 0);
    }

    // bcast(a) ADD (array ADD bcast(b))
    @Test
    @IR(applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.ADD_VB, IRNode.VECTOR_SIZE_ANY, " 1 ", IRNode.ADD_I, ">= 1",
                   IRNode.REPLICATE_B, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    @Warmup(value = 10000)
    static void test_byte_add_reassociation_pattern2() {
        ByteVector.broadcast(BSP, ba)
                  .lanewise(VectorOperators.ADD,
                            ByteVector.fromArray(BSP, byteIn, 0)
                                     .lanewise(VectorOperators.ADD,
                                               ByteVector.broadcast(BSP, bb)))
                  .intoArray(byteOut, 0);
    }

    // (bcast(a) ADD array) ADD bcast(b)
    @Test
    @IR(applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.ADD_VB, IRNode.VECTOR_SIZE_ANY, " 1 ", IRNode.ADD_I, ">= 1",
                   IRNode.REPLICATE_B, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    @Warmup(value = 10000)
    static void test_byte_add_reassociation_pattern3() {
        ByteVector.broadcast(BSP, ba)
                  .lanewise(VectorOperators.ADD,
                            ByteVector.fromArray(BSP, byteIn, 0))
                  .lanewise(VectorOperators.ADD,
                            ByteVector.broadcast(BSP, bb))
                  .intoArray(byteOut, 0);
    }

    // (array ADD bcast(a)) ADD bcast(b)
    @Test
    @IR(applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.ADD_VB, IRNode.VECTOR_SIZE_ANY, " 1 ", IRNode.ADD_I, ">= 1",
                   IRNode.REPLICATE_B, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    @Warmup(value = 10000)
    static void test_byte_add_reassociation_pattern4() {
        ByteVector.fromArray(BSP, byteIn, 0)
                  .lanewise(VectorOperators.ADD,
                            ByteVector.broadcast(BSP, ba))
                  .lanewise(VectorOperators.ADD,
                            ByteVector.broadcast(BSP, bb))
                  .intoArray(byteOut, 0);
    }

    // --- BYTE MUL ---

    // bcast(a) MUL (bcast(b) MUL array)
    @Test
    @IR(applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.MUL_VB, IRNode.VECTOR_SIZE_ANY, " 1 ", IRNode.MUL_I, ">= 1",
                   IRNode.REPLICATE_B, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    @Warmup(value = 10000)
    static void test_byte_mul_reassociation_pattern1() {
        ByteVector.broadcast(BSP, ba)
                  .lanewise(VectorOperators.MUL,
                            ByteVector.broadcast(BSP, bb)
                                     .lanewise(VectorOperators.MUL,
                                               ByteVector.fromArray(BSP, byteIn, 0)))
                  .intoArray(byteOut, 0);
    }

    // bcast(a) MUL (array MUL bcast(b))
    @Test
    @IR(applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.MUL_VB, IRNode.VECTOR_SIZE_ANY, " 1 ", IRNode.MUL_I, ">= 1",
                   IRNode.REPLICATE_B, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    @Warmup(value = 10000)
    static void test_byte_mul_reassociation_pattern2() {
        ByteVector.broadcast(BSP, ba)
                  .lanewise(VectorOperators.MUL,
                            ByteVector.fromArray(BSP, byteIn, 0)
                                     .lanewise(VectorOperators.MUL,
                                               ByteVector.broadcast(BSP, bb)))
                  .intoArray(byteOut, 0);
    }

    // (bcast(a) MUL array) MUL bcast(b)
    @Test
    @IR(applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.MUL_VB, IRNode.VECTOR_SIZE_ANY, " 1 ", IRNode.MUL_I, ">= 1",
                   IRNode.REPLICATE_B, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    @Warmup(value = 10000)
    static void test_byte_mul_reassociation_pattern3() {
        ByteVector.broadcast(BSP, ba)
                  .lanewise(VectorOperators.MUL,
                            ByteVector.fromArray(BSP, byteIn, 0))
                  .lanewise(VectorOperators.MUL,
                            ByteVector.broadcast(BSP, bb))
                  .intoArray(byteOut, 0);
    }

    // (array MUL bcast(a)) MUL bcast(b)
    @Test
    @IR(applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.MUL_VB, IRNode.VECTOR_SIZE_ANY, " 1 ", IRNode.MUL_I, ">= 1",
                   IRNode.REPLICATE_B, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    @Warmup(value = 10000)
    static void test_byte_mul_reassociation_pattern4() {
        ByteVector.fromArray(BSP, byteIn, 0)
                  .lanewise(VectorOperators.MUL,
                            ByteVector.broadcast(BSP, ba))
                  .lanewise(VectorOperators.MUL,
                            ByteVector.broadcast(BSP, bb))
                  .intoArray(byteOut, 0);
    }
}
