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
import static jdk.vm.ci.amd64.AMD64.rdi;
import static jdk.vm.ci.amd64.AMD64.rdx;
import static jdk.vm.ci.amd64.AMD64.rsi;

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

@Opcode("AMD64_STRING_INFLATE")
public final class AMD64StringLatin1InflateOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64StringLatin1InflateOp> TYPE = LIRInstructionClass.create(AMD64StringLatin1InflateOp.class);

    @Alive({REG}) private Value rsrc;
    @Alive({REG}) private Value rdst;
    @Alive({REG}) private Value rlen;

    @Temp({REG}) private Value vtmp1;
    @Temp({REG}) private Value rtmp2;

    public AMD64StringLatin1InflateOp(LIRGeneratorTool tool, Value src, Value dst, Value len) {
        super(TYPE);

        assert asRegister(src).equals(rsi);
        assert asRegister(dst).equals(rdi);
        assert asRegister(len).equals(rdx);

        rsrc = src;
        rdst = dst;
        rlen = len;

        vtmp1 = tool.newVariable(LIRKind.value(AMD64Kind.V512_BYTE));
        rtmp2 = tool.newVariable(LIRKind.value(AMD64Kind.DWORD));
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        Register src = asRegister(rsrc);
        Register dst = asRegister(rdst);
        Register len = asRegister(rlen);

        Register tmp1 = asRegister(vtmp1);
        Register tmp2 = asRegister(rtmp2);

        byteArrayInflate(masm, src, dst, len, tmp1, tmp2);
    }

    /**
     * Inflate a Latin1 string using a byte[] array representation into a UTF16 string using a
     * char[] array representation.
     *
     * @param masm the assembler
     * @param src (rsi) the start address of source byte[] to be inflated
     * @param dst (rdi) the start address of destination char[] array
     * @param len (rdx) the length
     * @param vtmp (xmm) temporary xmm register
     * @param tmp (gpr) temporary gpr register
     */
    private static void byteArrayInflate(AMD64MacroAssembler masm, Register src, Register dst, Register len, Register vtmp, Register tmp) {
        assert vtmp.getRegisterCategory().equals(AMD64.XMM);

        Label labelDone = new Label();
        Label labelBelowThreshold = new Label();

        assert src.number != dst.number && src.number != len.number && src.number != tmp.number;
        assert dst.number != len.number && dst.number != tmp.number;
        assert len.number != tmp.number;

        if (masm.supports(AMD64.CPUFeature.AVX512BW) &&
                        masm.supports(AMD64.CPUFeature.AVX512VL) &&
                        masm.supports(AMD64.CPUFeature.BMI2)) {

            // If the length of the string is less than 16, we chose not to use the
            // AVX512 instructions.
            masm.testl(len, -16);
            masm.jcc(AMD64Assembler.ConditionFlag.Zero, labelBelowThreshold);

            Label labelAvx512Tail = new Label();
            // Test for suitable number chunks with respect to the size of the vector
            // operation, mask off remaining number of chars (bytes) to inflate (such
            // that 'len' will always hold the number of bytes left to inflate) after
            // committing to the vector loop.
            // Adjust vector pointers to upper address bounds and inverse loop index.
            // This will keep the loop condition simple.
            //
            // NOTE: The above idiom/pattern is used in all the loops below.

            masm.movl(tmp, len);
            masm.andl(tmp, -32);     // The vector count (in chars).
            masm.jccb(AMD64Assembler.ConditionFlag.Zero, labelAvx512Tail);
            masm.andl(len, 32 - 1);  // The tail count (in chars).

            masm.leaq(src, new AMD64Address(src, tmp, AMD64Address.Scale.Times1));
            masm.leaq(dst, new AMD64Address(dst, tmp, AMD64Address.Scale.Times2));
            masm.negq(tmp);

            Label labelAvx512Loop = new Label();
            // Inflate 32 chars per iteration, reading 256-bit compact vectors
            // and writing 512-bit inflated ditto.
            masm.bind(labelAvx512Loop);
            masm.evpmovzxbw(vtmp, new AMD64Address(src, tmp, AMD64Address.Scale.Times1));
            masm.evmovdqu16(new AMD64Address(dst, tmp, AMD64Address.Scale.Times2), vtmp);
            masm.addq(tmp, 32);
            masm.jcc(AMD64Assembler.ConditionFlag.NotZero, labelAvx512Loop);

            masm.bind(labelAvx512Tail);
            // All done if the tail count is zero.
            masm.testl(len, len);
            masm.jcc(AMD64Assembler.ConditionFlag.Zero, labelDone);

            masm.kmovq(k2, k1);      // Save k1

            // Compute (1 << N) - 1 = ~(~0 << N), where N is the remaining number
            // of characters to process.
            masm.movl(tmp, -1);
            masm.shlxl(tmp, tmp, len);
            masm.notl(tmp);

            masm.kmovd(k1, tmp);
            masm.evpmovzxbw(vtmp, k1, new AMD64Address(src));
            masm.evmovdqu16(new AMD64Address(dst), k1, vtmp);
            masm.kmovq(k1, k2);      // Restore k1
            masm.jmp(labelDone);
        }

        if (masm.supports(AMD64.CPUFeature.SSE4_1)) {

            Label labelSSETail = new Label();

            if (masm.supports(AMD64.CPUFeature.AVX2)) {

                Label labelAvx2Tail = new Label();

                masm.movl(tmp, len);
                masm.andl(tmp, -16);
                masm.jccb(AMD64Assembler.ConditionFlag.Zero, labelAvx2Tail);
                masm.andl(len, 16 - 1);

                masm.leaq(src, new AMD64Address(src, tmp, AMD64Address.Scale.Times1));
                masm.leaq(dst, new AMD64Address(dst, tmp, AMD64Address.Scale.Times2));
                masm.negq(tmp);

                Label labelAvx2Loop = new Label();
                // Inflate 16 bytes (chars) per iteration, reading 128-bit compact vectors
                // and writing 256-bit inflated ditto.
                masm.bind(labelAvx2Loop);
                masm.vpmovzxbw(vtmp, new AMD64Address(src, tmp, AMD64Address.Scale.Times1));
                masm.vmovdqu(new AMD64Address(dst, tmp, AMD64Address.Scale.Times2), vtmp);
                masm.addq(tmp, 16);
                masm.jcc(AMD64Assembler.ConditionFlag.NotZero, labelAvx2Loop);

                masm.bind(labelBelowThreshold);
                masm.bind(labelAvx2Tail);

                masm.movl(tmp, len);
                masm.andl(tmp, -8);
                masm.jccb(AMD64Assembler.ConditionFlag.Zero, labelSSETail);
                masm.andl(len, 8 - 1);

                // Inflate another 8 bytes before final tail copy.
                masm.pmovzxbw(vtmp, new AMD64Address(src));
                masm.movdqu(new AMD64Address(dst), vtmp);
                masm.addq(src, 8);
                masm.addq(dst, 16);

                // Fall-through to labelSSETail.
            } else {
                // When there is no AVX2 support available, we use AVX/SSE support to
                // inflate into maximum 128-bits per operation.

                masm.movl(tmp, len);
                masm.andl(tmp, -8);
                masm.jccb(AMD64Assembler.ConditionFlag.Zero, labelSSETail);
                masm.andl(len, 8 - 1);

                masm.leaq(src, new AMD64Address(src, tmp, AMD64Address.Scale.Times1));
                masm.leaq(dst, new AMD64Address(dst, tmp, AMD64Address.Scale.Times2));
                masm.negq(tmp);

                Label labelSSECopy8Loop = new Label();
                // Inflate 8 bytes (chars) per iteration, reading 64-bit compact vectors
                // and writing 128-bit inflated ditto.
                masm.bind(labelSSECopy8Loop);
                masm.pmovzxbw(vtmp, new AMD64Address(src, tmp, AMD64Address.Scale.Times1));
                masm.movdqu(new AMD64Address(dst, tmp, AMD64Address.Scale.Times2), vtmp);
                masm.addq(tmp, 8);
                masm.jcc(AMD64Assembler.ConditionFlag.NotZero, labelSSECopy8Loop);

                // Fall-through to labelSSETail.
            }

            Label labelCopyChars = new Label();

            masm.bind(labelSSETail);
            masm.cmpl(len, 4);
            masm.jccb(AMD64Assembler.ConditionFlag.Less, labelCopyChars);

            masm.movdl(vtmp, new AMD64Address(src));
            masm.pmovzxbw(vtmp, vtmp);
            masm.movq(new AMD64Address(dst), vtmp);
            masm.subq(len, 4);
            masm.addq(src, 4);
            masm.addq(dst, 8);

            masm.bind(labelCopyChars);
        }

        // Inflate any remaining characters (bytes) using a vanilla implementation.
        masm.testl(len, len);
        masm.jccb(AMD64Assembler.ConditionFlag.Zero, labelDone);
        masm.leaq(src, new AMD64Address(src, len, AMD64Address.Scale.Times1));
        masm.leaq(dst, new AMD64Address(dst, len, AMD64Address.Scale.Times2));
        masm.negq(len);

        Label labelCopyCharsLoop = new Label();
        // Inflate a single byte (char) per iteration.
        masm.bind(labelCopyCharsLoop);
        masm.movzbl(tmp, new AMD64Address(src, len, AMD64Address.Scale.Times1));
        masm.movw(new AMD64Address(dst, len, AMD64Address.Scale.Times2), tmp);
        masm.incrementq(len, 1);
        masm.jcc(AMD64Assembler.ConditionFlag.NotZero, labelCopyCharsLoop);

        masm.bind(labelDone);
    }

}
