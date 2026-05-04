/*
 * Copyright (c) 2002, 2025, Oracle and/or its affiliates. All rights reserved.
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

package nsk.share.jdb;

import nsk.share.*;
import nsk.share.jpda.*;
import nsk.share.jdi.ArgumentHandler;

import java.io.*;
import java.util.*;

/**
 * This class provides launching of <code>jdb</code> and debuggee
 * according to test command line options.
 */

public class Launcher extends DebugeeBinder {

    /* Delay in milliseconds after launching jdb.*/
    static final long DEBUGGEE_START_DELAY = 5 * 1000;

    protected static Jdb jdb;

    protected static Debuggee debuggee;

    /** Pattern for message of jdb has started. */
    protected static String JDB_STARTED = "Initializing jdb";

    /**
     * Get version string.
     */
    public static String getVersion () {
        return "@(#)Launcher.java %I% %E%";
    }

    // -------------------------------------------------- //

    /**
     * Handler of command line arguments.
     */
    protected static JdbArgumentHandler argumentHandler = null;

    /**
     * Return <code>argumentHandler</code> of this binder.
     */
    public static JdbArgumentHandler getJdbArgumentHandler() {
        return argumentHandler;
    }

    /**
     * Return <code>jdb</code> mirror of this binder.
     */
    public static Jdb getJdb() {
        return jdb;
    }

    /**
     * Return debuggee mirror of this binder.
     */
    public static Debuggee getDebuggee() {
        return debuggee;
    }

    /**
     * Incarnate new Launcher obeying the given
     * <code>argumentHandler</code>; and assign the given
     * <code>log</code>.
     */
    public Launcher (JdbArgumentHandler argumentHandler, Log log) {
        super(argumentHandler, log);
        setLogPrefix("launcher > ");
        this.argumentHandler = argumentHandler;
    }

    /**
     * Defines type of connector (default, launching,
     * raw launching, attaching or listening) according to options
     * parsed by <code>JdbArgumentHandler</code>. And then launches <code>jdb</code>
     * and debuggee.
     */
    public void launchJdbAndDebuggee (String classToExecute) throws IOException {

        String[] jdbCmdArgs = makeJdbCmdLine(classToExecute);

        if (argumentHandler.isDefaultConnector()) {
            defaultLaunch(jdbCmdArgs, classToExecute);
        } else if (argumentHandler.isRawLaunchingConnector()) {
            rawLaunch(jdbCmdArgs, classToExecute);
        } else if (argumentHandler.isLaunchingConnector()) {
            launchFromJdb(jdbCmdArgs, classToExecute);
        } else if (argumentHandler.isAttachingConnector()) {
            launchAndAttach(jdbCmdArgs, classToExecute);
        } else if (argumentHandler.isListeningConnector()) {
            launchAndListen(jdbCmdArgs, classToExecute);
        } else {
            throw new TestBug("Unexpected connector type: "
                              + argumentHandler.getConnectorType());
        }

    }

    /**
     * Creates String array to launch <code>jdb</code> according to options
     * parsed by <code>JdbArgumentHandler</code>.
     */
    private String[] makeJdbCmdLine (String classToExecute) {

        Vector<String> args = new Vector<String>();

        String jdbExecPath = argumentHandler.getJdbExecPath();
        args.add(jdbExecPath.trim());

        if (argumentHandler.isLaunchingConnector()) {
            boolean vthreadMode = "Virtual".equals(System.getProperty("test.thread.factory"));
            if (vthreadMode) {
                /* Some tests need more carrier threads than the default provided. */
                args.add("-R-Djdk.virtualThreadScheduler.parallelism=15");
            }
            /* Some jdb tests need java.library.path setup for native libraries. */
            String libpath = System.getProperty("java.library.path");
            args.add("-R-Djava.library.path=" + libpath);
        }

        args.addAll(argumentHandler.enwrapJavaOptions(argumentHandler.getJavaOptions()));

        String jdbOptions = argumentHandler.getJdbOptions();
        if (jdbOptions.trim().length() > 0) {
            StringTokenizer tokenizer = new StringTokenizer(jdbOptions);
            while (tokenizer.hasMoreTokens()) {
                String option = tokenizer.nextToken();
                args.add(option);
            }
        }
        if (classToExecute == null)
            return args.toArray(new String[args.size()]);
        args.add("-connect");
        StringBuffer connect = new StringBuffer();

        if (argumentHandler.isLaunchingConnector()) {

// Do not need to use quote symbol.
//            String quote = '\"';
//            connect.append(quote + argumentHandler.getConnectorName() + ":");
            connect.append(argumentHandler.getConnectorName() + ":");

            String connectorAddress;
            String vmAddress = makeTransportAddress();

            if (argumentHandler.isRawLaunchingConnector()) {

                if (argumentHandler.isSocketTransport()) {
                    connectorAddress = argumentHandler.getTransportPort();
                } else if (argumentHandler.isShmemTransport() ) {
                    connectorAddress = argumentHandler.getTransportSharedName();
                } else {
                    throw new TestBug("Launcher: Undefined transport type for RawLaunchingConnector");
                }

                connect.append("address=" + connectorAddress.trim());
                connect.append(",command=" + makeCommandLineString(classToExecute, vmAddress, " ").trim());

            } else /* LaunchingConnector or DefaultConnector */ {

                connect.append("vmexec=" + argumentHandler.getLaunchExecName().trim());
                connect.append(",options=");
                connect.append(" \"-cp\"");
                connect.append(" \"" + System.getProperty("test.class.path") + "\"");

                String debuggeeOpts = argumentHandler.getDebuggeeOptions();
                if (debuggeeOpts.trim().length() > 0) {
                    for (String arg : debuggeeOpts.split("\\s+")) {
                       connect.append(" \"");
                       connect.append(arg);
                       connect.append("\"");
                    }
                }
                String cmdline = classToExecute + " " + ArgumentHandler.joinArguments(argumentHandler.getArguments(), " ");
                cmdline += " -waittime " + argumentHandler.getWaitTime();
                if (argumentHandler.verbose()) {
                    cmdline += " -verbose";
                }
                if (System.getProperty("test.thread.factory") != null) {
                    cmdline = MainWrapper.class.getName() + " " + System.getProperty("test.thread.factory") +  " " + cmdline;
                }
                connect.append(",main=" + cmdline.trim());

            }

//            connect.append(quote);

        } else {

            connect.append(argumentHandler.getConnectorName() + ":");

            if (argumentHandler.isAttachingConnector()) {

                if (argumentHandler.isSocketTransport()) {
                    connect.append("port=" + argumentHandler.getTransportPort().trim());
                } else if (argumentHandler.isShmemTransport()) {
                    connect.append("name=" + argumentHandler.getTransportSharedName().trim());
                } else {
                    throw new TestBug("Launcher: Undefined transport type for AttachingConnector");
                }


            } else if (argumentHandler.isListeningConnector()) {

                if (!argumentHandler.isTransportAddressDynamic()) {
                    if (argumentHandler.isSocketTransport()) {
                        connect.append("port=" + argumentHandler.getTransportPort().trim());
                    } else if (argumentHandler.isShmemTransport()) {
                        connect.append("name=" + argumentHandler.getTransportSharedName().trim());
                    } else {
                        throw new TestBug("Launcher: Undefined transport type for AttachingConnector");
                    }
                }

            } else {
                throw new TestBug("Launcher: Undefined connector type");
            }

        }

        args.add(connect.toString().trim());

        String[] argsArray = new String[args.size()];
        for (int i = 0; i < args.size(); i++) {
            argsArray[i] = (String) args.elementAt(i);
        }

        return argsArray;
    }

    // ---------------------------------------------- //

    /**
     * Run test using default connector.
     */
    private void defaultLaunch
       (String[] jdbCmdArgs, String classToExecute) throws IOException {
        launchFromJdb(jdbCmdArgs, classToExecute);
    }

    /**
     * Run test using raw launching connector.
     */
    private void rawLaunch
       (String[] jdbCmdArgs, String classToExecute) throws IOException {
        launchFromJdb(jdbCmdArgs, classToExecute);
    }

    /**
     * Run test using launching connector.
     */
    private void launchFromJdb
       (String[] jdbCmdArgs, String classToExecute) throws IOException {

        jdb = new Jdb(this);
        display("Starting jdb launching debuggee");
        jdb.launch(jdbCmdArgs);

        if (classToExecute != null)
            jdb.waitForMessage(0, JDB_STARTED);
//        jdb.waitForPrompt(0, false);

    }

    /**
     * Run test using attaching connector.
     */
    private void launchAndAttach
       (String[] jdbCmdArgs, String classToExecute) throws IOException {

        debuggee = new Debuggee(this);
        String address = makeTransportAddress();
        String[] javaCmdArgs = makeCommandLineArgs(classToExecute, address);
        debuggee.launch(javaCmdArgs);

        display("Starting jdb attaching to debuggee");
        jdb = Jdb.startAttachingJdb (this, jdbCmdArgs, JDB_STARTED);
//        jdb.waitForPrompt(0, false);
    }

    /**
     * Run test using listening connector.
     */
    private void launchAndListen
       (String[] jdbCmdArgs, String classToExecute) throws IOException {

        jdb = new Jdb(this);
        display("Starting jdb listening to debuggee");
        jdb.launch(jdbCmdArgs);
        String address = jdb.waitForListeningJdb();
        display("Listening address found: " + address);

        debuggee = new Debuggee(this);
        String[] javaCmdArgs = makeCommandLineArgs(classToExecute, address);
        debuggee.launch(javaCmdArgs);

//        jdb.waitForPrompt(0, false);
    }


} // End of Launcher
