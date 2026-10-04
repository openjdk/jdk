/*
 * Copyright (c) 2026, ByteDance Ltd. All rights reserved.
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
 * @summary Test that C2 folds ConvI2L into RISC-V word arithmetic instructions.
 * @library /test/lib /
 * @requires os.arch == "riscv64" & vm.compiler2.enabled
 * @run driver compiler.c2.riscv64.TestConvI2LFusion
 */

package compiler.c2.riscv64;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;

public class TestConvI2LFusion {
    private static final String CONVI2L_REG = "convI2L_reg_reg";

    private static final int[][] TEST_CASES = {
        {0, 1},
        {1, -1},
        {-1, 1},
        {Integer.MAX_VALUE, 1},
        {Integer.MIN_VALUE, -1},
        {Integer.MIN_VALUE, Integer.MAX_VALUE},
        {0x76543210, 0x13579bdf},
        {0x89abcdef, 0x2468ace0},
        {-1, Integer.MIN_VALUE}
    };

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Run(test = {"testAddReg", "testAddImm", "testSubReg", "testMulReg",
                 "testNeg", "testDiv", "testUDiv", "testMod", "testUMod",
                 "testLShiftReg", "testLShiftImm",
                 "testURShiftReg", "testURShiftImm",
                 "testRShiftReg", "testRShiftImm"})
    public void run(RunInfo runInfo) {
        if (runInfo.isWarmUp()) {
            exercise(0x89abcdef, 0x2468ace0);
            return;
        }

        for (int[] testCase : TEST_CASES) {
            assertResult(testCase[0], testCase[1]);
        }
        assertDivisionByZero();
    }

    @DontCompile
    private static void exercise(int x, int y) {
        testAddReg(x, y);
        testAddImm(x);
        testSubReg(x, y);
        testMulReg(x, y);
        testNeg(x);
        testDiv(x, y);
        testUDiv(x, y);
        testMod(x, y);
        testUMod(x, y);
        testLShiftReg(x, y);
        testLShiftImm(x);
        testURShiftReg(x, y);
        testURShiftImm(x);
        testRShiftReg(x, y);
        testRShiftImm(x);
    }

    @DontCompile
    private static void assertResult(int x, int y) {
        Asserts.assertEQ((long) (x + y), testAddReg(x, y));
        Asserts.assertEQ((long) (x + 123), testAddImm(x));
        Asserts.assertEQ((long) (x - y), testSubReg(x, y));
        Asserts.assertEQ((long) (x * y), testMulReg(x, y));
        Asserts.assertEQ((long) -x, testNeg(x));
        Asserts.assertEQ((long) (x / y), testDiv(x, y));
        Asserts.assertEQ((long) Integer.divideUnsigned(x, y), testUDiv(x, y));
        Asserts.assertEQ((long) (x % y), testMod(x, y));
        Asserts.assertEQ((long) Integer.remainderUnsigned(x, y), testUMod(x, y));
        Asserts.assertEQ((long) (x << y), testLShiftReg(x, y));
        Asserts.assertEQ((long) (x << 7), testLShiftImm(x));
        Asserts.assertEQ((long) (x >>> y), testURShiftReg(x, y));
        Asserts.assertEQ((long) (x >>> 7), testURShiftImm(x));
        Asserts.assertEQ((long) (x >> y), testRShiftReg(x, y));
        Asserts.assertEQ((long) (x >> 7), testRShiftImm(x));
    }

    @DontCompile
    private static void assertDivisionByZero() {
        try {
            testDiv(1, 0);
            Asserts.fail("Expected ArithmeticException from signed division");
        } catch (ArithmeticException expected) {
        }

        try {
            testUDiv(1, 0);
            Asserts.fail("Expected ArithmeticException from unsigned division");
        } catch (ArithmeticException expected) {
        }

        try {
            testMod(1, 0);
            Asserts.fail("Expected ArithmeticException from signed remainder");
        } catch (ArithmeticException expected) {
        }

        try {
            testUMod(1, 0);
            Asserts.fail("Expected ArithmeticException from unsigned remainder");
        } catch (ArithmeticException expected) {
        }
    }

    @Test
    @IR(counts = {"convI2L_addI_reg_reg", ">= 1"},
        failOn = {CONVI2L_REG},
        phase = CompilePhase.PRINT_OPTO_ASSEMBLY)
    public static long testAddReg(int x, int y) {
        return (long) (x + y);
    }

    @Test
    @IR(counts = {"convI2L_addI_reg_imm", ">= 1"},
        failOn = {CONVI2L_REG},
        phase = CompilePhase.PRINT_OPTO_ASSEMBLY)
    public static long testAddImm(int x) {
        return (long) (x + 123);
    }

    @Test
    @IR(counts = {"convI2L_subI_reg_reg", ">= 1"},
        failOn = {CONVI2L_REG},
        phase = CompilePhase.PRINT_OPTO_ASSEMBLY)
    public static long testSubReg(int x, int y) {
        return (long) (x - y);
    }

    @Test
    @IR(counts = {"convI2L_mulI_reg_reg", ">= 1"},
        failOn = {CONVI2L_REG},
        phase = CompilePhase.PRINT_OPTO_ASSEMBLY)
    public static long testMulReg(int x, int y) {
        return (long) (x * y);
    }

    @Test
    @IR(counts = {"convI2L_negI_reg", ">= 1"},
        failOn = {CONVI2L_REG},
        phase = CompilePhase.PRINT_OPTO_ASSEMBLY)
    public static long testNeg(int x) {
        return (long) -x;
    }

    @Test
    @IR(counts = {"convI2L_divI", ">= 1"},
        failOn = {CONVI2L_REG},
        phase = CompilePhase.PRINT_OPTO_ASSEMBLY)
    public static long testDiv(int x, int y) {
        return (long) (x / y);
    }

    @Test
    @IR(counts = {"convI2L_UdivI", ">= 1"},
        failOn = {CONVI2L_REG},
        phase = CompilePhase.PRINT_OPTO_ASSEMBLY)
    public static long testUDiv(int x, int y) {
        return (long) Integer.divideUnsigned(x, y);
    }

    @Test
    @IR(counts = {"convI2L_modI", ">= 1"},
        failOn = {CONVI2L_REG},
        phase = CompilePhase.PRINT_OPTO_ASSEMBLY)
    public static long testMod(int x, int y) {
        return (long) (x % y);
    }

    @Test
    @IR(counts = {"convI2L_UmodI", ">= 1"},
        failOn = {CONVI2L_REG},
        phase = CompilePhase.PRINT_OPTO_ASSEMBLY)
    public static long testUMod(int x, int y) {
        return (long) Integer.remainderUnsigned(x, y);
    }

    @Test
    @IR(counts = {"convI2L_lShiftI_reg_reg", ">= 1"},
        failOn = {CONVI2L_REG},
        phase = CompilePhase.PRINT_OPTO_ASSEMBLY)
    public static long testLShiftReg(int x, int y) {
        return (long) (x << y);
    }

    @Test
    @IR(counts = {"convI2L_lShiftI_reg_imm", ">= 1"},
        failOn = {CONVI2L_REG},
        phase = CompilePhase.PRINT_OPTO_ASSEMBLY)
    public static long testLShiftImm(int x) {
        return (long) (x << 7);
    }

    @Test
    @IR(counts = {"convI2L_urShiftI_reg_reg", ">= 1"},
        failOn = {CONVI2L_REG},
        phase = CompilePhase.PRINT_OPTO_ASSEMBLY)
    public static long testURShiftReg(int x, int y) {
        return (long) (x >>> y);
    }

    @Test
    @IR(counts = {"convI2L_urShiftI_reg_imm", ">= 1"},
        failOn = {CONVI2L_REG},
        phase = CompilePhase.PRINT_OPTO_ASSEMBLY)
    public static long testURShiftImm(int x) {
        return (long) (x >>> 7);
    }

    @Test
    @IR(counts = {"convI2L_rShiftI_reg_reg", ">= 1"},
        failOn = {CONVI2L_REG},
        phase = CompilePhase.PRINT_OPTO_ASSEMBLY)
    public static long testRShiftReg(int x, int y) {
        return (long) (x >> y);
    }

    @Test
    @IR(counts = {"convI2L_rShiftI_reg_imm", ">= 1"},
        failOn = {CONVI2L_REG},
        phase = CompilePhase.PRINT_OPTO_ASSEMBLY)
    public static long testRShiftImm(int x) {
        return (long) (x >> 7);
    }
}
