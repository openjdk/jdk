/*
 * Copyright (c) 2026, Alibaba Group Holding Limited. All Rights Reserved.
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

/*
 * @test id=zbb
 * @summary Test that int-to-long conversions of masked ints are not sign extended
 * @library /test/lib /
 * @requires os.arch == "riscv64" & vm.compiler2.enabled
 * @requires vm.cpu.features ~= ".*zbb.*"
 * @run driver compiler.c2.riscv64.TestMaskedWidening -XX:+UseZbb
 */

/*
 * @test id=no-zbb
 * @summary Test that int-to-long conversions of masked ints are not sign extended
 * @library /test/lib /
 * @requires os.arch == "riscv64" & vm.compiler2.enabled
 * @run driver compiler.c2.riscv64.TestMaskedWidening -XX:-UseZbb
 */

package compiler.c2.riscv64;

import compiler.lib.ir_framework.CompilePhase;
import compiler.lib.ir_framework.DontCompile;
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.Run;
import compiler.lib.ir_framework.Test;
import compiler.lib.ir_framework.TestFramework;
import jdk.test.lib.Asserts;

public class TestMaskedWidening {
    private static final String I2L = "convI2L_reg_reg";
    private static final String I2L_ANDI = "convI2L_andI_reg_imm";
    private static final String I2L_ZEXT_H = "convI2L_andI_16bits_b";

    private static final int[] VALUES = {
        0, 1, -1, Integer.MIN_VALUE, Integer.MAX_VALUE,
        0x40000000, 0x80000001, 0x55555555, 0xaaaaaaaa
    };

    public static void main(String[] args) {
        TestFramework.runWithFlags(args);
    }

    @Test
    @IR(counts = {I2L_ANDI, "1"}, failOn = {I2L}, phase = CompilePhase.FINAL_CODE)
    static long maskImmediate(int value) {
        return (long) (value & 0x7ff);
    }

    @Test
    @IR(counts = {I2L_ANDI, "1"}, failOn = {I2L}, phase = CompilePhase.FINAL_CODE)
    static long maskNegativeImmediate(int value) {
        return (long) (value & -2048);
    }

    @Test
    @IR(counts = {I2L, "1"}, failOn = {I2L_ANDI}, phase = CompilePhase.FINAL_CODE)
    static long maskOutOfRangeImmediate(int value) {
        return (long) (value & 0xfff);
    }

    @Test
    @IR(counts = {I2L_ZEXT_H, "1"}, failOn = {I2L},
        applyIf = {"UseZbb", "true"}, phase = CompilePhase.FINAL_CODE)
    static long maskShort(int value) {
        return (long) (value & 0xffff);
    }

    @Run(test = {"maskImmediate", "maskNegativeImmediate", "maskOutOfRangeImmediate", "maskShort"})
    static void runTests() {
        for (int value : VALUES) {
            verify(value);
        }
    }

    @DontCompile
    static void verify(int value) {
        Asserts.assertEQ(maskImmediate(value), (long) (value & 0x7ff));
        Asserts.assertEQ(maskNegativeImmediate(value), (long) (value & -2048));
        Asserts.assertEQ(maskOutOfRangeImmediate(value), (long) (value & 0xfff));
        Asserts.assertEQ(maskShort(value), (long) (value & 0xffff));
    }
}
