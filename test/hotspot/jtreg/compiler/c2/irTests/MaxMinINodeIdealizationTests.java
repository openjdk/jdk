/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8290248 8312547
 * @summary Test that Ideal transformations of MaxINode and MinINode are
 * being performed as expected.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.MaxMinINodeIdealizationTests
 */

public class MaxMinINodeIdealizationTests {
    public static void main(String[] args) {
        TestFramework.run();
    }

    @Run(test = {"testMax1LL", "testMax1LR", "testMax1RL", "testMax1RR",
                 "testMax1LLNoInnerAdd", "testMax1LLNoInnerAdd2", "testMax1LLNoOuterAdd", "testMax1LLNoAdd",
                 "testMax2L", "testMax2R",
                 "testMax2LNoLeftAdd",
                 "testMax3",
                 "testMax4",
                 "testMin1",
                 "testMin2",
                 "testMin3",
                 "testMin4"})
    public void runPositiveTests() {
        int a = RunInfo.getRandom().nextInt();
        int min = Integer.MIN_VALUE;
        int max = Integer.MAX_VALUE;

        assertPositiveResult(a);
        assertPositiveResult(0);
        assertPositiveResult(min);
        assertPositiveResult(max);
    }

    @DontCompile
    public void assertPositiveResult(int a) {
        Asserts.assertEQ(Math.max(Math.max(((a >> 1) + 150), 200), ((a >> 1) + 100)), testMax1LL(a));
        Asserts.assertEQ(testMax1LL(a)                                              , testMax1LR(a));
        Asserts.assertEQ(testMax1LL(a)                                              , testMax1RL(a));
        Asserts.assertEQ(testMax1LL(a)                                              , testMax1RR(a));
        Asserts.assertEQ(Math.max(Math.max((a >> 1), 200), (a >> 1) + 100)          , testMax1LLNoInnerAdd(a));
        Asserts.assertEQ(Math.max(Math.max((a >> 1), (a << 1)), (a >> 1) + 100)     , testMax1LLNoInnerAdd2(a));
        Asserts.assertEQ(Math.max(Math.max(((a >> 1) + 150), 200), a >> 1)          , testMax1LLNoOuterAdd(a));
        Asserts.assertEQ(Math.max(Math.max((a >> 1), 200), a >> 1)                  , testMax1LLNoAdd(a));
        Asserts.assertEQ(Math.max(((a >> 1) + 10), ((a >> 1) + 11))                 , testMax2L(a));
        Asserts.assertEQ(testMax2L(a)                                               , testMax2R(a));
        Asserts.assertEQ(Math.max(a >> 1, ((a >> 1) + 11))                          , testMax2LNoLeftAdd(a));
        Asserts.assertEQ(Math.max(a, a)                                             , testMax3(a));
        Asserts.assertEQ(0                                                          , testMax4(a));

        Asserts.assertEQ(Math.min(((a >> 1) + 100), Math.min(((a >> 1) + 150), 200)), testMin1(a));
        Asserts.assertEQ(Math.min(((a >> 1) + 10), ((a >> 1) + 11))                 , testMin2(a));
        Asserts.assertEQ(Math.min(a, a)                                             , testMin3(a));
        Asserts.assertEQ(0                                                          , testMin4(a));
    }

    // The transformations in test*1 and test*2 can happen only if the compiler has enough information
    // to determine that the two addition operations can not overflow.

    // Transform max(x + c0, max(y + c1, z)) to max(add(x, c2), z) if x == y, where c2 = MAX2(c0, c1).
    // c0,c1,c2 are constants. x,y,z can be any valid c2 nodes. In this example, x and y are
    // RShiftI nodes and z is a ConI.
    @Test
    @IR(counts = {IRNode.MAX_I, "1",
                  IRNode.ADD  , "1",
                 })
    public int testMax1LL(int i) {
        return Math.max(Math.max(((i >> 1) + 150), 200), ((i >> 1) + 100));
    }

    @Test
    @IR(counts = {IRNode.MAX_I, "1",
                  IRNode.ADD  , "1",
                 })
    public int testMax1LR(int i) {
        return Math.max(Math.max(200, ((i >> 1) + 150)), ((i >> 1) + 100));
    }

    @Test
    @IR(counts = {IRNode.MAX_I, "1",
                  IRNode.ADD  , "1",
                 })
    public int testMax1RL(int i) {
        return Math.max(((i >> 1) + 100), Math.max(((i >> 1) + 150), 200));
    }

    @Test
    @IR(counts = {IRNode.MAX_I, "1",
                  IRNode.ADD  , "1",
                 })
    public int testMax1RR(int i) {
        return Math.max(((i >> 1) + 100), Math.max(200, ((i >> 1) + 150)));
    }

    @Test
    @IR(counts = {IRNode.MAX_I, "1",
                  IRNode.ADD  , "1",
                 })
    public int testMax1LLNoInnerAdd(int i) {
        return Math.max(Math.max((i >> 1), 200), (i >> 1) + 100);
    }

    @Test
    @IR(counts = {IRNode.MAX_I, "1",
                  IRNode.ADD  , "1",
                 })
    public int testMax1LLNoInnerAdd2(int i) {
        return Math.max(Math.max((i >> 1), (i << 1)), (i >> 1) + 100);
    }

    @Test
    @IR(counts = {IRNode.MAX_I, "1",
                  IRNode.ADD  , "1",
                 })
    public int testMax1LLNoOuterAdd(int i) {
        return Math.max(Math.max(((i >> 1) + 150), 200), i >> 1);
    }

    @Test
    @IR(failOn = {IRNode.ADD})
    @IR(counts = {IRNode.MAX_I, "1"})
    public int testMax1LLNoAdd(int i) {
        return Math.max(Math.max((i >> 1), 200), i >> 1);
    }

    // Similarly, transform min(x + c0, min(y + c1, z)) to min(add(x, c2), z) if x == y, where c2 = MIN2(c0, c1).
    @Test
    @IR(counts = {IRNode.MIN_I, "1",
                  IRNode.ADD  , "1",
                 })
    public int testMin1(int i) {
        return Math.min(((i >> 1) + 100), Math.min(((i >> 1) + 150), 200));
    }

    // Transform max(x + c0, y + c1) to add(x, c2) if x == y, where c2 = MAX2(c0, c1).
    // c0,c1,c2 are constants. x and y can be any valid c2 nodes. If they are equal, this
    // transformation would take place. In this example, x and y are same RShiftI nodes.
    @Test
    @IR(failOn = {IRNode.MAX_I})
    @IR(counts = {IRNode.ADD, "1"})
    public int testMax2L(int i) {
        return Math.max((i >> 1) + 10, (i >> 1) + 11);
    }

    @Test
    @IR(failOn = {IRNode.MAX_I})
    @IR(counts = {IRNode.ADD, "1"})
    public int testMax2R(int i) {
        return Math.max((i >> 1) + 11, (i >> 1) + 10);
    }

    @Test
    @IR(failOn = {IRNode.MAX_I})
    @IR(counts = {IRNode.ADD, "1"})
    public int testMax2LNoLeftAdd(int i) {
        return Math.max(i >> 1, (i >> 1) + 11);
    }

    // Similarly, transform min(x + c0, y + c1) to add(x, c2) if x == y, where c2 = MIN2(c0, c1).
    @Test
    @IR(failOn = {IRNode.MIN_I})
    @IR(counts = {IRNode.ADD, "1"})
    public int testMin2(int i) {
        return Math.min((i >> 1) + 10, (i >> 1) + 11);
    }

    // Return the same node without generating a MaxINode/MinINode when a node is compared with itself.
    // In this test, an integer is being compared with itself but it can be any valid c2 node.
    @Test
    @IR(failOn = {IRNode.MAX_I})
    public int testMax3(int i) {
        return Math.max(i, i);
    }

    @Test
    @IR(failOn = {IRNode.MIN_I})
    public int testMin3(int i) {
        return Math.min(i, i);
    }

    @Test
    @IR(failOn = {IRNode.MAX_I})
    public int testMax4(int i) {
        return Math.max(i, 0) < 0 ? 1 : 0;
    }

    @Test
    @IR(failOn = {IRNode.MIN_I})
    public int testMin4(int i) {
        return Math.min(i, 0) > 0 ? 1 : 0;
    }

    @Run(test = {"testTwoLevelsDifferentXY",
                 "testTwoLevelsNoLeftConstant",
                 "testTwoLevelsNoRightConstant",
                 "testDifferentXY",
                 "testNoLeftConstant",
                 "testNoRightConstant"})
    public void runNegativeTests() {
        int a = RunInfo.getRandom().nextInt();
        int min = Integer.MIN_VALUE;
        int max = Integer.MAX_VALUE;

        assertNegativeResult(a);
        assertNegativeResult(0);
        assertNegativeResult(min);
        assertNegativeResult(max);

        testTwoLevelsDifferentXY(10);
        testTwoLevelsNoLeftConstant(10, 42);
        testTwoLevelsNoRightConstant(10, 42);
        testDifferentXY(10);
        testNoLeftConstant(10, 42);
        testNoRightConstant(10, 42);
    }

    @DontCompile
    public void assertNegativeResult(int a) {
        Asserts.assertEQ(Math.max(Math.max(((a >> 1) + 150), 200), ((a >> 2) + 100)), testTwoLevelsDifferentXY(a));
        Asserts.assertEQ(Math.max(Math.max(((a >> 1) + a*2), 200), ((a >> 1) + 100)),  testTwoLevelsNoLeftConstant(a, a*2));
        Asserts.assertEQ(Math.max(Math.max(((a >> 1) + 150), 200), ((a >> 1) + a*2)),  testTwoLevelsNoRightConstant(a, a*2));
        Asserts.assertEQ(Math.max((a >> 1) + 10, (a >> 2) + 11), testDifferentXY(a));
        Asserts.assertEQ(Math.max((a >> 1) + a*2, (a >> 1) + 11), testNoLeftConstant(a, a*2));
        Asserts.assertEQ(Math.max((a >> 1) + 10, (a >> 1) + a*2), testNoRightConstant(a, a*2));
    }

    @Test
    @IR(counts = {IRNode.MAX_I, "2"})
    public int testTwoLevelsDifferentXY(int i) {
        return Math.max(Math.max(((i >> 1) + 150), 200), ((i >> 2) + 100));
    }

    @Test
    @IR(counts = {IRNode.MAX_I, "2"})
    public int testTwoLevelsNoLeftConstant(int i, int c0) {
        return Math.max(Math.max(((i >> 1) + c0), 200), ((i >> 1) + 100));
    }

    @Test
    @IR(counts = {IRNode.MAX_I, "2"})
    public int testTwoLevelsNoRightConstant(int i, int c1) {
        return Math.max(Math.max(((i >> 1) + 150), 200), ((i >> 1) + c1));
    }

    @Test
    @IR(counts = {IRNode.MAX_I, "1"})
    public int testDifferentXY(int i) {
        return Math.max((i >> 1) + 10, (i >> 2) + 11);
    }

    @Test
    @IR(counts = {IRNode.MAX_I, "1"})
    public int testNoLeftConstant(int i, int c0) {
        return Math.max((i >> 1) + c0, (i >> 1) + 11);
    }

    @Test
    @IR(counts = {IRNode.MAX_I, "1"})
    public int testNoRightConstant(int i, int c1) {
        return Math.max((i >> 1) + 10, (i >> 1) + c1);
    }


}
