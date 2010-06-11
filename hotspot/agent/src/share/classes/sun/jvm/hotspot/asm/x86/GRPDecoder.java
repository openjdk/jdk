/*
 * Copyright (c) 2002, 2003, Oracle and/or its affiliates. All rights reserved.
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

public class GRPDecoder extends InstructionDecoder {

   final private int number;
   //Please refer to IA-32 Intel Architecture Software Developer's Manual Volume 2
   //APPENDIX A - Table A-4. Opcode Extensions for One and Two-byte Opcodes by Group Number.
   private static final InstructionDecoder grpTable[][] = {
      {
      new ArithmeticDecoder("addb", ADDR_E, b_mode, ADDR_I, b_mode, RTLOP_ADD),
      new LogicalDecoder("orb", ADDR_E, b_mode, ADDR_I, b_mode, RTLOP_OR),
      new ArithmeticDecoder("adcb", ADDR_E, b_mode, ADDR_I, b_mode, RTLOP_ADDC),
      new ArithmeticDecoder("sbbb", ADDR_E, b_mode, ADDR_I, b_mode, RTLOP_SUBC),
      new LogicalDecoder("andb", ADDR_E, b_mode, ADDR_I, b_mode, RTLOP_AND),
      new ArithmeticDecoder("subb", ADDR_E, b_mode, ADDR_I, b_mode, RTLOP_SUB),
      new LogicalDecoder("xorb", ADDR_E, b_mode, ADDR_I, b_mode, RTLOP_XOR),
      new InstructionDecoder("cmpb", ADDR_E, b_mode, ADDR_I, b_mode)
      },
      {
      new ArithmeticDecoder("addS", ADDR_E, v_mode, ADDR_I, v_mode, RTLOP_ADD),
      new LogicalDecoder("orS", ADDR_E, v_mode, ADDR_I, v_mode, RTLOP_OR),
      new ArithmeticDecoder("adcS", ADDR_E, v_mode, ADDR_I, v_mode, RTLOP_ADDC),
      new ArithmeticDecoder("sbbS", ADDR_E, v_mode, ADDR_I, v_mode, RTLOP_SUBC),
      new LogicalDecoder("andS", ADDR_E, v_mode, ADDR_I, v_mode, RTLOP_AND),
      new ArithmeticDecoder("subS", ADDR_E, v_mode, ADDR_I, v_mode, RTLOP_SUB),
      new LogicalDecoder("xorS", ADDR_E, v_mode, ADDR_I, v_mode, RTLOP_XOR),
      new InstructionDecoder("cmpS", ADDR_E, v_mode, ADDR_I, v_mode)
      },
      {
      new ArithmeticDecoder("addS", ADDR_E, v_mode, ADDR_I, b_mode, RTLOP_ADD), /*note: sIb here*/
      new LogicalDecoder("orS", ADDR_E, v_mode, ADDR_I, b_mode, RTLOP_OR),
      new ArithmeticDecoder("adcS", ADDR_E, v_mode, ADDR_I, b_mode, RTLOP_ADDC),
      new ArithmeticDecoder("sbbS", ADDR_E, v_mode, ADDR_I, b_mode, RTLOP_SUBC),
      new LogicalDecoder("andS", ADDR_E, v_mode, ADDR_I, b_mode, RTLOP_AND),
      new ArithmeticDecoder("subS", ADDR_E, v_mode, ADDR_I, b_mode, RTLOP_SUB),
      new LogicalDecoder("xorS", ADDR_E, v_mode, ADDR_I, b_mode, RTLOP_XOR),
      new InstructionDecoder("cmpS", ADDR_E, v_mode, ADDR_I, b_mode)
      },
      {
      new RotateDecoder("rolb", ADDR_E, b_mode, ADDR_I, b_mode),
      new RotateDecoder("rorb", ADDR_E, b_mode, ADDR_I, b_mode),
      new RotateDecoder("rclb", ADDR_E, b_mode, ADDR_I, b_mode),
      new RotateDecoder("rcrb", ADDR_E, b_mode, ADDR_I, b_mode),
      new ShiftDecoder("shlb", ADDR_E, b_mode, ADDR_I, b_mode, RTLOP_SLL),
      new ShiftDecoder("shrb", ADDR_E, b_mode, ADDR_I, b_mode, RTLOP_SRL),
      null,
      new ShiftDecoder("sarb", ADDR_E, b_mode, ADDR_I, b_mode, RTLOP_SRA),
      },
      {
      new RotateDecoder("rolS", ADDR_E, v_mode, ADDR_I, b_mode),
      new RotateDecoder("rorS", ADDR_E, v_mode, ADDR_I, b_mode),
      new RotateDecoder("rclS", ADDR_E, v_mode, ADDR_I, b_mode),
      new RotateDecoder("rcrS", ADDR_E, v_mode, ADDR_I, b_mode),
      new ShiftDecoder("shlS", ADDR_E, v_mode, ADDR_I, b_mode,  RTLOP_SLL),
      new ShiftDecoder("shrS", ADDR_E, v_mode, ADDR_I, b_mode, RTLOP_SRL),
      null,
      new ShiftDecoder("sarS", ADDR_E, v_mode, ADDR_I, b_mode, RTLOP_SRA)
      },
      {
      new RotateDecoder("rolb", ADDR_E, b_mode),
      new RotateDecoder("rorb", ADDR_E, b_mode),
      new RotateDecoder("rclb", ADDR_E, b_mode),
      new RotateDecoder("rcrb", ADDR_E, b_mode),
      new ShiftDecoder("shlb", ADDR_E, b_mode, RTLOP_SLL),
      new ShiftDecoder("shrb", ADDR_E, b_mode, RTLOP_SRL),
      null,
      new ShiftDecoder("sarb", ADDR_E, b_mode, RTLOP_SRA)
      },
      {
      new RotateDecoder("rolS", ADDR_E, v_mode),
      new RotateDecoder("rorS", ADDR_E, v_mode),
      new RotateDecoder("rclS", ADDR_E, v_mode),
      new RotateDecoder("rcrS", ADDR_E, v_mode),
      new ShiftDecoder("shlS", ADDR_E, v_mode, RTLOP_SLL),
      new ShiftDecoder("shrS", ADDR_E, v_mode, RTLOP_SRL),
      null,
      new ShiftDecoder("sarS", ADDR_E, v_mode, RTLOP_SRA)
      },
      {
      new RotateDecoder("rolb", ADDR_E, b_mode, ADDR_REG, CL),
      new RotateDecoder("rorb", ADDR_E, b_mode, ADDR_REG, CL),
      new RotateDecoder("rclb", ADDR_E, b_mode, ADDR_REG, CL),
      new RotateDecoder("rcrb", ADDR_E, b_mode, ADDR_REG, CL),
      new ShiftDecoder( "shlb", ADDR_E, b_mode, ADDR_REG, CL, RTLOP_SLL),
      new ShiftDecoder("shrb", ADDR_E, b_mode, ADDR_REG, CL, RTLOP_SRL),
      null,
      new ShiftDecoder("sarb", ADDR_E, b_mode, ADDR_REG, CL, RTLOP_SRA)
      },
      {
      new RotateDecoder("rolS", ADDR_E, v_mode, ADDR_REG, CL),
      new RotateDecoder("rorS", ADDR_E, v_mode, ADDR_REG, CL),
      new RotateDecoder("rclS", ADDR_E, v_mode, ADDR_REG, CL),
      new RotateDecoder("rcrS", ADDR_E, v_mode, ADDR_REG, CL),
      new ShiftDecoder("shlS", ADDR_E, v_mode, ADDR_REG, CL, RTLOP_SLL),
      new ShiftDecoder("shrS", ADDR_E, v_mode, ADDR_REG, CL, RTLOP_SRL),
      null,
      new ShiftDecoder("sarS", ADDR_E, v_mode, ADDR_REG, CL, RTLOP_SRA)
      },
      {
      new InstructionDecoder("testb", ADDR_E, b_mode, ADDR_I, b_mode),
      null, /*new InstructionDecoder("(bad)", ADDR_E, b_mode)*/
      new LogicalDecoder("notb", ADDR_E, b_mode, RTLOP_NOT),
      new InstructionDecoder("negb", ADDR_E, b_mode),
      new ArithmeticDecoder("mulb", ADDR_REG, AL, ADDR_E, b_mode, RTLOP_UMUL),
      new ArithmeticDecoder("imulb", ADDR_REG, AL, ADDR_E, b_mode, RTLOP_SMUL),
      new ArithmeticDecoder("divb", ADDR_REG, AL, ADDR_E, b_mode, RTLOP_UDIV),
      new ArithmeticDecoder("idivb", ADDR_REG, AL, ADDR_E, b_mode, RTLOP_SDIV)
      },
      {
      new InstructionDecoder("testS", ADDR_E, v_mode, ADDR_I, v_mode),
      null,
      new LogicalDecoder("notS", ADDR_E, v_mode, RTLOP_NOT),
      new InstructionDecoder("negS", ADDR_E, v_mode),
      new ArithmeticDecoder("mulS", ADDR_REG, EAX, ADDR_E, v_mode, RTLOP_UMUL),
      new ArithmeticDecoder("imulS", ADDR_REG, EAX, ADDR_E, v_mode, RTLOP_SMUL),
      new ArithmeticDecoder("divS", ADDR_REG, EAX, ADDR_E, v_mode, RTLOP_SDIV),
      new ArithmeticDecoder("idivS", ADDR_REG, EAX, ADDR_E, v_mode, RTLOP_SDIV)
      },
      {
      new ArithmeticDecoder("incb", ADDR_E, b_mode, RTLOP_ADD),
      new ArithmeticDecoder("decb", ADDR_E, b_mode, RTLOP_SUB),
      null,
      null,
      null,
      null,
      null,
      null
      },
      {
      new ArithmeticDecoder("incS", ADDR_E, v_mode, RTLOP_ADD),
      new ArithmeticDecoder("decS", ADDR_E, v_mode, RTLOP_SUB),
      new CallDecoder("call", ADDR_E, v_mode),
      new CallDecoder("lcall", ADDR_E, p_mode),
      new JmpDecoder("jmp", ADDR_E, v_mode),
      new JmpDecoder("ljmp", ADDR_E, p_mode),
      new InstructionDecoder("pushS", ADDR_E, v_mode),
      null
      },
      {
      new InstructionDecoder("sldt", ADDR_E, w_mode),
      new InstructionDecoder("str", ADDR_E, w_mode),
      new InstructionDecoder("lldt", ADDR_E, w_mode),
      new InstructionDecoder("ltr", ADDR_E, w_mode),
      new InstructionDecoder("verr", ADDR_E, w_mode),
      new InstructionDecoder("verw", ADDR_E, w_mode),
      null,
      null
      },
      {
      new InstructionDecoder("sgdt", ADDR_E, w_mode),
      new InstructionDecoder("sidt", ADDR_E, w_mode),
      new InstructionDecoder("lgdt", ADDR_E, w_mode),
      new InstructionDecoder("lidt", ADDR_E, w_mode),
      new InstructionDecoder("smsw", ADDR_E, w_mode),
      null,
      new InstructionDecoder("lmsw", ADDR_E, w_mode),
      new InstructionDecoder("invlpg", ADDR_E, w_mode)
      },
      {
      null,
      null,
      null,
      null,
      new InstructionDecoder("btS", ADDR_E, v_mode, ADDR_I, b_mode),
      new InstructionDecoder("btsS", ADDR_E, v_mode, ADDR_I, b_mode),
      new InstructionDecoder("btrS", ADDR_E, v_mode, ADDR_I, b_mode),
      new InstructionDecoder("btcS", ADDR_E, v_mode, ADDR_I, b_mode)
      },
      /*16*/
      {
      null,
      new SSEInstructionDecoder("cmpxch8b", ADDR_W, q_mode),
      null,
      null,
      null,
      null,
      null,
      null
      },
      /*17*/
      {
      null,
      null,
      new SSEShiftDecoder("psrlw", ADDR_P, q_mode, ADDR_I, b_mode, RTLOP_SRL),
      null,
      new SSEShiftDecoder("psraw", ADDR_P, q_mode, ADDR_I, b_mode, RTLOP_SRA),
      null,
      new SSEShiftDecoder("psllw", ADDR_P, q_mode, ADDR_I, b_mode, RTLOP_SLL),
      null
      },
      /*18*/
      {
      null,
      null,
      new SSEShiftDecoder("psrld", ADDR_P, q_mode, ADDR_I, b_mode, RTLOP_SRL),
      null,
      new SSEShiftDecoder("psrad", ADDR_P, q_mode, ADDR_I, b_mode, RTLOP_SRA),
      null,
      new SSEShiftDecoder("pslld", ADDR_P, q_mode, ADDR_I, b_mode, RTLOP_SLL),
      null
      },
      /*19*/
      {
      null,
      null,
      new SSEShiftDecoder("psrlq", ADDR_P, q_mode, ADDR_I, b_mode, RTLOP_SRL),
      null,
      null,
      null,
      new SSEShiftDecoder("psllq", ADDR_P, q_mode, ADDR_I, b_mode, RTLOP_SLL),
      null
      },
      /*20 - Grp15*/
      {
      new SSEInstructionDecoder("fxsave"),
      new SSEInstructionDecoder("fxrstor"),
      new SSEInstructionDecoder("ldmxcsr"),
      new SSEInstructionDecoder("stmxcsr"),
      null,
      null,
      null,
      new SSEInstructionDecoder("clflush")
      },
      /*21 - Grp16*/
      {
      new SSEInstructionDecoder("prefetchnta"),
      new SSEInstructionDecoder("prefetcht0"),
      new SSEInstructionDecoder("prefetcht1"),
      new SSEInstructionDecoder("prefetcht2"),
      null,
      null,
      null,
      null
      },
      /*22 - Grp12:66*/
      {
      null,
      null,
      new SSEShiftDecoder("psrlw", ADDR_P, dq_mode, ADDR_I, b_mode, RTLOP_SRL),
      null,
      new SSEShiftDecoder("psraw", ADDR_P, dq_mode, ADDR_I, b_mode, RTLOP_SRA),
      null,
      new SSEShiftDecoder("psllw", ADDR_P, dq_mode, ADDR_I, b_mode, RTLOP_SLL),
      null
      },
      /*23 - Grp13:66*/
      {
      null,
      null,
      new SSEShiftDecoder("psrld", ADDR_W, dq_mode, ADDR_I, b_mode, RTLOP_SRL),
      null,
      new SSEShiftDecoder("psrad", ADDR_W, dq_mode, ADDR_I, b_mode, RTLOP_SRA),
      null,
      new SSEShiftDecoder("pslld", ADDR_W, dq_mode, ADDR_I, b_mode, RTLOP_SLL),
      null
      },
      /*24 - - Grp14:66*/
      {
      null,
      null,
      new SSEShiftDecoder("psrlq", ADDR_W, dq_mode, ADDR_I, b_mode, RTLOP_SRL),
      new SSEShiftDecoder("psrldq", ADDR_W, dq_mode, ADDR_I, b_mode, RTLOP_SRL),
      null,
      null,
      new SSEShiftDecoder("psllq", ADDR_W, dq_mode, ADDR_I, b_mode, RTLOP_SLL),
      new SSEShiftDecoder("psllq", ADDR_W, dq_mode, ADDR_I, b_mode, RTLOP_SLL)
      }
};

   public GRPDecoder(String name, int number) {
      super(name);
      this.number = number;
   }

   public Instruction decode(byte[] bytesArray, int index, int instrStartIndex, int segmentOverride, int prefixes, X86InstructionFactory factory) {
      this.byteIndex = index;
      this.instrStartIndex = instrStartIndex;
      this.prefixes = prefixes;

      int ModRM = readByte(bytesArray, byteIndex);
      int reg = (ModRM >> 3) & 7;

      InstructionDecoder instrDecoder = grpTable[number][reg];
      Instruction instr = null;
      if(instrDecoder != null) {
         instr = instrDecoder.decode(bytesArray, byteIndex, instrStartIndex, segmentOverride, prefixes, factory);
         byteIndex = instrDecoder.getCurrentIndex();
      } else {
         instr = factory.newIllegalInstruction();
      }

      return instr;
   }
}
