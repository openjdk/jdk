/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.amd64.AMD64.k2;
import static jdk.vm.ci.amd64.AMD64.k3;
import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.rdi;
import static jdk.vm.ci.amd64.AMD64.rdx;
import static jdk.vm.ci.amd64.AMD64.rsi;
import static jdk.vm.ci.amd64.AMD64.rsp;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;
import static org.graalvm.compiler.lir.amd64.AMD64StringLatin1InflateOp.useAVX512ForStringInflateCompress;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.EVEXComparisonPredicate;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.Value;

@Opcode("AMD64_STRING_COMPRESS")
public final class AMD64StringUTF16CompressOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64StringUTF16CompressOp> TYPE = LIRInstructionClass.create(AMD64StringUTF16CompressOp.class);

    private final int useAVX3Threshold;

    @Def({REG}) private Value rres;
    @Use({REG}) private Value rsrc;
    @Use({REG}) private Value rdst;
    @Use({REG}) private Value rlen;

    @Temp({REG}) private Value rsrcTemp;
    @Temp({REG}) private Value rdstTemp;
    @Temp({REG}) private Value rlenTemp;

    @Temp({REG}) private Value vtmp1;
    @Temp({REG}) private Value vtmp2;
    @Temp({REG}) private Value vtmp3;
    @Temp({REG}) private Value vtmp4;
    @Temp({REG}) private Value rtmp5;

    public AMD64StringUTF16CompressOp(LIRGeneratorTool tool, int useAVX3Threshold, Value res, Value src, Value dst, Value len) {
        super(TYPE);

        this.useAVX3Threshold = useAVX3Threshold;

        assert asRegister(src).equals(rsi);
        assert asRegister(dst).equals(rdi);
        assert asRegister(len).equals(rdx);
        assert asRegister(res).equals(rax);

        rres = res;
        rsrcTemp = rsrc = src;
        rdstTemp = rdst = dst;
        rlenTemp = rlen = len;

        LIRKind vkind = useAVX512ForStringInflateCompress(tool.target()) ? LIRKind.value(AMD64Kind.V512_BYTE) : LIRKind.value(AMD64Kind.V128_BYTE);

        vtmp1 = tool.newVariable(vkind);
        vtmp2 = tool.newVariable(vkind);
        vtmp3 = tool.newVariable(vkind);
        vtmp4 = tool.newVariable(vkind);

        rtmp5 = tool.newVariable(LIRKind.value(AMD64Kind.DWORD));
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        Register res = asRegister(rres);
        Register src = asRegister(rsrc);
        Register dst = asRegister(rdst);
        Register len = asRegister(rlen);

        Register tmp1 = asRegister(vtmp1);
        Register tmp2 = asRegister(vtmp2);
        Register tmp3 = asRegister(vtmp3);
        Register tmp4 = asRegister(vtmp4);
        Register tmp5 = asRegister(rtmp5);

        charArrayCompress(masm, src, dst, len, tmp1, tmp2, tmp3, tmp4, tmp5, res);
    }

    /**
     * Compress a UTF16 string which de facto is a Latin1 string into a byte array representation
     * (buffer).
     *
     * @param masm the assembler
     * @param src (rsi) the start address of source char[] to be compressed
     * @param dst (rdi) the start address of destination byte[] vector
     * @param len (rdx) the length
     * @param tmp1Reg (xmm) temporary xmm register
     * @param tmp2Reg (xmm) temporary xmm register
     * @param tmp3Reg (xmm) temporary xmm register
     * @param tmp4Reg (xmm) temporary xmm register
     * @param tmp5 (gpr) temporary gpr register
     * @param result (rax) the result code (length on success, zero otherwise)
     */
    private void charArrayCompress(AMD64MacroAssembler masm, Register src, Register dst, Register len, Register tmp1Reg,
                    Register tmp2Reg, Register tmp3Reg, Register tmp4Reg, Register tmp5, Register result) {
        assert tmp1Reg.getRegisterCategory().equals(AMD64.XMM);
        assert tmp2Reg.getRegisterCategory().equals(AMD64.XMM);
        assert tmp3Reg.getRegisterCategory().equals(AMD64.XMM);
        assert tmp4Reg.getRegisterCategory().equals(AMD64.XMM);

        Label labelCopyCharsLoop = new Label();
        Label labelReturnLength = new Label();
        Label labelReturnZero = new Label();
        Label labelDone = new Label();

        assert len.number != result.number;

        // Save length for return.
        masm.push(len);

        if (useAVX3Threshold == 0 && useAVX512ForStringInflateCompress(masm.target)) {
            Label labelCopy32Loop = new Label();
            Label labelCopyLoopTail = new Label();
            Label labelBelowThreshold = new Label();
            Label labelPostAlignment = new Label();

            // If the length of the string is less than 32, we chose not to use the
            // AVX512 instructions.
            masm.testlAndJcc(len, -32, ConditionFlag.Zero, labelBelowThreshold, false);

            // First check whether a character is compressible (<= 0xff).
            // Create mask to test for Unicode chars inside (zmm) vector.
            masm.movl(result, 0x00ff);
            masm.evpbroadcastw(tmp2Reg, result);

            masm.testlAndJcc(len, -64, ConditionFlag.Zero, labelPostAlignment, false);

            masm.movl(tmp5, dst);
            masm.andl(tmp5, (32 - 1));
            masm.negl(tmp5);
            masm.andl(tmp5, (32 - 1));

            // bail out when there is nothing to be done
            masm.testlAndJcc(tmp5, tmp5, ConditionFlag.Zero, labelPostAlignment, false);

            // Compute (1 << N) - 1 = ~(~0 << N), where N is the remaining number
            // of characters to process.
            masm.movl(result, 0xFFFFFFFF);
            masm.shlxl(result, result, tmp5);
            masm.notl(result);
            masm.kmovd(k3, result);

            masm.evmovdqu16(tmp1Reg, k3, new AMD64Address(src));
            masm.evpcmpuw(k2, k3, tmp1Reg, tmp2Reg, EVEXComparisonPredicate.LE);
            masm.ktestd(k2, k3);
            masm.jcc(ConditionFlag.CarryClear, labelReturnZero);

            masm.evpmovwb(new AMD64Address(dst), k3, tmp1Reg);

            masm.addq(src, tmp5);
            masm.addq(src, tmp5);
            masm.addq(dst, tmp5);
            masm.subl(len, tmp5);

            masm.bind(labelPostAlignment);
            // end of alignment

            masm.movl(tmp5, len);
            masm.andl(tmp5, 32 - 1);    // The tail count (in chars).
            // The vector count (in chars).
            masm.andlAndJcc(len, ~(32 - 1), ConditionFlag.Zero, labelCopyLoopTail, false);

            masm.leaq(src, new AMD64Address(src, len, AMD64Address.Scale.Times2));
            masm.leaq(dst, new AMD64Address(dst, len, AMD64Address.Scale.Times1));
            masm.negq(len);

            // Test and compress 32 chars per iteration, reading 512-bit vectors and
            // writing 256-bit compressed ditto.
            masm.bind(labelCopy32Loop);
            masm.evmovdqu16(tmp1Reg, new AMD64Address(src, len, AMD64Address.Scale.Times2));
            masm.evpcmpuw(k2, tmp1Reg, tmp2Reg, EVEXComparisonPredicate.LE);
            masm.kortestd(k2, k2);
            masm.jcc(ConditionFlag.CarryClear, labelReturnZero);

            // All 32 chars in the current vector (chunk) are valid for compression,
            // write truncated byte elements to memory.
            masm.evpmovwb(new AMD64Address(dst, len, AMD64Address.Scale.Times1), tmp1Reg);
            masm.addqAndJcc(len, 32, ConditionFlag.NotZero, labelCopy32Loop, false);

            masm.bind(labelCopyLoopTail);
            // All done if the tail count is zero.
            masm.testlAndJcc(tmp5, tmp5, ConditionFlag.Zero, labelReturnLength, false);

            masm.movl(len, tmp5);

            // Compute (1 << N) - 1 = ~(~0 << N), where N is the remaining number
            // of characters to process.
            masm.movl(result, -1);
            masm.shlxl(result, result, len);
            masm.notl(result);

            masm.kmovd(k3, result);

            masm.evmovdqu16(tmp1Reg, k3, new AMD64Address(src));
            masm.evpcmpuw(k2, k3, tmp1Reg, tmp2Reg, EVEXComparisonPredicate.LE);
            masm.ktestd(k2, k3);
            masm.jcc(ConditionFlag.CarryClear, labelReturnZero);

            masm.evpmovwb(new AMD64Address(dst), k3, tmp1Reg);
            masm.jmp(labelReturnLength);

            masm.bind(labelBelowThreshold);
        }

        if (masm.supports(AMD64.CPUFeature.SSE4_2)) {
            Label labelCopy32Loop = new Label();
            Label labelCopy16 = new Label();
            Label labelCopyTail = new Label();

            masm.movl(result, len);

            masm.movl(tmp5, 0xff00ff00);  // Create mask to test for Unicode chars in vectors.

            // vectored compression
            masm.andl(len, 0xfffffff0); // vector count (in chars)
            masm.andl(result, 0x0000000f); // tail count (in chars)
            masm.testlAndJcc(len, len, ConditionFlag.Zero, labelCopy16, false);

            // Compress 16 chars per iteration.
            masm.movdl(tmp1Reg, tmp5);
            masm.pshufd(tmp1Reg, tmp1Reg, 0);    // Store Unicode mask in 'vtmp1'.
            masm.pxor(tmp4Reg, tmp4Reg);

            masm.leaq(src, new AMD64Address(src, len, AMD64Address.Scale.Times2));
            masm.leaq(dst, new AMD64Address(dst, len, AMD64Address.Scale.Times1));
            masm.negq(len);

            // Test and compress 16 chars per iteration, reading 128-bit vectors and
            // writing 64-bit compressed ditto.
            masm.bind(labelCopy32Loop);
            // load 1st 8 characters
            masm.movdqu(tmp2Reg, new AMD64Address(src, len, AMD64Address.Scale.Times2));
            masm.por(tmp4Reg, tmp2Reg);
            // load next 8 characters
            masm.movdqu(tmp3Reg, new AMD64Address(src, len, AMD64Address.Scale.Times2, 16));
            masm.por(tmp4Reg, tmp3Reg);
            masm.ptest(tmp4Reg, tmp1Reg);        // Check for Unicode chars in vector.
            masm.jcc(ConditionFlag.NotZero, labelReturnZero);
            masm.packuswb(tmp2Reg, tmp3Reg);     // Only ASCII chars; compress each to a byte.
            masm.movdqu(new AMD64Address(dst, len, AMD64Address.Scale.Times1), tmp2Reg);
            masm.addqAndJcc(len, 16, ConditionFlag.NotZero, labelCopy32Loop, false);

            // Test and compress another 8 chars before final tail copy.
            masm.bind(labelCopy16);
            masm.movl(len, result);
            masm.andl(len, 0xfffffff8); // vector count (in chars)
            masm.andl(result, 0x00000007); // tail count (in chars)
            masm.testlAndJcc(len, len, ConditionFlag.Zero, labelCopyTail, true);

            masm.movdl(tmp1Reg, tmp5);
            masm.pshufd(tmp1Reg, tmp1Reg, 0);    // Store Unicode mask in 'vtmp1'.
            masm.pxor(tmp3Reg, tmp3Reg);

            masm.movdqu(tmp2Reg, new AMD64Address(src));
            masm.ptest(tmp2Reg, tmp1Reg);        // Check for Unicode chars in vector.
            masm.jccb(ConditionFlag.NotZero, labelReturnZero);
            masm.packuswb(tmp2Reg, tmp3Reg);     // Only ASCII chars; compress each to a byte.
            masm.movq(new AMD64Address(dst), tmp2Reg);
            masm.addq(src, 16);
            masm.addq(dst, 8);

            masm.bind(labelCopyTail);
            masm.movl(len, result);
        }

        // Compress any remaining characters using a vanilla implementation.
        masm.testlAndJcc(len, len, ConditionFlag.Zero, labelReturnLength, true);
        masm.leaq(src, new AMD64Address(src, len, AMD64Address.Scale.Times2));
        masm.leaq(dst, new AMD64Address(dst, len, AMD64Address.Scale.Times1));
        masm.negq(len);

        // Compress a single character per iteration.
        masm.bind(labelCopyCharsLoop);
        masm.movzwl(result, new AMD64Address(src, len, AMD64Address.Scale.Times2));
        // Check if Unicode character.
        masm.testlAndJcc(result, 0xff00, ConditionFlag.NotZero, labelReturnZero, true);
        // An ASCII character; compress to a byte.
        masm.movb(new AMD64Address(dst, len, AMD64Address.Scale.Times1), result);
        masm.incqAndJcc(len, ConditionFlag.NotZero, labelCopyCharsLoop, false);

        // If compression succeeded, return the length.
        masm.bind(labelReturnLength);
        masm.pop(result);
        masm.jmpb(labelDone);

        // If compression failed, return 0.
        masm.bind(labelReturnZero);
        masm.xorl(result, result);
        masm.addq(rsp, 8 /* wordSize */);

        masm.bind(labelDone);
    }

    @Override
    public boolean needsClearUpperVectorRegisters() {
        return true;
    }
}
