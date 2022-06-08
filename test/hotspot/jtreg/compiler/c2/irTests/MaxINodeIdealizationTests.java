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
 * @bug 8288107
 * @summary Test that Ideal transformations of MaxINode* are being performed as expected.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.MaxINodeIdealizationTests
 */

public class MaxINodeIdealizationTests {
    public static void main(String[] args) {
        TestFramework.run();
    }

    @Run(test = {"test1", "test2", "test3"})
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
        Asserts.assertEQ(Math.max(((a >> 1) + 100), Math.max(((a >> 1) + 150), 200)), test1(a));
        Asserts.assertEQ(Math.max(((a >> 1) + 10), ((a >> 1) + 11))                 , test2(a));
        Asserts.assertEQ(Math.max(a, a)                                             , test3(a));
    }

    // The transformations in test1 and test2 can happen only if the compiler has enough information
    // to determine that the two addition operations can not overflow.

    // Transform max(x + c0, max(y + c1, z)) to max(add(x, c2), z) if x == y, where c2 = MAX2(c0, c1).
    // c0,c1 and c2 are constants. x,y,z can be any valid c2 nodes. In this example, x and y are
    // RShiftI nodes and z is a ConI.
    @Test
    @IR(counts = {IRNode.Max_I, "1",
                  IRNode.ADD, "1",
                 })
    public int test1(int i) {
        return Math.max(((i >> 1) + 100), Math.max(((i >> 1) + 150), 200));
    }

    // Transform max(x + c0, y + c1) to add(x, c2) if x == y, where c2 = MAX2(c0, c1).
    // c0,c1,c2 are constants. x and y can be any valid c2 nodes. If they are equal, this
    // transformation would take place. In this example, x and y are same RShiftI nodes.
    @Test
    @IR(failOn = {IRNode.Max_I})
    @IR(counts = {IRNode.ADD, "1"})
    public int test2(int i) {
        return Math.max((i >> 1) + 10, (i >> 1) + 11);
    }

    // Do not perform a max operation when comparing the same node. As long as the same node is being
    // compared, a max operation will not be performed and instead the same node is returned. In this
    // test, an integer is being compared with itself but it can be any valid c2 node.
    @Test
    @IR(failOn = {IRNode.Max_I})
    public int test3(int i) {
        return Math.max(i, i);
    }
}
