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


package org.graalvm.compiler.lir.amd64;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64Address.Scale;
import org.graalvm.compiler.asm.amd64.AMD64Assembler;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.SSEOp;
import org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.OperandSize;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.asm.amd64.AVXKind;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

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
    private final int constantByteLength;

    @Def({REG}) private Value resultValue;
    @Alive({REG}) private Value array1Value;
    @Alive({REG}) private Value array2Value;
    @Alive({REG}) private Value lengthValue;
    @Temp({REG}) private Value temp1;
    @Temp({REG}) private Value temp2;
    @Temp({REG}) private Value temp3;
    @Temp({REG}) private Value temp4;

    @Temp({REG, ILLEGAL}) private Value temp5;
    @Temp({REG, ILLEGAL}) private Value tempXMM;

    @Temp({REG, ILLEGAL}) private Value vectorTemp1;
    @Temp({REG, ILLEGAL}) private Value vectorTemp2;
    @Temp({REG, ILLEGAL}) private Value vectorTemp3;
    @Temp({REG, ILLEGAL}) private Value vectorTemp4;

    public AMD64ArrayEqualsOp(LIRGeneratorTool tool, JavaKind kind, Value result, Value array1, Value array2, Value length,
                    int constantLength, boolean directPointers, int maxVectorSize) {
        super(TYPE);
        this.kind = kind;

        this.arrayBaseOffset = directPointers ? 0 : tool.getProviders().getMetaAccess().getArrayBaseOffset(kind);
        this.arrayIndexScale = tool.getProviders().getMetaAccess().getArrayIndexScale(kind);

        if (constantLength >= 0 && arrayIndexScale > 1) {
            // scale length
            this.constantByteLength = constantLength << NumUtil.log2Ceil(arrayIndexScale);
        } else {
            this.constantByteLength = constantLength;
        }

        this.resultValue = result;
        this.array1Value = array1;
        this.array2Value = array2;
        this.lengthValue = length;

        // Allocate some temporaries.
        this.temp1 = tool.newVariable(LIRKind.unknownReference(tool.target().arch.getWordKind()));
        this.temp2 = tool.newVariable(LIRKind.unknownReference(tool.target().arch.getWordKind()));
        this.temp3 = tool.newVariable(LIRKind.value(tool.target().arch.getWordKind()));
        this.temp4 = tool.newVariable(LIRKind.value(tool.target().arch.getWordKind()));

        this.temp5 = kind.isNumericFloat() ? tool.newVariable(LIRKind.value(tool.target().arch.getWordKind())) : Value.ILLEGAL;
        if (kind == JavaKind.Float) {
            this.tempXMM = tool.newVariable(LIRKind.value(AMD64Kind.SINGLE));
        } else if (kind == JavaKind.Double) {
            this.tempXMM = tool.newVariable(LIRKind.value(AMD64Kind.DOUBLE));
        } else {
            this.tempXMM = Value.ILLEGAL;
        }

        // We only need the vector temporaries if we generate SSE code.
        if (supportsSSE41(tool.target())) {
            if (canGenerateConstantLengthCompare(tool.target())) {
                LIRKind lirKind = LIRKind.value(supportsAVX2(tool.target()) && (maxVectorSize < 0 || maxVectorSize >= 32) ? AMD64Kind.V256_BYTE : AMD64Kind.V128_BYTE);
                this.vectorTemp1 = tool.newVariable(lirKind);
                this.vectorTemp2 = tool.newVariable(lirKind);
                this.vectorTemp3 = tool.newVariable(lirKind);
                this.vectorTemp4 = tool.newVariable(lirKind);
            } else {
                this.vectorTemp1 = tool.newVariable(LIRKind.value(AMD64Kind.DOUBLE));
                this.vectorTemp2 = tool.newVariable(LIRKind.value(AMD64Kind.DOUBLE));
                this.vectorTemp3 = Value.ILLEGAL;
                this.vectorTemp4 = Value.ILLEGAL;
            }
        } else {
            this.vectorTemp1 = Value.ILLEGAL;
            this.vectorTemp2 = Value.ILLEGAL;
            this.vectorTemp3 = Value.ILLEGAL;
            this.vectorTemp4 = Value.ILLEGAL;
        }
    }

    private boolean canGenerateConstantLengthCompare(TargetDescription target) {
        return constantByteLength >= 0 && kind.isNumericInteger() && supportsSSE41(target);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        Register result = asRegister(resultValue);
        Register array1 = asRegister(temp1);
        Register array2 = asRegister(temp2);

        Label trueLabel = new Label();
        Label falseLabel = new Label();
        Label done = new Label();

        // Load array base addresses.
        masm.leaq(array1, new AMD64Address(asRegister(array1Value), arrayBaseOffset));
        masm.leaq(array2, new AMD64Address(asRegister(array2Value), arrayBaseOffset));

        if (canGenerateConstantLengthCompare(crb.target)) {
            emitConstantLengthArrayCompareBytes(masm, array1, array2, asRegister(temp3), asRegister(temp4),
                            new Register[]{asRegister(vectorTemp1), asRegister(vectorTemp2), asRegister(vectorTemp3), asRegister(vectorTemp4)},
                            falseLabel, constantByteLength, AVXKind.getRegisterSize(vectorTemp1).getBytes());
        } else {
            Register length = asRegister(temp3);

            // Get array length in bytes.
            masm.movl(length, asRegister(lengthValue));

            if (arrayIndexScale > 1) {
                masm.shll(length, NumUtil.log2Ceil(arrayIndexScale)); // scale length
            }

            masm.movl(result, length); // copy

            emitArrayCompare(crb, masm, kind, result, array1, array2, length, temp4, temp5, tempXMM, vectorTemp1, vectorTemp2, trueLabel, falseLabel);
        }

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

    private static void emitArrayCompare(CompilationResultBuilder crb, AMD64MacroAssembler masm, JavaKind kind,
                    Register result, Register array1, Register array2, Register length,
                    Value temp4, Value temp5, Value tempXMM, Value vectorTemp1, Value vectorTemp2,
                    Label trueLabel, Label falseLabel) {
        if (supportsAVX2(crb.target)) {
            emitAVXCompare(crb, masm, kind, result, array1, array2, length, temp4, temp5, tempXMM, vectorTemp1, vectorTemp2, trueLabel, falseLabel);
        } else if (supportsSSE41(crb.target)) {
            // this code is used for AVX as well because our backend correctly ensures that
            // VEX-prefixed instructions are emitted if AVX is supported
            emitSSE41Compare(crb, masm, kind, result, array1, array2, length, temp4, temp5, tempXMM, vectorTemp1, vectorTemp2, trueLabel, falseLabel);
        }
        emit8ByteCompare(crb, masm, kind, result, array1, array2, length, temp4, tempXMM, trueLabel, falseLabel);
        emitTailCompares(masm, kind, result, array1, array2, length, temp4, tempXMM, trueLabel, falseLabel);
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
    private static void emitSSE41Compare(CompilationResultBuilder crb, AMD64MacroAssembler masm, JavaKind kind,
                    Register result, Register array1, Register array2, Register length,
                    Value temp4, Value temp5, Value tempXMM, Value vectorTemp1, Value vectorTemp2,
                    Label trueLabel, Label falseLabel) {
        assert supportsSSE41(crb.target);

        Register vector1 = asRegister(vectorTemp1);
        Register vector2 = asRegister(vectorTemp2);

        Label loop = new Label();
        Label compareTail = new Label();

        boolean requiresNaNCheck = kind.isNumericFloat();
        Label loopCheck = new Label();
        Label nanCheck = new Label();

        // Compare 16-byte vectors
        masm.andl(result, SSE4_1_VECTOR_SIZE - 1); // tail count (in bytes)
        masm.andl(length, ~(SSE4_1_VECTOR_SIZE - 1)); // vector count (in bytes)
        masm.jcc(ConditionFlag.Zero, compareTail);

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
        masm.jcc(ConditionFlag.NotZero, requiresNaNCheck ? nanCheck : falseLabel);

        masm.bind(loopCheck);
        masm.addq(length, SSE4_1_VECTOR_SIZE);
        masm.jcc(ConditionFlag.NotZero, loop);

        masm.testl(result, result);
        masm.jcc(ConditionFlag.Zero, trueLabel);

        if (requiresNaNCheck) {
            Label unalignedCheck = new Label();
            masm.jmpb(unalignedCheck);
            masm.bind(nanCheck);
            emitFloatCompareWithinRange(crb, masm, kind, array1, array2, length, temp4, temp5, tempXMM, 0, falseLabel, SSE4_1_VECTOR_SIZE);
            masm.jmpb(loopCheck);
            masm.bind(unalignedCheck);
        }

        /*
         * Compare the remaining bytes with an unaligned memory load aligned to the end of the
         * array.
         */
        masm.movdqu(vector1, new AMD64Address(array1, result, Scale.Times1, -SSE4_1_VECTOR_SIZE));
        masm.movdqu(vector2, new AMD64Address(array2, result, Scale.Times1, -SSE4_1_VECTOR_SIZE));
        masm.pxor(vector1, vector2);
        masm.ptest(vector1, vector1);
        if (requiresNaNCheck) {
            masm.jcc(ConditionFlag.Zero, trueLabel);
            emitFloatCompareWithinRange(crb, masm, kind, array1, array2, result, temp4, temp5, tempXMM, -SSE4_1_VECTOR_SIZE, falseLabel, SSE4_1_VECTOR_SIZE);
        } else {
            masm.jcc(ConditionFlag.NotZero, falseLabel);
        }
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

    private static void emitAVXCompare(CompilationResultBuilder crb, AMD64MacroAssembler masm, JavaKind kind, Register result,
                    Register array1, Register array2, Register length,
                    Value temp4, Value temp5, Value tempXMM, Value vectorTemp1, Value vectorTemp2,
                    Label trueLabel, Label falseLabel) {
        assert supportsAVX2(crb.target);

        Register vector1 = asRegister(vectorTemp1);
        Register vector2 = asRegister(vectorTemp2);

        Label loop = new Label();
        Label compareTail = new Label();

        boolean requiresNaNCheck = kind.isNumericFloat();
        Label loopCheck = new Label();
        Label nanCheck = new Label();

        // Compare 32-byte vectors
        masm.andl(result, AVX_VECTOR_SIZE - 1); // tail count (in bytes)
        masm.andl(length, ~(AVX_VECTOR_SIZE - 1)); // vector count (in bytes)
        masm.jcc(ConditionFlag.Zero, compareTail);

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
        masm.jcc(ConditionFlag.NotZero, requiresNaNCheck ? nanCheck : falseLabel);

        masm.bind(loopCheck);
        masm.addq(length, AVX_VECTOR_SIZE);
        masm.jcc(ConditionFlag.NotZero, loop);

        masm.testl(result, result);
        masm.jcc(ConditionFlag.Zero, trueLabel);

        if (requiresNaNCheck) {
            Label unalignedCheck = new Label();
            masm.jmpb(unalignedCheck);
            masm.bind(nanCheck);
            emitFloatCompareWithinRange(crb, masm, kind, array1, array2, length, temp4, temp5, tempXMM, 0, falseLabel, AVX_VECTOR_SIZE);
            masm.jmpb(loopCheck);
            masm.bind(unalignedCheck);
        }

        /*
         * Compare the remaining bytes with an unaligned memory load aligned to the end of the
         * array.
         */
        masm.vmovdqu(vector1, new AMD64Address(array1, result, Scale.Times1, -AVX_VECTOR_SIZE));
        masm.vmovdqu(vector2, new AMD64Address(array2, result, Scale.Times1, -AVX_VECTOR_SIZE));
        masm.vpxor(vector1, vector1, vector2);
        masm.vptest(vector1, vector1);
        if (requiresNaNCheck) {
            masm.jcc(ConditionFlag.Zero, trueLabel);
            emitFloatCompareWithinRange(crb, masm, kind, array1, array2, result, temp4, temp5, tempXMM, -AVX_VECTOR_SIZE, falseLabel, AVX_VECTOR_SIZE);
        } else {
            masm.jcc(ConditionFlag.NotZero, falseLabel);
        }
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
    private static void emit8ByteCompare(CompilationResultBuilder crb, AMD64MacroAssembler masm, JavaKind kind, Register result, Register array1, Register array2, Register length, Value temp4,
                    Value tempXMM, Label trueLabel, Label falseLabel) {
        Label loop = new Label();
        Label compareTail = new Label();

        boolean requiresNaNCheck = kind.isNumericFloat();
        Label loopCheck = new Label();
        Label nanCheck = new Label();

        Register temp = asRegister(temp4);

        masm.andl(result, VECTOR_SIZE - 1); // tail count (in bytes)
        masm.andl(length, ~(VECTOR_SIZE - 1));  // vector count (in bytes)
        masm.jcc(ConditionFlag.Zero, compareTail);

        masm.leaq(array1, new AMD64Address(array1, length, Scale.Times1, 0));
        masm.leaq(array2, new AMD64Address(array2, length, Scale.Times1, 0));
        masm.negq(length);

        // Align the main loop
        masm.align(crb.target.wordSize * 2);
        masm.bind(loop);
        masm.movq(temp, new AMD64Address(array1, length, Scale.Times1, 0));
        masm.cmpq(temp, new AMD64Address(array2, length, Scale.Times1, 0));
        masm.jcc(ConditionFlag.NotEqual, requiresNaNCheck ? nanCheck : falseLabel);

        masm.bind(loopCheck);
        masm.addq(length, VECTOR_SIZE);
        masm.jccb(ConditionFlag.NotZero, loop);

        masm.testl(result, result);
        masm.jcc(ConditionFlag.Zero, trueLabel);

        if (requiresNaNCheck) {
            // NaN check is slow path and hence placed outside of the main loop.
            Label unalignedCheck = new Label();
            masm.jmpb(unalignedCheck);
            masm.bind(nanCheck);
            // At most two iterations, unroll in the emitted code.
            for (int offset = 0; offset < VECTOR_SIZE; offset += kind.getByteCount()) {
                emitFloatCompare(masm, kind, array1, array2, length, temp4, tempXMM, offset, falseLabel, kind.getByteCount() == VECTOR_SIZE);
            }
            masm.jmpb(loopCheck);
            masm.bind(unalignedCheck);
        }

        /*
         * Compare the remaining bytes with an unaligned memory load aligned to the end of the
         * array.
         */
        masm.movq(temp, new AMD64Address(array1, result, Scale.Times1, -VECTOR_SIZE));
        masm.cmpq(temp, new AMD64Address(array2, result, Scale.Times1, -VECTOR_SIZE));
        if (requiresNaNCheck) {
            masm.jcc(ConditionFlag.Equal, trueLabel);
            // At most two iterations, unroll in the emitted code.
            for (int offset = 0; offset < VECTOR_SIZE; offset += kind.getByteCount()) {
                emitFloatCompare(masm, kind, array1, array2, result, temp4, tempXMM, -VECTOR_SIZE + offset, falseLabel, kind.getByteCount() == VECTOR_SIZE);
            }
        } else {
            masm.jccb(ConditionFlag.NotEqual, falseLabel);
        }
        masm.jmpb(trueLabel);

        masm.bind(compareTail);
        masm.movl(length, result);
    }

    /**
     * Emits code to compare the remaining 1 to 4 bytes.
     */
    private static void emitTailCompares(AMD64MacroAssembler masm, JavaKind kind, Register result, Register array1, Register array2, Register length, Value temp4, Value tempXMM,
                    Label trueLabel, Label falseLabel) {
        Label compare2Bytes = new Label();
        Label compare1Byte = new Label();

        Register temp = asRegister(temp4);

        if (kind.getByteCount() <= 4) {
            // Compare trailing 4 bytes, if any.
            masm.testl(result, 4);
            masm.jccb(ConditionFlag.Zero, compare2Bytes);
            masm.movl(temp, new AMD64Address(array1, 0));
            masm.cmpl(temp, new AMD64Address(array2, 0));
            if (kind == JavaKind.Float) {
                masm.jccb(ConditionFlag.Equal, trueLabel);
                emitFloatCompare(masm, kind, array1, array2, Register.None, temp4, tempXMM, 0, falseLabel, true);
                masm.jmpb(trueLabel);
            } else {
                masm.jccb(ConditionFlag.NotEqual, falseLabel);
            }
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

    /**
     * Emits code to fall through if {@code src} is NaN, otherwise jump to {@code branchOrdered}.
     */
    private static void emitNaNCheck(AMD64MacroAssembler masm, JavaKind kind, Value tempXMM, AMD64Address src, Label branchIfNonNaN) {
        assert kind.isNumericFloat();
        Register tempXMMReg = asRegister(tempXMM);
        if (kind == JavaKind.Float) {
            masm.movflt(tempXMMReg, src);
        } else {
            masm.movdbl(tempXMMReg, src);
        }
        SSEOp.UCOMIS.emit(masm, kind == JavaKind.Float ? OperandSize.PS : OperandSize.PD, tempXMMReg, tempXMMReg);
        masm.jcc(ConditionFlag.NoParity, branchIfNonNaN);
    }

    /**
     * Emits code to compare if two floats are bitwise equal or both NaN.
     */
    private static void emitFloatCompare(AMD64MacroAssembler masm, JavaKind kind, Register base1, Register base2, Register index, Value temp4, Value tempXMM, int offset, Label falseLabel,
                    boolean skipBitwiseCompare) {
        AMD64Address address1 = new AMD64Address(base1, index, Scale.Times1, offset);
        AMD64Address address2 = new AMD64Address(base2, index, Scale.Times1, offset);

        Label bitwiseEqual = new Label();

        if (!skipBitwiseCompare) {
            // Bitwise compare
            Register temp = asRegister(temp4);

            if (kind == JavaKind.Float) {
                masm.movl(temp, address1);
                masm.cmpl(temp, address2);
            } else {
                masm.movq(temp, address1);
                masm.cmpq(temp, address2);
            }
            masm.jccb(ConditionFlag.Equal, bitwiseEqual);
        }

        emitNaNCheck(masm, kind, tempXMM, address1, falseLabel);
        emitNaNCheck(masm, kind, tempXMM, address2, falseLabel);

        masm.bind(bitwiseEqual);
    }

    /**
     * Emits code to compare float equality within a range.
     */
    private static void emitFloatCompareWithinRange(CompilationResultBuilder crb, AMD64MacroAssembler masm, JavaKind kind, Register base1, Register base2, Register index, Value temp4, Value temp5,
                    Value tempXMM, int offset, Label falseLabel, int range) {
        assert kind.isNumericFloat();
        Label loop = new Label();
        Register i = asRegister(temp5);

        masm.movq(i, range);
        masm.negq(i);
        // Align the main loop
        masm.align(crb.target.wordSize * 2);
        masm.bind(loop);
        emitFloatCompare(masm, kind, base1, base2, index, temp4, tempXMM, offset, falseLabel, kind.getByteCount() == range);
        masm.addq(index, kind.getByteCount());
        masm.addq(i, kind.getByteCount());
        masm.jccb(ConditionFlag.NotZero, loop);
        // Floats within the range are equal, revert change to the register index
        masm.subq(index, range);
    }

    /**
     * Emits specialized assembly for checking equality of memory regions
     * {@code arrayPtr1[0..nBytes]} and {@code arrayPtr2[0..nBytes]}. If they match, execution
     * continues directly after the emitted code block, otherwise we jump to {@code noMatch}.
     */
    private static void emitConstantLengthArrayCompareBytes(
                    AMD64MacroAssembler asm,
                    Register arrayPtr1,
                    Register arrayPtr2,
                    Register tmp1,
                    Register tmp2,
                    Register[] tmpVectors,
                    Label noMatch,
                    int nBytes,
                    int bytesPerVector) {
        assert bytesPerVector >= 16;
        if (nBytes == 0) {
            // do nothing
            return;
        }
        if (nBytes < 16) {
            // array is shorter than any vector register, use regular CMP instructions
            int movSize = (nBytes < 2) ? 1 : ((nBytes < 4) ? 2 : ((nBytes < 8) ? 4 : 8));
            emitMovBytes(asm, tmp1, new AMD64Address(arrayPtr1), movSize);
            emitMovBytes(asm, tmp2, new AMD64Address(arrayPtr2), movSize);
            emitCmpBytes(asm, tmp1, tmp2, movSize);
            asm.jcc(AMD64Assembler.ConditionFlag.NotEqual, noMatch);
            if (nBytes > movSize) {
                emitMovBytes(asm, tmp1, new AMD64Address(arrayPtr1, nBytes - movSize), movSize);
                emitMovBytes(asm, tmp2, new AMD64Address(arrayPtr2, nBytes - movSize), movSize);
                emitCmpBytes(asm, tmp1, tmp2, movSize);
                asm.jcc(AMD64Assembler.ConditionFlag.NotEqual, noMatch);
            }
        } else if (nBytes < 32 && bytesPerVector >= 32) {
            // we could use YMM registers, but the array is too short, force XMM registers
            int bytesPerXMMVector = AVXKind.AVXSize.XMM.getBytes();
            AMD64Assembler.VexMoveOp.VMOVDQU.emit(asm, AVXKind.AVXSize.XMM, tmpVectors[0], new AMD64Address(arrayPtr1));
            AMD64Assembler.VexMoveOp.VMOVDQU.emit(asm, AVXKind.AVXSize.XMM, tmpVectors[1], new AMD64Address(arrayPtr2));
            AMD64Assembler.VexRVMOp.VPXOR.emit(asm, AVXKind.AVXSize.XMM, tmpVectors[0], tmpVectors[0], tmpVectors[1]);
            if (nBytes > bytesPerXMMVector) {
                AMD64Assembler.VexMoveOp.VMOVDQU.emit(asm, AVXKind.AVXSize.XMM, tmpVectors[2], new AMD64Address(arrayPtr1, nBytes - bytesPerXMMVector));
                AMD64Assembler.VexMoveOp.VMOVDQU.emit(asm, AVXKind.AVXSize.XMM, tmpVectors[3], new AMD64Address(arrayPtr2, nBytes - bytesPerXMMVector));
                AMD64Assembler.VexRVMOp.VPXOR.emit(asm, AVXKind.AVXSize.XMM, tmpVectors[2], tmpVectors[2], tmpVectors[3]);
                AMD64Assembler.VexRMOp.VPTEST.emit(asm, AVXKind.AVXSize.XMM, tmpVectors[2], tmpVectors[2]);
                asm.jcc(AMD64Assembler.ConditionFlag.NotZero, noMatch);
            }
            AMD64Assembler.VexRMOp.VPTEST.emit(asm, AVXKind.AVXSize.XMM, tmpVectors[0], tmpVectors[0]);
            asm.jcc(AMD64Assembler.ConditionFlag.NotZero, noMatch);
        } else if (bytesPerVector >= 32) {
            // AVX2 supported, use YMM vectors
            assert asm.supports(CPUFeature.AVX2);
            int loopCount = nBytes / (bytesPerVector * 2);
            int rest = nBytes % (bytesPerVector * 2);
            if (loopCount > 0) {
                if (0 < rest && rest < bytesPerVector) {
                    loopCount--;
                }
                if (loopCount > 0) {
                    if (loopCount > 1) {
                        asm.movl(tmp1, loopCount);
                    }
                    Label loopBegin = new Label();
                    asm.bind(loopBegin);
                    asm.vmovdqu(tmpVectors[0], new AMD64Address(arrayPtr1));
                    asm.vmovdqu(tmpVectors[1], new AMD64Address(arrayPtr2));
                    asm.vmovdqu(tmpVectors[2], new AMD64Address(arrayPtr1, bytesPerVector));
                    asm.vmovdqu(tmpVectors[3], new AMD64Address(arrayPtr2, bytesPerVector));
                    asm.vpxor(tmpVectors[0], tmpVectors[0], tmpVectors[1]);
                    asm.vpxor(tmpVectors[2], tmpVectors[2], tmpVectors[3]);
                    asm.vptest(tmpVectors[0], tmpVectors[0]);
                    asm.jcc(AMD64Assembler.ConditionFlag.NotZero, noMatch);
                    asm.vptest(tmpVectors[2], tmpVectors[2]);
                    asm.jcc(AMD64Assembler.ConditionFlag.NotZero, noMatch);
                    asm.addq(arrayPtr1, bytesPerVector * 2);
                    asm.addq(arrayPtr2, bytesPerVector * 2);
                    if (loopCount > 1) {
                        asm.decrementl(tmp1);
                        asm.jcc(AMD64Assembler.ConditionFlag.NotZero, loopBegin);
                    }
                }
                if (0 < rest && rest < bytesPerVector) {
                    asm.vmovdqu(tmpVectors[0], new AMD64Address(arrayPtr1));
                    asm.vmovdqu(tmpVectors[1], new AMD64Address(arrayPtr2));
                    asm.vmovdqu(tmpVectors[2], new AMD64Address(arrayPtr1, bytesPerVector));
                    asm.vmovdqu(tmpVectors[3], new AMD64Address(arrayPtr2, bytesPerVector));
                    asm.vpxor(tmpVectors[0], tmpVectors[0], tmpVectors[1]);
                    asm.vpxor(tmpVectors[2], tmpVectors[2], tmpVectors[3]);
                    asm.vptest(tmpVectors[0], tmpVectors[0]);
                    asm.jcc(AMD64Assembler.ConditionFlag.NotZero, noMatch);
                    asm.vptest(tmpVectors[2], tmpVectors[2]);
                    asm.jcc(AMD64Assembler.ConditionFlag.NotZero, noMatch);
                    asm.vmovdqu(tmpVectors[0], new AMD64Address(arrayPtr1, bytesPerVector + rest));
                    asm.vmovdqu(tmpVectors[1], new AMD64Address(arrayPtr2, bytesPerVector + rest));
                    asm.vpxor(tmpVectors[0], tmpVectors[0], tmpVectors[1]);
                    asm.vptest(tmpVectors[0], tmpVectors[0]);
                    asm.jcc(AMD64Assembler.ConditionFlag.NotZero, noMatch);
                }
            }
            if (rest >= bytesPerVector) {
                asm.vmovdqu(tmpVectors[0], new AMD64Address(arrayPtr1));
                asm.vmovdqu(tmpVectors[1], new AMD64Address(arrayPtr2));
                asm.vpxor(tmpVectors[0], tmpVectors[0], tmpVectors[1]);
                if (rest > bytesPerVector) {
                    asm.vmovdqu(tmpVectors[2], new AMD64Address(arrayPtr1, rest - bytesPerVector));
                    asm.vmovdqu(tmpVectors[3], new AMD64Address(arrayPtr2, rest - bytesPerVector));
                    asm.vpxor(tmpVectors[2], tmpVectors[2], tmpVectors[3]);
                    asm.vptest(tmpVectors[2], tmpVectors[2]);
                    asm.jcc(AMD64Assembler.ConditionFlag.NotZero, noMatch);
                }
                asm.vptest(tmpVectors[0], tmpVectors[0]);
                asm.jcc(AMD64Assembler.ConditionFlag.NotZero, noMatch);
            }
        } else {
            // on AVX or SSE, use XMM vectors
            int loopCount = nBytes / (bytesPerVector * 2);
            int rest = nBytes % (bytesPerVector * 2);
            if (loopCount > 0) {
                if (0 < rest && rest < bytesPerVector) {
                    loopCount--;
                }
                if (loopCount > 0) {
                    if (loopCount > 1) {
                        asm.movl(tmp1, loopCount);
                    }
                    Label loopBegin = new Label();
                    asm.bind(loopBegin);
                    asm.movdqu(tmpVectors[0], new AMD64Address(arrayPtr1));
                    asm.movdqu(tmpVectors[1], new AMD64Address(arrayPtr2));
                    asm.movdqu(tmpVectors[2], new AMD64Address(arrayPtr1, bytesPerVector));
                    asm.movdqu(tmpVectors[3], new AMD64Address(arrayPtr2, bytesPerVector));
                    asm.pxor(tmpVectors[0], tmpVectors[1]);
                    asm.pxor(tmpVectors[2], tmpVectors[3]);
                    asm.ptest(tmpVectors[0], tmpVectors[0]);
                    asm.jcc(AMD64Assembler.ConditionFlag.NotZero, noMatch);
                    asm.ptest(tmpVectors[2], tmpVectors[2]);
                    asm.jcc(AMD64Assembler.ConditionFlag.NotZero, noMatch);
                    asm.addq(arrayPtr1, bytesPerVector * 2);
                    asm.addq(arrayPtr2, bytesPerVector * 2);
                    if (loopCount > 1) {
                        asm.decrementl(tmp1);
                        asm.jcc(AMD64Assembler.ConditionFlag.NotZero, loopBegin);
                    }
                }
                if (0 < rest && rest < bytesPerVector) {
                    asm.movdqu(tmpVectors[0], new AMD64Address(arrayPtr1));
                    asm.movdqu(tmpVectors[1], new AMD64Address(arrayPtr2));
                    asm.movdqu(tmpVectors[2], new AMD64Address(arrayPtr1, bytesPerVector));
                    asm.movdqu(tmpVectors[3], new AMD64Address(arrayPtr2, bytesPerVector));
                    asm.pxor(tmpVectors[0], tmpVectors[1]);
                    asm.pxor(tmpVectors[2], tmpVectors[3]);
                    asm.ptest(tmpVectors[0], tmpVectors[0]);
                    asm.jcc(AMD64Assembler.ConditionFlag.NotZero, noMatch);
                    asm.ptest(tmpVectors[2], tmpVectors[2]);
                    asm.jcc(AMD64Assembler.ConditionFlag.NotZero, noMatch);
                    asm.movdqu(tmpVectors[0], new AMD64Address(arrayPtr1, bytesPerVector + rest));
                    asm.movdqu(tmpVectors[1], new AMD64Address(arrayPtr2, bytesPerVector + rest));
                    asm.pxor(tmpVectors[0], tmpVectors[1]);
                    asm.ptest(tmpVectors[0], tmpVectors[0]);
                    asm.jcc(AMD64Assembler.ConditionFlag.NotZero, noMatch);
                }
            }
            if (rest >= bytesPerVector) {
                asm.movdqu(tmpVectors[0], new AMD64Address(arrayPtr1));
                asm.movdqu(tmpVectors[1], new AMD64Address(arrayPtr2));
                asm.pxor(tmpVectors[0], tmpVectors[1]);
                if (rest > bytesPerVector) {
                    asm.movdqu(tmpVectors[2], new AMD64Address(arrayPtr1, rest - bytesPerVector));
                    asm.movdqu(tmpVectors[3], new AMD64Address(arrayPtr2, rest - bytesPerVector));
                    asm.pxor(tmpVectors[2], tmpVectors[3]);
                    asm.ptest(tmpVectors[2], tmpVectors[2]);
                    asm.jcc(AMD64Assembler.ConditionFlag.NotZero, noMatch);
                }
                asm.ptest(tmpVectors[0], tmpVectors[0]);
                asm.jcc(AMD64Assembler.ConditionFlag.NotZero, noMatch);
            }
        }
    }

    private static void emitMovBytes(AMD64MacroAssembler asm, Register dst, AMD64Address src, int size) {
        switch (size) {
            case 1:
                asm.movzbl(dst, src);
                break;
            case 2:
                asm.movzwl(dst, src);
                break;
            case 4:
                asm.movl(dst, src);
                break;
            case 8:
                asm.movq(dst, src);
                break;
            default:
                throw new IllegalStateException();
        }
    }

    private static void emitCmpBytes(AMD64MacroAssembler asm, Register dst, Register src, int size) {
        if (size < 8) {
            asm.cmpl(dst, src);
        } else {
            asm.cmpq(dst, src);
        }
    }
}
