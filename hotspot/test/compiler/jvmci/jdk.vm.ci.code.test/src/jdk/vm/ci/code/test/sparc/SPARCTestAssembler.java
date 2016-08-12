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

package jdk.vm.ci.code.test.sparc;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.DebugInfo;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.Register.RegisterCategory;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.site.ConstantReference;
import jdk.vm.ci.code.site.DataSectionReference;
import jdk.vm.ci.code.test.TestAssembler;
import jdk.vm.ci.code.test.TestHotSpotVMConfig;
import jdk.vm.ci.hotspot.HotSpotCallingConventionType;
import jdk.vm.ci.hotspot.HotSpotCompiledCode;
import jdk.vm.ci.hotspot.HotSpotConstant;
import jdk.vm.ci.hotspot.HotSpotForeignCallTarget;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.VMConstant;
import jdk.vm.ci.sparc.SPARC;
import jdk.vm.ci.sparc.SPARCKind;

public class SPARCTestAssembler extends TestAssembler {

    private static final int MASK13 = (1 << 13) - 1;
    private static final Register scratchRegister = SPARC.g5;
    private static final Register floatScratch = SPARC.f30;
    private static final Register doubleScratch = SPARC.d62;

    public SPARCTestAssembler(CodeCacheProvider codeCache, TestHotSpotVMConfig config) {
        super(codeCache, config, 0, 16, SPARCKind.WORD, SPARC.l0, SPARC.l1, SPARC.l2, SPARC.l3, SPARC.l4, SPARC.l5, SPARC.l6, SPARC.l7);
    }

    private void emitOp2(Register rd, int op2, int imm22) {
        assert isSimm(imm22, 22);
        code.emitInt((0b00 << 30) | (rd.encoding << 25) | (op2 << 22) | imm22);
    }

    private void emitOp3(int op, Register rd, int op3, Register rs1, Register rs2) {
        code.emitInt((op << 30) | (rd.encoding << 25) | (op3 << 19) | (rs1.encoding << 14) | rs2.encoding);
    }

    private void emitOp3(int op, Register rd, int op3, Register rs1, int simm13) {
        assert isSimm(simm13, 13);
        code.emitInt((op << 30) | (rd.encoding << 25) | (op3 << 19) | (rs1.encoding << 14) | (1 << 13) | (simm13 & MASK13));
    }

    private void emitNop() {
        code.emitInt(1 << 24);
    }

    /**
     * Minimum value for signed immediate ranges.
     */
    public static long minSimm(long nbits) {
        return -(1L << (nbits - 1));
    }

    /**
     * Maximum value for signed immediate ranges.
     */
    public static long maxSimm(long nbits) {
        return (1L << (nbits - 1)) - 1;
    }

    /**
     * Test if imm is within signed immediate range for nbits.
     */
    public static boolean isSimm(long imm, int nbits) {
        return minSimm(nbits) <= imm && imm <= maxSimm(nbits);
    }

    @Override
    public void emitPrologue() {
        // SAVE sp, -128, sp
        emitOp3(0b10, SPARC.sp, 0b111100, SPARC.sp, -SPARC.REGISTER_SAFE_AREA_SIZE);
        setDeoptRescueSlot(newStackSlot(SPARCKind.XWORD));
    }

    @Override
    public void emitEpilogue() {
        recordMark(config.MARKID_DEOPT_HANDLER_ENTRY);
        recordCall(new HotSpotForeignCallTarget(config.handleDeoptStub), 4, true, null);
        code.emitInt(1 << 30); // CALL
    }

    @Override
    public HotSpotCompiledCode finish(HotSpotResolvedJavaMethod method) {
        frameSize += SPARC.REGISTER_SAFE_AREA_SIZE;
        return super.finish(method);
    }

    @Override
    public void emitGrowStack(int size) {
        frameSize += size;
        if (isSimm(size, 13)) {
            emitOp3(0b10, SPARC.sp, 0b000100, SPARC.sp, size); // SUB sp, size, sp
        } else {
            Register r = emitLoadInt(size);
            emitOp3(0b10, SPARC.sp, 0b000100, SPARC.sp, r); // SUB sp, size, sp
        }
    }

    @Override
    public Register emitIntArg0() {
        return codeCache.getRegisterConfig().getCallingConventionRegisters(HotSpotCallingConventionType.JavaCallee, JavaKind.Int).get(0);
    }

    @Override
    public Register emitIntArg1() {
        return codeCache.getRegisterConfig().getCallingConventionRegisters(HotSpotCallingConventionType.JavaCallee, JavaKind.Int).get(1);
    }

    @Override
    public Register emitLoadInt(int c) {
        Register ret = newRegister();
        loadIntToRegister(c, ret);
        return ret;
    }

    private Register loadIntToRegister(int c, Register ret) {
        int hi = c >>> 10;
        int lo = c & ((1 << 10) - 1);
        if (hi == 0) {
            emitOp3(0b10, ret, 0b000010, SPARC.g0, lo); // OR g0, lo, ret
        } else {
            emitOp2(ret, 0b100, hi);                    // SETHI hi, ret
            if (lo != 0) {
                emitOp3(0b10, ret, 0b000010, ret, lo);  // OR ret, lo, ret
            }
        }
        return ret;
    }

    @Override
    public Register emitLoadLong(long c) {
        Register ret = newRegister();
        emitLoadLongToRegister(c, ret);
        return ret;
    }

    private void loadLongToRegister(long c, Register ret) {
        DataSectionReference ref = new DataSectionReference();
        data.align(8);
        ref.setOffset(data.position());
        data.emitLong(c);
        emitLoadPointerToRegister(ref, ret);
    }

    public Register emitLoadLongToRegister(long c, Register r) {
        if ((c & 0xFFFF_FFFFL) == c) {
            loadIntToRegister((int) c, r);
        } else {
            loadLongToRegister(c, r);
        }
        return r;
    }

    private void emitPatchableSethi(Register ret, boolean wide) {
        int startPos = code.position();
        emitOp2(ret, 0b100, 0);              // SETHI 0, ret
        if (wide) {
            // pad for later patching
            while (code.position() < (startPos + 28)) {
                emitNop();
            }
        }
    }

    @Override
    public Register emitLoadFloat(float c) {
        return emitLoadFloat(SPARC.f0, c);
    }

    public Register emitLoadFloat(Register reg, float c) {
        return emitLoadFloat(reg, c, newRegister());
    }

    public Register emitLoadFloat(Register reg, float c, Register scratch) {
        DataSectionReference ref = new DataSectionReference();
        data.align(4);
        ref.setOffset(data.position());
        data.emitFloat(c);

        recordDataPatchInCode(ref);
        emitPatchableSethi(scratch, true);
        emitOp3(0b11, reg, 0b100000, scratch, 0); // LDF [scratch+0], f0
        return reg;
    }

    public Register emitLoadDouble(Register reg, double c) {
        return emitLoadDouble(reg, c, newRegister());
    }

    public Register emitLoadDouble(Register reg, double c, Register scratch) {
        DataSectionReference ref = new DataSectionReference();
        data.align(8);
        ref.setOffset(data.position());
        data.emitDouble(c);

        recordDataPatchInCode(ref);
        emitPatchableSethi(scratch, true);
        emitOp3(0b11, reg, 0b100011, scratch, 0); // LDDF [ptr+0], f0
        return reg;
    }

    @Override
    public Register emitLoadPointer(HotSpotConstant c) {
        Register ret = newRegister();
        recordDataPatchInCode(new ConstantReference((VMConstant) c));

        emitPatchableSethi(ret, !c.isCompressed());
        emitOp3(0b10, ret, 0b000010, ret, 0); // OR ret, 0, ret

        return ret;
    }

    @Override
    public Register emitLoadPointer(DataSectionReference ref) {
        Register ret = newRegister();
        emitLoadPointerToRegister(ref, ret);
        return ret;
    }

    private void emitLoadPointerToRegister(DataSectionReference ref, Register ret) {
        recordDataPatchInCode(ref);
        emitPatchableSethi(ret, true);
        emitOp3(0b11, ret, 0b001011, ret, 0); // LDX [ret+0], ret
    }

    @Override
    public Register emitLoadNarrowPointer(DataSectionReference ref) {
        Register ret = newRegister();
        recordDataPatchInCode(ref);
        emitPatchableSethi(ret, true);
        emitOp3(0b11, ret, 0b000000, ret, 0); // LDUW [ret+0], ret
        return ret;
    }

    @Override
    public Register emitLoadPointer(Register b, int offset) {
        Register ret = newRegister();
        emitOp3(0b11, ret, 0b001011, b, offset); // LDX [b+offset], ret
        return ret;
    }

    @Override
    public StackSlot emitIntToStack(Register a) {
        StackSlot ret = newStackSlot(SPARCKind.WORD);
        intToStack(a, ret);
        return ret;
    }

    public void intToStack(Register a, StackSlot ret) {
        // STW a, [(s|f)p+offset]
        emitStore(0b000100, a, ret);
    }

    @Override
    public StackSlot emitLongToStack(Register a) {
        StackSlot ret = newStackSlot(SPARCKind.XWORD);
        longToStack(a, ret);
        return ret;
    }

    public void longToStack(Register a, StackSlot ret) {
        // STX a, [(f|s)p+offset]
        emitStore(0b001110, a, ret);
    }

    @Override
    public StackSlot emitFloatToStack(Register a) {
        StackSlot ret = newStackSlot(SPARCKind.SINGLE);
        floatToStack(a, ret);
        return ret;
    }

    public void floatToStack(Register a, StackSlot ret) {
        // STF a, [fp+offset]
        emitStore(0b100100, a, ret);
    }

    @Override
    public StackSlot emitDoubleToStack(Register a) {
        StackSlot ret = newStackSlot(SPARCKind.DOUBLE);
        return doubleToStack(a, ret);
    }

    public StackSlot doubleToStack(Register a, StackSlot ret) {
        // STD a, [(s|f)p+offset]
        emitStore(0b100111, a, ret);
        return ret;
    }

    @Override
    public StackSlot emitPointerToStack(Register a) {
        StackSlot ret = newStackSlot(SPARCKind.XWORD);
        // STX a, [fp+offset]
        emitStore(0b001110, a, ret);
        return ret;
    }

    @Override
    public StackSlot emitNarrowPointerToStack(Register a) {
        StackSlot ret = newStackSlot(SPARCKind.WORD);
        // STW a, [fp+offset]
        emitStore(0b000100, a, ret);
        return ret;
    }

    private void emitStore(int op3, Register a, StackSlot ret) {
        Register base;
        if (ret.getRawOffset() < 0) {
            base = SPARC.fp;
        } else {
            base = SPARC.sp;
        }
        int offset = ret.getRawOffset() + SPARC.STACK_BIAS;
        if (isSimm(offset, 13)) {
            // op3 a, [sp+offset]
            emitOp3(0b11, a, op3, base, offset);
        } else {
            assert a != SPARC.g3;
            Register r = SPARC.g3;
            loadLongToRegister(offset, r);
            // op3 a, [sp+g3]
            emitOp3(0b11, a, op3, base, r);
        }
    }

    @Override
    public Register emitUncompressPointer(Register compressed, long base, int shift) {
        Register ret;
        if (shift > 0) {
            ret = newRegister();
            emitOp3(0b10, ret, 0b100101, compressed, shift); // SLL compressed, shift, ret
        } else {
            ret = compressed;
        }
        if (base == 0) {
            return ret;
        } else {
            Register b = emitLoadLong(base);
            emitOp3(0b10, b, 0b00000, ret, b); // ADD b, ret, b
            return b;
        }
    }

    @Override
    public Register emitIntAdd(Register a, Register b) {
        Register ret = newRegister();
        emitOp3(0b10, ret, 0b00000, a, b); // ADD a, b, ret
        return ret;
    }

    private void emitMove(Register to, Register from) {
        if (to != from) {
            emitOp3(0b10, to, 0b000010, from, SPARC.g0); // OR from, g0, to
        }
    }

    @Override
    public void emitIntRet(Register a) {
        emitPointerRet(a);
    }

    @Override
    public void emitFloatRet(Register a) {
        assert a == SPARC.f0 : "Unimplemented";
        emitOp3(0b10, SPARC.g0, 0b111000, SPARC.i7, 8);        // JMPL [i7+8], g0
        emitOp3(0b10, SPARC.g0, 0b111101, SPARC.g0, SPARC.g0); // RESTORE g0, g0, g0
    }

    @Override
    public void emitPointerRet(Register a) {
        emitMove(SPARC.i0, a);
        emitOp3(0b10, SPARC.g0, 0b111000, SPARC.i7, 8);        // JMPL [i7+8], g0
        emitOp3(0b10, SPARC.g0, 0b111101, SPARC.g0, SPARC.g0); // RESTORE g0, g0, g0
    }

    @Override
    public void emitTrap(DebugInfo info) {
        recordImplicitException(info);
        emitOp3(0b11, SPARC.g0, 0b001011, SPARC.g0, 0); // LDX [g0+0], g0
    }

    @Override
    public DataSectionReference emitDataItem(HotSpotConstant c) {
        if (c.isCompressed()) {
            data.align(4);
        } else {
            data.align(8);
        }
        return super.emitDataItem(c);
    }

    @Override
    public void emitCall(long addr) {
        Register dst = emitLoadLong(addr);
        emitOp3(0b10, SPARC.o7, 0b111000, dst, 0);        // JMPL [dst+0], o7
        emitNop();
    }

    @Override
    public void emitLoad(AllocatableValue av, Object prim) {
        if (av instanceof RegisterValue) {
            Register reg = ((RegisterValue) av).getRegister();
            RegisterCategory cat = reg.getRegisterCategory();
            if (cat.equals(SPARC.FPUs)) {
                emitLoadFloat(reg, (Float) prim, scratchRegister);
            } else if (cat.equals(SPARC.FPUd)) {
                emitLoadDouble(reg, (Double) prim, scratchRegister);
            } else if (prim instanceof Integer) {
                loadIntToRegister((Integer) prim, reg);
            } else if (prim instanceof Long) {
                loadLongToRegister((Long) prim, reg);
            }
        } else if (av instanceof StackSlot) {
            StackSlot slot = (StackSlot) av;
            if (prim instanceof Float) {
                floatToStack(emitLoadFloat(floatScratch, (Float) prim, scratchRegister), slot);
            } else if (prim instanceof Double) {
                doubleToStack(emitLoadDouble(doubleScratch, (Double) prim, scratchRegister), slot);
            } else if (prim instanceof Integer) {
                intToStack(loadIntToRegister((Integer) prim, scratchRegister), slot);
            } else if (prim instanceof Long) {
                longToStack(emitLoadLongToRegister((Long) prim, scratchRegister), slot);
            }
        } else {
            throw new IllegalArgumentException("Unknown value " + av);
        }
    }

    @Override
    public void emitCallEpilogue(CallingConvention cc) {
        // Nothing to do here.
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

}
