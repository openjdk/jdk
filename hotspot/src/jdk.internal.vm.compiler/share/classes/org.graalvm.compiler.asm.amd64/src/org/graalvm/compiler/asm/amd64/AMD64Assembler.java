/*
 * Copyright (c) 2009, 2016, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.asm.NumUtil.isByte;
import static org.graalvm.compiler.asm.NumUtil.isInt;
import static org.graalvm.compiler.asm.NumUtil.isShiftCount;
import static org.graalvm.compiler.asm.NumUtil.isUByte;
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
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.OperandSize.BYTE;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.OperandSize.DWORD;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.OperandSize.PD;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.OperandSize.PS;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.OperandSize.QWORD;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.OperandSize.SD;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.OperandSize.SS;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.OperandSize.WORD;
import static jdk.vm.ci.amd64.AMD64.CPU;
import static jdk.vm.ci.amd64.AMD64.XMM;
import static jdk.vm.ci.amd64.AMD64.r12;
import static jdk.vm.ci.amd64.AMD64.r13;
import static jdk.vm.ci.amd64.AMD64.rbp;
import static jdk.vm.ci.amd64.AMD64.rip;
import static jdk.vm.ci.amd64.AMD64.rsp;
import static jdk.vm.ci.code.MemoryBarriers.STORE_LOAD;

import org.graalvm.compiler.asm.Assembler;
import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.NumUtil;
import org.graalvm.compiler.asm.amd64.AMD64Address.Scale;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.Register.RegisterCategory;
import jdk.vm.ci.code.TargetDescription;

/**
 * This class implements an assembler that can encode most X86 instructions.
 */
public class AMD64Assembler extends Assembler {

    private static final int MinEncodingNeedsRex = 8;

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
     * Constants for X86 prefix bytes.
     */
    private static class Prefix {
        private static final int REX = 0x40;
        private static final int REXB = 0x41;
        private static final int REXX = 0x42;
        private static final int REXXB = 0x43;
        private static final int REXR = 0x44;
        private static final int REXRB = 0x45;
        private static final int REXRX = 0x46;
        private static final int REXRXB = 0x47;
        private static final int REXW = 0x48;
        private static final int REXWB = 0x49;
        private static final int REXWX = 0x4A;
        private static final int REXWXB = 0x4B;
        private static final int REXWR = 0x4C;
        private static final int REXWRB = 0x4D;
        private static final int REXWRX = 0x4E;
        private static final int REXWRXB = 0x4F;
        private static final int VEX_3BYTES = 0xC4;
        private static final int VEX_2BYTES = 0xC5;
    }

    private static class VexPrefix {
        private static final int VEX_R = 0x80;
        private static final int VEX_W = 0x80;
    }

    private static class AvxVectorLen {
        private static final int AVX_128bit = 0x0;
        private static final int AVX_256bit = 0x1;
    }

    private static class VexSimdPrefix {
        private static final int VEX_SIMD_NONE = 0x0;
        private static final int VEX_SIMD_66 = 0x1;
        private static final int VEX_SIMD_F3 = 0x2;
        private static final int VEX_SIMD_F2 = 0x3;
    }

    private static class VexOpcode {
        private static final int VEX_OPCODE_NONE = 0x0;
        private static final int VEX_OPCODE_0F = 0x1;
        private static final int VEX_OPCODE_0F_38 = 0x2;
        private static final int VEX_OPCODE_0F_3A = 0x3;
    }

    private AMD64InstructionAttr curAttributes;

    AMD64InstructionAttr getCurAttributes() {
        return curAttributes;
    }

    void setCurAttributes(AMD64InstructionAttr attributes) {
        curAttributes = attributes;
    }

    /**
     * The x86 operand sizes.
     */
    public enum OperandSize {
        BYTE(1) {
            @Override
            protected void emitImmediate(AMD64Assembler asm, int imm) {
                assert imm == (byte) imm;
                asm.emitByte(imm);
            }

            @Override
            protected int immediateSize() {
                return 1;
            }
        },

        WORD(2, 0x66) {
            @Override
            protected void emitImmediate(AMD64Assembler asm, int imm) {
                assert imm == (short) imm;
                asm.emitShort(imm);
            }

            @Override
            protected int immediateSize() {
                return 2;
            }
        },

        DWORD(4) {
            @Override
            protected void emitImmediate(AMD64Assembler asm, int imm) {
                asm.emitInt(imm);
            }

            @Override
            protected int immediateSize() {
                return 4;
            }
        },

        QWORD(8) {
            @Override
            protected void emitImmediate(AMD64Assembler asm, int imm) {
                asm.emitInt(imm);
            }

            @Override
            protected int immediateSize() {
                return 4;
            }
        },

        SS(4, 0xF3, true),

        SD(8, 0xF2, true),

        PS(16, true),

        PD(16, 0x66, true);

        private final int sizePrefix;

        private final int bytes;
        private final boolean xmm;

        OperandSize(int bytes) {
            this(bytes, 0);
        }

        OperandSize(int bytes, int sizePrefix) {
            this(bytes, sizePrefix, false);
        }

        OperandSize(int bytes, boolean xmm) {
            this(bytes, 0, xmm);
        }

        OperandSize(int bytes, int sizePrefix, boolean xmm) {
            this.sizePrefix = sizePrefix;
            this.bytes = bytes;
            this.xmm = xmm;
        }

        public int getBytes() {
            return bytes;
        }

        public boolean isXmmType() {
            return xmm;
        }

        /**
         * Emit an immediate of this size. Note that immediate {@link #QWORD} operands are encoded
         * as sign-extended 32-bit values.
         *
         * @param asm
         * @param imm
         */
        protected void emitImmediate(AMD64Assembler asm, int imm) {
            throw new UnsupportedOperationException();
        }

        protected int immediateSize() {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Operand size and register type constraints.
     */
    private enum OpAssertion {
        ByteAssertion(CPU, CPU, BYTE),
        IntegerAssertion(CPU, CPU, WORD, DWORD, QWORD),
        No16BitAssertion(CPU, CPU, DWORD, QWORD),
        No32BitAssertion(CPU, CPU, WORD, QWORD),
        QwordOnlyAssertion(CPU, CPU, QWORD),
        FloatingAssertion(XMM, XMM, SS, SD, PS, PD),
        PackedFloatingAssertion(XMM, XMM, PS, PD),
        SingleAssertion(XMM, XMM, SS),
        DoubleAssertion(XMM, XMM, SD),
        PackedDoubleAssertion(XMM, XMM, PD),
        IntToFloatingAssertion(XMM, CPU, DWORD, QWORD),
        FloatingToIntAssertion(CPU, XMM, DWORD, QWORD);

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

    public abstract static class OperandDataAnnotation extends CodeAnnotation {
        /**
         * The position (bytes from the beginning of the method) of the operand.
         */
        public final int operandPosition;
        /**
         * The size of the operand, in bytes.
         */
        public final int operandSize;
        /**
         * The position (bytes from the beginning of the method) of the next instruction. On AMD64,
         * RIP-relative operands are relative to this position.
         */
        public final int nextInstructionPosition;

        OperandDataAnnotation(int instructionPosition, int operandPosition, int operandSize, int nextInstructionPosition) {
            super(instructionPosition);

            this.operandPosition = operandPosition;
            this.operandSize = operandSize;
            this.nextInstructionPosition = nextInstructionPosition;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " instruction [" + instructionPosition + ", " + nextInstructionPosition + "[ operand at " + operandPosition + " size " + operandSize;
        }
    }

    /**
     * Annotation that stores additional information about the displacement of a
     * {@link Assembler#getPlaceholder placeholder address} that needs patching.
     */
    public static class AddressDisplacementAnnotation extends OperandDataAnnotation {
        AddressDisplacementAnnotation(int instructionPosition, int operandPosition, int operndSize, int nextInstructionPosition) {
            super(instructionPosition, operandPosition, operndSize, nextInstructionPosition);
        }
    }

    /**
     * Annotation that stores additional information about the immediate operand, e.g., of a call
     * instruction, that needs patching.
     */
    public static class ImmediateOperandAnnotation extends OperandDataAnnotation {
        ImmediateOperandAnnotation(int instructionPosition, int operandPosition, int operndSize, int nextInstructionPosition) {
            super(instructionPosition, operandPosition, operndSize, nextInstructionPosition);
        }
    }

    /**
     * Constructs an assembler for the AMD64 architecture.
     */
    public AMD64Assembler(TargetDescription target) {
        super(target);
    }

    public boolean supports(CPUFeature feature) {
        return ((AMD64) target.arch).getFeatures().contains(feature);
    }

    private static int encode(Register r) {
        assert r.encoding < 16 && r.encoding >= 0 : "encoding out of range: " + r.encoding;
        return r.encoding & 0x7;
    }

    /**
     * Get RXB bits for register-register instruction. In that encoding, ModRM.rm contains a
     * register index. The R bit extends the ModRM.reg field and the B bit extends the ModRM.rm
     * field. The X bit must be 0.
     */
    protected static int getRXB(Register reg, Register rm) {
        int rxb = (reg == null ? 0 : reg.encoding & 0x08) >> 1;
        rxb |= (rm == null ? 0 : rm.encoding & 0x08) >> 3;
        return rxb;
    }

    /**
     * Get RXB bits for register-memory instruction. The R bit extends the ModRM.reg field. There
     * are two cases for the memory operand:<br>
     * ModRM.rm contains the base register: In that case, B extends the ModRM.rm field and X = 0.
     * <br>
     * There is an SIB byte: In that case, X extends SIB.index and B extends SIB.base.
     */
    protected static int getRXB(Register reg, AMD64Address rm) {
        int rxb = (reg == null ? 0 : reg.encoding & 0x08) >> 1;
        if (!rm.getIndex().equals(Register.None)) {
            rxb |= (rm.getIndex().encoding & 0x08) >> 2;
        }
        if (!rm.getBase().equals(Register.None)) {
            rxb |= (rm.getBase().encoding & 0x08) >> 3;
        }
        return rxb;
    }

    /**
     * Emit the ModR/M byte for one register operand and an opcode extension in the R field.
     * <p>
     * Format: [ 11 reg r/m ]
     */
    protected void emitModRM(int reg, Register rm) {
        assert (reg & 0x07) == reg;
        emitByte(0xC0 | (reg << 3) | (rm.encoding & 0x07));
    }

    /**
     * Emit the ModR/M byte for two register operands.
     * <p>
     * Format: [ 11 reg r/m ]
     */
    protected void emitModRM(Register reg, Register rm) {
        emitModRM(reg.encoding & 0x07, rm);
    }

    protected void emitOperandHelper(Register reg, AMD64Address addr, int additionalInstructionSize) {
        assert !reg.equals(Register.None);
        emitOperandHelper(encode(reg), addr, false, additionalInstructionSize);
    }

    /**
     * Emits the ModR/M byte and optionally the SIB byte for one register and one memory operand.
     *
     * @param force4Byte use 4 byte encoding for displacements that would normally fit in a byte
     */
    protected void emitOperandHelper(Register reg, AMD64Address addr, boolean force4Byte, int additionalInstructionSize) {
        assert !reg.equals(Register.None);
        emitOperandHelper(encode(reg), addr, force4Byte, additionalInstructionSize);
    }

    protected void emitOperandHelper(int reg, AMD64Address addr, int additionalInstructionSize) {
        emitOperandHelper(reg, addr, false, additionalInstructionSize);
    }

    /**
     * Emits the ModR/M byte and optionally the SIB byte for one memory operand and an opcode
     * extension in the R field.
     *
     * @param force4Byte use 4 byte encoding for displacements that would normally fit in a byte
     * @param additionalInstructionSize the number of bytes that will be emitted after the operand,
     *            so that the start position of the next instruction can be computed even though
     *            this instruction has not been completely emitted yet.
     */
    protected void emitOperandHelper(int reg, AMD64Address addr, boolean force4Byte, int additionalInstructionSize) {
        assert (reg & 0x07) == reg;
        int regenc = reg << 3;

        Register base = addr.getBase();
        Register index = addr.getIndex();

        AMD64Address.Scale scale = addr.getScale();
        int disp = addr.getDisplacement();

        if (base.equals(AMD64.rip)) { // also matches addresses returned by getPlaceholder()
            // [00 000 101] disp32
            assert index.equals(Register.None) : "cannot use RIP relative addressing with index register";
            emitByte(0x05 | regenc);
            if (codePatchingAnnotationConsumer != null && addr.instructionStartPosition >= 0) {
                codePatchingAnnotationConsumer.accept(new AddressDisplacementAnnotation(addr.instructionStartPosition, position(), 4, position() + 4 + additionalInstructionSize));
            }
            emitInt(disp);
        } else if (base.isValid()) {
            int baseenc = base.isValid() ? encode(base) : 0;
            if (index.isValid()) {
                int indexenc = encode(index) << 3;
                // [base + indexscale + disp]
                if (disp == 0 && !base.equals(rbp) && !base.equals(r13)) {
                    // [base + indexscale]
                    // [00 reg 100][ss index base]
                    assert !index.equals(rsp) : "illegal addressing mode";
                    emitByte(0x04 | regenc);
                    emitByte(scale.log2 << 6 | indexenc | baseenc);
                } else if (isByte(disp) && !force4Byte) {
                    // [base + indexscale + imm8]
                    // [01 reg 100][ss index base] imm8
                    assert !index.equals(rsp) : "illegal addressing mode";
                    emitByte(0x44 | regenc);
                    emitByte(scale.log2 << 6 | indexenc | baseenc);
                    emitByte(disp & 0xFF);
                } else {
                    // [base + indexscale + disp32]
                    // [10 reg 100][ss index base] disp32
                    assert !index.equals(rsp) : "illegal addressing mode";
                    emitByte(0x84 | regenc);
                    emitByte(scale.log2 << 6 | indexenc | baseenc);
                    emitInt(disp);
                }
            } else if (base.equals(rsp) || base.equals(r12)) {
                // [rsp + disp]
                if (disp == 0) {
                    // [rsp]
                    // [00 reg 100][00 100 100]
                    emitByte(0x04 | regenc);
                    emitByte(0x24);
                } else if (isByte(disp) && !force4Byte) {
                    // [rsp + imm8]
                    // [01 reg 100][00 100 100] disp8
                    emitByte(0x44 | regenc);
                    emitByte(0x24);
                    emitByte(disp & 0xFF);
                } else {
                    // [rsp + imm32]
                    // [10 reg 100][00 100 100] disp32
                    emitByte(0x84 | regenc);
                    emitByte(0x24);
                    emitInt(disp);
                }
            } else {
                // [base + disp]
                assert !base.equals(rsp) && !base.equals(r12) : "illegal addressing mode";
                if (disp == 0 && !base.equals(rbp) && !base.equals(r13)) {
                    // [base]
                    // [00 reg base]
                    emitByte(0x00 | regenc | baseenc);
                } else if (isByte(disp) && !force4Byte) {
                    // [base + disp8]
                    // [01 reg base] disp8
                    emitByte(0x40 | regenc | baseenc);
                    emitByte(disp & 0xFF);
                } else {
                    // [base + disp32]
                    // [10 reg base] disp32
                    emitByte(0x80 | regenc | baseenc);
                    emitInt(disp);
                }
            }
        } else {
            if (index.isValid()) {
                int indexenc = encode(index) << 3;
                // [indexscale + disp]
                // [00 reg 100][ss index 101] disp32
                assert !index.equals(rsp) : "illegal addressing mode";
                emitByte(0x04 | regenc);
                emitByte(scale.log2 << 6 | indexenc | 0x05);
                emitInt(disp);
            } else {
                // [disp] ABSOLUTE
                // [00 reg 100][00 100 101] disp32
                emitByte(0x04 | regenc);
                emitByte(0x25);
                emitInt(disp);
            }
        }
        setCurAttributes(null);
    }

    /**
     * Base class for AMD64 opcodes.
     */
    public static class AMD64Op {

        protected static final int P_0F = 0x0F;
        protected static final int P_0F38 = 0x380F;
        protected static final int P_0F3A = 0x3A0F;

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
            if (size.sizePrefix != 0) {
                asm.emitByte(size.sizePrefix);
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
            super(opcode, 0, prefix, op, assertion, null);
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
                return size.bytes;
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
     * Opcode with operand order of either RM or MR for 3 address forms.
     */
    public abstract static class AMD64RRROp extends AMD64Op {

        protected AMD64RRROp(String opcode, int prefix1, int prefix2, int op, OpAssertion assertion, CPUFeature feature) {
            super(opcode, prefix1, prefix2, op, assertion, feature);
        }

        protected AMD64RRROp(String opcode, int prefix1, int prefix2, int op, boolean dstIsByte, boolean srcIsByte, OpAssertion assertion, CPUFeature feature) {
            super(opcode, prefix1, prefix2, op, dstIsByte, srcIsByte, assertion, feature);
        }

        public abstract void emit(AMD64Assembler asm, OperandSize size, Register dst, Register nds, Register src);
    }

    /**
     * Opcode with operand order of RM.
     */
    public static class AMD64RMOp extends AMD64RROp {
        // @formatter:off
        public static final AMD64RMOp IMUL   = new AMD64RMOp("IMUL",         P_0F, 0xAF);
        public static final AMD64RMOp BSF    = new AMD64RMOp("BSF",          P_0F, 0xBC);
        public static final AMD64RMOp BSR    = new AMD64RMOp("BSR",          P_0F, 0xBD);
        public static final AMD64RMOp POPCNT = new AMD64RMOp("POPCNT", 0xF3, P_0F, 0xB8, CPUFeature.POPCNT);
        public static final AMD64RMOp TZCNT  = new AMD64RMOp("TZCNT",  0xF3, P_0F, 0xBC, CPUFeature.BMI1);
        public static final AMD64RMOp LZCNT  = new AMD64RMOp("LZCNT",  0xF3, P_0F, 0xBD, CPUFeature.LZCNT);
        public static final AMD64RMOp MOVZXB = new AMD64RMOp("MOVZXB",       P_0F, 0xB6, false, true, OpAssertion.IntegerAssertion);
        public static final AMD64RMOp MOVZX  = new AMD64RMOp("MOVZX",        P_0F, 0xB7, OpAssertion.No16BitAssertion);
        public static final AMD64RMOp MOVSXB = new AMD64RMOp("MOVSXB",       P_0F, 0xBE, false, true, OpAssertion.IntegerAssertion);
        public static final AMD64RMOp MOVSX  = new AMD64RMOp("MOVSX",        P_0F, 0xBF, OpAssertion.No16BitAssertion);
        public static final AMD64RMOp MOVSXD = new AMD64RMOp("MOVSXD",             0x63, OpAssertion.QwordOnlyAssertion);
        public static final AMD64RMOp MOVB   = new AMD64RMOp("MOVB",               0x8A, OpAssertion.ByteAssertion);
        public static final AMD64RMOp MOV    = new AMD64RMOp("MOV",                0x8B);

        // MOVD/MOVQ and MOVSS/MOVSD are the same opcode, just with different operand size prefix
        public static final AMD64RMOp MOVD   = new AMD64RMOp("MOVD",   0x66, P_0F, 0x6E, OpAssertion.IntToFloatingAssertion, CPUFeature.SSE2);
        public static final AMD64RMOp MOVQ   = new AMD64RMOp("MOVQ",   0x66, P_0F, 0x6E, OpAssertion.IntToFloatingAssertion, CPUFeature.SSE2);
        public static final AMD64RMOp MOVSS  = new AMD64RMOp("MOVSS",        P_0F, 0x10, OpAssertion.FloatingAssertion, CPUFeature.SSE);
        public static final AMD64RMOp MOVSD  = new AMD64RMOp("MOVSD",        P_0F, 0x10, OpAssertion.FloatingAssertion, CPUFeature.SSE);

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
            this(opcode, prefix1, prefix2, op, OpAssertion.IntegerAssertion, feature);
        }

        protected AMD64RMOp(String opcode, int prefix1, int prefix2, int op, OpAssertion assertion, CPUFeature feature) {
            super(opcode, prefix1, prefix2, op, assertion, feature);
        }

        @Override
        public final void emit(AMD64Assembler asm, OperandSize size, Register dst, Register src) {
            assert verify(asm, size, dst, src);
            boolean isSimd = false;
            boolean noNds = false;

            switch (op) {
                case 0x2A:
                case 0x2C:
                case 0x2E:
                case 0x5A:
                case 0x6E:
                    isSimd = true;
                    noNds = true;
                    break;
                case 0x10:
                case 0x51:
                case 0x54:
                case 0x55:
                case 0x56:
                case 0x57:
                case 0x58:
                case 0x59:
                case 0x5C:
                case 0x5D:
                case 0x5E:
                case 0x5F:
                    isSimd = true;
                    break;
            }

            if (isSimd) {
                int pre;
                int opc;
                boolean rexVexW = (size == QWORD) ? true : false;
                AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, rexVexW, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, asm.target);
                int curPrefix = size.sizePrefix | prefix1;
                switch (curPrefix) {
                    case 0x66:
                        pre = VexSimdPrefix.VEX_SIMD_66;
                        break;
                    case 0xF2:
                        pre = VexSimdPrefix.VEX_SIMD_F2;
                        break;
                    case 0xF3:
                        pre = VexSimdPrefix.VEX_SIMD_F3;
                        break;
                    default:
                        pre = VexSimdPrefix.VEX_SIMD_NONE;
                        break;
                }
                switch (prefix2) {
                    case P_0F:
                        opc = VexOpcode.VEX_OPCODE_0F;
                        break;
                    case P_0F38:
                        opc = VexOpcode.VEX_OPCODE_0F_38;
                        break;
                    case P_0F3A:
                        opc = VexOpcode.VEX_OPCODE_0F_3A;
                        break;
                    default:
                        opc = VexOpcode.VEX_OPCODE_NONE;
                        break;
                }
                int encode;
                if (noNds) {
                    encode = asm.simdPrefixAndEncode(dst, Register.None, src, pre, opc, attributes);
                } else {
                    encode = asm.simdPrefixAndEncode(dst, dst, src, pre, opc, attributes);
                }
                asm.emitByte(op);
                asm.emitByte(0xC0 | encode);
            } else {
                emitOpcode(asm, size, getRXB(dst, src), dst.encoding, src.encoding);
                asm.emitModRM(dst, src);
            }
        }

        public final void emit(AMD64Assembler asm, OperandSize size, Register dst, AMD64Address src) {
            assert verify(asm, size, dst, null);
            boolean isSimd = false;
            boolean noNds = false;

            switch (op) {
                case 0x10:
                case 0x2A:
                case 0x2C:
                case 0x2E:
                case 0x6E:
                    isSimd = true;
                    noNds = true;
                    break;
                case 0x51:
                case 0x54:
                case 0x55:
                case 0x56:
                case 0x57:
                case 0x58:
                case 0x59:
                case 0x5C:
                case 0x5D:
                case 0x5E:
                case 0x5F:
                    isSimd = true;
                    break;
            }

            if (isSimd) {
                int pre;
                int opc;
                boolean rexVexW = (size == QWORD) ? true : false;
                AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, rexVexW, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, asm.target);
                int curPrefix = size.sizePrefix | prefix1;
                switch (curPrefix) {
                    case 0x66:
                        pre = VexSimdPrefix.VEX_SIMD_66;
                        break;
                    case 0xF2:
                        pre = VexSimdPrefix.VEX_SIMD_F2;
                        break;
                    case 0xF3:
                        pre = VexSimdPrefix.VEX_SIMD_F3;
                        break;
                    default:
                        pre = VexSimdPrefix.VEX_SIMD_NONE;
                        break;
                }
                switch (prefix2) {
                    case P_0F:
                        opc = VexOpcode.VEX_OPCODE_0F;
                        break;
                    case P_0F38:
                        opc = VexOpcode.VEX_OPCODE_0F_38;
                        break;
                    case P_0F3A:
                        opc = VexOpcode.VEX_OPCODE_0F_3A;
                        break;
                    default:
                        opc = VexOpcode.VEX_OPCODE_NONE;
                        break;
                }
                if (noNds) {
                    asm.simdPrefix(dst, Register.None, src, pre, opc, attributes);
                } else {
                    asm.simdPrefix(dst, dst, src, pre, opc, attributes);
                }
                asm.emitByte(op);
                asm.emitOperandHelper(dst, src, 0);
            } else {
                emitOpcode(asm, size, getRXB(dst, src), dst.encoding, 0);
                asm.emitOperandHelper(dst, src, 0);
            }
        }
    }

    /**
     * Opcode with operand order of RM.
     */
    public static class AMD64RRMOp extends AMD64RRROp {
        protected AMD64RRMOp(String opcode, int op) {
            this(opcode, 0, op);
        }

        protected AMD64RRMOp(String opcode, int op, OpAssertion assertion) {
            this(opcode, 0, op, assertion);
        }

        protected AMD64RRMOp(String opcode, int prefix, int op) {
            this(opcode, 0, prefix, op, null);
        }

        protected AMD64RRMOp(String opcode, int prefix, int op, OpAssertion assertion) {
            this(opcode, 0, prefix, op, assertion, null);
        }

        protected AMD64RRMOp(String opcode, int prefix, int op, OpAssertion assertion, CPUFeature feature) {
            this(opcode, 0, prefix, op, assertion, feature);
        }

        protected AMD64RRMOp(String opcode, int prefix, int op, boolean dstIsByte, boolean srcIsByte, OpAssertion assertion) {
            super(opcode, 0, prefix, op, dstIsByte, srcIsByte, assertion, null);
        }

        protected AMD64RRMOp(String opcode, int prefix1, int prefix2, int op, CPUFeature feature) {
            this(opcode, prefix1, prefix2, op, OpAssertion.IntegerAssertion, feature);
        }

        protected AMD64RRMOp(String opcode, int prefix1, int prefix2, int op, OpAssertion assertion, CPUFeature feature) {
            super(opcode, prefix1, prefix2, op, assertion, feature);
        }

        @Override
        public final void emit(AMD64Assembler asm, OperandSize size, Register dst, Register nds, Register src) {
            assert verify(asm, size, dst, src);
            int pre;
            int opc;
            boolean rexVexW = (size == QWORD) ? true : false;
            AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, rexVexW, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, asm.target);
            int curPrefix = size.sizePrefix | prefix1;
            switch (curPrefix) {
                case 0x66:
                    pre = VexSimdPrefix.VEX_SIMD_66;
                    break;
                case 0xF2:
                    pre = VexSimdPrefix.VEX_SIMD_F2;
                    break;
                case 0xF3:
                    pre = VexSimdPrefix.VEX_SIMD_F3;
                    break;
                default:
                    pre = VexSimdPrefix.VEX_SIMD_NONE;
                    break;
            }
            switch (prefix2) {
                case P_0F:
                    opc = VexOpcode.VEX_OPCODE_0F;
                    break;
                case P_0F38:
                    opc = VexOpcode.VEX_OPCODE_0F_38;
                    break;
                case P_0F3A:
                    opc = VexOpcode.VEX_OPCODE_0F_3A;
                    break;
                default:
                    opc = VexOpcode.VEX_OPCODE_NONE;
                    break;
            }
            int encode;
            encode = asm.simdPrefixAndEncode(dst, nds, src, pre, opc, attributes);
            asm.emitByte(op);
            asm.emitByte(0xC0 | encode);
        }

        public final void emit(AMD64Assembler asm, OperandSize size, Register dst, Register nds, AMD64Address src) {
            assert verify(asm, size, dst, null);
            int pre;
            int opc;
            boolean rexVexW = (size == QWORD) ? true : false;
            AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, rexVexW, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, asm.target);
            int curPrefix = size.sizePrefix | prefix1;
            switch (curPrefix) {
                case 0x66:
                    pre = VexSimdPrefix.VEX_SIMD_66;
                    break;
                case 0xF2:
                    pre = VexSimdPrefix.VEX_SIMD_F2;
                    break;
                case 0xF3:
                    pre = VexSimdPrefix.VEX_SIMD_F3;
                    break;
                default:
                    pre = VexSimdPrefix.VEX_SIMD_NONE;
                    break;
            }
            switch (prefix2) {
                case P_0F:
                    opc = VexOpcode.VEX_OPCODE_0F;
                    break;
                case P_0F38:
                    opc = VexOpcode.VEX_OPCODE_0F_38;
                    break;
                case P_0F3A:
                    opc = VexOpcode.VEX_OPCODE_0F_3A;
                    break;
                default:
                    opc = VexOpcode.VEX_OPCODE_NONE;
                    break;
            }
            asm.simdPrefix(dst, nds, src, pre, opc, attributes);
            asm.emitByte(op);
            asm.emitOperandHelper(dst, src, 0);
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
        public static final AMD64MROp MOVD   = new AMD64MROp("MOVD",   0x66, P_0F, 0x7E, OpAssertion.IntToFloatingAssertion, CPUFeature.SSE2);
        public static final AMD64MROp MOVQ   = new AMD64MROp("MOVQ",   0x66, P_0F, 0x7E, OpAssertion.IntToFloatingAssertion, CPUFeature.SSE2);

        // MOVSS and MOVSD are the same opcode, just with different operand size prefix
        public static final AMD64MROp MOVSS  = new AMD64MROp("MOVSS",        P_0F, 0x11, OpAssertion.FloatingAssertion, CPUFeature.SSE);
        public static final AMD64MROp MOVSD  = new AMD64MROp("MOVSD",        P_0F, 0x11, OpAssertion.FloatingAssertion, CPUFeature.SSE);
        // @formatter:on

        protected AMD64MROp(String opcode, int op) {
            this(opcode, 0, op);
        }

        protected AMD64MROp(String opcode, int op, OpAssertion assertion) {
            this(opcode, 0, op, assertion);
        }

        protected AMD64MROp(String opcode, int prefix, int op) {
            this(opcode, prefix, op, OpAssertion.IntegerAssertion);
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
            boolean isSimd = false;
            boolean noNds = false;

            switch (op) {
                case 0x7E:
                    isSimd = true;
                    noNds = true;
                    break;
                case 0x11:
                    isSimd = true;
                    break;
            }

            if (isSimd) {
                int pre;
                int opc;
                boolean rexVexW = (size == QWORD) ? true : false;
                AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, rexVexW, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, asm.target);
                int curPrefix = size.sizePrefix | prefix1;
                switch (curPrefix) {
                    case 0x66:
                        pre = VexSimdPrefix.VEX_SIMD_66;
                        break;
                    case 0xF2:
                        pre = VexSimdPrefix.VEX_SIMD_F2;
                        break;
                    case 0xF3:
                        pre = VexSimdPrefix.VEX_SIMD_F3;
                        break;
                    default:
                        pre = VexSimdPrefix.VEX_SIMD_NONE;
                        break;
                }
                switch (prefix2) {
                    case P_0F:
                        opc = VexOpcode.VEX_OPCODE_0F;
                        break;
                    case P_0F38:
                        opc = VexOpcode.VEX_OPCODE_0F_38;
                        break;
                    case P_0F3A:
                        opc = VexOpcode.VEX_OPCODE_0F_3A;
                        break;
                    default:
                        opc = VexOpcode.VEX_OPCODE_NONE;
                        break;
                }
                int encode;
                if (noNds) {
                    encode = asm.simdPrefixAndEncode(src, Register.None, dst, pre, opc, attributes);
                } else {
                    encode = asm.simdPrefixAndEncode(src, src, dst, pre, opc, attributes);
                }
                asm.emitByte(op);
                asm.emitByte(0xC0 | encode);
            } else {
                emitOpcode(asm, size, getRXB(src, dst), src.encoding, dst.encoding);
                asm.emitModRM(src, dst);
            }
        }

        public final void emit(AMD64Assembler asm, OperandSize size, AMD64Address dst, Register src) {
            assert verify(asm, size, null, src);
            boolean isSimd = false;

            switch (op) {
                case 0x7E:
                case 0x11:
                    isSimd = true;
                    break;
            }

            if (isSimd) {
                int pre;
                int opc;
                boolean rexVexW = (size == QWORD) ? true : false;
                AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, rexVexW, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, asm.target);
                int curPrefix = size.sizePrefix | prefix1;
                switch (curPrefix) {
                    case 0x66:
                        pre = VexSimdPrefix.VEX_SIMD_66;
                        break;
                    case 0xF2:
                        pre = VexSimdPrefix.VEX_SIMD_F2;
                        break;
                    case 0xF3:
                        pre = VexSimdPrefix.VEX_SIMD_F3;
                        break;
                    default:
                        pre = VexSimdPrefix.VEX_SIMD_NONE;
                        break;
                }
                switch (prefix2) {
                    case P_0F:
                        opc = VexOpcode.VEX_OPCODE_0F;
                        break;
                    case P_0F38:
                        opc = VexOpcode.VEX_OPCODE_0F_38;
                        break;
                    case P_0F3A:
                        opc = VexOpcode.VEX_OPCODE_0F_3A;
                        break;
                    default:
                        opc = VexOpcode.VEX_OPCODE_NONE;
                        break;
                }
                asm.simdPrefix(src, Register.None, dst, pre, opc, attributes);
                asm.emitByte(op);
                asm.emitOperandHelper(src, dst, 0);
            } else {
                emitOpcode(asm, size, getRXB(src, dst), src.encoding, 0);
                asm.emitOperandHelper(src, dst, 0);
            }
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
        public static final AMD64MOp POP  = new AMD64MOp("POP",  0x8F, 0, OpAssertion.No32BitAssertion);
        // @formatter:on

        private final int ext;

        protected AMD64MOp(String opcode, int op, int ext) {
            this(opcode, 0, op, ext);
        }

        protected AMD64MOp(String opcode, int prefix, int op, int ext) {
            this(opcode, prefix, op, ext, OpAssertion.IntegerAssertion);
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
            this(opcode, immIsByte, op, ext, OpAssertion.IntegerAssertion);
        }

        protected AMD64MIOp(String opcode, boolean immIsByte, int op, int ext, OpAssertion assertion) {
            this(opcode, immIsByte, 0, op, ext, assertion);
        }

        protected AMD64MIOp(String opcode, boolean immIsByte, int prefix, int op, int ext, OpAssertion assertion) {
            super(opcode, immIsByte, prefix, op, assertion);
            this.ext = ext;
        }

        public final void emit(AMD64Assembler asm, OperandSize size, Register dst, int imm) {
            assert verify(asm, size, dst, null);
            emitOpcode(asm, size, getRXB(null, dst), 0, dst.encoding);
            asm.emitModRM(ext, dst);
            emitImmediate(asm, size, imm);
        }

        public final void emit(AMD64Assembler asm, OperandSize size, AMD64Address dst, int imm) {
            assert verify(asm, size, null, null);
            emitOpcode(asm, size, getRXB(null, dst), 0, 0);
            asm.emitOperandHelper(ext, dst, immediateSize(size));
            emitImmediate(asm, size, imm);
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
        public static final AMD64RMIOp ROUNDSS = new AMD64RMIOp("ROUNDSS", true, P_0F3A, 0x0A, OpAssertion.PackedDoubleAssertion);
        public static final AMD64RMIOp ROUNDSD = new AMD64RMIOp("ROUNDSD", true, P_0F3A, 0x0B, OpAssertion.PackedDoubleAssertion);
        // @formatter:on

        protected AMD64RMIOp(String opcode, boolean immIsByte, int op) {
            this(opcode, immIsByte, 0, op, OpAssertion.IntegerAssertion);
        }

        protected AMD64RMIOp(String opcode, boolean immIsByte, int prefix, int op, OpAssertion assertion) {
            super(opcode, immIsByte, prefix, op, assertion);
        }

        public final void emit(AMD64Assembler asm, OperandSize size, Register dst, Register src, int imm) {
            assert verify(asm, size, dst, src);
            boolean isSimd = false;
            boolean noNds = false;

            switch (op) {
                case 0x0A:
                case 0x0B:
                    isSimd = true;
                    noNds = true;
                    break;
            }

            if (isSimd) {
                int pre;
                int opc;
                AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, asm.target);
                int curPrefix = size.sizePrefix | prefix1;
                switch (curPrefix) {
                    case 0x66:
                        pre = VexSimdPrefix.VEX_SIMD_66;
                        break;
                    case 0xF2:
                        pre = VexSimdPrefix.VEX_SIMD_F2;
                        break;
                    case 0xF3:
                        pre = VexSimdPrefix.VEX_SIMD_F3;
                        break;
                    default:
                        pre = VexSimdPrefix.VEX_SIMD_NONE;
                        break;
                }
                switch (prefix2) {
                    case P_0F:
                        opc = VexOpcode.VEX_OPCODE_0F;
                        break;
                    case P_0F38:
                        opc = VexOpcode.VEX_OPCODE_0F_38;
                        break;
                    case P_0F3A:
                        opc = VexOpcode.VEX_OPCODE_0F_3A;
                        break;
                    default:
                        opc = VexOpcode.VEX_OPCODE_NONE;
                        break;
                }
                int encode;
                if (noNds) {
                    encode = asm.simdPrefixAndEncode(dst, Register.None, src, pre, opc, attributes);
                } else {
                    encode = asm.simdPrefixAndEncode(dst, dst, src, pre, opc, attributes);
                }
                asm.emitByte(op);
                asm.emitByte(0xC0 | encode);
                emitImmediate(asm, size, imm);
            } else {
                emitOpcode(asm, size, getRXB(dst, src), dst.encoding, src.encoding);
                asm.emitModRM(dst, src);
                emitImmediate(asm, size, imm);
            }
        }

        public final void emit(AMD64Assembler asm, OperandSize size, Register dst, AMD64Address src, int imm) {
            assert verify(asm, size, dst, null);

            boolean isSimd = false;
            boolean noNds = false;

            switch (op) {
                case 0x0A:
                case 0x0B:
                    isSimd = true;
                    noNds = true;
                    break;
            }

            if (isSimd) {
                int pre;
                int opc;
                AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, asm.target);
                int curPrefix = size.sizePrefix | prefix1;
                switch (curPrefix) {
                    case 0x66:
                        pre = VexSimdPrefix.VEX_SIMD_66;
                        break;
                    case 0xF2:
                        pre = VexSimdPrefix.VEX_SIMD_F2;
                        break;
                    case 0xF3:
                        pre = VexSimdPrefix.VEX_SIMD_F3;
                        break;
                    default:
                        pre = VexSimdPrefix.VEX_SIMD_NONE;
                        break;
                }
                switch (prefix2) {
                    case P_0F:
                        opc = VexOpcode.VEX_OPCODE_0F;
                        break;
                    case P_0F38:
                        opc = VexOpcode.VEX_OPCODE_0F_38;
                        break;
                    case P_0F3A:
                        opc = VexOpcode.VEX_OPCODE_0F_3A;
                        break;
                    default:
                        opc = VexOpcode.VEX_OPCODE_NONE;
                        break;
                }

                if (noNds) {
                    asm.simdPrefix(dst, Register.None, src, pre, opc, attributes);
                } else {
                    asm.simdPrefix(dst, dst, src, pre, opc, attributes);
                }
                asm.emitByte(op);
                asm.emitOperandHelper(dst, src, immediateSize(size));
                emitImmediate(asm, size, imm);
            } else {
                emitOpcode(asm, size, getRXB(dst, src), dst.encoding, 0);
                asm.emitOperandHelper(dst, src, immediateSize(size));
                emitImmediate(asm, size, imm);
            }
        }
    }

    public static class SSEOp extends AMD64RMOp {
        // @formatter:off
        public static final SSEOp CVTSI2SS  = new SSEOp("CVTSI2SS",  0xF3, P_0F, 0x2A, OpAssertion.IntToFloatingAssertion);
        public static final SSEOp CVTSI2SD  = new SSEOp("CVTSI2SS",  0xF2, P_0F, 0x2A, OpAssertion.IntToFloatingAssertion);
        public static final SSEOp CVTTSS2SI = new SSEOp("CVTTSS2SI", 0xF3, P_0F, 0x2C, OpAssertion.FloatingToIntAssertion);
        public static final SSEOp CVTTSD2SI = new SSEOp("CVTTSD2SI", 0xF2, P_0F, 0x2C, OpAssertion.FloatingToIntAssertion);
        public static final SSEOp UCOMIS    = new SSEOp("UCOMIS",          P_0F, 0x2E, OpAssertion.PackedFloatingAssertion);
        public static final SSEOp SQRT      = new SSEOp("SQRT",            P_0F, 0x51);
        public static final SSEOp AND       = new SSEOp("AND",             P_0F, 0x54, OpAssertion.PackedFloatingAssertion);
        public static final SSEOp ANDN      = new SSEOp("ANDN",            P_0F, 0x55, OpAssertion.PackedFloatingAssertion);
        public static final SSEOp OR        = new SSEOp("OR",              P_0F, 0x56, OpAssertion.PackedFloatingAssertion);
        public static final SSEOp XOR       = new SSEOp("XOR",             P_0F, 0x57, OpAssertion.PackedFloatingAssertion);
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
            this(opcode, prefix, op, OpAssertion.FloatingAssertion);
        }

        protected SSEOp(String opcode, int prefix, int op, OpAssertion assertion) {
            this(opcode, 0, prefix, op, assertion);
        }

        protected SSEOp(String opcode, int mandatoryPrefix, int prefix, int op, OpAssertion assertion) {
            super(opcode, mandatoryPrefix, prefix, op, assertion, CPUFeature.SSE2);
        }
    }

    public static class AVXOp extends AMD64RRMOp {
        // @formatter:off
        public static final AVXOp AND       = new AVXOp("AND",             P_0F, 0x54, OpAssertion.PackedFloatingAssertion);
        public static final AVXOp ANDN      = new AVXOp("ANDN",            P_0F, 0x55, OpAssertion.PackedFloatingAssertion);
        public static final AVXOp OR        = new AVXOp("OR",              P_0F, 0x56, OpAssertion.PackedFloatingAssertion);
        public static final AVXOp XOR       = new AVXOp("XOR",             P_0F, 0x57, OpAssertion.PackedFloatingAssertion);
        public static final AVXOp ADD       = new AVXOp("ADD",             P_0F, 0x58);
        public static final AVXOp MUL       = new AVXOp("MUL",             P_0F, 0x59);
        public static final AVXOp SUB       = new AVXOp("SUB",             P_0F, 0x5C);
        public static final AVXOp MIN       = new AVXOp("MIN",             P_0F, 0x5D);
        public static final AVXOp DIV       = new AVXOp("DIV",             P_0F, 0x5E);
        public static final AVXOp MAX       = new AVXOp("MAX",             P_0F, 0x5F);
        // @formatter:on

        protected AVXOp(String opcode, int prefix, int op) {
            this(opcode, prefix, op, OpAssertion.FloatingAssertion);
        }

        protected AVXOp(String opcode, int prefix, int op, OpAssertion assertion) {
            this(opcode, 0, prefix, op, assertion);
        }

        protected AVXOp(String opcode, int mandatoryPrefix, int prefix, int op, OpAssertion assertion) {
            super(opcode, mandatoryPrefix, prefix, op, assertion, CPUFeature.AVX);
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

            immOp = new AMD64MIOp(opcode, false, 0, 0x81, code, OpAssertion.IntegerAssertion);
            immSxOp = new AMD64MIOp(opcode, true, 0, 0x83, code, OpAssertion.IntegerAssertion);
            mrOp = new AMD64MROp(opcode, 0, baseOp | 0x01, OpAssertion.IntegerAssertion);
            rmOp = new AMD64RMOp(opcode, 0, baseOp | 0x03, OpAssertion.IntegerAssertion);
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
            m1Op = new AMD64MOp(opcode, 0, 0xD1, code, OpAssertion.IntegerAssertion);
            mcOp = new AMD64MOp(opcode, 0, 0xD3, code, OpAssertion.IntegerAssertion);
            miOp = new AMD64MIOp(opcode, true, 0, 0xC1, code, OpAssertion.IntegerAssertion);
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
        assert dst.getRegisterCategory().equals(AMD64.XMM) && src.getRegisterCategory().equals(AMD64.XMM);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        int encode = simdPrefixAndEncode(dst, dst, src, VexSimdPrefix.VEX_SIMD_66, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0x58);
        emitByte(0xC0 | encode);
    }

    public final void addpd(Register dst, AMD64Address src) {
        assert dst.getRegisterCategory().equals(AMD64.XMM);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        simdPrefix(dst, dst, src, VexSimdPrefix.VEX_SIMD_66, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0x58);
        emitOperandHelper(dst, src, 0);
    }

    public final void addsd(Register dst, Register src) {
        assert dst.getRegisterCategory().equals(AMD64.XMM) && src.getRegisterCategory().equals(AMD64.XMM);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        int encode = simdPrefixAndEncode(dst, dst, src, VexSimdPrefix.VEX_SIMD_F2, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0x58);
        emitByte(0xC0 | encode);
    }

    public final void addsd(Register dst, AMD64Address src) {
        assert dst.getRegisterCategory().equals(AMD64.XMM);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        simdPrefix(dst, dst, src, VexSimdPrefix.VEX_SIMD_F2, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0x58);
        emitOperandHelper(dst, src, 0);
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
        assert dst.getRegisterCategory().equals(AMD64.XMM) && src.getRegisterCategory().equals(AMD64.XMM);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        int encode = simdPrefixAndEncode(dst, dst, src, VexSimdPrefix.VEX_SIMD_66, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0x54);
        emitByte(0xC0 | encode);
    }

    public final void andpd(Register dst, AMD64Address src) {
        assert dst.getRegisterCategory().equals(AMD64.XMM);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        simdPrefix(dst, dst, src, VexSimdPrefix.VEX_SIMD_66, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0x54);
        emitOperandHelper(dst, src, 0);
    }

    public final void bsrl(Register dst, Register src) {
        int encode = prefixAndEncode(dst.encoding(), src.encoding());
        emitByte(0x0F);
        emitByte(0xBD);
        emitByte(0xC0 | encode);
    }

    public final void bswapl(Register reg) {
        int encode = prefixAndEncode(reg.encoding);
        emitByte(0x0F);
        emitByte(0xC8 | encode);
    }

    public final void cdql() {
        emitByte(0x99);
    }

    public final void cmovl(ConditionFlag cc, Register dst, Register src) {
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x40 | cc.getValue());
        emitByte(0xC0 | encode);
    }

    public final void cmovl(ConditionFlag cc, Register dst, AMD64Address src) {
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0x40 | cc.getValue());
        emitOperandHelper(dst, src, 0);
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

    // The 32-bit cmpxchg compares the value at adr with the contents of X86.rax,
    // and stores reg into adr if so; otherwise, the value at adr is loaded into X86.rax,.
    // The ZF is set if the compared values were equal, and cleared otherwise.
    public final void cmpxchgl(Register reg, AMD64Address adr) { // cmpxchg
        prefix(adr, reg);
        emitByte(0x0F);
        emitByte(0xB1);
        emitOperandHelper(reg, adr, 0);
    }

    public final void cvtsi2sdl(Register dst, Register src) {
        assert dst.getRegisterCategory().equals(AMD64.XMM) && src.getRegisterCategory().equals(AMD64.CPU);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        int encode = simdPrefixAndEncode(dst, dst, src, VexSimdPrefix.VEX_SIMD_F2, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0x2A);
        emitByte(0xC0 | encode);
    }

    public final void cvttsd2sil(Register dst, Register src) {
        assert dst.getRegisterCategory().equals(AMD64.CPU) && src.getRegisterCategory().equals(AMD64.XMM);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        int encode = simdPrefixAndEncode(dst, Register.None, src, VexSimdPrefix.VEX_SIMD_F2, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0x2C);
        emitByte(0xC0 | encode);
    }

    protected final void decl(AMD64Address dst) {
        prefix(dst);
        emitByte(0xFF);
        emitOperandHelper(1, dst, 0);
    }

    public final void divsd(Register dst, Register src) {
        assert dst.getRegisterCategory().equals(AMD64.XMM) && src.getRegisterCategory().equals(AMD64.XMM);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        int encode = simdPrefixAndEncode(dst, dst, src, VexSimdPrefix.VEX_SIMD_F2, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0x5E);
        emitByte(0xC0 | encode);
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

    protected final void incl(AMD64Address dst) {
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
        int encode = prefixAndEncode(entry.encoding);
        emitByte(0xFF);
        emitByte(0xE0 | encode);
    }

    public final void jmp(AMD64Address adr) {
        prefix(adr);
        emitByte(0xFF);
        emitOperandHelper(rsp, adr, 0);
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
        assert dst.getRegisterCategory().equals(AMD64.XMM) && src.getRegisterCategory().equals(AMD64.XMM);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        int encode = simdPrefixAndEncode(dst, Register.None, src, VexSimdPrefix.VEX_SIMD_66, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0x28);
        emitByte(0xC0 | encode);
    }

    public final void movaps(Register dst, Register src) {
        assert dst.getRegisterCategory().equals(AMD64.XMM) && src.getRegisterCategory().equals(AMD64.XMM);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        int encode = simdPrefixAndEncode(dst, Register.None, src, VexSimdPrefix.VEX_SIMD_NONE, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0x28);
        emitByte(0xC0 | encode);
    }

    public final void movb(AMD64Address dst, int imm8) {
        prefix(dst);
        emitByte(0xC6);
        emitOperandHelper(0, dst, 1);
        emitByte(imm8);
    }

    public final void movb(AMD64Address dst, Register src) {
        assert src.getRegisterCategory().equals(AMD64.CPU) : "must have byte register";
        prefix(dst, src, true);
        emitByte(0x88);
        emitOperandHelper(src, dst, 0);
    }

    public final void movl(Register dst, int imm32) {
        int encode = prefixAndEncode(dst.encoding);
        emitByte(0xB8 | encode);
        emitInt(imm32);
    }

    public final void movl(Register dst, Register src) {
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x8B);
        emitByte(0xC0 | encode);
    }

    public final void movl(Register dst, AMD64Address src) {
        prefix(src, dst);
        emitByte(0x8B);
        emitOperandHelper(dst, src, 0);
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
        assert dst.getRegisterCategory().equals(AMD64.XMM);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        simdPrefix(dst, dst, src, VexSimdPrefix.VEX_SIMD_66, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0x12);
        emitOperandHelper(dst, src, 0);
    }

    public final void movlhps(Register dst, Register src) {
        assert dst.getRegisterCategory().equals(AMD64.XMM) && src.getRegisterCategory().equals(AMD64.XMM);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        int encode = simdPrefixAndEncode(dst, src, src, VexSimdPrefix.VEX_SIMD_NONE, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0x16);
        emitByte(0xC0 | encode);
    }

    public final void movq(Register dst, AMD64Address src) {
        movq(dst, src, false);
    }

    public final void movq(Register dst, AMD64Address src, boolean wide) {
        if (dst.getRegisterCategory().equals(AMD64.XMM)) {
            AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ wide, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
            simdPrefix(dst, Register.None, src, VexSimdPrefix.VEX_SIMD_F3, VexOpcode.VEX_OPCODE_0F, attributes);
            emitByte(0x7E);
            emitOperandHelper(dst, src, wide, 0);
        } else {
            // gpr version of movq
            prefixq(src, dst);
            emitByte(0x8B);
            emitOperandHelper(dst, src, wide, 0);
        }
    }

    public final void movq(Register dst, Register src) {
        int encode = prefixqAndEncode(dst.encoding, src.encoding);
        emitByte(0x8B);
        emitByte(0xC0 | encode);
    }

    public final void movq(AMD64Address dst, Register src) {
        if (src.getRegisterCategory().equals(AMD64.XMM)) {
            AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ true, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
            simdPrefix(src, Register.None, dst, VexSimdPrefix.VEX_SIMD_66, VexOpcode.VEX_OPCODE_0F, attributes);
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
        int encode = prefixAndEncode(dst.encoding, false, src.encoding, true);
        emitByte(0x0F);
        emitByte(0xBE);
        emitByte(0xC0 | encode);
    }

    public final void movsbq(Register dst, AMD64Address src) {
        prefixq(src, dst);
        emitByte(0x0F);
        emitByte(0xBE);
        emitOperandHelper(dst, src, 0);
    }

    public final void movsbq(Register dst, Register src) {
        int encode = prefixqAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0xBE);
        emitByte(0xC0 | encode);
    }

    public final void movsd(Register dst, Register src) {
        assert dst.getRegisterCategory().equals(AMD64.XMM) && src.getRegisterCategory().equals(AMD64.XMM);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        int encode = simdPrefixAndEncode(dst, dst, src, VexSimdPrefix.VEX_SIMD_F2, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0x10);
        emitByte(0xC0 | encode);
    }

    public final void movsd(Register dst, AMD64Address src) {
        assert dst.getRegisterCategory().equals(AMD64.XMM);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        simdPrefix(dst, Register.None, src, VexSimdPrefix.VEX_SIMD_F2, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0x10);
        emitOperandHelper(dst, src, 0);
    }

    public final void movsd(AMD64Address dst, Register src) {
        assert src.getRegisterCategory().equals(AMD64.XMM);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        simdPrefix(src, Register.None, dst, VexSimdPrefix.VEX_SIMD_F2, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0x11);
        emitOperandHelper(src, dst, 0);
    }

    public final void movss(Register dst, Register src) {
        assert dst.getRegisterCategory().equals(AMD64.XMM) && src.getRegisterCategory().equals(AMD64.XMM);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        int encode = simdPrefixAndEncode(dst, dst, src, VexSimdPrefix.VEX_SIMD_F3, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0x10);
        emitByte(0xC0 | encode);
    }

    public final void movss(Register dst, AMD64Address src) {
        assert dst.getRegisterCategory().equals(AMD64.XMM);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        simdPrefix(dst, Register.None, src, VexSimdPrefix.VEX_SIMD_F3, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0x10);
        emitOperandHelper(dst, src, 0);
    }

    public final void movss(AMD64Address dst, Register src) {
        assert src.getRegisterCategory().equals(AMD64.XMM);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        simdPrefix(src, Register.None, dst, VexSimdPrefix.VEX_SIMD_F3, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0x11);
        emitOperandHelper(src, dst, 0);
    }

    public final void mulpd(Register dst, Register src) {
        assert dst.getRegisterCategory().equals(AMD64.XMM) && src.getRegisterCategory().equals(AMD64.XMM);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        int encode = simdPrefixAndEncode(dst, dst, src, VexSimdPrefix.VEX_SIMD_66, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0x59);
        emitByte(0xC0 | encode);
    }

    public final void mulpd(Register dst, AMD64Address src) {
        assert dst.getRegisterCategory().equals(AMD64.XMM);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        simdPrefix(dst, dst, src, VexSimdPrefix.VEX_SIMD_66, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0x59);
        emitOperandHelper(dst, src, 0);
    }

    public final void mulsd(Register dst, Register src) {
        assert dst.getRegisterCategory().equals(AMD64.XMM) && src.getRegisterCategory().equals(AMD64.XMM);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        int encode = simdPrefixAndEncode(dst, dst, src, VexSimdPrefix.VEX_SIMD_F2, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0x59);
        emitByte(0xC0 | encode);
    }

    public final void mulsd(Register dst, AMD64Address src) {
        assert dst.getRegisterCategory().equals(AMD64.XMM);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        simdPrefix(dst, dst, src, VexSimdPrefix.VEX_SIMD_F2, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0x59);
        emitOperandHelper(dst, src, 0);
    }

    public final void mulss(Register dst, Register src) {
        assert dst.getRegisterCategory().equals(AMD64.XMM) && src.getRegisterCategory().equals(AMD64.XMM);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        int encode = simdPrefixAndEncode(dst, dst, src, VexSimdPrefix.VEX_SIMD_F3, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0x59);
        emitByte(0xC0 | encode);
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

    public final void pop(Register dst) {
        int encode = prefixAndEncode(dst.encoding);
        emitByte(0x58 | encode);
    }

    public void popfq() {
        emitByte(0x9D);
    }

    public final void ptest(Register dst, Register src) {
        assert supports(CPUFeature.SSE4_1);
        assert dst.getRegisterCategory().equals(AMD64.XMM) && src.getRegisterCategory().equals(AMD64.XMM);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        int encode = simdPrefixAndEncode(dst, Register.None, src, VexSimdPrefix.VEX_SIMD_66, VexOpcode.VEX_OPCODE_0F_38, attributes);
        emitByte(0x17);
        emitByte(0xC0 | encode);
    }

    public final void vptest(Register dst, Register src) {
        assert supports(CPUFeature.AVX);
        assert dst.getRegisterCategory().equals(AMD64.XMM) && src.getRegisterCategory().equals(AMD64.XMM);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_256bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        int encode = vexPrefixAndEncode(dst, Register.None, src, VexSimdPrefix.VEX_SIMD_66, VexOpcode.VEX_OPCODE_0F_38, attributes);
        emitByte(0x17);
        emitByte(0xC0 | encode);
    }

    public final void push(Register src) {
        int encode = prefixAndEncode(src.encoding);
        emitByte(0x50 | encode);
    }

    public void pushfq() {
        emitByte(0x9c);
    }

    public final void paddd(Register dst, Register src) {
        assert dst.getRegisterCategory().equals(AMD64.XMM) && src.getRegisterCategory().equals(AMD64.XMM);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        int encode = simdPrefixAndEncode(dst, dst, src, VexSimdPrefix.VEX_SIMD_66, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0xFE);
        emitByte(0xC0 | encode);
    }

    public final void paddq(Register dst, Register src) {
        assert dst.getRegisterCategory().equals(AMD64.XMM) && src.getRegisterCategory().equals(AMD64.XMM);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        int encode = simdPrefixAndEncode(dst, dst, src, VexSimdPrefix.VEX_SIMD_66, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0xD4);
        emitByte(0xC0 | encode);
    }

    public final void pextrw(Register dst, Register src, int imm8) {
        assert dst.getRegisterCategory().equals(AMD64.CPU) && src.getRegisterCategory().equals(AMD64.XMM);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        int encode = simdPrefixAndEncode(dst, Register.None, src, VexSimdPrefix.VEX_SIMD_66, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0xC5);
        emitByte(0xC0 | encode);
        emitByte(imm8);
    }

    public final void pinsrw(Register dst, Register src, int imm8) {
        assert dst.getRegisterCategory().equals(AMD64.XMM) && src.getRegisterCategory().equals(AMD64.CPU);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        int encode = simdPrefixAndEncode(dst, dst, src, VexSimdPrefix.VEX_SIMD_66, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0xC4);
        emitByte(0xC0 | encode);
        emitByte(imm8);
    }

    public final void por(Register dst, Register src) {
        assert dst.getRegisterCategory().equals(AMD64.XMM) && src.getRegisterCategory().equals(AMD64.XMM);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        int encode = simdPrefixAndEncode(dst, dst, src, VexSimdPrefix.VEX_SIMD_66, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0xEB);
        emitByte(0xC0 | encode);
    }

    public final void pand(Register dst, Register src) {
        assert dst.getRegisterCategory().equals(AMD64.XMM) && src.getRegisterCategory().equals(AMD64.XMM);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        int encode = simdPrefixAndEncode(dst, dst, src, VexSimdPrefix.VEX_SIMD_66, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0xDB);
        emitByte(0xC0 | encode);
    }

    public final void pxor(Register dst, Register src) {
        assert dst.getRegisterCategory().equals(AMD64.XMM) && src.getRegisterCategory().equals(AMD64.XMM);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        int encode = simdPrefixAndEncode(dst, dst, src, VexSimdPrefix.VEX_SIMD_66, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0xEF);
        emitByte(0xC0 | encode);
    }

    public final void vpxor(Register dst, Register nds, Register src) {
        assert supports(CPUFeature.AVX);
        assert dst.getRegisterCategory().equals(AMD64.XMM) && src.getRegisterCategory().equals(AMD64.XMM);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_256bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        int encode = vexPrefixAndEncode(dst, nds, src, VexSimdPrefix.VEX_SIMD_66, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0xEF);
        emitByte(0xC0 | encode);
    }

    public final void pslld(Register dst, int imm8) {
        assert isUByte(imm8) : "invalid value";
        assert dst.getRegisterCategory().equals(AMD64.XMM);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        // XMM6 is for /6 encoding: 66 0F 72 /6 ib
        int encode = simdPrefixAndEncode(AMD64.xmm6, dst, dst, VexSimdPrefix.VEX_SIMD_66, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0x72);
        emitByte(0xC0 | encode);
        emitByte(imm8 & 0xFF);
    }

    public final void psllq(Register dst, Register shift) {
        assert dst.getRegisterCategory().equals(AMD64.XMM) && shift.getRegisterCategory().equals(AMD64.XMM);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        int encode = simdPrefixAndEncode(dst, dst, shift, VexSimdPrefix.VEX_SIMD_66, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0xF3);
        emitByte(0xC0 | encode);
    }

    public final void psllq(Register dst, int imm8) {
        assert isUByte(imm8) : "invalid value";
        assert dst.getRegisterCategory().equals(AMD64.XMM);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        // XMM6 is for /6 encoding: 66 0F 73 /6 ib
        int encode = simdPrefixAndEncode(AMD64.xmm6, dst, dst, VexSimdPrefix.VEX_SIMD_66, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0x73);
        emitByte(0xC0 | encode);
        emitByte(imm8);
    }

    public final void psrad(Register dst, int imm8) {
        assert isUByte(imm8) : "invalid value";
        assert dst.getRegisterCategory().equals(AMD64.XMM);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        // XMM4 is for /2 encoding: 66 0F 72 /4 ib
        int encode = simdPrefixAndEncode(AMD64.xmm4, dst, dst, VexSimdPrefix.VEX_SIMD_66, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0x72);
        emitByte(0xC0 | encode);
        emitByte(imm8);
    }

    public final void psrld(Register dst, int imm8) {
        assert isUByte(imm8) : "invalid value";
        assert dst.getRegisterCategory().equals(AMD64.XMM);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        // XMM2 is for /2 encoding: 66 0F 72 /2 ib
        int encode = simdPrefixAndEncode(AMD64.xmm2, dst, dst, VexSimdPrefix.VEX_SIMD_66, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0x72);
        emitByte(0xC0 | encode);
        emitByte(imm8);
    }

    public final void psrlq(Register dst, int imm8) {
        assert isUByte(imm8) : "invalid value";
        assert dst.getRegisterCategory().equals(AMD64.XMM);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        // XMM2 is for /2 encoding: 66 0F 73 /2 ib
        int encode = simdPrefixAndEncode(AMD64.xmm2, dst, dst, VexSimdPrefix.VEX_SIMD_66, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0x73);
        emitByte(0xC0 | encode);
        emitByte(imm8);
    }

    public final void pshufd(Register dst, Register src, int imm8) {
        assert isUByte(imm8) : "invalid value";
        assert dst.getRegisterCategory().equals(AMD64.XMM) && src.getRegisterCategory().equals(AMD64.XMM);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        int encode = simdPrefixAndEncode(dst, Register.None, src, VexSimdPrefix.VEX_SIMD_66, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0x70);
        emitByte(0xC0 | encode);
        emitByte(imm8);
    }

    public final void psubd(Register dst, Register src) {
        assert dst.getRegisterCategory().equals(AMD64.XMM) && src.getRegisterCategory().equals(AMD64.XMM);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        int encode = simdPrefixAndEncode(dst, dst, src, VexSimdPrefix.VEX_SIMD_66, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0xFA);
        emitByte(0xC0 | encode);
    }

    public final void rcpps(Register dst, Register src) {
        assert dst.getRegisterCategory().equals(AMD64.XMM) && src.getRegisterCategory().equals(AMD64.XMM);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ true, /* noMaskReg */ false, /* usesVl */ false, target);
        int encode = simdPrefixAndEncode(dst, Register.None, src, VexSimdPrefix.VEX_SIMD_NONE, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0x53);
        emitByte(0xC0 | encode);
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
        int encode = prefixAndEncode(dst.encoding);
        assert isShiftCount(imm8 >> 1) : "illegal shift count";
        if (imm8 == 1) {
            emitByte(0xD1);
            emitByte(0xF8 | encode);
        } else {
            emitByte(0xC1);
            emitByte(0xF8 | encode);
            emitByte(imm8);
        }
    }

    public final void shll(Register dst, int imm8) {
        assert isShiftCount(imm8 >> 1) : "illegal shift count";
        int encode = prefixAndEncode(dst.encoding);
        if (imm8 == 1) {
            emitByte(0xD1);
            emitByte(0xE0 | encode);
        } else {
            emitByte(0xC1);
            emitByte(0xE0 | encode);
            emitByte(imm8);
        }
    }

    public final void shll(Register dst) {
        int encode = prefixAndEncode(dst.encoding);
        emitByte(0xD3);
        emitByte(0xE0 | encode);
    }

    public final void shrl(Register dst, int imm8) {
        assert isShiftCount(imm8 >> 1) : "illegal shift count";
        int encode = prefixAndEncode(dst.encoding);
        emitByte(0xC1);
        emitByte(0xE8 | encode);
        emitByte(imm8);
    }

    public final void shrl(Register dst) {
        int encode = prefixAndEncode(dst.encoding);
        emitByte(0xD3);
        emitByte(0xE8 | encode);
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
        assert dst.getRegisterCategory().equals(AMD64.XMM) && src.getRegisterCategory().equals(AMD64.XMM);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        int encode = simdPrefixAndEncode(dst, dst, src, VexSimdPrefix.VEX_SIMD_66, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0x5C);
        emitByte(0xC0 | encode);
    }

    public final void subsd(Register dst, Register src) {
        assert dst.getRegisterCategory().equals(AMD64.XMM) && src.getRegisterCategory().equals(AMD64.XMM);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        int encode = simdPrefixAndEncode(dst, dst, src, VexSimdPrefix.VEX_SIMD_F2, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0x5C);
        emitByte(0xC0 | encode);
    }

    public final void subsd(Register dst, AMD64Address src) {
        assert dst.getRegisterCategory().equals(AMD64.XMM);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        simdPrefix(dst, dst, src, VexSimdPrefix.VEX_SIMD_F2, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0x5C);
        emitOperandHelper(dst, src, 0);
    }

    public final void testl(Register dst, int imm32) {
        // not using emitArith because test
        // doesn't support sign-extension of
        // 8bit operands
        int encode = dst.encoding;
        if (encode == 0) {
            emitByte(0xA9);
        } else {
            encode = prefixAndEncode(encode);
            emitByte(0xF7);
            emitByte(0xC0 | encode);
        }
        emitInt(imm32);
    }

    public final void testl(Register dst, Register src) {
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x85);
        emitByte(0xC0 | encode);
    }

    public final void testl(Register dst, AMD64Address src) {
        prefix(src, dst);
        emitByte(0x85);
        emitOperandHelper(dst, src, 0);
    }

    public final void unpckhpd(Register dst, Register src) {
        assert dst.getRegisterCategory().equals(AMD64.XMM) && src.getRegisterCategory().equals(AMD64.XMM);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        int encode = simdPrefixAndEncode(dst, dst, src, VexSimdPrefix.VEX_SIMD_66, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0x15);
        emitByte(0xC0 | encode);
    }

    public final void unpcklpd(Register dst, Register src) {
        assert dst.getRegisterCategory().equals(AMD64.XMM) && src.getRegisterCategory().equals(AMD64.XMM);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        int encode = simdPrefixAndEncode(dst, dst, src, VexSimdPrefix.VEX_SIMD_66, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0x14);
        emitByte(0xC0 | encode);
    }

    public final void xorl(Register dst, Register src) {
        XOR.rmOp.emit(this, DWORD, dst, src);
    }

    public final void xorpd(Register dst, Register src) {
        assert dst.getRegisterCategory().equals(AMD64.XMM) && src.getRegisterCategory().equals(AMD64.XMM);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        int encode = simdPrefixAndEncode(dst, dst, src, VexSimdPrefix.VEX_SIMD_66, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0x57);
        emitByte(0xC0 | encode);
    }

    public final void xorps(Register dst, Register src) {
        assert dst.getRegisterCategory().equals(AMD64.XMM) && src.getRegisterCategory().equals(AMD64.XMM);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        int encode = simdPrefixAndEncode(dst, dst, src, VexSimdPrefix.VEX_SIMD_NONE, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0x57);
        emitByte(0xC0 | encode);
    }

    protected final void decl(Register dst) {
        // Use two-byte form (one-byte form is a REX prefix in 64-bit mode)
        int encode = prefixAndEncode(dst.encoding);
        emitByte(0xFF);
        emitByte(0xC8 | encode);
    }

    protected final void incl(Register dst) {
        // Use two-byte form (one-byte from is a REX prefix in 64-bit mode)
        int encode = prefixAndEncode(dst.encoding);
        emitByte(0xFF);
        emitByte(0xC0 | encode);
    }

    private int prefixAndEncode(int regEnc) {
        return prefixAndEncode(regEnc, false);
    }

    private int prefixAndEncode(int regEnc, boolean byteinst) {
        if (regEnc >= 8) {
            emitByte(Prefix.REXB);
            return regEnc - 8;
        } else if (byteinst && regEnc >= 4) {
            emitByte(Prefix.REX);
        }
        return regEnc;
    }

    private int prefixqAndEncode(int regEnc) {
        if (regEnc < 8) {
            emitByte(Prefix.REXW);
            return regEnc;
        } else {
            emitByte(Prefix.REXWB);
            return regEnc - 8;
        }
    }

    private int prefixAndEncode(int dstEnc, int srcEnc) {
        return prefixAndEncode(dstEnc, false, srcEnc, false);
    }

    private int prefixAndEncode(int dstEncoding, boolean dstIsByte, int srcEncoding, boolean srcIsByte) {
        int srcEnc = srcEncoding;
        int dstEnc = dstEncoding;
        if (dstEnc < 8) {
            if (srcEnc >= 8) {
                emitByte(Prefix.REXB);
                srcEnc -= 8;
            } else if ((srcIsByte && srcEnc >= 4) || (dstIsByte && dstEnc >= 4)) {
                emitByte(Prefix.REX);
            }
        } else {
            if (srcEnc < 8) {
                emitByte(Prefix.REXR);
            } else {
                emitByte(Prefix.REXRB);
                srcEnc -= 8;
            }
            dstEnc -= 8;
        }
        return dstEnc << 3 | srcEnc;
    }

    /**
     * Creates prefix and the encoding of the lower 6 bits of the ModRM-Byte. It emits an operand
     * prefix. If the given operands exceed 3 bits, the 4th bit is encoded in the prefix.
     *
     * @param regEncoding the encoding of the register part of the ModRM-Byte
     * @param rmEncoding the encoding of the r/m part of the ModRM-Byte
     * @return the lower 6 bits of the ModRM-Byte that should be emitted
     */
    private int prefixqAndEncode(int regEncoding, int rmEncoding) {
        int rmEnc = rmEncoding;
        int regEnc = regEncoding;
        if (regEnc < 8) {
            if (rmEnc < 8) {
                emitByte(Prefix.REXW);
            } else {
                emitByte(Prefix.REXWB);
                rmEnc -= 8;
            }
        } else {
            if (rmEnc < 8) {
                emitByte(Prefix.REXWR);
            } else {
                emitByte(Prefix.REXWRB);
                rmEnc -= 8;
            }
            regEnc -= 8;
        }
        return regEnc << 3 | rmEnc;
    }

    private void vexPrefix(int rxb, int ndsEncoding, int pre, int opc, AMD64InstructionAttr attributes) {
        int vectorLen = attributes.getVectorLen();
        boolean vexW = attributes.isRexVexW();
        boolean isXorB = ((rxb & 0x3) > 0);
        if (isXorB || vexW || (opc == VexOpcode.VEX_OPCODE_0F_38) || (opc == VexOpcode.VEX_OPCODE_0F_3A)) {
            emitByte(Prefix.VEX_3BYTES);

            int byte1 = (rxb << 5);
            byte1 = ((~byte1) & 0xE0) | opc;
            emitByte(byte1);

            int byte2 = ((~ndsEncoding) & 0xf) << 3;
            byte2 |= (vexW ? VexPrefix.VEX_W : 0) | ((vectorLen > 0) ? 4 : 0) | pre;
            emitByte(byte2);
        } else {
            emitByte(Prefix.VEX_2BYTES);

            int byte1 = ((rxb & 0x4) > 0) ? VexPrefix.VEX_R : 0;
            byte1 = (~byte1) & 0x80;
            byte1 |= ((~ndsEncoding) & 0xf) << 3;
            byte1 |= ((vectorLen > 0) ? 4 : 0) | pre;
            emitByte(byte1);
        }
    }

    private void vexPrefix(AMD64Address adr, Register nds, Register src, int pre, int opc, AMD64InstructionAttr attributes) {
        int rxb = getRXB(src, adr);
        int ndsEncoding = nds.isValid() ? nds.encoding : 0;
        vexPrefix(rxb, ndsEncoding, pre, opc, attributes);
        setCurAttributes(attributes);
    }

    private int vexPrefixAndEncode(Register dst, Register nds, Register src, int pre, int opc, AMD64InstructionAttr attributes) {
        int rxb = getRXB(dst, src);
        int ndsEncoding = nds.isValid() ? nds.encoding : 0;
        vexPrefix(rxb, ndsEncoding, pre, opc, attributes);
        // return modrm byte components for operands
        return (((dst.encoding & 7) << 3) | (src.encoding & 7));
    }

    private void simdPrefix(Register xreg, Register nds, AMD64Address adr, int pre, int opc, AMD64InstructionAttr attributes) {
        if (supports(CPUFeature.AVX)) {
            vexPrefix(adr, nds, xreg, pre, opc, attributes);
        } else {
            switch (pre) {
                case VexSimdPrefix.VEX_SIMD_66:
                    emitByte(0x66);
                    break;
                case VexSimdPrefix.VEX_SIMD_F2:
                    emitByte(0xF2);
                    break;
                case VexSimdPrefix.VEX_SIMD_F3:
                    emitByte(0xF3);
                    break;
            }
            if (attributes.isRexVexW()) {
                prefixq(adr, xreg);
            } else {
                prefix(adr, xreg);
            }
            switch (opc) {
                case VexOpcode.VEX_OPCODE_0F:
                    emitByte(0x0F);
                    break;
                case VexOpcode.VEX_OPCODE_0F_38:
                    emitByte(0x0F);
                    emitByte(0x38);
                    break;
                case VexOpcode.VEX_OPCODE_0F_3A:
                    emitByte(0x0F);
                    emitByte(0x3A);
                    break;
            }
        }
    }

    private int simdPrefixAndEncode(Register dst, Register nds, Register src, int pre, int opc, AMD64InstructionAttr attributes) {
        if (supports(CPUFeature.AVX)) {
            return vexPrefixAndEncode(dst, nds, src, pre, opc, attributes);
        } else {
            switch (pre) {
                case VexSimdPrefix.VEX_SIMD_66:
                    emitByte(0x66);
                    break;
                case VexSimdPrefix.VEX_SIMD_F2:
                    emitByte(0xF2);
                    break;
                case VexSimdPrefix.VEX_SIMD_F3:
                    emitByte(0xF3);
                    break;
            }
            int encode;
            int dstEncoding = dst.encoding;
            int srcEncoding = src.encoding;
            if (attributes.isRexVexW()) {
                encode = prefixqAndEncode(dstEncoding, srcEncoding);
            } else {
                encode = prefixAndEncode(dstEncoding, srcEncoding);
            }
            switch (opc) {
                case VexOpcode.VEX_OPCODE_0F:
                    emitByte(0x0F);
                    break;
                case VexOpcode.VEX_OPCODE_0F_38:
                    emitByte(0x0F);
                    emitByte(0x38);
                    break;
                case VexOpcode.VEX_OPCODE_0F_3A:
                    emitByte(0x0F);
                    emitByte(0x3A);
                    break;
            }
            return encode;
        }
    }

    private static boolean needsRex(Register reg) {
        return reg.encoding >= MinEncodingNeedsRex;
    }

    private void prefix(AMD64Address adr) {
        if (needsRex(adr.getBase())) {
            if (needsRex(adr.getIndex())) {
                emitByte(Prefix.REXXB);
            } else {
                emitByte(Prefix.REXB);
            }
        } else {
            if (needsRex(adr.getIndex())) {
                emitByte(Prefix.REXX);
            }
        }
    }

    private void prefixq(AMD64Address adr) {
        if (needsRex(adr.getBase())) {
            if (needsRex(adr.getIndex())) {
                emitByte(Prefix.REXWXB);
            } else {
                emitByte(Prefix.REXWB);
            }
        } else {
            if (needsRex(adr.getIndex())) {
                emitByte(Prefix.REXWX);
            } else {
                emitByte(Prefix.REXW);
            }
        }
    }

    private void prefix(AMD64Address adr, Register reg) {
        prefix(adr, reg, false);
    }

    private void prefix(AMD64Address adr, Register reg, boolean byteinst) {
        if (reg.encoding < 8) {
            if (needsRex(adr.getBase())) {
                if (needsRex(adr.getIndex())) {
                    emitByte(Prefix.REXXB);
                } else {
                    emitByte(Prefix.REXB);
                }
            } else {
                if (needsRex(adr.getIndex())) {
                    emitByte(Prefix.REXX);
                } else if (byteinst && reg.encoding >= 4) {
                    emitByte(Prefix.REX);
                }
            }
        } else {
            if (needsRex(adr.getBase())) {
                if (needsRex(adr.getIndex())) {
                    emitByte(Prefix.REXRXB);
                } else {
                    emitByte(Prefix.REXRB);
                }
            } else {
                if (needsRex(adr.getIndex())) {
                    emitByte(Prefix.REXRX);
                } else {
                    emitByte(Prefix.REXR);
                }
            }
        }
    }

    private void prefixq(AMD64Address adr, Register src) {
        if (src.encoding < 8) {
            if (needsRex(adr.getBase())) {
                if (needsRex(adr.getIndex())) {
                    emitByte(Prefix.REXWXB);
                } else {
                    emitByte(Prefix.REXWB);
                }
            } else {
                if (needsRex(adr.getIndex())) {
                    emitByte(Prefix.REXWX);
                } else {
                    emitByte(Prefix.REXW);
                }
            }
        } else {
            if (needsRex(adr.getBase())) {
                if (needsRex(adr.getIndex())) {
                    emitByte(Prefix.REXWRXB);
                } else {
                    emitByte(Prefix.REXWRB);
                }
            } else {
                if (needsRex(adr.getIndex())) {
                    emitByte(Prefix.REXWRX);
                } else {
                    emitByte(Prefix.REXWR);
                }
            }
        }
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
        int encode = prefixqAndEncode(dst.encoding(), src.encoding());
        emitByte(0x0F);
        emitByte(0xBD);
        emitByte(0xC0 | encode);
    }

    public final void bswapq(Register reg) {
        int encode = prefixqAndEncode(reg.encoding);
        emitByte(0x0F);
        emitByte(0xC8 | encode);
    }

    public final void cdqq() {
        emitByte(Prefix.REXW);
        emitByte(0x99);
    }

    public final void cmovq(ConditionFlag cc, Register dst, Register src) {
        int encode = prefixqAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x40 | cc.getValue());
        emitByte(0xC0 | encode);
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
        assert dst.getRegisterCategory().equals(AMD64.XMM) && src.getRegisterCategory().equals(AMD64.XMM);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        int encode = simdPrefixAndEncode(dst, Register.None, src, VexSimdPrefix.VEX_SIMD_F3, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0xE6);
        emitByte(0xC0 | encode);
    }

    public final void cvtsi2sdq(Register dst, Register src) {
        assert dst.getRegisterCategory().equals(AMD64.XMM) && src.getRegisterCategory().equals(AMD64.CPU);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ true, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        int encode = simdPrefixAndEncode(dst, dst, src, VexSimdPrefix.VEX_SIMD_F2, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0x2A);
        emitByte(0xC0 | encode);
    }

    public final void cvttsd2siq(Register dst, Register src) {
        assert dst.getRegisterCategory().equals(AMD64.CPU) && src.getRegisterCategory().equals(AMD64.XMM);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ true, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        int encode = simdPrefixAndEncode(dst, Register.None, src, VexSimdPrefix.VEX_SIMD_F2, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0x2C);
        emitByte(0xC0 | encode);
    }

    public final void cvttpd2dq(Register dst, Register src) {
        assert dst.getRegisterCategory().equals(AMD64.XMM) && src.getRegisterCategory().equals(AMD64.XMM);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        int encode = simdPrefixAndEncode(dst, Register.None, src, VexSimdPrefix.VEX_SIMD_66, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0xE6);
        emitByte(0xC0 | encode);
    }

    protected final void decq(Register dst) {
        // Use two-byte form (one-byte from is a REX prefix in 64-bit mode)
        int encode = prefixqAndEncode(dst.encoding);
        emitByte(0xFF);
        emitByte(0xC8 | encode);
    }

    public final void decq(AMD64Address dst) {
        DEC.emit(this, QWORD, dst);
    }

    public final void imulq(Register dst, Register src) {
        int encode = prefixqAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0xAF);
        emitByte(0xC0 | encode);
    }

    public final void incq(Register dst) {
        // Don't use it directly. Use Macroincrementq() instead.
        // Use two-byte form (one-byte from is a REX prefix in 64-bit mode)
        int encode = prefixqAndEncode(dst.encoding);
        emitByte(0xFF);
        emitByte(0xC0 | encode);
    }

    public final void incq(AMD64Address dst) {
        INC.emit(this, QWORD, dst);
    }

    public final void movq(Register dst, long imm64) {
        int encode = prefixqAndEncode(dst.encoding);
        emitByte(0xB8 | encode);
        emitLong(imm64);
    }

    public final void movslq(Register dst, int imm32) {
        int encode = prefixqAndEncode(dst.encoding);
        emitByte(0xC7);
        emitByte(0xC0 | encode);
        emitInt(imm32);
    }

    public final void movdq(Register dst, AMD64Address src) {
        assert dst.getRegisterCategory().equals(AMD64.XMM);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ true, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        simdPrefix(dst, Register.None, src, VexSimdPrefix.VEX_SIMD_66, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0x6E);
        emitOperandHelper(dst, src, 0);
    }

    public final void movdq(AMD64Address dst, Register src) {
        assert src.getRegisterCategory().equals(AMD64.XMM);
        // swap src/dst to get correct prefix
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ true, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        simdPrefix(src, Register.None, dst, VexSimdPrefix.VEX_SIMD_66, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0x7E);
        emitOperandHelper(src, dst, 0);
    }

    public final void movdq(Register dst, Register src) {
        if (dst.getRegisterCategory().equals(AMD64.XMM) && src.getRegisterCategory().equals(AMD64.CPU)) {
            AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ true, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
            int encode = simdPrefixAndEncode(dst, Register.None, src, VexSimdPrefix.VEX_SIMD_66, VexOpcode.VEX_OPCODE_0F, attributes);
            emitByte(0x6E);
            emitByte(0xC0 | encode);
        } else if (src.getRegisterCategory().equals(AMD64.XMM) && dst.getRegisterCategory().equals(AMD64.CPU)) {
            // swap src/dst to get correct prefix
            AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ true, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
            int encode = simdPrefixAndEncode(src, Register.None, dst, VexSimdPrefix.VEX_SIMD_66, VexOpcode.VEX_OPCODE_0F, attributes);
            emitByte(0x7E);
            emitByte(0xC0 | encode);
        } else {
            throw new InternalError("should not reach here");
        }
    }

    public final void movdl(Register dst, Register src) {
        if (dst.getRegisterCategory().equals(AMD64.XMM) && src.getRegisterCategory().equals(AMD64.CPU)) {
            AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
            int encode = simdPrefixAndEncode(dst, Register.None, src, VexSimdPrefix.VEX_SIMD_66, VexOpcode.VEX_OPCODE_0F, attributes);
            emitByte(0x6E);
            emitByte(0xC0 | encode);
        } else if (src.getRegisterCategory().equals(AMD64.XMM) && dst.getRegisterCategory().equals(AMD64.CPU)) {
            // swap src/dst to get correct prefix
            AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
            int encode = simdPrefixAndEncode(src, Register.None, dst, VexSimdPrefix.VEX_SIMD_66, VexOpcode.VEX_OPCODE_0F, attributes);
            emitByte(0x7E);
            emitByte(0xC0 | encode);
        } else {
            throw new InternalError("should not reach here");
        }
    }

    public final void movddup(Register dst, Register src) {
        assert supports(CPUFeature.SSE3);
        assert dst.getRegisterCategory().equals(AMD64.XMM);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        int encode = simdPrefixAndEncode(dst, Register.None, src, VexSimdPrefix.VEX_SIMD_F2, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0x12);
        emitByte(0xC0 | encode);
    }

    public final void movdqu(Register dst, AMD64Address src) {
        assert dst.getRegisterCategory().equals(AMD64.XMM);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        simdPrefix(dst, Register.None, src, VexSimdPrefix.VEX_SIMD_F3, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0x6F);
        emitOperandHelper(dst, src, 0);
    }

    public final void movdqu(Register dst, Register src) {
        assert dst.getRegisterCategory().equals(AMD64.XMM);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        int encode = simdPrefixAndEncode(dst, Register.None, src, VexSimdPrefix.VEX_SIMD_F3, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0x6F);
        emitByte(0xC0 | encode);
    }

    public final void vmovdqu(Register dst, AMD64Address src) {
        assert supports(CPUFeature.AVX);
        assert dst.getRegisterCategory().equals(AMD64.XMM);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_256bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        vexPrefix(src, Register.None, dst, VexSimdPrefix.VEX_SIMD_F3, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0x6F);
        emitOperandHelper(dst, src, 0);
    }

    public final void vzeroupper() {
        assert supports(CPUFeature.AVX);
        AMD64InstructionAttr attributes = new AMD64InstructionAttr(AvxVectorLen.AVX_128bit, /* rexVexW */ false, /* legacyMode */ false, /* noMaskReg */ false, /* usesVl */ false, target);
        vexPrefixAndEncode(AMD64.xmm0, AMD64.xmm0, AMD64.xmm0, VexSimdPrefix.VEX_SIMD_NONE, VexOpcode.VEX_OPCODE_0F, attributes);
        emitByte(0x77);
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
        int encode = prefixqAndEncode(dst.encoding, src.encoding);
        emitByte(0x63);
        emitByte(0xC0 | encode);
    }

    public final void negq(Register dst) {
        int encode = prefixqAndEncode(dst.encoding);
        emitByte(0xF7);
        emitByte(0xD8 | encode);
    }

    public final void orq(Register dst, Register src) {
        OR.rmOp.emit(this, QWORD, dst, src);
    }

    public final void shlq(Register dst, int imm8) {
        assert isShiftCount(imm8 >> 1) : "illegal shift count";
        int encode = prefixqAndEncode(dst.encoding);
        if (imm8 == 1) {
            emitByte(0xD1);
            emitByte(0xE0 | encode);
        } else {
            emitByte(0xC1);
            emitByte(0xE0 | encode);
            emitByte(imm8);
        }
    }

    public final void shlq(Register dst) {
        int encode = prefixqAndEncode(dst.encoding);
        emitByte(0xD3);
        emitByte(0xE0 | encode);
    }

    public final void shrq(Register dst, int imm8) {
        assert isShiftCount(imm8 >> 1) : "illegal shift count";
        int encode = prefixqAndEncode(dst.encoding);
        if (imm8 == 1) {
            emitByte(0xD1);
            emitByte(0xE8 | encode);
        } else {
            emitByte(0xC1);
            emitByte(0xE8 | encode);
            emitByte(imm8);
        }
    }

    public final void shrq(Register dst) {
        int encode = prefixqAndEncode(dst.encoding);
        emitByte(0xD3);
        emitByte(0xE8 | encode);
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
        int encode = prefixqAndEncode(dst.encoding, src.encoding);
        emitByte(0x85);
        emitByte(0xC0 | encode);
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
                addl(new AMD64Address(rsp, 0), 0); // Assert the lock# signal here
            }
        }
    }

    @Override
    protected final void patchJumpTarget(int branch, int branchTarget) {
        int op = getByte(branch);
        assert op == 0xE8 // call
                        ||
                        op == 0x00 // jump table entry
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
        if (codePatchingAnnotationConsumer != null) {
            int pos = position();
            codePatchingAnnotationConsumer.accept(new ImmediateOperandAnnotation(pos, pos + 1, 4, pos + 5));
        }
        emitByte(0xE8);
        emitInt(0);
    }

    public final void call(Register src) {
        int encode = prefixAndEncode(src.encoding);
        emitByte(0xFF);
        emitByte(0xD0 | encode);
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
        return new AMD64Address(rip, Register.None, Scale.Times1, 0, instructionStartPosition);
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
}
