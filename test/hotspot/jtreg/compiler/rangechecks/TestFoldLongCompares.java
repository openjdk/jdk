/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @summary C2's fold_compares may fold two CmpL nodes into one CmpUL node
 * @requires vm.compiler2.enabled
 * @library /test/lib /
 * @run driver compiler.rangechecks.TestFoldLongCompares
 */

package compiler.rangechecks;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;

public class TestFoldLongCompares {
    // Choose bounds outside the int range to exercise long-specific folding.
    // Leave room for LO - 1 and HI + 1, which checkBounds() tests without overflow.
    private static final long LO = Integer.MIN_VALUE - 1L;
    private static final long HI = Integer.MAX_VALUE + 1L;

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Test
    @IR(counts = {IRNode.CMP_L, "= 2", IRNode.CMP_UL, "= 0"}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.CMP_L, "= 0", IRNode.CMP_UL, "= 1"})
    public static boolean test_lohi_ltle(long x) {
        return x < LO || x > HI;
    }

    @Test
    @IR(counts = {IRNode.CMP_L, "= 2", IRNode.CMP_UL, "= 0"}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.CMP_L, "= 0", IRNode.CMP_UL, "= 1"})
    public static boolean test_lohi_lele(long x) {
        return x <= LO || x > HI;
    }

    @Test
    @IR(counts = {IRNode.CMP_L, "= 2", IRNode.CMP_UL, "= 0"}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.CMP_L, "= 0", IRNode.CMP_UL, "= 1"})
    public static boolean test_lohi_ltlt(long x) {
        return x < LO || x >= HI;
    }

    @Test
    @IR(counts = {IRNode.CMP_L, "= 2", IRNode.CMP_UL, "= 0"}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.CMP_L, "= 0", IRNode.CMP_UL, "= 1"})
    public static boolean test_lohi_lelt(long x) {
        return x <= LO || x >= HI;
    }

    @Test
    @IR(counts = {IRNode.CMP_L, "= 2", IRNode.CMP_UL, "= 0"}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.CMP_L, "= 0", IRNode.CMP_UL, "= 1"})
    public static boolean test_hilo_ltle(long x) {
        return x >= HI || x <= LO;
    }

    @Test
    @IR(counts = {IRNode.CMP_L, "= 2", IRNode.CMP_UL, "= 0"}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.CMP_L, "= 0", IRNode.CMP_UL, "= 1"})
    public static boolean test_hilo_lele(long x) {
        return x > HI || x <= LO;
    }

    @Test
    @IR(counts = {IRNode.CMP_L, "= 2", IRNode.CMP_UL, "= 0"}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.CMP_L, "= 0", IRNode.CMP_UL, "= 1"})
    public static boolean test_hilo_lelt(long x) {
        return x > HI || x < LO;
    }

    @Test
    @IR(counts = {IRNode.CMP_L, "= 2", IRNode.CMP_UL, "= 0"}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.CMP_L, "= 0", IRNode.CMP_UL, "= 1"})
    public static boolean test_hilo_ltlt(long x) {
        return x >= HI || x < LO;
    }

    @Run(test = {"test_lohi_ltle", "test_lohi_lele", "test_lohi_ltlt", "test_lohi_lelt",
                 "test_hilo_ltle", "test_hilo_lele", "test_hilo_lelt", "test_hilo_ltlt"})
    @Warmup(0)
    public static void checkBounds() {
        long[] values = {Long.MIN_VALUE, LO - 1, LO, LO + 1, 0, HI - 1, HI, HI + 1, Long.MAX_VALUE};
        for (long x : values) {
            Asserts.assertEQ(test_lohi_ltle(x), x < LO || x > HI);
            Asserts.assertEQ(test_lohi_lele(x), x <= LO || x > HI);
            Asserts.assertEQ(test_lohi_ltlt(x), x < LO || x >= HI);
            Asserts.assertEQ(test_lohi_lelt(x), x <= LO || x >= HI);
            Asserts.assertEQ(test_hilo_ltle(x), x >= HI || x <= LO);
            Asserts.assertEQ(test_hilo_lele(x), x > HI || x <= LO);
            Asserts.assertEQ(test_hilo_lelt(x), x > HI || x < LO);
            Asserts.assertEQ(test_hilo_ltlt(x), x >= HI || x < LO);
        }
    }

    // hi - lo + 1 would overflow when the interval covers all long values.
    @Test
    @IR(counts = {IRNode.CMP_L, "= 2", IRNode.CMP_UL, "= 0"}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.CMP_L, "= 0", IRNode.CMP_UL, "= 1"})
    public static boolean testFullRange(long x, long a, long b) {
        long lo = Long.MIN_VALUE + (a & 1);
        long hi = Long.MAX_VALUE - (b & 1);
        return x < lo || x > hi;
    }

    @Run(test = "testFullRange")
    @Warmup(100)
    public static void checkFullRange() {
        long[] values = {Long.MIN_VALUE, Long.MIN_VALUE + 1, 0, Long.MAX_VALUE - 1, Long.MAX_VALUE};
        for (long x : values) {
            for (long a = 0; a <= 1; a++) {
                for (long b = 0; b <= 1; b++) {
                    boolean expected = (x == Long.MIN_VALUE && a == 1) || (x == Long.MAX_VALUE && b == 1);
                    Asserts.assertEQ(testFullRange(x, a, b), expected);
                }
            }
        }
    }

    @Test
    @IR(failOn = {IRNode.CMP_L, IRNode.CMP_UL})
    @Arguments(values = {Argument.NUMBER_42})
    @Warmup(0)
    public static void testEmptyRange(long x) {
        if (x >= HI && x <= LO) {
            throw new RuntimeException("empty range");
        }
    }
}
