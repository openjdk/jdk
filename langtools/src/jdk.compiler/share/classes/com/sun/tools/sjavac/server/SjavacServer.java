/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.tools.sjavac.server;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import com.sun.tools.sjavac.ProblemException;
import com.sun.tools.sjavac.Util;
import com.sun.tools.sjavac.comp.SjavacImpl;
import com.sun.tools.sjavac.comp.PooledSjavac;

/**
 * The JavacServer class contains methods both to setup a server that responds to requests and methods to connect to this server.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class SjavacServer implements Terminable {

    // Used in protocol to indicate which method to invoke
    public final static String CMD_COMPILE = "compile";
    public final static String CMD_SYS_INFO = "sys-info";

    final private String portfilename;
    final private String logfile;
    final private String stdouterrfile;
    final private int poolsize;
    final private int keepalive;
    final private PrintStream err;

    // The secret cookie shared between server and client through the port file.
    // Used to prevent clients from believing that they are communicating with
    // an old server when a new server has started and reused the same port as
    // an old server.
    private final long myCookie;

    // Accumulated build time, not counting idle time, used for logging purposes
    private long totalBuildTime;

    // The javac server specific log file.
    PrintWriter theLog;

    // The sjavac implementation to delegate requests to
    Sjavac sjavac;

    private ServerSocket serverSocket;

    private PortFile portFile;
    private PortFileMonitor portFileMonitor;

    // Set to false break accept loop
    final AtomicBoolean keepAcceptingRequests = new AtomicBoolean();

    // For the client, all port files fetched, one per started javac server.
    // Though usually only one javac server is started by a client.
    private static Map<String, PortFile> allPortFiles;
    private static Map<String, Long> maxServerMemory;

    public SjavacServer(String settings, PrintStream err) throws FileNotFoundException {
        // Extract options. TODO: Change to proper constructor args
        portfilename = Util.extractStringOption("portfile", settings);
        logfile = Util.extractStringOption("logfile", settings);
        stdouterrfile = Util.extractStringOption("stdouterrfile", settings);
        keepalive = Util.extractIntOption("keepalive", settings, 120);
        poolsize = Util.extractIntOption("poolsize", settings,
                                         Runtime.getRuntime().availableProcessors());
        this.err = err;

        myCookie = new Random().nextLong();
        theLog = new PrintWriter(logfile);
    }


    /**
     * Acquire the port file. Synchronized since several threads inside an smart javac wrapper client acquires the same port file at the same time.
     */
    public static synchronized PortFile getPortFile(String filename) throws FileNotFoundException {
        if (allPortFiles == null) {
            allPortFiles = new HashMap<>();
        }
        PortFile pf = allPortFiles.get(filename);

        // Port file known. Does it still exist?
        if (pf != null) {
            try {
                if (!pf.exists())
                    pf = null;
            } catch (IOException ioex) {
                ioex.printStackTrace();
            }
        }

        if (pf == null) {
            pf = new PortFile(filename);
            allPortFiles.put(filename, pf);
        }
        return pf;
    }

    /**
     * Get the cookie used for this server.
     */
    long getCookie() {
        return myCookie;
    }

    /**
     * Get the port used for this server.
     */
    int getPort() {
        return serverSocket.getLocalPort();
    }

    /**
     * Sum up the total build time for this javac server.
     */
    public void addBuildTime(long inc) {
        totalBuildTime += inc;
    }

    /**
     * Log this message.
     */
    public void log(String msg) {
        if (theLog != null) {
            theLog.println(msg);
        } else {
            System.err.println(msg);
        }
    }

    /**
     * Make sure the log is flushed.
     */
    public void flushLog() {
        if (theLog != null) {
            theLog.flush();
        }
    }

    /**
     * Start a server using a settings string. Typically: "--startserver:portfile=/tmp/myserver,poolsize=3" and the string "portfile=/tmp/myserver,poolsize=3"
     * is sent as the settings parameter. Returns 0 on success, -1 on failure.
     */
    public int startServer() throws IOException {
        long serverStart = System.currentTimeMillis();

        // The port file is locked and the server port and cookie is written into it.
        portFile = getPortFile(portfilename);

        synchronized (portFile) {
            portFile.lock();
            portFile.getValues();
            if (portFile.containsPortInfo()) {
                err.println("Javac server not started because portfile exists!");
                portFile.unlock();
                return -1;
            }

            //           .-----------.   .--------.   .------.
            // socket -->| IdleReset |-->| Pooled |-->| Impl |--> javac
            //           '-----------'   '--------'   '------'
            sjavac = new SjavacImpl();
            sjavac = new PooledSjavac(sjavac, poolsize);
            sjavac = new IdleResetSjavac(sjavac,
                                         this,
                                         keepalive * 1000);

            serverSocket = new ServerSocket();
            InetAddress localhost = InetAddress.getByName(null);
            serverSocket.bind(new InetSocketAddress(localhost, 0));

            // At this point the server accepts connections, so it is  now safe
            // to publish the port / cookie information
            portFile.setValues(getPort(), getCookie());
            portFile.unlock();
        }

        portFileMonitor = new PortFileMonitor(portFile, this);
        portFileMonitor.start();

        log("Sjavac server started. Accepting connections...");
        log("    port: " + getPort());
        log("    time: " + new java.util.Date());
        log("    poolsize: " + poolsize);
        flushLog();

        keepAcceptingRequests.set(true);
        do {
            try {
                Socket socket = serverSocket.accept();
                new Thread(new RequestHandler(socket, sjavac)).start();
            } catch (SocketException se) {
                // Caused by serverSocket.close() and indicates shutdown
            }
        } while (keepAcceptingRequests.get());

        log("Shutting down.");

        // No more connections accepted. If any client managed to connect after
        // the accept() was interrupted but before the server socket is closed
        // here, any attempt to read or write to the socket will result in an
        // IOException on the client side.

        long realTime = System.currentTimeMillis() - serverStart;
        log("Total wall clock time " + realTime + "ms build time " + totalBuildTime + "ms");
        flushLog();

        // Shut down
        sjavac.shutdown();

        return 0;
    }

    /**
     * Fork a background process. Returns the command line used that can be printed if something failed.
     */
    public static String fork(String sjavac, String portfile, String logfile, int poolsize, int keepalive,
            final PrintStream err, String stdouterrfile, boolean background)
            throws IOException, ProblemException {
        if (stdouterrfile != null && stdouterrfile.trim().equals("")) {
            stdouterrfile = null;
        }
        final String startserver = "--startserver:portfile=" + portfile + ",logfile=" + logfile + ",stdouterrfile=" + stdouterrfile + ",poolsize=" + poolsize + ",keepalive="+ keepalive;

        if (background) {
            sjavac += "%20" + startserver;
            sjavac = sjavac.replaceAll("%20", " ");
            sjavac = sjavac.replaceAll("%2C", ",");
            // If the java/sh/cmd launcher fails the failure will be captured by stdouterr because of the redirection here.
            String[] cmd = {"/bin/sh", "-c", sjavac + " >> " + stdouterrfile + " 2>&1"};
            if (!(new File("/bin/sh")).canExecute()) {
                ArrayList<String> wincmd = new ArrayList<>();
                wincmd.add("cmd");
                wincmd.add("/c");
                wincmd.add("start");
                wincmd.add("cmd");
                wincmd.add("/c");
                wincmd.add(sjavac + " >> " + stdouterrfile + " 2>&1");
                cmd = wincmd.toArray(new String[wincmd.size()]);
            }
            Process pp = null;
            try {
                pp = Runtime.getRuntime().exec(cmd);
            } catch (Exception e) {
                e.printStackTrace(err);
                e.printStackTrace(new PrintWriter(stdouterrfile));
            }
            StringBuilder rs = new StringBuilder();
            for (String s : cmd) {
                rs.append(s + " ");
            }
            return rs.toString();
        }

        // Do not spawn a background server, instead run it within the same JVM.
        Thread t = new Thread() {
            @Override
            public void run() {
                try {
                    SjavacServer server = new SjavacServer(startserver, err);
                    server.startServer();
                } catch (Throwable t) {
                    t.printStackTrace(err);
                }
            }
        };
        t.setDaemon(true);
        t.start();
        return "";
    }

    @Override
    public void shutdown(String quitMsg) {
        if (!keepAcceptingRequests.compareAndSet(true, false)) {
            // Already stopped, no need to shut down again
            return;
        }

        log("Quitting: " + quitMsg);
        flushLog();

        portFileMonitor.shutdown(); // No longer any need to monitor port file

        // Unpublish port before shutting down socket to minimize the number of
        // failed connection attempts
        try {
            portFile.delete();
        } catch (IOException e) {
            e.printStackTrace(theLog);
        }
        try {
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace(theLog);
        }
    }
}
