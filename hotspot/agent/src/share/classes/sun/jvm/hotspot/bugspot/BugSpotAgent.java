/*
 * Copyright (c) 2002, 2006, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.bugspot;

import java.io.PrintStream;
import java.net.*;
import java.rmi.*;
import sun.jvm.hotspot.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.debugger.dbx.*;
import sun.jvm.hotspot.debugger.proc.*;
import sun.jvm.hotspot.debugger.cdbg.*;
import sun.jvm.hotspot.debugger.win32.*;
import sun.jvm.hotspot.debugger.windbg.*;
import sun.jvm.hotspot.debugger.linux.*;
import sun.jvm.hotspot.debugger.sparc.*;
import sun.jvm.hotspot.debugger.remote.*;
import sun.jvm.hotspot.livejvm.*;
import sun.jvm.hotspot.memory.*;
import sun.jvm.hotspot.oops.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.types.*;
import sun.jvm.hotspot.utilities.*;

/** <P> This class wraps the basic functionality for connecting to the
 * target process or debug server. It makes it simple to start up the
 * debugging system. </P>
 *
 * <P> This agent (as compared to the HotSpotAgent) can connect to
 * and interact with arbitrary processes. If the target process
 * happens to be a HotSpot JVM, the Java debugging features of the
 * Serviceability Agent are enabled. Further, if the Serviceability
 * Agent's JVMDI module is loaded into the target VM, interaction
 * with the live Java program is possible, specifically the catching
 * of exceptions and setting of breakpoints. </P>
 *
 * <P> The BugSpot debugger requires that the underlying Debugger
 * support C/C++ debugging via the CDebugger interface. </P>
 *
 * <P> FIXME: need to add a way to configure the paths to dbx and the
 * DSO from the outside. However, this should work for now for
 * internal use. </P>
 *
 * <P> FIXME: especially with the addition of remote debugging, this
 * has turned into a mess; needs rethinking. </P> */

public class BugSpotAgent {

    private JVMDebugger debugger;
    private MachineDescription machDesc;
    private TypeDataBase db;

    private String os;
    private String cpu;
    private String fileSep;

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
    private String executableName;
    private String coreFileName;
    private String debugServerID;

    // All needed information for server side
    private String serverID;

    // Indicates whether we are attached to a HotSpot JVM or not
    private boolean javaMode;

    // Indicates whether we have process control over a live HotSpot JVM
    // or not; non-null if so.
    private ServiceabilityAgentJVMDIModule jvmdi;
    // While handling C breakpoints interactivity with the Java program
    // is forbidden. Too many invariants are broken while the target is
    // stopped at a C breakpoint to risk making JVMDI calls.
    private boolean javaInteractionDisabled;

    private String[] jvmLibNames;
    private String[] saLibNames;

    // FIXME: make these configurable, i.e., via a dotfile; also
    // consider searching within the JDK from which this Java executable
    // comes to find them
    private static final String defaultDbxPathPrefix                = "/net/jano.eng/export/disk05/hotspot/sa";
    private static final String defaultDbxSvcAgentDSOPathPrefix     = "/net/jano.eng/export/disk05/hotspot/sa";

    private static final boolean DEBUG;
    static {
        DEBUG = System.getProperty("sun.jvm.hotspot.bugspot.BugSpotAgent.DEBUG")
        != null;
    }

    static void debugPrintln(String str) {
        if (DEBUG) {
            System.err.println(str);
        }
    }

    static void showUsage() {
        System.out.println("    You can also pass these -D options to java to specify where to find dbx and the \n" +
        "    Serviceability Agent plugin for dbx:");
        System.out.println("       -DdbxPathName=<path-to-dbx-executable>\n" +
        "             Default is derived from dbxPathPrefix");
        System.out.println("    or");
        System.out.println("       -DdbxPathPrefix=<xxx>\n" +
        "             where xxx is the path name of a dir structure that contains:\n" +
        "                   <os>/<arch>/bin/dbx\n" +
        "             The default is " + defaultDbxPathPrefix);
        System.out.println("    and");
        System.out.println("       -DdbxSvcAgentDSOPathName=<path-to-dbx-serviceability-agent-module>\n" +
        "             Default is determined from dbxSvcAgentDSOPathPrefix");
        System.out.println("    or");
        System.out.println("       -DdbxSvcAgentDSOPathPrefix=<xxx>\n" +
        "             where xxx is the pathname of a dir structure that contains:\n" +
        "                   <os>/<arch>/bin/lib/libsvc_agent_dbx.so\n" +
        "             The default is " + defaultDbxSvcAgentDSOPathPrefix);
    }

    public BugSpotAgent() {
        // for non-server add shutdown hook to clean-up debugger in case
        // of forced exit. For remote server, shutdown hook is added by
        // DebugServer.
        Runtime.getRuntime().addShutdownHook(new java.lang.Thread(
        new Runnable() {
            public void run() {
                synchronized (BugSpotAgent.this) {
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

    public synchronized CDebugger getCDebugger() {
        return getDebugger().getCDebugger();
    }

    public synchronized ProcessControl getProcessControl() {
        return getCDebugger().getProcessControl();
    }

    public synchronized TypeDataBase getTypeDataBase() {
        return db;
    }

    /** Indicates whether the target process is suspended
      completely. Equivalent to getProcessControl().isSuspended(). */
    public synchronized boolean isSuspended() throws DebuggerException {
        return getProcessControl().isSuspended();
    }

    /** Suspends the target process completely. Equivalent to
      getProcessControl().suspend(). */
    public synchronized void suspend() throws DebuggerException {
        getProcessControl().suspend();
    }

    /** Resumes the target process completely. Equivalent to
      getProcessControl().suspend(). */
    public synchronized void resume() throws DebuggerException {
        getProcessControl().resume();
    }

    /** Indicates whether we are attached to a Java HotSpot virtual
      machine */
    public synchronized boolean isJavaMode() {
        return javaMode;
    }

    /** Temporarily disables interaction with the target process via
      JVMDI. This is done while the target process is stopped at a C
      breakpoint. Can be called even if the JVMDI agent has not been
      initialized. */
    public synchronized void disableJavaInteraction() {
        javaInteractionDisabled = true;
    }

    /** Re-enables interaction with the target process via JVMDI. This
      is done while the target process is continued past a C
      braekpoint. Can be called even if the JVMDI agent has not been
      initialized. */
    public synchronized void enableJavaInteraction() {
        javaInteractionDisabled = false;
    }

    /** Indicates whether Java interaction has been disabled */
    public synchronized boolean isJavaInteractionDisabled() {
        return javaInteractionDisabled;
    }

    /** Indicates whether we can talk to the Serviceability Agent's
      JVMDI module to be able to set breakpoints */
    public synchronized boolean canInteractWithJava() {
        return (jvmdi != null) && !javaInteractionDisabled;
    }

    /** Suspends all Java threads in the target process. Can only be
      called if we are attached to a HotSpot JVM and can connect to
      the SA's JVMDI module. Must not be called when the target
      process has been suspended with suspend(). */
    public synchronized void suspendJava() throws DebuggerException {
        if (!canInteractWithJava()) {
            throw new DebuggerException("Could not connect to SA's JVMDI module");
        }
        if (jvmdi.isSuspended()) {
            throw new DebuggerException("Target process already suspended via JVMDI");
        }
        jvmdi.suspend();
    }

    /** Resumes all Java threads in the target process. Can only be
      called if we are attached to a HotSpot JVM and can connect to
      the SA's JVMDI module. Must not be called when the target
      process has been suspended with suspend(). */
    public synchronized void resumeJava() throws DebuggerException {
        if (!canInteractWithJava()) {
            throw new DebuggerException("Could not connect to SA's JVMDI module");
        }
        if (!jvmdi.isSuspended()) {
            throw new DebuggerException("Target process already resumed via JVMDI");
        }
        jvmdi.resume();
    }

    /** Indicates whether the target process has been suspended at the
      Java language level via the SA's JVMDI module */
    public synchronized boolean isJavaSuspended() throws DebuggerException {
        return jvmdi.isSuspended();
    }

    /** Toggle a Java breakpoint at the given location. */
    public synchronized ServiceabilityAgentJVMDIModule.BreakpointToggleResult
    toggleJavaBreakpoint(String srcFileName,
    String pkgName,
    int lineNo) {
        if (!canInteractWithJava()) {
            throw new DebuggerException("Could not connect to SA's JVMDI module; can not toggle Java breakpoints");
        }
        return jvmdi.toggleBreakpoint(srcFileName, pkgName, lineNo);
    }

    /** Access to JVMDI module's eventPending */
    public synchronized boolean javaEventPending() throws DebuggerException {
        if (!canInteractWithJava()) {
            throw new DebuggerException("Could not connect to SA's JVMDI module; can not poll for Java debug events");
        }
        return jvmdi.eventPending();
    }

    /** Access to JVMDI module's eventPoll */
    public synchronized Event javaEventPoll() throws DebuggerException {
        if (!canInteractWithJava()) {
            throw new DebuggerException("Could not connect to SA's JVMDI module; can not poll for Java debug events");
        }
        return jvmdi.eventPoll();
    }

    /** Access to JVMDI module's eventContinue */
    public synchronized void javaEventContinue() throws DebuggerException {
        if (!canInteractWithJava()) {
            throw new DebuggerException("Could not connect to SA's JVMDI module; can not continue past Java debug events");
        }
        jvmdi.eventContinue();
    }


    // FIXME: add other accessors. For example, suspension and
    // resumption should be done through this interface, as well as
    // interaction with the live Java process such as breakpoint setting.
    // Probably should not expose the ServiceabilityAgentJVMDIModule
    // from this interface.

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
    public synchronized void attach(String executableName, String coreFileName)
    throws DebuggerException {
        if (debugger != null) {
            throw new DebuggerException("Already attached");
        }
        if ((executableName == null) || (coreFileName == null)) {
            throw new DebuggerException("Both the core file name and executable name must be specified");
        }
        this.executableName = executableName;
        this.coreFileName = coreFileName;
        startupMode = CORE_FILE_MODE;
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
      examine this process. uniqueID is used to uniquely identify the
      debuggee */
    public synchronized void startServer(int processID, String uniqueID)
    throws DebuggerException {
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
      core file. uniqueID is used to uniquely identify the
      debuggee */
    public synchronized void startServer(String executableName, String coreFileName,
    String uniqueID)
    throws DebuggerException {
        if (debugger != null) {
            throw new DebuggerException("Already attached");
        }
        if ((executableName == null) || (coreFileName == null)) {
            throw new DebuggerException("Both the core file name and Java executable name must be specified");
        }
        this.executableName = executableName;
        this.coreFileName = coreFileName;
        startupMode = CORE_FILE_MODE;
        isServer = true;
        serverID = uniqueID;
        go();
    }

    /** This opens a core file on the local machine and starts a debug
      server, allowing remote machines to connect and examine this
      core file.*/
    public synchronized void startServer(String executableName, String coreFileName)
    throws DebuggerException {
        startServer(executableName, coreFileName, null);
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
        if (canInteractWithJava()) {
            jvmdi.detach();
            jvmdi = null;
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
        javaMode = setupVM();
    }

    private void setupDebugger() {
        if (startupMode != REMOTE_MODE) {
            //
            // Local mode (client attaching to local process or setting up
            // server, but not client attaching to server)
            //

            try {
                os  = PlatformInfo.getOS();
                cpu = PlatformInfo.getCPU();
            }
            catch (UnsupportedPlatformException e) {
                throw new DebuggerException(e);
            }
            fileSep = System.getProperty("file.separator");

            if (os.equals("solaris")) {
                setupDebuggerSolaris();
            } else if (os.equals("win32")) {
                setupDebuggerWin32();
            } else if (os.equals("linux")) {
                setupDebuggerLinux();
            } else {
                // Add support for more operating systems here
                throw new DebuggerException("Operating system " + os + " not yet supported");
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

    private boolean setupVM() {
        // We need to instantiate a HotSpotTypeDataBase on both the client
        // and server machine. On the server it is only currently used to
        // configure the Java primitive type sizes (which we should
        // consider making constant). On the client it is used to
        // configure the VM.

        try {
            if (os.equals("solaris")) {
                db = new HotSpotTypeDataBase(machDesc, new HotSpotSolarisVtblAccess(debugger, jvmLibNames),
                debugger, jvmLibNames);
            } else if (os.equals("win32")) {
                db = new HotSpotTypeDataBase(machDesc, new Win32VtblAccess(debugger, jvmLibNames),
                debugger, jvmLibNames);
            } else if (os.equals("linux")) {
                db = new HotSpotTypeDataBase(machDesc, new LinuxVtblAccess(debugger, jvmLibNames),
                debugger, jvmLibNames);
            } else {
                throw new DebuggerException("OS \"" + os + "\" not yet supported (no VtblAccess implemented yet)");
            }
        }
        catch (NoSuchSymbolException e) {
            e.printStackTrace();
            return false;
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
            VM.initialize(db, debugger);
        }

        try {
            jvmdi = new ServiceabilityAgentJVMDIModule(debugger, saLibNames);
            if (jvmdi.canAttach()) {
                jvmdi.attach();
                jvmdi.setCommandTimeout(6000);
                debugPrintln("Attached to Serviceability Agent's JVMDI module.");
                // Jog VM to suspended point with JVMDI module
                resume();
                suspendJava();
                suspend();
                debugPrintln("Suspended all Java threads.");
            } else {
                debugPrintln("Could not locate SA's JVMDI module; skipping attachment");
                jvmdi = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            jvmdi = null;
        }

        return true;
    }

    //--------------------------------------------------------------------------------
    // OS-specific debugger setup/connect routines
    //

    //
    // Solaris
    //

    private void setupDebuggerSolaris() {
        setupJVMLibNamesSolaris();
        String prop = System.getProperty("sun.jvm.hotspot.debugger.useProcDebugger");
        if (prop != null && !prop.equals("false")) {
            ProcDebuggerLocal dbg = new ProcDebuggerLocal(null, true);
            debugger = dbg;
            attachDebugger();

            // Set up CPU-dependent stuff
            if (cpu.equals("x86")) {
                machDesc = new MachineDescriptionIntelX86();
            } else if (cpu.equals("sparc")) {
                int addressSize = dbg.getRemoteProcessAddressSize();
                if (addressSize == -1) {
                    throw new DebuggerException("Error occurred while trying to determine the remote process's address size");
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
        } else {
            String dbxPathName;
            String dbxPathPrefix;
            String dbxSvcAgentDSOPathName;
            String dbxSvcAgentDSOPathPrefix;
            String[] dbxSvcAgentDSOPathNames = null;

            // use path names/prefixes specified on command
            dbxPathName = System.getProperty("dbxPathName");
            if (dbxPathName == null) {
                dbxPathPrefix = System.getProperty("dbxPathPrefix");
                if (dbxPathPrefix == null) {
                    dbxPathPrefix = defaultDbxPathPrefix;
                }
                dbxPathName = dbxPathPrefix + fileSep + os + fileSep + cpu + fileSep + "bin" + fileSep + "dbx";
            }

            dbxSvcAgentDSOPathName = System.getProperty("dbxSvcAgentDSOPathName");
            if (dbxSvcAgentDSOPathName != null) {
                dbxSvcAgentDSOPathNames = new String[] { dbxSvcAgentDSOPathName } ;
            } else {
                dbxSvcAgentDSOPathPrefix = System.getProperty("dbxSvcAgentDSOPathPrefix");
                if (dbxSvcAgentDSOPathPrefix == null) {
                    dbxSvcAgentDSOPathPrefix = defaultDbxSvcAgentDSOPathPrefix;
                }
                if (cpu.equals("sparc")) {
                    dbxSvcAgentDSOPathNames = new String[] {
                        // FIXME: bad hack for SPARC v9. This is necessary because
                        // there are two dbx executables on SPARC, one for v8 and one
                        // for v9, and it isn't obvious how to tell the two apart
                        // using the dbx command line. See
                        // DbxDebuggerLocal.importDbxModule().
                        dbxSvcAgentDSOPathPrefix + fileSep + os + fileSep + cpu + "v9" + fileSep + "lib" + fileSep + "libsvc_agent_dbx.so",
                        dbxSvcAgentDSOPathPrefix + fileSep + os + fileSep + cpu + fileSep + "lib" + fileSep + "libsvc_agent_dbx.so",
                    };
                } else {
                    dbxSvcAgentDSOPathNames = new String[] {
                        dbxSvcAgentDSOPathPrefix + fileSep + os + fileSep + cpu + fileSep + "lib" + fileSep + "libsvc_agent_dbx.so"
                    };
                }
            }
            // Note we do not use a cache for the local debugger in server
            // mode; it's taken care of on the client side
            DbxDebuggerLocal dbg = new DbxDebuggerLocal(null, dbxPathName, dbxSvcAgentDSOPathNames, !isServer);
            debugger = dbg;

            attachDebugger();

            // Set up CPU-dependent stuff
            if (cpu.equals("x86")) {
                machDesc = new MachineDescriptionIntelX86();
            } else if (cpu.equals("sparc")) {
                int addressSize = dbg.getRemoteProcessAddressSize();
                if (addressSize == -1) {
                    throw new DebuggerException("Error occurred while trying to determine the remote process's address size. It's possible that the Serviceability Agent's dbx module failed to initialize. Examine the standard output and standard error streams from the dbx process for more information.");
                }

                if (addressSize == 32) {
                    machDesc = new MachineDescriptionSPARC32Bit();
                } else if (addressSize == 64) {
                    machDesc = new MachineDescriptionSPARC64Bit();
                } else {
                    throw new DebuggerException("Address size " + addressSize + " is not supported on SPARC");
                }
            }

            dbg.setMachineDescription(machDesc);
        }
    }

    private void connectRemoteDebugger() throws DebuggerException {
        RemoteDebugger remote =
        (RemoteDebugger) RMIHelper.lookup(debugServerID);
        debugger = new RemoteDebuggerClient(remote);
        machDesc = ((RemoteDebuggerClient) debugger).getMachineDescription();
        os = debugger.getOS();
        if (os.equals("solaris")) {
            setupJVMLibNamesSolaris();
        } else if (os.equals("win32")) {
            setupJVMLibNamesWin32();
        } else if (os.equals("linux")) {
            setupJVMLibNamesLinux();
        } else {
            throw new RuntimeException("Unknown OS type");
        }

        cpu = debugger.getCPU();
    }

    private void setupJVMLibNamesSolaris() {
        jvmLibNames = new String[] { "libjvm.so", "libjvm_g.so", "gamma_g" };
        saLibNames = new String[] { "libsa.so", "libsa_g.so" };
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

        if (System.getProperty("sun.jvm.hotspot.debugger.useWindbgDebugger") != null) {
            debugger = new WindbgDebuggerLocal(machDesc, !isServer);
        } else {
            debugger = new Win32DebuggerLocal(machDesc, !isServer);
        }

        attachDebugger();
    }

    private void setupJVMLibNamesWin32() {
        jvmLibNames = new String[] { "jvm.dll", "jvm_g.dll" };
        saLibNames = new String[] { "sa.dll", "sa_g.dll" };
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
        } else if (cpu.equals("sparc")) {
            if (LinuxDebuggerLocal.getAddressSize()==8) {
               machDesc = new MachineDescriptionSPARC64Bit();
            } else {
               machDesc = new MachineDescriptionSPARC32Bit();
            }
        } else {
            throw new DebuggerException("Linux only supported on x86/ia64/amd64/sparc/sparc64");
        }

        // Note we do not use a cache for the local debugger in server
        // mode; it will be taken care of on the client side (once remote
        // debugging is implemented).

        debugger = new LinuxDebuggerLocal(machDesc, !isServer);
        attachDebugger();
    }

    private void setupJVMLibNamesLinux() {
        // same as solaris
        setupJVMLibNamesSolaris();
    }

    /** Convenience routine which should be called by per-platform
      debugger setup. Should not be called when startupMode is
      REMOTE_MODE. */
    private void attachDebugger() {
        if (startupMode == PROCESS_MODE) {
            debugger.attach(pid);
        } else if (startupMode == CORE_FILE_MODE) {
            debugger.attach(executableName, coreFileName);
        } else {
            throw new DebuggerException("Should not call attach() for startupMode == " + startupMode);
        }
    }
}
