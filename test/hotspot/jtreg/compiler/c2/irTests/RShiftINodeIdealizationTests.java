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
 * @summary Test that RShiftINode optimizations are being performed as expected.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.RShiftINodeIdealizationTests
 */
public class RShiftINodeIdealizationTests {
    public static void main(String[] args) {
        TestFramework.run();
    }

    @Run(test = { "test1", "test2", "test3", "test4", "test5", "test6", "test7", "test8", "test9", "test10" })
    public void runMethod() {
        int a = RunInfo.getRandom().nextInt();
        int b = RunInfo.getRandom().nextInt();
        int c = RunInfo.getRandom().nextInt();
        int d = RunInfo.getRandom().nextInt();

        int min = Integer.MIN_VALUE;
        int max = Integer.MAX_VALUE;

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
    public void assertResult(int x, int y) {
        Asserts.assertEQ((x >> y) >= 0 ? 0 : 1, test1(x, y));
        Asserts.assertEQ(((x & 127) >> y) >= 0 ? 0 : 1, test2(x, y));
        Asserts.assertEQ(((-(x & 127) - 1) >> y) >= 0 ? 0 : 1, test3(x, y));
        Asserts.assertEQ((x >> 30) > 4 ? 0 : 1, test4(x, y));
        Asserts.assertEQ((x & test5Mask) >> test5Shift, test5(x));
        Asserts.assertEQ(x, test6(x));
        int x7 = Integer.max(Integer.min(x, test7Max), test7Min);
        Asserts.assertEQ(((x7 << test7Shift) >> test7Shift), test7(x));
        int x8 = Integer.max(Integer.min(x, test7Max+1), test7Min);
        Asserts.assertEQ((x8 << test7Shift) >> test7Shift, test8(x));
        int x9 = Integer.max(Integer.min(x, test7Max), test7Min-1);
        Asserts.assertEQ((x9 << test7Shift) >> test7Shift, test9(x));
        Asserts.assertEQ((x7 << test7Shift) >> test10Shift, test10(x));
    }

    @Test
    @IR(counts = { IRNode.RSHIFT, "1" })
    public int test1(int x, int y) {
        return (x >> y) >= 0 ? 0 : 1;
    }

    @Test
    @IR(failOn = { IRNode.RSHIFT })
    public int test2(int x, int y) {
        return ((x & 127) >> y) >= 0 ? 0 : 1;
    }

    @Test
    @IR(failOn = { IRNode.RSHIFT })
    public int test3(int x, int y) {
        return ((-(x & 127) - 1) >> y) >= 0 ? 0 : 1;
    }

    @Test
    @IR(failOn = { IRNode.RSHIFT })
    public int test4(int x, int y) {
        return (x >> 30) > 4 ? 0 : 1;
    }

    final static int test5Shift = RunInfo.getRandom().nextInt(1, 32);
    final static int test5Mask = -1 << test5Shift;

    @Test
    @IR(counts = { IRNode.RSHIFT_I, "1" })
    @IR(failOn = { IRNode.AND_I })
    public int test5(int x) {
        return (x & test5Mask) >> test5Shift;
    }

    final static int test6Shift = RunInfo.getRandom().nextInt(Integer.MAX_VALUE / 32) * 32 ;

    @Test
    @IR(failOn = { IRNode.RSHIFT_I })
    public int test6(int x) {
        return (x >> test6Shift);
    }


    // test that (x << shift) >> shift) is a nop if upper bits of x
    // that are shifted left and then right + one bit are all ones or
    // zeroes. For instance:
    // shift = 15, min = 0xffff0000, max=0x0000ffff
    // min << shift = 0x80000000, (min << shift) >> shift = 0xffff0000
    // (min+1) << shift = 0x80008000, ((min+1) << shift) >> shift = 0xffff0001
    // (max-1) << shift = 0x7fff0000, ((max-1) << shift) >> shift = 0x0000fffe
    // max << shift = 0x7fff8000, (min << shift) >> shift = 0x0000ffff
    // But:
    // (min-1) << shift = 7fff8000, ((min-1) << shift) >> shift = 0x0000ffff != 0xfffeffff
    // (max+1) << shift = 0x80000000, ((max+1) << shift) >> shift = 0xffff0000 != 0x00010000
    final static int test7Shift = RunInfo.getRandom().nextInt(1, 32);
    final static int test7Min = -1 << (32 - test7Shift - 1);
    final static int test7Max = ~test7Min;

    @Test
    @IR(failOn = { IRNode.RSHIFT_I, IRNode.LSHIFT_I })
    public int test7(int x) {
        x = Integer.max(Integer.min(x, test7Max), test7Min);
        return ((x << test7Shift) >> test7Shift);
    }

    @Test
    @IR(counts = { IRNode.RSHIFT_I, "1", IRNode.LSHIFT_I, "1" })
    public int test8(int x) {
        x = Integer.max(Integer.min(x, test7Max+1), test7Min);
        return ((x << test7Shift) >> test7Shift);
    }

    @Test
    @IR(counts = { IRNode.RSHIFT_I, "1", IRNode.LSHIFT_I, "1" })
    public int test9(int x) {
        x = Integer.max(Integer.min(x, test7Max), test7Min-1);
        return ((x << test7Shift) >> test7Shift);
    }

    final static int test10Shift = RunInfo.getRandom().nextInt(32);

    @Test
    public int test10(int x) {
        x = Integer.max(Integer.min(x, test7Max), test7Min);
        return ((x << test7Shift) >> test10Shift);
    }
}
