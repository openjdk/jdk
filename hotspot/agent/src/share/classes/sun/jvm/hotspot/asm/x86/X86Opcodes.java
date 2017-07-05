/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.asm.x86;

import sun.jvm.hotspot.asm.*;

//Please refer to IA-32 Intel Architecture Software Developer's Manual Volume 2
//APPENDIX A - A.1.

public interface X86Opcodes
                  extends RTLDataTypes, RTLOperations {
   public static final int b_mode = 1;
   public static final int v_mode = 2;
   public static final int w_mode = 3;
   public static final int d_mode = 4;
   public static final int p_mode = 5;

   public static final int dq_mode = 6;     //SSE: double-quadword
   public static final int pi_mode = 7;     //SSE: quadword MMX register
   public static final int ps_mode = 8;     //SSE: 128bit single precision floating point data
   public static final int pd_mode = 9;     //SSE: 128bit double precision floating point data
   public static final int sd_mode = 10;    //SSE: 128bit scalar double precision floating point data
   public static final int  q_mode = 11;    //SSE: quadword
   public static final int ss_mode = 12;    //SSE: scalar element of 128bit floating data
   public static final int si_mode = 13;    //SSE: doubleword integer register (e.g. eax)
   public static final int  s_mode = 14;    //SSE: 6 byte pseudo descriptor

   public static final int INVALID_OPERANDTYPE = -1;

   public static final int EAX = 0;
   public static final int ECX = 1;
   public static final int EDX = 2;
   public static final int EBX = 3;
   public static final int ESP = 4;
   public static final int EBP = 5;
   public static final int ESI = 6;
   public static final int EDI = 7;

   public static final int AX = 8;
   public static final int CX = 9;
   public static final int DX = 10;
   public static final int BX = 11;
   public static final int SP = 12;
   public static final int BP = 13;
   public static final int SI = 14;
   public static final int DI = 15;

   public static final int AL = 16;
   public static final int CL = 17;
   public static final int DL = 18;
   public static final int BL = 19;
   public static final int AH = 20;
   public static final int CH = 21;
   public static final int DH = 22;
   public static final int BH = 23;

   public static final int ES = 24;
   public static final int CS = 25;
   public static final int SS = 26;
   public static final int DS = 27;
   public static final int FS = 28;
   public static final int GS = 29;

   //Addressing modes
   public static final int ADDR_E = 1;
   public static final int ADDR_I = 2;
   public static final int ADDR_DIR = 3;
   public static final int ADDR_J = 4;
   public static final int ADDR_G = 5;
   public static final int ADDR_REG = 6;
   public static final int ADDR_ESDI = 7;
   public static final int ADDR_DSSI = 8;
   public static final int ADDR_SEG = 9;
   public static final int ADDR_OFF = 10;
   public static final int INDIR_REG = 11;
   public static final int ADDR_INDIR_E = 12;
   public static final int ADDR_R = 13;  //mod field selects a register
   public static final int ADDR_C = 14;  //reg field selects a control register
   public static final int ADDR_D = 15; //reg field selects debug register
   public static final int ADDR_T = 16; //reg field selects test register
   public static final int ADDR_M = 17; //modR/M refer only to memory
   public static final int ADDR_FPREG = 18;
   //SSE
   public static final int ADDR_W = 19;  //modR/M: either a 128 bit XMM register or memory
   public static final int ADDR_Q = 20;  //modR/M: either a 128 bit MMX register or memory
   public static final int ADDR_V = 21;  //reg field of modR/M selects a 128-bit XMM register
   public static final int ADDR_P = 22;  //reg field of modR/M selects a 64-bit MMX register

   public static final int INVALID_ADDRMODE = -1;

   //Refer to chapter 2 - Instruction Format
   //Prefix codes
   public static final int PREFIX_REPZ = 1;
   public static final int PREFIX_REPNZ = 2;
   public static final int PREFIX_LOCK = 4;
   public static final int PREFIX_CS = 8;
   public static final int PREFIX_SS = 0x10;
   public static final int PREFIX_DS = 0x20;
   public static final int PREFIX_ES = 0x40;
   public static final int PREFIX_FS = 0x80;
   public static final int PREFIX_GS = 0x100;
   public static final int PREFIX_DATA = 0x200;
   public static final int PREFIX_ADR = 0x400;
   public static final int PREFIX_FWAIT = 0x800;
}
