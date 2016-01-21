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

package compiler.jvmci.code.sparc;

import jdk.vm.ci.code.CallingConvention.Type;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.DebugInfo;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.site.ConstantReference;
import jdk.vm.ci.code.site.DataSectionReference;
import jdk.vm.ci.hotspot.HotSpotCompiledCode;
import jdk.vm.ci.hotspot.HotSpotConstant;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.LIRKind;
import jdk.vm.ci.meta.VMConstant;
import jdk.vm.ci.sparc.SPARC;
import jdk.vm.ci.sparc.SPARCKind;

import compiler.jvmci.code.TestAssembler;

public class SPARCTestAssembler extends TestAssembler {

    private static final int MASK13 = (1 << 13) - 1;

    public SPARCTestAssembler(CodeCacheProvider codeCache) {
        super(codeCache, 0, 16, SPARCKind.WORD, SPARC.l0, SPARC.l1, SPARC.l2, SPARC.l3, SPARC.l4, SPARC.l5, SPARC.l6, SPARC.l7);
    }

    private void emitOp2(Register rd, int op2, int imm22) {
        code.emitInt((0b00 << 30) | (rd.encoding << 25) | (op2 << 22) | imm22);
    }

    private void emitOp3(int op, Register rd, int op3, Register rs1, Register rs2) {
        code.emitInt((op << 30) | (rd.encoding << 25) | (op3 << 19) | (rs1.encoding << 14) | rs2.encoding);
    }

    private void emitOp3(int op, Register rd, int op3, Register rs1, int simm13) {
        code.emitInt((op << 30) | (rd.encoding << 25) | (op3 << 19) | (rs1.encoding << 14) | (1 << 13) | (simm13 & MASK13));
    }

    private void emitNop() {
        code.emitInt(1 << 24);
    }

    @Override
    public void emitPrologue() {
        emitOp3(0b10, SPARC.sp, 0b111100, SPARC.sp, -SPARC.REGISTER_SAFE_AREA_SIZE); // SAVE sp, -128, sp
    }

    @Override
    public HotSpotCompiledCode finish(HotSpotResolvedJavaMethod method) {
        frameSize += SPARC.REGISTER_SAFE_AREA_SIZE;
        return super.finish(method);
    }

    @Override
    public void emitGrowStack(int size) {
        emitOp3(0b10, SPARC.sp, 0b000100, SPARC.sp, size); // SUB sp, size, sp
    }

    @Override
    public Register emitIntArg0() {
        return codeCache.getRegisterConfig().getCallingConventionRegisters(Type.JavaCallee, JavaKind.Int)[0];
    }

    @Override
    public Register emitIntArg1() {
        return codeCache.getRegisterConfig().getCallingConventionRegisters(Type.JavaCallee, JavaKind.Int)[1];
    }

    @Override
    public Register emitLoadInt(int c) {
        Register ret = newRegister();
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
        if ((c & 0xFFFFFFFF) == c) {
            return emitLoadInt((int) c);
        } else {
            DataSectionReference ref = new DataSectionReference();
            ref.setOffset(data.position());
            data.emitLong(c);
            return emitLoadPointer(ref);
        }
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
        DataSectionReference ref = new DataSectionReference();
        ref.setOffset(data.position());
        data.emitFloat(c);

        Register ptr = newRegister();
        recordDataPatchInCode(ref);
        emitPatchableSethi(ptr, true);
        emitOp3(0b11, SPARC.f0, 0b100000, ptr, 0); // LDF [ptr+0], f0
        return SPARC.f0;
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
        recordDataPatchInCode(ref);
        emitPatchableSethi(ret, true);
        emitOp3(0b11, ret, 0b001011, ret, 0); // LDX [ret+0], ret
        return ret;
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
        StackSlot ret = newStackSlot(LIRKind.value(SPARCKind.WORD));
        emitOp3(0b11, a, 0b000100, SPARC.fp, ret.getRawOffset() + SPARC.STACK_BIAS); // STW a, [fp+offset]
        return ret;
    }

    @Override
    public StackSlot emitLongToStack(Register a) {
        StackSlot ret = newStackSlot(LIRKind.value(SPARCKind.XWORD));
        emitOp3(0b11, a, 0b001110, SPARC.fp, ret.getRawOffset() + SPARC.STACK_BIAS); // STX a, [fp+offset]
        return ret;
    }

    @Override
    public StackSlot emitFloatToStack(Register a) {
        StackSlot ret = newStackSlot(LIRKind.value(SPARCKind.SINGLE));
        emitOp3(0b11, a, 0b100100, SPARC.fp, ret.getRawOffset() + SPARC.STACK_BIAS); // STF a, [fp+offset]
        return ret;
    }

    @Override
    public StackSlot emitPointerToStack(Register a) {
        StackSlot ret = newStackSlot(LIRKind.reference(SPARCKind.XWORD));
        emitOp3(0b11, a, 0b001110, SPARC.fp, ret.getRawOffset() + SPARC.STACK_BIAS); // STX a, [fp+offset]
        return ret;
    }

    @Override
    public StackSlot emitNarrowPointerToStack(Register a) {
        StackSlot ret = newStackSlot(LIRKind.reference(SPARCKind.WORD));
        emitOp3(0b11, a, 0b000100, SPARC.fp, ret.getRawOffset() + SPARC.STACK_BIAS); // STW a, [fp+offset]
        return ret;
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
}
