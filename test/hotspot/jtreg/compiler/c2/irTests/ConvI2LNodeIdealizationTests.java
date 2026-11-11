/*
 * Copyright (c) 2026, Alibaba Group Holding Limited. All rights reserved.
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
 * @summary Test that Ideal transformations of ConvI2LNode are being performed as expected.
 * @requires vm.bits == "64"
 * @library /test/lib /
 * @run driver compiler.c2.irTests.ConvI2LNodeIdealizationTests
 */
public class ConvI2LNodeIdealizationTests {

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Run(test = { "test1", "test2", "test3", "test4", "test5" })
    public void runMethod() {
        long a = RunInfo.getRandom().nextLong();
        int[] arr = new int[2048];

        assertResult(0L, arr);
        assertResult(10L, arr);
        assertResult(-10L, arr);
        assertResult(Long.MIN_VALUE, arr);
        assertResult(Long.MAX_VALUE, arr);
        assertResult(0x1234_5678_9abcL, arr);
        assertResult(0xffff_ffff_8000_0000L, arr);
        assertResult(a, arr);
    }

    @DontCompile
    public void assertResult(long a, int[] arr) {
        Asserts.assertEQ((long)((int)a & 0x7ff), test1(a));
        Asserts.assertEQ((long)((int)a & Integer.MAX_VALUE), test2(a));
        Asserts.assertEQ((long)((int)a & -8), test3(a));
        Asserts.assertEQ(a & 0x7ffL, test4(a));
        Asserts.assertEQ(arr[(int)a & 0x7ff], test5(a, arr));
    }

    @Test
    @IR(counts = { IRNode.AND_L, "1" })
    @IR(failOn = { IRNode.AND_I,
                   IRNode.CONV_I2L,
                   IRNode.CONV_L2I })
    // Checks ConvI2L(AndI(ConvL2I(a), 0x7ff)) => AndL(a, 0x7ff)
    public long test1(long a) {
        return (int)a & 0x7ff;
    }

    @Test
    @IR(counts = { IRNode.AND_L, "1" })
    @IR(failOn = { IRNode.AND_I,
                   IRNode.CONV_I2L,
                   IRNode.CONV_L2I })
    // Checks ConvI2L(AndI(ConvL2I(a), maxint)) => AndL(a, maxint), i.e. that the
    // optimization also works for masks with bit 30 set
    public long test2(long a) {
        return (int)a & Integer.MAX_VALUE;
    }

    @Test
    @IR(counts = { IRNode.AND_I, "1",
                   IRNode.CONV_I2L, "1",
                   IRNode.CONV_L2I, "1" })
    @IR(failOn = { IRNode.AND_L })
    // Checks that a negative mask is not commuted: the AND result can be
    // negative, so ConvI2L sign-extends it and AndL would not be equivalent
    public long test3(long a) {
        return (int)a & -8;
    }

    @Test
    @IR(counts = { IRNode.AND_L, "1" })
    @IR(failOn = { IRNode.AND_I,
                   IRNode.CONV_I2L,
                   IRNode.CONV_L2I })
    // Checks that a plain long AND is not disturbed
    public long test4(long a) {
        return a & 0x7ffL;
    }

    @Test
    @IR(counts = { IRNode.AND_I, "1",
                   IRNode.CONV_I2L, "1",
                   IRNode.CONV_L2I, "1" })
    @IR(failOn = { IRNode.AND_L })
    // Checks that the optimization is not applied when the AndI has another
    // user: the array range check still needs it, so commuting would compute
    // the mask twice
    public int test5(long a, int[] arr) {
        return arr[(int)a & 0x7ff];
    }
}
