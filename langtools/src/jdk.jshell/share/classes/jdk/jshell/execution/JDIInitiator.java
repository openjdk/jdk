/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jshell.execution;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import com.sun.jdi.Bootstrap;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.connect.ListeningConnector;

/**
 * Sets up a JDI connection, providing the resulting JDI {@link VirtualMachine}
 * and the {@link Process} the remote agent is running in.
 */
public class JDIInitiator {

    private VirtualMachine vm;
    private Process process = null;
    private final Connector connector;
    private final String remoteAgent;
    private final Map<String, com.sun.jdi.connect.Connector.Argument> connectorArgs;

    /**
     * Start the remote agent and establish a JDI connection to it.
     *
     * @param port the socket port for (non-JDI) commands
     * @param remoteVMOptions any user requested VM options
     * @param remoteAgent full class name of remote agent to launch
     * @param isLaunch does JDI do the launch? That is, LaunchingConnector,
     * otherwise we start explicitly and use ListeningConnector
     */
    public JDIInitiator(int port, List<String> remoteVMOptions,
            String remoteAgent, boolean isLaunch) {
        this.remoteAgent = remoteAgent;
        String connectorName
                = isLaunch
                        ? "com.sun.jdi.CommandLineLaunch"
                        : "com.sun.jdi.SocketListen";
        this.connector = findConnector(connectorName);
        if (connector == null) {
            throw new IllegalArgumentException("No connector named: " + connectorName);
        }
        Map<String, String> argumentName2Value
                = isLaunch
                        ? launchArgs(port, String.join(" ", remoteVMOptions))
                        : new HashMap<>();
        this.connectorArgs = mergeConnectorArgs(connector, argumentName2Value);
        this.vm = isLaunch
                ? launchTarget()
                : listenTarget(port, remoteVMOptions);

    }

    /**
     * Returns the resulting {@code VirtualMachine} instance.
     *
     * @return the virtual machine
     */
    public VirtualMachine vm() {
        return vm;
    }

    /**
     * Returns the launched process.
     *
     * @return the remote agent process
     */
    public Process process() {
        return process;
    }

    /* launch child target vm */
    private VirtualMachine launchTarget() {
        LaunchingConnector launcher = (LaunchingConnector) connector;
        try {
            VirtualMachine new_vm = launcher.launch(connectorArgs);
            process = new_vm.process();
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
            debug("Listening at address: " + addr);

            // Launch the RemoteAgent requesting a connection on that address
            String javaHome = System.getProperty("java.home");
            List<String> args = new ArrayList<>();
            args.add(javaHome == null
                    ? "java"
                    : javaHome + File.separator + "bin" + File.separator + "java");
            args.add("-agentlib:jdwp=transport=" + connector.transport().name() +
                    ",address=" + addr);
            args.addAll(remoteVMOptions);
            args.add(remoteAgent);
            args.add("" + port);
            ProcessBuilder pb = new ProcessBuilder(args);
            process = pb.start();

            // Forward out, err, and in
            // Accept the connection from the remote agent
            vm = listener.accept(connectorArgs);
            listener.stopListening(connectorArgs);
            return vm;
        } catch (Exception ex) {
            reportLaunchFail(ex, "listen");
        }
        return null;
    }

    private Connector findConnector(String name) {
        for (Connector cntor
                : Bootstrap.virtualMachineManager().allConnectors()) {
            if (cntor.name().equals(name)) {
                return cntor;
            }
        }
        return null;
    }

    private Map<String, Connector.Argument> mergeConnectorArgs(Connector connector, Map<String, String> argumentName2Value) {
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
    private Map<String, String> launchArgs(int port, String remoteVMOptions) {
        Map<String, String> argumentName2Value = new HashMap<>();
        argumentName2Value.put("main", remoteAgent + " " + port);
        argumentName2Value.put("options", remoteVMOptions);
        return argumentName2Value;
    }

    private void reportLaunchFail(Exception ex, String context) {
        throw new InternalError("Failed remote " + context + ": " + connector +
                " -- " + connectorArgs, ex);
    }

    /**
     * Log debugging information. Arguments as for {@code printf}.
     *
     * @param format a format string as described in Format string syntax
     * @param args arguments referenced by the format specifiers in the format
     * string.
     */
    private void debug(String format, Object... args) {
        // Reserved for future logging
    }

    /**
     * Log a serious unexpected internal exception.
     *
     * @param ex the exception
     * @param where a description of the context of the exception
     */
    private void debug(Throwable ex, String where) {
        // Reserved for future logging
    }

}
