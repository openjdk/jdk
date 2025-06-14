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

package nsk.share.jpda;

import nsk.share.*;

import java.io.*;
import java.lang.ref.Cleaner;
import java.net.*;
import java.util.*;

/**
 * This class provides debugger with ability to launch
 * debuggee VM and to make connection to it using JDI connector or
 * JDWP transport.
 * <p>
 * <code>Binder</code> also makes it possible to establish TCP/IP
 * connection between debugger and debuggee throw <code>IOPipe</code>
 * object. This connection allows debugger to communicate with debuggee
 * by exchanging with synchronization messages and data.
 * <p>
 * To launch debuggee VM and bind to it use <code>bindToDebugee()</code>
 * method. This method construct mirror of debugee VM, represented by
 * object of <code>DebugeeProcess</code> class or derived. This mirror object
 * allows to control debuggee VM.
 * <p>
 * See also <code>nsk.share.jdi.Binder</code> and <code>nsk.share.jdwp.Binder</code>
 * classes which provide launching and binding to debuggee VM using specific
 * JDI and JDWP features.
 *
 * @see DebugeeArgumentHandler
 * @see DebugeeProcess
 * @see IOPipe
 *
 * @see nsk.share.jdb.Launcher
 * @see nsk.share.jdi.Binder
 * @see nsk.share.jdwp.Binder
 */
public class DebugeeBinder extends Log.Logger {

    private static final boolean IS_WINDOWS = System.getProperty("os.name")
                                                    .toLowerCase()
                                                    .startsWith("win");

    public static int TRY_DELAY = 1000;                     // milliseconds

    public static int CONNECT_TIMEOUT = 1 * 60 * 1000;      // milliseconds
    public static int CONNECT_TRY_DELAY = 2 * 1000;         // milliseconds
    public static int CONNECT_TRIES = CONNECT_TIMEOUT / CONNECT_TRY_DELAY;

    public static int THREAD_TIMEOUT = 2 * CONNECT_TRY_DELAY;  // milliseconds
    public static int PING_TIMEOUT = 30 * 1000;  // milliseconds

    public static int SOCKET_TIMEOUT = 2 * 1000;  // milliseconds
    public static int SOCKET_LINGER = 1;   // milliseconds

    private static int TRACE_LEVEL_PACKETS = 10;
    private static int TRACE_LEVEL_THREADS = 20;
    private static int TRACE_LEVEL_ACTIONS = 30;
    private static int TRACE_LEVEL_SOCKETS = 40;
    private static int TRACE_LEVEL_IO = 50;

    /**
     * Default message prefix for <code>Binder</code> object.
     */
    public static final String LOG_PREFIX = "binder> ";

    private DebugeeArgumentHandler argumentHandler = null;

    /**
     * Get version string.
     */
    public static String getVersion () {
        return "@(#)Binder.java %I% %E%";
    }

    // -------------------------------------------------- //
    private ServerSocket pipeServerSocket = null;

    // -------------------------------------------------- //
    /**
     * Incarnate new Binder obeying the given
     * <code>argumentHandler</code>; and assign the given
     * <code>log</code>.
     */
    public DebugeeBinder (DebugeeArgumentHandler argumentHandler, Log log) {
        super(log, LOG_PREFIX);
        this.argumentHandler = argumentHandler;
    }

    /**
     * Get argument handler of this binder object.
     */
    DebugeeArgumentHandler getArgumentHandler() {
        return argumentHandler;
    }

    // -------------------------------------------------- //

    /**
     * Wait for given thread finished for THREAD_TIMEOUT timeout and
     * interrupt this thread if not finished.
     *
     * @param thr thread to wait for
     * @param logger to write log messages to
     */
    public static void waitForThread(Thread thr, Log.Logger logger) {
        waitForThread(thr, THREAD_TIMEOUT, logger);
    }

    /**
     * Wait for given thread finished for specified timeout and
     * interrupt this thread if not finished.
     *
     * @param thr thread to wait for
     * @param millisecs timeout in milliseconds
     * @param logger to write log messages to
     */
    public static void waitForThread(Thread thr, long millisecs, Log.Logger logger) {
        if (thr != null) {
            if (thr.isAlive()) {
                try {
                    logger.trace(TRACE_LEVEL_THREADS, "Waiting for thread: " + thr.getName());
                    thr.join(millisecs);
                } catch (InterruptedException e) {
                    e.printStackTrace(logger.getOutStream());
                    throw new Failure ("Thread interrupted while waiting for another thread:\n\t"
                                         + e);
                } finally {
                    if (thr.isAlive()) {
                        logger.trace(TRACE_LEVEL_THREADS, "Interrupting not finished thread: " + thr);
                        thr.interrupt();
                    }
                }
            }
        }
    }


    /**
     * Make preperation for IOPipe connection before starting debugee VM process.
     * May change options in the passed <code>argumentHandler</code>.
     */
    public void prepareForPipeConnection(DebugeeArgumentHandler argumentHandler) {
        if (argumentHandler.isTransportAddressDynamic()) {
            try {
                pipeServerSocket = new ServerSocket();
                pipeServerSocket.setReuseAddress(false);
                pipeServerSocket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));

            } catch (IOException e) {
                e.printStackTrace(getOutStream());
                throw new Failure("Caught IOException while binding for IOPipe connection: \n\t"
                                + e);
            }

            int port = pipeServerSocket.getLocalPort();
            argumentHandler.setPipePortNumber(port);
        }
    }

    /**
     * Return already bound ServerSocket for IOPipe connection or null.
     */
    protected ServerSocket getPipeServerSocket() {
        return pipeServerSocket;
    }

    /**
     * Close ServerSocket used for IOPipeConnection if any.
     */
    private void closePipeServerSocket() {
        if (pipeServerSocket != null) {
            try {
                pipeServerSocket.close();
            } catch (IOException e) {
                println("# WARNING: Caught IOException while closing ServerSocket used for IOPipe connection: \n\t"
                        + e);
            }
        }
    }

    // -------------------------------------------------- //

    /**
     * Make environment for launching JVM process.
     */
    public String[] makeProcessEnvironment() {
/*
        String env = new String[0];
        return env;
 */
        return null;
    }

    /**
     * Launch process by the specified command line.
     *
     * @throws IOException if I/O error occured while launching process
     */
    public Process launchProcess(String cmdLine) throws IOException {
        String env[] = makeProcessEnvironment();
        return Runtime.getRuntime().exec(cmdLine, env);
    }

    /**
     * Launch process by the arguments array.
     *
     * @throws IOException if I/O error occured while launching process
     */
    public Process launchProcess(String[] args) throws IOException {
        String env[] = makeProcessEnvironment();
        return Runtime.getRuntime().exec(args, env);
    }

    /**
     * Make string representation of debuggee VM transport address according
     * to current command line options.
     */
    public String makeTransportAddress() {
        String address = null;
        if (argumentHandler.isSocketTransport()) {
            if (argumentHandler.isListeningConnector()) {
                address = argumentHandler.getTestHost()
                        + ":" + argumentHandler.getTransportPort();
            } else {
                address = argumentHandler.getTransportPort();
            }
        } else if (argumentHandler.isShmemTransport() ) {
            address = argumentHandler.getTransportSharedName();
        } else {
            throw new TestBug("Undefined transport type: "
                        + argumentHandler.getTransportType());
        }
        return address;
    }

    /**
     * Make command line to launch debugee VM as a string using given quote symbol,
     * using specified <code>transportAddress</code> for JDWP connection.
     */
    public String makeCommandLineString(String classToExecute, String transportAddress, String quote) {
        String[] args = makeCommandLineArgs(classToExecute, transportAddress);
        return ArgumentParser.joinArguments(args, quote);
    }

    /**
     * Make command line to launch debugee VM as a string using given quote symbol.
     */
    public String makeCommandLineString(String classToExecute, String quote) {
        return makeCommandLineString(classToExecute, makeTransportAddress(), quote);
    }

    /**
     * Make command line to launch debugee VM as a string using default quote symbol,
     * using specified <code>transportAddress</code> for JDWP connection.
     */
/*
    public String makeCommandLineString(String classToExecute, String transportAddress) {
        return makeCommandLineString(classToExecute, transportAddress, "\"");
    }
 */

    /**
     * Make command line to launch debugee VM as a string using default quote symbol.
     */
/*
    public String makeCommandLineString(String classToExecute) {
        return makeCommandLineString(classToExecute, makeTransportAddress());
    }
 */

    /**
     * Make command line to launch debugee VM as an array of arguments,
     * using specified <code>transportAddress</code> for JDWP connection.
     */
    public String[] makeCommandLineArgs(String classToExecute, String transportAddress) {
        Vector<String> args = new Vector<String>();

        args.add(argumentHandler.getLaunchExecPath());

        String javaOpts = argumentHandler.getLaunchOptions();
        if (javaOpts != null && javaOpts.length() > 0) {
            StringTokenizer st = new StringTokenizer(javaOpts);

            while (st.hasMoreTokens()) {
                args.add(st.nextToken());
            }
        }


        String classPath = System.getProperty("test.class.path");
        args.add("-classpath");
        args.add(classPath);


        String server;
        if (argumentHandler.isAttachingConnector()) {
            server = "y";
        } else {
            server = "n";
        }

        String jdwpArgs = "-Xrunjdwp:"
                        + "server=" + server
                        + ",transport=" + argumentHandler.getTransportName()
                        + ",address=" + transportAddress
                        + ",includevirtualthreads=y";

        if (! argumentHandler.isDefaultJVMDIStrictMode()) {
            if (argumentHandler.isJVMDIStrictMode())
                jdwpArgs += ",strict=y";
            else
                jdwpArgs += ",strict=n";
        }

        args.add(jdwpArgs);

        if (System.getProperty("test.thread.factory") != null) {
            args.add(MainWrapper.class.getName());
            args.add(System.getProperty("test.thread.factory"));
        }

        if (classToExecute != null) {
            StringTokenizer st = new StringTokenizer(classToExecute);

            while (st.hasMoreTokens()) {
                args.add(st.nextToken());
            }
        }

        String[] rawArgs = argumentHandler.getRawArguments();
        for (int i = 0; i < rawArgs.length; i++) {
            String rawArg = rawArgs[i];
            // " has to be escaped on windows
            if (IS_WINDOWS) {
                rawArg = rawArg.replace("\"", "\\\"");
            }
            args.add(rawArg);
        }

        String[] argsArray = new String[args.size()];
        for (int i = 0; i < args.size(); i++) {
            argsArray[i] = (String) args.elementAt(i);
        }

        return argsArray;
    }

    /**
     * Make command line to launch debugee VM as an array of arguments.
     */
    public String[] makeCommandLineArgs(String classToExecute) {
        return makeCommandLineArgs(classToExecute, makeTransportAddress());
    }


    /**
     * Close binder by closing all started threads.
     */
    public void close() {
        closePipeServerSocket();
    }

} // DebugeeBinder
