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
 * @summary Test that Ideal transformations of MulLNode* are being performed as expected.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.MulLNodeIdealizationTests
 */
public class MulLNodeIdealizationTests {
    public static void main(String[] args) {
        TestFramework.run();
    }

    @Run(test = {"combineConstants", "moveConstants", "moveConstantsAgain",
                 "multiplyZero", "multiplyZeroAgain", "distribute",
                 "identity",  "identityAgain", "powerTwo",
                 "powerTwoAgain", "powerTwoPlusOne", "powerTwoMinusOne"})
    public void runMethod() {
        long a = RunInfo.getRandom().nextLong();
        long b = RunInfo.getRandom().nextLong();

        long min = Long.MIN_VALUE;
        long max = Long.MAX_VALUE;

        assertResult(0, 0);
        assertResult(a, b);
        assertResult(min, min);
        assertResult(max, max);
    }

    @DontCompile
    public void assertResult(long a, long b) {
        Asserts.assertEQ((a * 13) * 14 * 15, combineConstants(a));
        Asserts.assertEQ((a * 13) * b      , moveConstants(a, b));
        Asserts.assertEQ(a * (b * 13)      , moveConstantsAgain(a, b));
        Asserts.assertEQ(0 * a             , multiplyZero(a));
        Asserts.assertEQ(a * 0             , multiplyZeroAgain(a));
        Asserts.assertEQ((13 + a) * 14     , distribute(a));
        Asserts.assertEQ(1 * a             , identity(a));
        Asserts.assertEQ(a * 1             , identityAgain(a));
        Asserts.assertEQ(a * 64            , powerTwo(a));
        Asserts.assertEQ(a * (1025 - 1)    , powerTwoAgain(a));
        Asserts.assertEQ(a * (64 + 1)      , powerTwoPlusOne(a));
        Asserts.assertEQ(a * (64 - 1)      , powerTwoMinusOne(a));
    }

    @Test
    @IR(counts = {IRNode.MUL, "1"})
    //Checks (x * c1) * c2 => x * c3 where c3 = c1 * c2
    public long combineConstants(long x){
        return (x * 13) * 14 * 15;
    }

    @Test
    @IR(counts = {IRNode.MUL, "2"})
    // Checks (x * c1) * y => (x * y) * c1
    public long moveConstants(long x, long y) {
        return (x * 13) * y;
    }

    @Test
    @IR(counts = {IRNode.MUL, "2"})
    // Checks x * (y * c1) => (x * y) * c1
    public long moveConstantsAgain(long x, long y) {
        return x * (y * 13);
    }

    @Test
    @IR(failOn = {IRNode.MUL})
    // Checks 0 * x => 0
    public long multiplyZero(long x) {
        return 0 * x;
    }

    @Test
    @IR(failOn = {IRNode.MUL})
    // Checks x * 0 => 0
    public long multiplyZeroAgain(long x) {
        return x * 0;
    }

    @Test
    @IR(counts = {IRNode.MUL, "1",
                  IRNode.ADD, "1",
                 })
    // Checks (c1 + x) * c2 => x * c2 + c3 where c3 = c1 * c2
    public long distribute(long x) {
        return (13 + x) * 14;
    }

    @Test
    @IR(failOn = {IRNode.MUL})
    // Checks 1 * x => x
    public long identity(long x) {
        return 1 * x;
    }

    @Test
    @IR(failOn = {IRNode.MUL})
    // Checks x * 1 => x
    public long identityAgain(long x) {
        return x * 1;
    }

    @Test
    @IR(failOn = {IRNode.MUL})
    @IR(counts = {IRNode.LSHIFT, "1"})
    // Checks x * 2^n => x << n
    public long powerTwo(long x) {
        return x * 64;
    }

    @Test
    @IR(failOn = {IRNode.SUB, IRNode.MUL})
    @IR(counts = {IRNode.LSHIFT, "1"})
    // Checks x * 2^n => x << n
    public long powerTwoAgain(long x) {
        return x * (1025 - 1);
    }

    @Test
    @IR(failOn = {IRNode.MUL})
    @IR(counts = {IRNode.LSHIFT, "1",
                  IRNode.ADD, "1",
                 })
    // Checks x * (2^n + 1) => (x << n) + x
    public long powerTwoPlusOne(long x) {
        return x * (64 + 1);
    }

    @Test
    @IR(failOn = {IRNode.MUL})
    @IR(counts = {IRNode.LSHIFT, "1",
                  IRNode.SUB, "1",
                 })
    // Checks x * (2^n - 1) => (x << n) - x
    public long powerTwoMinusOne(long x) {
        return x * (64 - 1);
    }
}
