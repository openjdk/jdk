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

import sun.jvm.hotspot.asm.*;

public class X86LogicInstruction extends X86Instruction
                                      implements LogicInstruction {
   final private Operand operand1;
   final private Operand operand2;
   final private int operation;

   public X86LogicInstruction(String name, int operation, Operand op1, Operand op2, int size, int prefixes) {
      super(name, size, prefixes);
      this.operation = operation;
      this.operand1 = op1;
      this.operand2 = op2;
   }

   public String asString(long currentPc, SymbolFinder symFinder) {
      StringBuffer buf = new StringBuffer();
      buf.append(getPrefixString());
      buf.append(getName());
      buf.append(spaces);
      buf.append(getOperandAsString(operand1));
      if(operand2 != null) {
         buf.append(comma);
         buf.append(getOperandAsString(operand2));
      }
      return buf.toString();
   }

   public Operand getLogicDestination() {
      return operand1;
   }

   public Operand[] getLogicSources() {
      return (new Operand[] { operand2 });
   }

   public int getOperation() {
      return operation;
   }

   public boolean isLogic() {
      return true;
   }
}
