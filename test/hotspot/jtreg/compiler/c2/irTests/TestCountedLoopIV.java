/*
 * Copyright (c) 2024 Red Hat and/or its affiliates. All rights reserved.
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

import compiler.lib.ir_framework.Argument;
import compiler.lib.ir_framework.Arguments;
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.Test;
import compiler.lib.ir_framework.TestFramework;

import java.util.function.Function;

/**
 * @test
 * @bug 8328528
 * @summary test the long typed parallel iv replacing transformation for int counted loop
 * @library /test/lib /
 * @run main compiler.c2.irTests.TestCountedLoopIV
 */
public class TestCountedLoopIV {
    public static void main(String[] args) {
        TestFramework.run();
        testCorrectness();
    }

    @Test
    @Arguments(values = {Argument.DEFAULT})
    @IR(failOn = {IRNode.LOOP, IRNode.COUNTED_LOOP})
    private static int testIntCountedLoopWithIntIV(int stop) {
        int a = 0;
        for (int i = 0; i < stop; i++) {
            a += 1;
        }

        return a;
    }

    private static int testIntCountedLoopWithIntIVZero(int stop) {
        int a = 0;
        for (int i = 0; i < stop; i++) {
            a += 0; // we unfortunately have to repeat ourselves because the operand has to be a constant
        }

        return a;
    }

    private static int testIntCountedLoopWithIntIVMax(int stop) {
        int a = 0;
        for (int i = 0; i < stop; i++) {
            a += Integer.MAX_VALUE;
        }

        return a;
    }

    @Test
    @Arguments(values = {Argument.DEFAULT})
    @IR(failOn = {IRNode.LOOP, IRNode.COUNTED_LOOP})
    private static long testIntCountedLoopWithLongIV(int stop) {
        long a = 0;
        for (int i = 0; i < stop; i++) {
            a += 1;
        }

        return a;
    }

    private static long testIntCountedLoopWithLongIVZero(int stop) {
        long a = 0;
        for (int i = 0; i < stop; i++) {
            a += 0;
        }

        return a;
    }

    private static long testIntCountedLoopWithLongIVMax(int stop) {
        long a = 0;
        for (int i = 0; i < stop; i++) {
            a += Long.MAX_VALUE;
        }

        return a;
    }

    private static <T extends Number> void test(Function<Integer, T> function, int iteration, T expected) {
        T result = function.apply(iteration);
        if (!result.equals(expected)) {
            throw new RuntimeException("Bad result for IV with stop = " + iteration + ", expected " + expected
                    + ", got " + result);
        }
    }

    private static void testCorrectness() {
        int[] iterations = {0, 1, 2, 42, 100};

        for (int i : iterations) {
            test(TestCountedLoopIV::testIntCountedLoopWithIntIV, i, i);
            test(TestCountedLoopIV::testIntCountedLoopWithIntIVZero, i, 0);
            test(TestCountedLoopIV::testIntCountedLoopWithIntIVMax, i, i * Integer.MAX_VALUE);
            test(TestCountedLoopIV::testIntCountedLoopWithLongIV, i, (long) i);
            test(TestCountedLoopIV::testIntCountedLoopWithLongIVZero, i, (long) 0);
            test(TestCountedLoopIV::testIntCountedLoopWithLongIVMax, i, (long) i * Long.MAX_VALUE);
        }
    }
}