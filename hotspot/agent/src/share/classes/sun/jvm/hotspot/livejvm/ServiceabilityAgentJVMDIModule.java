/*
 * Copyright (c) 2002, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.livejvm;

import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.oops.*;
import sun.jvm.hotspot.runtime.*;

/** Provides Java programming language-level interaction with a live
    Java HotSpot VM via the use of the SA's JVMDI module. This is an
    experimental mechanism. The BugSpot debugger should be converted
    to use the JVMDI/JDWP-based JDI implementation for live process
    interaction once the JDI binding for the SA is complete. */

public class ServiceabilityAgentJVMDIModule {
  private Debugger dbg;
  private String[] saLibNames;
  private String   saLibName;
  private boolean  attached;

  private boolean  suspended;

  private static final int JVMDI_EVENT_BREAKPOINT = 2;
  private static final int JVMDI_EVENT_EXCEPTION = 4;

  private static long timeoutMillis = 3000;

  // Values in target process
  // Events sent from VM to SA
  private CIntegerAccessor saAttached;
  private CIntegerAccessor saEventPending;
  private CIntegerAccessor saEventKind;
  // Exception events
  private JNIHandleAccessor saExceptionThread;
  private JNIHandleAccessor saExceptionClass;
  private JNIid             saExceptionMethod;
  private CIntegerAccessor  saExceptionLocation;
  private JNIHandleAccessor saExceptionException;
  private JNIHandleAccessor saExceptionCatchClass;
  private JNIid             saExceptionCatchMethod;
  private CIntegerAccessor  saExceptionCatchLocation;
  // Breakpoint events
  private JNIHandleAccessor saBreakpointThread;
  private JNIHandleAccessor saBreakpointClass;
  private JNIid             saBreakpointMethod;
  private CIntegerAccessor  saBreakpointLocation;
  // Commands sent by the SA to the VM
  private int               SA_CMD_SUSPEND_ALL;
  private int               SA_CMD_RESUME_ALL;
  private int               SA_CMD_TOGGLE_BREAKPOINT;
  private int               SA_CMD_BUF_SIZE;
  private CIntegerAccessor  saCmdPending;
  private CIntegerAccessor  saCmdType;
  private CIntegerAccessor  saCmdResult;
  private CStringAccessor   saCmdResultErrMsg;
  // Toggle breakpoint command arguments
  private CStringAccessor   saCmdBkptSrcFileName;
  private CStringAccessor   saCmdBkptPkgName;
  private CIntegerAccessor  saCmdBkptLineNumber;
  private CIntegerAccessor  saCmdBkptResWasError;
  private CIntegerAccessor  saCmdBkptResLineNumber;
  private CIntegerAccessor  saCmdBkptResBCI;
  private CIntegerAccessor  saCmdBkptResWasSet;
  private CStringAccessor   saCmdBkptResMethodName;
  private CStringAccessor   saCmdBkptResMethodSig;

  public ServiceabilityAgentJVMDIModule(Debugger dbg, String[] saLibNames) {
    this.dbg = dbg;
    this.saLibNames = saLibNames;
  }

  /** Indicates whether a call to attach() should complete without an
      exception. */
  public boolean canAttach() {
    return setupLookup("SA_CMD_SUSPEND_ALL");
  }

  /** Attempt to initiate a connection with the JVMDI module in the
      target VM. */
  public void attach() throws DebuggerException {
    if (!canAttach()) {
      throw new DebuggerException("Unable to initiate symbol lookup in SA's JVMDI module");
    }

    if (attached) {
      throw new DebuggerException("Already attached");
    }

    // Attempt to look up well-known symbols in the target VM.
    SA_CMD_SUSPEND_ALL      = lookupConstInt("SA_CMD_SUSPEND_ALL");
    SA_CMD_RESUME_ALL       = lookupConstInt("SA_CMD_RESUME_ALL");
    SA_CMD_TOGGLE_BREAKPOINT = lookupConstInt("SA_CMD_TOGGLE_BREAKPOINT");
    SA_CMD_BUF_SIZE         = lookupConstInt("SA_CMD_BUF_SIZE");

    saAttached              = lookupCInt("saAttached");
    saEventPending          = lookupCInt("saEventPending");
    saEventKind             = lookupCInt("saEventKind");
    saCmdPending            = lookupCInt("saCmdPending");
    saCmdType               = lookupCInt("saCmdType");
    saCmdResult             = lookupCInt("saCmdResult");
    saCmdResultErrMsg       = lookupCString("saCmdResultErrMsg", SA_CMD_BUF_SIZE);
    // Toggling of breakpoints
    saCmdBkptSrcFileName    = lookupCString("saCmdBkptSrcFileName", SA_CMD_BUF_SIZE);
    saCmdBkptPkgName        = lookupCString("saCmdBkptPkgName", SA_CMD_BUF_SIZE);
    saCmdBkptLineNumber     = lookupCInt("saCmdBkptLineNumber");
    saCmdBkptResWasError    = lookupCInt("saCmdBkptResWasError");
    saCmdBkptResLineNumber  = lookupCInt("saCmdBkptResLineNumber");
    saCmdBkptResBCI         = lookupCInt("saCmdBkptResBCI");
    saCmdBkptResWasSet      = lookupCInt("saCmdBkptResWasSet");
    saCmdBkptResMethodName  = lookupCString("saCmdBkptResMethodName", SA_CMD_BUF_SIZE);
    saCmdBkptResMethodSig   = lookupCString("saCmdBkptResMethodSig", SA_CMD_BUF_SIZE);

    // Check for existence of symbols needed later
    // FIXME: should probably cache these since we can't support the
    // -Xrun module or the VM getting unloaded anyway
    lookup("saExceptionThread");
    lookup("saExceptionClass");
    lookup("saExceptionMethod");
    lookup("saExceptionLocation");
    lookup("saExceptionException");
    lookup("saExceptionCatchClass");
    lookup("saExceptionCatchMethod");
    lookup("saExceptionCatchLocation");
    lookup("saBreakpointThread");
    lookup("saBreakpointClass");
    lookup("saBreakpointMethod");
    lookup("saBreakpointLocation");

    saAttached.setValue(1);
    attached = true;
  }

  public void detach() {
    saAttached.setValue(0);
    attached = false;
    saLibName = null;
  }

  /** Set the timeout value (in milliseconds) for the VM to reply to
      commands. Once this timeout has elapsed, the VM is assumed to
      have disconnected. Defaults to 3000 milliseconds (3 seconds). */
  public void setCommandTimeout(long millis) {
    timeoutMillis = millis;
  }

  /** Get the timeout value (in milliseconds) for the VM to reply to
      commands. Once this timeout has elapsed, the VM is assumed to
      have disconnected. Defaults to 3000 milliseconds (3 seconds). */
  public long getCommandTimeout() {
    return timeoutMillis;
  }

  /** Indicates whether a Java debug event is pending */
  public boolean eventPending() {
    return (saEventPending.getValue() != 0);
  }

  /** Poll for event; returns null if none pending. */
  public Event eventPoll() {
    if (saEventPending.getValue() == 0) {
      return null;
    }

    int kind = (int) saEventKind.getValue();
    switch (kind) {
    case JVMDI_EVENT_EXCEPTION: {
      JNIHandleAccessor thread = lookupJNIHandle("saExceptionThread");
      JNIHandleAccessor clazz = lookupJNIHandle("saExceptionClass");
      JNIid method = lookupJNIid("saExceptionMethod");
      CIntegerAccessor location = lookupCInt("saExceptionLocation");
      JNIHandleAccessor exception = lookupJNIHandle("saExceptionException");
      JNIHandleAccessor catchClass = lookupJNIHandle("saExceptionCatchClass");
      JNIid catchMethod = lookupJNIid("saExceptionCatchMethod");
      CIntegerAccessor catchLocation = lookupCInt("saExceptionCatchLocation");
      return new ExceptionEvent(thread.getValue(), clazz.getValue(), method,
                                (int) location.getValue(), exception.getValue(),
                                catchClass.getValue(), catchMethod, (int) catchLocation.getValue());
    }

    case JVMDI_EVENT_BREAKPOINT: {
      JNIHandleAccessor thread = lookupJNIHandle("saBreakpointThread");
      JNIHandleAccessor clazz = lookupJNIHandle("saBreakpointClass");
      JNIid method = lookupJNIid("saBreakpointMethod");
      CIntegerAccessor location = lookupCInt("saBreakpointLocation");
      return new BreakpointEvent(thread.getValue(), clazz.getValue(),
                                 method, (int) location.getValue());
    }

    default:
      throw new DebuggerException("Unsupported event type " + kind);
    }
  }

  /** Continue past current event */
  public void eventContinue() {
    saEventPending.setValue(0);
  }

  /** Suspend all Java threads in the target VM. Throws
      DebuggerException if the VM disconnected. */
  public void suspend() {
    saCmdType.setValue(SA_CMD_SUSPEND_ALL);
    saCmdPending.setValue(1);
    waitForCommandCompletion();
    suspended = true;
  }

  /** Resume all Java threads in the target VM. Throws
      DebuggerException if the VM disconnected. */
  public void resume() {
    saCmdType.setValue(SA_CMD_RESUME_ALL);
    saCmdPending.setValue(1);
    waitForCommandCompletion();
    suspended = false;
  }

  /** Indicates whether all Java threads have been suspended via this
      interface. */
  public boolean isSuspended() {
    return suspended;
  }

  /** Information about toggling of breakpoints */
  public static class BreakpointToggleResult {
    private boolean success;
    private String errMsg;
    private int lineNumber;
    private int bci;
    private boolean wasSet;
    private String methodName;
    private String methodSig;

    /** Success constructor */
    public BreakpointToggleResult(int lineNumber, int bci, boolean wasSet,
                                  String methodName, String methodSig) {
      this.lineNumber = lineNumber;
      this.bci = bci;
      this.wasSet = wasSet;
      this.methodName = methodName;
      this.methodSig = methodSig;
      success = true;
    }

    /** Failure constructor */
    public BreakpointToggleResult(String errMsg) {
      this.errMsg = errMsg;
      success = false;
    }

    /** Indicates whether this represents a successful return or not */
    public boolean getSuccess() { return success; }

    /** Valid only if getSuccess() returns false */
    public String getErrMsg() { return errMsg; }

    /** Line number at which breakpoint toggle occurred; valid only if
        getSuccess() returns true. */
    public int getLineNumber() { return lineNumber; }

    /** BCI at which breakpoint toggle occurred; valid only if
        getSuccess() returns true. */
    public int getBCI() { return bci; }

    /** Indicates whether the breakpoint toggle was the set of a
        breakpoint or not; valid only if getSuccess() returns true. */
    public boolean getWasSet() { return wasSet; }

    /** Method name in which the breakpoint toggle occurred; valid
        only if getSuccess() returns true. */
    public String getMethodName() { return methodName; }

    /** Method signature in which the breakpoint toggle occurred;
        valid only if getSuccess() returns true. */
    public String getMethodSignature() { return methodSig; }
  }

  /** Toggle a breakpoint. Throws DebuggerException if a real error
      occurred; otherwise returns non-null BreakpointToggleResult. The
      work of scanning the loaded classes is done in the target VM
      because it turns out to be significantly faster than scanning
      through the system dictionary from the SA, and interactivity
      when setting breakpoints is important. */
  public BreakpointToggleResult toggleBreakpoint(String srcFileName,
                                                 String pkgName,
                                                 int lineNo) {
    saCmdBkptSrcFileName.setValue(srcFileName);
    saCmdBkptPkgName.setValue(pkgName);
    saCmdBkptLineNumber.setValue(lineNo);
    saCmdType.setValue(SA_CMD_TOGGLE_BREAKPOINT);
    saCmdPending.setValue(1);
    if (waitForCommandCompletion(true)) {
      return new BreakpointToggleResult((int) saCmdBkptResLineNumber.getValue(),
                                        (int) saCmdBkptResBCI.getValue(),
                                        (saCmdBkptResWasSet.getValue() != 0),
                                        saCmdBkptResMethodName.getValue(),
                                        saCmdBkptResMethodSig.getValue());
    } else {
      return new BreakpointToggleResult(saCmdResultErrMsg.getValue());
    }
  }


  //----------------------------------------------------------------------
  // Internals only below this point
  //

  private CIntegerAccessor lookupCInt(String symbolName) {
    return new CIntegerAccessor(lookup(symbolName), 4, false);
  }

  private CStringAccessor lookupCString(String symbolName, int bufLen) {
    return new CStringAccessor(lookup(symbolName), bufLen);
  }

  private JNIHandleAccessor lookupJNIHandle(String symbolName) {
    return new JNIHandleAccessor(lookup(symbolName), VM.getVM().getObjectHeap());
  }

  private JNIid lookupJNIid(String symbolName) {
    Address idAddr = lookup(symbolName).getAddressAt(0);
    if (idAddr == null) {
      return null;
    }
    return new JNIid(idAddr, VM.getVM().getObjectHeap());
  }

  private int lookupConstInt(String symbolName) {
    Address addr = lookup(symbolName);
    return (int) addr.getCIntegerAt(0, 4, false);
  }

  private boolean setupLookup(String symbolName) {
    if (saLibName == null) {
      for (int i = 0; i < saLibNames.length; i++) {
        Address addr = dbg.lookup(saLibNames[i], symbolName);
        if (addr != null) {
          saLibName = saLibNames[i];
          return true;
        }
      }
      return false;
    }
    return true;
  }

  private Address lookup(String symbolName) {
    if (saLibName == null) {
      for (int i = 0; i < saLibNames.length; i++) {
        Address addr = dbg.lookup(saLibNames[i], symbolName);
        if (addr != null) {
          saLibName = saLibNames[i];
          return addr;
        }
      }
      throw new DebuggerException("Unable to find symbol " + symbolName + " in any of the known names for the SA");
    }

    Address addr = dbg.lookup(saLibName, symbolName);
    if (addr == null) {
      throw new DebuggerException("Unable to find symbol " + symbolName + " in " + saLibName);
    }
    return addr;
  }

  private void waitForCommandCompletion() {
    waitForCommandCompletion(false);
  }

  /** Returns true if command succeeded, false if not */
  private boolean waitForCommandCompletion(boolean forBreakpoint) {
    long start = System.currentTimeMillis();
    long cur = start;
    while ((saCmdPending.getValue() != 0) &&
           (cur - start < timeoutMillis)) {
      try {
        java.lang.Thread.currentThread().sleep(10);
      } catch (InterruptedException e) {
      }
      cur = System.currentTimeMillis();
    }
    if (saCmdPending.getValue() != 0) {
      detach();
      throw new DebuggerException("VM appears to have died");
    }
    boolean succeeded = saCmdResult.getValue() == 0;
    if (!succeeded &&
        (!forBreakpoint || saCmdBkptResWasError.getValue() != 0)) {
      String err = saCmdResultErrMsg.getValue();
      throw new DebuggerException("Error executing JVMDI command: " + err);
    }
    return succeeded;
  }
}
