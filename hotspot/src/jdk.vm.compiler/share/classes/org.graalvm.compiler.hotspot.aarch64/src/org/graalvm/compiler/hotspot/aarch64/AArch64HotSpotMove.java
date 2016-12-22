/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.HINT;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.STACK;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import org.graalvm.compiler.asm.aarch64.AArch64Assembler;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.hotspot.CompressEncoding;
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
                throw GraalError.unimplemented();
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
            Register base = asRegister(baseRegister);
            // result = (ptr - base) >> shift
            if (encoding.base == 0) {
                if (encoding.shift == 0) {
                    masm.movx(resultRegister, ptr);
                } else {
                    assert encoding.alignment == encoding.shift : "Encode algorithm is wrong";
                    masm.lshr(64, resultRegister, ptr, encoding.shift);
                }
            } else if (nonNull) {
                masm.sub(64, resultRegister, ptr, base);
                if (encoding.shift != 0) {
                    assert encoding.alignment == encoding.shift : "Encode algorithm is wrong";
                    masm.shl(64, resultRegister, resultRegister, encoding.shift);
                }
            } else {
                // if ptr is null it still has to be null after compression
                masm.cmp(64, ptr, 0);
                masm.cmov(64, resultRegister, ptr, base, AArch64Assembler.ConditionFlag.NE);
                masm.sub(64, resultRegister, resultRegister, base);
                if (encoding.shift != 0) {
                    assert encoding.alignment == encoding.shift : "Encode algorithm is wrong";
                    masm.lshr(64, resultRegister, resultRegister, encoding.shift);
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
            Register ptr = asRegister(input);
            Register resultRegister = asRegister(result);
            Register base = asRegister(baseRegister);
            // result = base + (ptr << shift)
            if (nonNull) {
                assert encoding.shift == encoding.alignment;
                masm.add(64, resultRegister, base, ptr, AArch64Assembler.ShiftType.ASR, encoding.shift);
            } else {
                // if ptr is null it has to be null after decompression
                // masm.cmp(64, );
                throw GraalError.unimplemented();
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

    public static void decodeKlassPointer(AArch64MacroAssembler masm, Register result, Register ptr, Register klassBase, CompressEncoding encoding) {
        // result = klassBase + ptr << shift
        if (encoding.shift != 0 || encoding.base != 0) {
            // (shift != 0 -> shift == alignment)
            assert (encoding.shift == 0 || encoding.shift == encoding.alignment) : "Decode algorithm is wrong: " + encoding;
            masm.add(64, result, klassBase, ptr, AArch64Assembler.ExtendType.UXTX, encoding.shift);
        }
    }

}
