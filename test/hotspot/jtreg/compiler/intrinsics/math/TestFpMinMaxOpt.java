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
 * @run driver compiler.intrinsics.math.TestFpMinMaxOpt
 */

package compiler.intrinsics.math;

import compiler.lib.ir_framework.Argument;
import compiler.lib.ir_framework.Arguments;
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.Test;
import compiler.lib.ir_framework.TestFramework;

public class TestFpMinMaxOpt {
    public static void main(String[] args) {
        TestFramework.run();
    }

    @Test
    @Arguments(values = {Argument.NUMBER_42, Argument.NUMBER_42})
    @IR(counts = {IRNode.MIN_F, "1"})
    private static float testFloatMin(float a, float b) {
        return Math.min(a, b);
    }

    @Test
    @Arguments(values = {Argument.NUMBER_42, Argument.NUMBER_42})
    @IR(counts = {IRNode.MAX_F, "1"})
    private static float testFloatMax(float a, float b) {
        return Math.max(a, b);
    }

    @Test
    @Arguments(values = {Argument.NUMBER_42, Argument.NUMBER_42})
    @IR(counts = {IRNode.MIN_D, "1"})
    private static double testDoubleMin(double a, double b) {
        return Math.min(a, b);
    }

    @Test
    @Arguments(values = {Argument.NUMBER_42, Argument.NUMBER_42})
    @IR(counts = {IRNode.MAX_D, "1"})
    private static double testDoubleMax(double a, double b) {
        return Math.max(a, b);
    }
}
