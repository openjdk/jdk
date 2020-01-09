/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.runtime.JVMCI;
import org.graalvm.compiler.asm.aarch64.AArch64Address;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.test.GraalTest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assume.assumeTrue;

public class AArch64LoadStoreMergingAssemblerTest extends GraalTest {
    private Register base;
    private Register rt1;
    private Register rt2;

    @Before
    public void checkAArch64() {
        TargetDescription target = JVMCI.getRuntime().getHostJVMCIBackend().getTarget();
        assumeTrue("skipping non AArch64 specific test", target.arch instanceof AArch64);
    }

    @Before
    public void setupEnvironment() {
        base = AArch64.sp;
        rt1 = AArch64.r1;
        rt2 = AArch64.r2;
    }

    private abstract static class AArch64LoadStoreCodeGen {
        protected AArch64MacroAssembler masm1;
        protected AArch64MacroAssembler masm2;

        AArch64LoadStoreCodeGen() {
            TargetDescription target = JVMCI.getRuntime().getHostJVMCIBackend().getTarget();
            masm1 = new AArch64MacroAssembler(target);
            masm2 = new AArch64MacroAssembler(target);
        }

        void emitScaledImmLdr(int size, Register rt, Register base, int imm12) {
            AArch64Address address = AArch64Address.createScaledImmediateAddress(base, imm12);
            masm2.ldr(size, rt, address);
        }

        void emitUnscaledImmLdr(int size, Register rt, Register base, int imm9) {
            AArch64Address address1 = AArch64Address.createUnscaledImmediateAddress(base, imm9);
            masm2.ldr(size, rt, address1);
        }

        void emitScaledImmStr(int size, Register rt, Register base, int imm12) {
            AArch64Address address = AArch64Address.createScaledImmediateAddress(base, imm12);
            masm2.str(size, rt, address);
        }

        void emitUnscaledImmStr(int size, Register rt, Register base, int imm9) {
            AArch64Address address1 = AArch64Address.createUnscaledImmediateAddress(base, imm9);
            masm2.str(size, rt, address1);
        }

        void emitScaledLdp(int size, Register rt1, Register rt2, Register base, int imm7) {
            AArch64Address mergeAddress = AArch64Address.createScaledImmediateAddress(base, imm7);
            masm1.ldp(size, rt1, rt2, mergeAddress);
        }

        void emitScaledStp(int size, Register rt1, Register rt2, Register base, int imm7) {
            AArch64Address mergeAddress = AArch64Address.createScaledImmediateAddress(base, imm7);
            masm1.stp(size, rt1, rt2, mergeAddress);
        }

        void emitUnscaledLdp(int size, Register rt1, Register rt2, Register base, int imm) {
            AArch64Address mergeAddress = AArch64Address.createUnscaledImmediateAddress(base, imm);
            masm1.ldp(size, rt1, rt2, mergeAddress);
        }

        void emitUnscaledStp(int size, Register rt1, Register rt2, Register base, int imm) {
            AArch64Address mergeAddress = AArch64Address.createUnscaledImmediateAddress(base, imm);
            masm1.stp(size, rt1, rt2, mergeAddress);
        }

        abstract void checkAssembly();
    }

    private static class AArch64LoadStoreMergingCodeGen extends AArch64LoadStoreCodeGen {
        AArch64LoadStoreMergingCodeGen() {
            super();
        }

        @Override
        void checkAssembly() {
            byte[] expected = masm1.close(false);
            byte[] actual = masm2.close(false);
            assertArrayEquals(expected, actual);
        }
    }

    @Test
    public void testLoad64BitsScaledImmAddress() {
        AArch64LoadStoreMergingCodeGen codeGen = new AArch64LoadStoreMergingCodeGen();
        codeGen.emitScaledImmLdr(64, rt1, base, 4);
        codeGen.emitScaledImmLdr(64, rt2, base, 5);
        codeGen.emitScaledLdp(64, rt1, rt2, base, 4);
        codeGen.checkAssembly();
    }

    @Test
    public void testLoad32BitsScaledImmAddress() {
        AArch64LoadStoreMergingCodeGen codeGen = new AArch64LoadStoreMergingCodeGen();
        codeGen.emitScaledImmLdr(32, rt1, base, 5);
        codeGen.emitScaledImmLdr(32, rt2, base, 4);
        codeGen.emitScaledLdp(32, rt2, rt1, base, 4);
        codeGen.checkAssembly();
    }

    @Test
    public void testStore64BitsScaledImmAddress() {
        AArch64LoadStoreMergingCodeGen codeGen = new AArch64LoadStoreMergingCodeGen();
        codeGen.emitScaledImmStr(64, rt1, base, 4);
        codeGen.emitScaledImmStr(64, rt2, base, 5);
        codeGen.emitScaledStp(64, rt1, rt2, base, 4);
        codeGen.checkAssembly();
    }

    @Test
    public void testStore32BitsScaledImmAddress() {
        AArch64LoadStoreMergingCodeGen codeGen = new AArch64LoadStoreMergingCodeGen();
        codeGen.emitScaledImmStr(32, rt1, base, 4);
        codeGen.emitScaledImmStr(32, rt2, base, 5);
        codeGen.emitScaledStp(32, rt1, rt2, base, 4);
        codeGen.checkAssembly();
    }

    @Test
    public void testLoad64BitsUnscaledImmAddress() {
        AArch64LoadStoreMergingCodeGen codeGen = new AArch64LoadStoreMergingCodeGen();
        codeGen.emitUnscaledImmLdr(64, rt1, base, -32);
        codeGen.emitUnscaledImmLdr(64, rt2, base, -24);
        codeGen.emitUnscaledLdp(64, rt1, rt2, base, -32);
        codeGen.checkAssembly();
    }

    @Test
    public void testLoad32BitsUnscaledImmAddress() {
        AArch64LoadStoreMergingCodeGen codeGen = new AArch64LoadStoreMergingCodeGen();
        codeGen.emitUnscaledImmLdr(32, rt1, base, 248);
        codeGen.emitUnscaledImmLdr(32, rt2, base, 252);
        codeGen.emitUnscaledLdp(32, rt1, rt2, base, 248);
        codeGen.checkAssembly();
    }

    @Test
    public void testStore64BitsUnscaledImmAddress() {
        AArch64LoadStoreMergingCodeGen codeGen = new AArch64LoadStoreMergingCodeGen();
        codeGen.emitUnscaledImmStr(64, rt1, base, 32);
        codeGen.emitUnscaledImmStr(64, rt2, base, 40);
        codeGen.emitUnscaledStp(64, rt1, rt2, base, 32);
        codeGen.checkAssembly();
    }

    @Test
    public void testStore32BitsUnscaledImmAddress() {
        AArch64LoadStoreMergingCodeGen codeGen = new AArch64LoadStoreMergingCodeGen();
        codeGen.emitUnscaledImmStr(32, rt1, base, 32);
        codeGen.emitUnscaledImmStr(32, rt2, base, 36);
        codeGen.emitUnscaledStp(32, rt1, rt2, base, 32);
        codeGen.checkAssembly();
    }

    @Test
    public void testLoadUnscaledScaledImmAddress() {
        AArch64LoadStoreMergingCodeGen codeGen = new AArch64LoadStoreMergingCodeGen();
        codeGen.emitUnscaledImmLdr(32, rt1, base, 48);
        codeGen.emitScaledImmLdr(32, rt2, base, 13);
        codeGen.emitScaledLdp(32, rt1, rt2, base, 12);
        codeGen.checkAssembly();
    }

    @Test
    public void testLoadScaledUnscaledImmAddress() {
        AArch64LoadStoreMergingCodeGen codeGen = new AArch64LoadStoreMergingCodeGen();
        codeGen.emitScaledImmLdr(32, rt1, base, 13);
        codeGen.emitUnscaledImmLdr(32, rt2, base, 48);
        codeGen.emitUnscaledLdp(32, rt2, rt1, base, 48);
        codeGen.checkAssembly();
    }

    @Test
    public void testLoadMaxAlignedOffset() {
        AArch64LoadStoreMergingCodeGen codeGen = new AArch64LoadStoreMergingCodeGen();
        codeGen.emitScaledImmLdr(64, rt1, base, 62);
        codeGen.emitScaledImmLdr(64, rt2, base, 63);
        codeGen.emitScaledLdp(64, rt1, rt2, base, 62);
        codeGen.checkAssembly();
    }

    @Test
    public void testStoreMinAlignedOffest() {
        AArch64LoadStoreMergingCodeGen codeGen = new AArch64LoadStoreMergingCodeGen();
        codeGen.emitUnscaledImmStr(32, rt1, base, -256);
        codeGen.emitUnscaledImmStr(32, rt2, base, -252);
        codeGen.emitUnscaledStp(32, rt1, rt2, base, -256);
        codeGen.checkAssembly();
    }

    // All the following tests are the negative ones that ldr/str will not be merged to ldp/stp.
    private static class AArch64LoadStoreNotMergingCodeGen extends AArch64LoadStoreCodeGen {
        AArch64LoadStoreNotMergingCodeGen() {
            super();
        }

        @Override
        void checkAssembly() {
            boolean isMerged = masm2.isImmLoadStoreMerged();
            masm2.close(false);
            Assert.assertFalse(isMerged);
        }
    }

    @Test
    public void testDifferentBase() {
        AArch64LoadStoreNotMergingCodeGen codeGen = new AArch64LoadStoreNotMergingCodeGen();
        codeGen.emitScaledImmLdr(32, rt1, base, 4);
        codeGen.emitScaledImmLdr(32, rt2, AArch64.r3, 5);
        codeGen.checkAssembly();
    }

    @Test
    public void testDifferentSize() {
        AArch64LoadStoreNotMergingCodeGen codeGen = new AArch64LoadStoreNotMergingCodeGen();
        codeGen.emitScaledImmLdr(32, rt1, base, 4);
        codeGen.emitScaledImmLdr(64, rt2, base, 5);
        codeGen.checkAssembly();
    }

    @Test
    public void testSameRt() {
        AArch64LoadStoreNotMergingCodeGen codeGen = new AArch64LoadStoreNotMergingCodeGen();
        codeGen.emitScaledImmLdr(32, rt1, base, 4);
        codeGen.emitScaledImmLdr(32, rt1, base, 5);
        codeGen.checkAssembly();
    }

    @Test
    public void testDependencyLdrs() {
        AArch64LoadStoreNotMergingCodeGen codeGen = new AArch64LoadStoreNotMergingCodeGen();
        codeGen.emitScaledImmLdr(32, rt1, rt1, 4);
        codeGen.emitScaledImmLdr(32, rt2, rt1, 5);
        codeGen.checkAssembly();
    }

    @Test
    public void testUnalignedOffset() {
        AArch64LoadStoreNotMergingCodeGen codeGen = new AArch64LoadStoreNotMergingCodeGen();
        codeGen.emitUnscaledImmLdr(32, rt1, base, 34);
        codeGen.emitUnscaledImmLdr(32, rt2, base, 38);
        codeGen.checkAssembly();
    }

    @Test
    public void testUncontinuousOffset() {
        AArch64LoadStoreNotMergingCodeGen codeGen = new AArch64LoadStoreNotMergingCodeGen();
        codeGen.emitScaledImmLdr(32, rt1, base, 4);
        codeGen.emitScaledImmLdr(32, rt2, base, 6);
        codeGen.checkAssembly();
    }

    @Test
    public void testGreaterThanMaxOffset() {
        AArch64LoadStoreNotMergingCodeGen codeGen = new AArch64LoadStoreNotMergingCodeGen();
        codeGen.emitScaledImmLdr(32, rt1, base, 66);
        codeGen.emitScaledImmLdr(32, rt2, base, 67);
        codeGen.checkAssembly();
    }

    @Test
    public void testLdrStr() {
        AArch64LoadStoreNotMergingCodeGen codeGen = new AArch64LoadStoreNotMergingCodeGen();
        codeGen.emitScaledImmLdr(32, rt1, base, 4);
        codeGen.emitScaledImmStr(32, rt2, base, 5);
        codeGen.checkAssembly();
    }
}
