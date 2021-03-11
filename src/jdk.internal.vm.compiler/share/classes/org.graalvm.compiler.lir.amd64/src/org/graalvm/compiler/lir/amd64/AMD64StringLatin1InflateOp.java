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
import static jdk.vm.ci.amd64.AMD64.rdi;
import static jdk.vm.ci.amd64.AMD64.rdx;
import static jdk.vm.ci.amd64.AMD64.rsi;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

import java.util.EnumSet;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.amd64.AMD64Address;
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
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.Value;

@Opcode("AMD64_STRING_INFLATE")
public final class AMD64StringLatin1InflateOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64StringLatin1InflateOp> TYPE = LIRInstructionClass.create(AMD64StringLatin1InflateOp.class);

    private final int useAVX3Threshold;

    @Use({REG}) private Value rsrc;
    @Use({REG}) private Value rdst;
    @Use({REG}) private Value rlen;

    @Temp({REG}) private Value rsrcTemp;
    @Temp({REG}) private Value rdstTemp;
    @Temp({REG}) private Value rlenTemp;

    @Temp({REG}) private Value vtmp1;
    @Temp({REG}) private Value rtmp2;

    public AMD64StringLatin1InflateOp(LIRGeneratorTool tool, int useAVX3Threshold, Value src, Value dst, Value len) {
        super(TYPE);

        this.useAVX3Threshold = useAVX3Threshold;

        assert asRegister(src).equals(rsi);
        assert asRegister(dst).equals(rdi);
        assert asRegister(len).equals(rdx);

        rsrcTemp = rsrc = src;
        rdstTemp = rdst = dst;
        rlenTemp = rlen = len;

        vtmp1 = useAVX512ForStringInflateCompress(tool.target()) ? tool.newVariable(LIRKind.value(AMD64Kind.V512_BYTE)) : tool.newVariable(LIRKind.value(AMD64Kind.V128_BYTE));
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

    public static boolean useAVX512ForStringInflateCompress(TargetDescription target) {
        EnumSet<CPUFeature> features = ((AMD64) target.arch).getFeatures();
        return features.contains(AMD64.CPUFeature.AVX512BW) &&
                        features.contains(AMD64.CPUFeature.AVX512VL) &&
                        features.contains(AMD64.CPUFeature.BMI2);
    }

    /**
     * Inflate a Latin1 string using a byte[] array representation into a UTF16 string using a
     * char[] array representation.
     *
     * @param masm the assembler
     * @param src (rsi) the start address of source byte[] to be inflated
     * @param dst (rdi) the start address of destination char[] array
     * @param len (rdx) the length
     * @param tmp1 (xmm) temporary xmm register
     * @param tmp2 (gpr) temporary gpr register
     */
    private void byteArrayInflate(AMD64MacroAssembler masm, Register src, Register dst, Register len, Register tmp1, Register tmp2) {
        assert tmp1.getRegisterCategory().equals(AMD64.XMM);

        Label labelCopyCharsLoop = new Label();
        Label labelDone = new Label();
        Label labelBelowThreshold = new Label();
        Label labelAVX3Threshold = new Label();

        // assert different registers
        assert src.number != dst.number && src.number != len.number && src.number != tmp2.number;
        assert dst.number != len.number && dst.number != tmp2.number;
        assert len.number != tmp2.number;

        masm.movl(tmp2, len);
        if (useAVX512ForStringInflateCompress(masm.target)) {
            Label labelCopy32Loop = new Label();
            Label labelCopyTail = new Label();
            Register tmp3Aliased = len;

            // If the length of the string is less than 16, we chose not to use the
            // AVX512 instructions.
            masm.testlAndJcc(len, -16, ConditionFlag.Zero, labelBelowThreshold, false);
            masm.testlAndJcc(len, -1 * useAVX3Threshold, ConditionFlag.Zero, labelAVX3Threshold, false);

            // Test for suitable number chunks with respect to the size of the vector
            // operation, mask off remaining number of chars (bytes) to inflate after
            // committing to the vector loop.
            // Adjust vector pointers to upper address bounds and inverse loop index.
            // This will keep the loop condition simple.
            //
            // NOTE: The above idiom/pattern is used in all the loops below.

            masm.andl(tmp2, 32 - 1);  // The tail count (in chars).
            // The vector count (in chars).
            masm.andlAndJcc(len, -32, ConditionFlag.Zero, labelCopyTail, true);

            masm.leaq(src, new AMD64Address(src, len, AMD64Address.Scale.Times1));
            masm.leaq(dst, new AMD64Address(dst, len, AMD64Address.Scale.Times2));
            masm.negq(len);

            // Inflate 32 chars per iteration, reading 256-bit compact vectors
            // and writing 512-bit inflated ditto.
            masm.bind(labelCopy32Loop);
            masm.evpmovzxbw(tmp1, new AMD64Address(src, len, AMD64Address.Scale.Times1));
            masm.evmovdqu16(new AMD64Address(dst, len, AMD64Address.Scale.Times2), tmp1);
            masm.addqAndJcc(len, 32, ConditionFlag.NotZero, labelCopy32Loop, false);

            masm.bind(labelCopyTail);
            // All done if the tail count is zero.
            masm.testlAndJcc(tmp2, tmp2, ConditionFlag.Zero, labelDone, false);

            // Compute (1 << N) - 1 = ~(~0 << N), where N is the remaining number
            // of characters to process.
            masm.movl(tmp3Aliased, -1);
            masm.shlxl(tmp3Aliased, tmp3Aliased, tmp2);
            masm.notl(tmp3Aliased);

            masm.kmovd(k2, tmp3Aliased);
            masm.evpmovzxbw(tmp1, k2, new AMD64Address(src));
            masm.evmovdqu16(new AMD64Address(dst), k2, tmp1);

            masm.jmp(labelDone);
            masm.bind(labelAVX3Threshold);
        }

        if (masm.supports(AMD64.CPUFeature.SSE4_2)) {
            Label labelCopy16Loop = new Label();
            Label labelCopy8Loop = new Label();
            Label labelCopyBytes = new Label();
            Label labelCopyNewTail = new Label();
            Label labelCopyTail = new Label();

            if (masm.supports(AMD64.CPUFeature.AVX2)) {
                masm.andl(tmp2, 16 - 1);
                masm.andlAndJcc(len, -16, ConditionFlag.Zero, labelCopyNewTail, true);
            } else {
                masm.andl(tmp2, 0x00000007);
                masm.andlAndJcc(len, 0xfffffff8, ConditionFlag.Zero, labelCopyTail, true);
            }

            // vectored inflation
            masm.leaq(src, new AMD64Address(src, len, AMD64Address.Scale.Times1));
            masm.leaq(dst, new AMD64Address(dst, len, AMD64Address.Scale.Times2));
            masm.negq(len);

            if (masm.supports(AMD64.CPUFeature.AVX2)) {
                masm.bind(labelCopy16Loop);
                masm.vpmovzxbw(tmp1, new AMD64Address(src, len, AMD64Address.Scale.Times1));
                masm.vmovdqu(new AMD64Address(dst, len, AMD64Address.Scale.Times2), tmp1);
                masm.addqAndJcc(len, 16, ConditionFlag.NotZero, labelCopy16Loop, false);

                // The avx512 logic may branch here. We assume that avx2 is supported when we use
                // avx512 instructions.
                masm.bind(labelBelowThreshold);
                masm.bind(labelCopyNewTail);
                masm.movl(len, tmp2);
                masm.andl(tmp2, 0x00000007);
                masm.andlAndJcc(len, 0xfffffff8, ConditionFlag.Zero, labelCopyTail, true);

                // Inflate another 8 bytes before final tail copy.
                masm.pmovzxbw(tmp1, new AMD64Address(src));
                masm.movdqu(new AMD64Address(dst), tmp1);
                masm.addq(src, 8);
                masm.addq(dst, 16);

                masm.jmp(labelCopyTail);
            }

            // Inflate 8 bytes (chars) per iteration, reading 64-bit compact vectors
            // and writing 128-bit inflated ditto.
            masm.bind(labelCopy8Loop);
            masm.pmovzxbw(tmp1, new AMD64Address(src, len, AMD64Address.Scale.Times1));
            masm.movdqu(new AMD64Address(dst, len, AMD64Address.Scale.Times2), tmp1);
            masm.addqAndJcc(len, 8, ConditionFlag.NotZero, labelCopy8Loop, false);

            masm.bind(labelCopyTail);
            masm.movl(len, tmp2);

            masm.cmplAndJcc(len, 4, ConditionFlag.Less, labelCopyBytes, true);

            masm.movdl(tmp1, new AMD64Address(src));
            masm.pmovzxbw(tmp1, tmp1);
            masm.movq(new AMD64Address(dst), tmp1);
            masm.subq(len, 4);
            masm.addq(src, 4);
            masm.addq(dst, 8);

            masm.bind(labelCopyBytes);
        } else {
            // TODO this seems meaningless. And previously this recast does not contain this.
            masm.bind(labelBelowThreshold);
        }

        // Inflate any remaining characters (bytes) using a vanilla implementation.
        masm.testlAndJcc(len, len, ConditionFlag.Zero, labelDone, true);
        masm.leaq(src, new AMD64Address(src, len, AMD64Address.Scale.Times1));
        masm.leaq(dst, new AMD64Address(dst, len, AMD64Address.Scale.Times2));
        masm.negq(len);

        // Inflate a single byte (char) per iteration.
        masm.bind(labelCopyCharsLoop);
        masm.movzbl(tmp2, new AMD64Address(src, len, AMD64Address.Scale.Times1));
        masm.movw(new AMD64Address(dst, len, AMD64Address.Scale.Times2), tmp2);
        masm.incqAndJcc(len, ConditionFlag.NotZero, labelCopyCharsLoop, false);

        masm.bind(labelDone);
    }

    @Override
    public boolean needsClearUpperVectorRegisters() {
        return true;
    }
}
