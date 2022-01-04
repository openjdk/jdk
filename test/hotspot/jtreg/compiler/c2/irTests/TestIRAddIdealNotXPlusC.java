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
 * @summary Test that transformation from ~x + c to (c - 1) - x works
 *          as intended.
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
    public int testIntConIsNormal(int x) {
        return ~x + 1234; // transformed to 1233 - x
    }

    @Run(test = "testIntConIsNormal")
    public void checkTestIntConIsNormal(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(1223, testIntConIsNormal(10));
        Asserts.assertEquals(1233, testIntConIsNormal(0));
    }

    @Test
    @IR(failOn = {IRNode.ADD_L, IRNode.XOR_L})
    @IR(counts = {IRNode.SUB_L, "1"})
    public long testLongConIsNormal(long x) {
        return ~x + 123_456_789_123L; // transformed to 123_456_789_122L - x
    }

    @Run(test = "testLongConIsNormal")
    public void checkTestLongConIsNormal(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(113_456_789_122L, testLongConIsNormal(10_000_000_000L));
        Asserts.assertEquals(123_456_789_122L, testLongConIsNormal(0L));
    }

    @Test
    @IR(failOn = {IRNode.ADD_I, IRNode.XOR_I})
    @IR(counts = {IRNode.SUB_I, "1"})
    public int testIntConIsZero(int x) {
        return ~x + 0; // transformed to -1 - x
    }

    @Run(test = "testIntConIsZero")
    public void checkTestIntConIsZero(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(-11, testIntConIsZero(10));
    }

    @Test
    @IR(failOn = {IRNode.ADD_L, IRNode.XOR_L})
    @IR(counts = {IRNode.SUB_L, "1"})
    public long testLongConIsZero(long x) {
        return ~x + 0L; // transformed to -1 - x
    }

    @Run(test = "testLongConIsZero")
    public void checkTestLongConIsZero(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(-10_000_000_001L, testLongConIsZero(10_000_000_000L));
    }

    @Test
    @IR(failOn = {IRNode.ADD_I, IRNode.XOR_I})
    @IR(counts = {IRNode.SUB_I, "1"})
    public int testIntConIsOne(int x) {
        return ~x + 1; // transformed to 0 - x
    }

    @Run(test = "testIntConIsOne")
    public void checkTestIntConIsOne(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(-10, testIntConIsOne(10));
    }

    @Test
    @IR(failOn = {IRNode.ADD_L, IRNode.XOR_L})
    @IR(counts = {IRNode.SUB_L, "1"})
    public long testLongConIsOne(long x) {
        return ~x + 1L; // transformed to 0 - x
    }

    @Run(test = "testLongConIsOne")
    public void checkTestLongConIsOne(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(-10_000_000_000L, testLongConIsOne(10_000_000_000L));
    }

    @Test
    @IR(failOn = {IRNode.ADD_I, IRNode.XOR_I})
    @IR(counts = {IRNode.SUB_I, "1"})
    public int testIntConMinusOneIsUnderflow(int x) {
        return ~x + Integer.MIN_VALUE; // transformed to Integer.MAX_VALUE - x
    }

    @Run(test = "testIntConMinusOneIsUnderflow")
    public void checkTestIntConMinusOneIsUnderflow(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(2147483637, testIntConMinusOneIsUnderflow(10));
    }

    @Test
    @IR(failOn = {IRNode.ADD_L, IRNode.XOR_L})
    @IR(counts = {IRNode.SUB_L, "1"})
    public long testLongConMinusOneIsUnderflow(long x) {
        return ~x + Long.MIN_VALUE; // transformed to Long.MAX_VALUE - x
    }

    @Run(test = "testLongConMinusOneIsUnderflow")
    public void checkTestLongConMinusOneIsUnderflow(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(9223372036854775797L, testLongConMinusOneIsUnderflow(10));
    }

    @Test
    @IR(failOn = {IRNode.ADD_I, IRNode.XOR_I})
    @IR(counts = {IRNode.SUB_I, "1"})
    public int testIntResultIsUnderflow(int x) {
        return ~x + -2147483638; // transformed to -2147483639 - x
    }

    @Run(test = "testIntResultIsUnderflow")
    public void checkTestIntResultIsUnderflow(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(Integer.MAX_VALUE, testIntResultIsUnderflow(10));
    }

    @Test
    @IR(failOn = {IRNode.ADD_L, IRNode.XOR_L})
    @IR(counts = {IRNode.SUB_L, "1"})
    public long testLongResultIsUnderflow(long x) {
        return ~x + -9223372036854775798L; // transformed to -9223372036854775799L - x
    }

    @Run(test = "testLongResultIsUnderflow")
    public void checkTestLongResultIsUnderflow(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(Long.MAX_VALUE, testLongResultIsUnderflow(10));
    }

    @Test
    @IR(failOn = {IRNode.ADD_I, IRNode.XOR_I})
    @IR(counts = {IRNode.SUB_I, "1"})
    public int testIntResultIsOverflow(int x) {
        return ~x + 2147483637; // transformed to 2147483646 - x
    }

    @Run(test = "testIntResultIsOverflow")
    public void checkTestIntResultIsOverflow(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(Integer.MIN_VALUE, testIntResultIsOverflow(-12));
    }

    @Test
    @IR(failOn = {IRNode.ADD_L, IRNode.XOR_L})
    @IR(counts = {IRNode.SUB_L, "1"})
    public long testLongResultIsOverflow(long x) {
        return ~x + 9223372036854775797L; // transformed to 9223372036854775798L - x
    }

    @Run(test = "testLongResultIsOverflow")
    public void checkTestLongResultIsOverflow(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(Long.MIN_VALUE, testLongResultIsOverflow(-12));
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
