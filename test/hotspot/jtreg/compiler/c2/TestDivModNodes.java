/*
 * Copyright (c) 2024 Red Hat and/or its affiliates. All rights reserved.
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

import java.util.function.BiFunction;

/*
 * @test
 * @summary Test DIV and MOD nodes are converted into DIVMOD where possible
 * @library /test/lib /
 * @run main/othervm -XX:+UseDivMod compiler.c2.TestDivModNodes
 * @run main/othervm -XX:-UseDivMod compiler.c2.TestDivModNodes
 */
public class TestDivModNodes {
    public static void main(String[] args) {
        TestFramework.run();
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(applyIf = {"UseDivMod", "true"}, applyIfPlatform = {"x64", "true"},
            counts = {IRNode.DIV_MOD_I, "1"},
            failOn = {IRNode.DIV_I, IRNode.MOD_I})
    @IR(applyIf = {"UseDivMod", "true"}, applyIfPlatform = {"aarch64", "true"},
            counts = {IRNode.DIV_I, "1", IRNode.MUL_I, "1", IRNode.SUB_I, "1"},
            failOn = {IRNode.MOD_I})
    @IR(applyIf = {"UseDivMod", "false"},
            counts = {IRNode.DIV_I, "1", IRNode.MOD_I, "1"})
    private static void testSignedIntDivMod(int dividend, int divisor) {
        int q = dividend / divisor;
        int r = dividend % divisor;

        verifyResult(dividend, divisor, q, r, TestDivModNodes::signedIntDiv, TestDivModNodes::signedIntMod);
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(applyIf = {"UseDivMod", "true"}, applyIfPlatform = {"x64", "true"},
            counts = {IRNode.DIV_MOD_L, "1"},
            failOn = {IRNode.DIV_L, IRNode.MOD_L})
    @IR(applyIf = {"UseDivMod", "true"}, applyIfPlatform = {"aarch64", "true"},
            counts = {IRNode.DIV_L, "1", IRNode.MUL_L, "1", IRNode.SUB_L, "1"},
            failOn = {IRNode.MOD_L})
    @IR(applyIf = {"UseDivMod", "false"},
            counts = {IRNode.DIV_L, "1", IRNode.MOD_L, "1"})
    private static void testSignedLongDivMod(long dividend, long divisor) {
        long q = dividend / divisor;
        long r = dividend % divisor;

        verifyResult(dividend, divisor, q, r, TestDivModNodes::signedLongDiv, TestDivModNodes::signedLongMod);
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(applyIf = {"UseDivMod", "true"}, applyIfPlatform = {"x64", "true"},
            counts = {IRNode.UDIV_MOD_I, "1"},
            failOn = {IRNode.UDIV_I, IRNode.UMOD_I})
    @IR(applyIf = {"UseDivMod", "true"}, applyIfPlatform = {"aarch64", "true"},
            counts = {IRNode.UDIV_I, "1", IRNode.MUL_I, "1", IRNode.SUB_I, "1"},
            failOn = {IRNode.UMOD_I})
    @IR(applyIf = {"UseDivMod", "false"},
            counts = {IRNode.UDIV_I, "1", IRNode.UMOD_I, "1"})
    private static void testUnsignedIntDivMod(int dividend, int divisor) {
        int q = Integer.divideUnsigned(dividend, divisor); // intrinsified on x86
        int r = Integer.remainderUnsigned(dividend, divisor); // intrinsified on x86

        verifyResult(dividend, divisor, q, r, TestDivModNodes::unsignedIntDiv, TestDivModNodes::unsignedIntMod);
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(applyIf = {"UseDivMod", "true"}, applyIfPlatform = {"x64", "true"},
            counts = {IRNode.UDIV_MOD_L, "1"},
            failOn = {IRNode.UDIV_L, IRNode.UMOD_L})
    @IR(applyIf = {"UseDivMod", "true"}, applyIfPlatform = {"aarch64", "true"},
            counts = {IRNode.UDIV_L, "1", IRNode.MUL_L, "1", IRNode.SUB_L, "1"},
            failOn = {IRNode.MOD_L})
    @IR(applyIf = {"UseDivMod", "false"},
            counts = {IRNode.UDIV_L, "1", IRNode.UMOD_L, "1"})
    private static void testUnsignedLongDivMod(long dividend, long divisor) {
        long q = Long.divideUnsigned(dividend, divisor); // intrinsified on x86
        long r = Long.remainderUnsigned(dividend, divisor); // intrinsified on x86

        verifyResult(dividend, divisor, q, r, TestDivModNodes::unsignedLongDiv, TestDivModNodes::unsignedLongMod);
    }

    private static <T extends Number> void verifyResult(T dividend, T divisor, T quotient, T remainder,
            BiFunction<T, T, T> quotientFunc, BiFunction<T, T, T> remainderFunc) {
        T expectedQ = quotientFunc.apply(dividend, divisor);
        T expectedR = remainderFunc.apply(dividend, divisor);

        if (!expectedQ.equals(quotient) || !expectedR.equals(remainder)) {
            throw new AssertionError(String.format("Mismatched result from %d / %d. " +
                    "Expected: quotient = %d remainder = %d, " +
                    "but got: quotient = %d remainder = %d",
                    dividend, divisor, expectedQ, expectedR, quotient, remainder));
        }
    }

    // By spreading div and mod into different, not inlined methods, we can confuse the compiler enough to not perform
    // the divmod optimization, so we can test for correctness.
    @DontInline
    private static int signedIntDiv(int dividend, int divisor) {
        return dividend / divisor;
    }

    @DontInline
    private static int signedIntMod(int dividend, int divisor) {
        return dividend % divisor;
    }

    @DontInline
    private static int unsignedIntDiv(int dividend, int divisor) {
        return Integer.divideUnsigned(dividend, divisor);
    }

    @DontInline
    private static int unsignedIntMod(int dividend, int divisor) {
        return Integer.remainderUnsigned(dividend, divisor);
    }

    @DontInline
    private static long signedLongDiv(long dividend, long divisor) {
        return dividend / divisor;
    }

    @DontInline
    private static long signedLongMod(long dividend, long divisor) {
        return dividend % divisor;
    }

    @DontInline
    private static long unsignedLongDiv(long dividend, long divisor) {
        return Long.divideUnsigned(dividend, divisor); // intrinsified on x86
    }

    @DontInline
    private static long unsignedLongMod(long dividend, long divisor) {
        return Long.remainderUnsigned(dividend, divisor); // intrinsified on x86
    }
}
