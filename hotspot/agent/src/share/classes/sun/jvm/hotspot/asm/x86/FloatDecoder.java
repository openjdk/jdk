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

public class FloatDecoder extends FPInstructionDecoder {

   public FloatDecoder() {
      super(null);
   }

   //Please refer to IA-32 Intel Architecture Software Developer's Manual Volume 2
   //APPENDIX A - Escape opcodes

   /*When ModR/M byte is within 00h to BFh*/
   private static final FPInstructionDecoder floatMapOne[][] = {
      /* d8 */
      {
      new FPArithmeticDecoder("fadds", ADDR_E, v_mode, RTLOP_ADD),
      new FPArithmeticDecoder("fmuls", ADDR_E, v_mode, RTLOP_SMUL),
      new FPInstructionDecoder("fcoms", ADDR_E, v_mode),
      new FPInstructionDecoder("fcomps", ADDR_E, v_mode),
      new FPArithmeticDecoder("fsubs", ADDR_E, v_mode, RTLOP_SUB),
      new FPArithmeticDecoder("fsubrs", ADDR_E, v_mode, RTLOP_SUB),
      new FPArithmeticDecoder("fdivs", ADDR_E, v_mode, RTLOP_SDIV),
      new FPArithmeticDecoder("fdivrs", ADDR_E, v_mode, RTLOP_SDIV)
      },
      /*  d9 */
      {
      new FPLoadDecoder("flds", ADDR_E, v_mode),
      null,
      new FPStoreDecoder("fsts", ADDR_E, v_mode),
      new FPStoreDecoder("fstps", ADDR_E, v_mode),
      new FPStoreDecoder("fldenv", ADDR_E, v_mode),
      new FPStoreDecoder("fldcw", ADDR_E, v_mode),
      new FPStoreDecoder("fNstenv", ADDR_E, v_mode),
      new FPStoreDecoder("fNstcw", ADDR_E, v_mode)
      },
      /* da */
      {
      new FPArithmeticDecoder("fiaddl", ADDR_E, v_mode, RTLOP_ADD),
      new FPArithmeticDecoder("fimull", ADDR_E, v_mode, RTLOP_SMUL),
      new FPInstructionDecoder("ficoml", ADDR_E, v_mode),
      new FPInstructionDecoder("ficompl", ADDR_E, v_mode),
      new FPArithmeticDecoder("fisubl", ADDR_E, v_mode, RTLOP_SUB),
      new FPArithmeticDecoder("fisubrl", ADDR_E, v_mode, RTLOP_SUB),
      new FPArithmeticDecoder("fidivl", ADDR_E, v_mode, RTLOP_SDIV),
      new FPArithmeticDecoder("fidivrl", ADDR_E, v_mode, RTLOP_SDIV)
      },
      /* db */
      {
      new FPLoadDecoder("fildl", ADDR_E, v_mode),
      null,
      new FPStoreDecoder("fistl", ADDR_E, v_mode),
      new FPStoreDecoder("fistpl", ADDR_E, v_mode),
      null,
      new FPLoadDecoder("fldt", ADDR_E, v_mode),
      null,
      new FPStoreDecoder("fstpt", ADDR_E, v_mode)
      },
      /* dc */
      {
      new FPArithmeticDecoder("faddl", ADDR_E, v_mode, RTLOP_ADD),
      new FPArithmeticDecoder("fmull", ADDR_E, v_mode, RTLOP_SMUL),
      new FPInstructionDecoder("fcoml", ADDR_E, v_mode),
      new FPInstructionDecoder("fcompl", ADDR_E, v_mode),
      new FPArithmeticDecoder("fsubl", ADDR_E, v_mode, RTLOP_SUB),
      new FPArithmeticDecoder("fsubrl", ADDR_E, v_mode, RTLOP_SUB),
      new FPArithmeticDecoder("fdivl", ADDR_E, v_mode, RTLOP_SDIV),
      new FPArithmeticDecoder("fdivrl", ADDR_E, v_mode, RTLOP_SDIV)
      },
      /* dd */
      {
      new FPLoadDecoder("fldl", ADDR_E, v_mode),
      null,
      new FPStoreDecoder("fstl", ADDR_E, v_mode),
      new FPStoreDecoder("fstpl", ADDR_E, v_mode),
      new FPStoreDecoder("frstor", ADDR_E, v_mode),
      null,
      new FPStoreDecoder("fNsave", ADDR_E, v_mode),
      new FPStoreDecoder("fNstsw", ADDR_E, v_mode)
      },
      /* de */
      {
      new FPArithmeticDecoder("fiadd", ADDR_E, v_mode, RTLOP_ADD),
      new FPArithmeticDecoder("fimul", ADDR_E, v_mode, RTLOP_SMUL),
      new FPInstructionDecoder("ficom", ADDR_E, v_mode),
      new FPInstructionDecoder("ficomp", ADDR_E, v_mode),
      new FPArithmeticDecoder("fisub", ADDR_E, v_mode, RTLOP_SUB),
      new FPArithmeticDecoder("fisubr", ADDR_E, v_mode, RTLOP_SUB),
      new FPArithmeticDecoder("fidiv", ADDR_E, v_mode, RTLOP_SDIV),
      new FPArithmeticDecoder("fidivr", ADDR_E, v_mode, RTLOP_SDIV)
      },
      /* df */
      {
      new FPLoadDecoder("fild", ADDR_E, v_mode),
      null,
      new FPStoreDecoder("fist", ADDR_E, v_mode),
      new FPStoreDecoder("fistp", ADDR_E, v_mode),
      new FPLoadDecoder("fbld", ADDR_E, v_mode),
      new FPLoadDecoder("fildll", ADDR_E, v_mode),
      new FPStoreDecoder("fbstp", ADDR_E, v_mode),
      new FPStoreDecoder("fistpll", ADDR_E, v_mode)
      }
   };

   /*When ModR/M byte is outside 00h to BFh*/
   private static final FPInstructionDecoder floatMapTwo[][] = {

      /* d8 */
      /*parameter for ADDR_FPREG, 0 means ST(0), 1 means ST at rm value. */
      {
      new FPArithmeticDecoder("fadd", ADDR_FPREG, 0, ADDR_FPREG, 1, RTLOP_ADD),
      new FPArithmeticDecoder("fmul", ADDR_FPREG, 0, ADDR_FPREG, 1, RTLOP_SMUL),
      new FPInstructionDecoder("fcom", ADDR_FPREG, 1),
      new FPInstructionDecoder("fcomp", ADDR_FPREG, 1),
      new FPArithmeticDecoder("fsub", ADDR_FPREG, 0, ADDR_FPREG, 1, RTLOP_SUB),
      new FPArithmeticDecoder("fsubr", ADDR_FPREG, 0, ADDR_FPREG, 1, RTLOP_SUB),
      new FPArithmeticDecoder("fdiv", ADDR_FPREG, 0, ADDR_FPREG, 1, RTLOP_SDIV),
      new FPArithmeticDecoder("fdivr", ADDR_FPREG, 0, ADDR_FPREG, 1, RTLOP_SDIV)
      },
      /* d9 */
      {
      new FPLoadDecoder("fld", ADDR_FPREG, 1),
      new FPInstructionDecoder("fxch", ADDR_FPREG, 1),
      new FloatGRPDecoder(null, 0),
      null,
      new FloatGRPDecoder(null, 1),
      new FloatGRPDecoder(null, 2),
      new FloatGRPDecoder(null, 3),
      new FloatGRPDecoder(null, 4)
      },
      /* da */
      {
      null,
      null,
      null,
      null,
      null,
      new FloatGRPDecoder(null, 5),
      null,
      null
      },
      /* db */
      {
      null,
      null,
      null,
      null,
      new FloatGRPDecoder(null, 6),
      null,
      null,
      null
      },
      /* dc */
      {
      new FPArithmeticDecoder("fadd", ADDR_FPREG, 1, ADDR_FPREG, 0, RTLOP_ADD),
      new FPArithmeticDecoder("fmul", ADDR_FPREG, 1, ADDR_FPREG, 0, RTLOP_SMUL),
      null,
      null,
      new FPArithmeticDecoder("fsub", ADDR_FPREG, 1, ADDR_FPREG, 0, RTLOP_SUB),
      new FPArithmeticDecoder("fsubr",ADDR_FPREG, 1, ADDR_FPREG, 0, RTLOP_SUB),
      new FPArithmeticDecoder("fdiv", ADDR_FPREG, 1, ADDR_FPREG, 0, RTLOP_SDIV),
      new FPArithmeticDecoder("fdivr", ADDR_FPREG, 1, ADDR_FPREG, 0, RTLOP_SDIV)
      },
      /* dd */
      {
      new FPInstructionDecoder("ffree", ADDR_FPREG, 1),
      null,
      new FPStoreDecoder("fst", ADDR_FPREG, 1),
      new FPStoreDecoder("fstp", ADDR_FPREG, 1),
      new FPInstructionDecoder("fucom", ADDR_FPREG, 1),
      new FPInstructionDecoder("fucomp", ADDR_FPREG, 1),
      null,
      null
      },
      /* de */
      {
      new FPArithmeticDecoder("faddp", ADDR_FPREG, 1, ADDR_FPREG, 0, RTLOP_ADD),
      new FPArithmeticDecoder("fmulp", ADDR_FPREG, 1, ADDR_FPREG, 0, RTLOP_SMUL),
      null,
      new FloatGRPDecoder(null, 7),
      new FPArithmeticDecoder("fsubrp", ADDR_FPREG, 1, ADDR_FPREG, 0, RTLOP_SUB),
      new FPArithmeticDecoder("fsubp", ADDR_FPREG, 1, ADDR_FPREG, 0, RTLOP_SUB),
      new FPArithmeticDecoder("fdivrp", ADDR_FPREG, 1, ADDR_FPREG, 0, RTLOP_SDIV),
      new FPArithmeticDecoder("fdivp", ADDR_FPREG, 1, ADDR_FPREG, 0, RTLOP_SDIV)
      },
      /* df */
      {
      null,
      null,
      null,
      null,
      new FloatGRPDecoder(null, 7),
      null,
      null,
      null
      }
   };

   public Instruction decode(byte[] bytesArray, int index, int instrStartIndex, int segmentOverride, int prefixes, X86InstructionFactory factory) {
      this.byteIndex = index;
      this.instrStartIndex = instrStartIndex;
      this.prefixes = prefixes;

      int ModRM = readByte(bytesArray, byteIndex);
      int reg = (ModRM >> 3) & 7;
      int regOrOpcode = (ModRM >> 3) & 7;
      int rm = ModRM & 7;

      int floatOpcode = InstructionDecoder.readByte(bytesArray, instrStartIndex);
      FPInstructionDecoder instrDecoder = null;

      if(ModRM < 0xbf) {
         instrDecoder = floatMapOne[floatOpcode - 0xd8][reg];
      }
      else {
         instrDecoder = floatMapTwo[floatOpcode - 0xd8][reg];
      }

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
