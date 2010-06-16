/*
 * Copyright (c) 2000, 2003, Oracle and/or its affiliates. All rights reserved.
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

import sun.jvm.hotspot.utilities.*;

public class SPARCRegisters {

  public static final SPARCRegister G0;
  public static final SPARCRegister G1;
  public static final SPARCRegister G2;
  public static final SPARCRegister G3;
  public static final SPARCRegister G4;
  public static final SPARCRegister G5;
  public static final SPARCRegister G6;
  public static final SPARCRegister G7;
  public static final SPARCRegister O0;
  public static final SPARCRegister O1;
  public static final SPARCRegister O2;
  public static final SPARCRegister O3;
  public static final SPARCRegister O4;
  public static final SPARCRegister O5;
  public static final SPARCRegister O6;
  public static final SPARCRegister O7;
  public static final SPARCRegister L0;
  public static final SPARCRegister L1;
  public static final SPARCRegister L2;
  public static final SPARCRegister L3;
  public static final SPARCRegister L4;
  public static final SPARCRegister L5;
  public static final SPARCRegister L6;
  public static final SPARCRegister L7;
  public static final SPARCRegister I0;
  public static final SPARCRegister I1;
  public static final SPARCRegister I2;
  public static final SPARCRegister I3;
  public static final SPARCRegister I4;
  public static final SPARCRegister I5;
  public static final SPARCRegister I6;
  public static final SPARCRegister I7;

  private static String registerNames[];
  public static final int NUM_REGISTERS = 32;
  private static SPARCRegister registers[];

  static {
     G0 = new SPARCRegister(0);
     G1 = new SPARCRegister(1);
     G2 = new SPARCRegister(2);
     G3 = new SPARCRegister(3);
     G4 = new SPARCRegister(4);
     G5 = new SPARCRegister(5);
     G6 = new SPARCRegister(6);
     G7 = new SPARCRegister(7);
     O0 = new SPARCRegister(8);
     O1 = new SPARCRegister(9);
     O2 = new SPARCRegister(10);
     O3 = new SPARCRegister(11);
     O4 = new SPARCRegister(12);
     O5 = new SPARCRegister(13);
     O6 = new SPARCRegister(14);
     O7 = new SPARCRegister(15);
     L0 = new SPARCRegister(16);
     L1 = new SPARCRegister(17);
     L2 = new SPARCRegister(18);
     L3 = new SPARCRegister(19);
     L4 = new SPARCRegister(20);
     L5 = new SPARCRegister(21);
     L6 = new SPARCRegister(22);
     L7 = new SPARCRegister(23);
     I0 = new SPARCRegister(24);
     I1 = new SPARCRegister(25);
     I2 = new SPARCRegister(26);
     I3 = new SPARCRegister(27);
     I4 = new SPARCRegister(28);
     I5 = new SPARCRegister(29);
     I6 = new SPARCRegister(30);
     I7 = new SPARCRegister(31);
     registerNames = new String[NUM_REGISTERS];
     registerNames[G0.getNumber()] = "%g0";
     registerNames[G1.getNumber()] = "%g1";
     registerNames[G2.getNumber()] = "%g2";
     registerNames[G3.getNumber()] = "%g3";
     registerNames[G4.getNumber()] = "%g4";
     registerNames[G5.getNumber()] = "%g5";
     registerNames[G6.getNumber()] = "%g6";
     registerNames[G7.getNumber()] = "%g7";
     registerNames[O0.getNumber()] = "%o0";
     registerNames[O1.getNumber()] = "%o1";
     registerNames[O2.getNumber()] = "%o2";
     registerNames[O3.getNumber()] = "%o3";
     registerNames[O4.getNumber()] = "%o4";
     registerNames[O5.getNumber()] = "%o5";
     registerNames[O6.getNumber()] = "%sp";
     registerNames[O7.getNumber()] = "%o7";
     registerNames[I0.getNumber()] = "%i0";
     registerNames[I1.getNumber()] = "%i1";
     registerNames[I2.getNumber()] = "%i2";
     registerNames[I3.getNumber()] = "%i3";
     registerNames[I4.getNumber()] = "%i4";
     registerNames[I5.getNumber()] = "%i5";
     registerNames[I6.getNumber()] = "%fp";
     registerNames[I7.getNumber()] = "%i7";
     registerNames[L0.getNumber()] = "%l0";
     registerNames[L1.getNumber()] = "%l1";
     registerNames[L2.getNumber()] = "%l2";
     registerNames[L3.getNumber()] = "%l3";
     registerNames[L4.getNumber()] = "%l4";
     registerNames[L5.getNumber()] = "%l5";
     registerNames[L6.getNumber()] = "%l6";
     registerNames[L7.getNumber()] = "%l7";
     registers = (new SPARCRegister[] {
            G0, G1, G2, G3, G4, G5, G6, G7, O0, O1,
            O2, O3, O4, O5, O6, O7, L0, L1, L2, L3,
            L4, L5, L6, L7, I0, I1, I2, I3, I4, I5,
            I6, I7
        });
  }

  public static final SPARCRegister FP = I6;
  public static final SPARCRegister SP = O6;

  // Interpreter frames

  public static final SPARCRegister Lesp        = L0; // expression stack pointer
  public static final SPARCRegister Lbcp        = L1; // pointer to next bytecode
  public static final SPARCRegister Lmethod     = L2;
  public static final SPARCRegister Llocals     = L3;
  public static final SPARCRegister Lmonitors   = L4;
  public static final SPARCRegister Lbyte_code  = L5;
  public static final SPARCRegister Lscratch    = L5;
  public static final SPARCRegister Lscratch2   = L6;
  public static final SPARCRegister LcpoolCache = L6; // constant pool cache

  public static final SPARCRegister OparamAddr       = O0; // Callers Parameter area address
  public static final SPARCRegister IsavedSP         = I5; // Saved SP before bumping for locals
  public static final SPARCRegister IsizeCalleeParms = I4; // Saved size of Callee parms used to pop arguments
  public static final SPARCRegister IdispatchAddress = I3; // Register which saves the dispatch address for each bytecode
  public static final SPARCRegister IdispatchTables  = I2; // Base address of the bytecode dispatch tables


  /** Prefer to use this instead of the constant above */
  public static int getNumRegisters() {
    return NUM_REGISTERS;
  }


  public static String getRegisterName(int regNum) {
    if (regNum < 0 || regNum >= NUM_REGISTERS) {
      return "[Illegal register " + regNum + "]";
    }

    if (Assert.ASSERTS_ENABLED) {
      Assert.that(regNum > -1 && regNum < NUM_REGISTERS, "invalid integer register number!");
    }

    return registerNames[regNum];
  }

  public static SPARCRegister getRegister(int regNum) {
    if (Assert.ASSERTS_ENABLED) {
      Assert.that(regNum > -1 && regNum < NUM_REGISTERS, "invalid integer register number!");
    }

    return registers[regNum];
  }
}
