package com.sun.tools.sjavac.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sun.tools.sjavac.Util;

import static com.sun.tools.sjavac.server.CompilationResult.ERROR_BUT_TRY_AGAIN;
import static com.sun.tools.sjavac.server.CompilationResult.ERROR_FATAL;

public class JavacServiceClient implements JavacService {


    // The id can perhaps be used in the future by the javac server to reuse the
    // JavaCompiler instance for several compiles using the same id.
    private final String id;
    private final String portfile;
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
    private final String sjavac;

    public JavacServiceClient(String settings) {
        id = Util.extractStringOption("id", settings);
        portfile = Util.extractStringOption("portfile", settings);
        logfile = Util.extractStringOption("logfile", settings, portfile + ".javaclog");
        stdouterrfile = Util.extractStringOption("stdouterrfile", settings, portfile + ".stdouterr");
        background = Util.extractBooleanOption("background", settings, true);
        sjavac = Util.extractStringOption("sjavac", settings, "sjavac");
        int poolsize = Util.extractIntOption("poolsize", settings);
        keepalive = Util.extractIntOption("keepalive", settings, 120);

        this.poolsize = poolsize > 0 ? poolsize : Runtime.getRuntime().availableProcessors();
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
        try {
            CompilationResult cr = useServer(new String[0],
                                             Collections.<URI>emptySet(),
                                             Collections.<URI>emptySet(),
                                             Collections.<URI, Set<String>>emptyMap());
            return cr.sysinfo;
        } catch (Exception e) {
            return new SysInfo(-1, -1);
        }
    }

    @Override
    public CompilationResult compile(String protocolId,
                                     String invocationId,
                                     String[] args,
                                     List<File> explicitSources,
                                     Set<URI> sourcesToCompile,
                                     Set<URI> visibleSources) {
        // Delegate to useServer, which delegates to compileHelper
        return useServer(args, sourcesToCompile, visibleSources, null);
    }

    /**
     * Connect and compile using the javac server settings and the args. When using more advanced features, the sources_to_compile and visible_sources are
     * supplied to the server and meta data is returned in package_artifacts, package_dependencies and package_pubapis.
     */
    public CompilationResult compileHelper(String id,
                                           String[] args,
                                           Set<URI> sourcesToCompile,
                                           Set<URI> visibleSources) {

        CompilationResult rc = new CompilationResult(-3);

        try {
            PortFile portFile = JavacServer.getPortFile(this.portfile);

            int port = portFile.containsPortInfo() ? portFile.getPort() : 0;
            if (port == 0) {
                return new CompilationResult(ERROR_BUT_TRY_AGAIN);
            }
            long cookie = portFile.getCookie();
            // Acquire the localhost/127.0.0.1 address.
            InetAddress addr = InetAddress.getByName(null);
            SocketAddress sockaddr = new InetSocketAddress(addr, port);
            Socket sock = new Socket();
            int timeoutMs = JavacServer.CONNECTION_TIMEOUT * 1000;
            try {
                sock.connect(sockaddr, timeoutMs);
            } catch (java.net.ConnectException e) {
                rc.setReturnCode(ERROR_BUT_TRY_AGAIN);
                rc.stderr = "Could not connect to javac server found in portfile: " + portFile.getFilename() + " " + e;
                return rc;
            }
            if (!sock.isConnected()) {
                rc.setReturnCode(ERROR_BUT_TRY_AGAIN);
                rc.stderr = "Could not connect to javac server found in portfile: " + portFile.getFilename();
                return rc;
            }

            //
            // Send arguments
            //
            BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
            PrintWriter sockout = new PrintWriter(sock.getOutputStream());

            sockout.println(JavacServer.PROTOCOL_COOKIE_VERSION);
            sockout.println("" + cookie);
            sockout.println(JavacServer.PROTOCOL_CWD);
            sockout.println(System.getProperty("user.dir"));
            sockout.println(JavacServer.PROTOCOL_ID);
            sockout.println(id);
            sockout.println(JavacServer.PROTOCOL_ARGS);
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
            sockout.println(JavacServer.PROTOCOL_SOURCES_TO_COMPILE);
            for (URI uri : sourcesToCompile) {
                sockout.println(uri.toString());
            }
            sockout.println(JavacServer.PROTOCOL_VISIBLE_SOURCES);
            for (URI uri : visibleSources) {
                sockout.println(uri.toString());
            }
            sockout.println(JavacServer.PROTOCOL_END);
            sockout.flush();

            //
            // Receive result
            //
            StringBuffer stdout = new StringBuffer();
            StringBuffer stderr = new StringBuffer();

            if (!JavacServiceClient.expect(in, JavacServer.PROTOCOL_STDOUT)) {
                return new CompilationResult(ERROR_FATAL);
            }
            // Load stdout
            for (;;) {
                String l = in.readLine();
                if (l == null) {
                    return new CompilationResult(ERROR_FATAL);
                }
                if (l.equals(JavacServer.PROTOCOL_STDERR)) {
                    break;
                }
                stdout.append(l);
                stdout.append('\n');
            }
            // Load stderr
            for (;;) {
                String l = in.readLine();
                if (l == null) {
                    return new CompilationResult(ERROR_FATAL);
                }
                if (l.equals(JavacServer.PROTOCOL_PACKAGE_ARTIFACTS)) {
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
                    return new CompilationResult(ERROR_FATAL);
                }
                if (l.equals(JavacServer.PROTOCOL_PACKAGE_DEPENDENCIES)) {
                    break;
                }
                if (l.length() > 1 && l.charAt(0) == '+') {
                    String pkg = l.substring(1);
                    lastUriSet = new HashSet<>();
                    rc.packageArtifacts.put(pkg, lastUriSet);
                } else if (l.length() > 1 && lastUriSet != null) {
                    lastUriSet.add(new URI(l.substring(1)));
                }
            }
            // Load package dependencies
            Set<String> lastPackageSet = null;
            for (;;) {
                String l = in.readLine();
                if (l == null) {
                    return new CompilationResult(ERROR_FATAL);
                }
                if (l.equals(JavacServer.PROTOCOL_PACKAGE_PUBLIC_APIS)) {
                    break;
                }
                if (l.length() > 1 && l.charAt(0) == '+') {
                    String pkg = l.substring(1);
                    lastPackageSet = new HashSet<>();
                    rc.packageDependencies.put(pkg, lastPackageSet);
                } else if (l.length() > 1 && lastPackageSet != null) {
                    lastPackageSet.add(l.substring(1));
                }
            }
            // Load package pubapis
            Map<String, StringBuffer> tmp = new HashMap<>();
            StringBuffer lastPublicApi = null;
            for (;;) {
                String l = in.readLine();
                if (l == null) {
                    return new CompilationResult(ERROR_FATAL);
                }
                if (l.equals(JavacServer.PROTOCOL_SYSINFO)) {
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
                //assert (packagePublicApis.get(p) == null);
                String api = tmp.get(p).toString();
                rc.packagePubapis.put(p, api);
            }
            // Now reading the max memory possible.
            for (;;) {
                String l = in.readLine();
                if (l == null) {
                    return new CompilationResult(ERROR_FATAL);
                }
                if (l.equals(JavacServer.PROTOCOL_RETURN_CODE)) {
                    break;
                }
                if (l.startsWith("num_cores=")) {
                    rc.sysinfo.numCores = Integer.parseInt(l.substring(10));
                }
                if (l.startsWith("max_memory=")) {
                    rc.sysinfo.maxMemory = Long.parseLong(l.substring(11));
                }
            }
            String l = in.readLine();
            if (l == null) {
                rc.setReturnCode(ERROR_FATAL);
                rc.stderr = "No return value from the server!";
                return rc;
            }
            rc.setReturnCode(Integer.parseInt(l));
            rc.stdout = stdout.toString();
            rc.stderr = stderr.toString();
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            rc.stderr = sw.toString();
        }
        return rc;
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
    public CompilationResult useServer(String[] args,
                                       Set<URI> sourcesToCompile,
                                       Set<URI> visibleSources,
                                       Map<URI, Set<String>> visibleClasses) {
        try {
            if (portfile == null) {
                CompilationResult cr = new CompilationResult(CompilationResult.ERROR_FATAL);
                cr.stderr = "No portfile was specified!";
                return cr;
            }

            int attempts = 0;
            CompilationResult rc;
            do {
                PortFile port_file = JavacServer.getPortFile(portfile);
                synchronized (port_file) {
                    port_file.lock();
                    port_file.getValues();
                    port_file.unlock();
                }
                if (!port_file.containsPortInfo()) {
                    String cmd = JavacServer.fork(sjavac, port_file.getFilename(), logfile, poolsize, keepalive, System.err, stdouterrfile, background);

                    if (background && !port_file.waitForValidValues()) {
                        // Ouch the server did not start! Lets print its stdouterrfile and the command used.
                        StringWriter sw = new StringWriter();
                        JavacServiceClient.printFailedAttempt(cmd, stdouterrfile, new PrintWriter(sw));
                        // And give up.
                        CompilationResult cr = new CompilationResult(ERROR_FATAL);
                        cr.stderr = sw.toString();
                        return cr;
                    }
                }
                rc = compileHelper(id, args, sourcesToCompile, visibleSources);
                // Try again until we manage to connect. Any error after that
                // will cause the compilation to fail.
                if (rc.returnCode == CompilationResult.ERROR_BUT_TRY_AGAIN) {
                    // We could not connect to the server. Try again.
                    attempts++;
                    try {
                        Thread.sleep(JavacServer.WAIT_BETWEEN_CONNECT_ATTEMPTS * 1000);
                    } catch (InterruptedException e) {
                    }
                }
            } while (rc.returnCode == ERROR_BUT_TRY_AGAIN && attempts < JavacServer.MAX_NUM_CONNECT_ATTEMPTS);
            return rc;
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            CompilationResult cr = new CompilationResult(ERROR_FATAL);
            cr.stderr = sw.toString();
            return cr;
        }
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

    /**
     * Expect this key on the next line read from the reader.
     */
    public static boolean expect(BufferedReader in, String key) throws IOException {
        String s = in.readLine();
        if (s != null && s.equals(key)) {
            return true;
        }
        return false;
    }
}
