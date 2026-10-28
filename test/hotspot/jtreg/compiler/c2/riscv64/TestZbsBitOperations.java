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
 * @bug 8389236
 * @summary Test Zbs register and immediate single-bit operations on long values
 * @library /test/lib /
 * @requires os.arch == "riscv64" & vm.compiler2.enabled
 * @requires vm.cpu.features ~= ".*zbs.*"
 * @run driver compiler.c2.riscv64.TestZbsBitOperations
 */

package compiler.c2.riscv64;

import compiler.lib.ir_framework.CompLevel;
import compiler.lib.ir_framework.CompilePhase;
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.Run;
import compiler.lib.ir_framework.Test;
import compiler.lib.ir_framework.TestFramework;

import jdk.test.lib.Asserts;

public class TestZbsBitOperations {
    private static final String BEXT = "andL_urshiftL_reg_1";
    private static final String BSET = "orL_reg_lshiftL_1";
    private static final String BINV = "xorL_reg_lshiftL_1";
    private static final String BCLR = "andL_reg_lshiftL_1_not";
    private static final String BSETI = "orL_reg_immL_SingleBit";
    private static final String BINVI = "xorL_reg_immL_SingleBit";
    private static final String BCLRI = "andL_reg_immL_SingleZero";

    private static final long[] INPUTS = {
        0L,
        -1L,
        Long.MIN_VALUE,
        Long.MAX_VALUE,
        0x55AA55AA55AA55AAL
    };

    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:-TieredCompilation");
    }

    @Test(compLevel = CompLevel.C2)
    @IR(counts = {BEXT, "1"}, phase = CompilePhase.FINAL_CODE)
    static long testBext(long value, int shift) {
        return (value >>> shift) & 1L;
    }

    @Test(compLevel = CompLevel.C2)
    @IR(counts = {BSET, "1"}, phase = CompilePhase.FINAL_CODE)
    static long testBset(long value, int shift) {
        return value | (1L << shift);
    }

    @Test(compLevel = CompLevel.C2)
    @IR(counts = {BINV, "1"}, phase = CompilePhase.FINAL_CODE)
    static long testBinv(long value, int shift) {
        return value ^ (1L << shift);
    }

    @Test(compLevel = CompLevel.C2)
    @IR(counts = {BCLR, "1"}, phase = CompilePhase.FINAL_CODE)
    static long testBclr(long value, int shift) {
        return value & ~(1L << shift);
    }

    @Test(compLevel = CompLevel.C2)
    @IR(counts = {BSETI, "4"}, phase = CompilePhase.FINAL_CODE)
    static void testBseti(long[] values) {
        values[0] |= 1L << 11;
        values[1] |= 1L << 31;
        values[2] |= 1L << 32;
        values[3] |= 1L << 63;
    }

    @Test(compLevel = CompLevel.C2)
    @IR(counts = {BINVI, "4"}, phase = CompilePhase.FINAL_CODE)
    static void testBinvi(long[] values) {
        values[0] ^= 1L << 11;
        values[1] ^= 1L << 31;
        values[2] ^= 1L << 32;
        values[3] ^= 1L << 63;
    }

    @Test(compLevel = CompLevel.C2)
    @IR(counts = {BCLRI, "4"}, phase = CompilePhase.FINAL_CODE)
    static void testBclri(long[] values) {
        values[0] &= ~(1L << 11);
        values[1] &= ~(1L << 31);
        values[2] &= ~(1L << 32);
        values[3] &= ~(1L << 63);
    }

    // Bit 10 is the largest bit mask encodable by ori/xori/andi. It must not
    // select a Zbs immediate instruction, even when UseZbs is enabled.
    @Test(compLevel = CompLevel.C2)
    @IR(failOn = {BSETI, BINVI, BCLRI}, phase = CompilePhase.FINAL_CODE)
    static void testSimm12(long[] values) {
        values[0] |= 1L << 10;
        values[1] ^= 1L << 10;
        values[2] &= ~(1L << 10);
    }

    @Run(test = {"testBseti", "testBinvi", "testBclri", "testSimm12"})
    static void runTests() {
        for (long input : INPUTS) {
            long[] values = {input, input, input, input};
            testBseti(values);
            Asserts.assertEQ(values[0], input | (1L << 11));
            Asserts.assertEQ(values[1], input | (1L << 31));
            Asserts.assertEQ(values[2], input | (1L << 32));
            Asserts.assertEQ(values[3], input | (1L << 63));

            values = new long[] {input, input, input, input};
            testBinvi(values);
            Asserts.assertEQ(values[0], input ^ (1L << 11));
            Asserts.assertEQ(values[1], input ^ (1L << 31));
            Asserts.assertEQ(values[2], input ^ (1L << 32));
            Asserts.assertEQ(values[3], input ^ (1L << 63));

            values = new long[] {input, input, input, input};
            testBclri(values);
            Asserts.assertEQ(values[0], input & ~(1L << 11));
            Asserts.assertEQ(values[1], input & ~(1L << 31));
            Asserts.assertEQ(values[2], input & ~(1L << 32));
            Asserts.assertEQ(values[3], input & ~(1L << 63));

            values = new long[] {input, input, input};
            testSimm12(values);
            Asserts.assertEQ(values[0], input | (1L << 10));
            Asserts.assertEQ(values[1], input ^ (1L << 10));
            Asserts.assertEQ(values[2], input & ~(1L << 10));
        }
    }

    @Run(test = {"testBext", "testBset", "testBinv", "testBclr"})
    static void runRegisterTests() {
        int[] shifts = {
            Integer.MIN_VALUE, -65, -64, -33, -32, -1,
            0, 1, 31, 32, 33, 63, 64, 65, Integer.MAX_VALUE
        };
        for (long input : INPUTS) {
            for (int shift : shifts) {
                long mask = 1L << (shift & 63);
                Asserts.assertEQ(testBext(input, shift), (input & mask) == 0 ? 0L : 1L);
                Asserts.assertEQ(testBset(input, shift), input | mask);
                Asserts.assertEQ(testBinv(input, shift), input ^ mask);
                Asserts.assertEQ(testBclr(input, shift), input & ~mask);
            }
        }
    }
}
