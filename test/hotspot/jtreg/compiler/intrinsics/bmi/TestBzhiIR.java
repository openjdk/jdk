/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8389111
 * @summary Verify BZHI matching for bounded and unrestricted int shift counts
 * @requires vm.simpleArch == "x64" & vm.compiler2.enabled
 * @library /test/lib /
 * @run driver compiler.intrinsics.bmi.TestBzhiIR
 */

package compiler.intrinsics.bmi;

import compiler.lib.ir_framework.Argument;
import compiler.lib.ir_framework.Arguments;
import compiler.lib.ir_framework.CompilePhase;
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.Test;
import compiler.lib.ir_framework.TestFramework;

public class TestBzhiIR {

    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:+IgnoreUnrecognizedVMOptions",
                                   "-XX:+UseBMI2Instructions");
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(counts = {"bzhiI_rReg_rReg\\b", "1"},
        failOn = {"bzhiI_rReg_rReg_bounded"},
        phase = CompilePhase.MATCHING,
        applyIfCPUFeature = {"bmi2", "true"})
    public static int testUnrestricted(int value, int bits) {
        return value & ((1 << bits) - 1);
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(counts = {"bzhiI_rReg_rReg_bounded", "1"},
        failOn = {"bzhiI_rReg_rReg\\b"},
        phase = CompilePhase.MATCHING,
        applyIfCPUFeature = {"bmi2", "true"})
    public static int testBounded(int value, int bits) {
        return value & ((1 << (bits & 31)) - 1);
    }
}
