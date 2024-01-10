/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8297384 8315066
 * @summary Test that Ideal transformations of AndINode* are being performed as expected.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.AndINodeIdealizationTests
 */
public class AndINodeIdealizationTests {

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Run(test = {"test1", "test2", "test3",
                 "test4", "test5", "test6"})
    public void runMethod() {
        int a = RunInfo.getRandom().nextInt();
        int b = RunInfo.getRandom().nextInt();

        int min = Integer.MIN_VALUE;
        int max = Integer.MAX_VALUE;

        assertResult(0, 0);
        assertResult(a, b);
        assertResult(min, min);
        assertResult(max, max);
    }

    @DontCompile
    public void assertResult(int a, int b) {
        Asserts.assertEQ((0 - a) & 1, test1(a));
        Asserts.assertEQ((~a) & (~b), test2(a, b));
        Asserts.assertEQ(b << 8, test3(a, b));
        Asserts.assertEQ(1, test4(a, b));
        Asserts.assertEQ(0, test5(a, b));
        Asserts.assertEQ(1, test6(a, b));
    }

    @Test
    @IR(failOn = { IRNode.SUB })
    @IR(counts = { IRNode.AND, "1" })
    // Checks (0 - x) & 1 => x & 1
    public int test1(int x) {
        return (0 - x) & 1;
    }

    @Test
    @IR(failOn = { IRNode.AND })
    @IR(counts = { IRNode.OR, "1",
                   IRNode.XOR, "1" })
    // Checks (~a) & (~b) => ~(a | b)
    public int test2(int a, int b) {
        return (~a) & (~b);
    }

    @Test
    @IR(failOn = {IRNode.AND_I, IRNode.OR_I})
    // All bits that can be unset in one operand is known to be unset in the other
    public int test3(int x, int y) {
        return (x | -256) & (y << 8);
    }

    @Test
    @IR(failOn = {IRNode.AND_I, IRNode.OR_I})
    // Bits set in both are set in the result
    public int test4(int x, int y) {
        return ((x | 3) & (y | 101)) & 1;
    }

    @Test
    @IR(failOn = {IRNode.AND_I})
    // Bits unset in either are unset in the result
    public int test5(int x, int y) {
        return (x & (y & 6)) & 1;
    }

    @Test
    @IR(failOn = {IRNode.AND_I})
    // The unsigned value of the result is smaller than both operands
    public int test6(int x, int y) {
        return Integer.compareUnsigned(((byte)x + 150) & y, 300) < 0 ? 1 : 0;
    }
}
