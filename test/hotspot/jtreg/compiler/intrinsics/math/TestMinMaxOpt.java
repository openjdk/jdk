/*
 * Copyright (c) 2024, Red Hat, Inc. All rights reserved.
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

/**
 * @test
 * @bug 8323429
 * @summary Test min and max optimizations
 * @library /test/lib /
 * @run driver compiler.intrinsics.math.TestMinMaxOpt
 */

package compiler.intrinsics.math;

import compiler.lib.ir_framework.Argument;
import compiler.lib.ir_framework.Arguments;
import compiler.lib.ir_framework.Check;
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.Test;
import compiler.lib.ir_framework.TestFramework;

public class TestMinMaxOpt {
    public static void main(String[] args) {
        TestFramework.run();
    }

    @Test
    @Arguments(values = {Argument.NUMBER_42})
    @IR(failOn = {IRNode.MIN_I})
    private static int testIntMin(int v) {
        return Math.min(v, v);
    }

    @Check(test = "testIntMin")
    public static void checkTestIntMin(int result) {
        if (result != 42) {
            throw new RuntimeException("Incorrect result: " + result);
        }
    }

    @Test
    @Arguments(values = {Argument.NUMBER_42})
    @IR(failOn = {IRNode.MAX_I})
    private static int testIntMax(int v) {
        return Math.max(v, v);
    }

    @Check(test = "testIntMax")
    public static void checkTestIntMax(int result) {
        if (result != 42) {
            throw new RuntimeException("Incorrect result: " + result);
        }
    }

    @Test
    @Arguments(values = {Argument.NUMBER_42})
    @IR(failOn = {IRNode.MIN_L})
    private static long testLongMin(long v) {
        return Math.min(v, v);
    }

    @Check(test = "testLongMin")
    public static void checkTestLongMin(long result) {
        if (result != 42) {
            throw new RuntimeException("Incorrect result: " + result);
        }
    }

    @Test
    @Arguments(values = {Argument.NUMBER_42})
    @IR(failOn = {IRNode.MAX_L})
    private static long testLongMax(long v) {
        return Math.max(v, v);
    }

    @Check(test = "testLongMax")
    public static void checkTestLongMax(long result) {
        if (result != 42) {
            throw new RuntimeException("Incorrect result: " + result);
        }
    }

    @Test
    @Arguments(values = {Argument.NUMBER_42})
    @IR(failOn = {IRNode.MIN_F})
    private static float testFloatMin(float v) {
        return Math.min(v, v);
    }

    @Check(test = "testFloatMin")
    public static void checkTestFloatMin(float result) {
        if (result != 42) {
            throw new RuntimeException("Incorrect result: " + result);
        }
    }

    @Test
    @Arguments(values = {Argument.NUMBER_42})
    @IR(failOn = {IRNode.MAX_F})
    private static float testFloatMax(float v) {
        return Math.max(v, v);
    }

    @Check(test = "testFloatMax")
    public static void checkTestFloatMax(float result) {
        if (result != 42) {
            throw new RuntimeException("Incorrect result: " + result);
        }
    }

    @Test
    @Arguments(values = {Argument.NUMBER_42})
    @IR(failOn = {IRNode.MIN_D})
    private static double testDoubleMin(double v) {
        return Math.min(v, v);
    }

    @Check(test = "testDoubleMin")
    public static void checkTestDoubleMin(double result) {
        if (result != 42) {
            throw new RuntimeException("Incorrect result: " + result);
        }
    }

    @Test
    @Arguments(values = {Argument.NUMBER_42})
    @IR(failOn = {IRNode.MAX_D})
    private static double testDoubleMax(double v) {
        return Math.max(v, v);
    }

    @Check(test = "testDoubleMax")
    public static void checkTestDoubleMax(double result) {
        if (result != 42) {
            throw new RuntimeException("Incorrect result: " + result);
        }
    }
}
