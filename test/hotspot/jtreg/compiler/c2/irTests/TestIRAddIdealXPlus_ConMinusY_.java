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
 * @summary Test that transformation from x + (con - y) or (con - y) + x
 *          to (x - y) + con works as intended.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.TestIRAddIdealXPlus_ConMinusY_
 */
public class TestIRAddIdealXPlus_ConMinusY_ {

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Test
    @IR(failOn = {IRNode.ADD_I, IRNode.CON_I})
    @IR(counts = {IRNode.SUB_I, "1"})
    public int testIntXPlus_ZeroMinusY_StillWorks(int x, int y) {
        return x + (0 - y); // transformed to x - y
    }

    @Run(test = "testIntXPlus_ZeroMinusY_StillWorks")
    public void checkTestIntXPlus_ZeroMinusY_StillWorks(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(1, testIntXPlus_ZeroMinusY_StillWorks(10, 9));
    }

    @Test
    @IR(failOn = {IRNode.ADD_L, IRNode.CON_L})
    @IR(counts = {IRNode.SUB_L, "1"})
    public long testLongXPlus_ZeroMinusY_StillWorks(long x, long y) {
        return x + (0 - y); // transformed to x - y
    }

    @Run(test = "testLongXPlus_ZeroMinusY_StillWorks")
    public void checkTestLongXPlus_ZeroMinusY_StillWorks(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(100_000_000_000L, testLongXPlus_ZeroMinusY_StillWorks(123_456_789_000L, 23_456_789_000L));
    }

    @Test
    @IR(failOn = {IRNode.ADD_I, IRNode.CON_I})
    @IR(counts = {IRNode.SUB_I, "1"})
    public int testIntXPlus_ZeroMinusY_SymStillWorks(int x, int y) {
        return (0 - y) + x; // transformed to x - y
    }

    @Run(test = "testIntXPlus_ZeroMinusY_SymStillWorks")
    public void checkTestIntXPlus_ZeroMinusY_SymStillWorks(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(1, testIntXPlus_ZeroMinusY_SymStillWorks(10, 9));
    }

    @Test
    @IR(failOn = {IRNode.ADD_L, IRNode.CON_L})
    @IR(counts = {IRNode.SUB_L, "1"})
    public long testLongXPlus_ZeroMinusY_SymStillWorks(long x, long y) {
        return (0 - y) + x; // transformed to x - y
    }

    @Run(test = "testLongXPlus_ZeroMinusY_SymStillWorks")
    public void checkTestLongXPlus_ZeroMinusY_SymStillWorks(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(100_000_000_000L, testLongXPlus_ZeroMinusY_SymStillWorks(123_456_789_000L, 23_456_789_000L));
    }

    @Test
    @IR(counts = {IRNode.SUB_I, "1",
                  IRNode.ADD_I, "1",
                  IRNode.CON_I, "1"})
    public int testInt1(int x, int y) {
        return x + (10 - y) + 200; // transformed to (x - y) + 210;
    }

    @Run(test = "testInt1")
    public void checkTestInt1(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(211, testInt1(10, 9));
        Asserts.assertEquals(210, testInt1(100, 100)); // x - y == 0
        // con - y overflows while x - y does not
        Asserts.assertEquals(-2147483439, testInt1(1, -2147483646));
        // x - y underflows while con - y does not
        Asserts.assertEquals(-2147483447, testInt1(-10, 2147483647));
        // x - y overflows while con - y does not
        Asserts.assertEquals(-2147483349, testInt1(100, -2147483637));
    }

    @Test
    @IR(counts = {IRNode.SUB_I, "1",
                  IRNode.ADD_I, "1",
                  IRNode.CON_I, "1"})
    public int testInt2(int x, int y) {
        return x + (-10 - y) + 200; // transformed to (x - y) + 190;
    }

    @Run(test = "testInt2")
    public void checkTestInt2(RunInfo info) {
        assertC2Compiled(info);
        // con - y underflows while x - y does not
        Asserts.assertEquals(-2147483458, testInt2(-1, 2147483647));
    }

    @Test
    @IR(counts = {IRNode.SUB_I, "1",
                  IRNode.ADD_I, "1",
                  IRNode.CON_I, "1"})
    public int testSymInt1(int x, int y) {
        return (10 - y) + x + 200; // transformed to (x - y) + 210;
    }

    @Run(test = "testSymInt1")
    public void checkTestSymInt1(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(211, testSymInt1(10, 9));
        Asserts.assertEquals(210, testSymInt1(100, 100)); // x - y == 0
        // con - y overflows while x - y does not
        Asserts.assertEquals(-2147483439, testSymInt1(1, -2147483646));
        // x - y underflows while con - y does not
        Asserts.assertEquals(-2147483447, testSymInt1(-10, 2147483647));
        // x - y overflows while con - y does not
        Asserts.assertEquals(-2147483349, testSymInt1(100, -2147483637));
    }

    @Test
    @IR(counts = {IRNode.SUB_I, "1",
                  IRNode.ADD_I, "1",
                  IRNode.CON_I, "1"})
    public int testSymInt2(int x, int y) {
        return x + (-10 - y) + 200; // transformed to (x - y) + 190;
    }

    @Run(test = "testSymInt2")
    public void checkTestSymInt2(RunInfo info) {
        assertC2Compiled(info);
        // con - y underflows while x - y does not
        Asserts.assertEquals(-2147483458, testSymInt2(-1, 2147483647));
    }

    @Test
    @IR(counts = {IRNode.SUB_L, "1",
                  IRNode.ADD_L, "1",
                  IRNode.CON_L, "1"})
    public long testLong1(long x, long y) {
        return x + (123_456_789_000L - y) + 123; // transformed to (x - y) + 123_456_789_123L;
    }

    @Run(test = "testLong1")
    public void checkTestLong1(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(223_456_789_123L, testLong1(123_456_789_000L, 23_456_789_000L));
        Asserts.assertEquals(123_456_789_123L, testLong1(123_456_789_000L, 123_456_789_000L)); // x - y == 0
        // con - y overflows while x - y does not
        Asserts.assertEquals(-9223371913397986686L, testLong1(1L, -9223372036854775806L));
        // x - y underflows while con - y does not
        Asserts.assertEquals(-9223371913397986694L, testLong1(-10, 9223372036854775807L));
        // x - y overflows while con - y does not
        Asserts.assertEquals(-9223371913397986456L, testLong1(123_456_789_230L, -9223371913397986807L));
    }

    @Test
    @IR(counts = {IRNode.SUB_L, "1",
                  IRNode.ADD_L, "1",
                  IRNode.CON_L, "1"})
    public long testLong2(long x, long y) {
        return x + (-123_456_789_000L - y) + 123; // transformed to (x - y) + -123_456_788_877L;
    }

    @Run(test = "testLong2")
    public void checkTestLong2(RunInfo info) {
        assertC2Compiled(info);
        // con - y underflows while x - y does not
        Asserts.assertEquals(9223371913397986931L, testLong2(-1, 9223372036854775807L));
    }

    @Test
    @IR(counts = {IRNode.SUB_L, "1",
                  IRNode.ADD_L, "1",
                  IRNode.CON_L, "1"})
    public long testSymLong1(long x, long y) {
        return x + (123_456_789_000L - y) + 123; // transformed to (x - y) + 123_456_789_123L;
    }

    @Run(test = "testSymLong1")
    public void checkTestSymLong1(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(223_456_789_123L, testSymLong1(123_456_789_000L, 23_456_789_000L));
        Asserts.assertEquals(123_456_789_123L, testSymLong1(123_456_789_000L, 123_456_789_000L)); // x - y == 0
        // con - y overflows while x - y does not
        Asserts.assertEquals(-9223371913397986686L, testSymLong1(1L, -9223372036854775806L));
        // x - y underflows while con - y does not
        Asserts.assertEquals(-9223371913397986694L, testSymLong1(-10, 9223372036854775807L));
        // x - y overflows while con - y does not
        Asserts.assertEquals(-9223371913397986456L, testSymLong1(123_456_789_230L, -9223371913397986807L));
    }

    @Test
    @IR(counts = {IRNode.SUB_L, "1",
                  IRNode.ADD_L, "1",
                  IRNode.CON_L, "1"})
    public long testSymLong2(long x, long y) {
        return x + (-123_456_789_000L - y) + 123; // transformed to (x - y) + -123_456_788_877L;
    }

    @Run(test = "testSymLong2")
    public void checkTestSymLong2(RunInfo info) {
        assertC2Compiled(info);
        // con - y underflows while x - y does not
        Asserts.assertEquals(9223371913397986931L, testSymLong2(-1, 9223372036854775807L));
    }

    private void assertC2Compiled(RunInfo info) {
        // Test VM allows C2 to work
        Asserts.assertTrue(info.isC2CompilationEnabled());
        if (!info.isWarmUp()) {
            // C2 compilation happens
            Asserts.assertTrue(info.isTestC2Compiled());
        }
    }
}
