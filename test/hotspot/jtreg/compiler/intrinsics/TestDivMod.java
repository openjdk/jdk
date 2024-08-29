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

package compiler.intrinsics; // TODO: not intrinsics

import compiler.lib.ir_framework.*;

import java.util.function.BiFunction;

/*
 * @test
 * @summary Test DIV and MOD nodes are converted into DIVMOD where possible
 * @requires os.arch=="amd64" | os.arch=="x86_64"
 * @library /test/lib /
 * @run driver compiler.intrinsics.TestDivMod
 */
public class TestDivMod {
    public static void main(String[] args) {
        TestFramework.run();
    }

//    @Test
//    @IR(counts = {IRNode.UDIV_MOD_I, "1" })
//    private static void testIntSignedDivMod(int dividend, int divisor) {
//        // TODO
//    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(counts = {IRNode.UDIV_MOD_I, "1" })
    private static void testIntUnsignedDivMod(int dividend, int divisor) {
        int q = Integer.divideUnsigned(dividend, divisor); // intrinsified on x86
        int r = Integer.remainderUnsigned(dividend, divisor); // intrinsified on x86

        verifyResult(dividend, divisor, q, r, TestDivMod::intUnsignedDiv, TestDivMod::intUnsignedMod);
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(counts = {IRNode.UDIV_MOD_L, "1" })
    private static void testLongUnsignedDivMod(long dividend, long divisor) {
        long q = Long.divideUnsigned(dividend, divisor); // intrinsified on x86
        long r = Long.remainderUnsigned(dividend, divisor); // intrinsified on x86

        verifyResult(dividend, divisor, q, r, TestDivMod::longUnsignedDiv, TestDivMod::longUnsignedMod);
    }

    private static <T extends Number> void verifyResult(
            T dividend, T divisor, T quotient, T remainder,
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

    // by spreading div and mod into different, not inlined methods, we can confuse the compiler enough to not perform
    // this optimization, so we can test for correctness
    @DontInline
    private static int intSignedDiv(int dividend, int divisor) {
        return dividend / divisor;
    }

    @DontInline
    private static int intSignedMod(int dividend, int divisor) {
        return dividend % divisor;
    }

    @DontInline
    private static int intUnsignedDiv(int dividend, int divisor) {
        return Integer.divideUnsigned(dividend, divisor); // intrinsified on x86
    }

    @DontInline
    private static int intUnsignedMod(int dividend, int divisor) {
        return Integer.remainderUnsigned(dividend, divisor); // intrinsified on x86
    }

    @DontInline
    private static long longSignedDiv(long dividend, long divisor) {
        return dividend / divisor;
    }

    @DontInline
    private static long longSignedMod(long dividend, long divisor) {
        return dividend % divisor;
    }

    @DontInline
    private static long longUnsignedDiv(long dividend, long divisor) {
        return Long.divideUnsigned(dividend, divisor); // intrinsified on x86
    }

    @DontInline
    private static long longUnsignedMod(long dividend, long divisor) {
        return Long.remainderUnsigned(dividend, divisor); // intrinsified on x86
    }
}
