/*
 * Copyright (c) 1998, 2015, Oracle and/or its affiliates. All rights reserved.
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


package jdk.jshell;

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

    private VirtualMachine vm;
    private Process process = null;
    private int outputCompleteCount = 0;

    private final JShell proc;
    private final JDIEnv env;
    private final Connector connector;
    private final Map<String, com.sun.jdi.connect.Connector.Argument> connectorArgs;
    private final int traceFlags;

    synchronized void notifyOutputComplete() {
        outputCompleteCount++;
        notifyAll();
    }

    synchronized void waitOutputComplete() {
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

    JDIConnection(JDIEnv env, String connectorName, Map<String, String> argumentName2Value, int traceFlags, JShell proc) {
        this.env = env;
        this.proc = proc;
        this.connector = findConnector(connectorName);

        if (connector == null) {
            throw new IllegalArgumentException("No connector named: " + connectorName);
        }

        connectorArgs = mergeConnectorArgs(connector, argumentName2Value);
        this.traceFlags = traceFlags;
    }

    synchronized VirtualMachine open() {
        if (connector instanceof LaunchingConnector) {
            vm = launchTarget();
        } else if (connector instanceof AttachingConnector) {
            vm = attachTarget();
        } else if (connector instanceof ListeningConnector) {
            vm = listenTarget();
        } else {
            throw new InternalError("Invalid connect type");
        }
        vm.setDebugTraceMode(traceFlags);
        // Uncomment here and below to enable event requests
        // installEventRequests(vm);

        return vm;
    }

    boolean setConnectorArg(String name, String value) {
        /*
         * Too late if the connection already made
         */
        if (vm != null) {
            return false;
        }

        Connector.Argument argument = connectorArgs.get(name);
        if (argument == null) {
            return false;
        }
        argument.setValue(value);
        return true;
    }

    String connectorArg(String name) {
        Connector.Argument argument = connectorArgs.get(name);
        if (argument == null) {
            return "";
        }
        return argument.value();
    }

    public synchronized VirtualMachine vm() {
        if (vm == null) {
            throw new JDINotConnectedException();
        } else {
            return vm;
        }
    }

    boolean isOpen() {
        return (vm != null);
    }

    boolean isLaunch() {
        return (connector instanceof LaunchingConnector);
    }

    public void disposeVM() {
        try {
            if (vm != null) {
                vm.dispose(); // This could NPE, so it is caught below
                vm = null;
            }
        } catch (VMDisconnectedException | NullPointerException ex) {
            // Ignore if already closed
        } finally {
            if (process != null) {
                process.destroy();
                process = null;
            }
            waitOutputComplete();
        }
    }

/*** Preserved for possible future support of event requests

    private void installEventRequests(VirtualMachine vm) {
        if (vm.canBeModified()){
            setEventRequests(vm);
            resolveEventRequests();
        }
    }

    private void setEventRequests(VirtualMachine vm) {
        EventRequestManager erm = vm.eventRequestManager();

        // Normally, we want all uncaught exceptions.  We request them
        // via the same mechanism as Commands.commandCatchException()
        // so the user can ignore them later if they are not
        // interested.
        // FIXME: this works but generates spurious messages on stdout
        //        during startup:
        //          Set uncaught java.lang.Throwable
        //          Set deferred uncaught java.lang.Throwable
        Commands evaluator = new Commands();
        evaluator.commandCatchException
            (new StringTokenizer("uncaught java.lang.Throwable"));

        ThreadStartRequest tsr = erm.createThreadStartRequest();
        tsr.enable();
        ThreadDeathRequest tdr = erm.createThreadDeathRequest();
        tdr.enable();
    }

    private void resolveEventRequests() {
        Env.specList.resolveAll();
    }
***/

    private void dumpStream(InputStream inStream, final PrintStream pStream) throws IOException {
        BufferedReader in =
            new BufferedReader(new InputStreamReader(inStream));
        int i;
        try {
            while ((i = in.read()) != -1) {
                pStream.print((char) i);
            }
        } catch (IOException ex) {
            String s = ex.getMessage();
            if (!s.startsWith("Bad file number")) {
                throw ex;
            }
            // else we got a Bad file number IOException which just means
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
                    proc.debug(ex, "Failed reading output");
                    env.shutdown();
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
                    proc.debug(ex, "Failed reading output");
                    env.shutdown();
                }
            }
        };
        thr.setPriority(Thread.MAX_PRIORITY-1);
        thr.start();
    }

    /* launch child target vm */
    private VirtualMachine launchTarget() {
        LaunchingConnector launcher = (LaunchingConnector)connector;
        try {
            VirtualMachine new_vm = launcher.launch(connectorArgs);
            process = new_vm.process();
            displayRemoteOutput(process.getErrorStream(), proc.err);
            displayRemoteOutput(process.getInputStream(), proc.out);
            readRemoteInput(process.getOutputStream(), proc.in);
            return new_vm;
        } catch (Exception ex) {
            reportLaunchFail(ex, "launch");
        }
        return null;
    }

    /* JShell currently uses only launch, preserved for futures: */
    /* attach to running target vm */
    private VirtualMachine attachTarget() {
        AttachingConnector attacher = (AttachingConnector)connector;
        try {
            return attacher.attach(connectorArgs);
        } catch (Exception ex) {
            reportLaunchFail(ex, "attach");
        }
        return null;
    }

    /* JShell currently uses only launch, preserved for futures: */
    /* listen for connection from target vm */
    private VirtualMachine listenTarget() {
        ListeningConnector listener = (ListeningConnector)connector;
        try {
            String retAddress = listener.startListening(connectorArgs);
            proc.debug(DBG_GEN, "Listening at address: " + retAddress);
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
