/*
 * Copyright (c) 2003, 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2015, Red Hat Inc.
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

package sun.jvm.hotspot.debugger.linux.aarch64;

import java.util.function.Function;

import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.debugger.aarch64.*;
import sun.jvm.hotspot.debugger.linux.*;
import sun.jvm.hotspot.debugger.cdbg.*;
import sun.jvm.hotspot.code.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.runtime.aarch64.*;

public final class LinuxAARCH64CFrame extends DwarfCFrame {

   private Address lr;

   private static LinuxAARCH64CFrame getFrameFromReg(LinuxDebugger linuxDbg, Function<Integer, Address> getreg) {
      Address pc = getreg.apply(AARCH64ThreadContext.PC);
      Address sp = getreg.apply(AARCH64ThreadContext.SP);
      Address fp = getreg.apply(AARCH64ThreadContext.FP);
      Address lr = getreg.apply(AARCH64ThreadContext.LR);
      Address cfa = null;
      DwarfParser dwarf = createDwarfParser(linuxDbg, pc);

      if (dwarf != null) { // Native frame
        cfa = getreg.apply(dwarf.getCFARegister())
                    .addOffsetTo(dwarf.getCFAOffset());
      }

      return (fp == null && cfa == null)
        ? null
        : new LinuxAARCH64CFrame(linuxDbg, sp, fp, cfa, pc, lr, dwarf);
   }

   public static LinuxAARCH64CFrame getTopFrame(LinuxDebugger linuxDbg, ThreadContext context) {
      return getFrameFromReg(linuxDbg, context::getRegisterAsAddress);
   }

   private LinuxAARCH64CFrame(LinuxDebugger linuxDbg, Address sp, Address fp, Address cfa, Address pc, Address lr, DwarfParser dwarf) {
      this(linuxDbg, sp, fp, cfa, pc, lr, dwarf, false);
   }

   private LinuxAARCH64CFrame(LinuxDebugger linuxDbg, Address sp, Address fp, Address cfa, Address pc, DwarfParser dwarf) {
      this(linuxDbg, sp, fp, cfa, pc, null, dwarf, false);
   }

   private LinuxAARCH64CFrame(LinuxDebugger linuxDbg, Address sp, Address fp, Address cfa, Address pc, DwarfParser dwarf, boolean use1ByteBeforeToLookup) {
      this(linuxDbg, sp, fp, cfa, pc, null, dwarf, use1ByteBeforeToLookup);
   }

   private LinuxAARCH64CFrame(LinuxDebugger linuxDbg, Address sp, Address fp, Address cfa, Address pc, Address lr, DwarfParser dwarf, boolean use1ByteBeforeToLookup) {
      super(linuxDbg, sp, fp, cfa, pc, dwarf, use1ByteBeforeToLookup);

      if (dwarf != null) {
        // Prioritize to use RA from DWARF instead of LR
        var senderPCFromDwarf = getSenderPC(null);
        if (senderPCFromDwarf != null) {
          lr = senderPCFromDwarf;
        } else if (lr != null) {
          // We should set passed lr to LR of this frame,
          // but throws DebuggerException if lr is not used for RA.
          var raReg = dwarf.getRARegister();
          if (raReg != AARCH64ThreadContext.LR) {
            throw new DebuggerException("Unexpected RA register: " + raReg);
          }
        }
      }

      this.lr = lr;
   }

   private Address getSenderCFA(DwarfParser senderDwarf, Address senderSP, Address senderFP) {
     if (senderDwarf == null) { // Sender frame is Java
       // CFA is not available on Java frame
       return null;
     }

     // Sender frame is Native
     int senderCFAReg = senderDwarf.getCFARegister();
     return switch(senderCFAReg){
       case AARCH64ThreadContext.FP -> senderFP.addOffsetTo(senderDwarf.getCFAOffset());
       case AARCH64ThreadContext.SP -> senderSP.addOffsetTo(senderDwarf.getCFAOffset());
       default -> throw new DebuggerException("Unsupported CFA register: " + senderCFAReg);
     };
   }

   @Override
   public CFrame sender(ThreadProxy thread, Address senderSP, Address senderFP, Address senderPC) {
      if (linuxDbg().isSignalTrampoline(pc())) {
        // SP points signal context
        //   https://github.com/torvalds/linux/blob/v6.17/arch/arm64/kernel/signal.c#L1357
        return getFrameFromReg(linuxDbg(), r -> LinuxAARCH64ThreadContext.getRegFromSignalTrampoline(sp(), r.intValue()));
      }

      if (senderPC == null) {
        // Use getSenderPC() if current frame is Java because we cannot rely on lr in this case.
        senderPC = dwarf() == null ? getSenderPC(null) : lr;
        if (senderPC == null) {
          return null;
        }
      }

      senderFP = getSenderFP(senderFP);

      if (senderSP == null) {
        CodeCache cc = VM.getVM().getCodeCache();
        CodeBlob currentBlob = cc.findBlobUnsafe(pc());

        // This case is different from HotSpot. See JDK-8371194 for details.
        if (currentBlob != null && (currentBlob.isContinuationStub() || currentBlob.isNativeMethod())) {
          // Use FP since it should always be valid for these cases.
          // TODO: These should be walked as Frames not CFrames.
          senderSP = fp().addOffsetTo(2 * VM.getVM().getAddressSize());
        } else {
          CodeBlob codeBlob = cc.findBlobUnsafe(senderPC);
          boolean useCodeBlob = codeBlob != null && codeBlob.getFrameSize() > 0;
          senderSP = useCodeBlob ? senderFP.addOffsetTo((2 * VM.getVM().getAddressSize()) - codeBlob.getFrameSize()) : getSenderSP(null);
        }
      }
      if (senderSP == null) {
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
          if (linuxDbg().isSignalTrampoline(senderPC)) {
            // We can through the caller frame if it is signal trampoline.
            // DWARF processing might be fail because vdso.so does not have .eh_frame .
            return new LinuxAARCH64CFrame(linuxDbg(), senderSP, senderFP, null, senderPC, senderDwarf);
          }

          // DWARF processing should succeed when the frame is native
          // but it might fail if Common Information Entry (CIE) has language
          // personality routine and/or Language Specific Data Area (LSDA).
          return null;
        }
      }

      try {
        Address senderCFA = getSenderCFA(senderDwarf, senderSP, senderFP);
        return isValidFrame(senderCFA, senderFP)
          ? new LinuxAARCH64CFrame(linuxDbg(), senderSP, senderFP, senderCFA, senderPC, senderDwarf, fallback)
          : null;
      } catch (DebuggerException e) {
        if (linuxDbg().isSignalTrampoline(senderPC)) {
          // We can through the caller frame if it is signal trampoline.
          // getSenderCFA() might fail because DwarfParser cannot find out CFA register.
          return new LinuxAARCH64CFrame(linuxDbg(), senderSP, senderFP, null, senderPC, senderDwarf, fallback);
        }

        // Rethrow the original exception if getSenderCFA() failed
        // and the caller is not signal trampoline.
        throw e;
      }
   }

   @Override
   public Frame toFrame() {
     return new AARCH64Frame(sp(), fp(), pc());
   }

}
