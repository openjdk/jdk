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
 * @summary Test that Ideal transformations of AddINode* are being performed as expected.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.AddINodeIdealizationTests
 */
public class AddINodeIdealizationTests {
    public static void main(String[] args) {
        TestFramework.run();
    }

    @Run(test = {"additions", "xMinusX", "test1",
                 "test2", "test3", "test4",
                 "test5", "test6", "test7",
                 "test8", "test9", "test10",
                 "test11", "test12", "test13",
                 "test14", "test15", "test16",
                 "test17", "test18", "test19",
                 "test20", "test21", "test22",
                 "test23"})
    public void runMethod() {
        int a = RunInfo.getRandom().nextInt();
        int b = RunInfo.getRandom().nextInt();
        int c = RunInfo.getRandom().nextInt();
        int d = RunInfo.getRandom().nextInt();

        int min = Integer.MIN_VALUE;
        int max = Integer.MAX_VALUE;

        assertResult(0, 0, 0, 0);
        assertResult(a, b, c, d);
        assertResult(min, min, min, min);
        assertResult(max, max, max, max);
    }

    @DontCompile
    public void assertResult(int a, int b, int c, int d) {
        Asserts.assertEQ(((a+a) + (a+a))  , additions(a));
        Asserts.assertEQ(0                , xMinusX(a));
        Asserts.assertEQ(a + 1 + 2        , test1(a));
        Asserts.assertEQ((a + 2021) + b   , test2(a, b));
        Asserts.assertEQ(a + (b + 2021)   , test3(a, b));
        Asserts.assertEQ((1 - a) + 2      , test4(a));
        Asserts.assertEQ((a - b) + (c - d), test5(a, b, c, d));
        Asserts.assertEQ((a - b) + (b + c), test6(a, b, c));
        Asserts.assertEQ((a - b) + (c + b), test7(a, b, c));
        Asserts.assertEQ((a - b) + (b - c), test8(a, b, c));
        Asserts.assertEQ((a - b) + (c - a), test9(a, b, c));
        Asserts.assertEQ(a + (0 - b)      , test10(a, b));
        Asserts.assertEQ((0 - b) + a      , test11(a, b));
        Asserts.assertEQ((a - b) + b      , test12(a, b));
        Asserts.assertEQ(b + (a - b)      , test13(a, b));
        Asserts.assertEQ(a + 0            , test14(a));
        Asserts.assertEQ(0 + a            , test15(a));
        Asserts.assertEQ(a*b + a*c        , test16(a, b, c));
        Asserts.assertEQ(a*b + b*c        , test17(a, b, c));
        Asserts.assertEQ(a*c + b*c        , test18(a, b, c));
        Asserts.assertEQ(a*b + c*a        , test19(a, b, c));
        Asserts.assertEQ((a - b) + 210    , test20(a, b));
        Asserts.assertEQ((a - b) + 190    , test21(a, b));
        Asserts.assertEQ((a - b) + 210    , test22(a, b));
        Asserts.assertEQ((a - b) + 190    , test23(a, b));
    }

    @Test
    @IR(counts = {IRNode.ADD, "2"})
    // Checks (x + x) + (x + x) => a=(x + x); r=a+a
    public int additions(int x) {
        return (x + x) + (x + x);
    }

    @Test
    @IR(failOn = {IRNode.ADD, IRNode.SUB})
    // Checks (x - x) + (x - x) => 0
    public int xMinusX(int x) {
        return (x - x) + (x - x);
    }

    @Test
    @IR(counts = {IRNode.ADD, "1"})
    // Checks (x + c1) + c2 => x + c3 where c3 = c1 + c2
    public int test1(int x) {
        return (x + 1) + 2;
    }

    @Test
    @IR(counts = {IRNode.ADD, "2"})
    // Checks (x + c1) + y => (x + y) + c1
    public int test2(int x, int y) {
        return (x + 2021) + y;
    }

    @Test
    @IR(counts = {IRNode.ADD, "2"})
    // Checks x + (y + c1) => (x + y) + c1
    public int test3(int x, int y) {
        return x + (y + 2021);
    }

    @Test
    @IR(failOn = {IRNode.ADD})
    @IR(counts = {IRNode.SUB, "1"})
    // Checks (c1 - x) + c2 => c3 - x where c3 = c1 + c2
    public int test4(int x) {
        return (1 - x) + 2;
    }

    @Test
    @IR(counts = {IRNode.SUB, "1",
                  IRNode.ADD, "2",
                 })
    // Checks (a - b) + (c - d) => (a + c) - (b + d)
    public int test5(int a, int b, int c, int d) {
        return (a - b) + (c - d);
    }

    @Test
    @IR(failOn = {IRNode.SUB})
    @IR(counts = {IRNode.ADD, "1"})
    // Checks (a - b) + (b + c) => (a + c)
    public int test6(int a, int b, int c) {
        return (a - b) + (b + c);
    }

    @Test
    @IR(failOn = {IRNode.SUB})
    @IR(counts = {IRNode.ADD, "1"})
    // Checks (a - b) + (c + b) => (a + c)
    public int test7(int a, int b, int c) {
        return (a - b) + (c + b);
    }

    @Test
    @IR(failOn = {IRNode.ADD})
    @IR(counts = {IRNode.SUB, "1"})
    // Checks (a - b) + (b - c) => (a - c)
    public int test8(int a, int b, int c) {
        return (a - b) + (b - c);
    }

    @Test
    @IR(failOn = {IRNode.ADD})
    @IR(counts = {IRNode.SUB, "1"})
    // Checks (a - b) + (c - a) => (c - b)
    public int test9(int a, int b, int c) {
        return (a - b) + (c - a);
    }

    @Test
    @IR(failOn = {IRNode.ADD})
    @IR(counts = {IRNode.SUB, "1"})
    // Checks x + (0 - y) => (x - y)
    public int test10(int x, int y) {
        return x + (0 - y);
    }

    @Test
    @IR(failOn = {IRNode.ADD})
    @IR(counts = {IRNode.SUB, "1"})
    // Checks (0 - y) + x => (x - y)
    public int test11(int x, int y) {
        return (0 - y) + x;
    }

    @Test
    @IR(failOn = {IRNode.ADD, IRNode.SUB})
    // Checks (x - y) + y => x
    public int test12(int x, int y) {
        return (x - y) + y;
    }

    @Test
    @IR(failOn = {IRNode.ADD, IRNode.SUB})
    // Checks y + (x - y) => x
    public int test13(int x, int y) {
        return y + (x - y);
    }

    @Test
    @IR(failOn = {IRNode.ADD})
    // Checks x + 0 => x
    public int test14(int x) {
        return x + 0;
    }

    @Test
    @IR(failOn = {IRNode.ADD})
    // Checks 0 + x => x
    public int test15(int x) {
        return 0 + x;
    }

    @Test
    @IR(counts = {IRNode.MUL, "1",
                  IRNode.ADD, "1"
                 })
    // Checks "a*b + a*c => a*(b+c)
    public int test16(int a, int b, int c) {
        return a*b + a*c;
    }

    @Test
    @IR(counts = {IRNode.MUL, "1",
                  IRNode.ADD, "1"
                 })
    // Checks a*b + b*c => b*(a+c)
    public int test17(int a, int b, int c) {
        return a*b + b*c;
    }

    @Test
    @IR(counts = {IRNode.MUL, "1",
                  IRNode.ADD, "1"
                 })
    // Checks a*c + b*c => (a+b)*c
    public int test18(int a, int b, int c) {
        return a*c + b*c;
    }

    @Test
    @IR(counts = {IRNode.MUL, "1",
                  IRNode.ADD, "1"
                 })
    // Checks a*b + c*a => a*(b+c)
    public int test19(int a, int b, int c) {
        return a*b + c*a;
    }

    @Test
    @IR(counts = {IRNode.SUB_I, "1",
                  IRNode.ADD_I, "1",
                  IRNode.CON_I, "1"})
    // Checks x + (con - y) => (x - y) + con
    // where con > 0
    public int test20(int x, int y) {
        return x + (10 - y) + 200; // transformed to (x - y) + 210;
    }

    @Test
    @IR(counts = {IRNode.SUB_I, "1",
                  IRNode.ADD_I, "1",
                  IRNode.CON_I, "1"})
    // Checks x + (con - y) => (x - y) + con
    // where con < 0
    public int test21(int x, int y) {
        return x + (-10 - y) + 200; // transformed to (x - y) + 190;
    }

    @Test
    @IR(counts = {IRNode.SUB_I, "1",
                  IRNode.ADD_I, "1",
                  IRNode.CON_I, "1"})
    // Checks (con - y) + x => (x - y) + con
    // where con > 0
    public int test22(int x, int y) {
        return (10 - y) + x + 200; // transformed to (x - y) + 210;
    }

    @Test
    @IR(counts = {IRNode.SUB_I, "1",
                  IRNode.ADD_I, "1",
                  IRNode.CON_I, "1"})
    // Checks (con - y) + x => (x - y) + con
    // where con < 0
    public int test23(int x, int y) {
        return x + (-10 - y) + 200; // transformed to (x - y) + 190;
    }
}
