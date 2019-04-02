/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2018, Red Hat Inc. All rights reserved.
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


package org.graalvm.compiler.asm.aarch64;

import static jdk.vm.ci.aarch64.AArch64.cpuRegisters;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.ADD;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.ADDS;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.ADR;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.ADRP;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.AND;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.ANDS;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.ASRV;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.BFM;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.BIC;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.BICS;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.BLR;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.BR;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.BRK;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.CAS;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.CCMP;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.CLREX;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.CLS;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.CLZ;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.CSEL;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.CSINC;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.CSNEG;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.DMB;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.EON;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.EOR;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.EXTR;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.FABS;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.FADD;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.FCCMP;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.FCMP;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.FCMPZERO;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.FCSEL;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.FCVTDS;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.FCVTSD;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.FCVTZS;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.FDIV;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.FMADD;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.FMOV;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.FMSUB;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.FMUL;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.FNEG;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.FRINTM;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.FRINTN;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.FRINTP;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.FRINTZ;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.FSQRT;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.FSUB;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.HINT;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.HLT;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.LDADD;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.LDAR;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.LDAXR;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.LDP;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.LDR;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.LDRS;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.LDXR;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.LSLV;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.LSRV;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.MADD;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.MOVK;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.MOVN;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.MOVZ;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.MRS;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.MSUB;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.ORN;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.ORR;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.RBIT;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.RET;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.REVW;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.REVX;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.RORV;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.SBFM;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.SCVTF;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.SDIV;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.STLR;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.STLXR;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.STP;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.STR;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.STXR;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.SUB;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.SUBS;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.SWP;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.TBZ;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.TBNZ;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.UBFM;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.UDIV;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.InstructionType.FP32;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.InstructionType.FP64;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.InstructionType.General32;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.InstructionType.General64;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.InstructionType.floatFromSize;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.InstructionType.generalFromSize;
import static jdk.vm.ci.aarch64.AArch64.CPU;
import static jdk.vm.ci.aarch64.AArch64.SIMD;
import static jdk.vm.ci.aarch64.AArch64.r0;
import static jdk.vm.ci.aarch64.AArch64.sp;
import static jdk.vm.ci.aarch64.AArch64.zr;

import java.util.Arrays;

import org.graalvm.compiler.asm.Assembler;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.asm.aarch64.AArch64Address.AddressingMode;
import org.graalvm.compiler.debug.GraalError;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.aarch64.AArch64.CPUFeature;
import jdk.vm.ci.aarch64.AArch64.Flag;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.TargetDescription;

public abstract class AArch64Assembler extends Assembler {

    public static class LogicalImmediateTable {

        private static final Immediate[] IMMEDIATE_TABLE = buildImmediateTable();

        private static final int ImmediateOffset = 10;
        private static final int ImmediateRotateOffset = 16;
        private static final int ImmediateSizeOffset = 22;

        /**
         * Specifies whether immediate can be represented in all cases (YES), as a 64bit instruction
         * (SIXTY_FOUR_BIT_ONLY) or not at all (NO).
         */
        enum Representable {
            YES,
            SIXTY_FOUR_BIT_ONLY,
            NO
        }

        /**
         * Tests whether an immediate can be encoded for logical instructions.
         *
         * @param is64bit if true immediate is considered a 64-bit pattern. If false we may use a
         *            64-bit instruction to load the 32-bit pattern into a register.
         * @return enum specifying whether immediate can be used for 32- and 64-bit logical
         *         instructions ({@code #Representable.YES}), for 64-bit instructions only (
         *         {@link Representable#SIXTY_FOUR_BIT_ONLY}) or not at all (
         *         {@link Representable#NO}).
         */
        public static Representable isRepresentable(boolean is64bit, long immediate) {
            int pos = getLogicalImmTablePos(is64bit, immediate);
            if (pos < 0) {
                // if 32bit instruction we can try again as 64bit immediate which may succeed.
                // i.e. 0xffffffff fails as a 32bit immediate but works as 64bit one.
                if (!is64bit) {
                    assert NumUtil.isUnsignedNbit(32, immediate);
                    pos = getLogicalImmTablePos(true, immediate);
                    return pos >= 0 ? Representable.SIXTY_FOUR_BIT_ONLY : Representable.NO;
                }
                return Representable.NO;
            }
            Immediate imm = IMMEDIATE_TABLE[pos];
            return imm.only64bit() ? Representable.SIXTY_FOUR_BIT_ONLY : Representable.YES;
        }

        public static Representable isRepresentable(int immediate) {
            return isRepresentable(false, immediate & 0xFFFF_FFFFL);
        }

        public static int getLogicalImmEncoding(boolean is64bit, long value) {
            int pos = getLogicalImmTablePos(is64bit, value);
            assert pos >= 0 : "Value cannot be represented as logical immediate: " + value + ", is64bit=" + is64bit;
            Immediate imm = IMMEDIATE_TABLE[pos];
            assert is64bit || !imm.only64bit() : "Immediate can only be represented for 64bit, but 32bit instruction specified";
            return IMMEDIATE_TABLE[pos].encoding;
        }

        /**
         * @param is64bit if true also allow 64-bit only encodings to be returned.
         * @return If positive the return value is the position into the IMMEDIATE_TABLE for the
         *         given immediate, if negative the immediate cannot be encoded.
         */
        private static int getLogicalImmTablePos(boolean is64bit, long value) {
            Immediate imm;
            if (!is64bit) {
                // 32bit instructions can only have 32bit immediates.
                if (!NumUtil.isUnsignedNbit(32, value)) {
                    return -1;
                }
                // If we have a 32bit instruction (and therefore immediate) we have to duplicate it
                // across 64bit to find it in the table.
                imm = new Immediate(value << 32 | value);
            } else {
                imm = new Immediate(value);
            }
            int pos = Arrays.binarySearch(IMMEDIATE_TABLE, imm);
            if (pos < 0) {
                return -1;
            }
            if (!is64bit && IMMEDIATE_TABLE[pos].only64bit()) {
                return -1;
            }
            return pos;
        }

        /**
         * To quote 5.4.2: [..] an immediate is a 32 or 64 bit pattern viewed as a vector of
         * identical elements of size e = 2, 4, 8, 16, 32 or (in the case of bimm64) 64 bits. Each
         * element contains the same sub-pattern: a single run of 1 to e-1 non-zero bits, rotated by
         * 0 to e-1 bits. It is encoded in the following: 10-16: rotation amount (6bit) starting
         * from 1s in the LSB (i.e. 0111->1011->1101->1110) 16-22: This stores a combination of the
         * number of set bits and the pattern size. The pattern size is encoded as follows (x is
         * used to store the number of 1 bits - 1) e pattern 2 1111xx 4 1110xx 8 110xxx 16 10xxxx 32
         * 0xxxxx 64 xxxxxx 22: if set we have an instruction with 64bit pattern?
         */
        private static final class Immediate implements Comparable<Immediate> {
            public final long imm;
            public final int encoding;

            Immediate(long imm, boolean is64, int s, int r) {
                this.imm = imm;
                this.encoding = computeEncoding(is64, s, r);
            }

            // Used to be able to binary search for an immediate in the table.
            Immediate(long imm) {
                this(imm, false, 0, 0);
            }

            /**
             * Returns true if this pattern is only representable as 64bit.
             */
            public boolean only64bit() {
                return (encoding & (1 << ImmediateSizeOffset)) != 0;
            }

            private static int computeEncoding(boolean is64, int s, int r) {
                int sf = is64 ? 1 : 0;
                return sf << ImmediateSizeOffset | r << ImmediateRotateOffset | s << ImmediateOffset;
            }

            @Override
            public int compareTo(Immediate o) {
                return Long.compare(imm, o.imm);
            }
        }

        private static Immediate[] buildImmediateTable() {
            final int nrImmediates = 5334;
            final Immediate[] table = new Immediate[nrImmediates];
            int nrImms = 0;
            for (int logE = 1; logE <= 6; logE++) {
                int e = 1 << logE;
                long mask = NumUtil.getNbitNumberLong(e);
                for (int nrOnes = 1; nrOnes < e; nrOnes++) {
                    long val = (1L << nrOnes) - 1;
                    // r specifies how much we rotate the value
                    for (int r = 0; r < e; r++) {
                        long immediate = (val >>> r | val << (e - r)) & mask;
                        // Duplicate pattern to fill whole 64bit range.
                        switch (logE) {
                            case 1:
                                immediate |= immediate << 2;
                                immediate |= immediate << 4;
                                immediate |= immediate << 8;
                                immediate |= immediate << 16;
                                immediate |= immediate << 32;
                                break;
                            case 2:
                                immediate |= immediate << 4;
                                immediate |= immediate << 8;
                                immediate |= immediate << 16;
                                immediate |= immediate << 32;
                                break;
                            case 3:
                                immediate |= immediate << 8;
                                immediate |= immediate << 16;
                                immediate |= immediate << 32;
                                break;
                            case 4:
                                immediate |= immediate << 16;
                                immediate |= immediate << 32;
                                break;
                            case 5:
                                immediate |= immediate << 32;
                                break;
                        }
                        // 5 - logE can underflow to -1, but we shift this bogus result
                        // out of the masked area.
                        int sizeEncoding = (1 << (5 - logE)) - 1;
                        int s = ((sizeEncoding << (logE + 1)) & 0x3f) | (nrOnes - 1);
                        table[nrImms++] = new Immediate(immediate, /* is64bit */e == 64, s, r);
                    }
                }
            }
            Arrays.sort(table);
            assert nrImms == nrImmediates : nrImms + " instead of " + nrImmediates + " in table.";
            assert checkDuplicates(table) : "Duplicate values in table.";
            return table;
        }

        private static boolean checkDuplicates(Immediate[] table) {
            for (int i = 0; i < table.length - 1; i++) {
                if (table[i].imm >= table[i + 1].imm) {
                    return false;
                }
            }
            return true;
        }
    }

    private static final int RdOffset = 0;
    private static final int Rs1Offset = 5;
    private static final int Rs2Offset = 16;
    private static final int Rs3Offset = 10;
    private static final int RtOffset = 0;
    private static final int RnOffset = 5;
    private static final int Rt2Offset = 10;

    /* Helper functions */
    private static int rd(Register reg) {
        return reg.encoding << RdOffset;
    }

    private static int rs1(Register reg) {
        return reg.encoding << Rs1Offset;
    }

    private static int rs2(Register reg) {
        return reg.encoding << Rs2Offset;
    }

    private static int rs3(Register reg) {
        return reg.encoding << Rs3Offset;
    }

    private static int rt(Register reg) {
        return reg.encoding << RtOffset;
    }

    private static int rt2(Register reg) {
        return reg.encoding << Rt2Offset;
    }

    private static int rn(Register reg) {
        return reg.encoding << RnOffset;
    }

    private static int maskField(int sizeInBits, int n) {
        assert NumUtil.isSignedNbit(sizeInBits, n);
        return n & NumUtil.getNbitNumberInt(sizeInBits);
    }

    /**
     * Enumeration of all different instruction kinds: General32/64 are the general instructions
     * (integer, branch, etc.), for 32-, respectively 64-bit operands. FP32/64 is the encoding for
     * the 32/64bit float operations
     */
    protected enum InstructionType {
        General32(0b00 << 30, 32, true),
        General64(0b10 << 30, 64, true),
        FP32(0x00000000, 32, false),
        FP64(0x00400000, 64, false);

        public final int encoding;
        public final int width;
        public final boolean isGeneral;

        InstructionType(int encoding, int width, boolean isGeneral) {
            this.encoding = encoding;
            this.width = width;
            this.isGeneral = isGeneral;
        }

        public static InstructionType generalFromSize(int size) {
            assert size == 32 || size == 64;
            return size == 32 ? General32 : General64;
        }

        public static InstructionType floatFromSize(int size) {
            assert size == 32 || size == 64;
            return size == 32 ? FP32 : FP64;
        }

    }

    private static final int ImmediateOffset = 10;
    private static final int ImmediateRotateOffset = 16;
    private static final int ImmediateSizeOffset = 22;
    private static final int ExtendTypeOffset = 13;

    private static final int AddSubImmOp = 0x11000000;
    private static final int AddSubShift12 = 0b01 << 22;
    private static final int AddSubSetFlag = 0x20000000;

    private static final int LogicalImmOp = 0x12000000;

    private static final int MoveWideImmOp = 0x12800000;
    private static final int MoveWideImmOffset = 5;
    private static final int MoveWideShiftOffset = 21;

    private static final int BitfieldImmOp = 0x13000000;

    private static final int AddSubShiftedOp = 0x0B000000;
    private static final int ShiftTypeOffset = 22;

    private static final int AddSubExtendedOp = 0x0B200000;

    private static final int MulOp = 0x1B000000;
    private static final int DataProcessing1SourceOp = 0x5AC00000;
    private static final int DataProcessing2SourceOp = 0x1AC00000;

    private static final int Fp1SourceOp = 0x1E204000;
    private static final int Fp2SourceOp = 0x1E200800;
    private static final int Fp3SourceOp = 0x1F000000;

    private static final int FpConvertOp = 0x1E200000;
    private static final int FpImmOp = 0x1E201000;
    private static final int FpImmOffset = 13;

    private static final int FpCmpOp = 0x1E202000;
    private static final int FpCmpeOp = 0x1E202010;

    private static final int PcRelImmHiOffset = 5;
    private static final int PcRelImmLoOffset = 29;

    private static final int PcRelImmOp = 0x10000000;

    private static final int UnconditionalBranchImmOp = 0x14000000;
    private static final int UnconditionalBranchRegOp = 0xD6000000;
    private static final int CompareBranchOp = 0x34000000;

    private static final int ConditionalBranchImmOffset = 5;

    private static final int ConditionalSelectOp = 0x1A800000;
    private static final int ConditionalConditionOffset = 12;

    private static final int LoadStoreScaledOp = 0b111_0_01_00 << 22;
    private static final int LoadStoreUnscaledOp = 0b111_0_00_00 << 22;

    private static final int LoadStoreRegisterOp = 0b111_0_00_00_1 << 21 | 0b10 << 10;

    private static final int LoadLiteralOp = 0x18000000;

    private static final int LoadStorePostIndexedOp = 0b111_0_00_00_0 << 21 | 0b01 << 10;
    private static final int LoadStorePreIndexedOp = 0b111_0_00_00_0 << 21 | 0b11 << 10;

    private static final int LoadStoreUnscaledImmOffset = 12;
    private static final int LoadStoreScaledImmOffset = 10;
    private static final int LoadStoreScaledRegOffset = 12;
    private static final int LoadStoreIndexedImmOffset = 12;
    private static final int LoadStoreTransferSizeOffset = 30;
    private static final int LoadStoreFpFlagOffset = 26;
    private static final int LoadLiteralImmeOffset = 5;

    private static final int LoadStorePairOp = 0b101_0 << 26;
    @SuppressWarnings("unused") private static final int LoadStorePairPostIndexOp = 0b101_0_001 << 23;
    @SuppressWarnings("unused") private static final int LoadStorePairPreIndexOp = 0b101_0_011 << 23;
    private static final int LoadStorePairImm7Offset = 15;

    private static final int LogicalShiftOp = 0x0A000000;

    private static final int ExceptionOp = 0xD4000000;
    private static final int SystemImmediateOffset = 5;

    @SuppressWarnings("unused") private static final int SimdImmediateOffset = 16;

    private static final int BarrierOp = 0xD503301F;
    private static final int BarrierKindOffset = 8;

    private static final int CASAcquireOffset = 22;
    private static final int CASReleaseOffset = 15;

    private static final int LDADDAcquireOffset = 23;
    private static final int LDADDReleaseOffset = 22;

    /**
     * Encoding for all instructions.
     */
    public enum Instruction {
        BCOND(0x54000000),
        CBNZ(0x01000000),
        CBZ(0x00000000),
        TBZ(0x36000000),
        TBNZ(0x37000000),

        B(0x00000000),
        BL(0x80000000),
        BR(0x001F0000),
        BLR(0x003F0000),
        RET(0x005F0000),

        LDR(0x00000000),
        LDRS(0x00800000),
        LDXR(0x081f7c00),
        LDAR(0x8dffc00),
        LDAXR(0x85ffc00),

        STR(0x00000000),
        STXR(0x08007c00),
        STLR(0x089ffc00),
        STLXR(0x0800fc00),

        LDP(0b1 << 22),
        STP(0b0 << 22),

        CAS(0x08A07C00),
        LDADD(0x38200000),
        SWP(0x38208000),

        ADR(0x00000000),
        ADRP(0x80000000),

        ADD(0x00000000),
        ADDS(ADD.encoding | AddSubSetFlag),
        SUB(0x40000000),
        SUBS(SUB.encoding | AddSubSetFlag),

        CCMP(0x7A400000),

        NOT(0x00200000),
        AND(0x00000000),
        BIC(AND.encoding | NOT.encoding),
        ORR(0x20000000),
        ORN(ORR.encoding | NOT.encoding),
        EOR(0x40000000),
        EON(EOR.encoding | NOT.encoding),
        ANDS(0x60000000),
        BICS(ANDS.encoding | NOT.encoding),

        ASRV(0x00002800),
        RORV(0x00002C00),
        LSRV(0x00002400),
        LSLV(0x00002000),

        CLS(0x00001400),
        CLZ(0x00001000),
        RBIT(0x00000000),
        REVX(0x00000C00),
        REVW(0x00000800),

        MOVN(0x00000000),
        MOVZ(0x40000000),
        MOVK(0x60000000),

        CSEL(0x00000000),
        CSNEG(0x40000400),
        CSINC(0x00000400),

        BFM(0x20000000),
        SBFM(0x00000000),
        UBFM(0x40000000),
        EXTR(0x13800000),

        MADD(0x00000000),
        MSUB(0x00008000),
        SDIV(0x00000C00),
        UDIV(0x00000800),

        FMOV(0x00000000),
        FMOVCPU2FPU(0x00070000),
        FMOVFPU2CPU(0x00060000),

        FCVTDS(0x00028000),
        FCVTSD(0x00020000),

        FCVTZS(0x00180000),
        SCVTF(0x00020000),

        FABS(0x00008000),
        FSQRT(0x00018000),
        FNEG(0x00010000),

        FRINTM(0x00050000),
        FRINTN(0x00040000),
        FRINTP(0x00048000),
        FRINTZ(0x00058000),

        FADD(0x00002000),
        FSUB(0x00003000),
        FMUL(0x00000000),
        FDIV(0x00001000),
        FMAX(0x00004000),
        FMIN(0x00005000),

        FMADD(0x00000000),
        FMSUB(0x00008000),

        FCMP(0x00000000),
        FCMPZERO(0x00000008),
        FCCMP(0x1E200400),
        FCSEL(0x1E200C00),

        INS(0x4e081c00),
        UMOV(0x4e083c00),

        CNT(0xe205800),
        USRA(0x6f001400),

        HLT(0x00400000),
        BRK(0x00200000),

        CLREX(0xd5033f5f),
        HINT(0xD503201F),
        DMB(0x000000A0),

        MRS(0xD5300000),
        MSR(0xD5100000),

        BLR_NATIVE(0xc0000000);

        public final int encoding;

        Instruction(int encoding) {
            this.encoding = encoding;
        }

    }

    public enum SystemRegister {
        FPCR(0b11, 0b011, 0b0100, 0b0100, 0b000),
        FPSR(0b11, 0b011, 0b0100, 0b0100, 0b001);

        SystemRegister(int op0, int op1, int crn, int crm, int op2) {
            this.op0 = op0;
            this.op1 = op1;
            this.crn = crn;
            this.crm = crm;
            this.op2 = op2;
        }

        public int encoding() {
            return op0 << 19 | op1 << 16 | crn << 12 | crm << 8 | op2 << 5;
        }

        private final int op0;
        private final int op1;
        private final int crn;
        private final int crm;
        private final int op2;
    }

    public enum ShiftType {
        LSL(0),
        LSR(1),
        ASR(2),
        ROR(3);

        public final int encoding;

        ShiftType(int encoding) {
            this.encoding = encoding;
        }
    }

    public enum ExtendType {
        UXTB(0),
        UXTH(1),
        UXTW(2),
        UXTX(3),
        SXTB(4),
        SXTH(5),
        SXTW(6),
        SXTX(7);

        public final int encoding;

        ExtendType(int encoding) {
            this.encoding = encoding;
        }
    }

    /**
     * Condition Flags for branches. See 4.3
     */
    public enum ConditionFlag {
        // Integer | Floating-point meanings
        /** Equal | Equal. */
        EQ(0x0),

        /** Not Equal | Not equal or unordered. */
        NE(0x1),

        /** Unsigned Higher or Same | Greater than, equal or unordered. */
        HS(0x2),

        /** Unsigned lower | less than. */
        LO(0x3),

        /** Minus (negative) | less than. */
        MI(0x4),

        /** Plus (positive or zero) | greater than, equal or unordered. */
        PL(0x5),

        /** Overflow set | unordered. */
        VS(0x6),

        /** Overflow clear | ordered. */
        VC(0x7),

        /** Unsigned higher | greater than or unordered. */
        HI(0x8),

        /** Unsigned lower or same | less than or equal. */
        LS(0x9),

        /** Signed greater than or equal | greater than or equal. */
        GE(0xA),

        /** Signed less than | less than or unordered. */
        LT(0xB),

        /** Signed greater than | greater than. */
        GT(0xC),

        /** Signed less than or equal | less than, equal or unordered. */
        LE(0xD),

        /** Always | always. */
        AL(0xE),

        /** Always | always (identical to AL, just to have valid 0b1111 encoding). */
        NV(0xF);

        public final int encoding;

        ConditionFlag(int encoding) {
            this.encoding = encoding;
        }

        /**
         * @return ConditionFlag specified by decoding.
         */
        public static ConditionFlag fromEncoding(int encoding) {
            return values()[encoding];
        }

        public ConditionFlag negate() {
            switch (this) {
                case EQ:
                    return NE;
                case NE:
                    return EQ;
                case HS:
                    return LO;
                case LO:
                    return HS;
                case MI:
                    return PL;
                case PL:
                    return MI;
                case VS:
                    return VC;
                case VC:
                    return VS;
                case HI:
                    return LS;
                case LS:
                    return HI;
                case GE:
                    return LT;
                case LT:
                    return GE;
                case GT:
                    return LE;
                case LE:
                    return GT;
                case AL:
                case NV:
                default:
                    throw GraalError.shouldNotReachHere();
            }
        }
    }

    public AArch64Assembler(TargetDescription target) {
        super(target);
    }

    public boolean supports(CPUFeature feature) {
        return ((AArch64) target.arch).getFeatures().contains(feature);
    }

    public boolean isFlagSet(Flag flag) {
        return ((AArch64) target.arch).getFlags().contains(flag);
    }

    /* Conditional Branch (5.2.1) */

    /**
     * Branch conditionally.
     *
     * @param condition may not be null.
     * @param imm21 Signed 21-bit offset, has to be word aligned.
     */
    protected void b(ConditionFlag condition, int imm21) {
        b(condition, imm21, -1);
    }

    /**
     * Branch conditionally. Inserts instruction into code buffer at pos.
     *
     * @param condition may not be null.
     * @param imm21 Signed 21-bit offset, has to be word aligned.
     * @param pos Position at which instruction is inserted into buffer. -1 means insert at end.
     */
    protected void b(ConditionFlag condition, int imm21, int pos) {
        if (pos == -1) {
            emitInt(Instruction.BCOND.encoding | getConditionalBranchImm(imm21) | condition.encoding);
        } else {
            emitInt(Instruction.BCOND.encoding | getConditionalBranchImm(imm21) | condition.encoding, pos);
        }
    }

    /**
     * Compare register and branch if non-zero.
     *
     * @param reg general purpose register. May not be null, zero-register or stackpointer.
     * @param size Instruction size in bits. Should be either 32 or 64.
     * @param imm21 Signed 21-bit offset, has to be word aligned.
     */
    protected void cbnz(int size, Register reg, int imm21) {
        conditionalBranchInstruction(reg, imm21, generalFromSize(size), Instruction.CBNZ, -1);
    }

    /**
     * Compare register and branch if non-zero.
     *
     * @param reg general purpose register. May not be null, zero-register or stackpointer.
     * @param size Instruction size in bits. Should be either 32 or 64.
     * @param imm21 Signed 21-bit offset, has to be word aligned.
     * @param pos Position at which instruction is inserted into buffer. -1 means insert at end.
     */
    protected void cbnz(int size, Register reg, int imm21, int pos) {
        conditionalBranchInstruction(reg, imm21, generalFromSize(size), Instruction.CBNZ, pos);
    }

    /**
     * Compare and branch if zero.
     *
     * @param reg general purpose register. May not be null, zero-register or stackpointer.
     * @param size Instruction size in bits. Should be either 32 or 64.
     * @param imm21 Signed 21-bit offset, has to be word aligned.
     */
    protected void cbz(int size, Register reg, int imm21) {
        conditionalBranchInstruction(reg, imm21, generalFromSize(size), Instruction.CBZ, -1);
    }

    /**
     * Compare register and branch if zero.
     *
     * @param reg general purpose register. May not be null, zero-register or stackpointer.
     * @param size Instruction size in bits. Should be either 32 or 64.
     * @param imm21 Signed 21-bit offset, has to be word aligned.
     * @param pos Position at which instruction is inserted into buffer. -1 means insert at end.
     */
    protected void cbz(int size, Register reg, int imm21, int pos) {
        conditionalBranchInstruction(reg, imm21, generalFromSize(size), Instruction.CBZ, pos);
    }

    /**
     * Test a single bit and branch if the bit is nonzero.
     *
     * @param reg general purpose register. May not be null, zero-register or stackpointer.
     * @param uimm6 Unsigned 6-bit bit index.
     * @param imm16 signed 16 bit offset
     */
    protected void tbnz(Register reg, int uimm6, int imm16) {
        tbnz(reg, uimm6, imm16, -1);
    }

    /**
     * Test a single bit and branch if the bit is zero.
     *
     * @param reg general purpose register. May not be null, zero-register or stackpointer.
     * @param uimm6 Unsigned 6-bit bit index.
     * @param imm16 signed 16 bit offset
     */
    protected void tbz(Register reg, int uimm6, int imm16) {
        tbz(reg, uimm6, imm16, -1);
    }

    /**
     * Test a single bit and branch if the bit is nonzero.
     *
     * @param reg general purpose register. May not be null, zero-register or stackpointer.
     * @param uimm6 Unsigned 6-bit bit index.
     * @param imm16 signed 16 bit offset
     * @param pos Position at which instruction is inserted into buffer. -1 means insert at end.
     */
    protected void tbnz(Register reg, int uimm6, int imm16, int pos) {
        assert reg.getRegisterCategory().equals(CPU);
        assert NumUtil.isUnsignedNbit(6, uimm6);
        assert NumUtil.isSignedNbit(16, imm16) : String.format("Offset value must fit in 16 bits signed: 0x%x", imm16);
        assert (imm16 & 3) == 0 : String.format("Lower two bits must be zero: 0x%x", imm16 & 3);
        // size bit is overloaded as top bit of uimm6 bit index
        int size = (((uimm6 >> 5) & 1) == 0 ? 32 : 64);
        // remaining 5 bits are encoded lower down
        int uimm5 = uimm6 & 0x1F;
        int imm14 = (imm16 & NumUtil.getNbitNumberInt(16)) >> 2;
        InstructionType type = generalFromSize(size);
        int encoding = type.encoding | TBNZ.encoding | (uimm5 << 19) | (imm14 << 5) | rd(reg);
        if (pos == -1) {
            emitInt(encoding);
        } else {
            emitInt(encoding, pos);
        }
    }

    /**
     * Test a single bit and branch if the bit is zero.
     *
     * @param reg general purpose register. May not be null, zero-register or stackpointer.
     * @param uimm6 Unsigned 6-bit bit index.
     * @param imm16 signed 16 bit offset
     * @param pos Position at which instruction is inserted into buffer. -1 means insert at end.
     */
    protected void tbz(Register reg, int uimm6, int imm16, int pos) {
        assert reg.getRegisterCategory().equals(CPU);
        assert NumUtil.isUnsignedNbit(6, uimm6);
        assert NumUtil.isSignedNbit(16, imm16) : String.format("Offset value must fit in 16 bits signed: 0x%x", imm16);
        assert (imm16 & 3) == 0 : String.format("Lower two bits must be zero: 0x%x", imm16 & 3);
        // size bit is overloaded as top bit of uimm6 bit index
        int size = (((uimm6 >> 5) & 1) == 0 ? 32 : 64);
        // remaining 5 bits are encoded lower down
        int uimm5 = uimm6 & 0x1F;
        int imm14 = (imm16 & NumUtil.getNbitNumberInt(16)) >> 2;
        InstructionType type = generalFromSize(size);
        int encoding = type.encoding | TBZ.encoding | (uimm5 << 19) | (imm14 << 5) | rd(reg);
        if (pos == -1) {
            emitInt(encoding);
        } else {
            emitInt(encoding, pos);
        }
    }

    private void conditionalBranchInstruction(Register reg, int imm21, InstructionType type, Instruction instr, int pos) {
        assert reg.getRegisterCategory().equals(CPU);
        int instrEncoding = instr.encoding | CompareBranchOp;
        if (pos == -1) {
            emitInt(type.encoding | instrEncoding | getConditionalBranchImm(imm21) | rd(reg));
        } else {
            emitInt(type.encoding | instrEncoding | getConditionalBranchImm(imm21) | rd(reg), pos);
        }
    }

    private static int getConditionalBranchImm(int imm21) {
        assert NumUtil.isSignedNbit(21, imm21) && (imm21 & 0x3) == 0 : "Immediate has to be 21bit signed number and word aligned";
        int imm = (imm21 & NumUtil.getNbitNumberInt(21)) >> 2;
        return imm << ConditionalBranchImmOffset;
    }

    /* Unconditional Branch (immediate) (5.2.2) */

    /**
     * @param imm28 Signed 28-bit offset, has to be word aligned.
     */
    protected void b(int imm28) {
        unconditionalBranchImmInstruction(imm28, Instruction.B, -1);
    }

    /**
     *
     * @param imm28 Signed 28-bit offset, has to be word aligned.
     * @param pos Position where instruction is inserted into code buffer.
     */
    protected void b(int imm28, int pos) {
        unconditionalBranchImmInstruction(imm28, Instruction.B, pos);
    }

    /**
     * Branch and link return address to register X30.
     *
     * @param imm28 Signed 28-bit offset, has to be word aligned.
     */
    public void bl(int imm28) {
        unconditionalBranchImmInstruction(imm28, Instruction.BL, -1);
    }

    private void unconditionalBranchImmInstruction(int imm28, Instruction instr, int pos) {
        assert NumUtil.isSignedNbit(28, imm28) && (imm28 & 0x3) == 0 : "Immediate has to be 28bit signed number and word aligned";
        int imm = (imm28 & NumUtil.getNbitNumberInt(28)) >> 2;
        int instrEncoding = instr.encoding | UnconditionalBranchImmOp;
        if (pos == -1) {
            annotatePatchingImmediate(position(), instr, 26, 0, 2);
            emitInt(instrEncoding | imm);
        } else {
            annotatePatchingImmediate(pos, instr, 26, 0, 2);
            emitInt(instrEncoding | imm, pos);
        }
    }

    /* Unconditional Branch (register) (5.2.3) */

    /**
     * Branches to address in register and writes return address into register X30.
     *
     * @param reg general purpose register. May not be null, zero-register or stackpointer.
     */
    public void blr(Register reg) {
        unconditionalBranchRegInstruction(BLR, reg);
    }

    /**
     * Branches to address in register.
     *
     * @param reg general purpose register. May not be null, zero-register or stackpointer.
     */
    protected void br(Register reg) {
        unconditionalBranchRegInstruction(BR, reg);
    }

    /**
     * Return to address in register.
     *
     * @param reg general purpose register. May not be null, zero-register or stackpointer.
     */
    public void ret(Register reg) {
        unconditionalBranchRegInstruction(RET, reg);
    }

    private void unconditionalBranchRegInstruction(Instruction instr, Register reg) {
        assert reg.getRegisterCategory().equals(CPU);
        assert !reg.equals(zr);
        assert !reg.equals(sp);
        emitInt(instr.encoding | UnconditionalBranchRegOp | rs1(reg));

    }

    /* Load-Store Single Register (5.3.1) */

    /**
     * Loads a srcSize value from address into rt zero-extending it.
     *
     * @param srcSize size of memory read in bits. Must be 8, 16, 32 or 64.
     * @param rt general purpose register. May not be null or stackpointer.
     * @param address all addressing modes allowed. May not be null.
     */
    public void ldr(int srcSize, Register rt, AArch64Address address) {
        assert rt.getRegisterCategory().equals(CPU);
        assert srcSize == 8 || srcSize == 16 || srcSize == 32 || srcSize == 64;
        int transferSize = NumUtil.log2Ceil(srcSize / 8);
        loadStoreInstruction(LDR, rt, address, General32, transferSize);
    }

    /**
     * Loads a srcSize value from address into rt sign-extending it.
     *
     * @param targetSize size of target register in bits. Must be 32 or 64.
     * @param srcSize size of memory read in bits. Must be 8, 16 or 32, but may not be equivalent to
     *            targetSize.
     * @param rt general purpose register. May not be null or stackpointer.
     * @param address all addressing modes allowed. May not be null.
     */
    protected void ldrs(int targetSize, int srcSize, Register rt, AArch64Address address) {
        assert rt.getRegisterCategory().equals(CPU);
        assert (srcSize == 8 || srcSize == 16 || srcSize == 32) && srcSize != targetSize;
        int transferSize = NumUtil.log2Ceil(srcSize / 8);
        loadStoreInstruction(LDRS, rt, address, generalFromSize(targetSize), transferSize);
    }

    public enum PrefetchMode {
        PLDL1KEEP(0b00000),
        PLDL1STRM(0b00001),
        PLDL2KEEP(0b00010),
        PLDL2STRM(0b00011),
        PLDL3KEEP(0b00100),
        PLDL3STRM(0b00101),

        PLIL1KEEP(0b01000),
        PLIL1STRM(0b01001),
        PLIL2KEEP(0b01010),
        PLIL2STRM(0b01011),
        PLIL3KEEP(0b01100),
        PLIL3STRM(0b01101),

        PSTL1KEEP(0b10000),
        PSTL1STRM(0b10001),
        PSTL2KEEP(0b10010),
        PSTL2STRM(0b10011),
        PSTL3KEEP(0b10100),
        PSTL3STRM(0b10101);

        private final int encoding;

        PrefetchMode(int encoding) {
            this.encoding = encoding;
        }

        private static PrefetchMode[] modes = {
                        PLDL1KEEP,
                        PLDL1STRM,
                        PLDL2KEEP,
                        PLDL2STRM,
                        PLDL3KEEP,
                        PLDL3STRM,

                        null,
                        null,

                        PLIL1KEEP,
                        PLIL1STRM,
                        PLIL2KEEP,
                        PLIL2STRM,
                        PLIL3KEEP,
                        PLIL3STRM,

                        null,
                        null,

                        PSTL1KEEP,
                        PSTL1STRM,
                        PSTL2KEEP,
                        PSTL2STRM,
                        PSTL3KEEP,
                        PSTL3STRM
        };

        public static PrefetchMode lookup(int enc) {
            assert enc >= 00 && enc < modes.length;
            return modes[enc];
        }

        public Register toRegister() {
            return cpuRegisters.get(encoding);
        }
    }

    /*
     * implements a prefetch at a 64-bit aligned address using a scaled 12 bit or unscaled 9 bit
     * displacement addressing mode
     *
     * @param rt general purpose register. May not be null, zr or stackpointer.
     *
     * @param address only displacement addressing modes allowed. May not be null.
     */
    public void prfm(AArch64Address address, PrefetchMode mode) {
        assert (address.getAddressingMode() == AddressingMode.IMMEDIATE_SCALED ||
                        address.getAddressingMode() == AddressingMode.IMMEDIATE_UNSCALED ||
                        address.getAddressingMode() == AddressingMode.REGISTER_OFFSET);
        assert mode != null;
        final int srcSize = 64;
        final int transferSize = NumUtil.log2Ceil(srcSize / 8);
        final Register rt = mode.toRegister();
        // this looks weird but that's because loadStoreInstruction is weird
        // instruction select fields are size [31:30], v [26] and opc [25:24]
        // prfm requires size == 0b11, v == 0b0 and opc == 0b11
        // passing LDRS ensures opc[1] == 0b1
        // (n.b. passing LDR/STR makes no difference to opc[1:0]!!)
        // passing General64 ensures opc[0] == 0b1 and v = 0b0
        // (n.b. passing General32 ensures opc[0] == 0b0 and v = 0b0)
        // srcSize 64 ensures size == 0b11
        loadStoreInstruction(LDRS, rt, address, General64, transferSize);
    }

    /**
     * Stores register rt into memory pointed by address.
     *
     * @param destSize number of bits written to memory. Must be 8, 16, 32 or 64.
     * @param rt general purpose register. May not be null or stackpointer.
     * @param address all addressing modes allowed. May not be null.
     */
    public void str(int destSize, Register rt, AArch64Address address) {
        assert rt.getRegisterCategory().equals(CPU);
        assert destSize == 8 || destSize == 16 || destSize == 32 || destSize == 64;
        int transferSize = NumUtil.log2Ceil(destSize / 8);
        loadStoreInstruction(STR, rt, address, General64, transferSize);
    }

    private void loadStoreInstruction(Instruction instr, Register reg, AArch64Address address, InstructionType type, int log2TransferSize) {
        assert log2TransferSize >= 0 && log2TransferSize < 4;
        int transferSizeEncoding = log2TransferSize << LoadStoreTransferSizeOffset;
        int is32Bit = type.width == 32 ? 1 << ImmediateSizeOffset : 0;
        int isFloat = !type.isGeneral ? 1 << LoadStoreFpFlagOffset : 0;
        int memop = instr.encoding | transferSizeEncoding | is32Bit | isFloat | rt(reg);
        switch (address.getAddressingMode()) {
            case IMMEDIATE_SCALED:
                annotatePatchingImmediate(position(), instr, 12, LoadStoreScaledImmOffset, log2TransferSize);
                emitInt(memop | LoadStoreScaledOp | address.getImmediate() << LoadStoreScaledImmOffset | rs1(address.getBase()));
                break;
            case IMMEDIATE_UNSCALED:
                annotatePatchingImmediate(position(), instr, 9, LoadStoreUnscaledImmOffset, 0);
                emitInt(memop | LoadStoreUnscaledOp | address.getImmediate() << LoadStoreUnscaledImmOffset | rs1(address.getBase()));
                break;
            case BASE_REGISTER_ONLY:
                emitInt(memop | LoadStoreScaledOp | rs1(address.getBase()));
                break;
            case EXTENDED_REGISTER_OFFSET:
            case REGISTER_OFFSET:
                ExtendType extendType = address.getAddressingMode() == AddressingMode.EXTENDED_REGISTER_OFFSET ? address.getExtendType() : ExtendType.UXTX;
                boolean shouldScale = address.isScaled() && log2TransferSize != 0;
                emitInt(memop | LoadStoreRegisterOp | rs2(address.getOffset()) | extendType.encoding << ExtendTypeOffset | (shouldScale ? 1 : 0) << LoadStoreScaledRegOffset | rs1(address.getBase()));
                break;
            case PC_LITERAL:
                assert log2TransferSize >= 2 : "PC literal loads only works for load/stores of 32-bit and larger";
                transferSizeEncoding = (log2TransferSize - 2) << LoadStoreTransferSizeOffset;
                annotatePatchingImmediate(position(), instr, 21, LoadLiteralImmeOffset, 2);
                emitInt(transferSizeEncoding | isFloat | LoadLiteralOp | rd(reg) | address.getImmediate() << LoadLiteralImmeOffset);
                break;
            case IMMEDIATE_POST_INDEXED:
                annotatePatchingImmediate(position(), instr, 9, LoadStoreIndexedImmOffset, 0);
                emitInt(memop | LoadStorePostIndexedOp | rs1(address.getBase()) | address.getImmediate() << LoadStoreIndexedImmOffset);
                break;
            case IMMEDIATE_PRE_INDEXED:
                annotatePatchingImmediate(position(), instr, 9, LoadStoreIndexedImmOffset, 0);
                emitInt(memop | LoadStorePreIndexedOp | rs1(address.getBase()) | address.getImmediate() << LoadStoreIndexedImmOffset);
                break;
            default:
                throw GraalError.shouldNotReachHere("Unhandled addressing mode: " + address.getAddressingMode());
        }
    }

    /**
     * Load Pair of Registers calculates an address from a base register value and an immediate
     * offset, and stores two 32-bit words or two 64-bit doublewords to the calculated address, from
     * two registers.
     */
    public void ldp(int size, Register rt, Register rt2, AArch64Address address) {
        assert size == 32 || size == 64;
        loadStorePairInstruction(LDP, rt, rt2, address, generalFromSize(size));
    }

    /**
     * Store Pair of Registers calculates an address from a base register value and an immediate
     * offset, and stores two 32-bit words or two 64-bit doublewords to the calculated address, from
     * two registers.
     */
    public void stp(int size, Register rt, Register rt2, AArch64Address address) {
        assert size == 32 || size == 64;
        loadStorePairInstruction(STP, rt, rt2, address, generalFromSize(size));
    }

    private void loadStorePairInstruction(Instruction instr, Register rt, Register rt2, AArch64Address address, InstructionType type) {
        int scaledOffset = maskField(7, address.getImmediateRaw());  // LDP/STP use a 7-bit scaled
                                                                     // offset
        int memop = type.encoding | instr.encoding | scaledOffset << LoadStorePairImm7Offset | rt2(rt2) | rn(address.getBase()) | rt(rt);
        switch (address.getAddressingMode()) {
            case IMMEDIATE_SCALED:
                emitInt(memop | LoadStorePairOp | (0b010 << 23));
                break;
            case IMMEDIATE_POST_INDEXED:
                emitInt(memop | LoadStorePairOp | (0b001 << 23));
                break;
            case IMMEDIATE_PRE_INDEXED:
                emitInt(memop | LoadStorePairOp | (0b011 << 23));
                break;
            default:
                throw GraalError.shouldNotReachHere("Unhandled addressing mode: " + address.getAddressingMode());
        }
    }

    /* Load-Store Exclusive (5.3.6) */

    /**
     * Load address exclusive. Natural alignment of address is required.
     *
     * @param size size of memory read in bits. Must be 8, 16, 32 or 64.
     * @param rt general purpose register. May not be null or stackpointer.
     * @param rn general purpose register.
     */
    protected void ldxr(int size, Register rt, Register rn) {
        assert size == 8 || size == 16 || size == 32 || size == 64;
        int transferSize = NumUtil.log2Ceil(size / 8);
        exclusiveLoadInstruction(LDXR, rt, rn, transferSize);
    }

    /**
     * Store address exclusive. Natural alignment of address is required. rs and rt may not point to
     * the same register.
     *
     * @param size size of bits written to memory. Must be 8, 16, 32 or 64.
     * @param rs general purpose register. Set to exclusive access status. 0 means success,
     *            everything else failure. May not be null, or stackpointer.
     * @param rt general purpose register. May not be null or stackpointer.
     * @param rn general purpose register.
     */
    protected void stxr(int size, Register rs, Register rt, Register rn) {
        assert size == 8 || size == 16 || size == 32 || size == 64;
        int transferSize = NumUtil.log2Ceil(size / 8);
        exclusiveStoreInstruction(STXR, rs, rt, rn, transferSize);
    }

    /* Load-Acquire/Store-Release (5.3.7) */

    /* non exclusive access */
    /**
     * Load acquire. Natural alignment of address is required.
     *
     * @param size size of memory read in bits. Must be 8, 16, 32 or 64.
     * @param rt general purpose register. May not be null or stackpointer.
     * @param rn general purpose register.
     */
    protected void ldar(int size, Register rt, Register rn) {
        assert size == 8 || size == 16 || size == 32 || size == 64;
        int transferSize = NumUtil.log2Ceil(size / 8);
        exclusiveLoadInstruction(LDAR, rt, rn, transferSize);
    }

    /**
     * Store-release. Natural alignment of address is required.
     *
     * @param size size of bits written to memory. Must be 8, 16, 32 or 64.
     * @param rt general purpose register. May not be null or stackpointer.
     * @param rn general purpose register.
     */
    protected void stlr(int size, Register rt, Register rn) {
        assert size == 8 || size == 16 || size == 32 || size == 64;
        int transferSize = NumUtil.log2Ceil(size / 8);
        // Hack: Passing the zero-register means it is ignored when building the encoding.
        exclusiveStoreInstruction(STLR, r0, rt, rn, transferSize);
    }

    /* exclusive access */
    /**
     * Load acquire exclusive. Natural alignment of address is required.
     *
     * @param size size of memory read in bits. Must be 8, 16, 32 or 64.
     * @param rt general purpose register. May not be null or stackpointer.
     * @param rn general purpose register.
     */
    public void ldaxr(int size, Register rt, Register rn) {
        assert size == 8 || size == 16 || size == 32 || size == 64;
        int transferSize = NumUtil.log2Ceil(size / 8);
        exclusiveLoadInstruction(LDAXR, rt, rn, transferSize);
    }

    /**
     * Store-release exclusive. Natural alignment of address is required. rs and rt may not point to
     * the same register.
     *
     * @param size size of bits written to memory. Must be 8, 16, 32 or 64.
     * @param rs general purpose register. Set to exclusive access status. 0 means success,
     *            everything else failure. May not be null, or stackpointer.
     * @param rt general purpose register. May not be null or stackpointer.
     * @param rn general purpose register.
     */
    public void stlxr(int size, Register rs, Register rt, Register rn) {
        assert size == 8 || size == 16 || size == 32 || size == 64;
        int transferSize = NumUtil.log2Ceil(size / 8);
        exclusiveStoreInstruction(STLXR, rs, rt, rn, transferSize);
    }

    private void exclusiveLoadInstruction(Instruction instr, Register reg, Register rn, int log2TransferSize) {
        assert log2TransferSize >= 0 && log2TransferSize < 4;
        assert reg.getRegisterCategory().equals(CPU);
        int transferSizeEncoding = log2TransferSize << LoadStoreTransferSizeOffset;
        emitInt(transferSizeEncoding | instr.encoding | 1 << ImmediateSizeOffset | rn(rn) | rt(reg));
    }

    /**
     * Stores data from rt into address and sets rs to the returned exclusive access status.
     *
     * @param rs general purpose register into which the exclusive access status is written. May not
     *            be null.
     * @param rt general purpose register containing data to be written to memory at address. May
     *            not be null
     * @param rn general purpose register containing the address specifying where rt is written to.
     * @param log2TransferSize log2Ceil of memory transfer size.
     */
    private void exclusiveStoreInstruction(Instruction instr, Register rs, Register rt, Register rn, int log2TransferSize) {
        assert log2TransferSize >= 0 && log2TransferSize < 4;
        assert rt.getRegisterCategory().equals(CPU) && rs.getRegisterCategory().equals(CPU) && !rs.equals(rt);
        int transferSizeEncoding = log2TransferSize << LoadStoreTransferSizeOffset;
        emitInt(transferSizeEncoding | instr.encoding | rs2(rs) | rn(rn) | rt(rt));
    }

    /**
     * Compare And Swap word or doubleword in memory. This reads a value from an address rn,
     * compares it against a given value rs, and, if equal, stores the value rt to memory. The value
     * read from address rn is stored in register rs.
     *
     * @param size size of bits read from memory. Must be 32 or 64.
     * @param rs general purpose register to be compared and loaded. May not be null.
     * @param rt general purpose register to be conditionally stored. May not be null.
     * @param rn general purpose register containing the address from which to read.
     * @param acquire boolean value signifying if the load should use acquire semantics.
     * @param release boolean value signifying if the store should use release semantics.
     */
    public void cas(int size, Register rs, Register rt, Register rn, boolean acquire, boolean release) {
        assert size == 32 || size == 64;
        int transferSize = NumUtil.log2Ceil(size / 8);
        compareAndSwapInstruction(CAS, rs, rt, rn, transferSize, acquire, release);
    }

    private void compareAndSwapInstruction(Instruction instr, Register rs, Register rt, Register rn, int log2TransferSize, boolean acquire, boolean release) {
        assert log2TransferSize >= 0 && log2TransferSize < 4;
        assert rt.getRegisterCategory().equals(CPU) && rs.getRegisterCategory().equals(CPU) && !rs.equals(rt);
        int transferSizeEncoding = log2TransferSize << LoadStoreTransferSizeOffset;
        emitInt(transferSizeEncoding | instr.encoding | rs2(rs) | rn(rn) | rt(rt) | (acquire ? 1 : 0) << CASAcquireOffset | (release ? 1 : 0) << CASReleaseOffset);
    }

    /**
     * Atomic add. This reads a value from an address rn, stores the value in rt, and adds the value
     * in rs to it, and stores the result back at address rn. The initial value read from memory is
     * stored in rt.
     *
     * @param size size of operand to read from memory. Must be 8, 16, 32, or 64.
     * @param rs general purpose register to be added to contents. May not be null.
     * @param rt general purpose register to be loaded. May not be null.
     * @param rn general purpose register or stack pointer holding an address from which to load.
     * @param acquire boolean value signifying if the load should use acquire semantics.
     * @param release boolean value signifying if the store should use release semantics.
     */
    public void ldadd(int size, Register rs, Register rt, Register rn, boolean acquire, boolean release) {
        assert size == 8 || size == 16 || size == 32 || size == 64;
        int transferSize = NumUtil.log2Ceil(size / 8);
        loadAndAddInstruction(LDADD, rs, rt, rn, transferSize, acquire, release);
    }

    private void loadAndAddInstruction(Instruction instr, Register rs, Register rt, Register rn, int log2TransferSize, boolean acquire, boolean release) {
        assert log2TransferSize >= 0 && log2TransferSize < 4;
        assert rt.getRegisterCategory().equals(CPU) && rs.getRegisterCategory().equals(CPU) && !rs.equals(rt);
        int transferSizeEncoding = log2TransferSize << LoadStoreTransferSizeOffset;
        emitInt(transferSizeEncoding | instr.encoding | rs2(rs) | rn(rn) | rt(rt) | (acquire ? 1 : 0) << LDADDAcquireOffset | (release ? 1 : 0) << LDADDReleaseOffset);
    }

    /**
     * Atomic swap. This reads a value from an address rn, stores the value in rt, and then stores
     * the value in rs back at address rn.
     *
     * @param size size of operand to read from memory. Must be 8, 16, 32, or 64.
     * @param rs general purpose register to be stored. May not be null.
     * @param rt general purpose register to be loaded. May not be null.
     * @param rn general purpose register or stack pointer holding an address from which to load.
     * @param acquire boolean value signifying if the load should use acquire semantics.
     * @param release boolean value signifying if the store should use release semantics.
     */
    public void swp(int size, Register rs, Register rt, Register rn, boolean acquire, boolean release) {
        assert size == 8 || size == 16 || size == 32 || size == 64;
        int transferSize = NumUtil.log2Ceil(size / 8);
        swapInstruction(SWP, rs, rt, rn, transferSize, acquire, release);
    }

    private void swapInstruction(Instruction instr, Register rs, Register rt, Register rn, int log2TransferSize, boolean acquire, boolean release) {
        assert log2TransferSize >= 0 && log2TransferSize < 4;
        assert rt.getRegisterCategory().equals(CPU) && rs.getRegisterCategory().equals(CPU) && !rs.equals(rt);
        int transferSizeEncoding = log2TransferSize << LoadStoreTransferSizeOffset;
        emitInt(transferSizeEncoding | instr.encoding | rs2(rs) | rn(rn) | rt(rt) | (acquire ? 1 : 0) << LDADDAcquireOffset | (release ? 1 : 0) << LDADDReleaseOffset);
    }

    /* PC-relative Address Calculation (5.4.4) */

    /**
     * Address of page: sign extends 21-bit offset, shifts if left by 12 and adds it to the value of
     * the PC with its bottom 12-bits cleared, writing the result to dst. No offset is emitted; the
     * instruction will be patched later.
     *
     * @param dst general purpose register. May not be null, zero-register or stackpointer.
     */
    public void adrp(Register dst) {
        emitInt(ADRP.encoding | PcRelImmOp | rd(dst));
    }

    /**
     * Adds a 21-bit signed offset to the program counter and writes the result to dst.
     *
     * @param dst general purpose register. May not be null, zero-register or stackpointer.
     * @param imm21 Signed 21-bit offset.
     */
    public void adr(Register dst, int imm21) {
        emitInt(ADR.encoding | PcRelImmOp | rd(dst) | getPcRelativeImmEncoding(imm21));
    }

    /**
     * Adds a 21-bit signed offset to the program counter and writes the result to dst.
     *
     * @param dst general purpose register. May not be null, zero-register or stackpointer.
     * @param imm21 Signed 21-bit offset.
     * @param pos the position in the code that the instruction is emitted.
     */
    public void adr(Register dst, int imm21, int pos) {
        emitInt(ADR.encoding | PcRelImmOp | rd(dst) | getPcRelativeImmEncoding(imm21), pos);
    }

    private static int getPcRelativeImmEncoding(int imm21) {
        assert NumUtil.isSignedNbit(21, imm21);
        int imm = imm21 & NumUtil.getNbitNumberInt(21);
        // higher 19 bit
        int immHi = (imm >> 2) << PcRelImmHiOffset;
        // lower 2 bit
        int immLo = (imm & 0x3) << PcRelImmLoOffset;
        return immHi | immLo;
    }

    /* Arithmetic (Immediate) (5.4.1) */

    /**
     * dst = src + aimm.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or zero-register.
     * @param src general purpose register. May not be null or zero-register.
     * @param aimm arithmetic immediate. Either unsigned 12-bit value or unsigned 24-bit value with
     *            the lower 12-bit cleared.
     */
    protected void add(int size, Register dst, Register src, int aimm) {
        assert !dst.equals(zr);
        assert !src.equals(zr);
        addSubImmInstruction(ADD, dst, src, aimm, generalFromSize(size));
    }

    /**
     * dst = src + aimm and sets condition flags.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src general purpose register. May not be null or zero-register.
     * @param aimm arithmetic immediate. Either unsigned 12-bit value or unsigned 24-bit value with
     *            the lower 12-bit cleared.
     */
    protected void adds(int size, Register dst, Register src, int aimm) {
        assert !dst.equals(sp);
        assert !src.equals(zr);
        addSubImmInstruction(ADDS, dst, src, aimm, generalFromSize(size));
    }

    /**
     * dst = src - aimm.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or zero-register.
     * @param src general purpose register. May not be null or zero-register.
     * @param aimm arithmetic immediate. Either unsigned 12-bit value or unsigned 24-bit value with
     *            the lower 12-bit cleared.
     */
    protected void sub(int size, Register dst, Register src, int aimm) {
        assert !dst.equals(zr);
        assert !src.equals(zr);
        addSubImmInstruction(SUB, dst, src, aimm, generalFromSize(size));
    }

    /**
     * dst = src - aimm and sets condition flags.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src general purpose register. May not be null or zero-register.
     * @param aimm arithmetic immediate. Either unsigned 12-bit value or unsigned 24-bit value with
     *            the lower 12-bit cleared.
     */
    protected void subs(int size, Register dst, Register src, int aimm) {
        assert !dst.equals(sp);
        assert !src.equals(zr);
        addSubImmInstruction(SUBS, dst, src, aimm, generalFromSize(size));
    }

    private void addSubImmInstruction(Instruction instr, Register dst, Register src, int aimm, InstructionType type) {
        emitInt(type.encoding | instr.encoding | AddSubImmOp | encodeAimm(aimm) | rd(dst) | rs1(src));
    }

    public void ccmp(int size, Register x, Register y, int aimm, ConditionFlag condition) {
        emitInt(generalFromSize(size).encoding | CCMP.encoding | rs1(x) | rs2(y) | encodeAimm(aimm) | condition.encoding << ConditionalConditionOffset);
    }

    /**
     * Encodes arithmetic immediate.
     *
     * @param imm Immediate has to be either an unsigned 12-bit value or an unsigned 24-bit value
     *            with the lower 12 bits zero.
     * @return Representation of immediate for use with arithmetic instructions.
     */
    private static int encodeAimm(int imm) {
        assert isAimm(imm) : "Immediate has to be legal arithmetic immediate value " + imm;
        if (NumUtil.isUnsignedNbit(12, imm)) {
            return imm << ImmediateOffset;
        } else {
            // First 12-bit are zero, so shift immediate 12-bit and set flag to indicate
            // shifted immediate value.
            return (imm >>> 12 << ImmediateOffset) | AddSubShift12;
        }
    }

    /**
     * Checks whether immediate can be encoded as an arithmetic immediate.
     *
     * @param imm Immediate has to be either an unsigned 12bit value or un unsigned 24bit value with
     *            the lower 12 bits 0.
     * @return true if valid arithmetic immediate, false otherwise.
     */
    protected static boolean isAimm(int imm) {
        return NumUtil.isUnsignedNbit(12, imm) || NumUtil.isUnsignedNbit(12, imm >>> 12) && (imm & 0xfff) == 0;
    }

    /* Logical (immediate) (5.4.2) */

    /**
     * dst = src & bimm.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or zero-register.
     * @param src general purpose register. May not be null or stack-pointer.
     * @param bimm logical immediate. See {@link LogicalImmediateTable} for exact definition.
     */
    public void and(int size, Register dst, Register src, long bimm) {
        assert !dst.equals(zr);
        assert !src.equals(sp);
        logicalImmInstruction(AND, dst, src, bimm, generalFromSize(size));
    }

    /**
     * dst = src & bimm and sets condition flags.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stack-pointer.
     * @param src general purpose register. May not be null or stack-pointer.
     * @param bimm logical immediate. See {@link LogicalImmediateTable} for exact definition.
     */
    public void ands(int size, Register dst, Register src, long bimm) {
        assert !dst.equals(sp);
        assert !src.equals(sp);
        logicalImmInstruction(ANDS, dst, src, bimm, generalFromSize(size));
    }

    /**
     * dst = src ^ bimm.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or zero-register.
     * @param src general purpose register. May not be null or stack-pointer.
     * @param bimm logical immediate. See {@link LogicalImmediateTable} for exact definition.
     */
    public void eor(int size, Register dst, Register src, long bimm) {
        assert !dst.equals(zr);
        assert !src.equals(sp);
        logicalImmInstruction(EOR, dst, src, bimm, generalFromSize(size));
    }

    /**
     * dst = src | bimm.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or zero-register.
     * @param src general purpose register. May not be null or stack-pointer.
     * @param bimm logical immediate. See {@link LogicalImmediateTable} for exact definition.
     */
    protected void orr(int size, Register dst, Register src, long bimm) {
        assert !dst.equals(zr);
        assert !src.equals(sp);
        logicalImmInstruction(ORR, dst, src, bimm, generalFromSize(size));
    }

    private void logicalImmInstruction(Instruction instr, Register dst, Register src, long bimm, InstructionType type) {
        // Mask higher bits off, since we always pass longs around even for the 32-bit instruction.
        long bimmValue;
        if (type == General32) {
            assert (bimm >> 32) == 0 || (bimm >> 32) == -1L : "Higher order bits for 32-bit instruction must either all be 0 or 1.";
            bimmValue = bimm & NumUtil.getNbitNumberLong(32);
        } else {
            bimmValue = bimm;
        }
        int immEncoding = LogicalImmediateTable.getLogicalImmEncoding(type == General64, bimmValue);
        emitInt(type.encoding | instr.encoding | LogicalImmOp | immEncoding | rd(dst) | rs1(src));
    }

    /* Move (wide immediate) (5.4.3) */

    /**
     * dst = uimm16 << shiftAmt.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null, stackpointer or zero-register.
     * @param uimm16 16-bit unsigned immediate
     * @param shiftAmt amount by which uimm16 is left shifted. Can be any multiple of 16 smaller
     *            than size.
     */
    protected void movz(int size, Register dst, int uimm16, int shiftAmt) {
        moveWideImmInstruction(MOVZ, dst, uimm16, shiftAmt, generalFromSize(size));
    }

    /**
     * dst = ~(uimm16 << shiftAmt).
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null, stackpointer or zero-register.
     * @param uimm16 16-bit unsigned immediate
     * @param shiftAmt amount by which uimm16 is left shifted. Can be any multiple of 16 smaller
     *            than size.
     */
    protected void movn(int size, Register dst, int uimm16, int shiftAmt) {
        moveWideImmInstruction(MOVN, dst, uimm16, shiftAmt, generalFromSize(size));
    }

    /**
     * dst<pos+15:pos> = uimm16.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null, stackpointer or zero-register.
     * @param uimm16 16-bit unsigned immediate
     * @param pos position into which uimm16 is inserted. Can be any multiple of 16 smaller than
     *            size.
     */
    protected void movk(int size, Register dst, int uimm16, int pos) {
        moveWideImmInstruction(MOVK, dst, uimm16, pos, generalFromSize(size));
    }

    private void moveWideImmInstruction(Instruction instr, Register dst, int uimm16, int shiftAmt, InstructionType type) {
        assert dst.getRegisterCategory().equals(CPU);
        assert NumUtil.isUnsignedNbit(16, uimm16) : "Immediate has to be unsigned 16bit";
        assert shiftAmt == 0 || shiftAmt == 16 || (type == InstructionType.General64 && (shiftAmt == 32 || shiftAmt == 48)) : "Invalid shift amount: " + shiftAmt;
        int shiftValue = shiftAmt >> 4;
        emitInt(type.encoding | instr.encoding | MoveWideImmOp | rd(dst) | uimm16 << MoveWideImmOffset | shiftValue << MoveWideShiftOffset);
    }

    /* Bitfield Operations (5.4.5) */

    /**
     * Bitfield move.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null, stackpointer or zero-register.
     * @param src general purpose register. May not be null, stackpointer or zero-register.
     * @param r must be in the range 0 to size - 1
     * @param s must be in the range 0 to size - 1
     */
    public void bfm(int size, Register dst, Register src, int r, int s) {
        bitfieldInstruction(BFM, dst, src, r, s, generalFromSize(size));
    }

    /**
     * Unsigned bitfield move.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null, stackpointer or zero-register.
     * @param src general purpose register. May not be null, stackpointer or zero-register.
     * @param r must be in the range 0 to size - 1
     * @param s must be in the range 0 to size - 1
     */
    public void ubfm(int size, Register dst, Register src, int r, int s) {
        bitfieldInstruction(UBFM, dst, src, r, s, generalFromSize(size));
    }

    /**
     * Signed bitfield move.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null, stackpointer or zero-register.
     * @param src general purpose register. May not be null, stackpointer or zero-register.
     * @param r must be in the range 0 to size - 1
     * @param s must be in the range 0 to size - 1
     */
    protected void sbfm(int size, Register dst, Register src, int r, int s) {
        bitfieldInstruction(SBFM, dst, src, r, s, generalFromSize(size));
    }

    private void bitfieldInstruction(Instruction instr, Register dst, Register src, int r, int s, InstructionType type) {
        assert !dst.equals(sp) && !dst.equals(zr);
        assert !src.equals(sp) && !src.equals(zr);
        assert s >= 0 && s < type.width && r >= 0 && r < type.width;
        int sf = type == General64 ? 1 << ImmediateSizeOffset : 0;
        emitInt(type.encoding | instr.encoding | BitfieldImmOp | sf | r << ImmediateRotateOffset | s << ImmediateOffset | rd(dst) | rs1(src));
    }

    /* Extract (Immediate) (5.4.6) */

    /**
     * Extract. dst = src1:src2<lsb+31:lsb>
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     * @param lsb must be in range 0 to size - 1.
     */
    protected void extr(int size, Register dst, Register src1, Register src2, int lsb) {
        assert !dst.equals(sp);
        assert !src1.equals(sp);
        assert !src2.equals(sp);
        InstructionType type = generalFromSize(size);
        assert lsb >= 0 && lsb < type.width;
        int sf = type == General64 ? 1 << ImmediateSizeOffset : 0;
        emitInt(type.encoding | EXTR.encoding | sf | lsb << ImmediateOffset | rd(dst) | rs1(src1) | rs2(src2));
    }

    /* Arithmetic (shifted register) (5.5.1) */

    /**
     * dst = src1 + shiftType(src2, imm).
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     * @param shiftType any type but ROR.
     * @param imm must be in range 0 to size - 1.
     */
    protected void add(int size, Register dst, Register src1, Register src2, ShiftType shiftType, int imm) {
        addSubShiftedInstruction(ADD, dst, src1, src2, shiftType, imm, generalFromSize(size));
    }

    /**
     * dst = src1 + shiftType(src2, imm) and sets condition flags.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     * @param shiftType any type but ROR.
     * @param imm must be in range 0 to size - 1.
     */
    public void adds(int size, Register dst, Register src1, Register src2, ShiftType shiftType, int imm) {
        addSubShiftedInstruction(ADDS, dst, src1, src2, shiftType, imm, generalFromSize(size));
    }

    /**
     * dst = src1 - shiftType(src2, imm).
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     * @param shiftType any type but ROR.
     * @param imm must be in range 0 to size - 1.
     */
    protected void sub(int size, Register dst, Register src1, Register src2, ShiftType shiftType, int imm) {
        addSubShiftedInstruction(SUB, dst, src1, src2, shiftType, imm, generalFromSize(size));
    }

    /**
     * dst = src1 - shiftType(src2, imm) and sets condition flags.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     * @param shiftType any type but ROR.
     * @param imm must be in range 0 to size - 1.
     */
    public void subs(int size, Register dst, Register src1, Register src2, ShiftType shiftType, int imm) {
        addSubShiftedInstruction(SUBS, dst, src1, src2, shiftType, imm, generalFromSize(size));
    }

    private void addSubShiftedInstruction(Instruction instr, Register dst, Register src1, Register src2, ShiftType shiftType, int imm, InstructionType type) {
        assert shiftType != ShiftType.ROR;
        assert imm >= 0 && imm < type.width;
        emitInt(type.encoding | instr.encoding | AddSubShiftedOp | imm << ImmediateOffset | shiftType.encoding << ShiftTypeOffset | rd(dst) | rs1(src1) | rs2(src2));
    }

    /* Arithmetic (extended register) (5.5.2) */
    /**
     * dst = src1 + extendType(src2) << imm.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or zero-register..
     * @param src1 general purpose register. May not be null or zero-register.
     * @param src2 general purpose register. May not be null or stackpointer.
     * @param extendType defines how src2 is extended to the same size as src1.
     * @param shiftAmt must be in range 0 to 4.
     */
    public void add(int size, Register dst, Register src1, Register src2, ExtendType extendType, int shiftAmt) {
        assert !dst.equals(zr);
        assert !src1.equals(zr);
        assert !src2.equals(sp);
        addSubExtendedInstruction(ADD, dst, src1, src2, extendType, shiftAmt, generalFromSize(size));
    }

    /**
     * dst = src1 + extendType(src2) << imm and sets condition flags.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer..
     * @param src1 general purpose register. May not be null or zero-register.
     * @param src2 general purpose register. May not be null or stackpointer.
     * @param extendType defines how src2 is extended to the same size as src1.
     * @param shiftAmt must be in range 0 to 4.
     */
    protected void adds(int size, Register dst, Register src1, Register src2, ExtendType extendType, int shiftAmt) {
        assert !dst.equals(sp);
        assert !src1.equals(zr);
        assert !src2.equals(sp);
        addSubExtendedInstruction(ADDS, dst, src1, src2, extendType, shiftAmt, generalFromSize(size));
    }

    /**
     * dst = src1 - extendType(src2) << imm.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or zero-register..
     * @param src1 general purpose register. May not be null or zero-register.
     * @param src2 general purpose register. May not be null or stackpointer.
     * @param extendType defines how src2 is extended to the same size as src1.
     * @param shiftAmt must be in range 0 to 4.
     */
    protected void sub(int size, Register dst, Register src1, Register src2, ExtendType extendType, int shiftAmt) {
        assert !dst.equals(zr);
        assert !src1.equals(zr);
        assert !src2.equals(sp);
        addSubExtendedInstruction(SUB, dst, src1, src2, extendType, shiftAmt, generalFromSize(size));
    }

    /**
     * dst = src1 - extendType(src2) << imm and sets flags.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer..
     * @param src1 general purpose register. May not be null or zero-register.
     * @param src2 general purpose register. May not be null or stackpointer.
     * @param extendType defines how src2 is extended to the same size as src1.
     * @param shiftAmt must be in range 0 to 4.
     */
    public void subs(int size, Register dst, Register src1, Register src2, ExtendType extendType, int shiftAmt) {
        assert !dst.equals(sp);
        assert !src1.equals(zr);
        assert !src2.equals(sp);
        addSubExtendedInstruction(SUBS, dst, src1, src2, extendType, shiftAmt, generalFromSize(size));
    }

    private void addSubExtendedInstruction(Instruction instr, Register dst, Register src1, Register src2, ExtendType extendType, int shiftAmt, InstructionType type) {
        assert shiftAmt >= 0 && shiftAmt <= 4;
        emitInt(type.encoding | instr.encoding | AddSubExtendedOp | shiftAmt << ImmediateOffset | extendType.encoding << ExtendTypeOffset | rd(dst) | rs1(src1) | rs2(src2));
    }

    /* Logical (shifted register) (5.5.3) */
    /**
     * dst = src1 & shiftType(src2, imm).
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     * @param shiftType all types allowed, may not be null.
     * @param shiftAmt must be in range 0 to size - 1.
     */
    protected void and(int size, Register dst, Register src1, Register src2, ShiftType shiftType, int shiftAmt) {
        logicalRegInstruction(AND, dst, src1, src2, shiftType, shiftAmt, generalFromSize(size));
    }

    /**
     * dst = src1 & shiftType(src2, imm) and sets condition flags.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     * @param shiftType all types allowed, may not be null.
     * @param shiftAmt must be in range 0 to size - 1.
     */
    protected void ands(int size, Register dst, Register src1, Register src2, ShiftType shiftType, int shiftAmt) {
        logicalRegInstruction(ANDS, dst, src1, src2, shiftType, shiftAmt, generalFromSize(size));
    }

    /**
     * dst = src1 & ~(shiftType(src2, imm)).
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     * @param shiftType all types allowed, may not be null.
     * @param shiftAmt must be in range 0 to size - 1.
     */
    protected void bic(int size, Register dst, Register src1, Register src2, ShiftType shiftType, int shiftAmt) {
        logicalRegInstruction(BIC, dst, src1, src2, shiftType, shiftAmt, generalFromSize(size));
    }

    /**
     * dst = src1 & ~(shiftType(src2, imm)) and sets condition flags.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     * @param shiftType all types allowed, may not be null.
     * @param shiftAmt must be in range 0 to size - 1.
     */
    protected void bics(int size, Register dst, Register src1, Register src2, ShiftType shiftType, int shiftAmt) {
        logicalRegInstruction(BICS, dst, src1, src2, shiftType, shiftAmt, generalFromSize(size));
    }

    /**
     * dst = src1 ^ ~(shiftType(src2, imm)).
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     * @param shiftType all types allowed, may not be null.
     * @param shiftAmt must be in range 0 to size - 1.
     */
    protected void eon(int size, Register dst, Register src1, Register src2, ShiftType shiftType, int shiftAmt) {
        logicalRegInstruction(EON, dst, src1, src2, shiftType, shiftAmt, generalFromSize(size));
    }

    /**
     * dst = src1 ^ shiftType(src2, imm).
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     * @param shiftType all types allowed, may not be null.
     * @param shiftAmt must be in range 0 to size - 1.
     */
    protected void eor(int size, Register dst, Register src1, Register src2, ShiftType shiftType, int shiftAmt) {
        logicalRegInstruction(EOR, dst, src1, src2, shiftType, shiftAmt, generalFromSize(size));
    }

    /**
     * dst = src1 | shiftType(src2, imm).
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     * @param shiftType all types allowed, may not be null.
     * @param shiftAmt must be in range 0 to size - 1.
     */
    protected void orr(int size, Register dst, Register src1, Register src2, ShiftType shiftType, int shiftAmt) {
        logicalRegInstruction(ORR, dst, src1, src2, shiftType, shiftAmt, generalFromSize(size));
    }

    /**
     * dst = src1 | ~(shiftType(src2, imm)).
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     * @param shiftType all types allowed, may not be null.
     * @param shiftAmt must be in range 0 to size - 1.
     */
    protected void orn(int size, Register dst, Register src1, Register src2, ShiftType shiftType, int shiftAmt) {
        logicalRegInstruction(ORN, dst, src1, src2, shiftType, shiftAmt, generalFromSize(size));
    }

    private void logicalRegInstruction(Instruction instr, Register dst, Register src1, Register src2, ShiftType shiftType, int shiftAmt, InstructionType type) {
        assert !dst.equals(sp);
        assert !src1.equals(sp);
        assert !src2.equals(sp);
        assert shiftAmt >= 0 && shiftAmt < type.width;
        emitInt(type.encoding | instr.encoding | LogicalShiftOp | shiftAmt << ImmediateOffset | shiftType.encoding << ShiftTypeOffset | rd(dst) | rs1(src1) | rs2(src2));
    }

    /* Variable Shift (5.5.4) */
    /**
     * dst = src1 >> (src2 & log2(size)).
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     */
    protected void asr(int size, Register dst, Register src1, Register src2) {
        dataProcessing2SourceOp(ASRV, dst, src1, src2, generalFromSize(size));
    }

    /**
     * dst = src1 << (src2 & log2(size)).
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     */
    protected void lsl(int size, Register dst, Register src1, Register src2) {
        dataProcessing2SourceOp(LSLV, dst, src1, src2, generalFromSize(size));
    }

    /**
     * dst = src1 >>> (src2 & log2(size)).
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     */
    protected void lsr(int size, Register dst, Register src1, Register src2) {
        dataProcessing2SourceOp(LSRV, dst, src1, src2, generalFromSize(size));
    }

    /**
     * dst = rotateRight(src1, (src2 & log2(size))).
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     */
    protected void ror(int size, Register dst, Register src1, Register src2) {
        dataProcessing2SourceOp(RORV, dst, src1, src2, generalFromSize(size));
    }

    /* Bit Operations (5.5.5) */

    /**
     * Counts leading sign bits. Sets Wd to the number of consecutive bits following the topmost bit
     * in dst, that are the same as the topmost bit. The count does not include the topmost bit
     * itself , so the result will be in the range 0 to size-1 inclusive.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null, zero-register or the stackpointer.
     * @param src source register. May not be null, zero-register or the stackpointer.
     */
    protected void cls(int size, Register dst, Register src) {
        dataProcessing1SourceOp(CLS, dst, src, generalFromSize(size));
    }

    /**
     * Counts leading zeros.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null, zero-register or the stackpointer.
     * @param src source register. May not be null, zero-register or the stackpointer.
     */
    public void clz(int size, Register dst, Register src) {
        dataProcessing1SourceOp(CLZ, dst, src, generalFromSize(size));
    }

    /**
     * Reverses bits.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null, zero-register or the stackpointer.
     * @param src source register. May not be null, zero-register or the stackpointer.
     */
    public void rbit(int size, Register dst, Register src) {
        dataProcessing1SourceOp(RBIT, dst, src, generalFromSize(size));
    }

    /**
     * Reverses bytes.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or the stackpointer.
     * @param src source register. May not be null or the stackpointer.
     */
    public void rev(int size, Register dst, Register src) {
        if (size == 64) {
            dataProcessing1SourceOp(REVX, dst, src, generalFromSize(size));
        } else {
            assert size == 32;
            dataProcessing1SourceOp(REVW, dst, src, generalFromSize(size));
        }
    }

    /* Conditional Data Processing (5.5.6) */

    /**
     * Conditional select. dst = src1 if condition else src2.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or the stackpointer.
     * @param src1 general purpose register. May not be null or the stackpointer.
     * @param src2 general purpose register. May not be null or the stackpointer.
     * @param condition any condition flag. May not be null.
     */
    protected void csel(int size, Register dst, Register src1, Register src2, ConditionFlag condition) {
        conditionalSelectInstruction(CSEL, dst, src1, src2, condition, generalFromSize(size));
    }

    /**
     * Conditional select negate. dst = src1 if condition else -src2.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or the stackpointer.
     * @param src1 general purpose register. May not be null or the stackpointer.
     * @param src2 general purpose register. May not be null or the stackpointer.
     * @param condition any condition flag. May not be null.
     */
    protected void csneg(int size, Register dst, Register src1, Register src2, ConditionFlag condition) {
        conditionalSelectInstruction(CSNEG, dst, src1, src2, condition, generalFromSize(size));
    }

    /**
     * Conditional increase. dst = src1 if condition else src2 + 1.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or the stackpointer.
     * @param src1 general purpose register. May not be null or the stackpointer.
     * @param src2 general purpose register. May not be null or the stackpointer.
     * @param condition any condition flag. May not be null.
     */
    protected void csinc(int size, Register dst, Register src1, Register src2, ConditionFlag condition) {
        conditionalSelectInstruction(CSINC, dst, src1, src2, condition, generalFromSize(size));
    }

    private void conditionalSelectInstruction(Instruction instr, Register dst, Register src1, Register src2, ConditionFlag condition, InstructionType type) {
        assert !dst.equals(sp);
        assert !src1.equals(sp);
        assert !src2.equals(sp);
        emitInt(type.encoding | instr.encoding | ConditionalSelectOp | rd(dst) | rs1(src1) | rs2(src2) | condition.encoding << ConditionalConditionOffset);
    }

    /* Integer Multiply/Divide (5.6) */

    /**
     * dst = src1 * src2 + src3.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or the stackpointer.
     * @param src1 general purpose register. May not be null or the stackpointer.
     * @param src2 general purpose register. May not be null or the stackpointer.
     * @param src3 general purpose register. May not be null or the stackpointer.
     */
    protected void madd(int size, Register dst, Register src1, Register src2, Register src3) {
        mulInstruction(MADD, dst, src1, src2, src3, generalFromSize(size));
    }

    /**
     * dst = src3 - src1 * src2.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or the stackpointer.
     * @param src1 general purpose register. May not be null or the stackpointer.
     * @param src2 general purpose register. May not be null or the stackpointer.
     * @param src3 general purpose register. May not be null or the stackpointer.
     */
    protected void msub(int size, Register dst, Register src1, Register src2, Register src3) {
        mulInstruction(MSUB, dst, src1, src2, src3, generalFromSize(size));
    }

    /**
     * Signed multiply high. dst = (src1 * src2)[127:64]
     *
     * @param dst general purpose register. May not be null or the stackpointer.
     * @param src1 general purpose register. May not be null or the stackpointer.
     * @param src2 general purpose register. May not be null or the stackpointer.
     */
    protected void smulh(Register dst, Register src1, Register src2) {
        assert !dst.equals(sp);
        assert !src1.equals(sp);
        assert !src2.equals(sp);
        emitInt(0b10011011010 << 21 | dst.encoding | rs1(src1) | rs2(src2) | 0b011111 << ImmediateOffset);
    }

    /**
     * unsigned multiply high. dst = (src1 * src2)[127:64]
     *
     * @param dst general purpose register. May not be null or the stackpointer.
     * @param src1 general purpose register. May not be null or the stackpointer.
     * @param src2 general purpose register. May not be null or the stackpointer.
     */
    protected void umulh(Register dst, Register src1, Register src2) {
        assert !dst.equals(sp);
        assert !src1.equals(sp);
        assert !src2.equals(sp);
        emitInt(0b10011011110 << 21 | dst.encoding | rs1(src1) | rs2(src2) | 0b011111 << ImmediateOffset);
    }

    /**
     * unsigned multiply add-long. xDst = xSrc3 + (wSrc1 * wSrc2)
     *
     * @param dst general purpose register. May not be null or the stackpointer.
     * @param src1 general purpose register. May not be null or the stackpointer.
     * @param src2 general purpose register. May not be null or the stackpointer.
     * @param src3 general purpose register. May not be null or the stackpointer.
     */
    protected void umaddl(Register dst, Register src1, Register src2, Register src3) {
        assert !dst.equals(sp);
        assert !src1.equals(sp);
        assert !src2.equals(sp);
        assert !src3.equals(sp);
        emitInt(0b10011011101 << 21 | dst.encoding | rs1(src1) | rs2(src2) | 0b011111 << ImmediateOffset);
    }

    /**
     * signed multiply add-long. xDst = xSrc3 + (wSrc1 * wSrc2)
     *
     * @param dst general purpose register. May not be null or the stackpointer.
     * @param src1 general purpose register. May not be null or the stackpointer.
     * @param src2 general purpose register. May not be null or the stackpointer.
     * @param src3 general purpose register. May not be null or the stackpointer.
     */
    public void smaddl(Register dst, Register src1, Register src2, Register src3) {
        assert !dst.equals(sp);
        assert !src1.equals(sp);
        assert !src2.equals(sp);
        assert !src3.equals(sp);
        emitInt(0b10011011001 << 21 | dst.encoding | rs1(src1) | rs2(src2) | rs3(src3));
    }

    private void mulInstruction(Instruction instr, Register dst, Register src1, Register src2, Register src3, InstructionType type) {
        assert !dst.equals(sp);
        assert !src1.equals(sp);
        assert !src2.equals(sp);
        assert !src3.equals(sp);
        emitInt(type.encoding | instr.encoding | MulOp | rd(dst) | rs1(src1) | rs2(src2) | rs3(src3));
    }

    /**
     * Signed divide. dst = src1 / src2.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or the stackpointer.
     * @param src1 general purpose register. May not be null or the stackpointer.
     * @param src2 general purpose register. May not be null or the stackpointer.
     */
    public void sdiv(int size, Register dst, Register src1, Register src2) {
        dataProcessing2SourceOp(SDIV, dst, src1, src2, generalFromSize(size));
    }

    /**
     * Unsigned divide. dst = src1 / src2.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or the stackpointer.
     * @param src1 general purpose register. May not be null or the stackpointer.
     * @param src2 general purpose register. May not be null or the stackpointer.
     */
    public void udiv(int size, Register dst, Register src1, Register src2) {
        dataProcessing2SourceOp(UDIV, dst, src1, src2, generalFromSize(size));
    }

    private void dataProcessing1SourceOp(Instruction instr, Register dst, Register src, InstructionType type) {
        emitInt(type.encoding | instr.encoding | DataProcessing1SourceOp | rd(dst) | rs1(src));
    }

    private void dataProcessing2SourceOp(Instruction instr, Register dst, Register src1, Register src2, InstructionType type) {
        assert !dst.equals(sp);
        assert !src1.equals(sp);
        assert !src2.equals(sp);
        emitInt(type.encoding | instr.encoding | DataProcessing2SourceOp | rd(dst) | rs1(src1) | rs2(src2));
    }

    /* Floating point operations */

    /* Load-Store Single FP register (5.7.1.1) */
    /**
     * Floating point load.
     *
     * @param size number of bits read from memory into rt. Must be 32 or 64.
     * @param rt floating point register. May not be null.
     * @param address all addressing modes allowed. May not be null.
     */
    public void fldr(int size, Register rt, AArch64Address address) {
        assert rt.getRegisterCategory().equals(SIMD);
        assert size == 32 || size == 64;
        int transferSize = NumUtil.log2Ceil(size / 8);
        loadStoreInstruction(LDR, rt, address, InstructionType.FP32, transferSize);
    }

    /**
     * Floating point store.
     *
     * @param size number of bits read from memory into rt. Must be 32 or 64.
     * @param rt floating point register. May not be null.
     * @param address all addressing modes allowed. May not be null.
     */
    public void fstr(int size, Register rt, AArch64Address address) {
        assert rt.getRegisterCategory().equals(SIMD);
        assert size == 32 || size == 64;
        int transferSize = NumUtil.log2Ceil(size / 8);
        loadStoreInstruction(STR, rt, address, InstructionType.FP64, transferSize);
    }

    /* Floating-point Move (register) (5.7.2) */

    /**
     * Floating point move.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst floating point register. May not be null.
     * @param src floating point register. May not be null.
     */
    protected void fmov(int size, Register dst, Register src) {
        fpDataProcessing1Source(FMOV, dst, src, floatFromSize(size));
    }

    /**
     * Move size bits from floating point register unchanged to general purpose register.
     *
     * @param size number of bits read from memory into rt. Must be 32 or 64.
     * @param dst general purpose register. May not be null, stack-pointer or zero-register
     * @param src floating point register. May not be null.
     */
    protected void fmovFpu2Cpu(int size, Register dst, Register src) {
        assert dst.getRegisterCategory().equals(CPU);
        assert src.getRegisterCategory().equals(SIMD);
        fmovCpuFpuInstruction(dst, src, size == 64, Instruction.FMOVFPU2CPU);
    }

    /**
     * Move size bits from general purpose register unchanged to floating point register.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst floating point register. May not be null.
     * @param src general purpose register. May not be null or stack-pointer.
     */
    protected void fmovCpu2Fpu(int size, Register dst, Register src) {
        assert dst.getRegisterCategory().equals(SIMD);
        assert src.getRegisterCategory().equals(CPU);
        fmovCpuFpuInstruction(dst, src, size == 64, Instruction.FMOVCPU2FPU);
    }

    private void fmovCpuFpuInstruction(Register dst, Register src, boolean is64bit, Instruction instr) {
        int sf = is64bit ? FP64.encoding | General64.encoding : FP32.encoding | General32.encoding;
        emitInt(sf | instr.encoding | FpConvertOp | rd(dst) | rs1(src));
    }

    /* Floating-point Move (immediate) (5.7.3) */

    /**
     * Move immediate into register.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst floating point register. May not be null.
     * @param imm immediate that is loaded into dst. If size is 32 only float immediates can be
     *            loaded, i.e. (float) imm == imm must be true. In all cases
     *            {@code isFloatImmediate}, respectively {@code #isDoubleImmediate} must be true
     *            depending on size.
     */
    protected void fmov(int size, Register dst, double imm) {
        assert dst.getRegisterCategory().equals(SIMD);
        InstructionType type = floatFromSize(size);
        int immEncoding;
        if (type == FP64) {
            immEncoding = getDoubleImmediate(imm);
        } else {
            assert imm == (float) imm : "float mov must use an immediate that can be represented using a float.";
            immEncoding = getFloatImmediate((float) imm);
        }
        emitInt(type.encoding | FMOV.encoding | FpImmOp | immEncoding | rd(dst));
    }

    private static int getDoubleImmediate(double imm) {
        assert isDoubleImmediate(imm);
        // bits: aBbb.bbbb.bbcd.efgh.0000.0000.0000.0000
        // 0000.0000.0000.0000.0000.0000.0000.0000
        long repr = Double.doubleToRawLongBits(imm);
        int a = (int) (repr >>> 63) << 7;
        int b = (int) ((repr >>> 61) & 0x1) << 6;
        int cToH = (int) (repr >>> 48) & 0x3f;
        return (a | b | cToH) << FpImmOffset;
    }

    protected static boolean isDoubleImmediate(double imm) {
        // Valid values will have the form:
        // aBbb.bbbb.bbcd.efgh.0000.0000.0000.0000
        // 0000.0000.0000.0000.0000.0000.0000.0000
        long bits = Double.doubleToRawLongBits(imm);
        // lower 48 bits are cleared
        if ((bits & NumUtil.getNbitNumberLong(48)) != 0) {
            return false;
        }
        // bits[61..54] are all set or all cleared.
        long pattern = (bits >> 54) & NumUtil.getNbitNumberLong(7);
        if (pattern != 0 && pattern != NumUtil.getNbitNumberLong(7)) {
            return false;
        }
        // bits[62] and bits[61] are opposites.
        boolean result = ((bits ^ (bits << 1)) & (1L << 62)) != 0;
        return result;
    }

    private static int getFloatImmediate(float imm) {
        assert isFloatImmediate(imm);
        // bits: aBbb.bbbc.defg.h000.0000.0000.0000.0000
        int repr = Float.floatToRawIntBits(imm);
        int a = (repr >>> 31) << 7;
        int b = ((repr >>> 29) & 0x1) << 6;
        int cToH = (repr >>> 19) & NumUtil.getNbitNumberInt(6);
        return (a | b | cToH) << FpImmOffset;
    }

    protected static boolean isFloatImmediate(float imm) {
        // Valid values will have the form:
        // aBbb.bbbc.defg.h000.0000.0000.0000.0000
        int bits = Float.floatToRawIntBits(imm);
        // lower 20 bits are cleared.
        if ((bits & NumUtil.getNbitNumberInt(19)) != 0) {
            return false;
        }
        // bits[29..25] are all set or all cleared
        int pattern = (bits >> 25) & NumUtil.getNbitNumberInt(5);
        if (pattern != 0 && pattern != NumUtil.getNbitNumberInt(5)) {
            return false;
        }
        // bits[29] and bits[30] have to be opposite
        return ((bits ^ (bits << 1)) & (1 << 30)) != 0;
    }

    /* Convert Floating-point Precision (5.7.4.1) */
    /* Converts float to double and vice-versa */

    /**
     * Convert float to double and vice-versa.
     *
     * @param srcSize size of source register in bits.
     * @param dst floating point register. May not be null.
     * @param src floating point register. May not be null.
     */
    public void fcvt(int srcSize, Register dst, Register src) {
        if (srcSize == 32) {
            fpDataProcessing1Source(FCVTDS, dst, src, floatFromSize(srcSize));
        } else {
            fpDataProcessing1Source(FCVTSD, dst, src, floatFromSize(srcSize));
        }
    }

    /* Convert to Integer (5.7.4.2) */

    /**
     * Convert floating point to integer. Rounds towards zero.
     *
     * @param targetSize size of integer register. 32 or 64.
     * @param srcSize size of floating point register. 32 or 64.
     * @param dst general purpose register. May not be null, the zero-register or the stackpointer.
     * @param src floating point register. May not be null.
     */
    public void fcvtzs(int targetSize, int srcSize, Register dst, Register src) {
        assert !dst.equals(zr) && !dst.equals(sp);
        assert src.getRegisterCategory().equals(SIMD);
        fcvtCpuFpuInstruction(FCVTZS, dst, src, generalFromSize(targetSize), floatFromSize(srcSize));
    }

    /* Convert from Integer (5.7.4.2) */
    /**
     * Converts integer to floating point. Uses rounding mode defined by FCPR.
     *
     * @param targetSize size of floating point register. 32 or 64.
     * @param srcSize size of integer register. 32 or 64.
     * @param dst floating point register. May not be null.
     * @param src general purpose register. May not be null or the stackpointer.
     */
    public void scvtf(int targetSize, int srcSize, Register dst, Register src) {
        assert dst.getRegisterCategory().equals(SIMD);
        assert !src.equals(sp);
        fcvtCpuFpuInstruction(SCVTF, dst, src, floatFromSize(targetSize), generalFromSize(srcSize));
    }

    private void fcvtCpuFpuInstruction(Instruction instr, Register dst, Register src, InstructionType type1, InstructionType type2) {
        emitInt(type1.encoding | type2.encoding | instr.encoding | FpConvertOp | rd(dst) | rs1(src));
    }

    /* Floating-point Round to Integral (5.7.5) */

    /**
     * Rounds floating-point to integral. Rounds towards zero.
     *
     * @param size register size.
     * @param dst floating point register. May not be null.
     * @param src floating point register. May not be null.
     */
    protected void frintz(int size, Register dst, Register src) {
        fpDataProcessing1Source(FRINTZ, dst, src, floatFromSize(size));
    }

    /**
     * Rounds floating-point to integral. Rounds towards nearest with ties to even.
     *
     * @param size register size.
     * @param dst floating point register. May not be null.
     * @param src floating point register. May not be null.
     */
    public void frintn(int size, Register dst, Register src) {
        fpDataProcessing1Source(FRINTN, dst, src, floatFromSize(size));
    }

    /**
     * Rounds floating-point to integral. Rounds towards minus infinity.
     *
     * @param size register size.
     * @param dst floating point register. May not be null.
     * @param src floating point register. May not be null.
     */
    public void frintm(int size, Register dst, Register src) {
        fpDataProcessing1Source(FRINTM, dst, src, floatFromSize(size));
    }

    /**
     * Rounds floating-point to integral. Rounds towards plus infinity.
     *
     * @param size register size.
     * @param dst floating point register. May not be null.
     * @param src floating point register. May not be null.
     */
    public void frintp(int size, Register dst, Register src) {
        fpDataProcessing1Source(FRINTP, dst, src, floatFromSize(size));
    }

    /* Floating-point Arithmetic (1 source) (5.7.6) */

    /**
     * dst = |src|.
     *
     * @param size register size.
     * @param dst floating point register. May not be null.
     * @param src floating point register. May not be null.
     */
    public void fabs(int size, Register dst, Register src) {
        fpDataProcessing1Source(FABS, dst, src, floatFromSize(size));
    }

    /**
     * dst = -neg.
     *
     * @param size register size.
     * @param dst floating point register. May not be null.
     * @param src floating point register. May not be null.
     */
    public void fneg(int size, Register dst, Register src) {
        fpDataProcessing1Source(FNEG, dst, src, floatFromSize(size));
    }

    /**
     * dst = Sqrt(src).
     *
     * @param size register size.
     * @param dst floating point register. May not be null.
     * @param src floating point register. May not be null.
     */
    public void fsqrt(int size, Register dst, Register src) {
        fpDataProcessing1Source(FSQRT, dst, src, floatFromSize(size));
    }

    private void fpDataProcessing1Source(Instruction instr, Register dst, Register src, InstructionType type) {
        assert dst.getRegisterCategory().equals(SIMD);
        assert src.getRegisterCategory().equals(SIMD);
        emitInt(type.encoding | instr.encoding | Fp1SourceOp | rd(dst) | rs1(src));
    }

    /* Floating-point Arithmetic (2 source) (5.7.7) */

    /**
     * dst = src1 + src2.
     *
     * @param size register size.
     * @param dst floating point register. May not be null.
     * @param src1 floating point register. May not be null.
     * @param src2 floating point register. May not be null.
     */
    public void fadd(int size, Register dst, Register src1, Register src2) {
        fpDataProcessing2Source(FADD, dst, src1, src2, floatFromSize(size));
    }

    /**
     * dst = src1 - src2.
     *
     * @param size register size.
     * @param dst floating point register. May not be null.
     * @param src1 floating point register. May not be null.
     * @param src2 floating point register. May not be null.
     */
    public void fsub(int size, Register dst, Register src1, Register src2) {
        fpDataProcessing2Source(FSUB, dst, src1, src2, floatFromSize(size));
    }

    /**
     * dst = src1 * src2.
     *
     * @param size register size.
     * @param dst floating point register. May not be null.
     * @param src1 floating point register. May not be null.
     * @param src2 floating point register. May not be null.
     */
    public void fmul(int size, Register dst, Register src1, Register src2) {
        fpDataProcessing2Source(FMUL, dst, src1, src2, floatFromSize(size));
    }

    /**
     * dst = src1 / src2.
     *
     * @param size register size.
     * @param dst floating point register. May not be null.
     * @param src1 floating point register. May not be null.
     * @param src2 floating point register. May not be null.
     */
    public void fdiv(int size, Register dst, Register src1, Register src2) {
        fpDataProcessing2Source(FDIV, dst, src1, src2, floatFromSize(size));
    }

    private void fpDataProcessing2Source(Instruction instr, Register dst, Register src1, Register src2, InstructionType type) {
        assert dst.getRegisterCategory().equals(SIMD);
        assert src1.getRegisterCategory().equals(SIMD);
        assert src2.getRegisterCategory().equals(SIMD);
        emitInt(type.encoding | instr.encoding | Fp2SourceOp | rd(dst) | rs1(src1) | rs2(src2));
    }

    /* Floating-point Multiply-Add (5.7.9) */

    /**
     * dst = src1 * src2 + src3.
     *
     * @param size register size.
     * @param dst floating point register. May not be null.
     * @param src1 floating point register. May not be null.
     * @param src2 floating point register. May not be null.
     * @param src3 floating point register. May not be null.
     */
    protected void fmadd(int size, Register dst, Register src1, Register src2, Register src3) {
        fpDataProcessing3Source(FMADD, dst, src1, src2, src3, floatFromSize(size));
    }

    /**
     * dst = src3 - src1 * src2.
     *
     * @param size register size.
     * @param dst floating point register. May not be null.
     * @param src1 floating point register. May not be null.
     * @param src2 floating point register. May not be null.
     * @param src3 floating point register. May not be null.
     */
    protected void fmsub(int size, Register dst, Register src1, Register src2, Register src3) {
        fpDataProcessing3Source(FMSUB, dst, src1, src2, src3, floatFromSize(size));
    }

    private void fpDataProcessing3Source(Instruction instr, Register dst, Register src1, Register src2, Register src3, InstructionType type) {
        assert dst.getRegisterCategory().equals(SIMD);
        assert src1.getRegisterCategory().equals(SIMD);
        assert src2.getRegisterCategory().equals(SIMD);
        assert src3.getRegisterCategory().equals(SIMD);
        emitInt(type.encoding | instr.encoding | Fp3SourceOp | rd(dst) | rs1(src1) | rs2(src2) | rs3(src3));
    }

    /* Floating-point Comparison (5.7.10) */

    /**
     * Compares src1 to src2.
     *
     * @param size register size.
     * @param src1 floating point register. May not be null.
     * @param src2 floating point register. May not be null.
     */
    public void fcmp(int size, Register src1, Register src2) {
        assert src1.getRegisterCategory().equals(SIMD);
        assert src2.getRegisterCategory().equals(SIMD);
        InstructionType type = floatFromSize(size);
        emitInt(type.encoding | FCMP.encoding | FpCmpOp | rs1(src1) | rs2(src2));
    }

    /**
     * Signalling compares src1 to src2.
     *
     * @param size register size.
     * @param src1 floating point register. May not be null.
     * @param src2 floating point register. May not be null.
     */
    public void fcmpe(int size, Register src1, Register src2) {
        assert src1.getRegisterCategory().equals(SIMD);
        assert src2.getRegisterCategory().equals(SIMD);
        InstructionType type = floatFromSize(size);
        emitInt(type.encoding | FCMP.encoding | FpCmpeOp | rs1(src1) | rs2(src2));
    }

    /**
     * Conditional compare. NZCV = fcmp(src1, src2) if condition else uimm4.
     *
     * @param size register size.
     * @param src1 floating point register. May not be null.
     * @param src2 floating point register. May not be null.
     * @param uimm4 condition flags that are used if condition is false.
     * @param condition every condition allowed. May not be null.
     */
    public void fccmp(int size, Register src1, Register src2, int uimm4, ConditionFlag condition) {
        assert NumUtil.isUnsignedNbit(4, uimm4);
        assert src1.getRegisterCategory().equals(SIMD);
        assert src2.getRegisterCategory().equals(SIMD);
        InstructionType type = floatFromSize(size);
        emitInt(type.encoding | FCCMP.encoding | uimm4 | condition.encoding << ConditionalConditionOffset | rs1(src1) | rs2(src2));
    }

    /**
     * Compare register to 0.0 .
     *
     * @param size register size.
     * @param src floating point register. May not be null.
     */
    public void fcmpZero(int size, Register src) {
        assert src.getRegisterCategory().equals(SIMD);
        InstructionType type = floatFromSize(size);
        emitInt(type.encoding | FCMPZERO.encoding | FpCmpOp | rs1(src));
    }

    /**
     * Signalling compare register to 0.0 .
     *
     * @param size register size.
     * @param src floating point register. May not be null.
     */
    public void fcmpeZero(int size, Register src) {
        assert src.getRegisterCategory().equals(SIMD);
        InstructionType type = floatFromSize(size);
        emitInt(type.encoding | FCMPZERO.encoding | FpCmpeOp | rs1(src));
    }

    /* Floating-point Conditional Select (5.7.11) */

    /**
     * Conditional select. dst = src1 if condition else src2.
     *
     * @param size register size.
     * @param dst floating point register. May not be null.
     * @param src1 floating point register. May not be null.
     * @param src2 floating point register. May not be null.
     * @param condition every condition allowed. May not be null.
     */
    protected void fcsel(int size, Register dst, Register src1, Register src2, ConditionFlag condition) {
        assert dst.getRegisterCategory().equals(SIMD);
        assert src1.getRegisterCategory().equals(SIMD);
        assert src2.getRegisterCategory().equals(SIMD);
        InstructionType type = floatFromSize(size);
        emitInt(type.encoding | FCSEL.encoding | rd(dst) | rs1(src1) | rs2(src2) | condition.encoding << ConditionalConditionOffset);
    }

    /* Debug exceptions (5.9.1.2) */

    /**
     * Halting mode software breakpoint: Enters halting mode debug state if enabled, else treated as
     * UNALLOCATED instruction.
     *
     * @param uimm16 Arbitrary 16-bit unsigned payload.
     */
    protected void hlt(int uimm16) {
        exceptionInstruction(HLT, uimm16);
    }

    /**
     * Monitor mode software breakpoint: exception routed to a debug monitor executing in a higher
     * exception level.
     *
     * @param uimm16 Arbitrary 16-bit unsigned payload.
     */
    protected void brk(int uimm16) {
        exceptionInstruction(BRK, uimm16);
    }

    private void exceptionInstruction(Instruction instr, int uimm16) {
        assert NumUtil.isUnsignedNbit(16, uimm16);
        emitInt(instr.encoding | ExceptionOp | uimm16 << SystemImmediateOffset);
    }

    /* Architectural hints (5.9.4) */
    public enum SystemHint {
        NOP(0x0),
        YIELD(0x1),
        WFE(0x2),
        WFI(0x3),
        SEV(0x4),
        SEVL(0x5),
        CSDB(0x14);

        private final int encoding;

        SystemHint(int encoding) {
            this.encoding = encoding;
        }
    }

    /**
     * Architectural hints.
     *
     * @param hint Can be any of the defined hints. May not be null.
     */
    protected void hint(SystemHint hint) {
        emitInt(HINT.encoding | hint.encoding << SystemImmediateOffset);
    }

    /**
     * Clear Exclusive: clears the local record of the executing processor that an address has had a
     * request for an exclusive access.
     */
    protected void clrex() {
        emitInt(CLREX.encoding);
    }

    /**
     * Possible barrier definitions for Aarch64. LOAD_LOAD and LOAD_STORE map to the same underlying
     * barrier.
     *
     * We only need synchronization across the inner shareable domain (see B2-90 in the Reference
     * documentation).
     */
    public enum BarrierKind {
        LOAD_LOAD(0x9, "ISHLD"),
        LOAD_STORE(0x9, "ISHLD"),
        STORE_STORE(0xA, "ISHST"),
        ANY_ANY(0xB, "ISH");

        public final int encoding;
        public final String optionName;

        BarrierKind(int encoding, String optionName) {
            this.encoding = encoding;
            this.optionName = optionName;
        }
    }

    /**
     * Data Memory Barrier.
     *
     * @param barrierKind barrier that is issued. May not be null.
     */
    public void dmb(BarrierKind barrierKind) {
        emitInt(DMB.encoding | BarrierOp | barrierKind.encoding << BarrierKindOffset);
    }

    public void mrs(Register dst, SystemRegister systemRegister) {
        emitInt(MRS.encoding | systemRegister.encoding() | rt(dst));
    }

    public void msr(SystemRegister systemRegister, Register src) {
        emitInt(MRS.encoding | systemRegister.encoding() | rt(src));
    }

    public void annotatePatchingImmediate(int pos, Instruction instruction, int operandSizeBits, int offsetBits, int shift) {
        if (codePatchingAnnotationConsumer != null) {
            codePatchingAnnotationConsumer.accept(new OperandDataAnnotation(pos, instruction, operandSizeBits, offsetBits, shift));
        }
    }

    void annotatePatchingImmediateNativeAddress(int pos, int operandSizeBits, int numInstrs) {
        if (codePatchingAnnotationConsumer != null) {
            codePatchingAnnotationConsumer.accept(new MovSequenceAnnotation(pos, operandSizeBits, numInstrs));
        }
    }

    public static class OperandDataAnnotation extends CodeAnnotation {

        /**
         * The size of the operand, in bytes.
         */
        public final int operandSizeBits;
        public final int offsetBits;
        public final Instruction instruction;
        public final int shift;

        OperandDataAnnotation(int instructionPosition, Instruction instruction, int operandSizeBits, int offsetBits, int shift) {
            super(instructionPosition);
            this.operandSizeBits = operandSizeBits;
            this.offsetBits = offsetBits;
            this.shift = shift;
            this.instruction = instruction;
        }
    }

    public static class MovSequenceAnnotation extends CodeAnnotation {

        /**
         * The size of the operand, in bytes.
         */
        public final int operandSizeBits;
        public final int numInstrs;

        MovSequenceAnnotation(int instructionPosition, int operandSizeBits, int numInstrs) {
            super(instructionPosition);
            this.operandSizeBits = operandSizeBits;
            this.numInstrs = numInstrs;
        }
    }

}
