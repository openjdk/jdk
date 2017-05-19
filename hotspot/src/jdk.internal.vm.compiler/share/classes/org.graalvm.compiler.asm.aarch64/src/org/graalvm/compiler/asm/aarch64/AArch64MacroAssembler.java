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

package org.graalvm.compiler.asm.aarch64;

import static org.graalvm.compiler.asm.aarch64.AArch64Address.AddressingMode.BASE_REGISTER_ONLY;
import static org.graalvm.compiler.asm.aarch64.AArch64Address.AddressingMode.EXTENDED_REGISTER_OFFSET;
import static org.graalvm.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_SCALED;
import static org.graalvm.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_UNSCALED;
import static org.graalvm.compiler.asm.aarch64.AArch64Address.AddressingMode.REGISTER_OFFSET;
import static org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler.AddressGenerationPlan.WorkPlan.ADD_TO_BASE;
import static org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler.AddressGenerationPlan.WorkPlan.ADD_TO_INDEX;
import static org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler.AddressGenerationPlan.WorkPlan.NO_WORK;
import static jdk.vm.ci.aarch64.AArch64.CPU;
import static jdk.vm.ci.aarch64.AArch64.r8;
import static jdk.vm.ci.aarch64.AArch64.r9;
import static jdk.vm.ci.aarch64.AArch64.sp;
import static jdk.vm.ci.aarch64.AArch64.zr;

import org.graalvm.compiler.asm.AbstractAddress;
import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.NumUtil;
import org.graalvm.compiler.debug.GraalError;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.TargetDescription;

public class AArch64MacroAssembler extends AArch64Assembler {

    private final ScratchRegister[] scratchRegister = new ScratchRegister[]{new ScratchRegister(r8), new ScratchRegister(r9)};

    // Points to the next free scratch register
    private int nextFreeScratchRegister = 0;

    public AArch64MacroAssembler(TargetDescription target) {
        super(target);
    }

    public class ScratchRegister implements AutoCloseable {
        private final Register register;

        public ScratchRegister(Register register) {
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

    public ScratchRegister getScratchRegister() {
        return scratchRegister[nextFreeScratchRegister++];
    }

    /**
     * Specifies what actions have to be taken to turn an arbitrary address of the form
     * {@code base + displacement [+ index [<< scale]]} into a valid AArch64Address.
     */
    public static class AddressGenerationPlan {
        public final WorkPlan workPlan;
        public final AArch64Address.AddressingMode addressingMode;
        public final boolean needsScratch;

        public enum WorkPlan {
            /**
             * Can be used as-is without extra work.
             */
            NO_WORK,
            /**
             * Add scaled displacement to index register.
             */
            ADD_TO_INDEX,
            /**
             * Add unscaled displacement to base register.
             */
            ADD_TO_BASE,
        }

        /**
         * @param workPlan Work necessary to generate a valid address.
         * @param addressingMode Addressing mode of generated address.
         * @param needsScratch True if generating address needs a scatch register, false otherwise.
         */
        public AddressGenerationPlan(WorkPlan workPlan, AArch64Address.AddressingMode addressingMode, boolean needsScratch) {
            this.workPlan = workPlan;
            this.addressingMode = addressingMode;
            this.needsScratch = needsScratch;
        }
    }

    /**
     * Generates an addressplan for an address of the form
     * {@code base + displacement [+ index [<< log2(transferSize)]]} with the index register and
     * scaling being optional.
     *
     * @param displacement an arbitrary displacement.
     * @param hasIndexRegister true if the address uses an index register, false otherwise. non null
     * @param transferSize the memory transfer size in bytes. The log2 of this specifies how much
     *            the index register is scaled. If 0 no scaling is assumed. Can be 0, 1, 2, 4 or 8.
     * @return AddressGenerationPlan that specifies the actions necessary to generate a valid
     *         AArch64Address for the given parameters.
     */
    public static AddressGenerationPlan generateAddressPlan(long displacement, boolean hasIndexRegister, int transferSize) {
        assert transferSize == 0 || transferSize == 1 || transferSize == 2 || transferSize == 4 || transferSize == 8;
        boolean indexScaled = transferSize != 0;
        int log2Scale = NumUtil.log2Ceil(transferSize);
        long scaledDisplacement = displacement >> log2Scale;
        boolean displacementScalable = indexScaled && (displacement & (transferSize - 1)) == 0;
        if (displacement == 0) {
            // register offset without any work beforehand.
            return new AddressGenerationPlan(NO_WORK, REGISTER_OFFSET, false);
        } else {
            if (hasIndexRegister) {
                if (displacementScalable) {
                    boolean needsScratch = !isArithmeticImmediate(scaledDisplacement);
                    return new AddressGenerationPlan(ADD_TO_INDEX, REGISTER_OFFSET, needsScratch);
                } else {
                    boolean needsScratch = !isArithmeticImmediate(displacement);
                    return new AddressGenerationPlan(ADD_TO_BASE, REGISTER_OFFSET, needsScratch);
                }
            } else {
                if (NumUtil.isSignedNbit(9, displacement)) {
                    return new AddressGenerationPlan(NO_WORK, IMMEDIATE_UNSCALED, false);
                } else if (displacementScalable && NumUtil.isUnsignedNbit(12, scaledDisplacement)) {
                    return new AddressGenerationPlan(NO_WORK, IMMEDIATE_SCALED, false);
                } else {
                    boolean needsScratch = !isArithmeticImmediate(displacement);
                    return new AddressGenerationPlan(ADD_TO_BASE, REGISTER_OFFSET, needsScratch);
                }
            }
        }
    }

    /**
     * Returns an AArch64Address pointing to
     * {@code base + displacement + index << log2(transferSize)}.
     *
     * @param base general purpose register. May not be null or the zero register.
     * @param displacement arbitrary displacement added to base.
     * @param index general purpose register. May not be null or the stack pointer.
     * @param signExtendIndex if true consider index register a word register that should be
     *            sign-extended before being added.
     * @param transferSize the memory transfer size in bytes. The log2 of this specifies how much
     *            the index register is scaled. If 0 no scaling is assumed. Can be 0, 1, 2, 4 or 8.
     * @param additionalReg additional register used either as a scratch register or as part of the
     *            final address, depending on whether allowOverwrite is true or not. May not be null
     *            or stackpointer.
     * @param allowOverwrite if true allows to change value of base or index register to generate
     *            address.
     * @return AArch64Address pointing to memory at
     *         {@code base + displacement + index << log2(transferSize)}.
     */
    public AArch64Address makeAddress(Register base, long displacement, Register index, boolean signExtendIndex, int transferSize, Register additionalReg, boolean allowOverwrite) {
        AddressGenerationPlan plan = generateAddressPlan(displacement, !index.equals(zr), transferSize);
        assert allowOverwrite || !zr.equals(additionalReg) || plan.workPlan == NO_WORK;
        assert !plan.needsScratch || !zr.equals(additionalReg);
        int log2Scale = NumUtil.log2Ceil(transferSize);
        long scaledDisplacement = displacement >> log2Scale;
        Register newIndex = index;
        Register newBase = base;
        int immediate;
        switch (plan.workPlan) {
            case NO_WORK:
                if (plan.addressingMode == IMMEDIATE_SCALED) {
                    immediate = (int) scaledDisplacement;
                } else {
                    immediate = (int) displacement;
                }
                break;
            case ADD_TO_INDEX:
                newIndex = allowOverwrite ? index : additionalReg;
                if (plan.needsScratch) {
                    mov(additionalReg, scaledDisplacement);
                    add(signExtendIndex ? 32 : 64, newIndex, index, additionalReg);
                } else {
                    add(signExtendIndex ? 32 : 64, newIndex, index, (int) scaledDisplacement);
                }
                immediate = 0;
                break;
            case ADD_TO_BASE:
                newBase = allowOverwrite ? base : additionalReg;
                if (plan.needsScratch) {
                    mov(additionalReg, displacement);
                    add(64, newBase, base, additionalReg);
                } else {
                    add(64, newBase, base, (int) displacement);
                }
                immediate = 0;
                break;
            default:
                throw GraalError.shouldNotReachHere();
        }
        AArch64Address.AddressingMode addressingMode = plan.addressingMode;
        ExtendType extendType = null;
        if (addressingMode == REGISTER_OFFSET) {
            if (newIndex.equals(zr)) {
                addressingMode = BASE_REGISTER_ONLY;
            } else if (signExtendIndex) {
                addressingMode = EXTENDED_REGISTER_OFFSET;
                extendType = ExtendType.SXTW;
            }
        }
        return AArch64Address.createAddress(addressingMode, newBase, newIndex, immediate, transferSize != 0, extendType);
    }

    /**
     * Returns an AArch64Address pointing to {@code base + displacement}. Specifies the memory
     * transfer size to allow some optimizations when building the address.
     *
     * @param base general purpose register. May not be null or the zero register.
     * @param displacement arbitrary displacement added to base.
     * @param transferSize the memory transfer size in bytes.
     * @param additionalReg additional register used either as a scratch register or as part of the
     *            final address, depending on whether allowOverwrite is true or not. May not be
     *            null, zero register or stackpointer.
     * @param allowOverwrite if true allows to change value of base or index register to generate
     *            address.
     * @return AArch64Address pointing to memory at {@code base + displacement}.
     */
    public AArch64Address makeAddress(Register base, long displacement, Register additionalReg, int transferSize, boolean allowOverwrite) {
        assert additionalReg.getRegisterCategory().equals(CPU);
        return makeAddress(base, displacement, zr, /* sign-extend */false, transferSize, additionalReg, allowOverwrite);
    }

    /**
     * Returns an AArch64Address pointing to {@code base + displacement}. Fails if address cannot be
     * represented without overwriting base register or using a scratch register.
     *
     * @param base general purpose register. May not be null or the zero register.
     * @param displacement arbitrary displacement added to base.
     * @param transferSize the memory transfer size in bytes. The log2 of this specifies how much
     *            the index register is scaled. If 0 no scaling is assumed. Can be 0, 1, 2, 4 or 8.
     * @return AArch64Address pointing to memory at {@code base + displacement}.
     */
    public AArch64Address makeAddress(Register base, long displacement, int transferSize) {
        return makeAddress(base, displacement, zr, /* signExtend */false, transferSize, zr, /* allowOverwrite */false);
    }

    /**
     * Loads memory address into register.
     *
     * @param dst general purpose register. May not be null, zero-register or stackpointer.
     * @param address address whose value is loaded into dst. May not be null,
     *            {@link org.graalvm.compiler.asm.aarch64.AArch64Address.AddressingMode#IMMEDIATE_POST_INDEXED
     *            POST_INDEXED} or
     *            {@link org.graalvm.compiler.asm.aarch64.AArch64Address.AddressingMode#IMMEDIATE_PRE_INDEXED
     *            IMMEDIATE_PRE_INDEXED}
     * @param transferSize the memory transfer size in bytes. The log2 of this specifies how much
     *            the index register is scaled. Can be 1, 2, 4 or 8.
     */
    public void loadAddress(Register dst, AArch64Address address, int transferSize) {
        assert transferSize == 1 || transferSize == 2 || transferSize == 4 || transferSize == 8;
        assert dst.getRegisterCategory().equals(CPU);
        int shiftAmt = NumUtil.log2Ceil(transferSize);
        switch (address.getAddressingMode()) {
            case IMMEDIATE_SCALED:
                int scaledImmediate = address.getImmediateRaw() << shiftAmt;
                int lowerBits = scaledImmediate & NumUtil.getNbitNumberInt(12);
                int higherBits = scaledImmediate & ~NumUtil.getNbitNumberInt(12);
                boolean firstAdd = true;
                if (lowerBits != 0) {
                    add(64, dst, address.getBase(), lowerBits);
                    firstAdd = false;
                }
                if (higherBits != 0) {
                    Register src = firstAdd ? address.getBase() : dst;
                    add(64, dst, src, higherBits);
                }
                break;
            case IMMEDIATE_UNSCALED:
                int immediate = address.getImmediateRaw();
                add(64, dst, address.getBase(), immediate);
                break;
            case REGISTER_OFFSET:
                add(64, dst, address.getBase(), address.getOffset(), ShiftType.LSL, address.isScaled() ? shiftAmt : 0);
                break;
            case EXTENDED_REGISTER_OFFSET:
                add(64, dst, address.getBase(), address.getOffset(), address.getExtendType(), address.isScaled() ? shiftAmt : 0);
                break;
            case PC_LITERAL:
                super.adr(dst, address.getImmediateRaw());
                break;
            case BASE_REGISTER_ONLY:
                movx(dst, address.getBase());
                break;
            default:
                throw GraalError.shouldNotReachHere();
        }
    }

    public void movx(Register dst, Register src) {
        mov(64, dst, src);
    }

    public void mov(int size, Register dst, Register src) {
        if (dst.equals(sp) || src.equals(sp)) {
            add(size, dst, src, 0);
        } else {
            or(size, dst, zr, src);
        }
    }

    /**
     * Generates a 64-bit immediate move code sequence.
     *
     * @param dst general purpose register. May not be null, stackpointer or zero-register.
     * @param imm
     */
    private void mov64(Register dst, long imm) {
        // We have to move all non zero parts of the immediate in 16-bit chunks
        boolean firstMove = true;
        for (int offset = 0; offset < 64; offset += 16) {
            int chunk = (int) (imm >> offset) & NumUtil.getNbitNumberInt(16);
            if (chunk == 0) {
                continue;
            }
            if (firstMove) {
                movz(64, dst, chunk, offset);
                firstMove = false;
            } else {
                movk(64, dst, chunk, offset);
            }
        }
        assert !firstMove;
    }

    /**
     * Loads immediate into register.
     *
     * @param dst general purpose register. May not be null, zero-register or stackpointer.
     * @param imm immediate loaded into register.
     */
    public void mov(Register dst, long imm) {
        assert dst.getRegisterCategory().equals(CPU);
        if (imm == 0L) {
            movx(dst, zr);
        } else if (LogicalImmediateTable.isRepresentable(true, imm) != LogicalImmediateTable.Representable.NO) {
            or(64, dst, zr, imm);
        } else if (imm >> 32 == -1L && (int) imm < 0 && LogicalImmediateTable.isRepresentable((int) imm) != LogicalImmediateTable.Representable.NO) {
            // If the higher 32-bit are 1s and the sign bit of the lower 32-bits is set *and* we can
            // represent the lower 32 bits as a logical immediate we can create the lower 32-bit and
            // then sign extend
            // them. This allows us to cover immediates like ~1L with 2 instructions.
            mov(dst, (int) imm);
            sxt(64, 32, dst, dst);
        } else {
            mov64(dst, imm);
        }
    }

    /**
     * Loads immediate into register.
     *
     * @param dst general purpose register. May not be null, zero-register or stackpointer.
     * @param imm immediate loaded into register.
     */
    public void mov(Register dst, int imm) {
        mov(dst, imm & 0xFFFF_FFFFL);
    }

    /**
     * Generates a 48-bit immediate move code sequence. The immediate may later be updated by
     * HotSpot.
     *
     * In AArch64 mode the virtual address space is 48-bits in size, so we only need three
     * instructions to create a patchable instruction sequence that can reach anywhere.
     *
     * @param dst general purpose register. May not be null, stackpointer or zero-register.
     * @param imm
     */
    public void movNativeAddress(Register dst, long imm) {
        assert (imm & 0xFFFF_0000_0000_0000L) == 0;
        // We have to move all non zero parts of the immediate in 16-bit chunks
        boolean firstMove = true;
        for (int offset = 0; offset < 48; offset += 16) {
            int chunk = (int) (imm >> offset) & NumUtil.getNbitNumberInt(16);
            if (firstMove) {
                movz(64, dst, chunk, offset);
                firstMove = false;
            } else {
                movk(64, dst, chunk, offset);
            }
        }
        assert !firstMove;
    }

    /**
     * @return Number of instructions necessary to load immediate into register.
     */
    public static int nrInstructionsToMoveImmediate(long imm) {
        if (imm == 0L || LogicalImmediateTable.isRepresentable(true, imm) != LogicalImmediateTable.Representable.NO) {
            return 1;
        }
        if (imm >> 32 == -1L && (int) imm < 0 && LogicalImmediateTable.isRepresentable((int) imm) != LogicalImmediateTable.Representable.NO) {
            // If the higher 32-bit are 1s and the sign bit of the lower 32-bits is set *and* we can
            // represent the lower 32 bits as a logical immediate we can create the lower 32-bit and
            // then sign extend
            // them. This allows us to cover immediates like ~1L with 2 instructions.
            return 2;
        }
        int nrInstructions = 0;
        for (int offset = 0; offset < 64; offset += 16) {
            int part = (int) (imm >> offset) & NumUtil.getNbitNumberInt(16);
            if (part != 0) {
                nrInstructions++;
            }
        }
        return nrInstructions;
    }

    /**
     * Loads a srcSize value from address into rt sign-extending it if necessary.
     *
     * @param targetSize size of target register in bits. Must be 32 or 64.
     * @param srcSize size of memory read in bits. Must be 8, 16 or 32 and smaller or equal to
     *            targetSize.
     * @param rt general purpose register. May not be null or stackpointer.
     * @param address all addressing modes allowed. May not be null.
     */
    @Override
    public void ldrs(int targetSize, int srcSize, Register rt, AArch64Address address) {
        assert targetSize == 32 || targetSize == 64;
        assert srcSize <= targetSize;
        if (targetSize == srcSize) {
            super.ldr(srcSize, rt, address);
        } else {
            super.ldrs(targetSize, srcSize, rt, address);
        }
    }

    /**
     * Conditional move. dst = src1 if condition else src2.
     *
     * @param size register size. Has to be 32 or 64.
     * @param result general purpose register. May not be null or the stackpointer.
     * @param trueValue general purpose register. May not be null or the stackpointer.
     * @param falseValue general purpose register. May not be null or the stackpointer.
     * @param cond any condition flag. May not be null.
     */
    public void cmov(int size, Register result, Register trueValue, Register falseValue, ConditionFlag cond) {
        super.csel(size, result, trueValue, falseValue, cond);
    }

    /**
     * Conditional set. dst = 1 if condition else 0.
     *
     * @param dst general purpose register. May not be null or stackpointer.
     * @param condition any condition. May not be null.
     */
    public void cset(Register dst, ConditionFlag condition) {
        super.csinc(32, dst, zr, zr, condition.negate());
    }

    /**
     * dst = src1 + src2.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     */
    public void add(int size, Register dst, Register src1, Register src2) {
        super.add(size, dst, src1, src2, ShiftType.LSL, 0);
    }

    /**
     * dst = src1 + src2 and sets condition flags.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     */
    public void adds(int size, Register dst, Register src1, Register src2) {
        super.adds(size, dst, src1, src2, getNopExtendType(size), 0);
    }

    /**
     * dst = src1 - src2 and sets condition flags.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     */
    public void subs(int size, Register dst, Register src1, Register src2) {
        super.subs(size, dst, src1, src2, getNopExtendType(size), 0);
    }

    /**
     * Returns the ExtendType for the given size that corresponds to a no-op.
     *
     * I.e. when doing add X0, X1, X2, the actual instruction has the form add X0, X1, X2 UXTX.
     *
     * @param size
     */
    private static ExtendType getNopExtendType(int size) {
        if (size == 64) {
            return ExtendType.UXTX;
        } else if (size == 32) {
            return ExtendType.UXTW;
        } else {
            throw GraalError.shouldNotReachHere("No-op ");
        }
    }

    /**
     * dst = src1 - src2.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     */
    public void sub(int size, Register dst, Register src1, Register src2) {
        super.sub(size, dst, src1, src2, ShiftType.LSL, 0);
    }

    /**
     * dst = src1 + shiftType(src2, shiftAmt & (size - 1)).
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     * @param shiftType any type but ROR.
     * @param shiftAmt arbitrary shift amount.
     */
    @Override
    public void add(int size, Register dst, Register src1, Register src2, ShiftType shiftType, int shiftAmt) {
        int shift = clampShiftAmt(size, shiftAmt);
        super.add(size, dst, src1, src2, shiftType, shift);
    }

    /**
     * dst = src1 + shiftType(src2, shiftAmt & (size-1)) and sets condition flags.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     * @param shiftType any type but ROR.
     * @param shiftAmt arbitrary shift amount.
     */
    @Override
    public void sub(int size, Register dst, Register src1, Register src2, ShiftType shiftType, int shiftAmt) {
        int shift = clampShiftAmt(size, shiftAmt);
        super.sub(size, dst, src1, src2, shiftType, shift);
    }

    /**
     * dst = -src1.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src general purpose register. May not be null or stackpointer.
     */
    public void neg(int size, Register dst, Register src) {
        sub(size, dst, zr, src);
    }

    /**
     * dst = src + immediate.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src general purpose register. May not be null or stackpointer.
     * @param immediate arithmetic immediate
     */
    @Override
    public void add(int size, Register dst, Register src, int immediate) {
        if (immediate < 0) {
            sub(size, dst, src, -immediate);
        } else if (!(dst.equals(src) && immediate == 0)) {
            super.add(size, dst, src, immediate);
        }
    }

    /**
     * dst = src + aimm and sets condition flags.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src general purpose register. May not be null or zero-register.
     * @param immediate arithmetic immediate.
     */
    @Override
    public void adds(int size, Register dst, Register src, int immediate) {
        if (immediate < 0) {
            subs(size, dst, src, -immediate);
        } else if (!(dst.equals(src) && immediate == 0)) {
            super.adds(size, dst, src, immediate);
        }
    }

    /**
     * dst = src - immediate.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src general purpose register. May not be null or stackpointer.
     * @param immediate arithmetic immediate
     */
    @Override
    public void sub(int size, Register dst, Register src, int immediate) {
        if (immediate < 0) {
            add(size, dst, src, -immediate);
        } else if (!dst.equals(src) || immediate != 0) {
            super.sub(size, dst, src, immediate);
        }
    }

    /**
     * dst = src - aimm and sets condition flags.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src general purpose register. May not be null or zero-register.
     * @param immediate arithmetic immediate.
     */
    @Override
    public void subs(int size, Register dst, Register src, int immediate) {
        if (immediate < 0) {
            adds(size, dst, src, -immediate);
        } else if (!dst.equals(src) || immediate != 0) {
            super.sub(size, dst, src, immediate);
        }
    }

    /**
     * dst = src1 * src2.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or the stackpointer.
     * @param src1 general purpose register. May not be null or the stackpointer.
     * @param src2 general purpose register. May not be null or the stackpointer.
     */
    public void mul(int size, Register dst, Register src1, Register src2) {
        super.madd(size, dst, src1, src2, zr);
    }

    /**
     * unsigned multiply high. dst = (src1 * src2) >> size
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or the stackpointer.
     * @param src1 general purpose register. May not be null or the stackpointer.
     * @param src2 general purpose register. May not be null or the stackpointer.
     */
    public void umulh(int size, Register dst, Register src1, Register src2) {
        assert size == 32 || size == 64;
        if (size == 64) {
            super.umulh(dst, src1, src2);
        } else {
            // xDst = wSrc1 * wSrc2
            super.umaddl(dst, src1, src2, zr);
            // xDst = xDst >> 32
            lshr(64, dst, dst, 32);
        }
    }

    /**
     * signed multiply high. dst = (src1 * src2) >> size
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or the stackpointer.
     * @param src1 general purpose register. May not be null or the stackpointer.
     * @param src2 general purpose register. May not be null or the stackpointer.
     */
    public void smulh(int size, Register dst, Register src1, Register src2) {
        assert size == 32 || size == 64;
        if (size == 64) {
            super.smulh(dst, src1, src2);
        } else {
            // xDst = wSrc1 * wSrc2
            super.smaddl(dst, src1, src2, zr);
            // xDst = xDst >> 32
            lshr(64, dst, dst, 32);
        }
    }

    /**
     * dst = src1 % src2. Signed.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or the stackpointer.
     * @param n numerator. General purpose register. May not be null or the stackpointer.
     * @param d denominator. General purpose register. Divisor May not be null or the stackpointer.
     */
    public void rem(int size, Register dst, Register n, Register d) {
        // There is no irem or similar instruction. Instead we use the relation:
        // n % d = n - Floor(n / d) * d if nd >= 0
        // n % d = n - Ceil(n / d) * d else
        // Which is equivalent to n - TruncatingDivision(n, d) * d
        super.sdiv(size, dst, n, d);
        super.msub(size, dst, dst, d, n);
    }

    /**
     * dst = src1 % src2. Unsigned.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or the stackpointer.
     * @param n numerator. General purpose register. May not be null or the stackpointer.
     * @param d denominator. General purpose register. Divisor May not be null or the stackpointer.
     */
    public void urem(int size, Register dst, Register n, Register d) {
        // There is no irem or similar instruction. Instead we use the relation:
        // n % d = n - Floor(n / d) * d
        // Which is equivalent to n - TruncatingDivision(n, d) * d
        super.udiv(size, dst, n, d);
        super.msub(size, dst, dst, d, n);
    }

    /**
     * Add/subtract instruction encoding supports 12-bit immediate values.
     *
     * @param imm immediate value to be tested.
     * @return true if immediate can be used directly for arithmetic instructions (add/sub), false
     *         otherwise.
     */
    public static boolean isArithmeticImmediate(long imm) {
        // If we have a negative immediate we just use the opposite operator. I.e.: x - (-5) == x +
        // 5.
        return NumUtil.isInt(Math.abs(imm)) && isAimm((int) Math.abs(imm));
    }

    /**
     * Compare instructions are add/subtract instructions and so support 12-bit immediate values.
     *
     * @param imm immediate value to be tested.
     * @return true if immediate can be used directly with comparison instructions, false otherwise.
     */
    public static boolean isComparisonImmediate(long imm) {
        return isArithmeticImmediate(imm);
    }

    /**
     * Move wide immediate instruction encoding supports 16-bit immediate values which can be
     * optionally-shifted by multiples of 16 (i.e. 0, 16, 32, 48).
     *
     * @return true if immediate can be moved directly into a register, false otherwise.
     */
    public static boolean isMovableImmediate(long imm) {
        // // Positions of first, respectively last set bit.
        // int start = Long.numberOfTrailingZeros(imm);
        // int end = 64 - Long.numberOfLeadingZeros(imm);
        // int length = end - start;
        // if (length > 16) {
        // return false;
        // }
        // // We can shift the necessary part of the immediate (i.e. everything between the first
        // and
        // // last set bit) by as much as 16 - length around to arrive at a valid shift amount
        // int tolerance = 16 - length;
        // int prevMultiple = NumUtil.roundDown(start, 16);
        // int nextMultiple = NumUtil.roundUp(start, 16);
        // return start - prevMultiple <= tolerance || nextMultiple - start <= tolerance;
        /*
         * This is a bit optimistic because the constant could also be for an arithmetic instruction
         * which only supports 12-bits. That case needs to be handled in the backend.
         */
        return NumUtil.isInt(Math.abs(imm)) && NumUtil.isUnsignedNbit(16, (int) Math.abs(imm));
    }

    /**
     * dst = src << (shiftAmt & (size - 1)).
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null, stackpointer or zero-register.
     * @param src general purpose register. May not be null, stackpointer or zero-register.
     * @param shiftAmt amount by which src is shifted.
     */
    public void shl(int size, Register dst, Register src, long shiftAmt) {
        int shift = clampShiftAmt(size, shiftAmt);
        super.ubfm(size, dst, src, (size - shift) & (size - 1), size - 1 - shift);
    }

    /**
     * dst = src1 << (src2 & (size - 1)).
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src general purpose register. May not be null or stackpointer.
     * @param shift general purpose register. May not be null or stackpointer.
     */
    public void shl(int size, Register dst, Register src, Register shift) {
        super.lsl(size, dst, src, shift);
    }

    /**
     * dst = src >>> (shiftAmt & (size - 1)).
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null, stackpointer or zero-register.
     * @param src general purpose register. May not be null, stackpointer or zero-register.
     * @param shiftAmt amount by which src is shifted.
     */
    public void lshr(int size, Register dst, Register src, long shiftAmt) {
        int shift = clampShiftAmt(size, shiftAmt);
        super.ubfm(size, dst, src, shift, size - 1);
    }

    /**
     * dst = src1 >>> (src2 & (size - 1)).
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src general purpose register. May not be null or stackpointer.
     * @param shift general purpose register. May not be null or stackpointer.
     */
    public void lshr(int size, Register dst, Register src, Register shift) {
        super.lsr(size, dst, src, shift);
    }

    /**
     * dst = src >> (shiftAmt & log2(size)).
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null, stackpointer or zero-register.
     * @param src general purpose register. May not be null, stackpointer or zero-register.
     * @param shiftAmt amount by which src is shifted.
     */
    public void ashr(int size, Register dst, Register src, long shiftAmt) {
        int shift = clampShiftAmt(size, shiftAmt);
        super.sbfm(size, dst, src, shift, size - 1);
    }

    /**
     * dst = src1 >> (src2 & log2(size)).
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src general purpose register. May not be null or stackpointer.
     * @param shift general purpose register. May not be null or stackpointer.
     */
    public void ashr(int size, Register dst, Register src, Register shift) {
        super.asr(size, dst, src, shift);
    }

    /**
     * Clamps shiftAmt into range 0 <= shiftamt < size according to JLS.
     *
     * @param size size of operation.
     * @param shiftAmt arbitrary shift amount.
     * @return value between 0 and size - 1 inclusive that is equivalent to shiftAmt according to
     *         JLS.
     */
    private static int clampShiftAmt(int size, long shiftAmt) {
        return (int) (shiftAmt & (size - 1));
    }

    /**
     * dst = src1 & src2.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     */
    public void and(int size, Register dst, Register src1, Register src2) {
        super.and(size, dst, src1, src2, ShiftType.LSL, 0);
    }

    /**
     * dst = src1 ^ src2.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     */
    public void eor(int size, Register dst, Register src1, Register src2) {
        super.eor(size, dst, src1, src2, ShiftType.LSL, 0);
    }

    /**
     * dst = src1 | src2.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     */
    public void or(int size, Register dst, Register src1, Register src2) {
        super.orr(size, dst, src1, src2, ShiftType.LSL, 0);
    }

    /**
     * dst = src | bimm.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or zero-register.
     * @param src general purpose register. May not be null or stack-pointer.
     * @param bimm logical immediate. See {@link AArch64Assembler.LogicalImmediateTable} for exact
     *            definition.
     */
    public void or(int size, Register dst, Register src, long bimm) {
        super.orr(size, dst, src, bimm);
    }

    /**
     * dst = ~src.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src general purpose register. May not be null or stackpointer.
     */
    public void not(int size, Register dst, Register src) {
        super.orn(size, dst, zr, src, ShiftType.LSL, 0);
    }

    /**
     * Sign-extend value from src into dst.
     *
     * @param destSize destination register size. Has to be 32 or 64.
     * @param srcSize source register size. May be 8, 16 or 32 and smaller than destSize.
     * @param dst general purpose register. May not be null, stackpointer or zero-register.
     * @param src general purpose register. May not be null, stackpointer or zero-register.
     */
    public void sxt(int destSize, int srcSize, Register dst, Register src) {
        assert (destSize == 32 || destSize == 64) && srcSize < destSize;
        assert srcSize == 8 || srcSize == 16 || srcSize == 32;
        int[] srcSizeValues = {7, 15, 31};
        super.sbfm(destSize, dst, src, 0, srcSizeValues[NumUtil.log2Ceil(srcSize / 8)]);
    }

    /**
     * dst = src if condition else -src.
     *
     * @param size register size. Must be 32 or 64.
     * @param dst general purpose register. May not be null or the stackpointer.
     * @param src general purpose register. May not be null or the stackpointer.
     * @param condition any condition except AV or NV. May not be null.
     */
    public void csneg(int size, Register dst, Register src, ConditionFlag condition) {
        super.csneg(size, dst, src, src, condition.negate());
    }

    /**
     * @return True if the immediate can be used directly for logical 64-bit instructions.
     */
    public static boolean isLogicalImmediate(long imm) {
        return LogicalImmediateTable.isRepresentable(true, imm) != LogicalImmediateTable.Representable.NO;
    }

    /**
     * @return True if the immediate can be used directly for logical 32-bit instructions.
     */
    public static boolean isLogicalImmediate(int imm) {
        return LogicalImmediateTable.isRepresentable(imm) == LogicalImmediateTable.Representable.YES;
    }

    /* Float instructions */

    /**
     * Moves integer to float, float to integer, or float to float. Does not support integer to
     * integer moves.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst Either floating-point or general-purpose register. If general-purpose register may
     *            not be stackpointer or zero register. Cannot be null in any case.
     * @param src Either floating-point or general-purpose register. If general-purpose register may
     *            not be stackpointer. Cannot be null in any case.
     */
    @Override
    public void fmov(int size, Register dst, Register src) {
        assert !(dst.getRegisterCategory().equals(CPU) && src.getRegisterCategory().equals(CPU)) : "src and dst cannot both be integer registers.";
        if (dst.getRegisterCategory().equals(CPU)) {
            super.fmovFpu2Cpu(size, dst, src);
        } else if (src.getRegisterCategory().equals(CPU)) {
            super.fmovCpu2Fpu(size, dst, src);
        } else {
            super.fmov(size, dst, src);
        }
    }

    /**
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst floating point register. May not be null.
     * @param imm immediate that is loaded into dst. If size is 32 only float immediates can be
     *            loaded, i.e. (float) imm == imm must be true. In all cases
     *            {@code isFloatImmediate}, respectively {@code #isDoubleImmediate} must be true
     *            depending on size.
     */
    @Override
    public void fmov(int size, Register dst, double imm) {
        if (imm == 0.0) {
            assert Double.doubleToRawLongBits(imm) == 0L : "-0.0 is no valid immediate.";
            super.fmovCpu2Fpu(size, dst, zr);
        } else {
            super.fmov(size, dst, imm);
        }
    }

    /**
     *
     * @return true if immediate can be loaded directly into floating-point register, false
     *         otherwise.
     */
    public static boolean isDoubleImmediate(double imm) {
        return Double.doubleToRawLongBits(imm) == 0L || AArch64Assembler.isDoubleImmediate(imm);
    }

    /**
     *
     * @return true if immediate can be loaded directly into floating-point register, false
     *         otherwise.
     */
    public static boolean isFloatImmediate(float imm) {
        return Float.floatToRawIntBits(imm) == 0 || AArch64Assembler.isFloatImmediate(imm);
    }

    /**
     * Conditional move. dst = src1 if condition else src2.
     *
     * @param size register size.
     * @param result floating point register. May not be null.
     * @param trueValue floating point register. May not be null.
     * @param falseValue floating point register. May not be null.
     * @param condition every condition allowed. May not be null.
     */
    public void fcmov(int size, Register result, Register trueValue, Register falseValue, ConditionFlag condition) {
        super.fcsel(size, result, trueValue, falseValue, condition);
    }

    /**
     * dst = src1 % src2.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst floating-point register. May not be null.
     * @param n numerator. Floating-point register. May not be null.
     * @param d denominator. Floating-point register. May not be null.
     */
    public void frem(int size, Register dst, Register n, Register d) {
        // There is no frem instruction, instead we compute the remainder using the relation:
        // rem = n - Truncating(n / d) * d
        super.fdiv(size, dst, n, d);
        super.frintz(size, dst, dst);
        super.fmsub(size, dst, dst, d, n);
    }

    /* Branches */

    /**
     * Compares x and y and sets condition flags.
     *
     * @param size register size. Has to be 32 or 64.
     * @param x general purpose register. May not be null or stackpointer.
     * @param y general purpose register. May not be null or stackpointer.
     */
    public void cmp(int size, Register x, Register y) {
        super.subs(size, zr, x, y, ShiftType.LSL, 0);
    }

    /**
     * Compares x to y and sets condition flags.
     *
     * @param size register size. Has to be 32 or 64.
     * @param x general purpose register. May not be null or stackpointer.
     * @param y comparison immediate, {@link #isComparisonImmediate(long)} has to be true for it.
     */
    public void cmp(int size, Register x, int y) {
        if (y < 0) {
            super.adds(size, zr, x, -y);
        } else {
            super.subs(size, zr, x, y);
        }
    }

    /**
     * Sets condition flags according to result of x & y.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stack-pointer.
     * @param x general purpose register. May not be null or stackpointer.
     * @param y general purpose register. May not be null or stackpointer.
     */
    public void ands(int size, Register dst, Register x, Register y) {
        super.ands(size, dst, x, y, ShiftType.LSL, 0);
    }

    /**
     * When patching up Labels we have to know what kind of code to generate.
     */
    public enum PatchLabelKind {
        BRANCH_CONDITIONALLY(0x0),
        BRANCH_UNCONDITIONALLY(0x1),
        BRANCH_NONZERO(0x2),
        BRANCH_ZERO(0x3),
        JUMP_ADDRESS(0x4),
        ADR(0x5);

        /**
         * Offset by which additional information for branch conditionally, branch zero and branch
         * non zero has to be shifted.
         */
        public static final int INFORMATION_OFFSET = 5;

        public final int encoding;

        PatchLabelKind(int encoding) {
            this.encoding = encoding;
        }

        /**
         * @return PatchLabelKind with given encoding.
         */
        private static PatchLabelKind fromEncoding(int encoding) {
            return values()[encoding & NumUtil.getNbitNumberInt(INFORMATION_OFFSET)];
        }

    }

    public void adr(Register dst, Label label) {
        // TODO Handle case where offset is too large for a single jump instruction
        if (label.isBound()) {
            int offset = label.position() - position();
            super.adr(dst, offset);
        } else {
            label.addPatchAt(position());
            // Encode condition flag so that we know how to patch the instruction later
            emitInt(PatchLabelKind.ADR.encoding | dst.encoding << PatchLabelKind.INFORMATION_OFFSET);
        }
    }

    /**
     * Compare register and branch if non-zero.
     *
     * @param size Instruction size in bits. Should be either 32 or 64.
     * @param cmp general purpose register. May not be null, zero-register or stackpointer.
     * @param label Can only handle 21-bit word-aligned offsets for now. May be unbound. Non null.
     */
    public void cbnz(int size, Register cmp, Label label) {
        // TODO Handle case where offset is too large for a single jump instruction
        if (label.isBound()) {
            int offset = label.position() - position();
            super.cbnz(size, cmp, offset);
        } else {
            label.addPatchAt(position());
            int regEncoding = cmp.encoding << (PatchLabelKind.INFORMATION_OFFSET + 1);
            int sizeEncoding = (size == 64 ? 1 : 0) << PatchLabelKind.INFORMATION_OFFSET;
            // Encode condition flag so that we know how to patch the instruction later
            emitInt(PatchLabelKind.BRANCH_NONZERO.encoding | regEncoding | sizeEncoding);
        }
    }

    /**
     * Compare register and branch if zero.
     *
     * @param size Instruction size in bits. Should be either 32 or 64.
     * @param cmp general purpose register. May not be null, zero-register or stackpointer.
     * @param label Can only handle 21-bit word-aligned offsets for now. May be unbound. Non null.
     */
    public void cbz(int size, Register cmp, Label label) {
        // TODO Handle case where offset is too large for a single jump instruction
        if (label.isBound()) {
            int offset = label.position() - position();
            super.cbz(size, cmp, offset);
        } else {
            label.addPatchAt(position());
            int regEncoding = cmp.encoding << (PatchLabelKind.INFORMATION_OFFSET + 1);
            int sizeEncoding = (size == 64 ? 1 : 0) << PatchLabelKind.INFORMATION_OFFSET;
            // Encode condition flag so that we know how to patch the instruction later
            emitInt(PatchLabelKind.BRANCH_ZERO.encoding | regEncoding | sizeEncoding);
        }
    }

    /**
     * Branches to label if condition is true.
     *
     * @param condition any condition value allowed. Non null.
     * @param label Can only handle 21-bit word-aligned offsets for now. May be unbound. Non null.
     */
    public void branchConditionally(ConditionFlag condition, Label label) {
        // TODO Handle case where offset is too large for a single jump instruction
        if (label.isBound()) {
            int offset = label.position() - position();
            super.b(condition, offset);
        } else {
            label.addPatchAt(position());
            // Encode condition flag so that we know how to patch the instruction later
            emitInt(PatchLabelKind.BRANCH_CONDITIONALLY.encoding | condition.encoding << PatchLabelKind.INFORMATION_OFFSET);
        }
    }

    /**
     * Branches if condition is true. Address of jump is patched up by HotSpot c++ code.
     *
     * @param condition any condition value allowed. Non null.
     */
    public void branchConditionally(ConditionFlag condition) {
        // Correct offset is fixed up by HotSpot later.
        super.b(condition, 0);
    }

    /**
     * Jumps to label.
     *
     * param label Can only handle signed 28-bit offsets. May be unbound. Non null.
     */
    @Override
    public void jmp(Label label) {
        // TODO Handle case where offset is too large for a single jump instruction
        if (label.isBound()) {
            int offset = label.position() - position();
            super.b(offset);
        } else {
            label.addPatchAt(position());
            emitInt(PatchLabelKind.BRANCH_UNCONDITIONALLY.encoding);
        }
    }

    /**
     * Jump to address in dest.
     *
     * @param dest General purpose register. May not be null, zero-register or stackpointer.
     */
    public void jmp(Register dest) {
        super.br(dest);
    }

    /**
     * Immediate jump instruction fixed up by HotSpot c++ code.
     */
    public void jmp() {
        // Offset has to be fixed up by c++ code.
        super.b(0);
    }

    /**
     *
     * @return true if immediate offset can be used in a single branch instruction.
     */
    public static boolean isBranchImmediateOffset(long imm) {
        return NumUtil.isSignedNbit(28, imm);
    }

    /* system instructions */

    /**
     * Exception codes used when calling hlt instruction.
     */
    public enum AArch64ExceptionCode {
        NO_SWITCH_TARGET(0x0),
        BREAKPOINT(0x1);

        public final int encoding;

        AArch64ExceptionCode(int encoding) {
            this.encoding = encoding;
        }
    }

    /**
     * Halting mode software breakpoint: Enters halting mode debug state if enabled, else treated as
     * UNALLOCATED instruction.
     *
     * @param exceptionCode exception code specifying why halt was called. Non null.
     */
    public void hlt(AArch64ExceptionCode exceptionCode) {
        super.hlt(exceptionCode.encoding);
    }

    /**
     * Monitor mode software breakpoint: exception routed to a debug monitor executing in a higher
     * exception level.
     *
     * @param exceptionCode exception code specifying why break was called. Non null.
     */
    public void brk(AArch64ExceptionCode exceptionCode) {
        super.brk(exceptionCode.encoding);
    }

    public void pause() {
        throw GraalError.unimplemented();
    }

    /**
     * Executes no-op instruction. No registers or flags are updated, except for PC.
     */
    public void nop() {
        super.hint(SystemHint.NOP);
    }

    /**
     * Same as {@link #nop()}.
     */
    @Override
    public void ensureUniquePC() {
        nop();
    }

    /**
     * Aligns PC.
     *
     * @param modulus Has to be positive multiple of 4.
     */
    @Override
    public void align(int modulus) {
        assert modulus > 0 && (modulus & 0x3) == 0 : "Modulus has to be a positive multiple of 4.";
        if (position() % modulus == 0) {
            return;
        }
        int offset = modulus - position() % modulus;
        for (int i = 0; i < offset; i += 4) {
            nop();
        }
    }

    /**
     * Patches jump targets when label gets bound.
     */
    @Override
    protected void patchJumpTarget(int branch, int jumpTarget) {
        int instruction = getInt(branch);
        int branchOffset = jumpTarget - branch;
        PatchLabelKind type = PatchLabelKind.fromEncoding(instruction);
        switch (type) {
            case BRANCH_CONDITIONALLY:
                ConditionFlag cf = ConditionFlag.fromEncoding(instruction >>> PatchLabelKind.INFORMATION_OFFSET);
                super.b(cf, branchOffset, branch);
                break;
            case BRANCH_UNCONDITIONALLY:
                super.b(branchOffset, branch);
                break;
            case JUMP_ADDRESS:
                emitInt(jumpTarget, branch);
                break;
            case BRANCH_NONZERO:
            case BRANCH_ZERO: {
                int information = instruction >>> PatchLabelKind.INFORMATION_OFFSET;
                int sizeEncoding = information & 1;
                int regEncoding = information >>> 1;
                Register reg = AArch64.cpuRegisters.get(regEncoding);
                // 1 => 64; 0 => 32
                int size = sizeEncoding * 32 + 32;
                switch (type) {
                    case BRANCH_NONZERO:
                        super.cbnz(size, reg, branchOffset, branch);
                        break;
                    case BRANCH_ZERO:
                        super.cbz(size, reg, branchOffset, branch);
                        break;
                }
                break;
            }
            case ADR: {
                int information = instruction >>> PatchLabelKind.INFORMATION_OFFSET;
                int regEncoding = information;
                Register reg = AArch64.cpuRegisters.get(regEncoding);
                super.adr(reg, branchOffset, branch);
                break;
            }
            default:
                throw GraalError.shouldNotReachHere();
        }
    }

    /**
     * Generates an address of the form {@code base + displacement}.
     *
     * Does not change base register to fulfill this requirement. Will fail if displacement cannot
     * be represented directly as address.
     *
     * @param base general purpose register. May not be null or the zero register.
     * @param displacement arbitrary displacement added to base.
     * @return AArch64Address referencing memory at {@code base + displacement}.
     */
    @Override
    public AArch64Address makeAddress(Register base, int displacement) {
        return makeAddress(base, displacement, zr, /* signExtend */false, /* transferSize */0, zr, /* allowOverwrite */false);
    }

    @Override
    public AbstractAddress getPlaceholder(int instructionStartPosition) {
        return AArch64Address.PLACEHOLDER;
    }
}
