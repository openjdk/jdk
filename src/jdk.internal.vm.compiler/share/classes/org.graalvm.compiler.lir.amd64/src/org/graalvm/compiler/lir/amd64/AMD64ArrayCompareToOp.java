/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.amd64.AMD64.k7;
import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.rcx;
import static jdk.vm.ci.amd64.AMD64.rdx;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

import java.util.EnumSet;

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

/**
 * Emits code which compares two arrays lexicographically. If the CPU supports any vector
 * instructions specialized code is emitted to leverage these instructions.
 */
@Opcode("ARRAY_COMPARE_TO")
public final class AMD64ArrayCompareToOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64ArrayCompareToOp> TYPE = LIRInstructionClass.create(AMD64ArrayCompareToOp.class);

    private final JavaKind kind1;
    private final JavaKind kind2;
    private final int array1BaseOffset;
    private final int array2BaseOffset;

    @Def({REG}) protected Value resultValue;
    @Alive({REG}) protected Value array1Value;
    @Alive({REG}) protected Value array2Value;
    @Use({REG}) protected Value length1Value;
    @Use({REG}) protected Value length2Value;
    @Temp({REG}) protected Value length1ValueTemp;
    @Temp({REG}) protected Value length2ValueTemp;
    @Temp({REG}) protected Value temp1;
    @Temp({REG}) protected Value temp2;

    @Temp({REG, ILLEGAL}) protected Value vectorTemp1;

    public AMD64ArrayCompareToOp(LIRGeneratorTool tool, JavaKind kind1, JavaKind kind2, Value result, Value array1, Value array2, Value length1, Value length2) {
        super(TYPE);
        this.kind1 = kind1;
        this.kind2 = kind2;

        // Both offsets should be the same but better be safe than sorry.
        this.array1BaseOffset = tool.getProviders().getMetaAccess().getArrayBaseOffset(kind1);
        this.array2BaseOffset = tool.getProviders().getMetaAccess().getArrayBaseOffset(kind2);

        this.resultValue = result;
        this.array1Value = array1;
        this.array2Value = array2;
        /*
         * The length values are inputs but are also killed like temporaries so need both Use and
         * Temp annotations, which will only work with fixed registers.
         */
        this.length1Value = length1;
        this.length2Value = length2;
        this.length1ValueTemp = length1;
        this.length2ValueTemp = length2;

        // Allocate some temporaries.
        this.temp1 = tool.newVariable(LIRKind.unknownReference(tool.target().arch.getWordKind()));
        this.temp2 = tool.newVariable(LIRKind.unknownReference(tool.target().arch.getWordKind()));

        // We only need the vector temporaries if we generate SSE code.
        if (supportsSSE42(tool.target())) {
            this.vectorTemp1 = tool.newVariable(LIRKind.value(AMD64Kind.DOUBLE));
        } else {
            this.vectorTemp1 = Value.ILLEGAL;
        }
    }

    private static boolean supportsSSE42(TargetDescription target) {
        AMD64 arch = (AMD64) target.arch;
        return arch.getFeatures().contains(CPUFeature.SSE4_2);
    }

    private static boolean supportsAVX2(TargetDescription target) {
        AMD64 arch = (AMD64) target.arch;
        return arch.getFeatures().contains(CPUFeature.AVX2);
    }

    private static boolean supportsAVX512VLBW(TargetDescription target) {
        EnumSet<CPUFeature> features = ((AMD64) target.arch).getFeatures();
        return features.contains(CPUFeature.AVX512BW) && features.contains(CPUFeature.AVX512VL);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        Register result = asRegister(resultValue);
        Register str1 = asRegister(temp1);
        Register str2 = asRegister(temp2);

        // Load array base addresses.
        masm.leaq(str1, new AMD64Address(asRegister(array1Value), array1BaseOffset));
        masm.leaq(str2, new AMD64Address(asRegister(array2Value), array2BaseOffset));
        Register cnt1 = asRegister(length1Value);
        Register cnt2 = asRegister(length2Value);

        // Checkstyle: stop
        Label LENGTH_DIFF_LABEL = new Label();
        Label POP_LABEL = new Label();
        Label DONE_LABEL = new Label();
        Label WHILE_HEAD_LABEL = new Label();
        Label COMPARE_WIDE_VECTORS_LOOP_FAILED = new Label(); // used only _LP64 && AVX3
        int stride, stride2;
        int adr_stride = -1;
        int adr_stride1 = -1;
        int adr_stride2 = -1;
        // Checkstyle: resume
        int stride2x2 = 0x40;
        AMD64Address.Scale scale = null;
        AMD64Address.Scale scale1 = null;
        AMD64Address.Scale scale2 = null;

        // if (ae != StrIntrinsicNode::LL) {
        if (kind1 == JavaKind.Byte && kind2 == JavaKind.Byte) {
            stride2x2 = 0x20;
        }

        // if (ae == StrIntrinsicNode::LU || ae == StrIntrinsicNode::UL) {
        if (kind1 != kind2) {
            masm.shrl(cnt2, 1);
        }
        // Compute the minimum of the string lengths and the
        // difference of the string lengths (stack).
        // Do the conditional move stuff
        masm.movl(result, cnt1);
        masm.subl(cnt1, cnt2);
        masm.push(cnt1);
        masm.cmovl(ConditionFlag.LessEqual, cnt2, result);    // cnt2 = min(cnt1, cnt2)

        // Is the minimum length zero?
        masm.testl(cnt2, cnt2);
        masm.jcc(ConditionFlag.Zero, LENGTH_DIFF_LABEL);
        // if (ae == StrIntrinsicNode::LL) {
        if (kind1 == JavaKind.Byte && kind2 == JavaKind.Byte) {
            // Load first bytes
            masm.movzbl(result, new AMD64Address(str1, 0));  // result = str1[0]
            masm.movzbl(cnt1, new AMD64Address(str2, 0));    // cnt1 = str2[0]
            // } else if (ae == StrIntrinsicNode::UU) {
        } else if (kind1 == JavaKind.Char && kind2 == JavaKind.Char) {
            // Load first characters
            masm.movzwl(result, new AMD64Address(str1, 0));
            masm.movzwl(cnt1, new AMD64Address(str2, 0));
        } else {
            masm.movzbl(result, new AMD64Address(str1, 0));
            masm.movzwl(cnt1, new AMD64Address(str2, 0));
        }
        masm.subl(result, cnt1);
        masm.jcc(ConditionFlag.NotZero, POP_LABEL);

        // if (ae == StrIntrinsicNode::UU) {
        if (kind1 == JavaKind.Char && kind2 == JavaKind.Char) {
            // Divide length by 2 to get number of chars
            masm.shrl(cnt2, 1);
        }
        masm.cmpl(cnt2, 1);
        masm.jcc(ConditionFlag.Equal, LENGTH_DIFF_LABEL);

        // Check if the strings start at the same location and setup scale and stride
        // if (ae == StrIntrinsicNode::LL || ae == StrIntrinsicNode::UU) {
        if (kind1 == kind2) {
            masm.cmpptr(str1, str2);
            masm.jcc(ConditionFlag.Equal, LENGTH_DIFF_LABEL);
            // if (ae == StrIntrinsicNode::LL) {
            if (kind1 == JavaKind.Byte && kind2 == JavaKind.Byte) {
                scale = AMD64Address.Scale.Times1;
                stride = 16;
            } else {
                scale = AMD64Address.Scale.Times2;
                stride = 8;
            }
        } else {
            scale1 = AMD64Address.Scale.Times1;
            scale2 = AMD64Address.Scale.Times2;
            // scale not used
            stride = 8;
        }

        // if (UseAVX >= 2 && UseSSE42Intrinsics) {
        if (supportsAVX2(crb.target) && supportsSSE42(crb.target)) {
            Register vec1 = asRegister(vectorTemp1, AMD64Kind.DOUBLE);

            // Checkstyle: stop
            Label COMPARE_WIDE_VECTORS = new Label();
            Label VECTOR_NOT_EQUAL = new Label();
            Label COMPARE_WIDE_TAIL = new Label();
            Label COMPARE_SMALL_STR = new Label();
            Label COMPARE_WIDE_VECTORS_LOOP = new Label();
            Label COMPARE_16_CHARS = new Label();
            Label COMPARE_INDEX_CHAR = new Label();
            Label COMPARE_WIDE_VECTORS_LOOP_AVX2 = new Label();
            Label COMPARE_TAIL_LONG = new Label();
            Label COMPARE_WIDE_VECTORS_LOOP_AVX3 = new Label();  // used only _LP64 && AVX3
            // Checkstyle: resume

            int pcmpmask = 0x19;
            // if (ae == StrIntrinsicNode::LL) {
            if (kind1 == JavaKind.Byte && kind2 == JavaKind.Byte) {
                pcmpmask &= ~0x01;
            }

            // Setup to compare 16-chars (32-bytes) vectors,
            // start from first character again because it has aligned address.
            // if (ae == StrIntrinsicNode::LL) {
            if (kind1 == JavaKind.Byte && kind2 == JavaKind.Byte) {
                stride2 = 32;
            } else {
                stride2 = 16;
            }
            // if (ae == StrIntrinsicNode::LL || ae == StrIntrinsicNode::UU) {
            if (kind1 == kind2) {
                adr_stride = stride << scale.log2;
            } else {
                adr_stride1 = 8;  // stride << scale1;
                adr_stride2 = 16; // stride << scale2;
            }

            assert result.equals(rax) && cnt2.equals(rdx) && cnt1.equals(rcx) : "pcmpestri";
            // rax and rdx are used by pcmpestri as elements counters
            masm.movl(result, cnt2);
            masm.andl(cnt2, ~(stride2 - 1));   // cnt2 holds the vector count
            masm.jcc(ConditionFlag.Zero, COMPARE_TAIL_LONG);

            // fast path : compare first 2 8-char vectors.
            masm.bind(COMPARE_16_CHARS);
            // if (ae == StrIntrinsicNode::LL || ae == StrIntrinsicNode::UU) {
            if (kind1 == kind2) {
                masm.movdqu(vec1, new AMD64Address(str1, 0));
            } else {
                masm.pmovzxbw(vec1, new AMD64Address(str1, 0));
            }
            masm.pcmpestri(vec1, new AMD64Address(str2, 0), pcmpmask);
            masm.jccb(ConditionFlag.Below, COMPARE_INDEX_CHAR);

            // if (ae == StrIntrinsicNode::LL || ae == StrIntrinsicNode::UU) {
            if (kind1 == kind2) {
                masm.movdqu(vec1, new AMD64Address(str1, adr_stride));
                masm.pcmpestri(vec1, new AMD64Address(str2, adr_stride), pcmpmask);
            } else {
                masm.pmovzxbw(vec1, new AMD64Address(str1, adr_stride1));
                masm.pcmpestri(vec1, new AMD64Address(str2, adr_stride2), pcmpmask);
            }
            masm.jccb(ConditionFlag.AboveEqual, COMPARE_WIDE_VECTORS);
            masm.addl(cnt1, stride);

            // Compare the characters at index in cnt1
            masm.bind(COMPARE_INDEX_CHAR); // cnt1 has the offset of the mismatching character
            loadNextElements(masm, result, cnt2, str1, str2, scale, scale1, scale2, cnt1);
            masm.subl(result, cnt2);
            masm.jmp(POP_LABEL);

            // Setup the registers to start vector comparison loop
            masm.bind(COMPARE_WIDE_VECTORS);
            // if (ae == StrIntrinsicNode::LL || ae == StrIntrinsicNode::UU) {
            if (kind1 == kind2) {
                masm.leaq(str1, new AMD64Address(str1, result, scale));
                masm.leaq(str2, new AMD64Address(str2, result, scale));
            } else {
                masm.leaq(str1, new AMD64Address(str1, result, scale1));
                masm.leaq(str2, new AMD64Address(str2, result, scale2));
            }
            masm.subl(result, stride2);
            masm.subl(cnt2, stride2);
            masm.jcc(ConditionFlag.Zero, COMPARE_WIDE_TAIL);
            masm.negq(result);

            // In a loop, compare 16-chars (32-bytes) at once using (vpxor+vptest)
            masm.bind(COMPARE_WIDE_VECTORS_LOOP);

            // if (VM_Version::supports_avx512vlbw()) { // trying 64 bytes fast loop
            if (supportsAVX512VLBW(crb.target)) {
                masm.cmpl(cnt2, stride2x2);
                masm.jccb(ConditionFlag.Below, COMPARE_WIDE_VECTORS_LOOP_AVX2);
                masm.testl(cnt2, stride2x2 - 1);   // cnt2 holds the vector count
                // means we cannot subtract by 0x40
                masm.jccb(ConditionFlag.NotZero, COMPARE_WIDE_VECTORS_LOOP_AVX2);

                masm.bind(COMPARE_WIDE_VECTORS_LOOP_AVX3); // the hottest loop
                // if (ae == StrIntrinsicNode::LL || ae == StrIntrinsicNode::UU) {
                if (kind1 == kind2) {
                    masm.evmovdqu64(vec1, new AMD64Address(str1, result, scale));
                    // k7 == 11..11, if operands equal, otherwise k7 has some 0
                    masm.evpcmpeqb(k7, vec1, new AMD64Address(str2, result, scale));
                } else {
                    masm.evpmovzxbw(vec1, new AMD64Address(str1, result, scale1));
                    // k7 == 11..11, if operands equal, otherwise k7 has some 0
                    masm.evpcmpeqb(k7, vec1, new AMD64Address(str2, result, scale2));
                }
                masm.kortestq(k7, k7);
                masm.jcc(ConditionFlag.AboveEqual, COMPARE_WIDE_VECTORS_LOOP_FAILED);     // miscompare
                masm.addq(result, stride2x2);  // update since we already compared at this addr
                masm.subl(cnt2, stride2x2);      // and sub the size too
                masm.jccb(ConditionFlag.NotZero, COMPARE_WIDE_VECTORS_LOOP_AVX3);

                masm.vpxor(vec1, vec1, vec1);
                masm.jmpb(COMPARE_WIDE_TAIL);
            }

            masm.bind(COMPARE_WIDE_VECTORS_LOOP_AVX2);
            // if (ae == StrIntrinsicNode::LL || ae == StrIntrinsicNode::UU) {
            if (kind1 == kind2) {
                masm.vmovdqu(vec1, new AMD64Address(str1, result, scale));
                masm.vpxor(vec1, vec1, new AMD64Address(str2, result, scale));
            } else {
                masm.vpmovzxbw(vec1, new AMD64Address(str1, result, scale1));
                masm.vpxor(vec1, vec1, new AMD64Address(str2, result, scale2));
            }
            masm.vptest(vec1, vec1);
            masm.jcc(ConditionFlag.NotZero, VECTOR_NOT_EQUAL);
            masm.addq(result, stride2);
            masm.subl(cnt2, stride2);
            masm.jcc(ConditionFlag.NotZero, COMPARE_WIDE_VECTORS_LOOP);
            // clean upper bits of YMM registers
            masm.vpxor(vec1, vec1, vec1);

            // compare wide vectors tail
            masm.bind(COMPARE_WIDE_TAIL);
            masm.testq(result, result);
            masm.jcc(ConditionFlag.Zero, LENGTH_DIFF_LABEL);

            masm.movl(result, stride2);
            masm.movl(cnt2, result);
            masm.negq(result);
            masm.jmp(COMPARE_WIDE_VECTORS_LOOP_AVX2);

            // Identifies the mismatching (higher or lower)16-bytes in the 32-byte vectors.
            masm.bind(VECTOR_NOT_EQUAL);
            // clean upper bits of YMM registers
            masm.vpxor(vec1, vec1, vec1);
            // if (ae == StrIntrinsicNode::LL || ae == StrIntrinsicNode::UU) {
            if (kind1 == kind2) {
                masm.leaq(str1, new AMD64Address(str1, result, scale));
                masm.leaq(str2, new AMD64Address(str2, result, scale));
            } else {
                masm.leaq(str1, new AMD64Address(str1, result, scale1));
                masm.leaq(str2, new AMD64Address(str2, result, scale2));
            }
            masm.jmp(COMPARE_16_CHARS);

            // Compare tail chars, length between 1 to 15 chars
            masm.bind(COMPARE_TAIL_LONG);
            masm.movl(cnt2, result);
            masm.cmpl(cnt2, stride);
            masm.jcc(ConditionFlag.Less, COMPARE_SMALL_STR);

            // if (ae == StrIntrinsicNode::LL || ae == StrIntrinsicNode::UU) {
            if (kind1 == kind2) {
                masm.movdqu(vec1, new AMD64Address(str1, 0));
            } else {
                masm.pmovzxbw(vec1, new AMD64Address(str1, 0));
            }
            masm.pcmpestri(vec1, new AMD64Address(str2, 0), pcmpmask);
            masm.jcc(ConditionFlag.Below, COMPARE_INDEX_CHAR);
            masm.subq(cnt2, stride);
            masm.jcc(ConditionFlag.Zero, LENGTH_DIFF_LABEL);
            // if (ae == StrIntrinsicNode::LL || ae == StrIntrinsicNode::UU) {
            if (kind1 == kind2) {
                masm.leaq(str1, new AMD64Address(str1, result, scale));
                masm.leaq(str2, new AMD64Address(str2, result, scale));
            } else {
                masm.leaq(str1, new AMD64Address(str1, result, scale1));
                masm.leaq(str2, new AMD64Address(str2, result, scale2));
            }
            masm.negq(cnt2);
            masm.jmpb(WHILE_HEAD_LABEL);

            masm.bind(COMPARE_SMALL_STR);
        } else if (supportsSSE42(crb.target)) {
            Register vec1 = asRegister(vectorTemp1, AMD64Kind.DOUBLE);

            // Checkstyle: stop
            Label COMPARE_WIDE_VECTORS = new Label();
            Label VECTOR_NOT_EQUAL = new Label();
            Label COMPARE_TAIL = new Label();
            // Checkstyle: resume
            int pcmpmask = 0x19;
            // Setup to compare 8-char (16-byte) vectors,
            // start from first character again because it has aligned address.
            masm.movl(result, cnt2);
            masm.andl(cnt2, ~(stride - 1));   // cnt2 holds the vector count
            // if (ae == StrIntrinsicNode::LL) {
            if (kind1 == JavaKind.Byte && kind2 == JavaKind.Byte) {
                pcmpmask &= ~0x01;
            }
            masm.jcc(ConditionFlag.Zero, COMPARE_TAIL);
            // if (ae == StrIntrinsicNode::LL || ae == StrIntrinsicNode::UU) {
            if (kind1 == kind2) {
                masm.leaq(str1, new AMD64Address(str1, result, scale));
                masm.leaq(str2, new AMD64Address(str2, result, scale));
            } else {
                masm.leaq(str1, new AMD64Address(str1, result, scale1));
                masm.leaq(str2, new AMD64Address(str2, result, scale2));
            }
            masm.negq(result);

            // pcmpestri
            // inputs:
            // vec1- substring
            // rax - negative string length (elements count)
            // mem - scanned string
            // rdx - string length (elements count)
            // pcmpmask - cmp mode: 11000 (string compare with negated result)
            // + 00 (unsigned bytes) or + 01 (unsigned shorts)
            // outputs:
            // rcx - first mismatched element index
            assert result.equals(rax) && cnt2.equals(rdx) && cnt1.equals(rcx) : "pcmpestri";

            masm.bind(COMPARE_WIDE_VECTORS);
            // if (ae == StrIntrinsicNode::LL || ae == StrIntrinsicNode::UU) {
            if (kind1 == kind2) {
                masm.movdqu(vec1, new AMD64Address(str1, result, scale));
                masm.pcmpestri(vec1, new AMD64Address(str2, result, scale), pcmpmask);
            } else {
                masm.pmovzxbw(vec1, new AMD64Address(str1, result, scale1));
                masm.pcmpestri(vec1, new AMD64Address(str2, result, scale2), pcmpmask);
            }
            // After pcmpestri cnt1(rcx) contains mismatched element index

            masm.jccb(ConditionFlag.Below, VECTOR_NOT_EQUAL);  // CF==1
            masm.addq(result, stride);
            masm.subq(cnt2, stride);
            masm.jccb(ConditionFlag.NotZero, COMPARE_WIDE_VECTORS);

            // compare wide vectors tail
            masm.testq(result, result);
            masm.jcc(ConditionFlag.Zero, LENGTH_DIFF_LABEL);

            masm.movl(cnt2, stride);
            masm.movl(result, stride);
            masm.negq(result);
            // if (ae == StrIntrinsicNode::LL || ae == StrIntrinsicNode::UU) {
            if (kind1 == kind2) {
                masm.movdqu(vec1, new AMD64Address(str1, result, scale));
                masm.pcmpestri(vec1, new AMD64Address(str2, result, scale), pcmpmask);
            } else {
                masm.pmovzxbw(vec1, new AMD64Address(str1, result, scale1));
                masm.pcmpestri(vec1, new AMD64Address(str2, result, scale2), pcmpmask);
            }
            masm.jccb(ConditionFlag.AboveEqual, LENGTH_DIFF_LABEL);

            // Mismatched characters in the vectors
            masm.bind(VECTOR_NOT_EQUAL);
            masm.addq(cnt1, result);
            loadNextElements(masm, result, cnt2, str1, str2, scale, scale1, scale2, cnt1);
            masm.subl(result, cnt2);
            masm.jmpb(POP_LABEL);

            masm.bind(COMPARE_TAIL); // limit is zero
            masm.movl(cnt2, result);
            // Fallthru to tail compare
        }

        // Shift str2 and str1 to the end of the arrays, negate min
        // if (ae == StrIntrinsicNode::LL || ae == StrIntrinsicNode::UU) {
        if (kind1 == kind2) {
            masm.leaq(str1, new AMD64Address(str1, cnt2, scale));
            masm.leaq(str2, new AMD64Address(str2, cnt2, scale));
        } else {
            masm.leaq(str1, new AMD64Address(str1, cnt2, scale1));
            masm.leaq(str2, new AMD64Address(str2, cnt2, scale2));
        }
        masm.decrementl(cnt2);  // first character was compared already
        masm.negq(cnt2);

        // Compare the rest of the elements
        masm.bind(WHILE_HEAD_LABEL);
        loadNextElements(masm, result, cnt1, str1, str2, scale, scale1, scale2, cnt2);
        masm.subl(result, cnt1);
        masm.jccb(ConditionFlag.NotZero, POP_LABEL);
        masm.incrementq(cnt2, 1);
        masm.jccb(ConditionFlag.NotZero, WHILE_HEAD_LABEL);

        // Strings are equal up to min length. Return the length difference.
        masm.bind(LENGTH_DIFF_LABEL);
        masm.pop(result);
        // if (ae == StrIntrinsicNode::UU) {
        if (kind1 == JavaKind.Char && kind2 == JavaKind.Char) {
            // Divide diff by 2 to get number of chars
            masm.sarl(result, 1);
        }
        masm.jmpb(DONE_LABEL);

        // if (VM_Version::supports_avx512vlbw()) {
        if (supportsAVX512VLBW(crb.target)) {
            masm.bind(COMPARE_WIDE_VECTORS_LOOP_FAILED);

            masm.kmovq(cnt1, k7);
            masm.notq(cnt1);
            masm.bsfq(cnt2, cnt1);
            // if (ae != StrIntrinsicNode::LL) {
            if (kind1 != JavaKind.Byte && kind2 != JavaKind.Byte) {
                // Divide diff by 2 to get number of chars
                masm.sarl(cnt2, 1);
            }
            masm.addq(result, cnt2);
            // if (ae == StrIntrinsicNode::LL) {
            if (kind1 == JavaKind.Byte && kind2 == JavaKind.Byte) {
                masm.movzbl(cnt1, new AMD64Address(str2, result, Scale.Times1));
                masm.movzbl(result, new AMD64Address(str1, result, Scale.Times1));
            } else if (kind1 == JavaKind.Char && kind2 == JavaKind.Char) {
                masm.movzwl(cnt1, new AMD64Address(str2, result, scale));
                masm.movzwl(result, new AMD64Address(str1, result, scale));
            } else {
                masm.movzwl(cnt1, new AMD64Address(str2, result, scale2));
                masm.movzbl(result, new AMD64Address(str1, result, scale1));
            }
            masm.subl(result, cnt1);
            masm.jmpb(POP_LABEL);
        }

        // Discard the stored length difference
        masm.bind(POP_LABEL);
        masm.pop(cnt1);

        // That's it
        masm.bind(DONE_LABEL);
        // if (ae == StrIntrinsicNode::UL) {
        if (kind1 == JavaKind.Char && kind2 == JavaKind.Byte) {
            masm.negl(result);
        }
    }

    private void loadNextElements(AMD64MacroAssembler masm, Register elem1, Register elem2, Register str1, Register str2,
                    AMD64Address.Scale scale, AMD64Address.Scale scale1,
                    AMD64Address.Scale scale2, Register index) {
        // if (ae == StrIntrinsicNode::LL) {
        if (kind1 == JavaKind.Byte && kind2 == JavaKind.Byte) {
            masm.movzbl(elem1, new AMD64Address(str1, index, scale, 0));
            masm.movzbl(elem2, new AMD64Address(str2, index, scale, 0));
            // } else if (ae == StrIntrinsicNode::UU) {
        } else if (kind1 == JavaKind.Char && kind2 == JavaKind.Char) {
            masm.movzwl(elem1, new AMD64Address(str1, index, scale, 0));
            masm.movzwl(elem2, new AMD64Address(str2, index, scale, 0));
        } else {
            masm.movzbl(elem1, new AMD64Address(str1, index, scale1, 0));
            masm.movzwl(elem2, new AMD64Address(str2, index, scale2, 0));
        }
    }
}
