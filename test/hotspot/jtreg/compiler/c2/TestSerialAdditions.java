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

import compiler.lib.ir_framework.Test;
import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

import java.util.Random;

/*
 * @test
 * @bug 8325495
 * @summary C2 should optimize for series of Add of unique value. e.g., a + a + ... + a => a*n
 * @library /test/lib /
 * @run driver compiler.c2.TestSerialAdditions
 */
public class TestSerialAdditions {
    private static final Random RNG = Utils.getRandomInstance();

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
        for (int a : new int[] { 0, 1, Integer.MIN_VALUE, Integer.MAX_VALUE, RNG.nextInt() }) {
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
        for (long a : new long[] { 0, 1, Long.MIN_VALUE, Long.MAX_VALUE, RNG.nextLong() }) {
            Asserts.assertEQ(a * (Integer.MAX_VALUE + 1L), mulAndAddToIntOverflowL(a));
            Asserts.assertEQ(a * Long.MAX_VALUE, mulAndAddToMaxL(a));
            Asserts.assertEQ(a * Long.MIN_VALUE, mulAndAddToOverflowL(a));
        }
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
}
