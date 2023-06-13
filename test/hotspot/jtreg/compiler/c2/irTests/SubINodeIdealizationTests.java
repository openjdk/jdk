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
 * @bug 8267265
 * @summary Test that Ideal transformations of SubINode* are being performed as expected.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.SubINodeIdealizationTests
 */
public class SubINodeIdealizationTests {
    public static void main(String[] args) {
        TestFramework.run();
    }

    @Run(test = {"test1", "test2", "test3",
                 "test4", "test5", "test6",
                 "test7", "test8", "test9",
                 "test10", "test11", "test12",
                 "test13", "test14", "test15",
                 "test16", "test17", "test18",
                 "test19", "test20", "test21"})
    public void runMethod() {
        int a = RunInfo.getRandom().nextInt();
        int b = RunInfo.getRandom().nextInt();
        int c = RunInfo.getRandom().nextInt();

        int min = Integer.MIN_VALUE;
        int max = Integer.MAX_VALUE;

        assertResult(0, 0, 0);
        assertResult(a, b, c);
        assertResult(min, min, min);
        assertResult(max, max, max);
    }

    @DontCompile
    public void assertResult(int a, int b, int c) {
        Asserts.assertEQ(a - 1            , test1(a));
        Asserts.assertEQ((a + 1) - b      , test2(a, b));
        Asserts.assertEQ(a - (b + 2021)   , test3(a, b));
        Asserts.assertEQ(a - (a + b)      , test4(a, b));
        Asserts.assertEQ((a - b) - a      , test5(a, b));
        Asserts.assertEQ(a - (b + a)      , test6(a, b));
        Asserts.assertEQ(0 - (a - b)      , test7(a, b));
        Asserts.assertEQ(0 - (a + 2021)   , test8(a));
        Asserts.assertEQ((a + b) - (a + c), test9(a, b, c));
        Asserts.assertEQ((b + a) - (c + a), test10(a, b, c));
        Asserts.assertEQ((b + a) - (a + c), test11(a, b, c));
        Asserts.assertEQ((a + b) - (c + a), test12(a, b, c));
        Asserts.assertEQ(a - (b - c)      , test13(a, b, c));
        Asserts.assertEQ(0 - (a >> 31)    , test14(a));
        Asserts.assertEQ(0 - (0 - a)      , test15(a));
        Asserts.assertEQ((a + b) - b      , test16(a, b));
        Asserts.assertEQ((a + b) - a      , test17(a, b));
        Asserts.assertEQ(a*b - a*c        , test18(a, b, c));
        Asserts.assertEQ(a*b - b*c        , test19(a, b, c));
        Asserts.assertEQ(a*c - b*c        , test20(a, b, c));
        Asserts.assertEQ(a*b - c*a        , test21(a, b, c));
    }

    @Test
    @IR(failOn = {IRNode.SUB})
    @IR(counts = {IRNode.ADD, "1"})
    // Checks (x - c0) => x + (-c0)
    public int test1(int x) {
        return (x - 1);
    }

    @Test
    @IR(counts = {IRNode.ADD, "1",
                  IRNode.SUB, "1"
                 })
    // Checks (x + c0) - y => (x - y) + c0
    public int test2(int x, int y) {
        return (x + 1) - y;
    }

    @Test
    @IR(counts = {IRNode.SUB, "1",
                  IRNode.ADD, "1"
                 })
    // Checks x - (y + c0) => (x - y) + (-c0)
    public int test3(int x, int y) {
        return x - (y + 2021);
    }

    @Test
    @IR(failOn = {IRNode.ADD})
    @IR(counts = {IRNode.SUB, "1"})
    // Checks x - (x + y) => 0 - y
    public int test4(int x, int y) {
        return x - (x + y);
    }

    @Test
    @IR(counts = {IRNode.SUB, "1"})
    // Checks (x - y) - x => 0 - y
    public int test5(int x, int y) {
        return (x - y) - x;
    }

    @Test
    @IR(failOn = {IRNode.ADD})
    @IR(counts = {IRNode.SUB, "1"})
    // Checks x - (y + x) => 0 - y
    public int test6(int x, int y) {
        return x - (y + x);
    }

    @Test
    @IR(counts = {IRNode.SUB, "1"})
    // Checks 0 - (x - y) => y - x
    public int test7(int x, int y) {
        return 0 - (x - y);
    }

    @Test
    @IR(failOn = {IRNode.ADD})
    @IR(counts = {IRNode.SUB, "1"})
    // Checks 0 - (x + 2021) => -2021 - x
    public int test8(int x) {
        return 0 - (x + 2021);
    }

    @Test
    @IR(failOn = {IRNode.ADD})
    @IR(counts = {IRNode.SUB, "1"})
    // Checks (x + a) - (x + b) => a - b;
    public int test9(int x, int a, int b) {
        return (x + a) - (x + b);
    }

    @Test
    @IR(failOn = {IRNode.ADD})
    @IR(counts = {IRNode.SUB, "1"})
    // Checks (a + x) - (b + x) => a - b
    public int test10(int x, int a, int b) {
        return (a + x) - (b + x);
    }

    @Test
    @IR(failOn = {IRNode.ADD})
    @IR(counts = {IRNode.SUB, "1"})
    // Checks (a + x) - (x + b) => a - b
    public int test11(int x, int a, int b) {
        return (a + x) - (x + b);
    }

    @Test
    @IR(failOn = {IRNode.ADD})
    @IR(counts = {IRNode.SUB, "1"})
    // Checks (x + a) - (b + x) => a - b
    public int test12(int x, int a, int b) {
        return (x + a) - (b + x);
    }

    @Test
    @IR(counts = {IRNode.SUB, "1",
                  IRNode.ADD, "1"
                 })
    // Checks a - (b - c) => (a + c) - b
    public int test13(int a, int b, int c) {
        return a - (b - c);
    }

    @Test
    @IR(failOn = {IRNode.SUB, IRNode.RSHIFT_I})
    @IR(counts = {IRNode.URSHIFT_I, "1"})
    // Checks 0 - (a >> 31) => a >>> 31
    //        signed ^^          ^^^ unsigned
    public int test14(int a) {
        return 0 - (a >> 31);
    }

    @Test
    @IR(failOn = {IRNode.SUB})
    // Checks 0 - (0 - x) => x
    public int test15(int x) {
        return 0 - (0 - x);
    }

    @Test
    @IR(failOn = {IRNode.SUB, IRNode.ADD})
    // Checks (x + y) - y => x
    public int test16(int x, int y) {
        return (x + y) - y;
    }

    @Test
    @IR(failOn = {IRNode.SUB, IRNode.ADD})
    // Checks (x + y) - x => y
    public int test17(int x, int y) {
        return (x + y) - x;
    }

    @Test
    @IR(counts = {IRNode.MUL, "1",
                  IRNode.SUB, "1"})
    // Checks "a*b-a*c => a*(b-c)
    public int test18(int a, int b, int c) {
        return a*b - a*c;
    }

    @Test
    @IR(counts = {IRNode.MUL, "1",
                  IRNode.SUB, "1"})
    // Checks a*b-b*c => b*(a-c)
    public int test19(int a, int b, int c) {
        return a*b - b*c;
    }

    @Test
    @IR(counts = {IRNode.MUL, "1",
                  IRNode.SUB, "1"})
    // Checks a*c-b*c => (a-b)*c
    public int test20(int a, int b, int c) {
        return a*c - b*c;
    }

    @Test
    @IR(counts = {IRNode.MUL, "1",
                  IRNode.SUB, "1"})
    // Checks a*b-c*a => a*(b-c)
    public int test21(int a, int b, int c) {
        return a*b - c*a;
    }
}
