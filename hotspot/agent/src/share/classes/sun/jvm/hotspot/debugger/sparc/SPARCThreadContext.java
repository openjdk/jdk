/*
 * Copyright (c) 2000, 2012, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.debugger.sparc;

import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.debugger.cdbg.*;

/** Currently provides just the minimal information necessary to get
    stack traces working. FIXME: currently hardwired for v9 -- will
    have to factor out v8/v9 specific code. FIXME: may want to try to
    share code between this class and asm/sparc. */

public abstract class SPARCThreadContext implements ThreadContext {
  // Taken from /usr/include/sys/procfs_isa.h
  public static final int R_G0 = 0;
  public static final int R_G1 = 1;
  public static final int R_G2 = 2;
  public static final int R_G3 = 3;
  public static final int R_G4 = 4;
  public static final int R_G5 = 5;
  public static final int R_G6 = 6;
  public static final int R_G7 = 7;
  public static final int R_O0 = 8;
  public static final int R_O1 = 9;
  public static final int R_O2 = 10;
  public static final int R_O3 = 11;
  public static final int R_O4 = 12;
  public static final int R_O5 = 13;
  public static final int R_O6 = 14;
  public static final int R_O7 = 15;
  public static final int R_L0 = 16;
  public static final int R_L1 = 17;
  public static final int R_L2 = 18;
  public static final int R_L3 = 19;
  public static final int R_L4 = 20;
  public static final int R_L5 = 21;
  public static final int R_L6 = 22;
  public static final int R_L7 = 23;
  public static final int R_I0 = 24;
  public static final int R_I1 = 25;
  public static final int R_I2 = 26;
  public static final int R_I3 = 27;
  public static final int R_I4 = 28;
  public static final int R_I5 = 29;
  public static final int R_I6 = 30;
  public static final int R_I7 = 31;

  // sparc-v9
  public static final int R_CCR = 32;
  // sparc-v8
  public static final int R_PSR = 32;

  public static final int R_PC = 33;
  public static final int R_nPC = 34;

  public static final int R_SP = R_O6;
  public static final int R_FP = R_I6;

  public static final int R_Y  = 35;

  // sparc-v9
  public static final int R_ASI = 36;
  public static final int R_FPRS = 37;

  // sparc-v8
  public static final int R_WIM = 36;
  public static final int R_TBR = 37;

  public static final int NPRGREG = 38;

  private static final String[] regNames = {
    "G0",    "G1",    "G2",    "G3",
    "G4",    "G5",    "G6",    "G7",
    "O0",    "O1",    "O2",    "O3",
    "O4",    "O5",    "O6/SP", "O7",
    "L0",    "L1",    "L2",    "L3",
    "L4",    "L5",    "L6",    "L7",
    "I0",    "I1",    "I2",    "I3",
    "I4",    "I5",    "I6/FP", "I7",
    "CCR/PSR", "PC",  "nPC",   "Y",
    "ASI/WIM", "FPRS/TBR"
  };

  private long[] data;

  public SPARCThreadContext() {
    data = new long[NPRGREG];
  }

  public int getNumRegisters() {
    return NPRGREG;
  }

  public String getRegisterName(int index) {
    return regNames[index];
  }

  public void setRegister(int index, long value) {
    data[index] = value;
  }

  public long getRegister(int index) {
    return data[index];
  }

  public CFrame getTopFrame(Debugger dbg) {
    return null;
  }

  /** This can't be implemented in this class since we would have to
      tie the implementation to, for example, the debugging system */
  public abstract void setRegisterAsAddress(int index, Address value);

  /** This can't be implemented in this class since we would have to
      tie the implementation to, for example, the debugging system */
  public abstract Address getRegisterAsAddress(int index);
}
