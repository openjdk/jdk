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
 * @bug 8279607
 * @summary Test that transformation from ~x + c to (c - 1) - x and
 *          from ~(x + c) to (-c - 1) - x works as intended.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.TestIRAddIdealNotXPlusC
 */
public class TestIRAddIdealNotXPlusC {

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Test
    @IR(failOn = {IRNode.ADD_I, IRNode.XOR_I})
    @IR(counts = {IRNode.SUB_I, "1"})
    public int testIntConIsNormal1(int x) {
        return ~x + 1234; // transformed to 1233 - x
    }

    @Run(test = "testIntConIsNormal1")
    public void checkTestIntConIsNormal1(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(1223, testIntConIsNormal1(10));
        Asserts.assertEquals(1233, testIntConIsNormal1(0));
    }

    @Test
    @IR(failOn = {IRNode.ADD_I, IRNode.XOR_I})
    @IR(counts = {IRNode.SUB_I, "1"})
    public int testIntConIsNormal2(int x) {
        return ~(x + -1234); // transformed to 1233 - x
    }

    @Run(test = "testIntConIsNormal2")
    public void checkTestIntConIsNormal2(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(1223, testIntConIsNormal2(10));
        Asserts.assertEquals(1233, testIntConIsNormal2(0));
    }

    @Test
    @IR(failOn = {IRNode.ADD_L, IRNode.XOR_L})
    @IR(counts = {IRNode.SUB_L, "1"})
    public long testLongConIsNormal1(long x) {
        return ~x + 123_456_789_123L; // transformed to 123_456_789_122L - x
    }

    @Run(test = "testLongConIsNormal1")
    public void checkTestLongConIsNormal1(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(113_456_789_122L, testLongConIsNormal1(10_000_000_000L));
        Asserts.assertEquals(123_456_789_122L, testLongConIsNormal1(0L));
    }

    @Test
    @IR(failOn = {IRNode.ADD_L, IRNode.XOR_L})
    @IR(counts = {IRNode.SUB_L, "1"})
    public long testLongConIsNormal2(long x) {
        return ~(x + -123_456_789_123L); // transformed to 123_456_789_122L - x
    }

    @Run(test = "testLongConIsNormal2")
    public void checkTestLongConIsNormal2(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(113_456_789_122L, testLongConIsNormal2(10_000_000_000L));
        Asserts.assertEquals(123_456_789_122L, testLongConIsNormal2(0L));
    }

    @Test
    @IR(failOn = {IRNode.ADD_I, IRNode.XOR_I})
    @IR(counts = {IRNode.SUB_I, "1"})
    public int testIntConIsZero1(int x) {
        return ~x + 0; // transformed to -1 - x
    }

    @Run(test = "testIntConIsZero1")
    public void checkTestIntConIsZero1(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(-11, testIntConIsZero1(10));
    }

    @Test
    @IR(failOn = {IRNode.ADD_I, IRNode.SUB_I})
    @IR(counts = {IRNode.XOR_I, "1"})
    public int testIntConIsZero2(int x) {
        return ~(x + 0); // should not happen, transformed to ~x
    }

    @Run(test = "testIntConIsZero2")
    public void checkTestIntConIsZero2(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(-11, testIntConIsZero2(10));
    }

    @Test
    @IR(failOn = {IRNode.ADD_L, IRNode.XOR_L})
    @IR(counts = {IRNode.SUB_L, "1"})
    public long testLongConIsZero1(long x) {
        return ~x + 0L; // transformed to -1 - x
    }

    @Run(test = "testLongConIsZero1")
    public void checkTestLongConIsZero1(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(-10_000_000_001L, testLongConIsZero1(10_000_000_000L));
    }

    @Test
    @IR(failOn = {IRNode.ADD_L, IRNode.SUB_L})
    @IR(counts = {IRNode.XOR_L, "1"})
    public long testLongConIsZero2(long x) {
        return ~(x + 0L); // should not happen, transformed to ~x
    }

    @Run(test = "testLongConIsZero2")
    public void checkTestLongConIsZero2(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(-10_000_000_001L, testLongConIsZero2(10_000_000_000L));
    }

    @Test
    @IR(failOn = {IRNode.ADD_I, IRNode.XOR_I})
    @IR(counts = {IRNode.SUB_I, "1"})
    public int testIntConIsOne1(int x) {
        return ~x + 1; // transformed to 0 - x
    }

    @Run(test = "testIntConIsOne1")
    public void checkTestIntConIsOne1(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(-10, testIntConIsOne1(10));
    }

    @Test
    @IR(failOn = {IRNode.ADD_I, IRNode.XOR_I})
    @IR(counts = {IRNode.SUB_I, "1"})
    public int testIntConIsNegOne2(int x) {
        return ~(x + -1); // transformed to 0 - x
    }

    @Run(test = "testIntConIsNegOne2")
    public void checkTestIntConIsNegOne2(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(-10, testIntConIsNegOne2(10));
    }

    @Test
    @IR(failOn = {IRNode.ADD_L, IRNode.XOR_L})
    @IR(counts = {IRNode.SUB_L, "1"})
    public long testLongConIsOne1(long x) {
        return ~x + 1L; // transformed to 0 - x
    }

    @Run(test = "testLongConIsOne1")
    public void checkTestLongConIsOne1(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(-10_000_000_000L, testLongConIsOne1(10_000_000_000L));
    }

    @Test
    @IR(failOn = {IRNode.ADD_L, IRNode.XOR_L})
    @IR(counts = {IRNode.SUB_L, "1"})
    public long testLongConIsNegOne2(long x) {
        return ~(x + -1L); // transformed to 0 - x
    }

    @Run(test = "testLongConIsNegOne2")
    public void checkTestLongConIsNegOne2(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(-10_000_000_000L, testLongConIsNegOne2(10_000_000_000L));
    }

    @Test
    @IR(failOn = {IRNode.ADD_I, IRNode.XOR_I})
    @IR(counts = {IRNode.SUB_I, "1"})
    public int testIntConMinusOneIsUnderflow1(int x) {
        return ~x + Integer.MIN_VALUE; // transformed to Integer.MAX_VALUE - x
    }

    @Run(test = "testIntConMinusOneIsUnderflow1")
    public void checkTestIntConMinusOneIsUnderflow1(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(2147483637, testIntConMinusOneIsUnderflow1(10));
    }

    @Test
    @IR(failOn = {IRNode.ADD_I, IRNode.XOR_I})
    @IR(counts = {IRNode.SUB_I, "1"})
    public int testIntNegConMinusOneIsUnderflow2(int x) {
        return ~(x + Integer.MIN_VALUE); // transformed to Integer.MAX_VALUE - x
    }

    @Run(test = "testIntNegConMinusOneIsUnderflow2")
    public void checkTestIntNegConMinusOneIsUnderflow2(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(2147483637, testIntNegConMinusOneIsUnderflow2(10));
    }

    @Test
    @IR(failOn = {IRNode.ADD_L, IRNode.XOR_L})
    @IR(counts = {IRNode.SUB_L, "1"})
    public long testLongConMinusOneIsUnderflow1(long x) {
        return ~x + Long.MIN_VALUE; // transformed to Long.MAX_VALUE - x
    }

    @Run(test = "testLongConMinusOneIsUnderflow1")
    public void checkTestLongConMinusOneIsUnderflow1(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(9223372036854775797L, testLongConMinusOneIsUnderflow1(10));
    }

    @Test
    @IR(failOn = {IRNode.ADD_L, IRNode.XOR_L})
    @IR(counts = {IRNode.SUB_L, "1"})
    public long testLongNegConMinusOneIsUnderflow2(long x) {
        return ~(x + Long.MIN_VALUE); // transformed to Long.MAX_VALUE - x
    }

    @Run(test = "testLongNegConMinusOneIsUnderflow2")
    public void checkTestLongNegConMinusOneIsUnderflow2(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(9223372036854775797L, testLongNegConMinusOneIsUnderflow2(10));
    }

    @Test
    @IR(failOn = {IRNode.ADD_I, IRNode.XOR_I})
    @IR(counts = {IRNode.SUB_I, "1"})
    public int testIntResultIsUnderflow1(int x) {
        return ~x + -2147483638; // transformed to -2147483639 - x
    }

    @Run(test = "testIntResultIsUnderflow1")
    public void checkTestIntResultIsUnderflow1(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(Integer.MAX_VALUE, testIntResultIsUnderflow1(10));
    }

    @Test
    @IR(failOn = {IRNode.ADD_I, IRNode.XOR_I})
    @IR(counts = {IRNode.SUB_I, "1"})
    public int testIntResultIsUnderflow2(int x) {
        return ~(x + 2147483638); // transformed to -2147483639 - x
    }

    @Run(test = "testIntResultIsUnderflow2")
    public void checkTestIntResultIsUnderflow2(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(Integer.MAX_VALUE, testIntResultIsUnderflow2(10));
    }

    @Test
    @IR(failOn = {IRNode.ADD_L, IRNode.XOR_L})
    @IR(counts = {IRNode.SUB_L, "1"})
    public long testLongResultIsUnderflow1(long x) {
        return ~x + -9223372036854775798L; // transformed to -9223372036854775799L - x
    }

    @Run(test = "testLongResultIsUnderflow1")
    public void checkTestLongResultIsUnderflow1(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(Long.MAX_VALUE, testLongResultIsUnderflow1(10));
    }

    @Test
    @IR(failOn = {IRNode.ADD_L, IRNode.XOR_L})
    @IR(counts = {IRNode.SUB_L, "1"})
    public long testLongResultIsUnderflow2(long x) {
        return ~(x + 9223372036854775798L); // transformed to -9223372036854775799L - x
    }

    @Run(test = "testLongResultIsUnderflow2")
    public void checkTestLongResultIsUnderflow2(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(Long.MAX_VALUE, testLongResultIsUnderflow2(10));
    }

    @Test
    @IR(failOn = {IRNode.ADD_I, IRNode.XOR_I})
    @IR(counts = {IRNode.SUB_I, "1"})
    public int testIntResultIsOverflow1(int x) {
        return ~x + 2147483637; // transformed to 2147483646 - x
    }

    @Run(test = "testIntResultIsOverflow1")
    public void checkTestIntResultIsOverflow1(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(Integer.MIN_VALUE, testIntResultIsOverflow1(-12));
    }
    @Test
    @IR(failOn = {IRNode.ADD_I, IRNode.XOR_I})
    @IR(counts = {IRNode.SUB_I, "1"})
    public int testIntResultIsOverflow2(int x) {
        return ~(x + -2147483637); // transformed to 2147483646 - x
    }

    @Run(test = "testIntResultIsOverflow2")
    public void checkTestIntResultIsOverflow2(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(Integer.MIN_VALUE, testIntResultIsOverflow2(-12));
    }

    @Test
    @IR(failOn = {IRNode.ADD_L, IRNode.XOR_L})
    @IR(counts = {IRNode.SUB_L, "1"})
    public long testLongResultIsOverflow1(long x) {
        return ~x + 9223372036854775797L; // transformed to 9223372036854775798L - x
    }

    @Run(test = "testLongResultIsOverflow1")
    public void checkTestLongResultIsOverflow1(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(Long.MIN_VALUE, testLongResultIsOverflow1(-12));
    }

    @Test
    @IR(failOn = {IRNode.ADD_L, IRNode.XOR_L})
    @IR(counts = {IRNode.SUB_L, "1"})
    public long testLongResultIsOverflow2(long x) {
        return ~(x + -9223372036854775797L); // transformed to 9223372036854775798L - x
    }

    @Run(test = "testLongResultIsOverflow2")
    public void checkTestLongResultIsOverflow2(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(Long.MIN_VALUE, testLongResultIsOverflow2(-12));
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
