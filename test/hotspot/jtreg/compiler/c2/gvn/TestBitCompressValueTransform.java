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
 * @bug 8350896
 * @library /test/lib /
 * @summary C2: wrong result: Integer/Long.compress gets wrong type from CompressBitsNode::Value.
 * @run driver compiler.c2.gvn.TestBitCompressValueTransform
 */
package compiler.c2.gvn;

import jdk.test.lib.Asserts;
import compiler.lib.ir_framework.*;

public class TestBitCompressValueTransform {

    public static final int  field_I = 0x400_0000;
    public static final long field_L = 0x400_0000_0000_0000L;
    public static final int  gold_I = Integer.valueOf(Integer.compress(0x8000_0000, field_I));
    public static final long gold_L = Long.valueOf(Long.compress(0x8000_0000_0000_0000L, field_L));

    @Test
    @IR (counts = { IRNode.COMPRESS_BITS, " >0 " }, applyIfCPUFeature = { "bmi2", "true" })
    public long test1(long value) {
        return Long.compress(0x8000_0000_0000_0000L, value);
    }

    @Run(test = "test1")
    public void run1(RunInfo info) {
        long res = 0;
        for (int i = 0; i < 100000; i++) {
            res |= test1(field_L);
        }
        Asserts.assertEQ(res, gold_L);
    }


    @Test
    @IR (counts = { IRNode.COMPRESS_BITS, " >0 " }, applyIfCPUFeature = { "bmi2", "true" })
    public int test2(int value) {
        return Integer.compress(0x8000_0000, value);
    }

    @Run(test = "test2")
    public void run2(RunInfo info) {
        int res = 0;
        for (int i = 0; i < 100000; i++) {
            res |= test2(field_I);
        }
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
    public void run3(RunInfo info) {
        int res = 0;
        for (int i = 1; i < 100000; i++) {
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
    public void run4(RunInfo info) {
        long res = 0;
        for (long i = 1; i < 100000; i++) {
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
    public void run5(RunInfo info) {
        long res = 0;
        for (int i = -10000; i < 100000; i++) {
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
    public void run6(RunInfo info) {
        long res = 0;
        for (int i = -10000; i < 100000; i++) {
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
    public void run7(RunInfo info) {
        long res = Long.MIN_VALUE;
        for (int i = -10000; i < 100000; i++) {
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
    public void run8(RunInfo info) {
        int res = 0;
        for (int i = -10000; i < 100000; i++) {
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
    public void run9(RunInfo info) {
        int res = 0;
        for (int i = -10000; i < 100000; i++) {
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
    public void run10(RunInfo info) {
        int res = Integer.MIN_VALUE;
        for (int i = -10000; i < 100000; i++) {
            res = Integer.max(test10(i), res);
        }
        Asserts.assertGTE(10000, res);
    }

    @Test
    @IR (counts = { IRNode.COMPRESS_BITS, " 0 " })
    public int test11(int value) {
        // For constant zero input, compress is folded to zero
        int mask = Integer.min(10000, Integer.max(0, value));
        return Integer.compress(0, mask);
    }

    @Run(test = "test11")
    public void run11(RunInfo info) {
        int res = 0;
        for (int i = -10000; i < 100000; i++) {
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
    public void run12(RunInfo info) {
        long res = 0;
        for (int i = -10000; i < 100000; i++) {
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
    public void run13(RunInfo info) {
        int res = 0;
        for (int i = -10000; i < 100000; i++) {
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
    public void run14(RunInfo info) {
        long res = 0;
        for (int i = -10000; i < 100000; i++) {
            res |= test14(i);
        }
        Asserts.assertEQ(0L, res);
    }

    @Test
    @IR (counts = { IRNode.COMPRESS_BITS, " 1 " })
    public int test15(int src, int mask) {
        // src_type = [min_int + 1, -1]
        src = Math.max(Integer.MIN_VALUE + 1, Math.min(src, -1));
        return Integer.compress(src, mask);
    }

    @Run (test = "test15")
    public void run15(RunInfo info) {
        int res = 0;
        for (int i = 0; i < 100000; i++) {
            res |= test15(0, 0);
        }
        Asserts.assertEQ(0, res);
    }

    public static void main(String[] args) {
        TestFramework.run(TestBitCompressValueTransform.class);
    }
}
