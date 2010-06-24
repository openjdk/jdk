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

// basic instruction decoder class
public class InstructionDecoder implements /* imports */ X86Opcodes , RTLDataTypes, RTLOperations {

   protected String name;
   protected int addrMode1;
   protected int operandType1;
   protected int addrMode2;
   protected int operandType2;
   protected int addrMode3;
   protected int operandType3;

   private int mod;
   private int regOrOpcode;
   private int rm;
   protected int prefixes;

   protected int byteIndex;
   protected int instrStartIndex;

   public InstructionDecoder(String name) {
      this.name = name;
      this.operandType1 = INVALID_OPERANDTYPE;
      this.operandType2 = INVALID_OPERANDTYPE;
      this.operandType3 = INVALID_OPERANDTYPE;
      this.addrMode1 = INVALID_ADDRMODE;
      this.addrMode2 = INVALID_ADDRMODE;
      this.addrMode3 = INVALID_ADDRMODE;
   }
   public InstructionDecoder(String name, int addrMode1, int operandType1) {
      this(name);
      this.addrMode1 = addrMode1;
      this.operandType1 = operandType1;
   }
   public InstructionDecoder(String name, int addrMode1, int operandType1, int addrMode2, int operandType2) {
      this(name, addrMode1, operandType1);
      this.addrMode2 = addrMode2;
      this.operandType2 = operandType2;
   }
   public InstructionDecoder(String name, int addrMode1, int operandType1, int addrMode2, int operandType2,
                                       int addrMode3, int operandType3) {
      this(name, addrMode1, operandType1, addrMode2, operandType2);
      this.addrMode3 = addrMode3;
      this.operandType3 = operandType3;
   }
   // "operand1"
   protected Operand getOperand1(byte[] bytesArray, boolean operandSize, boolean addrSize) {
      if( (addrMode1 != INVALID_ADDRMODE) && (operandType1 != INVALID_OPERANDTYPE) )
         return getOperand(bytesArray, addrMode1, operandType1, operandSize, addrSize);
      else
         return null;
   }

   // "operand2"
   protected Operand getOperand2(byte[] bytesArray, boolean operandSize, boolean addrSize) {
      if( (addrMode2 != INVALID_ADDRMODE) && (operandType2 != INVALID_OPERANDTYPE) )
         return getOperand(bytesArray, addrMode2, operandType2, operandSize, addrSize);
      else
         return null;
   }

   // "operand3"
   protected Operand getOperand3(byte[] bytesArray, boolean operandSize, boolean addrSize) {
      if( (addrMode3 != INVALID_ADDRMODE) && (operandType3 != INVALID_OPERANDTYPE) )
         return getOperand(bytesArray, addrMode3, operandType3, operandSize, addrSize);
      else
         return null;
   }

   static int readInt32(byte[] bytesArray, int index) {
      int ret = 0;
      ret = readByte(bytesArray, index);
      ret |= readByte(bytesArray, index+1) << 8;
      ret |= readByte(bytesArray, index+2) << 16;
      ret |= readByte(bytesArray, index+3) << 24;
      return ret;
   }
   static int readInt16(byte[] bytesArray, int index) {
      int ret = 0;
      ret = readByte(bytesArray, index);
      ret |= readByte(bytesArray, index+1) << 8;
      return ret;
   }
   static int readByte(byte[] bytesArray, int index) {
      int ret = 0;
      if (index < bytesArray.length) {
         ret = (int)bytesArray[index];
         ret = ret & 0xff;
      }
      return ret;
   }
   private boolean isModRMPresent(int addrMode) {
      if( (addrMode == ADDR_E) || (addrMode == ADDR_G) || (addrMode == ADDR_FPREG) || (addrMode == ADDR_Q) || (addrMode == ADDR_W) )
         return true;
      else
         return false;
   }
   public int getCurrentIndex() {
      return byteIndex;
   }

   public Instruction decode(byte[] bytesArray, int index, int instrStartIndex, int segmentOverride, int prefixes, X86InstructionFactory factory) {
      this.byteIndex = index;
      this.instrStartIndex = instrStartIndex;
      this.prefixes = prefixes;
      boolean operandSize; //operand-size prefix
      boolean addrSize;    //address-size prefix
      if ( ( (prefixes & PREFIX_DATA) ^ segmentOverride ) == 1)
         operandSize = true;
      else
         operandSize = false;
      if ( ((prefixes & PREFIX_ADR) ^ segmentOverride) == 1)
         addrSize = true;
      else
         addrSize = false;
      this.name = getCorrectOpcodeName(name, prefixes, operandSize, addrSize);

      //Fetch the mod/reg/rm byte only if it is present.
      if( isModRMPresent(addrMode1) || isModRMPresent(addrMode2) || isModRMPresent(addrMode3) ) {

         int ModRM = readByte(bytesArray, byteIndex);
         byteIndex++;
         mod = (ModRM >> 6) & 3;
         regOrOpcode = (ModRM >> 3) & 7;
         rm = ModRM & 7;
      }
      return decodeInstruction(bytesArray, operandSize, addrSize, factory);
   }

   protected Instruction decodeInstruction(byte[] bytesArray, boolean operandSize, boolean addrSize, X86InstructionFactory factory) {
      Operand op1 = getOperand1(bytesArray, operandSize, addrSize);
      Operand op2 = getOperand2(bytesArray, operandSize, addrSize);
      Operand op3 = getOperand3(bytesArray, operandSize, addrSize);
      int size = byteIndex - instrStartIndex;
      return factory.newGeneralInstruction(name, op1, op2, op3, size, prefixes);
   }

   // capital letters in template are macros
   private String getCorrectOpcodeName(String oldName, int prefixes, boolean operandSize, boolean addrSize) {
      StringBuffer newName = new StringBuffer(oldName);
      int index = 0;
      for(index=0; index<oldName.length(); index++) {
         switch (oldName.charAt(index)) {
            case 'C':           /* For jcxz/jecxz */
               if (addrSize)
                  newName.setCharAt(index, 'e');
               index++;
               break;
            case 'N':
               if ((prefixes & PREFIX_FWAIT) == 0)
                  newName.setCharAt(index, 'n');
               index++;
               break;
            case 'S':
            /* operand size flag */
               if (operandSize == true)
                  newName.setCharAt(index, 'l');
               else
                  newName.setCharAt(index, 'w');
               index++;
               break;
            default:
               break;
         }
      }
      return newName.toString();
   }

   //IA-32 Intel Architecture Software Developer's Manual Volume 2
   //Refer to Chapter 2 - Instruction Format

   //Get the Operand object from the address type and the operand type
   private Operand getOperand(byte[] bytesArray, int addrMode, int operandType, boolean operandSize, boolean addrSize) {
      Operand op = null;
      switch(addrMode) {
         case ADDR_E:
         case ADDR_W:   //SSE: ModR/M byte specifies either 128 bit XMM register or memory
         case ADDR_Q:   //SSE: ModR/M byte specifies either 128 bit MMX register or memory
            X86SegmentRegister segReg = getSegmentRegisterFromPrefix(prefixes);

            if (mod == 3) {    //Register operand, no SIB follows
               if (addrMode == ADDR_E) {
                  switch (operandType) {
                     case b_mode:
                        op = X86Registers.getRegister8(rm);
                        break;
                     case w_mode:
                        op = X86Registers.getRegister16(rm);
                        break;
                     case v_mode:
                        if (operandSize == true) //Operand size prefix is present
                           op = X86Registers.getRegister32(rm);
                        else
                           op = X86Registers.getRegister16(rm);
                        break;
                     case p_mode:
                        X86Register reg;
                        if (operandSize == true) //Operand size prefix is present
                           reg = X86Registers.getRegister32(rm);
                        else
                           reg = X86Registers.getRegister16(rm);

                        op = new X86RegisterIndirectAddress(segReg, reg, null, 0);
                        break;
                     default:
                        break;
                  }
               } else if (addrMode == ADDR_W) {
                  op = X86XMMRegisters.getRegister(rm);
               } else if (addrMode == ADDR_Q) {
                  op = X86MMXRegisters.getRegister(rm);
               }

            } else {   //mod != 3
               //SIB follows for (rm==4), SIB gives scale, index and base in this case
               //disp32 is present for (mod==0 && rm==5) || (mod==2)
               //disp8 is present for (mod==1)
               //for (rm!=4) base is register at rm.
               int scale = 0;
               int index = 0;
               int base = 0;
               long disp = 0;
               if(rm == 4) {
                  int sib = readByte(bytesArray, byteIndex);
                  byteIndex++;
                  scale = (sib >> 6) & 3;
                  index = (sib >> 3) & 7;
                  base = sib & 7;
               }

               switch (mod) {
                  case 0:
                     switch(rm) {
                        case 4:
                           if(base == 5) {
                              disp = readInt32(bytesArray, byteIndex);
                              byteIndex += 4;
                              if (index != 4) {
                                 op = new X86RegisterIndirectAddress(segReg, null, X86Registers.getRegister32(index), disp, scale);
                              } else {
                                 op = new X86RegisterIndirectAddress(segReg, null, null, disp, scale);
                              }
                           }
                           else {
                              if (index != 4) {
                                 op = new X86RegisterIndirectAddress(segReg, X86Registers.getRegister32(base), X86Registers.getRegister32(index), 0, scale);
                              } else {
                                 op = new X86RegisterIndirectAddress(segReg, X86Registers.getRegister32(base), null, 0, scale);
                              }
                           }
                           break;
                        case 5:
                           disp = readInt32(bytesArray, byteIndex);
                           byteIndex += 4;
                           //Create an Address object only with displacement
                           op = new X86RegisterIndirectAddress(segReg, null, null, disp);
                           break;
                        default:
                           base = rm;
                           //Create an Address object only with base
                           op = new X86RegisterIndirectAddress(segReg, X86Registers.getRegister32(base), null, 0);
                           break;
                        }
                        break;
                  case 1:
                     disp = (byte)readByte(bytesArray, byteIndex);
                     byteIndex++;
                     if (rm !=4) {
                        base = rm;
                        //Address with base and disp only
                        op = new X86RegisterIndirectAddress(segReg, X86Registers.getRegister32(base), null, disp);
                     } else {
                        if (index != 4) {
                           op = new X86RegisterIndirectAddress(segReg, X86Registers.getRegister32(base), X86Registers.getRegister32(index), disp, scale);
                        } else {
                           op = new X86RegisterIndirectAddress(segReg, X86Registers.getRegister32(base), null, disp, scale);
                        }
                     }
                     break;
                  case 2:
                     disp = readInt32(bytesArray, byteIndex);
                     byteIndex += 4;
                     if (rm !=4) {
                        base = rm;
                        //Address with base and disp
                        op = new X86RegisterIndirectAddress(segReg, X86Registers.getRegister32(base), null, disp);
                     } else if (index != 4) {
                        op = new X86RegisterIndirectAddress(segReg, X86Registers.getRegister32(base), X86Registers.getRegister32(index), disp, scale);
                     } else {
                        op = new X86RegisterIndirectAddress(segReg, X86Registers.getRegister32(base), null, disp, scale);
                     }
                     break;
               }
            }
            break;

         case ADDR_I:
            switch (operandType) {
               case b_mode:
                  op = new Immediate(new Integer(readByte(bytesArray, byteIndex)));
                  byteIndex++;
                  break;
               case w_mode:
                  op = new Immediate(new Integer(readInt16(bytesArray, byteIndex)));
                  byteIndex += 2;
                  break;
               case v_mode:
                  if (operandSize == true) { //Operand size prefix is present
                     op = new Immediate(new Integer(readInt32(bytesArray, byteIndex)));
                     byteIndex += 4;
                  } else {
                     op = new Immediate(new Integer(readInt16(bytesArray, byteIndex)));
                     byteIndex += 2;
                  }
                  break;
               default:
                  break;
            }
            break;
         case ADDR_REG: //registers
            switch(operandType) {
               case EAX:
               case ECX:
               case EDX:
               case EBX:
               case ESP:
               case EBP:
               case ESI:
               case EDI:
                  if(operandSize == true) {
                     op = X86Registers.getRegister32(operandType - EAX);
                  }
                  else {
                     op = X86Registers.getRegister16(operandType - EAX);
                  }
                  break;
               case AX:
               case CX:
               case DX:
               case BX:
               case SP:
               case BP:
               case SI:
               case DI:
                  op = X86Registers.getRegister16(operandType - AX);
                  break;
               case AL:
               case CL:
               case DL:
               case BL:
               case AH:
               case CH:
               case DH:
               case BH:
                  op = X86Registers.getRegister8(operandType - AL);
                  break;
               case ES:  //ES, CS, SS, DS, FS, GS
               case CS:
               case SS:
               case DS:
               case FS:
               case GS:
                  op = X86SegmentRegisters.getSegmentRegister(operandType - ES);
                  break;
           }
           break;
         case ADDR_DIR: //segment and offset
            long segment = 0;
            long offset = 0;
            switch (operandType) {
               case p_mode:
                  if (addrSize == true) {
                     offset = readInt32(bytesArray, byteIndex);
                     byteIndex += 4;
                     segment = readInt16(bytesArray, byteIndex);
                     byteIndex += 2;
                  } else {
                     offset = readInt16(bytesArray, byteIndex);
                     byteIndex += 2;
                     segment = readInt16(bytesArray, byteIndex);
                     byteIndex += 2;
                  }
                  op = new X86DirectAddress(segment, offset); //with offset
                  break;
               case v_mode:
                  if (addrSize == true) {
                     offset = readInt32(bytesArray, byteIndex);
                     byteIndex += 4;
                  } else {
                     offset = readInt16(bytesArray, byteIndex);
                     byteIndex += 2;
                  }
                  op = new X86DirectAddress(offset); //with offset
                  break;
               default:
                  break;
            }
            break;
         case ADDR_G:
            switch (operandType) {
               case b_mode:
                  op = X86Registers.getRegister8(regOrOpcode);
                  break;
               case w_mode:
                  op = X86Registers.getRegister16(regOrOpcode);
                  break;
               case d_mode:
                  op = X86Registers.getRegister32(regOrOpcode);
                  break;
              case v_mode:
                 if (operandSize == true)
                    op = X86Registers.getRegister32(regOrOpcode);
                 else
                    op = X86Registers.getRegister16(regOrOpcode);
                    break;
              default:
                 break;
            }
            break;
         case ADDR_SEG:
            op = X86SegmentRegisters.getSegmentRegister(regOrOpcode);
            break;
         case ADDR_OFF:
            int off = 0;
            if (addrSize == true) {
               off = readInt32(bytesArray, byteIndex);
               byteIndex += 4;
            }
            else {
               off = readInt16(bytesArray, byteIndex);
               byteIndex += 2;
            }
            op = new X86DirectAddress((long)off);
            break;
         case ADDR_J:
            long disp = 0;
            //The effective address is Instruction pointer + relative offset
            switch(operandType) {
               case b_mode:
                  disp = (byte)readByte(bytesArray, byteIndex);
                  byteIndex++;
                  break;
               case v_mode:
                  if (operandSize == true) {
                     disp = readInt32(bytesArray, byteIndex);
                     byteIndex += 4;
                  }
                  else {
                     disp = readInt16(bytesArray, byteIndex);
                     byteIndex += 2;
                  }
                  //disp = disp + (byteIndex-instrStartIndex);
                  break;
            }
            op = new X86PCRelativeAddress(disp);
            break;
         case ADDR_ESDI:
            op = new X86SegmentRegisterAddress(X86SegmentRegisters.ES, X86Registers.DI);
            break;
         case ADDR_DSSI:
            op = new X86SegmentRegisterAddress(X86SegmentRegisters.DS, X86Registers.SI);
            break;
         case ADDR_R:
            switch (operandType) {
               case b_mode:
                  op = X86Registers.getRegister8(mod);
                  break;
              case w_mode:
                 op = X86Registers.getRegister16(mod);
                 break;
              case d_mode:
                 op = X86Registers.getRegister32(mod);
                 break;
             case v_mode:
                if (operandSize == true)
                   op = X86Registers.getRegister32(mod);
                else
                   op = X86Registers.getRegister16(mod);
                   break;
             default:
                break;
            }
            break;
         case ADDR_FPREG:
            switch (operandType) {
               case 0:
                  op = X86FloatRegisters.getRegister(0);
                  break;
               case 1:
                  op = X86FloatRegisters.getRegister(rm);
                  break;
            }
            break;

         //SSE: reg field of ModR/M byte selects a 128-bit XMM register
         case ADDR_V:
            op = X86XMMRegisters.getRegister(regOrOpcode);
            break;

         //SSE: reg field of ModR/M byte selects a 64-bit MMX register
         case ADDR_P:
            op = X86MMXRegisters.getRegister(regOrOpcode);
            break;
      }
      return op;
   }

   private X86SegmentRegister getSegmentRegisterFromPrefix(int prefixes) {
      X86SegmentRegister segRegister = null;

      if ( (prefixes & PREFIX_CS) != 0)
         segRegister = X86SegmentRegisters.CS;
      if ( (prefixes & PREFIX_DS) != 0)
         segRegister =  X86SegmentRegisters.DS;
      if ( (prefixes & PREFIX_ES) != 0)
         segRegister =  X86SegmentRegisters.ES;
      if ( (prefixes & PREFIX_FS) != 0)
         segRegister =  X86SegmentRegisters.FS;
      if ( (prefixes & PREFIX_SS) != 0)
         segRegister =  X86SegmentRegisters.SS;
      if ( (prefixes & PREFIX_GS) != 0)
         segRegister =  X86SegmentRegisters.GS;

      return segRegister;
   }

}
