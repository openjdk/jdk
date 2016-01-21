/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

package compiler.jvmci.code.amd64;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.CallingConvention.Type;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.DebugInfo;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.site.ConstantReference;
import jdk.vm.ci.code.site.DataSectionReference;
import jdk.vm.ci.hotspot.HotSpotConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.LIRKind;
import jdk.vm.ci.meta.VMConstant;

import compiler.jvmci.code.TestAssembler;

public class AMD64TestAssembler extends TestAssembler {

    public AMD64TestAssembler(CodeCacheProvider codeCache) {
        super(codeCache, 16, 16, AMD64Kind.DWORD, AMD64.rax, AMD64.rcx, AMD64.rdi, AMD64.r8, AMD64.r9, AMD64.r10);
    }

    private void emitFatNop() {
        // 5 byte NOP:
        // NOP DWORD ptr [EAX + EAX*1 + 00H]
        code.emitByte(0x0F);
        code.emitByte(0x1F);
        code.emitByte(0x44);
        code.emitByte(0x00);
        code.emitByte(0x00);
    }

    @Override
    public void emitPrologue() {
        // WARNING: Initial instruction MUST be 5 bytes or longer so that
        // NativeJump::patch_verified_entry will be able to patch out the entry
        // code safely.
        emitFatNop();
        code.emitByte(0x50 | AMD64.rbp.encoding);  // PUSH rbp
        emitMove(true, AMD64.rbp, AMD64.rsp);      // MOV rbp, rsp
    }

    @Override
    public void emitGrowStack(int size) {
        // SUB rsp, size
        code.emitByte(0x48);
        code.emitByte(0x81);
        code.emitByte(0xEC);
        code.emitInt(size);
    }

    @Override
    public Register emitIntArg0() {
        return codeCache.getRegisterConfig().getCallingConventionRegisters(Type.JavaCall, JavaKind.Int)[0];
    }

    @Override
    public Register emitIntArg1() {
        return codeCache.getRegisterConfig().getCallingConventionRegisters(Type.JavaCall, JavaKind.Int)[1];
    }

    private void emitREX(boolean w, int r, int x, int b) {
        int wrxb = (w ? 0x08 : 0) | ((r >> 3) << 2) | ((x >> 3) << 1) | (b >> 3);
        if (wrxb != 0) {
            code.emitByte(0x40 | wrxb);
        }
    }

    private void emitModRMReg(boolean w, int opcode, int r, int m) {
        emitREX(w, r, 0, m);
        code.emitByte((byte) opcode);
        code.emitByte((byte) 0xC0 | ((r & 0x7) << 3) | (m & 0x7));
    }

    private void emitModRMMemory(boolean w, int opcode, int r, int b, int offset) {
        emitREX(w, r, 0, b);
        code.emitByte((byte) opcode);
        code.emitByte((byte) 0x80 | ((r & 0x7) << 3) | (b & 0x7));
        code.emitInt(offset);
    }

    @Override
    public Register emitLoadInt(int c) {
        Register ret = newRegister();
        emitREX(false, 0, 0, ret.encoding);
        code.emitByte(0xB8 | (ret.encoding & 0x7)); // MOV r32, imm32
        code.emitInt(c);
        return ret;
    }

    @Override
    public Register emitLoadLong(long c) {
        Register ret = newRegister();
        emitREX(true, 0, 0, ret.encoding);
        code.emitByte(0xB8 | (ret.encoding & 0x7)); // MOV r64, imm64
        code.emitLong(c);
        return ret;
    }

    @Override
    public Register emitLoadFloat(float c) {
        DataSectionReference ref = new DataSectionReference();
        ref.setOffset(data.position());
        data.emitFloat(c);

        recordDataPatchInCode(ref);
        Register ret = AMD64.xmm0;
        emitREX(false, ret.encoding, 0, 0);
        code.emitByte(0xF3);
        code.emitByte(0x0F);
        code.emitByte(0x10);                               // MOVSS xmm1, xmm2/m32
        code.emitByte(0x05 | ((ret.encoding & 0x7) << 3)); // xmm, [rip+offset]
        code.emitInt(0xDEADDEAD);
        return ret;
    }

    @Override
    public Register emitLoadPointer(HotSpotConstant c) {
        recordDataPatchInCode(new ConstantReference((VMConstant) c));
        if (c.isCompressed()) {
            Register ret = newRegister();
            emitREX(false, 0, 0, ret.encoding);
            code.emitByte(0xB8 | (ret.encoding & 0x7)); // MOV r32, imm32
            code.emitInt(0xDEADDEAD);
            return ret;
        } else {
            return emitLoadLong(0xDEADDEADDEADDEADl);
        }
    }

    private Register emitLoadPointer(DataSectionReference ref, boolean narrow) {
        recordDataPatchInCode(ref);
        Register ret = newRegister();
        emitREX(!narrow, ret.encoding, 0, 0);
        code.emitByte(0x8B);                               // MOV r64,r/m64
        code.emitByte(0x05 | ((ret.encoding & 0x7) << 3)); // r64, [rip+offset]
        code.emitInt(0xDEADDEAD);
        return ret;
    }

    @Override
    public Register emitLoadPointer(DataSectionReference ref) {
        return emitLoadPointer(ref, false);
    }

    @Override
    public Register emitLoadNarrowPointer(DataSectionReference ref) {
        return emitLoadPointer(ref, true);
    }

    @Override
    public Register emitLoadPointer(Register b, int offset) {
        Register ret = newRegister();
        emitModRMMemory(true, 0x8B, ret.encoding, b.encoding, offset); // MOV r64,r/m64
        return ret;
    }

    @Override
    public StackSlot emitIntToStack(Register a) {
        StackSlot ret = newStackSlot(LIRKind.value(AMD64Kind.DWORD));
        emitModRMMemory(false, 0x89, a.encoding, AMD64.rbp.encoding, ret.getRawOffset() + 16); // MOV r/m32,r32
        return ret;
    }

    @Override
    public StackSlot emitLongToStack(Register a) {
        StackSlot ret = newStackSlot(LIRKind.value(AMD64Kind.QWORD));
        emitModRMMemory(true, 0x89, a.encoding, AMD64.rbp.encoding, ret.getRawOffset() + 16); // MOV r/m64,r64
        return ret;
    }

    @Override
    public StackSlot emitFloatToStack(Register a) {
        StackSlot ret = newStackSlot(LIRKind.value(AMD64Kind.SINGLE));
        emitREX(false, a.encoding, 0, 0);
        code.emitByte(0xF3);
        code.emitByte(0x0F);
        code.emitByte(0x11);                               // MOVSS xmm2/m32, xmm1
        code.emitByte(0x85 | ((a.encoding & 0x7) << 3));   // [rbp+offset]
        code.emitInt(ret.getRawOffset() + 16);
        return ret;
    }

    @Override
    public StackSlot emitPointerToStack(Register a) {
        StackSlot ret = newStackSlot(LIRKind.reference(AMD64Kind.QWORD));
        emitModRMMemory(true, 0x89, a.encoding, AMD64.rbp.encoding, ret.getRawOffset() + 16); // MOV r/m64,r64
        return ret;
    }

    @Override
    public StackSlot emitNarrowPointerToStack(Register a) {
        StackSlot ret = newStackSlot(LIRKind.reference(AMD64Kind.DWORD));
        emitModRMMemory(false, 0x89, a.encoding, AMD64.rbp.encoding, ret.getRawOffset() + 16); // MOV r/m32,r32
        return ret;
    }

    @Override
    public Register emitUncompressPointer(Register compressed, long base, int shift) {
        if (shift > 0) {
            emitModRMReg(true, 0xC1, 4, compressed.encoding);
            code.emitByte(shift);
        }
        if (base == 0) {
            return compressed;
        } else {
            Register tmp = emitLoadLong(base);
            emitModRMReg(true, 0x03, tmp.encoding, compressed.encoding);
            return tmp;
        }
    }

    @Override
    public Register emitIntAdd(Register a, Register b) {
        emitModRMReg(false, 0x03, a.encoding, b.encoding);
        return a;
    }

    private void emitMove(boolean w, Register to, Register from) {
        if (to != from) {
            emitModRMReg(w, 0x8B, to.encoding, from.encoding);
        }
    }

    @Override
    public void emitIntRet(Register a) {
        emitMove(false, AMD64.rax, a);             // MOV eax, ...
        emitMove(true, AMD64.rsp, AMD64.rbp);      // MOV rsp, rbp
        code.emitByte(0x58 | AMD64.rbp.encoding);  // POP rbp
        code.emitByte(0xC3);                       // RET
    }

    @Override
    public void emitPointerRet(Register a) {
        emitMove(true, AMD64.rax, a);              // MOV rax, ...
        emitMove(true, AMD64.rsp, AMD64.rbp);      // MOV rsp, rbp
        code.emitByte(0x58 | AMD64.rbp.encoding);  // POP rbp
        code.emitByte(0xC3);                       // RET
    }

    @Override
    public void emitTrap(DebugInfo info) {
        recordImplicitException(info);
        // MOV rax, [0]
        code.emitByte(0x8B);
        code.emitByte(0x04);
        code.emitByte(0x25);
        code.emitInt(0);
    }
}
