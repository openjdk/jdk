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

package sun.jvm.hotspot.debugger.ia64;

import sun.jvm.hotspot.debugger.*;

/** Specifies the thread context on ia64 platform; only a sub-portion
    of the context is guaranteed to be present on all operating
    systems. */

public abstract class IA64ThreadContext implements ThreadContext {
  // Refer to winnt.h CONTEXT structure - Nov 2001 edition Platform SDK
  // only a relevant subset of CONTEXT structure is used here.
  // For eg. floating point registers are ignored.

  // NOTE: the indices for the various registers must be maintained as
  // listed across various operating systems. However, only a
  // subset of the registers' values are guaranteed to be present

  // global registers r0-r31
  public static final int GR0  = 0;
  public static final int GR1  = 1;
  public static final int GR2  = 2;
  public static final int GR3  = 3;
  public static final int GR4  = 4;
  public static final int GR5  = 5;
  public static final int GR6  = 6;
  public static final int GR7  = 7;
  public static final int GR8  = 8;
  public static final int GR9  = 9;
  public static final int GR10 = 10;
  public static final int GR11 = 11;
  public static final int GR12 = 12;
  public static final int SP = GR12;
  public static final int GR13 = 13;
  public static final int GR14 = 14;
  public static final int GR15 = 15;
  public static final int GR16 = 16;
  public static final int GR17 = 17;
  public static final int GR18 = 18;
  public static final int GR19 = 19;
  public static final int GR20 = 20;
  public static final int GR21 = 21;
  public static final int GR22 = 22;
  public static final int GR23 = 23;
  public static final int GR24 = 24;
  public static final int GR25 = 25;
  public static final int GR26 = 26;
  public static final int GR27 = 27;
  public static final int GR28 = 28;
  public static final int GR29 = 29;
  public static final int GR30 = 30;
  public static final int GR31 = 31;

  // Nat bits for r1-r31
  public static final int INT_NATS = 32;

  // predicates
  public static final int PREDS    = 33;

  // branch registers
  public static final int BR0      = 34;
  public static final int BR_RP    = BR0;
  public static final int BR1      = 35;
  public static final int BR2      = 36;
  public static final int BR3      = 37;
  public static final int BR4      = 38;
  public static final int BR5      = 39;
  public static final int BR6      = 40;
  public static final int BR7      = 41;

  // application registers
  public static final int AP_UNAT  = 42; // User Nat Collection register
  public static final int AP_LC    = 43; // Loop counter register
  public static final int AP_EC    = 43; // Epilog counter register
  public static final int AP_CCV   = 45; // CMPXCHG value register
  public static final int AP_DCR   = 46; // Default control register

  // register stack info
  public static final int RS_PFS   = 47; // Previous function state
  public static final int AP_PFS   = RS_PFS;
  public static final int RS_BSP   = 48; // Backing store pointer
  public static final int AR_BSP   = RS_BSP;
  public static final int RS_BSPSTORE = 49;
  public static final int AP_BSPSTORE = RS_BSPSTORE;
  public static final int RS_RSC   = 50;     // RSE configuration
  public static final int AP_RSC   = RS_RSC;
  public static final int RS_RNAT  = 51; // RSE Nat collection register
  public static final int AP_RNAT  = RS_RNAT;

  // trap status register
  public static final int ST_IPSR  = 52; // Interuption Processor Status
  public static final int ST_IIP   = 53; // Interruption IP
  public static final int ST_IFS   = 54; // Interruption Function State

  // debug registers
  public static final int DB_I0    = 55;
  public static final int DB_I1    = 56;
  public static final int DB_I2    = 57;
  public static final int DB_I3    = 58;
  public static final int DB_I4    = 59;
  public static final int DB_I5    = 60;
  public static final int DB_I6    = 61;
  public static final int DB_I7    = 62;

  public static final int DB_D0    = 63;
  public static final int DB_D1    = 64;
  public static final int DB_D2    = 65;
  public static final int DB_D3    = 66;
  public static final int DB_D4    = 67;
  public static final int DB_D5    = 68;
  public static final int DB_D6    = 69;
  public static final int DB_D7    = 70;

  public static final int NPRGREG  = 71;

  private static final String[] regNames = {
     "GR0", "GR1", "GR2", "GR3", "GR4", "GR5", "GR6", "GR7", "GR8",
     "GR9", "GR10", "GR11", "GR12", "GR13", "GR14", "GR15", "GR16",
     "GR17","GR18", "GR19", "GR20", "GR21", "GR22", "GR23", "GR24",
     "GR25","GR26", "GR27", "GR28", "GR29", "GR30", "GR31",
     "INT_NATS", "PREDS",
     "BR0", "BR1", "BR2", "BR3", "BR4", "BR5", "BR6", "BR7",
     "AP_UNAT", "AP_LC", "AP_EC", "AP_CCV", "AP_DCR",
     "RS_FPS", "RS_BSP", "RS_BSPSTORE", "RS_RSC", "RS_RNAT",
     "ST_IPSR", "ST_IIP", "ST_IFS",
     "DB_I0", "DB_I1", "DB_I2", "DB_I3", "DB_I4", "DB_I5", "DB_I6", "DB_I7",
     "DB_D0", "DB_D1", "DB_D2", "DB_D3", "DB_D4", "DB_D5", "DB_D6", "DB_D7"
  };

  private long[] data;

  public IA64ThreadContext() {
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

  /** This can't be implemented in this class since we would have to
      tie the implementation to, for example, the debugging system */
  public abstract void setRegisterAsAddress(int index, Address value);

  /** This can't be implemented in this class since we would have to
      tie the implementation to, for example, the debugging system */
  public abstract Address getRegisterAsAddress(int index);
}
