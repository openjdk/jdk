/*
 * Copyright (c) 2022, Arm Limited. All rights reserved.
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
 * @bug 8290248
 * @summary Test that Ideal transformations of MaxINode and MinINode are
 * being performed as expected.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.MaxMinINodeIdealizationTests
 */

public class MaxMinINodeIdealizationTests {
    public static void main(String[] args) {
        TestFramework.run();
    }

    @Run(test = {"testMax1", "testMax2", "testMax3", "testMin1", "testMin2", "testMin3"})
    public void runMethod() {
        int a = RunInfo.getRandom().nextInt();
        int min = Integer.MIN_VALUE;
        int max = Integer.MAX_VALUE;

        assertResult(a);
        assertResult(0);
        assertResult(min);
        assertResult(max);
    }

    @DontCompile
    public void assertResult(int a) {
        Asserts.assertEQ(Math.max(((a >> 1) + 100), Math.max(((a >> 1) + 150), 200)), testMax1(a));
        Asserts.assertEQ(Math.max(((a >> 1) + 10), ((a >> 1) + 11))                 , testMax2(a));
        Asserts.assertEQ(Math.max(a, a)                                             , testMax3(a));

        Asserts.assertEQ(Math.min(((a >> 1) + 100), Math.min(((a >> 1) + 150), 200)), testMin1(a));
        Asserts.assertEQ(Math.min(((a >> 1) + 10), ((a >> 1) + 11))                 , testMin2(a));
        Asserts.assertEQ(Math.min(a, a)                                             , testMin3(a));
    }

    // The transformations in test*1 and test*2 can happen only if the compiler has enough information
    // to determine that the two addition operations can not overflow.

    // Transform max(x + c0, max(y + c1, z)) to max(add(x, c2), z) if x == y, where c2 = MAX2(c0, c1).
    // c0,c1,c2 are constants. x,y,z can be any valid c2 nodes. In this example, x and y are
    // RShiftI nodes and z is a ConI.
    @Test
    @IR(counts = {IRNode.Max_I, "1",
                  IRNode.ADD  , "1",
                 })
    public int testMax1(int i) {
        return Math.max(((i >> 1) + 100), Math.max(((i >> 1) + 150), 200));
    }

    // Similarly, transform min(x + c0, min(y + c1, z)) to min(add(x, c2), z) if x == y, where c2 = MIN2(c0, c1).
    @Test
    @IR(counts = {IRNode.Min_I, "1",
                  IRNode.ADD  , "1",
                 })
    public int testMin1(int i) {
        return Math.min(((i >> 1) + 100), Math.min(((i >> 1) + 150), 200));
    }

    // Transform max(x + c0, y + c1) to add(x, c2) if x == y, where c2 = MAX2(c0, c1).
    // c0,c1,c2 are constants. x and y can be any valid c2 nodes. If they are equal, this
    // transformation would take place. In this example, x and y are same RShiftI nodes.
    @Test
    @IR(failOn = {IRNode.Max_I})
    @IR(counts = {IRNode.ADD, "1"})
    public int testMax2(int i) {
        return Math.max((i >> 1) + 10, (i >> 1) + 11);
    }

    // Similarly, transform min(x + c0, y + c1) to add(x, c2) if x == y, where c2 = MIN2(c0, c1).
    @Test
    @IR(failOn = {IRNode.Min_I})
    @IR(counts = {IRNode.ADD, "1"})
    public int testMin2(int i) {
        return Math.min((i >> 1) + 10, (i >> 1) + 11);
    }

    // Return the same node without generating a MaxINode/MinINode when a node is compared with itself.
    // In this test, an integer is being compared with itself but it can be any valid c2 node.
    @Test
    @IR(failOn = {IRNode.Max_I})
    public int testMax3(int i) {
        return Math.max(i, i);
    }

    @Test
    @IR(failOn = {IRNode.Min_I})
    public int testMin3(int i) {
        return Math.min(i, i);
    }
}
