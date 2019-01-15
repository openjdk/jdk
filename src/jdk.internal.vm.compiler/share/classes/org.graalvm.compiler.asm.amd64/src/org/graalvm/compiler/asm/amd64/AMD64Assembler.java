/*
 * Copyright (c) 2009, 2019, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.amd64.AMD64.CPU;
import static jdk.vm.ci.amd64.AMD64.MASK;
import static jdk.vm.ci.amd64.AMD64.XMM;
import static jdk.vm.ci.code.MemoryBarriers.STORE_LOAD;
import static org.graalvm.compiler.asm.amd64.AMD64AsmOptions.UseAddressNop;
import static org.graalvm.compiler.asm.amd64.AMD64AsmOptions.UseNormalNop;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.ADD;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.AND;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.CMP;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.OR;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.SBB;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.SUB;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.XOR;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64MOp.DEC;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64MOp.INC;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64MOp.NEG;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64MOp.NOT;
import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.EVEXPrefixConfig.B0;
import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.EVEXPrefixConfig.Z0;
import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.EVEXPrefixConfig.Z1;
import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.BYTE;
import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.DWORD;
import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.PD;
import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.PS;
import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.QWORD;
import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.SD;
import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.SS;
import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.WORD;
import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.VEXPrefixConfig.L128;
import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.VEXPrefixConfig.L256;
import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.VEXPrefixConfig.LZ;
import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.VEXPrefixConfig.M_0F;
import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.VEXPrefixConfig.M_0F38;
import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.VEXPrefixConfig.M_0F3A;
import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.VEXPrefixConfig.P_;
import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.VEXPrefixConfig.P_66;
import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.VEXPrefixConfig.P_F2;
import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.VEXPrefixConfig.P_F3;
import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.VEXPrefixConfig.W0;
import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.VEXPrefixConfig.W1;
import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.VEXPrefixConfig.WIG;
import static org.graalvm.compiler.core.common.NumUtil.isByte;
import static org.graalvm.compiler.core.common.NumUtil.isInt;
import static org.graalvm.compiler.core.common.NumUtil.isShiftCount;
import static org.graalvm.compiler.core.common.NumUtil.isUByte;

import java.util.EnumSet;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.amd64.AMD64Address.Scale;
import org.graalvm.compiler.asm.amd64.AVXKind.AVXSize;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.debug.GraalError;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.Register.RegisterCategory;
import jdk.vm.ci.code.TargetDescription;

/**
 * This class implements an assembler that can encode most X86 instructions.
 */
public class AMD64Assembler extends AMD64BaseAssembler {

    /**
     * Constructs an assembler for the AMD64 architecture.
     */
    public AMD64Assembler(TargetDescription target) {
        super(target);
    }

    /**
     * The x86 condition codes used for conditional jumps/moves.
     */
    public enum ConditionFlag {
        Zero(0x4, "|zero|"),
        NotZero(0x5, "|nzero|"),
        Equal(0x4, "="),
        NotEqual(0x5, "!="),
        Less(0xc, "<"),
        LessEqual(0xe, "<="),
        Greater(0xf, ">"),
        GreaterEqual(0xd, ">="),
        Below(0x2, "|<|"),
        BelowEqual(0x6, "|<=|"),
        Above(0x7, "|>|"),
        AboveEqual(0x3, "|>=|"),
        Overflow(0x0, "|of|"),
        NoOverflow(0x1, "|nof|"),
        CarrySet(0x2, "|carry|"),
        CarryClear(0x3, "|ncarry|"),
        Negative(0x8, "|neg|"),
        Positive(0x9, "|pos|"),
        Parity(0xa, "|par|"),
        NoParity(0xb, "|npar|");

        private final int value;
        private final String operator;

        ConditionFlag(int value, String operator) {
            this.value = value;
            this.operator = operator;
        }

        public ConditionFlag negate() {
            switch (this) {
                case Zero:
                    return NotZero;
                case NotZero:
                    return Zero;
                case Equal:
                    return NotEqual;
                case NotEqual:
                    return Equal;
                case Less:
                    return GreaterEqual;
                case LessEqual:
                    return Greater;
                case Greater:
                    return LessEqual;
                case GreaterEqual:
                    return Less;
                case Below:
                    return AboveEqual;
                case BelowEqual:
                    return Above;
                case Above:
                    return BelowEqual;
                case AboveEqual:
                    return Below;
                case Overflow:
                    return NoOverflow;
                case NoOverflow:
                    return Overflow;
                case CarrySet:
                    return CarryClear;
                case CarryClear:
                    return CarrySet;
                case Negative:
                    return Positive;
                case Positive:
                    return Negative;
                case Parity:
                    return NoParity;
                case NoParity:
                    return Parity;
            }
            throw new IllegalArgumentException();
        }

        public int getValue() {
            return value;
        }

        @Override
        public String toString() {
            return operator;
        }
    }

    /**
     * Operand size and register type constraints.
     */
    private enum OpAssertion {
        ByteAssertion(CPU, CPU, BYTE),
        ByteOrLargerAssertion(CPU, CPU, BYTE, WORD, DWORD, QWORD),
        WordOrLargerAssertion(CPU, CPU, WORD, DWORD, QWORD),
        DwordOrLargerAssertion(CPU, CPU, DWORD, QWORD),
        WordOrDwordAssertion(CPU, CPU, WORD, QWORD),
        QwordAssertion(CPU, CPU, QWORD),
        FloatAssertion(XMM, XMM, SS, SD, PS, PD),
        PackedFloatAssertion(XMM, XMM, PS, PD),
        SingleAssertion(XMM, XMM, SS),
        DoubleAssertion(XMM, XMM, SD),
        PackedDoubleAssertion(XMM, XMM, PD),
        IntToFloatAssertion(XMM, CPU, DWORD, QWORD),
        FloatToIntAssertion(CPU, XMM, DWORD, QWORD);

        private final RegisterCategory resultCategory;
        private final RegisterCategory inputCategory;
        private final OperandSize[] allowedSizes;

        OpAssertion(RegisterCategory resultCategory, RegisterCategory inputCategory, OperandSize... allowedSizes) {
            this.resultCategory = resultCategory;
            this.inputCategory = inputCategory;
            this.allowedSizes = allowedSizes;
        }

        protected boolean checkOperands(AMD64Op op, OperandSize size, Register resultReg, Register inputReg) {
            assert resultReg == null || resultCategory.equals(resultReg.getRegisterCategory()) : "invalid result register " + resultReg + " used in " + op;
            assert inputReg == null || inputCategory.equals(inputReg.getRegisterCategory()) : "invalid input register " + inputReg + " used in " + op;

            for (OperandSize s : allowedSizes) {
                if (size == s) {
                    return true;
                }
            }

            assert false : "invalid operand size " + size + " used in " + op;
            return false;
        }

    }

    protected static final int P_0F = 0x0F;
    protected static final int P_0F38 = 0x380F;
    protected static final int P_0F3A = 0x3A0F;

    /**
     * Base class for AMD64 opcodes.
     */
    public static class AMD64Op {

        private final String opcode;

        protected final int prefix1;
        protected final int prefix2;
        protected final int op;

        private final boolean dstIsByte;
        private final boolean srcIsByte;

        private final OpAssertion assertion;
        private final CPUFeature feature;

        protected AMD64Op(String opcode, int prefix1, int prefix2, int op, OpAssertion assertion, CPUFeature feature) {
            this(opcode, prefix1, prefix2, op, assertion == OpAssertion.ByteAssertion, assertion == OpAssertion.ByteAssertion, assertion, feature);
        }

        protected AMD64Op(String opcode, int prefix1, int prefix2, int op, boolean dstIsByte, boolean srcIsByte, OpAssertion assertion, CPUFeature feature) {
            this.opcode = opcode;
            this.prefix1 = prefix1;
            this.prefix2 = prefix2;
            this.op = op;

            this.dstIsByte = dstIsByte;
            this.srcIsByte = srcIsByte;

            this.assertion = assertion;
            this.feature = feature;
        }

        protected final void emitOpcode(AMD64Assembler asm, OperandSize size, int rxb, int dstEnc, int srcEnc) {
            if (prefix1 != 0) {
                asm.emitByte(prefix1);
            }
            if (size.getSizePrefix() != 0) {
                asm.emitByte(size.getSizePrefix());
            }
            int rexPrefix = 0x40 | rxb;
            if (size == QWORD) {
                rexPrefix |= 0x08;
            }
            if (rexPrefix != 0x40 || (dstIsByte && dstEnc >= 4) || (srcIsByte && srcEnc >= 4)) {
                asm.emitByte(rexPrefix);
            }
            if (prefix2 > 0xFF) {
                asm.emitShort(prefix2);
            } else if (prefix2 > 0) {
                asm.emitByte(prefix2);
            }
            asm.emitByte(op);
        }

        protected final boolean verify(AMD64Assembler asm, OperandSize size, Register resultReg, Register inputReg) {
            assert feature == null || asm.supports(feature) : String.format("unsupported feature %s required for %s", feature, opcode);
            assert assertion.checkOperands(this, size, resultReg, inputReg);
            return true;
        }

        public OperandSize[] getAllowedSizes() {
            return assertion.allowedSizes;
        }

        protected final boolean isSSEInstruction() {
            if (feature == null) {
                return false;
            }
            switch (feature) {
                case SSE:
                case SSE2:
                case SSE3:
                case SSSE3:
                case SSE4A:
                case SSE4_1:
                case SSE4_2:
                    return true;
                default:
                    return false;
            }
        }

        public final OpAssertion getAssertion() {
            return assertion;
        }

        @Override
        public String toString() {
            return opcode;
        }
    }

    /**
     * Base class for AMD64 opcodes with immediate operands.
     */
    public static class AMD64ImmOp extends AMD64Op {

        private final boolean immIsByte;

        protected AMD64ImmOp(String opcode, boolean immIsByte, int prefix, int op, OpAssertion assertion) {
            this(opcode, immIsByte, prefix, op, assertion, null);
        }

        protected AMD64ImmOp(String opcode, boolean immIsByte, int prefix, int op, OpAssertion assertion, CPUFeature feature) {
            super(opcode, 0, prefix, op, assertion, feature);
            this.immIsByte = immIsByte;
        }

        protected final void emitImmediate(AMD64Assembler asm, OperandSize size, int imm) {
            if (immIsByte) {
                assert imm == (byte) imm;
                asm.emitByte(imm);
            } else {
                size.emitImmediate(asm, imm);
            }
        }

        protected final int immediateSize(OperandSize size) {
            if (immIsByte) {
                return 1;
            } else {
                return size.getBytes();
            }
        }
    }

    /**
     * Opcode with operand order of either RM or MR for 2 address forms.
     */
    public abstract static class AMD64RROp extends AMD64Op {

        protected AMD64RROp(String opcode, int prefix1, int prefix2, int op, OpAssertion assertion, CPUFeature feature) {
            super(opcode, prefix1, prefix2, op, assertion, feature);
        }

        protected AMD64RROp(String opcode, int prefix1, int prefix2, int op, boolean dstIsByte, boolean srcIsByte, OpAssertion assertion, CPUFeature feature) {
            super(opcode, prefix1, prefix2, op, dstIsByte, srcIsByte, assertion, feature);
        }

        public abstract void emit(AMD64Assembler asm, OperandSize size, Register dst, Register src);
    }

    /**
     * Opcode with operand order of RM.
     */
    public static class AMD64RMOp extends AMD64RROp {
        // @formatter:off
        public static final AMD64RMOp IMUL   = new AMD64RMOp("IMUL",         P_0F, 0xAF, OpAssertion.ByteOrLargerAssertion);
        public static final AMD64RMOp BSF    = new AMD64RMOp("BSF",          P_0F, 0xBC);
        public static final AMD64RMOp BSR    = new AMD64RMOp("BSR",          P_0F, 0xBD);
        // POPCNT, TZCNT, and LZCNT support word operation. However, the legacy size prefix should
        // be emitted before the mandatory prefix 0xF3. Since we are not emitting bit count for
        // 16-bit operands, here we simply use DwordOrLargerAssertion.
        public static final AMD64RMOp POPCNT = new AMD64RMOp("POPCNT", 0xF3, P_0F, 0xB8, OpAssertion.DwordOrLargerAssertion, CPUFeature.POPCNT);
        public static final AMD64RMOp TZCNT  = new AMD64RMOp("TZCNT",  0xF3, P_0F, 0xBC, OpAssertion.DwordOrLargerAssertion, CPUFeature.BMI1);
        public static final AMD64RMOp LZCNT  = new AMD64RMOp("LZCNT",  0xF3, P_0F, 0xBD, OpAssertion.DwordOrLargerAssertion, CPUFeature.LZCNT);
        public static final AMD64RMOp MOVZXB = new AMD64RMOp("MOVZXB",       P_0F, 0xB6, false, true, OpAssertion.WordOrLargerAssertion);
        public static final AMD64RMOp MOVZX  = new AMD64RMOp("MOVZX",        P_0F, 0xB7, OpAssertion.DwordOrLargerAssertion);
        public static final AMD64RMOp MOVSXB = new AMD64RMOp("MOVSXB",       P_0F, 0xBE, false, true, OpAssertion.WordOrLargerAssertion);
        public static final AMD64RMOp MOVSX  = new AMD64RMOp("MOVSX",        P_0F, 0xBF, OpAssertion.DwordOrLargerAssertion);
        public static final AMD64RMOp MOVSXD = new AMD64RMOp("MOVSXD",             0x63, OpAssertion.QwordAssertion);
        public static final AMD64RMOp MOVB   = new AMD64RMOp("MOVB",               0x8A, OpAssertion.ByteAssertion);
        public static final AMD64RMOp MOV    = new AMD64RMOp("MOV",                0x8B);
        public static final AMD64RMOp CMP    = new AMD64RMOp("CMP",                0x3B);

        // MOVD/MOVQ and MOVSS/MOVSD are the same opcode, just with different operand size prefix
        public static final AMD64RMOp MOVD   = new AMD64RMOp("MOVD",   0x66, P_0F, 0x6E, OpAssertion.IntToFloatAssertion, CPUFeature.SSE2);
        public static final AMD64RMOp MOVQ   = new AMD64RMOp("MOVQ",   0x66, P_0F, 0x6E, OpAssertion.IntToFloatAssertion, CPUFeature.SSE2);
        public static final AMD64RMOp MOVSS  = new AMD64RMOp("MOVSS",        P_0F, 0x10, OpAssertion.FloatAssertion, CPUFeature.SSE);
        public static final AMD64RMOp MOVSD  = new AMD64RMOp("MOVSD",        P_0F, 0x10, OpAssertion.FloatAssertion, CPUFeature.SSE);

        // TEST is documented as MR operation, but it's symmetric, and using it as RM operation is more convenient.
        public static final AMD64RMOp TESTB  = new AMD64RMOp("TEST",               0x84, OpAssertion.ByteAssertion);
        public static final AMD64RMOp TEST   = new AMD64RMOp("TEST",               0x85);
        // @formatter:on

        protected AMD64RMOp(String opcode, int op) {
            this(opcode, 0, op);
        }

        protected AMD64RMOp(String opcode, int op, OpAssertion assertion) {
            this(opcode, 0, op, assertion);
        }

        protected AMD64RMOp(String opcode, int prefix, int op) {
            this(opcode, 0, prefix, op, null);
        }

        protected AMD64RMOp(String opcode, int prefix, int op, OpAssertion assertion) {
            this(opcode, 0, prefix, op, assertion, null);
        }

        protected AMD64RMOp(String opcode, int prefix, int op, OpAssertion assertion, CPUFeature feature) {
            this(opcode, 0, prefix, op, assertion, feature);
        }

        protected AMD64RMOp(String opcode, int prefix, int op, boolean dstIsByte, boolean srcIsByte, OpAssertion assertion) {
            super(opcode, 0, prefix, op, dstIsByte, srcIsByte, assertion, null);
        }

        protected AMD64RMOp(String opcode, int prefix1, int prefix2, int op, CPUFeature feature) {
            this(opcode, prefix1, prefix2, op, OpAssertion.WordOrLargerAssertion, feature);
        }

        protected AMD64RMOp(String opcode, int prefix1, int prefix2, int op, OpAssertion assertion, CPUFeature feature) {
            super(opcode, prefix1, prefix2, op, assertion, feature);
        }

        @Override
        public final void emit(AMD64Assembler asm, OperandSize size, Register dst, Register src) {
            assert verify(asm, size, dst, src);
            if (isSSEInstruction()) {
                Register nds = Register.None;
                switch (op) {
                    case 0x10:
                    case 0x51:
                        if ((size == SS) || (size == SD)) {
                            nds = dst;
                        }
                        break;
                    case 0x2A:
                    case 0x54:
                    case 0x55:
                    case 0x56:
                    case 0x57:
                    case 0x58:
                    case 0x59:
                    case 0x5A:
                    case 0x5C:
                    case 0x5D:
                    case 0x5E:
                    case 0x5F:
                        nds = dst;
                        break;
                    default:
                        break;
                }
                asm.simdPrefix(dst, nds, src, size, prefix1, prefix2, size == QWORD);
                asm.emitByte(op);
                asm.emitModRM(dst, src);
            } else {
                emitOpcode(asm, size, getRXB(dst, src), dst.encoding, src.encoding);
                asm.emitModRM(dst, src);
            }
        }

        public final void emit(AMD64Assembler asm, OperandSize size, Register dst, AMD64Address src) {
            assert verify(asm, size, dst, null);
            if (isSSEInstruction()) {
                Register nds = Register.None;
                switch (op) {
                    case 0x51:
                        if ((size == SS) || (size == SD)) {
                            nds = dst;
                        }
                        break;
                    case 0x2A:
                    case 0x54:
                    case 0x55:
                    case 0x56:
                    case 0x57:
                    case 0x58:
                    case 0x59:
                    case 0x5A:
                    case 0x5C:
                    case 0x5D:
                    case 0x5E:
                    case 0x5F:
                        nds = dst;
                        break;
                    default:
                        break;
                }
                asm.simdPrefix(dst, nds, src, size, prefix1, prefix2, size == QWORD);
                asm.emitByte(op);
                asm.emitOperandHelper(dst, src, 0);
            } else {
                emitOpcode(asm, size, getRXB(dst, src), dst.encoding, 0);
                asm.emitOperandHelper(dst, src, 0);
            }
        }
    }

    /**
     * Opcode with operand order of MR.
     */
    public static class AMD64MROp extends AMD64RROp {
        // @formatter:off
        public static final AMD64MROp MOVB   = new AMD64MROp("MOVB",               0x88, OpAssertion.ByteAssertion);
        public static final AMD64MROp MOV    = new AMD64MROp("MOV",                0x89);

        // MOVD and MOVQ are the same opcode, just with different operand size prefix
        // Note that as MR opcodes, they have reverse operand order, so the IntToFloatingAssertion must be used.
        public static final AMD64MROp MOVD   = new AMD64MROp("MOVD",   0x66, P_0F, 0x7E, OpAssertion.IntToFloatAssertion, CPUFeature.SSE2);
        public static final AMD64MROp MOVQ   = new AMD64MROp("MOVQ",   0x66, P_0F, 0x7E, OpAssertion.IntToFloatAssertion, CPUFeature.SSE2);

        // MOVSS and MOVSD are the same opcode, just with different operand size prefix
        public static final AMD64MROp MOVSS  = new AMD64MROp("MOVSS",        P_0F, 0x11, OpAssertion.FloatAssertion, CPUFeature.SSE);
        public static final AMD64MROp MOVSD  = new AMD64MROp("MOVSD",        P_0F, 0x11, OpAssertion.FloatAssertion, CPUFeature.SSE);
        // @formatter:on

        protected AMD64MROp(String opcode, int op) {
            this(opcode, 0, op);
        }

        protected AMD64MROp(String opcode, int op, OpAssertion assertion) {
            this(opcode, 0, op, assertion);
        }

        protected AMD64MROp(String opcode, int prefix, int op) {
            this(opcode, prefix, op, OpAssertion.WordOrLargerAssertion);
        }

        protected AMD64MROp(String opcode, int prefix, int op, OpAssertion assertion) {
            this(opcode, prefix, op, assertion, null);
        }

        protected AMD64MROp(String opcode, int prefix, int op, OpAssertion assertion, CPUFeature feature) {
            this(opcode, 0, prefix, op, assertion, feature);
        }

        protected AMD64MROp(String opcode, int prefix1, int prefix2, int op, OpAssertion assertion, CPUFeature feature) {
            super(opcode, prefix1, prefix2, op, assertion, feature);
        }

        @Override
        public final void emit(AMD64Assembler asm, OperandSize size, Register dst, Register src) {
            assert verify(asm, size, src, dst);
            if (isSSEInstruction()) {
                Register nds = Register.None;
                switch (op) {
                    case 0x11:
                        if ((size == SS) || (size == SD)) {
                            nds = src;
                        }
                        break;
                    default:
                        break;
                }
                asm.simdPrefix(src, nds, dst, size, prefix1, prefix2, size == QWORD);
                asm.emitByte(op);
                asm.emitModRM(src, dst);
            } else {
                emitOpcode(asm, size, getRXB(src, dst), src.encoding, dst.encoding);
                asm.emitModRM(src, dst);
            }
        }

        public final void emit(AMD64Assembler asm, OperandSize size, AMD64Address dst, Register src) {
            assert verify(asm, size, src, null);
            if (isSSEInstruction()) {
                asm.simdPrefix(src, Register.None, dst, size, prefix1, prefix2, size == QWORD);
                asm.emitByte(op);
            } else {
                emitOpcode(asm, size, getRXB(src, dst), src.encoding, 0);
            }
            asm.emitOperandHelper(src, dst, 0);
        }
    }

    /**
     * Opcodes with operand order of M.
     */
    public static class AMD64MOp extends AMD64Op {
        // @formatter:off
        public static final AMD64MOp NOT  = new AMD64MOp("NOT",  0xF7, 2);
        public static final AMD64MOp NEG  = new AMD64MOp("NEG",  0xF7, 3);
        public static final AMD64MOp MUL  = new AMD64MOp("MUL",  0xF7, 4);
        public static final AMD64MOp IMUL = new AMD64MOp("IMUL", 0xF7, 5);
        public static final AMD64MOp DIV  = new AMD64MOp("DIV",  0xF7, 6);
        public static final AMD64MOp IDIV = new AMD64MOp("IDIV", 0xF7, 7);
        public static final AMD64MOp INC  = new AMD64MOp("INC",  0xFF, 0);
        public static final AMD64MOp DEC  = new AMD64MOp("DEC",  0xFF, 1);
        public static final AMD64MOp PUSH = new AMD64MOp("PUSH", 0xFF, 6);
        public static final AMD64MOp POP  = new AMD64MOp("POP",  0x8F, 0, OpAssertion.WordOrDwordAssertion);
        // @formatter:on

        private final int ext;

        protected AMD64MOp(String opcode, int op, int ext) {
            this(opcode, 0, op, ext);
        }

        protected AMD64MOp(String opcode, int prefix, int op, int ext) {
            this(opcode, prefix, op, ext, OpAssertion.WordOrLargerAssertion);
        }

        protected AMD64MOp(String opcode, int op, int ext, OpAssertion assertion) {
            this(opcode, 0, op, ext, assertion);
        }

        protected AMD64MOp(String opcode, int prefix, int op, int ext, OpAssertion assertion) {
            super(opcode, 0, prefix, op, assertion, null);
            this.ext = ext;
        }

        public final void emit(AMD64Assembler asm, OperandSize size, Register dst) {
            assert verify(asm, size, dst, null);
            emitOpcode(asm, size, getRXB(null, dst), 0, dst.encoding);
            asm.emitModRM(ext, dst);
        }

        public final void emit(AMD64Assembler asm, OperandSize size, AMD64Address dst) {
            assert verify(asm, size, null, null);
            emitOpcode(asm, size, getRXB(null, dst), 0, 0);
            asm.emitOperandHelper(ext, dst, 0);
        }
    }

    /**
     * Opcodes with operand order of MI.
     */
    public static class AMD64MIOp extends AMD64ImmOp {
        // @formatter:off
        public static final AMD64MIOp MOVB = new AMD64MIOp("MOVB", true,  0xC6, 0, OpAssertion.ByteAssertion);
        public static final AMD64MIOp MOV  = new AMD64MIOp("MOV",  false, 0xC7, 0);
        public static final AMD64MIOp TEST = new AMD64MIOp("TEST", false, 0xF7, 0);
        // @formatter:on

        private final int ext;

        protected AMD64MIOp(String opcode, boolean immIsByte, int op, int ext) {
            this(opcode, immIsByte, op, ext, OpAssertion.WordOrLargerAssertion);
        }

        protected AMD64MIOp(String opcode, boolean immIsByte, int op, int ext, OpAssertion assertion) {
            this(opcode, immIsByte, 0, op, ext, assertion);
        }

        protected AMD64MIOp(String opcode, boolean immIsByte, int prefix, int op, int ext, OpAssertion assertion) {
            super(opcode, immIsByte, prefix, op, assertion);
            this.ext = ext;
        }

        public final void emit(AMD64Assembler asm, OperandSize size, Register dst, int imm) {
            emit(asm, size, dst, imm, false);
        }

        public final void emit(AMD64Assembler asm, OperandSize size, Register dst, int imm, boolean annotateImm) {
            assert verify(asm, size, dst, null);
            int insnPos = asm.position();
            emitOpcode(asm, size, getRXB(null, dst), 0, dst.encoding);
            asm.emitModRM(ext, dst);
            int immPos = asm.position();
            emitImmediate(asm, size, imm);
            int nextInsnPos = asm.position();
            if (annotateImm && asm.codePatchingAnnotationConsumer != null) {
                asm.codePatchingAnnotationConsumer.accept(new ImmediateOperandAnnotation(insnPos, immPos, nextInsnPos - immPos, nextInsnPos));
            }
        }

        public final void emit(AMD64Assembler asm, OperandSize size, AMD64Address dst, int imm) {
            emit(asm, size, dst, imm, false);
        }

        public final void emit(AMD64Assembler asm, OperandSize size, AMD64Address dst, int imm, boolean annotateImm) {
            assert verify(asm, size, null, null);
            int insnPos = asm.position();
            emitOpcode(asm, size, getRXB(null, dst), 0, 0);
            asm.emitOperandHelper(ext, dst, immediateSize(size));
            int immPos = asm.position();
            emitImmediate(asm, size, imm);
            int nextInsnPos = asm.position();
            if (annotateImm && asm.codePatchingAnnotationConsumer != null) {
                asm.codePatchingAnnotationConsumer.accept(new ImmediateOperandAnnotation(insnPos, immPos, nextInsnPos - immPos, nextInsnPos));
            }
        }
    }

    /**
     * Opcodes with operand order of RMI.
     *
     * We only have one form of round as the operation is always treated with single variant input,
     * making its extension to 3 address forms redundant.
     */
    public static class AMD64RMIOp extends AMD64ImmOp {
        // @formatter:off
        public static final AMD64RMIOp IMUL    = new AMD64RMIOp("IMUL", false, 0x69);
        public static final AMD64RMIOp IMUL_SX = new AMD64RMIOp("IMUL", true,  0x6B);
        public static final AMD64RMIOp ROUNDSS = new AMD64RMIOp("ROUNDSS", true, P_0F3A, 0x0A, OpAssertion.PackedDoubleAssertion, CPUFeature.SSE4_1);
        public static final AMD64RMIOp ROUNDSD = new AMD64RMIOp("ROUNDSD", true, P_0F3A, 0x0B, OpAssertion.PackedDoubleAssertion, CPUFeature.SSE4_1);
        // @formatter:on

        protected AMD64RMIOp(String opcode, boolean immIsByte, int op) {
            this(opcode, immIsByte, 0, op, OpAssertion.WordOrLargerAssertion, null);
        }

        protected AMD64RMIOp(String opcode, boolean immIsByte, int prefix, int op, OpAssertion assertion, CPUFeature feature) {
            super(opcode, immIsByte, prefix, op, assertion, feature);
        }

        public final void emit(AMD64Assembler asm, OperandSize size, Register dst, Register src, int imm) {
            assert verify(asm, size, dst, src);
            if (isSSEInstruction()) {
                Register nds = Register.None;
                switch (op) {
                    case 0x0A:
                    case 0x0B:
                        nds = dst;
                        break;
                    default:
                        break;
                }
                asm.simdPrefix(dst, nds, src, size, prefix1, prefix2, false);
                asm.emitByte(op);
                asm.emitModRM(dst, src);
            } else {
                emitOpcode(asm, size, getRXB(dst, src), dst.encoding, src.encoding);
                asm.emitModRM(dst, src);
            }
            emitImmediate(asm, size, imm);
        }

        public final void emit(AMD64Assembler asm, OperandSize size, Register dst, AMD64Address src, int imm) {
            assert verify(asm, size, dst, null);
            if (isSSEInstruction()) {
                Register nds = Register.None;
                switch (op) {
                    case 0x0A:
                    case 0x0B:
                        nds = dst;
                        break;
                    default:
                        break;
                }
                asm.simdPrefix(dst, nds, src, size, prefix1, prefix2, false);
                asm.emitByte(op);
            } else {
                emitOpcode(asm, size, getRXB(dst, src), dst.encoding, 0);
            }
            asm.emitOperandHelper(dst, src, immediateSize(size));
            emitImmediate(asm, size, imm);
        }
    }

    public static class SSEOp extends AMD64RMOp {
        // @formatter:off
        public static final SSEOp CVTSI2SS  = new SSEOp("CVTSI2SS",  0xF3, P_0F, 0x2A, OpAssertion.IntToFloatAssertion);
        public static final SSEOp CVTSI2SD  = new SSEOp("CVTSI2SD",  0xF2, P_0F, 0x2A, OpAssertion.IntToFloatAssertion);
        public static final SSEOp CVTTSS2SI = new SSEOp("CVTTSS2SI", 0xF3, P_0F, 0x2C, OpAssertion.FloatToIntAssertion);
        public static final SSEOp CVTTSD2SI = new SSEOp("CVTTSD2SI", 0xF2, P_0F, 0x2C, OpAssertion.FloatToIntAssertion);
        public static final SSEOp UCOMIS    = new SSEOp("UCOMIS",          P_0F, 0x2E, OpAssertion.PackedFloatAssertion);
        public static final SSEOp SQRT      = new SSEOp("SQRT",            P_0F, 0x51);
        public static final SSEOp AND       = new SSEOp("AND",             P_0F, 0x54, OpAssertion.PackedFloatAssertion);
        public static final SSEOp ANDN      = new SSEOp("ANDN",            P_0F, 0x55, OpAssertion.PackedFloatAssertion);
        public static final SSEOp OR        = new SSEOp("OR",              P_0F, 0x56, OpAssertion.PackedFloatAssertion);
        public static final SSEOp XOR       = new SSEOp("XOR",             P_0F, 0x57, OpAssertion.PackedFloatAssertion);
        public static final SSEOp ADD       = new SSEOp("ADD",             P_0F, 0x58);
        public static final SSEOp MUL       = new SSEOp("MUL",             P_0F, 0x59);
        public static final SSEOp CVTSS2SD  = new SSEOp("CVTSS2SD",        P_0F, 0x5A, OpAssertion.SingleAssertion);
        public static final SSEOp CVTSD2SS  = new SSEOp("CVTSD2SS",        P_0F, 0x5A, OpAssertion.DoubleAssertion);
        public static final SSEOp SUB       = new SSEOp("SUB",             P_0F, 0x5C);
        public static final SSEOp MIN       = new SSEOp("MIN",             P_0F, 0x5D);
        public static final SSEOp DIV       = new SSEOp("DIV",             P_0F, 0x5E);
        public static final SSEOp MAX       = new SSEOp("MAX",             P_0F, 0x5F);
        // @formatter:on

        protected SSEOp(String opcode, int prefix, int op) {
            this(opcode, prefix, op, OpAssertion.FloatAssertion);
        }

        protected SSEOp(String opcode, int prefix, int op, OpAssertion assertion) {
            this(opcode, 0, prefix, op, assertion);
        }

        protected SSEOp(String opcode, int mandatoryPrefix, int prefix, int op, OpAssertion assertion) {
            super(opcode, mandatoryPrefix, prefix, op, assertion, CPUFeature.SSE2);
        }
    }

    /**
     * Arithmetic operation with operand order of RM, MR or MI.
     */
    public static final class AMD64BinaryArithmetic {
        // @formatter:off
        public static final AMD64BinaryArithmetic ADD = new AMD64BinaryArithmetic("ADD", 0);
        public static final AMD64BinaryArithmetic OR  = new AMD64BinaryArithmetic("OR",  1);
        public static final AMD64BinaryArithmetic ADC = new AMD64BinaryArithmetic("ADC", 2);
        public static final AMD64BinaryArithmetic SBB = new AMD64BinaryArithmetic("SBB", 3);
        public static final AMD64BinaryArithmetic AND = new AMD64BinaryArithmetic("AND", 4);
        public static final AMD64BinaryArithmetic SUB = new AMD64BinaryArithmetic("SUB", 5);
        public static final AMD64BinaryArithmetic XOR = new AMD64BinaryArithmetic("XOR", 6);
        public static final AMD64BinaryArithmetic CMP = new AMD64BinaryArithmetic("CMP", 7);
        // @formatter:on

        private final AMD64MIOp byteImmOp;
        private final AMD64MROp byteMrOp;
        private final AMD64RMOp byteRmOp;

        private final AMD64MIOp immOp;
        private final AMD64MIOp immSxOp;
        private final AMD64MROp mrOp;
        private final AMD64RMOp rmOp;

        private AMD64BinaryArithmetic(String opcode, int code) {
            int baseOp = code << 3;

            byteImmOp = new AMD64MIOp(opcode, true, 0, 0x80, code, OpAssertion.ByteAssertion);
            byteMrOp = new AMD64MROp(opcode, 0, baseOp, OpAssertion.ByteAssertion);
            byteRmOp = new AMD64RMOp(opcode, 0, baseOp | 0x02, OpAssertion.ByteAssertion);

            immOp = new AMD64MIOp(opcode, false, 0, 0x81, code, OpAssertion.WordOrLargerAssertion);
            immSxOp = new AMD64MIOp(opcode, true, 0, 0x83, code, OpAssertion.WordOrLargerAssertion);
            mrOp = new AMD64MROp(opcode, 0, baseOp | 0x01, OpAssertion.WordOrLargerAssertion);
            rmOp = new AMD64RMOp(opcode, 0, baseOp | 0x03, OpAssertion.WordOrLargerAssertion);
        }

        public AMD64MIOp getMIOpcode(OperandSize size, boolean sx) {
            if (size == BYTE) {
                return byteImmOp;
            } else if (sx) {
                return immSxOp;
            } else {
                return immOp;
            }
        }

        public AMD64MROp getMROpcode(OperandSize size) {
            if (size == BYTE) {
                return byteMrOp;
            } else {
                return mrOp;
            }
        }

        public AMD64RMOp getRMOpcode(OperandSize size) {
            if (size == BYTE) {
                return byteRmOp;
            } else {
                return rmOp;
            }
        }
    }

    /**
     * Shift operation with operand order of M1, MC or MI.
     */
    public static final class AMD64Shift {
        // @formatter:off
        public static final AMD64Shift ROL = new AMD64Shift("ROL", 0);
        public static final AMD64Shift ROR = new AMD64Shift("ROR", 1);
        public static final AMD64Shift RCL = new AMD64Shift("RCL", 2);
        public static final AMD64Shift RCR = new AMD64Shift("RCR", 3);
        public static final AMD64Shift SHL = new AMD64Shift("SHL", 4);
        public static final AMD64Shift SHR = new AMD64Shift("SHR", 5);
        public static final AMD64Shift SAR = new AMD64Shift("SAR", 7);
        // @formatter:on

        public final AMD64MOp m1Op;
        public final AMD64MOp mcOp;
        public final AMD64MIOp miOp;

        private AMD64Shift(String opcode, int code) {
            m1Op = new AMD64MOp(opcode, 0, 0xD1, code, OpAssertion.WordOrLargerAssertion);
            mcOp = new AMD64MOp(opcode, 0, 0xD3, code, OpAssertion.WordOrLargerAssertion);
            miOp = new AMD64MIOp(opcode, true, 0, 0xC1, code, OpAssertion.WordOrLargerAssertion);
        }
    }

    private enum VEXOpAssertion {
        AVX1(CPUFeature.AVX, CPUFeature.AVX),
        AVX1_2(CPUFeature.AVX, CPUFeature.AVX2),
        AVX2(CPUFeature.AVX2, CPUFeature.AVX2),
        AVX1_128ONLY(CPUFeature.AVX, null),
        AVX1_256ONLY(null, CPUFeature.AVX),
        AVX2_256ONLY(null, CPUFeature.AVX2),
        XMM_CPU(CPUFeature.AVX, null, XMM, null, CPU, null),
        XMM_XMM_CPU(CPUFeature.AVX, null, XMM, XMM, CPU, null),
        CPU_XMM(CPUFeature.AVX, null, CPU, null, XMM, null),
        AVX1_2_CPU_XMM(CPUFeature.AVX, CPUFeature.AVX2, CPU, null, XMM, null),
        BMI1(CPUFeature.BMI1, null, CPU, CPU, CPU, null),
        BMI2(CPUFeature.BMI2, null, CPU, CPU, CPU, null);

        private final CPUFeature l128feature;
        private final CPUFeature l256feature;

        private final RegisterCategory rCategory;
        private final RegisterCategory vCategory;
        private final RegisterCategory mCategory;
        private final RegisterCategory imm8Category;

        VEXOpAssertion(CPUFeature l128feature, CPUFeature l256feature) {
            this(l128feature, l256feature, XMM, XMM, XMM, XMM);
        }

        VEXOpAssertion(CPUFeature l128feature, CPUFeature l256feature, RegisterCategory rCategory, RegisterCategory vCategory, RegisterCategory mCategory, RegisterCategory imm8Category) {
            this.l128feature = l128feature;
            this.l256feature = l256feature;
            this.rCategory = rCategory;
            this.vCategory = vCategory;
            this.mCategory = mCategory;
            this.imm8Category = imm8Category;
        }

        public boolean check(AMD64 arch, AVXSize size, Register r, Register v, Register m) {
            return check(arch, getLFlag(size), r, v, m, null);
        }

        public boolean check(AMD64 arch, AVXSize size, Register r, Register v, Register m, Register imm8) {
            return check(arch, getLFlag(size), r, v, m, imm8);
        }

        public boolean check(AMD64 arch, int l, Register r, Register v, Register m, Register imm8) {
            switch (l) {
                case L128:
                    assert l128feature != null && arch.getFeatures().contains(l128feature) : "emitting illegal 128 bit instruction";
                    break;
                case L256:
                    assert l256feature != null && arch.getFeatures().contains(l256feature) : "emitting illegal 256 bit instruction";
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
                    return l128feature != null && features.contains(l128feature);
                case YMM:
                    return l256feature != null && features.contains(l256feature);
                default:
                    throw GraalError.shouldNotReachHere();
            }
        }
    }

    /**
     * Base class for VEX-encoded instructions.
     */
    public static class VexOp {
        protected final int pp;
        protected final int mmmmm;
        protected final int w;
        protected final int op;

        private final String opcode;
        protected final VEXOpAssertion assertion;

        protected VexOp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion) {
            this.pp = pp;
            this.mmmmm = mmmmm;
            this.w = w;
            this.op = op;
            this.opcode = opcode;
            this.assertion = assertion;
        }

        public final boolean isSupported(AMD64Assembler vasm, AVXSize size) {
            return assertion.supports(((AMD64) vasm.target.arch).getFeatures(), size);
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
        public static final VexRROp VMASKMOVDQU = new VexRROp("VMASKMOVDQU", P_66, M_0F, WIG, 0xF7, VEXOpAssertion.AVX1_128ONLY);
        // @formatter:on

        protected VexRROp(String opcode, int pp, int mmmmm, int w, int op) {
            this(opcode, pp, mmmmm, w, op, VEXOpAssertion.AVX1);
        }

        protected VexRROp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion) {
            super(opcode, pp, mmmmm, w, op, assertion);
        }

        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register src) {
            assert assertion.check((AMD64) asm.target.arch, size, dst, null, src);
            assert op != 0x1A || op != 0x5A;
            asm.vexPrefix(dst, Register.None, src, size, pp, mmmmm, w, false);
            asm.emitByte(op);
            asm.emitModRM(dst, src);
        }
    }

    /**
     * VEX-encoded instructions with an operand order of RM.
     */
    public static class VexRMOp extends VexRROp {
        // @formatter:off
        public static final VexRMOp VCVTTSS2SI      = new VexRMOp("VCVTTSS2SI",      P_F3, M_0F,   W0,  0x2C, VEXOpAssertion.CPU_XMM);
        public static final VexRMOp VCVTTSS2SQ      = new VexRMOp("VCVTTSS2SQ",      P_F3, M_0F,   W1,  0x2C, VEXOpAssertion.CPU_XMM);
        public static final VexRMOp VCVTTSD2SI      = new VexRMOp("VCVTTSD2SI",      P_F2, M_0F,   W0,  0x2C, VEXOpAssertion.CPU_XMM);
        public static final VexRMOp VCVTTSD2SQ      = new VexRMOp("VCVTTSD2SQ",      P_F2, M_0F,   W1,  0x2C, VEXOpAssertion.CPU_XMM);
        public static final VexRMOp VCVTPS2PD       = new VexRMOp("VCVTPS2PD",       P_,   M_0F,   WIG, 0x5A);
        public static final VexRMOp VCVTPD2PS       = new VexRMOp("VCVTPD2PS",       P_66, M_0F,   WIG, 0x5A);
        public static final VexRMOp VCVTDQ2PS       = new VexRMOp("VCVTDQ2PS",       P_,   M_0F,   WIG, 0x5B);
        public static final VexRMOp VCVTTPS2DQ      = new VexRMOp("VCVTTPS2DQ",      P_F3, M_0F,   WIG, 0x5B);
        public static final VexRMOp VCVTTPD2DQ      = new VexRMOp("VCVTTPD2DQ",      P_66, M_0F,   WIG, 0xE6);
        public static final VexRMOp VCVTDQ2PD       = new VexRMOp("VCVTDQ2PD",       P_F3, M_0F,   WIG, 0xE6);
        public static final VexRMOp VBROADCASTSS    = new VexRMOp("VBROADCASTSS",    P_66, M_0F38, W0,  0x18);
        public static final VexRMOp VBROADCASTSD    = new VexRMOp("VBROADCASTSD",    P_66, M_0F38, W0,  0x19, VEXOpAssertion.AVX1_256ONLY);
        public static final VexRMOp VBROADCASTF128  = new VexRMOp("VBROADCASTF128",  P_66, M_0F38, W0,  0x1A, VEXOpAssertion.AVX1_256ONLY);
        public static final VexRMOp VPBROADCASTI128 = new VexRMOp("VPBROADCASTI128", P_66, M_0F38, W0,  0x5A, VEXOpAssertion.AVX2_256ONLY);
        public static final VexRMOp VPBROADCASTB    = new VexRMOp("VPBROADCASTB",    P_66, M_0F38, W0,  0x78, VEXOpAssertion.AVX2);
        public static final VexRMOp VPBROADCASTW    = new VexRMOp("VPBROADCASTW",    P_66, M_0F38, W0,  0x79, VEXOpAssertion.AVX2);
        public static final VexRMOp VPBROADCASTD    = new VexRMOp("VPBROADCASTD",    P_66, M_0F38, W0,  0x58, VEXOpAssertion.AVX2);
        public static final VexRMOp VPBROADCASTQ    = new VexRMOp("VPBROADCASTQ",    P_66, M_0F38, W0,  0x59, VEXOpAssertion.AVX2);
        public static final VexRMOp VPMOVMSKB       = new VexRMOp("VPMOVMSKB",       P_66, M_0F,   WIG, 0xD7, VEXOpAssertion.AVX1_2_CPU_XMM);
        public static final VexRMOp VPMOVSXBW       = new VexRMOp("VPMOVSXBW",       P_66, M_0F38, WIG, 0x20);
        public static final VexRMOp VPMOVSXBD       = new VexRMOp("VPMOVSXBD",       P_66, M_0F38, WIG, 0x21);
        public static final VexRMOp VPMOVSXBQ       = new VexRMOp("VPMOVSXBQ",       P_66, M_0F38, WIG, 0x22);
        public static final VexRMOp VPMOVSXWD       = new VexRMOp("VPMOVSXWD",       P_66, M_0F38, WIG, 0x23);
        public static final VexRMOp VPMOVSXWQ       = new VexRMOp("VPMOVSXWQ",       P_66, M_0F38, WIG, 0x24);
        public static final VexRMOp VPMOVSXDQ       = new VexRMOp("VPMOVSXDQ",       P_66, M_0F38, WIG, 0x25);
        public static final VexRMOp VPMOVZXBW       = new VexRMOp("VPMOVZXBW",       P_66, M_0F38, WIG, 0x30);
        public static final VexRMOp VPMOVZXBD       = new VexRMOp("VPMOVZXBD",       P_66, M_0F38, WIG, 0x31);
        public static final VexRMOp VPMOVZXBQ       = new VexRMOp("VPMOVZXBQ",       P_66, M_0F38, WIG, 0x32);
        public static final VexRMOp VPMOVZXWD       = new VexRMOp("VPMOVZXWD",       P_66, M_0F38, WIG, 0x33);
        public static final VexRMOp VPMOVZXWQ       = new VexRMOp("VPMOVZXWQ",       P_66, M_0F38, WIG, 0x34);
        public static final VexRMOp VPMOVZXDQ       = new VexRMOp("VPMOVZXDQ",       P_66, M_0F38, WIG, 0x35);
        public static final VexRMOp VPTEST          = new VexRMOp("VPTEST",          P_66, M_0F38, WIG, 0x17);
        public static final VexRMOp VSQRTPD         = new VexRMOp("VSQRTPD",         P_66, M_0F,   WIG, 0x51);
        public static final VexRMOp VSQRTPS         = new VexRMOp("VSQRTPS",         P_,   M_0F,   WIG, 0x51);
        public static final VexRMOp VSQRTSD         = new VexRMOp("VSQRTSD",         P_F2, M_0F,   WIG, 0x51);
        public static final VexRMOp VSQRTSS         = new VexRMOp("VSQRTSS",         P_F3, M_0F,   WIG, 0x51);
        public static final VexRMOp VUCOMISS        = new VexRMOp("VUCOMISS",        P_,   M_0F,   WIG, 0x2E);
        public static final VexRMOp VUCOMISD        = new VexRMOp("VUCOMISD",        P_66, M_0F,   WIG, 0x2E);
        // @formatter:on

        protected VexRMOp(String opcode, int pp, int mmmmm, int w, int op) {
            this(opcode, pp, mmmmm, w, op, VEXOpAssertion.AVX1);
        }

        protected VexRMOp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion) {
            super(opcode, pp, mmmmm, w, op, assertion);
        }

        public void emit(AMD64Assembler asm, AVXSize size, Register dst, AMD64Address src) {
            assert assertion.check((AMD64) asm.target.arch, size, dst, null, null);
            asm.vexPrefix(dst, Register.None, src, size, pp, mmmmm, w, false);
            asm.emitByte(op);
            asm.emitOperandHelper(dst, src, 0);
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
        public static final VexMoveOp VMOVD   = new VexMoveOp("VMOVD",   P_66, M_0F, W0,  0x6E, 0x7E, VEXOpAssertion.XMM_CPU);
        public static final VexMoveOp VMOVQ   = new VexMoveOp("VMOVQ",   P_66, M_0F, W1,  0x6E, 0x7E, VEXOpAssertion.XMM_CPU);
        // @formatter:on

        private final int opReverse;

        private VexMoveOp(String opcode, int pp, int mmmmm, int w, int op, int opReverse) {
            this(opcode, pp, mmmmm, w, op, opReverse, VEXOpAssertion.AVX1);
        }

        private VexMoveOp(String opcode, int pp, int mmmmm, int w, int op, int opReverse, VEXOpAssertion assertion) {
            super(opcode, pp, mmmmm, w, op, assertion);
            this.opReverse = opReverse;
        }

        public void emit(AMD64Assembler asm, AVXSize size, AMD64Address dst, Register src) {
            assert assertion.check((AMD64) asm.target.arch, size, src, null, null);
            asm.vexPrefix(src, Register.None, dst, size, pp, mmmmm, w, false);
            asm.emitByte(opReverse);
            asm.emitOperandHelper(src, dst, 0);
        }

        public void emitReverse(AMD64Assembler asm, AVXSize size, Register dst, Register src) {
            assert assertion.check((AMD64) asm.target.arch, size, src, null, dst);
            asm.vexPrefix(src, Register.None, dst, size, pp, mmmmm, w, false);
            asm.emitByte(opReverse);
            asm.emitModRM(src, dst);
        }
    }

    public interface VexRRIOp {
        void emit(AMD64Assembler asm, AVXSize size, Register dst, Register src, int imm8);
    }

    /**
     * VEX-encoded instructions with an operand order of RMI.
     */
    public static final class VexRMIOp extends VexOp implements VexRRIOp {
        // @formatter:off
        public static final VexRMIOp VPERMQ   = new VexRMIOp("VPERMQ",   P_66, M_0F3A, W1,  0x00, VEXOpAssertion.AVX2_256ONLY);
        public static final VexRMIOp VPSHUFLW = new VexRMIOp("VPSHUFLW", P_F2, M_0F,   WIG, 0x70, VEXOpAssertion.AVX1_2);
        public static final VexRMIOp VPSHUFHW = new VexRMIOp("VPSHUFHW", P_F3, M_0F,   WIG, 0x70, VEXOpAssertion.AVX1_2);
        public static final VexRMIOp VPSHUFD  = new VexRMIOp("VPSHUFD",  P_66, M_0F,   WIG, 0x70, VEXOpAssertion.AVX1_2);
        // @formatter:on

        private VexRMIOp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion) {
            super(opcode, pp, mmmmm, w, op, assertion);
        }

        @Override
        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register src, int imm8) {
            assert assertion.check((AMD64) asm.target.arch, size, dst, null, src);
            asm.vexPrefix(dst, Register.None, src, size, pp, mmmmm, w, false);
            asm.emitByte(op);
            asm.emitModRM(dst, src);
            asm.emitByte(imm8);
        }

        public void emit(AMD64Assembler asm, AVXSize size, Register dst, AMD64Address src, int imm8) {
            assert assertion.check((AMD64) asm.target.arch, size, dst, null, null);
            asm.vexPrefix(dst, Register.None, src, size, pp, mmmmm, w, false);
            asm.emitByte(op);
            asm.emitOperandHelper(dst, src, 1);
            asm.emitByte(imm8);
        }
    }

    /**
     * VEX-encoded instructions with an operand order of MRI.
     */
    public static final class VexMRIOp extends VexOp implements VexRRIOp {
        // @formatter:off
        public static final VexMRIOp VEXTRACTF128 = new VexMRIOp("VEXTRACTF128", P_66, M_0F3A, W0, 0x19, VEXOpAssertion.AVX1_256ONLY);
        public static final VexMRIOp VEXTRACTI128 = new VexMRIOp("VEXTRACTI128", P_66, M_0F3A, W0, 0x39, VEXOpAssertion.AVX2_256ONLY);
        public static final VexMRIOp VPEXTRB      = new VexMRIOp("VPEXTRB",      P_66, M_0F3A, W0, 0x14, VEXOpAssertion.XMM_CPU);
        public static final VexMRIOp VPEXTRW      = new VexMRIOp("VPEXTRW",      P_66, M_0F3A, W0, 0x15, VEXOpAssertion.XMM_CPU);
        public static final VexMRIOp VPEXTRD      = new VexMRIOp("VPEXTRD",      P_66, M_0F3A, W0, 0x16, VEXOpAssertion.XMM_CPU);
        public static final VexMRIOp VPEXTRQ      = new VexMRIOp("VPEXTRQ",      P_66, M_0F3A, W1, 0x16, VEXOpAssertion.XMM_CPU);
        // @formatter:on

        private VexMRIOp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion) {
            super(opcode, pp, mmmmm, w, op, assertion);
        }

        @Override
        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register src, int imm8) {
            assert assertion.check((AMD64) asm.target.arch, size, src, null, dst);
            asm.vexPrefix(src, Register.None, dst, size, pp, mmmmm, w, false);
            asm.emitByte(op);
            asm.emitModRM(src, dst);
            asm.emitByte(imm8);
        }

        public void emit(AMD64Assembler asm, AVXSize size, AMD64Address dst, Register src, int imm8) {
            assert assertion.check((AMD64) asm.target.arch, size, src, null, null);
            asm.vexPrefix(src, Register.None, dst, size, pp, mmmmm, w, false);
            asm.emitByte(op);
            asm.emitOperandHelper(src, dst, 1);
            asm.emitByte(imm8);
        }
    }

    /**
     * VEX-encoded instructions with an operand order of RVMR.
     */
    public static class VexRVMROp extends VexOp {
        // @formatter:off
        public static final VexRVMROp VPBLENDVB  = new VexRVMROp("VPBLENDVB",  P_66, M_0F3A, W0, 0x4C, VEXOpAssertion.AVX1_2);
        public static final VexRVMROp VPBLENDVPS = new VexRVMROp("VPBLENDVPS", P_66, M_0F3A, W0, 0x4A, VEXOpAssertion.AVX1);
        public static final VexRVMROp VPBLENDVPD = new VexRVMROp("VPBLENDVPD", P_66, M_0F3A, W0, 0x4B, VEXOpAssertion.AVX1);
        // @formatter:on

        protected VexRVMROp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion) {
            super(opcode, pp, mmmmm, w, op, assertion);
        }

        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register mask, Register src1, Register src2) {
            assert assertion.check((AMD64) asm.target.arch, size, dst, mask, src1, src2);
            asm.vexPrefix(dst, src1, src2, size, pp, mmmmm, w, false);
            asm.emitByte(op);
            asm.emitModRM(dst, src2);
            asm.emitByte(mask.encoding() << 4);
        }

        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register mask, Register src1, AMD64Address src2) {
            assert assertion.check((AMD64) asm.target.arch, size, dst, mask, src1, null);
            asm.vexPrefix(dst, src1, src2, size, pp, mmmmm, w, false);
            asm.emitByte(op);
            asm.emitOperandHelper(dst, src2, 0);
            asm.emitByte(mask.encoding() << 4);
        }
    }

    /**
     * VEX-encoded instructions with an operand order of RVM.
     */
    public static class VexRVMOp extends VexOp {
        // @formatter:off
        public static final VexRVMOp VANDPS    = new VexRVMOp("VANDPS",    P_,   M_0F,   WIG, 0x54);
        public static final VexRVMOp VANDPD    = new VexRVMOp("VANDPD",    P_66, M_0F,   WIG, 0x54);
        public static final VexRVMOp VANDNPS   = new VexRVMOp("VANDNPS",   P_,   M_0F,   WIG, 0x55);
        public static final VexRVMOp VANDNPD   = new VexRVMOp("VANDNPD",   P_66, M_0F,   WIG, 0x55);
        public static final VexRVMOp VORPS     = new VexRVMOp("VORPS",     P_,   M_0F,   WIG, 0x56);
        public static final VexRVMOp VORPD     = new VexRVMOp("VORPD",     P_66, M_0F,   WIG, 0x56);
        public static final VexRVMOp VXORPS    = new VexRVMOp("VXORPS",    P_,   M_0F,   WIG, 0x57);
        public static final VexRVMOp VXORPD    = new VexRVMOp("VXORPD",    P_66, M_0F,   WIG, 0x57);
        public static final VexRVMOp VADDPS    = new VexRVMOp("VADDPS",    P_,   M_0F,   WIG, 0x58);
        public static final VexRVMOp VADDPD    = new VexRVMOp("VADDPD",    P_66, M_0F,   WIG, 0x58);
        public static final VexRVMOp VADDSS    = new VexRVMOp("VADDSS",    P_F3, M_0F,   WIG, 0x58);
        public static final VexRVMOp VADDSD    = new VexRVMOp("VADDSD",    P_F2, M_0F,   WIG, 0x58);
        public static final VexRVMOp VMULPS    = new VexRVMOp("VMULPS",    P_,   M_0F,   WIG, 0x59);
        public static final VexRVMOp VMULPD    = new VexRVMOp("VMULPD",    P_66, M_0F,   WIG, 0x59);
        public static final VexRVMOp VMULSS    = new VexRVMOp("VMULSS",    P_F3, M_0F,   WIG, 0x59);
        public static final VexRVMOp VMULSD    = new VexRVMOp("VMULSD",    P_F2, M_0F,   WIG, 0x59);
        public static final VexRVMOp VSUBPS    = new VexRVMOp("VSUBPS",    P_,   M_0F,   WIG, 0x5C);
        public static final VexRVMOp VSUBPD    = new VexRVMOp("VSUBPD",    P_66, M_0F,   WIG, 0x5C);
        public static final VexRVMOp VSUBSS    = new VexRVMOp("VSUBSS",    P_F3, M_0F,   WIG, 0x5C);
        public static final VexRVMOp VSUBSD    = new VexRVMOp("VSUBSD",    P_F2, M_0F,   WIG, 0x5C);
        public static final VexRVMOp VMINPS    = new VexRVMOp("VMINPS",    P_,   M_0F,   WIG, 0x5D);
        public static final VexRVMOp VMINPD    = new VexRVMOp("VMINPD",    P_66, M_0F,   WIG, 0x5D);
        public static final VexRVMOp VMINSS    = new VexRVMOp("VMINSS",    P_F3, M_0F,   WIG, 0x5D);
        public static final VexRVMOp VMINSD    = new VexRVMOp("VMINSD",    P_F2, M_0F,   WIG, 0x5D);
        public static final VexRVMOp VDIVPS    = new VexRVMOp("VDIVPS",    P_,   M_0F,   WIG, 0x5E);
        public static final VexRVMOp VDIVPD    = new VexRVMOp("VDIVPD",    P_66, M_0F,   WIG, 0x5E);
        public static final VexRVMOp VDIVSS    = new VexRVMOp("VDIVPS",    P_F3, M_0F,   WIG, 0x5E);
        public static final VexRVMOp VDIVSD    = new VexRVMOp("VDIVPD",    P_F2, M_0F,   WIG, 0x5E);
        public static final VexRVMOp VMAXPS    = new VexRVMOp("VMAXPS",    P_,   M_0F,   WIG, 0x5F);
        public static final VexRVMOp VMAXPD    = new VexRVMOp("VMAXPD",    P_66, M_0F,   WIG, 0x5F);
        public static final VexRVMOp VMAXSS    = new VexRVMOp("VMAXSS",    P_F3, M_0F,   WIG, 0x5F);
        public static final VexRVMOp VMAXSD    = new VexRVMOp("VMAXSD",    P_F2, M_0F,   WIG, 0x5F);
        public static final VexRVMOp VADDSUBPS = new VexRVMOp("VADDSUBPS", P_F2, M_0F,   WIG, 0xD0);
        public static final VexRVMOp VADDSUBPD = new VexRVMOp("VADDSUBPD", P_66, M_0F,   WIG, 0xD0);
        public static final VexRVMOp VPAND     = new VexRVMOp("VPAND",     P_66, M_0F,   WIG, 0xDB, VEXOpAssertion.AVX1_2);
        public static final VexRVMOp VPOR      = new VexRVMOp("VPOR",      P_66, M_0F,   WIG, 0xEB, VEXOpAssertion.AVX1_2);
        public static final VexRVMOp VPXOR     = new VexRVMOp("VPXOR",     P_66, M_0F,   WIG, 0xEF, VEXOpAssertion.AVX1_2);
        public static final VexRVMOp VPADDB    = new VexRVMOp("VPADDB",    P_66, M_0F,   WIG, 0xFC, VEXOpAssertion.AVX1_2);
        public static final VexRVMOp VPADDW    = new VexRVMOp("VPADDW",    P_66, M_0F,   WIG, 0xFD, VEXOpAssertion.AVX1_2);
        public static final VexRVMOp VPADDD    = new VexRVMOp("VPADDD",    P_66, M_0F,   WIG, 0xFE, VEXOpAssertion.AVX1_2);
        public static final VexRVMOp VPADDQ    = new VexRVMOp("VPADDQ",    P_66, M_0F,   WIG, 0xD4, VEXOpAssertion.AVX1_2);
        public static final VexRVMOp VPMULHUW  = new VexRVMOp("VPMULHUW",  P_66, M_0F,   WIG, 0xE4, VEXOpAssertion.AVX1_2);
        public static final VexRVMOp VPMULHW   = new VexRVMOp("VPMULHW",   P_66, M_0F,   WIG, 0xE5, VEXOpAssertion.AVX1_2);
        public static final VexRVMOp VPMULLW   = new VexRVMOp("VPMULLW",   P_66, M_0F,   WIG, 0xD5, VEXOpAssertion.AVX1_2);
        public static final VexRVMOp VPMULLD   = new VexRVMOp("VPMULLD",   P_66, M_0F38, WIG, 0x40, VEXOpAssertion.AVX1_2);
        public static final VexRVMOp VPSUBB    = new VexRVMOp("VPSUBB",    P_66, M_0F,   WIG, 0xF8, VEXOpAssertion.AVX1_2);
        public static final VexRVMOp VPSUBW    = new VexRVMOp("VPSUBW",    P_66, M_0F,   WIG, 0xF9, VEXOpAssertion.AVX1_2);
        public static final VexRVMOp VPSUBD    = new VexRVMOp("VPSUBD",    P_66, M_0F,   WIG, 0xFA, VEXOpAssertion.AVX1_2);
        public static final VexRVMOp VPSUBQ    = new VexRVMOp("VPSUBQ",    P_66, M_0F,   WIG, 0xFB, VEXOpAssertion.AVX1_2);
        public static final VexRVMOp VPSHUFB   = new VexRVMOp("VPSHUFB",   P_66, M_0F38, WIG, 0x00, VEXOpAssertion.AVX1_2);
        public static final VexRVMOp VCVTSD2SS = new VexRVMOp("VCVTSD2SS", P_F2, M_0F,   WIG, 0x5A);
        public static final VexRVMOp VCVTSS2SD = new VexRVMOp("VCVTSS2SD", P_F3, M_0F,   WIG, 0x5A);
        public static final VexRVMOp VCVTSI2SD = new VexRVMOp("VCVTSI2SD", P_F2, M_0F,   W0,  0x2A, VEXOpAssertion.XMM_XMM_CPU);
        public static final VexRVMOp VCVTSQ2SD = new VexRVMOp("VCVTSQ2SD", P_F2, M_0F,   W1,  0x2A, VEXOpAssertion.XMM_XMM_CPU);
        public static final VexRVMOp VCVTSI2SS = new VexRVMOp("VCVTSI2SS", P_F3, M_0F,   W0,  0x2A, VEXOpAssertion.XMM_XMM_CPU);
        public static final VexRVMOp VCVTSQ2SS = new VexRVMOp("VCVTSQ2SS", P_F3, M_0F,   W1,  0x2A, VEXOpAssertion.XMM_XMM_CPU);
        public static final VexRVMOp VPCMPEQB  = new VexRVMOp("VPCMPEQB",  P_66, M_0F,   WIG, 0x74, VEXOpAssertion.AVX1_2);
        public static final VexRVMOp VPCMPEQW  = new VexRVMOp("VPCMPEQW",  P_66, M_0F,   WIG, 0x75, VEXOpAssertion.AVX1_2);
        public static final VexRVMOp VPCMPEQD  = new VexRVMOp("VPCMPEQD",  P_66, M_0F,   WIG, 0x76, VEXOpAssertion.AVX1_2);
        public static final VexRVMOp VPCMPEQQ  = new VexRVMOp("VPCMPEQQ",  P_66, M_0F38, WIG, 0x29, VEXOpAssertion.AVX1_2);
        public static final VexRVMOp VPCMPGTB  = new VexRVMOp("VPCMPGTB",  P_66, M_0F,   WIG, 0x64, VEXOpAssertion.AVX1_2);
        public static final VexRVMOp VPCMPGTW  = new VexRVMOp("VPCMPGTW",  P_66, M_0F,   WIG, 0x65, VEXOpAssertion.AVX1_2);
        public static final VexRVMOp VPCMPGTD  = new VexRVMOp("VPCMPGTD",  P_66, M_0F,   WIG, 0x66, VEXOpAssertion.AVX1_2);
        public static final VexRVMOp VPCMPGTQ  = new VexRVMOp("VPCMPGTQ",  P_66, M_0F38, WIG, 0x37, VEXOpAssertion.AVX1_2);
        // @formatter:on

        private VexRVMOp(String opcode, int pp, int mmmmm, int w, int op) {
            this(opcode, pp, mmmmm, w, op, VEXOpAssertion.AVX1);
        }

        protected VexRVMOp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion) {
            super(opcode, pp, mmmmm, w, op, assertion);
        }

        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register src1, Register src2) {
            assert assertion.check((AMD64) asm.target.arch, size, dst, src1, src2);
            asm.vexPrefix(dst, src1, src2, size, pp, mmmmm, w, false);
            asm.emitByte(op);
            asm.emitModRM(dst, src2);
        }

        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register src1, AMD64Address src2) {
            assert assertion.check((AMD64) asm.target.arch, size, dst, src1, null);
            asm.vexPrefix(dst, src1, src2, size, pp, mmmmm, w, false);
            asm.emitByte(op);
            asm.emitOperandHelper(dst, src2, 0);
        }
    }

    public static final class VexGeneralPurposeRVMOp extends VexRVMOp {
        // @formatter:off
        public static final VexGeneralPurposeRVMOp ANDN   = new VexGeneralPurposeRVMOp("ANDN",   P_,   M_0F38, WIG, 0xF2, VEXOpAssertion.BMI1);
        public static final VexGeneralPurposeRVMOp MULX   = new VexGeneralPurposeRVMOp("MULX",   P_F2, M_0F38, WIG, 0xF6, VEXOpAssertion.BMI2);
        public static final VexGeneralPurposeRVMOp PDEP   = new VexGeneralPurposeRVMOp("PDEP",   P_F2, M_0F38, WIG, 0xF5, VEXOpAssertion.BMI2);
        public static final VexGeneralPurposeRVMOp PEXT   = new VexGeneralPurposeRVMOp("PEXT",   P_F3, M_0F38, WIG, 0xF5, VEXOpAssertion.BMI2);
        // @formatter:on

        private VexGeneralPurposeRVMOp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion) {
            super(opcode, pp, mmmmm, w, op, assertion);
        }

        @Override
        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register src1, Register src2) {
            assert assertion.check((AMD64) asm.target.arch, LZ, dst, src1, src2, null);
            assert size == AVXSize.DWORD || size == AVXSize.QWORD;
            asm.vexPrefix(dst, src1, src2, size, pp, mmmmm, size == AVXSize.DWORD ? W0 : W1, false);
            asm.emitByte(op);
            asm.emitModRM(dst, src2);
        }

        @Override
        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register src1, AMD64Address src2) {
            assert assertion.check((AMD64) asm.target.arch, LZ, dst, src1, null, null);
            assert size == AVXSize.DWORD || size == AVXSize.QWORD;
            asm.vexPrefix(dst, src1, src2, size, pp, mmmmm, size == AVXSize.DWORD ? W0 : W1, false);
            asm.emitByte(op);
            asm.emitOperandHelper(dst, src2, 0);
        }
    }

    public static final class VexGeneralPurposeRMVOp extends VexOp {
        // @formatter:off
        public static final VexGeneralPurposeRMVOp BEXTR  = new VexGeneralPurposeRMVOp("BEXTR",  P_,   M_0F38, WIG, 0xF7, VEXOpAssertion.BMI1);
        public static final VexGeneralPurposeRMVOp BZHI   = new VexGeneralPurposeRMVOp("BZHI",   P_,   M_0F38, WIG, 0xF5, VEXOpAssertion.BMI2);
        public static final VexGeneralPurposeRMVOp SARX   = new VexGeneralPurposeRMVOp("SARX",   P_F3, M_0F38, WIG, 0xF7, VEXOpAssertion.BMI2);
        public static final VexGeneralPurposeRMVOp SHRX   = new VexGeneralPurposeRMVOp("SHRX",   P_F2, M_0F38, WIG, 0xF7, VEXOpAssertion.BMI2);
        public static final VexGeneralPurposeRMVOp SHLX   = new VexGeneralPurposeRMVOp("SHLX",   P_66, M_0F38, WIG, 0xF7, VEXOpAssertion.BMI2);
        // @formatter:on

        private VexGeneralPurposeRMVOp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion) {
            super(opcode, pp, mmmmm, w, op, assertion);
        }

        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register src1, Register src2) {
            assert assertion.check((AMD64) asm.target.arch, LZ, dst, src2, src1, null);
            assert size == AVXSize.DWORD || size == AVXSize.QWORD;
            asm.vexPrefix(dst, src2, src1, size, pp, mmmmm, size == AVXSize.DWORD ? W0 : W1, false);
            asm.emitByte(op);
            asm.emitModRM(dst, src1);
        }

        public void emit(AMD64Assembler asm, AVXSize size, Register dst, AMD64Address src1, Register src2) {
            assert assertion.check((AMD64) asm.target.arch, LZ, dst, src2, null, null);
            assert size == AVXSize.DWORD || size == AVXSize.QWORD;
            asm.vexPrefix(dst, src2, src1, size, pp, mmmmm, size == AVXSize.DWORD ? W0 : W1, false);
            asm.emitByte(op);
            asm.emitOperandHelper(dst, src1, 0);
        }
    }

    public static final class VexGeneralPurposeRMOp extends VexRMOp {
        // @formatter:off
        public static final VexGeneralPurposeRMOp BLSI    = new VexGeneralPurposeRMOp("BLSI",   P_,    M_0F38, WIG, 0xF3, 3, VEXOpAssertion.BMI1);
        public static final VexGeneralPurposeRMOp BLSMSK  = new VexGeneralPurposeRMOp("BLSMSK", P_,    M_0F38, WIG, 0xF3, 2, VEXOpAssertion.BMI1);
        public static final VexGeneralPurposeRMOp BLSR    = new VexGeneralPurposeRMOp("BLSR",   P_,    M_0F38, WIG, 0xF3, 1, VEXOpAssertion.BMI1);
        // @formatter:on
        private final int ext;

        private VexGeneralPurposeRMOp(String opcode, int pp, int mmmmm, int w, int op, int ext, VEXOpAssertion assertion) {
            super(opcode, pp, mmmmm, w, op, assertion);
            this.ext = ext;
        }

        @Override
        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register src) {
            assert assertion.check((AMD64) asm.target.arch, size, dst, null, null);
            asm.vexPrefix(AMD64.cpuRegisters[ext], dst, src, size, pp, mmmmm, size == AVXSize.DWORD ? W0 : W1, false);
            asm.emitByte(op);
            asm.emitModRM(ext, src);
        }

        @Override
        public void emit(AMD64Assembler asm, AVXSize size, Register dst, AMD64Address src) {
            assert assertion.check((AMD64) asm.target.arch, size, dst, null, null);
            asm.vexPrefix(AMD64.cpuRegisters[ext], dst, src, size, pp, mmmmm, size == AVXSize.DWORD ? W0 : W1, false);
            asm.emitByte(op);
            asm.emitOperandHelper(ext, src, 0);
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
            super(opcode, pp, mmmmm, w, op, VEXOpAssertion.AVX1_2);
            this.immOp = immOp;
            this.r = r;
        }

        @Override
        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register src, int imm8) {
            assert assertion.check((AMD64) asm.target.arch, size, null, dst, src);
            asm.vexPrefix(null, dst, src, size, pp, mmmmm, w, false);
            asm.emitByte(immOp);
            asm.emitModRM(r, src);
            asm.emitByte(imm8);
        }
    }

    public static final class VexMaskMoveOp extends VexOp {
        // @formatter:off
        public static final VexMaskMoveOp VMASKMOVPS = new VexMaskMoveOp("VMASKMOVPS", P_66, M_0F38, W0, 0x2C, 0x2E);
        public static final VexMaskMoveOp VMASKMOVPD = new VexMaskMoveOp("VMASKMOVPD", P_66, M_0F38, W0, 0x2D, 0x2F);
        public static final VexMaskMoveOp VPMASKMOVD = new VexMaskMoveOp("VPMASKMOVD", P_66, M_0F38, W0, 0x8C, 0x8E, VEXOpAssertion.AVX2);
        public static final VexMaskMoveOp VPMASKMOVQ = new VexMaskMoveOp("VPMASKMOVQ", P_66, M_0F38, W1, 0x8C, 0x8E, VEXOpAssertion.AVX2);
        // @formatter:on

        private final int opReverse;

        private VexMaskMoveOp(String opcode, int pp, int mmmmm, int w, int op, int opReverse) {
            this(opcode, pp, mmmmm, w, op, opReverse, VEXOpAssertion.AVX1);
        }

        private VexMaskMoveOp(String opcode, int pp, int mmmmm, int w, int op, int opReverse, VEXOpAssertion assertion) {
            super(opcode, pp, mmmmm, w, op, assertion);
            this.opReverse = opReverse;
        }

        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register mask, AMD64Address src) {
            assert assertion.check((AMD64) asm.target.arch, size, dst, mask, null);
            asm.vexPrefix(dst, mask, src, size, pp, mmmmm, w, false);
            asm.emitByte(op);
            asm.emitOperandHelper(dst, src, 0);
        }

        public void emit(AMD64Assembler asm, AVXSize size, AMD64Address dst, Register mask, Register src) {
            assert assertion.check((AMD64) asm.target.arch, size, src, mask, null);
            asm.vexPrefix(src, mask, dst, size, pp, mmmmm, w, false);
            asm.emitByte(opReverse);
            asm.emitOperandHelper(src, dst, 0);
        }
    }

    /**
     * VEX-encoded instructions with an operand order of RVMI.
     */
    public static final class VexRVMIOp extends VexOp {
        // @formatter:off
        public static final VexRVMIOp VSHUFPS     = new VexRVMIOp("VSHUFPS",     P_,   M_0F,   WIG, 0xC6);
        public static final VexRVMIOp VSHUFPD     = new VexRVMIOp("VSHUFPD",     P_66, M_0F,   WIG, 0xC6);
        public static final VexRVMIOp VINSERTF128 = new VexRVMIOp("VINSERTF128", P_66, M_0F3A, W0,  0x18, VEXOpAssertion.AVX1_256ONLY);
        public static final VexRVMIOp VINSERTI128 = new VexRVMIOp("VINSERTI128", P_66, M_0F3A, W0,  0x38, VEXOpAssertion.AVX2_256ONLY);
        // @formatter:on

        private VexRVMIOp(String opcode, int pp, int mmmmm, int w, int op) {
            this(opcode, pp, mmmmm, w, op, VEXOpAssertion.AVX1);
        }

        private VexRVMIOp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion) {
            super(opcode, pp, mmmmm, w, op, assertion);
        }

        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register src1, Register src2, int imm8) {
            assert assertion.check((AMD64) asm.target.arch, size, dst, src1, src2);
            assert (imm8 & 0xFF) == imm8;
            asm.vexPrefix(dst, src1, src2, size, pp, mmmmm, w, false);
            asm.emitByte(op);
            asm.emitModRM(dst, src2);
            asm.emitByte(imm8);
        }

        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register src1, AMD64Address src2, int imm8) {
            assert assertion.check((AMD64) asm.target.arch, size, dst, src1, null);
            assert (imm8 & 0xFF) == imm8;
            asm.vexPrefix(dst, src1, src2, size, pp, mmmmm, w, false);
            asm.emitByte(op);
            asm.emitOperandHelper(dst, src2, 1);
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
            super(opcode, pp, mmmmm, w, op, VEXOpAssertion.AVX1);
        }

        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register src1, Register src2, Predicate p) {
            assert assertion.check((AMD64) asm.target.arch, size, dst, src1, src2);
            asm.vexPrefix(dst, src1, src2, size, pp, mmmmm, w, false);
            asm.emitByte(op);
            asm.emitModRM(dst, src2);
            asm.emitByte(p.imm8);
        }

        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register src1, AMD64Address src2, Predicate p) {
            assert assertion.check((AMD64) asm.target.arch, size, dst, src1, null);
            asm.vexPrefix(dst, src1, src2, size, pp, mmmmm, w, false);
            asm.emitByte(op);
            asm.emitOperandHelper(dst, src2, 1);
            asm.emitByte(p.imm8);
        }
    }

    public final void addl(AMD64Address dst, int imm32) {
        ADD.getMIOpcode(DWORD, isByte(imm32)).emit(this, DWORD, dst, imm32);
    }

    public final void addl(Register dst, int imm32) {
        ADD.getMIOpcode(DWORD, isByte(imm32)).emit(this, DWORD, dst, imm32);
    }

    public final void addl(Register dst, Register src) {
        ADD.rmOp.emit(this, DWORD, dst, src);
    }

    public final void addpd(Register dst, Register src) {
        SSEOp.ADD.emit(this, PD, dst, src);
    }

    public final void addpd(Register dst, AMD64Address src) {
        SSEOp.ADD.emit(this, PD, dst, src);
    }

    public final void addsd(Register dst, Register src) {
        SSEOp.ADD.emit(this, SD, dst, src);
    }

    public final void addsd(Register dst, AMD64Address src) {
        SSEOp.ADD.emit(this, SD, dst, src);
    }

    private void addrNop4() {
        // 4 bytes: NOP DWORD PTR [EAX+0]
        emitByte(0x0F);
        emitByte(0x1F);
        emitByte(0x40); // emitRm(cbuf, 0x1, EAXEnc, EAXEnc);
        emitByte(0); // 8-bits offset (1 byte)
    }

    private void addrNop5() {
        // 5 bytes: NOP DWORD PTR [EAX+EAX*0+0] 8-bits offset
        emitByte(0x0F);
        emitByte(0x1F);
        emitByte(0x44); // emitRm(cbuf, 0x1, EAXEnc, 0x4);
        emitByte(0x00); // emitRm(cbuf, 0x0, EAXEnc, EAXEnc);
        emitByte(0); // 8-bits offset (1 byte)
    }

    private void addrNop7() {
        // 7 bytes: NOP DWORD PTR [EAX+0] 32-bits offset
        emitByte(0x0F);
        emitByte(0x1F);
        emitByte(0x80); // emitRm(cbuf, 0x2, EAXEnc, EAXEnc);
        emitInt(0); // 32-bits offset (4 bytes)
    }

    private void addrNop8() {
        // 8 bytes: NOP DWORD PTR [EAX+EAX*0+0] 32-bits offset
        emitByte(0x0F);
        emitByte(0x1F);
        emitByte(0x84); // emitRm(cbuf, 0x2, EAXEnc, 0x4);
        emitByte(0x00); // emitRm(cbuf, 0x0, EAXEnc, EAXEnc);
        emitInt(0); // 32-bits offset (4 bytes)
    }

    public final void andl(Register dst, int imm32) {
        AND.getMIOpcode(DWORD, isByte(imm32)).emit(this, DWORD, dst, imm32);
    }

    public final void andl(Register dst, Register src) {
        AND.rmOp.emit(this, DWORD, dst, src);
    }

    public final void andpd(Register dst, Register src) {
        SSEOp.AND.emit(this, PD, dst, src);
    }

    public final void andpd(Register dst, AMD64Address src) {
        SSEOp.AND.emit(this, PD, dst, src);
    }

    public final void bsfq(Register dst, Register src) {
        prefixq(dst, src);
        emitByte(0x0F);
        emitByte(0xBC);
        emitModRM(dst, src);
    }

    public final void bsrl(Register dst, Register src) {
        prefix(dst, src);
        emitByte(0x0F);
        emitByte(0xBD);
        emitModRM(dst, src);
    }

    public final void bswapl(Register reg) {
        prefix(reg);
        emitByte(0x0F);
        emitModRM(1, reg);
    }

    public final void cdql() {
        emitByte(0x99);
    }

    public final void cmovl(ConditionFlag cc, Register dst, Register src) {
        prefix(dst, src);
        emitByte(0x0F);
        emitByte(0x40 | cc.getValue());
        emitModRM(dst, src);
    }

    public final void cmovl(ConditionFlag cc, Register dst, AMD64Address src) {
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0x40 | cc.getValue());
        emitOperandHelper(dst, src, 0);
    }

    public final void cmpb(Register dst, Register src) {
        CMP.byteRmOp.emit(this, BYTE, dst, src);
    }

    public final void cmpw(Register dst, Register src) {
        CMP.rmOp.emit(this, WORD, dst, src);
    }

    public final void cmpl(Register dst, int imm32) {
        CMP.getMIOpcode(DWORD, isByte(imm32)).emit(this, DWORD, dst, imm32);
    }

    public final void cmpl(Register dst, Register src) {
        CMP.rmOp.emit(this, DWORD, dst, src);
    }

    public final void cmpl(Register dst, AMD64Address src) {
        CMP.rmOp.emit(this, DWORD, dst, src);
    }

    public final void cmpl(AMD64Address dst, int imm32) {
        CMP.getMIOpcode(DWORD, isByte(imm32)).emit(this, DWORD, dst, imm32);
    }

    /**
     * The 8-bit cmpxchg compares the value at adr with the contents of X86.rax, and stores reg into
     * adr if so; otherwise, the value at adr is loaded into X86.rax,. The ZF is set if the compared
     * values were equal, and cleared otherwise.
     */
    public final void cmpxchgb(Register reg, AMD64Address adr) { // cmpxchg
        prefixb(adr, reg);
        emitByte(0x0F);
        emitByte(0xB0);
        emitOperandHelper(reg, adr, 0);
    }

    /**
     * The 16-bit cmpxchg compares the value at adr with the contents of X86.rax, and stores reg
     * into adr if so; otherwise, the value at adr is loaded into X86.rax,. The ZF is set if the
     * compared values were equal, and cleared otherwise.
     */
    public final void cmpxchgw(Register reg, AMD64Address adr) { // cmpxchg
        emitByte(0x66); // Switch to 16-bit mode.
        prefix(adr, reg);
        emitByte(0x0F);
        emitByte(0xB1);
        emitOperandHelper(reg, adr, 0);
    }

    /**
     * The 32-bit cmpxchg compares the value at adr with the contents of X86.rax, and stores reg
     * into adr if so; otherwise, the value at adr is loaded into X86.rax,. The ZF is set if the
     * compared values were equal, and cleared otherwise.
     */
    public final void cmpxchgl(Register reg, AMD64Address adr) { // cmpxchg
        prefix(adr, reg);
        emitByte(0x0F);
        emitByte(0xB1);
        emitOperandHelper(reg, adr, 0);
    }

    public final void cvtsi2sdl(Register dst, Register src) {
        SSEOp.CVTSI2SD.emit(this, DWORD, dst, src);
    }

    public final void cvttsd2sil(Register dst, Register src) {
        SSEOp.CVTTSD2SI.emit(this, DWORD, dst, src);
    }

    public final void decl(AMD64Address dst) {
        prefix(dst);
        emitByte(0xFF);
        emitOperandHelper(1, dst, 0);
    }

    public final void divsd(Register dst, Register src) {
        SSEOp.DIV.emit(this, SD, dst, src);
    }

    public final void hlt() {
        emitByte(0xF4);
    }

    public final void imull(Register dst, Register src, int value) {
        if (isByte(value)) {
            AMD64RMIOp.IMUL_SX.emit(this, DWORD, dst, src, value);
        } else {
            AMD64RMIOp.IMUL.emit(this, DWORD, dst, src, value);
        }
    }

    public final void incl(AMD64Address dst) {
        prefix(dst);
        emitByte(0xFF);
        emitOperandHelper(0, dst, 0);
    }

    public void jcc(ConditionFlag cc, int jumpTarget, boolean forceDisp32) {
        int shortSize = 2;
        int longSize = 6;
        long disp = jumpTarget - position();
        if (!forceDisp32 && isByte(disp - shortSize)) {
            // 0111 tttn #8-bit disp
            emitByte(0x70 | cc.getValue());
            emitByte((int) ((disp - shortSize) & 0xFF));
        } else {
            // 0000 1111 1000 tttn #32-bit disp
            assert isInt(disp - longSize) : "must be 32bit offset (call4)";
            emitByte(0x0F);
            emitByte(0x80 | cc.getValue());
            emitInt((int) (disp - longSize));
        }
    }

    public final void jcc(ConditionFlag cc, Label l) {
        assert (0 <= cc.getValue()) && (cc.getValue() < 16) : "illegal cc";
        if (l.isBound()) {
            jcc(cc, l.position(), false);
        } else {
            // Note: could eliminate cond. jumps to this jump if condition
            // is the same however, seems to be rather unlikely case.
            // Note: use jccb() if label to be bound is very close to get
            // an 8-bit displacement
            l.addPatchAt(position());
            emitByte(0x0F);
            emitByte(0x80 | cc.getValue());
            emitInt(0);
        }

    }

    public final void jccb(ConditionFlag cc, Label l) {
        if (l.isBound()) {
            int shortSize = 2;
            int entry = l.position();
            assert isByte(entry - (position() + shortSize)) : "Dispacement too large for a short jmp";
            long disp = entry - position();
            // 0111 tttn #8-bit disp
            emitByte(0x70 | cc.getValue());
            emitByte((int) ((disp - shortSize) & 0xFF));
        } else {
            l.addPatchAt(position());
            emitByte(0x70 | cc.getValue());
            emitByte(0);
        }
    }

    public final void jmp(int jumpTarget, boolean forceDisp32) {
        int shortSize = 2;
        int longSize = 5;
        long disp = jumpTarget - position();
        if (!forceDisp32 && isByte(disp - shortSize)) {
            emitByte(0xEB);
            emitByte((int) ((disp - shortSize) & 0xFF));
        } else {
            emitByte(0xE9);
            emitInt((int) (disp - longSize));
        }
    }

    @Override
    public final void jmp(Label l) {
        if (l.isBound()) {
            jmp(l.position(), false);
        } else {
            // By default, forward jumps are always 32-bit displacements, since
            // we can't yet know where the label will be bound. If you're sure that
            // the forward jump will not run beyond 256 bytes, use jmpb to
            // force an 8-bit displacement.

            l.addPatchAt(position());
            emitByte(0xE9);
            emitInt(0);
        }
    }

    public final void jmp(Register entry) {
        prefix(entry);
        emitByte(0xFF);
        emitModRM(4, entry);
    }

    public final void jmp(AMD64Address adr) {
        prefix(adr);
        emitByte(0xFF);
        emitOperandHelper(AMD64.rsp, adr, 0);
    }

    public final void jmpb(Label l) {
        if (l.isBound()) {
            int shortSize = 2;
            int entry = l.position();
            assert isByte((entry - position()) + shortSize) : "Dispacement too large for a short jmp";
            long offs = entry - position();
            emitByte(0xEB);
            emitByte((int) ((offs - shortSize) & 0xFF));
        } else {

            l.addPatchAt(position());
            emitByte(0xEB);
            emitByte(0);
        }
    }

    public final void lead(Register dst, AMD64Address src) {
        prefix(src, dst);
        emitByte(0x8D);
        emitOperandHelper(dst, src, 0);
    }

    public final void leaq(Register dst, AMD64Address src) {
        prefixq(src, dst);
        emitByte(0x8D);
        emitOperandHelper(dst, src, 0);
    }

    public final void leave() {
        emitByte(0xC9);
    }

    public final void lock() {
        emitByte(0xF0);
    }

    public final void movapd(Register dst, Register src) {
        assert inRC(XMM, dst) && inRC(XMM, src);
        simdPrefix(dst, Register.None, src, PD, P_0F, false);
        emitByte(0x28);
        emitModRM(dst, src);
    }

    public final void movaps(Register dst, Register src) {
        assert inRC(XMM, dst) && inRC(XMM, src);
        simdPrefix(dst, Register.None, src, PS, P_0F, false);
        emitByte(0x28);
        emitModRM(dst, src);
    }

    public final void movb(AMD64Address dst, int imm8) {
        prefix(dst);
        emitByte(0xC6);
        emitOperandHelper(0, dst, 1);
        emitByte(imm8);
    }

    public final void movb(AMD64Address dst, Register src) {
        assert inRC(CPU, src) : "must have byte register";
        prefixb(dst, src);
        emitByte(0x88);
        emitOperandHelper(src, dst, 0);
    }

    public final void movl(Register dst, int imm32) {
        movl(dst, imm32, false);
    }

    public final void movl(Register dst, int imm32, boolean annotateImm) {
        int insnPos = position();
        prefix(dst);
        emitByte(0xB8 + encode(dst));
        int immPos = position();
        emitInt(imm32);
        int nextInsnPos = position();
        if (annotateImm && codePatchingAnnotationConsumer != null) {
            codePatchingAnnotationConsumer.accept(new ImmediateOperandAnnotation(insnPos, immPos, nextInsnPos - immPos, nextInsnPos));
        }
    }

    public final void movl(Register dst, Register src) {
        prefix(dst, src);
        emitByte(0x8B);
        emitModRM(dst, src);
    }

    public final void movl(Register dst, AMD64Address src) {
        prefix(src, dst);
        emitByte(0x8B);
        emitOperandHelper(dst, src, 0);
    }

    /**
     * @param wide use 4 byte encoding for displacements that would normally fit in a byte
     */
    public final void movl(Register dst, AMD64Address src, boolean wide) {
        prefix(src, dst);
        emitByte(0x8B);
        emitOperandHelper(dst, src, wide, 0);
    }

    public final void movl(AMD64Address dst, int imm32) {
        prefix(dst);
        emitByte(0xC7);
        emitOperandHelper(0, dst, 4);
        emitInt(imm32);
    }

    public final void movl(AMD64Address dst, Register src) {
        prefix(dst, src);
        emitByte(0x89);
        emitOperandHelper(src, dst, 0);
    }

    /**
     * New CPUs require use of movsd and movss to avoid partial register stall when loading from
     * memory. But for old Opteron use movlpd instead of movsd. The selection is done in
     * {@link AMD64MacroAssembler#movdbl(Register, AMD64Address)} and
     * {@link AMD64MacroAssembler#movflt(Register, Register)}.
     */
    public final void movlpd(Register dst, AMD64Address src) {
        assert inRC(XMM, dst);
        simdPrefix(dst, dst, src, PD, P_0F, false);
        emitByte(0x12);
        emitOperandHelper(dst, src, 0);
    }

    public final void movlhps(Register dst, Register src) {
        assert inRC(XMM, dst) && inRC(XMM, src);
        simdPrefix(dst, src, src, PS, P_0F, false);
        emitByte(0x16);
        emitModRM(dst, src);
    }

    public final void movq(Register dst, AMD64Address src) {
        movq(dst, src, false);
    }

    public final void movq(Register dst, AMD64Address src, boolean force4BytesDisplacement) {
        if (inRC(XMM, dst)) {
            // Insn: MOVQ xmm, r/m64
            // Code: F3 0F 7E /r
            // An alternative instruction would be 66 REX.W 0F 6E /r. We prefer the REX.W free
            // format, because it would allow us to emit 2-bytes-prefixed vex-encoding instruction
            // when applicable.
            simdPrefix(dst, Register.None, src, SS, P_0F, false);
            emitByte(0x7E);
            emitOperandHelper(dst, src, force4BytesDisplacement, 0);
        } else {
            // gpr version of movq
            prefixq(src, dst);
            emitByte(0x8B);
            emitOperandHelper(dst, src, force4BytesDisplacement, 0);
        }
    }

    public final void movq(Register dst, Register src) {
        assert inRC(CPU, dst) && inRC(CPU, src);
        prefixq(dst, src);
        emitByte(0x8B);
        emitModRM(dst, src);
    }

    public final void movq(AMD64Address dst, Register src) {
        if (inRC(XMM, src)) {
            // Insn: MOVQ r/m64, xmm
            // Code: 66 0F D6 /r
            // An alternative instruction would be 66 REX.W 0F 7E /r. We prefer the REX.W free
            // format, because it would allow us to emit 2-bytes-prefixed vex-encoding instruction
            // when applicable.
            simdPrefix(src, Register.None, dst, PD, P_0F, false);
            emitByte(0xD6);
            emitOperandHelper(src, dst, 0);
        } else {
            // gpr version of movq
            prefixq(dst, src);
            emitByte(0x89);
            emitOperandHelper(src, dst, 0);
        }
    }

    public final void movsbl(Register dst, AMD64Address src) {
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0xBE);
        emitOperandHelper(dst, src, 0);
    }

    public final void movsbl(Register dst, Register src) {
        prefix(dst, false, src, true);
        emitByte(0x0F);
        emitByte(0xBE);
        emitModRM(dst, src);
    }

    public final void movsbq(Register dst, AMD64Address src) {
        prefixq(src, dst);
        emitByte(0x0F);
        emitByte(0xBE);
        emitOperandHelper(dst, src, 0);
    }

    public final void movsbq(Register dst, Register src) {
        prefixq(dst, src);
        emitByte(0x0F);
        emitByte(0xBE);
        emitModRM(dst, src);
    }

    public final void movsd(Register dst, Register src) {
        AMD64RMOp.MOVSD.emit(this, SD, dst, src);
    }

    public final void movsd(Register dst, AMD64Address src) {
        AMD64RMOp.MOVSD.emit(this, SD, dst, src);
    }

    public final void movsd(AMD64Address dst, Register src) {
        AMD64MROp.MOVSD.emit(this, SD, dst, src);
    }

    public final void movss(Register dst, Register src) {
        AMD64RMOp.MOVSS.emit(this, SS, dst, src);
    }

    public final void movss(Register dst, AMD64Address src) {
        AMD64RMOp.MOVSS.emit(this, SS, dst, src);
    }

    public final void movss(AMD64Address dst, Register src) {
        AMD64MROp.MOVSS.emit(this, SS, dst, src);
    }

    public final void mulpd(Register dst, Register src) {
        SSEOp.MUL.emit(this, PD, dst, src);
    }

    public final void mulpd(Register dst, AMD64Address src) {
        SSEOp.MUL.emit(this, PD, dst, src);
    }

    public final void mulsd(Register dst, Register src) {
        SSEOp.MUL.emit(this, SD, dst, src);
    }

    public final void mulsd(Register dst, AMD64Address src) {
        SSEOp.MUL.emit(this, SD, dst, src);
    }

    public final void mulss(Register dst, Register src) {
        SSEOp.MUL.emit(this, SS, dst, src);
    }

    public final void movswl(Register dst, AMD64Address src) {
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0xBF);
        emitOperandHelper(dst, src, 0);
    }

    public final void movw(AMD64Address dst, int imm16) {
        emitByte(0x66); // switch to 16-bit mode
        prefix(dst);
        emitByte(0xC7);
        emitOperandHelper(0, dst, 2);
        emitShort(imm16);
    }

    public final void movw(AMD64Address dst, Register src) {
        emitByte(0x66);
        prefix(dst, src);
        emitByte(0x89);
        emitOperandHelper(src, dst, 0);
    }

    public final void movzbl(Register dst, AMD64Address src) {
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0xB6);
        emitOperandHelper(dst, src, 0);
    }

    public final void movzbl(Register dst, Register src) {
        AMD64RMOp.MOVZXB.emit(this, DWORD, dst, src);
    }

    public final void movzbq(Register dst, Register src) {
        AMD64RMOp.MOVZXB.emit(this, QWORD, dst, src);
    }

    public final void movzwl(Register dst, AMD64Address src) {
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0xB7);
        emitOperandHelper(dst, src, 0);
    }

    public final void negl(Register dst) {
        NEG.emit(this, DWORD, dst);
    }

    public final void notl(Register dst) {
        NOT.emit(this, DWORD, dst);
    }

    public final void notq(Register dst) {
        NOT.emit(this, QWORD, dst);
    }

    @Override
    public final void ensureUniquePC() {
        nop();
    }

    public final void nop() {
        nop(1);
    }

    public void nop(int count) {
        int i = count;
        if (UseNormalNop) {
            assert i > 0 : " ";
            // The fancy nops aren't currently recognized by debuggers making it a
            // pain to disassemble code while debugging. If assert are on clearly
            // speed is not an issue so simply use the single byte traditional nop
            // to do alignment.

            for (; i > 0; i--) {
                emitByte(0x90);
            }
            return;
        }

        if (UseAddressNop) {
            //
            // Using multi-bytes nops "0x0F 0x1F [Address]" for AMD.
            // 1: 0x90
            // 2: 0x66 0x90
            // 3: 0x66 0x66 0x90 (don't use "0x0F 0x1F 0x00" - need patching safe padding)
            // 4: 0x0F 0x1F 0x40 0x00
            // 5: 0x0F 0x1F 0x44 0x00 0x00
            // 6: 0x66 0x0F 0x1F 0x44 0x00 0x00
            // 7: 0x0F 0x1F 0x80 0x00 0x00 0x00 0x00
            // 8: 0x0F 0x1F 0x84 0x00 0x00 0x00 0x00 0x00
            // 9: 0x66 0x0F 0x1F 0x84 0x00 0x00 0x00 0x00 0x00
            // 10: 0x66 0x66 0x0F 0x1F 0x84 0x00 0x00 0x00 0x00 0x00
            // 11: 0x66 0x66 0x66 0x0F 0x1F 0x84 0x00 0x00 0x00 0x00 0x00

            // The rest coding is AMD specific - use consecutive Address nops

            // 12: 0x66 0x0F 0x1F 0x44 0x00 0x00 0x66 0x0F 0x1F 0x44 0x00 0x00
            // 13: 0x0F 0x1F 0x80 0x00 0x00 0x00 0x00 0x66 0x0F 0x1F 0x44 0x00 0x00
            // 14: 0x0F 0x1F 0x80 0x00 0x00 0x00 0x00 0x0F 0x1F 0x80 0x00 0x00 0x00 0x00
            // 15: 0x0F 0x1F 0x84 0x00 0x00 0x00 0x00 0x00 0x0F 0x1F 0x80 0x00 0x00 0x00 0x00
            // 16: 0x0F 0x1F 0x84 0x00 0x00 0x00 0x00 0x00 0x0F 0x1F 0x84 0x00 0x00 0x00 0x00 0x00
            // Size prefixes (0x66) are added for larger sizes

            while (i >= 22) {
                i -= 11;
                emitByte(0x66); // size prefix
                emitByte(0x66); // size prefix
                emitByte(0x66); // size prefix
                addrNop8();
            }
            // Generate first nop for size between 21-12
            switch (i) {
                case 21:
                    i -= 11;
                    emitByte(0x66); // size prefix
                    emitByte(0x66); // size prefix
                    emitByte(0x66); // size prefix
                    addrNop8();
                    break;
                case 20:
                case 19:
                    i -= 10;
                    emitByte(0x66); // size prefix
                    emitByte(0x66); // size prefix
                    addrNop8();
                    break;
                case 18:
                case 17:
                    i -= 9;
                    emitByte(0x66); // size prefix
                    addrNop8();
                    break;
                case 16:
                case 15:
                    i -= 8;
                    addrNop8();
                    break;
                case 14:
                case 13:
                    i -= 7;
                    addrNop7();
                    break;
                case 12:
                    i -= 6;
                    emitByte(0x66); // size prefix
                    addrNop5();
                    break;
                default:
                    assert i < 12;
            }

            // Generate second nop for size between 11-1
            switch (i) {
                case 11:
                    emitByte(0x66); // size prefix
                    emitByte(0x66); // size prefix
                    emitByte(0x66); // size prefix
                    addrNop8();
                    break;
                case 10:
                    emitByte(0x66); // size prefix
                    emitByte(0x66); // size prefix
                    addrNop8();
                    break;
                case 9:
                    emitByte(0x66); // size prefix
                    addrNop8();
                    break;
                case 8:
                    addrNop8();
                    break;
                case 7:
                    addrNop7();
                    break;
                case 6:
                    emitByte(0x66); // size prefix
                    addrNop5();
                    break;
                case 5:
                    addrNop5();
                    break;
                case 4:
                    addrNop4();
                    break;
                case 3:
                    // Don't use "0x0F 0x1F 0x00" - need patching safe padding
                    emitByte(0x66); // size prefix
                    emitByte(0x66); // size prefix
                    emitByte(0x90); // nop
                    break;
                case 2:
                    emitByte(0x66); // size prefix
                    emitByte(0x90); // nop
                    break;
                case 1:
                    emitByte(0x90); // nop
                    break;
                default:
                    assert i == 0;
            }
            return;
        }

        // Using nops with size prefixes "0x66 0x90".
        // From AMD Optimization Guide:
        // 1: 0x90
        // 2: 0x66 0x90
        // 3: 0x66 0x66 0x90
        // 4: 0x66 0x66 0x66 0x90
        // 5: 0x66 0x66 0x90 0x66 0x90
        // 6: 0x66 0x66 0x90 0x66 0x66 0x90
        // 7: 0x66 0x66 0x66 0x90 0x66 0x66 0x90
        // 8: 0x66 0x66 0x66 0x90 0x66 0x66 0x66 0x90
        // 9: 0x66 0x66 0x90 0x66 0x66 0x90 0x66 0x66 0x90
        // 10: 0x66 0x66 0x66 0x90 0x66 0x66 0x90 0x66 0x66 0x90
        //
        while (i > 12) {
            i -= 4;
            emitByte(0x66); // size prefix
            emitByte(0x66);
            emitByte(0x66);
            emitByte(0x90); // nop
        }
        // 1 - 12 nops
        if (i > 8) {
            if (i > 9) {
                i -= 1;
                emitByte(0x66);
            }
            i -= 3;
            emitByte(0x66);
            emitByte(0x66);
            emitByte(0x90);
        }
        // 1 - 8 nops
        if (i > 4) {
            if (i > 6) {
                i -= 1;
                emitByte(0x66);
            }
            i -= 3;
            emitByte(0x66);
            emitByte(0x66);
            emitByte(0x90);
        }
        switch (i) {
            case 4:
                emitByte(0x66);
                emitByte(0x66);
                emitByte(0x66);
                emitByte(0x90);
                break;
            case 3:
                emitByte(0x66);
                emitByte(0x66);
                emitByte(0x90);
                break;
            case 2:
                emitByte(0x66);
                emitByte(0x90);
                break;
            case 1:
                emitByte(0x90);
                break;
            default:
                assert i == 0;
        }
    }

    public final void orl(Register dst, Register src) {
        OR.rmOp.emit(this, DWORD, dst, src);
    }

    public final void orl(Register dst, int imm32) {
        OR.getMIOpcode(DWORD, isByte(imm32)).emit(this, DWORD, dst, imm32);
    }

    // Insn: VPACKUSWB xmm1, xmm2, xmm3/m128
    // -----
    // Insn: VPACKUSWB xmm1, xmm1, xmm2

    public final void packuswb(Register dst, Register src) {
        assert inRC(XMM, dst) && inRC(XMM, src);
        // Code: VEX.NDS.128.66.0F.WIG 67 /r
        simdPrefix(dst, dst, src, PD, P_0F, false);
        emitByte(0x67);
        emitModRM(dst, src);
    }

    public final void pop(Register dst) {
        prefix(dst);
        emitByte(0x58 + encode(dst));
    }

    public void popfq() {
        emitByte(0x9D);
    }

    public final void ptest(Register dst, Register src) {
        assert supports(CPUFeature.SSE4_1);
        assert inRC(XMM, dst) && inRC(XMM, src);
        simdPrefix(dst, Register.None, src, PD, P_0F38, false);
        emitByte(0x17);
        emitModRM(dst, src);
    }

    public final void pcmpeqb(Register dst, Register src) {
        assert supports(CPUFeature.SSE2);
        assert inRC(XMM, dst) && inRC(XMM, src);
        simdPrefix(dst, dst, src, PD, P_0F, false);
        emitByte(0x74);
        emitModRM(dst, src);
    }

    public final void pcmpeqw(Register dst, Register src) {
        assert supports(CPUFeature.SSE2);
        assert inRC(XMM, dst) && inRC(XMM, src);
        simdPrefix(dst, dst, src, PD, P_0F, false);
        emitByte(0x75);
        emitModRM(dst, src);
    }

    public final void pcmpeqd(Register dst, Register src) {
        assert supports(CPUFeature.SSE2);
        assert dst.getRegisterCategory().equals(XMM) && src.getRegisterCategory().equals(XMM);
        simdPrefix(dst, dst, src, PD, P_0F, false);
        emitByte(0x76);
        emitModRM(dst, src);
    }

    public final void pcmpestri(Register dst, AMD64Address src, int imm8) {
        assert supports(CPUFeature.SSE4_2);
        assert inRC(XMM, dst);
        simdPrefix(dst, Register.None, src, PD, P_0F3A, false);
        emitByte(0x61);
        emitOperandHelper(dst, src, 0);
        emitByte(imm8);
    }

    public final void pcmpestri(Register dst, Register src, int imm8) {
        assert supports(CPUFeature.SSE4_2);
        assert inRC(XMM, dst) && inRC(XMM, src);
        simdPrefix(dst, Register.None, src, PD, P_0F3A, false);
        emitByte(0x61);
        emitModRM(dst, src);
        emitByte(imm8);
    }

    public final void pmovmskb(Register dst, Register src) {
        assert supports(CPUFeature.SSE2);
        assert inRC(CPU, dst) && inRC(XMM, src);
        simdPrefix(dst, Register.None, src, PD, P_0F, false);
        emitByte(0xD7);
        emitModRM(dst, src);
    }

    // Insn: VPMOVZXBW xmm1, xmm2/m64

    public final void pmovzxbw(Register dst, AMD64Address src) {
        assert supports(CPUFeature.SSE4_1);
        assert inRC(XMM, dst);
        simdPrefix(dst, Register.None, src, PD, P_0F38, false);
        emitByte(0x30);
        emitOperandHelper(dst, src, 0);
    }

    public final void pmovzxbw(Register dst, Register src) {
        assert supports(CPUFeature.SSE4_1);
        assert inRC(XMM, dst) && inRC(XMM, src);
        simdPrefix(dst, Register.None, src, PD, P_0F38, false);
        emitByte(0x30);
        emitModRM(dst, src);
    }

    public final void push(Register src) {
        prefix(src);
        emitByte(0x50 + encode(src));
    }

    public void pushfq() {
        emitByte(0x9c);
    }

    public final void paddd(Register dst, Register src) {
        assert inRC(XMM, dst) && inRC(XMM, src);
        simdPrefix(dst, dst, src, PD, P_0F, false);
        emitByte(0xFE);
        emitModRM(dst, src);
    }

    public final void paddq(Register dst, Register src) {
        assert inRC(XMM, dst) && inRC(XMM, src);
        simdPrefix(dst, dst, src, PD, P_0F, false);
        emitByte(0xD4);
        emitModRM(dst, src);
    }

    public final void pextrw(Register dst, Register src, int imm8) {
        assert inRC(CPU, dst) && inRC(XMM, src);
        simdPrefix(dst, Register.None, src, PD, P_0F, false);
        emitByte(0xC5);
        emitModRM(dst, src);
        emitByte(imm8);
    }

    public final void pinsrw(Register dst, Register src, int imm8) {
        assert inRC(XMM, dst) && inRC(CPU, src);
        simdPrefix(dst, dst, src, PD, P_0F, false);
        emitByte(0xC4);
        emitModRM(dst, src);
        emitByte(imm8);
    }

    public final void por(Register dst, Register src) {
        assert inRC(XMM, dst) && inRC(XMM, src);
        simdPrefix(dst, dst, src, PD, P_0F, false);
        emitByte(0xEB);
        emitModRM(dst, src);
    }

    public final void pand(Register dst, Register src) {
        assert inRC(XMM, dst) && inRC(XMM, src);
        simdPrefix(dst, dst, src, PD, P_0F, false);
        emitByte(0xDB);
        emitModRM(dst, src);
    }

    public final void pxor(Register dst, Register src) {
        assert inRC(XMM, dst) && inRC(XMM, src);
        simdPrefix(dst, dst, src, PD, P_0F, false);
        emitByte(0xEF);
        emitModRM(dst, src);
    }

    public final void pslld(Register dst, int imm8) {
        assert isUByte(imm8) : "invalid value";
        assert inRC(XMM, dst);
        // XMM6 is for /6 encoding: 66 0F 72 /6 ib
        simdPrefix(AMD64.xmm6, dst, dst, PD, P_0F, false);
        emitByte(0x72);
        emitModRM(6, dst);
        emitByte(imm8 & 0xFF);
    }

    public final void psllq(Register dst, Register shift) {
        assert inRC(XMM, dst) && inRC(XMM, shift);
        simdPrefix(dst, dst, shift, PD, P_0F, false);
        emitByte(0xF3);
        emitModRM(dst, shift);
    }

    public final void psllq(Register dst, int imm8) {
        assert isUByte(imm8) : "invalid value";
        assert inRC(XMM, dst);
        // XMM6 is for /6 encoding: 66 0F 73 /6 ib
        simdPrefix(AMD64.xmm6, dst, dst, PD, P_0F, false);
        emitByte(0x73);
        emitModRM(6, dst);
        emitByte(imm8);
    }

    public final void psrad(Register dst, int imm8) {
        assert isUByte(imm8) : "invalid value";
        assert inRC(XMM, dst);
        // XMM4 is for /4 encoding: 66 0F 72 /4 ib
        simdPrefix(AMD64.xmm4, dst, dst, PD, P_0F, false);
        emitByte(0x72);
        emitModRM(4, dst);
        emitByte(imm8);
    }

    public final void psrld(Register dst, int imm8) {
        assert isUByte(imm8) : "invalid value";
        assert inRC(XMM, dst);
        // XMM2 is for /2 encoding: 66 0F 72 /2 ib
        simdPrefix(AMD64.xmm2, dst, dst, PD, P_0F, false);
        emitByte(0x72);
        emitModRM(2, dst);
        emitByte(imm8);
    }

    public final void psrlq(Register dst, int imm8) {
        assert isUByte(imm8) : "invalid value";
        assert inRC(XMM, dst);
        // XMM2 is for /2 encoding: 66 0F 73 /2 ib
        simdPrefix(AMD64.xmm2, dst, dst, PD, P_0F, false);
        emitByte(0x73);
        emitModRM(2, dst);
        emitByte(imm8);
    }

    public final void psrldq(Register dst, int imm8) {
        assert isUByte(imm8) : "invalid value";
        assert inRC(XMM, dst);
        simdPrefix(AMD64.xmm3, dst, dst, PD, P_0F, false);
        emitByte(0x73);
        emitModRM(3, dst);
        emitByte(imm8);
    }

    public final void pshufb(Register dst, Register src) {
        assert supports(CPUFeature.SSSE3);
        assert inRC(XMM, dst) && inRC(XMM, src);
        simdPrefix(dst, dst, src, PD, P_0F38, false);
        emitByte(0x00);
        emitModRM(dst, src);
    }

    public final void pshuflw(Register dst, Register src, int imm8) {
        assert supports(CPUFeature.SSE2);
        assert isUByte(imm8) : "invalid value";
        assert inRC(XMM, dst) && inRC(XMM, src);
        simdPrefix(dst, Register.None, src, SD, P_0F, false);
        emitByte(0x70);
        emitModRM(dst, src);
        emitByte(imm8);
    }

    public final void pshufd(Register dst, Register src, int imm8) {
        assert isUByte(imm8) : "invalid value";
        assert inRC(XMM, dst) && inRC(XMM, src);
        simdPrefix(dst, Register.None, src, PD, P_0F, false);
        emitByte(0x70);
        emitModRM(dst, src);
        emitByte(imm8);
    }

    public final void psubd(Register dst, Register src) {
        assert inRC(XMM, dst) && inRC(XMM, src);
        simdPrefix(dst, dst, src, PD, P_0F, false);
        emitByte(0xFA);
        emitModRM(dst, src);
    }

    public final void punpcklbw(Register dst, Register src) {
        assert supports(CPUFeature.SSE2);
        assert inRC(XMM, dst) && inRC(XMM, src);
        simdPrefix(dst, dst, src, PD, P_0F, false);
        emitByte(0x60);
        emitModRM(dst, src);
    }

    public final void rcpps(Register dst, Register src) {
        assert inRC(XMM, dst) && inRC(XMM, src);
        simdPrefix(dst, Register.None, src, PS, P_0F, false);
        emitByte(0x53);
        emitModRM(dst, src);
    }

    public final void ret(int imm16) {
        if (imm16 == 0) {
            emitByte(0xC3);
        } else {
            emitByte(0xC2);
            emitShort(imm16);
        }
    }

    public final void sarl(Register dst, int imm8) {
        prefix(dst);
        assert isShiftCount(imm8 >> 1) : "illegal shift count";
        if (imm8 == 1) {
            emitByte(0xD1);
            emitModRM(7, dst);
        } else {
            emitByte(0xC1);
            emitModRM(7, dst);
            emitByte(imm8);
        }
    }

    public final void shll(Register dst, int imm8) {
        assert isShiftCount(imm8 >> 1) : "illegal shift count";
        prefix(dst);
        if (imm8 == 1) {
            emitByte(0xD1);
            emitModRM(4, dst);
        } else {
            emitByte(0xC1);
            emitModRM(4, dst);
            emitByte(imm8);
        }
    }

    public final void shll(Register dst) {
        // Multiply dst by 2, CL times.
        prefix(dst);
        emitByte(0xD3);
        emitModRM(4, dst);
    }

    // Insn: SHLX r32a, r/m32, r32b

    public final void shlxl(Register dst, Register src1, Register src2) {
        VexGeneralPurposeRMVOp.SHLX.emit(this, AVXSize.DWORD, dst, src1, src2);
    }

    public final void shrl(Register dst, int imm8) {
        assert isShiftCount(imm8 >> 1) : "illegal shift count";
        prefix(dst);
        emitByte(0xC1);
        emitModRM(5, dst);
        emitByte(imm8);
    }

    public final void shrl(Register dst) {
        // Unsigned divide dst by 2, CL times.
        prefix(dst);
        emitByte(0xD3);
        emitModRM(5, dst);
    }

    public final void subl(AMD64Address dst, int imm32) {
        SUB.getMIOpcode(DWORD, isByte(imm32)).emit(this, DWORD, dst, imm32);
    }

    public final void subl(Register dst, int imm32) {
        SUB.getMIOpcode(DWORD, isByte(imm32)).emit(this, DWORD, dst, imm32);
    }

    public final void subl(Register dst, Register src) {
        SUB.rmOp.emit(this, DWORD, dst, src);
    }

    public final void subpd(Register dst, Register src) {
        SSEOp.SUB.emit(this, PD, dst, src);
    }

    public final void subsd(Register dst, Register src) {
        SSEOp.SUB.emit(this, SD, dst, src);
    }

    public final void subsd(Register dst, AMD64Address src) {
        SSEOp.SUB.emit(this, SD, dst, src);
    }

    public final void testl(Register dst, int imm32) {
        // not using emitArith because test
        // doesn't support sign-extension of
        // 8bit operands
        if (dst.encoding == 0) {
            emitByte(0xA9);
        } else {
            prefix(dst);
            emitByte(0xF7);
            emitModRM(0, dst);
        }
        emitInt(imm32);
    }

    public final void testl(Register dst, Register src) {
        prefix(dst, src);
        emitByte(0x85);
        emitModRM(dst, src);
    }

    public final void testl(Register dst, AMD64Address src) {
        prefix(src, dst);
        emitByte(0x85);
        emitOperandHelper(dst, src, 0);
    }

    public final void unpckhpd(Register dst, Register src) {
        assert inRC(XMM, dst) && inRC(XMM, src);
        simdPrefix(dst, dst, src, PD, P_0F, false);
        emitByte(0x15);
        emitModRM(dst, src);
    }

    public final void unpcklpd(Register dst, Register src) {
        assert inRC(XMM, dst) && inRC(XMM, src);
        simdPrefix(dst, dst, src, PD, P_0F, false);
        emitByte(0x14);
        emitModRM(dst, src);
    }

    public final void xorl(Register dst, Register src) {
        XOR.rmOp.emit(this, DWORD, dst, src);
    }

    public final void xorpd(Register dst, Register src) {
        SSEOp.XOR.emit(this, PD, dst, src);
    }

    public final void xorps(Register dst, Register src) {
        SSEOp.XOR.emit(this, PS, dst, src);
    }

    protected final void decl(Register dst) {
        // Use two-byte form (one-byte form is a REX prefix in 64-bit mode)
        prefix(dst);
        emitByte(0xFF);
        emitModRM(1, dst);
    }

    protected final void incl(Register dst) {
        // Use two-byte form (one-byte from is a REX prefix in 64-bit mode)
        prefix(dst);
        emitByte(0xFF);
        emitModRM(0, dst);
    }

    public final void addq(Register dst, int imm32) {
        ADD.getMIOpcode(QWORD, isByte(imm32)).emit(this, QWORD, dst, imm32);
    }

    public final void addq(AMD64Address dst, int imm32) {
        ADD.getMIOpcode(QWORD, isByte(imm32)).emit(this, QWORD, dst, imm32);
    }

    public final void addq(Register dst, Register src) {
        ADD.rmOp.emit(this, QWORD, dst, src);
    }

    public final void addq(AMD64Address dst, Register src) {
        ADD.mrOp.emit(this, QWORD, dst, src);
    }

    public final void andq(Register dst, int imm32) {
        AND.getMIOpcode(QWORD, isByte(imm32)).emit(this, QWORD, dst, imm32);
    }

    public final void bsrq(Register dst, Register src) {
        prefixq(dst, src);
        emitByte(0x0F);
        emitByte(0xBD);
        emitModRM(dst, src);
    }

    public final void bswapq(Register reg) {
        prefixq(reg);
        emitByte(0x0F);
        emitByte(0xC8 + encode(reg));
    }

    public final void cdqq() {
        rexw();
        emitByte(0x99);
    }

    public final void cmovq(ConditionFlag cc, Register dst, Register src) {
        prefixq(dst, src);
        emitByte(0x0F);
        emitByte(0x40 | cc.getValue());
        emitModRM(dst, src);
    }

    public final void setb(ConditionFlag cc, Register dst) {
        prefix(dst, true);
        emitByte(0x0F);
        emitByte(0x90 | cc.getValue());
        emitModRM(0, dst);
    }

    public final void cmovq(ConditionFlag cc, Register dst, AMD64Address src) {
        prefixq(src, dst);
        emitByte(0x0F);
        emitByte(0x40 | cc.getValue());
        emitOperandHelper(dst, src, 0);
    }

    public final void cmpq(Register dst, int imm32) {
        CMP.getMIOpcode(QWORD, isByte(imm32)).emit(this, QWORD, dst, imm32);
    }

    public final void cmpq(Register dst, Register src) {
        CMP.rmOp.emit(this, QWORD, dst, src);
    }

    public final void cmpq(Register dst, AMD64Address src) {
        CMP.rmOp.emit(this, QWORD, dst, src);
    }

    public final void cmpxchgq(Register reg, AMD64Address adr) {
        prefixq(adr, reg);
        emitByte(0x0F);
        emitByte(0xB1);
        emitOperandHelper(reg, adr, 0);
    }

    public final void cvtdq2pd(Register dst, Register src) {
        assert inRC(XMM, dst) && inRC(XMM, src);
        simdPrefix(dst, Register.None, src, SS, P_0F, false);
        emitByte(0xE6);
        emitModRM(dst, src);
    }

    public final void cvtsi2sdq(Register dst, Register src) {
        SSEOp.CVTSI2SD.emit(this, QWORD, dst, src);
    }

    public final void cvttsd2siq(Register dst, Register src) {
        SSEOp.CVTTSD2SI.emit(this, QWORD, dst, src);
    }

    public final void cvttpd2dq(Register dst, Register src) {
        assert inRC(XMM, dst) && inRC(XMM, src);
        simdPrefix(dst, Register.None, src, PD, P_0F, false);
        emitByte(0xE6);
        emitModRM(dst, src);
    }

    public final void decq(Register dst) {
        // Use two-byte form (one-byte form is a REX prefix in 64-bit mode)
        prefixq(dst);
        emitByte(0xFF);
        emitModRM(1, dst);
    }

    public final void decq(AMD64Address dst) {
        DEC.emit(this, QWORD, dst);
    }

    public final void imulq(Register dst, Register src) {
        prefixq(dst, src);
        emitByte(0x0F);
        emitByte(0xAF);
        emitModRM(dst, src);
    }

    public final void incq(Register dst) {
        // Don't use it directly. Use Macroincrementq() instead.
        // Use two-byte form (one-byte from is a REX prefix in 64-bit mode)
        prefixq(dst);
        emitByte(0xFF);
        emitModRM(0, dst);
    }

    public final void incq(AMD64Address dst) {
        INC.emit(this, QWORD, dst);
    }

    public final void movq(Register dst, long imm64) {
        movq(dst, imm64, false);
    }

    public final void movq(Register dst, long imm64, boolean annotateImm) {
        int insnPos = position();
        prefixq(dst);
        emitByte(0xB8 + encode(dst));
        int immPos = position();
        emitLong(imm64);
        int nextInsnPos = position();
        if (annotateImm && codePatchingAnnotationConsumer != null) {
            codePatchingAnnotationConsumer.accept(new ImmediateOperandAnnotation(insnPos, immPos, nextInsnPos - immPos, nextInsnPos));
        }
    }

    public final void movslq(Register dst, int imm32) {
        prefixq(dst);
        emitByte(0xC7);
        emitModRM(0, dst);
        emitInt(imm32);
    }

    public final void movdq(Register dst, AMD64Address src) {
        AMD64RMOp.MOVQ.emit(this, QWORD, dst, src);
    }

    public final void movdq(AMD64Address dst, Register src) {
        AMD64MROp.MOVQ.emit(this, QWORD, dst, src);
    }

    public final void movdq(Register dst, Register src) {
        if (inRC(XMM, dst) && inRC(CPU, src)) {
            AMD64RMOp.MOVQ.emit(this, QWORD, dst, src);
        } else if (inRC(XMM, src) && inRC(CPU, dst)) {
            AMD64MROp.MOVQ.emit(this, QWORD, dst, src);
        } else {
            throw new InternalError("should not reach here");
        }
    }

    public final void movdl(Register dst, Register src) {
        if (inRC(XMM, dst) && inRC(CPU, src)) {
            AMD64RMOp.MOVD.emit(this, DWORD, dst, src);
        } else if (inRC(XMM, src) && inRC(CPU, dst)) {
            AMD64MROp.MOVD.emit(this, DWORD, dst, src);
        } else {
            throw new InternalError("should not reach here");
        }
    }

    public final void movdl(Register dst, AMD64Address src) {
        AMD64RMOp.MOVD.emit(this, DWORD, dst, src);
    }

    public final void movddup(Register dst, Register src) {
        assert supports(CPUFeature.SSE3);
        assert inRC(XMM, dst) && inRC(XMM, src);
        simdPrefix(dst, Register.None, src, SD, P_0F, false);
        emitByte(0x12);
        emitModRM(dst, src);
    }

    public final void movdqu(Register dst, AMD64Address src) {
        assert inRC(XMM, dst);
        simdPrefix(dst, Register.None, src, SS, P_0F, false);
        emitByte(0x6F);
        emitOperandHelper(dst, src, 0);
    }

    public final void movdqu(Register dst, Register src) {
        assert inRC(XMM, dst) && inRC(XMM, src);
        simdPrefix(dst, Register.None, src, SS, P_0F, false);
        emitByte(0x6F);
        emitModRM(dst, src);
    }

    // Insn: VMOVDQU xmm2/m128, xmm1

    public final void movdqu(AMD64Address dst, Register src) {
        assert inRC(XMM, src);
        // Code: VEX.128.F3.0F.WIG 7F /r
        simdPrefix(src, Register.None, dst, SS, P_0F, false);
        emitByte(0x7F);
        emitOperandHelper(src, dst, 0);
    }

    public final void movslq(AMD64Address dst, int imm32) {
        prefixq(dst);
        emitByte(0xC7);
        emitOperandHelper(0, dst, 4);
        emitInt(imm32);
    }

    public final void movslq(Register dst, AMD64Address src) {
        prefixq(src, dst);
        emitByte(0x63);
        emitOperandHelper(dst, src, 0);
    }

    public final void movslq(Register dst, Register src) {
        prefixq(dst, src);
        emitByte(0x63);
        emitModRM(dst, src);
    }

    public final void negq(Register dst) {
        prefixq(dst);
        emitByte(0xF7);
        emitModRM(3, dst);
    }

    public final void orq(Register dst, Register src) {
        OR.rmOp.emit(this, QWORD, dst, src);
    }

    public final void shlq(Register dst, int imm8) {
        assert isShiftCount(imm8 >> 1) : "illegal shift count";
        prefixq(dst);
        if (imm8 == 1) {
            emitByte(0xD1);
            emitModRM(4, dst);
        } else {
            emitByte(0xC1);
            emitModRM(4, dst);
            emitByte(imm8);
        }
    }

    public final void shlq(Register dst) {
        // Multiply dst by 2, CL times.
        prefixq(dst);
        emitByte(0xD3);
        emitModRM(4, dst);
    }

    public final void shrq(Register dst, int imm8) {
        assert isShiftCount(imm8 >> 1) : "illegal shift count";
        prefixq(dst);
        if (imm8 == 1) {
            emitByte(0xD1);
            emitModRM(5, dst);
        } else {
            emitByte(0xC1);
            emitModRM(5, dst);
            emitByte(imm8);
        }
    }

    public final void shrq(Register dst) {
        prefixq(dst);
        emitByte(0xD3);
        // Unsigned divide dst by 2, CL times.
        emitModRM(5, dst);
    }

    public final void sbbq(Register dst, Register src) {
        SBB.rmOp.emit(this, QWORD, dst, src);
    }

    public final void subq(Register dst, int imm32) {
        SUB.getMIOpcode(QWORD, isByte(imm32)).emit(this, QWORD, dst, imm32);
    }

    public final void subq(AMD64Address dst, int imm32) {
        SUB.getMIOpcode(QWORD, isByte(imm32)).emit(this, QWORD, dst, imm32);
    }

    public final void subqWide(Register dst, int imm32) {
        // don't use the sign-extending version, forcing a 32-bit immediate
        SUB.getMIOpcode(QWORD, false).emit(this, QWORD, dst, imm32);
    }

    public final void subq(Register dst, Register src) {
        SUB.rmOp.emit(this, QWORD, dst, src);
    }

    public final void testq(Register dst, Register src) {
        prefixq(dst, src);
        emitByte(0x85);
        emitModRM(dst, src);
    }

    public final void btrq(Register src, int imm8) {
        prefixq(src);
        emitByte(0x0F);
        emitByte(0xBA);
        emitModRM(6, src);
        emitByte(imm8);
    }

    public final void xaddb(AMD64Address dst, Register src) {
        prefixb(dst, src);
        emitByte(0x0F);
        emitByte(0xC0);
        emitOperandHelper(src, dst, 0);
    }

    public final void xaddw(AMD64Address dst, Register src) {
        emitByte(0x66); // Switch to 16-bit mode.
        prefix(dst, src);
        emitByte(0x0F);
        emitByte(0xC1);
        emitOperandHelper(src, dst, 0);
    }

    public final void xaddl(AMD64Address dst, Register src) {
        prefix(dst, src);
        emitByte(0x0F);
        emitByte(0xC1);
        emitOperandHelper(src, dst, 0);
    }

    public final void xaddq(AMD64Address dst, Register src) {
        prefixq(dst, src);
        emitByte(0x0F);
        emitByte(0xC1);
        emitOperandHelper(src, dst, 0);
    }

    public final void xchgb(Register dst, AMD64Address src) {
        prefixb(src, dst);
        emitByte(0x86);
        emitOperandHelper(dst, src, 0);
    }

    public final void xchgw(Register dst, AMD64Address src) {
        emitByte(0x66);
        prefix(src, dst);
        emitByte(0x87);
        emitOperandHelper(dst, src, 0);
    }

    public final void xchgl(Register dst, AMD64Address src) {
        prefix(src, dst);
        emitByte(0x87);
        emitOperandHelper(dst, src, 0);
    }

    public final void xchgq(Register dst, AMD64Address src) {
        prefixq(src, dst);
        emitByte(0x87);
        emitOperandHelper(dst, src, 0);
    }

    public final void membar(int barriers) {
        if (target.isMP) {
            // We only have to handle StoreLoad
            if ((barriers & STORE_LOAD) != 0) {
                // All usable chips support "locked" instructions which suffice
                // as barriers, and are much faster than the alternative of
                // using cpuid instruction. We use here a locked add [rsp],0.
                // This is conveniently otherwise a no-op except for blowing
                // flags.
                // Any change to this code may need to revisit other places in
                // the code where this idiom is used, in particular the
                // orderAccess code.
                lock();
                addl(new AMD64Address(AMD64.rsp, 0), 0); // Assert the lock# signal here
            }
        }
    }

    @Override
    protected final void patchJumpTarget(int branch, int branchTarget) {
        int op = getByte(branch);
        assert op == 0xE8 // call
                        || op == 0x00 // jump table entry
                        || op == 0xE9 // jmp
                        || op == 0xEB // short jmp
                        || (op & 0xF0) == 0x70 // short jcc
                        || op == 0x0F && (getByte(branch + 1) & 0xF0) == 0x80 // jcc
        : "Invalid opcode at patch point branch=" + branch + ", branchTarget=" + branchTarget + ", op=" + op;

        if (op == 0x00) {
            int offsetToJumpTableBase = getShort(branch + 1);
            int jumpTableBase = branch - offsetToJumpTableBase;
            int imm32 = branchTarget - jumpTableBase;
            emitInt(imm32, branch);
        } else if (op == 0xEB || (op & 0xF0) == 0x70) {

            // short offset operators (jmp and jcc)
            final int imm8 = branchTarget - (branch + 2);
            /*
             * Since a wrongly patched short branch can potentially lead to working but really bad
             * behaving code we should always fail with an exception instead of having an assert.
             */
            if (!NumUtil.isByte(imm8)) {
                throw new InternalError("branch displacement out of range: " + imm8);
            }
            emitByte(imm8, branch + 1);

        } else {

            int off = 1;
            if (op == 0x0F) {
                off = 2;
            }

            int imm32 = branchTarget - (branch + 4 + off);
            emitInt(imm32, branch + off);
        }
    }

    public void nullCheck(AMD64Address address) {
        testl(AMD64.rax, address);
    }

    @Override
    public void align(int modulus) {
        if (position() % modulus != 0) {
            nop(modulus - (position() % modulus));
        }
    }

    /**
     * Emits a direct call instruction. Note that the actual call target is not specified, because
     * all calls need patching anyway. Therefore, 0 is emitted as the call target, and the user is
     * responsible to add the call address to the appropriate patching tables.
     */
    public final void call() {
        annotatePatchingImmediate(1, 4);
        emitByte(0xE8);
        emitInt(0);
    }

    public final void call(Register src) {
        prefix(src);
        emitByte(0xFF);
        emitModRM(2, src);
    }

    public final void int3() {
        emitByte(0xCC);
    }

    public final void pause() {
        emitByte(0xF3);
        emitByte(0x90);
    }

    private void emitx87(int b1, int b2, int i) {
        assert 0 <= i && i < 8 : "illegal stack offset";
        emitByte(b1);
        emitByte(b2 + i);
    }

    public final void fldd(AMD64Address src) {
        emitByte(0xDD);
        emitOperandHelper(0, src, 0);
    }

    public final void flds(AMD64Address src) {
        emitByte(0xD9);
        emitOperandHelper(0, src, 0);
    }

    public final void fldln2() {
        emitByte(0xD9);
        emitByte(0xED);
    }

    public final void fldlg2() {
        emitByte(0xD9);
        emitByte(0xEC);
    }

    public final void fyl2x() {
        emitByte(0xD9);
        emitByte(0xF1);
    }

    public final void fstps(AMD64Address src) {
        emitByte(0xD9);
        emitOperandHelper(3, src, 0);
    }

    public final void fstpd(AMD64Address src) {
        emitByte(0xDD);
        emitOperandHelper(3, src, 0);
    }

    private void emitFPUArith(int b1, int b2, int i) {
        assert 0 <= i && i < 8 : "illegal FPU register: " + i;
        emitByte(b1);
        emitByte(b2 + i);
    }

    public void ffree(int i) {
        emitFPUArith(0xDD, 0xC0, i);
    }

    public void fincstp() {
        emitByte(0xD9);
        emitByte(0xF7);
    }

    public void fxch(int i) {
        emitFPUArith(0xD9, 0xC8, i);
    }

    public void fnstswAX() {
        emitByte(0xDF);
        emitByte(0xE0);
    }

    public void fwait() {
        emitByte(0x9B);
    }

    public void fprem() {
        emitByte(0xD9);
        emitByte(0xF8);
    }

    public final void fsin() {
        emitByte(0xD9);
        emitByte(0xFE);
    }

    public final void fcos() {
        emitByte(0xD9);
        emitByte(0xFF);
    }

    public final void fptan() {
        emitByte(0xD9);
        emitByte(0xF2);
    }

    public final void fstp(int i) {
        emitx87(0xDD, 0xD8, i);
    }

    @Override
    public AMD64Address makeAddress(Register base, int displacement) {
        return new AMD64Address(base, displacement);
    }

    @Override
    public AMD64Address getPlaceholder(int instructionStartPosition) {
        return new AMD64Address(AMD64.rip, Register.None, Scale.Times1, 0, instructionStartPosition);
    }

    private void prefetchPrefix(AMD64Address src) {
        prefix(src);
        emitByte(0x0F);
    }

    public void prefetchnta(AMD64Address src) {
        prefetchPrefix(src);
        emitByte(0x18);
        emitOperandHelper(0, src, 0);
    }

    void prefetchr(AMD64Address src) {
        assert supports(CPUFeature.AMD_3DNOW_PREFETCH);
        prefetchPrefix(src);
        emitByte(0x0D);
        emitOperandHelper(0, src, 0);
    }

    public void prefetcht0(AMD64Address src) {
        assert supports(CPUFeature.SSE);
        prefetchPrefix(src);
        emitByte(0x18);
        emitOperandHelper(1, src, 0);
    }

    public void prefetcht1(AMD64Address src) {
        assert supports(CPUFeature.SSE);
        prefetchPrefix(src);
        emitByte(0x18);
        emitOperandHelper(2, src, 0);
    }

    public void prefetcht2(AMD64Address src) {
        assert supports(CPUFeature.SSE);
        prefix(src);
        emitByte(0x0f);
        emitByte(0x18);
        emitOperandHelper(3, src, 0);
    }

    public void prefetchw(AMD64Address src) {
        assert supports(CPUFeature.AMD_3DNOW_PREFETCH);
        prefix(src);
        emitByte(0x0f);
        emitByte(0x0D);
        emitOperandHelper(1, src, 0);
    }

    public void rdtsc() {
        emitByte(0x0F);
        emitByte(0x31);
    }

    /**
     * Emits an instruction which is considered to be illegal. This is used if we deliberately want
     * to crash the program (debugging etc.).
     */
    public void illegal() {
        emitByte(0x0f);
        emitByte(0x0b);
    }

    public void lfence() {
        emitByte(0x0f);
        emitByte(0xae);
        emitByte(0xe8);
    }

    public final void vptest(Register dst, Register src) {
        VexRMOp.VPTEST.emit(this, AVXSize.YMM, dst, src);
    }

    public final void vpxor(Register dst, Register nds, Register src) {
        VexRVMOp.VPXOR.emit(this, AVXSize.YMM, dst, nds, src);
    }

    public final void vpxor(Register dst, Register nds, AMD64Address src) {
        VexRVMOp.VPXOR.emit(this, AVXSize.YMM, dst, nds, src);
    }

    public final void vmovdqu(Register dst, AMD64Address src) {
        VexMoveOp.VMOVDQU.emit(this, AVXSize.YMM, dst, src);
    }

    public final void vmovdqu(AMD64Address dst, Register src) {
        assert inRC(XMM, src);
        VexMoveOp.VMOVDQU.emit(this, AVXSize.YMM, dst, src);
    }

    public final void vpmovzxbw(Register dst, AMD64Address src) {
        assert supports(CPUFeature.AVX2);
        VexRMOp.VPMOVZXBW.emit(this, AVXSize.YMM, dst, src);
    }

    public final void vzeroupper() {
        emitVEX(L128, P_, M_0F, W0, 0, 0, true);
        emitByte(0x77);
    }

    // Insn: KORTESTD k1, k2

    // This instruction produces ZF or CF flags
    public final void kortestd(Register src1, Register src2) {
        assert supports(CPUFeature.AVX512BW);
        assert inRC(MASK, src1) && inRC(MASK, src2);
        // Code: VEX.L0.66.0F.W1 98 /r
        vexPrefix(src1, Register.None, src2, AVXSize.XMM, P_66, M_0F, W1, true);
        emitByte(0x98);
        emitModRM(src1, src2);
    }

    // Insn: KORTESTQ k1, k2

    // This instruction produces ZF or CF flags
    public final void kortestq(Register src1, Register src2) {
        assert supports(CPUFeature.AVX512BW);
        assert inRC(MASK, src1) && inRC(MASK, src2);
        // Code: VEX.L0.0F.W1 98 /r
        vexPrefix(src1, Register.None, src2, AVXSize.XMM, P_, M_0F, W1, true);
        emitByte(0x98);
        emitModRM(src1, src2);
    }

    public final void kmovd(Register dst, Register src) {
        assert supports(CPUFeature.AVX512BW);
        assert inRC(MASK, dst) || inRC(CPU, dst);
        assert inRC(MASK, src) || inRC(CPU, src);
        assert !(inRC(CPU, dst) && inRC(CPU, src));

        if (inRC(MASK, dst)) {
            if (inRC(MASK, src)) {
                // kmovd(KRegister dst, KRegister src):
                // Insn: KMOVD k1, k2/m32
                // Code: VEX.L0.66.0F.W1 90 /r
                vexPrefix(dst, Register.None, src, AVXSize.XMM, P_66, M_0F, W1, true);
                emitByte(0x90);
                emitModRM(dst, src);
            } else {
                // kmovd(KRegister dst, Register src)
                // Insn: KMOVD k1, r32
                // Code: VEX.L0.F2.0F.W0 92 /r
                vexPrefix(dst, Register.None, src, AVXSize.XMM, P_F2, M_0F, W0, true);
                emitByte(0x92);
                emitModRM(dst, src);
            }
        } else {
            if (inRC(MASK, src)) {
                // kmovd(Register dst, KRegister src)
                // Insn: KMOVD r32, k1
                // Code: VEX.L0.F2.0F.W0 93 /r
                vexPrefix(dst, Register.None, src, AVXSize.XMM, P_F2, M_0F, W0, true);
                emitByte(0x93);
                emitModRM(dst, src);
            } else {
                throw GraalError.shouldNotReachHere();
            }
        }
    }

    public final void kmovq(Register dst, Register src) {
        assert supports(CPUFeature.AVX512BW);
        assert inRC(MASK, dst) || inRC(CPU, dst);
        assert inRC(MASK, src) || inRC(CPU, src);
        assert !(inRC(CPU, dst) && inRC(CPU, src));

        if (inRC(MASK, dst)) {
            if (inRC(MASK, src)) {
                // kmovq(KRegister dst, KRegister src):
                // Insn: KMOVQ k1, k2/m64
                // Code: VEX.L0.0F.W1 90 /r
                vexPrefix(dst, Register.None, src, AVXSize.XMM, P_, M_0F, W1, true);
                emitByte(0x90);
                emitModRM(dst, src);
            } else {
                // kmovq(KRegister dst, Register src)
                // Insn: KMOVQ k1, r64
                // Code: VEX.L0.F2.0F.W1 92 /r
                vexPrefix(dst, Register.None, src, AVXSize.XMM, P_F2, M_0F, W1, true);
                emitByte(0x92);
                emitModRM(dst, src);
            }
        } else {
            if (inRC(MASK, src)) {
                // kmovq(Register dst, KRegister src)
                // Insn: KMOVQ r64, k1
                // Code: VEX.L0.F2.0F.W1 93 /r
                vexPrefix(dst, Register.None, src, AVXSize.XMM, P_F2, M_0F, W1, true);
                emitByte(0x93);
                emitModRM(dst, src);
            } else {
                throw GraalError.shouldNotReachHere();
            }
        }
    }

    // Insn: KTESTD k1, k2

    public final void ktestd(Register src1, Register src2) {
        assert supports(CPUFeature.AVX512BW);
        assert inRC(MASK, src1) && inRC(MASK, src2);
        // Code: VEX.L0.66.0F.W1 99 /r
        vexPrefix(src1, Register.None, src2, AVXSize.XMM, P_66, M_0F, W1, true);
        emitByte(0x99);
        emitModRM(src1, src2);
    }

    public final void evmovdqu64(Register dst, AMD64Address src) {
        assert supports(CPUFeature.AVX512F);
        assert inRC(XMM, dst);
        evexPrefix(dst, Register.None, Register.None, src, AVXSize.ZMM, P_F3, M_0F, W1, Z0, B0);
        emitByte(0x6F);
        emitEVEXOperandHelper(dst, src, 0, EVEXTuple.FVM.getDisp8ScalingFactor(AVXSize.ZMM));
    }

    // Insn: VPMOVZXBW zmm1, m256

    public final void evpmovzxbw(Register dst, AMD64Address src) {
        assert supports(CPUFeature.AVX512BW);
        assert inRC(XMM, dst);
        // Code: EVEX.512.66.0F38.WIG 30 /r
        evexPrefix(dst, Register.None, Register.None, src, AVXSize.ZMM, P_66, M_0F38, WIG, Z0, B0);
        emitByte(0x30);
        emitEVEXOperandHelper(dst, src, 0, EVEXTuple.HVM.getDisp8ScalingFactor(AVXSize.ZMM));
    }

    public final void evpcmpeqb(Register kdst, Register nds, AMD64Address src) {
        assert supports(CPUFeature.AVX512BW);
        assert inRC(MASK, kdst) && inRC(XMM, nds);
        evexPrefix(kdst, Register.None, nds, src, AVXSize.ZMM, P_66, M_0F, WIG, Z0, B0);
        emitByte(0x74);
        emitEVEXOperandHelper(kdst, src, 0, EVEXTuple.FVM.getDisp8ScalingFactor(AVXSize.ZMM));
    }

    // Insn: VMOVDQU16 zmm1 {k1}{z}, zmm2/m512
    // -----
    // Insn: VMOVDQU16 zmm1, m512

    public final void evmovdqu16(Register dst, AMD64Address src) {
        assert supports(CPUFeature.AVX512BW);
        assert inRC(XMM, dst);
        // Code: EVEX.512.F2.0F.W1 6F /r
        evexPrefix(dst, Register.None, Register.None, src, AVXSize.ZMM, P_F2, M_0F, W1, Z0, B0);
        emitByte(0x6F);
        emitEVEXOperandHelper(dst, src, 0, EVEXTuple.FVM.getDisp8ScalingFactor(AVXSize.ZMM));
    }

    // Insn: VMOVDQU16 zmm1, k1:z, m512

    public final void evmovdqu16(Register dst, Register mask, AMD64Address src) {
        assert supports(CPUFeature.AVX512BW);
        assert inRC(XMM, dst) && inRC(MASK, mask);
        // Code: EVEX.512.F2.0F.W1 6F /r
        evexPrefix(dst, mask, Register.None, src, AVXSize.ZMM, P_F2, M_0F, W1, Z1, B0);
        emitByte(0x6F);
        emitEVEXOperandHelper(dst, src, 0, EVEXTuple.FVM.getDisp8ScalingFactor(AVXSize.ZMM));
    }

    // Insn: VMOVDQU16 zmm2/m512 {k1}{z}, zmm1
    // -----
    // Insn: VMOVDQU16 m512, zmm1

    public final void evmovdqu16(AMD64Address dst, Register src) {
        assert supports(CPUFeature.AVX512BW);
        assert inRC(XMM, src);
        // Code: EVEX.512.F2.0F.W1 7F /r
        evexPrefix(src, Register.None, Register.None, dst, AVXSize.ZMM, P_F2, M_0F, W1, Z0, B0);
        emitByte(0x7F);
        emitEVEXOperandHelper(src, dst, 0, EVEXTuple.FVM.getDisp8ScalingFactor(AVXSize.ZMM));
    }

    // Insn: VMOVDQU16 m512, k1, zmm1

    public final void evmovdqu16(AMD64Address dst, Register mask, Register src) {
        assert supports(CPUFeature.AVX512BW);
        assert inRC(MASK, mask) && inRC(XMM, src);
        // Code: EVEX.512.F2.0F.W1 7F /r
        evexPrefix(src, mask, Register.None, dst, AVXSize.ZMM, P_F2, M_0F, W1, Z0, B0);
        emitByte(0x7F);
        emitEVEXOperandHelper(src, dst, 0, EVEXTuple.FVM.getDisp8ScalingFactor(AVXSize.ZMM));
    }

    // Insn: VPBROADCASTW zmm1 {k1}{z}, reg
    // -----
    // Insn: VPBROADCASTW zmm1, reg

    public final void evpbroadcastw(Register dst, Register src) {
        assert supports(CPUFeature.AVX512BW);
        assert inRC(XMM, dst) && inRC(CPU, src);
        // Code: EVEX.512.66.0F38.W0 7B /r
        evexPrefix(dst, Register.None, Register.None, src, AVXSize.ZMM, P_66, M_0F38, W0, Z0, B0);
        emitByte(0x7B);
        emitModRM(dst, src);
    }

    // Insn: VPCMPUW k1 {k2}, zmm2, zmm3/m512, imm8
    // -----
    // Insn: VPCMPUW k1, zmm2, zmm3, imm8

    public final void evpcmpuw(Register kdst, Register nds, Register src, int vcc) {
        assert supports(CPUFeature.AVX512BW);
        assert inRC(MASK, kdst) && inRC(XMM, nds) && inRC(XMM, src);
        // Code: EVEX.NDS.512.66.0F3A.W1 3E /r ib
        evexPrefix(kdst, Register.None, nds, src, AVXSize.ZMM, P_66, M_0F3A, W1, Z0, B0);
        emitByte(0x3E);
        emitModRM(kdst, src);
        emitByte(vcc);
    }

    // Insn: VPCMPUW k1 {k2}, zmm2, zmm3/m512, imm8
    // -----
    // Insn: VPCMPUW k1, k2, zmm2, zmm3, imm8

    public final void evpcmpuw(Register kdst, Register mask, Register nds, Register src, int vcc) {
        assert supports(CPUFeature.AVX512BW);
        assert inRC(MASK, kdst) && inRC(MASK, mask);
        assert inRC(XMM, nds) && inRC(XMM, src);
        // Code: EVEX.NDS.512.66.0F3A.W1 3E /r ib
        evexPrefix(kdst, mask, nds, src, AVXSize.ZMM, P_66, M_0F3A, W1, Z0, B0);
        emitByte(0x3E);
        emitModRM(kdst, src);
        emitByte(vcc);
    }

    // Insn: VPMOVWB ymm1/m256 {k1}{z}, zmm2
    // -----
    // Insn: VPMOVWB m256, zmm2

    public final void evpmovwb(AMD64Address dst, Register src) {
        assert supports(CPUFeature.AVX512BW);
        assert inRC(XMM, src);
        // Code: EVEX.512.F3.0F38.W0 30 /r
        evexPrefix(src, Register.None, Register.None, dst, AVXSize.ZMM, P_F3, M_0F38, W0, Z0, B0);
        emitByte(0x30);
        emitEVEXOperandHelper(src, dst, 0, EVEXTuple.HVM.getDisp8ScalingFactor(AVXSize.ZMM));
    }

    // Insn: VPMOVWB m256, k1, zmm2

    public final void evpmovwb(AMD64Address dst, Register mask, Register src) {
        assert supports(CPUFeature.AVX512BW);
        assert inRC(MASK, mask) && inRC(XMM, src);
        // Code: EVEX.512.F3.0F38.W0 30 /r
        evexPrefix(src, mask, Register.None, dst, AVXSize.ZMM, P_F3, M_0F38, W0, Z0, B0);
        emitByte(0x30);
        emitEVEXOperandHelper(src, dst, 0, EVEXTuple.HVM.getDisp8ScalingFactor(AVXSize.ZMM));
    }

    // Insn: VPMOVZXBW zmm1 {k1}{z}, ymm2/m256
    // -----
    // Insn: VPMOVZXBW zmm1, k1, m256

    public final void evpmovzxbw(Register dst, Register mask, AMD64Address src) {
        assert supports(CPUFeature.AVX512BW);
        assert inRC(MASK, mask) && inRC(XMM, dst);
        // Code: EVEX.512.66.0F38.WIG 30 /r
        evexPrefix(dst, mask, Register.None, src, AVXSize.ZMM, P_66, M_0F38, WIG, Z0, B0);
        emitByte(0x30);
        emitEVEXOperandHelper(dst, src, 0, EVEXTuple.HVM.getDisp8ScalingFactor(AVXSize.ZMM));
    }

}
