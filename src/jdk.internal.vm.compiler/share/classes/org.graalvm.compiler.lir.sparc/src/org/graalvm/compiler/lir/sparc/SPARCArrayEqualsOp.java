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


package org.graalvm.compiler.lir.sparc;

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.sparc.SPARC.g0;
import static jdk.vm.ci.sparc.SPARCKind.WORD;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.BPCC;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Annul.ANNUL;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Annul.NOT_ANNUL;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.BranchPredict.PREDICT_NOT_TAKEN;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.BranchPredict.PREDICT_TAKEN;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.CC.Xcc;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.ConditionFlag.Equal;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.ConditionFlag.Less;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.ConditionFlag.NotEqual;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.sparc.SPARCAddress;
import org.graalvm.compiler.asm.sparc.SPARCMacroAssembler;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.sparc.SPARCKind;

/**
 * Emits code which compares two arrays of the same length.
 */
@Opcode("ARRAY_EQUALS")
public final class SPARCArrayEqualsOp extends SPARCLIRInstruction {
    public static final LIRInstructionClass<SPARCArrayEqualsOp> TYPE = LIRInstructionClass.create(SPARCArrayEqualsOp.class);
    public static final SizeEstimate SIZE = SizeEstimate.create(32);

    private final JavaKind kind;
    private final int arrayBaseOffset;
    private final int arrayIndexScale;

    @Def({REG}) protected AllocatableValue resultValue;
    @Alive({REG}) protected AllocatableValue array1Value;
    @Alive({REG}) protected AllocatableValue array2Value;
    @Alive({REG}) protected AllocatableValue lengthValue;
    @Temp({REG}) protected AllocatableValue temp1;
    @Temp({REG}) protected AllocatableValue temp2;
    @Temp({REG}) protected AllocatableValue temp3;
    @Temp({REG}) protected AllocatableValue temp4;
    @Temp({REG}) protected AllocatableValue temp5;

    public SPARCArrayEqualsOp(LIRGeneratorTool tool, JavaKind kind, AllocatableValue result, AllocatableValue array1, AllocatableValue array2, AllocatableValue length, boolean directPointers) {
        super(TYPE, SIZE);

        assert !kind.isNumericFloat() : "Float arrays comparison (bitwise_equal || both_NaN) isn't supported";
        this.kind = kind;

        this.arrayBaseOffset = directPointers ? 0 : tool.getProviders().getMetaAccess().getArrayBaseOffset(kind);
        this.arrayIndexScale = tool.getProviders().getMetaAccess().getArrayIndexScale(kind);

        this.resultValue = result;
        this.array1Value = array1;
        this.array2Value = array2;
        this.lengthValue = length;

        // Allocate some temporaries.
        this.temp1 = tool.newVariable(LIRKind.unknownReference(tool.target().arch.getWordKind()));
        this.temp2 = tool.newVariable(LIRKind.unknownReference(tool.target().arch.getWordKind()));
        this.temp3 = tool.newVariable(LIRKind.value(tool.target().arch.getWordKind()));
        this.temp4 = tool.newVariable(LIRKind.value(tool.target().arch.getWordKind()));
        this.temp5 = tool.newVariable(LIRKind.value(tool.target().arch.getWordKind()));
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
        Register result = asRegister(resultValue);
        Register array1 = asRegister(temp1);
        Register array2 = asRegister(temp2);
        Register length = asRegister(temp3);

        Label trueLabel = new Label();
        Label falseLabel = new Label();
        Label done = new Label();

        // Load array base addresses.
        masm.add(asRegister(array1Value), arrayBaseOffset, array1);
        masm.add(asRegister(array2Value), arrayBaseOffset, array2);

        // Get array length in bytes.
        masm.mulx(asRegister(lengthValue, WORD), arrayIndexScale, length);
        masm.mov(length, result); // copy

        emit8ByteCompare(masm, result, array1, array2, length, trueLabel, falseLabel);
        emitTailCompares(masm, result, array1, array2, trueLabel, falseLabel);

        // Return true
        masm.bind(trueLabel);
        masm.mov(1, result);
        masm.jmp(done);

        // Return false
        masm.bind(falseLabel);
        masm.mov(g0, result);

        // That's it
        masm.bind(done);
    }

    /**
     * Vector size used in {@link #emit8ByteCompare}.
     */
    private static final int VECTOR_SIZE = 8;

    /**
     * Emits code that uses 8-byte vector compares.
     */
    private void emit8ByteCompare(SPARCMacroAssembler masm, Register result, Register array1, Register array2, Register length, Label trueLabel, Label falseLabel) {
        assert lengthValue.getPlatformKind().equals(SPARCKind.WORD);
        Label loop = new Label();
        Label compareTail = new Label();
        Label compareTailCorrectVectorEnd = new Label();

        Register tempReg1 = asRegister(temp4);
        Register tempReg2 = asRegister(temp5);

        masm.sra(length, 0, length);
        masm.and(result, VECTOR_SIZE - 1, result); // tail count (in bytes)
        masm.andcc(length, ~(VECTOR_SIZE - 1), length);  // vector count (in bytes)
        BPCC.emit(masm, Xcc, Equal, NOT_ANNUL, PREDICT_NOT_TAKEN, compareTail);

        masm.sub(length, VECTOR_SIZE, length); // Delay slot
        masm.add(array1, length, array1);
        masm.add(array2, length, array2);
        masm.sub(g0, length, length);

        // Compare the last element first
        masm.ldx(new SPARCAddress(array1, 0), tempReg1);
        masm.ldx(new SPARCAddress(array2, 0), tempReg2);
        masm.compareBranch(tempReg1, tempReg2, NotEqual, Xcc, falseLabel, PREDICT_NOT_TAKEN, null);
        masm.compareBranch(length, 0, Equal, Xcc, compareTailCorrectVectorEnd, PREDICT_NOT_TAKEN, null);

        // Load the first value from array 1 (Later done in back branch delay-slot)
        masm.ldx(new SPARCAddress(array1, length), tempReg1);
        masm.bind(loop);
        masm.ldx(new SPARCAddress(array2, length), tempReg2);
        masm.cmp(tempReg1, tempReg2);

        BPCC.emit(masm, Xcc, NotEqual, NOT_ANNUL, PREDICT_NOT_TAKEN, falseLabel);
        // Delay slot, not annul, add for next iteration
        masm.addcc(length, VECTOR_SIZE, length);
        // Annul, to prevent access past the array
        BPCC.emit(masm, Xcc, NotEqual, ANNUL, PREDICT_TAKEN, loop);
        masm.ldx(new SPARCAddress(array1, length), tempReg1); // Load in delay slot

        // Tail count zero, therefore we can go to the end
        masm.compareBranch(result, 0, Equal, Xcc, trueLabel, PREDICT_TAKEN, null);

        masm.bind(compareTailCorrectVectorEnd);
        // Correct the array pointers
        masm.add(array1, VECTOR_SIZE, array1);
        masm.add(array2, VECTOR_SIZE, array2);

        masm.bind(compareTail);
    }

    /**
     * Emits code to compare the remaining 1 to 4 bytes.
     */
    private void emitTailCompares(SPARCMacroAssembler masm, Register result, Register array1, Register array2, Label trueLabel, Label falseLabel) {
        Label compare2Bytes = new Label();
        Label compare1Byte = new Label();

        Register tempReg1 = asRegister(temp3);
        Register tempReg2 = asRegister(temp4);

        if (kind.getByteCount() <= 4) {
            // Compare trailing 4 bytes, if any.
            masm.compareBranch(result, 4, Less, Xcc, compare2Bytes, PREDICT_NOT_TAKEN, null);

            masm.lduw(new SPARCAddress(array1, 0), tempReg1);
            masm.lduw(new SPARCAddress(array2, 0), tempReg2);
            masm.compareBranch(tempReg1, tempReg2, NotEqual, Xcc, falseLabel, PREDICT_NOT_TAKEN, null);

            if (kind.getByteCount() <= 2) {
                // Move array pointers forward.
                masm.add(array1, 4, array1);
                masm.add(array2, 4, array2);
                masm.sub(result, 4, result);

                // Compare trailing 2 bytes, if any.
                masm.bind(compare2Bytes);

                masm.compareBranch(result, 2, Less, Xcc, compare1Byte, PREDICT_TAKEN, null);

                masm.lduh(new SPARCAddress(array1, 0), tempReg1);
                masm.lduh(new SPARCAddress(array2, 0), tempReg2);

                masm.compareBranch(tempReg1, tempReg2, NotEqual, Xcc, falseLabel, PREDICT_TAKEN, null);

                // The one-byte tail compare is only required for boolean and byte arrays.
                if (kind.getByteCount() <= 1) {
                    // Move array pointers forward before we compare the last trailing byte.
                    masm.add(array1, 2, array1);
                    masm.add(array2, 2, array2);
                    masm.sub(result, 2, result);

                    // Compare trailing byte, if any.
                    masm.bind(compare1Byte);
                    masm.compareBranch(result, 1, NotEqual, Xcc, trueLabel, PREDICT_TAKEN, null);

                    masm.ldub(new SPARCAddress(array1, 0), tempReg1);
                    masm.ldub(new SPARCAddress(array2, 0), tempReg2);
                    masm.compareBranch(tempReg1, tempReg2, NotEqual, Xcc, falseLabel, PREDICT_TAKEN, null);
                } else {
                    masm.bind(compare1Byte);
                }
            } else {
                masm.bind(compare2Bytes);
            }
        }
    }
}
