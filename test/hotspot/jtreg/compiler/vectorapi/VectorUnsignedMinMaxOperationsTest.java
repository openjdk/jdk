/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

/**
* @test
* @bug 8342676
* @summary Unsigned Vector Min / Max transforms
* @modules jdk.incubator.vector
* @library /test/lib /
* @run driver compiler.vectorapi.VectorUnsignedMinMaxOperationsTest
*/

package compiler.vectorapi;

import jdk.incubator.vector.*;
import compiler.lib.ir_framework.*;
import compiler.lib.verify.*;
import java.util.stream.IntStream;

public class VectorUnsignedMinMaxOperationsTest {
    private static final int COUNT = 1024;
    private static final VectorSpecies<Long> lspec    = LongVector.SPECIES_PREFERRED;
    private static final VectorSpecies<Integer> ispec = IntVector.SPECIES_PREFERRED;
    private static final VectorSpecies<Short> sspec   = ShortVector.SPECIES_PREFERRED;
    private static final VectorSpecies<Byte> bspec    = ByteVector.SPECIES_PREFERRED;

    private long[]  long_in1;
    private int[]   int_in1;
    private short[] short_in1;
    private byte[]  byte_in1;

    private long[]  long_in2;
    private int[]   int_in2;
    private short[] short_in2;
    private byte[]  byte_in2;

    private long[]  long_out;
    private int[]   int_out;
    private short[] short_out;
    private byte[]  byte_out;

    private boolean[] m1arr, m2arr, m3arr;

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.setDefaultWarmup(5000)
                     .addFlags("--add-modules=jdk.incubator.vector")
                     .start();
    }

    public VectorUnsignedMinMaxOperationsTest() {
        byte_in1  = new byte[COUNT];
        short_in1 = new short[COUNT];
        int_in1   = new int[COUNT];
        long_in1  = new long[COUNT];

        byte_in2  = new byte[COUNT];
        short_in2 = new short[COUNT];
        int_in2   = new int[COUNT];
        long_in2  = new long[COUNT];
        IntStream.range(0, COUNT).forEach(
            i -> {
                if ((i & 1) == 0) {
                    long_in1[i] = Long.MAX_VALUE;
                    long_in2[i] = i;
                    int_in1[i]  = Integer.MAX_VALUE;
                    int_in2[i]  = i;
                    short_in1[i] = Short.MAX_VALUE;
                    short_in2[i] = (short)i;
                    byte_in1[i]  = Byte.MAX_VALUE;
                    byte_in2[i]  = (byte)i;
                } else {
                    long_in1[i] = Long.MIN_VALUE;
                    long_in2[i] = -i;
                    int_in1[i]  = Integer.MIN_VALUE;
                    int_in2[i]  = -i;
                    short_in1[i] = Short.MIN_VALUE;
                    short_in2[i] = (short)-i;
                    byte_in1[i]  = Byte.MIN_VALUE;
                    byte_in2[i]  = (byte)-i;
                }
            }
        );
        long_out  = new long[COUNT];
        int_out   = new int[COUNT];
        short_out = new short[COUNT];
        byte_out  = new byte[COUNT];
        m1arr = new boolean[COUNT];
        m2arr = new boolean[COUNT];
        m3arr = new boolean[COUNT];
        for (int j = 0; j < COUNT; j++) {
            m1arr[j] = (j % 2) == 0;
            m2arr[j] = (j % 2) != 0;
            m3arr[j] = (j % 3) == 0;
        }
    }

    @Test
    @IR(counts = {IRNode.UMAX_VB, " >0 "}, applyIfCPUFeatureOr = {"avx", "true", "rvv", "true"})
    @Warmup(value = 10000)
    public void umax_byte() {
        for (int i = 0; i < COUNT; i += bspec.length()) {
            ByteVector.fromArray(bspec, byte_in1, i)
                      .lanewise(VectorOperators.UMAX,
                                ByteVector.fromArray(bspec, byte_in2, i))
                      .intoArray(byte_out, i);
        }
    }

    @Check(test = "umax_byte", when = CheckAt.COMPILED)
    public void umax_byte_verify() {
        for (int i = 0; i < COUNT; i++) {
            byte actual = byte_out[i];
            byte expected = VectorMath.maxUnsigned(byte_in1[i], byte_in2[i]);
            if (actual != expected) {
                throw new AssertionError("Result Mismatch : actual (" +  actual + ") !=  expected (" + expected  + ")");
            }
        }
    }

    @Test
    @IR(counts = {IRNode.UMAX_VS, " >0 "}, applyIfCPUFeatureOr = {"avx", "true", "rvv", "true"})
    @Warmup(value = 10000)
    public void umax_short() {
        for (int i = 0; i < COUNT; i += sspec.length()) {
            ShortVector.fromArray(sspec, short_in1, i)
                       .lanewise(VectorOperators.UMAX,
                                 ShortVector.fromArray(sspec, short_in2, i))
                       .intoArray(short_out, i);
        }
    }

    @Check(test = "umax_short", when = CheckAt.COMPILED)
    public void umax_short_verify() {
        for (int i = 0; i < COUNT; i++) {
            short actual = short_out[i];
            short expected = VectorMath.maxUnsigned(short_in1[i], short_in2[i]);
            if (actual != expected) {
                throw new AssertionError("Result Mismatch : actual (" +  actual + ") !=  expected (" + expected  + ")");
            }
        }
    }

    @Test
    @IR(counts = {IRNode.UMAX_VI, " >0 "}, applyIfCPUFeatureOr = {"avx", "true", "rvv", "true"})
    @Warmup(value = 10000)
    public void umax_int() {
        for (int i = 0; i < COUNT; i += ispec.length()) {
            IntVector.fromArray(ispec, int_in1, i)
                     .lanewise(VectorOperators.UMAX,
                               IntVector.fromArray(ispec, int_in2, i))
                     .intoArray(int_out, i);
        }
    }

    @Check(test = "umax_int", when = CheckAt.COMPILED)
    public void umax_int_verify() {
        for (int i = 0; i < COUNT; i++) {
            int actual = int_out[i];
            int expected = VectorMath.maxUnsigned(int_in1[i], int_in2[i]);
            if (actual != expected) {
                throw new AssertionError("Result Mismatch : actual (" +  actual + ") !=  expected (" + expected  + ")");
            }
        }
    }

    @Test
    @IR(counts = {IRNode.UMAX_VL, " >0 "}, applyIfCPUFeatureOr = {"avx", "true", "rvv", "true"})
    @Warmup(value = 10000)
    public void umax_long() {
        for (int i = 0; i < COUNT; i += lspec.length()) {
            LongVector.fromArray(lspec, long_in1, i)
                      .lanewise(VectorOperators.UMAX,
                                LongVector.fromArray(lspec, long_in2, i))
                      .intoArray(long_out, i);
        }
    }

    @Check(test = "umax_long", when = CheckAt.COMPILED)
    public void umax_long_verify() {
        for (int i = 0; i < COUNT; i++) {
            long actual = long_out[i];
            long expected = VectorMath.maxUnsigned(long_in1[i], long_in2[i]);
            if (actual != expected) {
                throw new AssertionError("Result Mismatch : actual (" +  actual + ") !=  expected (" + expected  + ")");
            }
        }
    }

    @Test
    @IR(counts = {IRNode.UMIN_VB, " >0 "}, applyIfCPUFeatureOr = {"avx", "true", "rvv", "true"})
    @Warmup(value = 10000)
    public void umin_byte() {
        for (int i = 0; i < COUNT; i += bspec.length()) {
            ByteVector.fromArray(bspec, byte_in1, i)
                      .lanewise(VectorOperators.UMIN,
                                ByteVector.fromArray(bspec, byte_in2, i))
                      .intoArray(byte_out, i);
        }
    }

    @Check(test = "umin_byte", when = CheckAt.COMPILED)
    public void umin_byte_verify() {
        for (int i = 0; i < COUNT; i++) {
            byte actual = byte_out[i];
            byte expected = VectorMath.minUnsigned(byte_in1[i], byte_in2[i]);
            if (actual != expected) {
                throw new AssertionError("Result Mismatch : actual (" +  actual + ") !=  expected (" + expected  + ")");
            }
        }
    }

    @Test
    @IR(counts = {IRNode.UMIN_VS, " >0 "}, applyIfCPUFeatureOr = {"avx", "true", "rvv", "true"})
    @Warmup(value = 10000)
    public void umin_short() {
        for (int i = 0; i < COUNT; i += sspec.length()) {
            ShortVector.fromArray(sspec, short_in1, i)
                       .lanewise(VectorOperators.UMIN,
                                 ShortVector.fromArray(sspec, short_in2, i))
                       .intoArray(short_out, i);
        }
    }

    @Check(test = "umin_short", when = CheckAt.COMPILED)
    public void umin_short_verify() {
        for (int i = 0; i < COUNT; i++) {
            short actual = short_out[i];
            short expected = VectorMath.minUnsigned(short_in1[i], short_in2[i]);
            if (actual != expected) {
                throw new AssertionError("Result Mismatch : actual (" +  actual + ") !=  expected (" + expected  + ")");
            }
        }
    }

    @Test
    @IR(counts = {IRNode.UMIN_VI, " >0 "}, applyIfCPUFeatureOr = {"avx", "true", "rvv", "true"})
    @Warmup(value = 10000)
    public void umin_int() {
        for (int i = 0; i < COUNT; i += ispec.length()) {
            IntVector.fromArray(ispec, int_in1, i)
                     .lanewise(VectorOperators.UMIN,
                               IntVector.fromArray(ispec, int_in2, i))
                     .intoArray(int_out, i);
        }
    }

    @Check(test = "umin_int", when = CheckAt.COMPILED)
    public void umin_int_verify() {
        for (int i = 0; i < COUNT; i++) {
            int actual = int_out[i];
            int expected = VectorMath.minUnsigned(int_in1[i], int_in2[i]);
            if (actual != expected) {
                throw new AssertionError("Result Mismatch : actual (" +  actual + ") !=  expected (" + expected  + ")");
            }
        }
    }

    @Test
    @IR(counts = {IRNode.UMIN_VL, " >0 "}, applyIfCPUFeatureOr = {"avx", "true", "rvv", "true"})
    @Warmup(value = 10000)
    public void umin_long() {
        for (int i = 0; i < COUNT; i += lspec.length()) {
            LongVector.fromArray(lspec, long_in1, i)
                      .lanewise(VectorOperators.UMIN,
                                LongVector.fromArray(lspec, long_in2, i))
                      .intoArray(long_out, i);
        }
    }

    @Check(test = "umin_long")
    public void umin_long_verify() {
        for (int i = 0; i < COUNT; i++) {
            long actual = long_out[i];
            long expected = VectorMath.minUnsigned(long_in1[i], long_in2[i]);
            if (actual != expected) {
                throw new AssertionError("Result Mismatch : actual (" +  actual + ") !=  expected (" + expected  + ")");
            }
        }
    }

    @Test
    @IR(counts = {IRNode.UMIN_VI, " 0 "}, applyIfCPUFeatureOr = {"avx", "true", "rvv", "true"})
    @Warmup(value = 10000)
    public void umin_ir_transform1() {
        for (int i = 0; i < COUNT; i += ispec.length()) {
            IntVector.fromArray(ispec, int_in1, i)
                     .lanewise(VectorOperators.UMIN,
                               IntVector.fromArray(ispec, int_in1, i))
                     .intoArray(int_out, i);
       }
    }

    @Check(test = "umin_ir_transform1", when = CheckAt.COMPILED)
    public void umin_ir_transform1_verify() {
        for (int i = 0; i < COUNT; i++) {
            int actual = int_out[i];
            int expected = VectorMath.minUnsigned(int_in1[i], int_in1[i]);
            if (actual != expected) {
                throw new AssertionError("Result Mismatch : actual (" +  actual + ") !=  expected (" + expected  + ")");
            }
        }
    }

    @Test
    @IR(counts = {IRNode.UMAX_VI, " 0 "}, applyIfCPUFeatureOr = {"avx", "true", "rvv", "true"})
    @Warmup(value = 10000)
    public void umax_ir_transform1() {
        for (int i = 0; i < COUNT; i += ispec.length()) {
            IntVector.fromArray(ispec, int_in1, i)
                     .lanewise(VectorOperators.UMAX,
                               IntVector.fromArray(ispec, int_in1, i))
                     .intoArray(int_out, i);
        }
    }

    @Check(test = "umax_ir_transform1", when = CheckAt.COMPILED)
    public void umax_ir_transform1_verify() {
        for (int i = 0; i < COUNT; i++) {
            int actual = int_out[i];
            int expected = VectorMath.maxUnsigned(int_in1[i], int_in1[i]);
            if (actual != expected) {
                throw new AssertionError("Result Mismatch : actual (" +  actual + ") !=  expected (" + expected  + ")");
            }
        }
    }

    @Test
    @IR(counts = {IRNode.UMAX_VI, " 0 ", IRNode.UMIN_VI, " >0 "}, applyIfCPUFeatureOr = {"avx", "true", "rvv", "true"})
    @Warmup(value = 10000)
    public void umin_max_ir_transform1() {
        for (int i = 0; i < COUNT; i += ispec.length()) {
            IntVector vec1 = IntVector.fromArray(ispec, int_in1, i);
            IntVector vec2 = IntVector.fromArray(ispec, int_in2, i);
            // UMinV (UMinV vec1, vec2) (UMaxV vec1, vec2) => UMinV vec1 vec2
            vec1.lanewise(VectorOperators.UMIN, vec2)
                .lanewise(VectorOperators.UMIN,
                          vec1.lanewise(VectorOperators.UMAX, vec2))
                .intoArray(int_out, i);
        }
    }

    @Check(test = "umin_max_ir_transform1", when = CheckAt.COMPILED)
    public void umin_max_ir_transform1_verify() {
        for (int i = 0; i < COUNT; i++) {
            int actual = int_out[i];
            int expected = VectorMath.minUnsigned(VectorMath.minUnsigned(int_in1[i], int_in2[i]),
                                                  VectorMath.maxUnsigned(int_in1[i], int_in2[i]));
            if (actual != expected) {
                throw new AssertionError("Result Mismatch : actual (" +  actual + ") !=  expected (" + expected  + ")");
            }
        }
    }

    @Test
    @IR(counts = {IRNode.UMIN_VI, " 0 ", IRNode.UMAX_VI, " >0 "}, applyIfCPUFeatureOr = {"avx", "true", "rvv", "true"})
    @Warmup(value = 10000)
    public void umin_max_ir_transform2() {
        for (int i = 0; i < COUNT; i += ispec.length()) {
            IntVector vec1 = IntVector.fromArray(ispec, int_in1, i);
            IntVector vec2 = IntVector.fromArray(ispec, int_in2, i);
            // UMaxV (UMinV vec2, vec1) (UMaxV vec1, vec2) => UMaxV vec1 vec2
            vec2.lanewise(VectorOperators.UMIN, vec1)
                .lanewise(VectorOperators.UMAX,
                          vec1.lanewise(VectorOperators.UMAX, vec2))
                .intoArray(int_out, i);
        }
    }

    @Check(test = "umin_max_ir_transform2", when = CheckAt.COMPILED)
    public void umin_max_ir_transform2_verify() {
        for (int i = 0; i < COUNT; i++) {
            int actual = int_out[i];
            int expected = VectorMath.maxUnsigned(VectorMath.minUnsigned(int_in2[i], int_in1[i]),
                                                  VectorMath.maxUnsigned(int_in1[i], int_in2[i]));
            if (actual != expected) {
                throw new AssertionError("Result Mismatch : actual (" +  actual + ") !=  expected (" + expected  + ")");
            }
        }
    }

    @Test
    @IR(counts = {IRNode.UMAX_VI, " 0 ", IRNode.UMIN_VI, " >0 "}, applyIfCPUFeatureOr = {"avx", "true", "rvv", "true"})
    @Warmup(value = 10000)
    public void umin_max_ir_transform3() {
        for (int i = 0; i < COUNT; i += ispec.length()) {
            IntVector vec1 = IntVector.fromArray(ispec, int_in1, i);
            IntVector vec2 = IntVector.fromArray(ispec, int_in2, i);
            // UMaxV (UMinV vec1, vec2) (UMinV vec1, vec2) => UMinV vec1 vec2
            vec1.lanewise(VectorOperators.UMIN, vec2)
                .lanewise(VectorOperators.UMAX,
                          vec1.lanewise(VectorOperators.UMIN, vec2))
                .intoArray(int_out, i);
        }
    }

    @Check(test = "umin_max_ir_transform3", when = CheckAt.COMPILED)
    public void umin_max_ir_transform3_verify() {
        for (int i = 0; i < COUNT; i++) {
            int actual = int_out[i];
            int expected = VectorMath.maxUnsigned(VectorMath.minUnsigned(int_in1[i], int_in2[i]),
                                                  VectorMath.minUnsigned(int_in1[i], int_in2[i]));
            if (actual != expected) {
                throw new AssertionError("Result Mismatch : actual (" +  actual + ") !=  expected (" + expected  + ")");
            }
        }
    }

    @Test
    @IR(counts = {IRNode.UMIN_VI, " 0 ", IRNode.UMAX_VI, " >0 "}, applyIfCPUFeatureOr = {"avx", "true", "rvv", "true"})
    @Warmup(value = 10000)
    public void umin_max_ir_transform4() {
        for (int i = 0; i < COUNT; i += ispec.length()) {
            IntVector vec1 = IntVector.fromArray(ispec, int_in1, i);
            IntVector vec2 = IntVector.fromArray(ispec, int_in2, i);
            // UMinV (UMaxV vec2, vec1) (UMaxV vec1, vec2) => UMaxV vec1 vec2
            vec2.lanewise(VectorOperators.UMAX, vec1)
                .lanewise(VectorOperators.UMIN,
                          vec1.lanewise(VectorOperators.UMAX, vec2))
                .intoArray(int_out, i);
        }
    }

    @Check(test = "umin_max_ir_transform4", when = CheckAt.COMPILED)
    public void umin_max_ir_transform4_verify() {
        for (int i = 0; i < COUNT; i++) {
            int actual = int_out[i];
            int expected = VectorMath.minUnsigned(VectorMath.maxUnsigned(int_in2[i], int_in1[i]),
                                                  VectorMath.maxUnsigned(int_in1[i], int_in2[i]));
            if (actual != expected) {
                throw new AssertionError("Result Mismatch : actual (" +  actual + ") !=  expected (" + expected  + ")");
            }
        }
    }

    @Test
    @IR(counts = {IRNode.UMIN_VI, " 0 ", IRNode.UMAX_VI, " >0 "}, applyIfCPUFeatureOr = {"avx", "true", "rvv", "true"})
    @Warmup(value = 10000)
    public void umin_max_ir_transform5() {
        for (int i = 0; i < COUNT; i += ispec.length()) {
            IntVector vec1 = IntVector.fromArray(ispec, int_in1, i);
            IntVector vec2 = IntVector.fromArray(ispec, int_in2, i);
            // UMaxV (UMinV vec1, vec2) (UMaxV vec2, vec1) => UMaxV vec1 vec2
            vec1.lanewise(VectorOperators.UMIN, vec2)
                .lanewise(VectorOperators.UMAX,
                          vec2.lanewise(VectorOperators.UMAX, vec1))
                .intoArray(int_out, i);
        }
    }

    @Check(test = "umin_max_ir_transform5", when = CheckAt.COMPILED)
    public void umin_max_ir_transform5_verify() {
        for (int i = 0; i < COUNT; i++) {
            int actual = int_out[i];
            int expected = VectorMath.maxUnsigned(VectorMath.minUnsigned(int_in1[i], int_in2[i]),
                                                  VectorMath.maxUnsigned(int_in2[i], int_in1[i]));
            if (actual != expected) {
                throw new AssertionError("Result Mismatch : actual (" +  actual + ") !=  expected (" + expected  + ")");
            }
        }
    }


    // Predicated: umin(umin(a,b,m), umax(a,b,m), m) => umin(a,b,m)
    @Test
    @IR(counts = {IRNode.UMIN_VI, " 1 ", IRNode.UMAX_VI, " 0 "}, applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void umin_masked_same_mask(int index) {
        IntVector vec1 = IntVector.fromArray(ispec, int_in1, index);
        IntVector vec2 = IntVector.fromArray(ispec, int_in2, index);
        VectorMask<Integer> m = VectorMask.fromArray(ispec, m1arr, index);
        vec1.lanewise(VectorOperators.UMIN, vec2, m)
            .lanewise(VectorOperators.UMIN,
                      vec1.lanewise(VectorOperators.UMAX, vec2, m), m)
            .intoArray(int_out, index);
    }

    @Run(test = "umin_masked_same_mask")
    public void umin_masked_same_mask_runner() {
        for (int i = 0; i < ispec.loopBound(COUNT); i += ispec.length()) {
            umin_masked_same_mask(i);
        }
        for (int i = 0; i < ispec.loopBound(COUNT); i++) {
            int a = int_in1[i], b = int_in2[i];
            int minAB = m1arr[i] ? VectorMath.minUnsigned(a, b) : a;
            int maxAB = m1arr[i] ? VectorMath.maxUnsigned(a, b) : a;
            int expected = m1arr[i] ? VectorMath.minUnsigned(minAB, maxAB) : minAB;
            Verify.checkEQ(int_out[i], expected);
        }
    }

    // Predicated: umin(umin(a,b,m), umax(b,a,m), m) => umin(a,b,m)
    @Test
    @IR(counts = {IRNode.UMIN_VI, " 1 ", IRNode.UMAX_VI, " 0 "}, applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void umin_masked_flipped_inputs(int index) {
        IntVector vec1 = IntVector.fromArray(ispec, int_in1, index);
        IntVector vec2 = IntVector.fromArray(ispec, int_in2, index);
        VectorMask<Integer> m = VectorMask.fromArray(ispec, m1arr, index);
        vec1.lanewise(VectorOperators.UMIN, vec2, m)
            .lanewise(VectorOperators.UMIN,
                      vec2.lanewise(VectorOperators.UMAX, vec1, m), m)
            .intoArray(int_out, index);
    }

    @Run(test = "umin_masked_flipped_inputs")
    public void umin_masked_flipped_inputs_runner() {
        for (int i = 0; i < ispec.loopBound(COUNT); i += ispec.length()) {
            umin_masked_flipped_inputs(i);
        }
        for (int i = 0; i < ispec.loopBound(COUNT); i++) {
            int a = int_in1[i], b = int_in2[i];
            int minAB = m1arr[i] ? VectorMath.minUnsigned(a, b) : a;
            int maxBA = m1arr[i] ? VectorMath.maxUnsigned(b, a) : b;
            int expected = m1arr[i] ? VectorMath.minUnsigned(minAB, maxBA) : minAB;
            Verify.checkEQ(int_out[i], expected);
        }
    }

    // Predicated: umin(umin(a,b,m1), umax(a,b,m2), m1) => NO transform
    @Test
    @IR(counts = {IRNode.UMIN_VI, " 2 ", IRNode.UMAX_VI, " 1 "}, applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void umin_masked_diff_mask_minmax(int index) {
        IntVector vec1 = IntVector.fromArray(ispec, int_in1, index);
        IntVector vec2 = IntVector.fromArray(ispec, int_in2, index);
        VectorMask<Integer> mask1 = VectorMask.fromArray(ispec, m1arr, index);
        VectorMask<Integer> mask2 = VectorMask.fromArray(ispec, m2arr, index);
        vec1.lanewise(VectorOperators.UMIN, vec2, mask1)
            .lanewise(VectorOperators.UMIN,
                      vec1.lanewise(VectorOperators.UMAX, vec2, mask2), mask1)
            .intoArray(int_out, index);
    }

    @Run(test = "umin_masked_diff_mask_minmax")
    public void umin_masked_diff_mask_minmax_runner() {
        for (int i = 0; i < ispec.loopBound(COUNT); i += ispec.length()) {
            umin_masked_diff_mask_minmax(i);
        }
        for (int i = 0; i < ispec.loopBound(COUNT); i++) {
            int a = int_in1[i], b = int_in2[i];
            int minAB = m1arr[i] ? VectorMath.minUnsigned(a, b) : a;
            int maxAB = m2arr[i] ? VectorMath.maxUnsigned(a, b) : a;
            int expected = m1arr[i] ? VectorMath.minUnsigned(minAB, maxAB) : minAB;
            Verify.checkEQ(int_out[i], expected);
        }
    }

    // Predicated: umin(umin(a,b,m2), umax(a,b,m1), m1) => NO transform
    @Test
    @IR(counts = {IRNode.UMIN_VI, " 2 ", IRNode.UMAX_VI, " 1 "}, applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void umin_masked_diff_mask_minmax_swap(int index) {
        IntVector vec1 = IntVector.fromArray(ispec, int_in1, index);
        IntVector vec2 = IntVector.fromArray(ispec, int_in2, index);
        VectorMask<Integer> mask1 = VectorMask.fromArray(ispec, m1arr, index);
        VectorMask<Integer> mask2 = VectorMask.fromArray(ispec, m2arr, index);
        vec1.lanewise(VectorOperators.UMIN, vec2, mask2)
            .lanewise(VectorOperators.UMIN,
                      vec1.lanewise(VectorOperators.UMAX, vec2, mask1), mask1)
            .intoArray(int_out, index);
    }

    @Run(test = "umin_masked_diff_mask_minmax_swap")
    public void umin_masked_diff_mask_minmax_swap_runner() {
        for (int i = 0; i < ispec.loopBound(COUNT); i += ispec.length()) {
            umin_masked_diff_mask_minmax_swap(i);
        }
        for (int i = 0; i < ispec.loopBound(COUNT); i++) {
            int a = int_in1[i], b = int_in2[i];
            int minAB = m2arr[i] ? VectorMath.minUnsigned(a, b) : a;
            int maxAB = m1arr[i] ? VectorMath.maxUnsigned(a, b) : a;
            int expected = m1arr[i] ? VectorMath.minUnsigned(minAB, maxAB) : minAB;
            Verify.checkEQ(int_out[i], expected);
        }
    }

    // Predicated: umin(umin(a,b,m1), umax(a,b,m1), m2) => NO transform
    @Test
    @IR(counts = {IRNode.UMIN_VI, " 2 ", IRNode.UMAX_VI, " 1 "}, applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void umin_masked_diff_mask_outer(int index) {
        IntVector vec1 = IntVector.fromArray(ispec, int_in1, index);
        IntVector vec2 = IntVector.fromArray(ispec, int_in2, index);
        VectorMask<Integer> mask1 = VectorMask.fromArray(ispec, m1arr, index);
        VectorMask<Integer> mask2 = VectorMask.fromArray(ispec, m2arr, index);
        vec1.lanewise(VectorOperators.UMIN, vec2, mask1)
            .lanewise(VectorOperators.UMIN,
                      vec1.lanewise(VectorOperators.UMAX, vec2, mask1), mask2)
            .intoArray(int_out, index);
    }

    @Run(test = "umin_masked_diff_mask_outer")
    public void umin_masked_diff_mask_outer_runner() {
        for (int i = 0; i < ispec.loopBound(COUNT); i += ispec.length()) {
            umin_masked_diff_mask_outer(i);
        }
        for (int i = 0; i < ispec.loopBound(COUNT); i++) {
            int a = int_in1[i], b = int_in2[i];
            int minAB = m1arr[i] ? VectorMath.minUnsigned(a, b) : a;
            int maxAB = m1arr[i] ? VectorMath.maxUnsigned(a, b) : a;
            int expected = m2arr[i] ? VectorMath.minUnsigned(minAB, maxAB) : minAB;
            Verify.checkEQ(int_out[i], expected);
        }
    }

    // Predicated: umin(umin(a,b,m1), umax(a,b,m2), m3) => NO transform
    @Test
    @IR(counts = {IRNode.UMIN_VI, " 2 ", IRNode.UMAX_VI, " 1 "}, applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void umin_masked_all_diff_mask(int index) {
        IntVector vec1 = IntVector.fromArray(ispec, int_in1, index);
        IntVector vec2 = IntVector.fromArray(ispec, int_in2, index);
        VectorMask<Integer> mask1 = VectorMask.fromArray(ispec, m1arr, index);
        VectorMask<Integer> mask2 = VectorMask.fromArray(ispec, m2arr, index);
        VectorMask<Integer> mask3 = VectorMask.fromArray(ispec, m3arr, index);
        vec1.lanewise(VectorOperators.UMIN, vec2, mask1)
            .lanewise(VectorOperators.UMIN,
                      vec1.lanewise(VectorOperators.UMAX, vec2, mask2), mask3)
            .intoArray(int_out, index);
    }

    @Run(test = "umin_masked_all_diff_mask")
    public void umin_masked_all_diff_mask_runner() {
        for (int i = 0; i < ispec.loopBound(COUNT); i += ispec.length()) {
            umin_masked_all_diff_mask(i);
        }
        for (int i = 0; i < ispec.loopBound(COUNT); i++) {
            int a = int_in1[i], b = int_in2[i];
            int minAB = m1arr[i] ? VectorMath.minUnsigned(a, b) : a;
            int maxAB = m2arr[i] ? VectorMath.maxUnsigned(a, b) : a;
            int expected = m3arr[i] ? VectorMath.minUnsigned(minAB, maxAB) : minAB;
            Verify.checkEQ(int_out[i], expected);
        }
    }

    // Predicated: umax(umin(a,b,m), umax(a,b,m), m) => umax(a,b,m)
    @Test
    @IR(counts = {IRNode.UMIN_VI, " 0 ", IRNode.UMAX_VI, " 1 "}, applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void umax_masked_same_mask(int index) {
        IntVector vec1 = IntVector.fromArray(ispec, int_in1, index);
        IntVector vec2 = IntVector.fromArray(ispec, int_in2, index);
        VectorMask<Integer> m = VectorMask.fromArray(ispec, m1arr, index);
        vec1.lanewise(VectorOperators.UMIN, vec2, m)
            .lanewise(VectorOperators.UMAX,
                      vec1.lanewise(VectorOperators.UMAX, vec2, m), m)
            .intoArray(int_out, index);
    }

    @Run(test = "umax_masked_same_mask")
    public void umax_masked_same_mask_runner() {
        for (int i = 0; i < ispec.loopBound(COUNT); i += ispec.length()) {
            umax_masked_same_mask(i);
        }
        for (int i = 0; i < ispec.loopBound(COUNT); i++) {
            int a = int_in1[i], b = int_in2[i];
            int minAB = m1arr[i] ? VectorMath.minUnsigned(a, b) : a;
            int maxAB = m1arr[i] ? VectorMath.maxUnsigned(a, b) : a;
            int expected = m1arr[i] ? VectorMath.maxUnsigned(minAB, maxAB) : minAB;
            Verify.checkEQ(int_out[i], expected);
        }
    }

    // Predicated: umax(umin(a,b,m), umax(b,a,m), m) => umax(a,b,m)
    @Test
    @IR(counts = {IRNode.UMIN_VI, " 0 ", IRNode.UMAX_VI, " 1 "}, applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void umax_masked_flipped_inputs(int index) {
        IntVector vec1 = IntVector.fromArray(ispec, int_in1, index);
        IntVector vec2 = IntVector.fromArray(ispec, int_in2, index);
        VectorMask<Integer> m = VectorMask.fromArray(ispec, m1arr, index);
        vec1.lanewise(VectorOperators.UMIN, vec2, m)
            .lanewise(VectorOperators.UMAX,
                      vec2.lanewise(VectorOperators.UMAX, vec1, m), m)
            .intoArray(int_out, index);
    }

    @Run(test = "umax_masked_flipped_inputs")
    public void umax_masked_flipped_inputs_runner() {
        for (int i = 0; i < ispec.loopBound(COUNT); i += ispec.length()) {
            umax_masked_flipped_inputs(i);
        }
        for (int i = 0; i < ispec.loopBound(COUNT); i++) {
            int a = int_in1[i], b = int_in2[i];
            int minAB = m1arr[i] ? VectorMath.minUnsigned(a, b) : a;
            int maxBA = m1arr[i] ? VectorMath.maxUnsigned(b, a) : b;
            int expected = m1arr[i] ? VectorMath.maxUnsigned(minAB, maxBA) : minAB;
            Verify.checkEQ(int_out[i], expected);
        }
    }

    // Predicated: umax(umin(a,b,m1), umax(a,b,m2), m1) => NO transform
    @Test
    @IR(counts = {IRNode.UMIN_VI, " 1 ", IRNode.UMAX_VI, " 2 "}, applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void umax_masked_diff_mask_minmax(int index) {
        IntVector vec1 = IntVector.fromArray(ispec, int_in1, index);
        IntVector vec2 = IntVector.fromArray(ispec, int_in2, index);
        VectorMask<Integer> mask1 = VectorMask.fromArray(ispec, m1arr, index);
        VectorMask<Integer> mask2 = VectorMask.fromArray(ispec, m2arr, index);
        vec1.lanewise(VectorOperators.UMIN, vec2, mask1)
            .lanewise(VectorOperators.UMAX,
                      vec1.lanewise(VectorOperators.UMAX, vec2, mask2), mask1)
            .intoArray(int_out, index);
    }

    @Run(test = "umax_masked_diff_mask_minmax")
    public void umax_masked_diff_mask_minmax_runner() {
        for (int i = 0; i < ispec.loopBound(COUNT); i += ispec.length()) {
            umax_masked_diff_mask_minmax(i);
        }
        for (int i = 0; i < ispec.loopBound(COUNT); i++) {
            int a = int_in1[i], b = int_in2[i];
            int minAB = m1arr[i] ? VectorMath.minUnsigned(a, b) : a;
            int maxAB = m2arr[i] ? VectorMath.maxUnsigned(a, b) : a;
            int expected = m1arr[i] ? VectorMath.maxUnsigned(minAB, maxAB) : minAB;
            Verify.checkEQ(int_out[i], expected);
        }
    }

    // Predicated: umax(umin(a,b,m2), umax(a,b,m1), m1) => NO transform
    @Test
    @IR(counts = {IRNode.UMIN_VI, " 1 ", IRNode.UMAX_VI, " 2 "}, applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void umax_masked_diff_mask_minmax_swap(int index) {
        IntVector vec1 = IntVector.fromArray(ispec, int_in1, index);
        IntVector vec2 = IntVector.fromArray(ispec, int_in2, index);
        VectorMask<Integer> mask1 = VectorMask.fromArray(ispec, m1arr, index);
        VectorMask<Integer> mask2 = VectorMask.fromArray(ispec, m2arr, index);
        vec1.lanewise(VectorOperators.UMIN, vec2, mask2)
            .lanewise(VectorOperators.UMAX,
                      vec1.lanewise(VectorOperators.UMAX, vec2, mask1), mask1)
            .intoArray(int_out, index);
    }

    @Run(test = "umax_masked_diff_mask_minmax_swap")
    public void umax_masked_diff_mask_minmax_swap_runner() {
        for (int i = 0; i < ispec.loopBound(COUNT); i += ispec.length()) {
            umax_masked_diff_mask_minmax_swap(i);
        }
        for (int i = 0; i < ispec.loopBound(COUNT); i++) {
            int a = int_in1[i], b = int_in2[i];
            int minAB = m2arr[i] ? VectorMath.minUnsigned(a, b) : a;
            int maxAB = m1arr[i] ? VectorMath.maxUnsigned(a, b) : a;
            int expected = m1arr[i] ? VectorMath.maxUnsigned(minAB, maxAB) : minAB;
            Verify.checkEQ(int_out[i], expected);
        }
    }

    // Predicated: umax(umin(a,b,m1), umax(a,b,m1), m2) => NO transform
    @Test
    @IR(counts = {IRNode.UMIN_VI, " 1 ", IRNode.UMAX_VI, " 2 "}, applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void umax_masked_diff_mask_outer(int index) {
        IntVector vec1 = IntVector.fromArray(ispec, int_in1, index);
        IntVector vec2 = IntVector.fromArray(ispec, int_in2, index);
        VectorMask<Integer> mask1 = VectorMask.fromArray(ispec, m1arr, index);
        VectorMask<Integer> mask2 = VectorMask.fromArray(ispec, m2arr, index);
        vec1.lanewise(VectorOperators.UMIN, vec2, mask1)
            .lanewise(VectorOperators.UMAX,
                      vec1.lanewise(VectorOperators.UMAX, vec2, mask1), mask2)
            .intoArray(int_out, index);
    }

    @Run(test = "umax_masked_diff_mask_outer")
    public void umax_masked_diff_mask_outer_runner() {
        for (int i = 0; i < ispec.loopBound(COUNT); i += ispec.length()) {
            umax_masked_diff_mask_outer(i);
        }
        for (int i = 0; i < ispec.loopBound(COUNT); i++) {
            int a = int_in1[i], b = int_in2[i];
            int minAB = m1arr[i] ? VectorMath.minUnsigned(a, b) : a;
            int maxAB = m1arr[i] ? VectorMath.maxUnsigned(a, b) : a;
            int expected = m2arr[i] ? VectorMath.maxUnsigned(minAB, maxAB) : minAB;
            Verify.checkEQ(int_out[i], expected);
        }
    }

    // Predicated: umax(umin(a,b,m1), umax(a,b,m2), m3) => NO transform
    @Test
    @IR(counts = {IRNode.UMIN_VI, " 1 ", IRNode.UMAX_VI, " 2 "}, applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void umax_masked_all_diff_mask(int index) {
        IntVector vec1 = IntVector.fromArray(ispec, int_in1, index);
        IntVector vec2 = IntVector.fromArray(ispec, int_in2, index);
        VectorMask<Integer> mask1 = VectorMask.fromArray(ispec, m1arr, index);
        VectorMask<Integer> mask2 = VectorMask.fromArray(ispec, m2arr, index);
        VectorMask<Integer> mask3 = VectorMask.fromArray(ispec, m3arr, index);
        vec1.lanewise(VectorOperators.UMIN, vec2, mask1)
            .lanewise(VectorOperators.UMAX,
                      vec1.lanewise(VectorOperators.UMAX, vec2, mask2), mask3)
            .intoArray(int_out, index);
    }

    @Run(test = "umax_masked_all_diff_mask")
    public void umax_masked_all_diff_mask_runner() {
        for (int i = 0; i < ispec.loopBound(COUNT); i += ispec.length()) {
            umax_masked_all_diff_mask(i);
        }
        for (int i = 0; i < ispec.loopBound(COUNT); i++) {
            int a = int_in1[i], b = int_in2[i];
            int minAB = m1arr[i] ? VectorMath.minUnsigned(a, b) : a;
            int maxAB = m2arr[i] ? VectorMath.maxUnsigned(a, b) : a;
            int expected = m3arr[i] ? VectorMath.maxUnsigned(minAB, maxAB) : minAB;
            Verify.checkEQ(int_out[i], expected);
        }
    }

    // Predicated Byte: umin(umin(a,b,m), umax(a,b,m), m) => umin(a,b,m)
    @Test
    @IR(counts = {IRNode.UMIN_VB, " 1 ", IRNode.UMAX_VB, " 0 "}, applyIfCPUFeatureOr = {"avx512bw", "true", "sve", "true"})
    public void umin_byte_masked_same_mask(int index) {
        ByteVector vec1 = ByteVector.fromArray(bspec, byte_in1, index);
        ByteVector vec2 = ByteVector.fromArray(bspec, byte_in2, index);
        VectorMask<Byte> m = VectorMask.fromArray(bspec, m1arr, index);
        vec1.lanewise(VectorOperators.UMIN, vec2, m)
            .lanewise(VectorOperators.UMIN,
                      vec1.lanewise(VectorOperators.UMAX, vec2, m), m)
            .intoArray(byte_out, index);
    }

    @Run(test = "umin_byte_masked_same_mask")
    public void umin_byte_masked_same_mask_runner() {
        for (int i = 0; i < bspec.loopBound(COUNT); i += bspec.length()) {
            umin_byte_masked_same_mask(i);
        }
        for (int i = 0; i < bspec.loopBound(COUNT); i++) {
            byte a = byte_in1[i], b = byte_in2[i];
            byte minAB = (byte)(m1arr[i] ? VectorMath.minUnsigned(a, b) : a);
            byte maxAB = (byte)(m1arr[i] ? VectorMath.maxUnsigned(a, b) : a);
            byte expected = (byte)(m1arr[i] ? VectorMath.minUnsigned(minAB, maxAB) : minAB);
            Verify.checkEQ(byte_out[i], expected);
        }
    }

    // Predicated Byte: umin(umin(a,b,m), umax(b,a,m), m) => umin(a,b,m)
    @Test
    @IR(counts = {IRNode.UMIN_VB, " 1 ", IRNode.UMAX_VB, " 0 "}, applyIfCPUFeatureOr = {"avx512bw", "true", "sve", "true"})
    public void umin_byte_masked_flipped_inputs(int index) {
        ByteVector vec1 = ByteVector.fromArray(bspec, byte_in1, index);
        ByteVector vec2 = ByteVector.fromArray(bspec, byte_in2, index);
        VectorMask<Byte> m = VectorMask.fromArray(bspec, m1arr, index);
        vec1.lanewise(VectorOperators.UMIN, vec2, m)
            .lanewise(VectorOperators.UMIN,
                      vec2.lanewise(VectorOperators.UMAX, vec1, m), m)
            .intoArray(byte_out, index);
    }

    @Run(test = "umin_byte_masked_flipped_inputs")
    public void umin_byte_masked_flipped_inputs_runner() {
        for (int i = 0; i < bspec.loopBound(COUNT); i += bspec.length()) {
            umin_byte_masked_flipped_inputs(i);
        }
        for (int i = 0; i < bspec.loopBound(COUNT); i++) {
            byte a = byte_in1[i], b = byte_in2[i];
            byte minAB = (byte)(m1arr[i] ? VectorMath.minUnsigned(a, b) : a);
            byte maxBA = (byte)(m1arr[i] ? VectorMath.maxUnsigned(b, a) : b);
            byte expected = (byte)(m1arr[i] ? VectorMath.minUnsigned(minAB, maxBA) : minAB);
            Verify.checkEQ(byte_out[i], expected);
        }
    }

    // Predicated Byte: umin(umin(a,b,m1), umax(a,b,m2), m1) => NO transform
    @Test
    @IR(counts = {IRNode.UMIN_VB, " 2 ", IRNode.UMAX_VB, " 1 "}, applyIfCPUFeatureOr = {"avx512bw", "true", "sve", "true"})
    public void umin_byte_masked_diff_mask_minmax(int index) {
        ByteVector vec1 = ByteVector.fromArray(bspec, byte_in1, index);
        ByteVector vec2 = ByteVector.fromArray(bspec, byte_in2, index);
        VectorMask<Byte> mask1 = VectorMask.fromArray(bspec, m1arr, index);
        VectorMask<Byte> mask2 = VectorMask.fromArray(bspec, m2arr, index);
        vec1.lanewise(VectorOperators.UMIN, vec2, mask1)
            .lanewise(VectorOperators.UMIN,
                      vec1.lanewise(VectorOperators.UMAX, vec2, mask2), mask1)
            .intoArray(byte_out, index);
    }

    @Run(test = "umin_byte_masked_diff_mask_minmax")
    public void umin_byte_masked_diff_mask_minmax_runner() {
        for (int i = 0; i < bspec.loopBound(COUNT); i += bspec.length()) {
            umin_byte_masked_diff_mask_minmax(i);
        }
        for (int i = 0; i < bspec.loopBound(COUNT); i++) {
            byte a = byte_in1[i], b = byte_in2[i];
            byte minAB = (byte)(m1arr[i] ? VectorMath.minUnsigned(a, b) : a);
            byte maxAB = (byte)(m2arr[i] ? VectorMath.maxUnsigned(a, b) : a);
            byte expected = (byte)(m1arr[i] ? VectorMath.minUnsigned(minAB, maxAB) : minAB);
            Verify.checkEQ(byte_out[i], expected);
        }
    }

    // Predicated Byte: umin(umin(a,b,m2), umax(a,b,m1), m1) => NO transform
    @Test
    @IR(counts = {IRNode.UMIN_VB, " 2 ", IRNode.UMAX_VB, " 1 "}, applyIfCPUFeatureOr = {"avx512bw", "true", "sve", "true"})
    public void umin_byte_masked_diff_mask_minmax_swap(int index) {
        ByteVector vec1 = ByteVector.fromArray(bspec, byte_in1, index);
        ByteVector vec2 = ByteVector.fromArray(bspec, byte_in2, index);
        VectorMask<Byte> mask1 = VectorMask.fromArray(bspec, m1arr, index);
        VectorMask<Byte> mask2 = VectorMask.fromArray(bspec, m2arr, index);
        vec1.lanewise(VectorOperators.UMIN, vec2, mask2)
            .lanewise(VectorOperators.UMIN,
                      vec1.lanewise(VectorOperators.UMAX, vec2, mask1), mask1)
            .intoArray(byte_out, index);
    }

    @Run(test = "umin_byte_masked_diff_mask_minmax_swap")
    public void umin_byte_masked_diff_mask_minmax_swap_runner() {
        for (int i = 0; i < bspec.loopBound(COUNT); i += bspec.length()) {
            umin_byte_masked_diff_mask_minmax_swap(i);
        }
        for (int i = 0; i < bspec.loopBound(COUNT); i++) {
            byte a = byte_in1[i], b = byte_in2[i];
            byte minAB = (byte)(m2arr[i] ? VectorMath.minUnsigned(a, b) : a);
            byte maxAB = (byte)(m1arr[i] ? VectorMath.maxUnsigned(a, b) : a);
            byte expected = (byte)(m1arr[i] ? VectorMath.minUnsigned(minAB, maxAB) : minAB);
            Verify.checkEQ(byte_out[i], expected);
        }
    }

    // Predicated Byte: umin(umin(a,b,m1), umax(a,b,m1), m2) => NO transform
    @Test
    @IR(counts = {IRNode.UMIN_VB, " 2 ", IRNode.UMAX_VB, " 1 "}, applyIfCPUFeatureOr = {"avx512bw", "true", "sve", "true"})
    public void umin_byte_masked_diff_mask_outer(int index) {
        ByteVector vec1 = ByteVector.fromArray(bspec, byte_in1, index);
        ByteVector vec2 = ByteVector.fromArray(bspec, byte_in2, index);
        VectorMask<Byte> mask1 = VectorMask.fromArray(bspec, m1arr, index);
        VectorMask<Byte> mask2 = VectorMask.fromArray(bspec, m2arr, index);
        vec1.lanewise(VectorOperators.UMIN, vec2, mask1)
            .lanewise(VectorOperators.UMIN,
                      vec1.lanewise(VectorOperators.UMAX, vec2, mask1), mask2)
            .intoArray(byte_out, index);
    }

    @Run(test = "umin_byte_masked_diff_mask_outer")
    public void umin_byte_masked_diff_mask_outer_runner() {
        for (int i = 0; i < bspec.loopBound(COUNT); i += bspec.length()) {
            umin_byte_masked_diff_mask_outer(i);
        }
        for (int i = 0; i < bspec.loopBound(COUNT); i++) {
            byte a = byte_in1[i], b = byte_in2[i];
            byte minAB = (byte)(m1arr[i] ? VectorMath.minUnsigned(a, b) : a);
            byte maxAB = (byte)(m1arr[i] ? VectorMath.maxUnsigned(a, b) : a);
            byte expected = (byte)(m2arr[i] ? VectorMath.minUnsigned(minAB, maxAB) : minAB);
            Verify.checkEQ(byte_out[i], expected);
        }
    }

    // Predicated Byte: umin(umin(a,b,m1), umax(a,b,m2), m3) => NO transform
    @Test
    @IR(counts = {IRNode.UMIN_VB, " 2 ", IRNode.UMAX_VB, " 1 "}, applyIfCPUFeatureOr = {"avx512bw", "true", "sve", "true"})
    public void umin_byte_masked_all_diff_mask(int index) {
        ByteVector vec1 = ByteVector.fromArray(bspec, byte_in1, index);
        ByteVector vec2 = ByteVector.fromArray(bspec, byte_in2, index);
        VectorMask<Byte> mask1 = VectorMask.fromArray(bspec, m1arr, index);
        VectorMask<Byte> mask2 = VectorMask.fromArray(bspec, m2arr, index);
        VectorMask<Byte> mask3 = VectorMask.fromArray(bspec, m3arr, index);
        vec1.lanewise(VectorOperators.UMIN, vec2, mask1)
            .lanewise(VectorOperators.UMIN,
                      vec1.lanewise(VectorOperators.UMAX, vec2, mask2), mask3)
            .intoArray(byte_out, index);
    }

    @Run(test = "umin_byte_masked_all_diff_mask")
    public void umin_byte_masked_all_diff_mask_runner() {
        for (int i = 0; i < bspec.loopBound(COUNT); i += bspec.length()) {
            umin_byte_masked_all_diff_mask(i);
        }
        for (int i = 0; i < bspec.loopBound(COUNT); i++) {
            byte a = byte_in1[i], b = byte_in2[i];
            byte minAB = (byte)(m1arr[i] ? VectorMath.minUnsigned(a, b) : a);
            byte maxAB = (byte)(m2arr[i] ? VectorMath.maxUnsigned(a, b) : a);
            byte expected = (byte)(m3arr[i] ? VectorMath.minUnsigned(minAB, maxAB) : minAB);
            Verify.checkEQ(byte_out[i], expected);
        }
    }

    // Predicated Byte: umax(umin(a,b,m), umax(a,b,m), m) => umax(a,b,m)
    @Test
    @IR(counts = {IRNode.UMIN_VB, " 0 ", IRNode.UMAX_VB, " 1 "}, applyIfCPUFeatureOr = {"avx512bw", "true", "sve", "true"})
    public void umax_byte_masked_same_mask(int index) {
        ByteVector vec1 = ByteVector.fromArray(bspec, byte_in1, index);
        ByteVector vec2 = ByteVector.fromArray(bspec, byte_in2, index);
        VectorMask<Byte> m = VectorMask.fromArray(bspec, m1arr, index);
        vec1.lanewise(VectorOperators.UMIN, vec2, m)
            .lanewise(VectorOperators.UMAX,
                      vec1.lanewise(VectorOperators.UMAX, vec2, m), m)
            .intoArray(byte_out, index);
    }

    @Run(test = "umax_byte_masked_same_mask")
    public void umax_byte_masked_same_mask_runner() {
        for (int i = 0; i < bspec.loopBound(COUNT); i += bspec.length()) {
            umax_byte_masked_same_mask(i);
        }
        for (int i = 0; i < bspec.loopBound(COUNT); i++) {
            byte a = byte_in1[i], b = byte_in2[i];
            byte minAB = (byte)(m1arr[i] ? VectorMath.minUnsigned(a, b) : a);
            byte maxAB = (byte)(m1arr[i] ? VectorMath.maxUnsigned(a, b) : a);
            byte expected = (byte)(m1arr[i] ? VectorMath.maxUnsigned(minAB, maxAB) : minAB);
            Verify.checkEQ(byte_out[i], expected);
        }
    }

    // Predicated Byte: umax(umin(a,b,m), umax(b,a,m), m) => umax(a,b,m)
    @Test
    @IR(counts = {IRNode.UMIN_VB, " 0 ", IRNode.UMAX_VB, " 1 "}, applyIfCPUFeatureOr = {"avx512bw", "true", "sve", "true"})
    public void umax_byte_masked_flipped_inputs(int index) {
        ByteVector vec1 = ByteVector.fromArray(bspec, byte_in1, index);
        ByteVector vec2 = ByteVector.fromArray(bspec, byte_in2, index);
        VectorMask<Byte> m = VectorMask.fromArray(bspec, m1arr, index);
        vec1.lanewise(VectorOperators.UMIN, vec2, m)
            .lanewise(VectorOperators.UMAX,
                      vec2.lanewise(VectorOperators.UMAX, vec1, m), m)
            .intoArray(byte_out, index);
    }

    @Run(test = "umax_byte_masked_flipped_inputs")
    public void umax_byte_masked_flipped_inputs_runner() {
        for (int i = 0; i < bspec.loopBound(COUNT); i += bspec.length()) {
            umax_byte_masked_flipped_inputs(i);
        }
        for (int i = 0; i < bspec.loopBound(COUNT); i++) {
            byte a = byte_in1[i], b = byte_in2[i];
            byte minAB = (byte)(m1arr[i] ? VectorMath.minUnsigned(a, b) : a);
            byte maxBA = (byte)(m1arr[i] ? VectorMath.maxUnsigned(b, a) : b);
            byte expected = (byte)(m1arr[i] ? VectorMath.maxUnsigned(minAB, maxBA) : minAB);
            Verify.checkEQ(byte_out[i], expected);
        }
    }

    // Predicated Byte: umax(umin(a,b,m1), umax(a,b,m2), m1) => NO transform
    @Test
    @IR(counts = {IRNode.UMIN_VB, " 1 ", IRNode.UMAX_VB, " 2 "}, applyIfCPUFeatureOr = {"avx512bw", "true", "sve", "true"})
    public void umax_byte_masked_diff_mask_minmax(int index) {
        ByteVector vec1 = ByteVector.fromArray(bspec, byte_in1, index);
        ByteVector vec2 = ByteVector.fromArray(bspec, byte_in2, index);
        VectorMask<Byte> mask1 = VectorMask.fromArray(bspec, m1arr, index);
        VectorMask<Byte> mask2 = VectorMask.fromArray(bspec, m2arr, index);
        vec1.lanewise(VectorOperators.UMIN, vec2, mask1)
            .lanewise(VectorOperators.UMAX,
                      vec1.lanewise(VectorOperators.UMAX, vec2, mask2), mask1)
            .intoArray(byte_out, index);
    }

    @Run(test = "umax_byte_masked_diff_mask_minmax")
    public void umax_byte_masked_diff_mask_minmax_runner() {
        for (int i = 0; i < bspec.loopBound(COUNT); i += bspec.length()) {
            umax_byte_masked_diff_mask_minmax(i);
        }
        for (int i = 0; i < bspec.loopBound(COUNT); i++) {
            byte a = byte_in1[i], b = byte_in2[i];
            byte minAB = (byte)(m1arr[i] ? VectorMath.minUnsigned(a, b) : a);
            byte maxAB = (byte)(m2arr[i] ? VectorMath.maxUnsigned(a, b) : a);
            byte expected = (byte)(m1arr[i] ? VectorMath.maxUnsigned(minAB, maxAB) : minAB);
            Verify.checkEQ(byte_out[i], expected);
        }
    }

    // Predicated Byte: umax(umin(a,b,m2), umax(a,b,m1), m1) => NO transform
    @Test
    @IR(counts = {IRNode.UMIN_VB, " 1 ", IRNode.UMAX_VB, " 2 "}, applyIfCPUFeatureOr = {"avx512bw", "true", "sve", "true"})
    public void umax_byte_masked_diff_mask_minmax_swap(int index) {
        ByteVector vec1 = ByteVector.fromArray(bspec, byte_in1, index);
        ByteVector vec2 = ByteVector.fromArray(bspec, byte_in2, index);
        VectorMask<Byte> mask1 = VectorMask.fromArray(bspec, m1arr, index);
        VectorMask<Byte> mask2 = VectorMask.fromArray(bspec, m2arr, index);
        vec1.lanewise(VectorOperators.UMIN, vec2, mask2)
            .lanewise(VectorOperators.UMAX,
                      vec1.lanewise(VectorOperators.UMAX, vec2, mask1), mask1)
            .intoArray(byte_out, index);
    }

    @Run(test = "umax_byte_masked_diff_mask_minmax_swap")
    public void umax_byte_masked_diff_mask_minmax_swap_runner() {
        for (int i = 0; i < bspec.loopBound(COUNT); i += bspec.length()) {
            umax_byte_masked_diff_mask_minmax_swap(i);
        }
        for (int i = 0; i < bspec.loopBound(COUNT); i++) {
            byte a = byte_in1[i], b = byte_in2[i];
            byte minAB = (byte)(m2arr[i] ? VectorMath.minUnsigned(a, b) : a);
            byte maxAB = (byte)(m1arr[i] ? VectorMath.maxUnsigned(a, b) : a);
            byte expected = (byte)(m1arr[i] ? VectorMath.maxUnsigned(minAB, maxAB) : minAB);
            Verify.checkEQ(byte_out[i], expected);
        }
    }

    // Predicated Byte: umax(umin(a,b,m1), umax(a,b,m1), m2) => NO transform
    @Test
    @IR(counts = {IRNode.UMIN_VB, " 1 ", IRNode.UMAX_VB, " 2 "}, applyIfCPUFeatureOr = {"avx512bw", "true", "sve", "true"})
    public void umax_byte_masked_diff_mask_outer(int index) {
        ByteVector vec1 = ByteVector.fromArray(bspec, byte_in1, index);
        ByteVector vec2 = ByteVector.fromArray(bspec, byte_in2, index);
        VectorMask<Byte> mask1 = VectorMask.fromArray(bspec, m1arr, index);
        VectorMask<Byte> mask2 = VectorMask.fromArray(bspec, m2arr, index);
        vec1.lanewise(VectorOperators.UMIN, vec2, mask1)
            .lanewise(VectorOperators.UMAX,
                      vec1.lanewise(VectorOperators.UMAX, vec2, mask1), mask2)
            .intoArray(byte_out, index);
    }

    @Run(test = "umax_byte_masked_diff_mask_outer")
    public void umax_byte_masked_diff_mask_outer_runner() {
        for (int i = 0; i < bspec.loopBound(COUNT); i += bspec.length()) {
            umax_byte_masked_diff_mask_outer(i);
        }
        for (int i = 0; i < bspec.loopBound(COUNT); i++) {
            byte a = byte_in1[i], b = byte_in2[i];
            byte minAB = (byte)(m1arr[i] ? VectorMath.minUnsigned(a, b) : a);
            byte maxAB = (byte)(m1arr[i] ? VectorMath.maxUnsigned(a, b) : a);
            byte expected = (byte)(m2arr[i] ? VectorMath.maxUnsigned(minAB, maxAB) : minAB);
            Verify.checkEQ(byte_out[i], expected);
        }
    }

    // Predicated Byte: umax(umin(a,b,m1), umax(a,b,m2), m3) => NO transform
    @Test
    @IR(counts = {IRNode.UMIN_VB, " 1 ", IRNode.UMAX_VB, " 2 "}, applyIfCPUFeatureOr = {"avx512bw", "true", "sve", "true"})
    public void umax_byte_masked_all_diff_mask(int index) {
        ByteVector vec1 = ByteVector.fromArray(bspec, byte_in1, index);
        ByteVector vec2 = ByteVector.fromArray(bspec, byte_in2, index);
        VectorMask<Byte> mask1 = VectorMask.fromArray(bspec, m1arr, index);
        VectorMask<Byte> mask2 = VectorMask.fromArray(bspec, m2arr, index);
        VectorMask<Byte> mask3 = VectorMask.fromArray(bspec, m3arr, index);
        vec1.lanewise(VectorOperators.UMIN, vec2, mask1)
            .lanewise(VectorOperators.UMAX,
                      vec1.lanewise(VectorOperators.UMAX, vec2, mask2), mask3)
            .intoArray(byte_out, index);
    }

    @Run(test = "umax_byte_masked_all_diff_mask")
    public void umax_byte_masked_all_diff_mask_runner() {
        for (int i = 0; i < bspec.loopBound(COUNT); i += bspec.length()) {
            umax_byte_masked_all_diff_mask(i);
        }
        for (int i = 0; i < bspec.loopBound(COUNT); i++) {
            byte a = byte_in1[i], b = byte_in2[i];
            byte minAB = (byte)(m1arr[i] ? VectorMath.minUnsigned(a, b) : a);
            byte maxAB = (byte)(m2arr[i] ? VectorMath.maxUnsigned(a, b) : a);
            byte expected = (byte)(m3arr[i] ? VectorMath.maxUnsigned(minAB, maxAB) : minAB);
            Verify.checkEQ(byte_out[i], expected);
        }
    }

    // Predicated Short: umin(umin(a,b,m), umax(a,b,m), m) => umin(a,b,m)
    @Test
    @IR(counts = {IRNode.UMIN_VS, " 1 ", IRNode.UMAX_VS, " 0 "}, applyIfCPUFeatureOr = {"avx512bw", "true", "sve", "true"})
    public void umin_short_masked_same_mask(int index) {
        ShortVector vec1 = ShortVector.fromArray(sspec, short_in1, index);
        ShortVector vec2 = ShortVector.fromArray(sspec, short_in2, index);
        VectorMask<Short> m = VectorMask.fromArray(sspec, m1arr, index);
        vec1.lanewise(VectorOperators.UMIN, vec2, m)
            .lanewise(VectorOperators.UMIN,
                      vec1.lanewise(VectorOperators.UMAX, vec2, m), m)
            .intoArray(short_out, index);
    }

    @Run(test = "umin_short_masked_same_mask")
    public void umin_short_masked_same_mask_runner() {
        for (int i = 0; i < sspec.loopBound(COUNT); i += sspec.length()) {
            umin_short_masked_same_mask(i);
        }
        for (int i = 0; i < sspec.loopBound(COUNT); i++) {
            short a = short_in1[i], b = short_in2[i];
            short minAB = (short)(m1arr[i] ? VectorMath.minUnsigned(a, b) : a);
            short maxAB = (short)(m1arr[i] ? VectorMath.maxUnsigned(a, b) : a);
            short expected = (short)(m1arr[i] ? VectorMath.minUnsigned(minAB, maxAB) : minAB);
            Verify.checkEQ(short_out[i], expected);
        }
    }

    // Predicated Short: umin(umin(a,b,m), umax(b,a,m), m) => umin(a,b,m)
    @Test
    @IR(counts = {IRNode.UMIN_VS, " 1 ", IRNode.UMAX_VS, " 0 "}, applyIfCPUFeatureOr = {"avx512bw", "true", "sve", "true"})
    public void umin_short_masked_flipped_inputs(int index) {
        ShortVector vec1 = ShortVector.fromArray(sspec, short_in1, index);
        ShortVector vec2 = ShortVector.fromArray(sspec, short_in2, index);
        VectorMask<Short> m = VectorMask.fromArray(sspec, m1arr, index);
        vec1.lanewise(VectorOperators.UMIN, vec2, m)
            .lanewise(VectorOperators.UMIN,
                      vec2.lanewise(VectorOperators.UMAX, vec1, m), m)
            .intoArray(short_out, index);
    }

    @Run(test = "umin_short_masked_flipped_inputs")
    public void umin_short_masked_flipped_inputs_runner() {
        for (int i = 0; i < sspec.loopBound(COUNT); i += sspec.length()) {
            umin_short_masked_flipped_inputs(i);
        }
        for (int i = 0; i < sspec.loopBound(COUNT); i++) {
            short a = short_in1[i], b = short_in2[i];
            short minAB = (short)(m1arr[i] ? VectorMath.minUnsigned(a, b) : a);
            short maxBA = (short)(m1arr[i] ? VectorMath.maxUnsigned(b, a) : b);
            short expected = (short)(m1arr[i] ? VectorMath.minUnsigned(minAB, maxBA) : minAB);
            Verify.checkEQ(short_out[i], expected);
        }
    }

    // Predicated Short: umin(umin(a,b,m1), umax(a,b,m2), m1) => NO transform
    @Test
    @IR(counts = {IRNode.UMIN_VS, " 2 ", IRNode.UMAX_VS, " 1 "}, applyIfCPUFeatureOr = {"avx512bw", "true", "sve", "true"})
    public void umin_short_masked_diff_mask_minmax(int index) {
        ShortVector vec1 = ShortVector.fromArray(sspec, short_in1, index);
        ShortVector vec2 = ShortVector.fromArray(sspec, short_in2, index);
        VectorMask<Short> mask1 = VectorMask.fromArray(sspec, m1arr, index);
        VectorMask<Short> mask2 = VectorMask.fromArray(sspec, m2arr, index);
        vec1.lanewise(VectorOperators.UMIN, vec2, mask1)
            .lanewise(VectorOperators.UMIN,
                      vec1.lanewise(VectorOperators.UMAX, vec2, mask2), mask1)
            .intoArray(short_out, index);
    }

    @Run(test = "umin_short_masked_diff_mask_minmax")
    public void umin_short_masked_diff_mask_minmax_runner() {
        for (int i = 0; i < sspec.loopBound(COUNT); i += sspec.length()) {
            umin_short_masked_diff_mask_minmax(i);
        }
        for (int i = 0; i < sspec.loopBound(COUNT); i++) {
            short a = short_in1[i], b = short_in2[i];
            short minAB = (short)(m1arr[i] ? VectorMath.minUnsigned(a, b) : a);
            short maxAB = (short)(m2arr[i] ? VectorMath.maxUnsigned(a, b) : a);
            short expected = (short)(m1arr[i] ? VectorMath.minUnsigned(minAB, maxAB) : minAB);
            Verify.checkEQ(short_out[i], expected);
        }
    }

    // Predicated Short: umin(umin(a,b,m2), umax(a,b,m1), m1) => NO transform
    @Test
    @IR(counts = {IRNode.UMIN_VS, " 2 ", IRNode.UMAX_VS, " 1 "}, applyIfCPUFeatureOr = {"avx512bw", "true", "sve", "true"})
    public void umin_short_masked_diff_mask_minmax_swap(int index) {
        ShortVector vec1 = ShortVector.fromArray(sspec, short_in1, index);
        ShortVector vec2 = ShortVector.fromArray(sspec, short_in2, index);
        VectorMask<Short> mask1 = VectorMask.fromArray(sspec, m1arr, index);
        VectorMask<Short> mask2 = VectorMask.fromArray(sspec, m2arr, index);
        vec1.lanewise(VectorOperators.UMIN, vec2, mask2)
            .lanewise(VectorOperators.UMIN,
                      vec1.lanewise(VectorOperators.UMAX, vec2, mask1), mask1)
            .intoArray(short_out, index);
    }

    @Run(test = "umin_short_masked_diff_mask_minmax_swap")
    public void umin_short_masked_diff_mask_minmax_swap_runner() {
        for (int i = 0; i < sspec.loopBound(COUNT); i += sspec.length()) {
            umin_short_masked_diff_mask_minmax_swap(i);
        }
        for (int i = 0; i < sspec.loopBound(COUNT); i++) {
            short a = short_in1[i], b = short_in2[i];
            short minAB = (short)(m2arr[i] ? VectorMath.minUnsigned(a, b) : a);
            short maxAB = (short)(m1arr[i] ? VectorMath.maxUnsigned(a, b) : a);
            short expected = (short)(m1arr[i] ? VectorMath.minUnsigned(minAB, maxAB) : minAB);
            Verify.checkEQ(short_out[i], expected);
        }
    }

    // Predicated Short: umin(umin(a,b,m1), umax(a,b,m1), m2) => NO transform
    @Test
    @IR(counts = {IRNode.UMIN_VS, " 2 ", IRNode.UMAX_VS, " 1 "}, applyIfCPUFeatureOr = {"avx512bw", "true", "sve", "true"})
    public void umin_short_masked_diff_mask_outer(int index) {
        ShortVector vec1 = ShortVector.fromArray(sspec, short_in1, index);
        ShortVector vec2 = ShortVector.fromArray(sspec, short_in2, index);
        VectorMask<Short> mask1 = VectorMask.fromArray(sspec, m1arr, index);
        VectorMask<Short> mask2 = VectorMask.fromArray(sspec, m2arr, index);
        vec1.lanewise(VectorOperators.UMIN, vec2, mask1)
            .lanewise(VectorOperators.UMIN,
                      vec1.lanewise(VectorOperators.UMAX, vec2, mask1), mask2)
            .intoArray(short_out, index);
    }

    @Run(test = "umin_short_masked_diff_mask_outer")
    public void umin_short_masked_diff_mask_outer_runner() {
        for (int i = 0; i < sspec.loopBound(COUNT); i += sspec.length()) {
            umin_short_masked_diff_mask_outer(i);
        }
        for (int i = 0; i < sspec.loopBound(COUNT); i++) {
            short a = short_in1[i], b = short_in2[i];
            short minAB = (short)(m1arr[i] ? VectorMath.minUnsigned(a, b) : a);
            short maxAB = (short)(m1arr[i] ? VectorMath.maxUnsigned(a, b) : a);
            short expected = (short)(m2arr[i] ? VectorMath.minUnsigned(minAB, maxAB) : minAB);
            Verify.checkEQ(short_out[i], expected);
        }
    }

    // Predicated Short: umin(umin(a,b,m1), umax(a,b,m2), m3) => NO transform
    @Test
    @IR(counts = {IRNode.UMIN_VS, " 2 ", IRNode.UMAX_VS, " 1 "}, applyIfCPUFeatureOr = {"avx512bw", "true", "sve", "true"})
    public void umin_short_masked_all_diff_mask(int index) {
        ShortVector vec1 = ShortVector.fromArray(sspec, short_in1, index);
        ShortVector vec2 = ShortVector.fromArray(sspec, short_in2, index);
        VectorMask<Short> mask1 = VectorMask.fromArray(sspec, m1arr, index);
        VectorMask<Short> mask2 = VectorMask.fromArray(sspec, m2arr, index);
        VectorMask<Short> mask3 = VectorMask.fromArray(sspec, m3arr, index);
        vec1.lanewise(VectorOperators.UMIN, vec2, mask1)
            .lanewise(VectorOperators.UMIN,
                      vec1.lanewise(VectorOperators.UMAX, vec2, mask2), mask3)
            .intoArray(short_out, index);
    }

    @Run(test = "umin_short_masked_all_diff_mask")
    public void umin_short_masked_all_diff_mask_runner() {
        for (int i = 0; i < sspec.loopBound(COUNT); i += sspec.length()) {
            umin_short_masked_all_diff_mask(i);
        }
        for (int i = 0; i < sspec.loopBound(COUNT); i++) {
            short a = short_in1[i], b = short_in2[i];
            short minAB = (short)(m1arr[i] ? VectorMath.minUnsigned(a, b) : a);
            short maxAB = (short)(m2arr[i] ? VectorMath.maxUnsigned(a, b) : a);
            short expected = (short)(m3arr[i] ? VectorMath.minUnsigned(minAB, maxAB) : minAB);
            Verify.checkEQ(short_out[i], expected);
        }
    }

    // Predicated Short: umax(umin(a,b,m), umax(a,b,m), m) => umax(a,b,m)
    @Test
    @IR(counts = {IRNode.UMIN_VS, " 0 ", IRNode.UMAX_VS, " 1 "}, applyIfCPUFeatureOr = {"avx512bw", "true", "sve", "true"})
    public void umax_short_masked_same_mask(int index) {
        ShortVector vec1 = ShortVector.fromArray(sspec, short_in1, index);
        ShortVector vec2 = ShortVector.fromArray(sspec, short_in2, index);
        VectorMask<Short> m = VectorMask.fromArray(sspec, m1arr, index);
        vec1.lanewise(VectorOperators.UMIN, vec2, m)
            .lanewise(VectorOperators.UMAX,
                      vec1.lanewise(VectorOperators.UMAX, vec2, m), m)
            .intoArray(short_out, index);
    }

    @Run(test = "umax_short_masked_same_mask")
    public void umax_short_masked_same_mask_runner() {
        for (int i = 0; i < sspec.loopBound(COUNT); i += sspec.length()) {
            umax_short_masked_same_mask(i);
        }
        for (int i = 0; i < sspec.loopBound(COUNT); i++) {
            short a = short_in1[i], b = short_in2[i];
            short minAB = (short)(m1arr[i] ? VectorMath.minUnsigned(a, b) : a);
            short maxAB = (short)(m1arr[i] ? VectorMath.maxUnsigned(a, b) : a);
            short expected = (short)(m1arr[i] ? VectorMath.maxUnsigned(minAB, maxAB) : minAB);
            Verify.checkEQ(short_out[i], expected);
        }
    }

    // Predicated Short: umax(umin(a,b,m), umax(b,a,m), m) => umax(a,b,m)
    @Test
    @IR(counts = {IRNode.UMIN_VS, " 0 ", IRNode.UMAX_VS, " 1 "}, applyIfCPUFeatureOr = {"avx512bw", "true", "sve", "true"})
    public void umax_short_masked_flipped_inputs(int index) {
        ShortVector vec1 = ShortVector.fromArray(sspec, short_in1, index);
        ShortVector vec2 = ShortVector.fromArray(sspec, short_in2, index);
        VectorMask<Short> m = VectorMask.fromArray(sspec, m1arr, index);
        vec1.lanewise(VectorOperators.UMIN, vec2, m)
            .lanewise(VectorOperators.UMAX,
                      vec2.lanewise(VectorOperators.UMAX, vec1, m), m)
            .intoArray(short_out, index);
    }

    @Run(test = "umax_short_masked_flipped_inputs")
    public void umax_short_masked_flipped_inputs_runner() {
        for (int i = 0; i < sspec.loopBound(COUNT); i += sspec.length()) {
            umax_short_masked_flipped_inputs(i);
        }
        for (int i = 0; i < sspec.loopBound(COUNT); i++) {
            short a = short_in1[i], b = short_in2[i];
            short minAB = (short)(m1arr[i] ? VectorMath.minUnsigned(a, b) : a);
            short maxBA = (short)(m1arr[i] ? VectorMath.maxUnsigned(b, a) : b);
            short expected = (short)(m1arr[i] ? VectorMath.maxUnsigned(minAB, maxBA) : minAB);
            Verify.checkEQ(short_out[i], expected);
        }
    }

    // Predicated Short: umax(umin(a,b,m1), umax(a,b,m2), m1) => NO transform
    @Test
    @IR(counts = {IRNode.UMIN_VS, " 1 ", IRNode.UMAX_VS, " 2 "}, applyIfCPUFeatureOr = {"avx512bw", "true", "sve", "true"})
    public void umax_short_masked_diff_mask_minmax(int index) {
        ShortVector vec1 = ShortVector.fromArray(sspec, short_in1, index);
        ShortVector vec2 = ShortVector.fromArray(sspec, short_in2, index);
        VectorMask<Short> mask1 = VectorMask.fromArray(sspec, m1arr, index);
        VectorMask<Short> mask2 = VectorMask.fromArray(sspec, m2arr, index);
        vec1.lanewise(VectorOperators.UMIN, vec2, mask1)
            .lanewise(VectorOperators.UMAX,
                      vec1.lanewise(VectorOperators.UMAX, vec2, mask2), mask1)
            .intoArray(short_out, index);
    }

    @Run(test = "umax_short_masked_diff_mask_minmax")
    public void umax_short_masked_diff_mask_minmax_runner() {
        for (int i = 0; i < sspec.loopBound(COUNT); i += sspec.length()) {
            umax_short_masked_diff_mask_minmax(i);
        }
        for (int i = 0; i < sspec.loopBound(COUNT); i++) {
            short a = short_in1[i], b = short_in2[i];
            short minAB = (short)(m1arr[i] ? VectorMath.minUnsigned(a, b) : a);
            short maxAB = (short)(m2arr[i] ? VectorMath.maxUnsigned(a, b) : a);
            short expected = (short)(m1arr[i] ? VectorMath.maxUnsigned(minAB, maxAB) : minAB);
            Verify.checkEQ(short_out[i], expected);
        }
    }

    // Predicated Short: umax(umin(a,b,m2), umax(a,b,m1), m1) => NO transform
    @Test
    @IR(counts = {IRNode.UMIN_VS, " 1 ", IRNode.UMAX_VS, " 2 "}, applyIfCPUFeatureOr = {"avx512bw", "true", "sve", "true"})
    public void umax_short_masked_diff_mask_minmax_swap(int index) {
        ShortVector vec1 = ShortVector.fromArray(sspec, short_in1, index);
        ShortVector vec2 = ShortVector.fromArray(sspec, short_in2, index);
        VectorMask<Short> mask1 = VectorMask.fromArray(sspec, m1arr, index);
        VectorMask<Short> mask2 = VectorMask.fromArray(sspec, m2arr, index);
        vec1.lanewise(VectorOperators.UMIN, vec2, mask2)
            .lanewise(VectorOperators.UMAX,
                      vec1.lanewise(VectorOperators.UMAX, vec2, mask1), mask1)
            .intoArray(short_out, index);
    }

    @Run(test = "umax_short_masked_diff_mask_minmax_swap")
    public void umax_short_masked_diff_mask_minmax_swap_runner() {
        for (int i = 0; i < sspec.loopBound(COUNT); i += sspec.length()) {
            umax_short_masked_diff_mask_minmax_swap(i);
        }
        for (int i = 0; i < sspec.loopBound(COUNT); i++) {
            short a = short_in1[i], b = short_in2[i];
            short minAB = (short)(m2arr[i] ? VectorMath.minUnsigned(a, b) : a);
            short maxAB = (short)(m1arr[i] ? VectorMath.maxUnsigned(a, b) : a);
            short expected = (short)(m1arr[i] ? VectorMath.maxUnsigned(minAB, maxAB) : minAB);
            Verify.checkEQ(short_out[i], expected);
        }
    }

    // Predicated Short: umax(umin(a,b,m1), umax(a,b,m1), m2) => NO transform
    @Test
    @IR(counts = {IRNode.UMIN_VS, " 1 ", IRNode.UMAX_VS, " 2 "}, applyIfCPUFeatureOr = {"avx512bw", "true", "sve", "true"})
    public void umax_short_masked_diff_mask_outer(int index) {
        ShortVector vec1 = ShortVector.fromArray(sspec, short_in1, index);
        ShortVector vec2 = ShortVector.fromArray(sspec, short_in2, index);
        VectorMask<Short> mask1 = VectorMask.fromArray(sspec, m1arr, index);
        VectorMask<Short> mask2 = VectorMask.fromArray(sspec, m2arr, index);
        vec1.lanewise(VectorOperators.UMIN, vec2, mask1)
            .lanewise(VectorOperators.UMAX,
                      vec1.lanewise(VectorOperators.UMAX, vec2, mask1), mask2)
            .intoArray(short_out, index);
    }

    @Run(test = "umax_short_masked_diff_mask_outer")
    public void umax_short_masked_diff_mask_outer_runner() {
        for (int i = 0; i < sspec.loopBound(COUNT); i += sspec.length()) {
            umax_short_masked_diff_mask_outer(i);
        }
        for (int i = 0; i < sspec.loopBound(COUNT); i++) {
            short a = short_in1[i], b = short_in2[i];
            short minAB = (short)(m1arr[i] ? VectorMath.minUnsigned(a, b) : a);
            short maxAB = (short)(m1arr[i] ? VectorMath.maxUnsigned(a, b) : a);
            short expected = (short)(m2arr[i] ? VectorMath.maxUnsigned(minAB, maxAB) : minAB);
            Verify.checkEQ(short_out[i], expected);
        }
    }

    // Predicated Short: umax(umin(a,b,m1), umax(a,b,m2), m3) => NO transform
    @Test
    @IR(counts = {IRNode.UMIN_VS, " 1 ", IRNode.UMAX_VS, " 2 "}, applyIfCPUFeatureOr = {"avx512bw", "true", "sve", "true"})
    public void umax_short_masked_all_diff_mask(int index) {
        ShortVector vec1 = ShortVector.fromArray(sspec, short_in1, index);
        ShortVector vec2 = ShortVector.fromArray(sspec, short_in2, index);
        VectorMask<Short> mask1 = VectorMask.fromArray(sspec, m1arr, index);
        VectorMask<Short> mask2 = VectorMask.fromArray(sspec, m2arr, index);
        VectorMask<Short> mask3 = VectorMask.fromArray(sspec, m3arr, index);
        vec1.lanewise(VectorOperators.UMIN, vec2, mask1)
            .lanewise(VectorOperators.UMAX,
                      vec1.lanewise(VectorOperators.UMAX, vec2, mask2), mask3)
            .intoArray(short_out, index);
    }

    @Run(test = "umax_short_masked_all_diff_mask")
    public void umax_short_masked_all_diff_mask_runner() {
        for (int i = 0; i < sspec.loopBound(COUNT); i += sspec.length()) {
            umax_short_masked_all_diff_mask(i);
        }
        for (int i = 0; i < sspec.loopBound(COUNT); i++) {
            short a = short_in1[i], b = short_in2[i];
            short minAB = (short)(m1arr[i] ? VectorMath.minUnsigned(a, b) : a);
            short maxAB = (short)(m2arr[i] ? VectorMath.maxUnsigned(a, b) : a);
            short expected = (short)(m3arr[i] ? VectorMath.maxUnsigned(minAB, maxAB) : minAB);
            Verify.checkEQ(short_out[i], expected);
        }
    }

    // Predicated Long: umin(umin(a,b,m), umax(a,b,m), m) => umin(a,b,m)
    @Test
    @IR(counts = {IRNode.UMIN_VL, " 1 ", IRNode.UMAX_VL, " 0 "}, applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void umin_long_masked_same_mask(int index) {
        LongVector vec1 = LongVector.fromArray(lspec, long_in1, index);
        LongVector vec2 = LongVector.fromArray(lspec, long_in2, index);
        VectorMask<Long> m = VectorMask.fromArray(lspec, m1arr, index);
        vec1.lanewise(VectorOperators.UMIN, vec2, m)
            .lanewise(VectorOperators.UMIN,
                      vec1.lanewise(VectorOperators.UMAX, vec2, m), m)
            .intoArray(long_out, index);
    }

    @Run(test = "umin_long_masked_same_mask")
    public void umin_long_masked_same_mask_runner() {
        for (int i = 0; i < lspec.loopBound(COUNT); i += lspec.length()) {
            umin_long_masked_same_mask(i);
        }
        for (int i = 0; i < lspec.loopBound(COUNT); i++) {
            long a = long_in1[i], b = long_in2[i];
            long minAB = m1arr[i] ? VectorMath.minUnsigned(a, b) : a;
            long maxAB = m1arr[i] ? VectorMath.maxUnsigned(a, b) : a;
            long expected = m1arr[i] ? VectorMath.minUnsigned(minAB, maxAB) : minAB;
            Verify.checkEQ(long_out[i], expected);
        }
    }

    // Predicated Long: umin(umin(a,b,m), umax(b,a,m), m) => umin(a,b,m)
    @Test
    @IR(counts = {IRNode.UMIN_VL, " 1 ", IRNode.UMAX_VL, " 0 "}, applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void umin_long_masked_flipped_inputs(int index) {
        LongVector vec1 = LongVector.fromArray(lspec, long_in1, index);
        LongVector vec2 = LongVector.fromArray(lspec, long_in2, index);
        VectorMask<Long> m = VectorMask.fromArray(lspec, m1arr, index);
        vec1.lanewise(VectorOperators.UMIN, vec2, m)
            .lanewise(VectorOperators.UMIN,
                      vec2.lanewise(VectorOperators.UMAX, vec1, m), m)
            .intoArray(long_out, index);
    }

    @Run(test = "umin_long_masked_flipped_inputs")
    public void umin_long_masked_flipped_inputs_runner() {
        for (int i = 0; i < lspec.loopBound(COUNT); i += lspec.length()) {
            umin_long_masked_flipped_inputs(i);
        }
        for (int i = 0; i < lspec.loopBound(COUNT); i++) {
            long a = long_in1[i], b = long_in2[i];
            long minAB = m1arr[i] ? VectorMath.minUnsigned(a, b) : a;
            long maxBA = m1arr[i] ? VectorMath.maxUnsigned(b, a) : b;
            long expected = m1arr[i] ? VectorMath.minUnsigned(minAB, maxBA) : minAB;
            Verify.checkEQ(long_out[i], expected);
        }
    }

    // Predicated Long: umin(umin(a,b,m1), umax(a,b,m2), m1) => NO transform
    @Test
    @IR(counts = {IRNode.UMIN_VL, " 2 ", IRNode.UMAX_VL, " 1 "}, applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void umin_long_masked_diff_mask_minmax(int index) {
        LongVector vec1 = LongVector.fromArray(lspec, long_in1, index);
        LongVector vec2 = LongVector.fromArray(lspec, long_in2, index);
        VectorMask<Long> mask1 = VectorMask.fromArray(lspec, m1arr, index);
        VectorMask<Long> mask2 = VectorMask.fromArray(lspec, m2arr, index);
        vec1.lanewise(VectorOperators.UMIN, vec2, mask1)
            .lanewise(VectorOperators.UMIN,
                      vec1.lanewise(VectorOperators.UMAX, vec2, mask2), mask1)
            .intoArray(long_out, index);
    }

    @Run(test = "umin_long_masked_diff_mask_minmax")
    public void umin_long_masked_diff_mask_minmax_runner() {
        for (int i = 0; i < lspec.loopBound(COUNT); i += lspec.length()) {
            umin_long_masked_diff_mask_minmax(i);
        }
        for (int i = 0; i < lspec.loopBound(COUNT); i++) {
            long a = long_in1[i], b = long_in2[i];
            long minAB = m1arr[i] ? VectorMath.minUnsigned(a, b) : a;
            long maxAB = m2arr[i] ? VectorMath.maxUnsigned(a, b) : a;
            long expected = m1arr[i] ? VectorMath.minUnsigned(minAB, maxAB) : minAB;
            Verify.checkEQ(long_out[i], expected);
        }
    }

    // Predicated Long: umin(umin(a,b,m2), umax(a,b,m1), m1) => NO transform
    @Test
    @IR(counts = {IRNode.UMIN_VL, " 2 ", IRNode.UMAX_VL, " 1 "}, applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void umin_long_masked_diff_mask_minmax_swap(int index) {
        LongVector vec1 = LongVector.fromArray(lspec, long_in1, index);
        LongVector vec2 = LongVector.fromArray(lspec, long_in2, index);
        VectorMask<Long> mask1 = VectorMask.fromArray(lspec, m1arr, index);
        VectorMask<Long> mask2 = VectorMask.fromArray(lspec, m2arr, index);
        vec1.lanewise(VectorOperators.UMIN, vec2, mask2)
            .lanewise(VectorOperators.UMIN,
                      vec1.lanewise(VectorOperators.UMAX, vec2, mask1), mask1)
            .intoArray(long_out, index);
    }

    @Run(test = "umin_long_masked_diff_mask_minmax_swap")
    public void umin_long_masked_diff_mask_minmax_swap_runner() {
        for (int i = 0; i < lspec.loopBound(COUNT); i += lspec.length()) {
            umin_long_masked_diff_mask_minmax_swap(i);
        }
        for (int i = 0; i < lspec.loopBound(COUNT); i++) {
            long a = long_in1[i], b = long_in2[i];
            long minAB = m2arr[i] ? VectorMath.minUnsigned(a, b) : a;
            long maxAB = m1arr[i] ? VectorMath.maxUnsigned(a, b) : a;
            long expected = m1arr[i] ? VectorMath.minUnsigned(minAB, maxAB) : minAB;
            Verify.checkEQ(long_out[i], expected);
        }
    }

    // Predicated Long: umin(umin(a,b,m1), umax(a,b,m1), m2) => NO transform
    @Test
    @IR(counts = {IRNode.UMIN_VL, " 2 ", IRNode.UMAX_VL, " 1 "}, applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void umin_long_masked_diff_mask_outer(int index) {
        LongVector vec1 = LongVector.fromArray(lspec, long_in1, index);
        LongVector vec2 = LongVector.fromArray(lspec, long_in2, index);
        VectorMask<Long> mask1 = VectorMask.fromArray(lspec, m1arr, index);
        VectorMask<Long> mask2 = VectorMask.fromArray(lspec, m2arr, index);
        vec1.lanewise(VectorOperators.UMIN, vec2, mask1)
            .lanewise(VectorOperators.UMIN,
                      vec1.lanewise(VectorOperators.UMAX, vec2, mask1), mask2)
            .intoArray(long_out, index);
    }

    @Run(test = "umin_long_masked_diff_mask_outer")
    public void umin_long_masked_diff_mask_outer_runner() {
        for (int i = 0; i < lspec.loopBound(COUNT); i += lspec.length()) {
            umin_long_masked_diff_mask_outer(i);
        }
        for (int i = 0; i < lspec.loopBound(COUNT); i++) {
            long a = long_in1[i], b = long_in2[i];
            long minAB = m1arr[i] ? VectorMath.minUnsigned(a, b) : a;
            long maxAB = m1arr[i] ? VectorMath.maxUnsigned(a, b) : a;
            long expected = m2arr[i] ? VectorMath.minUnsigned(minAB, maxAB) : minAB;
            Verify.checkEQ(long_out[i], expected);
        }
    }

    // Predicated Long: umin(umin(a,b,m1), umax(a,b,m2), m3) => NO transform
    @Test
    @IR(counts = {IRNode.UMIN_VL, " 2 ", IRNode.UMAX_VL, " 1 "}, applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void umin_long_masked_all_diff_mask(int index) {
        LongVector vec1 = LongVector.fromArray(lspec, long_in1, index);
        LongVector vec2 = LongVector.fromArray(lspec, long_in2, index);
        VectorMask<Long> mask1 = VectorMask.fromArray(lspec, m1arr, index);
        VectorMask<Long> mask2 = VectorMask.fromArray(lspec, m2arr, index);
        VectorMask<Long> mask3 = VectorMask.fromArray(lspec, m3arr, index);
        vec1.lanewise(VectorOperators.UMIN, vec2, mask1)
            .lanewise(VectorOperators.UMIN,
                      vec1.lanewise(VectorOperators.UMAX, vec2, mask2), mask3)
            .intoArray(long_out, index);
    }

    @Run(test = "umin_long_masked_all_diff_mask")
    public void umin_long_masked_all_diff_mask_runner() {
        for (int i = 0; i < lspec.loopBound(COUNT); i += lspec.length()) {
            umin_long_masked_all_diff_mask(i);
        }
        for (int i = 0; i < lspec.loopBound(COUNT); i++) {
            long a = long_in1[i], b = long_in2[i];
            long minAB = m1arr[i] ? VectorMath.minUnsigned(a, b) : a;
            long maxAB = m2arr[i] ? VectorMath.maxUnsigned(a, b) : a;
            long expected = m3arr[i] ? VectorMath.minUnsigned(minAB, maxAB) : minAB;
            Verify.checkEQ(long_out[i], expected);
        }
    }

    // Predicated Long: umax(umin(a,b,m), umax(a,b,m), m) => umax(a,b,m)
    @Test
    @IR(counts = {IRNode.UMIN_VL, " 0 ", IRNode.UMAX_VL, " 1 "}, applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void umax_long_masked_same_mask(int index) {
        LongVector vec1 = LongVector.fromArray(lspec, long_in1, index);
        LongVector vec2 = LongVector.fromArray(lspec, long_in2, index);
        VectorMask<Long> m = VectorMask.fromArray(lspec, m1arr, index);
        vec1.lanewise(VectorOperators.UMIN, vec2, m)
            .lanewise(VectorOperators.UMAX,
                      vec1.lanewise(VectorOperators.UMAX, vec2, m), m)
            .intoArray(long_out, index);
    }

    @Run(test = "umax_long_masked_same_mask")
    public void umax_long_masked_same_mask_runner() {
        for (int i = 0; i < lspec.loopBound(COUNT); i += lspec.length()) {
            umax_long_masked_same_mask(i);
        }
        for (int i = 0; i < lspec.loopBound(COUNT); i++) {
            long a = long_in1[i], b = long_in2[i];
            long minAB = m1arr[i] ? VectorMath.minUnsigned(a, b) : a;
            long maxAB = m1arr[i] ? VectorMath.maxUnsigned(a, b) : a;
            long expected = m1arr[i] ? VectorMath.maxUnsigned(minAB, maxAB) : minAB;
            Verify.checkEQ(long_out[i], expected);
        }
    }

    // Predicated Long: umax(umin(a,b,m), umax(b,a,m), m) => umax(a,b,m)
    @Test
    @IR(counts = {IRNode.UMIN_VL, " 0 ", IRNode.UMAX_VL, " 1 "}, applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void umax_long_masked_flipped_inputs(int index) {
        LongVector vec1 = LongVector.fromArray(lspec, long_in1, index);
        LongVector vec2 = LongVector.fromArray(lspec, long_in2, index);
        VectorMask<Long> m = VectorMask.fromArray(lspec, m1arr, index);
        vec1.lanewise(VectorOperators.UMIN, vec2, m)
            .lanewise(VectorOperators.UMAX,
                      vec2.lanewise(VectorOperators.UMAX, vec1, m), m)
            .intoArray(long_out, index);
    }

    @Run(test = "umax_long_masked_flipped_inputs")
    public void umax_long_masked_flipped_inputs_runner() {
        for (int i = 0; i < lspec.loopBound(COUNT); i += lspec.length()) {
            umax_long_masked_flipped_inputs(i);
        }
        for (int i = 0; i < lspec.loopBound(COUNT); i++) {
            long a = long_in1[i], b = long_in2[i];
            long minAB = m1arr[i] ? VectorMath.minUnsigned(a, b) : a;
            long maxBA = m1arr[i] ? VectorMath.maxUnsigned(b, a) : b;
            long expected = m1arr[i] ? VectorMath.maxUnsigned(minAB, maxBA) : minAB;
            Verify.checkEQ(long_out[i], expected);
        }
    }

    // Predicated Long: umax(umin(a,b,m1), umax(a,b,m2), m1) => NO transform
    @Test
    @IR(counts = {IRNode.UMIN_VL, " 1 ", IRNode.UMAX_VL, " 2 "}, applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void umax_long_masked_diff_mask_minmax(int index) {
        LongVector vec1 = LongVector.fromArray(lspec, long_in1, index);
        LongVector vec2 = LongVector.fromArray(lspec, long_in2, index);
        VectorMask<Long> mask1 = VectorMask.fromArray(lspec, m1arr, index);
        VectorMask<Long> mask2 = VectorMask.fromArray(lspec, m2arr, index);
        vec1.lanewise(VectorOperators.UMIN, vec2, mask1)
            .lanewise(VectorOperators.UMAX,
                      vec1.lanewise(VectorOperators.UMAX, vec2, mask2), mask1)
            .intoArray(long_out, index);
    }

    @Run(test = "umax_long_masked_diff_mask_minmax")
    public void umax_long_masked_diff_mask_minmax_runner() {
        for (int i = 0; i < lspec.loopBound(COUNT); i += lspec.length()) {
            umax_long_masked_diff_mask_minmax(i);
        }
        for (int i = 0; i < lspec.loopBound(COUNT); i++) {
            long a = long_in1[i], b = long_in2[i];
            long minAB = m1arr[i] ? VectorMath.minUnsigned(a, b) : a;
            long maxAB = m2arr[i] ? VectorMath.maxUnsigned(a, b) : a;
            long expected = m1arr[i] ? VectorMath.maxUnsigned(minAB, maxAB) : minAB;
            Verify.checkEQ(long_out[i], expected);
        }
    }

    // Predicated Long: umax(umin(a,b,m2), umax(a,b,m1), m1) => NO transform
    @Test
    @IR(counts = {IRNode.UMIN_VL, " 1 ", IRNode.UMAX_VL, " 2 "}, applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void umax_long_masked_diff_mask_minmax_swap(int index) {
        LongVector vec1 = LongVector.fromArray(lspec, long_in1, index);
        LongVector vec2 = LongVector.fromArray(lspec, long_in2, index);
        VectorMask<Long> mask1 = VectorMask.fromArray(lspec, m1arr, index);
        VectorMask<Long> mask2 = VectorMask.fromArray(lspec, m2arr, index);
        vec1.lanewise(VectorOperators.UMIN, vec2, mask2)
            .lanewise(VectorOperators.UMAX,
                      vec1.lanewise(VectorOperators.UMAX, vec2, mask1), mask1)
            .intoArray(long_out, index);
    }

    @Run(test = "umax_long_masked_diff_mask_minmax_swap")
    public void umax_long_masked_diff_mask_minmax_swap_runner() {
        for (int i = 0; i < lspec.loopBound(COUNT); i += lspec.length()) {
            umax_long_masked_diff_mask_minmax_swap(i);
        }
        for (int i = 0; i < lspec.loopBound(COUNT); i++) {
            long a = long_in1[i], b = long_in2[i];
            long minAB = m2arr[i] ? VectorMath.minUnsigned(a, b) : a;
            long maxAB = m1arr[i] ? VectorMath.maxUnsigned(a, b) : a;
            long expected = m1arr[i] ? VectorMath.maxUnsigned(minAB, maxAB) : minAB;
            Verify.checkEQ(long_out[i], expected);
        }
    }

    // Predicated Long: umax(umin(a,b,m1), umax(a,b,m1), m2) => NO transform
    @Test
    @IR(counts = {IRNode.UMIN_VL, " 1 ", IRNode.UMAX_VL, " 2 "}, applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void umax_long_masked_diff_mask_outer(int index) {
        LongVector vec1 = LongVector.fromArray(lspec, long_in1, index);
        LongVector vec2 = LongVector.fromArray(lspec, long_in2, index);
        VectorMask<Long> mask1 = VectorMask.fromArray(lspec, m1arr, index);
        VectorMask<Long> mask2 = VectorMask.fromArray(lspec, m2arr, index);
        vec1.lanewise(VectorOperators.UMIN, vec2, mask1)
            .lanewise(VectorOperators.UMAX,
                      vec1.lanewise(VectorOperators.UMAX, vec2, mask1), mask2)
            .intoArray(long_out, index);
    }

    @Run(test = "umax_long_masked_diff_mask_outer")
    public void umax_long_masked_diff_mask_outer_runner() {
        for (int i = 0; i < lspec.loopBound(COUNT); i += lspec.length()) {
            umax_long_masked_diff_mask_outer(i);
        }
        for (int i = 0; i < lspec.loopBound(COUNT); i++) {
            long a = long_in1[i], b = long_in2[i];
            long minAB = m1arr[i] ? VectorMath.minUnsigned(a, b) : a;
            long maxAB = m1arr[i] ? VectorMath.maxUnsigned(a, b) : a;
            long expected = m2arr[i] ? VectorMath.maxUnsigned(minAB, maxAB) : minAB;
            Verify.checkEQ(long_out[i], expected);
        }
    }

    // Predicated Long: umax(umin(a,b,m1), umax(a,b,m2), m3) => NO transform
    @Test
    @IR(counts = {IRNode.UMIN_VL, " 1 ", IRNode.UMAX_VL, " 2 "}, applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void umax_long_masked_all_diff_mask(int index) {
        LongVector vec1 = LongVector.fromArray(lspec, long_in1, index);
        LongVector vec2 = LongVector.fromArray(lspec, long_in2, index);
        VectorMask<Long> mask1 = VectorMask.fromArray(lspec, m1arr, index);
        VectorMask<Long> mask2 = VectorMask.fromArray(lspec, m2arr, index);
        VectorMask<Long> mask3 = VectorMask.fromArray(lspec, m3arr, index);
        vec1.lanewise(VectorOperators.UMIN, vec2, mask1)
            .lanewise(VectorOperators.UMAX,
                      vec1.lanewise(VectorOperators.UMAX, vec2, mask2), mask3)
            .intoArray(long_out, index);
    }

    @Run(test = "umax_long_masked_all_diff_mask")
    public void umax_long_masked_all_diff_mask_runner() {
        for (int i = 0; i < lspec.loopBound(COUNT); i += lspec.length()) {
            umax_long_masked_all_diff_mask(i);
        }
        for (int i = 0; i < lspec.loopBound(COUNT); i++) {
            long a = long_in1[i], b = long_in2[i];
            long minAB = m1arr[i] ? VectorMath.minUnsigned(a, b) : a;
            long maxAB = m2arr[i] ? VectorMath.maxUnsigned(a, b) : a;
            long expected = m3arr[i] ? VectorMath.maxUnsigned(minAB, maxAB) : minAB;
            Verify.checkEQ(long_out[i], expected);
        }
    }
}
