/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test that Ideal transformations of AddINode* are being performed as expected.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.AddINodeIdealizationTests
 */
public class AddINodeIdealizationTests {
    public static void main(String[] args) {
        TestFramework.run();
    }

    @Test
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.SUB})
    @IR(counts = {IRNode.ADD, "2"})
    // Checks (x + x) + (x + x) => a=(x + x); r=a+a
    public int additions(int x) {
        return (x + x) + (x + x);
    }

    @Run(test = "additions")
    public void runAdditions() {
        int x = RunInfo.getRandom().nextInt();
        Asserts.assertEQ(((x+x) + (x+x)), additions(x));

        x = Integer.MIN_VALUE;
        Asserts.assertEQ(((x+x) + (x+x)), additions(x));

        x = Integer.MAX_VALUE;
        Asserts.assertEQ(((x+x) + (x+x)), additions(x));
    }

    @Test
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.SUB})
    // Checks (x - x) + (x - x) => 0
    public int xMinusX(int x) {
        return (x - x) + (x - x);
    }

    @Run(test = "xMinusX")
    public void runXMinusX() {
        int x = RunInfo.getRandom().nextInt();
        Asserts.assertEQ(0, xMinusX(x));

        x = Integer.MIN_VALUE;
        Asserts.assertEQ(0, xMinusX(x));

        x = Integer.MAX_VALUE;
        Asserts.assertEQ(0, xMinusX(x));
    }

    @Test
    @Arguments(Argument.RANDOM_EACH)
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.SUB})
    @IR(counts = {IRNode.ADD, "1"})
    // Checks (x + c1) + c2 => x + c3 where c3 = c1 + c2
    public int test1(int x) {
        return (x + 1) + 2;
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.SUB})
    @IR(counts = {IRNode.ADD, "2"})
    // Checks (x + c1) + y => (x + y) + c1
    public int test2(int x, int y) {
        return (x + 2021) + y;
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.SUB})
    @IR(counts = {IRNode.ADD, "2"})
    // Checks x + (y + c1) => (x + y) + c1
    public int test3(int x, int y) {
        return x + (y + 2021);
    }

    @Test
    @Arguments(Argument.RANDOM_EACH)
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.ADD})
    @IR(counts = {IRNode.SUB, "1"})
    // Checks (c1 - x) + c2 => c3 - x where c3 = c1 + c2
    public int test4(int x) {
        return (1 - x) + 2;
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV})
    @IR(counts = {IRNode.SUB, "1",
                  IRNode.ADD, "2",
                 })
    // Checks (a - b) + (c - d) => (a + c) - (b + d)
    public int test5(int a, int b, int c, int d) {
        return (a - b) + (c - d);
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.SUB})
    @IR(counts = {IRNode.ADD, "1"})
    // Checks (a - b) + (b + c) => (a + c)
    public int test6(int a, int b, int c) {
        return (a - b) + (b + c);
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.SUB})
    @IR(counts = {IRNode.ADD, "1"})
    // Checks (a - b) + (c + b) => (a + c)
    public int test7(int a, int b, int c) {
        return (a - b) + (c + b);
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.ADD})
    @IR(counts = {IRNode.SUB, "1"})
    // Checks (a - b) + (b - c) => (a - c)
    public int test8(int a, int b, int c) {
        return (a - b) + (b - c);
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.ADD})
    @IR(counts = {IRNode.SUB, "1"})
    // Checks (a - b) + (c - a) => (c - b)
    public int test9(int a, int b, int c) {
        return (a - b) + (c - a);
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.ADD})
    @IR(counts = {IRNode.SUB, "1"})
    // Checks x + (0 - y) => (x - y)
    public int test10(int x, int y) {
        return x + (0 - y);
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.ADD})
    @IR(counts = {IRNode.SUB, "1"})
    // Checks (0 - y) + x => (x - y)
    public int test11(int x, int y) {
        return (0 - y) + x;
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.ADD, IRNode.SUB})
    // Checks (x - y) + y => x
    public int test12(int x, int y) {
        return (x - y) + y;
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.ADD, IRNode.SUB})
    // Checks y + (x - y) => x
    public int test13(int x, int y) {
        return y + (x - y);
    }

    @Test
    @Arguments(Argument.RANDOM_EACH)
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.ADD, IRNode.SUB})
    // Checks x + 0 => x
    public int test14(int x) {
        return x + 0;
    }

    @Test
    @Arguments(Argument.RANDOM_EACH)
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.ADD, IRNode.SUB})
    // Checks 0 + x => x
    public int test15(int x) {
        return 0 + x;
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.DIV, IRNode.SUB})
    @IR(counts = {IRNode.MUL, "1", IRNode.ADD, "1"})
    // Checks "a*b + a*c => a*(b+c)
    public int test16(int a, int b, int c) {
        return a*b + a*c;
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.DIV, IRNode.SUB})
    @IR(counts = {IRNode.MUL, "1", IRNode.ADD, "1"})
    // Checks a*b + b*c => b*(a+c)
    public int test17(int a, int b, int c) {
        return a*b + b*c;
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.DIV, IRNode.SUB})
    @IR(counts = {IRNode.MUL, "1", IRNode.ADD, "1"})
    // Checks a*c + b*c => (a+b)*c
    public int test18(int a, int b, int c) {
        return a*c + b*c;
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.DIV, IRNode.SUB})
    @IR(counts = {IRNode.MUL, "1", IRNode.ADD, "1"})
    // Checks a*b + c*a => a*(b+c)
    public int test19(int a, int b, int c) {
        return a*b + c*a;
    }
}
