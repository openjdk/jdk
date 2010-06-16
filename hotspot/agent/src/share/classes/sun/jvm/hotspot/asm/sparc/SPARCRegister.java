/*
 * Copyright (c) 2000, 2002, Oracle and/or its affiliates. All rights reserved.
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
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.utilities.*;

public class SPARCRegister extends Register {
  private static final int nofRegisters = 32;  // total number of registers

  private static final int GLOBAL_BASE = 0;
  private static final int OUT_BASE    = 8;
  private static final int LOCAL_BASE  = 16;
  private static final int IN_BASE     = 24;

  private static final int LOCAL_SP_WORD_OFFSET = 0;
  private static final int IN_SP_WORD_OFFSET    = 8;

  /** Constructor for an explicitly numbered register */
  public SPARCRegister(int number) {
    super(number);
  }

  /** Constructor for an I, G, O, or L register */
  public SPARCRegister(SPARCRegisterType type, int number) {
    if (type == SPARCRegisterType.GLOBAL) {
      this.number = number + GLOBAL_BASE;
    } else if (type == SPARCRegisterType.OUT) {
      this.number = number + OUT_BASE;
    } else if (type == SPARCRegisterType.LOCAL) {
      this.number = number + LOCAL_BASE;
    } else if (type == SPARCRegisterType.IN) {
      this.number = number + IN_BASE;
    } else {
      throw new IllegalArgumentException("Invalid SPARC register type");
    }
  }

  public int getNumberOfRegisters() {
    return nofRegisters;
  }

  public boolean isIn() {
    return (IN_BASE <= getNumber());
  }

  public boolean isLocal() {
    return (LOCAL_BASE <= getNumber() && getNumber() < IN_BASE);
  }

  public boolean isOut() {
    return (OUT_BASE <= getNumber() && getNumber() < LOCAL_BASE);
  }

  public boolean isGlobal() {
    return (GLOBAL_BASE <= getNumber() && getNumber() < OUT_BASE);
  }

  public SPARCRegister afterSave() {
    if (Assert.ASSERTS_ENABLED) {
      Assert.that(isOut() || isGlobal(), "register not visible after save");
    }
    return isOut() ? new SPARCRegister(getNumber() + (IN_BASE - OUT_BASE)) : this;
  }

  public SPARCRegister afterRestore() {
    if (Assert.ASSERTS_ENABLED) {
      Assert.that(isIn() || isGlobal(), "register not visible after save");
    }
    return isIn() ? new SPARCRegister(getNumber() + (OUT_BASE - IN_BASE)) : this;
  }

  /** NOTE: this returns an offset in BYTES in this system! */
  public long spOffsetInSavedWindow() {
    if (isIn()) {
      return VM.getVM().getAddressSize() * (getNumber() - IN_BASE + IN_SP_WORD_OFFSET);
    } else if (isLocal()) {
      return VM.getVM().getAddressSize() * (getNumber() - LOCAL_BASE + LOCAL_SP_WORD_OFFSET);
    }
    if (Assert.ASSERTS_ENABLED) {
      Assert.that(isIn() || isLocal(), "only ins and locals are saved in my frame");
    }
    return 0;
  }

  public String toString() {
    return SPARCRegisters.getRegisterName(number);
  }

  public boolean isFramePointer() {
    return number == 30; // is I6?
  }

  public boolean isStackPointer() {
    return number == 14; // is O6?
  }

  public boolean isFloat() {
    return false;
  }

  public boolean isV9Only() {
    return false;
  }
}
