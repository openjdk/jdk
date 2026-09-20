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
 * @modules java.base/jdk.internal.misc
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
import jdk.internal.misc.Unsafe;
import jdk.test.lib.Asserts;

public class TestAddWithZeroExtendedInt {
    private static final String ADD_P_UXTW = "addP_reg_reg_uxtw_b";
    private static final String SHADD_P_UXTW = "shaddP_reg_reg_uxtw_b";
    private static final String ADD_L_UXTW = "addL_reg_reg_uxtw_b";
    private static final String SHADD_L_UXTW = "shaddL_reg_reg_uxtw_b";
    private static final String ADD_L_AND_UXTW = "addL_reg_reg_and_uxtw_b";
    private static final String SHADD_L_AND_UXTW = "shaddL_reg_reg_and_uxtw_b";

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    private static final byte[] ARRAY = new byte[256];

    static {
        for (int i = 0; i < ARRAY.length; i++) {
            ARRAY[i] = (byte) i;
        }
    }

    public static void main(String[] args) {
        TestFramework.runWithFlags("--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED",
                                   "-XX:-TieredCompilation");
    }

    @Test
    @IR(counts = {ADD_L_UXTW, "1"}, phase = CompilePhase.FINAL_CODE)
    static long testAddL(long base, int value) {
        return base + (value & 0xFFFF_FFFFL);
    }

    @Test
    @IR(counts = {SHADD_L_UXTW, "1"}, phase = CompilePhase.FINAL_CODE)
    static long testSh1AddL(long base, int value) {
        return base + ((value & 0xFFFF_FFFFL) << 1);
    }

    @Test
    @IR(counts = {SHADD_L_UXTW, "1"}, phase = CompilePhase.FINAL_CODE)
    static long testSh2AddL(long base, int value) {
        return base + ((value & 0xFFFF_FFFFL) << 2);
    }

    @Test
    @IR(counts = {SHADD_L_UXTW, "1"}, phase = CompilePhase.FINAL_CODE)
    static long testSh3AddL(long base, int value) {
        return base + ((value & 0xFFFF_FFFFL) << 3);
    }

    @Test
    @IR(counts = {ADD_L_AND_UXTW, "1"}, phase = CompilePhase.FINAL_CODE)
    static long testAddLFromLong(long base, long value) {
        return base + (value & 0xFFFF_FFFFL);
    }

    @Test
    @IR(counts = {SHADD_L_AND_UXTW, "1"}, phase = CompilePhase.FINAL_CODE)
    static long testSh1AddLFromLong(long base, long value) {
        return base + ((value & 0xFFFF_FFFFL) << 1);
    }

    @Test
    @IR(counts = {SHADD_L_AND_UXTW, "1"}, phase = CompilePhase.FINAL_CODE)
    static long testSh2AddLFromLong(long base, long value) {
        return base + ((value & 0xFFFF_FFFFL) << 2);
    }

    @Test
    @IR(counts = {SHADD_L_AND_UXTW, "1"}, phase = CompilePhase.FINAL_CODE)
    static long testSh3AddLFromLong(long base, long value) {
        return base + ((value & 0xFFFF_FFFFL) << 3);
    }

    @Test
    @IR(counts = {ADD_P_UXTW, "1"}, phase = CompilePhase.FINAL_CODE)
    static byte testAddP(int index) {
        return UNSAFE.getByte(ARRAY, index & 0xFFFF_FFFFL);
    }

    @Test
    @IR(counts = {SHADD_P_UXTW, "1"}, phase = CompilePhase.FINAL_CODE)
    static byte testSh1AddP(int index) {
        return UNSAFE.getByte(ARRAY, (index & 0xFFFF_FFFFL) << 1);
    }

    @Test
    @IR(counts = {SHADD_P_UXTW, "1"}, phase = CompilePhase.FINAL_CODE)
    static byte testSh2AddP(int index) {
        return UNSAFE.getByte(ARRAY, (index & 0xFFFF_FFFFL) << 2);
    }

    @Test
    @IR(counts = {SHADD_P_UXTW, "1"}, phase = CompilePhase.FINAL_CODE)
    static byte testSh3AddP(int index) {
        return UNSAFE.getByte(ARRAY, (index & 0xFFFF_FFFFL) << 3);
    }

    @Run(test = {"testAddL", "testSh1AddL", "testSh2AddL", "testSh3AddL",
                 "testAddLFromLong", "testSh1AddLFromLong", "testSh2AddLFromLong", "testSh3AddLFromLong",
                 "testAddP", "testSh1AddP", "testSh2AddP", "testSh3AddP"})
    static void runTests() {
        long base = RunInfo.getRandom().nextLong();
        int positive = RunInfo.getRandom().nextInt() & Integer.MAX_VALUE;
        int negative = RunInfo.getRandom().nextInt() | Integer.MIN_VALUE;
        verifyAddLResults(base, positive);
        verifyAddLResults(base, negative);
        verifyAddLFromLongResults(base, RunInfo.getRandom().nextLong());

        int index = RunInfo.getRandom().nextInt(32);
        verifyAddPResults(index);
    }

    @DontCompile
    static void verifyAddLResults(long base, int value) {
        long unsigned = Integer.toUnsignedLong(value);
        Asserts.assertEQ(testAddL(base, value), base + unsigned);
        Asserts.assertEQ(testSh1AddL(base, value), base + (unsigned << 1));
        Asserts.assertEQ(testSh2AddL(base, value), base + (unsigned << 2));
        Asserts.assertEQ(testSh3AddL(base, value), base + (unsigned << 3));
    }

    @DontCompile
    static void verifyAddLFromLongResults(long base, long value) {
        long unsigned = value & 0xFFFF_FFFFL;
        Asserts.assertEQ(testAddLFromLong(base, value), base + unsigned);
        Asserts.assertEQ(testSh1AddLFromLong(base, value), base + (unsigned << 1));
        Asserts.assertEQ(testSh2AddLFromLong(base, value), base + (unsigned << 2));
        Asserts.assertEQ(testSh3AddLFromLong(base, value), base + (unsigned << 3));
    }

    @DontCompile
    static void verifyAddPResults(int index) {
        int baseOffset = (int) Unsafe.ARRAY_BYTE_BASE_OFFSET;
        int index1 = (index << 1) + ((-baseOffset) & 1);
        int index2 = (index << 2) + ((-baseOffset) & 3);
        int index3 = (index << 3) + ((-baseOffset) & 7);
        Asserts.assertEQ(testAddP(index + baseOffset), ARRAY[index]);
        Asserts.assertEQ(testSh1AddP((baseOffset + index1) >> 1), ARRAY[index1]);
        Asserts.assertEQ(testSh2AddP((baseOffset + index2) >> 2), ARRAY[index2]);
        Asserts.assertEQ(testSh3AddP((baseOffset + index3) >> 3), ARRAY[index3]);
    }
}
