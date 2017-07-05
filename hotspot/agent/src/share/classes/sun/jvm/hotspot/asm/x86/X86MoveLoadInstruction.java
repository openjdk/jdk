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

public class X86MoveLoadInstruction extends X86MemoryInstruction
                                          implements LoadInstruction {
   public X86MoveLoadInstruction(String name, X86Register register, Address address, int dataType, int size, int prefixes) {
      super(name, address, register, dataType, size, prefixes);
   }

   public boolean isLoad() {
      return true;
   }

   public Register[] getLoadDestinations() {
      Register[] destinations = new Register[1];
      destinations[0] = register;
      return destinations;
   }

   protected String initDescription() {
      StringBuffer buf = new StringBuffer();
      buf.append(getPrefixString());
      buf.append(getName());
      buf.append(spaces);
      buf.append(register.toString());
      buf.append(comma);
      buf.append(address.toString());
      return buf.toString();
   }

   public Address getLoadSource() {
      return address;
   }

}
