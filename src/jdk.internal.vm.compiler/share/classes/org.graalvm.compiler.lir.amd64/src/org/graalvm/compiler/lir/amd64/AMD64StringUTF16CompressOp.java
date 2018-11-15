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

import static jdk.vm.ci.amd64.AMD64.k1;
import static jdk.vm.ci.amd64.AMD64.k2;
import static jdk.vm.ci.amd64.AMD64.k3;
import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.rdi;
import static jdk.vm.ci.amd64.AMD64.rdx;
import static jdk.vm.ci.amd64.AMD64.rsi;

import static jdk.vm.ci.amd64.AMD64.rsp;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

import jdk.vm.ci.amd64.AMD64;
import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64Assembler;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.Value;

@Opcode("AMD64_STRING_COMPRESS")
public final class AMD64StringUTF16CompressOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64StringUTF16CompressOp> TYPE = LIRInstructionClass.create(AMD64StringUTF16CompressOp.class);

    @Def({REG}) private Value rres;
    @Alive({REG}) private Value rsrc;
    @Alive({REG}) private Value rdst;
    @Alive({REG}) private Value rlen;

    @Temp({REG}) private Value vtmp1;
    @Temp({REG}) private Value vtmp2;
    @Temp({REG}) private Value vtmp3;
    @Temp({REG}) private Value vtmp4;
    @Temp({REG}) private Value rtmp5;

    public AMD64StringUTF16CompressOp(LIRGeneratorTool tool, Value res, Value src, Value dst, Value len) {
        super(TYPE);

        assert asRegister(src).equals(rsi);
        assert asRegister(dst).equals(rdi);
        assert asRegister(len).equals(rdx);
        assert asRegister(res).equals(rax);

        rres = res;
        rsrc = src;
        rdst = dst;
        rlen = len;

        LIRKind vkind = LIRKind.value(AMD64Kind.V512_BYTE);

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
     * @param tmp1 (xmm) temporary xmm register
     * @param tmp2 (xmm) temporary xmm register
     * @param tmp3 (xmm) temporary xmm register
     * @param tmp4 (xmm) temporary xmm register
     * @param tmp (gpr) temporary gpr register
     * @param res (rax) the result code (length on success, zero otherwise)
     */
    private static void charArrayCompress(AMD64MacroAssembler masm, Register src, Register dst, Register len, Register tmp1,
                    Register tmp2, Register tmp3, Register tmp4, Register tmp, Register res) {
        assert tmp1.getRegisterCategory().equals(AMD64.XMM);
        assert tmp2.getRegisterCategory().equals(AMD64.XMM);
        assert tmp3.getRegisterCategory().equals(AMD64.XMM);
        assert tmp4.getRegisterCategory().equals(AMD64.XMM);

        Label labelReturnLength = new Label();
        Label labelReturnZero = new Label();
        Label labelDone = new Label();
        Label labelBelowThreshold = new Label();

        assert len.number != res.number;

        masm.push(len);      // Save length for return.

        if (masm.supports(AMD64.CPUFeature.AVX512BW) &&
                        masm.supports(AMD64.CPUFeature.AVX512VL) &&
                        masm.supports(AMD64.CPUFeature.BMI2)) {

            Label labelRestoreK1ReturnZero = new Label();
            Label labelAvxPostAlignment = new Label();

            // If the length of the string is less than 32, we chose not to use the
            // AVX512 instructions.
            masm.testl(len, -32);
            masm.jcc(AMD64Assembler.ConditionFlag.Zero, labelBelowThreshold);

            // First check whether a character is compressible (<= 0xff).
            // Create mask to test for Unicode chars inside (zmm) vector.
            masm.movl(res, 0x00ff);
            masm.evpbroadcastw(tmp2, res);

            masm.kmovq(k3, k1);      // Save k1

            masm.testl(len, -64);
            masm.jcc(AMD64Assembler.ConditionFlag.Zero, labelAvxPostAlignment);

            masm.movl(tmp, dst);
            masm.andl(tmp, (32 - 1));
            masm.negl(tmp);
            masm.andl(tmp, (32 - 1));

            // bail out when there is nothing to be done
            masm.testl(tmp, tmp);
            masm.jcc(AMD64Assembler.ConditionFlag.Zero, labelAvxPostAlignment);

            // Compute (1 << N) - 1 = ~(~0 << N), where N is the remaining number
            // of characters to process.
            masm.movl(res, -1);
            masm.shlxl(res, res, tmp);
            masm.notl(res);

            masm.kmovd(k1, res);
            masm.evmovdqu16(tmp1, k1, new AMD64Address(src));
            masm.evpcmpuw(k2, k1, tmp1, tmp2, 2 /* le */);
            masm.ktestd(k2, k1);
            masm.jcc(AMD64Assembler.ConditionFlag.CarryClear, labelRestoreK1ReturnZero);

            masm.evpmovwb(new AMD64Address(dst), k1, tmp1);

            masm.addq(src, tmp);
            masm.addq(src, tmp);
            masm.addq(dst, tmp);
            masm.subl(len, tmp);

            masm.bind(labelAvxPostAlignment);
            // end of alignment
            Label labelAvx512LoopTail = new Label();

            masm.movl(tmp, len);
            masm.andl(tmp, -32);         // The vector count (in chars).
            masm.jcc(AMD64Assembler.ConditionFlag.Zero, labelAvx512LoopTail);
            masm.andl(len, 32 - 1);      // The tail count (in chars).

            masm.leaq(src, new AMD64Address(src, tmp, AMD64Address.Scale.Times2));
            masm.leaq(dst, new AMD64Address(dst, tmp, AMD64Address.Scale.Times1));
            masm.negq(tmp);

            Label labelAvx512Loop = new Label();
            // Test and compress 32 chars per iteration, reading 512-bit vectors and
            // writing 256-bit compressed ditto.
            masm.bind(labelAvx512Loop);
            masm.evmovdqu16(tmp1, new AMD64Address(src, tmp, AMD64Address.Scale.Times2));
            masm.evpcmpuw(k2, tmp1, tmp2, 2 /* le */);
            masm.kortestd(k2, k2);
            masm.jcc(AMD64Assembler.ConditionFlag.CarryClear, labelRestoreK1ReturnZero);

            // All 32 chars in the current vector (chunk) are valid for compression,
            // write truncated byte elements to memory.
            masm.evpmovwb(new AMD64Address(dst, tmp, AMD64Address.Scale.Times1), tmp1);
            masm.addq(tmp, 32);
            masm.jcc(AMD64Assembler.ConditionFlag.NotZero, labelAvx512Loop);

            masm.bind(labelAvx512LoopTail);
            masm.kmovq(k1, k3);      // Restore k1

            // All done if the tail count is zero.
            masm.testl(len, len);
            masm.jcc(AMD64Assembler.ConditionFlag.Zero, labelReturnLength);

            // Compute (1 << N) - 1 = ~(~0 << N), where N is the remaining number
            // of characters to process.
            masm.movl(res, -1);
            masm.shlxl(res, res, len);
            masm.notl(res);

            masm.kmovd(k1, res);
            masm.evmovdqu16(tmp1, k1, new AMD64Address(src));
            masm.evpcmpuw(k2, k1, tmp1, tmp2, 2 /* le */);
            masm.ktestd(k2, k1);
            masm.jcc(AMD64Assembler.ConditionFlag.CarryClear, labelRestoreK1ReturnZero);

            masm.evpmovwb(new AMD64Address(dst), k1, tmp1);

            masm.kmovq(k1, k3);      // Restore k1
            masm.jmp(labelReturnLength);

            masm.bind(labelRestoreK1ReturnZero);
            masm.kmovq(k1, k3);      // Restore k1
            masm.jmp(labelReturnZero);
        }

        if (masm.supports(AMD64.CPUFeature.SSE4_2)) {

            Label labelSSETail = new Label();

            masm.bind(labelBelowThreshold);

            masm.movl(tmp, 0xff00ff00);  // Create mask to test for Unicode chars in vectors.

            masm.movl(res, len);
            masm.andl(res, -16);
            masm.jccb(AMD64Assembler.ConditionFlag.Zero, labelSSETail);
            masm.andl(len, 16 - 1);

            // Compress 16 chars per iteration.
            masm.movdl(tmp1, tmp);
            masm.pshufd(tmp1, tmp1, 0);    // Store Unicode mask in 'vtmp1'.
            masm.pxor(tmp4, tmp4);

            masm.leaq(src, new AMD64Address(src, res, AMD64Address.Scale.Times2));
            masm.leaq(dst, new AMD64Address(dst, res, AMD64Address.Scale.Times1));
            masm.negq(res);

            Label lSSELoop = new Label();
            // Test and compress 16 chars per iteration, reading 128-bit vectors and
            // writing 64-bit compressed ditto.
            masm.bind(lSSELoop);
            masm.movdqu(tmp2, new AMD64Address(src, res, AMD64Address.Scale.Times2));     // load
                                                                                          // 1st 8
                                                                                          // characters
            masm.movdqu(tmp3, new AMD64Address(src, res, AMD64Address.Scale.Times2, 16)); // load
                                                                                          // next 8
                                                                                          // characters
            masm.por(tmp4, tmp2);
            masm.por(tmp4, tmp3);
            masm.ptest(tmp4, tmp1);        // Check for Unicode chars in vector.
            masm.jcc(AMD64Assembler.ConditionFlag.NotZero, labelReturnZero);

            masm.packuswb(tmp2, tmp3);     // Only ASCII chars; compress each to a byte.
            masm.movdqu(new AMD64Address(dst, res, AMD64Address.Scale.Times1), tmp2);
            masm.addq(res, 16);
            masm.jcc(AMD64Assembler.ConditionFlag.NotZero, lSSELoop);

            Label labelCopyChars = new Label();
            // Test and compress another 8 chars before final tail copy.
            masm.bind(labelSSETail);
            masm.movl(res, len);
            masm.andl(res, -8);
            masm.jccb(AMD64Assembler.ConditionFlag.Zero, labelCopyChars);
            masm.andl(len, 8 - 1);

            masm.movdl(tmp1, tmp);
            masm.pshufd(tmp1, tmp1, 0);    // Store Unicode mask in 'vtmp1'.
            masm.pxor(tmp3, tmp3);

            masm.movdqu(tmp2, new AMD64Address(src));
            masm.ptest(tmp2, tmp1);        // Check for Unicode chars in vector.
            masm.jccb(AMD64Assembler.ConditionFlag.NotZero, labelReturnZero);
            masm.packuswb(tmp2, tmp3);     // Only ASCII chars; compress each to a byte.
            masm.movq(new AMD64Address(dst), tmp2);
            masm.addq(src, 16);
            masm.addq(dst, 8);

            masm.bind(labelCopyChars);
        }

        // Compress any remaining characters using a vanilla implementation.
        masm.testl(len, len);
        masm.jccb(AMD64Assembler.ConditionFlag.Zero, labelReturnLength);
        masm.leaq(src, new AMD64Address(src, len, AMD64Address.Scale.Times2));
        masm.leaq(dst, new AMD64Address(dst, len, AMD64Address.Scale.Times1));
        masm.negq(len);

        Label labelCopyCharsLoop = new Label();
        // Compress a single character per iteration.
        masm.bind(labelCopyCharsLoop);
        masm.movzwl(res, new AMD64Address(src, len, AMD64Address.Scale.Times2));
        masm.testl(res, 0xff00);     // Check if Unicode character.
        masm.jccb(AMD64Assembler.ConditionFlag.NotZero, labelReturnZero);
        // An ASCII character; compress to a byte.
        masm.movb(new AMD64Address(dst, len, AMD64Address.Scale.Times1), res);
        masm.incrementq(len, 1);
        masm.jcc(AMD64Assembler.ConditionFlag.NotZero, labelCopyCharsLoop);

        // If compression succeeded, return the length.
        masm.bind(labelReturnLength);
        masm.pop(res);
        masm.jmpb(labelDone);

        // If compression failed, return 0.
        masm.bind(labelReturnZero);
        masm.xorl(res, res);
        masm.addq(rsp, 8 /* wordSize */);

        masm.bind(labelDone);
    }

}
