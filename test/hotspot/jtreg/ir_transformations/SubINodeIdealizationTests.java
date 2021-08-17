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
 * @summary Test that Ideal transformations of SubINode* are being performed as expected.
 * @library /test/lib /
 * @run driver ir_transformations.SubINodeIdealizationTests
 */
public class SubINodeIdealizationTests {
    public static void main(String[] args) {
        TestFramework.run();
    }

    @Test
    @Arguments(Argument.RANDOM_EACH)
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.SUB})
    @IR(counts = {IRNode.ADD, "1"})
    // Checks (x - c0) => x + (-c0)
    public int simpleOne(int x) {
        return (x - 1);
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV})
    @IR(counts = {IRNode.ADD, "1",
                  IRNode.SUB, "1"
                 })
    // Checks (x + c0) - y => (x - y) + c0
    public int simpleTwo(int x, int y) {
        return (x + 1) - y;
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV})
    @IR(counts = {IRNode.SUB, "1",
                  IRNode.ADD, "1"
                 })
    // Checks x - (y + c0) => (x - y) + (-c0)
    public int simpleThree(int x, int y) {
        return x - (y + 2021);
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.ADD})
    @IR(counts = {IRNode.SUB, "1"})
    // Checks x - (x + y) => -y
    public int simpleFour(int x, int y) {
        return x - (x + y);
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.ADD})
    @IR(counts = {IRNode.SUB, "1"})
    // Checks (x - y) - x => -y
    public int simpleFive(int x, int y) {
        return (x - y) - x;
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.ADD})
    @IR(counts = {IRNode.SUB, "1"})
    // Checks x - (y + x) => -y
    public int simpleSix(int x, int y) {
        return x - (y + x);
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.ADD})
    @IR(counts = {IRNode.SUB, "1"})
    // Checks 0 - (x - y) => y - x
    public int simpleSeven(int x, int y) {
        return 0 - (x - y);
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.ADD})
    @IR(counts = {IRNode.SUB, "1"})
    // Checks 0 - (x + 2021) => -2021 - x
    public int simpleEight(int x, int y) {
        return 0 - (x + 2021);
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.ADD})
    @IR(counts = {IRNode.SUB, "1"})
    // Checks (x + a) - (x + b) => a - b;
    public int simpleNine(int x, int a, int b) {
        return (x + a) - (x + b);
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.ADD})
    @IR(counts = {IRNode.SUB, "1"})
    // Checks (a + x) - (b + x) => a - b
    public int simpleTen(int x, int a, int b) {
        return (a + x) - (b + x);
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.ADD})
    @IR(counts = {IRNode.SUB, "1"})
    // Checks (a + x) - (x + b) => a - b
    public int simpleEleven(int x, int a, int b) {
        return (a + x) - (x + b);
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.ADD})
    @IR(counts = {IRNode.SUB, "1"})
    // Checks (x + a) - (b + x) => a - b
    public int simpleTwelve(int x, int a, int b) {
        return (x + a) - (b + x);
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV})
    @IR(counts = {IRNode.SUB, "1",
                  IRNode.ADD, "1"
                 })
    // Checks a - (b - c) => (a + c) - b
    public int simpleThirteen(int a, int b, int c) {
        return a - (b - c);
    }

    @Test
    @Arguments(Argument.RANDOM_EACH)
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.SUB, IRNode.ADD})
    // Checks 0 - (a >> 31) => a >> 31
    public int simpleFourteen(int a) {
        return 0 - (a >> 31);
    }

    @Test
    @Arguments(Argument.RANDOM_EACH)
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.SUB, IRNode.ADD})
    // Checks 0 - (0 - x) => x
    public int simpleFifteen(int x) {
        return 0 - (0 - x);
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.SUB, IRNode.ADD})
    // Checks (x + y) - y => y
    public int simpleSixteen(int x, int y) {
        return (x + y) - y;
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.SUB, IRNode.ADD})
    // Checks (x + y) - x => y
    public int simpleSeventeen(int x, int y) {
        return (x + y) - x;
    }
}