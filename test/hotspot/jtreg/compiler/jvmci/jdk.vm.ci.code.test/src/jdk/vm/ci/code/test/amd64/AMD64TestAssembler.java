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

package jdk.vm.ci.code.test.amd64;

import static jdk.vm.ci.amd64.AMD64.xmm0;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.DebugInfo;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.site.ConstantReference;
import jdk.vm.ci.code.site.DataSectionReference;
import jdk.vm.ci.code.test.TestAssembler;
import jdk.vm.ci.code.test.TestHotSpotVMConfig;
import jdk.vm.ci.hotspot.HotSpotCallingConventionType;
import jdk.vm.ci.hotspot.HotSpotConstant;
import jdk.vm.ci.hotspot.HotSpotForeignCallTarget;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.VMConstant;

public class AMD64TestAssembler extends TestAssembler {

    private static final Register scratchRegister = AMD64.r12;
    private static final Register doubleScratch = AMD64.xmm15;

    /**
     * The x86 condition codes used for conditional jumps/moves.
     */
    public enum ConditionFlag {
        Zero(0x4, "|zero|"),
        NotZero(0x5, "|nzero|"),
        Equal(0x4, "="),
        NotEqual(0x5, "!="),
        Less(0xc, "<"),
        LessEqual(0xe, "<="),
        Greater(0xf, ">"),
        GreaterEqual(0xd, ">="),
        Below(0x2, "|<|"),
        BelowEqual(0x6, "|<=|"),
        Above(0x7, "|>|"),
        AboveEqual(0x3, "|>=|"),
        Overflow(0x0, "|of|"),
        NoOverflow(0x1, "|nof|"),
        CarrySet(0x2, "|carry|"),
        CarryClear(0x3, "|ncarry|"),
        Negative(0x8, "|neg|"),
        Positive(0x9, "|pos|"),
        Parity(0xa, "|par|"),
        NoParity(0xb, "|npar|");

        public final int value;
        public final String operator;

        ConditionFlag(int value, String operator) {
            this.value = value;
            this.operator = operator;
        }
    }

    public AMD64TestAssembler(CodeCacheProvider codeCache, TestHotSpotVMConfig config) {
        super(codeCache, config, 16, 16, AMD64Kind.DWORD, AMD64.rax, AMD64.rcx, AMD64.rdi, AMD64.r8, AMD64.r9, AMD64.r10);
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

    /**
     * Emit the expected patchable code sequence for the nmethod entry barrier. The int sized
     * payload must be naturally aligned so it can be patched atomically.
     */
    private void emitNMethodEntryCompare(int displacement) {
        // cmp dword ptr [r15 + <displacement>], 0x00000000
        // 41 81 7f <db> 00 00 00 00
        code.emitByte(0x41);
        code.emitByte(0x81);
        code.emitByte(0x7f);
        check(isByte(displacement), "expected byte sized displacement: 0x%x", displacement);
        code.emitByte(displacement & 0xff);
        check(code.position() % 4 == 0, "must be aligned");
        code.emitInt(0);
    }

    /**
     * Emits a long (i.e. 6 byte) format conditional branch.
     *
     * @param offset the offset of the branch target wrt the start of the branch instruction
     */
    private void emitBranch(ConditionFlag condition, int offset) {
        final int longSize = 6;
        int disp32 = offset - longSize;

        // 0000 1111 1000 tttn #32-bit disp
        check(isInt(disp32), "must be 32bit disp: %d", disp32);
        code.emitByte(0x0F);
        code.emitByte(0x80 | condition.value);
        code.emitInt(disp32);
    }

    public void emitAlign(int modulus) {
        while (code.position() % modulus != 0) {
            code.emitByte(0x90);
        }
    }

    private void emitNMethodEntryBarrier() {
        // The following code sequence must be emitted in exactly this fashion as HotSpot
        // will check that the barrier is the expected code sequence.
        emitAlign(4);
        recordMark(config.MARKID_FRAME_COMPLETE);
        recordMark(config.MARKID_ENTRY_BARRIER_PATCH);
        emitNMethodEntryCompare(config.threadDisarmedOffset);
        int branchOffset;
        try (Bookmark bm = bookmark()) {
            int pos = code.position();
            emitBranch(ConditionFlag.Equal, 0);
            emitCall(config.nmethodEntryBarrier);
            branchOffset = code.position() - pos;
        }
        emitBranch(ConditionFlag.Equal, branchOffset);
        emitCall(config.nmethodEntryBarrier);
    }

    @Override
    public void emitPrologue() {
        // WARNING: Initial instruction MUST be 5 bytes or longer so that
        // NativeJump::patch_verified_entry will be able to patch out the entry
        // code safely.
        emitFatNop();
        code.emitByte(0x50 | AMD64.rbp.encoding);  // PUSH rbp
        emitMove(true, AMD64.rbp, AMD64.rsp);      // MOV rbp, rsp
        emitNMethodEntryBarrier();
        setDeoptRescueSlot(newStackSlot(AMD64Kind.QWORD));
    }

    @Override
    public void emitEpilogue() {
        recordMark(config.MARKID_DEOPT_HANDLER_ENTRY);
        recordCall(new HotSpotForeignCallTarget(config.handleDeoptStub), 5, true, null);
        code.emitByte(0xE8); // CALL rel32
        code.emitInt(0xDEADDEAD);
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
        return codeCache.getRegisterConfig().getCallingConventionRegisters(HotSpotCallingConventionType.JavaCall, JavaKind.Int).get(0);
    }

    @Override
    public Register emitIntArg1() {
        return codeCache.getRegisterConfig().getCallingConventionRegisters(HotSpotCallingConventionType.JavaCall, JavaKind.Int).get(1);
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
        return emitLoadInt(ret, c);
    }

    public Register emitLoadInt(Register ret, int c) {
        emitREX(false, 0, 0, ret.encoding);
        code.emitByte(0xB8 | (ret.encoding & 0x7)); // MOV r32, imm32
        code.emitInt(c);
        return ret;
    }

    @Override
    public Register emitLoadLong(long c) {
        Register ret = newRegister();
        return emitLoadLong(ret, c);
    }

    public Register emitLoadLong(Register ret, long c) {
        emitREX(true, 0, 0, ret.encoding);
        code.emitByte(0xB8 | (ret.encoding & 0x7)); // MOV r64, imm64
        code.emitLong(c);
        return ret;
    }

    @Override
    public Register emitLoadFloat(float c) {
        Register ret = AMD64.xmm0;
        return emitLoadFloat(ret, c);
    }

    public Register emitLoadFloat(Register ret, float c) {
        DataSectionReference ref = new DataSectionReference();
        ref.setOffset(data.position());
        data.emitFloat(c);

        recordDataPatchInCode(ref);
        emitREX(false, ret.encoding, 0, 0);
        code.emitByte(0xF3);
        code.emitByte(0x0F);
        code.emitByte(0x10);                               // MOVSS xmm1, xmm2/m32
        code.emitByte(0x05 | ((ret.encoding & 0x7) << 3)); // xmm, [rip+offset]
        code.emitInt(0xDEADDEAD);
        return ret;
    }

    public Register emitLoadDouble(double c) {
        Register ret = AMD64.xmm0;
        return emitLoadDouble(ret, c);
    }

    public Register emitLoadDouble(Register ret, double c) {
        DataSectionReference ref = new DataSectionReference();
        ref.setOffset(data.position());
        data.emitDouble(c);

        recordDataPatchInCode(ref);
        emitREX(false, ret.encoding, 0, 0);
        code.emitByte(0xF2);
        code.emitByte(0x0F);
        code.emitByte(0x10);                               // MOVSD xmm1, xmm2/m32
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
            return emitLoadLong(0xDEADDEADDEADDEADL);
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

    private int getAdjustedOffset(StackSlot ret) {
        if (ret.getRawOffset() < 0) {
            return ret.getRawOffset() + 16;
        } else {
            return -(frameSize - ret.getRawOffset()) + 16;
        }
    }

    @Override
    public StackSlot emitIntToStack(Register a) {
        StackSlot ret = newStackSlot(AMD64Kind.DWORD);
        return emitIntToStack(ret, a);
    }

    public StackSlot emitIntToStack(StackSlot ret, Register a) {
        // MOV r/m32,r32
        emitModRMMemory(false, 0x89, a.encoding, AMD64.rbp.encoding, getAdjustedOffset(ret));
        return ret;
    }

    @Override
    public StackSlot emitLongToStack(Register a) {
        StackSlot ret = newStackSlot(AMD64Kind.QWORD);
        return emitLongToStack(ret, a);
    }

    public StackSlot emitLongToStack(StackSlot ret, Register a) {
        // MOV r/m64,r64
        emitModRMMemory(true, 0x89, a.encoding, AMD64.rbp.encoding, getAdjustedOffset(ret));
        return ret;
    }

    @Override
    public StackSlot emitFloatToStack(Register a) {
        StackSlot ret = newStackSlot(AMD64Kind.SINGLE);
        return emitFloatToStack(ret, a);
    }

    public StackSlot emitFloatToStack(StackSlot ret, Register a) {
        emitREX(false, a.encoding, 0, 0);
        code.emitByte(0xF3);
        code.emitByte(0x0F);
        code.emitByte(0x11);                               // MOVSS xmm2/m32, xmm1
        code.emitByte(0x85 | ((a.encoding & 0x7) << 3));   // [rbp+offset]
        code.emitInt(getAdjustedOffset(ret));
        return ret;
    }

    @Override
    public StackSlot emitDoubleToStack(Register a) {
        StackSlot ret = newStackSlot(AMD64Kind.DOUBLE);
        return emitDoubleToStack(ret, a);
    }

    public StackSlot emitDoubleToStack(StackSlot ret, Register a) {
        emitREX(false, a.encoding, 0, 0);
        code.emitByte(0xF2);
        code.emitByte(0x0F);
        code.emitByte(0x11);                               // MOVSD xmm2/m32, xmm1
        code.emitByte(0x85 | ((a.encoding & 0x7) << 3));   // [rbp+offset]
        code.emitInt(getAdjustedOffset(ret));
        return ret;
    }

    @Override
    public StackSlot emitPointerToStack(Register a) {
        StackSlot ret = newStackSlot(AMD64Kind.QWORD);
        // MOV r/m64,r64
        emitModRMMemory(true, 0x89, a.encoding, AMD64.rbp.encoding, ret.getRawOffset() + 16);
        return ret;
    }

    @Override
    public StackSlot emitNarrowPointerToStack(Register a) {
        StackSlot ret = newStackSlot(AMD64Kind.DWORD);
        // MOV r/m32,r32
        emitModRMMemory(false, 0x89, a.encoding, AMD64.rbp.encoding, ret.getRawOffset() + 16);
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
    public void emitFloatRet(Register a) {
        assert a == xmm0 : "Unimplemented move " + a;
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

    @Override
    public void emitLoad(AllocatableValue av, Object prim) {
        if (av instanceof RegisterValue) {
            Register reg = ((RegisterValue) av).getRegister();
            if (prim instanceof Float) {
                emitLoadFloat(reg, (Float) prim);
            } else if (prim instanceof Double) {
                emitLoadDouble(reg, (Double) prim);
            } else if (prim instanceof Integer) {
                emitLoadInt(reg, (Integer) prim);
            } else if (prim instanceof Long) {
                emitLoadLong(reg, (Long) prim);
            }
        } else if (av instanceof StackSlot) {
            StackSlot slot = (StackSlot) av;
            if (prim instanceof Float) {
                emitFloatToStack(slot, emitLoadFloat(doubleScratch, (Float) prim));
            } else if (prim instanceof Double) {
                emitDoubleToStack(slot, emitLoadDouble(doubleScratch, (Double) prim));
            } else if (prim instanceof Integer) {
                emitIntToStack(slot, emitLoadInt(scratchRegister, (Integer) prim));
            } else if (prim instanceof Long) {
                emitLongToStack(slot, emitLoadLong(scratchRegister, (Long) prim));
            } else {
                assert false : "Unimplemented";
            }
        } else {
            throw new IllegalArgumentException("Unknown value " + av);
        }
    }

    @Override
    public void emitCallPrologue(CallingConvention cc, Object... prim) {
        growFrame(cc.getStackSize());
        AllocatableValue[] args = cc.getArguments();
        // Do the emission in reverse, this avoids register collisons of xmm0 - which is used a
        // scratch register when putting arguments on the stack.
        for (int i = args.length - 1; i >= 0; i--) {
            emitLoad(args[i], prim[i]);
        }
    }

    @Override
    public void emitCall(long addr) {
        Register target = emitLoadLong(AMD64.rax, addr);
        code.emitByte(0xFF); // CALL r/m64
        int enc = target.encoding;
        if (enc >= 8) {
            code.emitByte(0x41);
            enc -= 8;
        }
        code.emitByte(0xD0 | enc);
    }

    @Override
    public void emitCallEpilogue(CallingConvention cc) {
        growFrame(-cc.getStackSize());
    }
}
