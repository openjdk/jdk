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

public abstract class X86Instruction implements Instruction, X86Opcodes {
   final private String name;
   final private int size;
   final private int prefixes;

   public X86Instruction(String name, int size, int prefixes) {
      this.name = name;
      this.size = size;
      this.prefixes = prefixes;
   }

   public abstract String asString(long currentPc, SymbolFinder symFinder);

   public String getName() {
      return name;
   }

   public String getPrefixString() {
      StringBuffer buf = new StringBuffer();
      if ((prefixes & PREFIX_REPZ) != 0)
         buf.append("repz ");
      if ((prefixes & PREFIX_REPNZ) != 0)
         buf.append("repnz ");
      if ((prefixes & PREFIX_LOCK) != 0)
         buf.append("lock ");

      return buf.toString();
   }

   protected String getOperandAsString(Operand op) {
      StringBuffer buf = new StringBuffer();
      if ((op instanceof Register) || (op instanceof Address)) {
         buf.append(op.toString());
      } else {
         Number number = ((Immediate)op).getNumber();
         buf.append("0x");
         buf.append(Integer.toHexString(number.intValue()));
      }
      return buf.toString();
   }

   public int getSize() {
      return size;
   }

   public boolean isArithmetic() {
      return false;
   }

   public boolean isBranch() {
      return false;
   }

   public boolean isCall() {
      return false;
   }

   public boolean isFloat() {
      return false;
   }

   public boolean isIllegal() {
      return false;
   }

   public boolean isLoad() {
      return false;
   }

   public boolean isLogical() {
      return false;
   }

   public boolean isMove() {
      return false;
   }

   public boolean isReturn() {
      return false;
   }

   public boolean isShift() {
      return false;
   }

   public boolean isStore() {
      return false;
   }

   public boolean isTrap() {
      return false;
   }

   public boolean isNoop() {
      return false;
   }

   protected static String comma = ", ";
   protected static String spaces = "\t";
}
