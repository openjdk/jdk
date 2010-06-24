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

public class X86BranchInstruction extends X86Instruction
                                        implements BranchInstruction {
   final private X86PCRelativeAddress addr;

   public X86BranchInstruction(String name, X86PCRelativeAddress addr, int size, int prefixes) {
      super(name, size, prefixes);
      this.addr = addr;
      if(addr instanceof X86PCRelativeAddress) {
         addr.setInstructionSize(getSize());
      }
   }

   public String asString(long currentPc, SymbolFinder symFinder) {
      StringBuffer buf = new StringBuffer();
      buf.append(getPrefixString());
      buf.append(getName());
      if(addr != null) {
         buf.append(spaces);
         if(addr instanceof X86PCRelativeAddress) {
            long disp = ((X86PCRelativeAddress)addr).getDisplacement();
            long address = disp + currentPc;
            buf.append(symFinder.getSymbolFor(address));
         }
      }
      return buf.toString();
   }

   public Address getBranchDestination() {
      return addr;
   }

   public boolean isBranch() {
      return true;
   }

   public boolean isConditional() {
      return false;
   }

}
