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
 * @summary Test that Ideal transformations of AddLNode* are being performed as expected.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.AddLNodeIdealizationTests
 */
public class AddLNodeIdealizationTests {
    public static void main(String[] args) {
        TestFramework.run();
    }

    @Test
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.SUB})
    @IR(counts = {IRNode.ADD, "2"})
    // Checks (x + x) + (x + x) => a=(x + x); r=a+a
    public long additions(long x) {
        return (x + x) + (x + x);
    }

    @Run(test = "additions")
    public void runAdditions() {
        long x = RunInfo.getRandom().nextLong();
        Asserts.assertEQ(((x+x) + (x+x)), additions(x));

        x = Long.MIN_VALUE;
        Asserts.assertEQ(((x+x) + (x+x)), additions(x));

        x = Long.MAX_VALUE;
        Asserts.assertEQ(((x+x) + (x+x)), additions(x));
    }

    @Test
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.SUB, IRNode.SUB})
    // Checks (x - x) => 0 and 0 - 0 => 0
    public long xMinusX(long x) {
        return (x - x) + (x - x);
    }

    @Run(test = "xMinusX")
    public void runXMinusX() {
        long x = RunInfo.getRandom().nextLong();
        Asserts.assertEQ(0L, xMinusX(x));

        x = Long.MIN_VALUE;
        Asserts.assertEQ(0L, xMinusX(x));

        x = Long.MAX_VALUE;
        Asserts.assertEQ(0L, xMinusX(x));
    }

    @Test
    @Arguments(Argument.RANDOM_EACH)
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.SUB})
    @IR(counts = {IRNode.ADD, "1"})
    // Checks (x + c1) + c2 => x + c3 where c3 = c1 + c2
    public long test1(long x) {
        return (x + 1) + 2;
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.SUB})
    @IR(counts = {IRNode.ADD, "2"})
    // Checks (x + c1) + y => (x + y) + c1
    public long test2(long x, long y) {
        return (x + 2021) + y;
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.SUB})
    @IR(counts = {IRNode.ADD, "2"})
    // Checks x + (y + c1) => (x + y) + c1
    public long test3(long x, long y) {
        return x + (y + 2021);
    }

    @Test
    @Arguments(Argument.RANDOM_EACH)
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.ADD})
    @IR(counts = {IRNode.SUB, "1"})
    // Checks (c1 - x) + c2 => c3 - x where c3 = c1 + c2
    public long test4(long x) {
        return (1 - x) + 2;
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV})
    @IR(counts = {IRNode.SUB, "1",
                  IRNode.ADD, "2",
                 })
    // Checks (a - b) + (c - d) => (a + c) - (b + d)
    public long test5(long a, long b, long c, long d) {
        return (a - b) + (c - d);
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.SUB})
    @IR(counts = {IRNode.ADD, "1"})
    // Checks (a - b) + (b + c) => (a + c)
    public long test6(long a, long b, long c) {
        return (a - b) + (b + c);
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.SUB})
    @IR(counts = {IRNode.ADD, "1"})
    // Checks (a - b) + (c + b) => (a + c)
    public long test7(long a, long b, long c) {
        return (a - b) + (c + b);
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.ADD})
    @IR(counts = {IRNode.SUB, "1"})
    // Checks (a - b) + (c - a) => (c - b)
    public long test8(long a, long b, long c) {
        return (a - b) + (c - a);
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.ADD})
    @IR(counts = {IRNode.SUB, "1"})
    // Checks x + (0 - y) => (x - y)
    public long test9(long x, long y) {
        return x + (0 - y);
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.ADD})
    @IR(counts = {IRNode.SUB, "1"})
    // Checks (0 - y) + x => (x - y)
    public long test10(long x, long y) {
        return (0 - y) + x;
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.ADD, IRNode.SUB})
    // Checks (x - y) + y => x
    public long test11(long x, long y) {
        return (x - y) + y;
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.ADD, IRNode.SUB})
    // Checks y + (x - y) => x
    public long test12(long x, long y) {
        return y + (x - y);
    }

    @Test
    @Arguments(Argument.RANDOM_EACH)
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.ADD, IRNode.SUB})
    // Checks x + 0 => x
    public long test13(long x) {
        return x + 0;
    }

    @Test
    @Arguments(Argument.RANDOM_EACH)
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.ADD, IRNode.SUB})
    // Checks 0 + x => x
    public long test14(long x) {
        return 0 + x;
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.DIV, IRNode.SUB})
    @IR(counts = {IRNode.MUL, "1", IRNode.ADD, "1"})
    // Checks "a*b + a*c => a*(b+c)
    public long test15(long a, long b, long c) {
        return a*b + a*c;
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.DIV, IRNode.SUB})
    @IR(counts = {IRNode.MUL, "1", IRNode.ADD, "1"})
    // Checks a*b + b*c => b*(a+c)
    public long test16(long a, long b, long c) {
        return a*b + b*c;
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.DIV, IRNode.SUB})
    @IR(counts = {IRNode.MUL, "1", IRNode.ADD, "1"})
    // Checks a*c + b*c => (a+b)*c
    public long test17(long a, long b, long c) {
        return a*c + b*c;
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.DIV, IRNode.SUB})
    @IR(counts = {IRNode.MUL, "1", IRNode.ADD, "1"})
    // Checks a*b + c*a => a*(b+c)
    public long test18(long a, long b, long c) {
        return a*b + c*a;
    }
}
