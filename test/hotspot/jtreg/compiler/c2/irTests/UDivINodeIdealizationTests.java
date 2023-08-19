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
 * @bug 8282365
 * @summary Test that Ideal transformations of UDivINode and UModINode are
 * being performed as expected.
 *
 * @requires os.arch=="amd64" | os.arch=="x86_64"
 * @library /test/lib /
 * @run driver compiler.c2.irTests.UDivINodeIdealizationTests
 */
public class UDivINodeIdealizationTests {
    public static void main(String[] args) {
        TestFramework.run();
    }

    @Run(test = {"constantDiv", "identity", "identityAgain", "identityThird",
                 "retainDenominator", "divByPow2", "largeDivisorCon", "largeDivisorVar",
                 "magicDiv13", "magicDiv7", "magicDiv13Bounded",
                 "constantMod", "constantModAgain", "modByPow2", "magicMod13"})
    public void runMethod() {
        int a = RunInfo.getRandom().nextInt();
            a = (a == 0) ? 1 : a;
        int b = RunInfo.getRandom().nextInt();
            b = (b == 0) ? 1 : b;

        int min = Integer.MIN_VALUE;
        int max = Integer.MAX_VALUE;

        assertResult(0, 0, true);
        assertResult(a, b, false);
        assertResult(min, min, false);
        assertResult(max, max, false);
    }

    @DontCompile
    public int udiv(int a, int b) {
        return Integer.divideUnsigned(a, b);
    }

    @DontCompile
    public int umod(int a, int b) {
        return Integer.remainderUnsigned(a, b);
    }

    @DontCompile
    public void assertResult(int a, int b, boolean shouldThrow) {
        try {
            Asserts.assertEQ(1, constantDiv(a));
            Asserts.assertFalse(shouldThrow, "Expected an exception to be thrown.");
        }
        catch (ArithmeticException e) {
            Asserts.assertTrue(shouldThrow, "Did not expected an exception to be thrown.");
        }

        try {
            Asserts.assertEQ(a, identityThird(a, b));
            Asserts.assertFalse(shouldThrow, "Expected an exception to be thrown.");
        }
        catch (ArithmeticException e) {
            Asserts.assertTrue(shouldThrow, "Did not expected an exception to be thrown.");
        }

        try {
            Asserts.assertEQ(udiv(a * b, b), retainDenominator(a, b));
            Asserts.assertFalse(shouldThrow, "Expected an exception to be thrown.");
        }
        catch (ArithmeticException e) {
            Asserts.assertTrue(shouldThrow, "Did not expected an exception to be thrown.");
        }

        try {
            Asserts.assertEQ(0, constantMod(a));
            Asserts.assertFalse(shouldThrow, "Expected an exception to be thrown.");
        }
        catch (ArithmeticException e) {
            Asserts.assertTrue(shouldThrow, "Did not expected an exception to be thrown.");
        }

        Asserts.assertEQ(a, identity(a));
        Asserts.assertEQ(a, identityAgain(a));
        Asserts.assertEQ(udiv(a, 8), divByPow2(a));
        Asserts.assertEQ(udiv(a, -7), largeDivisorCon(a));
        Asserts.assertEQ(udiv(a, Math.min((short)b, -1)), largeDivisorVar(a, b));
        Asserts.assertEQ(udiv(a, 13), magicDiv13(a));
        Asserts.assertEQ(udiv(a, 7), magicDiv7(a));
        Asserts.assertEQ(udiv((char)a, 13), magicDiv13Bounded(a));
        Asserts.assertEQ(umod(a, 1), constantModAgain(a));
        Asserts.assertEQ(umod(a, 8), modByPow2(a));
        Asserts.assertEQ(umod(a, 13), magicMod13(a));
    }

    @Test
    @IR(failOn = {IRNode.UDIV_I})
    @IR(counts = {IRNode.DIV_BY_ZERO_TRAP, "1"})
    // Checks x / x => 1
    public int constantDiv(int x) {
        return Integer.divideUnsigned(x, x);
    }

    @Test
    @IR(failOn = {IRNode.UDIV_I})
    // Checks x / 1 => x
    public int identity(int x) {
        return Integer.divideUnsigned(x, 1);
    }

    @Test
    @IR(failOn = {IRNode.UDIV_I})
    // Checks x / (c / c) => x
    public int identityAgain(int x) {
        return Integer.divideUnsigned(x, Integer.divideUnsigned(13, 13));
    }

    @Test
    @IR(failOn = {IRNode.UDIV_I})
    @IR(counts = {IRNode.DIV_BY_ZERO_TRAP, "1"})
    // Checks x / (y / y) => x
    public int identityThird(int x, int y) {
        return Integer.divideUnsigned(x, Integer.divideUnsigned(y, y));
    }

    @Test
    @IR(counts = {IRNode.MUL_I, "1",
                  IRNode.UDIV_I, "1",
                  IRNode.DIV_BY_ZERO_TRAP, "1"
                 })
    // Hotspot should keep the division because it may cause a division by zero trap
    public int retainDenominator(int x, int y) {
        return Integer.divideUnsigned(x * y, y);
    }

    @Test
    @IR(failOn = {IRNode.UDIV_I})
    @IR(counts = {IRNode.URSHIFT_I, "1"})
    // Checks x / 2^c0 => x >>> c0
    public int divByPow2(int x) {
        return Integer.divideUnsigned(x, 8);
    }

    @Test
    @IR(failOn = {IRNode.UDIV_I})
    @IR(counts = {IRNode.CMP_U, "1",
                  IRNode.CMOVE_I, "1"
                 })
    // Checks x / d => x u>= d ? 1 : 0 for large d
    public int largeDivisorCon(int x) {
        return Integer.divideUnsigned(x, -7);
    }

    @Test
    @IR(failOn = {IRNode.UDIV_I})
    @IR(counts = {IRNode.CMP_U, "1",
                  IRNode.CMOVE_I, "1"
                 })
    // Checks x / d => x u>= d ? 1 : 0 for large d
    public int largeDivisorVar(int x, int y) {
        return Integer.divideUnsigned(x, Math.min((short)y, -1));
    }

    @Test
    @IR(failOn = {IRNode.UDIV_I})
    @IR(counts = {IRNode.MUL_L, "1",
                  IRNode.URSHIFT_L, "1",
                  IRNode.CONV_I2L, "1",
                  IRNode.CONV_L2I, "1",
                 })
    // Checks magic int division occurs in general when dividing by a non power of 2.
    // The constant derived from 13 lies inside the limit of a u32
    public int magicDiv13(int x) {
        return Integer.divideUnsigned(x, 13);
    }

    @Test
    @IR(failOn = {IRNode.UDIV_I})
    @IR(counts = {IRNode.MUL_L, "1",
                  IRNode.ADD_L, "1",
                  IRNode.URSHIFT_L, "1",
                  IRNode.CONV_I2L, "1",
                  IRNode.CONV_L2I, "1",
                 })
    // Checks magic int division occurs in general when dividing by a non power of 2.
    // The constant derived from 7 lies outside the limit of a u32 but inside the limit
    // of a u33
    public int magicDiv7(int x) {
        return Integer.divideUnsigned(x, 7);
    }

    @Test
    @IR(failOn = {IRNode.UDIV_I})
    @IR(counts = {IRNode.MUL_I, "1",
                  IRNode.URSHIFT_I, "1"
                 })
    // Checks magic int division occurs in general when dividing by a non power of 2.
    // When the dividend is bounded, we can use smaller constant and do not need to use
    // u64 arithmetic
    public int magicDiv13Bounded(int x) {
        return Integer.divideUnsigned((char)x, 13);
    }

    @Test
    @IR(failOn = {IRNode.UMOD_I})
    @IR(counts = {IRNode.DIV_BY_ZERO_TRAP, "1"})
    // Checks x % x => 0
    public int constantMod(int x) {
        return Integer.remainderUnsigned(x, x);
    }

    @Test
    @IR(failOn = {IRNode.UMOD_I})
    // Checks x % 1 => 0
    public int constantModAgain(int x) {
        return Integer.remainderUnsigned(x, 1);
    }

    @Test
    @IR(failOn = {IRNode.UMOD_I})
    @IR(counts = {IRNode.AND_I, "1"})
    // Checks x % 2^c0 => x & (2^c0 - 1)
    public int modByPow2(int x) {
        return Integer.remainderUnsigned(x, 8);
    }

    @Test
    @IR(failOn = {IRNode.UMOD_I})
    @IR(counts = {IRNode.MUL_L, "1",
                  IRNode.URSHIFT_L, "1",
                  IRNode.CONV_I2L, "1",
                  IRNode.CONV_L2I, "1",
                  IRNode.MUL_I, "1",
                  IRNode.SUB_I, "1"
                 })
    // Checks magic int division occurs in general when dividing by a non power of 2.
    // The constant derived from 13 lies inside the limit of a u32
    public int magicMod13(int x) {
        return Integer.remainderUnsigned(x, 13);
    }
}
