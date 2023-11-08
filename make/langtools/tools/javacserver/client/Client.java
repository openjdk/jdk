/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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

package javacserver.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javacserver.server.Server;
import javacserver.shared.PortFileInaccessibleException;
import javacserver.shared.Protocol;
import javacserver.shared.Result;
import javacserver.util.AutoFlushWriter;
import javacserver.util.Log;

/**
 * The javacserver client. This is called from the makefiles, and is responsible for passing the command
 * line on to a server instance running javac, starting a new server if needed.
 */
public class Client {
    private static final Log.Level LOG_LEVEL = Log.Level.INFO;

    // Wait 4 seconds for response, before giving up on javac server.
    private static final int CONNECTION_TIMEOUT = 4000;
    private static final int MAX_CONNECT_ATTEMPTS = 10;
    private static final int WAIT_BETWEEN_CONNECT_ATTEMPTS = 2000;

    private final ClientConfiguration conf;

    public Client(ClientConfiguration conf) {
        this.conf = conf;
    }

    public static void main(String... args) {
        Log.setLogForCurrentThread(new Log(
                new AutoFlushWriter(new OutputStreamWriter(System.out)),
                new AutoFlushWriter(new OutputStreamWriter(System.err))));
        Log.setLogLevel(LOG_LEVEL);

        ClientConfiguration conf = ClientConfiguration.fromCommandLineArguments(args);
        if (conf == null) {
            System.exit(Result.CMDERR.exitCode);
        }

        Client client = new Client(conf);
        int exitCode = client.dispatchToServer();

        System.exit(exitCode);
    }

    private int dispatchToServer() {
        try {
            // Check if server seems to be already running
            if (!conf.portFile().hasValidValues()) {
                // Fork a new server and wait for it to start
                startNewServer();
            }

            try (Socket socket = tryConnect()) {
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));

                Protocol.sendCommand(out, conf.javacArgs());
                int exitCode = Protocol.readResponse(in);

                return exitCode;
            }
        } catch (PortFileInaccessibleException e) {
            Log.error("Port file inaccessible.");
            return Result.ERROR.exitCode;
        } catch (IOException ioe) {
            Log.error("IOException caught during compilation: " + ioe.getMessage());
            Log.debug(ioe);
            return Result.ERROR.exitCode;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt(); // Restore interrupt
            Log.error("Compilation interrupted.");
            Log.debug(ie);
            return Result.ERROR.exitCode;
        }
    }

    /*
     * Makes MAX_CONNECT_ATTEMPTS attempts to connect to server.
     */
    private Socket tryConnect() throws IOException, InterruptedException {
        int attempt = 0;

        while (true) {
            Log.debug("Trying to connect. Attempt " + (++attempt) + " of " + MAX_CONNECT_ATTEMPTS);
            try {
                Socket socket = new Socket();
                InetAddress localhost = InetAddress.getByName(null);
                InetSocketAddress address = new InetSocketAddress(localhost, conf.portFile().getPort());
                socket.connect(address, CONNECTION_TIMEOUT);
                Log.debug("Connected");
                return socket;
            } catch (IOException ex) {
                Log.error("Connection attempt failed: " + ex.getMessage());
                if (attempt >= MAX_CONNECT_ATTEMPTS) {
                    Log.error("Giving up");
                    throw new IOException("Could not connect to server after " + MAX_CONNECT_ATTEMPTS + " attempts with timeout " + CONNECTION_TIMEOUT, ex);
                }
            }
            Thread.sleep(WAIT_BETWEEN_CONNECT_ATTEMPTS);
        }
    }

    /*
     * Fork a server process and wait for server to come around
     */
    private void startNewServer() throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>();
        // conf.javaCommand() is how to start java in the way we want to run
        // the server
        cmd.addAll(Arrays.asList(conf.javaCommand().split(" ")));
        // javacserver.server.Server is the server main class
        cmd.add(Server.class.getName());
        // and it expects a port file path
        cmd.add(conf.portFile().getFilename());

        Process serverProcess;
        Log.debug("Starting server. Command: " + String.join(" ", cmd));
        try {
            // If the cmd for some reason can't be executed (file is not found,
            // or is not executable for instance) this will throw an
            // IOException
            serverProcess = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        } catch (IOException ex) {
            // Message is typically something like:
            // Cannot run program "xyz": error=2, No such file or directory
            Log.error("Failed to create server process: " + ex.getMessage());
            Log.debug(ex);
            throw new IOException(ex);
        }

        // serverProcess != null at this point.
        try {
            // Throws an IOException if no valid values materialize
            conf.portFile().waitForValidValues();
        } catch (IOException ex) {
            // Process was started, but server failed to initialize. This could
            // for instance be due to the JVM not finding the server class,
            // or the server running in to some exception early on.
            Log.error("javacserver server process failed to initialize: " + ex.getMessage());
            Log.error("Process output:");
            Reader serverStdoutStderr = new InputStreamReader(serverProcess.getInputStream());
            try (BufferedReader br = new BufferedReader(serverStdoutStderr)) {
                br.lines().forEach(Log::error);
            }
            Log.error("<End of process output>");
            try {
                Log.error("Process exit code: " + serverProcess.exitValue());
            } catch (IllegalThreadStateException e) {
                // Server is presumably still running.
            }
            throw new IOException("Server failed to initialize: " + ex.getMessage(), ex);
        }
    }
}
