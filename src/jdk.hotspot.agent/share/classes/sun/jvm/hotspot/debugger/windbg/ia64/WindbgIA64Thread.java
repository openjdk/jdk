/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.debugger.windbg.ia64;

import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.debugger.ia64.*;
import sun.jvm.hotspot.debugger.windbg.*;

class WindbgIA64Thread implements ThreadProxy {
  private WindbgDebugger debugger;
  private long           sysId;
  private boolean        gotID;
  private long           id;

  /** The address argument must be the address of the HANDLE of the
      desired thread in the target process. */
  WindbgIA64Thread(WindbgDebugger debugger, Address addr) {
    this.debugger = debugger;
    // FIXME: size of data fetched here should be configurable.
    // However, making it so would produce a dependency on the "types"
    // package from the debugger package, which is not desired.

    // another hack here is that we use sys thread id instead of handle.
    // windbg can't get details based on handles it seems.
    // I assume that osThread_win32 thread struct has _thread_id (which
    // sys thread id) just after handle field.

    this.sysId   = (int) addr.addOffsetTo(debugger.getAddressSize()).getCIntegerAt(0, 4, true);
    gotID = false;
  }

  WindbgIA64Thread(WindbgDebugger debugger, long sysId) {
    this.debugger = debugger;
    this.sysId    = sysId;
    gotID         = false;
  }

  public ThreadContext getContext() throws IllegalThreadStateException {
    long[] data = debugger.getThreadIntegerRegisterSet(getThreadID());
    WindbgIA64ThreadContext context = new WindbgIA64ThreadContext(debugger);
    for (int i = 0; i < data.length; i++) {
      context.setRegister(i, data[i]);
    }
    return context;
  }

  public boolean canSetContext() throws DebuggerException {
    return false;
  }

  public void setContext(ThreadContext thrCtx)
    throws IllegalThreadStateException, DebuggerException {
    throw new DebuggerException("Unimplemented");
  }

  public boolean equals(Object obj) {
    if ((obj == null) || !(obj instanceof WindbgIA64Thread)) {
      return false;
    }

    return (((WindbgIA64Thread) obj).getThreadID() == getThreadID());
  }

  public int hashCode() {
    return (int) getThreadID();
  }

  public String toString() {
    return Long.toString(getThreadID());
  }

  /** Retrieves the thread ID of this thread by examining the Thread
      Information Block. */
  private long getThreadID() {
    if (!gotID) {
       id = debugger.getThreadIdFromSysId(sysId);
    }

    return id;
  }
}
