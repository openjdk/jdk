/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2022, Arm Limited. All rights reserved.
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

import jdk.vm.ci.riscv64.RISCV64;
import jdk.vm.ci.riscv64.RISCV64Kind;
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

public class RISCV64TestAssembler extends TestAssembler {

    private static final Register scratchRegister = RISCV64.x28;
    private static final Register doubleScratch = RISCV64.f28;

    public RISCV64TestAssembler(CodeCacheProvider codeCache, TestHotSpotVMConfig config) {
        super(codeCache, config,
              16 /* initialFrameSize */, 16 /* stackAlignment */,
              RISCV64Kind.DWORD /* narrowOopKind */,
              /* registers */
              RISCV64.x0, RISCV64.x1, RISCV64.x2, RISCV64.x3,
              RISCV64.x4, RISCV64.x5, RISCV64.x6, RISCV64.x7);
    }

    private static int instructionImmediate(int imm, int rs1, int funct, int rd, int opcode) {
        return (imm << 20) | (rs1 << 15) | (funct << 12) | (rd << 7) | opcode;
    }

    private static int instructionRegister(int funct7, int rs2, int rs1, int funct3, int rd, int opcode) {
        return (funct7 << 25) | (rs2 << 20) | (rs1 << 15) | (funct3 << 12) | (rd << 7) | opcode;
    }

    private void emitNop() {
        code.emitInt(instructionImmediate(0, 0, 0b000, 0, 0b0010011));
    }

    private void emitAdd(Register Rd, Register Rn, Register Rm) {
        // ADD
        code.emitInt(instructionRegister(0b0000000, Rn.encoding, Rm.encoding, 0b000, Rd.encoding, 0b0110011));
    }

    private void emitAdd(Register Rd, Register Rn, int imm12) {
        // ADDI
        code.emitInt(instructionImmediate(imm12, Rn.encoding, 0b000, Rd.encoding, 0b0010011));
    }

    private void emitAddW(Register Rd, Register Rn, int imm12) {
        // ADDIW
        code.emitInt(instructionImmediate(imm12, Rn.encoding, 0b000, Rd.encoding, 0b0011011));
    }

    private void emitSub(Register Rd, Register Rn, int imm12) {
        // SUBI
        code.emitInt(instructionImmediate(-imm12, Rn.encoding, 0b000, Rd.encoding, 0b0010011));
    }

    private void emitSub(Register Rd, Register Rn, Register Rm) {
        // SUB
        code.emitInt(instructionRegister(0b0100000, Rn.encoding, Rm.encoding, 0b000, Rd.encoding, 0b0110011));
    }

    private void emitMv(Register Rd, Register Rn) {
        // MV
        code.emitInt(instructionRegister(0b0000000, Rn.encoding, 0, 0b000, Rd.encoding, 0b0110011));
    }

    private void emitShiftLeft(Register Rd, Register Rn, int shift) {
        // SLLI
        code.emitInt(instructionImmediate(shift, Rn.encoding, 0b001, Rd.encoding, 0b0010011));
    }

    private void emitLui(Register Rd, int imm20) {
        // LUI
        code.emitInt((imm20 << 12) | (Rd.encoding << 7) | 0b0110111);
    }

    private void emitAuipc(Register Rd, int imm20) {
        // AUIPC
        code.emitInt((imm20 << 12) | (Rd.encoding << 7) | 0b0010111);
    }

    private void emitLoadImmediate(Register Rd, int imm32) {
        emitLui(Rd, (imm32 >> 12) & 0xfffff);
        emitAddW(Rd, Rd, imm32 & 0xfff);
    }

    private void emitLoadRegister(Register Rt, RISCV64Kind kind, Register Rn, int offset) {
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
        code.emitInt((offset << 20) | (Rt.encoding << 15) | (size << 12) | (Rn.encoding << 7) | opc);
    }

    private void emitStoreRegister(Register Rt, RISCV64Kind kind, Register Rn, int offset) {
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
        code.emitInt(((offset >> 5) << 25) | (Rt.encoding << 20) | (Rn.encoding << 15) | (size << 12) | ((offset & 0x1f) << 7) | opc);
    }

    private void emitJalr(Register Rd, Register Rn, int imm) {
        code.emitInt(instructionImmediate(imm, Rn.encoding, 0b000, Rd.encoding, 0b1100111));
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
        if (size > -4096 && size < 0) {
            emitAdd(RISCV64.x2, RISCV64.x2, -size);
        } else if (size == 0) {
            // No-op
        } else if (size < 4096) {
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
        emitNop();
        emitGrowStack(32);
        emitStoreRegister(RISCV64.x8, RISCV64Kind.QWORD, RISCV64.x2, 32);
        emitMv(RISCV64.x8, RISCV64.x2);
        setDeoptRescueSlot(newStackSlot(RISCV64Kind.QWORD));
    }

    @Override
    public void emitEpilogue() {
        recordMark(config.MARKID_DEOPT_HANDLER_ENTRY);
        recordCall(new HotSpotForeignCallTarget(config.handleDeoptStub), 4*4, true, null);
        emitCall(0xdeaddeaddeadL);
    }

    @Override
    public void emitCallPrologue(CallingConvention cc, Object... prim) {
        emitGrowStack(cc.getStackSize());
        frameSize += cc.getStackSize();
        AllocatableValue[] args = cc.getArguments();
        for (int i = 0; i < args.length; i++) {
            emitLoad(args[i], prim[i]);
        }
    }

    @Override
    public void emitCallEpilogue(CallingConvention cc) {
        emitGrowStack(-cc.getStackSize());
        frameSize -= cc.getStackSize();
    }

    @Override
    public void emitCall(long addr) {
        emitLoadPointer48(scratchRegister, addr);
        emitJalr(scratchRegister, RISCV64.x1, 0);
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

    private void emitLoadPointer48(Register ret, long addr) {
        // 48-bit VA
        assert (addr >> 48) == 0 : "invalid pointer" + Long.toHexString(addr);
        emitLoadImmediate(ret, (int) ((addr >> 16) & 0xffffffff));
        emitShiftLeft(ret, ret, 12);
        emitAdd(ret, ret, (int) ((addr >> 4) & 0xfff));
        emitShiftLeft(ret, ret, 4);
        emitAdd(ret, ret, (int) (addr & 0xf));
    }

    private void emitLoadPointer32(Register ret, long addr) {
        emitLui(ret, (int) ((addr >> 12) & 0xfffff));
        emitAddW(ret, ret, (int) (addr & 0xfff));
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
        emitLoadRegister(ret, RISCV64Kind.QWORD, b, offset);
        return ret;
    }

    @Override
    public Register emitLoadNarrowPointer(DataSectionReference ref) {
        recordDataPatchInCode(ref);

        Register ret = newRegister();
        emitAuipc(ret, 0xdead >> 12);
        emitAdd(ret, ret, 0xdead & 0xfff);
        emitLoadRegister(ret, RISCV64Kind.DWORD, ret, 0);
        return ret;
    }

    @Override
    public Register emitLoadPointer(DataSectionReference ref) {
        recordDataPatchInCode(ref);

        Register ret = newRegister();
        emitAuipc(ret, 0xdead >> 12);
        emitAdd(ret, ret, 0xdead & 0xfff);
        emitLoadRegister(ret, RISCV64Kind.QWORD, ret, 0);
        return ret;
    }

    private Register emitLoadDouble(Register reg, double c) {
        DataSectionReference ref = new DataSectionReference();
        ref.setOffset(data.position());
        data.emitDouble(c);

        recordDataPatchInCode(ref);
        emitAuipc(scratchRegister, 0xdead >> 12);
        emitAdd(scratchRegister, scratchRegister, 0xdead & 0xfff);
        emitLoadRegister(scratchRegister, RISCV64Kind.QWORD, scratchRegister, 0);
        emitFmv(reg, RISCV64Kind.DOUBLE, scratchRegister);
        return reg;
    }

    private Register emitLoadFloat(Register reg, float c) {
        DataSectionReference ref = new DataSectionReference();
        ref.setOffset(data.position());
        data.emitFloat(c);

        recordDataPatchInCode(ref);
        emitAuipc(scratchRegister, 0xdead >> 12);
        emitAdd(scratchRegister, scratchRegister, 0xdead & 0xfff);
        emitLoadRegister(scratchRegister, RISCV64Kind.DWORD, scratchRegister, 0);
        emitFmv(reg, RISCV64Kind.SINGLE, scratchRegister);
        return reg;
    }

    @Override
    public Register emitLoadFloat(float c) {
        Register ret = RISCV64.f10;
        return emitLoadFloat(ret, c);
    }

    private Register emitLoadLong(Register reg, long c) {
        emitLoadImmediate(reg, (int) ((c >> 32) & 0xffffffff));
        emitShiftLeft(reg, reg, 12);
        emitAdd(reg, reg, (int) ((c >> 20) & 0xfff));
        emitShiftLeft(reg, reg, 12);
        emitAdd(reg, reg, (int) ((c >> 8) & 0xfff));
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
        emitMv(RISCV64.x2, RISCV64.x8);                                           // mov sp, x29
        emitLoadRegister(RISCV64.x2, RISCV64Kind.QWORD, RISCV64.x8, 32);   // ld x8 32(sp)
        emitLoadRegister(RISCV64.x2, RISCV64Kind.QWORD, RISCV64.x1, 48);   // ld x1 48(sp)
        emitJalr(RISCV64.x0, RISCV64.x1, 0);                                       // ret
    }

    @Override
    public void emitFloatRet(Register a) {
        assert a == RISCV64.f10 : "Unimplemented move " + a;
        emitMv(RISCV64.x2, RISCV64.x8);                                          // mov sp, x29
        emitLoadRegister(RISCV64.x2, RISCV64Kind.QWORD, RISCV64.x8, 32);  // ld x8 32(sp)
        emitLoadRegister(RISCV64.x2, RISCV64Kind.QWORD, RISCV64.x1, 48);  // ld x1 48(sp)
        emitJalr(RISCV64.x0, RISCV64.x1, 0);                                      // ret
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
        emitStoreRegister(a, RISCV64Kind.DOUBLE, RISCV64.x2, slot.getOffset(frameSize));
        return slot;
    }

    @Override
    public StackSlot emitDoubleToStack(Register a) {
        StackSlot ret = newStackSlot(RISCV64Kind.DOUBLE);
        return emitDoubleToStack(ret, a);
    }

    private StackSlot emitFloatToStack(StackSlot slot, Register a) {
        emitStoreRegister(a, RISCV64Kind.SINGLE, RISCV64.x2, slot.getOffset(frameSize));
        return slot;
    }

    @Override
    public StackSlot emitFloatToStack(Register a) {
        StackSlot ret = newStackSlot(RISCV64Kind.SINGLE);
        return emitFloatToStack(ret, a);
    }

    private StackSlot emitIntToStack(StackSlot slot, Register a) {
        emitStoreRegister(a, RISCV64Kind.DWORD, RISCV64.x2, slot.getOffset(frameSize));
        return slot;
    }

    @Override
    public StackSlot emitIntToStack(Register a) {
        StackSlot ret = newStackSlot(RISCV64Kind.DWORD);
        return emitIntToStack(ret, a);
    }

    private StackSlot emitLongToStack(StackSlot slot, Register a) {
        emitStoreRegister(a, RISCV64Kind.QWORD, RISCV64.x2, slot.getOffset(frameSize));
        return slot;
    }

    @Override
    public StackSlot emitLongToStack(Register a) {
        StackSlot ret = newStackSlot(RISCV64Kind.QWORD);
        return emitLongToStack(ret, a);
    }

}
