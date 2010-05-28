/*
 * Copyright (c) 2000, 2008, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.net.*;
import java.util.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.debugger.x86.*;
import sun.jvm.hotspot.debugger.win32.coff.*;
import sun.jvm.hotspot.debugger.cdbg.*;
import sun.jvm.hotspot.debugger.cdbg.basic.BasicDebugEvent;
import sun.jvm.hotspot.utilities.*;
import sun.jvm.hotspot.utilities.memo.*;

/** <P> An implementation of the JVMDebugger interface which talks to
    the Free Windows Debug Server (FwDbgSrv) over a socket to
    implement attach/detach and read from process memory. All DLL and
    symbol table management is done in Java. </P>

    <P> <B>NOTE</B> that since we have the notion of fetching "Java
    primitive types" from the remote process (which might have
    different sizes than we expect) we have a bootstrapping
    problem. We need to know the sizes of these types before we can
    fetch them. The current implementation solves this problem by
    requiring that it be configured with these type sizes before they
    can be fetched. The readJ(Type) routines here will throw a
    RuntimeException if they are called before the debugger is
    configured with the Java primitive type sizes. </P> */

public class Win32DebuggerLocal extends DebuggerBase implements Win32Debugger {
  private Socket debuggerSocket;
  private boolean attached;
  // FIXME: update when core files supported
  private long pid;
  // Communication with debug server
  private PrintWriter out;
  private DataOutputStream rawOut;
  private InputLexer in;
  private static final int PORT = 27000;
  private PageCache cache;
  private static final long SHORT_TIMEOUT = 2000;
  private static final long LONG_TIMEOUT = 20000;

  // Symbol lookup support
  // This is a map of library names to DLLs
  private Map nameToDllMap;

  // C/C++ debugging support
  private List/*<LoadObject>*/ loadObjects;
  private CDebugger cdbg;

  // ProcessControl support
  private boolean suspended;
  // Maps Long objects (addresses) to Byte objects (original instructions)
  // (Longs used instead of Addresses to properly represent breakpoints at 0x0 if needed)
  private Map     breakpoints;
  // Current debug event, if any
  private DebugEvent curDebugEvent;

  //--------------------------------------------------------------------------------
  // Implementation of Debugger interface
  //

  /** <P> machDesc may not be null. </P>

      <P> useCache should be set to true if debugging is being done
      locally, and to false if the debugger is being created for the
      purpose of supporting remote debugging. </P> */
  public Win32DebuggerLocal(MachineDescription machDesc,
                            boolean useCache) throws DebuggerException {
    this.machDesc = machDesc;
    utils = new DebuggerUtilities(machDesc.getAddressSize(), machDesc.isBigEndian());
    if (useCache) {
      // Cache portion of the remote process's address space.
      // Fetching data over the socket connection to dbx is slow.
      // Might be faster if we were using a binary protocol to talk to
      // dbx, but would have to test. For now, this cache works best
      // if it covers the entire heap of the remote process. FIXME: at
      // least should make this tunable from the outside, i.e., via
      // the UI. This is a cache of 4096 4K pages, or 16 MB. The page
      // size must be adjusted to be the hardware's page size.
      // (FIXME: should pick this up from the debugger.)
      initCache(4096, parseCacheNumPagesProperty(4096));
    }
    // FIXME: add instantiation of thread factory

    try {
      connectToDebugServer();
    } catch (IOException e) {
      throw new DebuggerException(e);
    }
  }

  /** From the Debugger interface via JVMDebugger */
  public boolean hasProcessList() throws DebuggerException {
    return true;
  }

  /** From the Debugger interface via JVMDebugger */
  public List getProcessList() throws DebuggerException {
    List processes = new ArrayList();

    try {
      printlnToOutput("proclist");
      int num = in.parseInt();
      for (int i = 0; i < num; i++) {
        int pid = in.parseInt();
        String name = parseString();
        // NOTE: Win32 hack
        if (name.equals("")) {
          name = "System Idle Process";
        }
        processes.add(new ProcessInfo(name, pid));
      }
      return processes;
    }
    catch (IOException e) {
      throw new DebuggerException(e);
    }
  }

  /** From the Debugger interface via JVMDebugger */
  public synchronized void attach(int processID) throws DebuggerException {
    if (attached) {
      // FIXME: update when core files supported
      throw new DebuggerException("Already attached to process " + pid);
    }

    try {
      printlnToOutput("attach " + processID);
      if (!in.parseBoolean()) {
        throw new DebuggerException("Error attaching to process, or no such process");
      }

      attached = true;
      pid = processID;
      suspended = true;
      breakpoints = new HashMap();
      curDebugEvent = null;
      nameToDllMap = null;
      loadObjects = null;
    }
    catch (IOException e) {
        throw new DebuggerException(e);
    }
  }

  /** From the Debugger interface via JVMDebugger */
  public synchronized void attach(String executableName, String coreFileName) throws DebuggerException {
    throw new DebuggerException("Core files not yet supported on Win32");
  }

  /** From the Debugger interface via JVMDebugger */
  public synchronized boolean detach() {
    if (!attached) {
      return false;
    }

    attached = false;
    suspended = false;
    breakpoints = null;

    // Close all open DLLs
    if (nameToDllMap != null) {
      for (Iterator iter = nameToDllMap.values().iterator(); iter.hasNext(); ) {
        DLL dll = (DLL) iter.next();
        dll.close();
      }
      nameToDllMap = null;
      loadObjects = null;
    }

    cdbg = null;
    clearCache();

    try {
      printlnToOutput("detach");
      return in.parseBoolean();
    }
    catch (IOException e) {
      throw new DebuggerException(e);
    }
  }

  /** From the Debugger interface via JVMDebugger */
  public Address parseAddress(String addressString) throws NumberFormatException {
    return newAddress(utils.scanAddress(addressString));
  }

  /** From the Debugger interface via JVMDebugger */
  public String getOS() {
    return PlatformInfo.getOS();
  }

  /** From the Debugger interface via JVMDebugger */
  public String getCPU() {
    return PlatformInfo.getCPU();
  }

  public boolean hasConsole() throws DebuggerException {
    return false;
  }

  public String consoleExecuteCommand(String cmd) throws DebuggerException {
    throw new DebuggerException("No debugger console available on Win32");
  }

  public String getConsolePrompt() throws DebuggerException {
    return null;
  }

  public CDebugger getCDebugger() throws DebuggerException {
    if (cdbg == null) {
      cdbg = new Win32CDebugger(this);
    }
    return cdbg;
  }

  /** From the SymbolLookup interface via Debugger and JVMDebugger */
  public synchronized Address lookup(String objectName, String symbol) {
    if (!attached) {
      return null;
    }
    return newAddress(lookupInProcess(objectName, symbol));
  }

  /** From the SymbolLookup interface via Debugger and JVMDebugger */
  public synchronized OopHandle lookupOop(String objectName, String symbol) {
    Address addr = lookup(objectName, symbol);
    if (addr == null) {
      return null;
    }
    return addr.addOffsetToAsOopHandle(0);
  }

  /** From the Debugger interface */
  public MachineDescription getMachineDescription() {
    return machDesc;
  }

  //--------------------------------------------------------------------------------
  // Implementation of ThreadAccess interface
  //

  /** From the ThreadAccess interface via Debugger and JVMDebugger */
  public ThreadProxy getThreadForIdentifierAddress(Address addr) {
    return new Win32Thread(this, addr);
  }

  public ThreadProxy getThreadForThreadId(long handle) {
    return new Win32Thread(this, handle);
  }

  //----------------------------------------------------------------------
  // Overridden from DebuggerBase because we need to relax alignment
  // constraints on x86

  public long readJLong(long address)
    throws UnmappedAddressException, UnalignedAddressException {
    checkJavaConfigured();
    // FIXME: allow this to be configurable. Undesirable to add a
    // dependency on the runtime package here, though, since this
    // package should be strictly underneath it.
    //    utils.checkAlignment(address, jlongSize);
    utils.checkAlignment(address, jintSize);
    byte[] data = readBytes(address, jlongSize);
    return utils.dataToJLong(data, jlongSize);
  }

  //--------------------------------------------------------------------------------
  // Internal routines (for implementation of Win32Address).
  // These must not be called until the MachineDescription has been set up.
  //

  /** From the Win32Debugger interface */
  public String addressValueToString(long address) {
    return utils.addressValueToString(address);
  }

  /** From the Win32Debugger interface */
  public Win32Address readAddress(long address)
    throws UnmappedAddressException, UnalignedAddressException {
    return (Win32Address) newAddress(readAddressValue(address));
  }

  public Win32Address readCompOopAddress(long address)
    throws UnmappedAddressException, UnalignedAddressException {
    return (Win32Address) newAddress(readCompOopAddressValue(address));
  }

  /** From the Win32Debugger interface */
  public Win32OopHandle readOopHandle(long address)
    throws UnmappedAddressException, UnalignedAddressException, NotInHeapException {
    long value = readAddressValue(address);
    return (value == 0 ? null : new Win32OopHandle(this, value));
  }
  public Win32OopHandle readCompOopHandle(long address)
    throws UnmappedAddressException, UnalignedAddressException, NotInHeapException {
    long value = readCompOopAddressValue(address);
    return (value == 0 ? null : new Win32OopHandle(this, value));
  }

  /** From the Win32Debugger interface */
  public void writeAddress(long address, Win32Address value) {
    writeAddressValue(address, getAddressValue(value));
  }

  /** From the Win32Debugger interface */
  public void writeOopHandle(long address, Win32OopHandle value) {
    writeAddressValue(address, getAddressValue(value));
  }

  //--------------------------------------------------------------------------------
  // Thread context access
  //

  public synchronized long[] getThreadIntegerRegisterSet(int threadHandleValue,
                                                         boolean mustDuplicateHandle)
    throws DebuggerException {
    if (!suspended) {
      throw new DebuggerException("Process not suspended");
    }

    try {
      int handle = threadHandleValue;
      if (mustDuplicateHandle) {
        printlnToOutput("duphandle 0x" + Integer.toHexString(threadHandleValue));
        if (!in.parseBoolean()) {
          throw new DebuggerException("Error duplicating thread handle 0x" + threadHandleValue);
        }
        handle = (int) in.parseAddress(); // Must close to avoid leaks
      }
      printlnToOutput("getcontext 0x" + Integer.toHexString(handle));
      if (!in.parseBoolean()) {
        if (mustDuplicateHandle) {
          printlnToOutput("closehandle 0x" + Integer.toHexString(handle));
        }
        String failMessage = "GetThreadContext failed for thread handle 0x" +
                             Integer.toHexString(handle);
        if (mustDuplicateHandle) {
          failMessage = failMessage + ", duplicated from thread handle " +
                        Integer.toHexString(threadHandleValue);
        }
        throw new DebuggerException(failMessage);
      }
      // Otherwise, parse all registers. See
      // src/os/win32/agent/README-commands.txt for the format.
      // Note the array we have to return has to match that specified by
      // X86ThreadContext.java.
      int numRegs = 22;
      long[] winRegs = new long[numRegs];
      for (int i = 0; i < numRegs; i++) {
        winRegs[i] = in.parseAddress();
      }
      if (mustDuplicateHandle) {
        // Clean up after ourselves
        printlnToOutput("closehandle 0x" + Integer.toHexString(handle));
      }
      // Now create the real return value
      long[] retval = new long[X86ThreadContext.NPRGREG];
      retval[X86ThreadContext.EAX] = winRegs[0];
      retval[X86ThreadContext.EBX] = winRegs[1];
      retval[X86ThreadContext.ECX] = winRegs[2];
      retval[X86ThreadContext.EDX] = winRegs[3];
      retval[X86ThreadContext.ESI] = winRegs[4];
      retval[X86ThreadContext.EDI] = winRegs[5];
      retval[X86ThreadContext.EBP] = winRegs[6];
      retval[X86ThreadContext.ESP] = winRegs[7];
      retval[X86ThreadContext.EIP] = winRegs[8];
      retval[X86ThreadContext.DS]  = winRegs[9];
      retval[X86ThreadContext.ES]  = winRegs[10];
      retval[X86ThreadContext.FS]  = winRegs[11];
      retval[X86ThreadContext.GS]  = winRegs[12];
      retval[X86ThreadContext.CS]  = winRegs[13];
      retval[X86ThreadContext.SS]  = winRegs[14];
      retval[X86ThreadContext.EFL] = winRegs[15];
      retval[X86ThreadContext.DR0] = winRegs[16];
      retval[X86ThreadContext.DR1] = winRegs[17];
      retval[X86ThreadContext.DR2] = winRegs[18];
      retval[X86ThreadContext.DR3] = winRegs[19];
      retval[X86ThreadContext.DR6] = winRegs[20];
      retval[X86ThreadContext.DR7] = winRegs[21];
      return retval;
    } catch (IOException e) {
      throw new DebuggerException(e);
    }
  }

  public synchronized void setThreadIntegerRegisterSet(int threadHandleValue,
                                                       boolean mustDuplicateHandle,
                                                       long[] context)
    throws DebuggerException {
    if (!suspended) {
      throw new DebuggerException("Process not suspended");
    }

    try {
      int handle = threadHandleValue;
      if (mustDuplicateHandle) {
        printlnToOutput("duphandle 0x" + Integer.toHexString(threadHandleValue));
        if (!in.parseBoolean()) {
          throw new DebuggerException("Error duplicating thread handle 0x" + threadHandleValue);
        }
        handle = (int) in.parseAddress(); // Must close to avoid leaks
      }
      // Change order of registers to match that of debug server
      long[] winRegs = new long[context.length];
      winRegs[0] = context[X86ThreadContext.EAX];
      winRegs[1] = context[X86ThreadContext.EBX];
      winRegs[2] = context[X86ThreadContext.ECX];
      winRegs[3] = context[X86ThreadContext.EDX];
      winRegs[4] = context[X86ThreadContext.ESI];
      winRegs[5] = context[X86ThreadContext.EDI];
      winRegs[6] = context[X86ThreadContext.EBP];
      winRegs[7] = context[X86ThreadContext.ESP];
      winRegs[8] = context[X86ThreadContext.EIP];
      winRegs[9] = context[X86ThreadContext.DS];
      winRegs[10] = context[X86ThreadContext.ES];
      winRegs[11] = context[X86ThreadContext.FS];
      winRegs[12] = context[X86ThreadContext.GS];
      winRegs[13] = context[X86ThreadContext.CS];
      winRegs[14] = context[X86ThreadContext.SS];
      winRegs[15] = context[X86ThreadContext.EFL];
      winRegs[16] = context[X86ThreadContext.DR0];
      winRegs[17] = context[X86ThreadContext.DR1];
      winRegs[18] = context[X86ThreadContext.DR2];
      winRegs[19] = context[X86ThreadContext.DR3];
      winRegs[20] = context[X86ThreadContext.DR6];
      winRegs[21] = context[X86ThreadContext.DR7];
      StringBuffer cmd = new StringBuffer();
      cmd.append("setcontext 0x");
      cmd.append(Integer.toHexString(threadHandleValue));
      for (int i = 0; i < context.length; i++) {
        cmd.append(" 0x");
        cmd.append(Long.toHexString(winRegs[i]));
      }
      printlnToOutput(cmd.toString());
      boolean res = in.parseBoolean();
      if (mustDuplicateHandle) {
        printlnToOutput("closehandle 0x" + Integer.toHexString(handle));
      }
      if (!res) {
        String failMessage = "SetThreadContext failed for thread handle 0x" +
          Integer.toHexString(handle);
        if (mustDuplicateHandle) {
          failMessage = failMessage + ", duplicated from thread handle " +
            Integer.toHexString(threadHandleValue);
        }
        throw new DebuggerException(failMessage);
      }
    } catch (IOException e) {
      throw new DebuggerException(e);
    }
  }

  /** Fetches the Win32 LDT_ENTRY for the given thread and selector.
      This data structure allows the conversion of a segment-relative
      address to a linear virtual address. For example, it allows the
      expression of operations like "mov eax, fs:[18h]", which fetches
      the thread information block, allowing access to the thread
      ID. */
  public synchronized Win32LDTEntry getThreadSelectorEntry(int threadHandleValue,
                                                           boolean mustDuplicateHandle,
                                                           int selector)
    throws DebuggerException {
    try {
      int handle = threadHandleValue;
      if (mustDuplicateHandle) {
        printlnToOutput("duphandle 0x" + Integer.toHexString(threadHandleValue));
        if (!in.parseBoolean()) {
          throw new DebuggerException("Error duplicating thread handle 0x" + threadHandleValue);
        }
        handle = (int) in.parseAddress(); // Must close to avoid leaks
      }
      printlnToOutput("selectorentry 0x" + Integer.toHexString(handle) + " " + selector);
      if (!in.parseBoolean()) {
        if (mustDuplicateHandle) {
          printlnToOutput("closehandle 0x" + Integer.toHexString(handle));
        }
        throw new DebuggerException("GetThreadContext failed for thread handle 0x" + handle +
                                    ", duplicated from thread handle " + threadHandleValue);
      }
      // Parse result. See
      // src/os/win32/agent/README-commands.txt for the format.
      short limitLow = (short) in.parseAddress();
      short baseLow  = (short) in.parseAddress();
      byte  baseMid  = (byte)  in.parseAddress();
      byte  flags1   = (byte)  in.parseAddress();
      byte  flags2   = (byte)  in.parseAddress();
      byte  baseHi   = (byte)  in.parseAddress();
      return new Win32LDTEntry(limitLow, baseLow, baseMid, flags1, flags2, baseHi);
    } catch (IOException e) {
      throw new DebuggerException(e);
    }
  }

  public synchronized List getThreadList() throws DebuggerException {
    if (!suspended) {
      throw new DebuggerException("Process not suspended");
    }

    try {
      printlnToOutput("threadlist");
      List ret = new ArrayList();
      int numThreads = in.parseInt();
      for (int i = 0; i < numThreads; i++) {
        int handle = (int) in.parseAddress();
        ret.add(new Win32Thread(this, handle));
      }
      return ret;
    } catch (IOException e) {
      throw new DebuggerException(e);
    }
  }

  public synchronized List getLoadObjectList() throws DebuggerException {
    if (!suspended) {
      throw new DebuggerException("Process not suspended");
    }

    try {
      if (loadObjects == null) {
        loadObjects  = new ArrayList();
        nameToDllMap = new HashMap();
        // Get list of library names and base addresses
        printlnToOutput("libinfo");
        int numInfo = in.parseInt();

        for (int i = 0; i < numInfo; i++) {
          // NOTE: because Win32 is case insensitive, we standardize on
          // lowercase file names.
          String  fullPathName = parseString().toLowerCase();
          Address base         = newAddress(in.parseAddress());

          File   file = new File(fullPathName);
          long   size = file.length();
          DLL    dll  = new DLL(this, fullPathName, size, base);
          String name = file.getName();
          nameToDllMap.put(name, dll);
          loadObjects.add(dll);
        }
      }
    } catch (IOException e) {
      throw new DebuggerException(e);
    }

    return loadObjects;
  }

  //----------------------------------------------------------------------
  // Process control access
  //

  public synchronized void writeBytesToProcess(long startAddress, long numBytes, byte[] data)
    throws UnmappedAddressException, DebuggerException {
    try {
      printToOutput("poke 0x" + Long.toHexString(startAddress) +
                    " |");
      writeIntToOutput((int) numBytes);
      writeToOutput(data, 0, (int) numBytes);
      printlnToOutput("");
      if (!in.parseBoolean()) {
        throw new UnmappedAddressException(startAddress);
      }
    } catch (IOException e) {
      throw new DebuggerException(e);
    }
  }

  public synchronized void suspend() throws DebuggerException {
    try {
      if (suspended) {
        throw new DebuggerException("Process already suspended");
      }
      printlnToOutput("suspend");
      suspended = true;
      enableCache();
      reresolveLoadObjects();
    } catch (IOException e) {
      throw new DebuggerException(e);
    }
  }

  public synchronized void resume() throws DebuggerException {
    try {
      if (!suspended) {
        throw new DebuggerException("Process not suspended");
      }
      disableCache();
      printlnToOutput("resume");
      suspended = false;
    } catch (IOException e) {
      throw new DebuggerException(e);
    }
  }

  public synchronized boolean isSuspended() throws DebuggerException {
    return suspended;
  }

  public synchronized void setBreakpoint(Address addr) throws DebuggerException {
    if (!suspended) {
      throw new DebuggerException("Process not suspended");
    }

    long addrVal = getAddressValue(addr);
    Long where = new Long(addrVal);
    if (breakpoints.get(where) != null) {
      throw new DebuggerException("Breakpoint already set at " + addr);
    }
    Byte what = new Byte(readBytes(addrVal, 1)[0]);
    // Now put 0xCC (int 3) at the target address, fail if can not
    writeBytesToProcess(addrVal, 1, new byte[] { (byte) 0xCC });
    // OK, the breakpoint is set.
    breakpoints.put(where, what);
  }

  public synchronized void clearBreakpoint(Address addr) throws DebuggerException {
    if (!suspended) {
      throw new DebuggerException("Process not suspended");
    }

    long addrVal = getAddressValue(addr);
    Long where = new Long(addrVal);
    Byte what = (Byte) breakpoints.get(where);
    if (what == null) {
      throw new DebuggerException("Breakpoint not set at " + addr);
    }
    // Put original data back at address
    writeBytesToProcess(addrVal, 1, new byte[] { what.byteValue() });
    // OK, breakpoint is cleared
    breakpoints.remove(where);
  }

  public synchronized boolean isBreakpointSet(Address addr) throws DebuggerException {
    return (breakpoints.get(new Long(getAddressValue(addr))) != null);
  }

  // Following constants taken from winnt.h
  private static final int EXCEPTION_DEBUG_EVENT  = 1;
  private static final int LOAD_DLL_DEBUG_EVENT   = 6;
  private static final int UNLOAD_DLL_DEBUG_EVENT = 7;
  private static final int EXCEPTION_ACCESS_VIOLATION = 0xC0000005;
  private static final int EXCEPTION_BREAKPOINT       = 0x80000003;
  private static final int EXCEPTION_SINGLE_STEP      = 0x80000004;

  public synchronized DebugEvent debugEventPoll() throws DebuggerException {
    if (curDebugEvent != null) {
      return curDebugEvent;
    }

    try {
      printlnToOutput("pollevent");
      if (!in.parseBoolean()) {
        return null;
      }
      // Otherwise, got a debug event. Need to figure out what kind it is.
      int handle = (int) in.parseAddress();
      ThreadProxy thread = new Win32Thread(this, handle);
      int code = in.parseInt();
      DebugEvent ev = null;
      switch (code) {
      case LOAD_DLL_DEBUG_EVENT: {
        Address addr = newAddress(in.parseAddress());
        ev = BasicDebugEvent.newLoadObjectLoadEvent(thread, addr);
        break;
      }

      case UNLOAD_DLL_DEBUG_EVENT: {
        Address addr = newAddress(in.parseAddress());
        ev = BasicDebugEvent.newLoadObjectUnloadEvent(thread, addr);
        break;
      }

      case EXCEPTION_DEBUG_EVENT: {
        int exceptionCode = in.parseInt();
        Address pc = newAddress(in.parseAddress());
        switch (exceptionCode) {
        case EXCEPTION_ACCESS_VIOLATION:
          boolean wasWrite = in.parseBoolean();
          Address addr = newAddress(in.parseAddress());
          ev = BasicDebugEvent.newAccessViolationEvent(thread, pc, wasWrite, addr);
          break;

        case EXCEPTION_BREAKPOINT:
          ev = BasicDebugEvent.newBreakpointEvent(thread, pc);
          break;

        case EXCEPTION_SINGLE_STEP:
          ev = BasicDebugEvent.newSingleStepEvent(thread, pc);
          break;

        default:
          ev = BasicDebugEvent.newUnknownEvent(thread,
                                               "Exception 0x" + Integer.toHexString(exceptionCode) +
                                               " at PC " + pc);
          break;
        }
        break;
      }

      default:
        ev = BasicDebugEvent.newUnknownEvent(thread,
                                             "Debug event " + code + " occurred");
        break;
      }
      if (Assert.ASSERTS_ENABLED) {
        Assert.that(ev != null, "Must have created event");
      }
      curDebugEvent = ev;
    } catch (IOException e) {
      throw new DebuggerException(e);
    }

    return curDebugEvent;
  }

  public synchronized void debugEventContinue() throws DebuggerException {
    if (curDebugEvent == null) {
      throw new DebuggerException("No debug event pending");
    }

    try {
      ///////////////////////////////////////////////////////////////////
      //                                                               //
      // FIXME: this **must** be modified to handle breakpoint events
      // properly. Must temporarily remove the breakpoint and enable
      // single-stepping mode (hiding those single-step events from
      // the user unless they have been requested; currently there is
      // no way to request single-step events; and it isn't clear how
      // to enable them or how the hardware and/or OS typically
      // supports them, i.e., are they on a per-process or per-thread
      // level?) until the process steps past the breakpoint, then put
      // the breakpoint back.
      //                                                               //
      ///////////////////////////////////////////////////////////////////

      DebugEvent.Type t = curDebugEvent.getType();
      boolean shouldPassOn = true;
      if (t == DebugEvent.Type.BREAKPOINT) {
        // FIXME: correct algorithm appears to be as follows:
        //
        // 1. Check to see whether we know about this breakpoint. If
        // not, it's requested by the user's program and we should
        // ignore it (not pass it on to the program).
        //
        // 2. Replace the original opcode.
        //
        // 3. Set single-stepping mode in the debug registers.
        //
        // 4. Back up the PC.
        //
        // 5. In debugEventPoll(), watch for a single-step event on
        // this thread. When we get it, put the breakpoint back. Only
        // deliver that single-step event if the user has requested
        // single-step events (FIXME: must figure out whether they are
        // per-thread or per-process, and also expose a way to turn
        // them on.)

        // To make breakpoints work for now, we will just back up the
        // PC, which we have to do in order to not disrupt the program
        // execution in case the user decides to disable the breakpoint.

        if (breakpoints.get(new Long(getAddressValue(curDebugEvent.getPC()))) != null) {
          System.err.println("Backing up PC due to breakpoint");
          X86ThreadContext ctx = (X86ThreadContext) curDebugEvent.getThread().getContext();
          ctx.setRegister(X86ThreadContext.EIP, ctx.getRegister(X86ThreadContext.EIP) - 1);
          curDebugEvent.getThread().setContext(ctx);
        } else {
          System.err.println("Skipping back up of PC since I didn't know about this breakpoint");
          System.err.println("Known breakpoints:");
          for (Iterator iter = breakpoints.keySet().iterator(); iter.hasNext(); ) {
            System.err.println("  0x" + Long.toHexString(((Long) iter.next()).longValue()));
          }
        }
        shouldPassOn = false;
      } else if (t == DebugEvent.Type.SINGLE_STEP) {
        shouldPassOn = false;
      }
      // Other kinds of debug events are either ignored if passed on
      // or probably should be passed on so the program exits
      // FIXME: generate process exiting events (should be easy)

      int val = (shouldPassOn ? 1 : 0);
      printlnToOutput("continueevent " + val);
      if (!in.parseBoolean()) {
        throw new DebuggerException("Unknown error while attempting to continue past debug event");
      }
      curDebugEvent = null;
    } catch (IOException e) {
      throw new DebuggerException(e);
    }
  }

  //--------------------------------------------------------------------------------
  // Address access
  //

  /** From the Debugger interface */
  public long getAddressValue(Address addr) {
    if (addr == null) return 0;
    return ((Win32Address) addr).getValue();
  }

  /** From the Win32Debugger interface */
  public Address newAddress(long value) {
    if (value == 0) return null;
    return new Win32Address(this, value);
  }

  //--------------------------------------------------------------------------------
  // Internals only below this point
  //

  private String parseString() throws IOException {
    int charSize = in.parseInt();
    int numChars = in.parseInt();
    in.skipByte();
    String str;
    if (charSize == 1) {
      str = in.readByteString(numChars);
    } else {
      str = in.readCharString(numChars);
    }
    return str;
  }

  /** Looks up an address in the remote process's address space.
      Returns 0 if symbol not found or upon error. Package private to
      allow Win32DebuggerRemoteIntfImpl access. NOTE that this returns
      a long instead of an Address because we do not want to serialize
      Addresses. */
  synchronized long lookupInProcess(String objectName, String symbol) {
    // NOTE: this assumes that process is suspended (which is probably
    // necessary assumption given that DLLs can be loaded/unloaded as
    // process runs). Should update documentation.
    if (nameToDllMap == null) {
      getLoadObjectList();
    }
    DLL dll = (DLL) nameToDllMap.get(objectName);
    // The DLL can be null because we use this to search through known
    // DLLs in HotSpotTypeDataBase (for example)
    if (dll != null) {
      Win32Address addr = (Win32Address) dll.lookupSymbol(symbol);
      if (addr != null) {
        return addr.getValue();
      }
    }
    return 0;
  }

  /** This reads bytes from the remote process. */
  public synchronized ReadResult readBytesFromProcess(long address, long numBytes)
    throws UnmappedAddressException, DebuggerException {
    try {
      String cmd = "peek " + utils.addressValueToString(address) + " " + numBytes;
      printlnToOutput(cmd);
      while (in.readByte() != 'B') {
      }
      byte res = in.readByte();
      if (res == 0) {
        System.err.println("Failing command: " + cmd);
        throw new DebuggerException("Read of remote process address space failed");
      }
      // NOTE: must read ALL of the data regardless of whether we need
      // to throw an UnmappedAddressException. Otherwise will corrupt
      // the input stream each time we have a failure. Not good. Do
      // not want to risk "flushing" the input stream in case a huge
      // read has a hangup in the middle and we leave data on the
      // stream.
      byte[] buf = new byte[(int) numBytes];
      boolean bailOut = false;
      long failureAddress = 0;
      while (numBytes > 0) {
        long len = in.readUnsignedInt();
        boolean isMapped = ((in.readByte() == 0) ? false : true);
        if (!isMapped) {
          if (!bailOut) {
            bailOut = true;
            failureAddress = address;
          }
        } else {
          // This won't work if we have unmapped regions, but if we do
          // then we're going to throw an exception anyway

          // NOTE: there is a factor of 20 speed difference between
          // these two ways of doing this read.
          in.readBytes(buf, 0, (int) len);
        }

        // Do NOT do this:
        //        for (int i = 0; i < (int) len; i++) {
        //          buf[i] = in.readByte();
        //        }

        numBytes -= len;
        address += len;
      }
      if (Assert.ASSERTS_ENABLED) {
        Assert.that(numBytes == 0, "Bug in debug server's implementation of peek");
      }
      if (bailOut) {
        return new ReadResult(failureAddress);
      }
      return new ReadResult(buf);
    }
    catch (IOException e) {
      throw new DebuggerException(e);
    }
  }

  /** Convenience routines */
  private void printlnToOutput(String s) throws IOException {
    out.println(s);
    if (out.checkError()) {
      throw new IOException("Error occurred while writing to debug server");
    }
  }

  private void printToOutput(String s) throws IOException {
    out.print(s);
    if (out.checkError()) {
      throw new IOException("Error occurred while writing to debug server");
    }
  }

  private void writeIntToOutput(int val) throws IOException {
    rawOut.writeInt(val);
    rawOut.flush();
  }

  private void writeToOutput(byte[] buf, int off, int len) throws IOException {
    rawOut.write(buf, off, len);
    rawOut.flush();
  }

  /** Connects to the debug server, setting up out and in streams. */
  private void connectToDebugServer() throws IOException {
    // Try for a short period of time to connect to debug server; time out
    // with failure if didn't succeed
    debuggerSocket = null;
    long endTime = System.currentTimeMillis() + SHORT_TIMEOUT;

    while ((debuggerSocket == null) && (System.currentTimeMillis() < endTime)) {
      try {
        // FIXME: this does not work if we are on a DHCP machine which
        // did not get an IP address this session. It appears to use
        // an old cached address and the connection does not actually
        // succeed. Must file a bug.
        // debuggerSocket = new Socket(InetAddress.getLocalHost(), PORT);
        debuggerSocket = new Socket(InetAddress.getByName("127.0.0.1"), PORT);
        debuggerSocket.setTcpNoDelay(true);
      }
      catch (IOException e) {
        // Swallow IO exceptions while attempting connection
        debuggerSocket = null;
        try {
          // Don't swamp the CPU
          Thread.sleep(750);
        }
        catch (InterruptedException ex) {
        }
      }
    }

    if (debuggerSocket == null) {
      // Failed to connect because of timeout
      throw new DebuggerException("Timed out while attempting to connect to debug server (please start SwDbgSrv.exe)");
    }

    out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(debuggerSocket.getOutputStream(), "US-ASCII")), true);
    rawOut = new DataOutputStream(new BufferedOutputStream(debuggerSocket.getOutputStream()));
    in = new InputLexer(new BufferedInputStream(debuggerSocket.getInputStream()));
  }

  private DLL findDLLByName(String fullPathName) {
    for (Iterator iter = loadObjects.iterator(); iter.hasNext(); ) {
      DLL dll = (DLL) iter.next();
      if (dll.getName().equals(fullPathName)) {
        return dll;
      }
    }
    return null;
  }

  private void reresolveLoadObjects() throws DebuggerException {
    try {
      // It is too expensive to throw away the loadobject list every
      // time the process is suspended, largely because of debug
      // information re-parsing. When we suspend the target process we
      // instead fetch the list of loaded libraries in the target and
      // see whether any loadobject needs to be thrown away (because it
      // was unloaded) or invalidated (because it was unloaded and
      // reloaded at a different target address). Note that we don't
      // properly handle the case of a loaded DLL being unloaded,
      // recompiled, and reloaded. We could handle this by keeping a
      // time stamp.

      if (loadObjects == null) {
        return;
      }

      // Need to create new list since have to figure out which ones
      // were unloaded
      List newLoadObjects = new ArrayList();

    // Get list of library names and base addresses
      printlnToOutput("libinfo");
      int numInfo = in.parseInt();

      for (int i = 0; i < numInfo; i++) {
        // NOTE: because Win32 is case insensitive, we standardize on
        // lowercase file names.
        String  fullPathName = parseString().toLowerCase();
        Address base         = newAddress(in.parseAddress());

        // Look for full path name in DLL list
        DLL dll = findDLLByName(fullPathName);
        boolean mustLoad = true;
        if (dll != null) {
          loadObjects.remove(dll);

          // See whether base addresses match; otherwise, need to reload
          if (AddressOps.equal(base, dll.getBase())) {
            mustLoad = false;
          }
        }

        if (mustLoad) {
          // Create new DLL
          File   file = new File(fullPathName);
          long   size = file.length();
          String name = file.getName();
          dll  = new DLL(this, fullPathName, size, base);
          nameToDllMap.put(name, dll);
        }
        newLoadObjects.add(dll);
      }

      // All remaining entries in loadObjects have to be removed from
      // the nameToDllMap
      for (Iterator dllIter = loadObjects.iterator(); dllIter.hasNext(); ) {
        DLL dll = (DLL) dllIter.next();
        for (Iterator iter = nameToDllMap.keySet().iterator(); iter.hasNext(); ) {
          String name = (String) iter.next();
          if (nameToDllMap.get(name) == dll) {
            nameToDllMap.remove(name);
            break;
          }
        }
      }

      loadObjects = newLoadObjects;
    } catch (IOException e) {
      loadObjects = null;
      nameToDllMap = null;
      throw new DebuggerException(e);
    }
  }
}
