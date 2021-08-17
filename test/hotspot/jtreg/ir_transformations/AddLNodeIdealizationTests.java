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
package ir_transformations;

import compiler.lib.ir_framework.*;

/*
 * @test
 * @summary Test that Ideal transformations of AddLNode* are being performed as expected.
 * @library /test/lib /
 * @run driver ir_transformations.AddLNodeIdealizationTests
 */
public class AddLNodeIdealizationTests {
    public static void main(String[] args) {
        TestFramework.run();
    }

    @Test
    @Arguments(Argument.RANDOM_EACH)
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.SUB})
    @IR(counts = {IRNode.ADD, "2"})
    // Checks
    public long simpleZero(long x) {
        return (x + x) + (x + x);
    }

    @Test
    @Arguments(Argument.RANDOM_EACH)
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.SUB, IRNode.SUB})
    // Checks (x - x) => 0 and 0 - 0 => 0
    public long simpleZeroSub(long x) {
        return (x - x) + (x - x);
    }

    @Test
    @Arguments(Argument.RANDOM_EACH)
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.SUB})
    @IR(counts = {IRNode.ADD, "1"})
    // Checks (x + c1) + c2 => x + c3 where c3 = c1 + c2
    public long simpleOne(long x) {
        return (x + 1) + 2;
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.SUB})
    @IR(counts = {IRNode.ADD, "2"})
    // Checks (x + c1) + y => (x + y) + c1
    public long simpleTwo(long x, long y) {
        return (x + 2021) + y;
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.SUB})
    @IR(counts = {IRNode.ADD, "2"})
    // Checks x + (y + c1) => (x + y) + c1
    public long simpleThree(long x, long y) {
        return x + (y + 2021);
    }

    @Test
    @Arguments(Argument.RANDOM_EACH)
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.ADD})
    @IR(counts = {IRNode.SUB, "1"})
    // Checks (c1 - x) + c2 => c3 - x where c3 = c1 + c2
    public long simpleFour(long x) {
        return (1 - x) + 2;
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV})
    @IR(counts = {IRNode.SUB, "1",
                  IRNode.ADD, "2",
                 })
    // Checks (a - b) + (c - d) => (a + c) - (b + d)
    public long simpleFive(long a, long b, long c, long d) {
        return (a - b) + (c - d);
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.SUB})
    @IR(counts = {IRNode.ADD, "1"})
    // Checks (a - b) + (b + c) => (a + c)
    public long simpleSix(long a, long b, long c) {
        return (a - b) + (b + c);
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.SUB})
    @IR(counts = {IRNode.ADD, "1"})
    // Checks (a - b) + (c + b) => (a + c)
    public long simpleSeven(long a, long b, long c) {
        return (a - b) + (c + b);
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.ADD})
    @IR(counts = {IRNode.SUB, "1"})
    // Checks (a - b) + (b - c) => (a - c)
    public long simpleEight(long a, long b, long c) {
        return (a - b) + (b - c);
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.ADD})
    @IR(counts = {IRNode.SUB, "1"})
    // Checks (a - b) + (c - a) => (c - b)
    public long simpleNine(long a, long b, long c) {
        return (a - b) + (c - a);
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.ADD})
    @IR(counts = {IRNode.SUB, "1"})
    // Checks x + (0 - y) => (x - y)
    public long simpleTen(long x, long y) {
        return x + (0 - y);
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.ADD})
    @IR(counts = {IRNode.SUB, "1"})
    // Checks (0 - y) + x => (x - y)
    public long simpleEleven(long x, long y) {
        return (0 - y) + x;
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.ADD, IRNode.SUB})
    // Checks (x - y) + y => x
    public long simpleTwelve(long x, long y) {
        return (x - y) + y;
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.ADD, IRNode.SUB})
    // Checks y + (x - y) => x
    public long simpleThirteen(long x, long y) {
        return y + (x - y);
    }

    @Test
    @Arguments(Argument.RANDOM_EACH)
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.ADD, IRNode.SUB})
    // Checks x + 0 => x
    public long simpleFourteen(long x) {
        return x + 0;
    }

    @Test
    @Arguments(Argument.RANDOM_EACH)
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.ADD, IRNode.SUB})
    // Checks 0 + x => x
    public long simpleFifteen(long x) {
        return 0 + x;
    }
}