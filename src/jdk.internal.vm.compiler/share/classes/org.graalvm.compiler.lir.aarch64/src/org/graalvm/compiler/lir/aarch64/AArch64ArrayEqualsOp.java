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


package org.graalvm.compiler.lir.aarch64;

import static jdk.vm.ci.aarch64.AArch64.zr;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.aarch64.AArch64Address;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler.ConditionFlag;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler.ScratchRegister;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

/**
 * Emits code which compares two arrays of the same length. If the CPU supports any vector
 * instructions specialized code is emitted to leverage these instructions.
 */
@Opcode("ARRAY_EQUALS")
public final class AArch64ArrayEqualsOp extends AArch64LIRInstruction {
    public static final LIRInstructionClass<AArch64ArrayEqualsOp> TYPE = LIRInstructionClass.create(AArch64ArrayEqualsOp.class);

    private final JavaKind kind;
    private final int arrayBaseOffset;
    private final int arrayIndexScale;

    @Def({REG}) protected Value resultValue;
    @Alive({REG}) protected Value array1Value;
    @Alive({REG}) protected Value array2Value;
    @Alive({REG}) protected Value lengthValue;
    @Temp({REG}) protected Value temp1;
    @Temp({REG}) protected Value temp2;
    @Temp({REG}) protected Value temp3;
    @Temp({REG}) protected Value temp4;

    public AArch64ArrayEqualsOp(LIRGeneratorTool tool, JavaKind kind, Value result, Value array1, Value array2, Value length) {
        super(TYPE);

        assert !kind.isNumericFloat() : "Float arrays comparison (bitwise_equal || both_NaN) isn't supported";
        this.kind = kind;

        this.arrayBaseOffset = tool.getProviders().getArrayOffsetProvider().arrayBaseOffset(kind);
        this.arrayIndexScale = tool.getProviders().getArrayOffsetProvider().arrayScalingFactor(kind);

        this.resultValue = result;
        this.array1Value = array1;
        this.array2Value = array2;
        this.lengthValue = length;

        // Allocate some temporaries.
        this.temp1 = tool.newVariable(LIRKind.unknownReference(tool.target().arch.getWordKind()));
        this.temp2 = tool.newVariable(LIRKind.unknownReference(tool.target().arch.getWordKind()));
        this.temp3 = tool.newVariable(LIRKind.value(tool.target().arch.getWordKind()));
        this.temp4 = tool.newVariable(LIRKind.value(tool.target().arch.getWordKind()));
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        Register result = asRegister(resultValue);
        Register array1 = asRegister(temp1);
        Register array2 = asRegister(temp2);
        Register length = asRegister(temp3);

        Label breakLabel = new Label();

        try (ScratchRegister sc1 = masm.getScratchRegister()) {
            Register rscratch1 = sc1.getRegister();
            // Load array base addresses.
            masm.lea(array1, AArch64Address.createUnscaledImmediateAddress(asRegister(array1Value), arrayBaseOffset));
            masm.lea(array2, AArch64Address.createUnscaledImmediateAddress(asRegister(array2Value), arrayBaseOffset));

            // Get array length in bytes.
            masm.mov(rscratch1, arrayIndexScale);
            masm.smaddl(length, asRegister(lengthValue), rscratch1, zr);
            masm.mov(64, result, length); // copy

            emit8ByteCompare(crb, masm, result, array1, array2, length, breakLabel, rscratch1);
            emitTailCompares(masm, result, array1, array2, breakLabel, rscratch1);

            // Return: rscratch1 is non-zero iff the arrays differ
            masm.bind(breakLabel);
            masm.cmp(64, rscratch1, zr);
            masm.cset(result, ConditionFlag.EQ);
        }
    }

    /**
     * Vector size used in {@link #emit8ByteCompare}.
     */
    private static final int VECTOR_SIZE = 8;

    /**
     * Emits code that uses 8-byte vector compares.
     *
     */
    private void emit8ByteCompare(CompilationResultBuilder crb, AArch64MacroAssembler masm, Register result, Register array1, Register array2, Register length, Label breakLabel,
                    Register rscratch1) {
        Label loop = new Label();
        Label compareTail = new Label();

        Register temp = asRegister(temp4);

        masm.and(64, result, result, VECTOR_SIZE - 1); // tail count (in bytes)
        masm.ands(64, length, length, ~(VECTOR_SIZE - 1));  // vector count (in bytes)
        masm.branchConditionally(ConditionFlag.EQ, compareTail);

        masm.lea(array1, AArch64Address.createRegisterOffsetAddress(array1, length, false));
        masm.lea(array2, AArch64Address.createRegisterOffsetAddress(array2, length, false));
        masm.sub(64, length, zr, length);

        // Align the main loop
        masm.align(crb.target.wordSize * 2);
        masm.bind(loop);
        masm.ldr(64, temp, AArch64Address.createRegisterOffsetAddress(array1, length, false));
        masm.ldr(64, rscratch1, AArch64Address.createRegisterOffsetAddress(array2, length, false));
        masm.eor(64, rscratch1, temp, rscratch1);
        masm.cbnz(64, rscratch1, breakLabel);
        masm.add(64, length, length, VECTOR_SIZE);
        masm.cbnz(64, length, loop);

        masm.cbz(64, result, breakLabel);

        /*
         * Compare the remaining bytes with an unaligned memory load aligned to the end of the
         * array.
         */
        masm.lea(array1, AArch64Address.createUnscaledImmediateAddress(array1, -VECTOR_SIZE));
        masm.lea(array2, AArch64Address.createUnscaledImmediateAddress(array2, -VECTOR_SIZE));
        masm.ldr(64, temp, AArch64Address.createRegisterOffsetAddress(array1, result, false));
        masm.ldr(64, rscratch1, AArch64Address.createRegisterOffsetAddress(array2, result, false));
        masm.eor(64, rscratch1, temp, rscratch1);
        masm.jmp(breakLabel);

        masm.bind(compareTail);
    }

    /**
     * Emits code to compare the remaining 1 to 4 bytes.
     *
     */
    private void emitTailCompares(AArch64MacroAssembler masm, Register result, Register array1, Register array2, Label breakLabel, Register rscratch1) {
        Label compare2Bytes = new Label();
        Label compare1Byte = new Label();
        Label end = new Label();

        Register temp = asRegister(temp4);

        if (kind.getByteCount() <= 4) {
            // Compare trailing 4 bytes, if any.
            masm.ands(32, zr, result, 4);
            masm.branchConditionally(ConditionFlag.EQ, compare2Bytes);
            masm.ldr(32, temp, AArch64Address.createPostIndexedImmediateAddress(array1, 4));
            masm.ldr(32, rscratch1, AArch64Address.createPostIndexedImmediateAddress(array2, 4));
            masm.eor(32, rscratch1, temp, rscratch1);
            masm.cbnz(32, rscratch1, breakLabel);

            if (kind.getByteCount() <= 2) {
                // Compare trailing 2 bytes, if any.
                masm.bind(compare2Bytes);
                masm.ands(32, zr, result, 2);
                masm.branchConditionally(ConditionFlag.EQ, compare1Byte);
                masm.ldr(16, temp, AArch64Address.createPostIndexedImmediateAddress(array1, 2));
                masm.ldr(16, rscratch1, AArch64Address.createPostIndexedImmediateAddress(array2, 2));
                masm.eor(32, rscratch1, temp, rscratch1);
                masm.cbnz(32, rscratch1, breakLabel);

                // The one-byte tail compare is only required for boolean and byte arrays.
                if (kind.getByteCount() <= 1) {
                    // Compare trailing byte, if any.
                    masm.bind(compare1Byte);
                    masm.ands(32, zr, result, 1);
                    masm.branchConditionally(ConditionFlag.EQ, end);
                    masm.ldr(8, temp, AArch64Address.createBaseRegisterOnlyAddress(array1));
                    masm.ldr(8, rscratch1, AArch64Address.createBaseRegisterOnlyAddress(array2));
                    masm.eor(32, rscratch1, temp, rscratch1);
                    masm.cbnz(32, rscratch1, breakLabel);
                } else {
                    masm.bind(compare1Byte);
                }
            } else {
                masm.bind(compare2Bytes);
            }
        }
        masm.bind(end);
        masm.mov(64, rscratch1, zr);
    }
}
