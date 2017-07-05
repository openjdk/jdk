/*
 * Copyright (c) 2000, 2006, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.runtime.solaris_sparc;

import java.io.*;
import java.util.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.debugger.sparc.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.runtime.sparc.*;
import sun.jvm.hotspot.types.*;
import sun.jvm.hotspot.utilities.*;

public class SolarisSPARCJavaThreadPDAccess implements JavaThreadPDAccess {
  private static AddressField baseOfStackPointerField;
  private static AddressField postJavaStateField;
  private static AddressField osThreadField;
  private static int          isPC;
  private static int          hasFlushed;

  // Field from OSThread
  private static CIntegerField osThreadThreadIDField;

  static {
    VM.registerVMInitializedObserver(new Observer() {
        public void update(Observable o, Object data) {
          initialize(VM.getVM().getTypeDataBase());
        }
      });
  }

  private static synchronized void initialize(TypeDataBase db) {
    Type type = db.lookupType("JavaThread");
    Type anchorType = db.lookupType("JavaFrameAnchor");

    baseOfStackPointerField = type.getAddressField("_base_of_stack_pointer");
    osThreadField           = type.getAddressField("_osthread");
    hasFlushed              = db.lookupIntConstant("JavaFrameAnchor::flushed").intValue();

    type = db.lookupType("OSThread");
    osThreadThreadIDField   = type.getCIntegerField("_thread_id");
  }

  public    Address getLastJavaFP(Address addr) {
        return null;

  }

  public    Address getLastJavaPC(Address addr) {
    return null;
  }

  public Address getBaseOfStackPointer(Address addr) {
    return baseOfStackPointerField.getValue(addr);
  }

  public Frame getLastFramePD(JavaThread thread, Address addr) {

    // This assert doesn't work in the debugging case for threads
    // which are running Java code and which haven't re-entered the
    // runtime (e.g., through a Method.invoke() or otherwise). They
    // haven't yet "decached" their last Java stack pointer to the
    // thread.

    //    if (Assert.ASSERTS_ENABLED) {
    //      Assert.that(hasLastJavaFrame(), "must have last_Java_sp() when suspended");
    //      // FIXME: add assertion about flushing register windows for runtime system
    //      // (not appropriate for debugging system, though, unless at safepoin t)
    //    }

    // FIXME: I don't think this is necessary, but might be useful
    // while debugging
    if (thread.getLastJavaSP() == null) {
      return null;
    }

    // sparc does a lazy window flush. The _flags field of the JavaFrameAnchor
    // encodes whether the windows have flushed. Whenever the windows have flushed
    // there will be a last_Java_pc.
    // In a relective system we'd have to  do something to force the thread to flush
    // its windows and give us the pc (or the younger_sp so we can find it ourselves)
    // In a debugger situation (process or core) the flush should have happened and
    // so if we don't have the younger sp we can find it
    //
    if (thread.getLastJavaPC() != null) {
      return new SPARCFrame(SPARCFrame.biasSP(thread.getLastJavaSP()), thread.getLastJavaPC());
    } else {
      Frame top = getCurrentFrameGuess(thread, addr);
      return new SPARCFrame(SPARCFrame.biasSP(thread.getLastJavaSP()),
                            SPARCFrame.biasSP(SPARCFrame.findYoungerSP(top.getSP(), thread.getLastJavaSP())),
                            false);
    }


  }

  public RegisterMap newRegisterMap(JavaThread thread, boolean updateMap) {
    return new SPARCRegisterMap(thread, updateMap);
  }

  public Frame getCurrentFrameGuess(JavaThread thread, Address addr) {

    // If java stack is walkable then both last_Java_sp and last_Java_pc are
    // non null and we can start stack walk from this frame.
    if (thread.getLastJavaSP() != null && thread.getLastJavaPC() != null) {
      return new SPARCFrame(SPARCFrame.biasSP(thread.getLastJavaSP()), thread.getLastJavaPC());
    }

    ThreadProxy t = getThreadProxy(addr);
    SPARCThreadContext context = (SPARCThreadContext) t.getContext();
    // For now, let's see what happens if we do a similar thing to
    // what the runtime code does. I suspect this may cause us to lose
    // the top frame from the stack.
    Address sp = context.getRegisterAsAddress(SPARCThreadContext.R_SP);
    Address pc = context.getRegisterAsAddress(SPARCThreadContext.R_PC);

    if ((sp == null) || (pc == null)) {
      // Problems (have not hit this case so far, but would be bad to continue if we did)
      return null;
    }

    return new SPARCFrame(sp, pc);
  }


  public void printThreadIDOn(Address addr, PrintStream tty) {
    tty.print(getThreadProxy(addr));
  }

  public Address getLastSP(Address addr) {
    ThreadProxy t = getThreadProxy(addr);
    SPARCThreadContext context = (SPARCThreadContext) t.getContext();
    return SPARCFrame.unBiasSP(context.getRegisterAsAddress(SPARCThreadContext.R_SP));
  }

  public void printInfoOn(Address threadAddr, PrintStream tty) {
  }

  public ThreadProxy getThreadProxy(Address addr) {
    // Fetch the OSThread (for now and for simplicity, not making a
    // separate "OSThread" class in this package)
    Address osThreadAddr = osThreadField.getValue(addr);
    // Get the address of the thread ID from the OSThread
    Address tidAddr = osThreadAddr.addOffsetTo(osThreadThreadIDField.getOffset());

    JVMDebugger debugger = VM.getVM().getDebugger();
    return debugger.getThreadForIdentifierAddress(tidAddr);
  }


}
