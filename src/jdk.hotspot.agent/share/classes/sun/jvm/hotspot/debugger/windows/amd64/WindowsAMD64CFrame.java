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

package sun.jvm.hotspot.debugger.windows.amd64;

import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.debugger.amd64.*;
import sun.jvm.hotspot.debugger.cdbg.*;
import sun.jvm.hotspot.debugger.cdbg.basic.*;
import sun.jvm.hotspot.debugger.windbg.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.runtime.amd64.*;

public class WindowsAMD64CFrame extends BasicCFrame {
  private JavaThread ownerThread;
  private Address rsp;
  private Address rbp;
  private Address pc;

  /** Constructor for topmost frame */
  public WindowsAMD64CFrame(WindbgDebugger dbg, JavaThread ownerThread, Address rsp, Address rbp, Address pc) {
    super(dbg.getCDebugger());
    this.ownerThread = ownerThread;
    this.rsp = rsp;
    this.rbp = rbp;
    this.pc  = pc;
    this.dbg = dbg;
  }

  @Override
  public CFrame sender(ThreadProxy thread) {
    return sender(thread, null, null, null);
  }

  @Override
  public CFrame sender(ThreadProxy th, Address nextSP, Address nextFP, Address nextPC) {
    if (nextSP == null && nextPC == null) {
      // GetStackTrace() by Windows Debug API would unwind frame with given SP, FP, and PC.
      // However it would not work for dynamic generated code like CodeBlob because
      // HotSpot would not register unwind info like RtlAddFunctionTable().
      // Thus SA should check whether current PC is in CodeCache at first when nextPC is null.
      var cb = VM.getVM().getCodeCache().findBlob(pc);
      if (cb != null) {
        if (cb.getFrameSize() > 0) {
          nextSP = rsp.addOffsetTo(cb.getFrameSize());
          nextPC = nextSP.getAddressAt(-1 * VM.getVM().getAddressSize());

          // Set nextFP to null when PreserveFramePointer is disabled because We could not find out
          // frame pointer of sender frame - it might be omitted.
          nextFP = VM.getVM().getCommandLineBooleanFlag("PreserveFramePointer") ? rsp.getAddressAt(0) : null;
        } else {
          // Use Frame (AMD64Frame) to access slots on stack.
          var frame = toFrame();
          nextSP = frame.getSenderSP();
          nextPC = frame.getSenderPC();
          nextFP = frame.getLink();
        }
        return new WindowsAMD64CFrame(dbg, ownerThread, nextSP, nextFP, nextPC);
      }

      WindbgDebugger.SenderRegs senderRegs = dbg.getSenderRegs(rsp, rbp, pc);
      if (senderRegs == null) {
        return null;
      }

      if (senderRegs.nextSP() == null || senderRegs.nextSP().lessThanOrEqual(rsp)) {
        return null;
      }
      nextSP = senderRegs.nextSP();

      if (senderRegs.nextPC() == null) {
        return null;
      }
      nextPC = senderRegs.nextPC();

      nextFP = senderRegs.nextFP();
    }
    return new WindowsAMD64CFrame(dbg, ownerThread, nextSP, nextFP, nextPC);
  }

  public Address pc() {
    return pc;
  }

  public Address localVariableBase() {
    return rbp;
  }

  @Override
  public Frame toFrame() {
    // Find the top of JavaVFrame related to this CFrame. The Windows  GetStackTrace DbgHelp API
    // cannot get FP for java frames.
    for (JavaVFrame vf = ownerThread.getLastJavaVFrameDbg(); vf != null; vf = vf.javaSender()) {
      Frame f = vf.getFrame();
      if (f.getSP().equals(rsp) && f.getPC().equals(pc)) {
        return f;
      } else if (f.getSP().greaterThanOrEqual(rsp)) {
        return f;
      }
    }

    return new AMD64Frame(rsp, localVariableBase(), pc);
  }

  private WindbgDebugger dbg;
}
