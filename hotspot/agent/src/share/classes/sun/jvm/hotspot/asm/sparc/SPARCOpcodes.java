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

// Please refer to "The SPARC Architecture Manual - Version 8"

public interface SPARCOpcodes {

   // format type is coded in 2 bits - primary opcode - "op"
   public static final int FORMAT_START_BIT = 30;
   public static final int FORMAT_MASK = 3 << FORMAT_START_BIT;

   // sparc instruction formats

   // PC Relative CALL
   public static final int FORMAT_1 = 1;

   // Bicc, FBfcc, CBccc, SETHI
   public static final int FORMAT_2 = 0;

   // memory instructions
   public static final int FORMAT_3 = 3;

   // arithmetic, logical, shift and remaining
   public static final int FORMAT_3A = 2;

   // disp 30 - used in pc relative call
   public static final int DISP_30_MASK = 0x3FFFFFFF;

   // secondary opcode "op2" used in FORMAT_2 instructions - 3 bits.
   public static final int OP_2_START_BIT = 22;
   public static final int OP_2_MASK = 7 << OP_2_START_BIT;

   // various "op2" masks
   public static final int OP_2_UNIMP = 0;
   public static final int OP_2_Bicc  = 2;
   public static final int OP_2_SETHI = 4;
   public static final int OP_2_FBfcc = 6;
   public static final int OP_2_CBccc = 7;

   // condition codes are encoded in 4 bits.
   public static final int CONDITION_CODE_START_BIT = 25;
   public static final int CONDITION_CODE_MASK = 0xF << CONDITION_CODE_START_BIT;

   // branch condition codes
   public static final int CONDITION_BN   = 0;
   public static final int CONDITION_FBN  = CONDITION_BN;
   public static final int CONDITION_CBN  = CONDITION_BN;
   public static final int CONDITION_TN   = CONDITION_BN;

   public static final int CONDITION_BE   = 1;
   public static final int CONDITION_FBNE = CONDITION_BE;
   public static final int CONDITION_CB123= CONDITION_BE;
   public static final int CONDITION_TE   = CONDITION_BE;

   public static final int CONDITION_BLE  = 2;
   public static final int CONDITION_FBLG = CONDITION_BLE;
   public static final int CONDITION_CB12 = CONDITION_BLE;
   public static final int CONDITION_TLE  = CONDITION_BLE;

   public static final int CONDITION_BL   = 3;
   public static final int CONDITION_FBUL = CONDITION_BL;
   public static final int CONDITION_CB13 = CONDITION_BL;
   public static final int CONDITION_TL   = CONDITION_BL;

   public static final int CONDITION_BLEU = 4;
   public static final int CONDITION_FBL  = CONDITION_BLEU;
   public static final int CONDITION_CB1  = CONDITION_BLEU;
   public static final int CONDITION_TLEU = CONDITION_BLEU;

   public static final int CONDITION_BCS  = 5;
   public static final int CONDITION_FBUG = CONDITION_BCS;
   public static final int CONDITION_CB23  = CONDITION_BCS;
   public static final int CONDITION_TCS  = CONDITION_BCS;

   public static final int CONDITION_BNEG = 6;
   public static final int CONDITION_FBG  = CONDITION_BNEG;
   public static final int CONDITION_CB2  = CONDITION_BNEG;
   public static final int CONDITION_TNEG = CONDITION_BNEG;

   public static final int CONDITION_BVS  = 7;
   public static final int CONDITION_FBU  = CONDITION_BVS;
   public static final int CONDITION_CB3  = CONDITION_BVS;
   public static final int CONDITION_TVS  = CONDITION_BVS;

   public static final int CONDITION_BA   = 8;
   public static final int CONDITION_FBA  = CONDITION_BA;
   public static final int CONDITION_CBA  = CONDITION_BA;
   public static final int CONDITION_TA   = CONDITION_BA;

   public static final int CONDITION_BNE  = 9;
   public static final int CONDITION_FBE  = CONDITION_BNE;
   public static final int CONDITION_CB0  = CONDITION_BNE;
   public static final int CONDITION_TNE  = CONDITION_BNE;

   public static final int CONDITION_BG   = 0xA;
   public static final int CONDITION_FBUE = CONDITION_BG;
   public static final int CONDITION_CB03 = CONDITION_BG;
   public static final int CONDITION_TG   = CONDITION_BG;

   public static final int CONDITION_BGE  = 0xB;
   public static final int CONDITION_FBGE = CONDITION_BGE;
   public static final int CONDITION_CB02 = CONDITION_BGE;
   public static final int CONDITION_TGE  = CONDITION_BGE;

   public static final int CONDITION_BGU  = 0xC;
   public static final int CONDITION_FBUGE= CONDITION_BGU;
   public static final int CONDITION_CB023= CONDITION_BGU;
   public static final int CONDITION_TGU  = CONDITION_BGU;

   public static final int CONDITION_BCC  = 0xD;
   public static final int CONDITION_FBLE = CONDITION_BCC;
   public static final int CONDITION_CB01 = CONDITION_BCC;
   public static final int CONDITION_TCC  = CONDITION_BCC;

   public static final int CONDITION_BPOS = 0xE;
   public static final int CONDITION_FBULE= CONDITION_BPOS;
   public static final int CONDITION_CB013= CONDITION_BPOS;
   public static final int CONDITION_TPOS = CONDITION_BPOS;

   public static final int CONDITION_BVC  = 0xF;
   public static final int CONDITION_FBO  = CONDITION_BVC;
   public static final int CONDITION_CB012= CONDITION_BVC;
   public static final int CONDITION_TVC  = CONDITION_BVC;

   // annul bit mask
   public static final int ANNUL_MASK = 1 << 29;

   // 22 bit displacement or immediate value - used in FORMAT_2 instructions.
   public static final int DISP_22_MASK = 0x3FFFFF;
   public static final int IMM_22_MASK  = DISP_22_MASK;

   // second operand mask, called "i" bit
   public static final int I_START_BIT = 13;
   public static final int I_MASK = 1 << I_START_BIT;

   // address space identifier - "asi" - 8 bits
   public static final int ASI_START_BIT = 5;
   public static final int ASI_MASK = 0xFF << ASI_START_BIT;

   // signed immediate value 13 bits - "simm13"
   public static final int SIMM_13_MASK = 0x1FFF;

   // co-processor or floating point opcode field - "ocf/opf" - 9 bits
   public static final int OPF_START_BIT = 5;
   public static final int OPF_MASK = 0x1FF << OPF_START_BIT;
   public static final int OPC_MASK = OPF_MASK;

   // opcode part 3 - used in FORMAT_3 and FORMAT_3A instructions
   // "op3" - 6 bits
   public static final int OP_3_START_BIT = 19;
   public static final int OP_3_MASK = 0x3F << OP_3_START_BIT;

   // register masks
   public static final int RD_START_BIT = 25;
   public static final int RD_MASK  = 0x1F << RD_START_BIT; // "rd"
   public static final int RS1_START_BIT = 14;
   public static final int RS1_MASK = 0x1F << RS1_START_BIT; // "rs1"
   public static final int RS2_MASK = 0x1F;       // "rs2"

   // load/store instructions - op3 values - used with op=3 (FORMAT_3)
   public static final int LD  = 0;
   public static final int LDA = (1 << 4);
   public static final int LDF = (2 << 4);
   public static final int LDC = (3 << 4);

   public static final int LDUB = 1;
   public static final int LDUBA = (1 << 4) | 1;
   public static final int LDFSR = (2 << 4) | 1;
   public static final int LDCSR = (3 << 4) | 1;

   public static final int LDUH  = 2;
   public static final int LDUHA = (1 << 4) | 2;

   public static final int LDD = 3;
   public static final int LDDA = (1 << 4) | 3;
   public static final int LDDF = (2 << 4) | 3;
   public static final int LDDC = (3 << 4) | 3;

   public static final int ST = 4;
   public static final int STA = (1 << 4) | 4;
   public static final int STF = (2 << 4) | 4;
   public static final int STC = (3 << 4) | 4;

   public static final int STB = 5;
   public static final int STBA = (1 << 4) | 5;
   public static final int STFSR = (2 << 4) | 5;
   public static final int STCSR = (3 << 4) | 5;

   public static final int STH = 6;
   public static final int STHA = (1 << 4) | 6;
   public static final int STDFQ = (2 << 4) | 6;
   public static final int STDCQ = (3 << 4) | 6;

   public static final int STD = 7;
   public static final int STDA = (1 << 4) | 7;
   public static final int STDF = (2 << 4) | 7;
   public static final int STDC = (3 << 4) | 7;

   public static final int LDSB = 9;
   public static final int LDSBA = (1 << 4) | 9;

   public static final int LDSH = 0xA;
   public static final int LDSHA = (1 << 4) | 0xA;

   public static final int LDSTUB = 0xD;
   public static final int LDSTUBA = (1 << 4) | 0xD;

   public static final int SWAP = 0xF;
   public static final int SWAPA = (1 << 4) | 0xF;

   // arithmetic, logic remaining - op3 with op=2 (FORMAT_3A)
   public static final int ADD = 0;
   public static final int ADDcc = (1 << 4);
   public static final int TADDcc = (2 << 4);
   public static final int WRASR  = (3 << 4);
   public static final int WRY = WRASR;

   public static final int AND = 1;
   public static final int ANDcc = (1 << 4) | 1;
   public static final int TSUBcc = (2 << 4) | 1;
   public static final int WRPSR = (3 << 4) | 1;

   public static final int OR = 2;
   public static final int ORcc = (1 << 4) | 2;
   public static final int TADDccTV = (2 << 4) | 2;
   public static final int WRWIM = (3 << 4) | 2;

   public static final int XOR = 3;
   public static final int XORcc = (1 << 4) | 3;
   public static final int TSUBccTV = (2 << 4) | 3;
   public static final int WRTBR = (3 << 4) | 3;

   public static final int SUB = 4;
   public static final int SUBcc = (1 << 4) | 4;
   public static final int MULScc = (2 << 4) | 4;
   public static final int FPop1 = (3 << 4) | 4;

   public static final int ANDN = 5;
   public static final int ANDNcc = (1 << 4) | 5;
   public static final int SLL = (2 << 4) | 5;
   public static final int FPop2 = (3 << 4) | 5;

   public static final int ORN = 6;
   public static final int ORNcc = (1 << 4) | 6;
   public static final int SRL = (2 << 4) | 6;
   public static final int CPop1 = (3 << 4) | 6;

   public static final int XNOR = 7;
   public static final int XNORcc = (1 << 4) | 7;
   public static final int SRA = (2 << 4) | 7;
   public static final int CPop2 = (3 << 4) | 7;

   public static final int ADDX = 8;
   public static final int ADDXcc = (1 << 4) | 8;
   public static final int RDASR = (2 << 4) | 8;
   public static final int RDY = RDASR;
   public static final int STBAR = RDASR;
   public static final int JMPL = (3 << 4) | 8;

   public static final int RDPSR = (2 << 4) | 9;
   public static final int RETT = (3 << 4) | 9;

   public static final int UMUL = 0xA;
   public static final int UMULcc = (1 << 4) | 0xA;
   public static final int RDWIM = (2 << 4) |  0xA;
   public static final int Ticc  = (3 << 4) | 0xA;

   public static final int SMUL = 0xB;
   public static final int SMULcc = (1 << 4) | 0xB;
   public static final int RDTBR = (2 << 4) | 0xB;
   public static final int FLUSH = (3 << 4) | 0xB;

   public static final int SUBX = 0xC;
   public static final int SUBXcc = (1 << 4) | 0xC;
   public static final int SAVE = (3 << 4) | 0xC;

   public static final int RESTORE = (3 << 4) | 0xD;

   public static final int UDIV = 0xE;
   public static final int UDIVcc = (1 << 4) | 0xE;

   public static final int SDIV = 0xF;
   public static final int SDIVcc = (1 << 4) | 0xF;

   // opf - 9 bits (op=2, op3=0x34=FPop1) - floating point arithmetic
   public static final int FMOVs    = 0x01;
   public static final int FNEGs    = 0x05;
   public static final int FABSs    = 0x09;
   public static final int FSQRTs   = 0x29;
   public static final int FSQRTd   = 0x2A;
   public static final int FSQRTq   = 0x2B;
   public static final int FADDs    = 0x41;
   public static final int FADDd    = 0x42;
   public static final int FADDq    = 0x43;
   public static final int FSUBs    = 0x45;
   public static final int FSUBd    = 0x46;
   public static final int FSUBq    = 0x47;
   public static final int FMULs    = 0x49;
   public static final int FMULd    = 0x4A;
   public static final int FMULq    = 0x4B;
   public static final int FDIVs    = 0x4D;
   public static final int FDIVd    = 0x4E;
   public static final int FDIVq    = 0x4F;
   public static final int FsMULd   = 0x69;
   public static final int FdMULq   = 0x6E;
   public static final int FiTOs    = 0xC4;
   public static final int FdTOs    = 0xC6;
   public static final int FqTOs    = 0xC7;
   public static final int FiTOd    = 0xC8;
   public static final int FsTOd    = 0xC9;
   public static final int FqTOd    = 0xCB;
   public static final int FiTOq    = 0xCC;
   public static final int FsTOq    = 0xCD;
   public static final int FdTOq    = 0xCE;
   public static final int FsTOi    = 0xD1;
   public static final int FdTOi    = 0xD2;
   public static final int FqTOi    = 0xD3;

   // opf - 9 bits (op=2, op3=0x35=FPop2) - floating point comparisons
   public static final int FCMPs    = 0x51;
   public static final int FCMPd    = 0x52;
   public static final int FCMPq    = 0x53;
   public static final int FCMPEs   = 0x55;
   public static final int FCMPEd   = 0x56;
   public static final int FCMPEq   = 0x57;

   // 5 bit shift count mask
   public static final int SHIFT_COUNT_5_MASK = 0x1F;
}
