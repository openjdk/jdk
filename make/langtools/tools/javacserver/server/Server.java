/*
 * Copyright (c) 2011, 2022, Oracle and/or its affiliates. All rights reserved.
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

package javacserver.server;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.spi.ToolProvider;
import javacserver.shared.PortFile;
import javacserver.shared.Protocol;
import javacserver.shared.Result;
import javacserver.util.LazyInitFileLog;
import javacserver.util.Log;
import javacserver.util.LoggingOutputStream;
import javacserver.util.Util;

/**
 * Start a new server main thread, that will listen to incoming connection requests from the client,
 * and dispatch these on to worker threads in a thread pool, running javac.
 */
public class Server {
    private ServerSocket serverSocket;
    private PortFile portFile;
    private PortFileMonitor portFileMonitor;
    private IdleMonitor idleMonitor;
    private CompilerThreadPool compilerThreadPool;

    // Set to false break accept loop
    final AtomicBoolean keepAcceptingRequests = new AtomicBoolean();

    // For logging server internal (non request specific) errors.
    private static LazyInitFileLog errorLog;

    public static void main(String... args) {
        initLogging();

        try {
            PortFile portFile = getPortFileFromArguments(args);
            if (portFile == null) {
                System.exit(Result.CMDERR.exitCode);
                return;
            }

            Server server = new Server(portFile);
            if (!server.start()) {
                System.exit(Result.ERROR.exitCode);
            } else {
                System.exit(Result.OK.exitCode);
            }
        } catch (IOException | InterruptedException ex) {
            ex.printStackTrace();
            System.exit(Result.ERROR.exitCode);
        }
    }

    private static void initLogging() {
        // Under normal operation, all logging messages generated server-side
        // are due to compilation requests. These logging messages should
        // be relayed back to the requesting client rather than written to the
        // server log. The only messages that should be written to the server
        // log (in production mode) should be errors,
        errorLog = new LazyInitFileLog("server.log");
        Log.setLogForCurrentThread(errorLog);
        Log.setLogLevel(Log.Level.ERROR); // should be set to ERROR.

        // Make sure no exceptions go under the radar
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            restoreServerErrorLog();
            Log.error(e);
        });

        // Inevitably someone will try to print messages using System.{out,err}.
        // Make sure this output also ends up in the log.
        System.setOut(new PrintStream(new LoggingOutputStream(System.out, Log.Level.INFO, "[stdout] ")));
        System.setErr(new PrintStream(new LoggingOutputStream(System.err, Log.Level.ERROR, "[stderr] ")));
    }

    private static PortFile getPortFileFromArguments(String[] args) {
        if (args.length != 1) {
            Log.error("javacserver daemon incorrectly called");
            return null;
        }
        String portfilename = args[0];
        PortFile portFile = new PortFile(portfilename);
        return portFile;
    }

    public Server(PortFile portFile) throws FileNotFoundException {
        this.portFile = portFile;
    }

    /**
     * Start the daemon, unless another one is already running, in which it returns
     * false and exits immediately.
     */
    private boolean start() throws IOException, InterruptedException {
        // The port file is locked and the server port and cookie is written into it.
        portFile.lock();
        portFile.getValues();
        if (portFile.containsPortInfo()) {
            Log.debug("javacserver daemon not started because portfile exists!");
            portFile.unlock();
            return false;
        }

        serverSocket = new ServerSocket();
        InetAddress localhost = InetAddress.getByName(null);
        serverSocket.bind(new InetSocketAddress(localhost, 0));

        // At this point the server accepts connections, so it is  now safe
        // to publish the port / cookie information

        // The secret cookie shared between server and client through the port file.
        // Used to prevent clients from believing that they are communicating with
        // an old server when a new server has started and reused the same port as
        // an old server.
        long myCookie = new Random().nextLong();
        portFile.setValues(serverSocket.getLocalPort(), myCookie);
        portFile.unlock();

        portFileMonitor = new PortFileMonitor(portFile, this::shutdownServer);
        portFileMonitor.start();
        compilerThreadPool = new CompilerThreadPool();
        idleMonitor = new IdleMonitor(this::shutdownServer);

        Log.debug("javacserver daemon started. Accepting connections...");
        Log.debug("    port: " + serverSocket.getLocalPort());
        Log.debug("    time: " + new java.util.Date());
        Log.debug("    poolsize: " + compilerThreadPool.poolSize());

        keepAcceptingRequests.set(true);
        do {
            try {
                Socket socket = serverSocket.accept();
                 // Handle each incoming request in a threapool thread
                compilerThreadPool.execute(() -> handleRequest(socket));
            } catch (SocketException se) {
                // Caused by serverSocket.close() and indicates shutdown
            }
        } while (keepAcceptingRequests.get());

        Log.debug("Shutting down.");

        // No more connections accepted. If any client managed to connect after
        // the accept() was interrupted but before the server socket is closed
        // here, any attempt to read or write to the socket will result in an
        // IOException on the client side.

        // Shut down
        idleMonitor.shutdown();
        compilerThreadPool.shutdown();

        return true;
    }

    private void handleRequest(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            try {
                idleMonitor.startCall();

                // Set up logging for this thread. Stream back logging messages to
                // client on the format "level:msg".
                Log.setLogForCurrentThread(new Protocol.ProtocolLog(out));

                String[] args = Protocol.readCommand(in);

                // If there has been any internal errors, notify client
                checkInternalErrorLog();

                // Perform compilation
                int exitCode = runCompiler(args);

                Protocol.sendExitCode(out, exitCode);

                // Check for internal errors again.
                checkInternalErrorLog();
            } finally {
                idleMonitor.endCall();
            }
        } catch (Exception ex) {
            // Not much to be done at this point. The client side request
            // code will most likely throw an IOException and the
            // compilation will fail.
            ex.printStackTrace();
            Log.error(ex);
        } finally {
            Log.setLogForCurrentThread(null);
        }
    }

    public static int runCompiler(String[] args) {
        // Direct logging to our byte array stream.
        StringWriter strWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(strWriter);

        // Compile
        Optional<ToolProvider> tool = ToolProvider.findFirst("javac");
        if (tool.isEmpty()) {
            Log.error("Can't find tool javac");
            return Result.ERROR.exitCode;
        }
        int exitcode = tool.get().run(printWriter, printWriter, args);

        // Process compiler output (which is always errors)
        printWriter.flush();
        Util.getLines(strWriter.toString()).forEach(Log::error);

        return exitcode;
    }

    private void checkInternalErrorLog() {
        Path errorLogPath = errorLog.getLogDestination();
        if (errorLogPath != null) {
            Log.error("Server has encountered an internal error. See " + errorLogPath.toAbsolutePath()
                    + " for details.");
        }
    }

    public static void restoreServerErrorLog() {
        Log.setLogForCurrentThread(errorLog);
    }

    public void shutdownServer(String quitMsg) {
        if (!keepAcceptingRequests.compareAndSet(true, false)) {
            // Already stopped, no need to shut down again
            return;
        }

        Log.debug("Quitting: " + quitMsg);

        portFileMonitor.shutdown(); // No longer any need to monitor port file

        // Unpublish port before shutting down socket to minimize the number of
        // failed connection attempts
        try {
            portFile.delete();
        } catch (IOException | InterruptedException e) {
            Log.error(e);
        }
        try {
            serverSocket.close();
        } catch (IOException e) {
            Log.error(e);
        }
    }
}
