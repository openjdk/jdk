/*
 * Copyright (c) 1998, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

/*
 * This source code is provided to illustrate the usage of a given feature
 * or technique and has been deliberately simplified. Additional steps
 * required for a production-quality application, such as security checks,
 * input validation and proper error handling, might not be present in
 * this sample code.
 */


package jdk.internal.jshell.jdi;

import com.sun.jdi.*;
import com.sun.jdi.connect.*;

import java.util.*;
import java.util.Map.Entry;
import java.io.*;
import static jdk.internal.jshell.debug.InternalDebugControl.DBG_GEN;

/**
 * Connection to a Java Debug Interface VirtualMachine instance.
 * Adapted from jdb VMConnection. Message handling, exception handling, and I/O
 * redirection changed.  Interface to JShell added.
 */
class JDIConnection {

    private static final String REMOTE_AGENT = "jdk.internal.jshell.remote.RemoteAgent";

    private VirtualMachine vm;
    private boolean active = true;
    private Process process = null;
    private int outputCompleteCount = 0;

    private final JDIExecutionControl ec;
    private final Connector connector;
    private final Map<String, com.sun.jdi.connect.Connector.Argument> connectorArgs;
    private final int traceFlags;

    private synchronized void notifyOutputComplete() {
        outputCompleteCount++;
        notifyAll();
    }

    private synchronized void waitOutputComplete() {
        // Wait for stderr and stdout
        if (process != null) {
            while (outputCompleteCount < 2) {
                try {wait();} catch (InterruptedException e) {}
            }
        }
    }

    private Connector findConnector(String name) {
        for (Connector cntor :
                 Bootstrap.virtualMachineManager().allConnectors()) {
            if (cntor.name().equals(name)) {
                return cntor;
            }
        }
        return null;
    }

    private Map <String, Connector.Argument> mergeConnectorArgs(Connector connector, Map<String, String> argumentName2Value) {
        Map<String, Connector.Argument> arguments = connector.defaultArguments();

        for (Entry<String, String> argumentEntry : argumentName2Value.entrySet()) {
            String name = argumentEntry.getKey();
            String value = argumentEntry.getValue();
            Connector.Argument argument = arguments.get(name);

            if (argument == null) {
                throw new IllegalArgumentException("Argument is not defined for connector:" +
                                          name + " -- " + connector.name());
            }

            argument.setValue(value);
        }

        return arguments;
    }

    /**
     * The JShell specific Connector args for the LaunchingConnector.
     *
     * @param portthe socket port for (non-JDI) commands
     * @param remoteVMOptions any user requested VM options
     * @return the argument map
     */
    private static Map<String, String> launchArgs(int port, String remoteVMOptions) {
        Map<String, String> argumentName2Value = new HashMap<>();
        argumentName2Value.put("main", REMOTE_AGENT + " " + port);
        argumentName2Value.put("options", remoteVMOptions);
        return argumentName2Value;
    }

    /**
     * Start the remote agent and establish a JDI connection to it.
     *
     * @param ec the execution control instance
     * @param port the socket port for (non-JDI) commands
     * @param remoteVMOptions any user requested VM options
     * @param isLaunch does JDI do the launch? That is, LaunchingConnector,
     * otherwise we start explicitly and use ListeningConnector
     */
    JDIConnection(JDIExecutionControl ec, int port, List<String> remoteVMOptions, boolean isLaunch) {
        this(ec,
                isLaunch
                        ? "com.sun.jdi.CommandLineLaunch"
                        : "com.sun.jdi.SocketListen",
                isLaunch
                        ? launchArgs(port, String.join(" ", remoteVMOptions))
                        : new HashMap<>(),
                0);
        if (isLaunch) {
            vm = launchTarget();
        } else {
            vm = listenTarget(port, remoteVMOptions);
        }

        if (isOpen() && vm().canBeModified()) {
            /*
             * Connection opened on startup.
             */
            new JDIEventHandler(vm(), (b) -> ec.handleVMExit())
                    .start();
        }
    }

    /**
     * Base constructor -- set-up a JDI connection.
     *
     * @param ec the execution control instance
     * @param connectorName the standardized name of the connector
     * @param argumentName2Value the argument map
     * @param traceFlags should we trace JDI behavior
     */
    JDIConnection(JDIExecutionControl ec, String connectorName, Map<String, String> argumentName2Value, int traceFlags) {
        this.ec = ec;
        this.connector = findConnector(connectorName);
        if (connector == null) {
            throw new IllegalArgumentException("No connector named: " + connectorName);
        }
        connectorArgs = mergeConnectorArgs(connector, argumentName2Value);
        this.traceFlags = traceFlags;
    }

    final synchronized VirtualMachine vm() {
        if (vm == null) {
            throw new JDINotConnectedException();
        } else {
            return vm;
        }
    }

    private synchronized boolean isOpen() {
        return (vm != null);
    }

    synchronized boolean isRunning() {
        return process != null && process.isAlive();
    }

    // Beginning shutdown, ignore any random dying squeals
    void beginShutdown() {
        active = false;
    }

    synchronized void disposeVM() {
        try {
            if (vm != null) {
                vm.dispose(); // This could NPE, so it is caught below
                vm = null;
            }
        } catch (VMDisconnectedException ex) {
            // Ignore if already closed
        } catch (Throwable e) {
            ec.debug(DBG_GEN, null, "disposeVM threw: " + e);
        } finally {
            if (process != null) {
                process.destroy();
                process = null;
            }
            waitOutputComplete();
        }
    }

    private void dumpStream(InputStream inStream, final PrintStream pStream) throws IOException {
        BufferedReader in =
            new BufferedReader(new InputStreamReader(inStream));
        int i;
        try {
            while ((i = in.read()) != -1) {
                // directly copy input to output, but skip if asked to close
                if (active) {
                    pStream.print((char) i);
                }
            }
        } catch (IOException ex) {
            String s = ex.getMessage();
            if (active && !s.startsWith("Bad file number")) {
                throw ex;
            }
            // else we are being shutdown (and don't want any spurious death
            // throws to ripple) or
            // we got a Bad file number IOException which just means
            // that the debuggee has gone away.  We'll just treat it the
            // same as if we got an EOF.
        }
    }

    /**
     *  Create a Thread that will retrieve and display any output.
     *  Needs to be high priority, else debugger may exit before
     *  it can be displayed.
     */
    private void displayRemoteOutput(final InputStream inStream, final PrintStream pStream) {
        Thread thr = new Thread("output reader") {
            @Override
            public void run() {
                try {
                    dumpStream(inStream, pStream);
                } catch (IOException ex) {
                    ec.debug(ex, "Failed reading output");
                    ec.handleVMExit();
                } finally {
                    notifyOutputComplete();
                }
            }
        };
        thr.setPriority(Thread.MAX_PRIORITY-1);
        thr.start();
    }

    /**
     *  Create a Thread that will ship all input to remote.
     *  Does it need be high priority?
     */
    private void readRemoteInput(final OutputStream outStream, final InputStream inputStream) {
        Thread thr = new Thread("input reader") {
            @Override
            public void run() {
                try {
                    byte[] buf = new byte[256];
                    int cnt;
                    while ((cnt = inputStream.read(buf)) != -1) {
                        outStream.write(buf, 0, cnt);
                        outStream.flush();
                    }
                } catch (IOException ex) {
                    ec.debug(ex, "Failed reading output");
                    ec.handleVMExit();
                }
            }
        };
        thr.setPriority(Thread.MAX_PRIORITY-1);
        thr.start();
    }

    private void forwardIO() {
        displayRemoteOutput(process.getErrorStream(), ec.execEnv.userErr());
        displayRemoteOutput(process.getInputStream(), ec.execEnv.userOut());
        readRemoteInput(process.getOutputStream(), ec.execEnv.userIn());
    }

    /* launch child target vm */
    private VirtualMachine launchTarget() {
        LaunchingConnector launcher = (LaunchingConnector)connector;
        try {
            VirtualMachine new_vm = launcher.launch(connectorArgs);
            process = new_vm.process();
            forwardIO();
            return new_vm;
        } catch (Exception ex) {
            reportLaunchFail(ex, "launch");
        }
        return null;
    }

    /**
     * Directly launch the remote agent and connect JDI to it with a
     * ListeningConnector.
     */
    private VirtualMachine listenTarget(int port, List<String> remoteVMOptions) {
        ListeningConnector listener = (ListeningConnector) connector;
        try {
            // Start listening, get the JDI connection address
            String addr = listener.startListening(connectorArgs);
            ec.debug(DBG_GEN, "Listening at address: " + addr);

            // Launch the RemoteAgent requesting a connection on that address
            String javaHome = System.getProperty("java.home");
            List<String> args = new ArrayList<>();
            args.add(javaHome == null
                    ? "java"
                    : javaHome + File.separator + "bin" + File.separator + "java");
            args.add("-agentlib:jdwp=transport=" + connector.transport().name() +
                    ",address=" + addr);
            args.addAll(remoteVMOptions);
            args.add(REMOTE_AGENT);
            args.add("" + port);
            ProcessBuilder pb = new ProcessBuilder(args);
            process = pb.start();

            // Forward out, err, and in
            forwardIO();

            // Accept the connection from the remote agent
            vm = listener.accept(connectorArgs);
            listener.stopListening(connectorArgs);
            return vm;
        } catch (Exception ex) {
            reportLaunchFail(ex, "listen");
        }
        return null;
    }

    private void reportLaunchFail(Exception ex, String context) {
        throw new InternalError("Failed remote " + context + ": " + connector +
                " -- " + connectorArgs, ex);
    }
}