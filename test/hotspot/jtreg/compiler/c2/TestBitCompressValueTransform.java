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
 * @key stress randomness
 * @requires vm.compiler2.enabled & os.simpleArch == "x64"
 * @bug 8350896
 * @library /test/lib /
 * @summary C2: wrong result: Integer/Long.compress gets wrong type from CompressBitsNode::Value.
 * @run main/othervm -Xbatch -XX:-TieredCompilation -XX:CompileThresholdScaling=0.3 compiler.c2.TestBitCompressValueTransform
 */
package compiler.c2;

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
        Asserts.assertGT(res, 0);
    }

    @Test
    @IR (counts = { IRNode.COMPRESS_BITS, " 0 "} , failOn = { IRNode.UNSTABLE_IF_TRAP }, applyIfCPUFeature = { "bmi2", "true" })
    public long test4(long value) {
        long filter_bits = value & 0xF;
        long compress_bits = Long.compress(15, filter_bits);
        if (compress_bits > 15) {
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
        Asserts.assertGT(res, 0L);
    }

    public static void main(String[] args) {
        TestFramework.run(TestBitCompressValueTransform.class);
    }
}
