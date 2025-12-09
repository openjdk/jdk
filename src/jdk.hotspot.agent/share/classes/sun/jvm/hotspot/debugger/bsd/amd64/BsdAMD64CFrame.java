/*
 * Copyright (c) 2003, 2025, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.debugger.bsd.amd64;

import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.debugger.amd64.*;
import sun.jvm.hotspot.debugger.bsd.*;
import sun.jvm.hotspot.debugger.cdbg.*;
import sun.jvm.hotspot.debugger.cdbg.basic.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.runtime.amd64.*;

public final class BsdAMD64CFrame extends BasicCFrame {
   public BsdAMD64CFrame(BsdDebugger dbg, Address rsp, Address rbp, Address rip) {
      super(dbg.getCDebugger());
      this.rsp = rsp;
      this.rbp = rbp;
      this.rip = rip;
      this.dbg = dbg;
   }

   // override base class impl to avoid ELF parsing
   public ClosestSymbol closestSymbolToPC() {
      // try native lookup in debugger.
      return dbg.lookup(dbg.getAddressValue(pc()));
   }

   public Address pc() {
      return rip;
   }

   public Address localVariableBase() {
      return rbp;
   }

   @Override
   public CFrame sender(ThreadProxy thread) {
      return sender(thread, null, null, null);
   }

   @Override
   public CFrame sender(ThreadProxy thread, Address sp, Address fp, Address pc) {
      // Check fp
      // Skip if both fp and pc are given - do not need to load from rbp.
      if (fp == null && pc == null) {
        if (rbp == null) {
          return null;
        }

        // Check alignment of rbp
        if (dbg.getAddressValue(rbp) % ADDRESS_SIZE != 0) {
          return null;
        }
      }

      Address nextRSP = sp != null ? sp : rbp.addOffsetTo(2 * ADDRESS_SIZE);
      if (nextRSP == null) {
        return null;
      }
      Address nextRBP = fp != null ? fp : rbp.getAddressAt(0);
      if (nextRBP == null) {
        return null;
      }
      Address nextPC  = pc != null ? pc : rbp.getAddressAt(ADDRESS_SIZE);
      if (nextPC == null) {
        return null;
      }
      return new BsdAMD64CFrame(dbg, nextRSP, nextRBP, nextPC);
   }

   @Override
   public Frame toFrame() {
      return new AMD64Frame(rsp, rbp, rip);
   }

   // package/class internals only
   private static final int ADDRESS_SIZE = 8;
   private Address rsp;
   private Address rip;
   private Address rbp;
   private BsdDebugger dbg;
}
