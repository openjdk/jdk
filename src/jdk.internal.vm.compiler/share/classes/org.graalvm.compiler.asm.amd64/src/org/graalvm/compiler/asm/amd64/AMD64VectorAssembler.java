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


package org.graalvm.compiler.asm.amd64;

import java.util.EnumSet;

import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.debug.GraalError;

import org.graalvm.compiler.asm.amd64.AVXKind.AVXSize;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.Register.RegisterCategory;
import jdk.vm.ci.code.TargetDescription;

/**
 * This class extends the AMD64 assembler with functions that emit instructions from the AVX
 * extension.
 */
public class AMD64VectorAssembler extends AMD64MacroAssembler {

    public AMD64VectorAssembler(TargetDescription target) {
        super(target);
        assert ((AMD64) target.arch).getFeatures().contains(CPUFeature.AVX);
    }

    private static final int L128 = 0;
    private static final int L256 = 1;
    private static final int LIG = 0;

    private static final int W0 = 0;
    private static final int W1 = 1;
    private static final int WIG = 0;

    private static final int P_ = 0x0;
    private static final int P_66 = 0x1;
    private static final int P_F3 = 0x2;
    private static final int P_F2 = 0x3;

    private static final int M_0F = 0x1;
    private static final int M_0F38 = 0x2;
    private static final int M_0F3A = 0x3;

    /**
     * Low-level function to encode and emit the VEX prefix.
     * <p>
     * 2 byte form: [1100 0101] [R vvvv L pp]<br>
     * 3 byte form: [1100 0100] [RXB m-mmmm] [W vvvv L pp]
     * <p>
     * The RXB and vvvv fields are stored in 1's complement in the prefix encoding. This function
     * performs the 1s complement conversion, the caller is expected to pass plain unencoded
     * arguments.
     * <p>
     * The pp field encodes an extension to the opcode:<br>
     * 00: no extension<br>
     * 01: 66<br>
     * 10: F3<br>
     * 11: F2
     * <p>
     * The m-mmmm field encodes the leading bytes of the opcode:<br>
     * 00001: implied 0F leading opcode byte (default in 2-byte encoding)<br>
     * 00010: implied 0F 38 leading opcode bytes<br>
     * 00011: implied 0F 3A leading opcode bytes
     * <p>
     * This function automatically chooses the 2 or 3 byte encoding, based on the XBW flags and the
     * m-mmmm field.
     */
    private void emitVEX(int l, int pp, int mmmmm, int w, int rxb, int vvvv) {
        assert ((AMD64) target.arch).getFeatures().contains(CPUFeature.AVX) : "emitting VEX prefix on a CPU without AVX support";

        assert l == L128 || l == L256 || l == LIG : "invalid value for VEX.L";
        assert pp == P_ || pp == P_66 || pp == P_F3 || pp == P_F2 : "invalid value for VEX.pp";
        assert mmmmm == M_0F || mmmmm == M_0F38 || mmmmm == M_0F3A : "invalid value for VEX.m-mmmm";
        assert w == W0 || w == W1 || w == WIG : "invalid value for VEX.W";

        assert (rxb & 0x07) == rxb : "invalid value for VEX.RXB";
        assert (vvvv & 0x0F) == vvvv : "invalid value for VEX.vvvv";

        int rxb1s = rxb ^ 0x07;
        int vvvv1s = vvvv ^ 0x0F;
        if ((rxb & 0x03) == 0 && w == WIG && mmmmm == M_0F) {
            // 2 byte encoding
            int byte2 = 0;
            byte2 |= (rxb1s & 0x04) << 5;
            byte2 |= vvvv1s << 3;
            byte2 |= l << 2;
            byte2 |= pp;

            emitByte(0xC5);
            emitByte(byte2);
        } else {
            // 3 byte encoding
            int byte2 = 0;
            byte2 = (rxb1s & 0x07) << 5;
            byte2 |= mmmmm;

            int byte3 = 0;
            byte3 |= w << 7;
            byte3 |= vvvv1s << 3;
            byte3 |= l << 2;
            byte3 |= pp;

            emitByte(0xC4);
            emitByte(byte2);
            emitByte(byte3);
        }
    }

    private static int getLFlag(AVXSize size) {
        switch (size) {
            case XMM:
                return L128;
            case YMM:
                return L256;
            default:
                return LIG;
        }
    }

    /**
     * Emit instruction with VEX prefix and two register operands.
     * <p>
     * Format: [VEX] [Opcode] [ModR/M]
     */
    private void emitVexOp(int l, int pp, int mmmmm, int w, int op, Register reg, Register rm) {
        emitVEX(l, pp, mmmmm, w, getRXB(reg, rm), 0);
        emitByte(op);
        emitModRM(reg, rm);
    }

    /**
     * Emit instruction with VEX prefix and three register operands.
     * <p>
     * Format: [VEX] [Opcode] [ModR/M]
     */
    private void emitVexOp(int l, int pp, int mmmmm, int w, int op, Register reg, Register vvvv, Register rm) {
        emitVEX(l, pp, mmmmm, w, getRXB(reg, rm), vvvv.encoding());
        emitByte(op);
        emitModRM(reg, rm);
    }

    /**
     * Emit instruction with VEX prefix and four register operands.
     * <p>
     * Format: [VEX] [Opcode] [ModR/M] [Imm8[7:4]]
     */
    private void emitVexOp(int l, int pp, int mmmmm, int w, int op, Register reg, Register vvvv, Register rm, Register imm8) {
        emitVEX(l, pp, mmmmm, w, getRXB(reg, rm), vvvv.encoding());
        emitByte(op);
        emitModRM(reg, rm);
        emitByte(imm8.encoding() << 4);
    }

    /**
     * Emit instruction with VEX prefix and three register operands and one memory operand.
     * <p>
     * Format: [VEX] [Opcode] [ModR/M] [Imm8[7:4]]
     */
    private void emitVexOp(int l, int pp, int mmmmm, int w, int op, Register reg, Register vvvv, AMD64Address rm, Register imm8, int additionalInstructionSize) {
        emitVEX(l, pp, mmmmm, w, getRXB(reg, rm), vvvv.encoding());
        emitByte(op);
        emitOperandHelper(reg, rm, additionalInstructionSize);
        emitByte(imm8.encoding() << 4);
    }

    /**
     * Emit instruction with VEX prefix and two register operands and an opcode extension in the r
     * field.
     * <p>
     * Format: [VEX] [Opcode] [ModR/M]
     */
    private void emitVexOp(int l, int pp, int mmmmm, int w, int op, int r, Register vvvv, Register rm) {
        emitVEX(l, pp, mmmmm, w, getRXB(null, rm), vvvv.encoding());
        emitByte(op);
        emitModRM(r, rm);
    }

    /**
     * Emit instruction with VEX prefix, one register operand and one memory operand.
     * <p>
     * Format: [VEX] [Opcode] [ModR/M] [SIB] [Disp]
     */
    private void emitVexOp(int l, int pp, int mmmmm, int w, int op, Register reg, AMD64Address rm, int additionalInstructionSize) {
        emitVEX(l, pp, mmmmm, w, getRXB(reg, rm), 0);
        emitByte(op);
        emitOperandHelper(reg, rm, additionalInstructionSize);
    }

    /**
     * Emit instruction with VEX prefix, two register operands and one memory operand.
     * <p>
     * Format: [VEX] [Opcode] [ModR/M] [SIB] [Disp]
     */
    private void emitVexOp(int l, int pp, int mmmmm, int w, int op, Register reg, Register vvvv, AMD64Address rm, int additionalInstructionSize) {
        emitVEX(l, pp, mmmmm, w, getRXB(reg, rm), vvvv.encoding());
        emitByte(op);
        emitOperandHelper(reg, rm, additionalInstructionSize);
    }

    private static final OpAssertion AVX1 = new OpAssertion(CPUFeature.AVX, CPUFeature.AVX);
    private static final OpAssertion AVX1_2 = new OpAssertion(CPUFeature.AVX, CPUFeature.AVX2);
    private static final OpAssertion AVX2 = new OpAssertion(CPUFeature.AVX2, CPUFeature.AVX2);

    private static final OpAssertion AVX1_128ONLY = new OpAssertion(CPUFeature.AVX, null);
    private static final OpAssertion AVX1_256ONLY = new OpAssertion(null, CPUFeature.AVX);
    private static final OpAssertion AVX2_256ONLY = new OpAssertion(null, CPUFeature.AVX2);

    private static final OpAssertion XMM_CPU = new OpAssertion(CPUFeature.AVX, null, AMD64.XMM, null, AMD64.CPU, null);
    private static final OpAssertion XMM_XMM_CPU = new OpAssertion(CPUFeature.AVX, null, AMD64.XMM, AMD64.XMM, AMD64.CPU, null);
    private static final OpAssertion CPU_XMM = new OpAssertion(CPUFeature.AVX, null, AMD64.CPU, null, AMD64.XMM, null);

    private static final class OpAssertion {
        private final CPUFeature avx128feature;
        private final CPUFeature avx256feature;

        private final RegisterCategory rCategory;
        private final RegisterCategory vCategory;
        private final RegisterCategory mCategory;
        private final RegisterCategory imm8Category;

        private OpAssertion(CPUFeature avx128feature, CPUFeature avx256feature) {
            this(avx128feature, avx256feature, AMD64.XMM, AMD64.XMM, AMD64.XMM, AMD64.XMM);
        }

        private OpAssertion(CPUFeature avx128feature, CPUFeature avx256feature, RegisterCategory rCategory, RegisterCategory vCategory, RegisterCategory mCategory, RegisterCategory imm8Category) {
            this.avx128feature = avx128feature;
            this.avx256feature = avx256feature;
            this.rCategory = rCategory;
            this.vCategory = vCategory;
            this.mCategory = mCategory;
            this.imm8Category = imm8Category;
        }

        public boolean check(AMD64 arch, AVXSize size, Register r, Register v, Register m) {
            return check(arch, size, r, v, m, null);
        }

        public boolean check(AMD64 arch, AVXSize size, Register r, Register v, Register m, Register imm8) {
            switch (size) {
                case XMM:
                    assert avx128feature != null && arch.getFeatures().contains(avx128feature) : "emitting illegal 128 bit instruction";
                    break;
                case YMM:
                    assert avx256feature != null && arch.getFeatures().contains(avx256feature) : "emitting illegal 256 bit instruction";
                    break;
            }
            if (r != null) {
                assert r.getRegisterCategory().equals(rCategory);
            }
            if (v != null) {
                assert v.getRegisterCategory().equals(vCategory);
            }
            if (m != null) {
                assert m.getRegisterCategory().equals(mCategory);
            }
            if (imm8 != null) {
                assert imm8.getRegisterCategory().equals(imm8Category);
            }
            return true;
        }

        public boolean supports(EnumSet<CPUFeature> features, AVXSize avxSize) {
            switch (avxSize) {
                case XMM:
                    return features.contains(avx128feature);
                case YMM:
                    return features.contains(avx256feature);
                default:
                    throw GraalError.shouldNotReachHere();
            }
        }
    }

    /**
     * Base class for VEX-encoded instructions.
     */
    private static class VexOp {
        protected final int pp;
        protected final int mmmmm;
        protected final int w;
        protected final int op;

        private final String opcode;
        protected final OpAssertion assertion;

        protected VexOp(String opcode, int pp, int mmmmm, int w, int op, OpAssertion assertion) {
            this.pp = pp;
            this.mmmmm = mmmmm;
            this.w = w;
            this.op = op;
            this.opcode = opcode;
            this.assertion = assertion;
        }

        public boolean isSupported(AMD64VectorAssembler vasm, AMD64Kind kind) {
            return assertion.supports(((AMD64) vasm.target.arch).getFeatures(), AVXKind.getRegisterSize(kind));
        }

        @Override
        public String toString() {
            return opcode;
        }
    }

    /**
     * VEX-encoded instructions with an operand order of RM, but the M operand must be a register.
     */
    public static class VexRROp extends VexOp {
        // @formatter:off
        public static final VexRROp VMASKMOVDQU = new VexRROp("VMASKMOVDQU", P_66, M_0F, WIG, 0xF7, AVX1_128ONLY);
        // @formatter:on

        protected VexRROp(String opcode, int pp, int mmmmm, int w, int op) {
            this(opcode, pp, mmmmm, w, op, AVX1);
        }

        protected VexRROp(String opcode, int pp, int mmmmm, int w, int op, OpAssertion assertion) {
            super(opcode, pp, mmmmm, w, op, assertion);
        }

        public void emit(AMD64VectorAssembler asm, AVXSize size, Register dst, Register src) {
            assert assertion.check((AMD64) asm.target.arch, size, dst, null, src);
            asm.emitVexOp(getLFlag(size), pp, mmmmm, w, op, dst, src);
        }
    }

    /**
     * VEX-encoded instructions with an operand order of RM.
     */
    public static class VexRMOp extends VexRROp {
        // @formatter:off
        public static final VexRMOp VCVTTSS2SI     = new VexRMOp("VCVTTSS2SI",     P_F3, M_0F,   W0,  0x2C, CPU_XMM);
        public static final VexRMOp VCVTTSS2SQ     = new VexRMOp("VCVTTSS2SQ",     P_F3, M_0F,   W1,  0x2C, CPU_XMM);
        public static final VexRMOp VCVTTSD2SI     = new VexRMOp("VCVTTSD2SI",     P_F2, M_0F,   W0,  0x2C, CPU_XMM);
        public static final VexRMOp VCVTTSD2SQ     = new VexRMOp("VCVTTSD2SQ",     P_F2, M_0F,   W1,  0x2C, CPU_XMM);
        public static final VexRMOp VCVTPS2PD      = new VexRMOp("VCVTPS2PD",      P_,   M_0F,   WIG, 0x5A);
        public static final VexRMOp VCVTPD2PS      = new VexRMOp("VCVTPD2PS",      P_66, M_0F,   WIG, 0x5A);
        public static final VexRMOp VCVTDQ2PS      = new VexRMOp("VCVTDQ2PS",      P_,   M_0F,   WIG, 0x5B);
        public static final VexRMOp VCVTTPS2DQ     = new VexRMOp("VCVTTPS2DQ",     P_F3, M_0F,   WIG, 0x5B);
        public static final VexRMOp VCVTTPD2DQ     = new VexRMOp("VCVTTPD2DQ",     P_66, M_0F,   WIG, 0xE6);
        public static final VexRMOp VCVTDQ2PD      = new VexRMOp("VCVTDQ2PD",      P_F3, M_0F,   WIG, 0xE6);
        public static final VexRMOp VBROADCASTSS   = new VexRMOp("VBROADCASTSS",   P_66, M_0F38, W0,  0x18);
        public static final VexRMOp VBROADCASTSD   = new VexRMOp("VBROADCASTSD",   P_66, M_0F38, W0,  0x19, AVX1_256ONLY);
        public static final VexRMOp VBROADCASTF128 = new VexRMOp("VBROADCASTF128", P_66, M_0F38, W0,  0x1A, AVX1_256ONLY);
        public static final VexRMOp VBROADCASTI128 = new VexRMOp("VBROADCASTI128", P_66, M_0F38, W0,  0x5A, AVX2_256ONLY);
        public static final VexRMOp VPBROADCASTB   = new VexRMOp("VPBROADCASTB",   P_66, M_0F38, W0,  0x78, AVX2);
        public static final VexRMOp VPBROADCASTW   = new VexRMOp("VPBROADCASTW",   P_66, M_0F38, W0,  0x79, AVX2);
        public static final VexRMOp VPBROADCASTD   = new VexRMOp("VPBROADCASTD",   P_66, M_0F38, W0,  0x58, AVX2);
        public static final VexRMOp VPBROADCASTQ   = new VexRMOp("VPBROADCASTQ",   P_66, M_0F38, W0,  0x59, AVX2);
        public static final VexRMOp VPMOVSXBW      = new VexRMOp("VPMOVSXBW",      P_66, M_0F38, WIG, 0x20);
        public static final VexRMOp VPMOVSXBD      = new VexRMOp("VPMOVSXBD",      P_66, M_0F38, WIG, 0x21);
        public static final VexRMOp VPMOVSXBQ      = new VexRMOp("VPMOVSXBQ",      P_66, M_0F38, WIG, 0x22);
        public static final VexRMOp VPMOVSXWD      = new VexRMOp("VPMOVSXWD",      P_66, M_0F38, WIG, 0x23);
        public static final VexRMOp VPMOVSXWQ      = new VexRMOp("VPMOVSXWQ",      P_66, M_0F38, WIG, 0x24);
        public static final VexRMOp VPMOVSXDQ      = new VexRMOp("VPMOVSXDQ",      P_66, M_0F38, WIG, 0x25);
        public static final VexRMOp VPMOVZXBW      = new VexRMOp("VPMOVZXBW",      P_66, M_0F38, WIG, 0x30);
        public static final VexRMOp VPMOVZXBD      = new VexRMOp("VPMOVZXBD",      P_66, M_0F38, WIG, 0x31);
        public static final VexRMOp VPMOVZXBQ      = new VexRMOp("VPMOVZXBQ",      P_66, M_0F38, WIG, 0x32);
        public static final VexRMOp VPMOVZXWD      = new VexRMOp("VPMOVZXWD",      P_66, M_0F38, WIG, 0x33);
        public static final VexRMOp VPMOVZXWQ      = new VexRMOp("VPMOVZXWQ",      P_66, M_0F38, WIG, 0x34);
        public static final VexRMOp VPMOVZXDQ      = new VexRMOp("VPMOVZXDQ",      P_66, M_0F38, WIG, 0x35);
        public static final VexRMOp VSQRTPD        = new VexRMOp("VSQRTPD",        P_66, M_0F,   WIG, 0x51);
        public static final VexRMOp VSQRTPS        = new VexRMOp("VSQRTPS",        P_,   M_0F,   WIG, 0x51);
        public static final VexRMOp VSQRTSD        = new VexRMOp("VSQRTSD",        P_F2, M_0F,   WIG, 0x51);
        public static final VexRMOp VSQRTSS        = new VexRMOp("VSQRTSS",        P_F3, M_0F,   WIG, 0x51);
        public static final VexRMOp VUCOMISS       = new VexRMOp("VUCOMISS",       P_,   M_0F,   WIG, 0x2E);
        public static final VexRMOp VUCOMISD       = new VexRMOp("VUCOMISD",       P_66, M_0F,   WIG, 0x2E);
        // @formatter:on

        protected VexRMOp(String opcode, int pp, int mmmmm, int w, int op) {
            this(opcode, pp, mmmmm, w, op, AVX1);
        }

        protected VexRMOp(String opcode, int pp, int mmmmm, int w, int op, OpAssertion assertion) {
            super(opcode, pp, mmmmm, w, op, assertion);
        }

        public void emit(AMD64VectorAssembler asm, AVXSize size, Register dst, AMD64Address src) {
            assert assertion.check((AMD64) asm.target.arch, size, dst, null, null);
            asm.emitVexOp(getLFlag(size), pp, mmmmm, w, op, dst, src, 0);
        }
    }

    /**
     * VEX-encoded move instructions.
     * <p>
     * These instructions have two opcodes: op is the forward move instruction with an operand order
     * of RM, and opReverse is the reverse move instruction with an operand order of MR.
     */
    public static final class VexMoveOp extends VexRMOp {
        // @formatter:off
        public static final VexMoveOp VMOVDQA = new VexMoveOp("VMOVDQA", P_66, M_0F, WIG, 0x6F, 0x7F);
        public static final VexMoveOp VMOVDQU = new VexMoveOp("VMOVDQU", P_F3, M_0F, WIG, 0x6F, 0x7F);
        public static final VexMoveOp VMOVAPS = new VexMoveOp("VMOVAPS", P_,   M_0F, WIG, 0x28, 0x29);
        public static final VexMoveOp VMOVAPD = new VexMoveOp("VMOVAPD", P_66, M_0F, WIG, 0x28, 0x29);
        public static final VexMoveOp VMOVUPS = new VexMoveOp("VMOVUPS", P_,   M_0F, WIG, 0x10, 0x11);
        public static final VexMoveOp VMOVUPD = new VexMoveOp("VMOVUPD", P_66, M_0F, WIG, 0x10, 0x11);
        public static final VexMoveOp VMOVSS  = new VexMoveOp("VMOVSS",  P_F3, M_0F, WIG, 0x10, 0x11);
        public static final VexMoveOp VMOVSD  = new VexMoveOp("VMOVSD",  P_F2, M_0F, WIG, 0x10, 0x11);
        public static final VexMoveOp VMOVD   = new VexMoveOp("VMOVD",   P_66, M_0F, W0,  0x6E, 0x7E, XMM_CPU);
        public static final VexMoveOp VMOVQ   = new VexMoveOp("VMOVQ",   P_66, M_0F, W1,  0x6E, 0x7E, XMM_CPU);
        // @formatter:on

        private final int opReverse;

        private VexMoveOp(String opcode, int pp, int mmmmm, int w, int op, int opReverse) {
            this(opcode, pp, mmmmm, w, op, opReverse, AVX1);
        }

        private VexMoveOp(String opcode, int pp, int mmmmm, int w, int op, int opReverse, OpAssertion assertion) {
            super(opcode, pp, mmmmm, w, op, assertion);
            this.opReverse = opReverse;
        }

        public void emit(AMD64VectorAssembler asm, AVXSize size, AMD64Address dst, Register src) {
            assert assertion.check((AMD64) asm.target.arch, size, src, null, null);
            asm.emitVexOp(getLFlag(size), pp, mmmmm, w, opReverse, src, dst, 0);
        }

        public void emitReverse(AMD64VectorAssembler asm, AVXSize size, Register dst, Register src) {
            assert assertion.check((AMD64) asm.target.arch, size, src, null, dst);
            asm.emitVexOp(getLFlag(size), pp, mmmmm, w, opReverse, src, dst);
        }
    }

    public interface VexRRIOp {
        void emit(AMD64VectorAssembler asm, AVXSize size, Register dst, Register src, int imm8);
    }

    /**
     * VEX-encoded instructions with an operand order of RMI.
     */
    public static final class VexRMIOp extends VexOp implements VexRRIOp {
        // @formatter:off
        public static final VexRMIOp VPERMQ   = new VexRMIOp("VPERMQ",   P_66, M_0F3A, W1,  0x00, AVX2_256ONLY);
        public static final VexRMIOp VPSHUFLW = new VexRMIOp("VPSHUFLW", P_F2, M_0F,   WIG, 0x70, AVX1_2);
        public static final VexRMIOp VPSHUFHW = new VexRMIOp("VPSHUFHW", P_F3, M_0F,   WIG, 0x70, AVX1_2);
        public static final VexRMIOp VPSHUFD  = new VexRMIOp("VPSHUFD",  P_66, M_0F,   WIG, 0x70, AVX1_2);
        // @formatter:on

        private VexRMIOp(String opcode, int pp, int mmmmm, int w, int op, OpAssertion assertion) {
            super(opcode, pp, mmmmm, w, op, assertion);
        }

        @Override
        public void emit(AMD64VectorAssembler asm, AVXSize size, Register dst, Register src, int imm8) {
            assert assertion.check((AMD64) asm.target.arch, size, dst, null, src);
            asm.emitVexOp(getLFlag(size), pp, mmmmm, w, op, dst, src);
            asm.emitByte(imm8);
        }

        public void emit(AMD64VectorAssembler asm, AVXSize size, Register dst, AMD64Address src, int imm8) {
            assert assertion.check((AMD64) asm.target.arch, size, dst, null, null);
            asm.emitVexOp(getLFlag(size), pp, mmmmm, w, op, dst, src, 1);
            asm.emitByte(imm8);
        }
    }

    /**
     * VEX-encoded instructions with an operand order of MRI.
     */
    public static final class VexMRIOp extends VexOp implements VexRRIOp {
        // @formatter:off
        public static final VexMRIOp VEXTRACTF128 = new VexMRIOp("VEXTRACTF128", P_66, M_0F3A, W0, 0x19, AVX1_256ONLY);
        public static final VexMRIOp VEXTRACTI128 = new VexMRIOp("VEXTRACTI128", P_66, M_0F3A, W0, 0x39, AVX2_256ONLY);
        public static final VexMRIOp VPEXTRB      = new VexMRIOp("VPEXTRB",      P_66, M_0F3A, W0, 0x14, XMM_CPU);
        public static final VexMRIOp VPEXTRW      = new VexMRIOp("VPEXTRW",      P_66, M_0F3A, W0, 0x15, XMM_CPU);
        public static final VexMRIOp VPEXTRD      = new VexMRIOp("VPEXTRD",      P_66, M_0F3A, W0, 0x16, XMM_CPU);
        public static final VexMRIOp VPEXTRQ      = new VexMRIOp("VPEXTRQ",      P_66, M_0F3A, W1, 0x16, XMM_CPU);
        // @formatter:on

        private VexMRIOp(String opcode, int pp, int mmmmm, int w, int op, OpAssertion assertion) {
            super(opcode, pp, mmmmm, w, op, assertion);
        }

        @Override
        public void emit(AMD64VectorAssembler asm, AVXSize size, Register dst, Register src, int imm8) {
            assert assertion.check((AMD64) asm.target.arch, size, src, null, dst);
            asm.emitVexOp(getLFlag(size), pp, mmmmm, w, op, src, dst);
            asm.emitByte(imm8);
        }

        public void emit(AMD64VectorAssembler asm, AVXSize size, AMD64Address dst, Register src, int imm8) {
            assert assertion.check((AMD64) asm.target.arch, size, src, null, null);
            asm.emitVexOp(getLFlag(size), pp, mmmmm, w, op, src, dst, 1);
            asm.emitByte(imm8);
        }
    }

    /**
     * VEX-encoded instructions with an operand order of RVMR.
     */
    public static class VexRVMROp extends VexOp {
        // @formatter:off
        public static final VexRVMROp VPBLENDVB  = new VexRVMROp("VPBLENDVB",  P_66, M_0F3A, W0, 0x4C, AVX1_2);
        public static final VexRVMROp VPBLENDVPS = new VexRVMROp("VPBLENDVPS", P_66, M_0F3A, W0, 0x4A, AVX1);
        public static final VexRVMROp VPBLENDVPD = new VexRVMROp("VPBLENDVPD", P_66, M_0F3A, W0, 0x4B, AVX1);
        // @formatter:on

        protected VexRVMROp(String opcode, int pp, int mmmmm, int w, int op, OpAssertion assertion) {
            super(opcode, pp, mmmmm, w, op, assertion);
        }

        public void emit(AMD64VectorAssembler asm, AVXSize size, Register dst, Register mask, Register src1, Register src2) {
            assert assertion.check((AMD64) asm.target.arch, size, dst, mask, src1, src2);
            asm.emitVexOp(getLFlag(size), pp, mmmmm, w, op, dst, src1, src2, mask);
        }

        public void emit(AMD64VectorAssembler asm, AVXSize size, Register dst, Register mask, Register src1, AMD64Address src2) {
            assert assertion.check((AMD64) asm.target.arch, size, dst, mask, src1, null);
            asm.emitVexOp(getLFlag(size), pp, mmmmm, w, op, dst, src1, src2, mask, 0);
        }
    }

    /**
     * VEX-encoded instructions with an operand order of RVM.
     */
    public static class VexRVMOp extends VexOp {
        // @formatter:off
        public static final VexRVMOp VANDPS    = new VexRVMOp("VANDPS",    P_,   M_0F,   WIG, 0x54);
        public static final VexRVMOp VANDPD    = new VexRVMOp("VANDPD",    P_66, M_0F,   WIG, 0x54);
        public static final VexRVMOp VORPS     = new VexRVMOp("VORPS",     P_,   M_0F,   WIG, 0x56);
        public static final VexRVMOp VORPD     = new VexRVMOp("VORPD",     P_66, M_0F,   WIG, 0x56);
        public static final VexRVMOp VADDPS    = new VexRVMOp("VADDPS",    P_,   M_0F,   WIG, 0x58);
        public static final VexRVMOp VADDPD    = new VexRVMOp("VADDPD",    P_66, M_0F,   WIG, 0x58);
        public static final VexRVMOp VADDSS    = new VexRVMOp("VADDSS",    P_F3, M_0F,   WIG, 0x58);
        public static final VexRVMOp VADDSD    = new VexRVMOp("VADDSD",    P_F2, M_0F,   WIG, 0x58);
        public static final VexRVMOp VXORPS    = new VexRVMOp("VXORPS",    P_,   M_0F,   WIG, 0x57);
        public static final VexRVMOp VXORPD    = new VexRVMOp("VXORPD",    P_66, M_0F,   WIG, 0x57);
        public static final VexRVMOp VMULPS    = new VexRVMOp("VMULPS",    P_,   M_0F,   WIG, 0x59);
        public static final VexRVMOp VMULPD    = new VexRVMOp("VMULPD",    P_66, M_0F,   WIG, 0x59);
        public static final VexRVMOp VMULSS    = new VexRVMOp("VMULSS",    P_F3, M_0F,   WIG, 0x59);
        public static final VexRVMOp VMULSD    = new VexRVMOp("VMULSD",    P_F2, M_0F,   WIG, 0x59);
        public static final VexRVMOp VSUBPS    = new VexRVMOp("VSUBPS",    P_,   M_0F,   WIG, 0x5C);
        public static final VexRVMOp VSUBPD    = new VexRVMOp("VSUBPD",    P_66, M_0F,   WIG, 0x5C);
        public static final VexRVMOp VSUBSS    = new VexRVMOp("VSUBSS",    P_F3, M_0F,   WIG, 0x5C);
        public static final VexRVMOp VSUBSD    = new VexRVMOp("VSUBSD",    P_F2, M_0F,   WIG, 0x5C);
        public static final VexRVMOp VDIVPS    = new VexRVMOp("VDIVPS",    P_,   M_0F,   WIG, 0x5E);
        public static final VexRVMOp VDIVPD    = new VexRVMOp("VDIVPD",    P_66, M_0F,   WIG, 0x5E);
        public static final VexRVMOp VDIVSS    = new VexRVMOp("VDIVPS",    P_F3, M_0F,   WIG, 0x5E);
        public static final VexRVMOp VDIVSD    = new VexRVMOp("VDIVPD",    P_F2, M_0F,   WIG, 0x5E);
        public static final VexRVMOp VADDSUBPS = new VexRVMOp("VADDSUBPS", P_F2, M_0F,   WIG, 0xD0);
        public static final VexRVMOp VADDSUBPD = new VexRVMOp("VADDSUBPD", P_66, M_0F,   WIG, 0xD0);
        public static final VexRVMOp VPAND     = new VexRVMOp("VPAND",     P_66, M_0F,   WIG, 0xDB, AVX1_2);
        public static final VexRVMOp VPOR      = new VexRVMOp("VPOR",      P_66, M_0F,   WIG, 0xEB, AVX1_2);
        public static final VexRVMOp VPXOR     = new VexRVMOp("VPXOR",     P_66, M_0F,   WIG, 0xEF, AVX1_2);
        public static final VexRVMOp VPADDB    = new VexRVMOp("VPADDB",    P_66, M_0F,   WIG, 0xFC, AVX1_2);
        public static final VexRVMOp VPADDW    = new VexRVMOp("VPADDW",    P_66, M_0F,   WIG, 0xFD, AVX1_2);
        public static final VexRVMOp VPADDD    = new VexRVMOp("VPADDD",    P_66, M_0F,   WIG, 0xFE, AVX1_2);
        public static final VexRVMOp VPADDQ    = new VexRVMOp("VPADDQ",    P_66, M_0F,   WIG, 0xD4, AVX1_2);
        public static final VexRVMOp VPMULHUW  = new VexRVMOp("VPMULHUW",  P_66, M_0F,   WIG, 0xE4, AVX1_2);
        public static final VexRVMOp VPMULHW   = new VexRVMOp("VPMULHW",   P_66, M_0F,   WIG, 0xE5, AVX1_2);
        public static final VexRVMOp VPMULLW   = new VexRVMOp("VPMULLW",   P_66, M_0F,   WIG, 0xD5, AVX1_2);
        public static final VexRVMOp VPMULLD   = new VexRVMOp("VPMULLD",   P_66, M_0F38, WIG, 0x40, AVX1_2);
        public static final VexRVMOp VPSUBB    = new VexRVMOp("VPSUBB",    P_66, M_0F,   WIG, 0xF8, AVX1_2);
        public static final VexRVMOp VPSUBW    = new VexRVMOp("VPSUBW",    P_66, M_0F,   WIG, 0xF9, AVX1_2);
        public static final VexRVMOp VPSUBD    = new VexRVMOp("VPSUBD",    P_66, M_0F,   WIG, 0xFA, AVX1_2);
        public static final VexRVMOp VPSUBQ    = new VexRVMOp("VPSUBQ",    P_66, M_0F,   WIG, 0xFB, AVX1_2);
        public static final VexRVMOp VPSHUFB   = new VexRVMOp("VPSHUFB",   P_66, M_0F38, WIG, 0x00, AVX1_2);
        public static final VexRVMOp VCVTSD2SS = new VexRVMOp("VCVTSD2SS", P_F2, M_0F,   WIG, 0x5A);
        public static final VexRVMOp VCVTSS2SD = new VexRVMOp("VCVTSS2SD", P_F3, M_0F,   WIG, 0x5A);
        public static final VexRVMOp VCVTSI2SD = new VexRVMOp("VCVTSI2SD", P_F2, M_0F,   W0,  0x2A, XMM_XMM_CPU);
        public static final VexRVMOp VCVTSQ2SD = new VexRVMOp("VCVTSQ2SD", P_F2, M_0F,   W1,  0x2A, XMM_XMM_CPU);
        public static final VexRVMOp VCVTSI2SS = new VexRVMOp("VCVTSI2SS", P_F3, M_0F,   W0,  0x2A, XMM_XMM_CPU);
        public static final VexRVMOp VCVTSQ2SS = new VexRVMOp("VCVTSQ2SS", P_F3, M_0F,   W1,  0x2A, XMM_XMM_CPU);
        public static final VexRVMOp VPCMPEQB  = new VexRVMOp("VPCMPEQB",  P_66, M_0F,   WIG, 0x74, AVX1_2);
        public static final VexRVMOp VPCMPEQW  = new VexRVMOp("VPCMPEQW",  P_66, M_0F,   WIG, 0x75, AVX1_2);
        public static final VexRVMOp VPCMPEQD  = new VexRVMOp("VPCMPEQD",  P_66, M_0F,   WIG, 0x76, AVX1_2);
        public static final VexRVMOp VPCMPEQQ  = new VexRVMOp("VPCMPEQQ",  P_66, M_0F38, WIG, 0x76, AVX1_2);
        public static final VexRVMOp VPCMPGTB  = new VexRVMOp("VPCMPGTB",  P_66, M_0F,   WIG, 0x64, AVX1_2);
        public static final VexRVMOp VPCMPGTW  = new VexRVMOp("VPCMPGTW",  P_66, M_0F,   WIG, 0x65, AVX1_2);
        public static final VexRVMOp VPCMPGTD  = new VexRVMOp("VPCMPGTD",  P_66, M_0F,   WIG, 0x66, AVX1_2);
        public static final VexRVMOp VPCMPGTQ  = new VexRVMOp("VPCMPGTQ",  P_66, M_0F38, WIG, 0x37, AVX1_2);
        // @formatter:on

        private VexRVMOp(String opcode, int pp, int mmmmm, int w, int op) {
            this(opcode, pp, mmmmm, w, op, AVX1);
        }

        protected VexRVMOp(String opcode, int pp, int mmmmm, int w, int op, OpAssertion assertion) {
            super(opcode, pp, mmmmm, w, op, assertion);
        }

        public void emit(AMD64VectorAssembler asm, AVXSize size, Register dst, Register src1, Register src2) {
            assert assertion.check((AMD64) asm.target.arch, size, dst, src1, src2);
            asm.emitVexOp(getLFlag(size), pp, mmmmm, w, op, dst, src1, src2);
        }

        public void emit(AMD64VectorAssembler asm, AVXSize size, Register dst, Register src1, AMD64Address src2) {
            assert assertion.check((AMD64) asm.target.arch, size, dst, src1, null);
            asm.emitVexOp(getLFlag(size), pp, mmmmm, w, op, dst, src1, src2, 0);
        }
    }

    /**
     * VEX-encoded shift instructions with an operand order of either RVM or VMI.
     */
    public static final class VexShiftOp extends VexRVMOp implements VexRRIOp {
        // @formatter:off
        public static final VexShiftOp VPSRLW = new VexShiftOp("VPSRLW", P_66, M_0F, WIG, 0xD1, 0x71, 2);
        public static final VexShiftOp VPSRLD = new VexShiftOp("VPSRLD", P_66, M_0F, WIG, 0xD2, 0x72, 2);
        public static final VexShiftOp VPSRLQ = new VexShiftOp("VPSRLQ", P_66, M_0F, WIG, 0xD3, 0x73, 2);
        public static final VexShiftOp VPSRAW = new VexShiftOp("VPSRAW", P_66, M_0F, WIG, 0xE1, 0x71, 4);
        public static final VexShiftOp VPSRAD = new VexShiftOp("VPSRAD", P_66, M_0F, WIG, 0xE2, 0x72, 4);
        public static final VexShiftOp VPSLLW = new VexShiftOp("VPSLLW", P_66, M_0F, WIG, 0xF1, 0x71, 6);
        public static final VexShiftOp VPSLLD = new VexShiftOp("VPSLLD", P_66, M_0F, WIG, 0xF2, 0x72, 6);
        public static final VexShiftOp VPSLLQ = new VexShiftOp("VPSLLQ", P_66, M_0F, WIG, 0xF3, 0x73, 6);
        // @formatter:on

        private final int immOp;
        private final int r;

        private VexShiftOp(String opcode, int pp, int mmmmm, int w, int op, int immOp, int r) {
            super(opcode, pp, mmmmm, w, op, AVX1_2);
            this.immOp = immOp;
            this.r = r;
        }

        @Override
        public void emit(AMD64VectorAssembler asm, AVXSize size, Register dst, Register src, int imm8) {
            assert assertion.check((AMD64) asm.target.arch, size, null, dst, src);
            asm.emitVexOp(getLFlag(size), pp, mmmmm, w, immOp, r, dst, src);
            asm.emitByte(imm8);
        }
    }

    public static final class VexMaskMoveOp extends VexOp {
        // @formatter:off
        public static final VexMaskMoveOp VMASKMOVPS = new VexMaskMoveOp("VMASKMOVPS", P_66, M_0F38, W0, 0x2C, 0x2E);
        public static final VexMaskMoveOp VMASKMOVPD = new VexMaskMoveOp("VMASKMOVPD", P_66, M_0F38, W0, 0x2D, 0x2F);
        public static final VexMaskMoveOp VPMASKMOVD = new VexMaskMoveOp("VPMASKMOVD", P_66, M_0F38, W0, 0x8C, 0x8E, AVX2);
        public static final VexMaskMoveOp VPMASKMOVQ = new VexMaskMoveOp("VPMASKMOVQ", P_66, M_0F38, W1, 0x8C, 0x8E, AVX2);
        // @formatter:on

        private final int opReverse;

        private VexMaskMoveOp(String opcode, int pp, int mmmmm, int w, int op, int opReverse) {
            this(opcode, pp, mmmmm, w, op, opReverse, AVX1);
        }

        private VexMaskMoveOp(String opcode, int pp, int mmmmm, int w, int op, int opReverse, OpAssertion assertion) {
            super(opcode, pp, mmmmm, w, op, assertion);
            this.opReverse = opReverse;
        }

        public void emit(AMD64VectorAssembler asm, AVXSize size, Register dst, Register mask, AMD64Address src) {
            assert assertion.check((AMD64) asm.target.arch, size, dst, mask, null);
            asm.emitVexOp(getLFlag(size), pp, mmmmm, w, op, dst, mask, src, 0);
        }

        public void emit(AMD64VectorAssembler asm, AVXSize size, AMD64Address dst, Register mask, Register src) {
            assert assertion.check((AMD64) asm.target.arch, size, src, mask, null);
            asm.emitVexOp(getLFlag(size), pp, mmmmm, w, opReverse, src, mask, dst, 0);
        }
    }

    /**
     * VEX-encoded instructions with an operand order of RVMI.
     */
    public static final class VexRVMIOp extends VexOp {
        // @formatter:off
        public static final VexRVMIOp VSHUFPS     = new VexRVMIOp("VSHUFPS",     P_,   M_0F,   WIG, 0xC6);
        public static final VexRVMIOp VSHUFPD     = new VexRVMIOp("VSHUFPD",     P_66, M_0F,   WIG, 0xC6);
        public static final VexRVMIOp VINSERTF128 = new VexRVMIOp("VINSERTF128", P_66, M_0F3A, W0,  0x18, AVX1_256ONLY);
        public static final VexRVMIOp VINSERTI128 = new VexRVMIOp("VINSERTI128", P_66, M_0F3A, W0,  0x38, AVX2_256ONLY);
        // @formatter:on

        private VexRVMIOp(String opcode, int pp, int mmmmm, int w, int op) {
            this(opcode, pp, mmmmm, w, op, AVX1);
        }

        private VexRVMIOp(String opcode, int pp, int mmmmm, int w, int op, OpAssertion assertion) {
            super(opcode, pp, mmmmm, w, op, assertion);
        }

        public void emit(AMD64VectorAssembler asm, AVXSize size, Register dst, Register src1, Register src2, int imm8) {
            assert assertion.check((AMD64) asm.target.arch, size, dst, src1, src2);
            assert (imm8 & 0xFF) == imm8;
            asm.emitVexOp(getLFlag(size), pp, mmmmm, w, op, dst, src1, src2);
            asm.emitByte(imm8);
        }

        public void emit(AMD64VectorAssembler asm, AVXSize size, Register dst, Register src1, AMD64Address src2, int imm8) {
            assert assertion.check((AMD64) asm.target.arch, size, dst, src1, null);
            assert (imm8 & 0xFF) == imm8;
            asm.emitVexOp(getLFlag(size), pp, mmmmm, w, op, dst, src1, src2, 1);
            asm.emitByte(imm8);
        }
    }

    /**
     * VEX-encoded comparison operation with an operand order of RVMI. The immediate operand is a
     * comparison operator.
     */
    public static final class VexFloatCompareOp extends VexOp {
        // @formatter:off
        public static final VexFloatCompareOp VCMPPS = new VexFloatCompareOp("VCMPPS", P_,   M_0F, WIG, 0xC2);
        public static final VexFloatCompareOp VCMPPD = new VexFloatCompareOp("VCMPPD", P_66, M_0F, WIG, 0xC2);
        public static final VexFloatCompareOp VCMPSS = new VexFloatCompareOp("VCMPSS", P_F2, M_0F, WIG, 0xC2);
        public static final VexFloatCompareOp VCMPSD = new VexFloatCompareOp("VCMPSD", P_F2, M_0F, WIG, 0xC2);
        // @formatter:on

        public enum Predicate {
            EQ_OQ(0x00),
            LT_OS(0x01),
            LE_OS(0x02),
            UNORD_Q(0x03),
            NEQ_UQ(0x04),
            NLT_US(0x05),
            NLE_US(0x06),
            ORD_Q(0x07),
            EQ_UQ(0x08),
            NGE_US(0x09),
            NGT_US(0x0a),
            FALSE_OQ(0x0b),
            NEQ_OQ(0x0c),
            GE_OS(0x0d),
            GT_OS(0x0e),
            TRUE_UQ(0x0f),
            EQ_OS(0x10),
            LT_OQ(0x11),
            LE_OQ(0x12),
            UNORD_S(0x13),
            NEQ_US(0x14),
            NLT_UQ(0x15),
            NLE_UQ(0x16),
            ORD_S(0x17),
            EQ_US(0x18),
            NGE_UQ(0x19),
            NGT_UQ(0x1a),
            FALSE_OS(0x1b),
            NEQ_OS(0x1c),
            GE_OQ(0x1d),
            GT_OQ(0x1e),
            TRUE_US(0x1f);

            private int imm8;

            Predicate(int imm8) {
                this.imm8 = imm8;
            }

            public static Predicate getPredicate(Condition condition, boolean unorderedIsTrue) {
                if (unorderedIsTrue) {
                    switch (condition) {
                        case EQ:
                            return EQ_UQ;
                        case NE:
                            return NEQ_UQ;
                        case LT:
                            return NGE_UQ;
                        case LE:
                            return NGT_UQ;
                        case GT:
                            return NLE_UQ;
                        case GE:
                            return NLT_UQ;
                        default:
                            throw GraalError.shouldNotReachHere();
                    }
                } else {
                    switch (condition) {
                        case EQ:
                            return EQ_OQ;
                        case NE:
                            return NEQ_OQ;
                        case LT:
                            return LT_OQ;
                        case LE:
                            return LE_OQ;
                        case GT:
                            return GT_OQ;
                        case GE:
                            return GE_OQ;
                        default:
                            throw GraalError.shouldNotReachHere();
                    }
                }
            }
        }

        private VexFloatCompareOp(String opcode, int pp, int mmmmm, int w, int op) {
            super(opcode, pp, mmmmm, w, op, AVX1);
        }

        public void emit(AMD64VectorAssembler asm, AVXSize size, Register dst, Register src1, Register src2, Predicate p) {
            assert assertion.check((AMD64) asm.target.arch, size, dst, src1, src2);
            asm.emitVexOp(getLFlag(size), pp, mmmmm, w, op, dst, src1, src2);
            asm.emitByte(p.imm8);
        }

        public void emit(AMD64VectorAssembler asm, AVXSize size, Register dst, Register src1, AMD64Address src2, Predicate p) {
            assert assertion.check((AMD64) asm.target.arch, size, dst, src1, null);
            asm.emitVexOp(getLFlag(size), pp, mmmmm, w, op, dst, src1, src2, 1);
            asm.emitByte(p.imm8);
        }
    }

    @Override
    public void movflt(Register dst, Register src) {
        VexMoveOp.VMOVAPS.emit(this, AVXSize.XMM, dst, src);
    }

    @Override
    public void movflt(Register dst, AMD64Address src) {
        VexMoveOp.VMOVSS.emit(this, AVXSize.XMM, dst, src);
    }

    @Override
    public void movflt(AMD64Address dst, Register src) {
        VexMoveOp.VMOVSS.emit(this, AVXSize.XMM, dst, src);
    }

    @Override
    public void movdbl(Register dst, Register src) {
        VexMoveOp.VMOVAPD.emit(this, AVXSize.XMM, dst, src);
    }

    @Override
    public void movdbl(Register dst, AMD64Address src) {
        VexMoveOp.VMOVSD.emit(this, AVXSize.XMM, dst, src);
    }

    @Override
    public void movdbl(AMD64Address dst, Register src) {
        VexMoveOp.VMOVSD.emit(this, AVXSize.XMM, dst, src);
    }
}
