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
 * @test
 * @summary Test that the int-to-long widening of a masked int is folded into the AND when the mask is non-negative
 * @library /test/lib /
 * @requires os.arch == "aarch64" & vm.compiler2.enabled
 * @run driver compiler.c2.aarch64.TestMaskedWidening
 */

package compiler.c2.aarch64;

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

    private static final long[] VALUES = {
        0L, 1L, -1L, Long.MIN_VALUE, Long.MAX_VALUE,
        0x1234_5678_9abc_def0L, 0xffff_ffff_8000_0000L
    };

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Test
    @IR(counts = {I2L_ANDI, "1"}, failOn = {I2L}, phase = CompilePhase.FINAL_CODE)
    static long maskImmediate(long value) {
        return (int) value & 0x7ff;
    }

    @Test
    @IR(counts = {I2L_ANDI, "1"}, failOn = {I2L}, phase = CompilePhase.FINAL_CODE)
    static long maskNonContiguousImmediate(long value) {
        return (int) value & 0x0f0f0f0f;
    }

    @Test
    @IR(counts = {I2L, "1"}, failOn = {I2L_ANDI}, phase = CompilePhase.FINAL_CODE)
    static long maskNegativeImmediate(long value) {
        return (int) value & -8;
    }

    @Test
    @IR(counts = {I2L, "1"}, failOn = {I2L_ANDI}, phase = CompilePhase.FINAL_CODE)
    static long maskOutOfRangeImmediate(long value) {
        return (int) value & 0x12345678;
    }

    @Run(test = {"maskImmediate", "maskNonContiguousImmediate", "maskNegativeImmediate", "maskOutOfRangeImmediate"})
    static void runTests() {
        for (long value : VALUES) {
            verify(value);
        }
    }

    @DontCompile
    static void verify(long value) {
        Asserts.assertEQ(maskImmediate(value), (long) ((int) value & 0x7ff));
        Asserts.assertEQ(maskNonContiguousImmediate(value), (long) ((int) value & 0x0f0f0f0f));
        Asserts.assertEQ(maskNegativeImmediate(value), (long) ((int) value & -8));
        Asserts.assertEQ(maskOutOfRangeImmediate(value), (long) ((int) value & 0x12345678));
    }
}