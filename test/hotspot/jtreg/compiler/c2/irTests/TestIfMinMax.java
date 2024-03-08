/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Random;
import jdk.test.lib.Utils;

/*
 * @test
 * @bug 8324655
 * @summary Test that if expressions are properly folded into min/max nodes
 * @library /test/lib /
 * @run driver compiler.c2.irTests.TestIfMinMax
 */
public class TestIfMinMax {
    private static final Random RANDOM = Utils.getRandomInstance();

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Test
    @IR(failOn = { IRNode.IF }, counts = { IRNode.MIN_I, "1" })
    public int testMinI1(int a, int b) {
        return a < b ? a : b;
    }

    @Test
    @IR(failOn = { IRNode.IF }, counts = { IRNode.MIN_I, "1" })
    public int testMinI2(int a, int b) {
        return a > b ? b : a;
    }

    @Test
    @IR(failOn = { IRNode.IF }, counts = { IRNode.MAX_I, "1" })
    public int testMaxI1(int a, int b) {
        return a > b ? a : b;
    }

    @Test
    @IR(failOn = { IRNode.IF }, counts = { IRNode.MAX_I, "1" })
    public int testMaxI2(int a, int b) {
        return a < b ? b : a;
    }

    @Test
    @IR(failOn = { IRNode.IF }, counts = { IRNode.MIN_I, "1" })
    public int testMinI1E(int a, int b) {
        return a <= b ? a : b;
    }

    @Test
    @IR(failOn = { IRNode.IF }, counts = { IRNode.MIN_I, "1" })
    public int testMinI2E(int a, int b) {
        return a >= b ? b : a;
    }

    @Test
    @IR(failOn = { IRNode.IF }, counts = { IRNode.MAX_I, "1" })
    public int testMaxI1E(int a, int b) {
        return a >= b ? a : b;
    }

    @Test
    @IR(failOn = { IRNode.IF }, counts = { IRNode.MAX_I, "1" })
    public int testMaxI2E(int a, int b) {
        return a <= b ? b : a;
    }

    @Test
    @IR(phase = { CompilePhase.BEFORE_MACRO_EXPANSION }, failOn = { IRNode.IF }, counts = { IRNode.MIN_L, "1" })
    public long testMinL1(long a, long b) {
        return a < b ? a : b;
    }

    @Test
    @IR(phase = { CompilePhase.BEFORE_MACRO_EXPANSION }, failOn = { IRNode.IF }, counts = { IRNode.MIN_L, "1" })
    public long testMinL2(long a, long b) {
        return a > b ? b : a;
    }

    @Test
    @IR(phase = { CompilePhase.BEFORE_MACRO_EXPANSION }, failOn = { IRNode.IF }, counts = { IRNode.MAX_L, "1" })
    public long testMaxL1(long a, long b) {
        return a > b ? a : b;
    }

    @Test
    @IR(phase = { CompilePhase.BEFORE_MACRO_EXPANSION }, failOn = { IRNode.IF }, counts = { IRNode.MAX_L, "1" })
    public long testMaxL2(long a, long b) {
        return a < b ? b : a;
    }

    @Test
    @IR(phase = { CompilePhase.BEFORE_MACRO_EXPANSION }, failOn = { IRNode.IF }, counts = { IRNode.MIN_L, "1" })
    public long testMinL1E(long a, long b) {
        return a <= b ? a : b;
    }

    @Test
    @IR(phase = { CompilePhase.BEFORE_MACRO_EXPANSION }, failOn = { IRNode.IF }, counts = { IRNode.MIN_L, "1" })
    public long testMinL2E(long a, long b) {
        return a >= b ? b : a;
    }

    @Test
    @IR(phase = { CompilePhase.BEFORE_MACRO_EXPANSION }, failOn = { IRNode.IF }, counts = { IRNode.MAX_L, "1" })
    public long testMaxL1E(long a, long b) {
        return a >= b ? a : b;
    }

    @Test
    @IR(phase = { CompilePhase.BEFORE_MACRO_EXPANSION }, failOn = { IRNode.IF }, counts = { IRNode.MAX_L, "1" })
    public long testMaxL2E(long a, long b) {
        return a <= b ? b : a;
    }

    @Run(test = { "testMinI1", "testMinI2", "testMaxI1", "testMaxI2", "testMinI1E", "testMinI2E", "testMaxI1E", "testMaxI2E" })
    public void runTestIntegers() {
        testIntegers(10, 20);
        testIntegers(20, 10);
        testIntegers(10, 10);
        testIntegers(Integer.MAX_VALUE, Integer.MIN_VALUE);
        testIntegers(Integer.MIN_VALUE, Integer.MAX_VALUE);
        testIntegers(RANDOM.nextInt(), RANDOM.nextInt());
    }

    @DontCompile
    public void testIntegers(int a, int b) {
        Asserts.assertEQ(a < b ? a : b, testMinI1(a, b));
        Asserts.assertEQ(a > b ? b : a, testMinI2(a, b));
        Asserts.assertEQ(a > b ? a : b, testMaxI1(a, b));
        Asserts.assertEQ(a < b ? b : a, testMaxI2(a, b));

        Asserts.assertEQ(a <= b ? a : b, testMinI1E(a, b));
        Asserts.assertEQ(a >= b ? b : a, testMinI2E(a, b));
        Asserts.assertEQ(a >= b ? a : b, testMaxI1E(a, b));
        Asserts.assertEQ(a <= b ? b : a, testMaxI2E(a, b));
    }

    @Run(test = { "testMinL1", "testMinL2", "testMaxL1", "testMaxL2", "testMinL1E", "testMinL2E", "testMaxL1E", "testMaxL2E" })
    public void runTestLongs() {
        testLongs(10, 20);
        testLongs(20, 10);
        testLongs(10, 10);
        testLongs(Integer.MAX_VALUE, Integer.MIN_VALUE);
        testLongs(Integer.MIN_VALUE, Integer.MAX_VALUE);
        testLongs(Long.MAX_VALUE, Long.MIN_VALUE);
        testLongs(Long.MIN_VALUE, Long.MAX_VALUE);
        testLongs(RANDOM.nextLong(), RANDOM.nextLong());
    }

    @DontCompile
    public void testLongs(long a, long b) {
        Asserts.assertEQ(a < b ? a : b, testMinL1(a, b));
        Asserts.assertEQ(a > b ? b : a, testMinL2(a, b));
        Asserts.assertEQ(a > b ? a : b, testMaxL1(a, b));
        Asserts.assertEQ(a < b ? b : a, testMaxL2(a, b));

        Asserts.assertEQ(a <= b ? a : b, testMinL1E(a, b));
        Asserts.assertEQ(a >= b ? b : a, testMinL2E(a, b));
        Asserts.assertEQ(a >= b ? a : b, testMaxL1E(a, b));
        Asserts.assertEQ(a <= b ? b : a, testMaxL2E(a, b));
    }
}
