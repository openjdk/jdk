/*
 * Copyright (c) 2006, 2012, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.debugger.linux.sparc;

import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.debugger.sparc.*;
import sun.jvm.hotspot.debugger.linux.*;
import sun.jvm.hotspot.debugger.cdbg.*;
import sun.jvm.hotspot.debugger.cdbg.basic.*;

final public class LinuxSPARCCFrame extends BasicCFrame {
   // package/class internals only

   public LinuxSPARCCFrame(LinuxDebugger dbg, Address sp, Address pc, int address_size) {
      super(dbg.getCDebugger());
      this.sp = sp;
      this.pc = pc;
      this.dbg = dbg;
      this.address_size=address_size;
      if (address_size==8) SPARC_STACK_BIAS = 0x7ff;
      else SPARC_STACK_BIAS = 0x0;
   }

   // override base class impl to avoid ELF parsing
   public ClosestSymbol closestSymbolToPC() {
      // try native lookup in debugger.
      return dbg.lookup(dbg.getAddressValue(pc()));
   }

   public Address pc() {
      return     pc;
   }

   public Address localVariableBase() {
      return sp;
   }

   public CFrame sender(ThreadProxy thread) {
      if (sp == null) {
        return null;
      }

      Address nextSP = sp.getAddressAt( SPARCThreadContext.R_SP * address_size + SPARC_STACK_BIAS);
      if (nextSP == null) {
        return null;
      }
      Address nextPC  = sp.getAddressAt(SPARCThreadContext.R_O7 * address_size + SPARC_STACK_BIAS);
      if (nextPC == null) {
        return null;
      }
      return new LinuxSPARCCFrame(dbg, nextSP, nextPC,address_size);
   }

   public static int SPARC_STACK_BIAS;
   private static int address_size;
   private Address pc;
   private Address sp;
   private LinuxDebugger dbg;
}
