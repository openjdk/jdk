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

import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.debugger.riscv64.*;
import sun.jvm.hotspot.debugger.linux.*;

public class LinuxRISCV64ThreadContext extends RISCV64ThreadContext {
  private LinuxDebugger debugger;

  public LinuxRISCV64ThreadContext(LinuxDebugger debugger) {
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
    if (index < 0 || index >= NPRGREG) {
      throw new IllegalArgumentException("Unsupported register index: " + index);
    }

    // rt_sigframe starts with siginfo_t (128 bytes), followed by ucontext.
    // On Linux RV64, uc_mcontext starts at offset 176 in ucontext. Its
    // sc_regs contains PC followed by x1-x31, just like RISCV64ThreadContext.
    // See arch/riscv/kernel/signal.c and arch/riscv/include/uapi/asm/ucontext.h
    // in the Linux kernel.
    return sp.getAddressAt(128 + 176 + index * 8L);
  }
}
