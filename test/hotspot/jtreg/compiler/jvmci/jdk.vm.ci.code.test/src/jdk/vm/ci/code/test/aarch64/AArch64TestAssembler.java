/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2023, Arm Limited. All rights reserved.
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

package jdk.vm.ci.code.test.aarch64;

import java.util.List;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.aarch64.AArch64Kind;
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

public class AArch64TestAssembler extends TestAssembler {

    private static final Register scratchRegister = AArch64.rscratch1;
    private static final Register scratchRegister2 = AArch64.rscratch2;
    private static final Register doubleScratch = AArch64.v9;

    /**
     * Condition Flags for branches. See C1.2.4
     */
    public enum ConditionFlag {
        // Integer | Floating-point meanings
        /** Equal | Equal. */
        EQ(0x0),

        /** Not Equal | Not equal or unordered. */
        NE(0x1),

        /** Unsigned Higher or Same | Greater than, equal or unordered. */
        HS(0x2),

        /** Unsigned lower | less than. */
        LO(0x3),

        /** Minus (negative) | less than. */
        MI(0x4),

        /** Plus (positive or zero) | greater than, equal or unordered. */
        PL(0x5),

        /** Overflow set | unordered. */
        VS(0x6),

        /** Overflow clear | ordered. */
        VC(0x7),

        /** Unsigned higher | greater than or unordered. */
        HI(0x8),

        /** Unsigned lower or same | less than or equal. */
        LS(0x9),

        /** Signed greater than or equal | greater than or equal. */
        GE(0xA),

        /** Signed less than | less than or unordered. */
        LT(0xB),

        /** Signed greater than | greater than. */
        GT(0xC),

        /** Signed less than or equal | less than, equal or unordered. */
        LE(0xD),

        /** Always | always. */
        AL(0xE),

        /** Always | always (identical to AL, just to have valid 0b1111 encoding). */
        NV(0xF);

        public final int encoding;

        ConditionFlag(int encoding) {
            this.encoding = encoding;
        }
    }

    public AArch64TestAssembler(CodeCacheProvider codeCache, TestHotSpotVMConfig config) {
        super(codeCache, config,
              16 /* initialFrameSize */, 16 /* stackAlignment */,
              AArch64Kind.DWORD /* narrowOopKind */,
              /* registers */
              AArch64.r0, AArch64.r1, AArch64.r2, AArch64.r3,
              AArch64.r4, AArch64.r5, AArch64.r6, AArch64.r7);
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

    private void emitNop() {
        code.emitInt(0xd503201f);
    }

    private void emitAdd(Register Rd, Register Rn, Register Rm) {
        // ADD (shifted register)
        code.emitInt(f(0b10001011000, 31, 21)
                     | f(Rm, 20, 16)
                     | f(0, 15, 10)
                     | f(Rn, 9, 5)
                     | f(Rd, 4, 0));
    }

    private void emitAdd(Register Rd, Register Rn, int imm12) {
        // ADD (immediate)
        code.emitInt(f(0b1001000100, 31, 22)
                     | f(imm12, 21, 10)
                     | f(Rn, 9, 5)
                     | f(Rd, 4, 0));
    }

    private void emitSub(Register Rd, Register Rn, int imm12) {
        // SUB (immediate)
        code.emitInt(f(0b1101000100, 31, 22)
                     | f(imm12, 21, 10)
                     | f(Rn, 9, 5)
                     | f(Rd, 4, 0));
    }

    private void emitSub(Register Rd, Register Rn, Register Rm) {
        // SUB (extended register)
        code.emitInt(f(0b11001011001, 31, 21)
                     | f(Rm, 20, 16)
                     | f(0b011000, 15, 10)
                     | f(Rn, 9, 5)
                     | f(Rd, 4, 0));
    }

    private void emitMov(Register Rd, Register Rm) {
        // MOV (register)
        code.emitInt(f(0b10101010000, 31, 21)
                     | f(Rm, 20, 16)
                     | f(0, 15, 10)
                     | f(AArch64.zr, 9, 5)
                     | f(Rd, 4, 0));
    }

    private void emitMovz(Register Rd, int imm16, int shift) {
        // MOVZ
        int hw = 0;
        switch (shift) {
            case 0:  hw = 0; break;
            case 16: hw = 1; break;
            case 32: hw = 2; break;
            case 48: hw = 3; break;
            default: throw new IllegalArgumentException();
        }
        code.emitInt(f(0b110100101, 31, 23)
                     | f(hw, 22, 21)
                     | f(imm16, 20, 5)
                     | f(Rd, 4, 0));
    }

    private void emitMovk(Register Rd, int imm16, int shift) {
        // MOVK
        int hw = 0;
        switch (shift) {
            case 0:  hw = 0; break;
            case 16: hw = 1; break;
            case 32: hw = 2; break;
            case 48: hw = 3; break;
            default: throw new IllegalArgumentException();
        }
        code.emitInt(f(0b111100101, 31, 23)
                     | f(hw, 22, 21)
                     | f(imm16, 20, 5)
                     | f(Rd, 4, 0));
    }

    private void emitShiftLeft(Register Rd, Register Rn, int shift) {
        // LSL (immediate)
        code.emitInt(f(0b1101001101, 31, 22)
                     | f(-shift & 0b111111, 21, 16)
                     | f(63 - shift, 15, 10)
                     | f(Rn, 9, 5)
                     | f(Rd, 4, 0));
    }

    private void emitLoadRegister(Register Rt, AArch64Kind kind, int offset) {
        // LDR (literal)
        int opc = 0;
        switch (kind) {
            case DWORD: opc = 0; break;
            case QWORD: opc = 1; break;
            default: throw new IllegalArgumentException();
        }
        code.emitInt(f(opc, 31, 30)
                     | f(0b011000, 29, 24)
                     | f(offset, 23, 5)
                     | f(Rt, 4, 0));
    }

    private void emitLoadRegister(Register Rt, AArch64Kind kind, Register Rn, int offset) {
        // LDR (immediate)
        assert offset >= 0;
        int size = 0;
        switch (kind) {
            case DWORD: size = 0b10; break;
            case QWORD: size = 0b11; break;
            default: throw new IllegalArgumentException();
        }
        code.emitInt(f(size, 31, 30)
                     | f(0b11100101, 29, 22)
                     | f(offset >> size, 21, 10)
                     | f(Rn, 9, 5)
                     | f(Rt, 4, 0));
    }

    private void emitStoreRegister(Register Rt, AArch64Kind kind, Register Rn, int offset) {
        // STR (immediate)
        assert offset >= 0;
        int size = 0, fp = 0;
        switch (kind) {
            case DWORD: size = 0b10; fp = 0; break;
            case QWORD: size = 0b11; fp = 0; break;
            case SINGLE: size = 0b10; fp = 1; break;
            case DOUBLE: size = 0b11; fp = 1; break;
            default: throw new IllegalArgumentException();
        }
        code.emitInt(f(size, 31, 30)
                     | f(0b111, 29, 27)
                     | f(fp, 26, 26)
                     | f(0b0100, 25, 22)
                     | f(offset >> size, 21, 10)
                     | f(Rn, 9, 5)
                     | f(Rt, 4, 0));
    }

    private void emitBlr(Register Rn) {
        // BLR
        code.emitInt(f(0b1101011000111111000000, 31, 10)
                     | f(Rn, 9, 5)
                     | f(0, 4, 0));
    }

    /**
     * C6.2.25 Branch conditionally.
     *
     * @param condition may not be null.
     * @param imm21 Signed 21-bit offset, has to be 4-byte aligned.
     */
    protected void emitBranch(ConditionFlag condition, int imm21) {
        // B.cond
        check(isSignedNbit(21, imm21) && (imm21 & 0b11) == 0,
              "0x%x must be a 21-bit signed number and 4-byte aligned", imm21);
        int imm19 = (imm21 & getNbitNumberInt(21)) >> 2;
        code.emitInt(f(0b001010100, 31, 24)
                     | f(imm19, 23, 4)
                     | f(condition.encoding, 3, 0));
    }

    private void emitFmov(Register Rd, AArch64Kind kind, Register Rn) {
        // FMOV (general)
        int ftype = 0, sf = 0;
        switch (kind) {
            case SINGLE: sf = 0; ftype = 0b00; break;
            case DOUBLE: sf = 1; ftype = 0b01; break;
            default: throw new IllegalArgumentException();
        }
        code.emitInt(f(sf, 31, 31)
                     | f(0b0011110, 30, 24)
                     | f(ftype, 23, 22)
                     | f(0b100111, 21, 16)
                     | f(0, 15, 10)
                     | f(Rn, 9, 5)
                     | f(Rd, 4, 0));
    }

    @Override
    public void emitGrowStack(int size) {
        assert size % 16 == 0;
        if (size > -4096 && size < 0) {
            emitAdd(AArch64.sp, AArch64.sp, -size);
        } else if (size == 0) {
            // No-op
        } else if (size < 4096) {
            emitSub(AArch64.sp, AArch64.sp, size);
        } else if (size < 65535) {
            emitMovz(scratchRegister, size & 0xffff, 0);
            emitMovk(scratchRegister, (size >> 16) & 0xffff, 16);
            emitSub(AArch64.sp, AArch64.sp, scratchRegister);
        } else {
            throw new IllegalArgumentException();
        }
    }

    @Override
    public void emitPrologue() {
        // Must be patchable by NativeJump::patch_verified_entry
        emitNop();
        if (config.ropProtection) {
            code.emitInt(0xf94003df);  // ldr xzr, [x30]
            code.emitInt(0xd503231f);  // paciaz
        }
        code.emitInt(0xa9bf7bfd);      // stp x29, x30, [sp, #-16]!
        code.emitInt(0x910003fd);      // mov x29, sp

        emitNMethodEntryBarrier();

        setDeoptRescueSlot(newStackSlot(AArch64Kind.QWORD));
    }

    private void emitNMethodEntryBarrier() {
        recordMark(config.MARKID_ENTRY_BARRIER_PATCH);
        DataSectionReference ref = emitDataItem(0);
        emitLoadPointer(scratchRegister, AArch64Kind.DWORD, ref);
        if (config.nmethodEntryBarrierConcurrentPatch) {
            code.emitInt(0xd50339bf); // dmb ishld
        }
        Register thread = AArch64.r28;
        emitLoadPointer(scratchRegister2, AArch64Kind.DWORD, thread, config.threadDisarmedOffset);
        code.emitInt(0x6b09011f);             // cmp w8, w9
        emitBranch(ConditionFlag.EQ, 8);      // jump over slow path, runtime call
        emitCall(config.nmethodEntryBarrier);
    }

    @Override
    public void emitEpilogue() {
        recordMark(config.MARKID_DEOPT_HANDLER_ENTRY);
        recordCall(new HotSpotForeignCallTarget(config.handleDeoptStub), 4*4, true, null);
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
        emitLoadPointer48(scratchRegister, addr);
        emitBlr(scratchRegister);
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

    private void emitLoadPointer32(Register ret, long addr) {
        long a = addr;
        long al = a;
        a >>= 16;
        long ah = a;
        a >>= 16;
        assert a == 0 : "invalid pointer" + Long.toHexString(addr);
        // Set upper 16 bits first. See MacroAssembler::patch_oop().
        emitMovz(ret, ((int)ah & 0xffff), 16);
        emitMovk(ret, ((int)al & 0xffff), 0);
    }

    private void emitLoadPointer48(Register ret, long addr) {
        // 48-bit VA
        long a = addr;
        emitMovz(ret, ((int)a & 0xffff), 0);
        a >>= 16;
        emitMovk(ret, ((int)a & 0xffff), 16);
        a >>= 16;
        emitMovk(ret, ((int)a & 0xffff), 32);
        a >>= 16;
        assert a == 0 : "invalid pointer" + Long.toHexString(addr);
    }

    @Override
    public Register emitLoadPointer(HotSpotConstant c) {
        recordDataPatchInCode(new ConstantReference((VMConstant) c));

        Register ret = newRegister();
        if (c.isCompressed()) {
            emitLoadPointer32(ret, 0xdeaddeadL);
        } else {
            emitLoadPointer48(ret, 0xdeaddeaddeadL);
        }
        return ret;
    }

    @Override
    public Register emitLoadPointer(Register b, int offset) {
        return emitLoadPointer(newRegister(), AArch64Kind.QWORD, b, offset);
    }

    public Register emitLoadPointer(Register ret, AArch64Kind kind, Register b, int offset) {
        emitLoadRegister(ret, kind, b, offset);
        return ret;
    }

    @Override
    public Register emitLoadNarrowPointer(DataSectionReference ref) {
        recordDataPatchInCode(ref);

        Register ret = newRegister();
        emitLoadRegister(ret, AArch64Kind.DWORD, 0xdead);
        return ret;
    }

    @Override
    public Register emitLoadPointer(DataSectionReference ref) {
        return emitLoadPointer(newRegister(), AArch64Kind.QWORD, ref);
    }

    public Register emitLoadPointer(Register ret, AArch64Kind kind, DataSectionReference ref) {
        recordDataPatchInCode(ref);

        emitLoadRegister(ret, kind, 0xdead);
        return ret;
    }

    private Register emitLoadDouble(Register reg, double c) {
        DataSectionReference ref = new DataSectionReference();
        ref.setOffset(data.position());
        data.emitDouble(c);

        recordDataPatchInCode(ref);
        emitLoadRegister(scratchRegister, AArch64Kind.QWORD, 0xdead);
        emitFmov(reg, AArch64Kind.DOUBLE, scratchRegister);
        return reg;
    }

    private Register emitLoadFloat(Register reg, float c) {
        DataSectionReference ref = new DataSectionReference();
        ref.setOffset(data.position());
        data.emitFloat(c);

        recordDataPatchInCode(ref);
        emitLoadRegister(scratchRegister, AArch64Kind.DWORD, 0xdead);
        emitFmov(reg, AArch64Kind.SINGLE, scratchRegister);
        return reg;
    }

    @Override
    public Register emitLoadFloat(float c) {
        Register ret = AArch64.v0;
        return emitLoadFloat(ret, c);
    }

    private Register emitLoadLong(Register reg, long c) {
        emitMovz(reg, (int)(c & 0xffff), 0);
        emitMovk(reg, (int)((c >> 16) & 0xffff), 16);
        emitMovk(reg, (int)((c >> 32) & 0xffff), 32);
        emitMovk(reg, (int)((c >> 48) & 0xffff), 48);
        return reg;
    }

    @Override
    public Register emitLoadLong(long c) {
        Register ret = newRegister();
        return emitLoadLong(ret, c);
    }

    private Register emitLoadInt(Register reg, int c) {
        emitMovz(reg, (int)(c & 0xffff), 0);
        emitMovk(reg, (int)((c >> 16) & 0xffff), 16);
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
        emitMovz(scratchRegister, 0, 0);
        recordImplicitException(info);
        emitLoadRegister(AArch64.zr, AArch64Kind.QWORD, scratchRegister, 0);
    }

    @Override
    public void emitIntRet(Register a) {
        emitMov(AArch64.r0, a);
        code.emitInt(0x910003bf);      // mov sp, x29
        code.emitInt(0xa8c17bfd);      // ldp x29, x30, [sp], #16
        if (config.ropProtection) {
            code.emitInt(0xd503239f);  // autiaz
            code.emitInt(0xf94003df);  // ldr xzr, [x30]
        }
        code.emitInt(0xd65f03c0);      // ret
    }

    @Override
    public void emitFloatRet(Register a) {
        assert a == AArch64.v0 : "Unimplemented move " + a;
        code.emitInt(0x910003bf);      // mov sp, x29
        code.emitInt(0xa8c17bfd);      // ldp x29, x30, [sp], #16
        if (config.ropProtection) {
            code.emitInt(0xd503239f);  // autiaz
            code.emitInt(0xf94003df);  // ldr xzr, [x30]
        }
        code.emitInt(0xd65f03c0);      // ret
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
        emitStoreRegister(a, AArch64Kind.DOUBLE, AArch64.sp, slot.getOffset(frameSize));
        return slot;
    }

    @Override
    public StackSlot emitDoubleToStack(Register a) {
        StackSlot ret = newStackSlot(AArch64Kind.DOUBLE);
        return emitDoubleToStack(ret, a);
    }

    private StackSlot emitFloatToStack(StackSlot slot, Register a) {
        emitStoreRegister(a, AArch64Kind.SINGLE, AArch64.sp, slot.getOffset(frameSize));
        return slot;
    }

    @Override
    public StackSlot emitFloatToStack(Register a) {
        StackSlot ret = newStackSlot(AArch64Kind.SINGLE);
        return emitFloatToStack(ret, a);
    }

    private StackSlot emitIntToStack(StackSlot slot, Register a) {
        emitStoreRegister(a, AArch64Kind.DWORD, AArch64.sp, slot.getOffset(frameSize));
        return slot;
    }

    @Override
    public StackSlot emitIntToStack(Register a) {
        StackSlot ret = newStackSlot(AArch64Kind.DWORD);
        return emitIntToStack(ret, a);
    }

    private StackSlot emitLongToStack(StackSlot slot, Register a) {
        emitStoreRegister(a, AArch64Kind.QWORD, AArch64.sp, slot.getOffset(frameSize));
        return slot;
    }

    @Override
    public StackSlot emitLongToStack(Register a) {
        StackSlot ret = newStackSlot(AArch64Kind.QWORD);
        return emitLongToStack(ret, a);
    }

}
