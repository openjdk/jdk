/*
 * Copyright (c) 2003, 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2015, Red Hat Inc.
 * Copyright (c) 2021, Huawei Technologies Co., Ltd. All rights reserved.
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

package sun.jvm.hotspot.debugger.linux.riscv64;

import java.util.function.Function;

import sun.jvm.hotspot.code.CodeBlob;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.debugger.riscv64.*;
import sun.jvm.hotspot.debugger.linux.*;
import sun.jvm.hotspot.debugger.cdbg.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.runtime.riscv64.*;

public final class LinuxRISCV64CFrame extends DwarfCFrame {
   private static final int ADDRESS_SIZE = 8;

   // Value of the CIE return-address register, when it can be recovered.
   private final Address ra;

   private static LinuxRISCV64CFrame getFrameFromReg(LinuxDebugger dbg,
                                                    Function<Integer, Address> getreg) {
      Address pc = getreg.apply(RISCV64ThreadContext.PC);
      Address sp = getreg.apply(RISCV64ThreadContext.SP);
      if (pc == null || sp == null) {
        return null;
      }
      Address fp = getreg.apply(RISCV64ThreadContext.FP);
      Address ra = null;

      // The RISC-V signal trampoline need not have unwind information.
      if (dbg.isSignalTrampoline(pc)) {
        return new LinuxRISCV64CFrame(dbg, sp, fp, null, pc, ra, null, false);
      }

      DwarfParser dwarf;
      try {
        // Use PC+1 to apply the CFA rules starting at PC.
        dwarf = createDwarfParser(dbg, pc.addOffsetTo(1));
      } catch (DebuggerException e) {
        // Show the native frame even if it cannot be unwound.
        return new LinuxRISCV64CFrame(dbg, sp, fp, null, pc, ra, null, false);
      }

      Address cfa = null;
      if (dwarf != null) {
        int raReg = dwarf.getRARegister();
        if (raReg < 0) {
          throw new DebuggerException("Unsupported RA register: " + raReg);
        }
        // GCC's __riscv_save_* helpers return through x5 rather than x1.
        ra = getreg.apply(raReg);
        int cfaReg = dwarf.getCFARegister();
        if (cfaReg < 0) {
          throw new DebuggerException("Unsupported CFA register: " + cfaReg);
        }
        Address base = getreg.apply(cfaReg);
        if (base == null) {
          return null;
        }
        cfa = base.addOffsetTo(dwarf.getCFAOffset());
      }
      return new LinuxRISCV64CFrame(dbg, sp, fp, cfa, pc, ra, dwarf, false);
   }

   public static LinuxRISCV64CFrame getTopFrame(LinuxDebugger dbg, ThreadContext context) {
      return getFrameFromReg(dbg, context::getRegisterAsAddress);
   }

   private LinuxRISCV64CFrame(LinuxDebugger dbg, Address sp, Address fp, Address cfa,
                             Address pc, Address ra, DwarfParser dwarf,
                             boolean use1ByteBeforeToLookup) {
      super(dbg, sp, fp, cfa, pc, dwarf, use1ByteBeforeToLookup);
      this.ra = ra;
   }

   @Override
   protected Address getSenderPC(Address senderPC) {
      if (senderPC != null) {
        return senderPC;
      }
      if (dwarf() != null) {
        // The return address may still be in the register specified by the CIE.
        if (dwarf().getReturnAddressOffsetFromCFA() == Integer.MAX_VALUE) {
          return ra;
        }
        return super.getSenderPC(null);
      }

      try {
        return fp() == null ? null : fp().getAddressAt(-ADDRESS_SIZE);
      } catch (UnmappedAddressException | UnalignedAddressException e) {
        return null;
      }
   }

   @Override
   protected Address getSenderSP(Address senderSP) {
      // RISC-V FP points above the saved FP and RA, at the caller's SP.
      return senderSP == null && dwarf() == null ? fp() : super.getSenderSP(senderSP);
   }

   @Override
   protected Address getSenderFP(Address senderFP) {
      if (senderFP == null && dwarf() == null) {
        try {
          return fp() == null ? null : fp().getAddressAt(-2 * ADDRESS_SIZE);
        } catch (UnmappedAddressException | UnalignedAddressException e) {
          return null;
        }
      }
      return super.getSenderFP(senderFP);
   }

   private Address getSenderCFA(DwarfParser senderDwarf, Address senderSP, Address senderFP) {
      if (senderDwarf == null) {
        return null;
      }
      int reg = senderDwarf.getCFARegister();
      Address base = switch (reg) {
        case RISCV64ThreadContext.SP -> senderSP;
        case RISCV64ThreadContext.FP -> senderFP;
        default -> throw new DebuggerException("Unsupported CFA register: " + reg);
      };
      if (base == null) {
        throw new DebuggerException("CFA register is unavailable: " + reg);
      }
      return base.addOffsetTo(senderDwarf.getCFAOffset());
   }

   private JavaThread getJavaThreadFromThreadProxy(ThreadProxy thread) {
      Threads threads = VM.getVM().getThreads();
      for (int i = 0; i < threads.getNumberOfThreads(); i++) {
        JavaThread jthread = threads.getJavaThreadAt(i);
        if (thread.equals(jthread.getThreadProxy())) {
          return jthread;
        }
      }
      throw new DebuggerException("JavaThread not found");
   }

   @Override
   public CFrame sender(ThreadProxy thread, Address senderSP, Address senderFP, Address senderPC) {
      if (linuxDbg().isSignalTrampoline(pc())) {
        return getFrameFromReg(linuxDbg(),
            r -> LinuxRISCV64ThreadContext.getRegFromSignalTrampoline(sp(), r));
      }

      if (hasNativeLibrary() && dwarf() == null) {
        return null;
      }

      if (senderSP == null && dwarf() == null) {
        CodeBlob blob = VM.getVM().getCodeCache().findBlobUnsafe(pc());
        if (blob != null && blob.isContinuationStub()) {
          JavaThread jthread = getJavaThreadFromThreadProxy(thread);
          var entry = Continuation.getContinuationEntryForSP(jthread, sp());
          senderSP = entry.getEntrySP();
          senderFP = entry.getEntryFP();
          senderPC = entry.getEntryPC();
        } else if (blob != null && blob.getFrameSize() > 0) {
          // A compiled frame's FP may be used as a general-purpose register.
          senderSP = sp().addOffsetTo(blob.getFrameSize());
          if (senderFP == null) {
            senderFP = senderSP.getAddressAt(-2 * ADDRESS_SIZE);
          }
          if (senderPC == null) {
            senderPC = senderSP.getAddressAt(-ADDRESS_SIZE);
          }
        }
      }

      senderPC = getSenderPC(senderPC);
      if (senderPC == null) {
        return null;
      }
      senderSP = getSenderSP(senderSP);
      if (senderSP == null || senderSP.lessThan(sp())) {
        return null;
      }
      // A leaf frame can have the same SP as its caller, but must make progress.
      if (senderSP.equals(sp()) && senderPC.equals(pc())) {
        return null;
      }
      senderFP = getSenderFP(senderFP);

      if (linuxDbg().isSignalTrampoline(senderPC)) {
        return new LinuxRISCV64CFrame(linuxDbg(), senderSP, senderFP, null,
                                     senderPC, null, null, false);
      }

      DwarfParser senderDwarf;
      // A return address denotes the instruction after the call. Look inside
      // the call, including when another FDE starts exactly at the return PC.
      // PC-1 works with compressed instructions as well.
      Address lookupPC = senderPC.addOffsetTo(-1);
      try {
        senderDwarf = createDwarfParser(linuxDbg(), lookupPC);
      } catch (DebuggerException e) {
        // createDwarfParser() throws only for a PC in a native library.
        return new LinuxRISCV64CFrame(linuxDbg(), senderSP, senderFP, null,
                                     senderPC, null, null, true);
      }

      Address senderCFA = getSenderCFA(senderDwarf, senderSP, senderFP);
      if (senderCFA != null && senderCFA.lessThan(senderSP)) {
        return null;
      }
      Address senderRA = null;
      if (dwarf() != null && senderDwarf != null &&
          senderDwarf.getReturnAddressOffsetFromCFA() == Integer.MAX_VALUE &&
          dwarf().getRARegister() != senderDwarf.getRARegister()) {
        // A save helper returning through x5 may have saved the caller's x1.
        // Keep that value for a caller whose CFI still describes a register-held
        // return address. With the same return column, the saved value is just
        // senderPC and must not be reused as the caller's own return address.
        int offset = dwarf().getOffsetFromCFA(senderDwarf.getRARegister());
        if (offset != Integer.MAX_VALUE) {
          senderRA = getSenderSP(null).getAddressAt(offset);
        }
      }
      return new LinuxRISCV64CFrame(linuxDbg(), senderSP, senderFP, senderCFA,
                                   senderPC, senderRA, senderDwarf, senderDwarf != null);
   }

   @Override
   public Address localVariableBase() {
      // RISC-V FP points to the CFA, not to the saved FP.
      return fp();
   }

   @Override
   public Frame toFrame() {
      return new RISCV64Frame(sp(), fp(), pc());
   }
}
