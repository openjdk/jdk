/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8267265
 * @summary Test that Ideal transformations of ModLNode* are being performed as expected.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.ModLNodeIdealizationTests
 */
public class ModLNodeIdealizationTests {
    public static final long RANDOM_POWER_OF_2 = 1L << (1 + Utils.getRandomInstance().nextInt(62));

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Run(test = {"constant", "constantAgain", "powerOf2", "powerOf2Random", "powerOf2Minus1"})
    public void runMethod() {
        long a = RunInfo.getRandom().nextLong();
        a = (a == 0) ? 2 : a;
        long b = RunInfo.getRandom().nextLong();
        b = (b == 0) ? 2 : b;

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
            Asserts.assertEQ(a % a, constant(a));
            Asserts.assertFalse(shouldThrow, "Expected an exception to be thrown.");
        } catch (ArithmeticException e) {
            Asserts.assertTrue(shouldThrow, "Did not expect an exception to be thrown.");
        }

        Asserts.assertEQ(a % (1L << 33), powerOf2(a));
        Asserts.assertEQ(a % ((1L << 33) - 1), powerOf2Minus1(a));
        Asserts.assertEQ(a % 1, constantAgain(a));
    }

    @Test
    @IR(failOn = {IRNode.MOD_L})
    @IR(counts = {IRNode.DIV_BY_ZERO_TRAP, "1"})
    // Checks x % x => 0
    public long constant(long x) {
        return x % x;
    }

    @Test
    @IR(failOn = {IRNode.MOD_L})
    // Checks x % 1 => 0
    public long constantAgain(long x) {
        return x % 1;
    }

    @Test
    @IR(failOn = {IRNode.MOD_L, IRNode.DIV})
    @IR(counts = {IRNode.AND_L, "1"})
    // If the dividend is positive, and divisor is of the form 2^k, we can use a simple bit mask.
    public long powerOf2(long x) {
        return x % (1L << 33);
    }

    @Test
    @IR(failOn = {IRNode.MOD_L, IRNode.DIV})
    @IR(counts = {IRNode.AND_L, "1"})
    // If the dividend is positive, and divisor is of the form 2^k, we can use a simple bit mask.
    public long powerOf2Random(long x) {
        return x % RANDOM_POWER_OF_2;
    }

    @Test
    @IR(failOn = {IRNode.MOD_L})
    @IR(counts = {IRNode.AND_L, ">=1", IRNode.RSHIFT, ">=1", IRNode.CMP_L, "2"})
    // Special optimization for the case 2^k-1 for bigger k
    public long powerOf2Minus1(long x) {
        return x % ((1L << 33) - 1);
    }
}
