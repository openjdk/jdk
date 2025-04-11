/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package compiler.c2.irTests;

import jdk.test.lib.Asserts;
import compiler.lib.ir_framework.*;

/*
 * @test
 * @bug 8320330 8349361
 * @summary Test that RShiftLNode optimizations are being performed as expected.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.RShiftLNodeIdealizationTests
 */
public class RShiftLNodeIdealizationTests {
    public static void main(String[] args) {
        TestFramework.run();
    }

    @Run(test = { "test1", "test2", "test3", "test4", "test5", "test6", "test7", "test8", "test9", "test10" })
    public void runMethod() {
        long a = RunInfo.getRandom().nextLong();
        long b = RunInfo.getRandom().nextLong();
        long c = RunInfo.getRandom().nextLong();
        long d = RunInfo.getRandom().nextLong();

        long min = Long.MIN_VALUE;
        long max = Long.MAX_VALUE;

        assertResult(a, 0);
        assertResult(a, b);
        assertResult(b, a);
        assertResult(c, d);
        assertResult(d, c);
        assertResult(min, max);
        assertResult(max, min);
        assertResult(min, min);
        assertResult(max, max);
        assertResult(test7Min, b);
        assertResult(test7Max, b);
        assertResult(test7Min-1, b);
        assertResult(test7Max+1, b);
    }

    @DontCompile
    public void assertResult(long x, long y) {
        Asserts.assertEQ((x >> y) >= 0 ? 0L : 1L, test1(x, y));
        Asserts.assertEQ(((x & 127) >> y) >= 0 ? 0L : 1L, test2(x, y));
        Asserts.assertEQ(((-(x & 127) - 1) >> y) >= 0 ? 0L : 1L, test3(x, y));
        Asserts.assertEQ((x >> 62) > 4 ? 0L : 1L, test4(x, y));
        Asserts.assertEQ((x & test5Mask) >> test5Shift, test5(x));
        Asserts.assertEQ(x, test6(x));
        long x7 = Long.max(Long.min(x, test7Max), test7Min);
        Asserts.assertEQ(((x7 << test7Shift) >> test7Shift), test7(x));
        long x8 = Long.max(Long.min(x, test7Max+1), test7Min);
        Asserts.assertEQ((x8 << test7Shift) >> test7Shift, test8(x));
        long x9 = Long.max(Long.min(x, test7Max), test7Min-1);
        Asserts.assertEQ((x9 << test7Shift) >> test7Shift, test9(x));
        Asserts.assertEQ(((x7 << test7Shift) >> test10Shift), test10(x));
    }

    @Test
    @IR(counts = { IRNode.RSHIFT, "1" })
    public long test1(long x, long y) {
        return (x >> y) >= 0 ? 0 : 1;
    }

    @Test
    @IR(failOn = { IRNode.RSHIFT })
    public long test2(long x, long y) {
        return ((x & 127) >> y) >= 0 ? 0L : 1L;
    }

    @Test
    @IR(failOn = { IRNode.RSHIFT })
    public long test3(long x, long y) {
        return ((-(x & 127) - 1) >> y) >= 0 ? 0L : 1L;
    }

    @Test
    @IR(failOn = { IRNode.RSHIFT })
    public long test4(long x, long y) {
        return (x >> 62) > 4 ? 0L : 1L;
    }

    final static int test5Shift = RunInfo.getRandom().nextInt(1, 64);
    final static long test5Mask = -1L << test5Shift;

    @Test
    @IR(counts = { IRNode.RSHIFT_L, "1" })
    @IR(failOn = { IRNode.AND_L })
    public long test5(long x) {
        return (x & test5Mask) >> test5Shift;
    }

    final static int test6Shift = RunInfo.getRandom().nextInt(Integer.MAX_VALUE / 64) * 64 ;

    @Test
    @IR(failOn = { IRNode.RSHIFT_L })
    public long test6(long x) {
        return (x >> test6Shift);
    }

    // See comment in RShiftINodeIdealizationTests
    final static int test7Shift = RunInfo.getRandom().nextInt(1, 64);
    final static long test7Min = -1L << (64 - test7Shift - 1);
    final static long test7Max = ~test7Min;

    @Test
    @IR(failOn = { IRNode.RSHIFT_L, IRNode.LSHIFT_L })
    public long test7(long x) {
        x = Long.max(Long.min(x, test7Max), test7Min);
        return ((x << test7Shift) >> test7Shift);
    }

    @Test
    @IR(counts = { IRNode.RSHIFT_L, "1", IRNode.LSHIFT_L, "1" })
    public long test8(long x) {
        x = Long.max(Long.min(x, test7Max+1), test7Min);
        return ((x << test7Shift) >> test7Shift);
    }

    @Test
    @IR(counts = { IRNode.RSHIFT_L, "1", IRNode.LSHIFT_L, "1" })
    public long test9(long x) {
        x = Long.max(Long.min(x, test7Max), test7Min-1);
        return ((x << test7Shift) >> test7Shift);
    }

    final static int test10Shift = RunInfo.getRandom().nextInt(64);
    @Test
    public long test10(long x) {
        x = Long.max(Long.min(x, test7Max), test7Min);
        return ((x << test7Shift) >> test10Shift);
    }
}
