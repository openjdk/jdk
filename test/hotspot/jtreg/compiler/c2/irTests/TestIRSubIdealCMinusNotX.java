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
 * @summary Test that transformation from c - (~x) to x + (c + 1) and
 *          from ~(c - x) to x + (-c - 1) works as intended.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.TestIRSubIdealCMinusNotX
 */
public class TestIRSubIdealCMinusNotX {

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Test
    @IR(failOn = {IRNode.SUB_I, IRNode.XOR_I})
    @IR(counts = {IRNode.ADD_I, "1"})
    public int testIntConIsNormal1(int x) {
        return 1234 - ~x; // transformed to x + 1235
    }

    @Run(test = "testIntConIsNormal1")
    public void checkTestIntConIsNormal1(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(1245, testIntConIsNormal1(10));
        Asserts.assertEquals(1235, testIntConIsNormal1(0));
    }

    @Test
    @IR(failOn = {IRNode.SUB_I, IRNode.XOR_I})
    @IR(counts = {IRNode.ADD_I, "1"})
    public int testIntConIsNormal2(int x) {
        return ~(-1234 - x); // transformed to x + 1233
    }

    @Run(test = "testIntConIsNormal2")
    public void checkTestIntConIsNormal2(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(1243, testIntConIsNormal2(10));
        Asserts.assertEquals(1233, testIntConIsNormal2(0));
    }

    @Test
    @IR(failOn = {IRNode.SUB_L, IRNode.XOR_L})
    @IR(counts = {IRNode.ADD_L, "1"})
    public long testLongConIsNormal1(long x) {
        return 123_456_789_123L - ~x; // transformed to x + 123_456_789_124L
    }

    @Run(test = "testLongConIsNormal1")
    public void checkTestLongConIsNormal1(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(133_456_789_124L, testLongConIsNormal1(10_000_000_000L));
        Asserts.assertEquals(123_456_789_124L, testLongConIsNormal1(0L));
    }

    @Test
    @IR(failOn = {IRNode.SUB_L, IRNode.XOR_L})
    @IR(counts = {IRNode.ADD_L, "1"})
    public long testLongConIsNormal2(long x) {
        return ~(-123_456_789_123L - x); // transformed to x + 123_456_789_122L
    }

    @Run(test = "testLongConIsNormal2")
    public void checkTestLongConIsNormal2(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(133_456_789_122L, testLongConIsNormal2(10_000_000_000L));
        Asserts.assertEquals(123_456_789_122L, testLongConIsNormal2(0L));
    }

    @Test
    @IR(failOn = {IRNode.SUB_I, IRNode.XOR_I})
    @IR(counts = {IRNode.ADD_I, "1"})
    public int testIntConIsZero1(int x) {
        return 0 - ~x; // transformed to x + 1
    }

    @Run(test = "testIntConIsZero1")
    public void checkTestIntConIsZero1(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(11, testIntConIsZero1(10));
    }

    @Test
    @IR(failOn = {IRNode.SUB_I, IRNode.XOR_I})
    @IR(counts = {IRNode.ADD_I, "1"})
    public int testIntConIsZero2(int x) {
        return ~(0 - x); // transformed to x + -1
    }

    @Run(test = "testIntConIsZero2")
    public void checkTestIntConIsZero2(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(9, testIntConIsZero2(10));
    }

    
    @Test
    @IR(failOn = {IRNode.SUB_L, IRNode.XOR_L})
    @IR(counts = {IRNode.ADD_L, "1"})
    public long testLongConIsZero1(long x) {
        return 0L - ~x; // transformed to x + 1
    }

    @Run(test = "testLongConIsZero1")
    public void checkTestLongConIsZero1(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(10_000_000_001L, testLongConIsZero1(10_000_000_000L));
    }

    @Test
    @IR(failOn = {IRNode.SUB_L, IRNode.XOR_L})
    @IR(counts = {IRNode.ADD_L, "1"})
    public long testLongConIsZero2(long x) {
        return ~(0L - x); // transformed to x + -1
    }

    @Run(test = "testLongConIsZero2")
    public void checkTestLongConIsZero2(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(9_999_999_999L, testLongConIsZero2(10_000_000_000L));
    }

    @Test
    @IR(failOn = {IRNode.SUB_I, IRNode.XOR_I, IRNode.ADD_I})
    public int testIntConIsNegOne1(int x) {
        return -1 - ~x; // transformed to x
    }

    @Run(test = "testIntConIsNegOne1")
    public void checkTestIntConIsNegOne1(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(10, testIntConIsNegOne1(10));
    }

    @Test
    @IR(failOn = {IRNode.SUB_I, IRNode.XOR_I, IRNode.ADD_I})
    public int testIntConIsNegOne2(int x) {
        return ~(-1 - x); // transformed to x
    }

    @Run(test = "testIntConIsNegOne2")
    public void checkTestIntConIsNegOne2(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(10, testIntConIsNegOne2(10));
    }

    @Test
    @IR(failOn = {IRNode.SUB_L, IRNode.XOR_L, IRNode.ADD_L})
    public long testLongConIsNegOne1(long x) {
        return -1L - ~x; // transformed to x
    }

    @Run(test = "testLongConIsNegOne1")
    public void checkTestLongConIsNegOne1(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(10_000_000_000L, testLongConIsNegOne1(10_000_000_000L));
    }

    @Test
    @IR(failOn = {IRNode.SUB_L, IRNode.XOR_L, IRNode.ADD_L})
    public long testLongConIsNegOne2(long x) {
        return ~(-1L - x); // transformed to x
    }

    @Run(test = "testLongConIsNegOne2")
    public void checkTestLongConIsNegOne2(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(10_000_000_000L, testLongConIsNegOne2(10_000_000_000L));
    }

    @Test
    @IR(failOn = {IRNode.SUB_I, IRNode.XOR_I})
    @IR(counts = {IRNode.ADD_I, "1"})
    public int testIntConPlusOneIsOverflow1(int x) {
        return Integer.MAX_VALUE - ~x; // transformed to x + Integer.MIN_VALUE
    }

    @Run(test = "testIntConPlusOneIsOverflow1")
    public void checkTestIntConPlusOneIsOverflow1(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(-2147483638, testIntConPlusOneIsOverflow1(10));
    }

    @Test
    @IR(failOn = {IRNode.SUB_I, IRNode.XOR_I})
    @IR(counts = {IRNode.ADD_I, "1"})
    public int testIntNegConMinusOneIsUnderflow2(int x) {
        return ~(Integer.MIN_VALUE - x); // transformed to x + Integer.MAX_VALUE
    }

    @Run(test = "testIntNegConMinusOneIsUnderflow2")
    public void checkTestIntNegConMinusOneIsUnderflow2(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(-2147483639, testIntNegConMinusOneIsUnderflow2(10));
    }

    @Test
    @IR(failOn = {IRNode.SUB_L, IRNode.XOR_L})
    @IR(counts = {IRNode.ADD_L, "1"})
    public long testLongConPlusOneIsOverflow1(long x) {
        return Long.MAX_VALUE - ~x; // transformed to x + Long.MIN_VALUE
    }

    @Run(test = "testLongConPlusOneIsOverflow1")
    public void checkTestLongConPlusOneIsOverflow1(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(-9_223_372_036_854_775_798L, testLongConPlusOneIsOverflow1(10));
    }

    @Test
    @IR(failOn = {IRNode.SUB_L, IRNode.XOR_L})
    @IR(counts = {IRNode.ADD_L, "1"})
    public long testLongNegConMinusOneIsUnderflow2(long x) {
        return ~(Long.MIN_VALUE - x); // transformed to x + Long.MAX_VALUE
    }

    @Run(test = "testLongNegConMinusOneIsUnderflow2")
    public void checkTestLongNegConMinusOneIsUnderflow2(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(-9_223_372_036_854_775_799L, testLongNegConMinusOneIsUnderflow2(10));
    }

    @Test
    @IR(failOn = {IRNode.SUB_I, IRNode.XOR_I})
    @IR(counts = {IRNode.ADD_I, "1"})
    public int testIntResultIsUnderflow1(int x) {
        return -2147483640 - ~x; // transformed to x + -2147483639
    }

    @Run(test = "testIntResultIsUnderflow1")
    public void checkTestIntResultIsUnderflow1(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(Integer.MAX_VALUE, testIntResultIsUnderflow1(-10));
    }

    @Test
    @IR(failOn = {IRNode.SUB_I, IRNode.XOR_I})
    @IR(counts = {IRNode.ADD_I, "1"})
    public int testIntResultIsUnderflow2(int x) {
        return ~(2147483638 - x); // transformed to x + -2147483639
    }

    @Run(test = "testIntResultIsUnderflow2")
    public void checkTestIntResultIsUnderflow2(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(Integer.MAX_VALUE, testIntResultIsUnderflow2(-10));
    }

    @Test
    @IR(failOn = {IRNode.SUB_L, IRNode.XOR_L})
    @IR(counts = {IRNode.ADD_L, "1"})
    public long testLongResultIsUnderflow1(long x) {
        return -9_223_372_036_854_775_800L - ~x; // transformed to x + -9_223_372_036_854_775_799L
    }

    @Run(test = "testLongResultIsUnderflow1")
    public void checkTestLongResultIsUnderflow1(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(Long.MAX_VALUE, testLongResultIsUnderflow1(-10));
    }

    @Test
    @IR(failOn = {IRNode.SUB_L, IRNode.XOR_L})
    @IR(counts = {IRNode.ADD_L, "1"})
    public long testLongResultIsUnderflow2(long x) {
        return ~(9223372036854775798L - x); // transformed to x + -9223372036854775799L
    }

    @Run(test = "testLongResultIsUnderflow2")
    public void checkTestLongResultIsUnderflow2(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(Long.MAX_VALUE, testLongResultIsUnderflow2(-10));
    }

    @Test
    @IR(failOn = {IRNode.SUB_I, IRNode.XOR_I})
    @IR(counts = {IRNode.ADD_I, "1"})
    public int testIntResultIsOverflow1(int x) {
        return 2147483636 - ~x; // transformed to x + 2147483637
    }

    @Run(test = "testIntResultIsOverflow1")
    public void checkTestIntResultIsOverflow1(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(Integer.MIN_VALUE, testIntResultIsOverflow1(11));
    }

    @Test
    @IR(failOn = {IRNode.SUB_I, IRNode.XOR_I})
    @IR(counts = {IRNode.ADD_I, "1"})
    public int testIntResultIsOverflow2(int x) {
        return ~(-2147483637 - x); // transformed to x + 2147483636
    }

    @Run(test = "testIntResultIsOverflow2")
    public void checkTestIntResultIsOverflow2(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(Integer.MIN_VALUE, testIntResultIsOverflow2(12));
    }

    @Test
    @IR(failOn = {IRNode.SUB_L, IRNode.XOR_L})
    @IR(counts = {IRNode.ADD_L, "1"})
    public long testLongResultIsOverflow1(long x) {
        return 9_223_372_036_854_775_796L - ~x; // transformed to x + 9_223_372_036_854_775_797L
    }

    @Run(test = "testLongResultIsOverflow1")
    public void checkTestLongResultIsOverflow1(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(Long.MIN_VALUE, testLongResultIsOverflow1(11));
    }

    @Test
    @IR(failOn = {IRNode.SUB_L, IRNode.XOR_L})
    @IR(counts = {IRNode.ADD_L, "1"})
    public long testLongResultIsOverflow2(long x) {
        return ~(-9_223_372_036_854_775_797L - x); // transformed to x + 9_223_372_036_854_775_796L
    }

    @Run(test = "testLongResultIsOverflow2")
    public void checkTestLongResultIsOverflow2(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(Long.MIN_VALUE, testLongResultIsOverflow2(12));
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
