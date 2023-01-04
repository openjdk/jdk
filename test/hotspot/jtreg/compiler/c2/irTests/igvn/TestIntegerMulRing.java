/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package compiler.c2.irTests.igvn;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8299546
 * @summary Test that IntegerMulRing works correctly and returns correct (and optimized) types.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.igvn.TestIntegerMulRing
 */
public class TestIntegerMulRing {
    public static int iFld, iFld2, iFld3, iFld4;
    public static long lFld, lFld2, lFld3, lFld4;

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Test
    @IR(failOn = {IRNode.STORE_I, IRNode.IF}, counts = {IRNode.STORE_L, "2"})
    public static void testLongPositive() {
        long l = 111111111111111111L;
        if (l * 81 == 1L) {
            iFld = 23;
        }
        if (l * 81 == 8999999999999999991L) {
            lFld = 23;
        }
        if (l * 83 == 1L) {
            iFld2 = 34;
        }
        if (l * 83 == 9222222222222222213L) {
            lFld2 = 23;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_I, IRNode.IF}, counts = {IRNode.STORE_L, "2"})
    public static void testLongPositive2() {
        long l = -111111111111111111L;
        if (l * -81 == 1L) {
            iFld = 23;
        }
        if (l * -81 == 8999999999999999991L) {
            lFld = 23;
        }
        if (l * -83 == 1L) {
            iFld2 = 34;
        }
        if (l * -83 == 9222222222222222213L) {
            lFld2 = 23;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_I, IRNode.IF}, counts = {IRNode.STORE_L, "2"})
    public static void testLongNegative() {
        long l = -111111111111111111L;
        if (l * 81 == 1L) {
            iFld = 23;
        }
        if (l * 81 == -8999999999999999991L) {
            lFld = 23;
        }
        if (l * 83 == 1L) {
            iFld2 = 34;
        }
        if (l * 83 == -9222222222222222213L) {
            lFld2 = 23;
        }
    }

    @Test
    @IR(failOn = {IRNode.STORE_I, IRNode.IF}, counts = {IRNode.STORE_L, "2"})
    public static void testLongNegative2() {
        long l = 111111111111111111L;
        if (l * -81 == 1L) {
            iFld = 23;
        }
        if (l * -81 == -8999999999999999991L) {
            lFld = 23;
        }
        if (l * -83 == 1L) {
            iFld2 = 34;
        }
        if (l * -83 == -9222222222222222213L) {
            lFld2 = 23;
        }
    }

    @Test
    @Warmup(0)
    @Arguments({Argument.BOOLEAN_TOGGLE_FIRST_TRUE, Argument.BOOLEAN_TOGGLE_FIRST_FALSE})
    @IR(counts = {IRNode.STORE_L, "2", IRNode.MUL_L, "1"})
    public static void testMinValueMinus1(boolean flag, boolean flag2) {
        long l = flag ? -1 : Long.MIN_VALUE;
        int x = flag2 ? -1 : 0;

        if (l * x != 2L) { // Type of multiplication is LONG as Long.MIN_VALUE * -1 does overflow. If cannot be removed.
            lFld = 23;
        } else {
            lFld = 34; // Emits StoreL since warmup is 0 and no UCT will be emitted.
        }
    }

    // Just some sanity testing.
    @Test
    public static void test() {
        iFld = 1073741823 * 2;
        iFld2 = 1073741824 * 2; // overflow
        iFld3 = -1073741824 * 2;
        iFld4 = -1073741825 * 2; // underflow
        lFld = 4611686018427387903L * 2;
        lFld2 = 4611686018427387904L * 2; // overflow
        lFld3 = -4611686018427387904L * 2;
        lFld4 = -4611686018427387905L * 2; // underflow
    }

    @Run(test = "test")
    public static void run() {
        test();
        Asserts.assertEQ(iFld, 2147483646);
        Asserts.assertEQ(iFld2, -2147483648);
        Asserts.assertEQ(iFld3, -2147483648);
        Asserts.assertEQ(iFld4, 2147483646);
        Asserts.assertEQ(lFld, 9223372036854775806L);
        Asserts.assertEQ(lFld2, -9223372036854775808L);
        Asserts.assertEQ(lFld3, -9223372036854775808L);
        Asserts.assertEQ(lFld4, 9223372036854775806L);
    }
}
