/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
import java.io.Writer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

import com.sun.tools.sjavac.Log;
import com.sun.tools.sjavac.Util;
import com.sun.tools.sjavac.options.OptionHelper;
import com.sun.tools.sjavac.options.Options;
import com.sun.tools.sjavac.server.CompilationSubResult;
import com.sun.tools.sjavac.server.PortFile;
import com.sun.tools.sjavac.server.Sjavac;
import com.sun.tools.sjavac.server.SjavacServer;

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
    private final String logfile;
    private final String stdouterrfile;

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

    public SjavacClient(Options options) throws PortFileInaccessibleException {
        String tmpServerConf = options.getServerConf();
        String serverConf = (tmpServerConf!=null)? tmpServerConf : "";
        String tmpId = Util.extractStringOption("id", serverConf);
        id = (tmpId!=null) ? tmpId : "id"+(((new java.util.Random()).nextLong())&Long.MAX_VALUE);
        String defaultPortfile = options.getStateDir()
                                        .resolve("javac_server")
                                        .toAbsolutePath()
                                        .toString();
        String portfileName = Util.extractStringOption("portfile", serverConf, defaultPortfile);
        try {
            portFile = SjavacServer.getPortFile(portfileName);
        } catch (PortFileInaccessibleException e) {
            Log.error("Port file inaccessable: " + e);
            throw e;
        }
        logfile = Util.extractStringOption("logfile", serverConf, portfileName + ".javaclog");
        stdouterrfile = Util.extractStringOption("stdouterrfile", serverConf, portfileName + ".stdouterr");
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
    public int compile(String[] args, Writer stdout, Writer stderr) {
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
                String[] typeAndContent = line.split(":", 2);
                String type = typeAndContent[0];
                String content = typeAndContent[1];
                switch (type) {
                case SjavacServer.LINE_TYPE_STDOUT:
                    stdout.write(content);
                    stdout.write('\n');
                    break;
                case SjavacServer.LINE_TYPE_STDERR:
                    stderr.write(content);
                    stderr.write('\n');
                    break;
                case SjavacServer.LINE_TYPE_RC:
                    result = Integer.parseInt(content);
                    break;
                }
            }
        } catch (IOException ioe) {
            Log.error("[CLIENT] Exception caught: " + ioe);
            result = CompilationSubResult.ERROR_FATAL;
            ioe.printStackTrace(new PrintWriter(stderr));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt(); // Restore interrupt
            Log.error("[CLIENT] compile interrupted.");
            result = CompilationSubResult.ERROR_FATAL;
            ie.printStackTrace(new PrintWriter(stderr));
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
            Log.info("Trying to connect. Attempt " + (++attempt) + " of " + MAX_CONNECT_ATTEMPTS);
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
        Log.info("Connected");
        return socket;
    }

    /*
     * Will return immediately if a server already seems to be running,
     * otherwise fork a new server and block until it seems to be running.
     */
    private void makeSureServerIsRunning(PortFile portFile)
            throws IOException, InterruptedException {

        portFile.lock();
        portFile.getValues();
        portFile.unlock();

        if (portFile.containsPortInfo()) {
            // Server seems to already be running
            return;
        }

        // Fork a new server and wait for it to start
        SjavacClient.fork(sjavacForkCmd,
                          portFile,
                          logfile,
                          poolsize,
                          keepalive,
                          System.err,
                          stdouterrfile);
    }

    @Override
    public void shutdown() {
        // Nothing to clean up
    }

    /*
     * Fork a server process process and wait for server to come around
     */
    public static void fork(String sjavacCmd,
                            PortFile portFile,
                            String logfile,
                            int poolsize,
                            int keepalive,
                            final PrintStream err,
                            String stdouterrfile)
                                    throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>();
        cmd.addAll(Arrays.asList(OptionHelper.unescapeCmdArg(sjavacCmd).split(" ")));
        cmd.add("--startserver:"
              + "portfile=" + portFile.getFilename()
              + ",logfile=" + logfile
              + ",stdouterrfile=" + stdouterrfile
              + ",poolsize=" + poolsize
              + ",keepalive="+ keepalive);

        Process p = null;
        Log.info("Starting server. Command: " + String.join(" ", cmd));
        try {
            // If the cmd for some reason can't be executed (file not found, or
            // is not executable) this will throw an IOException with a decent
            // error message.
            p = new ProcessBuilder(cmd)
                        .redirectErrorStream(true)
                        .redirectOutput(new File(stdouterrfile))
                        .start();

            // Throws an IOException if no valid values materialize
            portFile.waitForValidValues();

        } catch (IOException ex) {
            // Log and rethrow exception
            Log.error("Faild to launch server.");
            Log.error("    Message: " + ex.getMessage());
            String rc = p == null || p.isAlive() ? "n/a" : "" + p.exitValue();
            Log.error("    Server process exit code: " + rc);
            Log.error("Server log:");
            Log.error("------- Server log start -------");
            try (Scanner s = new Scanner(new File(stdouterrfile))) {
                while (s.hasNextLine())
                    Log.error(s.nextLine());
            }
            Log.error("------- Server log end ---------");
            throw ex;
        }
    }
}
