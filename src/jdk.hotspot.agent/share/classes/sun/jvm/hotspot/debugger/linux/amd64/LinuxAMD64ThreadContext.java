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

import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.debugger.amd64.*;
import sun.jvm.hotspot.debugger.linux.*;
import sun.jvm.hotspot.runtime.*;

public class LinuxAMD64ThreadContext extends AMD64ThreadContext {
  private LinuxDebugger debugger;

  public LinuxAMD64ThreadContext(LinuxDebugger debugger) {
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
    // ucontext_t is located at top of stack.
    // See definition of rt_sigframe in arch/x86/include/asm/sigframe.h
    // in Linux Kernel.
    Address addrUCMContext = sp.addOffsetTo(40); // offsetof(ucontext_t, uc_mcontext) = 40
    Address addrGRegs = addrUCMContext; // gregs is located at top of ucontext_t

    // They are from sys/ucontext.h
    final int REG_RBP = 10;
    final int REG_RSP = 15;
    final int REG_RIP = 16;
    return switch(index) {
      case AMD64ThreadContext.RBP -> addrGRegs.getAddressAt(REG_RBP * VM.getVM().getAddressSize());
      case AMD64ThreadContext.RSP -> addrGRegs.getAddressAt(REG_RSP * VM.getVM().getAddressSize());
      case AMD64ThreadContext.RIP -> addrGRegs.getAddressAt(REG_RIP * VM.getVM().getAddressSize());
      default -> throw new IllegalArgumentException("Unsupported register index: " + index);
    };
  }
}
