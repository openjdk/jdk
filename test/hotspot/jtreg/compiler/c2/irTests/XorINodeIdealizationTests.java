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
 * @bug 8281453
 * @summary Convert ~x into -1-x when ~x is used in an arithmetic expression
 * @library /test/lib /
 * @run driver compiler.c2.irTests.XorINodeIdealizationTests
 */
public class XorINodeIdealizationTests {
    public static void main(String[] args) {
        TestFramework.run();
    }

    @Run(test = {"test1", "test2", "test3",
                 "test4", "test5", "test6",
                 "test7", "test8", "test9",
                 "test10", "test11", "test12",
                 "test13", "test14", "test15",
                 "test16", "test17"})
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
        Asserts.assertEQ(b - a              , test1(a, b));
        Asserts.assertEQ(a - b              , test2(a, b));
        Asserts.assertEQ(b - a              , test3(a, b));
        Asserts.assertEQ(a - b              , test4(a, b));
        Asserts.assertEQ(b - a              , test5(a, b));
        Asserts.assertEQ(a + 1              , test6(a));
        Asserts.assertEQ(a                  , test7(a));
        Asserts.assertEQ((b + a) + 1        , test8(a, b));
        Asserts.assertEQ((-1 - a) - b       , test9(a, b));
        Asserts.assertEQ((b - a) + (-1)     , test10(a, b));
        Asserts.assertEQ((b - a) + (-1)     , test11(a, b));
        Asserts.assertEQ(~a                 , test12(a));
        Asserts.assertEQ(~a                 , test13(a));
        Asserts.assertEQ(~a                 , test14(a));
        Asserts.assertEQ(~a                 , test15(a));
        Asserts.assertEQ((~a + b) + (~a | c), test16(a, b, c));
        Asserts.assertEQ(-2023 - a          , test17(a));
    }

    @Test
    @IR(failOn = {IRNode.XOR, IRNode.ADD})
    @IR(counts = {IRNode.SUB, "1"})
    // Checks (~x + y) + 1 => y - x
    public int test1(int x, int y) {
        return (~x + y) + 1;
    }

    @Test
    @IR(failOn = {IRNode.XOR, IRNode.ADD})
    @IR(counts = {IRNode.SUB, "1"})
    // Checks (x + ~y) + 1 => x - y
    public int test2(int x, int y) {
        return (x + ~y) + 1;
    }

    @Test
    @IR(failOn = {IRNode.XOR, IRNode.ADD})
    @IR(counts = {IRNode.SUB, "1"})
    // Checks ~x + (y + 1) => y - x
    public int test3(int x, int y) {
        return ~x + (y + 1);
    }

    @Test
    @IR(failOn = {IRNode.XOR, IRNode.ADD})
    @IR(counts = {IRNode.SUB, "1"})
    // Checks (x + 1) + ~y => x - y
    public int test4(int x, int y) {
        return (x + 1) + ~y;
    }

    @Test
    @IR(failOn = {IRNode.XOR})
    @IR(counts = {IRNode.SUB, "1"})
    // Checks ~x - ~y => y - x
    public int test5(int x, int y) {
        return ~x - ~y; // transformed to y - x
    }

    @Test
    @IR(failOn = {IRNode.SUB, IRNode.XOR})
    @IR(counts = {IRNode.ADD, "1"})
    // Checks 0 - ~x => x + 1
    public int test6(int x) {
        return 0 - ~x; // transformed to x + 1
    }

    @Test
    @IR(failOn = {IRNode.SUB, IRNode.XOR, IRNode.ADD})
    // Checks -1 - ~x => x
    public int test7(int x) {
        return -1 - ~x;
    }

    @Test
    @IR(failOn = {IRNode.SUB, IRNode.XOR})
    @IR(counts = {IRNode.ADD, "2"})
    // Checks y - ~x => (y + x) + 1
    public int test8(int x, int y) {
        return y - ~x;
    }

    @Test
    @IR(failOn = {IRNode.ADD, IRNode.XOR})
    @IR(counts = {IRNode.SUB, "2"})
    // Checks ~x - y => (-1 - x) -y
    public int test9(int x, int y) {
        return ~x - y;
    }

    @Test
    @IR(failOn = {IRNode.XOR})
    @IR(counts = {IRNode.SUB, "1",
                  IRNode.ADD, "1"})
    // Checks ~x + y => (y - x) + (-1)
    public int test10(int x, int y) {
        return ~x + y;
    }

    @Test
    @IR(failOn = {IRNode.XOR})
    @IR(counts = {IRNode.SUB, "1",
                  IRNode.ADD, "1"})
    // Checks y + ~x => (y - x) + (-1)
    public int test11(int x, int y) {
        return y + ~x;
    }

    @Test
    @IR(failOn = {IRNode.SUB, IRNode.ADD})
    @IR(counts = {IRNode.XOR, "1"})
    // Checks ~(x + 0) => ~x, should not be transformed into -1-x
    public int test12(int x) {
        return ~(x + 0);
    }

    @Test
    @IR(failOn = {IRNode.SUB, IRNode.ADD})
    @IR(counts = {IRNode.XOR, "1"})
    // Checks ~(x - 0) => ~x, should not be transformed into -1-x
    public int test13(int x) {
        return ~(x - 0);
    }

    @Test
    @IR(failOn = {IRNode.SUB, IRNode.ADD})
    @IR(counts = {IRNode.XOR, "1"})
    // Checks ~x + 0 => ~x, should not be transformed into -1-x
    public int test14(int x) {
        return ~x + 0;
    }

    @Test
    @IR(failOn = {IRNode.SUB, IRNode.ADD})
    @IR(counts = {IRNode.XOR, "1"})
    // Checks ~x - 0 => ~x, should not be transformed into -1-x
    public int test15(int x) {
        return ~x - 0;
    }

    @Test
    @IR(counts = {IRNode.XOR, "1"})
    // Checks ~x + y should NOT be transformed into (y - x) + (-1)
    // because ~x has one non-arithmetic user.
    public int test16(int x, int y, int z) {
        int u = ~x + y;
        int v = ~x | z;
        return u + v;
    }

    @Test
    @IR(failOn = {IRNode.XOR, IRNode.ADD})
    @IR(counts = {IRNode.SUB, "1"})
    // Checks ~(x + c) => (-c-1) - x
    public int test17(int x) {
        return ~(x + 2022);
    }
}
