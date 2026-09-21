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

/**
 * @test
 * @bug 8391943
 * @key randomness
 * @summary Test Zba additions with zero-extended int values
 * @library /test/lib /
 * @requires os.arch == "riscv64" & vm.cpu.features ~= ".*zba.*"
 * @run driver compiler.c2.riscv64.TestAddWithZeroExtendedInt
 */

package compiler.c2.riscv64;

import compiler.lib.ir_framework.CompilePhase;
import compiler.lib.ir_framework.DontCompile;
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.Run;
import compiler.lib.ir_framework.RunInfo;
import compiler.lib.ir_framework.Test;
import compiler.lib.ir_framework.TestFramework;
import jdk.test.lib.Asserts;

public class TestAddWithZeroExtendedInt {
    private static final String ADD_L_AND_UXTW = "addL_reg_reg_and_uxtw_b";

    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:-TieredCompilation");
    }

    @Test
    @IR(counts = {ADD_L_AND_UXTW, "1"}, phase = CompilePhase.FINAL_CODE)
    static long testAddLFromLong(long base, long value) {
        return base + (value & 0xFFFF_FFFFL);
    }

    @Run(test = "testAddLFromLong")
    static void runTests() {
        long base = RunInfo.getRandom().nextLong();
        verifyAddLFromLongResults(base, RunInfo.getRandom().nextLong());
    }

    @DontCompile
    static void verifyAddLFromLongResults(long base, long value) {
        long unsigned = value & 0xFFFF_FFFFL;
        Asserts.assertEQ(testAddLFromLong(base, value), base + unsigned);
    }
}
