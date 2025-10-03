/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.vm.ci.code.test.riscv64;

import java.util.List;
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
import jdk.vm.ci.riscv64.RISCV64;
import jdk.vm.ci.riscv64.RISCV64Kind;

public class RISCV64TestAssembler extends TestAssembler {

    private static final Register scratchRegister = RISCV64.x5;
    private static final Register doubleScratch = RISCV64.f5;

    public RISCV64TestAssembler(CodeCacheProvider codeCache, TestHotSpotVMConfig config) {
        super(codeCache, config,
              16 /* initialFrameSize */, 16 /* stackAlignment */,
              RISCV64Kind.DWORD /* narrowOopKind */,
              /* registers */
              RISCV64.x10, RISCV64.x11, RISCV64.x12, RISCV64.x13,
              RISCV64.x14, RISCV64.x15, RISCV64.x16, RISCV64.x17);
    }

    private static int f(int val, int msb, int lsb) {
        int nbits = msb - lsb + 1;
        assert val >= 0;
        assert val < (1 << nbits);
        assert msb >= lsb;
        return val << lsb;
    }

    private static int f(Register r, int msb, int lsb) {
        assert msb - lsb == 4;
        return f(r.encoding, msb, lsb);
    }

    private static int instructionImmediate(int imm, int rs1, int funct, int rd, int opcode) {
        return f(imm, 31, 20) | f(rs1, 19, 15) | f(funct, 14, 12) | f(rd, 11, 7) | f(opcode, 6, 0);
    }

    private static int instructionRegister(int funct7, int rs2, int rs1, int funct3, int rd, int opcode) {
        return f(funct7, 31, 25) | f(rs2, 24, 20) | f(rs1, 19, 15) | f(funct3, 14, 12) | f(rd, 11, 7) | f(opcode, 6, 0);
    }

    private void emitNop() {
        code.emitInt(instructionImmediate(0, 0, 0b000, 0, 0b0010011));
    }

    private void emitAdd(Register Rd, Register Rm, Register Rn) {
        // ADD
        code.emitInt(instructionRegister(0b0000000, Rn.encoding, Rm.encoding, 0b000, Rd.encoding, 0b0110011));
    }

    private void emitAdd(Register Rd, Register Rn, int imm12) {
        // ADDI
        code.emitInt(instructionImmediate(imm12 & 0xfff, Rn.encoding, 0b000, Rd.encoding, 0b0010011));
    }

    private void emitAddW(Register Rd, Register Rn, int imm12) {
        // ADDIW
        code.emitInt(instructionImmediate(imm12 & 0xfff, Rn.encoding, 0b000, Rd.encoding, 0b0011011));
    }

    private void emitSub(Register Rd, Register Rn, int imm12) {
        // SUBI
        emitAdd(Rd, Rn, -imm12);;
    }

    private void emitSub(Register Rd, Register Rm, Register Rn) {
        // SUB
        code.emitInt(instructionRegister(0b0100000, Rn.encoding, Rm.encoding, 0b000, Rd.encoding, 0b0110011));
    }

    private void emitMv(Register Rd, Register Rn) {
        // MV
        code.emitInt(instructionRegister(0b0000000, 0, Rn.encoding, 0b000, Rd.encoding, 0b0110011));
    }

    private void emitShiftLeft(Register Rd, Register Rn, int shift) {
        // SLLI
        code.emitInt(instructionImmediate(shift & 0x3f, Rn.encoding, 0b001, Rd.encoding, 0b0010011));
    }

    private void emitShiftRight(Register Rd, Register Rn, int shift) {
        // SRLI
        code.emitInt(instructionImmediate(shift & 0x3f, Rn.encoding, 0b101, Rd.encoding, 0b0010011));
    }

    private void emitLui(Register Rd, int imm20) {
        // LUI
        code.emitInt(f(imm20, 31, 12) | f(Rd, 11, 7) | f(0b0110111, 6, 0));
    }

    private void emitAuipc(Register Rd, int imm20) {
        // AUIPC
        code.emitInt(f(imm20, 31, 12) | f(Rd, 11, 7) | f(0b0010111, 6, 0));
    }

    private void emitLoadImmediate(Register Rd, int imm32) {
        long upper = imm32, lower = imm32;
        lower = (lower << 52) >> 52;
        upper -= lower;
        upper = (int) upper;
        emitLui(Rd, ((int) (upper >> 12)) & 0xfffff);
        emitAddW(Rd, Rd, (int) lower);
    }

    private void emitLoadRegister(Register Rd, RISCV64Kind kind, Register Rn, int offset) {
        // LB/LH/LW/LD (immediate)
        assert offset >= 0;
        int size = 0;
        int opc = 0;
        switch (kind) {
            case BYTE: size = 0b000; opc = 0b0000011; break;
            case WORD: size = 0b001; opc = 0b0000011; break;
            case DWORD: size = 0b010; opc = 0b0000011; break;
            case QWORD: size = 0b011; opc = 0b0000011; break;
            case SINGLE: size = 0b010; opc = 0b0000111; break;
            case DOUBLE: size = 0b011; opc = 0b0000111; break;
            default: throw new IllegalArgumentException();
        }
        code.emitInt(f(offset, 31, 20) | f(Rn, 19, 15) | f(size, 14, 12) | f(Rd, 11, 7) | f(opc, 6, 0));
    }

    private void emitStoreRegister(Register Rd, RISCV64Kind kind, Register Rn, int offset) {
        // SB/SH/SW/SD (immediate)
        assert offset >= 0;
        int size = 0;
        int opc = 0;
        switch (kind) {
            case BYTE: size = 0b000; opc = 0b0100011; break;
            case WORD: size = 0b001; opc = 0b0100011; break;
            case DWORD: size = 0b010; opc = 0b0100011; break;
            case QWORD: size = 0b011; opc = 0b0100011; break;
            case SINGLE: size = 0b010; opc = 0b0100111; break;
            case DOUBLE: size = 0b011; opc = 0b0100111; break;
            default: throw new IllegalArgumentException();
        }
        code.emitInt(f((offset >> 5), 31, 25) | f(Rd, 24, 20) | f(Rn, 19, 15) | f(size, 14, 12) | f((offset & 0x1f), 11, 7) | f(opc, 6, 0));
    }

    private void emitJalr(Register Rd, Register Rn, int imm) {
        code.emitInt(instructionImmediate(imm & 0xfff, Rn.encoding, 0b000, Rd.encoding, 0b1100111));
    }

    private void emitFmv(Register Rd, RISCV64Kind kind, Register Rn) {
        int funct = 0;
        switch (kind) {
            case SINGLE: funct = 0b1111000; break;
            case DOUBLE: funct = 0b1111001; break;
            default: throw new IllegalArgumentException();
        }
        code.emitInt(instructionRegister(funct, 0b00000, Rn.encoding, 0b000, Rd.encoding, 0b1010011));
    }

    @Override
    public void emitGrowStack(int size) {
        assert size % 16 == 0;
        if (size > -2048 && size < 0) {
            emitAdd(RISCV64.x2, RISCV64.x2, -size);
        } else if (size == 0) {
            // No-op
        } else if (size < 2048) {
            emitSub(RISCV64.x2, RISCV64.x2, size);
        } else if (size < 65535) {
            emitLoadImmediate(scratchRegister, size);
            emitSub(RISCV64.x2, RISCV64.x2, scratchRegister);
        } else {
            throw new IllegalArgumentException();
        }
    }

    @Override
    public void emitPrologue() {
        // Must be patchable by NativeJump::patch_verified_entry
        emitNop();
        emitAdd(RISCV64.x2, RISCV64.x2, -32); // addi sp sp -32
        emitStoreRegister(RISCV64.x8, RISCV64Kind.QWORD, RISCV64.x2, 0); // sd x8 sp(0)
        emitStoreRegister(RISCV64.x1, RISCV64Kind.QWORD, RISCV64.x2, 8); // sd x1 sp(8)
        emitMv(RISCV64.x8, RISCV64.x2); // mv x8, x2

        setDeoptRescueSlot(newStackSlot(RISCV64Kind.QWORD));
    }

    @Override
    public void emitEpilogue() {
        recordMark(config.MARKID_DEOPT_HANDLER_ENTRY);
        recordCall(new HotSpotForeignCallTarget(config.handleDeoptStub), 6*4, true, null);
        emitCall(0xdeaddeaddeadL);
    }

    @Override
    public void emitCallPrologue(CallingConvention cc, Object... prim) {
        growFrame(cc.getStackSize());
        List<AllocatableValue> args = cc.getArguments();
        for (int i = 0; i < args.size(); i++) {
            emitLoad(args.get(i), prim[i]);
        }
    }

    @Override
    public void emitCallEpilogue(CallingConvention cc) {
        growFrame(-cc.getStackSize());
    }

    @Override
    public void emitCall(long addr) {
        emitMovPtrHelper(scratchRegister, addr);
        emitJalr(RISCV64.x1, scratchRegister, (int) (addr & 0x3f));
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

    private void emitLoad32(Register ret, int addr) {
        long upper = addr, lower = addr;
        lower = (lower << 52) >> 52;
        upper -= lower;
        upper = (int) upper;
        emitLui(ret, ((int) (upper >> 12)) & 0xfffff);
        emitAdd(ret, ret, (int) lower);
    }

    private void emitMovPtrHelper(Register ret, long addr) {
        // 48-bit VA
        assert (addr >> 48) == 0 : "invalid pointer" + Long.toHexString(addr);
        emitLoad32(ret, (int) (addr >> 17));
        emitShiftLeft(ret, ret, 11);
        emitAdd(ret, ret, (int) ((addr >> 6) & 0x7ff));
        emitShiftLeft(ret, ret, 6);
    }

    private void emitLoadPointer32(Register ret, int addr) {
        emitLoadImmediate(ret, addr);
        // Lui sign-extends the value, which we do not want
        emitShiftLeft(ret, ret, 32);
        emitShiftRight(ret, ret, 32);
    }

    private void emitLoadPointer48(Register ret, long addr) {
        emitMovPtrHelper(ret, addr);
        emitAdd(ret, ret, (int) (addr & 0x3f));
    }

    @Override
    public Register emitLoadPointer(HotSpotConstant c) {
        recordDataPatchInCode(new ConstantReference((VMConstant) c));

        Register ret = newRegister();
        if (c.isCompressed()) {
            emitLoadPointer32(ret, 0xdeaddead);
        } else {
            emitLoadPointer48(ret, 0xdeaddeaddeadL);
        }
        return ret;
    }

    @Override
    public Register emitLoadPointer(Register b, int offset) {
        Register ret = newRegister();
        emitLoadRegister(ret, RISCV64Kind.QWORD, b, offset & 0xfff);
        return ret;
    }

    @Override
    public Register emitLoadNarrowPointer(DataSectionReference ref) {
        recordDataPatchInCode(ref);

        Register ret = newRegister();
        emitAuipc(ret, 0xdead >> 11);
        emitLoadRegister(ret, RISCV64Kind.DWORD, ret, 0xdead & 0x7ff);
        // The value is sign-extendsed, which we do not want
        emitShiftLeft(ret, ret, 32);
        emitShiftRight(ret, ret, 32);
        return ret;
    }

    @Override
    public Register emitLoadPointer(DataSectionReference ref) {
        recordDataPatchInCode(ref);

        Register ret = newRegister();
        emitAuipc(ret, 0xdead >> 11);
        emitLoadRegister(ret, RISCV64Kind.QWORD, ret, 0xdead & 0x7ff);
        return ret;
    }

    private Register emitLoadDouble(Register reg, double c) {
        DataSectionReference ref = new DataSectionReference();
        ref.setOffset(data.position());
        data.emitDouble(c);

        recordDataPatchInCode(ref);
        emitAuipc(scratchRegister, 0xdead >> 11);
        emitLoadRegister(scratchRegister, RISCV64Kind.QWORD, scratchRegister, 0xdead & 0x7ff);
        if (reg.getRegisterCategory().equals(RISCV64.FP)) {
            emitFmv(reg, RISCV64Kind.DOUBLE, scratchRegister);
        } else {
            emitMv(reg, scratchRegister);
        }
        return reg;
    }

    private Register emitLoadFloat(Register reg, float c) {
        DataSectionReference ref = new DataSectionReference();
        ref.setOffset(data.position());
        data.emitFloat(c);

        recordDataPatchInCode(ref);
        emitAuipc(scratchRegister, 0xdead >> 11);
        emitLoadRegister(scratchRegister, RISCV64Kind.DWORD, scratchRegister, 0xdead & 0x7ff);
        if (reg.getRegisterCategory().equals(RISCV64.FP)) {
            emitFmv(reg, RISCV64Kind.SINGLE, scratchRegister);
        } else {
            emitMv(reg, scratchRegister);
        }
        return reg;
    }

    @Override
    public Register emitLoadFloat(float c) {
        Register ret = RISCV64.f10;
        return emitLoadFloat(ret, c);
    }

    private Register emitLoadLong(Register reg, long c) {
        long lower = c & 0xffffffff;
        lower = lower - ((lower << 44) >> 44);
        emitLoad32(reg, (int) ((c >> 32) & 0xffffffff));
        emitShiftLeft(reg, reg, 12);
        emitAdd(reg, reg, (int) ((lower >> 20) & 0xfff));
        emitShiftLeft(reg, reg, 12);
        emitAdd(reg, reg, (int) ((c << 44) >> 52));
        emitShiftLeft(reg, reg, 8);
        emitAdd(reg, reg, (int) (c & 0xff));
        return reg;
    }

    @Override
    public Register emitLoadLong(long c) {
        Register ret = newRegister();
        return emitLoadLong(ret, c);
    }

    private Register emitLoadInt(Register reg, int c) {
        emitLoadImmediate(reg, c);
        return reg;
    }

    @Override
    public Register emitLoadInt(int c) {
        Register ret = newRegister();
        return emitLoadInt(ret, c);
    }

    @Override
    public Register emitIntArg0() {
        return codeCache.getRegisterConfig()
            .getCallingConventionRegisters(HotSpotCallingConventionType.JavaCall, JavaKind.Int)
            .get(0);
    }

    @Override
    public Register emitIntArg1() {
        return codeCache.getRegisterConfig()
            .getCallingConventionRegisters(HotSpotCallingConventionType.JavaCall, JavaKind.Int)
            .get(1);
    }

    @Override
    public Register emitIntAdd(Register a, Register b) {
        emitAdd(a, a, b);
        return a;
    }

    @Override
    public void emitTrap(DebugInfo info) {
        // Dereference null pointer
        emitAdd(scratchRegister, RISCV64.x0, 0);
        recordImplicitException(info);
        emitLoadRegister(RISCV64.x0, RISCV64Kind.QWORD, scratchRegister, 0);
    }

    @Override
    public void emitIntRet(Register a) {
        emitMv(RISCV64.x10, a);
        emitMv(RISCV64.x2, RISCV64.x8);  // mv sp, x8
        emitLoadRegister(RISCV64.x8, RISCV64Kind.QWORD, RISCV64.x2, 0);  // ld x8 0(sp)
        emitLoadRegister(RISCV64.x1, RISCV64Kind.QWORD, RISCV64.x2, 8);  // ld x1 8(sp)
        emitAdd(RISCV64.x2, RISCV64.x2, 32);  // addi sp sp 32
        emitJalr(RISCV64.x0, RISCV64.x1, 0);  // ret
    }

    @Override
    public void emitFloatRet(Register a) {
        assert a == RISCV64.f10 : "Unimplemented move " + a;
        emitMv(RISCV64.x2, RISCV64.x8);  // mv sp, x8
        emitLoadRegister(RISCV64.x8, RISCV64Kind.QWORD, RISCV64.x2, 0);  // ld x8 0(sp)
        emitLoadRegister(RISCV64.x1, RISCV64Kind.QWORD, RISCV64.x2, 8);  // ld x1 8(sp)
        emitAdd(RISCV64.x2, RISCV64.x2, 32);  // addi sp sp 32
        emitJalr(RISCV64.x0, RISCV64.x1, 0);  // ret
    }

    @Override
    public void emitPointerRet(Register a) {
        emitIntRet(a);
    }

    @Override
    public StackSlot emitPointerToStack(Register a) {
        return emitLongToStack(a);
    }

    @Override
    public StackSlot emitNarrowPointerToStack(Register a) {
        return emitIntToStack(a);
    }

    @Override
    public Register emitUncompressPointer(Register compressed, long base, int shift) {
        if (shift > 0) {
            emitShiftLeft(compressed, compressed, shift);
        }

        if (base != 0) {
            emitLoadLong(scratchRegister, base);
            emitAdd(compressed, compressed, scratchRegister);
        }

        return compressed;
    }

    private StackSlot emitDoubleToStack(StackSlot slot, Register a) {
        emitStoreRegister(a, RISCV64Kind.DOUBLE, RISCV64.x2, slot.getOffset(frameSize) & 0xfff);
        return slot;
    }

    @Override
    public StackSlot emitDoubleToStack(Register a) {
        StackSlot ret = newStackSlot(RISCV64Kind.DOUBLE);
        return emitDoubleToStack(ret, a);
    }

    private StackSlot emitFloatToStack(StackSlot slot, Register a) {
        emitStoreRegister(a, RISCV64Kind.SINGLE, RISCV64.x2, slot.getOffset(frameSize) & 0xfff);
        return slot;
    }

    @Override
    public StackSlot emitFloatToStack(Register a) {
        StackSlot ret = newStackSlot(RISCV64Kind.SINGLE);
        return emitFloatToStack(ret, a);
    }

    private StackSlot emitIntToStack(StackSlot slot, Register a) {
        emitStoreRegister(a, RISCV64Kind.DWORD, RISCV64.x2, slot.getOffset(frameSize) & 0xfff);
        return slot;
    }

    @Override
    public StackSlot emitIntToStack(Register a) {
        StackSlot ret = newStackSlot(RISCV64Kind.DWORD);
        return emitIntToStack(ret, a);
    }

    private StackSlot emitLongToStack(StackSlot slot, Register a) {
        emitStoreRegister(a, RISCV64Kind.QWORD, RISCV64.x2, slot.getOffset(frameSize) & 0xfff);
        return slot;
    }

    @Override
    public StackSlot emitLongToStack(Register a) {
        StackSlot ret = newStackSlot(RISCV64Kind.QWORD);
        return emitLongToStack(ret, a);
    }

}
