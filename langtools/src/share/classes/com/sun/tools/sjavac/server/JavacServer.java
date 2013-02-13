/*
 * Copyright (c) 2011-2012, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.FileNotFoundException;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Random;

import com.sun.tools.sjavac.Util;
import com.sun.tools.sjavac.ProblemException;
import java.io.*;
import java.util.*;

/**
 * The JavacServer class contains methods both to setup a server that responds to requests and methods to connect to this server.
 *
 * <p><b>This is NOT part of any supported API. If you write code that depends on this, you do so at your own risk. This code and its internal interfaces are
 * subject to change or deletion without notice.</b></p>
 */
public class JavacServer {
    // Responding to this tcp/ip port on localhost.

    private final ServerSocket serverSocket;
    // The secret cookie shared between server and client through the port file.
    private final long myCookie;
    // When the server was started.
    private long serverStart;
    // Accumulated build time for all requests, not counting idle time.
    private long totalBuildTime;
    // The javac server specific log file.
    PrintWriter theLog;
    // The compiler pool that maintains the compiler threads.
    CompilerPool compilerPool;
    // For the client, all port files fetched, one per started javac server.
    // Though usually only one javac server is started by a client.
    private static Map<String, PortFile> allPortFiles;
    private static Map<String, Long> maxServerMemory;
    final static int ERROR_FATAL = -1;
    final static int ERROR_BUT_TRY_AGAIN = -4712;
    final static String PROTOCOL_COOKIE_VERSION = "----THE-COOKIE-V2----";
    final static String PROTOCOL_CWD = "----THE-CWD----";
    final static String PROTOCOL_ID = "----THE-ID----";
    final static String PROTOCOL_ARGS = "----THE-ARGS----";
    final static String PROTOCOL_SOURCES_TO_COMPILE = "----THE-SOURCES-TO-COMPILE----";
    final static String PROTOCOL_VISIBLE_SOURCES = "----THE-VISIBLE-SOURCES----";
    final static String PROTOCOL_END = "----THE-END----";
    final static String PROTOCOL_STDOUT = "----THE-STDOUT----";
    final static String PROTOCOL_STDERR = "----THE-STDERR----";
    final static String PROTOCOL_PACKAGE_ARTIFACTS = "----THE-PACKAGE_ARTIFACTS----";
    final static String PROTOCOL_PACKAGE_DEPENDENCIES = "----THE-PACKAGE_DEPENDENCIES----";
    final static String PROTOCOL_PACKAGE_PUBLIC_APIS = "----THE-PACKAGE-PUBLIC-APIS----";
    final static String PROTOCOL_SYSINFO = "----THE-SYSINFO----";
    final static String PROTOCOL_RETURN_CODE = "----THE-RETURN-CODE----";
    // Check if the portfile is gone, every 5 seconds.
    static int CHECK_PORTFILE_INTERVAL = 5;
    // Wait 2 seconds for response, before giving up on javac server.
    static int CONNECTION_TIMEOUT = 2;
    static int WAIT_BETWEEN_CONNECT_ATTEMPTS = 1;
    static int MAX_NUM_CONNECT_ATTEMPTS = 3;

    /**
     * Acquire the port file. Synchronized since several threads inside an smart javac wrapper client acquires the same port file at the same time.
     */
    private static synchronized PortFile getPortFile(String filename) throws FileNotFoundException {
        if (allPortFiles == null) {
            allPortFiles = new HashMap<String, PortFile>();
        }
        PortFile pf = allPortFiles.get(filename);
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
    public static int startServer(String settings, PrintStream err) {
        try {
            String portfile = Util.extractStringOption("portfile", settings);
            // The log file collects more javac server specific log information.
            String logfile = Util.extractStringOption("logfile", settings);
            // The stdouterr file collects all the System.out and System.err writes to disk.
            String stdouterrfile = Util.extractStringOption("stdouterrfile", settings);
            // We could perhaps use System.setOut and setErr here.
            // But for the moment we rely on the client to spawn a shell where stdout
            // and stderr are redirected already.
            // The pool size is a limit the number of concurrent compiler threads used.
            // The server might use less than these to avoid memory problems.
            int poolsize = Util.extractIntOption("poolsize", settings);
            if (poolsize <= 0) {
                // If not set, default to the number of cores.
                poolsize = Runtime.getRuntime().availableProcessors();
            }

            // How many seconds of inactivity will the server accept before quitting?
            int keepalive = Util.extractIntOption("keepalive", settings);
            if (keepalive <= 0) {
                keepalive = 120;
            }
            // The port file is locked and the server port and cookie is written into it.
            PortFile portFile = getPortFile(portfile);
            JavacServer s;

            synchronized (portFile) {
                portFile.lock();
                portFile.getValues();
                if (portFile.containsPortInfo()) {
                    err.println("Javac server not started because portfile exists!");
                    portFile.unlock();
                    return -1;
                }
                s = new JavacServer(poolsize, logfile);
                portFile.setValues(s.getPort(), s.getCookie());
                portFile.unlock();
            }

            // Run the server. Will delete the port file when shutting down.
            // It will shut down automatically when no new requests have come in
            // during the last 125 seconds.
            s.run(portFile, err, keepalive);
            // The run loop for the server has exited.
            return 0;
        } catch (Exception e) {
            e.printStackTrace(err);
            return -1;
        }
    }

    /**
     * Dispatch a compilation request to a javac server.
     *
     * @param args are the command line args to javac and is allowed to contain source files, @file and other command line options to javac.
     *
     * The generated classes, h files and other artifacts from the javac invocation are stored by the javac server to disk.
     *
     * @param sources_to_compile The sources to compile.
     *
     * @param visibleSources If visible sources has a non zero size, then visible_sources are the only files in the file system that the javac server can see!
     * (Sources to compile are always visible.) The visible sources are those supplied by the (filtered) -sourcepath
     *
     * @param visibleClasses If visible classes for a specific root/jar has a non zero size, then visible_classes are the only class files that the javac server
     * can see, in that root/jar. It maps from a classpath root or a jar file to the set of visible classes for that root/jar.
     *
     * The server return meta data about the build in the following parameters.
     * @param package_artifacts, map from package name to set of created artifacts for that package.
     * @param package_dependencies, map from package name to set of packages that it depends upon.
     * @param package_pubapis, map from package name to unique string identifying its pub api.
     */
    public static int useServer(String settings, String[] args,
            Set<URI> sourcesToCompile,
            Set<URI> visibleSources,
            Map<URI, Set<String>> visibleClasses,
            Map<String, Set<URI>> packageArtifacts,
            Map<String, Set<String>> packageDependencies,
            Map<String, String> packagePubapis,
            SysInfo sysinfo,
            PrintStream out,
            PrintStream err) {
        try {
            // The id can perhaps be used in the future by the javac server to reuse the
            // JavaCompiler instance for several compiles using the same id.
            String id = Util.extractStringOption("id", settings);
            String portfile = Util.extractStringOption("portfile", settings);
            String logfile = Util.extractStringOption("logfile", settings);
            String stdouterrfile = Util.extractStringOption("stdouterrfile", settings);
            String background = Util.extractStringOption("background", settings);
            if (background == null || !background.equals("false")) {
                background = "true";
            }
            // The sjavac option specifies how the server part of sjavac is spawned.
            // If you have the experimental sjavac in your path, you are done. If not, you have
            // to point to a com.sun.tools.sjavac.Main that supports --startserver
            // for example by setting: sjavac=java%20-jar%20...javac.jar%com.sun.tools.sjavac.Main
            String sjavac = Util.extractStringOption("sjavac", settings);
            int poolsize = Util.extractIntOption("poolsize", settings);
            int keepalive = Util.extractIntOption("keepalive", settings);

            if (keepalive <= 0) {
                // Default keepalive for server is 120 seconds.
                // I.e. it will accept 120 seconds of inactivity before quitting.
                keepalive = 120;
            }
            if (portfile == null) {
                err.println("No portfile was specified!");
                return -1;
            }
            if (logfile == null) {
                logfile = portfile + ".javaclog";
            }
            if (stdouterrfile == null) {
                stdouterrfile = portfile + ".stdouterr";
            }
            // Default to sjavac and hope it is in the path.
            if (sjavac == null) {
                sjavac = "sjavac";
            }

            int attempts = 0;
            int rc = -1;
            do {
                PortFile port_file = getPortFile(portfile);
                synchronized (port_file) {
                    port_file.lock();
                    port_file.getValues();
                    port_file.unlock();
                }
                if (!port_file.containsPortInfo()) {
                    String cmd = fork(sjavac, port_file.getFilename(), logfile, poolsize, keepalive, err, stdouterrfile, background);

                    if (background.equals("true") && !port_file.waitForValidValues()) {
                        // Ouch the server did not start! Lets print its stdouterrfile and the command used.
                        printFailedAttempt(cmd, stdouterrfile, err);
                        // And give up.
                        return -1;
                    }
                }
                rc = connectAndCompile(port_file, id, args, sourcesToCompile, visibleSources,
                        packageArtifacts, packageDependencies, packagePubapis, sysinfo,
                        out, err);
                // Try again until we manage to connect. Any error after that
                // will cause the compilation to fail.
                if (rc == ERROR_BUT_TRY_AGAIN) {
                    // We could not connect to the server. Try again.
                    attempts++;
                    try {
                        Thread.sleep(WAIT_BETWEEN_CONNECT_ATTEMPTS);
                    } catch (InterruptedException e) {
                    }
                }
            } while (rc == ERROR_BUT_TRY_AGAIN && attempts < MAX_NUM_CONNECT_ATTEMPTS);
            return rc;
        } catch (Exception e) {
            e.printStackTrace(err);
            return -1;
        }
    }

    private static void printFailedAttempt(String cmd, String f, PrintStream err) {
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

    /**
     * Spawn the server instance.
     */

    private JavacServer(int poolSize, String logfile) throws IOException {
        serverStart = System.currentTimeMillis();
        // Create a server socket on a random port that is bound to the localhost/127.0.0.1 interface.
        // I.e only local processes can connect to this port.
        serverSocket = new ServerSocket(0, 128, InetAddress.getByName(null));
        compilerPool = new CompilerPool(poolSize, this);
        Random rnd = new Random();
        myCookie = rnd.nextLong();
        theLog = new PrintWriter(logfile);
        log("Javac server started. port=" + getPort() + " date=" + (new java.util.Date()) + " with poolsize=" + poolSize);
        flushLog();
    }

    /**
     * Fork a background process. Returns the command line used that can be printed if something failed.
     */
    private static String fork(String sjavac, String portfile, String logfile, int poolsize, int keepalive,
            final PrintStream err, String stdouterrfile, String background)
            throws IOException, ProblemException {
        if (stdouterrfile != null && stdouterrfile.trim().equals("")) {
            stdouterrfile = null;
        }
        final String startserver = "--startserver:portfile=" + portfile + ",logfile=" + logfile + ",stdouterrfile=" + stdouterrfile + ",poolsize=" + poolsize + ",keepalive="+ keepalive;

        if (background.equals("true")) {
            sjavac += "%20" + startserver;
            sjavac = sjavac.replaceAll("%20", " ");
            sjavac = sjavac.replaceAll("%2C", ",");
            // If the java/sh/cmd launcher fails the failure will be captured by stdouterr because of the redirection here.
            String[] cmd = {"/bin/sh", "-c", sjavac + " >> " + stdouterrfile + " 2>&1"};
            if (!(new File("/bin/sh")).canExecute()) {
                ArrayList<String> wincmd = new ArrayList<String>();
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
                    JavacServer.startServer(startserver, err);
                } catch (Throwable t) {
                    t.printStackTrace(err);
                }
            }
        };
        t.start();
        return "";
    }

    /**
     * Expect this key on the next line read from the reader.
     */
    private static boolean expect(BufferedReader in, String key) throws IOException {
        String s = in.readLine();
        if (s != null && s.equals(key)) {
            return true;
        }
        return false;
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
    public static SysInfo connectGetSysInfo(String serverSettings, PrintStream out, PrintStream err) {
        SysInfo sysinfo = new SysInfo(-1, -1);
        String id = Util.extractStringOption("id", serverSettings);
        String portfile = Util.extractStringOption("portfile", serverSettings);
        try {
            PortFile pf = getPortFile(portfile);
            useServer(serverSettings, new String[0],
                    new HashSet<URI>(),
                    new HashSet<URI>(),
                    new HashMap<URI, Set<String>>(),
                    new HashMap<String, Set<URI>>(),
                    new HashMap<String, Set<String>>(),
                    new HashMap<String, String>(),
                    sysinfo, out, err);
        } catch (Exception e) {
            e.printStackTrace(err);
        }
        return sysinfo;
    }

    /**
     * Connect and compile using the javac server settings and the args. When using more advanced features, the sources_to_compile and visible_sources are
     * supplied to the server and meta data is returned in package_artifacts, package_dependencies and package_pubapis.
     */
    private static int connectAndCompile(PortFile portFile, String id, String[] args,
            Set<URI> sourcesToCompile,
            Set<URI> visibleSources,
            Map<String, Set<URI>> packageArtifacts,
            Map<String, Set<String>> packageDependencies,
            Map<String, String> packagePublicApis,
            SysInfo sysinfo,
            PrintStream out,
            PrintStream err) {
        int rc = -3;
        try {
            int port = portFile.getPort();
            if (port == 0) {
                return ERROR_BUT_TRY_AGAIN;
            }
            long cookie = portFile.getCookie();

            // Acquire the localhost/127.0.0.1 address.
            InetAddress addr = InetAddress.getByName(null);
            SocketAddress sockaddr = new InetSocketAddress(addr, port);
            Socket sock = new Socket();
            int timeoutMs = CONNECTION_TIMEOUT * 1000;
            try {
                sock.connect(sockaddr, timeoutMs);
            } catch (java.net.ConnectException e) {
                err.println("Could not connect to javac server found in portfile: " + portFile.getFilename() + " " + e);
                return ERROR_BUT_TRY_AGAIN;
            }
            if (!sock.isConnected()) {
                err.println("Could not connect to javac server found in portfile: " + portFile.getFilename());
                return ERROR_BUT_TRY_AGAIN;
            }
            BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
            PrintWriter sockout = new PrintWriter(sock.getOutputStream());

            sockout.println(PROTOCOL_COOKIE_VERSION);
            sockout.println("" + cookie);
            sockout.println(PROTOCOL_CWD);
            sockout.println(System.getProperty("user.dir"));
            sockout.println(PROTOCOL_ID);
            sockout.println(id);
            sockout.println(PROTOCOL_ARGS);
            for (String s : args) {
                StringBuffer buf = new StringBuffer();
                String[] paths = s.split(File.pathSeparator);
                int c = 0;
                for (String path : paths) {
                    File f = new File(path);
                    if (f.isFile() || f.isDirectory()) {
                        buf.append(f.getAbsolutePath());
                        c++;
                        if (c < paths.length) {
                            buf.append(File.pathSeparator);
                        }
                    } else {
                        buf = new StringBuffer(s);
                        break;
                    }
                }
                sockout.println(buf.toString());
            }
            sockout.println(PROTOCOL_SOURCES_TO_COMPILE);
            for (URI uri : sourcesToCompile) {
                sockout.println(uri.toString());
            }
            sockout.println(PROTOCOL_VISIBLE_SOURCES);
            for (URI uri : visibleSources) {
                sockout.println(uri.toString());
            }
            sockout.println(PROTOCOL_END);
            sockout.flush();

            StringBuffer stdout = new StringBuffer();
            StringBuffer stderr = new StringBuffer();

            if (!expect(in, PROTOCOL_STDOUT)) {
                return ERROR_FATAL;
            }
            // Load stdout
            for (;;) {
                String l = in.readLine();
                if (l == null) {
                    return ERROR_FATAL;
                }
                if (l.equals(PROTOCOL_STDERR)) {
                    break;
                }
                stdout.append(l);
                stdout.append('\n');
            }
            // Load stderr
            for (;;) {
                String l = in.readLine();
                if (l == null) {
                    return ERROR_FATAL;
                }
                if (l.equals(PROTOCOL_PACKAGE_ARTIFACTS)) {
                    break;
                }
                stderr.append(l);
                stderr.append('\n');
            }
            // Load the package artifacts
            Set<URI> lastUriSet = null;
            for (;;) {
                String l = in.readLine();
                if (l == null) {
                    return ERROR_FATAL;
                }
                if (l.equals(PROTOCOL_PACKAGE_DEPENDENCIES)) {
                    break;
                }
                if (l.length() > 1 && l.charAt(0) == '+') {
                    String pkg = l.substring(1);
                    lastUriSet = new HashSet<URI>();
                    packageArtifacts.put(pkg, lastUriSet);
                } else if (l.length() > 1 && lastUriSet != null) {
                    lastUriSet.add(new URI(l.substring(1)));
                }
            }
            // Load package dependencies
            Set<String> lastPackageSet = null;
            for (;;) {
                String l = in.readLine();
                if (l == null) {
                    return ERROR_FATAL;
                }
                if (l.equals(PROTOCOL_PACKAGE_PUBLIC_APIS)) {
                    break;
                }
                if (l.length() > 1 && l.charAt(0) == '+') {
                    String pkg = l.substring(1);
                    lastPackageSet = new HashSet<String>();
                    packageDependencies.put(pkg, lastPackageSet);
                } else if (l.length() > 1 && lastPackageSet != null) {
                    lastPackageSet.add(l.substring(1));
                }
            }
            // Load package pubapis
            Map<String, StringBuffer> tmp = new HashMap<String, StringBuffer>();
            StringBuffer lastPublicApi = null;
            for (;;) {
                String l = in.readLine();
                if (l == null) {
                    return ERROR_FATAL;
                }
                if (l.equals(PROTOCOL_SYSINFO)) {
                    break;
                }
                if (l.length() > 1 && l.charAt(0) == '+') {
                    String pkg = l.substring(1);
                    lastPublicApi = new StringBuffer();
                    tmp.put(pkg, lastPublicApi);
                } else if (l.length() > 1 && lastPublicApi != null) {
                    lastPublicApi.append(l.substring(1));
                    lastPublicApi.append("\n");
                }
            }
            for (String p : tmp.keySet()) {
                assert (packagePublicApis.get(p) == null);
                String api = tmp.get(p).toString();
                packagePublicApis.put(p, api);
            }
            // Now reading the max memory possible.
            for (;;) {
                String l = in.readLine();
                if (l == null) {
                    return ERROR_FATAL;
                }
                if (l.equals(PROTOCOL_RETURN_CODE)) {
                    break;
                }
                if (l.startsWith("num_cores=") && sysinfo != null) {
                    sysinfo.numCores = Integer.parseInt(l.substring(10));
                }
                if (l.startsWith("max_memory=") && sysinfo != null) {
                    sysinfo.maxMemory = Long.parseLong(l.substring(11));
                }
            }
            String l = in.readLine();
            if (l == null) {
                err.println("No return value from the server!");
                return ERROR_FATAL;
            }
            rc = Integer.parseInt(l);
            out.print(stdout);
            err.print(stderr);
        } catch (Exception e) {
            e.printStackTrace(err);
        }
        return rc;
    }

    /**
     * Run the server thread until it exits. Either because of inactivity or because the port file has been deleted by someone else, or overtaken by some other
     * javac server.
     */
    private void run(PortFile portFile, PrintStream err, int keepalive) {
        boolean fileDeleted = false;
        long timeSinceLastCompile;
        try {
            // Every 5 second (check_portfile_interval) we test if the portfile has disappeared => quit
            // Or if the last request was finished more than 125 seconds ago => quit
            // 125 = seconds_of_inactivity_before_shutdown+check_portfile_interval
            serverSocket.setSoTimeout(CHECK_PORTFILE_INTERVAL*1000);
            for (;;) {
                try {
                    Socket s = serverSocket.accept();
                    CompilerThread ct = compilerPool.grabCompilerThread();
                    ct.setSocket(s);
                    compilerPool.execute(ct);
                    flushLog();
                } catch (java.net.SocketTimeoutException e) {
                    if (compilerPool.numActiveRequests() > 0) {
                        // Never quit while there are active requests!
                        continue;
                    }
                    // If this is the timeout after the portfile
                    // has been deleted by us. Then we truly stop.
                    if (fileDeleted) {
                        log("Quitting because of "+(keepalive+CHECK_PORTFILE_INTERVAL)+" seconds of inactivity!");
                        break;
                    }
                    // Check if the portfile is still there.
                    if (!portFile.exists()) {
                        // Time to quit because the portfile was deleted by another
                        // process, probably by the makefile that is done building.
                        log("Quitting because portfile was deleted!");
                        flushLog();
                        break;
                    }
                    // Check if portfile.stop is still there.
                    if (portFile.markedForStop()) {
                        // Time to quit because another process touched the file
                        // server.port.stop to signal that the server should stop.
                        // This is necessary on some operating systems that lock
                        // the port file hard!
                        log("Quitting because a portfile.stop file was found!");
                        portFile.delete();
                        flushLog();
                        break;
                    }
                    // Does the portfile still point to me?
                    if (!portFile.stillMyValues()) {
                        // Time to quit because another build has started.
                        log("Quitting because portfile is now owned by another javac server!");
                        flushLog();
                        break;
                    }

                    // Check how long since the last request finished.
                    long diff = System.currentTimeMillis() - compilerPool.lastRequestFinished();
                    if (diff < keepalive * 1000) {
                        // Do not quit if we have waited less than 120 seconds.
                        continue;
                    }
                    // Ok, time to quit because of inactivity. Perhaps the build
                    // was killed and the portfile not cleaned up properly.
                    portFile.delete();
                    fileDeleted = true;
                    log("" + keepalive + " seconds of inactivity quitting in "
                        + CHECK_PORTFILE_INTERVAL + " seconds!");
                    flushLog();
                    // Now we have a second 5 second grace
                    // period where javac remote requests
                    // that have loaded the data from the
                    // recently deleted portfile can connect
                    // and complete their requests.
                }
            }
        } catch (Exception e) {
            e.printStackTrace(err);
            e.printStackTrace(theLog);
            flushLog();
        } finally {
            compilerPool.shutdown();
        }
        long realTime = System.currentTimeMillis() - serverStart;
        log("Shutting down.");
        log("Total wall clock time " + realTime + "ms build time " + totalBuildTime + "ms");
        flushLog();
    }

    public static void cleanup(String... args) {
        String settings = Util.findServerSettings(args);
        if (settings == null) return;
        String portfile = Util.extractStringOption("portfile", settings);
        String background = Util.extractStringOption("background", settings);
        if (background != null && background.equals("false")) {
            // If the server runs within this jvm, then delete the portfile,
            // since this jvm is about to exit soon.
            File f = new File(portfile);
            f.delete();
        }
    }
}
