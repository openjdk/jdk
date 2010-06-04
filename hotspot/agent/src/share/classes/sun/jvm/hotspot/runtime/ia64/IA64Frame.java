/*
 * Copyright (c) 2003, 2006, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.runtime.ia64;

import java.util.*;
// import sun.jvm.hotspot.asm.ia64.*;
import sun.jvm.hotspot.code.*;
import sun.jvm.hotspot.compiler.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.oops.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.types.*;
import sun.jvm.hotspot.utilities.*;

/** Specialization of and implementation of abstract methods of the
    Frame class for the ia64 family of CPUs. */

public class IA64Frame extends Frame {
  private static final boolean DEBUG = false;

  // All frames

  // Interpreter frames

  // Entry frames

  // Native frames

  // an additional field beyond sp and pc:
  // Address raw_fp; // frame pointer only 1.4.2

  Address iframe;

  private IA64Frame() {
  }

  public IA64Frame(Address raw_sp, Address iframe, Address pc) {
    this.raw_sp = raw_sp;
    this.iframe = iframe;
    this.pc = pc;
    if (DEBUG) {
      System.err.println("IA64Frame(sp, iframe, pc): " + this);
      dumpStack();
    }
  }

  public Object clone() {
    IA64Frame frame = new IA64Frame();
    frame.raw_sp = raw_sp;
    frame.iframe = iframe;
    frame.pc = pc;
    return frame;
  }

  public boolean equals(Object arg) {
    if (arg == null) {
      return false;
    }

    if (!(arg instanceof IA64Frame)) {
      return false;
    }

    IA64Frame other = (IA64Frame) arg;

    return (AddressOps.equal(getSP(), other.getSP()) &&
            AddressOps.equal(getIFRAME(), other.getIFRAME()) &&
            AddressOps.equal(getPC(), other.getPC()));
  }

  public int hashCode() {
    if (iframe == null) {
      return 0;
    }

    return iframe.hashCode();
  }

  public String toString() {
    return "sp: " + (getSP() == null? "null" : getSP().toString()) +
         ", iframe: " + (getIFRAME() == null? "null" : getIFRAME().toString()) +
         ", pc: " + (pc == null? "null" : pc.toString());
  }

  // accessors for the instance variables
  public Address getFP() { return null; }
  public Address getIFRAME() { return iframe; }
  public Address getSP() { return raw_sp; }
  public Address getID() { return getFP(); }

  // FIXME: not implemented yet
  public boolean isSignalHandlerFrameDbg() { return false; }
  public int     getSignalNumberDbg()      { return 0;     }
  public String  getSignalNameDbg()        { return null;  }

  // FIXME: do sanity checks
  public boolean isInterpretedFrameValid() {
    return true;
  }

  public boolean isInterpretedFrame() { return iframe != null; }


  // FIXME: not applicable in current system
  //  void    patch_pc(Thread* thread, address pc);

  public Frame sender(RegisterMap regMap, CodeBlob cb) {

    if (iframe == null) {
      return null;
    }

    cInterpreter fr = new cInterpreter(iframe);

    if (fr.prev() == null) {
      Address wrapper = fr.wrapper();
      if ( wrapper == null) {
        return null;
      }
      IA64JavaCallWrapper jcw = new IA64JavaCallWrapper(wrapper);
      Address iprev = jcw.getPrevIFrame();
      if (iprev == null) {
        return null;
      }
      return new IA64Frame(null, iprev, null);
    } else {
      return new IA64Frame(null, fr.prev(), null);
    }

    /*
    IA64RegisterMap map = (IA64RegisterMap) regMap;

    if (Assert.ASSERTS_ENABLED) {
      Assert.that(map != null, "map must be set");
    }

    // Default is we done have to follow them. The sender_for_xxx will
    // update it accordingly
    map.setIncludeArgumentOops(false);

    if (isEntryFrame())       return senderForEntryFrame(map);
    if (isInterpretedFrame()) return senderForInterpreterFrame(map);

    if (!VM.getVM().isCore()) {
      if(cb == null) {
        cb = VM.getVM().getCodeCache().findBlob(getPC());
      } else {
        if (Assert.ASSERTS_ENABLED) {
          Assert.that(cb.equals(VM.getVM().getCodeCache().findBlob(getPC())), "Must be the same");
        }
      }

      if (cb != null) {
         return senderForCompiledFrame(map, cb);
      }
    }

    // Must be native-compiled frame, i.e. the marshaling code for native
    // methods that exists in the core system.
    return new IA64Frame(getSenderSP(), getLink(), getSenderPC());

    */
  }

  private Frame senderForEntryFrame(IA64RegisterMap map) {
    if (Assert.ASSERTS_ENABLED) {
      Assert.that(map != null, "map must be set");
    }
    /*
    // Java frame called from C; skip all C frames and return top C
    // frame of that chunk as the sender
    IA64JavaCallWrapper jcw = (IA64JavaCallWrapper) getEntryFrameCallWrapper();
    if (Assert.ASSERTS_ENABLED) {
      Assert.that(!entryFrameIsFirst(), "next Java fp must be non zero");
      Assert.that(jcw.getLastJavaSP().greaterThan(getSP()), "must be above this frame on stack");
    }
    IA64Frame fr = new IA64Frame(jcw.getLastJavaSP(), jcw.getLastJavaFP(), null);
    map.clear();
    if (Assert.ASSERTS_ENABLED) {
      Assert.that(map.getIncludeArgumentOops(), "should be set by clear");
    }
    return fr;
    */
    throw new RuntimeException("senderForEntryFrame NYI");
  }

  private Frame senderForInterpreterFrame(IA64RegisterMap map) {
    /*
    Address sp = addressOfStackSlot(INTERPRETER_FRAME_SENDER_SP_OFFSET).getAddressAt(0);
    // We do not need to update the callee-save register mapping because above
    // us is either another interpreter frame or a converter-frame, but never
    // directly a compiled frame.
    return new IA64Frame(sp, getLink(), getSenderPC());
    */
    throw new RuntimeException("senderForInterpreterFrame NYI");
  }

  private Frame senderForDeoptimizedFrame(IA64RegisterMap map, CodeBlob cb) {
    // FIXME
    throw new RuntimeException("Deoptimized frames not handled yet");
  }

  private Frame senderForCompiledFrame(IA64RegisterMap map, CodeBlob cb) {
    //
    // NOTE: some of this code is (unfortunately) duplicated in IA64CurrentFrameGuess
    //

    if (Assert.ASSERTS_ENABLED) {
      Assert.that(map != null, "map must be set");
    }

    throw new RuntimeException("senderForCompiledFrame NYI");

    /*

    // frame owned by optimizing compiler
    Address        sender_sp = null;

    if (VM.getVM().isClientCompiler()) {
      sender_sp        = addressOfStackSlot(SENDER_SP_OFFSET);
    } else {
      if (Assert.ASSERTS_ENABLED) {
        Assert.that(cb.getFrameSize() >= 0, "Compiled by Compiler1: do not use");
      }
      sender_sp = getSP().addOffsetTo(cb.getFrameSize());
    }

    // On Intel the return_address is always the word on the stack
    Address sender_pc = sender_sp.getAddressAt(-1 * VM.getVM().getAddressSize());

    if (map.getUpdateMap() && cb.getOopMaps() != null) {
      OopMapSet.updateRegisterMap(this, cb, map, true);
    }

    Address saved_fp = null;
    if (VM.getVM().isClientCompiler()) {
      saved_fp = getFP().getAddressAt(0);
    } else {
      int llink_offset = cb.getLinkOffset();
      if (llink_offset >= 0) {
        // Restore base-pointer, since next frame might be an interpreter frame.
        Address fp_addr = getSP().addOffsetTo(VM.getVM().getAddressSize() * llink_offset);
        saved_fp = fp_addr.getAddressAt(0);
      }
    }

    sender_sp = null ; // sp_addr.getAddressAt(0);

    return new IA64Frame(sender_sp, saved_fp, sender_pc);

    */
  }

  protected boolean hasSenderPD() {
    // FIXME
    return true;
  }

  public long frameSize() {
    throw new RuntimeException("frameSize NYI");
    /*
    return (getSenderSP().minus(getSP()) / VM.getVM().getAddressSize());
    */
  }

  public Address getLink() {
    throw new RuntimeException("getLink NYI");
    /*
    return addressOfStackSlot(LINK_OFFSET).getAddressAt(0);
    */
  }

  // FIXME: not implementable yet
  //inline void      frame::set_link(intptr_t* addr)  { *(intptr_t **)addr_at(link_offset) = addr; }

  public Address getUnextendedSP() { return getSP(); }

  // Return address:
  /*
  public Address getSenderPCAddr() { return addressOfStackSlot(RETURN_ADDR_OFFSET); }
  */

  public Address getSenderPC()     { return null;  }

  /*
  // return address of param, zero origin index.
  public Address getNativeParamAddr(int idx) {
    return addressOfStackSlot(NATIVE_FRAME_INITIAL_PARAM_OFFSET + idx);
  }
  */

  public Address getSenderSP()     { return null; }

  /*
  public Address compiledArgumentToLocationPD(VMReg reg, RegisterMap regMap, int argSize) {
    if (VM.getVM().isCore() || VM.getVM().isClientCompiler()) {
      throw new RuntimeException("Should not reach here");
    }

    return oopMapRegToLocation(reg, regMap);
  }

  */

  public Address addressOfInterpreterFrameLocals() {
    if (iframe == null) {
      throw new RuntimeException("Not an Interpreter frame");
    }
    cInterpreter fr = new cInterpreter(iframe);
    return fr.locals();
  }

  private Address addressOfInterpreterFrameBCX() {
    if (iframe == null) {
      throw new RuntimeException("Not an Interpreter frame");
    }
    cInterpreter fr = new cInterpreter(iframe);
    return fr.bcpAddr();
  }

  public int getInterpreterFrameBCI() {
    // FIXME: this is not atomic with respect to GC and is unsuitable
    // for use in a non-debugging, or reflective, system. Need to
    // figure out how to express this.
    Address bcp = addressOfInterpreterFrameBCX().getAddressAt(0);
    OopHandle methodHandle = addressOfInterpreterFrameMethod().getOopHandleAt(0);
    Method method = (Method) VM.getVM().getObjectHeap().newOop(methodHandle);
    return bcpToBci(bcp, method);
  }

  public Address addressOfInterpreterFrameMDX() {
    return null;
  }

  // FIXME
  //inline int frame::interpreter_frame_monitor_size() {
  //  return BasicObjectLock::size();
  //}

  // expression stack
  // (the max_stack arguments are used by the GC; see class FrameClosure)

  public Address addressOfInterpreterFrameExpressionStack() {
    if (iframe == null) {
      throw new RuntimeException("Not an Interpreter frame");
    }
    cInterpreter fr = new cInterpreter(iframe);
    return fr.stackBase();
  }

  public int getInterpreterFrameExpressionStackDirection() { return -1; }

  // top of expression stack
  public Address addressOfInterpreterFrameTOS() {
    if (iframe == null) {
      throw new RuntimeException("Not an Interpreter frame");
    }
    cInterpreter fr = new cInterpreter(iframe);
    // tos always points to first free element in c++ interpreter not tos
    return fr.stackBase().addOffsetTo(VM.getVM().getAddressSize());
  }

  /** Expression stack from top down */
  public Address addressOfInterpreterFrameTOSAt(int slot) {
    return addressOfInterpreterFrameTOS().addOffsetTo(slot * VM.getVM().getAddressSize());
  }

  public Address getInterpreterFrameSenderSP() {
    if (Assert.ASSERTS_ENABLED) {
      Assert.that(isInterpretedFrame(), "interpreted frame expected");
    }
    throw new RuntimeException("getInterpreterFrameSenderSP NYI");
  }

  // Monitors
  public BasicObjectLock interpreterFrameMonitorBegin() {
    if (iframe == null) {
      throw new RuntimeException("Not an Interpreter frame");
    }
    cInterpreter fr = new cInterpreter(iframe);
    return new BasicObjectLock(fr.monitorBase());
  }

  public BasicObjectLock interpreterFrameMonitorEnd() {
    if (iframe == null) {
      throw new RuntimeException("Not an Interpreter frame");
    }
    cInterpreter fr = new cInterpreter(iframe);
    // Monitors end is just above stack base (2 slots per monitor)
    Address result = fr.stackBase().addOffsetTo(2 * VM.getVM().getAddressSize());
    /*
    if (Assert.ASSERTS_ENABLED) {
      // make sure the pointer points inside the frame
      Assert.that(AddressOps.gt(getFP(), result), "result must <  than frame pointer");
      Assert.that(AddressOps.lte(getSP(), result), "result must >= than stack pointer");
    }
    */
    return new BasicObjectLock(result);
  }

  public int interpreterFrameMonitorSize() {
    return BasicObjectLock.size();
  }

  // Method
  public Address addressOfInterpreterFrameMethod() {
    if (iframe == null) {
      throw new RuntimeException("Not an Interpreter frame");
    }
    cInterpreter fr = new cInterpreter(iframe);
    return fr.methodAddr();
  }

  // Constant pool cache
  public Address addressOfInterpreterFrameCPCache() {
    if (iframe == null) {
      throw new RuntimeException("Not an Interpreter frame");
    }
    cInterpreter fr = new cInterpreter(iframe);
    return fr.constantsAddr();
  }

  // Entry frames
  public JavaCallWrapper getEntryFrameCallWrapper() {
    throw new RuntimeException("getEntryFrameCallWrapper NYI");
  }

  protected Address addressOfSavedOopResult() {
    throw new RuntimeException("public boolean isInterpretedFrame() NYI");
    /*
    // offset is 2 for compiler2 and 3 for compiler1
    return getSP().addOffsetTo((VM.getVM().isClientCompiler() ? 2 : 3) *
                               VM.getVM().getAddressSize());
    */
  }

  protected Address addressOfSavedReceiver() {
    throw new RuntimeException("getEntryFrameCallWrapper NYI");
    // return getSP().addOffsetTo(-4 * VM.getVM().getAddressSize());
  }

  private void dumpStack() {
    /*
    if (getFP() != null) {
      for (Address addr = getSP().addOffsetTo(-5 * VM.getVM().getAddressSize());
           AddressOps.lte(addr, getFP().addOffsetTo(5 * VM.getVM().getAddressSize()));
           addr = addr.addOffsetTo(VM.getVM().getAddressSize())) {
        System.out.println(addr + ": " + addr.getAddressAt(0));
      }
    } else {
      for (Address addr = getSP().addOffsetTo(-5 * VM.getVM().getAddressSize());
           AddressOps.lte(addr, getSP().addOffsetTo(20 * VM.getVM().getAddressSize()));
           addr = addr.addOffsetTo(VM.getVM().getAddressSize())) {
        System.out.println(addr + ": " + addr.getAddressAt(0));
      }
    }
    */
  }
}
