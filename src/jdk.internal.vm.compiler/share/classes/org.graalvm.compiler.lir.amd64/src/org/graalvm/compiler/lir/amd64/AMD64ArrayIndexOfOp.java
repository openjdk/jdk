/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

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

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

/**
 */
@Opcode("AMD64_ARRAY_INDEX_OF")
public final class AMD64ArrayIndexOfOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64ArrayIndexOfOp> TYPE = LIRInstructionClass.create(AMD64ArrayIndexOfOp.class);

    private final JavaKind kind;
    private final int vmPageSize;

    @Def({REG}) protected Value resultValue;
    @Alive({REG}) protected Value charArrayPtrValue;
    @Use({REG}) protected Value charArrayLengthValue;
    @Alive({REG}) protected Value searchCharValue;
    @Temp({REG}) protected Value arraySlotsRemaining;
    @Temp({REG}) protected Value comparisonResult1;
    @Temp({REG}) protected Value comparisonResult2;
    @Temp({REG}) protected Value comparisonResult3;
    @Temp({REG}) protected Value comparisonResult4;
    @Temp({REG, ILLEGAL}) protected Value vectorCompareVal;
    @Temp({REG, ILLEGAL}) protected Value vectorArray1;
    @Temp({REG, ILLEGAL}) protected Value vectorArray2;
    @Temp({REG, ILLEGAL}) protected Value vectorArray3;
    @Temp({REG, ILLEGAL}) protected Value vectorArray4;

    public AMD64ArrayIndexOfOp(
                    JavaKind kind,
                    int vmPageSize, LIRGeneratorTool tool,
                    Value result,
                    Value arrayPtr,
                    Value arrayLength,
                    Value searchChar) {
        super(TYPE);
        this.kind = kind;
        this.vmPageSize = vmPageSize;
        assert byteMode() || charMode();
        assert supports(tool, CPUFeature.SSSE3) || supports(tool, CPUFeature.AVX) || supportsAVX2(tool);
        resultValue = result;
        charArrayPtrValue = arrayPtr;
        charArrayLengthValue = arrayLength;
        searchCharValue = searchChar;

        this.arraySlotsRemaining = tool.newVariable(LIRKind.value(AMD64Kind.DWORD));
        this.comparisonResult1 = tool.newVariable(LIRKind.value(AMD64Kind.DWORD));
        this.comparisonResult2 = tool.newVariable(LIRKind.value(AMD64Kind.DWORD));
        this.comparisonResult3 = tool.newVariable(LIRKind.value(AMD64Kind.DWORD));
        this.comparisonResult4 = tool.newVariable(LIRKind.value(AMD64Kind.DWORD));
        AMD64Kind vectorKind = byteMode() ? supportsAVX2(tool) ? AMD64Kind.V256_BYTE : AMD64Kind.V128_BYTE : supportsAVX2(tool) ? AMD64Kind.V256_WORD : AMD64Kind.V128_WORD;
        this.vectorCompareVal = tool.newVariable(LIRKind.value(vectorKind));
        this.vectorArray1 = tool.newVariable(LIRKind.value(vectorKind));
        this.vectorArray2 = tool.newVariable(LIRKind.value(vectorKind));
        this.vectorArray3 = tool.newVariable(LIRKind.value(vectorKind));
        this.vectorArray4 = tool.newVariable(LIRKind.value(vectorKind));
    }

    private boolean byteMode() {
        return kind == JavaKind.Byte;
    }

    private boolean charMode() {
        return kind == JavaKind.Char;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler asm) {
        Register arrayPtr = asRegister(charArrayPtrValue);
        Register arrayLength = asRegister(charArrayLengthValue);
        Register searchValue = asRegister(searchCharValue);
        Register result = asRegister(resultValue);
        Register vecCmp = asRegister(vectorCompareVal);
        Register vecArray1 = asRegister(vectorArray1);
        Register vecArray2 = asRegister(vectorArray2);
        Register vecArray3 = asRegister(vectorArray3);
        Register vecArray4 = asRegister(vectorArray4);
        Register slotsRemaining = asRegister(arraySlotsRemaining);
        Register cmpResult1 = asRegister(comparisonResult1);
        Register cmpResult2 = asRegister(comparisonResult2);
        Register cmpResult3 = asRegister(comparisonResult3);
        Register cmpResult4 = asRegister(comparisonResult4);

        Label bulkVectorLoop = new Label();
        Label singleVectorLoop = new Label();
        Label vectorFound1 = new Label();
        Label vectorFound2 = new Label();
        Label vectorFound3 = new Label();
        Label vectorFound4 = new Label();
        Label lessThanVectorSizeRemaining = new Label();
        Label lessThanVectorSizeRemainingLoop = new Label();
        Label retFound = new Label();
        Label retNotFound = new Label();
        Label end = new Label();

        AVXKind.AVXSize vectorSize = asm.supports(CPUFeature.AVX2) ? AVXKind.AVXSize.YMM : AVXKind.AVXSize.XMM;
        int nVectors = 4;
        int bytesPerVector = vectorSize.getBytes();
        int arraySlotsPerVector = vectorSize.getBytes() / kind.getByteCount();
        int bulkSize = arraySlotsPerVector * nVectors;
        int bulkSizeBytes = bytesPerVector * nVectors;
        assert bulkSizeBytes >= 64;

        // load array length
        // important: this must be the first register manipulation, since charArrayLengthValue is
        // annotated with @Use
        asm.movl(slotsRemaining, arrayLength);
        // move search value to vector
        if (asm.supports(CPUFeature.AVX)) {
            VexMoveOp.VMOVD.emit(asm, AVXKind.AVXSize.DWORD, vecCmp, searchValue);
        } else {
            asm.movdl(vecCmp, searchValue);
        }
        // load array pointer
        asm.movq(result, arrayPtr);
        // load copy of low part of array pointer
        asm.movl(cmpResult1, arrayPtr);
        // fill comparison vector with copies of the search value
        emitBroadcast(asm, vecCmp, vecArray1, vectorSize);

        // check if bulk vector load is in bounds
        asm.cmpl(slotsRemaining, bulkSize);
        asm.jcc(AMD64Assembler.ConditionFlag.Below, singleVectorLoop);

        // check if array pointer is 64-byte aligned
        asm.andl(cmpResult1, 63);
        asm.jcc(AMD64Assembler.ConditionFlag.Zero, bulkVectorLoop);

        // do one unaligned bulk comparison pass and adjust alignment afterwards
        emitBulkCompare(asm, vectorSize, bytesPerVector, result, vecCmp, vecArray1, vecArray2, vecArray3, vecArray4, cmpResult1, cmpResult2, cmpResult3, cmpResult4,
                        vectorFound1, vectorFound2, vectorFound3, vectorFound4, false);
        // load copy of low part of array pointer
        asm.movl(cmpResult1, arrayPtr);
        // adjust array pointer
        asm.addq(result, bulkSizeBytes);
        // adjust number of array slots remaining
        asm.subl(slotsRemaining, bulkSize);
        // get offset to 64-byte alignment
        asm.andl(cmpResult1, 63);
        emitBytesToArraySlots(asm, cmpResult1);
        // adjust array pointer to 64-byte alignment
        asm.andq(result, ~63);
        // adjust number of array slots remaining
        asm.addl(slotsRemaining, cmpResult1);
        // check if there are enough array slots remaining for the bulk loop
        asm.cmpl(slotsRemaining, bulkSize);
        asm.jcc(AMD64Assembler.ConditionFlag.Below, singleVectorLoop);

        emitAlign(crb, asm);
        asm.bind(bulkVectorLoop);
        // memory-aligned bulk comparison
        emitBulkCompare(asm, vectorSize, bytesPerVector, result, vecCmp, vecArray1, vecArray2, vecArray3, vecArray4, cmpResult1, cmpResult2, cmpResult3, cmpResult4,
                        vectorFound1, vectorFound2, vectorFound3, vectorFound4, true);
        // adjust number of array slots remaining
        asm.subl(slotsRemaining, bulkSize);
        // adjust array pointer
        asm.addq(result, bulkSizeBytes);
        // check if there are enough array slots remaining for the bulk loop
        asm.cmpl(slotsRemaining, bulkSize);
        asm.jcc(AMD64Assembler.ConditionFlag.Below, singleVectorLoop);
        // continue loop
        asm.jmpb(bulkVectorLoop);

        emitAlign(crb, asm);
        // same loop as bulkVectorLoop, with only one vector
        asm.bind(singleVectorLoop);
        // check if single vector load is in bounds
        asm.cmpl(slotsRemaining, arraySlotsPerVector);
        asm.jcc(AMD64Assembler.ConditionFlag.Below, lessThanVectorSizeRemaining);
        // compare
        emitSingleVectorCompare(asm, vectorSize, result, vecCmp, vecArray1, cmpResult1);

        // check if a match was found
        asm.testl(cmpResult1, cmpResult1);
        asm.jcc(AMD64Assembler.ConditionFlag.NotZero, vectorFound1);
        // adjust number of array slots remaining
        asm.subl(slotsRemaining, arraySlotsPerVector);
        // adjust array pointer
        asm.addq(result, bytesPerVector);
        // continue loop
        asm.jmpb(singleVectorLoop);

        asm.bind(lessThanVectorSizeRemaining);
        // check if any array slots remain
        asm.testl(slotsRemaining, slotsRemaining);
        asm.jcc(AMD64Assembler.ConditionFlag.Zero, retNotFound);

        // a vector compare will read out of bounds of the input array.
        // check if the out-of-bounds read would cross a memory page boundary.
        // load copy of low part of array pointer
        asm.movl(cmpResult1, result);
        // check if pointer + vector size would cross the page boundary
        asm.andl(cmpResult1, (vmPageSize - 1));
        asm.cmpl(cmpResult1, (vmPageSize - bytesPerVector));
        // if the page boundary would be crossed, do byte/character-wise comparison instead.
        asm.jccb(AMD64Assembler.ConditionFlag.Above, lessThanVectorSizeRemainingLoop);
        // otherwise, do a vector compare that reads beyond array bounds
        emitSingleVectorCompare(asm, vectorSize, result, vecCmp, vecArray1, cmpResult1);
        // check if a match was found
        asm.testl(cmpResult1, cmpResult1);
        asm.jcc(AMD64Assembler.ConditionFlag.Zero, retNotFound);
        // find match offset
        asm.bsfq(cmpResult1, cmpResult1);
        if (charMode()) {
            // convert number of remaining characters to bytes
            asm.shll(slotsRemaining, 1);
        }
        // adjust array pointer for match result
        asm.addq(result, cmpResult1);
        // check if offset of matched value is greater than number of bytes remaining / out of array
        // bounds
        asm.cmpl(cmpResult1, slotsRemaining);
        // match is out of bounds, return no match
        asm.jcc(AMD64Assembler.ConditionFlag.GreaterEqual, retNotFound);
        // match is in bounds, return offset
        asm.jmpb(retFound);

        // compare remaining slots in the array one-by-one
        asm.bind(lessThanVectorSizeRemainingLoop);
        // check if any array slots remain
        asm.testl(slotsRemaining, slotsRemaining);
        asm.jcc(AMD64Assembler.ConditionFlag.Zero, retNotFound);
        // load char / byte
        AMD64Assembler.OperandSize operandSize = byteMode() ? AMD64Assembler.OperandSize.BYTE : AMD64Assembler.OperandSize.WORD;
        if (byteMode()) {
            AMD64Assembler.AMD64RMOp.MOVB.emit(asm, operandSize, cmpResult1, new AMD64Address(result));
        } else {
            AMD64Assembler.AMD64RMOp.MOV.emit(asm, operandSize, cmpResult1, new AMD64Address(result));
        }
        // check for match
        AMD64Assembler.AMD64BinaryArithmetic.CMP.getRMOpcode(operandSize).emit(asm, operandSize, cmpResult1, searchValue);
        asm.jcc(AMD64Assembler.ConditionFlag.Equal, retFound);
        // adjust number of array slots remaining
        asm.decrementl(slotsRemaining);
        // adjust array pointer
        asm.addq(result, kind.getByteCount());
        // continue loop
        asm.jmpb(lessThanVectorSizeRemainingLoop);

        // return -1 (no match)
        asm.bind(retNotFound);
        asm.movl(result, -1);
        asm.jmpb(end);

        emitVectorFoundWithOffset(asm, bytesPerVector, result, cmpResult2, vectorFound2, retFound);
        emitVectorFoundWithOffset(asm, bytesPerVector * 2, result, cmpResult3, vectorFound3, retFound);
        emitVectorFoundWithOffset(asm, bytesPerVector * 3, result, cmpResult4, vectorFound4, retFound);

        asm.bind(vectorFound1);
        // find index of first set bit in bit mask
        asm.bsfq(cmpResult1, cmpResult1);
        // add offset to array pointer
        asm.addq(result, cmpResult1);

        asm.bind(retFound);
        // convert array pointer to offset
        asm.subq(result, arrayPtr);
        emitBytesToArraySlots(asm, result);
        asm.bind(end);
    }

    private static void emitAlign(CompilationResultBuilder crb, AMD64MacroAssembler asm) {
        asm.align(crb.target.wordSize * 2);
    }

    /**
     * Fills {@code vecDst} with copies of its lowest byte or word.
     */
    private void emitBroadcast(AMD64MacroAssembler asm, Register vecDst, Register vecTmp, AVXKind.AVXSize vectorSize) {
        if (asm.supports(CPUFeature.AVX2)) {
            if (byteMode()) {
                VexRMOp.VPBROADCASTB.emit(asm, vectorSize, vecDst, vecDst);
            } else {
                VexRMOp.VPBROADCASTW.emit(asm, vectorSize, vecDst, vecDst);
            }
        } else if (asm.supports(CPUFeature.AVX)) {
            if (byteMode()) {
                // fill vecTmp with zeroes
                VexRVMOp.VPXOR.emit(asm, vectorSize, vecTmp, vecTmp, vecTmp);
                // broadcast loaded search value
                VexRVMOp.VPSHUFB.emit(asm, vectorSize, vecDst, vecDst, vecTmp);
            } else {
                // fill low qword
                VexRMIOp.VPSHUFLW.emit(asm, vectorSize, vecDst, vecDst, 0);
                // copy low qword to high qword
                VexRMIOp.VPSHUFD.emit(asm, vectorSize, vecDst, vecDst, 0);
            }
        } else {
            // SSE version
            if (byteMode()) {
                // fill vecTmp with zeroes
                asm.pxor(vecTmp, vecTmp);
                // broadcast loaded search value
                asm.pshufb(vecDst, vecTmp);
            } else {
                // fill low qword
                asm.pshuflw(vecDst, vecDst, 0);
                // copy low qword to high qword
                asm.pshufd(vecDst, vecDst, 0);
            }
        }
    }

    /**
     * Loads {@code vectorSize} bytes from the position pointed to by {@code arrayPtr} and compares
     * them to the search value stored in {@code vecCmp}. {@code vecArray} is overwritten by this
     * operation. The comparison result is stored in {@code cmpResult}.
     */
    private void emitSingleVectorCompare(AMD64MacroAssembler asm, AVXKind.AVXSize vectorSize,
                    Register arrayPtr, Register vecCmp, Register vecArray, Register cmpResult) {
        // load array contents into vector
        emitArrayLoad(asm, vectorSize, vecArray, arrayPtr, 0, false);
        // compare all loaded bytes to the search value.
        emitVectorCompare(asm, vectorSize, vecArray, vecCmp);
        // create a 32-bit-mask from the most significant bit of every byte in the comparison
        // result.
        emitMOVMSK(asm, vectorSize, cmpResult, vecArray);
    }

    /**
     * Convert a byte offset stored in {@code bytes} to an array index offset.
     */
    private void emitBytesToArraySlots(AMD64MacroAssembler asm, Register bytes) {
        if (charMode()) {
            asm.shrl(bytes, 1);
        } else {
            assert byteMode();
        }
    }

    private void emitBulkCompare(AMD64MacroAssembler asm,
                    AVXKind.AVXSize vectorSize,
                    int bytesPerVector,
                    Register arrayPtr,
                    Register vecCmp,
                    Register vecArray1,
                    Register vecArray2,
                    Register vecArray3,
                    Register vecArray4,
                    Register cmpResult1,
                    Register cmpResult2,
                    Register cmpResult3,
                    Register cmpResult4,
                    Label vectorFound1,
                    Label vectorFound2,
                    Label vectorFound3,
                    Label vectorFound4,
                    boolean alignedLoad) {
        // load array contents into vectors
        emitArrayLoad(asm, vectorSize, vecArray1, arrayPtr, 0, alignedLoad);
        emitArrayLoad(asm, vectorSize, vecArray2, arrayPtr, bytesPerVector, alignedLoad);
        emitArrayLoad(asm, vectorSize, vecArray3, arrayPtr, bytesPerVector * 2, alignedLoad);
        emitArrayLoad(asm, vectorSize, vecArray4, arrayPtr, bytesPerVector * 3, alignedLoad);
        // compare all loaded bytes to the search value.
        // matching bytes are set to 0xff, non-matching bytes are set to 0x00.
        emitVectorCompare(asm, vectorSize, vecArray1, vecCmp);
        emitVectorCompare(asm, vectorSize, vecArray2, vecCmp);
        emitVectorCompare(asm, vectorSize, vecArray3, vecCmp);
        emitVectorCompare(asm, vectorSize, vecArray4, vecCmp);
        // create 32-bit-masks from the most significant bit of every byte in the comparison
        // results.
        emitMOVMSK(asm, vectorSize, cmpResult1, vecArray1);
        emitMOVMSK(asm, vectorSize, cmpResult2, vecArray2);
        emitMOVMSK(asm, vectorSize, cmpResult3, vecArray3);
        emitMOVMSK(asm, vectorSize, cmpResult4, vecArray4);
        // check if a match was found
        asm.testl(cmpResult1, cmpResult1);
        asm.jcc(AMD64Assembler.ConditionFlag.NotZero, vectorFound1);
        asm.testl(cmpResult2, cmpResult2);
        asm.jcc(AMD64Assembler.ConditionFlag.NotZero, vectorFound2);
        asm.testl(cmpResult3, cmpResult3);
        asm.jcc(AMD64Assembler.ConditionFlag.NotZero, vectorFound3);
        asm.testl(cmpResult4, cmpResult4);
        asm.jcc(AMD64Assembler.ConditionFlag.NotZero, vectorFound4);
    }

    private static void emitVectorFoundWithOffset(AMD64MacroAssembler asm, int resultOffset, Register result, Register cmpResult, Label entry, Label ret) {
        asm.bind(entry);
        if (resultOffset > 0) {
            // adjust array pointer
            asm.addq(result, resultOffset);
        }
        // find index of first set bit in bit mask
        asm.bsfq(cmpResult, cmpResult);
        // add offset to array pointer
        asm.addq(result, cmpResult);
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

    private void emitVectorCompare(AMD64MacroAssembler asm, AVXKind.AVXSize vectorSize, Register vecArray, Register vecCmp) {
        // compare all loaded bytes to the search value.
        // matching bytes are set to 0xff, non-matching bytes are set to 0x00.
        if (asm.supports(CPUFeature.AVX)) {
            if (byteMode()) {
                VexRVMOp.VPCMPEQB.emit(asm, vectorSize, vecArray, vecCmp, vecArray);
            } else {
                VexRVMOp.VPCMPEQW.emit(asm, vectorSize, vecArray, vecCmp, vecArray);
            }
        } else {
            // SSE
            if (byteMode()) {
                asm.pcmpeqb(vecArray, vecCmp);
            } else {
                asm.pcmpeqw(vecArray, vecCmp);
            }
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

    private static boolean supportsAVX2(LIRGeneratorTool tool) {
        return supports(tool, CPUFeature.AVX2);
    }

    private static boolean supports(LIRGeneratorTool tool, CPUFeature cpuFeature) {
        return ((AMD64) tool.target().arch).getFeatures().contains(cpuFeature);
    }
}
