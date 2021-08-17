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
 * @summary Test that Ideal transformations of MulLNode* are being performed as expected.
 * @library /test/lib /
 * @run driver ir_transformations.MulLNodeIdealizationTests
 */
public class MulLNodeIdealizationTests {
    public static void main(String[] args) {
        TestFramework.run();
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.DIV, IRNode.CALL})
    @IR(counts = {IRNode.MUL, "1"})
    //Checks Max(a,b) * min(a,b) => a*b
    public long excludeMaxMin(long x, long y){
        return Math.max(x, y) * Math.min(x, y);
    }

    @Test
    @Arguments(Argument.RANDOM_EACH)
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.DIV})
    @IR(counts = {IRNode.MUL, "1"})
    //Checks (x * c1) * c2 => x * c3 where c3 = c1 * c2
    public long combineConstants(long x){
        return (x * 13) * 14 * 15;
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.ADD, IRNode.DIV, IRNode.SUB})
    @IR(counts = {IRNode.MUL, "2"})
    // Checks (x * c1) * y => (x * y) * c1
    public long moveConstants(long x, long y) {
        return (x * 13) * y;
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.ADD, IRNode.DIV, IRNode.SUB})
    @IR(counts = {IRNode.MUL, "2"})
    // Checks x * (y * c1) => (x * y) * c1
    public long moveConstants1(long x, long y) {
        return x * (y * 13);
    }

    @Test
    @Arguments(Argument.RANDOM_EACH)
    @IR(failOn = {IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.ADD, IRNode.SUB})
    // Checks 0 * x => 0
    public long multiplyZero(long x) {
        return 0 * x;
    }

    @Test
    @Arguments(Argument.RANDOM_EACH)
    @IR(failOn = {IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.ADD, IRNode.SUB})
    // Checks x * 0 => 0
    public long multiplyZero1(long x) {
        return x * 0;
    }

    @Test
    @Arguments(Argument.RANDOM_EACH)
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.SUB, IRNode.DIV})
    @IR(counts = {IRNode.MUL, "1",
                  IRNode.ADD, "1",
                 })
    // Checks (c1 + x) * c2 => x * c2 + c3 where c3 = c1 * c2
    public long distribute(long x) {
        return (13 + x) * 14;
    }

    @Test
    @Arguments(Argument.RANDOM_EACH)
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.ADD, IRNode.SUB})
    // Checks 1 * x => x
    public long identity(long x) {
        return 1 * x;
    }

    @Test
    @Arguments(Argument.RANDOM_EACH)
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.ADD, IRNode.SUB})
    // Checks x * 1 => x
    public long identity1(long x) {
        return x * 1;
    }

    @Test
    @Arguments(Argument.RANDOM_EACH)
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.SUB, IRNode.DIV, IRNode.MUL, IRNode.ADD})
    @IR(counts = {IRNode.LSHIFT, "1"})
    // Checks x * 2^n => x << n
    public long powerTwo(long x) {
        return x * 64;
    }

    @Test
    @Arguments(Argument.RANDOM_EACH)
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.SUB, IRNode.DIV, IRNode.MUL, IRNode.ADD})
    @IR(counts = {IRNode.LSHIFT, "1"})
    // Checks x * 2^n => x << n
    public long powerTwo1(long x) {
        return x * (1025 - 1);
    }

    @Test
    @Arguments(Argument.RANDOM_EACH)
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.SUB, IRNode.DIV, IRNode.MUL})
    @IR(counts = {IRNode.LSHIFT, "1",
                  IRNode.ADD, "1",
                 })
    // Checks x * (2^n + 1) => (x << n) + x
    public long powerTwoPlusOne(long x) {
        return x * (64 + 1);
    }

    @Test
    @Arguments(Argument.RANDOM_EACH)
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.ADD, IRNode.DIV, IRNode.MUL})
    @IR(counts = {IRNode.LSHIFT, "1",
                  IRNode.SUB, "1",
                 })
    // Checks x * (2^n - 1) => (x << n) - x
    public long powerTwoMinusOne(long x) {
        return x * (64 - 1);
    }
}
