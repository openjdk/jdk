/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.util.List;
import java.util.Set;

import com.sun.tools.sjavac.Log;
import com.sun.tools.sjavac.ProblemException;
import com.sun.tools.sjavac.Util;
import com.sun.tools.sjavac.options.Options;
import com.sun.tools.sjavac.server.CompilationResult;
import com.sun.tools.sjavac.server.PortFile;
import com.sun.tools.sjavac.server.Sjavac;
import com.sun.tools.sjavac.server.SjavacServer;
import com.sun.tools.sjavac.server.SysInfo;

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
    private final String portfileName;
    private final String logfile;
    private final String stdouterrfile;
    private final boolean background;

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
        String p = Util.extractStringOption("portfile", serverConf);
        portfileName = (p!=null) ? p : options.getStateDir().toFile().getAbsolutePath()+File.separatorChar+"javac_server";
        logfile = Util.extractStringOption("logfile", serverConf, portfileName + ".javaclog");
        stdouterrfile = Util.extractStringOption("stdouterrfile", serverConf, portfileName + ".stdouterr");
        background = Util.extractBooleanOption("background", serverConf, true);
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

    /**
     * Make a request to the server only to get the maximum possible heap size to use for compilations.
     *
     * @param port_file The port file used to synchronize creation of this server.
     * @param id The identify of the compilation.
     * @param out Standard out information.
     * @param err Standard err information.
     * @return The maximum heap size in bytes.
     */
    @Override
    public SysInfo getSysInfo() {
        try (Socket socket = tryConnect()) {
            // The ObjectInputStream constructor will block until the
            // corresponding ObjectOutputStream has written and flushed the
            // header, so it is important that the ObjectOutputStreams on server
            // and client are opened before the ObjectInputStreams.
            ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
            oos.writeObject(id);
            oos.writeObject(SjavacServer.CMD_SYS_INFO);
            oos.flush();
            return (SysInfo) ois.readObject();
        } catch (IOException | ClassNotFoundException ex) {
            Log.error("[CLIENT] Exception caught: " + ex);
            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
        }
        return null;
    }

    @Override
    public CompilationResult compile(String protocolId,
                                     String invocationId,
                                     String[] args,
                                     List<File> explicitSources,
                                     Set<URI> sourcesToCompile,
                                     Set<URI> visibleSources) {
        CompilationResult result;
        try (Socket socket = tryConnect()) {
            // The ObjectInputStream constructor will block until the
            // corresponding ObjectOutputStream has written and flushed the
            // header, so it is important that the ObjectOutputStreams on server
            // and client are opened before the ObjectInputStreams.
            ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
            oos.writeObject(id);
            oos.writeObject(SjavacServer.CMD_COMPILE);
            oos.writeObject(protocolId);
            oos.writeObject(invocationId);
            oos.writeObject(args);
            oos.writeObject(explicitSources);
            oos.writeObject(sourcesToCompile);
            oos.writeObject(visibleSources);
            oos.flush();
            result = (CompilationResult) ois.readObject();
        } catch (IOException | ClassNotFoundException ex) {
            Log.error("Exception caught: " + ex);
            result = new CompilationResult(CompilationResult.ERROR_FATAL);
            result.stderr = Util.getStackTrace(ex);
        }
        return result;
    }

    private Socket tryConnect() throws IOException {

        PortFile portFile;
        try {
            // This should be taken care of at a higher level (JDK-8048451)
            portFile = SjavacServer.getPortFile(portfileName);
        } catch (FileNotFoundException e) {
            // Reached for instance if directory of port file does not exist
            Log.error("Port file inaccessable: " + e);
            throw new RuntimeException(e);
        }
        for (int i = 0; i < MAX_CONNECT_ATTEMPTS; i++) {
            Log.info(String.format("Trying to connect (attempt %d of %d)",
                                   i+1, MAX_CONNECT_ATTEMPTS));
            try {
                if (!makeSureServerIsRunning(portFile))
                    continue;
                Socket socket = new Socket();
                InetAddress localhost = InetAddress.getByName(null);
                socket.connect(new InetSocketAddress(localhost, portFile.getPort()),
                               CONNECTION_TIMEOUT);
                return socket;
            } catch (ProblemException | IOException ex) {
                Log.error("Caught exception during tryConnect: " + ex);
            }

            try {
                Thread.sleep(WAIT_BETWEEN_CONNECT_ATTEMPTS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        throw new IOException("Could not connect to server");
    }

    private boolean makeSureServerIsRunning(PortFile portFile)
            throws IOException, ProblemException, FileNotFoundException {

        synchronized (portFile) {
            portFile.lock();
            portFile.getValues();
            portFile.unlock();
        }

        if (!portFile.containsPortInfo()) {
            String forkCmd = SjavacServer.fork(sjavacForkCmd,
                                               portFile.getFilename(),
                                               logfile,
                                               poolsize,
                                               keepalive,
                                               System.err,
                                               stdouterrfile,
                                               background);
            if (!portFile.waitForValidValues()) {
                // This can be simplified once JDK-8048457 has been addressed
                // since we won't have an SjavacClient if background = false
                if (background) {
                    // There seems be some problem with spawning the external
                    // process (for instance no fork command provided and no
                    // sjavac on path)
                    StringWriter sw = new StringWriter();
                    SjavacClient.printFailedAttempt(forkCmd,
                                                    stdouterrfile,
                                                    new PrintWriter(sw));
                    Log.error(sw.toString());
                }
            }
        }
        return portFile.containsPortInfo();
    }


    public static void printFailedAttempt(String cmd, String f, PrintWriter err) {
        err.println("---- Failed to start javac server with this command -----");
        err.println(cmd);
        try {
            BufferedReader in = new BufferedReader(new FileReader(f));
            err.println("---- stdout/stderr output from attempt to start javac server -----");
            for (;;) {
                String l = in.readLine();
                if (l == null) {
                    break;
                }
                err.println(l);
            }
            err.println("------------------------------------------------------------------");
        } catch (Exception e) {
            err.println("The stdout/stderr output in file " + f + " does not exist and the server did not start.");
        }
    }

    @Override
    public void shutdown() {
        // Nothing to clean up
    }
}
