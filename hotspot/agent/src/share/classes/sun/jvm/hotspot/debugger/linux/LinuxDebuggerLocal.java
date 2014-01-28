/*
 * Copyright (c) 2002, 2013, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.debugger.linux;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import sun.jvm.hotspot.debugger.Address;
import sun.jvm.hotspot.debugger.DebuggerBase;
import sun.jvm.hotspot.debugger.DebuggerException;
import sun.jvm.hotspot.debugger.DebuggerUtilities;
import sun.jvm.hotspot.debugger.MachineDescription;
import sun.jvm.hotspot.debugger.NotInHeapException;
import sun.jvm.hotspot.debugger.OopHandle;
import sun.jvm.hotspot.debugger.ReadResult;
import sun.jvm.hotspot.debugger.ThreadProxy;
import sun.jvm.hotspot.debugger.UnalignedAddressException;
import sun.jvm.hotspot.debugger.UnmappedAddressException;
import sun.jvm.hotspot.debugger.cdbg.CDebugger;
import sun.jvm.hotspot.debugger.cdbg.ClosestSymbol;
import sun.jvm.hotspot.debugger.cdbg.LoadObject;
import sun.jvm.hotspot.utilities.PlatformInfo;

/** <P> An implementation of the JVMDebugger interface. The basic debug
    facilities are implemented through ptrace interface in the JNI code
    (libsaproc.so). Library maps and symbol table management are done in
    JNI. </P>

    <P> <B>NOTE</B> that since we have the notion of fetching "Java
    primitive types" from the remote process (which might have
    different sizes than we expect) we have a bootstrapping
    problem. We need to know the sizes of these types before we can
    fetch them. The current implementation solves this problem by
    requiring that it be configured with these type sizes before they
    can be fetched. The readJ(Type) routines here will throw a
    RuntimeException if they are called before the debugger is
    configured with the Java primitive type sizes. </P> */

public class LinuxDebuggerLocal extends DebuggerBase implements LinuxDebugger {
    private boolean useGCC32ABI;
    private boolean attached;
    private long    p_ps_prochandle; // native debugger handle
    private boolean isCore;

    // CDebugger support
    private LinuxCDebugger cdbg;

    // threadList and loadObjectList are filled by attach0 method
    private List threadList;
    private List loadObjectList;

    // called by native method lookupByAddress0
    private ClosestSymbol createClosestSymbol(String name, long offset) {
       return new ClosestSymbol(name, offset);
    }

    // called by native method attach0
    private LoadObject createLoadObject(String fileName, long textsize,
                                        long base) {
       File f = new File(fileName);
       Address baseAddr = newAddress(base);
       return new SharedObject(this, fileName, f.length(), baseAddr);
    }

    // native methods

    private native static void init0()
                                throws DebuggerException;
    private native void attach0(int pid)
                                throws DebuggerException;
    private native void attach0(String execName, String coreName)
                                throws DebuggerException;
    private native void detach0()
                                throws DebuggerException;
    private native long lookupByName0(String objectName, String symbol)
                                throws DebuggerException;
    private native ClosestSymbol lookupByAddress0(long address)
                                throws DebuggerException;
    private native long[] getThreadIntegerRegisterSet0(int lwp_id)
                                throws DebuggerException;
    private native byte[] readBytesFromProcess0(long address, long numBytes)
                                throws DebuggerException;
    public native static int  getAddressSize() ;

    // Note on Linux threads are really processes. When target process is
    // attached by a serviceability agent thread, only that thread can do
    // ptrace operations on the target. This is because from kernel's point
    // view, other threads are just separate processes and they are not
    // attached to the target. When they attempt to make ptrace calls,
    // an ESRCH error will be returned as kernel believes target is not
    // being traced by the caller.
    // To work around the problem, we use a worker thread here to handle
    // all JNI functions that are making ptrace calls.

    interface WorkerThreadTask {
       public void doit(LinuxDebuggerLocal debugger) throws DebuggerException;
    }

    class LinuxDebuggerLocalWorkerThread extends Thread {
       LinuxDebuggerLocal debugger;
       WorkerThreadTask task;
       DebuggerException lastException;

       public LinuxDebuggerLocalWorkerThread(LinuxDebuggerLocal debugger) {
         this.debugger = debugger;
         setDaemon(true);
       }

       public void run() {
          synchronized (workerThread) {
             for (;;) {
                if (task != null) {
                   lastException = null;
                   try {
                      task.doit(debugger);
                   } catch (DebuggerException exp) {
                      lastException = exp;
                   }
                   task = null;
                   workerThread.notifyAll();
                }

                try {
                   workerThread.wait();
                } catch (InterruptedException x) {}
             }
          }
       }

       public WorkerThreadTask execute(WorkerThreadTask task) throws DebuggerException {
          synchronized (workerThread) {
             this.task = task;
             workerThread.notifyAll();
             while (this.task != null) {
                try {
                   workerThread.wait();
                } catch (InterruptedException x) {}
             }
             if (lastException != null) {
                throw new DebuggerException(lastException);
             } else {
                return task;
             }
          }
       }
    }

    private LinuxDebuggerLocalWorkerThread workerThread = null;

    //----------------------------------------------------------------------
    // Implementation of Debugger interface
    //

    /** <P> machDesc may not be null. </P>

    <P> useCache should be set to true if debugging is being done
    locally, and to false if the debugger is being created for the
    purpose of supporting remote debugging. </P> */
    public LinuxDebuggerLocal(MachineDescription machDesc,
                              boolean useCache) throws DebuggerException {
        this.machDesc = machDesc;
        utils = new DebuggerUtilities(machDesc.getAddressSize(),
                                      machDesc.isBigEndian()) {
           public void checkAlignment(long address, long alignment) {
             // Need to override default checkAlignment because we need to
             // relax alignment constraints on Linux/x86
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

        if (useCache) {
            // FIXME: re-test necessity of cache on Linux, where data
            // fetching is faster
            // Cache portion of the remote process's address space.
            // Fetching data over the socket connection to dbx is slow.
            // Might be faster if we were using a binary protocol to talk to
            // dbx, but would have to test. For now, this cache works best
            // if it covers the entire heap of the remote process. FIXME: at
            // least should make this tunable from the outside, i.e., via
            // the UI. This is a cache of 4096 4K pages, or 16 MB. The page
            // size must be adjusted to be the hardware's page size.
            // (FIXME: should pick this up from the debugger.)
            if (getCPU().equals("ia64")) {
              initCache(16384, parseCacheNumPagesProperty(1024));
            } else {
              initCache(4096, parseCacheNumPagesProperty(4096));
            }
        }

        workerThread = new LinuxDebuggerLocalWorkerThread(this);
        workerThread.start();
    }

    /** From the Debugger interface via JVMDebugger */
    public boolean hasProcessList() throws DebuggerException {
        return false;
    }

    /** From the Debugger interface via JVMDebugger */
    public List getProcessList() throws DebuggerException {
        throw new DebuggerException("getProcessList not implemented yet");
    }

    private void checkAttached() throws DebuggerException {
        if (attached) {
            if (isCore) {
                throw new DebuggerException("attached to a core dump already");
            } else {
                throw new DebuggerException("attached to a process already");
            }
        }
    }

    private void requireAttach() {
        if (! attached) {
            throw new RuntimeException("not attached to a process or a core!");
        }
    }

    /* called from attach methods */
    private void findABIVersion() throws DebuggerException {
        if (lookupByName0("libjvm.so", "__vt_10JavaThread") != 0) {
            // old C++ ABI
            useGCC32ABI = false;
        } else {
            // new C++ ABI
            useGCC32ABI = true;
        }
    }

    /** From the Debugger interface via JVMDebugger */
    public synchronized void attach(int processID) throws DebuggerException {
        checkAttached();
        threadList = new ArrayList();
        loadObjectList = new ArrayList();
        class AttachTask implements WorkerThreadTask {
           int pid;
           public void doit(LinuxDebuggerLocal debugger) {
              debugger.attach0(pid);
              debugger.attached = true;
              debugger.isCore = false;
              findABIVersion();
           }
        }

        AttachTask task = new AttachTask();
        task.pid = processID;
        workerThread.execute(task);
    }

    /** From the Debugger interface via JVMDebugger */
    public synchronized void attach(String execName, String coreName) {
        checkAttached();
        threadList = new ArrayList();
        loadObjectList = new ArrayList();
        attach0(execName, coreName);
        attached = true;
        isCore = true;
        findABIVersion();
    }

    /** From the Debugger interface via JVMDebugger */
    public synchronized boolean detach() {
        if (!attached) {
            return false;
        }

        threadList = null;
        loadObjectList = null;

        if (isCore) {
            detach0();
            attached = false;
            return true;
        } else {
            class DetachTask implements WorkerThreadTask {
                boolean result = false;

                public void doit(LinuxDebuggerLocal debugger) {
                    debugger.detach0();
                    debugger.attached = false;
                    result = true;
                }
            }

            DetachTask task = new DetachTask();
            workerThread.execute(task);
            return task.result;
        }
    }

    /** From the Debugger interface via JVMDebugger */
    public Address parseAddress(String addressString)
            throws NumberFormatException {
        long addr = utils.scanAddress(addressString);
        if (addr == 0) {
            return null;
        }
        return new LinuxAddress(this, addr);
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
        throw new DebuggerException("No debugger console available on Linux");
    }

    public String getConsolePrompt() throws DebuggerException {
        return null;
    }

    /* called from lookup */
    private long handleGCC32ABI(long addr, String symbol) throws DebuggerException {
        if (useGCC32ABI && symbol.startsWith("_ZTV")) {
            return addr + (2 * machDesc.getAddressSize());
        } else {
            return addr;
        }
    }

    /** From the SymbolLookup interface via Debugger and JVMDebugger */
    public synchronized Address lookup(String objectName, String symbol) {
        requireAttach();
        if (!attached) {
            return null;
        }

        if (isCore) {
            long addr = lookupByName0(objectName, symbol);
            return (addr == 0)? null : new LinuxAddress(this, handleGCC32ABI(addr, symbol));
        } else {
            class LookupByNameTask implements WorkerThreadTask {
                String objectName, symbol;
                Address result;

                public void doit(LinuxDebuggerLocal debugger) {
                    long addr = debugger.lookupByName0(objectName, symbol);
                    result = (addr == 0 ? null : new LinuxAddress(debugger, handleGCC32ABI(addr, symbol)));
                }
            }

            LookupByNameTask task = new LookupByNameTask();
            task.objectName = objectName;
            task.symbol = symbol;
            workerThread.execute(task);
            return task.result;
        }
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

    //----------------------------------------------------------------------
    // Implementation of ThreadAccess interface
    //

    /** From the ThreadAccess interface via Debugger and JVMDebugger */
    public ThreadProxy getThreadForIdentifierAddress(Address addr) {
        return new LinuxThread(this, addr);
    }

    /** From the ThreadAccess interface via Debugger and JVMDebugger */
    public ThreadProxy getThreadForThreadId(long id) {
        return new LinuxThread(this, id);
    }

    //----------------------------------------------------------------------
    // Internal routines (for implementation of LinuxAddress).
    // These must not be called until the MachineDescription has been set up.
    //

    /** From the LinuxDebugger interface */
    public String addressValueToString(long address) {
        return utils.addressValueToString(address);
    }

    /** From the LinuxDebugger interface */
    public LinuxAddress readAddress(long address)
            throws UnmappedAddressException, UnalignedAddressException {
        long value = readAddressValue(address);
        return (value == 0 ? null : new LinuxAddress(this, value));
    }
    public LinuxAddress readCompOopAddress(long address)
            throws UnmappedAddressException, UnalignedAddressException {
        long value = readCompOopAddressValue(address);
        return (value == 0 ? null : new LinuxAddress(this, value));
    }

    public LinuxAddress readCompKlassAddress(long address)
            throws UnmappedAddressException, UnalignedAddressException {
        long value = readCompKlassAddressValue(address);
        return (value == 0 ? null : new LinuxAddress(this, value));
    }

    /** From the LinuxDebugger interface */
    public LinuxOopHandle readOopHandle(long address)
            throws UnmappedAddressException, UnalignedAddressException,
                NotInHeapException {
        long value = readAddressValue(address);
        return (value == 0 ? null : new LinuxOopHandle(this, value));
    }
    public LinuxOopHandle readCompOopHandle(long address)
            throws UnmappedAddressException, UnalignedAddressException,
                NotInHeapException {
        long value = readCompOopAddressValue(address);
        return (value == 0 ? null : new LinuxOopHandle(this, value));
    }

    //----------------------------------------------------------------------
    // Thread context access
    //

    public synchronized long[] getThreadIntegerRegisterSet(int lwp_id)
                                            throws DebuggerException {
        requireAttach();
        if (isCore) {
            return getThreadIntegerRegisterSet0(lwp_id);
        } else {
            class GetThreadIntegerRegisterSetTask implements WorkerThreadTask {
                int lwp_id;
                long[] result;
                public void doit(LinuxDebuggerLocal debugger) {
                    result = debugger.getThreadIntegerRegisterSet0(lwp_id);
                }
            }

            GetThreadIntegerRegisterSetTask task = new GetThreadIntegerRegisterSetTask();
            task.lwp_id = lwp_id;
            workerThread.execute(task);
            return task.result;
        }
    }

    /** Need to override this to relax alignment checks on x86. */
    public long readCInteger(long address, long numBytes, boolean isUnsigned)
        throws UnmappedAddressException, UnalignedAddressException {
        // Only slightly relaxed semantics -- this is a hack, but is
        // necessary on x86 where it seems the compiler is
        // putting some global 64-bit data on 32-bit boundaries
        if (numBytes == 8) {
            utils.checkAlignment(address, 4);
        } else {
            utils.checkAlignment(address, numBytes);
        }
        byte[] data = readBytes(address, numBytes);
        return utils.dataToCInteger(data, isUnsigned);
    }

    // Overridden from DebuggerBase because we need to relax alignment
    // constraints on x86
    public long readJLong(long address)
        throws UnmappedAddressException, UnalignedAddressException {
        utils.checkAlignment(address, jintSize);
        byte[] data = readBytes(address, jlongSize);
        return utils.dataToJLong(data, jlongSize);
    }

    //----------------------------------------------------------------------
    // Address access. Can not be package private, but should only be
    // accessed by the architecture-specific subpackages.

    /** From the LinuxDebugger interface */
    public long getAddressValue(Address addr) {
      if (addr == null) return 0;
      return ((LinuxAddress) addr).getValue();
    }

    /** From the LinuxDebugger interface */
    public Address newAddress(long value) {
      if (value == 0) return null;
      return new LinuxAddress(this, value);
    }

    /** From the LinuxCDebugger interface */
    public List/*<ThreadProxy>*/ getThreadList() {
      requireAttach();
      return threadList;
    }

    /** From the LinuxCDebugger interface */
    public List/*<LoadObject>*/ getLoadObjectList() {
      requireAttach();
      return loadObjectList;
    }

    /** From the LinuxCDebugger interface */
    public synchronized ClosestSymbol lookup(long addr) {
       requireAttach();
       if (isCore) {
          return lookupByAddress0(addr);
       } else {
          class LookupByAddressTask implements WorkerThreadTask {
             long addr;
             ClosestSymbol result;

             public void doit(LinuxDebuggerLocal debugger) {
                 result = debugger.lookupByAddress0(addr);
             }
          }

          LookupByAddressTask task = new LookupByAddressTask();
          task.addr = addr;
          workerThread.execute(task);
          return task.result;
       }
    }

    public CDebugger getCDebugger() {
      if (cdbg == null) {
         String cpu = getCPU();
         if (cpu.equals("ia64") ) {
            // IA-64 is not supported because of stack-walking issues
            return null;
         }
         cdbg = new LinuxCDebugger(this);
      }
      return cdbg;
    }

    /** This reads bytes from the remote process. */
    public synchronized ReadResult readBytesFromProcess(long address,
            long numBytes) throws UnmappedAddressException, DebuggerException {
        requireAttach();
        if (isCore) {
            byte[] res = readBytesFromProcess0(address, numBytes);
            return (res != null)? new ReadResult(res) : new ReadResult(address);
        } else {
            class ReadBytesFromProcessTask implements WorkerThreadTask {
                long address, numBytes;
                ReadResult result;
                public void doit(LinuxDebuggerLocal debugger) {
                    byte[] res = debugger.readBytesFromProcess0(address, numBytes);
                    if (res != null)
                        result = new ReadResult(res);
                    else
                        result = new ReadResult(address);
                }
            }

            ReadBytesFromProcessTask task = new ReadBytesFromProcessTask();
            task.address = address;
            task.numBytes = numBytes;
            workerThread.execute(task);
            return task.result;
        }
    }

    public void writeBytesToProcess(long address, long numBytes, byte[] data)
        throws UnmappedAddressException, DebuggerException {
        // FIXME
        throw new DebuggerException("Unimplemented");
    }

    static {
        System.loadLibrary("saproc");
        init0();
    }
}
