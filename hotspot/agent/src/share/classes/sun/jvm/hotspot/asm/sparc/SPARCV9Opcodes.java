/*
 * Copyright (c) 2002, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

package sun.jvm.hotspot.asm.sparc;

import sun.jvm.hotspot.asm.*;

// Please refer to "The SPARC Architecture Manual - Version 9"

public interface SPARCV9Opcodes extends SPARCOpcodes {
    // format 2, v9 specific "op2" values.

    // branch on integer condition codes with prediction
    public static final int OP_2_BPcc    = 1;

    // branch on integer register contents with prediction
    public static final int OP_2_BPr     = 3;

    // branch on float condition codes with prediction
    public static final int OP_2_FBPfcc  = 5;

    // "rcond" - branch on register condition
    public static final int BRANCH_RCOND_START_BIT  = 25;

    // rcond is 3 bits length
    public static final int BRANCH_RCOND_MASK = 7 << BRANCH_RCOND_START_BIT;

    // "rcond" - as used in conditional moves
    public static final int CMOVE_RCOND_START_BIT = 10;
    public static final int CMOVE_RCOND_MASK = 7 << CMOVE_RCOND_START_BIT;

    public static final int IMPDEP1 = CPop1;
    public static final int IMPDEP2 = CPop2;

    // various rcond values - used in BPr, MOVr and FMOVr

    // reserved register condition
    public static final int BRANCH_RCOND_RESERVED1 = 0; // 000

    public static final int BRZ     = 1;
    public static final int MOVRZ   = BRZ;
    public static final int FMOVZ   = BRZ;

    public static final int BRLEZ   = 2;
    public static final int MOVRLEZ = BRLEZ;
    public static final int FMOVLEZ = BRLEZ;

    public static final int BRLZ    = 3;
    public static final int MOVRLZ  = BRLZ;
    public static final int FMOVLZ  = BRLZ;

    // reserved register condition
    public static final int BRANCH_RCOND_RESERVED2 = 4; // 100

    public static final int BRNZ    = 5;
    public static final int MOVRNZ  = BRNZ;
    public static final int FMOVNZ  = BRNZ;

    public static final int BRGZ    = 6;
    public static final int MOVGZ   = BRGZ;
    public static final int FMOVGZ  = BRGZ;

    public static final int BRGEZ   = 7;
    public static final int MOVRGEZ = BRGEZ;
    public static final int FMOVGEZ = BRGEZ;

    // "p" - prediction bit - predict branch taken or not taken
    public static final int PREDICTION_START_BIT = 19;
    public static final int PREDICTION_MASK = 1 << PREDICTION_START_BIT;

    // branch pc relative displacement - hi 2 bits of disp16.
    public static final int DISP_16_HI_START_BIT = 20;

    // disp 16 hi is 2 bits length
    public static final int DISP_16_HI_MASK = 3 << DISP_16_HI_START_BIT;

    // disp 16 low 14 bits
    public static final int DISP_16_LO_START_BIT = 0; // just for completion.
    public static final int DISP_16_LO_MASK = 0x3FFF;
    public static final int DISP_16_LO_NUMBITS = 14;

    // disp 19 - integer branch with prediction - displacement
    public static final int DISP_19_MASK = 0x7FFFF;

    /*
     * condition code selected for integer branches - cc1 & cc0.
     * condition code selected for float branches - cc1 & cc0.
     * opf_cc field - floating conditional moves - 3 bits.
     * convert 2 bit codes as 3 bit codes always and use following codes
     * uniformly.
    */

    // opf_cc - 3 bits
    public static final int OPF_CC_START_BIT = 11;
    public static final int OPF_CC_MASK = 7 << OPF_CC_START_BIT;

    public static final int fcc0 = 0;  // 000
    public static final int fcc1 = 1;  // 001
    public static final int fcc2 = 2;  // 010
    public static final int fcc3 = 3;  // 011
    public static final int icc  = 4;  // 100
    public static final int CFLAG_RESERVED1 = 5; // 101
    public static final int xcc  = 6;  // 110
    public static final int CFLAG_RESERVED2 = 7; // 111

    // cc0, cc1 as in integer, float predicted branches
    public static final int BPcc_CC_START_BIT = 20;
    public static final int BPcc_CC_MASK = 3 << BPcc_CC_START_BIT;
    public static final int FBPfcc_CC_START_BIT = BPcc_CC_START_BIT;
    public static final int FBPfcc_CC_MASK = BPcc_CC_MASK;

    // condition codes for integer branches with prediction - BPcc
    public static final int CONDITION_BPN   = CONDITION_BN;
    public static final int CONDITION_BPE   = CONDITION_BE;
    public static final int CONDITION_BPLE  = CONDITION_BLE;
    public static final int CONDITION_BPL   = CONDITION_BL;
    public static final int CONDITION_BPLEU = CONDITION_BLEU;
    public static final int CONDITION_BPCS  = CONDITION_BCS;
    public static final int CONDITION_BPNEG = CONDITION_BNEG;
    public static final int CONDITION_BPVS  = CONDITION_BVS;
    public static final int CONDITION_BPA   = CONDITION_BA;
    public static final int CONDITION_BPNE  = CONDITION_BNE;
    public static final int CONDITION_BPG   = CONDITION_BG;
    public static final int CONDITION_BPGE  = CONDITION_BGE;
    public static final int CONDITION_BPGU  = CONDITION_BGU;
    public static final int CONDITION_BPCC  = CONDITION_BCC;
    public static final int CONDITION_BPPOS = CONDITION_BPOS;
    public static final int CONDITION_BPVC  = CONDITION_BVC;

    // condition codes for float branches with prediction
    public static final int CONDITION_FBPN  = CONDITION_BN;
    public static final int CONDITION_FBPNE = CONDITION_BE;
    public static final int CONDITION_FBPLG = CONDITION_BLE;
    public static final int CONDITION_FBPUL = CONDITION_BL;
    public static final int CONDITION_FBPL  = CONDITION_BLEU;
    public static final int CONDITION_FBPUG = CONDITION_BCS;
    public static final int CONDITION_FBPG  = CONDITION_BNEG;
    public static final int CONDITION_FBPU  = CONDITION_BVS;
    public static final int CONDITION_FBPA  = CONDITION_BA;
    public static final int CONDITION_FBPE  = CONDITION_BNE;
    public static final int CONDITION_FBPUE = CONDITION_BG;
    public static final int CONDITION_FBPGE = CONDITION_BGE;
    public static final int CONDITION_FBPUGE= CONDITION_BGU;
    public static final int CONDITION_FBPLE = CONDITION_BCC;
    public static final int CONDITION_FBPULE= CONDITION_BPOS;
    public static final int CONDITION_FBPO  = CONDITION_BVC;

    // "cmask" - 3 bit mask used in membar for completion constraints
    public static final int CMASK_START_BIT = 4;
    public static final int CMASK_MASK = 7 << CMASK_START_BIT;

    // "mmask" - 4 bit mask used in member for ordering instruction classes.
    public static final int MMASK_START_BIT = 0;
    public static final int MMASK_MASK = 0xF; // no need to shift

    // v9 specific load/store instruction opcodes
    // load/store instructions - op3 values - used with op=3 (FORMAT_3)

    public static final int LDUW   = LD;
    public static final int LDUWA  = LDA;

    public static final int LDXFSR = LDFSR;

    public static final int LDFA   = LDC;
    public static final int LDQF   = (2 << 4) | 2;
    public static final int LDQFA  = (3 << 4) | 2;
    public static final int LDDFA  = LDDC;

    public static final int STW    = ST;
    public static final int STWA   = STA;
    public static final int STFA   = STC;

    public static final int STXFSR = STFSR;

    public static final int STQF   = STDFQ;
    public static final int STQFA  = STDCQ;
    public static final int STDFA  = STDC;

    public static final int LDSW   = 8;
    public static final int LDSWA  = (1 << 4) | 8;

    public static final int LDX    = 0xB;
    public static final int LDXA   = (1 << 4) | 0xB;

    public static final int PREFETCH  = (2 << 4) | 0xD;
    public static final int PREFETCHA = (3 << 4) | 0xD;

    public static final int CASA   = (3 << 4) | 0xC;

    public static final int STX    = 0xE;
    public static final int STXA   = (1 << 4) | 0xE;
    public static final int CASXA  = (3 << 4) | 0xE;

    // 6 bit immediate shift count mask
    public static final int SHIFT_COUNT_6_MASK = 0x3F;

    // X bit mask - used to differentiate b/w 32 bit and 64 bit shifts
    public static final int X_MASK = 1 << 12;

    // E Opcode maps - Page 274 - Table 32 - op3 (op=2) table
    // v9 specific items
    public static final int ADDC   = ADDX;
    public static final int ADDCcc = ADDXcc;

    public static final int SUBC   = SUBX;
    public static final int SUBCcc = SUBXcc;

    public static final int MULX   = 9;
    public static final int UDIVX  = 0xD;

    public static final int SLLX   = SLL;
    public static final int SRLX   = SRL;
    public static final int SRAX   = SRA;

    // special register reads
    public static final int RDCCR  = RDY;
    public static final int RDASI  = RDY;
    public static final int RDTICK = RDY;
    public static final int RDPC   = RDY;
    public static final int RDFPRS = RDY;
    public static final int MEMBAR = RDY;
    public static final int STMBAR = RDY;

    public static final int RDPR   = (2 << 4) | 0xA;

    public static final int FLUSHW = (2 << 4) | 0xB;

    public static final int MOVcc  = (2 << 4) | 0xC;

    public static final int SDIVX  = (2 << 4) | 0xD;

    public static final int POPC   = (2 << 4) | 0xE;

    public static final int MOVr   = (2 << 4) | 0xF;

    // special regitser writes
    public static final int WRCCR  = WRY;
    public static final int WRASI  = WRY;
    public static final int WRFPRS = WRY;
    public static final int SIR    = WRY;

    public static final int SAVED  = (3 << 4) | 0x1;
    public static final int RESTORED = SAVED;

    public static final int WRPR   = (3 << 4) | 0x2;

    public static final int RETURN = RETT;

    public static final int DONE   = (3 << 4) | 0xE;
    public static final int RETRY  = DONE;

    // various integer condition code move instructions
    public static final int CONDITION_MOVN    = CONDITION_BN;
    public static final int CONDITION_MOVE    = CONDITION_BE;
    public static final int CONDITION_MOVLE   = CONDITION_BLE;
    public static final int CONDITION_MOVL    = CONDITION_BL;
    public static final int CONDITION_MOVLEU  = CONDITION_BLEU;
    public static final int CONDITION_MOVCS   = CONDITION_BCS;
    public static final int CONDITION_MOVNEG  = CONDITION_BNEG;
    public static final int CONDITION_MOVVS   = CONDITION_BVS;
    public static final int CONDITION_MOVA    = CONDITION_BA;
    public static final int CONDITION_MOVNE   = CONDITION_BNE;
    public static final int CONDITION_MOVG    = CONDITION_BG;
    public static final int CONDITION_MOVGE   = CONDITION_BGE;
    public static final int CONDITION_MOVGU   = CONDITION_BGU;
    public static final int CONDITION_MOVCC   = CONDITION_BCC;
    public static final int CONDITION_MOVPOS  = CONDITION_BPOS;
    public static final int CONDITION_MOVVC   = CONDITION_BVC;

    // cc0, cc1 & cc2 in conditional moves
    public static final int CMOVE_CC_START_BIT  = 11;
    public static final int CMOVE_CC0_CC1_MASK  = 3 << CMOVE_CC_START_BIT;
    public static final int CMOVE_CC2_START_BIT = 18;
    public static final int CMOVE_CC2_MASK      = 1 << CMOVE_CC2_START_BIT;

    public static final int CMOVE_COND_START_BIT = 14;
    // condition code is 4 bits
    public static final int CMOVE_COND_MASK = 0xF << CMOVE_COND_START_BIT;

    // opf[8:0] (op=2,op3=0x34=FPop1) - Table 34 - Page 276 - E Opcode Maps
    // v9 specific opcodes only - remaining are in SPARCOpcodes.

    public static final int FMOVd = 0x2;
    public static final int FMOVq = 0x3;
    public static final int FNEGd = 0x6;
    public static final int FNEGq = 0x7;
    public static final int FABSd = 0xA;
    public static final int FABSq = 0xB;
    public static final int FsTOx = (0x8 << 4) | 0x1;
    public static final int FdTOx = (0x8 << 4) | 0x2;
    public static final int FqTOx = (0x8 << 4) | 0x3;
    public static final int FxTOs = (0x8 << 4) | 0x4;
    public static final int FxTOd = (0x8 << 4) | 0x8;
    public static final int FxTOq = (0x8 << 4) | 0xC;

    // opf[8:0] (op=2, op3=0x35= FPop2) - Table 35 - Page 277 - E.2 Tables
    // v9 specific opcodes only 0 remanining are in SPARCOpcodes.

    // fp condition moves

    public static final int FMOVs_fcc0 = 1;
    public static final int FMOVs_fcc1 = 1 | (0x4 << 4);
    public static final int FMOVs_fcc2 = 1 | (0x8 << 4);
    public static final int FMOVs_fcc3 = 1 | (0xC << 4);
    public static final int FMOVs_icc  = 1 | (0x10 << 4);
    public static final int FMOVs_xcc  = 1 | (0x18 << 4);

    public static final int FMOVd_fcc0 = 2;
    public static final int FMOVd_fcc1 = 2 | (0x4 << 4);
    public static final int FMOVd_fcc2 = 2 | (0x8 << 4);
    public static final int FMOVd_fcc3 = 2 | (0xC << 4);
    public static final int FMOVd_icc  = 2 | (0x10 << 4);
    public static final int FMOVd_xcc  = 2 | (0x18 << 4);

    public static final int FMOVq_fcc0 = 3;
    public static final int FMOVq_fcc1 = 3 | (0x4 << 4);
    public static final int FMOVq_fcc2 = 3 | (0x8 << 4);
    public static final int FMOVq_fcc3 = 3 | (0xC << 4);
    public static final int FMOVq_icc  = 3 | (0x10 << 4);
    public static final int FMOVq_xcc  = 3 | (0x18 << 4);

    // fp register condition moves

    public static final int FMOVRsZ    = 5 | (0x2 << 4);
    public static final int FMOVRsLEZ  = 5 | (0x4 << 4);
    public static final int FMOVRsLZ   = 5 | (0x6 << 4);
    public static final int FMOVRsNZ   = 5 | (0xA << 4);
    public static final int FMOVRsGZ   = 5 | (0xC << 4);
    public static final int FMOVRsGEZ  = 5 | (0xE << 4);

    public static final int FMOVRdZ    = 6 | (0x2 << 4);
    public static final int FMOVRdLEZ  = 6 | (0x4 << 4);
    public static final int FMOVRdLZ   = 6 | (0x6 << 4);
    public static final int FMOVRdNZ   = 6 | (0xA << 4);
    public static final int FMOVRdGZ   = 6 | (0xC << 4);
    public static final int FMOVRdGEZ  = 6 | (0xE << 4);

    public static final int FMOVRqZ    = 7 | (0x2 << 4);
    public static final int FMOVRqLEZ  = 7 | (0x4 << 4);
    public static final int FMOVRqLZ   = 7 | (0x6 << 4);
    public static final int FMOVRqNZ   = 7 | (0xA << 4);
    public static final int FMOVRqGZ   = 7 | (0xC << 4);
    public static final int FMOVRqGEZ  = 7 | (0xE << 4);
}
