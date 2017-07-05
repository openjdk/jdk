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

package sun.jvm.hotspot.runtime.win32_ia64;

import java.io.*;
import java.util.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.debugger.ia64.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.runtime.ia64.*;
import sun.jvm.hotspot.types.*;
import sun.jvm.hotspot.utilities.*;

public class Win32IA64JavaThreadPDAccess implements JavaThreadPDAccess {
  // private static AddressField  lastJavaPCField;
  // private static AddressField  lastJavaFPField;
  private static AddressField  lastJavaIFrameField;
  private static AddressField  osThreadField;

  // Field from OSThread
  private static CIntegerField osThreadPThreadIDField;

  // This is currently unneeded but is being kept in case we change
  // the currentFrameGuess algorithm
  private static final long GUESS_SCAN_RANGE = 128 * 1024;

  static {
    VM.registerVMInitializedObserver(new Observer() {
        public void update(Observable o, Object data) {
          initialize(VM.getVM().getTypeDataBase());
        }
      });
  }

  private static synchronized void initialize(TypeDataBase db) {
    Type type = db.lookupType("JavaThread");

    lastJavaIFrameField     = type.getAddressField("_last_Java_iframe");
    osThreadField           = type.getAddressField("_osthread");

    type = db.lookupType("OSThread");
    osThreadPThreadIDField   = type.getCIntegerField("_pthread_id");
  }

  public    Address getLastJavaIFrame(Address addr) {
    return lastJavaIFrameField.getValue(addr);
  }


  public    Address getBaseOfStackPointer(Address addr) {
    return null;
  }

  public Address getLastJavaFP(Address addr) {
    return null; // Not in 1.4.1
  }

  public    Address getLastJavaPC(Address addr) {
    return null; // Not in 1.4.1
  }

  public boolean isInterpretedFrame() {

    // In 1.4.1 there are only interpreted frames
    // and there is no pc
    return true;
  }

  public    Frame getLastFramePD(JavaThread thread, Address addr) {
    // The thread is the JavaThread that contains "this"
    // so we don't need any new accessor at the JavaThread level
    Address iframe = getLastJavaIFrame(addr);
    Address pc = thread.getLastJavaPC();
    if (iframe == null) {
      return null; // no information
    }
    return new IA64Frame(thread.getLastJavaSP(), iframe, pc);
  }

  public    RegisterMap newRegisterMap(JavaThread thread, boolean updateMap) {
    return new IA64RegisterMap(thread, updateMap);
  }

  public    Frame getCurrentFrameGuess(JavaThread thread, Address addr) {
    return getLastFramePD(thread, addr);
  }

  public    void printThreadIDOn(Address addr, PrintStream tty) {
    tty.print(getThreadProxy(addr));
  }

  public    void printInfoOn(Address threadAddr, PrintStream tty) {
    tty.print("Thread id: ");
    printThreadIDOn(threadAddr, tty);
    tty.println("\nLastJavaIFrame: " + getLastJavaIFrame(threadAddr));
  }

  public    Address getLastSP(Address addr) {
    ThreadProxy t = getThreadProxy(addr);
    IA64ThreadContext context = (IA64ThreadContext) t.getContext();
    return context.getRegisterAsAddress(IA64ThreadContext.SP);
  }

  public    ThreadProxy getThreadProxy(Address addr) {
    // Addr is the address of the JavaThread.
    // Fetch the OSThread (for now and for simplicity, not making a
    // separate "OSThread" class in this package)
    Address osThreadAddr = osThreadField.getValue(addr);
    // Get the address of the _pthread_id from the OSThread
    Address pthreadIdAddr = osThreadAddr.addOffsetTo(osThreadPThreadIDField.getOffset());

    JVMDebugger debugger = VM.getVM().getDebugger();
    return debugger.getThreadForIdentifierAddress(pthreadIdAddr);
  }
}
