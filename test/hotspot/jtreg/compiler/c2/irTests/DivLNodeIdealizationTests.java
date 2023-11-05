/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8267265
 * @summary Test that Ideal transformations of DivLNode* are being performed as expected.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.DivLNodeIdealizationTests
 */
public class DivLNodeIdealizationTests {
    public static void main(String[] args) {
        TestFramework.run();
    }

    @Run(test = {"constant", "identity", "identityAgain", "identityThird",
                 "retainDenominator", "divByNegOne", "divByPow2And",
                 "divByPow2And1",  "divByPow2", "divByNegPow2"})
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
            Asserts.assertEQ(a / a, constant(a));
            Asserts.assertFalse(shouldThrow, "Expected an exception to be thrown.");
        }
        catch (ArithmeticException e) {
            Asserts.assertTrue(shouldThrow, "Did not expected an exception to be thrown.");
        }

        try {
            Asserts.assertEQ((a * b) / b, retainDenominator(a, b));
            Asserts.assertFalse(shouldThrow, "Expected an exception to be thrown.");
        }
        catch (ArithmeticException e) {
            Asserts.assertTrue(shouldThrow, "Did not expected an exception to be thrown.");
        }

        try {
            Asserts.assertEQ(a / (b / b), identityThird(a, b));
            Asserts.assertFalse(shouldThrow, "Expected an exception to be thrown.");
        }
        catch (ArithmeticException e) {
            Asserts.assertTrue(shouldThrow, "Did not expected an exception to be thrown.");
        }

        Asserts.assertEQ(a / 1        , identity(a));
        Asserts.assertEQ(a / (13 / 13), identityAgain(a));
        Asserts.assertEQ(a / -1       , divByNegOne(a));
        Asserts.assertEQ((a & -4) / 2 , divByPow2And(a));
        Asserts.assertEQ((a & -2) / 2 , divByPow2And1(a));
        Asserts.assertEQ(a / 8        , divByPow2(a));
        Asserts.assertEQ(a / -8       , divByNegPow2(a));
    }

    @Test
    @IR(failOn = {IRNode.DIV})
    @IR(counts = {IRNode.DIV_BY_ZERO_TRAP, "1"})
    // Checks x / x => 1
    public long constant(long x) {
        return x / x;
    }

    @Test
    @IR(failOn = {IRNode.DIV})
    // Checks x / 1 => x
    public long identity(long x) {
        return x / 1L;
    }

    @Test
    @IR(failOn = {IRNode.DIV})
    // Checks x / (c / c) => x
    public long identityAgain(long x) {
        return x / (13L / 13L);
    }

    @Test
    @IR(failOn = {IRNode.DIV})
    @IR(counts = {IRNode.DIV_BY_ZERO_TRAP, "1"})
    // Checks x / (y / y) => x
    public long identityThird(long x, long y) {
        return x / (y / y);
    }

    @Test
    @IR(counts = {IRNode.MUL_L, "1",
                  IRNode.DIV_L, "1",
                  IRNode.DIV_BY_ZERO_TRAP, "1"
                 })
    // Hotspot should keep the division because it may cause a division by zero trap
    public long retainDenominator(long x, long y) {
        return (x * y) / y;
    }

    @Test
    @IR(failOn = {IRNode.DIV})
    @IR(counts = {IRNode.SUB, "1"})
    // Checks x / -1 => 0 - x
    public long divByNegOne(long x) {
        return x / -1L;
    }

    @Test
    @IR(failOn = {IRNode.DIV})
    @IR(counts = {IRNode.AND, "1",
                  IRNode.RSHIFT, "1",
                 })
    // Checks (x & -(2^c0)) / 2^c1 => (x >> c1) & (2^c0 >> c1) => (x >> c1) & c3 where 2^c0 > |2^c1| "and" c3 = 2^c0 >> c1
    // Having a large enough and in the dividend removes the need to account for
    // rounding when converting to shifts and multiplies as in divByPow2()
    public long divByPow2And(long x) {
        return (x & -4L) / 2L;
    }

    @Test
    @IR(failOn = {IRNode.DIV, IRNode.AND})
    @IR(counts = {IRNode.RSHIFT, "1"})
    // Checks (x & -(2^c0)) / 2^c0 => x >> c0
    // If the negative of the constant within the & equals the divisor then
    // the and can be removed as it only affects bits that will be shifted off
    public long divByPow2And1(long x) {
        return (x & -2L) / 2L;
    }

    @Test
    @IR(failOn = {IRNode.DIV})
    @IR(counts = {IRNode.URSHIFT, "1",
                  IRNode.RSHIFT, "2",
                  IRNode.ADD, "1",
                 })
    // Checks x / 2^c0 => x + ((x >>)ith negative numbers. Since x may be negative
    // in this method, an additional add, logical right shift, and signed shift
    // are needed to account for rounding.
    public long divByPow2(long x) {
        return x / 8L;
    }

    @Test
    @IR(failOn = {IRNode.DIV})
    @IR(counts = {IRNode.URSHIFT, "1",
                  IRNode.RSHIFT, "2",
                  IRNode.ADD, "1",
                  IRNode.SUB, "1",
                 })
    // Checks x / -(2^c0) =>0 - (x + ((x >> (32-1)) >>> (32 - c0)) >> c0) => 0 -  (x + ((x >> 31) >>> c1) >> c0) where c1 = 32 - c0
    // Similar to divByPow2() except a negative divisor turns positive.
    // After the transformations, 0 is subtracted by the whole expression
    // to account for the negative.
    public long divByNegPow2(long x) {
        return x / -8L;
    }
}
