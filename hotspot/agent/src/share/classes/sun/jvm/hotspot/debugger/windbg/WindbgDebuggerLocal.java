/*
 * Copyright (c) 2002, 2008, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.debugger.windbg;

import java.io.*;
import java.net.*;
import java.util.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.debugger.amd64.*;
import sun.jvm.hotspot.debugger.x86.*;
import sun.jvm.hotspot.debugger.ia64.*;
import sun.jvm.hotspot.debugger.windbg.amd64.*;
import sun.jvm.hotspot.debugger.windbg.x86.*;
import sun.jvm.hotspot.debugger.windbg.ia64.*;
import sun.jvm.hotspot.debugger.win32.coff.*;
import sun.jvm.hotspot.debugger.cdbg.*;
import sun.jvm.hotspot.debugger.cdbg.basic.BasicDebugEvent;
import sun.jvm.hotspot.utilities.*;
import sun.jvm.hotspot.utilities.memo.*;
import sun.jvm.hotspot.runtime.*;

/** <P> An implementation of the JVMDebugger interface which talks to
    windbg and symbol table management is done in Java. </P>

    <P> <B>NOTE</B> that since we have the notion of fetching "Java
    primitive types" from the remote process (which might have
    different sizes than we expect) we have a bootstrapping
    problem. We need to know the sizes of these types before we can
    fetch them. The current implementation solves this problem by
    requiring that it be configured with these type sizes before they
    can be fetched. The readJ(Type) routines here will throw a
    RuntimeException if they are called before the debugger is
    configured with the Java primitive type sizes. </P> */

public class WindbgDebuggerLocal extends DebuggerBase implements WindbgDebugger {
  private PageCache cache;
  private boolean   attached;
  private boolean   isCore;

  // Symbol lookup support
  // This is a map of library names to DLLs
  private Map nameToDllMap;

  // C/C++ debugging support
  private List/*<LoadObject>*/ loadObjects;
  private CDebugger cdbg;

  // thread access
  private Map threadIntegerRegisterSet;
  private List threadList;

  // windbg native interface pointers

  private long ptrIDebugClient;
  private long ptrIDebugControl;
  private long ptrIDebugDataSpaces;
  private long ptrIDebugOutputCallbacks;
  private long ptrIDebugAdvanced;
  private long ptrIDebugSymbols;
  private long ptrIDebugSystemObjects;

  private WindbgThreadFactory threadFactory;

  //--------------------------------------------------------------------------------
  // Implementation of Debugger interface
  //

  /** <P> machDesc may not be null. </P>

      <P> useCache should be set to true if debugging is being done
      locally, and to false if the debugger is being created for the
      purpose of supporting remote debugging. </P> */
  public WindbgDebuggerLocal(MachineDescription machDesc,
                            boolean useCache) throws DebuggerException {
    this.machDesc = machDesc;
    utils = new DebuggerUtilities(machDesc.getAddressSize(), machDesc.isBigEndian()) {
           public void checkAlignment(long address, long alignment) {
             // Need to override default checkAlignment because we need to
             // relax alignment constraints on Windows/x86
             if ( (address % alignment != 0)
                &&(alignment != 8 || address % 4 != 0)) {
                throw new UnalignedAddressException(
                        "Trying to read at address: "
                      + addressValueToString(address)
                      + " with alignment: " + alignment,
                        address);
             }
           }
        };

    String cpu = PlatformInfo.getCPU();
    if (cpu.equals("x86")) {
       threadFactory = new WindbgX86ThreadFactory(this);
    } else if (cpu.equals("amd64")) {
       threadFactory = new WindbgAMD64ThreadFactory(this);
    } else if (cpu.equals("ia64")) {
       threadFactory = new WindbgIA64ThreadFactory(this);
    }

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
      initCache(4096, 4096);
    }
    // FIXME: add instantiation of thread factory

  }

  /** From the Debugger interface via JVMDebugger */
  public boolean hasProcessList() throws DebuggerException {
    return false;
  }

  /** From the Debugger interface via JVMDebugger */
  public List getProcessList() throws DebuggerException {
    return null;
  }


  /** From the Debugger interface via JVMDebugger */
  public synchronized void attach(int processID) throws DebuggerException {
    attachInit();
    attach0(processID);
    attached = true;
    isCore = false;
  }

  /** From the Debugger interface via JVMDebugger */
  public synchronized void attach(String executableName, String coreFileName) throws DebuggerException {
    attachInit();
    attach0(executableName, coreFileName);
    attached = true;
    isCore = true;
  }

  public List getLoadObjectList() {
    requireAttach();
    return loadObjects;
  }

  /** From the Debugger interface via JVMDebugger */
  public synchronized boolean detach() {
    if ( ! attached)
       return false;

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

    threadIntegerRegisterSet = null;
    threadList = null;
    try {
       detach0();
    } finally {
       attached = false;
       resetNativePointers();
    }
    return true;
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
    return true;
  }

  public synchronized String consoleExecuteCommand(String cmd) throws DebuggerException {
    requireAttach();
    if (! attached) {
       throw new DebuggerException("debugger not yet attached to a Dr. Watson dump!");
    }

    return consoleExecuteCommand0(cmd);
  }

  public String getConsolePrompt() throws DebuggerException {
    return "(windbg)";
  }

  public CDebugger getCDebugger() throws DebuggerException {
    if (cdbg == null) {
      // FIXME: CDebugger is not yet supported for IA64 because
      // of native stack walking issues.
      if (! getCPU().equals("ia64")) {
         cdbg = new WindbgCDebugger(this);
      }
    }
    return cdbg;
  }

  /** From the SymbolLookup interface via Debugger and JVMDebugger */
  public synchronized Address lookup(String objectName, String symbol) {
    requireAttach();
    return newAddress(lookupByName(objectName, symbol));
  }

  /** From the SymbolLookup interface via Debugger and JVMDebugger */
  public synchronized OopHandle lookupOop(String objectName, String symbol) {
    Address addr = lookup(objectName, symbol);
    if (addr == null) {
      return null;
    }
    return addr.addOffsetToAsOopHandle(0);
  }

  public synchronized ClosestSymbol lookup(long address) {
    return lookupByAddress0(address);
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
    return threadFactory.createThreadWrapper(addr);
  }

  public ThreadProxy getThreadForThreadId(long handle) {
    // with windbg we can't make out using handle
    throw new DebuggerException("Unimplemented!");
  }

  public long getThreadIdFromSysId(long sysId) throws DebuggerException {
    requireAttach();
    return getThreadIdFromSysId0(sysId);
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
  // Internal routines (for implementation of WindbgAddress).
  // These must not be called until the MachineDescription has been set up.
  //

  /** From the WindbgDebugger interface */
  public String addressValueToString(long address) {
    return utils.addressValueToString(address);
  }

  /** From the WindbgDebugger interface */
  public WindbgAddress readAddress(long address)
    throws UnmappedAddressException, UnalignedAddressException {
    return (WindbgAddress) newAddress(readAddressValue(address));
  }

  public WindbgAddress readCompOopAddress(long address)
    throws UnmappedAddressException, UnalignedAddressException {
    return (WindbgAddress) newAddress(readCompOopAddressValue(address));
  }

  /** From the WindbgDebugger interface */
  public WindbgOopHandle readOopHandle(long address)
    throws UnmappedAddressException, UnalignedAddressException, NotInHeapException {
    long value = readAddressValue(address);
    return (value == 0 ? null : new WindbgOopHandle(this, value));
  }
  public WindbgOopHandle readCompOopHandle(long address)
    throws UnmappedAddressException, UnalignedAddressException, NotInHeapException {
    long value = readCompOopAddressValue(address);
    return (value == 0 ? null : new WindbgOopHandle(this, value));
  }

  /** From the WindbgDebugger interface */
  public int getAddressSize() {
    return (int) machDesc.getAddressSize();
  }

  //--------------------------------------------------------------------------------
  // Thread context access
  //

  private synchronized void setThreadIntegerRegisterSet(long threadId,
                                               long[] regs) {
    threadIntegerRegisterSet.put(new Long(threadId), regs);
  }

  private synchronized void addThread(long sysId) {
    threadList.add(threadFactory.createThreadWrapper(sysId));
  }

  public synchronized long[] getThreadIntegerRegisterSet(long threadId)
    throws DebuggerException {
    requireAttach();
    return (long[]) threadIntegerRegisterSet.get(new Long(threadId));
  }

  public synchronized List getThreadList() throws DebuggerException {
    requireAttach();
    return threadList;
  }

  private String findFullPath(String file) {
    File f = new File(file);
    if (f.exists()) {
       return file;
    } else {
       // remove path part, if any.
       file = f.getName();
       StringTokenizer st = new StringTokenizer(imagePath, File.pathSeparator);
       while (st.hasMoreTokens()) {
          f = new File(st.nextToken(), file);
          if (f.exists()) {
             return f.getPath();
          }
       }
    }
    return null;
  }

  private synchronized void addLoadObject(String file, long size, long base) {
    String path = findFullPath(file);
    if (path != null) {
       DLL dll = null;
       if (useNativeLookup) {
          dll = new DLL(this, path, size,newAddress(base)) {
                 public ClosestSymbol  closestSymbolToPC(Address pcAsAddr) {
                   long pc = getAddressValue(pcAsAddr);
                   ClosestSymbol sym = lookupByAddress0(pc);
                   if (sym == null) {
                     return super.closestSymbolToPC(pcAsAddr);
                   } else {
                     return sym;
                   }
                 }
              };
       } else {
         dll = new DLL(this, path, size, newAddress(base));
       }
       loadObjects.add(dll);
       nameToDllMap.put(new File(file).getName(), dll);
    }
  }

  //--------------------------------------------------------------------------------
  // Address access
  //

  /** From the Debugger interface */
  public long getAddressValue(Address addr) {
    if (addr == null) return 0;
    return ((WindbgAddress) addr).getValue();
  }

  /** From the WindbgDebugger interface */
  public Address newAddress(long value) {
    if (value == 0) return null;
    return new WindbgAddress(this, value);
  }

  //--------------------------------------------------------------------------------
  // Internals only below this point
  //

  // attach/detach helpers
  private void checkAttached() {
    if (attached) {
       String msg = (isCore)? "already attached to a Dr. Watson dump!" :
                              "already attached to a process!";
       throw new DebuggerException(msg);
    }
  }

  private void requireAttach() {
    if (!attached) {
       throw new RuntimeException("not attached to a process or Dr Watson dump");
    }
  }

  private void attachInit() {
    checkAttached();
    loadObjects = new ArrayList();
    nameToDllMap = new HashMap();
    threadIntegerRegisterSet = new HashMap();
    threadList = new ArrayList();
  }

  private void resetNativePointers() {
    ptrIDebugClient          = 0L;
    ptrIDebugControl         = 0L;
    ptrIDebugDataSpaces      = 0L;
    ptrIDebugOutputCallbacks = 0L;
    ptrIDebugAdvanced        = 0L;
    ptrIDebugSymbols         = 0L;
    ptrIDebugSystemObjects   = 0L;
  }

  synchronized long lookupByName(String objectName, String symbol) {
    long res = 0L;
    if (useNativeLookup) {
      res = lookupByName0(objectName, symbol);
      if (res != 0L) {
        return res;
      } // else fallthru...
    }

    DLL dll = (DLL) nameToDllMap.get(objectName);
    // The DLL can be null because we use this to search through known
    // DLLs in HotSpotTypeDataBase (for example)
    if (dll != null) {
      WindbgAddress addr = (WindbgAddress) dll.lookupSymbol(symbol);
      if (addr != null) {
        return addr.getValue();
      }
    }
    return 0L;
  }

  /** This reads bytes from the remote process. */
  public synchronized ReadResult readBytesFromProcess(long address, long numBytes)
    throws UnmappedAddressException, DebuggerException {
    requireAttach();
    byte[] res = readBytesFromProcess0(address, numBytes);
    if(res != null)
       return new ReadResult(res);
    else
       return new ReadResult(address);
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

  public void writeBytesToProcess(long address, long numBytes, byte[] data)
    throws UnmappedAddressException, DebuggerException {
    // FIXME
    throw new DebuggerException("Unimplemented");
  }

  private static String  DTFWHome;
  private static String  imagePath;
  private static String  symbolPath;
  private static boolean useNativeLookup;

    static {

     /*
      * sawindbg.dll depends on dbgeng.dll which
      * itself depends on dbghelp.dll. dbgeng.dll and dbghelp.dll.
      * On systems newer than Windows 2000, these two .dlls are
      * in the standard system directory so we will find them there.
      * On Windows 2000 and earlier, these files do not exist.
      * The user must download Debugging Tools For Windows (DTFW)
      * and install it in order to use SA.
      *
      * We have to make sure we use the two files from the same directory
      * in case there are more than one copy on the system because
      * one version of dbgeng.dll might not be compatible with a
      * different version of dbghelp.dll.
      * We first look for them in the directory pointed at by
      * env. var. DEBUGGINGTOOLSFORWINDOWS, next in the default
      * installation dir for DTFW, and lastly in the standard
      * system directory.  We expect that that we will find
      * them in the standard system directory on all systems
      * newer than Windows 2000.
      */
    String dirName = null;
    DTFWHome = System.getenv("DEBUGGINGTOOLSFORWINDOWS");

    if (DTFWHome == null) {
      // See if we have the files in the default location.
      String sysRoot = System.getenv("SYSTEMROOT");
      DTFWHome = sysRoot + File.separator +
          ".." + File.separator + "Program Files" +
          File.separator + "Debugging Tools For Windows";
    }

    {
      String dbghelp = DTFWHome + File.separator + "dbghelp.dll";
      String dbgeng = DTFWHome + File.separator + "dbgeng.dll";
      File fhelp = new File(dbghelp);
      File feng = new File(dbgeng);
      if (fhelp.exists() && feng.exists()) {
        // found both, we are happy.
        // NOTE: The order of loads is important! If we load dbgeng.dll
        // first, then the dependency - dbghelp.dll - will be loaded
        // from usual DLL search thereby defeating the purpose!
        System.load(dbghelp);
        System.load(dbgeng);
      } else if (! fhelp.exists() && ! feng.exists()) {
        // neither exist. We will ignore this dir and assume
        // they are in the system dir.
        DTFWHome = null;
      } else {
        // one exists but not the other
        //System.err.println("Error: Both files dbghelp.dll and dbgeng.dll "
        //                   "must exist in directory " + DTFWHome);
        throw new UnsatisfiedLinkError("Both files dbghelp.dll and " +
                                       "dbgeng.dll must exist in " +
                                       "directory " + DTFWHome);
      }
    }
    if (DTFWHome == null) {
      // The files better be in the system dir.
      String sysDir = System.getenv("SYSTEMROOT") +
          File.separator + "system32";

      File feng = new File(sysDir + File.separator + "dbgeng.dll");
      if (!feng.exists()) {
        throw new UnsatisfiedLinkError("File dbgeng.dll does not exist in " +
                                        sysDir + ".  Please search microsoft.com " +
                                       "for Debugging Tools For Windows, and " +
                                       "either download it to the default " +
                                       "location, or download it to a custom " +
                                       "location and set environment variable " +
                                       "   DEBUGGINGTOOLSFORWINDOWS  "  +
                                       "to the pathname of that location.");
      }
    }

    // Now, load sawindbg.dll
    System.loadLibrary("sawindbg");
    // where do I find '.exe', '.dll' files?
    imagePath = System.getProperty("sun.jvm.hotspot.debugger.windbg.imagePath");
    if (imagePath == null) {
      imagePath = System.getenv("PATH");
    }

    // where do I find '.pdb', '.dbg' files?
    symbolPath = System.getProperty("sun.jvm.hotspot.debugger.windbg.symbolPath");

    // mostly, debug files would be find where .dll's, .exe's are found.
    if (symbolPath == null) {
      symbolPath = imagePath;
    }

    // should we parse DLL symbol table in Java code or use
    // Windbg's native lookup facility? By default, we use
    // native lookup so that we can take advantage of '.pdb'
    // files, if available.
    useNativeLookup = true;
    String str = System.getProperty("sun.jvm.hotspot.debugger.windbg.disableNativeLookup");
    if (str != null) {
      useNativeLookup = false;
    }

    initIDs();
  }

  // native methods
  private static native void initIDs();
  private native void attach0(String executableName, String coreFileName);
  private native void attach0(int processID);
  private native void detach0();
  private native byte[] readBytesFromProcess0(long address, long numBytes)
    throws UnmappedAddressException, DebuggerException;
  private native long getThreadIdFromSysId0(long sysId);
  private native String consoleExecuteCommand0(String cmd);
  private native long lookupByName0(String objName, String symName);
  private native ClosestSymbol lookupByAddress0(long address);

  // helper called lookupByAddress0
  private ClosestSymbol createClosestSymbol(String symbol, long diff) {
    return new ClosestSymbol(symbol, diff);
  }
}
