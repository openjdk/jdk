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

import sun.jvm.hotspot.utilities.*;

public class X86SegmentRegisters {

   public static final int NUM_SEGMENT_REGISTERS = 6;

   public static final X86SegmentRegister ES;
   public static final X86SegmentRegister CS;
   public static final X86SegmentRegister SS;
   public static final X86SegmentRegister DS;
   public static final X86SegmentRegister FS;
   public static final X86SegmentRegister GS;

   private static X86SegmentRegister segmentRegisters[];

   static {
      //Segment registers
      ES = new X86SegmentRegister(0, "%es");
      CS = new X86SegmentRegister(1, "%cs");
      SS = new X86SegmentRegister(2, "%ss");
      DS = new X86SegmentRegister(3, "%ds");
      FS = new X86SegmentRegister(4, "%fs");
      GS = new X86SegmentRegister(5, "%gs");

      segmentRegisters = (new X86SegmentRegister[] {
            ES, CS, SS, DS, FS, GS
      });
   }

   public static int getNumberOfRegisters() {
      return NUM_SEGMENT_REGISTERS;
   }

   public static X86SegmentRegister getSegmentRegister(int regNum) {
      if (Assert.ASSERTS_ENABLED) {
         Assert.that(regNum > -1 && regNum < NUM_SEGMENT_REGISTERS, "invalid segment register number!");
      }
     return segmentRegisters[regNum];
   }
}
