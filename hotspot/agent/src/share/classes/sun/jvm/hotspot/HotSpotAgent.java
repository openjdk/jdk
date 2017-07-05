/*
 * Copyright (c) 2000, 2014, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot;

import java.rmi.RemoteException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import sun.jvm.hotspot.debugger.Debugger;
import sun.jvm.hotspot.debugger.DebuggerException;
import sun.jvm.hotspot.debugger.JVMDebugger;
import sun.jvm.hotspot.debugger.MachineDescription;
import sun.jvm.hotspot.debugger.MachineDescriptionAMD64;
import sun.jvm.hotspot.debugger.MachineDescriptionPPC64;
import sun.jvm.hotspot.debugger.MachineDescriptionAArch64;
import sun.jvm.hotspot.debugger.MachineDescriptionIA64;
import sun.jvm.hotspot.debugger.MachineDescriptionIntelX86;
import sun.jvm.hotspot.debugger.MachineDescriptionSPARC32Bit;
import sun.jvm.hotspot.debugger.MachineDescriptionSPARC64Bit;
import sun.jvm.hotspot.debugger.NoSuchSymbolException;
import sun.jvm.hotspot.debugger.bsd.BsdDebuggerLocal;
import sun.jvm.hotspot.debugger.linux.LinuxDebuggerLocal;
import sun.jvm.hotspot.debugger.proc.ProcDebuggerLocal;
import sun.jvm.hotspot.debugger.remote.RemoteDebugger;
import sun.jvm.hotspot.debugger.remote.RemoteDebuggerClient;
import sun.jvm.hotspot.debugger.remote.RemoteDebuggerServer;
import sun.jvm.hotspot.debugger.windbg.WindbgDebuggerLocal;
import sun.jvm.hotspot.runtime.VM;
import sun.jvm.hotspot.types.TypeDataBase;
import sun.jvm.hotspot.utilities.PlatformInfo;
import sun.jvm.hotspot.utilities.UnsupportedPlatformException;

/** <P> This class wraps much of the basic functionality and is the
 * highest-level factory for VM data structures. It makes it simple
 * to start up the debugging system. </P>
 *
 * <P> FIXME: especially with the addition of remote debugging, this
 * has turned into a mess; needs rethinking. </P>
 */

public class HotSpotAgent {
    private JVMDebugger debugger;
    private MachineDescription machDesc;
    private TypeDataBase db;

    private String os;
    private String cpu;

    // The system can work in several ways:
    //  - Attaching to local process
    //  - Attaching to local core file
    //  - Connecting to remote debug server
    //  - Starting debug server for process
    //  - Starting debug server for core file

    // These are options for the "client" side of things
    private static final int PROCESS_MODE   = 0;
    private static final int CORE_FILE_MODE = 1;
    private static final int REMOTE_MODE    = 2;
    private int startupMode;

    // This indicates whether we are really starting a server or not
    private boolean isServer;

    // All possible required information for connecting
    private int pid;
    private String javaExecutableName;
    private String coreFileName;
    private String debugServerID;

    // All needed information for server side
    private String serverID;

    private String[] jvmLibNames;

    static void showUsage() {
    }

    public HotSpotAgent() {
        // for non-server add shutdown hook to clean-up debugger in case
        // of forced exit. For remote server, shutdown hook is added by
        // DebugServer.
        Runtime.getRuntime().addShutdownHook(new java.lang.Thread(
        new Runnable() {
            public void run() {
                synchronized (HotSpotAgent.this) {
                    if (!isServer) {
                        detach();
                    }
                }
            }
        }));
    }

    //--------------------------------------------------------------------------------
    // Accessors (once the system is set up)
    //

    public synchronized Debugger getDebugger() {
        return debugger;
    }

    public synchronized TypeDataBase getTypeDataBase() {
        return db;
    }

    //--------------------------------------------------------------------------------
    // Client-side operations
    //

    /** This attaches to a process running on the local machine. */
    public synchronized void attach(int processID)
    throws DebuggerException {
        if (debugger != null) {
            throw new DebuggerException("Already attached");
        }
        pid = processID;
        startupMode = PROCESS_MODE;
        isServer = false;
        go();
    }

    /** This opens a core file on the local machine */
    public synchronized void attach(String javaExecutableName, String coreFileName)
    throws DebuggerException {
        if (debugger != null) {
            throw new DebuggerException("Already attached");
        }
        if ((javaExecutableName == null) || (coreFileName == null)) {
            throw new DebuggerException("Both the core file name and Java executable name must be specified");
        }
        this.javaExecutableName = javaExecutableName;
        this.coreFileName = coreFileName;
        startupMode = CORE_FILE_MODE;
        isServer = false;
        go();
    }

    /** This uses a JVMDebugger that is already attached to the core or process */
    public synchronized void attach(JVMDebugger d)
    throws DebuggerException {
        debugger = d;
        isServer = false;
        go();
    }

    /** This attaches to a "debug server" on a remote machine; this
      remote server has already attached to a process or opened a
      core file and is waiting for RMI calls on the Debugger object to
      come in. */
    public synchronized void attach(String remoteServerID)
    throws DebuggerException {
        if (debugger != null) {
            throw new DebuggerException("Already attached to a process");
        }
        if (remoteServerID == null) {
            throw new DebuggerException("Debug server id must be specified");
        }

        debugServerID = remoteServerID;
        startupMode = REMOTE_MODE;
        isServer = false;
        go();
    }

    /** This should only be called by the user on the client machine,
      not the server machine */
    public synchronized boolean detach() throws DebuggerException {
        if (isServer) {
            throw new DebuggerException("Should not call detach() for server configuration");
        }
        return detachInternal();
    }

    //--------------------------------------------------------------------------------
    // Server-side operations
    //

    /** This attaches to a process running on the local machine and
      starts a debug server, allowing remote machines to connect and
      examine this process. Uses specified name to uniquely identify a
      specific debuggee on the server */
    public synchronized void startServer(int processID, String uniqueID) {
        if (debugger != null) {
            throw new DebuggerException("Already attached");
        }
        pid = processID;
        startupMode = PROCESS_MODE;
        isServer = true;
        serverID = uniqueID;
        go();
    }

    /** This attaches to a process running on the local machine and
      starts a debug server, allowing remote machines to connect and
      examine this process. */
    public synchronized void startServer(int processID)
    throws DebuggerException {
        startServer(processID, null);
    }

    /** This opens a core file on the local machine and starts a debug
      server, allowing remote machines to connect and examine this
      core file. Uses supplied uniqueID to uniquely identify a specific
      debugee */
    public synchronized void startServer(String javaExecutableName,
    String coreFileName,
    String uniqueID) {
        if (debugger != null) {
            throw new DebuggerException("Already attached");
        }
        if ((javaExecutableName == null) || (coreFileName == null)) {
            throw new DebuggerException("Both the core file name and Java executable name must be specified");
        }
        this.javaExecutableName = javaExecutableName;
        this.coreFileName = coreFileName;
        startupMode = CORE_FILE_MODE;
        isServer = true;
        serverID = uniqueID;
        go();
    }

    /** This opens a core file on the local machine and starts a debug
      server, allowing remote machines to connect and examine this
      core file. */
    public synchronized void startServer(String javaExecutableName, String coreFileName)
    throws DebuggerException {
        startServer(javaExecutableName, coreFileName, null);
    }

    /** This may only be called on the server side after startServer()
      has been called */
    public synchronized boolean shutdownServer() throws DebuggerException {
        if (!isServer) {
            throw new DebuggerException("Should not call shutdownServer() for client configuration");
        }
        return detachInternal();
    }


    //--------------------------------------------------------------------------------
    // Internals only below this point
    //

    private boolean detachInternal() {
        if (debugger == null) {
            return false;
        }
        boolean retval = true;
        if (!isServer) {
            VM.shutdown();
        }
        // We must not call detach() if we are a client and are connected
        // to a remote debugger
        Debugger dbg = null;
        DebuggerException ex = null;
        if (isServer) {
            try {
                RMIHelper.unbind(serverID);
            }
            catch (DebuggerException de) {
                ex = de;
            }
            dbg = debugger;
        } else {
            if (startupMode != REMOTE_MODE) {
                dbg = debugger;
            }
        }
        if (dbg != null) {
            retval = dbg.detach();
        }

        debugger = null;
        machDesc = null;
        db = null;
        if (ex != null) {
            throw(ex);
        }
        return retval;
    }

    private void go() {
        setupDebugger();
        setupVM();
    }

    private void setupDebugger() {
        if (startupMode != REMOTE_MODE) {
            //
            // Local mode (client attaching to local process or setting up
            // server, but not client attaching to server)
            //

            // Handle existing or alternate JVMDebugger:
            // these will set os, cpu independently of our PlatformInfo implementation.
            String alternateDebugger = System.getProperty("sa.altDebugger");
            if (debugger != null) {
                setupDebuggerExisting();

            } else if (alternateDebugger != null) {
                setupDebuggerAlternate(alternateDebugger);

            } else {
                // Otherwise, os, cpu are those of our current platform:
                try {
                    os  = PlatformInfo.getOS();
                    cpu = PlatformInfo.getCPU();
                } catch (UnsupportedPlatformException e) {
                   throw new DebuggerException(e);
                }
                if (os.equals("solaris")) {
                    setupDebuggerSolaris();
                } else if (os.equals("win32")) {
                    setupDebuggerWin32();
                } else if (os.equals("linux")) {
                    setupDebuggerLinux();
                } else if (os.equals("bsd")) {
                    setupDebuggerBsd();
                } else if (os.equals("darwin")) {
                    setupDebuggerDarwin();
                } else {
                    // Add support for more operating systems here
                    throw new DebuggerException("Operating system " + os + " not yet supported");
                }
            }

            if (isServer) {
                RemoteDebuggerServer remote = null;
                try {
                    remote = new RemoteDebuggerServer(debugger);
                }
                catch (RemoteException rem) {
                    throw new DebuggerException(rem);
                }
                RMIHelper.rebind(serverID, remote);
            }
        } else {
            //
            // Remote mode (client attaching to server)
            //

            // Create and install a security manager

            // FIXME: currently commented out because we were having
            // security problems since we're "in the sun.* hierarchy" here.
            // Perhaps a permissive policy file would work around this. In
            // the long run, will probably have to move into com.sun.*.

            //    if (System.getSecurityManager() == null) {
            //      System.setSecurityManager(new RMISecurityManager());
            //    }

            connectRemoteDebugger();
        }
    }

    private void setupVM() {
        // We need to instantiate a HotSpotTypeDataBase on both the client
        // and server machine. On the server it is only currently used to
        // configure the Java primitive type sizes (which we should
        // consider making constant). On the client it is used to
        // configure the VM.

        try {
            if (os.equals("solaris")) {
                db = new HotSpotTypeDataBase(machDesc,
                new HotSpotSolarisVtblAccess(debugger, jvmLibNames),
                debugger, jvmLibNames);
            } else if (os.equals("win32")) {
                db = new HotSpotTypeDataBase(machDesc,
                new Win32VtblAccess(debugger, jvmLibNames),
                debugger, jvmLibNames);
            } else if (os.equals("linux")) {
                db = new HotSpotTypeDataBase(machDesc,
                new LinuxVtblAccess(debugger, jvmLibNames),
                debugger, jvmLibNames);
            } else if (os.equals("bsd")) {
                db = new HotSpotTypeDataBase(machDesc,
                new BsdVtblAccess(debugger, jvmLibNames),
                debugger, jvmLibNames);
            } else if (os.equals("darwin")) {
                db = new HotSpotTypeDataBase(machDesc,
                new BsdVtblAccess(debugger, jvmLibNames),
                debugger, jvmLibNames);
            } else {
                throw new DebuggerException("OS \"" + os + "\" not yet supported (no VtblAccess yet)");
            }
        }
        catch (NoSuchSymbolException e) {
            throw new DebuggerException("Doesn't appear to be a HotSpot VM (could not find symbol \"" +
            e.getSymbol() + "\" in remote process)");
        }

        if (startupMode != REMOTE_MODE) {
            // Configure the debugger with the primitive type sizes just obtained from the VM
            debugger.configureJavaPrimitiveTypeSizes(db.getJBooleanType().getSize(),
            db.getJByteType().getSize(),
            db.getJCharType().getSize(),
            db.getJDoubleType().getSize(),
            db.getJFloatType().getSize(),
            db.getJIntType().getSize(),
            db.getJLongType().getSize(),
            db.getJShortType().getSize());
        }

        if (!isServer) {
            // Do not initialize the VM on the server (unnecessary, since it's
            // instantiated on the client)
            try {
                VM.initialize(db, debugger);
            } catch (DebuggerException e) {
                throw (e);
            } catch (Exception e) {
                throw new DebuggerException(e);
            }
        }
    }

    //--------------------------------------------------------------------------------
    // OS-specific debugger setup/connect routines
    //

    // Use the existing JVMDebugger, as passed to our constructor.
    // Retrieve os and cpu from that debugger, not the current platform.
    private void setupDebuggerExisting() {

        os = debugger.getOS();
        cpu = debugger.getCPU();
        setupJVMLibNames(os);
        machDesc = debugger.getMachineDescription();
    }

    // Given a classname, load an alternate implementation of JVMDebugger.
    private void setupDebuggerAlternate(String alternateName) {

        try {
            Class c = Class.forName(alternateName);
            Constructor cons = c.getConstructor();
            debugger = (JVMDebugger) cons.newInstance();
            attachDebugger();
            setupDebuggerExisting();

        } catch (ClassNotFoundException cnfe) {
            throw new DebuggerException("Cannot find alternate SA Debugger: '" + alternateName + "'");
        } catch (NoSuchMethodException nsme) {
            throw new DebuggerException("Alternate SA Debugger: '" + alternateName + "' has missing constructor.");
        } catch (InstantiationException ie) {
            throw new DebuggerException("Alternate SA Debugger: '" + alternateName + "' fails to initialise: ", ie);
        } catch (IllegalAccessException iae) {
            throw new DebuggerException("Alternate SA Debugger: '" + alternateName + "' fails to initialise: ", iae);
        } catch (InvocationTargetException iae) {
            throw new DebuggerException("Alternate SA Debugger: '" + alternateName + "' fails to initialise: ", iae);
        }

        System.err.println("Loaded alternate HotSpot SA Debugger: " + alternateName);
    }

    //
    // Solaris
    //

    private void setupDebuggerSolaris() {
        setupJVMLibNamesSolaris();
        ProcDebuggerLocal dbg = new ProcDebuggerLocal(null, true);
        debugger = dbg;
        attachDebugger();

        // Set up CPU-dependent stuff
        if (cpu.equals("x86")) {
            machDesc = new MachineDescriptionIntelX86();
        } else if (cpu.equals("sparc")) {
            int addressSize = dbg.getRemoteProcessAddressSize();
            if (addressSize == -1) {
                throw new DebuggerException("Error occurred while trying to determine the remote process's " +
                                            "address size");
            }

            if (addressSize == 32) {
                machDesc = new MachineDescriptionSPARC32Bit();
            } else if (addressSize == 64) {
                machDesc = new MachineDescriptionSPARC64Bit();
            } else {
                throw new DebuggerException("Address size " + addressSize + " is not supported on SPARC");
            }
        } else if (cpu.equals("amd64")) {
            machDesc = new MachineDescriptionAMD64();
        } else {
            throw new DebuggerException("Solaris only supported on sparc/sparcv9/x86/amd64");
        }

        dbg.setMachineDescription(machDesc);
        return;
    }

    private void connectRemoteDebugger() throws DebuggerException {
        RemoteDebugger remote =
        (RemoteDebugger) RMIHelper.lookup(debugServerID);
        debugger = new RemoteDebuggerClient(remote);
        machDesc = ((RemoteDebuggerClient) debugger).getMachineDescription();
        os = debugger.getOS();
        setupJVMLibNames(os);
        cpu = debugger.getCPU();
    }

    private void setupJVMLibNames(String os) {
        if (os.equals("solaris")) {
            setupJVMLibNamesSolaris();
        } else if (os.equals("win32")) {
            setupJVMLibNamesWin32();
        } else if (os.equals("linux")) {
            setupJVMLibNamesLinux();
        } else if (os.equals("bsd")) {
            setupJVMLibNamesBsd();
        } else if (os.equals("darwin")) {
            setupJVMLibNamesDarwin();
        } else {
            throw new RuntimeException("Unknown OS type");
        }
    }

    private void setupJVMLibNamesSolaris() {
        jvmLibNames = new String[] { "libjvm.so" };
    }

    //
    // Win32
    //

    private void setupDebuggerWin32() {
        setupJVMLibNamesWin32();

        if (cpu.equals("x86")) {
            machDesc = new MachineDescriptionIntelX86();
        } else if (cpu.equals("amd64")) {
            machDesc = new MachineDescriptionAMD64();
        } else if (cpu.equals("ia64")) {
            machDesc = new MachineDescriptionIA64();
        } else {
            throw new DebuggerException("Win32 supported under x86, amd64 and ia64 only");
        }

        // Note we do not use a cache for the local debugger in server
        // mode; it will be taken care of on the client side (once remote
        // debugging is implemented).

        debugger = new WindbgDebuggerLocal(machDesc, !isServer);

        attachDebugger();

        // FIXME: add support for server mode
    }

    private void setupJVMLibNamesWin32() {
        jvmLibNames = new String[] { "jvm.dll" };
    }

    //
    // Linux
    //

    private void setupDebuggerLinux() {
        setupJVMLibNamesLinux();

        if (cpu.equals("x86")) {
            machDesc = new MachineDescriptionIntelX86();
        } else if (cpu.equals("ia64")) {
            machDesc = new MachineDescriptionIA64();
        } else if (cpu.equals("amd64")) {
            machDesc = new MachineDescriptionAMD64();
        } else if (cpu.equals("ppc64")) {
            machDesc = new MachineDescriptionPPC64();
        } else if (cpu.equals("aarch64")) {
            machDesc = new MachineDescriptionAArch64();
        } else if (cpu.equals("sparc")) {
            if (LinuxDebuggerLocal.getAddressSize()==8) {
                    machDesc = new MachineDescriptionSPARC64Bit();
            } else {
                    machDesc = new MachineDescriptionSPARC32Bit();
            }
        } else {
          try {
            machDesc = (MachineDescription)
              Class.forName("sun.jvm.hotspot.debugger.MachineDescription" +
                            cpu.toUpperCase()).newInstance();
          } catch (Exception e) {
            throw new DebuggerException("Linux not supported on machine type " + cpu);
          }
        }

        LinuxDebuggerLocal dbg =
        new LinuxDebuggerLocal(machDesc, !isServer);
        debugger = dbg;

        attachDebugger();
    }

    private void setupJVMLibNamesLinux() {
        jvmLibNames = new String[] { "libjvm.so" };
    }

    //
    // BSD
    //

    private void setupDebuggerBsd() {
        setupJVMLibNamesBsd();

        if (cpu.equals("x86")) {
            machDesc = new MachineDescriptionIntelX86();
        } else if (cpu.equals("amd64") || cpu.equals("x86_64")) {
            machDesc = new MachineDescriptionAMD64();
        } else {
            throw new DebuggerException("BSD only supported on x86/x86_64. Current arch: " + cpu);
        }

        BsdDebuggerLocal dbg = new BsdDebuggerLocal(machDesc, !isServer);
        debugger = dbg;

        attachDebugger();
    }

    private void setupJVMLibNamesBsd() {
        jvmLibNames = new String[] { "libjvm.so" };
    }

    //
    // Darwin
    //

    private void setupDebuggerDarwin() {
        setupJVMLibNamesDarwin();

        if (cpu.equals("amd64") || cpu.equals("x86_64")) {
            machDesc = new MachineDescriptionAMD64();
        } else {
            throw new DebuggerException("Darwin only supported on x86_64. Current arch: " + cpu);
        }

        BsdDebuggerLocal dbg = new BsdDebuggerLocal(machDesc, !isServer);
        debugger = dbg;

        attachDebugger();
    }

    private void setupJVMLibNamesDarwin() {
        jvmLibNames = new String[] { "libjvm.dylib" };
    }

    /** Convenience routine which should be called by per-platform
      debugger setup. Should not be called when startupMode is
      REMOTE_MODE. */
    private void attachDebugger() {
        if (startupMode == PROCESS_MODE) {
            debugger.attach(pid);
        } else if (startupMode == CORE_FILE_MODE) {
            debugger.attach(javaExecutableName, coreFileName);
        } else {
            throw new DebuggerException("Should not call attach() for startupMode == " + startupMode);
        }
    }
}
