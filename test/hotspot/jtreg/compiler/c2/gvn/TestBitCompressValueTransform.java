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

/*
 * @test
 * @bug 8350896 8370459
 * @library /test/lib /
 * @summary C2: wrong result: Integer/Long.compress gets wrong type from CompressBitsNode::Value.
 * @run driver compiler.c2.gvn.TestBitCompressValueTransform
 */
package compiler.c2.gvn;

import jdk.test.lib.Asserts;
import compiler.lib.ir_framework.*;
import compiler.lib.generators.*;

public class TestBitCompressValueTransform {

    public static final int  field_I = 0x400_0000;
    public static final long field_L = 0x400_0000_0000_0000L;
    public static final int  gold_I = Integer.valueOf(Integer.compress(0x8000_0000, field_I));
    public static final long gold_L = Long.valueOf(Long.compress(0x8000_0000_0000_0000L, field_L));

    public static RestrictableGenerator<Integer> GEN_I = Generators.G.ints();
    public static RestrictableGenerator<Long> GEN_L = Generators.G.longs();

    public final int LIMIT_I1 = GEN_I.next();
    public final int LIMIT_I2 = GEN_I.next();
    public final int LIMIT_I3 = GEN_I.next();
    public final int LIMIT_I4 = GEN_I.next();
    public final int LIMIT_I5 = GEN_I.next();
    public final int LIMIT_I6 = GEN_I.next();
    public final int LIMIT_I7 = GEN_I.next();
    public final int LIMIT_I8 = GEN_I.next();

    public final long LIMIT_L1 = GEN_L.next();
    public final long LIMIT_L2 = GEN_L.next();
    public final long LIMIT_L3 = GEN_L.next();
    public final long LIMIT_L4 = GEN_L.next();
    public final long LIMIT_L5 = GEN_L.next();
    public final long LIMIT_L6 = GEN_L.next();
    public final long LIMIT_L7 = GEN_L.next();
    public final long LIMIT_L8 = GEN_L.next();

    public final int BOUND1_LO_I = GEN_I.next();
    public final int BOUND2_LO_I = GEN_I.next();
    public final int BOUND1_HI_I = GEN_I.next();
    public final int BOUND2_HI_I = GEN_I.next();

    public final long BOUND1_LO_L = GEN_L.next();
    public final long BOUND2_LO_L = GEN_L.next();
    public final long BOUND1_HI_L = GEN_L.next();
    public final long BOUND2_HI_L = GEN_L.next();

    @Test
    @IR (counts = { IRNode.COMPRESS_BITS, " >0 " }, applyIfCPUFeature = { "bmi2", "true" })
    public long test1(long value) {
        return Long.compress(0x8000_0000_0000_0000L, value);
    }

    @Run(test = "test1")
    public void run1() {
        long res = test1(field_L);
        Asserts.assertEQ(res, gold_L);
    }


    @Test
    @IR (counts = { IRNode.COMPRESS_BITS, " >0 " }, applyIfCPUFeature = { "bmi2", "true" })
    public int test2(int value) {
        return Integer.compress(0x8000_0000, value);
    }

    @Run(test = "test2")
    public void run2() {
        int res = test2(field_I);
        Asserts.assertEQ(res, gold_I);
    }

    @Test
    @IR (counts = { IRNode.COMPRESS_BITS, " 0 "} , failOn = { IRNode.UNSTABLE_IF_TRAP }, applyIfCPUFeature = { "bmi2", "true" })
    public int test3(int value) {
        int filter_bits = value & 0xF;
        int compress_bits = Integer.compress(15, filter_bits);
        if (compress_bits > 15) {
            value = -1;
        }
        return value;
    }

    @Run(test = "test3")
    public void run3() {
        int res = 0;
        for (int i = 1; i < 100; i++) {
            res |= test3(i);
        }
        Asserts.assertLTE(0, res);
    }

    @Test
    @IR (counts = { IRNode.COMPRESS_BITS, " 0 "} , failOn = { IRNode.UNSTABLE_IF_TRAP }, applyIfCPUFeature = { "bmi2", "true" })
    public long test4(long value) {
        long filter_bits = value & 0xFL;
        long compress_bits = Long.compress(15L, filter_bits);
        if (compress_bits > 15L) {
            value = -1;
        }
        return value;
    }

    @Run(test = "test4")
    public void run4() {
        long res = 0;
        for (long i = 1; i < 100; i++) {
            res |= test4(i);
        }
        Asserts.assertLTE(0L, res);
    }

    @Test
    @IR (counts = { IRNode.COMPRESS_BITS, " >0 " }, applyIfCPUFeature = { "bmi2", "true" })
    public long test5(long value) {
        // Since value range includes -1 hence with mask
        // and value as -1 all the result bits will be set.
        long mask = Long.min(10000L, Long.max(-10000L, value));
        return Long.compress(value, mask);
    }

    @Run(test = "test5")
    public void run5() {
        long res = 0;
        for (int i = -100; i < 100; i++) {
            res |= test5((long)i);
        }
        Asserts.assertEQ(-1L, res);
    }

    @Test
    @IR (counts = { IRNode.COMPRESS_BITS, " >0 " }, applyIfCPUFeature = { "bmi2", "true" })
    public long test6(long value) {
        // For mask within a strictly -ve value range less than -1,
        // result of compression will always be a +ve value.
        long mask = Long.min(-2L, Long.max(-10000L, value));
        return Long.compress(value, mask);
    }

    @Run(test = "test6")
    public void run6() {
        long res = 0;
        for (int i = -100; i < 100; i++) {
            res |= test6((long)i);
        }
        Asserts.assertLTE(0L, res);
    }

    @Test
    @IR (counts = { IRNode.COMPRESS_BITS, " >0 " }, applyIfCPUFeature = { "bmi2", "true" })
    public long test7(long value) {
        // For mask within a strictly +ve value range,
        // result of compression will always be a +ve value with
        // upper bound capped at max mask value.
        long mask = Long.min(10000L, Long.max(0L, value));
        return Long.compress(value, mask);
    }

    @Run(test = "test7")
    public void run7() {
        long res = Long.MIN_VALUE;
        for (int i = -100; i < 100; i++) {
            res = Long.max(test7((long)i), res);
        }
        Asserts.assertGTE(10000L, res);
    }

    @Test
    @IR (counts = { IRNode.COMPRESS_BITS, " >0 " }, applyIfCPUFeature = { "bmi2", "true" })
    public int test8(int value) {
        // Since value range includes -1 hence with mask
        // and value as -1 all the result bits will be set.
        int mask = Integer.min(10000, Integer.max(-10000, value));
        return Integer.compress(value, mask);
    }

    @Run(test = "test8")
    public void run8() {
        int res = 0;
        for (int i = -100; i < 100; i++) {
            res |= test8(i);
        }
        Asserts.assertEQ(-1, res);
    }

    @Test
    @IR (counts = { IRNode.COMPRESS_BITS, " >0 " }, applyIfCPUFeature = { "bmi2", "true" })
    public int test9(int value) {
        // For mask within a strictly -ve value range less than -1,
        // result of compression will always be a +ve value.
        int mask = Integer.min(-2, Integer.max(-10000, value));
        return Integer.compress(value, mask);
    }

    @Run(test = "test9")
    public void run9() {
        int res = 0;
        for (int i = -100; i < 100; i++) {
            res |= test9(i);
        }
        Asserts.assertLTE(0, res);
    }

    @Test
    @IR (counts = { IRNode.COMPRESS_BITS, " >0 " }, applyIfCPUFeature = { "bmi2", "true" })
    public int test10(int value) {
        // For mask within a strictly +ve value range,
        // result of compression will always be a +ve value with
        // upper bound capped at max mask value.
        int mask = Integer.min(10000, Integer.max(0, value));
        return Integer.compress(value, mask);
    }

    @Run(test = "test10")
    public void run10() {
        int res = Integer.MIN_VALUE;
        for (int i = -100; i < 100; i++) {
            res = Integer.max(test10(i), res);
        }
        Asserts.assertGTE(100, res);
    }

    @Test
    @IR (counts = { IRNode.COMPRESS_BITS, " 0 " })
    public int test11(int value) {
        // For constant zero input, compress is folded to zero
        int mask = Integer.min(10000, Integer.max(0, value));
        return Integer.compress(0, mask);
    }

    @Run(test = "test11")
    public void run11() {
        int res = 0;
        for (int i = -100; i < 100; i++) {
            res |= test11(i);
        }
        Asserts.assertEQ(0, res);
    }

    @Test
    @IR (counts = { IRNode.COMPRESS_BITS, " 0 " })
    public long test12(long value) {
        // For constant zero input, compress is folded to zero
        long mask = Long.min(10000L, Long.max(0L, value));
        return Long.compress(0L, mask);
    }

    @Run(test = "test12")
    public void run12() {
        long res = 0;
        for (int i = -100; i < 100; i++) {
            res |= test12(i);
        }
        Asserts.assertEQ(0L, res);
    }

    @Test
    @IR (counts = { IRNode.EXPAND_BITS, " 0 " })
    public int test13(int value) {
        // For constant zero input, expand is folded to zero
        int mask = Integer.min(10000, Integer.max(0, value));
        return Integer.expand(0, mask);
    }

    @Run(test = "test13")
    public void run13() {
        int res = 0;
        for (int i = -100; i < 100; i++) {
            res |= test13(i);
        }
        Asserts.assertEQ(0, res);
    }

    @Test
    @IR (counts = { IRNode.EXPAND_BITS, " 0 " })
    public long test14(long value) {
        // For constant zero input, compress is folded to zero
        long mask = Long.min(10000L, Long.max(0L, value));
        return Long.expand(0L, mask);
    }

    @Run(test = "test14")
    public void run14() {
        long res = 0;
        for (int i = -100; i < 100; i++) {
            res |= test14(i);
        }
        Asserts.assertEQ(0L, res);
    }

    @Test
    @IR (counts = { IRNode.COMPRESS_BITS, " >0 " }, applyIfCPUFeature = {"bmi2" , "true"})
    public int test15(int src, int mask) {
        // src_type = [min_int + 1, -1]
        src = Math.max(Integer.MIN_VALUE + 1, Math.min(src, -1));
        return Integer.compress(src, mask);
    }

    @Run (test = "test15")
    public void run15() {
        int res = test15(0, 0);
        Asserts.assertEQ(0, res);
    }

    @DontCompile
    public int test16_interpreted(int src, int mask) {
        src = Math.max(BOUND1_LO_I, Math.min(src, BOUND1_HI_I));
        mask = Math.max(BOUND2_LO_I, Math.min(mask, BOUND2_HI_I));
        int res = Integer.compress(src, mask);

        if (res > LIMIT_I1) {
            res += 1;
        }
        if (res > LIMIT_I2) {
            res += 2;
        }
        if (res > LIMIT_I3) {
            res += 4;
        }
        if (res > LIMIT_I4) {
            res += 8;
        }
        if (res > LIMIT_I5) {
            res += 16;
        }
        if (res > LIMIT_I6) {
            res += 32;
        }
        if (res > LIMIT_I7) {
            res += 64;
        }
        if (res > LIMIT_I8) {
            res += 128;
        }
        return res;
    }

    @Test
    @IR (counts = { IRNode.COMPRESS_BITS, " >0 " }, applyIfCPUFeature = {"bmi2" , "true"})
    public int test16(int src, int mask) {
        src = Math.max(BOUND1_LO_I, Math.min(src, BOUND1_HI_I));
        mask = Math.max(BOUND2_LO_I, Math.min(mask, BOUND2_HI_I));
        int res = Integer.compress(src, mask);

        // Check the result with some random value ranges, if any of the
        // following conditions incorrectly constant folds the result will
        // not comply with the interpreter.

        if (res > LIMIT_I1) {
            res += 1;
        }
        if (res > LIMIT_I2) {
            res += 2;
        }
        if (res > LIMIT_I3) {
            res += 4;
        }
        if (res > LIMIT_I4) {
            res += 8;
        }
        if (res > LIMIT_I5) {
            res += 16;
        }
        if (res > LIMIT_I6) {
            res += 32;
        }
        if (res > LIMIT_I7) {
            res += 64;
        }
        if (res > LIMIT_I8) {
            res += 128;
        }
        return res;
    }

    @Run (test = "test16")
    public void run16() {
        int actual = 0;
        int expected = 0;

        for (int i = 0; i < 100; i++) {
            int arg1 = GEN_I.next();
            int arg2 = GEN_I.next();

            actual += test16(arg1, arg2);
            expected += test16_interpreted(arg1, arg2);
        }
        Asserts.assertEQ(actual, expected);
    }

    @DontCompile
    public int test17_interpreted(int src, int mask) {
        src = Math.max(BOUND1_LO_I, Math.min(src, BOUND1_HI_I));
        mask = Math.max(BOUND2_LO_I, Math.min(mask, BOUND2_HI_I));
        int res = Integer.expand(src, mask);

        if (res > LIMIT_I1) {
            res += 1;
        }
        if (res > LIMIT_I2) {
            res += 2;
        }
        if (res > LIMIT_I3) {
            res += 4;
        }
        if (res > LIMIT_I4) {
            res += 8;
        }
        if (res > LIMIT_I5) {
            res += 16;
        }
        if (res > LIMIT_I6) {
            res += 32;
        }
        if (res > LIMIT_I7) {
            res += 64;
        }
        if (res > LIMIT_I8) {
            res += 128;
        }
        return res;
    }

    @Test
    @IR (counts = { IRNode.EXPAND_BITS, " >0 " }, applyIfCPUFeature = {"bmi2" , "true"})
    public int test17(int src, int mask) {
        src = Math.max(BOUND1_LO_I, Math.min(src, BOUND1_HI_I));
        mask = Math.max(BOUND2_LO_I, Math.min(mask, BOUND2_HI_I));
        int res = Integer.expand(src, mask);

        // Check the result with some random value ranges, if any of the
        // following conditions incorrectly constant folds the result will
        // not comply with the interpreter.

        if (res > LIMIT_I1) {
            res += 1;
        }
        if (res > LIMIT_I2) {
            res += 2;
        }
        if (res > LIMIT_I3) {
            res += 4;
        }
        if (res > LIMIT_I4) {
            res += 8;
        }
        if (res > LIMIT_I5) {
            res += 16;
        }
        if (res > LIMIT_I6) {
            res += 32;
        }
        if (res > LIMIT_I7) {
            res += 64;
        }
        if (res > LIMIT_I8) {
            res += 128;
        }
        return res;
    }

    @Run (test = "test17")
    public void run17() {
        int actual = 0;
        int expected = 0;

        for (int i = 0; i < 100; i++) {
            int arg1 = GEN_I.next();
            int arg2 = GEN_I.next();

            actual += test16(arg1, arg2);
            expected += test16_interpreted(arg1, arg2);
        }
        Asserts.assertEQ(actual, expected);
    }

    @DontCompile
    public long test18_interpreted(long src, long mask) {
        src = Math.max(BOUND1_LO_L, Math.min(src, BOUND1_HI_L));
        mask = Math.max(BOUND2_LO_L, Math.min(mask, BOUND2_HI_L));
        long res = Long.compress(src, mask);

        if (res > LIMIT_L1) {
            res += 1;
        }
        if (res > LIMIT_L2) {
            res += 2;
        }
        if (res > LIMIT_L3) {
            res += 4;
        }
        if (res > LIMIT_L4) {
            res += 8;
        }
        if (res > LIMIT_L5) {
            res += 16;
        }
        if (res > LIMIT_L6) {
            res += 32;
        }
        if (res > LIMIT_L7) {
            res += 64;
        }
        if (res > LIMIT_L8) {
            res += 128;
        }
        return res;
    }

    @Test
    @IR (counts = { IRNode.COMPRESS_BITS, " >0 " }, applyIfCPUFeature = {"bmi2" , "true"})
    public long test18(long src, long mask) {
        src = Math.max(BOUND1_LO_L, Math.min(src, BOUND1_HI_L));
        mask = Math.max(BOUND2_LO_L, Math.min(mask, BOUND2_HI_L));
        long res = Long.compress(src, mask);

        // Check the result with some random value ranges, if any of the
        // following conditions incorrectly constant folds the result will
        // not comply with the interpreter.

        if (res > LIMIT_L1) {
            res += 1;
        }
        if (res > LIMIT_L2) {
            res += 2;
        }
        if (res > LIMIT_L3) {
            res += 4;
        }
        if (res > LIMIT_L4) {
            res += 8;
        }
        if (res > LIMIT_L5) {
            res += 16;
        }
        if (res > LIMIT_L6) {
            res += 32;
        }
        if (res > LIMIT_L7) {
            res += 64;
        }
        if (res > LIMIT_L8) {
            res += 128;
        }
        return res;
    }

    @Run (test = "test18")
    public void run18() {
        long actual = 0;
        long expected = 0;

        for (int i = 0; i < 100; i++) {
            long arg1 = GEN_L.next();
            long arg2 = GEN_L.next();

            actual += test18(arg1, arg2);
            expected += test18_interpreted(arg1, arg2);
        }
        Asserts.assertEQ(actual, expected);
    }

    @DontCompile
    public long test19_interpreted(long src, long mask) {
        src = Math.max(BOUND1_LO_L, Math.min(src, BOUND1_HI_L));
        mask = Math.max(BOUND2_LO_L, Math.min(mask, BOUND2_HI_L));
        long res = Long.expand(src, mask);

        if (res > LIMIT_L1) {
            res += 1;
        }
        if (res > LIMIT_L2) {
            res += 2;
        }
        if (res > LIMIT_L3) {
            res += 4;
        }
        if (res > LIMIT_L4) {
            res += 8;
        }
        if (res > LIMIT_L5) {
            res += 16;
        }
        if (res > LIMIT_L6) {
            res += 32;
        }
        if (res > LIMIT_L7) {
            res += 64;
        }
        if (res > LIMIT_L8) {
            res += 128;
        }
        return res;
    }

    @Test
    @IR (counts = { IRNode.EXPAND_BITS, " >0 " }, applyIfCPUFeature = {"bmi2" , "true"})
    public long test19(long src, long mask) {
        src = Math.max(BOUND1_LO_L, Math.min(src, BOUND1_HI_L));
        mask = Math.max(BOUND2_LO_L, Math.min(mask, BOUND2_HI_L));
        long res = Long.expand(src, mask);

        // Check the result with some random value ranges, if any of the
        // following conditions incorrectly constant folds the result will
        // not comply with the interpreter.

        if (res > LIMIT_L1) {
            res += 1;
        }
        if (res > LIMIT_L2) {
            res += 2;
        }
        if (res > LIMIT_L3) {
            res += 4;
        }
        if (res > LIMIT_L4) {
            res += 8;
        }
        if (res > LIMIT_L5) {
            res += 16;
        }
        if (res > LIMIT_L6) {
            res += 32;
        }
        if (res > LIMIT_L7) {
            res += 64;
        }
        if (res > LIMIT_L8) {
            res += 128;
        }
        return res;
    }

    @Run (test = "test19")
    public void run19() {
        long actual = 0;
        long expected = 0;

        for (int i = 0; i < 100; i++) {
            long arg1 = GEN_L.next();
            long arg2 = GEN_L.next();

            actual += test19(arg1, arg2);
            expected += test19_interpreted(arg1, arg2);
        }
        Asserts.assertEQ(actual, expected);
    }

    @Test
    @IR (counts = { IRNode.COMPRESS_BITS, " >0 " }, applyIfCPUFeature = { "bmi2", "true" })
    public static long test20(int x) {
        // Analysis of when this is used to produce wrong results on Windows:
        //
        // src  = -2683206580L = ffff_ffff_6011_844c
        // mask = 0..maxuint, at runtime: 4294950911 = 0xffff_bfff
        //
        // Hence we go to the B) case of CompressBits in bitshuffle_value
        //
        // mask_bit_width = 64
        // clz = 32
        // result_bit_width = 32
        //
        // So we have result_bit_width < mask_bit_width
        //
        // And we do:
        // lo = result_bit_width == mask_bit_width ? lo : 0L;
        // -> lo = 0
        //
        // And we do:
        // hi = MIN2((jlong)((1UL << result_bit_width) - 1L), hi);
        //
        // But watch out: on windows 1UL is only a 32 bit value. Intended was probably 1ULL.
        // So when we calculate "1UL << 32", we just get 1. And so then hi would be 0 now.
        // If we instead did "1ULL << 32", we would get 0x1_0000_0000, and hi = 0xffff_ffff.
        //
        // We create type [lo, hi]:
        // Windows: [0, 0]           -> constant zero
        // correct:  [0, 0xffff_ffff] -> does not constant fold. At runtime: 0x3008_c44c
        return Long.compress(-2683206580L, Integer.toUnsignedLong(x));
    }

    @DontCompile
    public static long test20_interpreted(int x) {
        return Long.compress(-2683206580L, Integer.toUnsignedLong(x));
    }

    @Run (test = "test20")
    public void run20() {
        for (int i = 0; i < 100; i++) {
            int arg = GEN_I.next();

            long actual = test20(arg);
            long expected = test20_interpreted(arg);
            Asserts.assertEQ(actual, expected);
        }
    }

    @Test
    @IR (counts = { IRNode.COMPRESS_BITS, " >0 " }, applyIfCPUFeature = { "bmi2", "true" })
    public static long test21(long x) {
        // Analysis of when this is used to produce wrong results on Windows:
        //
        // Very similar to case in test20, but this time we go into the A) case.
        //
        // maskcon = 0xffff_ffff
        // bitcount = 32
        //
        // And now the problematic part:
        // hi = (1UL << bitcount) - 1;
        //
        // On Windows, this becomes 0 (but it should be 0xffff_ffff).
        // Hence, the range wrongly collapses to [0, 0], and the CompressBits node
        // is wrongly replaced with a zero constant.
        return Long.compress(x, 0xffff_ffffL);
    }

    @DontCompile
    public static long test21_interpreted(long x) {
        return Long.compress(x, 0xffff_ffffL);
    }

    @Run (test = "test21")
    public void run21() {
        for (int i = 0; i < 100; i++) {
            int arg = GEN_I.next();

            long actual = test21(arg);
            long expected = test21_interpreted(arg);
            Asserts.assertEQ(actual, expected);
        }
    }

    public static void main(String[] args) {
        TestFramework.run(TestBitCompressValueTransform.class);
    }
}
