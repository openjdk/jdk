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
 * @bug 8287087
 * @summary ...
 * @library /test/lib /
 * @requires vm.compiler2.enabled
 * @run driver compiler.intrinsics.math.TestMinMaxOpt
 */

package compiler.intrinsics.math;

import compiler.lib.ir_framework.Argument;
import compiler.lib.ir_framework.Arguments;
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
    @IR(counts = {IRNode.MIN_I, "0"})
    private static int testIntMin(int v) {
        return Math.min(v, v);
    }

    @Test
    @Arguments(values = {Argument.NUMBER_42})
    @IR(counts = {IRNode.MAX_I, "0"})
    private static int testIntMax(int v) {
        return Math.max(v, v);
    }

    @Test
    @Arguments(values = {Argument.NUMBER_42})
    @IR(counts = {IRNode.MIN_L, "0"})
    private static long testLongMin(long v) {
        return Math.min(v, v);
    }

    @Test
    @Arguments(values = {Argument.NUMBER_42})
    @IR(counts = {IRNode.MAX_L, "0"})
    private static long testLongMax(long v) {
        return Math.max(v, v);
    }

    @Test
    @Arguments(values = {Argument.NUMBER_42})
    @IR(counts = {IRNode.MIN_F, "0"})
    private static float testFloatMin(float v) {
        return Math.min(v, v);
    }

    @Test
    @Arguments(values = {Argument.NUMBER_42})
    @IR(counts = {IRNode.MAX_F, "0"})
    private static float testFloatMax(float v) {
        return Math.max(v, v);
    }

    @Test
    @Arguments(values = {Argument.NUMBER_42})
    @IR(counts = {IRNode.MIN_D, "0"})
    private static double testDoubleMin(double v) {
        return Math.min(v, v);
    }

    @Test
    @Arguments(values = {Argument.NUMBER_42})
    @IR(counts = {IRNode.MAX_D, "0"})
    private static double testDoubleMax(double v) {
        return Math.max(v, v);
    }
}
