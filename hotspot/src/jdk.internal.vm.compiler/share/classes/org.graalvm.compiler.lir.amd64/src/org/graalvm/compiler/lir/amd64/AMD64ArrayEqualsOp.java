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
package org.graalvm.compiler.lir.amd64;

import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import java.lang.reflect.Array;
import java.lang.reflect.Field;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64Address.Scale;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import sun.misc.Unsafe;

/**
 * Emits code which compares two arrays of the same length. If the CPU supports any vector
 * instructions specialized code is emitted to leverage these instructions.
 */
@Opcode("ARRAY_EQUALS")
public final class AMD64ArrayEqualsOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64ArrayEqualsOp> TYPE = LIRInstructionClass.create(AMD64ArrayEqualsOp.class);

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
    @Temp({REG, ILLEGAL}) protected Value vectorTemp1;
    @Temp({REG, ILLEGAL}) protected Value vectorTemp2;

    public AMD64ArrayEqualsOp(LIRGeneratorTool tool, JavaKind kind, Value result, Value array1, Value array2, Value length) {
        super(TYPE);
        this.kind = kind;

        Class<?> arrayClass = Array.newInstance(kind.toJavaClass(), 0).getClass();
        this.arrayBaseOffset = UNSAFE.arrayBaseOffset(arrayClass);
        this.arrayIndexScale = UNSAFE.arrayIndexScale(arrayClass);

        this.resultValue = result;
        this.array1Value = array1;
        this.array2Value = array2;
        this.lengthValue = length;

        // Allocate some temporaries.
        this.temp1 = tool.newVariable(LIRKind.unknownReference(tool.target().arch.getWordKind()));
        this.temp2 = tool.newVariable(LIRKind.unknownReference(tool.target().arch.getWordKind()));
        this.temp3 = tool.newVariable(LIRKind.value(tool.target().arch.getWordKind()));
        this.temp4 = tool.newVariable(LIRKind.value(tool.target().arch.getWordKind()));

        // We only need the vector temporaries if we generate SSE code.
        if (supportsSSE41(tool.target())) {
            this.vectorTemp1 = tool.newVariable(LIRKind.value(AMD64Kind.DOUBLE));
            this.vectorTemp2 = tool.newVariable(LIRKind.value(AMD64Kind.DOUBLE));
        } else {
            this.vectorTemp1 = Value.ILLEGAL;
            this.vectorTemp2 = Value.ILLEGAL;
        }
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        Register result = asRegister(resultValue);
        Register array1 = asRegister(temp1);
        Register array2 = asRegister(temp2);
        Register length = asRegister(temp3);

        Label trueLabel = new Label();
        Label falseLabel = new Label();
        Label done = new Label();

        // Load array base addresses.
        masm.leaq(array1, new AMD64Address(asRegister(array1Value), arrayBaseOffset));
        masm.leaq(array2, new AMD64Address(asRegister(array2Value), arrayBaseOffset));

        // Get array length in bytes.
        masm.imull(length, asRegister(lengthValue), arrayIndexScale);
        masm.movl(result, length); // copy

        if (supportsAVX2(crb.target)) {
            emitAVXCompare(crb, masm, result, array1, array2, length, trueLabel, falseLabel);
        } else if (supportsSSE41(crb.target)) {
            emitSSE41Compare(crb, masm, result, array1, array2, length, trueLabel, falseLabel);
        }

        emit8ByteCompare(crb, masm, result, array1, array2, length, trueLabel, falseLabel);
        emitTailCompares(masm, result, array1, array2, length, trueLabel, falseLabel);

        // Return true
        masm.bind(trueLabel);
        masm.movl(result, 1);
        masm.jmpb(done);

        // Return false
        masm.bind(falseLabel);
        masm.xorl(result, result);

        // That's it
        masm.bind(done);
    }

    /**
     * Returns if the underlying AMD64 architecture supports SSE 4.1 instructions.
     *
     * @param target target description of the underlying architecture
     * @return true if the underlying architecture supports SSE 4.1
     */
    private static boolean supportsSSE41(TargetDescription target) {
        AMD64 arch = (AMD64) target.arch;
        return arch.getFeatures().contains(CPUFeature.SSE4_1);
    }

    /**
     * Vector size used in {@link #emitSSE41Compare}.
     */
    private static final int SSE4_1_VECTOR_SIZE = 16;

    /**
     * Emits code that uses SSE4.1 128-bit (16-byte) vector compares.
     */
    private void emitSSE41Compare(CompilationResultBuilder crb, AMD64MacroAssembler masm, Register result, Register array1, Register array2, Register length, Label trueLabel, Label falseLabel) {
        assert supportsSSE41(crb.target);

        Register vector1 = asRegister(vectorTemp1, AMD64Kind.DOUBLE);
        Register vector2 = asRegister(vectorTemp2, AMD64Kind.DOUBLE);

        Label loop = new Label();
        Label compareTail = new Label();

        // Compare 16-byte vectors
        masm.andl(result, SSE4_1_VECTOR_SIZE - 1); // tail count (in bytes)
        masm.andl(length, ~(SSE4_1_VECTOR_SIZE - 1)); // vector count (in bytes)
        masm.jccb(ConditionFlag.Zero, compareTail);

        masm.leaq(array1, new AMD64Address(array1, length, Scale.Times1, 0));
        masm.leaq(array2, new AMD64Address(array2, length, Scale.Times1, 0));
        masm.negq(length);

        // Align the main loop
        masm.align(crb.target.wordSize * 2);
        masm.bind(loop);
        masm.movdqu(vector1, new AMD64Address(array1, length, Scale.Times1, 0));
        masm.movdqu(vector2, new AMD64Address(array2, length, Scale.Times1, 0));
        masm.pxor(vector1, vector2);
        masm.ptest(vector1, vector1);
        masm.jcc(ConditionFlag.NotZero, falseLabel);
        masm.addq(length, SSE4_1_VECTOR_SIZE);
        masm.jcc(ConditionFlag.NotZero, loop);

        masm.testl(result, result);
        masm.jcc(ConditionFlag.Zero, trueLabel);

        /*
         * Compare the remaining bytes with an unaligned memory load aligned to the end of the
         * array.
         */
        masm.movdqu(vector1, new AMD64Address(array1, result, Scale.Times1, -SSE4_1_VECTOR_SIZE));
        masm.movdqu(vector2, new AMD64Address(array2, result, Scale.Times1, -SSE4_1_VECTOR_SIZE));
        masm.pxor(vector1, vector2);
        masm.ptest(vector1, vector1);
        masm.jcc(ConditionFlag.NotZero, falseLabel);
        masm.jmp(trueLabel);

        masm.bind(compareTail);
        masm.movl(length, result);
    }

    /**
     * Returns if the underlying AMD64 architecture supports AVX instructions.
     *
     * @param target target description of the underlying architecture
     * @return true if the underlying architecture supports AVX
     */
    private static boolean supportsAVX2(TargetDescription target) {
        AMD64 arch = (AMD64) target.arch;
        return arch.getFeatures().contains(CPUFeature.AVX2);
    }

    /**
     * Vector size used in {@link #emitAVXCompare}.
     */
    private static final int AVX_VECTOR_SIZE = 32;

    private void emitAVXCompare(CompilationResultBuilder crb, AMD64MacroAssembler masm, Register result, Register array1, Register array2, Register length, Label trueLabel, Label falseLabel) {
        assert supportsAVX2(crb.target);

        Register vector1 = asRegister(vectorTemp1, AMD64Kind.DOUBLE);
        Register vector2 = asRegister(vectorTemp2, AMD64Kind.DOUBLE);

        Label loop = new Label();
        Label compareTail = new Label();

        // Compare 16-byte vectors
        masm.andl(result, AVX_VECTOR_SIZE - 1); // tail count (in bytes)
        masm.andl(length, ~(AVX_VECTOR_SIZE - 1)); // vector count (in bytes)
        masm.jccb(ConditionFlag.Zero, compareTail);

        masm.leaq(array1, new AMD64Address(array1, length, Scale.Times1, 0));
        masm.leaq(array2, new AMD64Address(array2, length, Scale.Times1, 0));
        masm.negq(length);

        // Align the main loop
        masm.align(crb.target.wordSize * 2);
        masm.bind(loop);
        masm.vmovdqu(vector1, new AMD64Address(array1, length, Scale.Times1, 0));
        masm.vmovdqu(vector2, new AMD64Address(array2, length, Scale.Times1, 0));
        masm.vpxor(vector1, vector1, vector2);
        masm.vptest(vector1, vector1);
        masm.jcc(ConditionFlag.NotZero, falseLabel);
        masm.addq(length, AVX_VECTOR_SIZE);
        masm.jcc(ConditionFlag.NotZero, loop);

        masm.testl(result, result);
        masm.jcc(ConditionFlag.Zero, trueLabel);

        /*
         * Compare the remaining bytes with an unaligned memory load aligned to the end of the
         * array.
         */
        masm.vmovdqu(vector1, new AMD64Address(array1, result, Scale.Times1, -AVX_VECTOR_SIZE));
        masm.vmovdqu(vector2, new AMD64Address(array2, result, Scale.Times1, -AVX_VECTOR_SIZE));
        masm.vpxor(vector1, vector1, vector2);
        masm.vptest(vector1, vector1);
        masm.jcc(ConditionFlag.NotZero, falseLabel);
        masm.jmp(trueLabel);

        masm.bind(compareTail);
        masm.movl(length, result);
    }

    /**
     * Vector size used in {@link #emit8ByteCompare}.
     */
    private static final int VECTOR_SIZE = 8;

    /**
     * Emits code that uses 8-byte vector compares.
     */
    private void emit8ByteCompare(CompilationResultBuilder crb, AMD64MacroAssembler masm, Register result, Register array1, Register array2, Register length, Label trueLabel, Label falseLabel) {
        Label loop = new Label();
        Label compareTail = new Label();

        Register temp = asRegister(temp4);

        masm.andl(result, VECTOR_SIZE - 1); // tail count (in bytes)
        masm.andl(length, ~(VECTOR_SIZE - 1));  // vector count (in bytes)
        masm.jccb(ConditionFlag.Zero, compareTail);

        masm.leaq(array1, new AMD64Address(array1, length, Scale.Times1, 0));
        masm.leaq(array2, new AMD64Address(array2, length, Scale.Times1, 0));
        masm.negq(length);

        // Align the main loop
        masm.align(crb.target.wordSize * 2);
        masm.bind(loop);
        masm.movq(temp, new AMD64Address(array1, length, Scale.Times1, 0));
        masm.cmpq(temp, new AMD64Address(array2, length, Scale.Times1, 0));
        masm.jccb(ConditionFlag.NotEqual, falseLabel);
        masm.addq(length, VECTOR_SIZE);
        masm.jccb(ConditionFlag.NotZero, loop);

        masm.testl(result, result);
        masm.jccb(ConditionFlag.Zero, trueLabel);

        /*
         * Compare the remaining bytes with an unaligned memory load aligned to the end of the
         * array.
         */
        masm.movq(temp, new AMD64Address(array1, result, Scale.Times1, -VECTOR_SIZE));
        masm.cmpq(temp, new AMD64Address(array2, result, Scale.Times1, -VECTOR_SIZE));
        masm.jccb(ConditionFlag.NotEqual, falseLabel);
        masm.jmpb(trueLabel);

        masm.bind(compareTail);
        masm.movl(length, result);
    }

    /**
     * Emits code to compare the remaining 1 to 4 bytes.
     */
    private void emitTailCompares(AMD64MacroAssembler masm, Register result, Register array1, Register array2, Register length, Label trueLabel, Label falseLabel) {
        Label compare2Bytes = new Label();
        Label compare1Byte = new Label();

        Register temp = asRegister(temp4);

        if (kind.getByteCount() <= 4) {
            // Compare trailing 4 bytes, if any.
            masm.testl(result, 4);
            masm.jccb(ConditionFlag.Zero, compare2Bytes);
            masm.movl(temp, new AMD64Address(array1, 0));
            masm.cmpl(temp, new AMD64Address(array2, 0));
            masm.jccb(ConditionFlag.NotEqual, falseLabel);

            if (kind.getByteCount() <= 2) {
                // Move array pointers forward.
                masm.leaq(array1, new AMD64Address(array1, 4));
                masm.leaq(array2, new AMD64Address(array2, 4));

                // Compare trailing 2 bytes, if any.
                masm.bind(compare2Bytes);
                masm.testl(result, 2);
                masm.jccb(ConditionFlag.Zero, compare1Byte);
                masm.movzwl(temp, new AMD64Address(array1, 0));
                masm.movzwl(length, new AMD64Address(array2, 0));
                masm.cmpl(temp, length);
                masm.jccb(ConditionFlag.NotEqual, falseLabel);

                // The one-byte tail compare is only required for boolean and byte arrays.
                if (kind.getByteCount() <= 1) {
                    // Move array pointers forward before we compare the last trailing byte.
                    masm.leaq(array1, new AMD64Address(array1, 2));
                    masm.leaq(array2, new AMD64Address(array2, 2));

                    // Compare trailing byte, if any.
                    masm.bind(compare1Byte);
                    masm.testl(result, 1);
                    masm.jccb(ConditionFlag.Zero, trueLabel);
                    masm.movzbl(temp, new AMD64Address(array1, 0));
                    masm.movzbl(length, new AMD64Address(array2, 0));
                    masm.cmpl(temp, length);
                    masm.jccb(ConditionFlag.NotEqual, falseLabel);
                } else {
                    masm.bind(compare1Byte);
                }
            } else {
                masm.bind(compare2Bytes);
            }
        }
    }

    private static final Unsafe UNSAFE = initUnsafe();

    private static Unsafe initUnsafe() {
        try {
            return Unsafe.getUnsafe();
        } catch (SecurityException se) {
            try {
                Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
                theUnsafe.setAccessible(true);
                return (Unsafe) theUnsafe.get(Unsafe.class);
            } catch (Exception e) {
                throw new RuntimeException("exception while trying to get Unsafe", e);
            }
        }
    }
}
