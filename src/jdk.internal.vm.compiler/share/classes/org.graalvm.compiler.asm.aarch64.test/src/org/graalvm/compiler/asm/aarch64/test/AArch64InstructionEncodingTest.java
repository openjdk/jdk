/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019, Arm Limited. All rights reserved.
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

import static org.junit.Assume.assumeTrue;

import java.nio.ByteBuffer;

import org.graalvm.compiler.asm.aarch64.AArch64Assembler;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.test.GraalTest;
import org.junit.Before;
import org.junit.Test;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.runtime.JVMCI;

public class AArch64InstructionEncodingTest extends GraalTest {
    @Before
    public void checkAArch64() {
        assumeTrue("skipping non AArch64 specific test", JVMCI.getRuntime().getHostJVMCIBackend().getTarget().arch instanceof AArch64);
    }

    private abstract class AArch64InstructionEncodingTestCase {
        private byte[] actual;
        private byte[] expected;
        TestProtectedAssembler assembler;

        AArch64InstructionEncodingTestCase(int expected) {
            this.expected = ByteBuffer.allocate(Integer.BYTES).putInt(expected).array();
            TargetDescription target = JVMCI.getRuntime().getHostJVMCIBackend().getTarget();
            assembler = new TestProtectedAssembler(target);
        }

        int getExpected() {
            return ByteBuffer.wrap(expected).getInt();
        }

        int getActual() {
            return ByteBuffer.wrap(actual).getInt();
        }

        void closeAssembler() {
            this.actual = assembler.close(true);
        }
    }

    private class CntEncodingTestCase extends AArch64InstructionEncodingTestCase {
        CntEncodingTestCase(int expected, int size, Register dst, Register src) {
            super(expected);
            assembler.cnt(size, dst, src);
            closeAssembler();
        }
    }

    private class AddvEncodingTestCase extends AArch64InstructionEncodingTestCase {
        AddvEncodingTestCase(int expected, int size, AArch64Assembler.SIMDElementSize laneWidth, Register dst, Register src) {
            super(expected);
            assembler.addv(size, laneWidth, dst, src);
            closeAssembler();
        }
    }

    private class UmovEncodingTestCase extends AArch64InstructionEncodingTestCase {
        UmovEncodingTestCase(int expected, int size, Register dst, int srcIdx, Register src) {
            super(expected);
            assembler.umov(size, dst, srcIdx, src);
            closeAssembler();
        }
    }

    private static final int invalidInstructionCode = 0x00000000;

    private void assertWrapper(AArch64InstructionEncodingTestCase testCase) {
        assertDeepEquals(testCase.getActual(), testCase.getExpected());
    }

    @Test
    public void testCnt() {
        assertWrapper(new CntEncodingTestCase(0x0058200e, 64, AArch64.v0, AArch64.v0));
        assertWrapper(new CntEncodingTestCase(0x3f58204e, 128, AArch64.v31, AArch64.v1));
    }

    @Test(expected = AssertionError.class)
    @SuppressWarnings("unused")
    public void testCntWithInvalidDataSize() {
        new CntEncodingTestCase(invalidInstructionCode, 32, AArch64.v5, AArch64.v5);
    }

    @Test
    public void testAddv() {
        assertWrapper(new AddvEncodingTestCase(0x20b8310e, 64, AArch64Assembler.SIMDElementSize.Byte, AArch64.v0, AArch64.v1));
        assertWrapper(new AddvEncodingTestCase(0x42b8314e, 128, AArch64Assembler.SIMDElementSize.Byte, AArch64.v2, AArch64.v2));
        assertWrapper(new AddvEncodingTestCase(0xd2ba710e, 64, AArch64Assembler.SIMDElementSize.HalfWord, AArch64.v18, AArch64.v22));
        assertWrapper(new AddvEncodingTestCase(0x77ba714e, 128, AArch64Assembler.SIMDElementSize.HalfWord, AArch64.v23, AArch64.v19));
        assertWrapper(new AddvEncodingTestCase(0x18bbb14e, 128, AArch64Assembler.SIMDElementSize.Word, AArch64.v24, AArch64.v24));
    }

    @Test(expected = AssertionError.class)
    @SuppressWarnings("unused")
    public void testAddvWithInvalidSizeLaneCombo() {
        new AddvEncodingTestCase(invalidInstructionCode, 64, AArch64Assembler.SIMDElementSize.Word, AArch64.v0, AArch64.v1);
    }

    @Test(expected = AssertionError.class)
    @SuppressWarnings("unused")
    public void testAddvWithInvalidDataSize() {
        new AddvEncodingTestCase(invalidInstructionCode, 32, AArch64Assembler.SIMDElementSize.Word, AArch64.v0, AArch64.v1);
    }

    @Test(expected = AssertionError.class)
    @SuppressWarnings("unused")
    public void testAddvWithInvalidLane() {
        new AddvEncodingTestCase(invalidInstructionCode, 128, AArch64Assembler.SIMDElementSize.DoubleWord, AArch64.v0, AArch64.v1);
    }

    @Test
    public void testUmov() {
        assertWrapper(new UmovEncodingTestCase(0x1f3c084e, 64, AArch64.r31, 0, AArch64.v0));
        assertWrapper(new UmovEncodingTestCase(0xe13f184e, 64, AArch64.r1, 1, AArch64.v31));

        assertWrapper(new UmovEncodingTestCase(0x5d3c040e, 32, AArch64.r29, 0, AArch64.v2));
        assertWrapper(new UmovEncodingTestCase(0x833f1c0e, 32, AArch64.r3, 3, AArch64.v28));

        assertWrapper(new UmovEncodingTestCase(0x4b3d020e, 16, AArch64.r11, 0, AArch64.v10));
        assertWrapper(new UmovEncodingTestCase(0x893d1e0e, 16, AArch64.r9, 7, AArch64.v12));

        assertWrapper(new UmovEncodingTestCase(0x0d3d010e, 8, AArch64.r13, 0, AArch64.v8));
        assertWrapper(new UmovEncodingTestCase(0xc73d1f0e, 8, AArch64.r7, 15, AArch64.v14));
    }

    @Test(expected = AssertionError.class)
    @SuppressWarnings("unused")
    public void testUmovInvalidSrcIdx() {
        new UmovEncodingTestCase(invalidInstructionCode, 64, AArch64.r0, 2, AArch64.v0);
    }

    @Test(expected = GraalError.class)
    @SuppressWarnings("unused")
    public void testUmovInvalidDataSize() {
        new UmovEncodingTestCase(invalidInstructionCode, 31, AArch64.r0, 3, AArch64.v0);
    }
}
