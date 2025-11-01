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

package sun.jvm.hotspot.debugger.linux.amd64;

import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.debugger.amd64.*;
import sun.jvm.hotspot.debugger.linux.*;
import sun.jvm.hotspot.debugger.cdbg.*;
import sun.jvm.hotspot.debugger.cdbg.basic.*;

public final class LinuxAMD64CFrame extends BasicCFrame {

   public static LinuxAMD64CFrame getTopFrame(LinuxDebugger dbg, Address rip, ThreadContext context) {
      Address libptr = dbg.findLibPtrByAddress(rip);
      Address cfa = context.getRegisterAsAddress(AMD64ThreadContext.RBP);
      DwarfParser dwarf = null;

      if (libptr != null) { // Native frame
        dwarf = new DwarfParser(libptr);
        try {
          dwarf.processDwarf(rip);
        } catch (DebuggerException e) {
          // DWARF processing should succeed when the frame is native
          // but it might fail if Common Information Entry (CIE) has language
          // personality routine and/or Language Specific Data Area (LSDA).
          return new LinuxAMD64CFrame(dbg, cfa, rip, dwarf, true);
        }

        cfa = context.getRegisterAsAddress(dwarf.getCFARegister())
                     .addOffsetTo(dwarf.getCFAOffset());
      }

      return (cfa == null) ? null
                           : new LinuxAMD64CFrame(dbg, cfa, rip, dwarf);
   }

   private LinuxAMD64CFrame(LinuxDebugger dbg, Address cfa, Address rip, DwarfParser dwarf) {
      this(dbg, cfa, rip, dwarf, false);
   }

   private LinuxAMD64CFrame(LinuxDebugger dbg, Address cfa, Address rip, DwarfParser dwarf, boolean finalFrame) {
      this(dbg, cfa, rip, dwarf, finalFrame, false);
   }

   private LinuxAMD64CFrame(LinuxDebugger dbg, Address cfa, Address rip, DwarfParser dwarf, boolean finalFrame, boolean use1ByteBeforeToLookup) {
      super(dbg.getCDebugger());
      this.cfa = cfa;
      this.rip = rip;
      this.dbg = dbg;
      this.dwarf = dwarf;
      this.finalFrame = finalFrame;
      this.use1ByteBeforeToLookup = use1ByteBeforeToLookup;
   }

   // override base class impl to avoid ELF parsing
   public ClosestSymbol closestSymbolToPC() {
      Address symAddr = use1ByteBeforeToLookup ? pc().addOffsetTo(-1) : pc();
      // try native lookup in debugger.
      return dbg.lookup(dbg.getAddressValue(symAddr));
   }

   public Address pc() {
      return rip;
   }

   public Address localVariableBase() {
      return cfa;
   }

   private Address getNextPC(boolean useDwarf) {
     try {
       long offs = useDwarf ? dwarf.getReturnAddressOffsetFromCFA()
                            : ADDRESS_SIZE;
       return cfa.getAddressAt(offs);
     } catch (UnmappedAddressException | UnalignedAddressException e) {
       return null;
     }
   }

   private boolean isValidFrame(Address nextCFA, boolean isNative) {
     // CFA should never be null.
     // nextCFA must be greater than current CFA, if frame is native.
     // Java interpreter frames can share the CFA (frame pointer).
     return nextCFA != null &&
         (!isNative || (isNative && nextCFA.greaterThan(cfa)));
   }

   private Address getNextCFA(DwarfParser nextDwarf, ThreadContext context, Address senderFP) {
     Address nextCFA;
     boolean isNative = false;

     if (senderFP == null) {
       senderFP = cfa.getAddressAt(0);  // RBP by default
     }

     if (nextDwarf == null) { // Next frame is Java
       nextCFA = (dwarf == null) ? senderFP // Current frame is Java
                                 : cfa.getAddressAt(dwarf.getBasePointerOffsetFromCFA()); // Current frame is Native
     } else { // Next frame is Native
       if (dwarf == null) { // Current frame is Java
         nextCFA = senderFP.addOffsetTo(-nextDwarf.getBasePointerOffsetFromCFA());
       } else { // Current frame is Native
         isNative = true;
         int nextCFAReg = nextDwarf.getCFARegister();
         if (nextCFAReg == AMD64ThreadContext.RBP) {
           Address rbp = dwarf.isBPOffsetAvailable() ? cfa.addOffsetTo(dwarf.getBasePointerOffsetFromCFA())
                                                     : context.getRegisterAsAddress(AMD64ThreadContext.RBP);
           Address nextRBP = rbp.getAddressAt(0);
           nextCFA = nextRBP.addOffsetTo(-nextDwarf.getBasePointerOffsetFromCFA());
         } else if (nextCFAReg == AMD64ThreadContext.RSP) {
           // next RSP should be previous slot of return address.
           Address nextRSP = cfa.addOffsetTo(dwarf.getReturnAddressOffsetFromCFA())
                                .addOffsetTo(ADDRESS_SIZE);
           nextCFA = nextRSP.addOffsetTo(nextDwarf.getCFAOffset());
         } else {
           throw new DebuggerException("Unsupported CFA register: " + nextCFAReg);
         }
       }
     }

     // Sanity check for next CFA address
     try {
       nextCFA.getAddressAt(0);
     } catch (Exception e) {
       // return null if next CFA address is invalid
       return null;
     }

     return isValidFrame(nextCFA, isNative) ? nextCFA : null;
   }

   @Override
   public CFrame sender(ThreadProxy th) {
     return sender(th, null, null);
   }

   @Override
   public CFrame sender(ThreadProxy th, Address fp, Address pc) {
     if (finalFrame) {
       return null;
     }

     ThreadContext context = th.getContext();

     Address nextPC = pc != null ? pc : getNextPC(dwarf != null);
     if (nextPC == null) {
       return null;
     }

     DwarfParser nextDwarf = null;
     boolean fallback = false;
     try {
       nextDwarf = createDwarfParser(nextPC);
     } catch (DebuggerException _) {
       // Try again with RIP-1 in case RIP is just outside function bounds,
       // due to function ending with a `call` instruction.
       try {
         nextDwarf = createDwarfParser(nextPC.addOffsetTo(-1));
         fallback = true;
       } catch (DebuggerException _) {
         // DWARF processing should succeed when the frame is native
         // but it might fail if Common Information Entry (CIE) has language
         // personality routine and/or Language Specific Data Area (LSDA).
         return new LinuxAMD64CFrame(dbg, null, nextPC, nextDwarf, true);
       }
     }

     Address nextCFA = getNextCFA(nextDwarf, context, fp);
     return nextCFA == null ? null
                            : new LinuxAMD64CFrame(dbg, nextCFA, nextPC, nextDwarf, false, fallback);
   }

   private DwarfParser createDwarfParser(Address pc) throws DebuggerException {
     DwarfParser nextDwarf = null;
     Address libptr = dbg.findLibPtrByAddress(pc);
     if (libptr != null) {
       try {
         nextDwarf = new DwarfParser(libptr);
       } catch (DebuggerException _) {
         // Bail out to Java frame
       }
     }

     if (nextDwarf != null) {
       nextDwarf.processDwarf(pc);
     }

     return nextDwarf;
   }

   // package/class internals only
   private static final int ADDRESS_SIZE = 8;
   private Address rip;
   private Address cfa;
   private LinuxDebugger dbg;
   private DwarfParser dwarf;
   private boolean finalFrame;
   private boolean use1ByteBeforeToLookup;
}
