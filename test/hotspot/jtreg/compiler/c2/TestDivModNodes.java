/*
 * Copyright (c) 2024, 2025, Red Hat and/or its affiliates. All rights reserved.
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

package compiler.c2;

import compiler.lib.ir_framework.*;
import compiler.lib.ir_framework.Test;

import java.util.Random;

/*
 * @test
 * @bug 8332442
 * @summary Test that DIV and MOD nodes are converted into DIVMOD where possible
 * @library /test/lib /
 * @run driver compiler.c2.TestDivModNodes
 */
public class TestDivModNodes {
    private static final Random RANDOM = AbstractInfo.getRandom();

    private static int intQuotient;
    private static int intRemainder;
    private static long longQuotient;
    private static long longRemainder;

    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:-UseDivMod");
        TestFramework.runWithFlags("-XX:+UseDivMod");
    }

    private static int nextNonZeroInt() {
        int i;
        do {
            i = RANDOM.nextInt();
        } while (i == 0);
        return i;
    }

    private static long nextNonZeroLong() {
        long i;
        do {
            i = RANDOM.nextLong();
        } while (i == 0);
        return i;
    }

    @Test
    @IR(applyIf = {"UseDivMod", "true"}, applyIfPlatform = {"x64", "true"},
            counts = {IRNode.DIV_MOD_I, "1"},
            failOn = {IRNode.DIV_I, IRNode.MOD_I})
    @IR(applyIf = {"UseDivMod", "true"}, applyIfPlatformOr = {"aarch64", "true", "riscv64", "true"},
            counts = {IRNode.DIV_I, "1", IRNode.MUL_I, "1", IRNode.SUB_I, "1"},
            failOn = {IRNode.MOD_I})
    @IR(applyIf = {"UseDivMod", "false"},
            counts = {IRNode.DIV_I, "1", IRNode.MOD_I, "1"})
    private static void testSignedIntDivMod(int dividend, int divisor) {
        intQuotient = dividend / divisor;
        intRemainder = dividend % divisor;
    }

    @Run(test = "testSignedIntDivMod")
    private static void runSignedIntDivMod() {
        int dividend = RANDOM.nextInt();
        int divisor = nextNonZeroInt();
        testSignedIntDivMod(dividend, divisor);

        verifyResult(dividend, divisor,
                intQuotient, intRemainder,
                dividend / divisor, dividend % divisor);
    }


    @Test
    @IR(applyIf = {"UseDivMod", "true"}, applyIfPlatform = {"x64", "true"},
            counts = {IRNode.DIV_MOD_L, "1"},
            failOn = {IRNode.DIV_L, IRNode.MOD_L})
    @IR(applyIf = {"UseDivMod", "true"}, applyIfPlatformOr = {"aarch64", "true", "riscv64", "true"},
            counts = {IRNode.DIV_L, "1", IRNode.MUL_L, "1", IRNode.SUB_L, "1"},
            failOn = {IRNode.MOD_L})
    @IR(applyIf = {"UseDivMod", "false"},
            counts = {IRNode.DIV_L, "1", IRNode.MOD_L, "1"})
    private static void testSignedLongDivMod(long dividend, long divisor) {
        longQuotient = dividend / divisor;
        longRemainder = dividend % divisor;
    }

    @Run(test = "testSignedLongDivMod")
    private static void runSignedLongDivMod() {
        long dividend = RANDOM.nextLong();
        long divisor = nextNonZeroLong();
        testSignedLongDivMod(dividend, divisor);

        verifyResult(dividend, divisor,
                longQuotient, longRemainder,
                dividend / divisor, dividend % divisor);
    }

    @Test
    @IR(applyIf = {"UseDivMod", "true"}, applyIfPlatform = {"x64", "true"},
            counts = {IRNode.UDIV_MOD_I, "1"},
            failOn = {IRNode.UDIV_I, IRNode.UMOD_I})
    @IR(applyIf = {"UseDivMod", "true"}, applyIfPlatformOr = {"aarch64", "true", "riscv64", "true"},
            counts = {IRNode.UDIV_I, "1", IRNode.MUL_I, "1", IRNode.SUB_I, "1"},
            failOn = {IRNode.UMOD_I})
    @IR(applyIf = {"UseDivMod", "false"},
            counts = {IRNode.UDIV_I, "1", IRNode.UMOD_I, "1"})
    private static void testUnsignedIntDivMod(int dividend, int divisor) {
        intQuotient = Integer.divideUnsigned(dividend, divisor); // intrinsified on x86
        intRemainder = Integer.remainderUnsigned(dividend, divisor); // intrinsified on x86
    }

    @Run(test = "testUnsignedIntDivMod")
    private static void runUnsignedIntDivMod() {
        int dividend = RANDOM.nextInt();
        int divisor = nextNonZeroInt();
        testUnsignedIntDivMod(dividend, divisor);

        verifyResult(dividend, divisor,
                intQuotient, intRemainder,
                Integer.divideUnsigned(dividend, divisor), Integer.remainderUnsigned(dividend, divisor));
    }

    @Test
    @IR(applyIf = {"UseDivMod", "true"}, applyIfPlatform = {"x64", "true"},
            counts = {IRNode.UDIV_MOD_L, "1"},
            failOn = {IRNode.UDIV_L, IRNode.UMOD_L})
    @IR(applyIf = {"UseDivMod", "true"}, applyIfPlatformOr = {"aarch64", "true", "riscv64", "true"},
            counts = {IRNode.UDIV_L, "1", IRNode.MUL_L, "1", IRNode.SUB_L, "1"},
            failOn = {IRNode.MOD_L})
    @IR(applyIf = {"UseDivMod", "false"},
            counts = {IRNode.UDIV_L, "1", IRNode.UMOD_L, "1"})
    private static void testUnsignedLongDivMod(long dividend, long divisor) {
        longQuotient = Long.divideUnsigned(dividend, divisor); // intrinsified on x86
        longRemainder = Long.remainderUnsigned(dividend, divisor); // intrinsified on x86
    }

    @Run(test = "testUnsignedLongDivMod")
    private static void runUnsignedLongDivMod() {
        long dividend = RANDOM.nextLong();
        long divisor = nextNonZeroLong();
        testUnsignedLongDivMod(dividend, divisor);

        verifyResult(dividend, divisor,
                longQuotient, longRemainder,
                Long.divideUnsigned(dividend, divisor), Long.remainderUnsigned(dividend, divisor));
    }

    private static <T extends Number> void verifyResult(T dividend, T divisor,
                                                        T quotient, T remainder,
                                                        T expectedQ, T expectedR) {
        if (!expectedQ.equals(quotient) || !expectedR.equals(remainder)) {
            throw new AssertionError(String.format("Mismatched result from %d / %d. " +
                    "Expected: quotient = %d remainder = %d, " +
                    "but got: quotient = %d remainder = %d",
                    dividend, divisor, expectedQ, expectedR, quotient, remainder));
        }
    }
}
