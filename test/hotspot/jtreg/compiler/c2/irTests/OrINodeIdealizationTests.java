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
package compiler.c2.irTests;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8315066
 * @summary Test that Ideal transformations of OrINode* are being performed as expected.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.OrINodeIdealizationTests
 */
public class OrINodeIdealizationTests {
    public static void main(String[] args) {
        TestFramework.run();
    }

    @Run(test = {"test1", "test2", "test3",
                 "test4"})
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
        Asserts.assertEQ(b | 15, test1(a, b));
        Asserts.assertEQ(0, test2(a, b));
        Asserts.assertEQ(1, test3(a, b));
        Asserts.assertEQ(1, test4(a, b));
    }

    @Test
    @IR(failOn = {IRNode.AND_I})
    @IR(counts = {IRNode.OR_I, "1"})
    // All bits that can be set in one operand is known to be set in the other
    public int test1(int x, int y) {
        return (x & 7) | (y | 15);
    }

    @Test
    @IR(failOn = {IRNode.OR_I, IRNode.AND_I})
    // Bits unset in both are unset in the result
    public int test2(int x, int y) {
        return ((x & -2) | (y & -6)) & 1;
    }

    @Test
    @IR(failOn = {IRNode.OR_I})
    // Bits set in either are set in the result
    public int test3(int x, int y) {
        return (x | (y | 5)) & 1;
    }

    @Test
    @IR(failOn = {IRNode.OR_I})
    // The unsigned value of the result is larger than both operands
    public int test4(int x, int y) {
        return Integer.compareUnsigned(((byte)x + 150) | y, 20) > 0 ? 1 : 0;
    }
}
