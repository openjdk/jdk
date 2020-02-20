/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019, Arm Limited and affiliates. All rights reserved.
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



package org.graalvm.compiler.asm.aarch64.test;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.runtime.JVMCI;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.test.GraalTest;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

public class AArch64MoveConstantTest extends GraalTest {

    private AArch64MacroAssembler masm;
    private TestProtectedAssembler asm;
    private Register dst;
    private Register zr;

    @Before
    public void setupEnvironment() {
        // Setup AArch64 MacroAssembler and Assembler.
        TargetDescription target = JVMCI.getRuntime().getHostJVMCIBackend().getTarget();
        masm = new AArch64MacroAssembler(target);
        asm = new TestProtectedAssembler(target);
        dst = AArch64.r10;
        zr = AArch64.zr;
    }

    /**
     * MacroAssembler behavior test for 32-bit constant move.
     */
    @Test
    public void testMoveIntZero() {
        masm.mov(dst, 0);   // zero is specially handled by OR(dst, zr, zr).
        asm.orr(32, dst, zr, zr, AArch64Assembler.ShiftType.LSL, 0);
        compareAssembly();
    }

    @Test
    public void testMoveIntLogicalImm() {
        masm.mov(dst, 0x5555_5555);  // 0b01010101...0101 is a 32-bit logical immediate.
        asm.orr(32, dst, zr, 0x5555_5555);
        compareAssembly();
    }

    @Test
    public void testMoveIntMinusOne() {
        masm.mov(dst, -1);
        asm.movn(32, dst, 0, 0);
        compareAssembly();
    }

    @Test
    public void testMoveIntHighZero() {
        masm.mov(dst, 0x0000_1234);
        asm.movz(32, dst, 0x1234, 0);
        compareAssembly();
    }

    @Test
    public void testMoveIntLowZero() {
        masm.mov(dst, 0x5678_0000);
        asm.movz(32, dst, 0x5678, 16);
        compareAssembly();
    }

    @Test
    public void testMoveIntHighNeg() {
        masm.mov(dst, 0xFFFF_CAFE);
        asm.movn(32, dst, 0xCAFE ^ 0xFFFF, 0);
        compareAssembly();
    }

    @Test
    public void testMoveIntLowNeg() {
        masm.mov(dst, 0xBABE_FFFF);
        asm.movn(32, dst, 0xBABE ^ 0xFFFF, 16);
        compareAssembly();
    }

    @Test
    public void testMoveIntCommon() {
        masm.mov(dst, 0x1357_BEEF);
        asm.movz(32, dst, 0xBEEF, 0);
        asm.movk(32, dst, 0x1357, 16);
        compareAssembly();
    }

    /**
     * MacroAssembler behavior test for 64-bit constant move.
     */
    @Test
    public void testMoveLongZero() {
        masm.mov(dst, 0L);  // zero is specially handled by OR(dst, zr, zr).
        asm.orr(64, dst, zr, zr, AArch64Assembler.ShiftType.LSL, 0);
        compareAssembly();
    }

    @Test
    public void testMoveLongLogicalImm() {
        masm.mov(dst, 0x3333_3333_3333_3333L); // 0b00110011...0011 is a 64-bit logical immediate.
        asm.orr(64, dst, zr, 0x3333_3333_3333_3333L);
        compareAssembly();
    }

    @Test
    public void testMoveLongSignExtendedLogicalImm() {
        masm.mov(dst, 0xFFFF_FFFF_8888_8888L); // 0x88888888 is a 32-bit logical immediate.
        asm.orr(32, dst, zr, 0x8888_8888);
        asm.sbfm(64, dst, dst, 0, 31);
        compareAssembly();
    }

    @Test
    public void testMoveLongWithTwoZeros() {
        masm.mov(dst, 0x1357_0000_ABCD_0000L);
        asm.movz(64, dst, 0xABCD, 16);
        asm.movk(64, dst, 0x1357, 48);
        compareAssembly();
    }

    @Test
    public void testMoveLongWithTwoNegs() {
        masm.mov(dst, 0x2222_FFFF_FFFF_7777L);
        asm.movn(64, dst, 0x7777 ^ 0xFFFF, 0);
        asm.movk(64, dst, 0x2222, 48);
        compareAssembly();
    }

    @Test
    public void testMoveLongWithOneZero() {
        masm.mov(dst, 0x0000_6666_5555_4444L);
        asm.movz(64, dst, 0x4444, 0);
        asm.movk(64, dst, 0x5555, 16);
        asm.movk(64, dst, 0x6666, 32);
        compareAssembly();
    }

    @Test
    public void testMoveLongWithOneNeg() {
        masm.mov(dst, 0xDDDD_CCCC_BBBB_FFFFL);
        asm.movn(64, dst, 0xBBBB ^ 0xFFFF, 16);
        asm.movk(64, dst, 0xCCCC, 32);
        asm.movk(64, dst, 0xDDDD, 48);
        compareAssembly();
    }

    @Test
    public void testMoveLongCommon() {
        masm.mov(dst, 0x3D38_2A05_B001_1942L);
        asm.movz(64, dst, 0x1942, 0);
        asm.movk(64, dst, 0xB001, 16);
        asm.movk(64, dst, 0x2A05, 32);
        asm.movk(64, dst, 0x3D38, 48);
        compareAssembly();
    }

    /**
     * Compares assembly generated by the macro assembler to the hand-generated assembly.
     */
    private void compareAssembly() {
        byte[] expected = asm.close(true);
        byte[] actual = masm.close(true);
        assertArrayEquals(expected, actual);
    }

    /**
     * Compare constant values with corresponding hex strings.
     */
    @Test
    public void testConstantHexRepresentation() {
        checkInt(0, "0");
        checkInt(-1, "FFFFFFFF");
        checkInt(0x4B95_0000, "4B950000");
        checkInt(0xEE2A, "EE2A");
        checkInt(0x31C2_FFFF, "31C2FFFF");
        checkInt(0xFFFF_5678, "FFFF5678");
        checkInt(0xB39F_01CC, "B39F01CC");
        checkLong(0L, "0");
        checkLong(-1L, "FFFFFFFFFFFFFFFF");
        checkLong(0x94DDL, "94DD");
        checkLong(0x351C_0000_7B7BL, "351C00007B7B");
        checkLong(0x9012_ABCD_3333_0000L, "9012ABCD33330000");
        checkLong(0xFFFFL, "FFFF");
        checkLong(0xFFFF_0001L, "FFFF0001");
        checkLong(0xFFFF_9302_FFFF_CDEFL, "FFFF9302FFFFCDEF");
        checkLong(0x102A_FFFF_FFFF_FFFFL, "102AFFFFFFFFFFFF");
        checkLong(0x9E8C_3A50_0BC9_44F8L, "9E8C3A500BC944F8");
    }

    private static void checkInt(int value, String hexString) {
        assertTrue(Integer.toHexString(value).toUpperCase().equals(hexString), "Expected: " + hexString);
    }

    private static void checkLong(long value, String hexString) {
        assertTrue(Long.toHexString(value).toUpperCase().equals(hexString), "Expected: " + hexString);
    }
}
