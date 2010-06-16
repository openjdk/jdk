/*
 * Copyright (c) 2001, Oracle and/or its affiliates. All rights reserved.
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

import sun.jvm.hotspot.utilities.*;

public class X86Registers {
  public static final int NUM_REGISTERS = 8;

   public static final X86Register EAX;
   public static final X86Register ECX;
   public static final X86Register EDX;
   public static final X86Register EBX;
   public static final X86Register ESP;
   public static final X86Register EBP;
   public static final X86Register ESI;
   public static final X86Register EDI;

   public static final X86Register AX;
   public static final X86Register CX;
   public static final X86Register DX;
   public static final X86Register BX;
   public static final X86Register SP;
   public static final X86Register BP;
   public static final X86Register SI;
   public static final X86Register DI;

   public static final X86Register AL;
   public static final X86Register CL;
   public static final X86Register DL;
   public static final X86Register BL;
   public static final X86Register AH;
   public static final X86Register CH;
   public static final X86Register DH;
   public static final X86Register BH;

   private static X86Register registers8[];
   private static X86Register registers16[];
   private static X86Register registers32[];

   static {
      EAX = new X86RegisterPart(0, "%eax", 0, 32);
      ECX = new X86RegisterPart(1, "%ecx", 0, 32);
      EDX = new X86RegisterPart(2, "%edx", 0, 32);
      EBX = new X86RegisterPart(3, "%ebx", 0, 32);
      ESP = new X86RegisterPart(4, "%esp", 0, 32);
      EBP = new X86RegisterPart(5, "%ebp", 0, 32);
      ESI = new X86RegisterPart(6, "%esi", 0, 32);
      EDI = new X86RegisterPart(7, "%edi", 0, 32);

      AX = new X86RegisterPart(0, "%ax", 0, 16);
      CX = new X86RegisterPart(1, "%cx", 0, 16);
      DX = new X86RegisterPart(2, "%dx", 0, 16);
      BX = new X86RegisterPart(3, "%bx", 0, 16);
      SP = new X86RegisterPart(4, "%sp", 0, 16);
      BP = new X86RegisterPart(5, "%bp", 0, 16);
      SI = new X86RegisterPart(6, "%si", 0, 16);
      DI = new X86RegisterPart(7, "%di", 0, 16);

      AL = new X86RegisterPart(0, "%al", 0, 8);
      CL = new X86RegisterPart(1, "%cl", 0, 8);
      DL = new X86RegisterPart(2, "%dl", 0, 8);
      BL = new X86RegisterPart(3, "%bl", 0, 8);
      AH = new X86RegisterPart(0, "%ah", 8, 8);
      CH = new X86RegisterPart(1, "%ch", 8, 8);
      DH = new X86RegisterPart(2, "%dh", 8, 8);
      BH = new X86RegisterPart(3, "%bh", 8, 8);

      registers32 = (new X86Register[] {
            EAX, ECX, EDX, EBX, ESP, EBP, ESI, EDI
      });
      registers16 = (new X86Register[] {
            AX, CX, DX, BX, SP, BP, SI, DI
      });
      registers8 = (new X86Register[] {
            AL, CL, DL, BL, AH, CH, DH, BH
      });
   }

   public static int getNumberOfRegisters() {
      return NUM_REGISTERS;
   }

   public static X86Register getRegister8(int regNum) {
      if (Assert.ASSERTS_ENABLED) {
         Assert.that(regNum > -1 && regNum < NUM_REGISTERS, "invalid integer register number!");
      }
      return registers8[regNum];
   }

   public static X86Register getRegister16(int regNum) {
      if (Assert.ASSERTS_ENABLED) {
         Assert.that(regNum > -1 && regNum < NUM_REGISTERS, "invalid integer register number!");
      }
      return registers16[regNum];
   }

   public static X86Register getRegister32(int regNum) {
      if (Assert.ASSERTS_ENABLED) {
         Assert.that(regNum > -1 && regNum < NUM_REGISTERS, "invalid integer register number!");
      }
      return registers32[regNum];
   }

   //Return the 32bit register name
   public static String getRegisterName(int regNum) {
      if (Assert.ASSERTS_ENABLED) {
         Assert.that(regNum > -1 && regNum < NUM_REGISTERS, "invalid integer register number!");
      }
      return registers32[regNum].toString();
   }
}
