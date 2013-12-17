/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.Future;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;

import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.BaseFileManager;
import com.sun.tools.javac.util.StringUtils;
import com.sun.tools.sjavac.comp.Dependencies;
import com.sun.tools.sjavac.comp.JavaCompilerWithDeps;
import com.sun.tools.sjavac.comp.SmartFileManager;
import com.sun.tools.sjavac.comp.ResolveWithDeps;

/**
 * The compiler thread maintains a JavaCompiler instance and
 * can receive a request from the client, perform the compilation
 * requested and report back the results.
 *
 *  * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</b></p>
 */
public class CompilerThread implements Runnable {
    private JavacServer javacServer;
    private CompilerPool compilerPool;
    private List<Future<?>> subTasks;

    // Communicating over this socket.
    private Socket socket;

    // The necessary classes to do a compilation.
    private com.sun.tools.javac.api.JavacTool compiler;
    private StandardJavaFileManager fileManager;
    private BaseFileManager fileManagerBase;
    private SmartFileManager smartFileManager;
    private Context context;

    // If true, then this thread is serving a request.
    private boolean inUse = false;

    CompilerThread(CompilerPool cp) {
        compilerPool = cp;
        javacServer = cp.getJavacServer();
    }

    /**
     * Execute a minor task, for example generating bytecodes and writing them to disk,
     * that belong to a major compiler thread task.
     */
    public synchronized void executeSubtask(Runnable r) {
        subTasks.add(compilerPool.executeSubtask(this, r));
    }

    /**
     * Count the number of active sub tasks.
     */
    public synchronized int numActiveSubTasks() {
        int c = 0;
        for (Future<?> f : subTasks) {
            if (!f.isDone() && !f.isCancelled()) {
                c++;
            }
        }
        return c;
    }

    /**
     * Use this socket for the upcoming request.
     */
    public void setSocket(Socket s) {
        socket = s;
    }

    /**
     * Prepare the compiler thread for use. It is not yet started.
     * It will be started by the executor service.
     */
    public synchronized void use() {
        assert(!inUse);
        inUse = true;
        compiler = com.sun.tools.javac.api.JavacTool.create();
        fileManager = compiler.getStandardFileManager(null, null, null);
        fileManagerBase = (BaseFileManager)fileManager;
        smartFileManager = new SmartFileManager(fileManager);
        context = new Context();
        context.put(JavaFileManager.class, smartFileManager);
        ResolveWithDeps.preRegister(context);
        JavaCompilerWithDeps.preRegister(context, this);
        subTasks = new ArrayList<Future<?>>();
    }

    /**
     * Prepare the compiler thread for idleness.
     */
    public synchronized void unuse() {
        assert(inUse);
        inUse = false;
        compiler = null;
        fileManager = null;
        fileManagerBase = null;
        smartFileManager = null;
        context = null;
        subTasks = null;
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

    // The request identifier, for example GENERATE_NEWBYTECODE
    String id = "";

    public String currentRequestId() {
        return id;
    }

    PrintWriter stdout;
    PrintWriter stderr;
    int forcedExitCode = 0;

    public void logError(String msg) {
        stderr.println(msg);
        forcedExitCode = -1;
    }

    /**
     * Invoked by the executor service.
     */
    public void run() {
        // Unique nr that identifies this request.
        int thisRequest = compilerPool.startRequest();
        long start = System.currentTimeMillis();
        int numClasses = 0;
        StringBuilder compiledPkgs = new StringBuilder();
        use();

        PrintWriter out = null;
        try {
            javacServer.log("<"+thisRequest+"> Connect from "+socket.getRemoteSocketAddress()+" activethreads="+compilerPool.numActiveRequests());
            BufferedReader in = new BufferedReader(new InputStreamReader(
                                                       socket.getInputStream()));
            out = new PrintWriter(new OutputStreamWriter(
                                                  socket.getOutputStream()));
            if (!expect(in, JavacServer.PROTOCOL_COOKIE_VERSION)) {
                javacServer.log("<"+thisRequest+"> Bad protocol from ip "+socket.getRemoteSocketAddress());
                return;
            }

            String cookie = in.readLine();
            if (cookie == null || !cookie.equals(""+javacServer.getCookie())) {
                javacServer.log("<"+thisRequest+"> Bad cookie from ip "+socket.getRemoteSocketAddress());
                return;
            }
            if (!expect(in, JavacServer.PROTOCOL_CWD)) {
                return;
            }
            String cwd = in.readLine();
            if (cwd == null)
                return;
            if (!expect(in, JavacServer.PROTOCOL_ID)) {
                return;
            }
            id = in.readLine();
            if (id == null)
                return;
            if (!expect(in, JavacServer.PROTOCOL_ARGS)) {
                return;
            }
            ArrayList<String> the_options = new ArrayList<String>();
            ArrayList<File> the_classes = new ArrayList<File>();
            Iterable<File> path = Arrays.<File> asList(new File(cwd));

            for (;;) {
                String l = in.readLine();
                if (l == null)
                    return;
                if (l.equals(JavacServer.PROTOCOL_SOURCES_TO_COMPILE))
                    break;
                if (l.startsWith("--server:"))
                    continue;
                if (!l.startsWith("-") && l.endsWith(".java")) {
                    the_classes.add(new File(l));
                    numClasses++;
                } else {
                    the_options.add(l);
                }
                continue;
            }

            // Load sources to compile
            Set<URI> sourcesToCompile = new HashSet<URI>();
            for (;;) {
                String l = in.readLine();
                if (l == null)
                    return;
                if (l.equals(JavacServer.PROTOCOL_VISIBLE_SOURCES))
                    break;
                try {
                    sourcesToCompile.add(new URI(l));
                    numClasses++;
                } catch (URISyntaxException e) {
                    return;
                }
            }
            // Load visible sources
            Set<URI> visibleSources = new HashSet<URI>();
            boolean fix_drive_letter_case =
                StringUtils.toLowerCase(System.getProperty("os.name")).startsWith("windows");
            for (;;) {
                String l = in.readLine();
                if (l == null)
                    return;
                if (l.equals(JavacServer.PROTOCOL_END))
                    break;
                try {
                    URI u = new URI(l);
                    if (fix_drive_letter_case) {
                        // Make sure the driver letter is lower case.
                        String s = u.toString();
                        if (s.startsWith("file:/") &&
                            Character.isUpperCase(s.charAt(6))) {
                            u = new URI("file:/"+Character.toLowerCase(s.charAt(6))+s.substring(7));
                        }
                    }
                    visibleSources.add(u);
                } catch (URISyntaxException e) {
                    return;
                }
            }

            // A completed request has been received.

            // Now setup the actual compilation....
            // First deal with explicit source files on cmdline and in at file.
            com.sun.tools.javac.util.ListBuffer<JavaFileObject> compilationUnits =
                new com.sun.tools.javac.util.ListBuffer<JavaFileObject>();
            for (JavaFileObject i : fileManager.getJavaFileObjectsFromFiles(the_classes)) {
                compilationUnits.append(i);
            }
            // Now deal with sources supplied as source_to_compile.
            com.sun.tools.javac.util.ListBuffer<File> sourcesToCompileFiles =
                new com.sun.tools.javac.util.ListBuffer<File>();
            for (URI u : sourcesToCompile) {
                sourcesToCompileFiles.append(new File(u));
            }
            for (JavaFileObject i : fileManager.getJavaFileObjectsFromFiles(sourcesToCompileFiles)) {
                compilationUnits.append(i);
            }
            // Log the options to be used.
            StringBuilder options = new StringBuilder();
            for (String s : the_options) {
                options.append(">").append(s).append("< ");
            }
            javacServer.log(id+" <"+thisRequest+"> options "+options.toString());

            forcedExitCode = 0;
            // Create a new logger.
            StringWriter stdoutLog = new StringWriter();
            StringWriter stderrLog = new StringWriter();
            stdout = new PrintWriter(stdoutLog);
            stderr = new PrintWriter(stderrLog);
            com.sun.tools.javac.main.Main.Result rc = com.sun.tools.javac.main.Main.Result.OK;
            try {
                if (compilationUnits.size() > 0) {
                    // Bind the new logger to the existing context.
                    context.put(Log.outKey, stderr);
                    Log.instance(context).setWriter(Log.WriterKind.NOTICE, stdout);
                    Log.instance(context).setWriter(Log.WriterKind.WARNING, stderr);
                    Log.instance(context).setWriter(Log.WriterKind.ERROR, stderr);
                    // Process the options.
                    com.sun.tools.javac.api.JavacTool.processOptions(context, smartFileManager, the_options);
                    fileManagerBase.setContext(context);
                    smartFileManager.setVisibleSources(visibleSources);
                    smartFileManager.cleanArtifacts();
                    smartFileManager.setLog(stdout);
                    Dependencies.instance(context).reset();

                    com.sun.tools.javac.main.Main ccompiler = new com.sun.tools.javac.main.Main("javacTask", stderr);
                    String[] aa = the_options.toArray(new String[0]);

                    // Do the compilation!
                    rc = ccompiler.compile(aa, context, compilationUnits.toList(), null);

                    while (numActiveSubTasks()>0) {
                        try { Thread.sleep(1000); } catch (InterruptedException e) { }
                    }

                    smartFileManager.flush();
                }
            } catch (Exception e) {
                stderr.println(e.getMessage());
                forcedExitCode = -1;
            }

            // Send the response..
            out.println(JavacServer.PROTOCOL_STDOUT);
            out.print(stdoutLog);
            out.println(JavacServer.PROTOCOL_STDERR);
            out.print(stderrLog);
            // The compilation is complete! And errors will have already been printed on out!
            out.println(JavacServer.PROTOCOL_PACKAGE_ARTIFACTS);
            Map<String,Set<URI>> pa = smartFileManager.getPackageArtifacts();
            for (String aPkgName : pa.keySet()) {
                out.println("+"+aPkgName);
                Set<URI> as = pa.get(aPkgName);
                for (URI a : as) {
                    out.println(" "+a.toString());
                }
            }
            Dependencies deps = Dependencies.instance(context);
            out.println(JavacServer.PROTOCOL_PACKAGE_DEPENDENCIES);
            Map<String,Set<String>> pd = deps.getDependencies();
            for (String aPkgName : pd.keySet()) {
                out.println("+"+aPkgName);
                Set<String> ds = pd.get(aPkgName);
                    // Everything depends on java.lang
                    if (!ds.contains(":java.lang")) ds.add(":java.lang");
                for (String d : ds) {
                    out.println(" "+d);
                }
            }
            out.println(JavacServer.PROTOCOL_PACKAGE_PUBLIC_APIS);
            Map<String,String> pp = deps.getPubapis();
            for (String aPkgName : pp.keySet()) {
                out.println("+"+aPkgName);
                String ps = pp.get(aPkgName);
                // getPubapis added a space to each line!
                out.println(ps);
                compiledPkgs.append(aPkgName+" ");
            }
            out.println(JavacServer.PROTOCOL_SYSINFO);
            out.println("num_cores=" + Runtime.getRuntime().availableProcessors());
            out.println("max_memory=" + Runtime.getRuntime().maxMemory());
            out.println(JavacServer.PROTOCOL_RETURN_CODE);

            // Errors from sjavac that affect compilation status!
            int rcv = rc.exitCode;
            if (rcv == 0 && forcedExitCode != 0) {
                rcv = forcedExitCode;
            }
            out.println("" + rcv);
            out.println(JavacServer.PROTOCOL_END);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (out != null) out.close();
                if (!socket.isClosed()) {
                    socket.close();
                }
                socket = null;
            } catch (Exception e) {
                javacServer.log("ERROR "+e);
                e.printStackTrace();
            }
            compilerPool.stopRequest();
            long duration = System.currentTimeMillis()-start;
            javacServer.addBuildTime(duration);
            float classpersec = ((float)numClasses)*(((float)1000.0)/((float)duration));
            javacServer.log(id+" <"+thisRequest+"> "+compiledPkgs+" duration " + duration+ " ms    num_classes="+numClasses+
                             "     classpersec="+classpersec+" subtasks="+subTasks.size());
            javacServer.flushLog();
            unuse();
            compilerPool.returnCompilerThread(this);
        }
    }
}

