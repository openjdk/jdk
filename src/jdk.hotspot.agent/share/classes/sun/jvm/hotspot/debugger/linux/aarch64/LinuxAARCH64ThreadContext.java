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

import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.debugger.aarch64.*;
import sun.jvm.hotspot.debugger.linux.*;
import sun.jvm.hotspot.runtime.*;

public class LinuxAARCH64ThreadContext extends AARCH64ThreadContext {
  private LinuxDebugger debugger;

  public LinuxAARCH64ThreadContext(LinuxDebugger debugger) {
    super();
    this.debugger = debugger;
  }

  public void setRegisterAsAddress(int index, Address value) {
    setRegister(index, debugger.getAddressValue(value));
  }

  public Address getRegisterAsAddress(int index) {
    return debugger.newAddress(getRegister(index));
  }

  public static Address getRegFromSignalTrampoline(Address sp, int index) {
    // ucontext_t locates at 2nd element of rt_sigframe.
    // See definition of rt_sigframe in arch/arm/kernel/signal.h
    // in Linux Kernel.
    Address addrUContext = sp.addOffsetTo(128); // sizeof(siginfo_t) = 128
    Address addrUCMContext = addrUContext.addOffsetTo(176); // offsetof(ucontext_t, uc_mcontext) = 176

    Address ptrCallerSP = addrUCMContext.addOffsetTo(256); // offsetof(uc_mcontext, sp) = 256
    Address ptrCallerPC = addrUCMContext.addOffsetTo(264); // offsetof(uc_mcontext, pc) = 264
    Address ptrCallerRegs = addrUCMContext.addOffsetTo(8); // offsetof(uc_mcontext, regs) = 8

    return switch(index) {
      case AARCH64ThreadContext.FP -> ptrCallerRegs.getAddressAt(AARCH64ThreadContext.FP * VM.getVM().getAddressSize());
      case AARCH64ThreadContext.LR -> ptrCallerRegs.getAddressAt(AARCH64ThreadContext.LR * VM.getVM().getAddressSize());
      case AARCH64ThreadContext.SP -> ptrCallerSP.getAddressAt(0);
      case AARCH64ThreadContext.PC -> ptrCallerPC.getAddressAt(0);
      default -> throw new IllegalArgumentException("Unsupported register index: " + index);
    };
  }
}
