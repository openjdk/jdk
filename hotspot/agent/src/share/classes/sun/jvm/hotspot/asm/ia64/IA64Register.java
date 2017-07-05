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

package sun.jvm.hotspot.asm.ia64;

import sun.jvm.hotspot.asm.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.utilities.*;

public class IA64Register extends Register {

  //
  private static final int STACKED_BASE = 32;
  private static final int STACKED_END = 127;

  // We put application registers here too rather than separate types
  private static final int APPL_BASE   = 128;

  private static final int nofRegisters = 129;  // total number of registers

  /** Constructor for an explicitly numbered register */
  public IA64Register(int number) {
    super(number);
  }

  public int getNumberOfRegisters() {
    return nofRegisters;
  }

  public boolean isStacked() {
    return (32 <= getNumber());
  }

  /** NOTE: this returns an offset in BYTES in this system! */
  public long spOffsetInSavedWindow() {
    return 0;
  }

  public String toString() {
    return IA64Registers.getRegisterName(number);
  }

  public boolean isFramePointer() {
    return number == APPL_BASE;
  }

  public boolean isStackPointer() {
    return number == 12;
  }

  public boolean isFloat() {
    return false;
  }

}
