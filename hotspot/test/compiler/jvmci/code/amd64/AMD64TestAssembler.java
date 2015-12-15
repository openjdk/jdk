/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.CompilationResult;
import jdk.vm.ci.code.CompilationResult.ConstantReference;
import jdk.vm.ci.code.CompilationResult.DataSectionReference;
import jdk.vm.ci.code.DataSection.Data;
import jdk.vm.ci.code.DebugInfo;
import jdk.vm.ci.code.InfopointReason;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.hotspot.HotSpotConstant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.LIRKind;
import jdk.vm.ci.meta.VMConstant;

import compiler.jvmci.code.TestAssembler;

public class AMD64TestAssembler extends TestAssembler {

    public AMD64TestAssembler(CompilationResult result, CodeCacheProvider codeCache) {
        super(result, codeCache, 16, 16, AMD64Kind.DWORD, AMD64.rax, AMD64.rcx, AMD64.rdi, AMD64.r8, AMD64.r9, AMD64.r10);
    }

    public void emitPrologue() {
        emitByte(0x50 | AMD64.rbp.encoding);  // PUSH rbp
        emitMove(true, AMD64.rbp, AMD64.rsp); // MOV rbp, rsp
    }

    public void emitGrowStack(int size) {
        // SUB rsp, size
        emitByte(0x48);
        emitByte(0x81);
        emitByte(0xEC);
        emitInt(size);
    }

    public Register emitIntArg0() {
        return AMD64.rsi;
    }

    public Register emitIntArg1() {
        return AMD64.rdx;
    }

    private void emitREX(boolean w, int r, int x, int b) {
        int wrxb = (w ? 0x08 : 0) | ((r >> 3) << 2) | ((x >> 3) << 1) | (b >> 3);
        if (wrxb != 0) {
            emitByte(0x40 | wrxb);
        }
    }

    private void emitModRMReg(boolean w, int opcode, int r, int m) {
        emitREX(w, r, 0, m);
        emitByte((byte) opcode);
        emitByte((byte) 0xC0 | ((r & 0x7) << 3) | (m & 0x7));
    }

    private void emitModRMMemory(boolean w, int opcode, int r, int b, int offset) {
        emitREX(w, r, 0, b);
        emitByte((byte) opcode);
        emitByte((byte) 0x80 | ((r & 0x7) << 3) | (b & 0x7));
        emitInt(offset);
    }

    public Register emitLoadInt(int c) {
        Register ret = newRegister();
        emitREX(false, 0, 0, ret.encoding);
        emitByte(0xB8 | (ret.encoding & 0x7)); // MOV r32, imm32
        emitInt(c);
        return ret;
    }

    public Register emitLoadLong(long c) {
        Register ret = newRegister();
        emitREX(true, 0, 0, ret.encoding);
        emitByte(0xB8 | (ret.encoding & 0x7)); // MOV r64, imm64
        emitLong(c);
        return ret;
    }

    public Register emitLoadFloat(float c) {
        Data data = codeCache.createDataItem(JavaConstant.forFloat(c));
        DataSectionReference ref = result.getDataSection().insertData(data);
        result.recordDataPatch(position(), ref);
        Register ret = AMD64.xmm0;
        emitREX(false, ret.encoding, 0, 0);
        emitByte(0xF3);
        emitByte(0x0F);
        emitByte(0x10);                               // MOVSS xmm1, xmm2/m32
        emitByte(0x05 | ((ret.encoding & 0x7) << 3)); // xmm, [rip+offset]
        emitInt(0xDEADDEAD);
        return ret;
    }

    public Register emitLoadPointer(HotSpotConstant c) {
        result.recordDataPatch(position(), new ConstantReference((VMConstant) c));
        if (c.isCompressed()) {
            Register ret = newRegister();
            emitREX(false, 0, 0, ret.encoding);
            emitByte(0xB8 | (ret.encoding & 0x7)); // MOV r32, imm32
            emitInt(0xDEADDEAD);
            return ret;
        } else {
            return emitLoadLong(0xDEADDEADDEADDEADl);
        }
    }

    private Register emitLoadPointer(DataSectionReference ref, boolean narrow) {
        result.recordDataPatch(position(), ref);
        Register ret = newRegister();
        emitREX(!narrow, ret.encoding, 0, 0);
        emitByte(0x8B);                               // MOV r64,r/m64
        emitByte(0x05 | ((ret.encoding & 0x7) << 3)); // r64, [rip+offset]
        emitInt(0xDEADDEAD);
        return ret;
    }

    public Register emitLoadPointer(DataSectionReference ref) {
        return emitLoadPointer(ref, false);
    }

    public Register emitLoadNarrowPointer(DataSectionReference ref) {
        return emitLoadPointer(ref, true);
    }

    public Register emitLoadPointer(Register b, int offset) {
        Register ret = newRegister();
        emitModRMMemory(true, 0x8B, ret.encoding, b.encoding, offset); // MOV r64,r/m64
        return ret;
    }

    public StackSlot emitIntToStack(Register a) {
        StackSlot ret = newStackSlot(LIRKind.value(AMD64Kind.DWORD));
        emitModRMMemory(false, 0x89, a.encoding, AMD64.rbp.encoding, ret.getRawOffset() + 16); // MOV r/m32,r32
        return ret;
    }

    public StackSlot emitLongToStack(Register a) {
        StackSlot ret = newStackSlot(LIRKind.value(AMD64Kind.QWORD));
        emitModRMMemory(true, 0x89, a.encoding, AMD64.rbp.encoding, ret.getRawOffset() + 16); // MOV r/m64,r64
        return ret;
    }

    public StackSlot emitFloatToStack(Register a) {
        StackSlot ret = newStackSlot(LIRKind.value(AMD64Kind.SINGLE));
        emitREX(false, a.encoding, 0, 0);
        emitByte(0xF3);
        emitByte(0x0F);
        emitByte(0x11);                               // MOVSS xmm2/m32, xmm1
        emitByte(0x85 | ((a.encoding & 0x7) << 3));   // [rbp+offset]
        emitInt(ret.getRawOffset() + 16);
        return ret;
    }

    public StackSlot emitPointerToStack(Register a) {
        StackSlot ret = newStackSlot(LIRKind.reference(AMD64Kind.QWORD));
        emitModRMMemory(true, 0x89, a.encoding, AMD64.rbp.encoding, ret.getRawOffset() + 16); // MOV r/m64,r64
        return ret;
    }

    public StackSlot emitNarrowPointerToStack(Register a) {
        StackSlot ret = newStackSlot(LIRKind.reference(AMD64Kind.DWORD));
        emitModRMMemory(false, 0x89, a.encoding, AMD64.rbp.encoding, ret.getRawOffset() + 16); // MOV r/m32,r32
        return ret;
    }

    public Register emitUncompressPointer(Register compressed, long base, int shift) {
        if (shift > 0) {
            emitModRMReg(true, 0xC1, 4, compressed.encoding);
            emitByte(shift);
        }
        if (base == 0) {
            return compressed;
        } else {
            Register tmp = emitLoadLong(base);
            emitModRMReg(true, 0x03, tmp.encoding, compressed.encoding);
            return tmp;
        }
    }

    public Register emitIntAdd(Register a, Register b) {
        emitModRMReg(false, 0x03, a.encoding, b.encoding);
        return a;
    }

    private void emitMove(boolean w, Register to, Register from) {
        if (to != from) {
            emitModRMReg(w, 0x8B, to.encoding, from.encoding);
        }
    }

    public void emitIntRet(Register a) {
        emitMove(false, AMD64.rax, a);        // MOV eax, ...
        emitMove(true, AMD64.rsp, AMD64.rbp); // MOV rsp, rbp
        emitByte(0x58 | AMD64.rbp.encoding);  // POP rbp
        emitByte(0xC3);                       // RET
    }

    public void emitPointerRet(Register a) {
        emitMove(true, AMD64.rax, a);         // MOV rax, ...
        emitMove(true, AMD64.rsp, AMD64.rbp); // MOV rsp, rbp
        emitByte(0x58 | AMD64.rbp.encoding);  // POP rbp
        emitByte(0xC3);                       // RET
    }

    public void emitTrap(DebugInfo info) {
        result.recordInfopoint(position(), info, InfopointReason.IMPLICIT_EXCEPTION);
        // MOV rax, [0]
        emitByte(0x8B);
        emitByte(0x04);
        emitByte(0x25);
        emitInt(0);
    }
}
