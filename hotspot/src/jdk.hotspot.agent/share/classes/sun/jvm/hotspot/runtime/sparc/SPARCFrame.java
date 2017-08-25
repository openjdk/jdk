/*
 * Copyright (c) 2000, 2012, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.runtime.sparc;

import sun.jvm.hotspot.asm.sparc.*;
import sun.jvm.hotspot.code.*;
import sun.jvm.hotspot.compiler.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.debugger.cdbg.*;
import sun.jvm.hotspot.oops.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.runtime.posix.*;
import sun.jvm.hotspot.utilities.*;

/** Specialization of and implementation of abstract methods of the
    Frame class for the SPARC CPU. (FIXME: this is as quick a port as
    possible to get things running; will have to do a better job right
    away.) */

public class SPARCFrame extends Frame {
  // The pc value is the raw return address, plus 8 (pcReturnOffset()).
  // the value of sp and youngerSP that is stored in this object
  // is always, always, always the value that would be found in the
  // register (or window save area) while the target VM was executing.
  // The caller of the constructor will alwasy know if has a biased or
  // unbiased version of the stack pointer and can convert real (unbiased)
  // value via a helper routine we supply.
  // Whenever we return sp or youngerSP values we do not return the internal
  // value but the real (unbiased) pointers since these are the true, usable
  // memory addresses. The outlier case is that of the null pointer. The current
  // mechanism makes null pointers always look null whether biased or not.
  // This seems to cause no problems. In theory null real pointers could be biased
  // just like other values however this has impact on things like addOffsetTo()
  // to be able to take an Address that represents null and add an offset to it.
  // This doesn't seem worth the bother and the impact on the rest of the code
  // when the biasSP and unbiasSP can make this invisible.
  //
  // The general rule in this code is that when we have a variable like FP, youngerSP, SP
  // that these are real (i.e. unbiased) addresses. The instance variables in a Frame are
  // always raw values. The other rule is that it except for the frame constructors and
  // the unBiasSP helper all methods accept parameters that are real addresses.
  //

  /** Optional next-younger SP (used to locate O7, the PC) */
  private Address raw_youngerSP;

  /** Intepreter adjusts the stack pointer to make all locals contiguous */
  private long    interpreterSPAdjustmentOffset;

  /** Number of stack entries for longs */
  private static final int WORDS_PER_LONG = 2;

  /** Normal SPARC return is 2 words past PC */
  public static final int PC_RETURN_OFFSET = 8;

  /** Size of each block, in order of increasing address */
  public static final int REGISTER_SAVE_WORDS   = 16;
  // FIXME: read these from the remote process
  //#ifdef _LP64
  //    callee_aggregate_return_pointer_words        =  0,
  //#else
  //    callee_aggregate_return_pointer_words        =  1,
  //#endif
  public static final int CALLEE_AGGREGATE_RETURN_POINTER_WORDS     = 1;
  public static final int CALLEE_REGISTER_ARGUMENT_SAVE_AREA_WORDS  = 6;

  // offset of each block, in order of increasing address:
  public static final int REGISTER_SAVE_WORDS_SP_OFFSET             = 0;
  public static final int CALLEE_AGGREGATE_RETURN_POINTER_SP_OFFSET = REGISTER_SAVE_WORDS_SP_OFFSET + REGISTER_SAVE_WORDS;
  public static final int CALLEE_REGISTER_ARGUMENT_SAVE_AREA_SP_OFFSET = (CALLEE_AGGREGATE_RETURN_POINTER_SP_OFFSET +
                                                                          CALLEE_AGGREGATE_RETURN_POINTER_WORDS);
  public static final int MEMORY_PARAMETER_WORD_SP_OFFSET              = (CALLEE_REGISTER_ARGUMENT_SAVE_AREA_SP_OFFSET +
                                                                          CALLEE_REGISTER_ARGUMENT_SAVE_AREA_WORDS);
  public static final int VARARGS_OFFSET                               = MEMORY_PARAMETER_WORD_SP_OFFSET;

  private static final boolean DEBUG = System.getProperty("sun.jvm.hotspot.runtime.sparc.SPARCFrame.DEBUG") != null;

  public static Address unBiasSP(Address raw_sp) {
    if (raw_sp != null) {
      return raw_sp.addOffsetTo(VM.getVM().getStackBias());
    } else {
      return null;
    }
  }

  public static Address biasSP(Address real_sp) {
    if (real_sp != null) {
      if (DEBUG) {
        System.out.println("biasing realsp: " + real_sp + " biased: " + real_sp.addOffsetTo(-VM.getVM().getStackBias()) );
      }
      return real_sp.addOffsetTo(-VM.getVM().getStackBias());
    } else {
      if (DEBUG) {
        System.out.println("biasing null realsp");
      }
      return null;
    }
  }
  //
  // This is used to find the younger sp for a thread thatn has stopped but hasn't
  // conveniently told us the information where we can find the pc or the frame
  // containing the pc that corresponds to last_java_sp. This method will walk
  // the frames trying to find the frame which we contains the data we need.
  //
  public static Address findYoungerSP(Address top, Address find) {
    // top and find are unBiased sp values
    // we return an unBiased value
    Address findRaw = biasSP(find);
    if (top == null || find == null || findRaw == null) {
      throw new RuntimeException("bad values for findYoungerSP top: " + top + " find: " + find);
    }
    // It would be unusual to find more than 20 native frames before we find the java frame
    // we are looking for.
    final int maxFrames = 20;
    int count = 0;
    Address search = top;
    Address next;
    Address pc;
    if (DEBUG) {
      System.out.println("findYoungerSP top: " + top + " find: " + find + " findRaw: " + findRaw);
    }
    while ( count != maxFrames && search != null) {
      next = search.getAddressAt(SPARCRegisters.I6.spOffsetInSavedWindow());
      pc = search.getAddressAt(SPARCRegisters.I7.spOffsetInSavedWindow());
      if (DEBUG) {
        System.out.println("findYoungerSP next: " + next + " pc: " + pc);
      }
      if (next.equals(findRaw)) {
        return search;
      }
      search = unBiasSP(next);
    }
    if (DEBUG) {
      System.out.println("findYoungerSP: never found younger, top: " + top + " find: " + find);
    }
    return null;
  }

  public Address getSP()              {
    if (DEBUG) {
      System.out.println("getSP raw: " + raw_sp + " unbiased: " + unBiasSP(raw_sp));
    }
    return  unBiasSP(raw_sp);
  }

  public Address getID()              {
    return getSP();
  }

  public Address getYoungerSP()       {
    if (DEBUG) {
      System.out.println("getYoungerSP: " + raw_youngerSP + " unbiased: " + unBiasSP(raw_youngerSP));
    }
    return unBiasSP(raw_youngerSP);
  }

  /** This constructor relies on the fact that the creator of a frame
      has flushed register windows which the frame will refer to, and
      that those register windows will not be reloaded until the frame
      is done reading and writing the stack.  Moreover, if the
      "younger_pc" argument points into the register save area of the
      next younger frame (though it need not), the register window for
      that next younger frame must also stay flushed.  (The caller is
      responsible for ensuring this.) */
  public SPARCFrame(Address raw_sp, Address raw_youngerSP, boolean youngerFrameIsInterpreted) {
    super();
    if (DEBUG) {
      System.out.println("Constructing frame(1) raw_sp: " + raw_sp + " raw_youngerSP: " + raw_youngerSP);
    }
    if (Assert.ASSERTS_ENABLED) {
      Assert.that((unBiasSP(raw_sp).andWithMask(VM.getVM().getAddressSize() - 1) == null),
                   "Expected raw sp likely got real sp, value was " + raw_sp);
      if (raw_youngerSP != null) {
        Assert.that((unBiasSP(raw_youngerSP).andWithMask(VM.getVM().getAddressSize() - 1) == null),
                    "Expected raw youngerSP likely got real youngerSP, value was " + raw_youngerSP);
      }
    }
    this.raw_sp = raw_sp;
    this.raw_youngerSP = raw_youngerSP;
    if (raw_youngerSP == null) {
      // make a deficient frame which doesn't know where its PC is
      pc = null;
    } else {
      Address youngerSP = unBiasSP(raw_youngerSP);
      pc = youngerSP.getAddressAt(SPARCRegisters.I7.spOffsetInSavedWindow()).addOffsetTo(PC_RETURN_OFFSET);

      if (Assert.ASSERTS_ENABLED) {
        Assert.that(youngerSP.getAddressAt(SPARCRegisters.FP.spOffsetInSavedWindow()).
                    equals(raw_sp),
                    "youngerSP must be valid");
      }
    }

    if (youngerFrameIsInterpreted) {
      long IsavedSP = SPARCRegisters.IsavedSP.spOffsetInSavedWindow();
      // compute adjustment to this frame's SP made by its interpreted callee
      interpreterSPAdjustmentOffset = 0;
      Address savedSP = unBiasSP(getYoungerSP().getAddressAt(IsavedSP));
      if (savedSP == null) {
        if ( DEBUG) {
          System.out.println("WARNING: IsavedSP was null for frame " + this);
        }
      } else {
        interpreterSPAdjustmentOffset = savedSP.minus(getSP());
      }
    } else {
      interpreterSPAdjustmentOffset = 0;
    }
    if ( pc != null) {
      // Look for a deopt pc and if it is deopted convert to original pc
      CodeBlob cb = VM.getVM().getCodeCache().findBlob(pc);
      if (cb != null && cb.isJavaMethod()) {
        NMethod nm = (NMethod) cb;
        if (pc.equals(nm.deoptHandlerBegin())) {
          // adjust pc if frame is deoptimized.
          pc = this.getUnextendedSP().getAddressAt(nm.origPCOffset());
          deoptimized = true;
        }
      }
    }
  }

  /** Make a deficient frame which doesn't know where its PC is (note
      no youngerSP argument) */
  public SPARCFrame(Address raw_sp, Address pc) {
    super();
    if (DEBUG) {
      System.out.println("Constructing frame(2) raw_sp: " + raw_sp );
    }
    this.raw_sp = raw_sp;
    if (Assert.ASSERTS_ENABLED) {
      Assert.that((unBiasSP(raw_sp).andWithMask(VM.getVM().getAddressSize() - 1) == null),
                   "Expected raw sp likely got real sp, value was " + raw_sp);
    }
    raw_youngerSP = null;
    this.pc = pc;
    interpreterSPAdjustmentOffset = 0;
  }

  /** Only used internally */
  private SPARCFrame() {
  }

  public Object clone() {
    SPARCFrame frame = new SPARCFrame();
    frame.raw_sp = raw_sp;
    frame.pc = pc;
    frame.raw_youngerSP = raw_youngerSP;
    frame.interpreterSPAdjustmentOffset = interpreterSPAdjustmentOffset;
    frame.deoptimized = deoptimized;
    return frame;
  }

  public boolean equals(Object arg) {
    if (arg == null) {
      return false;
    }

    if (!(arg instanceof SPARCFrame)) {
      return false;
    }

    SPARCFrame other = (SPARCFrame) arg;

    return (AddressOps.equal(getSP(), other.getSP()) &&
            AddressOps.equal(getFP(), other.getFP()) &&
            AddressOps.equal(getPC(), other.getPC()));
  }

  public int hashCode() {
    if (raw_sp == null) {
      return 0;
    }

    return raw_sp.hashCode();
  }

  public String toString() {
    Address fp = getFP();
    Address sp = getSP();
    Address youngerSP = getYoungerSP();

    return "sp: " + (sp == null? "null" : sp.toString()) +
         ", younger_sp: " + (youngerSP == null? "null" : youngerSP.toString()) +
         ", fp: " + (fp == null? "null" : fp.toString()) +
         ", pc: " + (pc == null? "null" : pc.toString());
  }

  /** <P> Identifies a signal handler frame on the stack. </P>

      <P> There are a few different algorithms for doing this, and
      they vary from platform to platform. For example, based on a
      conversation with Dave Dice, Solaris/x86 will be substantially
      simpler to handle than Solaris/SPARC because the signal handler
      frame can be identified because of a program counter == -1. </P>

      <P> The dbx group provided code and advice on these topics; the
      code below evolved from theirs, but is not correct/robust.
      Without going into too many details, it seems that looking for
      the incoming argument to the sigacthandler frame (which is what
      this code identifies) is not guaranteed to be stable across
      versions of Solaris, since that function is supplied by
      libthread and is not guaranteed not to clobber I2 before it
      calls __sighndlr later. From discussions, it sounds like a
      robust algorithm which wouldn't require traversal of the
      ucontext chain (used by dbx, but which Dave Dice thinks isn't
      robust in the face of libthread -- need to follow up) would be
      to be able to properly identify the __sighndlr frame, then get
      I2 and treat that as a ucontext. To identify __sighndlr we would
      need to look up that symbol in the remote process and look for a
      program counter within a certain (small) distance. </P>

      <P> If the underlying Debugger supports CDebugger interface, we
      take the approach of __sighnldr symbol. This approach is more robust
      compared to the original hueristic approach. Of course, if there
      is no CDebugger support, we fallback to the hueristic approach. </P>

      <P> The current implementation seems to work with Solaris 2.8.
      A nice property of this system is that if we find a core file
      this algorithm doesn't work on, we can change the code and try
      again, so I'm putting this in as the current mechanism for
      finding signal handler frames on Solaris/SPARC. </P> */
  public boolean isSignalHandlerFrameDbg() {
    CDebugger cdbg = VM.getVM().getDebugger().getCDebugger();
    if (cdbg != null) {
      LoadObject dso = cdbg.loadObjectContainingPC(getPC());
      if (dso != null) {
        ClosestSymbol cs = dso.closestSymbolToPC(getPC());
        if (cs != null && cs.getName().equals("__sighndlr")) {
          return true;
        } else {
          return false;
        }
      } else {
        return false;
      }
    } else {
      if (getYoungerSP() == null) {
        //      System.err.println("  SPARCFrame.isSignalHandlerFrameDbg: youngerSP = " + getYoungerSP());
        return false;
      }
      Address i2 = getSP().getAddressAt(SPARCRegisters.I2.spOffsetInSavedWindow());
      if (i2 == null) {
        return false;
      }
      Address fp = getFP();
      // My (mistaken) understanding of the dbx group's code was that
      // the signal handler frame could be identified by testing the
      // incoming argument to see whether it was a certain distance
      // below the frame pointer; in fact, their code did substantially
      // more than this (traversal of the ucontext chain, which this
      // code can't do because the topmost ucontext is not currently
      // available via the proc_service APIs in dbx). The current code
      // appears to work, but is probably not robust.
      int MAJOR_HACK_OFFSET = 8;  // Difference between expected location of the ucontext and reality
      // System.err.println("  SPARCFrame.isSignalHandlerFrameDbg: I2 = " + i2 +
      //                          ", fp = " + fp + ", raw_youngerSP = " + getYoungerSP());
      boolean res = i2.equals(fp.addOffsetTo(VM.getVM().getAddressSize() * (REGISTER_SAVE_WORDS + MAJOR_HACK_OFFSET)));
      if (res) {
        // Qualify this with another test (FIXME: this is a gross heuristic found while testing)
        Address sigInfoAddr = getSP().getAddressAt(SPARCRegisters.I5.spOffsetInSavedWindow());
        if (sigInfoAddr == null) {
          System.err.println("Frame with fp = " + fp + " looked like a signal handler frame but wasn't");
          res = false;
        }
      }
      return res;
    }
  }

  public int getSignalNumberDbg() {
    // From looking at the stack trace in dbx, it looks like the
    // siginfo* comes into sigacthandler in I5. It would be much more
    // robust to look at the __sighndlr frame instead, but we can't
    // currently identify that frame.

    Address sigInfoAddr = getSP().getAddressAt(SPARCRegisters.I5.spOffsetInSavedWindow());
    // Read si_signo out of siginfo*
    return (int) sigInfoAddr.getCIntegerAt(0, 4, false);
  }

  public String getSignalNameDbg() {
    return POSIXSignals.getSignalName(getSignalNumberDbg());
  }

  public boolean isInterpretedFrameValid() {
    if (Assert.ASSERTS_ENABLED) {
      Assert.that(isInterpretedFrame(), "Not an interpreted frame");
    }
    // These are reasonable sanity checks
    if (getFP() == null || (getFP().andWithMask(2 * VM.getVM().getAddressSize() - 1)) != null) {
      return false;
    }
    if (getSP() == null || (getSP().andWithMask(2 * VM.getVM().getAddressSize() - 1)) != null) {
      return false;
    }
    if (getFP().addOffsetTo(INTERPRETER_FRAME_VM_LOCAL_WORDS * VM.getVM().getAddressSize()).lessThan(getSP())) {
      return false;
    }

    Address methodHandle = addressOfInterpreterFrameMethod().getAddressAt(0);

    if (VM.getVM().getObjectHeap().isValidMethod(methodHandle) == false) {
      return false;
    }

    // These are hacks to keep us out of trouble.
    // The problem with these is that they mask other problems
    if (getFP().lessThanOrEqual(getSP())) {        // this attempts to deal with unsigned comparison above
      return false;
    }
    if (getFP().minus(getSP()) > 4096 * VM.getVM().getAddressSize()) {  // stack frames shouldn't be large.
      return false;
    }
    // FIXME: this is not atomic with respect to GC and is unsuitable
    // for use in a non-debugging, or reflective, system. Need to
    // figure out how to express this.
    Address bcx =  addressOfInterpreterFrameBCX().getAddressAt(0);

    Method method;
    try {
      method = (Method)Metadata.instantiateWrapperFor(methodHandle);
    } catch (UnknownOopException ex) {
       return false;
    }
    int  bci = bcpToBci(bcx, method);
    //validate bci
    if (bci < 0) return false;

    return true;
  }

  //--------------------------------------------------------------------------------
  // Accessors:
  //

  /** Accessors */

  public long frameSize() {
    return (getSenderSP().minus(getSP()) / VM.getVM().getAddressSize());
  }

  public Address getLink() {
    return unBiasSP(getFP().getAddressAt(SPARCRegisters.FP.spOffsetInSavedWindow()));
  }

  // FIXME: not implementable yet
  //  public void setLink(Address addr) {
  //    if (Assert.ASSERTS_ENABLED) {
  //      Assert.that(getLink().equals(addr), "frame nesting is controlled by hardware");
  //    }
  //  }

  public Frame sender(RegisterMap regMap, CodeBlob cb) {
    SPARCRegisterMap map = (SPARCRegisterMap) regMap;

    if (Assert.ASSERTS_ENABLED) {
      Assert.that(map != null, "map must be set");
    }

    // Default is we don't have to follow them. The sender_for_xxx
    // will update it accordingly
    map.setIncludeArgumentOops(false);

    if (isEntryFrame()) {
      return senderForEntryFrame(map);
    }

    Address youngerSP = getSP();
    Address sp        = getSenderSP();
    boolean isInterpreted = false;

    // FIXME: this is a hack to get stackwalking to work in the face
    // of a signal like a SEGV. For debugging purposes it's important
    // that (a) we are able to traverse the stack if we take a signal
    // and (b) that we get the correct program counter in this
    // situation. If we are not using alternate signal stacks then (a)
    // seems to work all the time (on SPARC), but (b) is violated for
    // the frame just below the signal handler.

    // The mechanism for finding the ucontext is not robust. In
    // addition, we may find that we need to be able to fetch more
    // registers from the ucontext than just the program counter,
    // since the register windows on the stack are "stale". This will
    // require substantial restructuring of this frame code, so has
    // been avoided for now.

    // It is difficult to find a clean solution for mixing debugging
    // situations with VM frame traversal. One could consider
    // implementing generic frame traversal in the dbx style and only
    // using the VM's stack walking mechanism on a per-frame basis,
    // for example to traverse Java-level activations in a compiled
    // frame. However, this will probably not interact well with the
    // mechanism for finding oops on the stack.

    if (VM.getVM().isDebugging()) {
      // If we are a signal handler frame, use a trick: make the
      // youngerSP of the caller frame point to the top of the
      // ucontext's contained register set. This should allow fetching
      // of the registers for the frame just below the signal handler
      // frame in the usual fashion.
      if (isSignalHandlerFrameDbg()) {

        if (DEBUG) {
          System.out.println("SPARCFrame.sender: found signal handler frame");
        }

        // Try to give a valid SP and PC for a "deficient frame" since
        // we don't have a real register save area; making this class
        // work by reading its information from a ucontext as well as
        // a register save area is a major undertaking and has been
        // deferred for now. It is very important that the PC is
        // correct, which is why we don't just fall through to the
        // other code (which would read the PC from the stale register
        // window and thereby fail to get the actual location of the
        // fault).

        long offset = getMContextAreaOffsetInUContext();
        Address fp = sp;
        // System.out.println("  FP: " + fp);
        fp = fp.addOffsetTo(getUContextOffset() + getMContextAreaOffsetInUContext());
        // System.out.println("  start of mcontext: " + fp);
        // FIXME: put these elsewhere. These are the register numbers
        // in /usr/include/sys/regset.h. They might belong in
        // SPARCReigsters.java, but we currently don't have that list
        // of numbers in the SA code (because all of the registers are
        // listed as instances of SPARCRegister) and it appears that
        // our numbering of the registers and this one don't match up.
        int PC_OFFSET_IN_GREGSET = 1;
        int SP_OFFSET_IN_GREGSET = 17;
        raw_sp = fp.getAddressAt(VM.getVM().getAddressSize() * SP_OFFSET_IN_GREGSET);
        Address pc = fp.getAddressAt(VM.getVM().getAddressSize() * PC_OFFSET_IN_GREGSET);
        return new SPARCFrame(raw_sp, pc);
      }
    }

    // Note:  The version of this operation on any platform with callee-save
    //        registers must update the register map (if not null).
    //        In order to do this correctly, the various subtypes of
    //        of frame (interpreted, compiled, glue, native),
    //        must be distinguished.  There is no need on SPARC for
    //        such distinctions, because all callee-save registers are
    //        preserved for all frames via SPARC-specific mechanisms.
    //
    //        *** HOWEVER, *** if and when we make any floating-point
    //        registers callee-saved, then we will have to copy over
    //        the RegisterMap update logic from the Intel code.

    // The constructor of the sender must know whether this frame is interpreted so it can set the
    // sender's _interpreter_sp_adjustment field.
    if (VM.getVM().getInterpreter().contains(pc)) {
      isInterpreted = true;
      map.makeIntegerRegsUnsaved();
      map.shiftWindow(sp, youngerSP);
    } else {
      // Find a CodeBlob containing this frame's pc or elide the lookup and use the
      // supplied blob which is already known to be associated with this frame.
      cb = VM.getVM().getCodeCache().findBlob(pc);
      if (cb != null) {
        // Update the location of all implicitly saved registers
        // as the address of these registers in the register save
        // area (for %o registers we use the address of the %i
        // register in the next younger frame)
        map.shiftWindow(sp, youngerSP);
        if (map.getUpdateMap()) {
          if (cb.callerMustGCArguments()) {
            map.setIncludeArgumentOops(true);
          }
          if (cb.getOopMaps() != null) {
            ImmutableOopMapSet.updateRegisterMap(this, cb, map, VM.getVM().isDebugging());
          }
        }
      }
    }

    return new SPARCFrame(biasSP(sp), biasSP(youngerSP), isInterpreted);
  }

  protected boolean hasSenderPD() {
    try {
      // FIXME: should not happen!!!
      if (getSP() == null) {
        return false;
      }
      if ( unBiasSP(getSP().getAddressAt(SPARCRegisters.FP.spOffsetInSavedWindow())) == null ) {
        return false;
      }
      return true;
    } catch (RuntimeException e) {
      if (DEBUG) {
        System.out.println("Bad frame " + this);
      }
      throw e;
    }
  }

  //--------------------------------------------------------------------------------
  // Return address:
  //

  public Address getSenderPC() {
    return addressOfI7().getAddressAt(0).addOffsetTo(PC_RETURN_OFFSET);
  }

  // FIXME: currently unimplementable
  // inline void     frame::set_sender_pc(address addr) { *I7_addr() = addr - pc_return_offset; }

  public Address getUnextendedSP() {
    return getSP().addOffsetTo(interpreterSPAdjustmentOffset);
  }

  public Address getSenderSP() {
    return getFP();
  }

  /** Given the next-younger sp for a given frame's sp, compute the
      frame. We need the next-younger sp, because its register save
      area holds the flushed copy of its I7, which is the PC of the
      frame we are interested in. */
  public SPARCFrame afterSave() {
    return new SPARCFrame(biasSP(getYoungerSP()), null);
  }

  /** Accessors for the instance variables */
  public Address getFP() {
    Address sp = getSP();
    if (sp == null) {
      System.out.println("SPARCFrame.getFP(): sp == null");
    }
    Address fpAddr = sp.addOffsetTo(SPARCRegisters.FP.spOffsetInSavedWindow());
    try {
      Address fp = unBiasSP(fpAddr.getAddressAt(0));
      if (fp == null) {
        System.out.println("SPARCFrame.getFP(): fp == null (&fp == " + fpAddr + ")");
      }
      return fp;
    } catch (RuntimeException e) {
      System.out.println("SPARCFrame.getFP(): is bad (&fp == " + fpAddr + " sp = " + sp + ")");
      return null;
    }
  }

  private Address addressOfFPSlot(int index) {
    return getFP().addOffsetTo(index * VM.getVM().getAddressSize());
  }

  // FIXME: temporarily elided
  //  // All frames
  //
  //  intptr_t*  fp_addr_at(int index) const   { return &fp()[index];    }
  //  intptr_t*  sp_addr_at(int index) const   { return &sp()[index];    }
  //  intptr_t    fp_at(     int index) const   { return *fp_addr_at(index); }
  //  intptr_t    sp_at(     int index) const   { return *sp_addr_at(index); }
  //
  // private:
  //  inline address* I7_addr() const;
  //  inline address* O7_addr() const;
  //
  //  inline address* I0_addr() const;
  //  inline address* O0_addr() const;
  //
  // public:
  //  // access to SPARC arguments and argument registers
  //
  //  intptr_t*     register_addr(Register reg) const {
  //    return sp_addr_at(reg.sp_offset_in_saved_window());
  //  }
  //  intptr_t* memory_param_addr(int param_ix, bool is_in) const {
  //    int offset = callee_register_argument_save_area_sp_offset + param_ix;
  //    if (is_in)
  //      return fp_addr_at(offset);
  //    else
  //      return sp_addr_at(offset);
  //  }
  //  intptr_t*        param_addr(int param_ix, bool is_in) const {
  //    if (param_ix >= callee_register_argument_save_area_words)
  //      return memory_param_addr(param_ix, is_in);
  //    else if (is_in)
  //      return register_addr(Argument(param_ix, true).as_register());
  //    else {
  //      // the registers are stored in the next younger frame
  //      // %%% is this really necessary?
  //      frame next_younger = after_save();
  //      return next_younger.register_addr(Argument(param_ix, true).as_register());
  //    }
  //  }

  //--------------------------------------------------------------------------------
  // Interpreter frames:
  //

  /** 2 words, also used to save float regs across  calls to C */
  public static final int INTERPRETER_FRAME_D_SCRATCH_FP_OFFSET           = -2;
  public static final int INTERPRETER_FRAME_L_SCRATCH_FP_OFFSET           = -4;
  public static final int INTERPRETER_FRAME_MIRROR_OFFSET                 = -5;
  public static final int INTERPRETER_FRAME_VM_LOCALS_FP_OFFSET           = -6;
  public static final int INTERPRETER_FRAME_VM_LOCAL_WORDS                = -INTERPRETER_FRAME_VM_LOCALS_FP_OFFSET;

  /** Interpreter frame set-up needs to save 2 extra words in outgoing
      param area for class and jnienv arguments for native stubs (see
      nativeStubGen_sparc.cpp) */
  public static final int INTERPRETER_FRAME_EXTRA_OUTGOING_ARGUMENT_WORDS = 2;

  // FIXME: elided for now
  //
  //  // the compiler frame has many of the same fields as the interpreter frame
  //  // %%%%% factor out declarations of the shared fields
  //  enum compiler_frame_fixed_locals {
  //       compiler_frame_d_scratch_fp_offset          = -2,
  //       compiler_frame_vm_locals_fp_offset          = -2, // should be same as above
  //
  //       compiler_frame_vm_local_words = -compiler_frame_vm_locals_fp_offset
  //  };
  //
  // private:
  //
  //  // where LcpoolCache is saved:
  //  ConstantPoolCache** interpreter_frame_cpoolcache_addr() const {
  //    return (ConstantPoolCache**)sp_addr_at( LcpoolCache.sp_offset_in_saved_window());
  //  }
  //
  //  // where Lmonitors is saved:
  //  BasicObjectLock**  interpreter_frame_monitors_addr() const {
  //    return (BasicObjectLock**) sp_addr_at( Lmonitors.sp_offset_in_saved_window());
  //  }
  //  intptr_t** interpreter_frame_esp_addr() const {
  //    return (intptr_t**)sp_addr_at( Lesp.sp_offset_in_saved_window());
  //  }
  //
  //  inline void interpreter_frame_set_tos_address(intptr_t* x);
  //
  //  // next two fns read and write Lmonitors value,
  // private:
  //  BasicObjectLock* interpreter_frame_monitors()           const  { return *interpreter_frame_monitors_addr(); }
  //  void interpreter_frame_set_monitors(BasicObjectLock* monitors) {        *interpreter_frame_monitors_addr() = monitors; }
  //
  //#ifndef CORE
  //inline oop *frame::pd_compiled_argument_to_location(VMReg::Name reg, RegisterMap reg_map, int arg_size) const {
  //  COMPILER1_ONLY(return (oop *) (arg_size - 1 - reg + sp() + memory_parameter_word_sp_offset);   )
  //  COMPILER2_ONLY(return oopmapreg_to_location(reg, &reg_map); )
  //}
  //#endif

  // FIXME: NOT FINISHED
  public Address addressOfInterpreterFrameLocals() {
    return getSP().addOffsetTo(SPARCRegisters.Llocals.spOffsetInSavedWindow());
  }

  // FIXME: this is not atomic with respect to GC and is unsuitable
  // for use in a non-debugging, or reflective, system.
  private Address addressOfInterpreterFrameBCX() {
    // %%%%% reinterpreting Lbcp as a bcx
    return getSP().addOffsetTo(SPARCRegisters.Lbcp.spOffsetInSavedWindow());
  }

  public int getInterpreterFrameBCI() {
    // FIXME: this is not atomic with respect to GC and is unsuitable
    // for use in a non-debugging, or reflective, system. Need to
    // figure out how to express this.
    Address bcp = addressOfInterpreterFrameBCX().getAddressAt(0);
    Address methodHandle = addressOfInterpreterFrameMethod().getAddressAt(0);
    Method method = (Method)Metadata.instantiateWrapperFor(methodHandle);
    return bcpToBci(bcp, method);
  }

  public Address addressOfInterpreterFrameExpressionStack() {
    return addressOfInterpreterFrameMonitors().addOffsetTo(-1 * VM.getVM().getAddressSize());
  }

  public int getInterpreterFrameExpressionStackDirection() {
    return -1;
  }

  /** Top of expression stack */
  public Address addressOfInterpreterFrameTOS() {
    return getSP().getAddressAt(SPARCRegisters.Lesp.spOffsetInSavedWindow()).addOffsetTo(VM.getVM().getAddressSize());
  }

  /** Expression stack from top down */
  public Address addressOfInterpreterFrameTOSAt(int slot) {
    return addressOfInterpreterFrameTOS().addOffsetTo(slot * VM.getVM().getAddressSize());
  }

  public Address getInterpreterFrameSenderSP() {
    if (Assert.ASSERTS_ENABLED) {
      Assert.that(isInterpretedFrame(), "interpreted frame expected");
    }
    return getFP();
  }

  // FIXME: elided for now
  //inline void frame::interpreter_frame_set_tos_address( intptr_t* x ) {
  //  *interpreter_frame_esp_addr() = x - 1;
  //}

  //--------------------------------------------------------------------------------
  // Monitors:
  //

  private Address addressOfInterpreterFrameMonitors() {
    return getSP().addOffsetTo(SPARCRegisters.Lmonitors.spOffsetInSavedWindow()).getAddressAt(0);
  }

  // Monitors
  public BasicObjectLock interpreterFrameMonitorBegin() {
    int roundedVMLocalWords = Bits.roundTo(INTERPRETER_FRAME_VM_LOCAL_WORDS, WORDS_PER_LONG);
    return new BasicObjectLock(addressOfFPSlot(-1 * roundedVMLocalWords));
  }

  public BasicObjectLock interpreterFrameMonitorEnd() {
    return new BasicObjectLock(addressOfInterpreterFrameMonitors());
  }

  public int interpreterFrameMonitorSize() {
    return Bits.roundTo(BasicObjectLock.size(), WORDS_PER_LONG * (int) VM.getVM().getAddressSize());
  }

  // FIXME: elided for now
  // // monitor elements
  //
  // // in keeping with Intel side: end is lower in memory than begin;
  // // and beginning element is oldest element
  // // Also begin is one past last monitor.
  //
  // inline BasicObjectLock* frame::interpreter_frame_monitor_begin()       const  {
  //   int rounded_vm_local_words = align_up(frame::interpreter_frame_vm_local_words, WordsPerLong);
  //   return (BasicObjectLock *)fp_addr_at(-rounded_vm_local_words);
  // }
  //
  // inline BasicObjectLock* frame::interpreter_frame_monitor_end()         const  {
  //   return interpreter_frame_monitors();
  // }
  //
  //
  // inline void frame::interpreter_frame_set_monitor_end(BasicObjectLock* value) {
  //   interpreter_frame_set_monitors(value);
  // }
  //
  //
  // inline int frame::interpreter_frame_monitor_size() {
  //   return align_up(BasicObjectLock::size(), WordsPerLong);
  // }

  public Address addressOfInterpreterFrameMethod() {
    return getSP().addOffsetTo(SPARCRegisters.Lmethod.spOffsetInSavedWindow());
  }

  public Address addressOfInterpreterFrameCPCache() {
    return getSP().addOffsetTo(SPARCRegisters.LcpoolCache.spOffsetInSavedWindow());
  }

  //--------------------------------------------------------------------------------
  // Entry frames:
  //

  public JavaCallWrapper getEntryFrameCallWrapper() {
    // Note: adjust this code if the link argument in StubGenerator::call_stub() changes!
    SPARCArgument link = new SPARCArgument(0, false);
    return (JavaCallWrapper) VMObjectFactory.newObject(JavaCallWrapper.class,
                                                       getSP().getAddressAt(link.asIn().asRegister().spOffsetInSavedWindow()));
  }

  //
  //
  // inline JavaCallWrapper* frame::entry_frame_call_wrapper() const {
  //   // note: adjust this code if the link argument in StubGenerator::call_stub() changes!
  //   const Argument link = Argument(0, false);
  //   return (JavaCallWrapper*)sp()[link.as_in().as_register().sp_offset_in_saved_window()];
  // }

  //--------------------------------------------------------------------------------
  // Safepoints:
  //

  protected Address addressOfSavedOopResult() {
    return addressOfO0();
  }

  protected Address addressOfSavedReceiver() {
    return addressOfO0();
  }


  //--------------------------------------------------------------------------------
  // Internals only below this point
  //

  private Address addressOfI7() {
    return getSP().addOffsetTo(SPARCRegisters.I7.spOffsetInSavedWindow());
  }

  private Address addressOfO7() {
    return afterSave().addressOfI7();
  }

  private Address addressOfI0() {
    return getSP().addOffsetTo(SPARCRegisters.I0.spOffsetInSavedWindow());
  }

  private Address addressOfO0() {
    return afterSave().addressOfI0();
  }

  private static boolean addressesEqual(Address a1, Address a2) {
    if ((a1 == null) && (a2 == null)) {
      return true;
    }

    if ((a1 == null) || (a2 == null)) {
      return false;
    }

    return (a1.equals(a2));
  }


  private Frame senderForEntryFrame(RegisterMap regMap) {
    SPARCRegisterMap map = (SPARCRegisterMap) regMap;

    if (Assert.ASSERTS_ENABLED) {
      Assert.that(map != null, "map must be set");
    }
    // Java frame called from C; skip all C frames and return top C
    // frame of that chunk as the sender
    JavaCallWrapper jcw = getEntryFrameCallWrapper();
    if (Assert.ASSERTS_ENABLED) {
      Assert.that(!entryFrameIsFirst(), "next Java fp must be non zero");
      Assert.that(jcw.getLastJavaSP().greaterThan(getSP()), "must be above this frame on stack");
    }
    Address lastJavaSP = jcw.getLastJavaSP();
    Address lastJavaPC = jcw.getLastJavaPC();
    map.clear();

    map.makeIntegerRegsUnsaved();
    map.shiftWindow(lastJavaSP, null);

    if (Assert.ASSERTS_ENABLED) {
      Assert.that(map.getIncludeArgumentOops(), "should be set by clear");
    }

    if (lastJavaPC != null) {
      return new SPARCFrame(biasSP(lastJavaSP), lastJavaPC);
    } else {
      Address youngerSP  = getNextYoungerSP(lastJavaSP, getSP());
      return new SPARCFrame(biasSP(lastJavaSP), biasSP(youngerSP), false);
    }
  }

  private static Address getNextYoungerSP(Address oldSP, Address youngSP) {
    Address sp = getNextYoungerSPOrNull(oldSP, youngSP, null);
    if (Assert.ASSERTS_ENABLED) {
      Assert.that(sp != null, "missed the SP");
    }
    return sp;
  }

  private static Address getNextYoungerSPOrNull(Address oldSP, Address youngSP, Address sp) {
    if (youngSP == null) {
      // FIXME
      throw new RuntimeException("can not handle null youngSP in debugging system (seems to require register window flush)");
    }

    if (sp == null) {
      sp = youngSP;
    }

    Address previousSP = null;

    /** Minimum frame size is 16 */
    int maxFrames = (int) (oldSP.minus(sp) / (16 * VM.getVM().getAddressSize()));

    while(!sp.equals(oldSP) && spIsValid(oldSP, youngSP, sp)) {
      if (maxFrames-- <= 0) {
        // too many frames have gone by; invalid parameters given to this function
        break;
      }
      previousSP = sp;
      sp = unBiasSP(sp.getAddressAt(SPARCRegisters.FP.spOffsetInSavedWindow()));
    }

    return (sp.equals(oldSP) ? previousSP : null);
  }

  private static boolean spIsValid(Address oldSP, Address youngSP, Address sp) {
    long mask = VM.getVM().getAddressSize();
    mask = 2 * mask - 1;
    return ((sp.andWithMask(mask) == null) &&
            (sp.lessThanOrEqual(oldSP)) &&
            (sp.greaterThanOrEqual(youngSP)));
  }

  // FIXME: this is a hopefully temporary hack (not sure what is going on)
  public long getUContextOffset() {
    // FIXME: there is something I clearly don't understand about the
    // way the signal handler frame is laid out, because I shouldn't need this extra offset
    int MAJOR_HACK_OFFSET = 8;
    //    System.out.println("  SPARCFrame.isSignalHandlerFrameDbg: I2 = " + i2 + ", fp = " + fp + ", youngerSP = " + youngerSP);
    return VM.getVM().getAddressSize() * (REGISTER_SAVE_WORDS + MAJOR_HACK_OFFSET);
  }

  public long getMContextAreaOffsetInUContext() {
    // From dbx-related sources:
    // /*
    //  * struct sigframe is declaredf in the kernel sources in
    //  * .../uts/sun4c/os/machdep.c/sendsig()
    //  * unfortunately we only get a pointer to the 'uc' passed
    //  * to the sighandler so we need to do this stuff to get
    //  * to 'rwin'.
    //  * Have to do it like this to take account of alignment.
    //  */
    // static struct sigframe {
    //     struct rwindow rwin;
    //     ucontext_t uc;
    // } sf_help;

    // From /usr/include/sys/ucontext.h:
    // #if !defined(_XPG4_2) || defined(__EXTENSIONS__)
    // struct   ucontext {
    // #else
    // struct   __ucontext {
    // #endif
    //  uint_t          uc_flags;
    //  ucontext_t      *uc_link;
    //  sigset_t        uc_sigmask;
    //  stack_t         uc_stack;
    //  mcontext_t      uc_mcontext;
    // #ifdef   __sparcv9
    //  long            uc_filler[4];
    // #else    /* __sparcv9 */
    //  long            uc_filler[23];
    // #endif   /* __sparcv9 */
    // };

    // This walks to the start of the gregs in the mcontext_t
    // (first entry in that data structure). Really should read
    // this from header file.

    // Also not sure exactly how alignment works...maybe should read these offsets from the target VM
    // (When you have a hammer, everything looks like a nail)
    long offset = VM.getVM().alignUp(4, VM.getVM().getAddressSize());   // uc_flags
    offset      = VM.getVM().alignUp(offset + VM.getVM().getAddressSize(), 8); // uc_link plus
                                                                        // doubleword alignment for structs?
    offset     += 16 +                                                  // uc_sigmask
                   2 * VM.getVM().getAddressSize() + 4;                 // uc_stack
    offset      = VM.getVM().alignUp(offset + VM.getVM().getAddressSize(), 8); // doubleword alignment for structs?

    //    System.out.println("SPARCFrame.getMContextAreaOffsetInUContext: offset = " + offset);

    return offset;
  }
}
