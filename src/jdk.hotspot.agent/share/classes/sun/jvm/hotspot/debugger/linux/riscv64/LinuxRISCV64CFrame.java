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

import sun.jvm.hotspot.code.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.debugger.cdbg.*;
import sun.jvm.hotspot.debugger.linux.*;
import sun.jvm.hotspot.debugger.riscv64.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.runtime.riscv64.*;

public final class LinuxRISCV64CFrame extends DwarfCFrame {
   private static final int C_FRAME_LINK_OFFSET        = -2;
   private static final int C_FRAME_RETURN_ADDR_OFFSET = -1;

   private Address ra;

   private static LinuxRISCV64CFrame getFrameFromReg(LinuxDebugger linuxDbg, Function<Integer, Address> getreg) {
      Address pc = getreg.apply(RISCV64ThreadContext.PC);
      Address sp = getreg.apply(RISCV64ThreadContext.SP);
      if (pc == null || sp == null) {
        return null;
      }

      Address fp = getreg.apply(RISCV64ThreadContext.FP);
      Address ra = getreg.apply(RISCV64ThreadContext.LR);
      if (linuxDbg.isSignalTrampoline(pc)) {
        return new LinuxRISCV64CFrame(linuxDbg, sp, fp, null, pc, ra, null);
      }

      Address cfa = null;
      DwarfParser dwarf = createDwarfParser(linuxDbg, pc);

      if (dwarf != null) { // Native frame
        cfa = getreg.apply(dwarf.getCFARegister())
                    .addOffsetTo(dwarf.getCFAOffset());
      }

      return (fp == null && cfa == null && VM.getVM().getCodeCache().findBlobUnsafe(pc) == null)
        ? null
        : new LinuxRISCV64CFrame(linuxDbg, sp, fp, cfa, pc, ra, dwarf);
   }

   public static LinuxRISCV64CFrame getTopFrame(LinuxDebugger linuxDbg, ThreadContext context) {
      return getFrameFromReg(linuxDbg, context::getRegisterAsAddress);
   }

   private LinuxRISCV64CFrame(LinuxDebugger linuxDbg, Address sp, Address fp, Address cfa, Address pc, Address ra, DwarfParser dwarf) {
      this(linuxDbg, sp, fp, cfa, pc, ra, dwarf, false);
   }

   private LinuxRISCV64CFrame(LinuxDebugger linuxDbg, Address sp, Address fp, Address cfa, Address pc, DwarfParser dwarf) {
      this(linuxDbg, sp, fp, cfa, pc, null, dwarf, false);
   }

   private LinuxRISCV64CFrame(LinuxDebugger linuxDbg, Address sp, Address fp, Address cfa, Address pc, DwarfParser dwarf, boolean use1ByteBeforeToLookup) {
      this(linuxDbg, sp, fp, cfa, pc, null, dwarf, use1ByteBeforeToLookup);
   }

   private LinuxRISCV64CFrame(LinuxDebugger linuxDbg, Address sp, Address fp, Address cfa, Address pc, Address ra, DwarfParser dwarf, boolean use1ByteBeforeToLookup) {
      super(linuxDbg, sp, fp, cfa, pc, dwarf, use1ByteBeforeToLookup);

      if (dwarf != null) {
        var senderPCFromDwarf = getSenderPC(null);
        if (senderPCFromDwarf != null) {
          ra = senderPCFromDwarf;
        } else if (ra != null) {
          var raReg = dwarf.getRARegister();
          if (raReg != RISCV64ThreadContext.LR) {
            throw new DebuggerException("Unexpected RA register: " + raReg);
          }
        }
      }

      this.ra = ra;
   }

   private Address getSenderCFA(DwarfParser senderDwarf, Address senderSP, Address senderFP) {
     if (senderDwarf == null) { // Sender frame is Java
       return null;
     }

     int senderCFAReg = senderDwarf.getCFARegister();
     Address cfaRegister = switch(senderCFAReg) {
       case RISCV64ThreadContext.FP -> senderFP;
       case RISCV64ThreadContext.SP -> senderSP;
       default -> throw new DebuggerException("Unsupported CFA register: " + senderCFAReg);
     };
     return cfaRegister == null ? null : cfaRegister.addOffsetTo(senderDwarf.getCFAOffset());
   }

   private boolean isValidFrame(Address senderCFA, Address senderFP, Address senderPC) {
     if (senderCFA == null && senderFP == null) {
       // A compiled Java sender can lack DWARF CFA and have a zero FP when PreserveFramePointer is disabled.
       return VM.getVM().getCodeCache().findBlobUnsafe(senderPC) != null;
     }
     return super.isValidFrame(senderCFA, senderFP);
   }

   private JavaThread getJavaThreadFromThreadProxy(ThreadProxy tp) {
     Threads threads = VM.getVM().getThreads();
     for (int i = 0; i < threads.getNumberOfThreads(); i++) {
       var jthread = threads.getJavaThreadAt(i);
       if (tp.equals(jthread.getThreadProxy())) {
         return jthread;
       }
     }
     throw new DebuggerException("JavaThread not found");
   }

   @Override
   protected Address getSenderPC(Address senderPC) {
     // Caller-supplied PCs and DWARF return-address rules do not depend on the RISC-V FP layout.
     if (senderPC != null || dwarf() != null) {
       return super.getSenderPC(senderPC);
     }
     if (fp() == null) {
       return null;
     }

     try {
       // Interpreter frames and FP-based stubs save PC at FP + return_addr_offset (-1 word; see frame_riscv.hpp).
       return fp().getAddressAt(C_FRAME_RETURN_ADDR_OFFSET * VM.getVM().getAddressSize());
     } catch (UnmappedAddressException | UnalignedAddressException _) {
       return null;
     }
   }

   @Override
   protected Address getSenderSP(Address senderSP) {
     if (senderSP != null || dwarf() != null) {
       return super.getSenderSP(senderSP);
     }
     // frame::sender_sp() in frame_riscv.inline.hpp returns FP + sender_sp_offset, which is zero on RISC-V.
     return fp();
   }

   @Override
   protected Address getSenderFP(Address senderFP) {
     if (senderFP != null || dwarf() != null) {
       return super.getSenderFP(senderFP);
     }
     // frame::link() in frame_riscv.inline.hpp reads the saved FP register at FP + link_offset (-2 words).
     return fp() == null
       ? null
       : fp().getAddressAt(C_FRAME_LINK_OFFSET * VM.getVM().getAddressSize());
   }

   @Override
   public CFrame sender(ThreadProxy thread, Address senderSP, Address senderFP, Address senderPC) {
     if (linuxDbg().isSignalTrampoline(pc())) {
       // SP points to the signal context
       //   https://github.com/torvalds/linux/blob/v6.3-rc5/arch/riscv/kernel/signal.c#L223
       return getFrameFromReg(linuxDbg(),
           r -> LinuxRISCV64ThreadContext.getRegFromSignalTrampoline(sp(), r.intValue()));
     }

     if (hasNativeLibrary() && dwarf() == null) {
       // Cannot find a sender frame if DWARF is missing even though PC in native library.
       return null;
     }

     if (senderSP == null || senderPC == null) {
       CodeCache cc = VM.getVM().getCodeCache();
       CodeBlob currentBlob = cc.findBlobUnsafe(pc());

       // This case is different from HotSpot. See JDK-8371194 and JDK-8382548 for details.
       if (currentBlob == null) { // current frame is native
         if (senderPC == null) {
           if (dwarf() == null) {
             // dwarf is not available, try with fp
             senderPC = getSenderPC(null);
           } else {
             // ra is assigned the sender pc on construction if dwarf is available
             senderPC = ra;
           }
         }
         senderSP = getSenderSP(senderSP);
         senderFP = getSenderFP(senderFP);
       } else { // current frame is Java
         if (currentBlob.isContinuationStub()) {
           var jthread = getJavaThreadFromThreadProxy(thread);
           var contEntry = jthread.getContEntry();
           // The walk can reach enclosing frames while the thread still points to the innermost entry.
           while (contEntry != null && contEntry.getEntrySP().lessThan(sp())) {
             contEntry = contEntry.getParent();
           }
           if (contEntry == null) {
             return null;
           }
           senderSP = contEntry.getEntrySP();
           senderFP = contEntry.getEntryFP();
           senderPC = contEntry.getEntryPC();
         } else if (currentBlob.getFrameSize() == 0) {
           senderSP = fp();
           senderPC = getSenderPC(senderPC);
           senderFP = getSenderFP(senderFP);
         } else {
           // Calculate sender SP and FP without FP
           // because we cannot believe FP if PreserveFramePointer is disabled.
           senderSP = sp().addOffsetTo(currentBlob.getFrameSize());
           senderFP = senderSP.getAddressAt(C_FRAME_LINK_OFFSET * VM.getVM().getAddressSize());
           senderPC = senderSP.getAddressAt(C_FRAME_RETURN_ADDR_OFFSET * VM.getVM().getAddressSize());
         }
       }
     }

     if (senderSP == null || senderPC == null) {
       return null;
     }

     // Native sender PCs are return addresses,
     // generated-code anchors and signal PCs need exact lookup.
     boolean use1ByteBeforeToLookup = VM.getVM().getCodeCache().findBlobUnsafe(senderPC) == null
         && !linuxDbg().isSignalTrampoline(senderPC);
     // A return PC can equal the next function's entry (os::abort/os::die), selecting the wrong FDE.
     //   0x3f9818f7b6 <os::abort+56>: jalr 302(ra)       # abort@plt
     //   0x3f9818f7ba <os::die+0>:    addi sp, sp, -16
     // Wrong SA chain: libc abort -> os::die -> end.
     // Correct chain: libc abort -> os::abort -> VMError::report_and_die.
     // PC-1 (0x3f9818f7b9) lies inside the call in os::abort and selects its FDE.
     Address lookupPC = use1ByteBeforeToLookup ? senderPC.addOffsetTo(-1) : senderPC;
     DwarfParser senderDwarf = null;
     try {
       senderDwarf = createDwarfParser(linuxDbg(), lookupPC);
     } catch (DebuggerException _) {
       // Signal trampolines are unwound by restoring registers from the signal context, not ordinary DWARF rules.
       if (linuxDbg().isSignalTrampoline(senderPC)) {
         return new LinuxRISCV64CFrame(linuxDbg(), senderSP, senderFP, null, senderPC, null);
       }

       // Returns CFrame if the sender is native frame even though it does not have DWARF,
       // otherwise returns null because we cannot unwind anymore.
       return linuxDbg().findLibPtrByAddress(lookupPC) != null
         ? new LinuxRISCV64CFrame(linuxDbg(), senderSP, senderFP, null, senderPC, null, use1ByteBeforeToLookup)
         : null;
     }

     try {
       Address senderCFA = getSenderCFA(senderDwarf, senderSP, senderFP);
       return isValidFrame(senderCFA, senderFP, senderPC)
         ? new LinuxRISCV64CFrame(linuxDbg(), senderSP, senderFP, senderCFA, senderPC, senderDwarf, use1ByteBeforeToLookup)
         : null;
     } catch (DebuggerException e) {
       if (linuxDbg().isSignalTrampoline(senderPC)) {
         return new LinuxRISCV64CFrame(linuxDbg(), senderSP, senderFP, null, senderPC, null);
       }
       throw e;
     }
   }

   @Override
   public Frame toFrame() {
     return new RISCV64Frame(sp(), fp(), pc());
   }
}
