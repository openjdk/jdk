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

package compiler.jvmci.code.sparc;

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
import jdk.vm.ci.sparc.SPARC;
import jdk.vm.ci.sparc.SPARCKind;

import compiler.jvmci.code.TestAssembler;

public class SPARCTestAssembler extends TestAssembler {

    private static final int MASK13 = (1 << 13) - 1;

    public SPARCTestAssembler(CompilationResult result, CodeCacheProvider codeCache) {
        super(result, codeCache, 0, 16, SPARCKind.WORD, SPARC.l0, SPARC.l1, SPARC.l2, SPARC.l3, SPARC.l4, SPARC.l5, SPARC.l6, SPARC.l7);
    }

    private void emitOp2(Register rd, int op2, int imm22) {
        emitInt((0b00 << 30) | (rd.encoding << 25) | (op2 << 22) | imm22);
    }

    private void emitOp3(int op, Register rd, int op3, Register rs1, Register rs2) {
        emitInt((op << 30) | (rd.encoding << 25) | (op3 << 19) | (rs1.encoding << 14) | rs2.encoding);
    }

    private void emitOp3(int op, Register rd, int op3, Register rs1, int simm13) {
        emitInt((op << 30) | (rd.encoding << 25) | (op3 << 19) | (rs1.encoding << 14) | (1 << 13) | (simm13 & MASK13));
    }

    private void emitNop() {
        emitInt(1 << 24);
    }

    public void emitPrologue() {
        emitOp3(0b10, SPARC.sp, 0b111100, SPARC.sp, -SPARC.REGISTER_SAFE_AREA_SIZE); // SAVE sp, -128, sp
    }

    @Override
    public void finish() {
        frameSize += SPARC.REGISTER_SAFE_AREA_SIZE;
        super.finish();
    }

    public void emitGrowStack(int size) {
        emitOp3(0b10, SPARC.sp, 0b000100, SPARC.sp, size); // SUB sp, size, sp
    }

    public Register emitIntArg0() {
        return SPARC.i0;
    }

    public Register emitIntArg1() {
        return SPARC.i1;
    }

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

    public Register emitLoadLong(long c) {
        if ((c & 0xFFFFFFFF) == c) {
            return emitLoadInt((int) c);
        } else {
            Data data = codeCache.createDataItem(JavaConstant.forLong(c));
            DataSectionReference ref = result.getDataSection().insertData(data);
            return emitLoadPointer(ref);
        }
    }

    private void emitPatchableSethi(Register ret, boolean wide) {
        int startPos = position();
        emitOp2(ret, 0b100, 0);              // SETHI 0, ret
        if (wide) {
            // pad for later patching
            while (position() < (startPos + 28)) {
                emitNop();
            }
        }
    }

    public Register emitLoadFloat(float c) {
        Data data = codeCache.createDataItem(JavaConstant.forFloat(c));
        DataSectionReference ref = result.getDataSection().insertData(data);

        Register ptr = newRegister();
        result.recordDataPatch(position(), ref);
        emitPatchableSethi(ptr, true);
        emitOp3(0b11, SPARC.f0, 0b100000, ptr, 0); // LDF [ptr+0], f0
        return SPARC.f0;
    }

    public Register emitLoadPointer(HotSpotConstant c) {
        Register ret = newRegister();
        result.recordDataPatch(position(), new ConstantReference((VMConstant) c));

        emitPatchableSethi(ret, !c.isCompressed());
        emitOp3(0b10, ret, 0b000010, ret, 0); // OR ret, 0, ret

        return ret;
    }

    public Register emitLoadPointer(DataSectionReference ref) {
        Register ret = newRegister();
        result.recordDataPatch(position(), ref);
        emitPatchableSethi(ret, true);
        emitOp3(0b11, ret, 0b001011, ret, 0); // LDX [ret+0], ret
        return ret;
    }

    public Register emitLoadNarrowPointer(DataSectionReference ref) {
        Register ret = newRegister();
        result.recordDataPatch(position(), ref);
        emitPatchableSethi(ret, true);
        emitOp3(0b11, ret, 0b000000, ret, 0); // LDUW [ret+0], ret
        return ret;
    }

    public Register emitLoadPointer(Register b, int offset) {
        Register ret = newRegister();
        emitOp3(0b11, ret, 0b001011, b, offset); // LDX [b+offset], ret
        return ret;
    }

    public StackSlot emitIntToStack(Register a) {
        StackSlot ret = newStackSlot(LIRKind.value(SPARCKind.WORD));
        emitOp3(0b11, a, 0b000100, SPARC.fp, ret.getRawOffset() + SPARC.STACK_BIAS); // STW a, [fp+offset]
        return ret;
    }

    public StackSlot emitLongToStack(Register a) {
        StackSlot ret = newStackSlot(LIRKind.value(SPARCKind.XWORD));
        emitOp3(0b11, a, 0b001110, SPARC.fp, ret.getRawOffset() + SPARC.STACK_BIAS); // STX a, [fp+offset]
        return ret;
    }

    public StackSlot emitFloatToStack(Register a) {
        StackSlot ret = newStackSlot(LIRKind.value(SPARCKind.SINGLE));
        emitOp3(0b11, a, 0b100100, SPARC.fp, ret.getRawOffset() + SPARC.STACK_BIAS); // STF a, [fp+offset]
        return ret;
    }

    public StackSlot emitPointerToStack(Register a) {
        StackSlot ret = newStackSlot(LIRKind.reference(SPARCKind.XWORD));
        emitOp3(0b11, a, 0b001110, SPARC.fp, ret.getRawOffset() + SPARC.STACK_BIAS); // STX a, [fp+offset]
        return ret;
    }

    public StackSlot emitNarrowPointerToStack(Register a) {
        StackSlot ret = newStackSlot(LIRKind.reference(SPARCKind.WORD));
        emitOp3(0b11, a, 0b000100, SPARC.fp, ret.getRawOffset() + SPARC.STACK_BIAS); // STW a, [fp+offset]
        return ret;
    }

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

    public void emitIntRet(Register a) {
        emitPointerRet(a);
    }

    public void emitPointerRet(Register a) {
        emitMove(SPARC.i0, a);
        emitOp3(0b10, SPARC.g0, 0b111000, SPARC.i7, 8);        // JMPL [i7+8], g0
        emitOp3(0b10, SPARC.g0, 0b111101, SPARC.g0, SPARC.g0); // RESTORE g0, g0, g0
    }

    public void emitTrap(DebugInfo info) {
        result.recordInfopoint(position(), info, InfopointReason.IMPLICIT_EXCEPTION);
        emitOp3(0b11, SPARC.g0, 0b001011, SPARC.g0, 0); // LDX [g0+0], g0
    }
}
