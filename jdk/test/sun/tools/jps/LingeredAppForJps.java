/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.net.URL;

public class LingeredAppForJps extends LingeredApp {

    // Copy runApp logic here to be able to run an app from JarFile
    public void runAppWithName(List<String> vmArguments, String runName)
            throws IOException {

        List<String> cmd = runAppPrepare(vmArguments);
        if (runName.endsWith(".jar")) {
            cmd.add("-Xdiag");
            cmd.add("-jar");
        }
        cmd.add(runName);
        cmd.add(lockFileName);

        printCommandLine(cmd);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        // we don't expect any error output but make sure we are not stuck on pipe
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        appProcess = pb.start();
        startGobblerPipe();
    }

    public static LingeredApp startAppJar(List<String> cmd, LingeredAppForJps app, File jar) throws IOException {
        app.createLock();
        try {
            app.runAppWithName(cmd, jar.getAbsolutePath());
            app.waitAppReady(appWaitTime);
        } catch (Exception ex) {
            app.deleteLock();
            throw ex;
        }

        return app;
    }

    /**
     * The jps output should contain processes' names
     * (except when jps is started in quite mode).
     * The expected name of the test process is prepared here.
     */
    public static String getProcessName() {
        return LingeredAppForJps.class.getSimpleName();
    }

    public static String getProcessName(File jar) {
        return jar.getName();
    }

    // full package name for the application's main class or the full path
    // name to the application's JAR file:

    public static String getFullProcessName() {
        return LingeredAppForJps.class.getCanonicalName();
    }

    public static String getFullProcessName(File jar) {
        return jar.getAbsolutePath();
    }

    public static File buildJar() throws IOException {
        String className = LingeredAppForJps.class.getName();
        File jar = new File(className + ".jar");
        String testClassPath = System.getProperty("test.class.path", "?");

        File manifestFile = new File(className + ".mf");
        String nl = System.getProperty("line.separator");
        try (BufferedWriter output = new BufferedWriter(new FileWriter(manifestFile))) {
            output.write("Main-Class: " + className + nl);
        }

        List<String> jarArgs = new ArrayList<>();
        jarArgs.add("-cfm");
        jarArgs.add(jar.getAbsolutePath());
        jarArgs.add(manifestFile.getAbsolutePath());

        for (String path : testClassPath.split(File.pathSeparator)) {
            String classFullName = path + File.separator + className + ".class";
            File f = new File(classFullName);
            if (f.exists()) {
              jarArgs.add("-C");
              jarArgs.add(path);
              jarArgs.add(".");
              System.out.println("INFO: scheduled to jar " + path);
              break;
            }
        }

        System.out.println("Running jar " + jarArgs.toString());
        sun.tools.jar.Main jarTool = new sun.tools.jar.Main(System.out, System.err, "jar");
        if (!jarTool.run(jarArgs.toArray(new String[jarArgs.size()]))) {
            throw new IOException("jar failed: args=" + jarArgs.toString());
        }

        manifestFile.delete();
        jar.deleteOnExit();

        // Print content of jar file
        System.out.println("Content of jar file" + jar.getAbsolutePath());

        jarArgs = new ArrayList<>();
        jarArgs.add("-tvf");
        jarArgs.add(jar.getAbsolutePath());

        jarTool = new sun.tools.jar.Main(System.out, System.err, "jar");
        if (!jarTool.run(jarArgs.toArray(new String[jarArgs.size()]))) {
            throw new IOException("jar failed: args=" + jarArgs.toString());
        }

        return jar;
    }

    public static void main(String args[]) {
        LingeredApp.main(args);
    }
 }
