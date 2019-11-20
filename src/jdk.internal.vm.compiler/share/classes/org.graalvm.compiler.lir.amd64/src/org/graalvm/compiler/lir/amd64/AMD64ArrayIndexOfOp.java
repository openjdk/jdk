/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isRegister;
import static jdk.vm.ci.code.ValueUtil.isStackSlot;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.CONST;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.STACK;

import java.util.Objects;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64Address.Scale;
import org.graalvm.compiler.asm.amd64.AMD64Assembler;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64RMOp;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.VexMoveOp;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRMIOp;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRMOp;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRVMOp;
import org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.OperandSize;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.asm.amd64.AVXKind;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

/**
 */
@Opcode("AMD64_ARRAY_INDEX_OF")
public final class AMD64ArrayIndexOfOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64ArrayIndexOfOp> TYPE = LIRInstructionClass.create(AMD64ArrayIndexOfOp.class);

    private final JavaKind valueKind;
    private final int nValues;
    private final boolean findTwoConsecutive;
    private final AMD64Kind vectorKind;
    private final int arrayBaseOffset;
    private final Scale arrayIndexScale;

    @Def({REG}) protected Value resultValue;
    @Alive({REG}) protected Value arrayPtrValue;
    @Alive({REG}) protected Value arrayLengthValue;
    @Use({REG}) protected Value fromIndexValue;
    @Alive({REG, STACK, CONST}) protected Value searchValue1;
    @Alive({REG, STACK, CONST, ILLEGAL}) protected Value searchValue2;
    @Alive({REG, STACK, CONST, ILLEGAL}) protected Value searchValue3;
    @Alive({REG, STACK, CONST, ILLEGAL}) protected Value searchValue4;
    @Temp({REG}) protected Value comparisonResult1;
    @Temp({REG, ILLEGAL}) protected Value comparisonResult2;
    @Temp({REG, ILLEGAL}) protected Value vectorCompareVal1;
    @Temp({REG, ILLEGAL}) protected Value vectorCompareVal2;
    @Temp({REG, ILLEGAL}) protected Value vectorCompareVal3;
    @Temp({REG, ILLEGAL}) protected Value vectorCompareVal4;
    @Temp({REG, ILLEGAL}) protected Value vectorArray1;
    @Temp({REG, ILLEGAL}) protected Value vectorArray2;
    @Temp({REG, ILLEGAL}) protected Value vectorArray3;
    @Temp({REG, ILLEGAL}) protected Value vectorArray4;

    public AMD64ArrayIndexOfOp(JavaKind arrayKind, JavaKind valueKind, boolean findTwoConsecutive, int maxVectorSize, LIRGeneratorTool tool,
                    Value result, Value arrayPtr, Value arrayLength, Value fromIndex, Value... searchValues) {
        super(TYPE);
        this.valueKind = valueKind;
        this.arrayBaseOffset = tool.getProviders().getMetaAccess().getArrayBaseOffset(arrayKind);
        this.arrayIndexScale = Objects.requireNonNull(Scale.fromInt(tool.getProviders().getMetaAccess().getArrayIndexScale(valueKind)));
        this.findTwoConsecutive = findTwoConsecutive;
        assert 0 < searchValues.length && searchValues.length <= 4;
        assert byteMode(valueKind) || charMode(valueKind);
        assert supports(tool, CPUFeature.SSE2) || supports(tool, CPUFeature.AVX) || supportsAVX2(tool);
        nValues = searchValues.length;
        assert !findTwoConsecutive || nValues == 1;
        resultValue = result;
        arrayPtrValue = arrayPtr;
        arrayLengthValue = arrayLength;
        fromIndexValue = fromIndex;
        searchValue1 = searchValues[0];
        searchValue2 = nValues > 1 ? searchValues[1] : Value.ILLEGAL;
        searchValue3 = nValues > 2 ? searchValues[2] : Value.ILLEGAL;
        searchValue4 = nValues > 3 ? searchValues[3] : Value.ILLEGAL;
        comparisonResult1 = tool.newVariable(LIRKind.value(tool.target().arch.getWordKind()));
        comparisonResult2 = findTwoConsecutive ? tool.newVariable(LIRKind.value(tool.target().arch.getWordKind())) : Value.ILLEGAL;
        vectorKind = supportsAVX2(tool) && (maxVectorSize < 0 || maxVectorSize >= 32) ? byteMode(valueKind) ? AMD64Kind.V256_BYTE : AMD64Kind.V256_WORD
                        : byteMode(valueKind) ? AMD64Kind.V128_BYTE : AMD64Kind.V128_WORD;
        vectorCompareVal1 = tool.newVariable(LIRKind.value(vectorKind));
        vectorCompareVal2 = nValues > 1 ? tool.newVariable(LIRKind.value(vectorKind)) : Value.ILLEGAL;
        vectorCompareVal3 = nValues > 2 ? tool.newVariable(LIRKind.value(vectorKind)) : Value.ILLEGAL;
        vectorCompareVal4 = nValues > 3 ? tool.newVariable(LIRKind.value(vectorKind)) : Value.ILLEGAL;
        vectorArray1 = tool.newVariable(LIRKind.value(vectorKind));
        vectorArray2 = tool.newVariable(LIRKind.value(vectorKind));
        vectorArray3 = tool.newVariable(LIRKind.value(vectorKind));
        vectorArray4 = tool.newVariable(LIRKind.value(vectorKind));
    }

    private static boolean byteMode(JavaKind kind) {
        return kind == JavaKind.Byte;
    }

    private static boolean charMode(JavaKind kind) {
        return kind == JavaKind.Char;
    }

    private JavaKind getComparisonKind() {
        return findTwoConsecutive ? (byteMode(valueKind) ? JavaKind.Char : JavaKind.Int) : valueKind;
    }

    private AVXKind.AVXSize getVectorSize() {
        return AVXKind.getDataSize(vectorKind);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler asm) {
        int nVectors = nValues == 1 ? 4 : nValues == 2 ? 2 : 1;
        Register arrayPtr = asRegister(arrayPtrValue);
        Register arrayLength = asRegister(arrayLengthValue);
        Register fromIndex = asRegister(fromIndexValue);
        Register index = asRegister(resultValue);
        Value[] searchValue = {
                        nValues > 0 ? searchValue1 : null,
                        nValues > 1 ? searchValue2 : null,
                        nValues > 2 ? searchValue3 : null,
                        nValues > 3 ? searchValue4 : null,
        };
        Register[] vecCmp = {
                        nValues > 0 ? asRegister(vectorCompareVal1) : null,
                        nValues > 1 ? asRegister(vectorCompareVal2) : null,
                        nValues > 2 ? asRegister(vectorCompareVal3) : null,
                        nValues > 3 ? asRegister(vectorCompareVal4) : null,
        };
        Register[] vecArray = {
                        asRegister(vectorArray1),
                        asRegister(vectorArray2),
                        asRegister(vectorArray3),
                        asRegister(vectorArray4),
        };
        Register[] cmpResult = {
                        asRegister(comparisonResult1),
                        findTwoConsecutive ? asRegister(comparisonResult2) : null,
        };
        Label ret = new Label();

        Label bulkVectorLoop = new Label();
        Label singleVectorLoop = new Label();
        Label[] vectorFound = {
                        new Label(),
                        new Label(),
                        new Label(),
                        new Label(),
        };
        Label runVectorized = new Label();
        Label elementWiseLoop = new Label();
        Label elementWiseFound = new Label();
        Label elementWiseNotFound = new Label();
        Label skipBulkVectorLoop = new Label();
        int vectorSize = getVectorSize().getBytes() / valueKind.getByteCount();
        int bulkSize = vectorSize * nVectors;
        JavaKind vectorCompareKind = valueKind;
        if (findTwoConsecutive) {
            bulkSize /= 2;
            vectorCompareKind = byteMode(valueKind) ? JavaKind.Char : JavaKind.Int;
        }
        // index = fromIndex + vectorSize (+1 if findTwoConsecutive)
        // important: this must be the first register manipulation, since fromIndex is
        // annotated with @Use
        asm.leaq(index, new AMD64Address(fromIndex, vectorSize + (findTwoConsecutive ? 1 : 0)));

        // check if vector vector load is in bounds
        asm.cmpq(index, arrayLength);
        asm.jccb(AMD64Assembler.ConditionFlag.LessEqual, runVectorized);

        // search range is smaller than vector size, do element-wise comparison

        // index = fromIndex (+ 1 if findTwoConsecutive)
        asm.subq(index, vectorSize);
        // check if enough array slots remain
        asm.cmpq(index, arrayLength);
        asm.jccb(AMD64Assembler.ConditionFlag.GreaterEqual, elementWiseNotFound);
        // compare one-by-one
        asm.bind(elementWiseLoop);
        // check for match
        OperandSize cmpSize = getOpSize(getComparisonKind());
        // address = findTwoConsecutive ? array[index - 1] : array[index]
        AMD64Address arrayAddr = new AMD64Address(arrayPtr, index, arrayIndexScale, arrayBaseOffset - (findTwoConsecutive ? valueKind.getByteCount() : 0));
        boolean valuesOnStack = searchValuesOnStack(searchValue);
        if (valuesOnStack) {
            (cmpSize == OperandSize.BYTE ? AMD64RMOp.MOVB : AMD64RMOp.MOV).emit(asm, cmpSize, cmpResult[0], arrayAddr);
            for (int i = 0; i < nValues; i++) {
                if (isConstant(searchValue[i])) {
                    int imm = asConstant(searchValue[i]).asInt();
                    AMD64Assembler.AMD64BinaryArithmetic.CMP.getMIOpcode(cmpSize, NumUtil.isByte(imm)).emit(asm, cmpSize, cmpResult[0], imm);
                } else if (isStackSlot(searchValue[i])) {
                    AMD64Assembler.AMD64BinaryArithmetic.CMP.getRMOpcode(cmpSize).emit(asm, cmpSize, cmpResult[0], (AMD64Address) crb.asAddress(searchValue[i]));
                } else {
                    AMD64Assembler.AMD64BinaryArithmetic.CMP.getRMOpcode(cmpSize).emit(asm, cmpSize, cmpResult[0], asRegister(searchValue[i]));
                }
                asm.jccb(AMD64Assembler.ConditionFlag.Equal, elementWiseFound);
            }
        } else {
            for (int i = 0; i < nValues; i++) {
                if (isConstant(searchValue[i])) {
                    int imm = asConstant(searchValue[i]).asInt();
                    AMD64Assembler.AMD64BinaryArithmetic.CMP.getMIOpcode(cmpSize, NumUtil.isByte(imm)).emit(asm, cmpSize, arrayAddr, imm);
                } else {
                    AMD64Assembler.AMD64BinaryArithmetic.CMP.getRMOpcode(cmpSize).emit(asm, cmpSize, asRegister(searchValue[i]), arrayAddr);
                }
                asm.jccb(AMD64Assembler.ConditionFlag.Equal, elementWiseFound);
            }
        }
        // adjust index
        asm.incrementq(index, 1);
        // continue loop
        asm.cmpq(index, arrayLength);
        asm.jccb(AMD64Assembler.ConditionFlag.Less, elementWiseLoop);

        asm.bind(elementWiseNotFound);
        asm.xorq(index, index);

        if (findTwoConsecutive) {
            asm.bind(elementWiseFound);
            asm.decrementq(index, 1);
        } else {
            asm.decrementq(index, 1);
            asm.bind(elementWiseFound);
        }
        asm.jmp(ret);

        // vectorized implementation
        asm.bind(runVectorized);

        // move search values to vectors
        for (int i = 0; i < nValues; i++) {
            // fill comparison vector with copies of the search value
            broadcastSearchValue(crb, asm, vecCmp[i], searchValue[i], cmpResult[0], vecArray[0]);
        }

        // do one unaligned vector comparison pass and adjust alignment afterwards
        emitVectorCompare(asm, vectorCompareKind, findTwoConsecutive ? 2 : 1, arrayPtr, index, vecCmp, vecArray, cmpResult, vectorFound, false, false);

        // adjust index to vector size alignment
        asm.leaq(cmpResult[0], new AMD64Address(arrayPtr, arrayBaseOffset));
        if (charMode(valueKind)) {
            asm.shrq(cmpResult[0], 1);
        }
        asm.addq(index, cmpResult[0]);
        // adjust to next lower multiple of vector size
        asm.andq(index, ~(vectorSize - 1));
        asm.subq(index, cmpResult[0]);
        // add bulk size
        asm.addq(index, bulkSize);

        // check if there are enough array slots remaining for the bulk loop
        asm.cmpq(index, arrayLength);
        asm.jccb(AMD64Assembler.ConditionFlag.Greater, skipBulkVectorLoop);

        emitAlign(crb, asm);
        asm.bind(bulkVectorLoop);
        // memory-aligned bulk comparison
        emitVectorCompare(asm, vectorCompareKind, nVectors, arrayPtr, index, vecCmp, vecArray, cmpResult, vectorFound, false, !findTwoConsecutive);
        // adjust index
        asm.addq(index, bulkSize);
        // check if there are enough array slots remaining for the bulk loop
        asm.cmpq(index, arrayLength);
        asm.jccb(AMD64Assembler.ConditionFlag.LessEqual, bulkVectorLoop);

        asm.bind(skipBulkVectorLoop);
        if ((findTwoConsecutive && nVectors == 2) || nVectors == 1) {
            // do last load from end of array
            asm.movq(index, arrayLength);
            // compare
            emitVectorCompare(asm, vectorCompareKind, findTwoConsecutive ? 2 : 1, arrayPtr, index, vecCmp, vecArray, cmpResult, vectorFound, true, false);
        } else {
            // remove bulk offset
            asm.subq(index, bulkSize);
            emitAlign(crb, asm);
            // same loop as bulkVectorLoop, with only one vector
            asm.bind(singleVectorLoop);
            // add vector size
            asm.addq(index, vectorSize);
            // check if vector load is in bounds
            asm.cmpq(index, arrayLength);
            // if load would be over bounds, set the load to the end of the array
            asm.cmovq(AMD64Assembler.ConditionFlag.Greater, index, arrayLength);
            // compare
            emitVectorCompare(asm, vectorCompareKind, findTwoConsecutive ? 2 : 1, arrayPtr, index, vecCmp, vecArray, cmpResult, vectorFound, true, false);
            // check if there are enough array slots remaining for the loop
            asm.cmpq(index, arrayLength);
            asm.jccb(AMD64Assembler.ConditionFlag.Less, singleVectorLoop);
        }

        asm.movl(index, -1);
        asm.jmpb(ret);

        if (findTwoConsecutive) {
            Label vectorFound2Done = new Label();

            // vectorFound[0] and vectorFound[2] behave like the single-char case
            asm.bind(vectorFound[2]);
            // add static offset
            asm.subq(index, getResultIndexDelta(2));
            asm.jmpb(vectorFound2Done);

            asm.bind(vectorFound[0]);
            // add static offset
            asm.subq(index, getResultIndexDelta(0));
            asm.bind(vectorFound2Done);
            // find offset
            asm.bsfq(cmpResult[0], cmpResult[0]);
            if (charMode(valueKind)) {
                // convert byte offset to chars
                asm.shrl(cmpResult[0], 1);
            }
            // add offset to index
            asm.addq(index, cmpResult[0]);
            asm.jmpb(ret);

            Label minResult = new Label();
            Label minResultDone = new Label();

            // in vectorFound[1] and vectorFound[3], we have to check the results 0 and 2 as well
            if (nVectors > 2) {
                asm.bind(vectorFound[3]);
                // add offset
                asm.subq(index, getResultIndexDelta(3));
                asm.jmpb(minResult);
            }

            asm.bind(vectorFound[1]);
            // add offset
            asm.subq(index, getResultIndexDelta(1));

            asm.bind(minResult);
            // find offset 0
            asm.bsfq(cmpResult[1], cmpResult[1]);
            // check if second result is also a match
            asm.testq(cmpResult[0], cmpResult[0]);
            asm.jccb(AMD64Assembler.ConditionFlag.Zero, minResultDone);
            // find offset 1
            asm.bsfq(cmpResult[0], cmpResult[0]);
            asm.addq(cmpResult[0], valueKind.getByteCount());
            // if first result is greater than second, replace it with the second result
            asm.cmpq(cmpResult[1], cmpResult[0]);
            asm.cmovq(AMD64Assembler.ConditionFlag.Greater, cmpResult[1], cmpResult[0]);
            asm.bind(minResultDone);
            if (charMode(valueKind)) {
                // convert byte offset to chars
                asm.shrl(cmpResult[1], 1);
            }
            // add offset to index
            asm.addq(index, cmpResult[1]);
        } else {
            Label end = new Label();
            for (int i = 0; i < nVectors; i++) {
                asm.bind(vectorFound[i]);
                // add static offset
                asm.subq(index, getResultIndexDelta(i));
                if (i < nVectors - 1) {
                    asm.jmpb(end);
                }
            }
            asm.bind(end);
            // find offset
            asm.bsfq(cmpResult[0], cmpResult[0]);
            if (charMode(valueKind)) {
                // convert byte offset to chars
                asm.shrl(cmpResult[0], 1);
            }
            // add offset to index
            asm.addq(index, cmpResult[0]);
        }
        asm.bind(ret);
    }

    private boolean searchValuesOnStack(Value[] searchValue) {
        for (int i = 0; i < nValues; i++) {
            if (isStackSlot(searchValue[i])) {
                return true;
            }
        }
        return false;
    }

    private int getResultIndexDelta(int i) {
        return (((findTwoConsecutive ? i / 2 : i) + 1) * (getVectorSize().getBytes() / valueKind.getByteCount())) + (findTwoConsecutive ? (i & 1) : 0);
    }

    private int getVectorOffset(int i) {
        return arrayBaseOffset - getResultIndexDelta(i) * valueKind.getByteCount();
    }

    private void broadcastSearchValue(CompilationResultBuilder crb, AMD64MacroAssembler asm, Register dst, Value srcVal, Register tmpReg, Register tmpVector) {
        Register src = asRegOrTmpReg(crb, asm, srcVal, tmpReg);
        if (asm.supports(CPUFeature.AVX)) {
            VexMoveOp.VMOVD.emit(asm, AVXKind.AVXSize.DWORD, dst, src);
        } else {
            asm.movdl(dst, src);
        }
        emitBroadcast(asm, getComparisonKind(), dst, tmpVector, getVectorSize());
    }

    private static boolean isConstant(Value val) {
        assert !(val instanceof ConstantValue) || ((ConstantValue) val).isJavaConstant();
        return val instanceof ConstantValue;
    }

    private static JavaConstant asConstant(Value val) {
        return ((ConstantValue) val).getJavaConstant();
    }

    private static Register asRegOrTmpReg(CompilationResultBuilder crb, AMD64MacroAssembler asm, Value val, Register tmpReg) {
        if (isRegister(val)) {
            return asRegister(val);
        } else if (isStackSlot(val)) {
            asm.movl(tmpReg, (AMD64Address) crb.asAddress(val));
            return tmpReg;
        } else {
            assert isConstant(val);
            asm.movl(tmpReg, asConstant(val).asInt());
            return tmpReg;
        }
    }

    private static void emitAlign(CompilationResultBuilder crb, AMD64MacroAssembler asm) {
        asm.align(crb.target.wordSize * 2);
    }

    /**
     * Fills {@code vecDst} with copies of its lowest byte, word or dword.
     */
    private static void emitBroadcast(AMD64MacroAssembler asm, JavaKind kind, Register vecDst, Register vecTmp, AVXKind.AVXSize vectorSize) {
        switch (kind) {
            case Byte:
                if (asm.supports(CPUFeature.AVX2)) {
                    VexRMOp.VPBROADCASTB.emit(asm, vectorSize, vecDst, vecDst);
                } else if (asm.supports(CPUFeature.AVX)) {
                    VexRVMOp.VPXOR.emit(asm, vectorSize, vecTmp, vecTmp, vecTmp);
                    VexRVMOp.VPSHUFB.emit(asm, vectorSize, vecDst, vecDst, vecTmp);
                } else if (asm.supports(CPUFeature.SSSE3)) {
                    asm.pxor(vecTmp, vecTmp);
                    asm.pshufb(vecDst, vecTmp);
                } else { // SSE2
                    asm.punpcklbw(vecDst, vecDst);
                    asm.punpcklbw(vecDst, vecDst);
                    asm.pshufd(vecDst, vecDst, 0);
                }
                break;
            case Short:
            case Char:
                if (asm.supports(CPUFeature.AVX2)) {
                    VexRMOp.VPBROADCASTW.emit(asm, vectorSize, vecDst, vecDst);
                } else if (asm.supports(CPUFeature.AVX)) {
                    VexRMIOp.VPSHUFLW.emit(asm, vectorSize, vecDst, vecDst, 0);
                    VexRMIOp.VPSHUFD.emit(asm, vectorSize, vecDst, vecDst, 0);
                } else { // SSE
                    asm.pshuflw(vecDst, vecDst, 0);
                    asm.pshufd(vecDst, vecDst, 0);
                }
                break;
            case Int:
                if (asm.supports(CPUFeature.AVX2)) {
                    VexRMOp.VPBROADCASTD.emit(asm, vectorSize, vecDst, vecDst);
                } else if (asm.supports(CPUFeature.AVX)) {
                    VexRMIOp.VPSHUFD.emit(asm, vectorSize, vecDst, vecDst, 0);
                } else { // SSE
                    asm.pshufd(vecDst, vecDst, 0);
                }
                break;
            default:
                throw new UnsupportedOperationException();
        }
    }

    private void emitVectorCompare(AMD64MacroAssembler asm,
                    JavaKind kind,
                    int nVectors,
                    Register arrayPtr,
                    Register index,
                    Register[] vecCmp,
                    Register[] vecArray,
                    Register[] cmpResult,
                    Label[] vectorFound,
                    boolean shortJmp,
                    boolean alignedLoad) {
        // load array contents into vectors
        for (int i = 0; i < nVectors; i++) {
            int base = i * nValues;
            for (int j = 0; j < nValues; j++) {
                emitArrayLoad(asm, getVectorSize(), vecArray[base + j], arrayPtr, index, getVectorOffset(nVectors - (i + 1)), alignedLoad);
            }
        }
        // compare all loaded bytes to the search value.
        // matching bytes are set to 0xff, non-matching bytes are set to 0x00.
        if (!findTwoConsecutive) {
            for (int i = 0; i < nVectors; i++) {
                int base = i * nValues;
                for (int j = 0; j < nValues; j++) {
                    emitVectorCompareInst(asm, kind, getVectorSize(), vecArray[base + j], vecCmp[j]);
                    if ((j & 1) == 1) {
                        emitPOR(asm, getVectorSize(), vecArray[base + j - 1], vecArray[base + j]);
                    }
                }
                if (nValues > 2) {
                    emitPOR(asm, getVectorSize(), vecArray[base], vecArray[base + 2]);
                }
                emitMOVMSK(asm, getVectorSize(), cmpResult[0], vecArray[base]);
                emitJnz(asm, cmpResult[0], vectorFound[nVectors - (i + 1)], shortJmp);
            }
        } else {
            for (int i = 0; i < nVectors; i += 2) {
                emitVectorCompareInst(asm, kind, getVectorSize(), vecArray[i], vecCmp[0]);
                emitVectorCompareInst(asm, kind, getVectorSize(), vecArray[i + 1], vecCmp[0]);
                emitMOVMSK(asm, getVectorSize(), cmpResult[1], vecArray[i]);
                emitMOVMSK(asm, getVectorSize(), cmpResult[0], vecArray[i + 1]);
                emitJnz(asm, cmpResult[1], vectorFound[nVectors - (i + 1)], shortJmp);
                emitJnz(asm, cmpResult[0], vectorFound[nVectors - (i + 2)], shortJmp);
            }
        }
    }

    private static void emitJnz(AMD64MacroAssembler asm, Register cond, Label tgt, boolean shortJmp) {
        asm.testl(cond, cond);
        if (shortJmp) {
            asm.jccb(AMD64Assembler.ConditionFlag.NotZero, tgt);
        } else {
            asm.jcc(AMD64Assembler.ConditionFlag.NotZero, tgt);
        }
    }

    private void emitArrayLoad(AMD64MacroAssembler asm, AVXKind.AVXSize vectorSize, Register vecDst, Register arrayPtr, Register index, int offset, boolean alignedLoad) {
        AMD64Address src = new AMD64Address(arrayPtr, index, arrayIndexScale, offset);
        if (asm.supports(CPUFeature.AVX)) {
            VexMoveOp loadOp = alignedLoad ? VexMoveOp.VMOVDQA32 : VexMoveOp.VMOVDQU32;
            loadOp.emit(asm, vectorSize, vecDst, src);
        } else {
            // SSE
            asm.movdqu(vecDst, src);
        }
    }

    /**
     * Compares all packed bytes/words/dwords in {@code vecArray} to {@code vecCmp}. Matching values
     * are set to all ones (0xff, 0xffff, ...), non-matching values are set to zero.
     */
    private static void emitVectorCompareInst(AMD64MacroAssembler asm, JavaKind kind, AVXKind.AVXSize vectorSize, Register vecArray, Register vecCmp) {
        switch (kind) {
            case Byte:
                if (asm.supports(CPUFeature.AVX)) {
                    VexRVMOp.VPCMPEQB.emit(asm, vectorSize, vecArray, vecCmp, vecArray);
                } else { // SSE
                    asm.pcmpeqb(vecArray, vecCmp);
                }
                break;
            case Short:
            case Char:
                if (asm.supports(CPUFeature.AVX)) {
                    VexRVMOp.VPCMPEQW.emit(asm, vectorSize, vecArray, vecCmp, vecArray);
                } else { // SSE
                    asm.pcmpeqw(vecArray, vecCmp);
                }
                break;
            case Int:
                if (asm.supports(CPUFeature.AVX)) {
                    VexRVMOp.VPCMPEQD.emit(asm, vectorSize, vecArray, vecCmp, vecArray);
                } else { // SSE
                    asm.pcmpeqd(vecArray, vecCmp);
                }
                break;
            default:
                throw new UnsupportedOperationException();
        }
    }

    private static void emitPOR(AMD64MacroAssembler asm, AVXKind.AVXSize vectorSize, Register dst, Register vecSrc) {
        if (asm.supports(CPUFeature.AVX)) {
            VexRVMOp.VPOR.emit(asm, vectorSize, dst, dst, vecSrc);
        } else {
            // SSE
            asm.por(dst, vecSrc);
        }
    }

    private static void emitMOVMSK(AMD64MacroAssembler asm, AVXKind.AVXSize vectorSize, Register dst, Register vecSrc) {
        if (asm.supports(CPUFeature.AVX)) {
            VexRMOp.VPMOVMSKB.emit(asm, vectorSize, dst, vecSrc);
        } else {
            // SSE
            asm.pmovmskb(dst, vecSrc);
        }
    }

    private static OperandSize getOpSize(JavaKind kind) {
        switch (kind) {
            case Byte:
                return OperandSize.BYTE;
            case Short:
            case Char:
                return OperandSize.WORD;
            case Int:
                return OperandSize.DWORD;
            default:
                return OperandSize.QWORD;
        }
    }

    private static boolean supportsAVX2(LIRGeneratorTool tool) {
        return supports(tool, CPUFeature.AVX2);
    }

    private static boolean supports(LIRGeneratorTool tool, CPUFeature cpuFeature) {
        return ((AMD64) tool.target().arch).getFeatures().contains(cpuFeature);
    }

    @Override
    public boolean needsClearUpperVectorRegisters() {
        return true;
    }
}
