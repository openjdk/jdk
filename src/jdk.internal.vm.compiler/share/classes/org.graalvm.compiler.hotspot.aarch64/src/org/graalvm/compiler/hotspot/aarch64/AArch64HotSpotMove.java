/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.aarch64;

import static jdk.vm.ci.aarch64.AArch64.zr;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isRegister;
import static org.graalvm.compiler.core.common.GraalOptions.GeneratePIC;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.HINT;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.STACK;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.aarch64.AArch64Address;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.StandardOp.LoadConstantOp;
import org.graalvm.compiler.lir.aarch64.AArch64LIRInstruction;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.hotspot.HotSpotConstant;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;

public class AArch64HotSpotMove {

    public static class LoadHotSpotObjectConstantInline extends AArch64LIRInstruction implements LoadConstantOp {
        public static final LIRInstructionClass<LoadHotSpotObjectConstantInline> TYPE = LIRInstructionClass.create(LoadHotSpotObjectConstantInline.class);

        private HotSpotConstant constant;
        @Def({REG, STACK}) AllocatableValue result;

        public LoadHotSpotObjectConstantInline(HotSpotConstant constant, AllocatableValue result) {
            super(TYPE);
            this.constant = constant;
            this.result = result;
        }

        @Override
        protected void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            crb.recordInlineDataInCode(constant);
            if (constant.isCompressed()) {
                // masm.forceMov(asRegister(result), 0);
                masm.movNarrowAddress(asRegister(result), 0);
            } else {
                masm.movNativeAddress(asRegister(result), 0);
            }
        }

        @Override
        public AllocatableValue getResult() {
            return result;
        }

        @Override
        public Constant getConstant() {
            return constant;
        }
    }

    public static final class BaseMove extends AArch64LIRInstruction {
        public static final LIRInstructionClass<BaseMove> TYPE = LIRInstructionClass.create(BaseMove.class);

        @LIRInstruction.Def({REG, HINT}) protected AllocatableValue result;
        private final GraalHotSpotVMConfig config;

        public BaseMove(AllocatableValue result, GraalHotSpotVMConfig config) {
            super(TYPE);
            this.result = result;
            this.config = config;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            try (AArch64MacroAssembler.ScratchRegister sc = masm.getScratchRegister()) {
                Register scratch = sc.getRegister();
                masm.adrp(scratch);
                masm.add(64, scratch, scratch, 1);
                masm.ldr(64, asRegister(result), AArch64Address.createBaseRegisterOnlyAddress(scratch));
                masm.nop();
                crb.recordMark(config.MARKID_NARROW_KLASS_BASE_ADDRESS);
            }
        }

    }

    /**
     * Compresses a 8-byte pointer as a 4-byte int.
     */
    public static class CompressPointer extends AArch64LIRInstruction {
        public static final LIRInstructionClass<CompressPointer> TYPE = LIRInstructionClass.create(CompressPointer.class);

        private final CompressEncoding encoding;
        private final boolean nonNull;

        @Def({REG, HINT}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue input;
        @Alive({REG, ILLEGAL}) protected AllocatableValue baseRegister;

        public CompressPointer(AllocatableValue result, AllocatableValue input, AllocatableValue baseRegister, CompressEncoding encoding, boolean nonNull) {
            super(TYPE);
            this.result = result;
            this.input = input;
            this.baseRegister = baseRegister;
            this.encoding = encoding;
            this.nonNull = nonNull;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            Register resultRegister = asRegister(result);
            Register ptr = asRegister(input);
            Register base = (isRegister(baseRegister) ? asRegister(baseRegister) : zr);
            // result = (ptr - base) >> shift
            if (!encoding.hasBase()) {
                if (encoding.hasShift()) {
                    masm.lshr(64, resultRegister, ptr, encoding.getShift());
                } else {
                    masm.movx(resultRegister, ptr);
                }
            } else if (nonNull) {
                masm.sub(64, resultRegister, ptr, base);
                if (encoding.hasShift()) {
                    masm.lshr(64, resultRegister, resultRegister, encoding.getShift());
                }
            } else {
                // if ptr is null it still has to be null after compression
                masm.cmp(64, ptr, 0);
                masm.cmov(64, resultRegister, ptr, base, AArch64Assembler.ConditionFlag.NE);
                masm.sub(64, resultRegister, resultRegister, base);
                if (encoding.hasShift()) {
                    masm.lshr(64, resultRegister, resultRegister, encoding.getShift());
                }
            }
        }
    }

    /**
     * Decompresses a 4-byte offset into an actual pointer.
     */
    public static class UncompressPointer extends AArch64LIRInstruction {
        public static final LIRInstructionClass<UncompressPointer> TYPE = LIRInstructionClass.create(UncompressPointer.class);

        private final CompressEncoding encoding;
        private final boolean nonNull;

        @Def({REG}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue input;
        @Alive({REG, ILLEGAL}) protected AllocatableValue baseRegister;

        public UncompressPointer(AllocatableValue result, AllocatableValue input, AllocatableValue baseRegister, CompressEncoding encoding, boolean nonNull) {
            super(TYPE);
            this.result = result;
            this.input = input;
            this.baseRegister = baseRegister;
            this.encoding = encoding;
            this.nonNull = nonNull;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            Register inputRegister = asRegister(input);
            Register resultRegister = asRegister(result);
            Register base = encoding.hasBase() ? asRegister(baseRegister) : null;
            emitUncompressCode(masm, inputRegister, resultRegister, base, encoding.getShift(), nonNull);
        }

        public static void emitUncompressCode(AArch64MacroAssembler masm, Register inputRegister, Register resReg, Register baseReg, int shift, boolean nonNull) {
            // result = base + (ptr << shift)
            if (nonNull || baseReg == null) {
                masm.add(64, resReg, baseReg == null ? zr : baseReg, inputRegister, AArch64Assembler.ShiftType.LSL, shift);
            } else {
                // if ptr is null it has to be null after decompression
                Label done = new Label();
                if (!resReg.equals(inputRegister)) {
                    masm.mov(32, resReg, inputRegister);
                }
                masm.cbz(32, resReg, done);
                masm.add(64, resReg, baseReg, resReg, AArch64Assembler.ShiftType.LSL, shift);
                masm.bind(done);
            }
        }
    }

    //
    // private static void decompressPointer(CompilationResultBuilder crb, ARMv8MacroAssembler masm,
    // Register result,
    // Register ptr, long base, int shift, int alignment) {
    // assert base != 0 || shift == 0 || alignment == shift;
    // // result = heapBase + ptr << alignment
    // Register heapBase = ARMv8.heapBaseRegister;
    // // if result == 0, we make sure that it will still be 0 at the end, so that it traps when
    // // loading storing a value.
    // masm.cmp(32, ptr, 0);
    // masm.add(64, result, heapBase, ptr, ARMv8Assembler.ExtendType.UXTX, alignment);
    // masm.cmov(64, result, result, ARMv8.zr, ARMv8Assembler.ConditionFlag.NE);
    // }

    public static void decodeKlassPointer(CompilationResultBuilder crb, AArch64MacroAssembler masm, Register result, Register ptr, CompressEncoding encoding, GraalHotSpotVMConfig config) {
        try (AArch64MacroAssembler.ScratchRegister sc = masm.getScratchRegister()) {
            Register scratch = sc.getRegister();
            boolean pic = GeneratePIC.getValue(crb.getOptions());
            if (pic || encoding.hasBase() || encoding.getShift() != 0) {
                if (pic) {
                    masm.addressOf(scratch);
                    masm.ldr(64, scratch, AArch64Address.createBaseRegisterOnlyAddress(scratch));
                    masm.add(64, result, scratch, ptr, AArch64Assembler.ExtendType.UXTX, encoding.getShift());
                    crb.recordMark(config.MARKID_NARROW_KLASS_BASE_ADDRESS);
                } else {
                    masm.mov(scratch, encoding.getBase());
                    masm.add(64, result, scratch, ptr, AArch64Assembler.ExtendType.UXTX, encoding.getShift());
                }
            }
        }
    }
}
