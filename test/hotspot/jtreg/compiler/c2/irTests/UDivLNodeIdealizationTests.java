/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
import jdk.test.lib.Utils;

import java.util.Random;

import compiler.lib.ir_framework.*;

/*
 * @test
 * @bug 8332268
 * @summary Test that Ideal transformations of UDivLNode* are being performed as expected.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.UDivLNodeIdealizationTests
 */
public class UDivLNodeIdealizationTests {
    public static final long RANDOM_POWER_OF_2 = 1L << (1 + Utils.getRandomInstance().nextInt(62));

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Run(test = {"constant", "identity", "identityAgain", "identityAgainButBig", "identityThird",
            "retainDenominator", "divByPow2", "divByPow2Big", "divByPow2Random"})
    public void runMethod() {
        long a = RunInfo.getRandom().nextLong();
        a = (a == 0) ? 1 : a;
        long b = RunInfo.getRandom().nextLong();
        b = (b == 0) ? 1 : b;

        long min = Long.MIN_VALUE;
        long max = Long.MAX_VALUE;

        assertResult(0, 0, true);
        assertResult(a, b, false);
        assertResult(min, min, false);
        assertResult(max, max, false);
    }

    @DontCompile
    public void assertResult(long a, long b, boolean shouldThrow) {
        try {
            Asserts.assertEQ(Long.divideUnsigned(a, a), constant(a));
            Asserts.assertFalse(shouldThrow, "Expected an exception to be thrown.");
        } catch (ArithmeticException e) {
            Asserts.assertTrue(shouldThrow, "Did not expect an exception to be thrown.");
        }

        try {
            Asserts.assertEQ(Long.divideUnsigned((a * b), b), retainDenominator(a, b));
            Asserts.assertFalse(shouldThrow, "Expected an exception to be thrown.");
        } catch (ArithmeticException e) {
            Asserts.assertTrue(shouldThrow, "Did not expect an exception to be thrown.");
        }

        try {
            Asserts.assertEQ(Long.divideUnsigned(a, Long.divideUnsigned(b, b)), identityThird(a, b));
            Asserts.assertFalse(shouldThrow, "Expected an exception to be thrown.");
        } catch (ArithmeticException e) {
            Asserts.assertTrue(shouldThrow, "Did not expect an exception to be thrown.");
        }

        Asserts.assertEQ(Long.divideUnsigned(a, 1), identity(a));
        Asserts.assertEQ(Long.divideUnsigned(a, Long.divideUnsigned(13, 13)), identityAgain(a));
        Asserts.assertEQ(Long.divideUnsigned(a, 8), divByPow2(a));
        Asserts.assertEQ(Long.divideUnsigned(a, Long.MIN_VALUE), divByPow2Big(a));
        Asserts.assertEQ(Long.divideUnsigned(a, RANDOM_POWER_OF_2), divByPow2Random(a));
        Asserts.assertEQ(Long.divideUnsigned(a, Long.divideUnsigned((1L << 40) + 1, (1L << 40) + 1)), identityAgainButBig(a));
    }

    @Test
    @IR(failOn = {IRNode.UDIV})
    @IR(counts = {IRNode.DIV_BY_ZERO_TRAP, "1"})
    // Checks x / x => 1
    public long constant(long x) {
        return Long.divideUnsigned(x, x);
    }

    @Test
    @IR(failOn = {IRNode.UDIV})
    // Checks x / 1 => x
    public long identity(long x) {
        return Long.divideUnsigned(x, 1L);
    }

    @Test
    @IR(failOn = {IRNode.UDIV})
    // Checks x / (c / c) => x
    public long identityAgain(long x) {
        return Long.divideUnsigned(x, Long.divideUnsigned(13L, 13L));
    }

    @Test
    @IR(failOn = {IRNode.UDIV})
    // Checks x / (c / c) => x
    public long identityAgainButBig(long x) {
        // (1L << 40) + 1 is an arbitrary integer that cannot be optimized by the power of 2 optimizations
        return Long.divideUnsigned(x, Long.divideUnsigned((1L << 40) + 1, (1L << 40) + 1));
    }

    @Test
    @IR(failOn = {IRNode.UDIV})
    @IR(counts = {IRNode.DIV_BY_ZERO_TRAP, "1"})
    // Checks x / (y / y) => x
    public long identityThird(long x, long y) {
        return Long.divideUnsigned(x, Long.divideUnsigned(y, y));
    }

    @Test
    @IR(counts = {IRNode.MUL_L, "1",
            IRNode.UDIV_L, "1",
            IRNode.DIV_BY_ZERO_TRAP, "1"
    })
    public long retainDenominator(long x, long y) {
        return Long.divideUnsigned((x * y), y);
    }

    @Test
    @IR(failOn = {IRNode.UDIV})
    @IR(counts = {IRNode.URSHIFT, "1"})
    // Dividing an unsigned number by 8 is a trivial right shift by 3
    public long divByPow2(long x) {
        return Long.divideUnsigned(x, 8L);
    }

    @Test
    @IR(failOn = {IRNode.UDIV})
    @IR(counts = {IRNode.URSHIFT, "1"})
    public long divByPow2Random(long x) {
        return Long.divideUnsigned(x, RANDOM_POWER_OF_2);
    }

    @Test
    @IR(failOn = {IRNode.UDIV})
    @IR(counts = {IRNode.URSHIFT, "1"})
    public long divByPow2Big(long x) {
        return Long.divideUnsigned(x, Long.MIN_VALUE);
    }
}
