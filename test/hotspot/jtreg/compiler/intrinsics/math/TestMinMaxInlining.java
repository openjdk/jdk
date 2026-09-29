/*
 * Copyright (c) 2025, Red Hat, Inc. All rights reserved.
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
 * @bug 8307513
 * @summary Test min and max IR inlining decisions
 * @library /test/lib /
 * @run driver compiler.intrinsics.math.TestMinMaxInlining
 */

package compiler.intrinsics.math;

import compiler.lib.ir_framework.Argument;
import compiler.lib.ir_framework.Arguments;
import compiler.lib.ir_framework.Check;
import compiler.lib.ir_framework.CompilePhase;
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.Test;
import compiler.lib.ir_framework.TestFramework;

public class TestMinMaxInlining {
    public static void main(String[] args) {
        TestFramework.run();
    }

    @Test
    @Arguments(values = { Argument.NUMBER_MINUS_42, Argument.NUMBER_42 })
    @IR(counts = { IRNode.MIN_I, "1" })
    private static int testIntMin(int a, int b) {
        return Math.min(a, b);
    }

    @Check(test = "testIntMin")
    public static void checkTestIntMin(int result) {
        if (result != -42) {
            throw new RuntimeException("Incorrect result: " + result);
        }
    }

    @Test
    @Arguments(values = { Argument.NUMBER_MINUS_42, Argument.NUMBER_42 })
    @IR(counts = { IRNode.MAX_I, "1" })
    private static int testIntMax(int a, int b) {
        return Math.max(a, b);
    }

    @Check(test = "testIntMax")
    public static void checkTestIntMax(int result) {
        if (result != 42) {
            throw new RuntimeException("Incorrect result: " + result);
        }
    }

    // JDK-8307513 does not change the way MinL/MaxL nodes intrinsified in backend.
    // So they are still transformed into CmpL + CMoveL nodes after macro expansion.
    // This is the reason for the different before/after macro expansion assertions below.

    // MinL is not implemented in the backed, so at macro expansion it gets transformed into a CMoveL.
    // The IR asserts verify that before macro expansion MinL exists,
    // but after macro expansion the node disappears.
    @Test
    @Arguments(values = { Argument.NUMBER_MINUS_42, Argument.NUMBER_42 })
    @IR(phase = { CompilePhase.BEFORE_MACRO_EXPANSION }, counts = { IRNode.MIN_L, "1" })
    @IR(phase = { CompilePhase.AFTER_MACRO_EXPANSION }, counts = { IRNode.MIN_L, "0" })
    private static long testLongMin(long a, long b) {
        return Math.min(a, b);
    }

    @Check(test = "testLongMin")
    public static void checkTestLongMin(long result) {
        if (result != -42L) {
            throw new RuntimeException("Incorrect result: " + result);
        }
    }

    // MaxL is not implemented in the backed, so at macro expansion it gets transformed into a CMoveL.
    // The IR asserts verify that before macro expansion MinL exists,
    // but after macro expansion the node disappears.
    @Test
    @Arguments(values = { Argument.NUMBER_MINUS_42, Argument.NUMBER_42 })
    @IR(phase = { CompilePhase.BEFORE_MACRO_EXPANSION }, counts = { IRNode.MAX_L, "1" })
    @IR(phase = { CompilePhase.AFTER_MACRO_EXPANSION }, counts = { IRNode.MAX_L, "0" })
    private static long testLongMax(long a, long b) {
        return Math.max(a, b);
    }

    @Check(test = "testLongMax")
    public static void checkTestLongMax(long result) {
        if (result != 42L) {
            throw new RuntimeException("Incorrect result: " + result);
        }
    }

    @Test
    @Arguments(values = { Argument.NUMBER_MINUS_42, Argument.NUMBER_42 })
    @IR(counts = { IRNode.MIN_F, "1" },
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static float testFloatMin(float a, float b) {
        return Math.min(a, b);
    }

    @Check(test = "testFloatMin")
    public static void checkTestFloatMin(float result) {
        if (result != -42f) {
            throw new RuntimeException("Incorrect result: " + result);
        }
    }

    @Test
    @Arguments(values = { Argument.NUMBER_MINUS_42, Argument.NUMBER_42 })
    @IR(counts = { IRNode.MAX_F, "1" },
       applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static float testFloatMax(float a, float b) {
        return Math.max(a, b);
    }

    @Check(test = "testFloatMax")
    public static void checkTestFloatMax(float result) {
        if (result != 42f) {
            throw new RuntimeException("Incorrect result: " + result);
        }
    }

    @Test
    @Arguments(values = { Argument.NUMBER_MINUS_42, Argument.NUMBER_42 })
    @IR(counts = { IRNode.MIN_D, "1" },
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static double testDoubleMin(double a, double b) {
        return Math.min(a, b);
    }

    @Check(test = "testDoubleMin")
    public static void checkTestDoubleMin(double result) {
        if (result != -42D) {
            throw new RuntimeException("Incorrect result: " + result);
        }
    }

    @Test
    @Arguments(values = { Argument.NUMBER_MINUS_42, Argument.NUMBER_42 })
    @IR(counts = { IRNode.MAX_D, "1" },
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private static double testDoubleMax(double a, double b) {
        return Math.max(a, b);
    }

    @Check(test = "testDoubleMax")
    public static void checkTestDoubleMax(double result) {
        if (result != 42D) {
            throw new RuntimeException("Incorrect result: " + result);
        }
    }
}
