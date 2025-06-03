/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
* @bug 8338021 8342677 8349522
* @summary Add IR validation tests for newly added saturated vector add / sub operations
* @modules jdk.incubator.vector
* @library /test/lib /
* @run driver compiler.vectorapi.VectorSaturatedOperationsTest
*/

package compiler.vectorapi;

import jdk.incubator.vector.*;
import compiler.lib.ir_framework.*;
import java.util.Random;
import java.util.stream.IntStream;

public class VectorSaturatedOperationsTest {
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

    private boolean[] mask;

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.setDefaultWarmup(5000)
                     .addFlags("--add-modules=jdk.incubator.vector")
                     .start();
    }

    public void setup_delimiting_byte_inputs() {
        // Saturating add
        byte_in1[COUNT - 1] = Byte.MAX_VALUE;
        byte_in2[COUNT - 1] = 100;
        // Saturating sub
        byte_in1[COUNT - 2] = Byte.MIN_VALUE;
        byte_in2[COUNT - 2] = 100;
        // Saturating unsigned add
        byte_in1[COUNT - 3] = -1;
        byte_in2[COUNT - 3] = 100;
        // Saturating unsigned sub
        byte_in1[COUNT - 4] = 0;
        byte_in2[COUNT - 4] = 100;
    }

    public void setup_delimiting_short_inputs() {
        // Saturating add
        short_in1[COUNT - 1] = Short.MAX_VALUE;
        short_in2[COUNT - 1] = 100;
        // Saturating sub
        short_in1[COUNT - 2] = Short.MIN_VALUE;
        short_in2[COUNT - 2] = 100;
        // Saturating unsigned add
        short_in1[COUNT - 3] = -1;
        short_in2[COUNT - 3] = 100;
        // Saturating unsigned sub
        short_in1[COUNT - 4] = 0;
        short_in2[COUNT - 4] = 100;
    }

    public void setup_delimiting_int_inputs() {
        // Saturating add
        int_in1[COUNT - 1] = Integer.MAX_VALUE;
        int_in2[COUNT - 1] = 100;
        // Saturating sub
        int_in1[COUNT - 2] = Integer.MIN_VALUE;
        int_in2[COUNT - 2] = 100;
        // Saturating unsigned add
        int_in1[COUNT - 3] = -1;
        int_in2[COUNT - 3] = 100;
        // Saturating unsigned sub
        int_in1[COUNT - 4] = 0;
        int_in2[COUNT - 4] = 100;
    }

    public void setup_delimiting_long_inputs() {
        // Saturating add
        long_in1[COUNT - 1] = Long.MAX_VALUE;
        long_in2[COUNT - 1] = 100;
        // Saturating sub
        long_in1[COUNT - 2] = Long.MIN_VALUE;
        long_in2[COUNT - 2] = 100;
        // Saturating unsigned add
        long_in1[COUNT - 3] =  -1L;
        long_in2[COUNT - 3] = 100;
        // Saturating unsigned sub
        long_in1[COUNT - 4] = 0;
        long_in2[COUNT - 4] = 100;
    }

    public VectorSaturatedOperationsTest() {
        Random r = jdk.test.lib.Utils.getRandomInstance();
        byte_in1  = new byte[COUNT];
        short_in1 = new short[COUNT];
        int_in1   = new int[COUNT];
        long_in1  = new long[COUNT];

        byte_in2  = new byte[COUNT];
        short_in2 = new short[COUNT];
        int_in2   = new int[COUNT];
        long_in2  = new long[COUNT];
        mask      = new boolean[COUNT];
        IntStream.range(0, COUNT-4).forEach(
            i -> {
                long_in1[i] = r.nextLong(Long.MIN_VALUE, Long.MAX_VALUE);
                long_in2[i] = r.nextLong(Long.MIN_VALUE, Long.MAX_VALUE);
                int_in1[i] = r.nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE);
                int_in2[i] = r.nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE);
                short_in1[i] = (short)r.nextInt(Short.MIN_VALUE, Short.MAX_VALUE);
                short_in2[i] = (short)r.nextInt(Short.MIN_VALUE, Short.MAX_VALUE);
                byte_in1[i] = (byte)r.nextInt(Byte.MIN_VALUE, Byte.MAX_VALUE);
                byte_in2[i] = (byte)r.nextInt(Byte.MIN_VALUE, Byte.MAX_VALUE);
                mask[i] = r.nextBoolean();
            }
        );

        setup_delimiting_byte_inputs();
        setup_delimiting_short_inputs();
        setup_delimiting_int_inputs();
        setup_delimiting_long_inputs();

        long_out  = new long[COUNT];
        int_out   = new int[COUNT];
        short_out = new short[COUNT];
        byte_out  = new byte[COUNT];
    }

    @Test
    @IR(counts = {IRNode.SATURATING_ADD_VB, " >0 "}, applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    @Warmup(value = 10000)
    public void sadd_byte() {
        for (int i = 0; i < COUNT; i += bspec.length()) {
            ByteVector.fromArray(bspec, byte_in1, i)
                     .lanewise(VectorOperators.SADD,
                               ByteVector.fromArray(bspec, byte_in2, i))
                     .intoArray(byte_out, i);
        }
    }

    @Check(test = "sadd_byte")
    public void sadd_byte_verify() {
        for (int i = 0; i < COUNT; i++) {
            byte actual = byte_out[i];
            byte expected = VectorMath.addSaturating(byte_in1[i], byte_in2[i]);
            if (actual != expected) {
                throw new AssertionError("Result Mismatch : actual (" +  actual + ") !=  expected (" + expected  + ")");
            }
        }
    }

    @Test
    @IR(counts = {IRNode.SATURATING_ADD_VS, " >0 "}, applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    @Warmup(value = 10000)
    public void sadd_short() {
        for (int i = 0; i < COUNT; i += sspec.length()) {
            ShortVector.fromArray(sspec, short_in1, i)
                     .lanewise(VectorOperators.SADD,
                               ShortVector.fromArray(sspec, short_in2, i))
                     .intoArray(short_out, i);
        }
    }

    @Check(test = "sadd_short")
    public void sadd_short_verify() {
        for (int i = 0; i < COUNT; i++) {
            short actual = short_out[i];
            short expected = VectorMath.addSaturating(short_in1[i], short_in2[i]);
            if (actual != expected) {
                throw new AssertionError("Result Mismatch : actual (" +  actual + ") !=  expected (" + expected  + ")");
            }
        }
    }

    @Test
    @IR(counts = {IRNode.SATURATING_ADD_VI, " >0 "}, applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    @Warmup(value = 10000)
    public void sadd_int() {
        for (int i = 0; i < COUNT; i += ispec.length()) {
            IntVector.fromArray(ispec, int_in1, i)
                     .lanewise(VectorOperators.SADD,
                               IntVector.fromArray(ispec, int_in2, i))
                     .intoArray(int_out, i);
        }
    }

    @Check(test = "sadd_int")
    public void sadd_int_verify() {
        for (int i = 0; i < COUNT; i++) {
            int actual = int_out[i];
            int expected = VectorMath.addSaturating(int_in1[i], int_in2[i]);
            if (actual != expected) {
                throw new AssertionError("Result Mismatch : actual (" +  actual + ") !=  expected (" + expected  + ")");
            }
        }
    }

    @Test
    @IR(counts = {IRNode.SATURATING_ADD_VL, " >0 "}, applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    @Warmup(value = 10000)
    public void sadd_long() {
        for (int i = 0; i < COUNT; i += lspec.length()) {
            LongVector.fromArray(lspec, long_in1, i)
                     .lanewise(VectorOperators.SADD,
                               LongVector.fromArray(lspec, long_in2, i))
                     .intoArray(long_out, i);
        }
    }

    @Check(test = "sadd_long")
    public void sadd_long_verify() {
        for (int i = 0; i < COUNT; i++) {
            long actual = long_out[i];
            long expected = VectorMath.addSaturating(long_in1[i], long_in2[i]);
            if (actual != expected) {
                throw new AssertionError("Result Mismatch : actual (" +  actual + ") !=  expected (" + expected  + ")");
            }
        }
    }

    @Test
    @IR(counts = {IRNode.SATURATING_ADD_VB, " >0 " , "unsigned_vector_node", " >0 "},
        phase = {CompilePhase.BEFORE_MATCHING},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    @Warmup(value = 10000)
    public void suadd_byte() {
        for (int i = 0; i < COUNT; i += bspec.length()) {
            ByteVector.fromArray(bspec, byte_in1, i)
                     .lanewise(VectorOperators.SUADD,
                               ByteVector.fromArray(bspec, byte_in2, i))
                     .intoArray(byte_out, i);
        }
    }

    @Check(test = "suadd_byte")
    public void suadd_byte_verify() {
        for (int i = 0; i < COUNT; i++) {
            byte actual = byte_out[i];
            byte expected = VectorMath.addSaturatingUnsigned(byte_in1[i], byte_in2[i]);
            if (actual != expected) {
                throw new AssertionError("Result Mismatch : actual (" +  actual + ") !=  expected (" + expected  + ")");
            }
        }
    }

    @Test
    @IR(counts = {IRNode.SATURATING_ADD_VS, " >0 ", "unsigned_vector_node", " >0 "},
        phase = {CompilePhase.BEFORE_MATCHING},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    @Warmup(value = 10000)
    public void suadd_short() {
        for (int i = 0; i < COUNT; i += sspec.length()) {
            ShortVector.fromArray(sspec, short_in1, i)
                     .lanewise(VectorOperators.SUADD,
                               ShortVector.fromArray(sspec, short_in2, i))
                     .intoArray(short_out, i);
        }
    }

    @Check(test = "suadd_short")
    public void suadd_short_verify() {
        for (int i = 0; i < COUNT; i++) {
            short actual = short_out[i];
            short expected = VectorMath.addSaturatingUnsigned(short_in1[i], short_in2[i]);
            if (actual != expected) {
                throw new AssertionError("Result Mismatch : actual (" +  actual + ") !=  expected (" + expected  + ")");
            }
        }
    }

    @Test
    @IR(counts = {IRNode.SATURATING_ADD_VI, " >0 ", "unsigned_vector_node", " >0 "},
        phase = {CompilePhase.BEFORE_MATCHING},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    @Warmup(value = 10000)
    public void suadd_int() {
        for (int i = 0; i < COUNT; i += ispec.length()) {
            IntVector.fromArray(ispec, int_in1, i)
                     .lanewise(VectorOperators.SUADD,
                               IntVector.fromArray(ispec, int_in2, i))
                     .intoArray(int_out, i);
        }
    }

    @Check(test = "suadd_int")
    public void suadd_int_verify() {
        for (int i = 0; i < COUNT; i++) {
            int actual = int_out[i];
            int expected = VectorMath.addSaturatingUnsigned(int_in1[i], int_in2[i]);
            if (actual != expected) {
                throw new AssertionError("Result Mismatch : actual (" +  actual + ") !=  expected (" + expected  + ")");
            }
        }
    }

    @Test
    @IR(counts = {IRNode.SATURATING_ADD_VL, " >0 ", "unsigned_vector_node", " >0 "},
        phase = {CompilePhase.BEFORE_MATCHING},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    @Warmup(value = 10000)
    public void suadd_long() {
        for (int i = 0; i < COUNT; i += lspec.length()) {
            LongVector.fromArray(lspec, long_in1, i)
                     .lanewise(VectorOperators.SUADD,
                               LongVector.fromArray(lspec, long_in2, i))
                     .intoArray(long_out, i);
        }
    }

    @Check(test = "suadd_long")
    public void suadd_long_verify() {
        for (int i = 0; i < COUNT; i++) {
            long actual = long_out[i];
            long expected = VectorMath.addSaturatingUnsigned(long_in1[i], long_in2[i]);
            if (actual != expected) {
                throw new AssertionError("Result Mismatch : actual (" +  actual + ") !=  expected (" + expected  + ")");
            }
        }
    }

    @Test
    @IR(counts = {IRNode.SATURATING_SUB_VB, " >0 "}, applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    @Warmup(value = 10000)
    public void ssub_byte() {
        for (int i = 0; i < COUNT; i += bspec.length()) {
            ByteVector.fromArray(bspec, byte_in1, i)
                     .lanewise(VectorOperators.SSUB,
                               ByteVector.fromArray(bspec, byte_in2, i))
                     .intoArray(byte_out, i);
        }
    }

    @Check(test = "ssub_byte")
    public void ssub_byte_verify() {
        for (int i = 0; i < COUNT; i++) {
            byte actual = byte_out[i];
            byte expected = VectorMath.subSaturating(byte_in1[i], byte_in2[i]);
            if (actual != expected) {
                throw new AssertionError("Result Mismatch : actual (" +  actual + ") !=  expected (" + expected  + ")");
            }
        }
    }

    @Test
    @IR(counts = {IRNode.SATURATING_SUB_VS, " >0 "}, applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    @Warmup(value = 10000)
    public void ssub_short() {
        for (int i = 0; i < COUNT; i += sspec.length()) {
            ShortVector.fromArray(sspec, short_in1, i)
                     .lanewise(VectorOperators.SSUB,
                               ShortVector.fromArray(sspec, short_in2, i))
                     .intoArray(short_out, i);
        }
    }

    @Check(test = "ssub_short")
    public void ssub_short_verify() {
        for (int i = 0; i < COUNT; i++) {
            short actual = short_out[i];
            short expected = VectorMath.subSaturating(short_in1[i], short_in2[i]);
            if (actual != expected) {
                throw new AssertionError("Result Mismatch : actual (" +  actual + ") !=  expected (" + expected  + ")");
            }
        }
    }

    @Test
    @IR(counts = {IRNode.SATURATING_SUB_VI, " >0 "}, applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    @Warmup(value = 10000)
    public void ssub_int() {
        for (int i = 0; i < COUNT; i += ispec.length()) {
            IntVector.fromArray(ispec, int_in1, i)
                     .lanewise(VectorOperators.SSUB,
                               IntVector.fromArray(ispec, int_in2, i))
                     .intoArray(int_out, i);
        }
    }

    @Check(test = "ssub_int")
    public void ssub_int_verify() {
        for (int i = 0; i < COUNT; i++) {
            int actual = int_out[i];
            int expected = VectorMath.subSaturating(int_in1[i], int_in2[i]);
            if (actual != expected) {
                throw new AssertionError("Result Mismatch : actual (" +  actual + ") !=  expected (" + expected  + ")");
            }
        }
    }

    @Test
    @IR(counts = {IRNode.SATURATING_SUB_VL, " >0 "}, applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    @Warmup(value = 10000)
    public void ssub_long() {
        for (int i = 0; i < COUNT; i += lspec.length()) {
            LongVector.fromArray(lspec, long_in1, i)
                     .lanewise(VectorOperators.SSUB,
                               LongVector.fromArray(lspec, long_in2, i))
                     .intoArray(long_out, i);
        }
    }

    @Check(test = "ssub_long")
    public void ssub_long_verify() {
        for (int i = 0; i < COUNT; i++) {
            long actual = long_out[i];
            long expected = VectorMath.subSaturating(long_in1[i], long_in2[i]);
            if (actual != expected) {
                throw new AssertionError("Result Mismatch : actual (" +  actual + ") !=  expected (" + expected  + ")");
            }
        }
    }

    @Test
    @IR(counts = {IRNode.SATURATING_SUB_VB, " >0 " , "unsigned_vector_node", " >0 "},
        phase = {CompilePhase.BEFORE_MATCHING},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    @Warmup(value = 10000)
    public void susub_byte() {
        for (int i = 0; i < COUNT; i += bspec.length()) {
            ByteVector.fromArray(bspec, byte_in1, i)
                     .lanewise(VectorOperators.SUSUB,
                               ByteVector.fromArray(bspec, byte_in2, i))
                     .intoArray(byte_out, i);
        }
    }

    @Check(test = "susub_byte")
    public void susub_byte_verify() {
        for (int i = 0; i < COUNT; i++) {
            byte actual = byte_out[i];
            byte expected = VectorMath.subSaturatingUnsigned(byte_in1[i], byte_in2[i]);
            if (actual != expected) {
                throw new AssertionError("Result Mismatch : actual (" +  actual + ") !=  expected (" + expected  + ")");
            }
        }
    }

    @Test
    @IR(counts = {IRNode.SATURATING_SUB_VS, " >0 ", "unsigned_vector_node", " >0 "},
        phase = {CompilePhase.BEFORE_MATCHING},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    @Warmup(value = 10000)
    public void susub_short() {
        for (int i = 0; i < COUNT; i += sspec.length()) {
            ShortVector.fromArray(sspec, short_in1, i)
                     .lanewise(VectorOperators.SUSUB,
                               ShortVector.fromArray(sspec, short_in2, i))
                     .intoArray(short_out, i);
        }
    }

    @Check(test = "susub_short")
    public void susub_short_verify() {
        for (int i = 0; i < COUNT; i++) {
            short actual = short_out[i];
            short expected = VectorMath.subSaturatingUnsigned(short_in1[i], short_in2[i]);
            if (actual != expected) {
                throw new AssertionError("Result Mismatch : actual (" +  actual + ") !=  expected (" + expected  + ")");
            }
        }
    }

    @Test
    @IR(counts = {IRNode.SATURATING_SUB_VI, " >0 ", "unsigned_vector_node", " >0 "},
        phase = {CompilePhase.BEFORE_MATCHING},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    @Warmup(value = 10000)
    public void susub_int() {
        for (int i = 0; i < COUNT; i += ispec.length()) {
            IntVector.fromArray(ispec, int_in1, i)
                     .lanewise(VectorOperators.SUSUB,
                               IntVector.fromArray(ispec, int_in2, i))
                     .intoArray(int_out, i);
        }
    }

    @Check(test = "susub_int")
    public void susub_int_verify() {
        for (int i = 0; i < COUNT; i++) {
            int actual = int_out[i];
            int expected = VectorMath.subSaturatingUnsigned(int_in1[i], int_in2[i]);
            if (actual != expected) {
                throw new AssertionError("Result Mismatch : actual (" +  actual + ") !=  expected (" + expected  + ")");
            }
        }
    }

    @Test
    @IR(counts = {IRNode.SATURATING_SUB_VL, " >0 ", "unsigned_vector_node", " >0 "},
        phase = {CompilePhase.BEFORE_MATCHING},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    @Warmup(value = 10000)
    public void susub_long() {
        for (int i = 0; i < COUNT; i += lspec.length()) {
            LongVector.fromArray(lspec, long_in1, i)
                     .lanewise(VectorOperators.SUSUB,
                               LongVector.fromArray(lspec, long_in2, i))
                     .intoArray(long_out, i);
        }
    }

    @Check(test = "susub_long")
    public void susub_long_verify() {
        for (int i = 0; i < COUNT; i++) {
            long actual = long_out[i];
            long expected = VectorMath.subSaturatingUnsigned(long_in1[i], long_in2[i]);
            if (actual != expected) {
                throw new AssertionError("Result Mismatch : actual (" +  actual + ") !=  expected (" + expected  + ")");
            }
        }
    }

    @Test
    @IR(counts = {IRNode.SATURATING_ADD_VB, " >0 "}, applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    @IR(counts = {IRNode.VECTOR_BLEND_B, " >0 "}, applyIfCPUFeatureAnd = {"asimd", "true", "sve2", "false"})
    @IR(failOn = IRNode.VECTOR_BLEND_B, applyIfCPUFeature = {"sve2", "true"})
    @Warmup(value = 10000)
    public void sadd_masked() {
        for (int i = 0; i < COUNT; i += bspec.length()) {
            VectorMask<Byte> m = VectorMask.fromArray(bspec, mask, i);
            ByteVector.fromArray(bspec, byte_in1, i)
                      .lanewise(VectorOperators.SADD,
                                ByteVector.fromArray(bspec, byte_in2, i), m)
                      .intoArray(byte_out, i);
        }
    }

    @Check(test = "sadd_masked")
    public void sadd_masked_verify() {
        for (int i = 0; i < COUNT; i++) {
            byte actual = byte_out[i];
            byte expected = mask[i] ? VectorMath.addSaturating(byte_in1[i], byte_in2[i]) : byte_in1[i];
            if (actual != expected) {
                throw new AssertionError("Result Mismatch : actual (" +  actual + ") !=  expected (" + expected  + ")");
            }
        }
    }

    @Test
    @IR(counts = {IRNode.SATURATING_SUB_VS, " >0 "}, applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    @IR(counts = {IRNode.VECTOR_BLEND_S, " >0 "}, applyIfCPUFeatureAnd = {"asimd", "true", "sve2", "false"})
    @IR(failOn = IRNode.VECTOR_BLEND_S, applyIfCPUFeature = {"sve2", "true"})
    @Warmup(value = 10000)
    public void ssub_masked() {
        for (int i = 0; i < COUNT; i += sspec.length()) {
            VectorMask<Short> m = VectorMask.fromArray(sspec, mask, i);
            ShortVector.fromArray(sspec, short_in1, i)
                       .lanewise(VectorOperators.SSUB,
                                 ShortVector.fromArray(sspec, short_in2, i), m)
                       .intoArray(short_out, i);
        }
    }

    @Check(test = "ssub_masked")
    public void ssub_masked_verify() {
        for (int i = 0; i < COUNT; i++) {
            short actual = short_out[i];
            short expected = mask[i] ? VectorMath.subSaturating(short_in1[i], short_in2[i]) : short_in1[i];
            if (actual != expected) {
                throw new AssertionError("Result Mismatch : actual (" +  actual + ") !=  expected (" + expected  + ")");
            }
        }
    }

    @Test
    @IR(counts = {IRNode.SATURATING_ADD_VI, " >0 ", "unsigned_vector_node", " >0 "},
        phase = {CompilePhase.BEFORE_MATCHING},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    @IR(counts = {IRNode.VECTOR_BLEND_I, " >0 "}, applyIfCPUFeatureAnd = {"asimd", "true", "sve2", "false"})
    @IR(failOn = IRNode.VECTOR_BLEND_I, applyIfCPUFeature = {"sve2", "true"})
    @Warmup(value = 10000)
    public void suadd_masked() {
        for (int i = 0; i < COUNT; i += ispec.length()) {
            VectorMask<Integer> m = VectorMask.fromArray(ispec, mask, i);
            IntVector.fromArray(ispec, int_in1, i)
                     .lanewise(VectorOperators.SUADD,
                               IntVector.fromArray(ispec, int_in2, i), m)
                     .intoArray(int_out, i);
        }
    }

    @Check(test = "suadd_masked")
    public void suadd_masked_verify() {
        for (int i = 0; i < COUNT; i++) {
            int actual = int_out[i];
            int expected = mask[i] ? VectorMath.addSaturatingUnsigned(int_in1[i], int_in2[i]) : int_in1[i];
            if (actual != expected) {
                throw new AssertionError("Result Mismatch : actual (" +  actual + ") !=  expected (" + expected  + ")");
            }
        }
    }

    @Test
    @IR(counts = {IRNode.SATURATING_SUB_VL, " >0 ", "unsigned_vector_node", " >0 "},
        phase = {CompilePhase.BEFORE_MATCHING},
        applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true", "rvv", "true"})
    @IR(counts = {IRNode.VECTOR_BLEND_L, " >0 "}, applyIfCPUFeatureAnd = {"asimd", "true", "sve2", "false"})
    @IR(failOn = IRNode.VECTOR_BLEND_L, applyIfCPUFeature = {"sve2", "true"})
    @Warmup(value = 10000)
    public void susub_masked() {
        for (int i = 0; i < COUNT; i += lspec.length()) {
            VectorMask<Long> m = VectorMask.fromArray(lspec, mask, i);
            LongVector.fromArray(lspec, long_in1, i)
                      .lanewise(VectorOperators.SUSUB,
                                LongVector.fromArray(lspec, long_in2, i), m)
                      .intoArray(long_out, i);
        }
    }

    @Check(test = "susub_masked")
    public void susub_masked_verify() {
        for (int i = 0; i < COUNT; i++) {
            long actual = long_out[i];
            long expected = mask[i] ? VectorMath.subSaturatingUnsigned(long_in1[i], long_in2[i]) : long_in1[i];
            if (actual != expected) {
                throw new AssertionError("Result Mismatch : actual (" +  actual + ") !=  expected (" + expected  + ")");
            }
        }
    }
}
