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


package org.graalvm.compiler.asm.amd64;

import static jdk.vm.ci.amd64.AMD64.MASK;
import static jdk.vm.ci.amd64.AMD64.XMM;
import static jdk.vm.ci.amd64.AMD64.r12;
import static jdk.vm.ci.amd64.AMD64.r13;
import static jdk.vm.ci.amd64.AMD64.rbp;
import static jdk.vm.ci.amd64.AMD64.rsp;
import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.EVEXPrefixConfig.B0;
import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.EVEXPrefixConfig.B1;
import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.EVEXPrefixConfig.L512;
import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.EVEXPrefixConfig.Z0;
import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.EVEXPrefixConfig.Z1;
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

import org.graalvm.compiler.asm.Assembler;
import org.graalvm.compiler.asm.amd64.AMD64Address.Scale;
import org.graalvm.compiler.asm.amd64.AVXKind.AVXSize;
import org.graalvm.compiler.debug.GraalError;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.Register.RegisterCategory;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.PlatformKind;

/**
 * This class implements an assembler that can encode most X86 instructions.
 */
public abstract class AMD64BaseAssembler extends Assembler {

    private final SIMDEncoder simdEncoder;

    /**
     * Constructs an assembler for the AMD64 architecture.
     */
    public AMD64BaseAssembler(TargetDescription target) {
        super(target);

        if (supports(CPUFeature.AVX)) {
            simdEncoder = new VEXEncoderImpl();
        } else {
            simdEncoder = new SSEEncoderImpl();
        }
    }

    /**
     * The x86 operand sizes.
     */
    public enum OperandSize {
        BYTE(1, AMD64Kind.BYTE) {
            @Override
            protected void emitImmediate(AMD64BaseAssembler asm, int imm) {
                assert imm == (byte) imm;
                asm.emitByte(imm);
            }

            @Override
            protected int immediateSize() {
                return 1;
            }
        },

        WORD(2, AMD64Kind.WORD, 0x66) {
            @Override
            protected void emitImmediate(AMD64BaseAssembler asm, int imm) {
                assert imm == (short) imm;
                asm.emitShort(imm);
            }

            @Override
            protected int immediateSize() {
                return 2;
            }
        },

        DWORD(4, AMD64Kind.DWORD) {
            @Override
            protected void emitImmediate(AMD64BaseAssembler asm, int imm) {
                asm.emitInt(imm);
            }

            @Override
            protected int immediateSize() {
                return 4;
            }
        },

        QWORD(8, AMD64Kind.QWORD) {
            @Override
            protected void emitImmediate(AMD64BaseAssembler asm, int imm) {
                asm.emitInt(imm);
            }

            @Override
            protected int immediateSize() {
                return 4;
            }
        },

        SS(4, AMD64Kind.SINGLE, 0xF3, true),

        SD(8, AMD64Kind.DOUBLE, 0xF2, true),

        PS(16, AMD64Kind.V128_SINGLE, true),

        PD(16, AMD64Kind.V128_DOUBLE, 0x66, true);

        private final int sizePrefix;
        private final int bytes;
        private final boolean xmm;
        private final AMD64Kind kind;

        OperandSize(int bytes, AMD64Kind kind) {
            this(bytes, kind, 0);
        }

        OperandSize(int bytes, AMD64Kind kind, int sizePrefix) {
            this(bytes, kind, sizePrefix, false);
        }

        OperandSize(int bytes, AMD64Kind kind, boolean xmm) {
            this(bytes, kind, 0, xmm);
        }

        OperandSize(int bytes, AMD64Kind kind, int sizePrefix, boolean xmm) {
            this.sizePrefix = sizePrefix;
            this.bytes = bytes;
            this.kind = kind;
            this.xmm = xmm;
        }

        public int getSizePrefix() {
            return sizePrefix;
        }

        public int getBytes() {
            return bytes;
        }

        public boolean isXmmType() {
            return xmm;
        }

        public AMD64Kind getKind() {
            return kind;
        }

        public static OperandSize get(PlatformKind kind) {
            for (OperandSize operandSize : OperandSize.values()) {
                if (operandSize.kind.equals(kind)) {
                    return operandSize;
                }
            }
            throw GraalError.shouldNotReachHere("Unexpected kind: " + kind.toString());
        }

        /**
         * Emit an immediate of this size. Note that immediate {@link #QWORD} operands are encoded
         * as sign-extended 32-bit values.
         *
         * @param asm
         * @param imm
         */
        protected void emitImmediate(AMD64BaseAssembler asm, int imm) {
            throw new UnsupportedOperationException();
        }

        protected int immediateSize() {
            throw new UnsupportedOperationException();
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
    protected static class AddressDisplacementAnnotation extends OperandDataAnnotation {
        AddressDisplacementAnnotation(int instructionPosition, int operandPosition, int operandSize, int nextInstructionPosition) {
            super(instructionPosition, operandPosition, operandSize, nextInstructionPosition);
        }
    }

    /**
     * Annotation that stores additional information about the immediate operand, e.g., of a call
     * instruction, that needs patching.
     */
    protected static class ImmediateOperandAnnotation extends OperandDataAnnotation {
        ImmediateOperandAnnotation(int instructionPosition, int operandPosition, int operandSize, int nextInstructionPosition) {
            super(instructionPosition, operandPosition, operandSize, nextInstructionPosition);
        }
    }

    protected void annotatePatchingImmediate(int operandOffset, int operandSize) {
        if (codePatchingAnnotationConsumer != null) {
            int pos = position();
            codePatchingAnnotationConsumer.accept(new ImmediateOperandAnnotation(pos, pos + operandOffset, operandSize, pos + operandOffset + operandSize));
        }
    }

    public final boolean supports(CPUFeature feature) {
        return ((AMD64) target.arch).getFeatures().contains(feature);
    }

    protected static boolean inRC(RegisterCategory rc, Register r) {
        return r.getRegisterCategory().equals(rc);
    }

    protected static int encode(Register r) {
        assert r.encoding >= 0 && (inRC(XMM, r) ? r.encoding < 32 : r.encoding < 16) : "encoding out of range: " + r.encoding;
        return r.encoding & 0x7;
    }

    private static final int MinEncodingNeedsRex = 8;

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

        private static final int VEX2 = 0xC5;
        private static final int VEX3 = 0xC4;
        private static final int EVEX = 0x62;
    }

    protected final void rexw() {
        emitByte(Prefix.REXW);
    }

    protected final void prefix(Register reg) {
        prefix(reg, false);
    }

    protected final void prefix(Register reg, boolean byteinst) {
        int regEnc = reg.encoding;
        if (regEnc >= 8) {
            emitByte(Prefix.REXB);
        } else if (byteinst && regEnc >= 4) {
            emitByte(Prefix.REX);
        }
    }

    protected final void prefixq(Register reg) {
        if (reg.encoding < 8) {
            emitByte(Prefix.REXW);
        } else {
            emitByte(Prefix.REXWB);
        }
    }

    protected final void prefix(Register dst, Register src) {
        prefix(dst, false, src, false);
    }

    protected final void prefix(Register dst, boolean dstIsByte, Register src, boolean srcIsByte) {
        int dstEnc = dst.encoding;
        int srcEnc = src.encoding;
        if (dstEnc < 8) {
            if (srcEnc >= 8) {
                emitByte(Prefix.REXB);
            } else if ((srcIsByte && srcEnc >= 4) || (dstIsByte && dstEnc >= 4)) {
                emitByte(Prefix.REX);
            }
        } else {
            if (srcEnc < 8) {
                emitByte(Prefix.REXR);
            } else {
                emitByte(Prefix.REXRB);
            }
        }
    }

    /**
     * Creates prefix for the operands. If the given operands exceed 3 bits, the 4th bit is encoded
     * in the prefix.
     */
    protected final void prefixq(Register reg, Register rm) {
        int regEnc = reg.encoding;
        int rmEnc = rm.encoding;
        if (regEnc < 8) {
            if (rmEnc < 8) {
                emitByte(Prefix.REXW);
            } else {
                emitByte(Prefix.REXWB);
            }
        } else {
            if (rmEnc < 8) {
                emitByte(Prefix.REXWR);
            } else {
                emitByte(Prefix.REXWRB);
            }
        }
    }

    private static boolean needsRex(Register reg) {
        return reg.encoding >= MinEncodingNeedsRex;
    }

    protected final void prefix(AMD64Address adr) {
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

    protected final void prefixq(AMD64Address adr) {
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

    protected void prefixb(AMD64Address adr, Register reg) {
        prefix(adr, reg, true);
    }

    protected void prefix(AMD64Address adr, Register reg) {
        prefix(adr, reg, false);
    }

    protected void prefix(AMD64Address adr, Register reg, boolean byteinst) {
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

    protected void prefixq(AMD64Address adr, Register src) {
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
    protected final void emitModRM(int reg, Register rm) {
        assert (reg & 0x07) == reg;
        emitByte(0xC0 | (reg << 3) | (rm.encoding & 0x07));
    }

    /**
     * Emit the ModR/M byte for two register operands.
     * <p>
     * Format: [ 11 reg r/m ]
     */
    protected final void emitModRM(Register reg, Register rm) {
        emitModRM(reg.encoding & 0x07, rm);
    }

    /**
     * Emits the ModR/M byte and optionally the SIB byte for one register and one memory operand.
     *
     * @param force4Byte use 4 byte encoding for displacements that would normally fit in a byte
     */
    protected final void emitOperandHelper(Register reg, AMD64Address addr, boolean force4Byte, int additionalInstructionSize) {
        assert !reg.equals(Register.None);
        emitOperandHelper(encode(reg), addr, force4Byte, additionalInstructionSize, 1);
    }

    protected final void emitOperandHelper(int reg, AMD64Address addr, int additionalInstructionSize) {
        emitOperandHelper(reg, addr, false, additionalInstructionSize, 1);
    }

    protected final void emitOperandHelper(Register reg, AMD64Address addr, int additionalInstructionSize) {
        assert !reg.equals(Register.None);
        emitOperandHelper(encode(reg), addr, false, additionalInstructionSize, 1);
    }

    protected final void emitEVEXOperandHelper(Register reg, AMD64Address addr, int additionalInstructionSize, int evexDisp8Scale) {
        assert !reg.equals(Register.None);
        emitOperandHelper(encode(reg), addr, false, additionalInstructionSize, evexDisp8Scale);
    }

    /**
     * Emits the ModR/M byte and optionally the SIB byte for one memory operand and an opcode
     * extension in the R field.
     *
     * @param force4Byte use 4 byte encoding for displacements that would normally fit in a byte
     * @param additionalInstructionSize the number of bytes that will be emitted after the operand,
     *            so that the start position of the next instruction can be computed even though
     *            this instruction has not been completely emitted yet.
     * @param evexDisp8Scale the scaling factor for computing the compressed displacement of
     *            EVEX-encoded instructions. This scaling factor only matters when the emitted
     *            instruction uses one-byte-displacement form.
     */
    private void emitOperandHelper(int reg, AMD64Address addr, boolean force4Byte, int additionalInstructionSize, int evexDisp8Scale) {
        assert (reg & 0x07) == reg;
        int regenc = reg << 3;

        Register base = addr.getBase();
        Register index = addr.getIndex();

        Scale scale = addr.getScale();
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
            boolean overriddenForce4Byte = force4Byte;
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
                } else {
                    if (evexDisp8Scale > 1 && !overriddenForce4Byte) {
                        if (disp % evexDisp8Scale == 0) {
                            int newDisp = disp / evexDisp8Scale;
                            if (isByte(newDisp)) {
                                disp = newDisp;
                                assert isByte(disp) && !overriddenForce4Byte;
                            }
                        } else {
                            overriddenForce4Byte = true;
                        }
                    }
                    if (isByte(disp) && !overriddenForce4Byte) {
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
                }
            } else if (base.equals(rsp) || base.equals(r12)) {
                // [rsp + disp]
                if (disp == 0) {
                    // [rsp]
                    // [00 reg 100][00 100 100]
                    emitByte(0x04 | regenc);
                    emitByte(0x24);
                } else {
                    if (evexDisp8Scale > 1 && !overriddenForce4Byte) {
                        if (disp % evexDisp8Scale == 0) {
                            int newDisp = disp / evexDisp8Scale;
                            if (isByte(newDisp)) {
                                disp = newDisp;
                                assert isByte(disp) && !overriddenForce4Byte;
                            }
                        } else {
                            overriddenForce4Byte = true;
                        }
                    }
                    if (isByte(disp) && !overriddenForce4Byte) {
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
                }
            } else {
                // [base + disp]
                assert !base.equals(rsp) && !base.equals(r12) : "illegal addressing mode";
                if (disp == 0 && !base.equals(rbp) && !base.equals(r13)) {
                    // [base]
                    // [00 reg base]
                    emitByte(0x00 | regenc | baseenc);
                } else {
                    if (evexDisp8Scale > 1 && !overriddenForce4Byte) {
                        if (disp % evexDisp8Scale == 0) {
                            int newDisp = disp / evexDisp8Scale;
                            if (isByte(newDisp)) {
                                disp = newDisp;
                                assert isByte(disp) && !overriddenForce4Byte;
                            }
                        } else {
                            overriddenForce4Byte = true;
                        }
                    }
                    if (isByte(disp) && !overriddenForce4Byte) {
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
    }

    private interface SIMDEncoder {

        void simdPrefix(Register xreg, Register nds, AMD64Address adr, int sizePrefix, int opcodeEscapePrefix, boolean isRexW);

        void simdPrefix(Register dst, Register nds, Register src, int sizePrefix, int opcodeEscapePrefix, boolean isRexW);

    }

    private class SSEEncoderImpl implements SIMDEncoder {

        @Override
        public void simdPrefix(Register xreg, Register nds, AMD64Address adr, int sizePrefix, int opcodeEscapePrefix, boolean isRexW) {
            assert (!nds.isValid()) || nds.equals(xreg);
            if (sizePrefix > 0) {
                emitByte(sizePrefix);
            }
            if (isRexW) {
                prefixq(adr, xreg);
            } else {
                prefix(adr, xreg);
            }
            if (opcodeEscapePrefix > 0xFF) {
                emitShort(opcodeEscapePrefix);
            } else if (opcodeEscapePrefix > 0) {
                emitByte(opcodeEscapePrefix);
            }
        }

        @Override
        public void simdPrefix(Register dst, Register nds, Register src, int sizePrefix, int opcodeEscapePrefix, boolean isRexW) {
            assert (!nds.isValid()) || nds.equals(dst) || nds.equals(src);
            if (sizePrefix > 0) {
                emitByte(sizePrefix);
            }
            if (isRexW) {
                prefixq(dst, src);
            } else {
                prefix(dst, src);
            }
            if (opcodeEscapePrefix > 0xFF) {
                emitShort(opcodeEscapePrefix);
            } else if (opcodeEscapePrefix > 0) {
                emitByte(opcodeEscapePrefix);
            }
        }
    }

    public static final class VEXPrefixConfig {
        public static final int L128 = 0;
        public static final int L256 = 1;
        public static final int LZ = 0;

        public static final int W0 = 0;
        public static final int W1 = 1;
        public static final int WIG = 0;

        public static final int P_ = 0x0;
        public static final int P_66 = 0x1;
        public static final int P_F3 = 0x2;
        public static final int P_F2 = 0x3;

        public static final int M_0F = 0x1;
        public static final int M_0F38 = 0x2;
        public static final int M_0F3A = 0x3;

        private VEXPrefixConfig() {
        }
    }

    private class VEXEncoderImpl implements SIMDEncoder {

        private int sizePrefixToPP(int sizePrefix) {
            switch (sizePrefix) {
                case 0x66:
                    return P_66;
                case 0xF2:
                    return P_F2;
                case 0xF3:
                    return P_F3;
                default:
                    return P_;
            }
        }

        private int opcodeEscapePrefixToMMMMM(int opcodeEscapePrefix) {
            switch (opcodeEscapePrefix) {
                case 0x0F:
                    return M_0F;
                case 0x380F:
                    return M_0F38;
                case 0x3A0F:
                    return M_0F3A;
                default:
                    return 0;
            }
        }

        @Override
        public void simdPrefix(Register reg, Register nds, AMD64Address rm, int sizePrefix, int opcodeEscapePrefix, boolean isRexW) {
            assert reg.encoding < 16 : "encoding out of range: " + reg.encoding;
            assert nds.encoding < 16 : "encoding out of range: " + nds.encoding;
            emitVEX(L128, sizePrefixToPP(sizePrefix), opcodeEscapePrefixToMMMMM(opcodeEscapePrefix), isRexW ? W1 : W0, getRXB(reg, rm), nds.isValid() ? nds.encoding : 0, true);
        }

        @Override
        public void simdPrefix(Register dst, Register nds, Register src, int sizePrefix, int opcodeEscapePrefix, boolean isRexW) {
            assert dst.encoding < 16 : "encoding out of range: " + dst.encoding;
            assert src.encoding < 16 : "encoding out of range: " + src.encoding;
            assert nds.encoding < 16 : "encoding out of range: " + nds.encoding;
            emitVEX(L128, sizePrefixToPP(sizePrefix), opcodeEscapePrefixToMMMMM(opcodeEscapePrefix), isRexW ? W1 : W0, getRXB(dst, src), nds.isValid() ? nds.encoding : 0, true);
        }
    }

    protected final void simdPrefix(Register xreg, Register nds, AMD64Address adr, OperandSize size, int overriddenSizePrefix, int opcodeEscapePrefix, boolean isRexW) {
        simdEncoder.simdPrefix(xreg, nds, adr, overriddenSizePrefix != 0 ? overriddenSizePrefix : size.sizePrefix, opcodeEscapePrefix, isRexW);
    }

    protected final void simdPrefix(Register xreg, Register nds, AMD64Address adr, OperandSize size, int opcodeEscapePrefix, boolean isRexW) {
        simdEncoder.simdPrefix(xreg, nds, adr, size.sizePrefix, opcodeEscapePrefix, isRexW);
    }

    protected final void simdPrefix(Register dst, Register nds, Register src, OperandSize size, int overriddenSizePrefix, int opcodeEscapePrefix, boolean isRexW) {
        simdEncoder.simdPrefix(dst, nds, src, overriddenSizePrefix != 0 ? overriddenSizePrefix : size.sizePrefix, opcodeEscapePrefix, isRexW);
    }

    protected final void simdPrefix(Register dst, Register nds, Register src, OperandSize size, int opcodeEscapePrefix, boolean isRexW) {
        simdEncoder.simdPrefix(dst, nds, src, size.sizePrefix, opcodeEscapePrefix, isRexW);
    }

 // @formatter:off
 //
 // Instruction Format and VEX illustrated below (optional []):
 //
 // #of bytes:    2,3      1       1       1       1,2,4       1
 // [Prefixes]    VEX   OpCode   ModR/M  [SIB]   [Disp8*N] [Immediate]
 //                                             [Disp16,32]
 //
 // VEX: 0xC4 | P1 | P2
 //
 //     7   6   5   4   3   2   1   0
 // P1  R   X   B   m   m   m   m   m      P[ 7:0]
 // P2  W   v   v   v   v   L   p   p      P[15:8]
 //
 // VEX: 0xC5 | B1
 //
 //     7   6   5   4   3   2   1   0
 // P1  R   v   v   v   v   L   p   p      P[7:0]
 //
 // Figure. Bit Field Layout of the VEX Prefix
 //
 // Table. VEX Prefix Bit Field Functional Grouping
 //
 // Notation        Bit field Group        Position        Comment
 // ----------  -------------------------  --------  -------------------
 // VEX.RXB     Next-8 register specifier  P[7:5]    Combine with ModR/M.reg, ModR/M.rm (base, index/vidx).
 // VEX.R       REX.R inverse              P[7]      Combine with EVEX.R and ModR/M.reg.
 // VEX.X       REX.X inverse              P[6]      Combine with EVEX.B and ModR/M.rm, when SIB/VSIB absent.
 // VEX.B       REX.B inverse              P[5]
 // VEX.mmmmmm  0F, 0F_38, 0F_3A encoding  P[4:0]    b01/0x0F, b10/0F_38, b11/0F_3A (all other reserved)
 //
 // VEX.W       Opcode specific            P[15]
 // VEX.vvvv    A register specifier       P[14:11]  In inverse form, b1111 if not used.
 //                                        P[6:3]
 // VEX.L       Vector length/RC           P[10]     b0/scalar or 128b vec, b1/256b vec.
 //                                        P[2]
 // VEX.pp      Compressed legacy prefix   P[9:8]    b00/None, b01/0x66, b10/0xF3, b11/0xF2
 //                                        P[1:0]
 // @formatter:on

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
    protected final void emitVEX(int l, int pp, int mmmmm, int w, int rxb, int vvvv, boolean checkAVX) {
        assert !checkAVX || ((AMD64) target.arch).getFeatures().contains(CPUFeature.AVX) : "emitting VEX prefix on a CPU without AVX support";

        assert l == L128 || l == L256 : "invalid value for VEX.L";
        assert pp == P_ || pp == P_66 || pp == P_F3 || pp == P_F2 : "invalid value for VEX.pp";
        assert mmmmm == M_0F || mmmmm == M_0F38 || mmmmm == M_0F3A : "invalid value for VEX.m-mmmm";
        assert w == W0 || w == W1 : "invalid value for VEX.W";

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

            emitByte(Prefix.VEX2);
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

            emitByte(Prefix.VEX3);
            emitByte(byte2);
            emitByte(byte3);
        }
    }

    public static int getLFlag(AVXSize size) {
        switch (size) {
            case XMM:
                return L128;
            case YMM:
                return L256;
            case ZMM:
                return L512;
            default:
                return LZ;
        }
    }

    public final void vexPrefix(Register dst, Register nds, Register src, AVXSize size, int pp, int mmmmm, int w, boolean checkAVX) {
        emitVEX(getLFlag(size), pp, mmmmm, w, getRXB(dst, src), nds.isValid() ? nds.encoding() : 0, checkAVX);
    }

    public final void vexPrefix(Register dst, Register nds, AMD64Address src, AVXSize size, int pp, int mmmmm, int w, boolean checkAVX) {
        emitVEX(getLFlag(size), pp, mmmmm, w, getRXB(dst, src), nds.isValid() ? nds.encoding() : 0, checkAVX);
    }

    protected static final class EVEXPrefixConfig {
        public static final int L512 = 2;
        public static final int LIG = 0;

        public static final int Z0 = 0x0;
        public static final int Z1 = 0x1;

        public static final int B0 = 0x0;
        public static final int B1 = 0x1;

        private EVEXPrefixConfig() {
        }
    }

    private static final int NOT_SUPPORTED_VECTOR_LENGTH = -1;

    /**
     * EVEX-encoded instructions use a compressed displacement scheme by multiplying disp8 with a
     * scaling factor N depending on the tuple type and the vector length.
     *
     * Reference: Intel Software Developer's Manual Volume 2, Section 2.6.5
     */
    protected enum EVEXTuple {
        FV_NO_BROADCAST_32BIT(16, 32, 64),
        FV_BROADCAST_32BIT(4, 4, 4),
        FV_NO_BROADCAST_64BIT(16, 32, 64),
        FV_BROADCAST_64BIT(8, 8, 8),
        HV_NO_BROADCAST_32BIT(8, 16, 32),
        HV_BROADCAST_32BIT(4, 4, 4),
        FVM(16, 32, 64),
        T1S_8BIT(1, 1, 1),
        T1S_16BIT(2, 2, 2),
        T1S_32BIT(4, 4, 4),
        T1S_64BIT(8, 8, 8),
        T1F_32BIT(4, 4, 4),
        T1F_64BIT(8, 8, 8),
        T2_32BIT(8, 8, 8),
        T2_64BIT(NOT_SUPPORTED_VECTOR_LENGTH, 16, 16),
        T4_32BIT(NOT_SUPPORTED_VECTOR_LENGTH, 16, 16),
        T4_64BIT(NOT_SUPPORTED_VECTOR_LENGTH, NOT_SUPPORTED_VECTOR_LENGTH, 32),
        T8_32BIT(NOT_SUPPORTED_VECTOR_LENGTH, NOT_SUPPORTED_VECTOR_LENGTH, 32),
        HVM(8, 16, 32),
        QVM(4, 8, 16),
        OVM(2, 4, 8),
        M128(16, 16, 16),
        DUP(8, 32, 64);

        private final int scalingFactorVL128;
        private final int scalingFactorVL256;
        private final int scalingFactorVL512;

        EVEXTuple(int scalingFactorVL128, int scalingFactorVL256, int scalingFactorVL512) {
            this.scalingFactorVL128 = scalingFactorVL128;
            this.scalingFactorVL256 = scalingFactorVL256;
            this.scalingFactorVL512 = scalingFactorVL512;
        }

        private static int verifyScalingFactor(int scalingFactor) {
            if (scalingFactor == NOT_SUPPORTED_VECTOR_LENGTH) {
                throw GraalError.shouldNotReachHere("Invalid scaling factor.");
            }
            return scalingFactor;
        }

        public int getDisp8ScalingFactor(AVXSize size) {
            switch (size) {
                case XMM:
                    return verifyScalingFactor(scalingFactorVL128);
                case YMM:
                    return verifyScalingFactor(scalingFactorVL256);
                case ZMM:
                    return verifyScalingFactor(scalingFactorVL512);
                default:
                    throw GraalError.shouldNotReachHere("Unsupported vector size.");
            }
        }
    }

 // @formatter:off
 //
 // Instruction Format and EVEX illustrated below (optional []):
 //
 // #of bytes:      4       1       1       1       1,2,4       1
 // [Prefixes]    EVEX   OpCode   ModR/M  [SIB]   [Disp8*N] [Immediate]
 //                                              [Disp16,32]
 //
 // The EVEX prefix is a 4-byte prefix, with the first two bytes derived from unused encoding
 // form of the 32-bit-mode-only BOUND instruction. The layout of the EVEX prefix is shown in
 // the figure below. The first byte must be 0x62, followed by three pay-load bytes, denoted
 // as P1, P2, and P3 individually or collectively as P[23:0] (see below).
 //
 // EVEX: 0x62 | P1 | P2 | P3
 //
 //     7   6   5   4   3   2   1   0
 // P1  R   X   B   R'  0   0   m   m      P[ 7: 0]
 // P2  W   v   v   v   v   1   p   p      P[15: 8]
 // P3  z   L'  L   b   V'  a   a   a      P[23:16]
 //
 // Figure. Bit Field Layout of the EVEX Prefix
 //
 // Table. EVEX Prefix Bit Field Functional Grouping
 //
 // Notation        Bit field Group        Position        Comment
 // ---------  --------------------------  --------  -----------------------
 // EVEX.RXB   Next-8 register specifier   P[7:5]    Combine with ModR/M.reg, ModR/M.rm (base, index/vidx).
 // EVEX.X     High-16 register specifier  P[6]      Combine with EVEX.B and ModR/M.rm, when SIB/VSIB absent.
 // EVEX.R'    High-16 register specifier  P[4]      Combine with EVEX.R and ModR/M.reg.
 // --         Reserved                    P[3:2]    Must be 0.
 // EVEX.mm    Compressed legacy escape    P[1:0]    Identical to low two bits of VEX.mmmmm.
 //
 // EVEX.W     Osize promotion/Opcode ext  P[15]
 // EVEX.vvvv  NDS register specifier      P[14:11]  Same as VEX.vvvv.
 // --         Fixed Value                 P[10]     Must be 1.
 // EVEX.pp    Compressed legacy prefix    P[9:8]    Identical to VEX.pp.
 //
 // EVEX.z     Zeroing/Merging             P[23]
 // EVEX.L'L   Vector length/RC            P[22:21]
 // EVEX.b     Broadcast/RC/SAE Context    P[20]
 // EVEX.V'    High-16 NDS/VIDX register   P[19]     Combine with EVEX.vvvv or VSIB when present.
 // EVEX.aaa   Embedded opmask register    P[18:16]
 //
 // @formatter:on

    /**
     * Low-level function to encode and emit the EVEX prefix.
     * <p>
     * 62 [0 1 1 0 0 0 1 0]<br>
     * P1 [R X B R'0 0 m m]<br>
     * P2 [W v v v v 1 p p]<br>
     * P3 [z L'L b V'a a a]
     * <p>
     * The pp field encodes an extension to the opcode:<br>
     * 00: no extension<br>
     * 01: 66<br>
     * 10: F3<br>
     * 11: F2
     * <p>
     * The mm field encodes the leading bytes of the opcode:<br>
     * 01: implied 0F leading opcode byte<br>
     * 10: implied 0F 38 leading opcode bytes<br>
     * 11: implied 0F 3A leading opcode bytes
     * <p>
     * The z field encodes the merging mode (merge or zero).
     * <p>
     * The b field encodes the source broadcast or data rounding modes.
     * <p>
     * The aaa field encodes the operand mask register.
     */
    private void emitEVEX(int l, int pp, int mm, int w, int rxb, int reg, int vvvvv, int z, int b, int aaa) {
        assert ((AMD64) target.arch).getFeatures().contains(CPUFeature.AVX512F) : "emitting EVEX prefix on a CPU without AVX512 support";

        assert l == L128 || l == L256 || l == L512 : "invalid value for EVEX.L'L";
        assert pp == P_ || pp == P_66 || pp == P_F3 || pp == P_F2 : "invalid value for EVEX.pp";
        assert mm == M_0F || mm == M_0F38 || mm == M_0F3A : "invalid value for EVEX.mm";
        assert w == W0 || w == W1 : "invalid value for EVEX.W";

        assert (rxb & 0x07) == rxb : "invalid value for EVEX.RXB";
        assert (reg & 0x1F) == reg : "invalid value for EVEX.R'";
        assert (vvvvv & 0x1F) == vvvvv : "invalid value for EVEX.V'vvvv";

        assert z == Z0 || z == Z1 : "invalid value for EVEX.z";
        assert b == B0 || b == B1 : "invalid value for EVEX.b";
        assert (aaa & 0x07) == aaa : "invalid value for EVEX.aaa";

        emitByte(Prefix.EVEX);
        int p1 = 0;
        p1 |= ((rxb ^ 0x07) & 0x07) << 5;
        p1 |= reg < 16 ? 0x10 : 0;
        p1 |= mm;
        emitByte(p1);

        int p2 = 0;
        p2 |= w << 7;
        p2 |= ((vvvvv ^ 0x0F) & 0x0F) << 3;
        p2 |= 0x04;
        p2 |= pp;
        emitByte(p2);

        int p3 = 0;
        p3 |= z << 7;
        p3 |= l << 5;
        p3 |= b << 4;
        p3 |= vvvvv < 16 ? 0x08 : 0;
        p3 |= aaa;
        emitByte(p3);
    }

    /**
     * Get RXB bits for register-register instructions in EVEX-encoding, where ModRM.rm contains a
     * register index. The R bit extends the ModRM.reg field and the X and B bits extends the
     * ModRM.rm field.
     */
    private static int getRXBForEVEX(Register reg, Register rm) {
        int rxb = (reg == null ? 0 : reg.encoding & 0x08) >> 1;
        rxb |= (rm == null ? 0 : rm.encoding & 0x018) >> 3;
        return rxb;
    }

    /**
     * Helper method for emitting EVEX prefix in the form of RRRR.
     */
    protected final void evexPrefix(Register dst, Register mask, Register nds, Register src, AVXSize size, int pp, int mm, int w, int z, int b) {
        assert !mask.isValid() || inRC(MASK, mask);
        emitEVEX(getLFlag(size), pp, mm, w, getRXBForEVEX(dst, src), dst.encoding, nds.isValid() ? nds.encoding() : 0, z, b, mask.isValid() ? mask.encoding : 0);
    }

    /**
     * Helper method for emitting EVEX prefix in the form of RRRM. Because the memory addressing in
     * EVEX-encoded instructions employ a compressed displacement scheme when using disp8 form, the
     * user of this API should make sure to encode the operands using
     * {@link #emitEVEXOperandHelper(Register, AMD64Address, int, int)}.
     */
    protected final void evexPrefix(Register dst, Register mask, Register nds, AMD64Address src, AVXSize size, int pp, int mm, int w, int z, int b) {
        assert !mask.isValid() || inRC(MASK, mask);
        emitEVEX(getLFlag(size), pp, mm, w, getRXB(dst, src), dst.encoding, nds.isValid() ? nds.encoding() : 0, z, b, mask.isValid() ? mask.encoding : 0);
    }

}
