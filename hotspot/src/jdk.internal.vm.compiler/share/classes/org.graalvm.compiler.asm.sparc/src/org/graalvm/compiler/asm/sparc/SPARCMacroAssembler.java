/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.asm.sparc;

import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Annul.NOT_ANNUL;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.BranchPredict.PREDICT_NOT_TAKEN;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.CC.Icc;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.CC.Xcc;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.ConditionFlag.Always;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.ConditionFlag.Equal;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.RCondition.Rc_z;
import static jdk.vm.ci.sparc.SPARC.g0;
import static jdk.vm.ci.sparc.SPARC.g3;
import static jdk.vm.ci.sparc.SPARC.i7;
import static jdk.vm.ci.sparc.SPARC.o7;

import org.graalvm.compiler.asm.AbstractAddress;
import org.graalvm.compiler.asm.Label;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.sparc.SPARC.CPUFeature;

public class SPARCMacroAssembler extends SPARCAssembler {

    /**
     * A sentinel value used as a place holder in an instruction stream for an address that will be
     * patched.
     */
    private static final SPARCAddress Placeholder = new SPARCAddress(g0, 0);
    private final ScratchRegister[] scratchRegister = new ScratchRegister[]{new ScratchRegister(g3), new ScratchRegister(o7)};
    // Points to the next free scratch register
    private int nextFreeScratchRegister = 0;
    /**
     * Use ld [reg+simm13], reg for loading constants (User has to make sure, that the size of the
     * constant table does not exceed simm13).
     */
    private boolean immediateConstantLoad;

    public SPARCMacroAssembler(TargetDescription target) {
        super(target);
    }

    /**
     * @see #immediateConstantLoad
     */
    public void setImmediateConstantLoad(boolean immediateConstantLoad) {
        this.immediateConstantLoad = immediateConstantLoad;
    }

    @Override
    public void align(int modulus) {
        while (position() % modulus != 0) {
            nop();
        }
    }

    @Override
    public void jmp(Label l) {
        BPCC.emit(this, Xcc, Always, NOT_ANNUL, PREDICT_NOT_TAKEN, l);
        nop();  // delay slot
    }

    public void bz(Label l) {
        BPCC.emit(this, Xcc, ConditionFlag.Zero, NOT_ANNUL, PREDICT_NOT_TAKEN, l);
    }

    @Override
    protected final void patchJumpTarget(int branch, int branchTarget) {
        final int disp = (branchTarget - branch) / 4;
        final int inst = getInt(branch);
        ControlTransferOp op = (ControlTransferOp) getSPARCOp(inst);
        int newInst = op.setDisp(inst, disp);
        emitInt(newInst, branch);
    }

    @Override
    public AbstractAddress makeAddress(Register base, int displacement) {
        return new SPARCAddress(base, displacement);
    }

    @Override
    public AbstractAddress getPlaceholder(int instructionStartPosition) {
        return Placeholder;
    }

    @Override
    public final void ensureUniquePC() {
        nop();
    }

    public void cas(Register rs1, Register rs2, Register rd) {
        casa(rs1, rs2, rd, Asi.ASI_PRIMARY);
    }

    public void casx(Register rs1, Register rs2, Register rd) {
        casxa(rs1, rs2, rd, Asi.ASI_PRIMARY);
    }

    public void clr(Register dst) {
        or(g0, g0, dst);
    }

    public void clrb(SPARCAddress addr) {
        stb(g0, addr);
    }

    public void clrh(SPARCAddress addr) {
        sth(g0, addr);
    }

    public void clrx(SPARCAddress addr) {
        stx(g0, addr);
    }

    public void cmp(Register rs1, Register rs2) {
        subcc(rs1, rs2, g0);
    }

    public void cmp(Register rs1, int simm13) {
        subcc(rs1, simm13, g0);
    }

    public void dec(Register rd) {
        sub(rd, 1, rd);
    }

    public void dec(int simm13, Register rd) {
        sub(rd, simm13, rd);
    }

    public void jmp(SPARCAddress address) {
        jmpl(address.getBase(), address.getDisplacement(), g0);
    }

    public void jmp(Register rd) {
        jmpl(rd, 0, g0);
    }

    public void neg(Register rs1, Register rd) {
        sub(g0, rs1, rd);
    }

    public void neg(Register rd) {
        sub(g0, rd, rd);
    }

    public void mov(Register rs, Register rd) {
        or(g0, rs, rd);
    }

    public void mov(int simm13, Register rd) {
        or(g0, simm13, rd);
    }

    public void not(Register rs1, Register rd) {
        xnor(rs1, g0, rd);
    }

    public void not(Register rd) {
        xnor(rd, g0, rd);
    }

    public void restoreWindow() {
        restore(g0, g0, g0);
    }

    public void ret() {
        jmpl(i7, 8, g0);
    }

    /**
     * Generates sethi hi22(value), dst; or dst, lo10(value), dst; code.
     */
    public void setw(int value, Register dst, boolean forceRelocatable) {
        if (!forceRelocatable && isSimm13(value)) {
            or(g0, value, dst);
        } else {
            sethi(hi22(value), dst);
            or(dst, lo10(value), dst);
        }
    }

    public void setx(long value, Register dst, boolean forceRelocatable) {
        int lo = (int) (value & ~0);
        sethix(value, dst, forceRelocatable);
        if (lo10(lo) != 0 || forceRelocatable) {
            add(dst, lo10(lo), dst);
        }
    }

    public void sethix(long value, Register dst, boolean forceRelocatable) {
        final int hi = (int) (value >> 32);
        final int lo = (int) (value & ~0);

        // This is the same logic as MacroAssembler::internal_set.
        final int startPc = position();
        if (hi == 0 && lo >= 0) {
            sethi(hi22(lo), dst);
        } else if (hi == -1) {
            sethi(hi22(~lo), dst);
            xor(dst, ~lo10(~0), dst);
        } else {
            final int shiftcnt;
            final int shiftcnt2;
            sethi(hi22(hi), dst);
            if ((hi & 0x3ff) != 0) {                                  // Any bits?
                // msb 32-bits are now in lsb 32
                or(dst, hi & 0x3ff, dst);
            }
            if ((lo & 0xFFFFFC00) != 0) {                             // done?
                if (((lo >> 20) & 0xfff) != 0) {                      // Any bits set?
                    // Make room for next 12 bits
                    sllx(dst, 12, dst);
                    // Or in next 12
                    or(dst, (lo >> 20) & 0xfff, dst);
                    shiftcnt = 0;                                     // We already shifted
                } else {
                    shiftcnt = 12;
                }
                if (((lo >> 10) & 0x3ff) != 0) {
                    // Make room for last 10 bits
                    sllx(dst, shiftcnt + 10, dst);
                    // Or in next 10
                    or(dst, (lo >> 10) & 0x3ff, dst);
                    shiftcnt2 = 0;
                } else {
                    shiftcnt2 = 10;
                }
                // Shift leaving disp field 0'd
                sllx(dst, shiftcnt2 + 10, dst);
            } else {
                sllx(dst, 32, dst);
            }
        }
        // Pad out the instruction sequence so it can be patched later.
        if (forceRelocatable) {
            while (position() < (startPc + (INSTRUCTION_SIZE * 7))) {
                nop();
            }
        }
    }

    public void signx(Register rs, Register rd) {
        sra(rs, g0, rd);
    }

    public void signx(Register rd) {
        sra(rd, g0, rd);
    }

    public boolean isImmediateConstantLoad() {
        return immediateConstantLoad;
    }

    public ScratchRegister getScratchRegister() {
        return scratchRegister[nextFreeScratchRegister++];
    }

    public class ScratchRegister implements AutoCloseable {
        private final Register register;

        public ScratchRegister(Register register) {
            super();
            this.register = register;
        }

        public Register getRegister() {
            return register;
        }

        @Override
        public void close() {
            assert nextFreeScratchRegister > 0 : "Close called too often";
            nextFreeScratchRegister--;
        }
    }

    public void compareBranch(Register rs1, Register rs2, ConditionFlag cond, CC ccRegister, Label label, BranchPredict predict, Runnable delaySlotInstruction) {
        assert isCPURegister(rs1, rs2);
        assert ccRegister == Icc || ccRegister == Xcc;
        if (hasFeature(CPUFeature.CBCOND)) {
            if (delaySlotInstruction != null) {
                delaySlotInstruction.run();
            }
            CBCOND.emit(this, cond, ccRegister == Xcc, rs1, rs2, label);
        } else {
            if (cond == Equal && rs1.equals(g0)) {
                BPR.emit(this, Rc_z, NOT_ANNUL, predict, rs1, label);
            } else {
                cmp(rs1, rs2);
                BPCC.emit(this, ccRegister, cond, NOT_ANNUL, predict, label);
            }
            if (delaySlotInstruction != null) {
                int positionBefore = position();
                delaySlotInstruction.run();
                int positionAfter = position();
                assert positionBefore - positionAfter > INSTRUCTION_SIZE : "Emitted more than one instruction into delay slot";
            } else {
                nop();
            }
        }
    }

    public void compareBranch(Register rs1, int simm, ConditionFlag cond, CC ccRegister, Label label, BranchPredict predict, Runnable delaySlotInstruction) {
        assert isCPURegister(rs1);
        assert ccRegister == Icc || ccRegister == Xcc;
        if (hasFeature(CPUFeature.CBCOND)) {
            if (delaySlotInstruction != null) {
                delaySlotInstruction.run();
            }
            CBCOND.emit(this, cond, ccRegister == Xcc, rs1, simm, label);
        } else {
            if (cond == Equal && simm == 0) {
                BPR.emit(this, Rc_z, NOT_ANNUL, PREDICT_NOT_TAKEN, rs1, label);
            } else {
                cmp(rs1, simm);
                BPCC.emit(this, ccRegister, cond, NOT_ANNUL, predict, label);
            }
            if (delaySlotInstruction != null) {
                int positionBefore = position();
                delaySlotInstruction.run();
                int positionAfter = position();
                assert positionBefore - positionAfter > INSTRUCTION_SIZE : "Emitted more than one instruction into delay slot";
            } else {
                nop();
            }
        }
    }
}
