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

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64Assembler;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.VexMoveOp;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRMIOp;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRMOp;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRVMOp;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.asm.amd64.AVXKind;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

/**
 */
@Opcode("AMD64_ARRAY_INDEX_OF")
public final class AMD64ArrayIndexOfOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64ArrayIndexOfOp> TYPE = LIRInstructionClass.create(AMD64ArrayIndexOfOp.class);

    private final JavaKind kind;
    private final int vmPageSize;
    private final int nValues;
    private final boolean findTwoConsecutive;
    private final AMD64Kind vectorKind;

    @Def({REG}) protected Value resultValue;
    @Alive({REG}) protected Value arrayPtrValue;
    @Use({REG}) protected Value arrayLengthValue;
    @Alive({REG}) protected Value searchValue1;
    @Alive({REG, ILLEGAL}) protected Value searchValue2;
    @Alive({REG, ILLEGAL}) protected Value searchValue3;
    @Alive({REG, ILLEGAL}) protected Value searchValue4;
    @Temp({REG}) protected Value arraySlotsRemaining;
    @Temp({REG}) protected Value comparisonResult1;
    @Temp({REG}) protected Value comparisonResult2;
    @Temp({REG}) protected Value comparisonResult3;
    @Temp({REG}) protected Value comparisonResult4;
    @Temp({REG, ILLEGAL}) protected Value vectorCompareVal1;
    @Temp({REG, ILLEGAL}) protected Value vectorCompareVal2;
    @Temp({REG, ILLEGAL}) protected Value vectorCompareVal3;
    @Temp({REG, ILLEGAL}) protected Value vectorCompareVal4;
    @Temp({REG, ILLEGAL}) protected Value vectorArray1;
    @Temp({REG, ILLEGAL}) protected Value vectorArray2;
    @Temp({REG, ILLEGAL}) protected Value vectorArray3;
    @Temp({REG, ILLEGAL}) protected Value vectorArray4;

    public AMD64ArrayIndexOfOp(JavaKind kind, boolean findTwoConsecutive, int vmPageSize, int maxVectorSize, LIRGeneratorTool tool, Value result, Value arrayPtr, Value arrayLength,
                    Value... searchValues) {
        super(TYPE);
        this.kind = kind;
        this.findTwoConsecutive = findTwoConsecutive;
        this.vmPageSize = vmPageSize;
        assert 0 < searchValues.length && searchValues.length <= 4;
        assert byteMode(kind) || charMode(kind);
        assert supports(tool, CPUFeature.SSE2) || supports(tool, CPUFeature.AVX) || supportsAVX2(tool);
        nValues = searchValues.length;
        assert !findTwoConsecutive || nValues == 1;
        resultValue = result;
        arrayPtrValue = arrayPtr;
        arrayLengthValue = arrayLength;
        searchValue1 = searchValues[0];
        searchValue2 = nValues > 1 ? searchValues[1] : Value.ILLEGAL;
        searchValue3 = nValues > 2 ? searchValues[2] : Value.ILLEGAL;
        searchValue4 = nValues > 3 ? searchValues[3] : Value.ILLEGAL;
        arraySlotsRemaining = tool.newVariable(LIRKind.value(AMD64Kind.DWORD));
        comparisonResult1 = tool.newVariable(LIRKind.value(AMD64Kind.DWORD));
        comparisonResult2 = tool.newVariable(LIRKind.value(AMD64Kind.DWORD));
        comparisonResult3 = tool.newVariable(LIRKind.value(AMD64Kind.DWORD));
        comparisonResult4 = tool.newVariable(LIRKind.value(AMD64Kind.DWORD));
        vectorKind = supportsAVX2(tool) && (maxVectorSize < 0 || maxVectorSize >= 32) ? byteMode(kind) ? AMD64Kind.V256_BYTE : AMD64Kind.V256_WORD
                        : byteMode(kind) ? AMD64Kind.V128_BYTE : AMD64Kind.V128_WORD;
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
        return findTwoConsecutive ? (byteMode(kind) ? JavaKind.Char : JavaKind.Int) : kind;
    }

    private AVXKind.AVXSize getVectorSize() {
        return AVXKind.getDataSize(vectorKind);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler asm) {
        Register arrayPtr = asRegister(arrayPtrValue);
        Register arrayLength = asRegister(arrayLengthValue);
        Register result = asRegister(resultValue);
        Register slotsRemaining = asRegister(arraySlotsRemaining);
        Register[] searchValue = {
                        nValues > 0 ? asRegister(searchValue1) : null,
                        nValues > 1 ? asRegister(searchValue2) : null,
                        nValues > 2 ? asRegister(searchValue3) : null,
                        nValues > 3 ? asRegister(searchValue4) : null,
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
                        asRegister(comparisonResult2),
                        asRegister(comparisonResult3),
                        asRegister(comparisonResult4),
        };
        Label retFound = new Label();
        Label retNotFound = new Label();
        Label end = new Label();

        // load array length
        // important: this must be the first register manipulation, since arrayLengthValue is
        // annotated with @Use
        asm.movl(slotsRemaining, arrayLength);
        // load array pointer
        asm.movq(result, arrayPtr);
        // move search values to vectors
        for (int i = 0; i < nValues; i++) {
            if (asm.supports(CPUFeature.AVX)) {
                VexMoveOp.VMOVD.emit(asm, AVXKind.AVXSize.DWORD, vecCmp[i], searchValue[i]);
            } else {
                asm.movdl(vecCmp[i], searchValue[i]);
            }
        }
        // fill comparison vector with copies of the search value
        for (int i = 0; i < nValues; i++) {
            emitBroadcast(asm, getComparisonKind(), vecCmp[i], vecArray[0], getVectorSize());
        }

        emitArrayIndexOfChars(crb, asm, result, slotsRemaining, searchValue, vecCmp, vecArray, cmpResult, retFound, retNotFound);

        // return -1 (no match)
        asm.bind(retNotFound);
        asm.movq(result, -1);
        asm.jmpb(end);

        asm.bind(retFound);
        // convert array pointer to offset
        asm.subq(result, arrayPtr);
        if (charMode(kind)) {
            asm.shrq(result, 1);
        }
        asm.bind(end);
    }

    private void emitArrayIndexOfChars(CompilationResultBuilder crb, AMD64MacroAssembler asm,
                    Register arrayPtr,
                    Register slotsRemaining,
                    Register[] searchValue,
                    Register[] vecCmp,
                    Register[] vecArray,
                    Register[] cmpResult,
                    Label retFound,
                    Label retNotFound) {
        int nVectors = nValues == 1 ? 4 : nValues == 2 ? 2 : 1;
        AVXKind.AVXSize vectorSize = getVectorSize();

        Label bulkVectorLoop = new Label();
        Label singleVectorLoop = new Label();
        Label[] vectorFound = {
                        new Label(),
                        new Label(),
                        new Label(),
                        new Label(),
        };
        Label lessThanVectorSizeRemaining = new Label();
        Label lessThanVectorSizeRemainingLoop = new Label();
        Label bulkVectorLoopExit = nVectors == 1 ? lessThanVectorSizeRemaining : singleVectorLoop;
        int bytesPerVector = vectorSize.getBytes();
        int arraySlotsPerVector = vectorSize.getBytes() / kind.getByteCount();
        int singleVectorLoopCondition = arraySlotsPerVector;
        int bulkSize = arraySlotsPerVector * nVectors;
        int bulkSizeBytes = bytesPerVector * nVectors;
        int bulkLoopCondition = bulkSize;
        int[] vectorOffsets;
        JavaKind vectorCompareKind = kind;
        if (findTwoConsecutive) {
            singleVectorLoopCondition++;
            bulkLoopCondition++;
            bulkSize /= 2;
            bulkSizeBytes /= 2;
            vectorOffsets = new int[]{0, kind.getByteCount(), bytesPerVector, bytesPerVector + kind.getByteCount()};
            vectorCompareKind = byteMode(kind) ? JavaKind.Char : JavaKind.Int;
        } else {
            vectorOffsets = new int[]{0, bytesPerVector, bytesPerVector * 2, bytesPerVector * 3};
        }

        // load copy of low part of array pointer
        Register tmpArrayPtrLow = cmpResult[0];
        asm.movl(tmpArrayPtrLow, arrayPtr);

        // check if bulk vector load is in bounds
        asm.cmpl(slotsRemaining, bulkLoopCondition);
        asm.jcc(AMD64Assembler.ConditionFlag.Below, bulkVectorLoopExit);

        // check if array pointer is aligned to bulkSize
        asm.andl(tmpArrayPtrLow, bulkSizeBytes - 1);
        asm.jcc(AMD64Assembler.ConditionFlag.Zero, bulkVectorLoop);

        // do one unaligned bulk comparison pass and adjust alignment afterwards
        emitVectorCompare(asm, vectorCompareKind, vectorSize, nValues, nVectors, vectorOffsets, arrayPtr, vecCmp, vecArray, cmpResult, vectorFound, false);
        // load copy of low part of array pointer
        asm.movl(tmpArrayPtrLow, arrayPtr);
        // adjust array pointer
        asm.addq(arrayPtr, bulkSizeBytes);
        // adjust number of array slots remaining
        asm.subl(slotsRemaining, bulkSize);
        // get offset to bulk size alignment
        asm.andl(tmpArrayPtrLow, bulkSizeBytes - 1);
        emitBytesToArraySlots(asm, kind, tmpArrayPtrLow);
        // adjust array pointer to bulk size alignment
        asm.andq(arrayPtr, ~(bulkSizeBytes - 1));
        // adjust number of array slots remaining
        asm.addl(slotsRemaining, tmpArrayPtrLow);
        // check if there are enough array slots remaining for the bulk loop
        asm.cmpl(slotsRemaining, bulkLoopCondition);
        asm.jcc(AMD64Assembler.ConditionFlag.Below, bulkVectorLoopExit);

        emitAlign(crb, asm);
        asm.bind(bulkVectorLoop);
        // memory-aligned bulk comparison
        emitVectorCompare(asm, vectorCompareKind, vectorSize, nValues, nVectors, vectorOffsets, arrayPtr, vecCmp, vecArray, cmpResult, vectorFound, !findTwoConsecutive);
        // adjust number of array slots remaining
        asm.subl(slotsRemaining, bulkSize);
        // adjust array pointer
        asm.addq(arrayPtr, bulkSizeBytes);
        // check if there are enough array slots remaining for the bulk loop
        asm.cmpl(slotsRemaining, bulkLoopCondition);
        asm.jcc(AMD64Assembler.ConditionFlag.Below, bulkVectorLoopExit);
        // continue loop
        asm.jmp(bulkVectorLoop);

        if (nVectors > 1) {
            emitAlign(crb, asm);
            // same loop as bulkVectorLoop, with only one vector
            asm.bind(singleVectorLoop);
            // check if single vector load is in bounds
            asm.cmpl(slotsRemaining, singleVectorLoopCondition);
            asm.jcc(AMD64Assembler.ConditionFlag.Below, lessThanVectorSizeRemaining);
            // compare
            emitVectorCompare(asm, vectorCompareKind, vectorSize, nValues, findTwoConsecutive ? 2 : 1, vectorOffsets, arrayPtr, vecCmp, vecArray, cmpResult, vectorFound, false);
            // adjust number of array slots remaining
            asm.subl(slotsRemaining, arraySlotsPerVector);
            // adjust array pointer
            asm.addq(arrayPtr, bytesPerVector);
            // continue loop
            asm.jmpb(singleVectorLoop);
        }

        asm.bind(lessThanVectorSizeRemaining);
        // check if any array slots remain
        asm.testl(slotsRemaining, slotsRemaining);
        asm.jcc(AMD64Assembler.ConditionFlag.Zero, retNotFound);

        // a vector compare will read out of bounds of the input array.
        // check if the out-of-bounds read would cross a memory page boundary.
        // load copy of low part of array pointer
        asm.movl(tmpArrayPtrLow, arrayPtr);
        // check if pointer + vector size would cross the page boundary
        asm.andl(tmpArrayPtrLow, (vmPageSize - 1));
        asm.cmpl(tmpArrayPtrLow, (vmPageSize - (findTwoConsecutive ? bytesPerVector + kind.getByteCount() : bytesPerVector)));
        // if the page boundary would be crossed, do byte/character-wise comparison instead.
        asm.jccb(AMD64Assembler.ConditionFlag.Above, lessThanVectorSizeRemainingLoop);

        Label[] overBoundsMatch = {new Label(), new Label()};
        // otherwise, do a vector compare that reads beyond array bounds
        emitVectorCompare(asm, vectorCompareKind, vectorSize, nValues, findTwoConsecutive ? 2 : 1, vectorOffsets, arrayPtr, vecCmp, vecArray, cmpResult, overBoundsMatch, false);
        // no match
        asm.jmp(retNotFound);
        if (findTwoConsecutive) {
            Label overBoundsFinish = new Label();
            asm.bind(overBoundsMatch[1]);
            // get match offset of second result
            asm.bsfq(cmpResult[1], cmpResult[1]);
            asm.addl(cmpResult[1], kind.getByteCount());
            // replace first result with second and continue
            asm.movl(cmpResult[0], cmpResult[1]);
            asm.jmpb(overBoundsFinish);

            asm.bind(overBoundsMatch[0]);
            emitFindTwoCharPrefixMinResult(asm, kind, cmpResult, overBoundsFinish);
        } else {
            asm.bind(overBoundsMatch[0]);
            // find match offset
            asm.bsfq(cmpResult[0], cmpResult[0]);
        }

        // adjust array pointer for match result
        asm.addq(arrayPtr, cmpResult[0]);
        if (charMode(kind)) {
            // convert byte offset to chars
            asm.shrl(cmpResult[0], 1);
        }
        // check if offset of matched value is greater than number of bytes remaining / out of array
        // bounds
        if (findTwoConsecutive) {
            asm.decrementl(slotsRemaining);
        }
        asm.cmpl(cmpResult[0], slotsRemaining);
        // match is out of bounds, return no match
        asm.jcc(AMD64Assembler.ConditionFlag.GreaterEqual, retNotFound);
        // adjust number of array slots remaining
        if (findTwoConsecutive) {
            asm.incrementl(slotsRemaining, 1);
        }
        asm.subl(slotsRemaining, cmpResult[0]);
        // match is in bounds, return offset
        asm.jmp(retFound);

        // compare remaining slots in the array one-by-one
        asm.bind(lessThanVectorSizeRemainingLoop);
        // check if enough array slots remain
        asm.cmpl(slotsRemaining, findTwoConsecutive ? 1 : 0);
        asm.jcc(AMD64Assembler.ConditionFlag.LessEqual, retNotFound);
        // load char / byte
        if (byteMode(kind)) {
            if (findTwoConsecutive) {
                asm.movzwl(cmpResult[0], new AMD64Address(arrayPtr));
            } else {
                asm.movzbl(cmpResult[0], new AMD64Address(arrayPtr));
            }
        } else {
            if (findTwoConsecutive) {
                asm.movl(cmpResult[0], new AMD64Address(arrayPtr));
            } else {
                asm.movzwl(cmpResult[0], new AMD64Address(arrayPtr));
            }
        }
        // check for match
        for (int i = 0; i < nValues; i++) {
            emitCompareInst(asm, getComparisonKind(), cmpResult[0], searchValue[i]);
            asm.jcc(AMD64Assembler.ConditionFlag.Equal, retFound);
        }
        // adjust number of array slots remaining
        asm.decrementl(slotsRemaining);
        // adjust array pointer
        asm.addq(arrayPtr, kind.getByteCount());
        // continue loop
        asm.jmpb(lessThanVectorSizeRemainingLoop);

        for (int i = 1; i < nVectors; i += (findTwoConsecutive ? 2 : 1)) {
            emitVectorFoundWithOffset(asm, kind, vectorOffsets[i], arrayPtr, cmpResult[i], slotsRemaining, vectorFound[i], retFound);
        }

        if (findTwoConsecutive) {
            asm.bind(vectorFound[2]);
            asm.addq(arrayPtr, vectorOffsets[2]);
            // adjust number of array slots remaining
            asm.subl(slotsRemaining, charMode(kind) ? vectorOffsets[2] / 2 : vectorOffsets[2]);
            asm.movl(cmpResult[0], cmpResult[2]);
            asm.movl(cmpResult[1], cmpResult[3]);
            asm.bind(vectorFound[0]);
            emitFindTwoCharPrefixMinResult(asm, kind, cmpResult, new Label());
        } else {
            asm.bind(vectorFound[0]);
            // find index of first set bit in bit mask
            asm.bsfq(cmpResult[0], cmpResult[0]);
        }
        // add offset to array pointer
        asm.addq(arrayPtr, cmpResult[0]);
        if (charMode(kind)) {
            // convert byte offset to chars
            asm.shrl(cmpResult[0], 1);
        }
        // adjust number of array slots remaining
        asm.subl(slotsRemaining, cmpResult[0]);
        asm.jmpb(retFound);
    }

    private static void emitFindTwoCharPrefixMinResult(AMD64MacroAssembler asm, JavaKind kind, Register[] cmpResult, Label done) {
        // find match offset
        asm.bsfq(cmpResult[0], cmpResult[0]);
        // check if second result is also a match
        asm.testl(cmpResult[1], cmpResult[1]);
        asm.jcc(AMD64Assembler.ConditionFlag.Zero, done);
        // get match offset of second result
        asm.bsfq(cmpResult[1], cmpResult[1]);
        asm.addl(cmpResult[1], kind.getByteCount());
        // check if first result is less than second
        asm.cmpl(cmpResult[0], cmpResult[1]);
        asm.jcc(AMD64Assembler.ConditionFlag.LessEqual, done);
        // first result is greater than second, replace it with the second result
        asm.movl(cmpResult[0], cmpResult[1]);
        asm.bind(done);
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

    /**
     * Convert a byte offset stored in {@code bytes} to an array index offset.
     */
    private static void emitBytesToArraySlots(AMD64MacroAssembler asm, JavaKind kind, Register bytes) {
        if (charMode(kind)) {
            asm.shrl(bytes, 1);
        } else {
            assert byteMode(kind);
        }
    }

    private static void emitVectorCompare(AMD64MacroAssembler asm,
                    JavaKind kind,
                    AVXKind.AVXSize vectorSize,
                    int nValues,
                    int nVectors,
                    int[] vectorOffsets,
                    Register arrayPtr,
                    Register[] vecCmp,
                    Register[] vecArray,
                    Register[] cmpResult,
                    Label[] vectorFound,
                    boolean alignedLoad) {
        // load array contents into vectors
        for (int i = 0; i < nValues; i++) {
            for (int j = 0; j < nVectors; j++) {
                emitArrayLoad(asm, vectorSize, vecArray[(i * nVectors) + j], arrayPtr, vectorOffsets[j], alignedLoad);
            }
        }
        // compare all loaded bytes to the search value.
        // matching bytes are set to 0xff, non-matching bytes are set to 0x00.
        for (int i = 0; i < nValues; i++) {
            for (int j = 0; j < nVectors; j++) {
                emitVectorCompareInst(asm, kind, vectorSize, vecArray[(i * nVectors) + j], vecCmp[i]);
            }
        }
        // create 32-bit-masks from the most significant bit of every byte in the comparison
        // results.
        for (int i = 0; i < nValues * nVectors; i++) {
            emitMOVMSK(asm, vectorSize, cmpResult[i], vecArray[i]);
        }
        // join results of comparisons against multiple values
        for (int stride = 1; stride < nValues; stride *= 2) {
            for (int i = 0; i < nVectors; i++) {
                for (int j = 0; j + stride < nValues; j += stride * 2) {
                    asm.orl(cmpResult[i + (j * nVectors)], cmpResult[i + ((j + stride) * nVectors)]);
                }
            }
        }
        // check if a match was found
        for (int i = 0; i < nVectors; i++) {
            asm.testl(cmpResult[i], cmpResult[i]);
            asm.jcc(AMD64Assembler.ConditionFlag.NotZero, vectorFound[i]);
        }
    }

    private static void emitVectorFoundWithOffset(AMD64MacroAssembler asm,
                    JavaKind kind,
                    int resultOffset,
                    Register result,
                    Register cmpResult,
                    Register slotsRemaining,
                    Label entry,
                    Label ret) {
        asm.bind(entry);
        if (resultOffset > 0) {
            // adjust array pointer
            asm.addq(result, resultOffset);
            // adjust number of array slots remaining
            asm.subl(slotsRemaining, charMode(kind) ? resultOffset / 2 : resultOffset);
        }
        // find index of first set bit in bit mask
        asm.bsfq(cmpResult, cmpResult);
        // add offset to array pointer
        asm.addq(result, cmpResult);
        if (charMode(kind)) {
            // convert byte offset to chars
            asm.shrl(cmpResult, 1);
        }
        // adjust number of array slots remaining
        asm.subl(slotsRemaining, cmpResult);
        asm.jmpb(ret);
    }

    private static void emitArrayLoad(AMD64MacroAssembler asm, AVXKind.AVXSize vectorSize, Register vecDst, Register arrayPtr, int offset, boolean alignedLoad) {
        AMD64Address src = new AMD64Address(arrayPtr, offset);
        if (asm.supports(CPUFeature.AVX)) {
            VexMoveOp loadOp = alignedLoad ? VexMoveOp.VMOVDQA : VexMoveOp.VMOVDQU;
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

    private static void emitMOVMSK(AMD64MacroAssembler asm, AVXKind.AVXSize vectorSize, Register dst, Register vecSrc) {
        if (asm.supports(CPUFeature.AVX)) {
            VexRMOp.VPMOVMSKB.emit(asm, vectorSize, dst, vecSrc);
        } else {
            // SSE
            asm.pmovmskb(dst, vecSrc);
        }
    }

    private static void emitCompareInst(AMD64MacroAssembler asm, JavaKind kind, Register dst, Register src) {
        switch (kind) {
            case Byte:
                asm.cmpb(dst, src);
                break;
            case Short:
            case Char:
                asm.cmpw(dst, src);
                break;
            case Int:
                asm.cmpl(dst, src);
                break;
            default:
                asm.cmpq(dst, src);
        }
    }

    private static boolean supportsAVX2(LIRGeneratorTool tool) {
        return supports(tool, CPUFeature.AVX2);
    }

    private static boolean supports(LIRGeneratorTool tool, CPUFeature cpuFeature) {
        return ((AMD64) tool.target().arch).getFeatures().contains(cpuFeature);
    }
}
