/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.util.stream.IntStream;

public class VectorUnsignedMinMaxOperationsTest {
    private static final int COUNT = 2048;
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
}

