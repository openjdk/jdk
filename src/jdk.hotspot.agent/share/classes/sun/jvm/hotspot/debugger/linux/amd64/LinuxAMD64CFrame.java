/*
 * Copyright (c) 2003, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.util.function.Function;

import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.debugger.amd64.*;
import sun.jvm.hotspot.debugger.linux.*;
import sun.jvm.hotspot.debugger.cdbg.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.runtime.amd64.*;

public final class LinuxAMD64CFrame extends DwarfCFrame {

   private static LinuxAMD64CFrame getFrameFromReg(LinuxDebugger linuxDbg, Function<Integer, Address> getreg) {
      Address rip = getreg.apply(AMD64ThreadContext.RIP);
      Address rsp = getreg.apply(AMD64ThreadContext.RSP);
      Address rbp = getreg.apply(AMD64ThreadContext.RBP);
      Address cfa = null;
      DwarfParser dwarf = createDwarfParser(linuxDbg, rip);

      if (dwarf != null) { // Native frame
        cfa = getreg.apply(dwarf.getCFARegister())
                    .addOffsetTo(dwarf.getCFAOffset());
      }

      return (rbp == null && cfa == null)
        ? null
        : new LinuxAMD64CFrame(linuxDbg, rsp, rbp, cfa, rip, dwarf);
   }

   public static LinuxAMD64CFrame getTopFrame(LinuxDebugger linuxDbg, ThreadContext context) {
      return getFrameFromReg(linuxDbg, context::getRegisterAsAddress);
   }

   private LinuxAMD64CFrame(LinuxDebugger linuxDbg, Address rsp, Address rbp, Address cfa, Address rip, DwarfParser dwarf) {
      this(linuxDbg, rsp, rbp, cfa, rip, dwarf, false);
   }

   private LinuxAMD64CFrame(LinuxDebugger linuxDbg, Address rsp, Address rbp, Address cfa, Address rip, DwarfParser dwarf, boolean use1ByteBeforeToLookup) {
      super(linuxDbg, rsp, rbp, cfa, rip, dwarf, use1ByteBeforeToLookup);
   }

   private Address getSenderCFA(DwarfParser senderDwarf, Address senderSP, Address senderFP) {
     if (senderDwarf == null) { // Sender frame is Java
       // CFA is not available on Java frame
       return null;
     }

     // Sender frame is Native
     int senderCFAReg = senderDwarf.getCFARegister();
     return switch(senderCFAReg){
       case AMD64ThreadContext.RBP -> senderFP.addOffsetTo(senderDwarf.getCFAOffset());
       case AMD64ThreadContext.RSP -> senderSP.addOffsetTo(senderDwarf.getCFAOffset());
       default -> throw new DebuggerException("Unsupported CFA register: " + senderCFAReg);
     };
   }

   @Override
   public CFrame sender(ThreadProxy th, Address senderSP, Address senderFP, Address senderPC) {
     if (linuxDbg().isSignalTrampoline(pc())) {
       // RSP points signal context
       //   https://github.com/torvalds/linux/blob/v6.17/arch/x86/kernel/signal.c#L94
       return getFrameFromReg(linuxDbg(), r -> LinuxAMD64ThreadContext.getRegFromSignalTrampoline(sp(), r.intValue()));
     }

     senderSP = getSenderSP(senderSP);
     if (senderSP == null) {
       return null;
     }
     senderPC = getSenderPC(senderPC);
     if (senderPC == null) {
       return null;
     }

     DwarfParser senderDwarf = null;
     boolean fallback = false;
     try {
       senderDwarf = createDwarfParser(linuxDbg(), senderPC);
     } catch (DebuggerException _) {
       // Try again with PC-1 in case PC is just outside function bounds,
       // due to function ending with a `call` instruction.
       try {
         senderDwarf = createDwarfParser(linuxDbg(), senderPC.addOffsetTo(-1));
         fallback = true;
       } catch (DebuggerException _) {
         // DWARF processing should succeed when the frame is native
         // but it might fail if Common Information Entry (CIE) has language
         // personality routine and/or Language Specific Data Area (LSDA).
         return null;
       }
     }

     senderFP = getSenderFP(senderFP);

     try {
       Address senderCFA = getSenderCFA(senderDwarf, senderSP, senderFP);
       return isValidFrame(senderCFA, senderFP)
         ? new LinuxAMD64CFrame(linuxDbg(), senderSP, senderFP, senderCFA, senderPC, senderDwarf, fallback)
         : null;
     } catch (DebuggerException e) {
       if (linuxDbg().isSignalTrampoline(senderPC)) {
         // We can through the caller frame if it is signal trampoline.
         // getSenderCFA() might fail because DwarfParser cannot find out CFA register.
         return new LinuxAMD64CFrame(linuxDbg(), senderSP, senderFP, null, senderPC, senderDwarf, fallback);
       }

       // Rethrow the original exception if getSenderCFA() failed
       // and the caller is not signal trampoline.
       throw e;
     }
   }

   @Override
   public Frame toFrame() {
     return new AMD64Frame(sp(), localVariableBase(), pc());
   }

}
