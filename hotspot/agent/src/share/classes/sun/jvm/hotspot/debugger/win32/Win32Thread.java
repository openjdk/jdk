/*
 * Copyright (c) 2001, 2004, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.debugger.win32;

import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.debugger.x86.*;

class Win32Thread implements ThreadProxy {
  private Win32Debugger debugger;
  private int           handle;
  private boolean       mustDuplicate;
  private boolean       gotID;
  private int           id;

  /** The address argument must be the address of the HANDLE of the
      desired thread in the target process. */
  Win32Thread(Win32Debugger debugger, Address addr) {
    this.debugger = debugger;
    // FIXME: size of data fetched here should be configurable.
    // However, making it so would produce a dependency on the "types"
    // package from the debugger package, which is not desired.
    this.handle   = (int) addr.getCIntegerAt(0, 4, true);
    // Thread handles in the target process must be duplicated before
    // fetching their contexts
    mustDuplicate = true;
    gotID = false;
  }

  /** The integer argument must be the value of a HANDLE received from
      the "threadlist" operation. */
  Win32Thread(Win32Debugger debugger, long handle) {
    this.debugger = debugger;
    this.handle   = (int) handle;
    mustDuplicate = false;
    gotID         = false;
  }

  public ThreadContext getContext() throws IllegalThreadStateException {
    if (!debugger.isSuspended()) {
      throw new IllegalThreadStateException("Target process must be suspended");
    }
    long[] data = debugger.getThreadIntegerRegisterSet(handle, mustDuplicate);
    Win32ThreadContext context = new Win32ThreadContext(debugger);
    for (int i = 0; i < data.length; i++) {
      context.setRegister(i, data[i]);
    }
    return context;
  }

  public boolean canSetContext() throws DebuggerException {
    return true;
  }

  public void setContext(ThreadContext thrCtx)
    throws IllegalThreadStateException, DebuggerException {
    if (!debugger.isSuspended()) {
      throw new IllegalThreadStateException("Target process must be suspended");
    }
    X86ThreadContext context = (X86ThreadContext) thrCtx;
    long[] data = new long[X86ThreadContext.NPRGREG];
    for (int i = 0; i < data.length; i++) {
      data[i] = context.getRegister(i);
    }
    debugger.setThreadIntegerRegisterSet(handle, mustDuplicate, data);
  }

  public boolean equals(Object obj) {
    if ((obj == null) || !(obj instanceof Win32Thread)) {
      return false;
    }

    return (((Win32Thread) obj).getThreadID() == getThreadID());
  }

  public int hashCode() {
    return getThreadID();
  }

  public String toString() {
    return Integer.toString(getThreadID());
  }

  /** Retrieves the thread ID of this thread by examining the Thread
      Information Block. */
  private int getThreadID() {
    if (!gotID) {
      try {
        // Get thread context
        X86ThreadContext context = (X86ThreadContext) getContext();
        // Get LDT entry for FS register
        Win32LDTEntry ldt =
          debugger.getThreadSelectorEntry(handle,
                                          mustDuplicate,
                                          (int) context.getRegister(X86ThreadContext.FS));
        // Get base address of segment = Thread Environment Block (TEB)
        Address teb = debugger.newAddress(ldt.getBase());
        // Thread ID is at offset 0x24
        id = (int) teb.getCIntegerAt(0x24, 4, true);
        gotID = true;
      } catch (AddressException e) {
        throw new DebuggerException(e);
      }
    }

    return id;
  }
}
