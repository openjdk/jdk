/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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
 */

package nsk.share.jdi;

import jdk.test.lib.Platform;
import nsk.share.*;
import nsk.share.jpda.*;

import com.sun.jdi.*;
import com.sun.jdi.connect.*;

import com.sun.jdi.connect.Connector.Argument;
import java.io.*;
import java.net.*;
import java.util.*;

/**
 * This class provides debugger with connection to debugee VM
 * using JDI connectors.
 *<p>
 * This class provides abilities to launch and bind to debugee VM
 * as described for base <code>DebugeeBinder</code> class,
 * using JDI connectors and <code>com.sun.VirtualMachine</code> mirror.
 * <p>
 * When <code>Binder</code> is asked to bind to debugee by invoking
 * <code>bindToBebugee()</code> method it uses
 * <code>com.sun.jdi.Connector</code> object corresponding to
 * value of command line options <code>-connector</code> and
 * <code>-transport</code> to launch and connect to debugee VM.
 * After debugee is launched and connection is established
 * <code>Binder</code> uses <code>com.sun.jdi.VirtualMachine</code>
 * object to construct <code>Debugee</code> object, that
 * provides abilities to interact with debugee VM.
 *
 * @see Debugee
 * @see DebugeeBinder
 */
public class Binder extends DebugeeBinder {

    /**
     * Default message prefix for <code>Binder</code> object.
     */
    public static final String LOG_PREFIX = "binder> ";

    /**
     * Get version string.
     */
    public static String getVersion () {
        return "@(#)Binder.java 1.14 03/10/08";
    }

    // -------------------------------------------------- //

    /**
     * Handler of command line arguments.
     */
    private ArgumentHandler argumentHandler = null;

    /**
     * Return <code>argumentHandler</code> of this binder.
     */
    public ArgumentHandler getArgumentHandler() {
        return argumentHandler;
    }

    // -------------------------------------------------- //

    /**
     * Make <code>Binder</code> object and pass raw command line arguments.
     *
     * @deprecated  Use newer
     *              <code>Binder(ArgumentHandler,Log)</code>
     *              constructor.
     */
    @Deprecated
    public Binder (String args[]) {
        this(args, new Log(System.err));
    }

    /**
     * Make <code>Binder</code> object for raw command line arguments
     * and specified <code>log</code> object.
     *
     * @deprecated  Use newer
     *              <code>Binder(ArgumentHandler,Log)</code>
     *              constructor.
     */
    @Deprecated
    public Binder (String args[], Log log) {
        this(new ArgumentHandler(args), log);
    }

    /**
     * Make <code>Binder</code> object for specified command line arguments
     * and <code>log</code> object.
     */
    public Binder (ArgumentHandler argumentHandler, Log log) {
        super(argumentHandler, log);
        this.argumentHandler = argumentHandler;
    }

    // -------------------------------------------------- //

    /**
     * Make initial <code>Debugee</code> object for debuggee process
     * started with launching connector.
     */
    public Debugee makeDebugee(Process process) {
        return new Debugee(process, this);
    }

    /**
     * Launch debuggee process with specified command line
     * and make initial <code>Debugee</code> object.
     */
    public Debugee startDebugee(String cmd) {
        Process process = null;

        try {
            process = launchProcess(cmd);
        } catch (IOException e) {
            e.printStackTrace(log.getOutStream());
            throw new Failure("Caught exception while launching debuggee VM process:\n\t"
                            + e);
        }

        return makeDebugee(process);
    }

    /**
     * Make debuggee wrapper for already launched debuggee VM.
     * After enwraping debugee's output is redirected to Binder's log,
     * VMStartEvent is received and debuggee is initialized.
     */
    public Debugee enwrapDebugee(VirtualMachine vm, Process proc) {
        Debugee debugee = makeDebugee(proc);

        display("Redirecting VM output");
        debugee.redirectOutput(log);
        debugee.setupVM(vm);

        long timeout = argumentHandler.getWaitTime() * 60 * 1000; // milliseconds

        display("Waiting for VM initialized");
        debugee.waitForVMInit(timeout);

        return debugee;
    }

    /**
     * Launch debugee VM and establish connection to it without waiting for VMStartEvent.
     * After launching debugee's output is redirected to Binder's log,
     * but VMStartEvent is not received and so debuggee is not fully initialized.
     *
     * @see #bindToDebugee(String)
     */
    public Debugee bindToDebugeeNoWait(String classToExecute) {

        VirtualMachineManager vmm = Bootstrap.virtualMachineManager();
        display("VirtualMachineManager: version "
                + vmm.majorInterfaceVersion() + "."
                + vmm.minorInterfaceVersion());

        Debugee debugee = null;

        String classPath = System.getProperty("test.class.path");

        prepareForPipeConnection(argumentHandler);

        if (argumentHandler.isDefaultConnector()) {
            debugee = defaultLaunchDebugee(vmm, classToExecute, classPath);
        } else if (argumentHandler.isRawLaunchingConnector()) {
            debugee = rawLaunchDebugee(vmm, classToExecute, classPath);
        } else if (argumentHandler.isLaunchingConnector()) {
            debugee = launchDebugee(vmm, classToExecute, classPath);
        } else if (argumentHandler.isAttachingConnector()) {
            debugee = launchAndAttachDebugee(vmm, classToExecute, classPath);
        } else if (argumentHandler.isListeningConnector()) {
            debugee = launchAndListenDebugee(vmm, classToExecute, classPath);
        } else {
            throw new TestBug("Unexpected connector type for debugee: "
                              + argumentHandler.getConnectorType());
        }


        return debugee;
    }

    /**
     * Launch debugee VM and establish JDI connection.
     * After launching debugee's output is redirected to Binder's log,
     * VMStart event is received and debuggee is initialized.
     *
     * @see #bindToDebugeeNoWait(String)
     */
    public Debugee bindToDebugee(String classToExecute) {
        Debugee debugee = bindToDebugeeNoWait(classToExecute);

        if(argumentHandler.getOptions().getProperty("traceAll") != null)
            debugee.VM().setDebugTraceMode(VirtualMachine.TRACE_ALL);

        long timeout = argumentHandler.getWaitTime() * 60 * 1000; // milliseconds

        display("Waiting for VM initialized");
        debugee.waitForVMInit(timeout);

        return debugee;
    }

    // -------------------------------------------------- //

    /**
     * Launch debugee via the default LaunchingConnector.
     */
    private Debugee defaultLaunchDebugee (VirtualMachineManager vmm,
                                                String classToExecute,
                                                String classPath) {
        display("Finding connector: " + "default" );
        LaunchingConnector connector = vmm.defaultConnector();
        Map<String,? extends Argument> arguments = setupLaunchingConnector(connector, classToExecute, classPath);

        VirtualMachine vm;
        try {
            display("Launching debugee");
            vm = connector.launch(arguments);
        } catch (IllegalConnectorArgumentsException e) {
            e.printStackTrace(log.getOutStream());
            throw new TestBug("Wrong connector arguments used to launch debuggee VM:\n\t" + e);
        } catch (VMStartException e) {
            e.printStackTrace(log.getOutStream());
            String msg = readVMStartExceptionOutput(e, log.getOutStream());
            throw new Failure("Caught exception while starting debugee VM:\n\t" + e + "\n" + msg);
        } catch (IOException e) {
            e.printStackTrace(log.getOutStream());
            throw new Failure("Caught exception while launching debugee VM:\n\t" + e);
        };

        Process process = vm.process();
        Debugee debugee = makeDebugee(process);
        debugee.redirectOutput(log);
        debugee.setupVM(vm);

        return debugee;
    }


    /**
     * Launch debugee via the default LaunchingConnector.
     */
    private Debugee launchDebugee (VirtualMachineManager vmm,
                                            String classToExecute,
                                            String classPath) {

        display("Finding connector: " + argumentHandler.getConnectorName() );
        LaunchingConnector connector =
            (LaunchingConnector) findConnector(argumentHandler.getConnectorName(),
                                                vmm.launchingConnectors());
        Map<java.lang.String,? extends com.sun.jdi.connect.Connector.Argument> arguments = setupLaunchingConnector(connector, classToExecute, classPath);

        VirtualMachine vm;
        try {
            display("Launching debugee");
            vm = connector.launch(arguments);
        } catch (IllegalConnectorArgumentsException e) {
            e.printStackTrace(log.getOutStream());
            throw new TestBug("Wrong connector arguments used to launch debuggee VM:\n\t" + e);
        } catch (VMStartException e) {
            e.printStackTrace(log.getOutStream());
            String msg = readVMStartExceptionOutput(e, log.getOutStream());
            throw new Failure("Caught exception while starting debugee VM:\n\t" + e + "\nProcess output:\n\t" + msg);
        } catch (IOException e) {
            e.printStackTrace(log.getOutStream());
            throw new Failure("Caught exception while launching debugee VM:\n\t" + e);
        };

        Process process = vm.process();
        Debugee debugee = makeDebugee(process);
        debugee.redirectOutput(log);
        debugee.setupVM(vm);

        return debugee;
    }

    /**
     * Launch debugee via the RawLaunchingConnector.
     */
    private Debugee rawLaunchDebugee (VirtualMachineManager vmm,
                                            String classToExecute,
                                            String classPath) {
        display("Finding connector: " + argumentHandler.getConnectorName() );
        LaunchingConnector connector =
            (LaunchingConnector) findConnector(argumentHandler.getConnectorName(),
                                                vmm.launchingConnectors());
        Map<java.lang.String,? extends com.sun.jdi.connect.Connector.Argument> arguments = setupRawLaunchingConnector(connector, classToExecute, classPath);

        VirtualMachine vm;
        try {
            display("Launching debugee");
            vm = connector.launch(arguments);
        } catch (IllegalConnectorArgumentsException e) {
            e.printStackTrace(log.getOutStream());
            throw new TestBug("Wrong connector arguments used to launch debuggee VM:\n\t" + e);
        } catch (VMStartException e) {
            e.printStackTrace(log.getOutStream());
            String msg = readVMStartExceptionOutput(e, log.getOutStream());
            throw new Failure("Caught exception while starting debugee VM:\n\t" + e + "\nProcess output:\n\t" + msg);
        } catch (IOException e) {
            e.printStackTrace(log.getOutStream());
            throw new Failure("Caught exception while launching debugee VM:\n\t" + e);
        };

        Process process = vm.process();
        Debugee debugee = makeDebugee(process);
        debugee.redirectOutput(log);
        debugee.setupVM(vm);

        return debugee;
    }

    /**
     * Launch debugee VM and connect to it using <code>AttachingConnector</code>.
     */
    private Debugee launchAndAttachDebugee (VirtualMachineManager vmm,
                                                    String classToExecute,
                                                    String classPath) {
        display("FindingConnector: " + argumentHandler.getConnectorName() );
        AttachingConnector connector =
            (AttachingConnector) findConnector(argumentHandler.getConnectorName(),
                                                vmm.attachingConnectors());
        Map<java.lang.String,? extends com.sun.jdi.connect.Connector.Argument> arguments = setupAttachingConnector(connector, classToExecute, classPath);

        String address = makeTransportAddress();
        String[] cmdLineArgs = makeCommandLineArgs(classToExecute, address);
        String javaCmdLine = makeCommandLineString(classToExecute, address, "\"");

        display("Starting java process:\n\t" + javaCmdLine);
        Debugee debugee = startDebugee(cmdLineArgs);
        debugee.redirectOutput(log);

        display("Attaching to debugee");
        VirtualMachine vm = null;
        IOException ioe = null;
        for (int i = 0; i < CONNECT_TRIES; i++) {
            try {
                vm = connector.attach(arguments);
                display("Debugee attached");
                debugee.setupVM(vm);
                return debugee;
            } catch (IOException e) {
                display("Attempt #" + i + " to connect to debugee VM failed:\n\t" + e);
                ioe = e;
                if (debugee.terminated()) {
                    throw new Failure("Unable to connect to debuggee VM: VM process is terminated");
                }
                try {
                    Thread.currentThread().sleep(CONNECT_TRY_DELAY);
                } catch (InterruptedException ie) {
                    ie.printStackTrace(log.getOutStream());
                    throw new Failure("Thread interrupted while pausing connection attempts:\n\t"
                                    + ie);
                }
            } catch (IllegalConnectorArgumentsException e) {
                e.printStackTrace(log.getOutStream());
                throw new TestBug("Wrong connector arguments used to attach to debuggee VM:\n\t" + e);
            }
        }
        throw new Failure("Unable to connect to debugee VM after " + CONNECT_TRIES
                        + " tries:\n\t" + ioe);
    }

    /**
     * Launch debugee VM and connect to it using <code>ListeningConnector</code>.
     */
    private Debugee launchAndListenDebugee (VirtualMachineManager vmm,
                                                    String classToExecute,
                                                    String classPath) {
        display("Finding connector: " + argumentHandler.getConnectorName() );
        ListeningConnector connector =
            (ListeningConnector) findConnector(argumentHandler.getConnectorName(),
                                                vmm.listeningConnectors());
        Map<java.lang.String,? extends com.sun.jdi.connect.Connector.Argument> arguments = setupListeningConnector(connector, classToExecute, classPath);

        String address = null;
        try {
            display("Listening for connection from debugee");
            address = connector.startListening(arguments);
        } catch (IllegalConnectorArgumentsException e) {
            e.printStackTrace(log.getOutStream());
            throw new TestBug("Wrong connector arguments used to listen debuggee VM:\n\t" + e);
        } catch (IOException e) {
            e.printStackTrace(log.getOutStream());
            throw new Failure("Caught exception while starting listening debugee VM:\n\t" + e);
        };

        String[] cmdLineArgs = makeCommandLineArgs(classToExecute, address);
        String javaCmdLine = makeCommandLineString(classToExecute, address, "\"");

        display("Starting java process:\n\t" + javaCmdLine);
        Debugee debugee = startDebugee(cmdLineArgs);
        debugee.redirectOutput(log);

        display("Waiting for connection from debugee");
        VirtualMachine vm = null;
        IOException ioe = null;
        for (int i = 0; i < CONNECT_TRIES; i++) {
            try {
                vm = connector.accept(arguments);
                connector.stopListening(arguments);
                display("Debugee attached");
                debugee.setupVM(vm);
                return debugee;
            } catch (IOException e) {
                display("Attempt #" + i + " to listen debugee VM failed:\n\t" + e);
                ioe = e;
                if (debugee.terminated()) {
                    throw new Failure("Unable to connect to debuggee VM: VM process is terminated");
                }
                try {
                    Thread.currentThread().sleep(CONNECT_TRY_DELAY);
                } catch (InterruptedException ie) {
                    ie.printStackTrace(log.getOutStream());
                    throw new Failure("Thread interrupted while pausing connection attempts:\n\t"
                                    + ie);
                }
            } catch (IllegalConnectorArgumentsException e) {
                e.printStackTrace(log.getOutStream());
                throw new TestBug("Wrong connector arguments used to listen debuggee VM:\n\t" + e);
            }
        }
        throw new Failure("Unable to connect to debugee VM after " + CONNECT_TRIES
                        + " tries:\n\t" + ioe);
    }

    // -------------------------------------------------- //

    // -------------------------------------------------- //

    /**
     * Make proper arguments for LaunchingConnector.
     */
    private Map<String,? extends Argument> setupLaunchingConnector(LaunchingConnector connector,
                                                String classToExecute,
                                                String classPath) {
        display("LaunchingConnector:");
        display("    name: " + connector.name());
        display("    description: " + connector.description());
        display("    transport: " + connector.transport());

        Hashtable<String,? extends Argument> arguments = new Hashtable<String,Argument>(connector.defaultArguments());

        Connector.Argument arg;

        arg = (Connector.StringArgument) arguments.get("quote");
        String quote = "\0";
        arg.setValue(quote);

        String[] rawArgs = argumentHandler.getRawArguments();
        if (Platform.isWindows()) {
            // " has to be escaped on windows
            rawArgs = Arrays.stream(rawArgs)
                            .map(s -> s.replace("\"", "\\\""))
                            .toArray(String[]::new);
        }

        String cmdline = classToExecute + " " + ArgumentHandler.joinArguments(rawArgs, quote);

        if (System.getProperty("test.thread.factory") != null) {
            cmdline = MainWrapper.class.getName() + " " + System.getProperty("test.thread.factory") + " " + cmdline;
        }

        arg = (Connector.StringArgument) arguments.get("main");
        arg.setValue(cmdline);

        if (! argumentHandler.willDebugeeSuspended()) {
            Connector.BooleanArgument barg = (Connector.BooleanArgument) arguments.get("suspend");
            barg.setValue(true);
        }

/*
        if (! argumentHandler.isJVMDIStrictMode()) {
            arg = (Connector.StringArgument) arguments.get("options");
            arg.setValue("strict=y");
        }
 */

        if (! argumentHandler.isDefaultDebugeeJavaHome()) {
            arg = (Connector.StringArgument) arguments.get("home");
            arg.setValue(argumentHandler.getDebugeeJavaHome());
        }

        if (! argumentHandler.isDefaultLaunchExecName()) {
            arg = (Connector.StringArgument) arguments.get("vmexec");
            arg.setValue(argumentHandler.getLaunchExecName());
        }

        // This flag is needed so VirtualMachine.allThreads() includes known vthreads.
        arg = (Connector.StringArgument) arguments.get("includevirtualthreads");
        arg.setValue(argumentHandler.isIncludeVirtualThreads() ? "y" : "n");

        String vmArgs = "";

        String vmUserArgs = argumentHandler.getLaunchOptions();

        if (vmUserArgs != null) {
            vmArgs = vmUserArgs;
        }

        boolean vthreadMode = "Virtual".equals(System.getProperty("test.thread.factory"));
        if (vthreadMode) {
            /* Some tests need more carrier threads than the default provided. */
            vmArgs += " -Djdk.virtualThreadScheduler.parallelism=15";
        }

        if (classPath != null && !vmArgs.contains("-cp") && !vmArgs.contains("-classpath")) {
            vmArgs += " -classpath " + quote + classPath + quote;
        }

        if (vmArgs.length() > 0) {
            arg = (Connector.StringArgument) arguments.get("options");
            arg.setValue(vmArgs);
        }

        display("Connector arguments:");
        Iterator iterator = arguments.values().iterator();
        while (iterator.hasNext()) {
            display("    " + iterator.next());
        }
        return arguments;
    }

    /**
     * Make proper arguments for RawLaunchingConnector.
     */
    private Map<java.lang.String,? extends com.sun.jdi.connect.Connector.Argument> setupRawLaunchingConnector(LaunchingConnector connector,
                                                String classToExecute,
                                                String classPath) {
        display("RawLaunchingConnector:");
        display("    name: " + connector.name());
        display("    description: " + connector.description());
        display("    transport: " + connector.transport());

        Hashtable<java.lang.String,? extends com.sun.jdi.connect.Connector.Argument> arguments = new Hashtable<java.lang.String, com.sun.jdi.connect.Connector.Argument>(connector.defaultArguments());

        String connectorAddress;
        String vmAddress;

        if (argumentHandler.isSocketTransport()) {
            connectorAddress = argumentHandler.getTransportPort();
            vmAddress = argumentHandler.getDebugeeHost()
                        + ":" + argumentHandler.getTransportPort();
        } else if (argumentHandler.isShmemTransport() ) {
            connectorAddress = argumentHandler.getTransportSharedName();
            vmAddress=connectorAddress;
        } else {
            throw new TestBug("Undefined transport type for AttachingConnector");
        }

        Connector.Argument arg;

        arg = (Connector.StringArgument) arguments.get("quote");
        String quote = arg.value();

        String javaCmdLine = makeCommandLineString(classToExecute, quote);

        arg = (Connector.StringArgument) arguments.get("command");
        arg.setValue(javaCmdLine);

        arg = (Connector.StringArgument) arguments.get("address");
        arg.setValue(connectorAddress);

        display("Connector arguments:");
        Iterator iterator = arguments.values().iterator();
        while (iterator.hasNext()) {
            display("    " + iterator.next());
        }
        return arguments;
    }

    /**
     * Make proper arguments for AttachingConnector.
     */
    private Map<java.lang.String,? extends com.sun.jdi.connect.Connector.Argument> setupAttachingConnector(AttachingConnector connector,
                                                String classToExecute,
                                                String classPath) {
        display("AttachingConnector:");
        display("    name: " + connector.name());
        display("    description: " + connector.description());
        display("    transport: " + connector.transport());

        Hashtable<java.lang.String,? extends com.sun.jdi.connect.Connector.Argument> arguments = new Hashtable<java.lang.String,com.sun.jdi.connect.Connector.Argument>(connector.defaultArguments());

        Connector.Argument arg;
        if (argumentHandler.isSocketTransport()) {
            arg = (Connector.StringArgument) arguments.get("hostname");
            arg.setValue(argumentHandler.getDebugeeHost());
            Connector.IntegerArgument iarg = (Connector.IntegerArgument) arguments.get("port");
            iarg.setValue(argumentHandler.getTransportPortNumber());
        } else {
            arg = (Connector.StringArgument) arguments.get("name");
            arg.setValue(argumentHandler.getTransportSharedName());
        }

        display("Connector arguments:");
        Iterator iterator = arguments.values().iterator();
        while (iterator.hasNext()) {
            display("    " + iterator.next());
        }
        return arguments;
    }

    /**
     * Make proper arguments for ListeningConnector.
     */
    private Map<java.lang.String,? extends com.sun.jdi.connect.Connector.Argument> setupListeningConnector(ListeningConnector connector,
                                                String classToExecute,
                                                String classPath) {
        display("ListeningConnector:");
        display("    name: " + connector.name());
        display("    description: " + connector.description());
        display("    transport: " + connector.transport());

        Hashtable<java.lang.String,? extends com.sun.jdi.connect.Connector.Argument> arguments = new Hashtable<java.lang.String,com.sun.jdi.connect.Connector.Argument>(connector.defaultArguments());

        Connector.Argument arg;
        if (argumentHandler.isSocketTransport()) {
            if (!argumentHandler.isTransportAddressDynamic()) {
                int port = argumentHandler.getTransportPortNumber();
                Connector.IntegerArgument iarg = (Connector.IntegerArgument) arguments.get("port");
                iarg.setValue(port);
            }
        } else {
            String sharedName = argumentHandler.getTransportSharedName();
            arg = (Connector.StringArgument) arguments.get("name");
            arg.setValue(sharedName);
        }

        display("Connector arguments:");
        Iterator iterator = arguments.values().iterator();
        while (iterator.hasNext()) {
            display("    " + iterator.next());
        }
        return arguments;
    }

    // -------------------------------------------------- //

    /**
     * Find connector by name from given connectors list.
     */
    private Connector findConnector(String connectorName, List connectors) {
        Iterator iter = connectors.iterator();

        while (iter.hasNext()) {
            Connector connector = (Connector) iter.next();
            if (connector.name().equals(connectorName)) {
                return connector;
            }
        }
        throw new Failure("JDI connector not found: " + connectorName);
    }

    // -------------------------------------------------- //

    /**
     * Launch debuggee process with specified command line arguments
     * and make initial <code>Debugee</code> mirror.
     */
    protected Debugee startDebugee(String[] cmdArgs) {
        Process process = null;

        try {
            process = launchProcess(cmdArgs);
        } catch (IOException e) {
            e.printStackTrace(log.getOutStream());
            throw new Failure("Caught exception while launching debuggee VM process:\n\t"
                            + e);
        }

        return makeDebugee(process);
    }

    public static String readVMStartExceptionOutput(VMStartException e, PrintStream log) {
        StringBuffer msg = new StringBuffer();
        try (InputStream is = e.process().getInputStream()) {
            msg.append("\tstdout: ").append(new String(readAllBytes(is))).append('\n');
        } catch (IOException e1) {
            log.println("Could not read normal output from launched process:" + e1);
        }
        try (InputStream is = e.process().getErrorStream()) {
            msg.append("\tstderr: ").append(new String(readAllBytes(is)));
        } catch (IOException e1) {
            log.println("Could not read error output from launched process:" + e1);
        }
        return msg.toString();
    }

    /**
     * Copied from the JDK 9 implementation in InputStream.java
     */
    private static byte[] readAllBytes(InputStream is) throws IOException {
        final int DEFAULT_BUFFER_SIZE = 8192;
        final int MAX_BUFFER_SIZE = Integer.MAX_VALUE - 8;

        byte[] buf = new byte[DEFAULT_BUFFER_SIZE];
        int capacity = buf.length;
        int nread = 0;
        int n;
        for (;;) {
            // read to EOF which may read more or less than initial buffer size
            while ((n = is.read(buf, nread, capacity - nread)) > 0)
                nread += n;

            // if the last call to read returned -1, then we're done
            if (n < 0)
                break;

            // need to allocate a larger buffer
            if (capacity <= MAX_BUFFER_SIZE - capacity) {
                capacity = capacity << 1;
            } else {
                if (capacity == MAX_BUFFER_SIZE)
                    throw new OutOfMemoryError("Required array size too large");
                capacity = MAX_BUFFER_SIZE;
            }
            buf = Arrays.copyOf(buf, capacity);
        }
        return (capacity == nread) ? buf : Arrays.copyOf(buf, nread);
    }

}
