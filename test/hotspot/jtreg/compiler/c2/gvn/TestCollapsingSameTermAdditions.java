/*
 * Copyright (c) 2025 Red Hat and/or its affiliates. All rights reserved.
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

package compiler.c2.gvn;

import compiler.lib.generators.Generators;
import compiler.lib.generators.RestrictableGenerator;
import compiler.lib.ir_framework.Test;
import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

import java.util.Random;

/*
 * @test
 * @bug 8325495 8347555
 * @summary C2 should optimize addition of the same terms by collapsing them into one multiplication.
 * @library /test/lib /
 * @run driver compiler.c2.gvn.TestCollapsingSameTermAdditions
 */
public class TestCollapsingSameTermAdditions {
    private static final RestrictableGenerator<Integer> GEN_INT = Generators.G.ints();
    private static final RestrictableGenerator<Long> GEN_LONG = Generators.G.longs();

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Run(test = {
            "addTo2",
            "addTo3",
            "addTo4",
            "shiftAndAddTo4",
            "mulAndAddTo4",
            "addTo5",
            "addTo6",
            "addTo7",
            "addTo8",
            "addTo16",
            "addAndShiftTo16",
            "addTo42",
            "mulAndAddTo42",
            "mulAndAddToMax",
            "mulAndAddToOverflow",
            "mulAndAddToZero",
            "mulAndAddToMinus1",
            "mulAndAddToMinus42"
    })
    private void runIntTests() {
        for (int a : new int[] { 0, 1, Integer.MIN_VALUE, Integer.MAX_VALUE, GEN_INT.next() }) {
            Asserts.assertEQ(a * 2, addTo2(a));
            Asserts.assertEQ(a * 3, addTo3(a));
            Asserts.assertEQ(a * 4, addTo4(a));
            Asserts.assertEQ(a * 4, shiftAndAddTo4(a));
            Asserts.assertEQ(a * 4, mulAndAddTo4(a));
            Asserts.assertEQ(a * 5, addTo5(a));
            Asserts.assertEQ(a * 6, addTo6(a));
            Asserts.assertEQ(a * 7, addTo7(a));
            Asserts.assertEQ(a * 8, addTo8(a));
            Asserts.assertEQ(a * 16, addTo16(a));
            Asserts.assertEQ(a * 16, addAndShiftTo16(a));
            Asserts.assertEQ(a * 42, addTo42(a));
            Asserts.assertEQ(a * 42, mulAndAddTo42(a));
            Asserts.assertEQ(a * Integer.MAX_VALUE, mulAndAddToMax(a));
            Asserts.assertEQ(a * Integer.MIN_VALUE, mulAndAddToOverflow(a));
            Asserts.assertEQ(0, mulAndAddToZero(a));
            Asserts.assertEQ(a * -1, mulAndAddToMinus1(a));
            Asserts.assertEQ(a * -42, mulAndAddToMinus42(a));
        }
    }

    @Run(test = {
            "mulAndAddToIntOverflowL",
            "mulAndAddToMaxL",
            "mulAndAddToOverflowL"
    })
    private void runLongTests() {
        for (long a : new long[] { 0, 1, Long.MIN_VALUE, Long.MAX_VALUE, GEN_LONG.next() }) {
            Asserts.assertEQ(a * (Integer.MAX_VALUE + 1L), mulAndAddToIntOverflowL(a));
            Asserts.assertEQ(a * Long.MAX_VALUE, mulAndAddToMaxL(a));
            Asserts.assertEQ(a * Long.MIN_VALUE, mulAndAddToOverflowL(a));
        }
    }

    @Run(test = {
            "bitShiftToOverflow",
            "bitShiftToOverflowL"
    })
    private void runBitShiftTests() {
        Asserts.assertEQ(95, bitShiftToOverflow());
        Asserts.assertEQ(191L, bitShiftToOverflowL());
    }

    // ----- integer tests -----
    @Test
    @IR(counts = { IRNode.ADD_I, "1" })
    @IR(failOn = IRNode.LSHIFT_I)
    private static int addTo2(int a) {
        return a + a; // Simple additions like a + a should be kept as-is
    }

    @Test
    @IR(counts = { IRNode.ADD_I, "1", IRNode.LSHIFT_I, "1" })
    private static int addTo3(int a) {
        return a + a + a; // a*3 => (a<<1) + a
    }

    @Test
    @IR(failOn = IRNode.ADD_I)
    @IR(counts = { IRNode.LSHIFT_I, "1" })
    private static int addTo4(int a) {
        return a + a + a + a; // a*4 => a<<2
    }

    @Test
    @IR(failOn = IRNode.ADD_I)
    @IR(counts = { IRNode.LSHIFT_I, "1" })
    private static int shiftAndAddTo4(int a) {
        return (a << 1) + a + a; // a*2 + a + a => a*3 + a => a*4 => a<<2
    }

    @Test
    @IR(failOn = IRNode.ADD_I)
    @IR(counts = { IRNode.LSHIFT_I, "1" })
    private static int mulAndAddTo4(int a) {
        return a * 3 + a; // a*4 => a<<2
    }

    @Test
    @IR(counts = { IRNode.ADD_I, "1", IRNode.LSHIFT_I, "1" })
    private static int addTo5(int a) {
        return a + a + a + a + a; // a*5 => (a<<2) + a
    }

    @Test
    @IR(counts = { IRNode.ADD_I, "1", IRNode.LSHIFT_I, "2" })
    private static int addTo6(int a) {
        return a + a + a + a + a + a; // a*6 => (a<<1) + (a<<2)
    }

    @Test
    @IR(failOn = IRNode.ADD_I)
    @IR(counts = { IRNode.LSHIFT_I, "1", IRNode.SUB_I, "1" })
    private static int addTo7(int a) {
        return a + a + a + a + a + a + a; // a*7 => (a<<3) - a
    }

    @Test
    @IR(failOn = IRNode.ADD_I)
    @IR(counts = { IRNode.LSHIFT_I, "1" })
    private static int addTo8(int a) {
        return a + a + a + a + a + a + a + a; // a*8 => a<<3
    }

    @Test
    @IR(failOn = IRNode.ADD_I)
    @IR(counts = { IRNode.LSHIFT_I, "1" })
    private static int addTo16(int a) {
        return a + a + a + a + a + a + a + a + a + a
                + a + a + a + a + a + a; // a*16 => a<<4
    }

    @Test
    @IR(failOn = IRNode.ADD_I)
    @IR(counts = { IRNode.LSHIFT_I, "1" })
    private static int addAndShiftTo16(int a) {
        return (a + a) << 3; // a<<(3 + 1) => a<<4
    }

    @Test
    @IR(failOn = IRNode.ADD_I)
    @IR(counts = { IRNode.MUL_I, "1" })
    private static int addTo42(int a) {
        return a + a + a + a + a + a + a + a + a + a
                + a + a + a + a + a + a + a + a + a + a
                + a + a + a + a + a + a + a + a + a + a
                + a + a + a + a + a + a + a + a + a + a
                + a + a; // a*42
    }

    @Test
    @IR(failOn = IRNode.ADD_I)
    @IR(counts = { IRNode.MUL_I, "1" })
    private static int mulAndAddTo42(int a) {
        return a * 40 + a + a; // a*41 + a => a*42
    }

    private static final int INT_MAX_MINUS_ONE = Integer.MAX_VALUE - 1;

    @Test
    @IR(failOn = IRNode.ADD_I)
    @IR(counts = { IRNode.LSHIFT_I, "1", IRNode.SUB_I, "1" })
    private static int mulAndAddToMax(int a) {
        return a * INT_MAX_MINUS_ONE + a; // a*MAX => a*(MIN-1) => a*MIN - a => (a<<31) - a
    }

    @Test
    @IR(failOn = IRNode.ADD_I)
    @IR(counts = { IRNode.LSHIFT_I, "1" })
    private static int mulAndAddToOverflow(int a) {
        return a * Integer.MAX_VALUE + a; // a*(MAX+1) => a*(MIN) => a<<31
    }

    @Test
    @IR(failOn = IRNode.ADD_I)
    @IR(counts = { IRNode.CON_I, "1" })
    private static int mulAndAddToZero(int a) {
        return a * -1 + a; // 0
    }

    @Test
    @IR(failOn = IRNode.ADD_I)
    @IR(counts = { IRNode.LSHIFT_I, "1", IRNode.SUB_I, "1" })
    private static int mulAndAddToMinus1(int a) {
        return a * -2 + a; // a*-1 => a - (a<<1)
    }

    @Test
    @IR(failOn = IRNode.ADD_I)
    @IR(counts = { IRNode.MUL_I, "1" })
    private static int mulAndAddToMinus42(int a) {
        return a * -43 + a; // a*-42
    }

    // --- long tests ---
    @Test
    @IR(failOn = IRNode.ADD_L)
    @IR(counts = { IRNode.LSHIFT_L, "1" })
    private static long mulAndAddToIntOverflowL(long a) {
        return a * Integer.MAX_VALUE + a; // a*(INT_MAX+1)
    }

    private static final long LONG_MAX_MINUS_ONE = Long.MAX_VALUE - 1;

    @Test
    @IR(failOn = IRNode.ADD_L)
    @IR(counts = { IRNode.LSHIFT_L, "1", IRNode.SUB_L, "1" })
    private static long mulAndAddToMaxL(long a) {
        return a * LONG_MAX_MINUS_ONE + a; // a*MAX => a*(MIN-1) => a*MIN - 1 => (a<<63) - 1
    }

    @Test
    @IR(failOn = IRNode.ADD_L)
    @IR(counts = { IRNode.LSHIFT_L, "1" })
    private static long mulAndAddToOverflowL(long a) {
        return a * Long.MAX_VALUE + a; // a*(MAX+1) => a*(MIN) => a<<63
    }

    // --- bit shift tests ---
    @Test
    @IR(failOn = {IRNode.ADD_I, IRNode.LSHIFT_I})
    private static int bitShiftToOverflow() {
        int i, x = 0;
        for (i = 0; i < 32; i++) {
            x = i;
        }

        // x = 31 (phi), i = 32 (phi + 1)
        return i + (x << i) + i; // Expects 32 + 31 + 32 = 95
    }

    @Test
    @IR(failOn = {IRNode.ADD_L, IRNode.LSHIFT_L})
    private static long bitShiftToOverflowL() {
        int i, x = 0;
        for (i = 0; i < 64; i++) {
            x = i;
        }

        // x = 63 (phi), i = 64 (phi + 1)
        return i + (x << i) + i; // Expects 64 + 63 + 64 = 191
    }

    // --- random tests ---
    private static final int CON1_I, CON2_I, CON3_I, CON4_I;
    private static final long CON1_L, CON2_L, CON3_L, CON4_L;

    static {
        CON1_I = GEN_INT.next();
        CON2_I = GEN_INT.next();
        CON3_I = GEN_INT.next();
        CON4_I = GEN_INT.next();

        CON1_L = GEN_LONG.next();
        CON2_L = GEN_LONG.next();
        CON3_L = GEN_LONG.next();
        CON4_L = GEN_LONG.next();
    }

    @Run(test = {
            "randomPowerOfTwoAddition",
            "randomPowerOfTwoAdditionL"
    })
    private void runRandomPowerOfTwoAddition() {
        for (int a : new int[] { 0, 1, Integer.MIN_VALUE, Integer.MAX_VALUE, GEN_INT.next() }) {
            Asserts.assertEQ(a * (CON1_I + CON2_I + CON3_I + CON4_I), randomPowerOfTwoAddition(a));
        }

        for (long a : new long[] { 0, 1, Long.MIN_VALUE, Long.MAX_VALUE, GEN_LONG.next() }) {
            Asserts.assertEQ(a * (CON1_L + CON2_L + CON3_L + CON4_L), randomPowerOfTwoAdditionL(a));
        }
    }

    // We can't do IR verification but only check for correctness for a better confidence.
    @Test
    private static int randomPowerOfTwoAddition(int a) {
        return a * CON1_I + a * CON2_I + a * CON3_I + a * CON4_I;
    }

    @Test
    private static long randomPowerOfTwoAdditionL(long a) {
        return a * CON1_L + a * CON2_L + a * CON3_L + a * CON4_L;
    }

    // Patterns that are originally cannot be recognized due to their right precedence making it difficult without
    // recursion, but some are made possible with swapping lhs and rhs.
    @Run(test = {
        "rightPrecedence",
        "rightPrecedenceL",
        "rightPrecedenceShift",
        "rightPrecedenceShiftL",
    })
    private void runLhsRhsSwaps() {
        for (int a : new int[] { 0, 1, Integer.MIN_VALUE, Integer.MAX_VALUE, GEN_INT.next() }) {
            Asserts.assertEQ(a * 3, rightPrecedence(a));
            Asserts.assertEQ(a * 4, rightPrecedenceShift(a));
        }

        for (long a : new long[] { 0, 1, Long.MIN_VALUE, Long.MAX_VALUE, GEN_LONG.next() }) {
            Asserts.assertEQ(a * 3, rightPrecedenceL(a));
            Asserts.assertEQ(a * 4, rightPrecedenceShiftL(a));
        }
    }

    @Test
    @IR(counts = { IRNode.ADD_I, "1", IRNode.LSHIFT_I, "1" })
    private static int rightPrecedence(int a) {
        return a + (a + a);
    }

    @Test
    @IR(counts = { IRNode.ADD_L, "1", IRNode.LSHIFT_L, "1" })
    private static long rightPrecedenceL(long a) {
        return a + (a + a);
    }

    @Test
    @IR(failOn = IRNode.ADD_I)
    @IR(counts = { IRNode.LSHIFT_I, "1" })
    private static int rightPrecedenceShift(int a) {
        return a + (a << 1) + a; // a + a*2 + a => a*2 + a + a => a*3 + a => a*4 => a<<2
    }

    @Test
    @IR(failOn = IRNode.ADD_L)
    @IR(counts = { IRNode.LSHIFT_L, "1" })
    private static long rightPrecedenceShiftL(long a) {
        return a + (a << 1) + a; // a + a*2 + a => a*2 + a + a => a*3 + a => a*4 => a<<2
    }

    // JDK-8347555 only aims to cover cases minimally needed for patterns a + a + ... + a => n*a. However, some patterns
    // like CON * a + a => (CON + 1) * a are considered unintended side-effects due to the way pattern matching is
    // implemented.
    //
    // The followings are patterns that could be, mathematically speaking, optimized, but not implemented at this stage.
    // These tests are to be updated if they are addressed in the future.

    @Test
    @IR(counts = { IRNode.ADD_I, "2", IRNode.LSHIFT_I, "2" })
    @Arguments(values = { Argument.RANDOM_EACH })
    private static int complexShiftPattern(int a) {
        return a + (a << 1) + (a << 2); // This could've been: a + a*2 + a*4 => a*7
    }

    @Test
    @IR(counts = { IRNode.ADD_I, "2" })  // b = a + a, c = b + b
    @Arguments(values = { Argument.RANDOM_EACH })
    private static int nestedAddPattern(int a) {
        return (a + a) + (a + a); // This could've been: 2*a + 2*a => 4*a
    }

    @Test
    @IR(counts = { IRNode.ADD_I, "3", IRNode.LSHIFT_I, "1" })
    @Arguments(values = { Argument.RANDOM_EACH })
    private static int complexPrecedence(int a) {
        return a + a + ((a + a) + a); // This could've been: 2*a + (2*a + a) => 2*a + 3*a => 5*a
    }

    @Test
    @IR(counts = { IRNode.ADD_L, "2", IRNode.LSHIFT_L, "2" })
    @Arguments(values = { Argument.RANDOM_EACH })
    private static long complexShiftPatternL(long a) {
        return a + (a << 1) + (a << 2); // This could've been: a + a*2 + a*4 => a*7
    }

    @Test
    @IR(counts = { IRNode.ADD_L, "2" })  // b = a + a, c = b + b
    @Arguments(values = { Argument.RANDOM_EACH })
    private static long nestedAddPatternL(long a) {
        return (a + a) + (a + a); // This could've been: 2*a + 2*a => 4*a
    }

    @Test
    @IR(counts = { IRNode.ADD_L, "3", IRNode.LSHIFT_L, "1" })
    @Arguments(values = { Argument.RANDOM_EACH })
    private static long complexPrecedenceL(long a) {
        return a + a + ((a + a) + a); // This could've been: 2*a + (2*a + a) => 2*a + 3*a => 5*a
    }
}
