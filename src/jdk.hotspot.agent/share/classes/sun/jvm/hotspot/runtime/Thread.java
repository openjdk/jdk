/*
 * Copyright (c) 2000, 2024, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.runtime;

import java.util.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.types.*;
import sun.jvm.hotspot.utilities.Observable;
import sun.jvm.hotspot.utilities.Observer;

public class Thread extends VMObject {
  private static long tlabFieldOffset;

  private static CIntegerField suspendFlagsField;

  private static AddressField currentPendingMonitorField;
  private static AddressField currentWaitingMonitorField;
  private static AddressField osThreadField;

  private static JLongField allocatedBytesField;

  static {
    VM.registerVMInitializedObserver(new Observer() {
        public void update(Observable o, Object data) {
          initialize(VM.getVM().getTypeDataBase());
        }
      });
  }

  private static synchronized void initialize(TypeDataBase db) {
    Type typeThread = db.lookupType("Thread");
    Type typeJavaThread = db.lookupType("JavaThread");

    suspendFlagsField = typeJavaThread.getCIntegerField("_suspend_flags");
    osThreadField = typeThread.getAddressField("_osthread");


    tlabFieldOffset    = typeThread.getField("_tlab").getOffset();
    currentPendingMonitorField = typeJavaThread.getAddressField("_current_pending_monitor");
    currentWaitingMonitorField = typeJavaThread.getAddressField("_current_waiting_monitor");
    allocatedBytesField = typeThread.getJLongField("_allocated_bytes");
  }

  public Thread(Address addr) {
    super(addr);
  }

  public int suspendFlags() {
    return (int) suspendFlagsField.getValue(addr);
  }

  public ThreadLocalAllocBuffer tlab() {
    return new ThreadLocalAllocBuffer(addr.addOffsetTo(tlabFieldOffset));
  }

  public long allocatedBytes() {
    return allocatedBytesField.getValue(addr);
  }

  public boolean   isVMThread()                  { return false; }
  public boolean   isJavaThread()                { return false; }
  public boolean   isCompilerThread()            { return false; }
  public boolean   isCodeCacheSweeperThread()    { return false; }
  public boolean   isHiddenFromExternalView()    { return false; }
  public boolean   isJvmtiAgentThread()          { return false; }
  public boolean   isWatcherThread()             { return false; }
  public boolean   isServiceThread()             { return false; }
  public boolean   isMonitorDeflationThread()    { return false; }
  public boolean   isAttachListenerThread()      { return false; }

  /** Memory operations */
  public void oopsDo(AddressVisitor oopVisitor) {
    // FIXME: Empty for now; will later traverse JNI handles and
    // pending exception
  }

  public ObjectMonitor getCurrentPendingMonitor() {
    Address monitorAddr = currentPendingMonitorField.getValue(addr);
    if (monitorAddr == null) {
      return null;
    }
    return new ObjectMonitor(monitorAddr);
  }

  public ObjectMonitor getCurrentWaitingMonitor() {
    Address monitorAddr = currentWaitingMonitorField.getValue(addr);
    if (monitorAddr == null) {
      return null;
    }
    return new ObjectMonitor(monitorAddr);
  }

  public boolean isLockOwned(Address lock) {
    if (isInStack(lock)) return true;
    return false;
  }

  public boolean isInStack(Address a) {
    // In the Serviceability Agent we need access to the thread's
    // stack pointer to be able to answer this question. Since it is
    // only a debugging system at the moment we need access to the
    // underlying thread, which is only present for Java threads; see
    // JavaThread.java.
    return false;
  }

  public OSThread osThread() {
    return new OSThread(osThreadField.getValue(addr));
  }

  /** Assistance for ObjectMonitor implementation */
  Address threadObjectAddress() { return addr; }
}
