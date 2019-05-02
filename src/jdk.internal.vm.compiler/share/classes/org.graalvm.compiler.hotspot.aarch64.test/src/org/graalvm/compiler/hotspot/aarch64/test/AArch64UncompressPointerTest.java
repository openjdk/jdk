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



package org.graalvm.compiler.hotspot.aarch64.test;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.runtime.JVMCI;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.hotspot.aarch64.AArch64HotSpotMove;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assume.assumeTrue;

public class AArch64UncompressPointerTest extends GraalCompilerTest {

    private AArch64MacroAssembler masm1;
    private AArch64MacroAssembler masm2;
    private Register input;
    private Register result;

    @Before
    public void checkAArch64() {
        assumeTrue("skipping AArch64 specific test", JVMCI.getRuntime().getHostJVMCIBackend().getTarget().arch instanceof AArch64);
    }

    @Before
    public void setupEnvironment() {
        TargetDescription target = JVMCI.getRuntime().getHostJVMCIBackend().getTarget();
        masm1 = new AArch64MacroAssembler(target);
        masm2 = new AArch64MacroAssembler(target);
        input = AArch64.r10;
        result = AArch64.r11;
    }

    private void emitUncompressPointer(Register base, int shift) {
        AArch64HotSpotMove.UncompressPointer.emitUncompressCode(masm2, input, result, base, shift, true);
    }

    private void compareAssembly() {
        byte[] expected = masm1.close(false);
        byte[] actual = masm2.close(false);
        assertArrayEquals(expected, actual);
    }

    @Test
    public void testUncompressPointerWithBase() {
        Register base = AArch64.r12;
        int shift = 3;
        masm1.add(64, result, base, input, AArch64Assembler.ShiftType.LSL, shift);
        emitUncompressPointer(base, shift);
        compareAssembly();
    }

    @Test
    public void testUncompressPointerWithZeroBase() {
        int shift = 3;
        masm1.shl(64, result, input, shift);
        emitUncompressPointer(null, shift);
        compareAssembly();
    }

    @Test
    public void testUncompressPointerWithZeroBaseAndShift() {
        masm1.or(64, result, AArch64.zr, input);
        emitUncompressPointer(null, 0);
        compareAssembly();
    }

    static class A {
        String str;

        A(String str) {
            this.str = str;
        }
    }

    public static String getObjectField(A a) {
        return a.str;
    }

    @Test
    public void testGetObjectField() {
        test("getObjectField", new A("asfghjkjhgfd"));
    }

    static String[] strings = {"asf", "egfda", "fsdasere", "eqwred", "fgdadgtre", "qwrrtreety"};

    public static String getArrayMember(int index) {
        return strings[index];
    }

    @Test
    public void testGetArrayMember() {
        test("getArrayMember", 4);
    }
}
