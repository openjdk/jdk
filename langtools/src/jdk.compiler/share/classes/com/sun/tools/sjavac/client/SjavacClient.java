/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.sjavac.client;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Stream;

import com.sun.tools.sjavac.Log;
import com.sun.tools.sjavac.Util;
import com.sun.tools.sjavac.options.OptionHelper;
import com.sun.tools.sjavac.options.Options;
import com.sun.tools.sjavac.server.CompilationSubResult;
import com.sun.tools.sjavac.server.PortFile;
import com.sun.tools.sjavac.server.Sjavac;
import com.sun.tools.sjavac.server.SjavacServer;

import static java.util.stream.Collectors.joining;

/**
 * Sjavac implementation that delegates requests to a SjavacServer.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class SjavacClient implements Sjavac {

    // The id can perhaps be used in the future by the javac server to reuse the
    // JavaCompiler instance for several compiles using the same id.
    private final String id;
    private final PortFile portFile;

    // Default keepalive for server is 120 seconds.
    // I.e. it will accept 120 seconds of inactivity before quitting.
    private final int keepalive;
    private final int poolsize;

    // The sjavac option specifies how the server part of sjavac is spawned.
    // If you have the experimental sjavac in your path, you are done. If not, you have
    // to point to a com.sun.tools.sjavac.Main that supports --startserver
    // for example by setting: sjavac=java%20-jar%20...javac.jar%com.sun.tools.sjavac.Main
    private final String sjavacForkCmd;

    // Wait 2 seconds for response, before giving up on javac server.
    static int CONNECTION_TIMEOUT = 2000;
    static int MAX_CONNECT_ATTEMPTS = 3;
    static int WAIT_BETWEEN_CONNECT_ATTEMPTS = 2000;

    // Store the server conf settings here.
    private final String settings;

    public SjavacClient(Options options) {
        String tmpServerConf = options.getServerConf();
        String serverConf = (tmpServerConf!=null)? tmpServerConf : "";
        String tmpId = Util.extractStringOption("id", serverConf);
        id = (tmpId!=null) ? tmpId : "id"+(((new java.util.Random()).nextLong())&Long.MAX_VALUE);
        String defaultPortfile = options.getDestDir()
                                        .resolve("javac_server")
                                        .toAbsolutePath()
                                        .toString();
        String portfileName = Util.extractStringOption("portfile", serverConf, defaultPortfile);
        portFile = SjavacServer.getPortFile(portfileName);
        sjavacForkCmd = Util.extractStringOption("sjavac", serverConf, "sjavac");
        int poolsize = Util.extractIntOption("poolsize", serverConf);
        keepalive = Util.extractIntOption("keepalive", serverConf, 120);

        this.poolsize = poolsize > 0 ? poolsize : Runtime.getRuntime().availableProcessors();
        settings = (serverConf.equals("")) ? "id="+id+",portfile="+portfileName : serverConf;
    }

    /**
     * Hand out the server settings.
     * @return The server settings, possibly a default value.
     */
    public String serverSettings() {
        return settings;
    }

    @Override
    public int compile(String[] args) {
        int result = -1;
        try (Socket socket = tryConnect()) {
            PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Send args array to server
            out.println(args.length);
            for (String arg : args)
                out.println(arg);
            out.flush();

            // Read server response line by line
            String line;
            while (null != (line = in.readLine())) {
                if (!line.contains(":")) {
                    throw new AssertionError("Could not parse protocol line: >>\"" + line + "\"<<");
                }
                String[] typeAndContent = line.split(":", 2);
                String type = typeAndContent[0];
                String content = typeAndContent[1];

                try {
                    Log.log(Log.Level.valueOf(type), "[server] " + content);
                    continue;
                } catch (IllegalArgumentException e) {
                    // Parsing of 'type' as log level failed.
                }

                if (type.equals(SjavacServer.LINE_TYPE_RC)) {
                    result = Integer.parseInt(content);
                }
            }
        } catch (PortFileInaccessibleException e) {
            Log.error("Port file inaccessible.");
            result = CompilationSubResult.ERROR_FATAL;
        } catch (IOException ioe) {
            Log.error("IOException caught during compilation: " + ioe.getMessage());
            Log.debug(ioe);
            result = CompilationSubResult.ERROR_FATAL;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt(); // Restore interrupt
            Log.error("Compilation interrupted.");
            Log.debug(ie);
            result = CompilationSubResult.ERROR_FATAL;
        }
        return result;
    }

    /*
     * Makes MAX_CONNECT_ATTEMPTS attepmts to connect to server.
     */
    private Socket tryConnect() throws IOException, InterruptedException {
        makeSureServerIsRunning(portFile);
        int attempt = 0;
        while (true) {
            Log.debug("Trying to connect. Attempt " + (++attempt) + " of " + MAX_CONNECT_ATTEMPTS);
            try {
                return makeConnectionAttempt();
            } catch (IOException ex) {
                Log.error("Connection attempt failed: " + ex.getMessage());
                if (attempt >= MAX_CONNECT_ATTEMPTS) {
                    Log.error("Giving up");
                    throw new IOException("Could not connect to server", ex);
                }
            }
            Thread.sleep(WAIT_BETWEEN_CONNECT_ATTEMPTS);
        }
    }

    private Socket makeConnectionAttempt() throws IOException {
        Socket socket = new Socket();
        InetAddress localhost = InetAddress.getByName(null);
        InetSocketAddress address = new InetSocketAddress(localhost, portFile.getPort());
        socket.connect(address, CONNECTION_TIMEOUT);
        Log.debug("Connected");
        return socket;
    }

    /*
     * Will return immediately if a server already seems to be running,
     * otherwise fork a new server and block until it seems to be running.
     */
    private void makeSureServerIsRunning(PortFile portFile)
            throws IOException, InterruptedException {

        if (portFile.exists()) {
            portFile.lock();
            portFile.getValues();
            portFile.unlock();

            if (portFile.containsPortInfo()) {
                // Server seems to already be running
                return;
            }
        }

        // Fork a new server and wait for it to start
        SjavacClient.fork(sjavacForkCmd,
                          portFile,
                          poolsize,
                          keepalive);
    }

    @Override
    public void shutdown() {
        // Nothing to clean up
    }

    /*
     * Fork a server process process and wait for server to come around
     */
    public static void fork(String sjavacCmd, PortFile portFile, int poolsize, int keepalive)
            throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>();
        cmd.addAll(Arrays.asList(OptionHelper.unescapeCmdArg(sjavacCmd).split(" ")));
        cmd.add("--startserver:"
              + "portfile=" + portFile.getFilename()
              + ",poolsize=" + poolsize
              + ",keepalive="+ keepalive);

        Process serverProcess;
        Log.debug("Starting server. Command: " + String.join(" ", cmd));
        try {
            // If the cmd for some reason can't be executed (file is not found,
            // or is not executable for instance) this will throw an
            // IOException and p == null.
            serverProcess = new ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .start();
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
            portFile.waitForValidValues();
        } catch (IOException ex) {
            // Process was started, but server failed to initialize. This could
            // for instance be due to the JVM not finding the server class,
            // or the server running in to some exception early on.
            Log.error("Sjavac server failed to initialize: " + ex.getMessage());
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
