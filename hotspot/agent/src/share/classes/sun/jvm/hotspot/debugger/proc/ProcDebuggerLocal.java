/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.debugger.proc;

import java.io.*;
import java.net.*;
import java.util.*;
import java.lang.reflect.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.debugger.cdbg.*;
import sun.jvm.hotspot.debugger.proc.amd64.*;
import sun.jvm.hotspot.debugger.proc.aarch64.*;
import sun.jvm.hotspot.debugger.proc.sparc.*;
import sun.jvm.hotspot.debugger.proc.ppc64.*;
import sun.jvm.hotspot.debugger.proc.x86.*;
import sun.jvm.hotspot.debugger.ppc64.*;
import sun.jvm.hotspot.debugger.amd64.*;
import sun.jvm.hotspot.debugger.aarch64.*;
import sun.jvm.hotspot.debugger.sparc.*;
import sun.jvm.hotspot.debugger.x86.*;
import sun.jvm.hotspot.utilities.*;

/** <P> An implementation of the JVMDebugger interface which sits on
 * top of proc and relies on the SA's proc import module for
 * communication with the debugger. </P>
 *
 * <P> <B>NOTE</B> that since we have the notion of fetching "Java
 * primitive types" from the remote process (which might have
 * different sizes than we expect) we have a bootstrapping
 * problem. We need to know the sizes of these types before we can
 * fetch them. The current implementation solves this problem by
 * requiring that it be configured with these type sizes before they
 * can be fetched. The readJ(Type) routines here will throw a
 * RuntimeException if they are called before the debugger is
 * configured with the Java primitive type sizes. </P>
 */

public class ProcDebuggerLocal extends DebuggerBase implements ProcDebugger {
    protected static final int cacheSize = 16 * 1024 * 1024; // 16 MB

    //------------------------------------------------------------------------
    // Implementation of Debugger interface
    //

    /** <P> machDesc may be null if it couldn't be determined yet; i.e.,
     * if we're on SPARC, we need to ask the remote process whether
     * we're in 32- or 64-bit mode. </P>
     *
     * <P> useCache should be set to true if debugging is being done
     * locally, and to false if the debugger is being created for the
     * purpose of supporting remote debugging. </P> */
    public ProcDebuggerLocal(MachineDescription machDesc, boolean useCache) {
        this.machDesc = machDesc;
        int cacheNumPages;
        int cachePageSize;

        final String cpu = PlatformInfo.getCPU();
        if (cpu.equals("sparc")) {
            threadFactory = new ProcSPARCThreadFactory(this);
            pcRegIndex = SPARCThreadContext.R_PC;
            fpRegIndex = SPARCThreadContext.R_I6;
        } else if (cpu.equals("x86")) {
            threadFactory = new ProcX86ThreadFactory(this);
            pcRegIndex = X86ThreadContext.EIP;
            fpRegIndex = X86ThreadContext.EBP;
            unalignedAccessesOkay = true;
        } else if (cpu.equals("amd64") || cpu.equals("x86_64")) {
            threadFactory = new ProcAMD64ThreadFactory(this);
            pcRegIndex = AMD64ThreadContext.RIP;
            fpRegIndex = AMD64ThreadContext.RBP;
        } else if (cpu.equals("aarch64")) {
            threadFactory = new ProcAARCH64ThreadFactory(this);
            pcRegIndex = AARCH64ThreadContext.PC;
            fpRegIndex = AARCH64ThreadContext.FP;
        } else if (cpu.equals("ppc64")) {
            threadFactory = new ProcPPC64ThreadFactory(this);
            pcRegIndex = PPC64ThreadContext.PC;
            fpRegIndex = PPC64ThreadContext.SP;
        } else {
          try {
            Class tfc = Class.forName("sun.jvm.hotspot.debugger.proc." +
               cpu.toLowerCase() + ".Proc" + cpu.toUpperCase() +
               "ThreadFactory");
            Constructor[] ctfc = tfc.getConstructors();
            threadFactory = (ProcThreadFactory)ctfc[0].newInstance(this);
          } catch (Exception e) {
            throw new RuntimeException("Thread access for CPU architecture " + PlatformInfo.getCPU() + " not yet supported");
            // Note: pcRegIndex and fpRegIndex do not appear to be referenced
          }
        }
        if (useCache) {
            // Cache portion of the remote process's address space.
            // For now, this cache works best if it covers the entire
            // heap of the remote process. FIXME: at least should make this
            // tunable from the outside, i.e., via the UI. This is a 16 MB
            // cache divided on SPARC into 2048 8K pages and on x86 into
            // 4096 4K pages; the page size must be adjusted to be the OS's
            // page size.

            cachePageSize = getPageSize();
            cacheNumPages = parseCacheNumPagesProperty(cacheSize / cachePageSize);
            initCache(cachePageSize, cacheNumPages);
        }

        resetNativePointers();
        clearCacheFields();
    }

    /** FIXME: implement this with a Runtime.exec() of ps followed by
     * parsing of its output */
    public boolean hasProcessList() throws DebuggerException {
        return false;
    }

    public List getProcessList() throws DebuggerException {
        throw new DebuggerException("Not yet supported");
    }


    /** From the Debugger interface via JVMDebugger */
    public synchronized void attach(int processID) throws DebuggerException {
        checkAttached();
        isCore = false;
        attach0(new Integer(processID).toString());
        attached = true;
        suspended = true;
    }

    /** From the Debugger interface via JVMDebugger */
    public synchronized void attach
    (String executableName, String coreFileName) throws DebuggerException {
        checkAttached();
        isCore = true;
        topFrameCache = new HashMap();
        attach0(executableName, coreFileName);
        attached = true;
        suspended = true;
    }

    /** From the Debugger interface via JVMDebugger */
    public synchronized boolean detach() {
        if (! attached) {
            return false;
        }

        try {
            if (p_ps_prochandle == 0L) {
                return false;
            }
            detach0();
            clearCache();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            resetNativePointers();
            clearCacheFields();
            suspended = false;
            attached = false;
        }
    }

    public synchronized void suspend() throws DebuggerException {
        requireAttach();
        if (suspended) {
            throw new DebuggerException("Process already suspended");
        }
        suspend0();
        suspended = true;
        enableCache();
        reresolveLoadObjects();
    }

    public synchronized void resume() throws DebuggerException {
        requireAttach();
        if (!suspended) {
            throw new DebuggerException("Process not suspended");
        }
        resume0();
        disableCache();
        suspended = false;
    }

    public synchronized boolean isSuspended() throws DebuggerException {
        requireAttach();
        return suspended;
    }

    /** From the Debugger interface via JVMDebugger */
    public Address parseAddress(String addressString) throws NumberFormatException {
        long addr = utils.scanAddress(addressString);
        if (addr == 0) {
            return null;
        }
        return new ProcAddress(this, addr);
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
        throw new DebuggerException("Can't execute console commands");
    }

    public String getConsolePrompt() throws DebuggerException {
        return "";
    }

    public CDebugger getCDebugger() throws DebuggerException {
        if (cdbg == null) {
            cdbg = new ProcCDebugger(this);
        }
        return cdbg;
    }

    /** From the SymbolLookup interface via Debugger and JVMDebugger */
    public synchronized Address lookup(String objectName, String symbol) {
        requireAttach();
        long addr = lookupByName0(objectName, symbol);
        if (addr == 0) {
            return null;
        }
        return new ProcAddress(this, addr);
    }

    /** From the SymbolLookup interface via Debugger and JVMDebugger */
    public synchronized OopHandle lookupOop(String objectName, String symbol) {
        Address addr = lookup(objectName, symbol);
        if (addr == null) {
            return null;
        }
        return addr.addOffsetToAsOopHandle(0);
    }

    /** From the ProcDebugger interface */
    public MachineDescription getMachineDescription() {
        return machDesc;
    }

    /** Internal routine supporting lazy setting of MachineDescription,
     * since on SPARC we will need to query the remote process to ask
     * it what its data model is (32- or 64-bit).
     */

    public void setMachineDescription(MachineDescription machDesc) {
        this.machDesc = machDesc;
        setBigEndian(machDesc.isBigEndian());
        utils = new DebuggerUtilities(machDesc.getAddressSize(), machDesc.isBigEndian());
    }

    public synchronized int getRemoteProcessAddressSize()
    throws DebuggerException {
        requireAttach();
        return getRemoteProcessAddressSize0();
    }

    //--------------------------------------------------------------------------------
    // Implementation of ThreadAccess interface
    //

    /** From the ThreadAccess interface via Debugger and JVMDebugger */
    public ThreadProxy getThreadForIdentifierAddress(Address addr) {
        return threadFactory.createThreadWrapper(addr);
    }

    public ThreadProxy getThreadForThreadId(long id) {
        return threadFactory.createThreadWrapper(id);
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
        if (unalignedAccessesOkay) {
            utils.checkAlignment(address, jintSize);
        } else {
            utils.checkAlignment(address, jlongSize);
        }
        byte[] data = readBytes(address, jlongSize);
        return utils.dataToJLong(data, jlongSize);
    }

    //--------------------------------------------------------------------------------
    // Internal routines (for implementation of ProcAddress).
    // These must not be called until the MachineDescription has been set up.
    //

    /** From the ProcDebugger interface */
    public String addressValueToString(long address) {
        return utils.addressValueToString(address);
    }

    /** Need to override this to relax alignment checks on Solaris/x86. */
    public long readCInteger(long address, long numBytes, boolean isUnsigned)
    throws UnmappedAddressException, UnalignedAddressException {
        checkConfigured();
        if (!unalignedAccessesOkay) {
            utils.checkAlignment(address, numBytes);
        } else {
            // Only slightly relaxed semantics -- this is a hack, but is
            // necessary on Solaris/x86 where it seems the compiler is
            // putting some global 64-bit data on 32-bit boundaries
            if (numBytes == 8) {
                utils.checkAlignment(address, 4);
            } else {
                utils.checkAlignment(address, numBytes);
            }
        }
        byte[] data = readBytes(address, numBytes);
        return utils.dataToCInteger(data, isUnsigned);
    }

    /** From the ProcDebugger interface */
    public ProcAddress readAddress(long address)
    throws UnmappedAddressException, UnalignedAddressException {
        long value = readAddressValue(address);
        return (value == 0 ? null : new ProcAddress(this, value));
    }

    public ProcAddress readCompOopAddress(long address)
    throws UnmappedAddressException, UnalignedAddressException {
        long value = readCompOopAddressValue(address);
        return (value == 0 ? null : new ProcAddress(this, value));
    }

    public ProcAddress readCompKlassAddress(long address)
    throws UnmappedAddressException, UnalignedAddressException {
        long value = readCompKlassAddressValue(address);
        return (value == 0 ? null : new ProcAddress(this, value));
    }

    /** From the ProcDebugger interface */
    public ProcOopHandle readOopHandle(long address)
    throws UnmappedAddressException, UnalignedAddressException, NotInHeapException {
        long   value = readAddressValue(address);
        return (value == 0 ? null : new ProcOopHandle(this, value));
    }

    public ProcOopHandle readCompOopHandle(long address) {
        long value = readCompOopAddressValue(address);
        return (value == 0 ? null : new ProcOopHandle(this, value));
    }

    public void writeBytesToProcess(long address, long numBytes, byte[] data)
    throws UnmappedAddressException, DebuggerException {
        if (isCore) {
            throw new DebuggerException("Attached to a core file!");
        }
        writeBytesToProcess0(address, numBytes, data);
    }

    public synchronized ReadResult readBytesFromProcess(long address, long numBytes)
    throws DebuggerException {
        requireAttach();
        byte[] res = readBytesFromProcess0(address, numBytes);
        if(res != null)
            return new ReadResult(res);
        else
            return new ReadResult(address);
    }

    protected int getPageSize() {
        int pagesize = getPageSize0();
        if (pagesize == -1) {
            // return the hard coded default value.
            if (PlatformInfo.getCPU().equals("sparc") ||
                PlatformInfo.getCPU().equals("amd64") )
               pagesize = 8196;
            else
               pagesize = 4096;
        }
        return pagesize;
    }

    //--------------------------------------------------------------------------------
    // Thread context access. Can not be package private, but should
    // only be accessed by the architecture-specific subpackages.

    /** From the ProcDebugger interface. May have to redefine this later. */
    public synchronized long[] getThreadIntegerRegisterSet(int tid) {
        requireAttach();
        return getThreadIntegerRegisterSet0(tid);
    }

    //--------------------------------------------------------------------------------
    // Address access. Can not be package private, but should only be
    // accessed by the architecture-specific subpackages.

    /** From the ProcDebugger interface */
    public long getAddressValue(Address addr) {
        if (addr == null) return 0;
        return ((ProcAddress) addr).getValue();
    }

    /** From the ProcDebugger interface */
    public Address newAddress(long value) {
        if (value == 0) return null;
        return new ProcAddress(this, value);
    }

    /** From the ProcDebugger interface */
    public synchronized List getThreadList() throws DebuggerException {
        requireAttach();
        List res = null;
        if (isCore && (threadListCache != null)) {
            res = threadListCache;
        } else {
            res = new ArrayList();
            fillThreadList0(res);
            if (isCore) {
                threadListCache = res;
            }
        }
        return res;
    }

    /** From the ProcDebugger interface */
    public synchronized List getLoadObjectList() throws DebuggerException {
        requireAttach();
        if (!suspended) {
            throw new DebuggerException("Process not suspended");
        }

        if (loadObjectCache == null) {
            updateLoadObjectCache();
        }
        return loadObjectCache;
    }

    /** From the ProcDebugger interface */
    public synchronized CFrame topFrameForThread(ThreadProxy thread)
    throws DebuggerException {
        requireAttach();
        CFrame res = null;
        if (isCore && ((res = (CFrame) topFrameCache.get(thread)) != null)) {
            return res;
        } else {
            ThreadContext context = thread.getContext();
            int numRegs = context.getNumRegisters();
            long[] regs = new long[numRegs];
            for (int i = 0; i < numRegs; i++) {
                regs[i] = context.getRegister(i);
            }
            res = fillCFrameList0(regs);
            if (isCore) {
                topFrameCache.put(thread, res);
            }
            return res;
        }
    }

    /** From the ProcDebugger interface */
    public synchronized ClosestSymbol lookup(long address) {
        requireAttach();
        return lookupByAddress0(address);
    }

    /** From the ProcDebugger interface */
    public String demangle(String name) {
        return demangle0(name);
    }

    //------------- Internals only below this point --------------------
    //
    //

    private void updateLoadObjectCache() {
        List res = new ArrayList();
        nameToDsoMap = new HashMap();
        fillLoadObjectList0(res);
        loadObjectCache = sortLoadObjects(res);
    }

    // sort load objects by base address
    private static List sortLoadObjects(List in) {
        // sort the list by base address
        Object[] arr = in.toArray();
        Arrays.sort(arr, loadObjectComparator);
        return Arrays.asList(arr);
    }

    private long lookupByName(String objectName, String symbolName)
    throws DebuggerException {
        // NOTE: this assumes that process is suspended (which is probably
        // necessary assumption given that DSOs can be loaded/unloaded as
        // process runs). Should update documentation.
        if (nameToDsoMap == null) {
            getLoadObjectList();
        }
        SharedObject dso = (SharedObject) nameToDsoMap.get(objectName);
        // The DSO can be null because we use this to search through known
        // DSOs in HotSpotTypeDataBase (for example)
        if (dso != null) {
            ProcAddress addr = (ProcAddress) dso.lookupSymbol(symbolName);
            if (addr != null) {
                return addr.getValue();
            }
        }
        return 0;
    }

    private SharedObject findDSOByName(String fullPathName) {
        if (loadObjectCache == null)
            return null;
        for (Iterator iter = loadObjectCache.iterator(); iter.hasNext(); ) {
            SharedObject dso = (SharedObject) iter.next();
            if (dso.getName().equals(fullPathName)) {
                return dso;
            }
        }
        return null;
    }

    private void reresolveLoadObjects() throws DebuggerException {
        if (loadObjectCache == null) {
            return;
        }
        updateLoadObjectCache();
    }


    private void checkAttached() {
        if (attached) {
            if (isCore) {
                throw new DebuggerException("already attached to a core file!");
            } else {
                throw new DebuggerException("already attached to a process!");
            }
        }
    }

    private void requireAttach() {
        if (! attached) {
            throw new RuntimeException("not attached to a process or core file!");
        }
    }

    private void clearCacheFields() {
        loadObjectCache = null;
        nameToDsoMap    = null;
        threadListCache = null;
        topFrameCache   = null;
    }

    private void resetNativePointers() {
        p_ps_prochandle          = 0L;

        // reset thread_db pointers
        libthread_db_handle    = 0L;
        p_td_thragent_t        = 0L;
        p_td_init              = 0L;
        p_td_ta_new            = 0L;
        p_td_ta_delete         = 0L;
        p_td_ta_thr_iter       = 0L;
        p_td_thr_get_info      = 0L;
        p_td_ta_map_id2thr     = 0L;
        p_td_thr_getgregs      = 0L;

        // part of class sharing workaround
        classes_jsa_fd         = -1;
        p_file_map_header      = 0L;
    }

    // native methods and native helpers

    // attach, detach
    private native void attach0(String pid) throws DebuggerException;
    private native void attach0(String executableFile, String coreFileName) throws DebuggerException;
    private native void detach0() throws DebuggerException;

    // address size, page size
    private native int getRemoteProcessAddressSize0() throws DebuggerException;
    private native int getPageSize0() throws DebuggerException;

    // threads, stacks
    private native long[] getThreadIntegerRegisterSet0(long tid) throws DebuggerException;
    private native void   fillThreadList0(List l) throws DebuggerException;

    // fills stack frame list given reg set of the top frame and top frame
    private native ProcCFrame fillCFrameList0(long[] regs) throws DebuggerException;

    // helper called by fillCFrameList0
    private ProcCFrame createSenderFrame(ProcCFrame f, long pc, long fp) {
        ProcCFrame sender = new ProcCFrame(this, newAddress(pc), newAddress(fp));
        if (f != null) {
            f.setSender(sender);
        }
        return sender;
    }

    // shared objects
    private native void fillLoadObjectList0(List l) throws DebuggerException;

    // helper called by fillLoadObjectList0
    private LoadObject createLoadObject(String fileName, long textsize, long base) {
        File f = new File(fileName);
        Address baseAddr = newAddress(base);
        SharedObject res = findDSOByName(fileName);
        if (res != null) {
            // already in cache. just change the base, if needed
            Address oldBase = res.getBase();
            if (! baseAddr.equals(oldBase)) {
                res.setBase(baseAddr);
            }
        } else {
            // new shared object.
            res = new SharedObject(this, fileName, f.length(), baseAddr);
        }
        nameToDsoMap.put(f.getName(), res);
        return res;
    }

    // symbol-to-pc
    private native long lookupByName0(String objectName, String symbolName) throws DebuggerException;
    private native ClosestSymbol lookupByAddress0(long address) throws DebuggerException;

    // helper called by lookupByAddress0
    private ClosestSymbol createClosestSymbol(String name, long offset) {
        return new ClosestSymbol(name, offset);
    }

    // process read/write
    private native byte[] readBytesFromProcess0(long address, long numBytes) throws DebuggerException;
    private native void writeBytesToProcess0(long address, long numBytes, byte[] data) throws DebuggerException;

    // process control
    private native void suspend0() throws DebuggerException;
    private native void resume0() throws DebuggerException;

    // demangle a C++ name
    private native String demangle0(String name);

    // init JNI ids to fields, methods
    private native static void initIDs() throws DebuggerException;
    private static LoadObjectComparator loadObjectComparator;

    static {
        System.loadLibrary("saproc");
        initIDs();
        loadObjectComparator = new LoadObjectComparator();
    }

    private boolean unalignedAccessesOkay;
    private ProcThreadFactory threadFactory;

    // indices of PC and FP registers in gregset
    private int pcRegIndex;
    private int fpRegIndex;

    // Symbol lookup support
    // This is a map of library names to DSOs
    private Map nameToDsoMap;  // Map<String, SharedObject>

    // C/C++ debugging support
    private List/*<LoadObject>*/ loadObjects;
    private CDebugger cdbg;

    // ProcessControl support
    private boolean suspended;

    // libproc handle
    private long p_ps_prochandle;

    // libthread.so's dlopen handle, thread agent
    // and function pointers
    private long libthread_db_handle;
    private long p_td_thragent_t;
    private long p_td_init;
    private long p_td_ta_new;
    private long p_td_ta_delete;
    private long p_td_ta_thr_iter;
    private long p_td_thr_get_info;
    private long p_td_ta_map_id2thr;
    private long p_td_thr_getgregs;

    // part of class sharing workaround
    private int classes_jsa_fd;
    private long p_file_map_header;

    private boolean attached = false;
    private boolean isCore;

    // for core files, we cache load object list, thread list, top frames etc.
    // for processes we cache load object list and sync. it during suspend.
    private List threadListCache;
    private List loadObjectCache;
    private Map  topFrameCache;      // Map<ThreadProxy, CFrame>
}
