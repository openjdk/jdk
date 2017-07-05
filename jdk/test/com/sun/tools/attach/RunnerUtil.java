/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import jdk.testlibrary.OutputAnalyzer;
import jdk.testlibrary.JDKToolLauncher;
import jdk.testlibrary.ProcessTools;
import jdk.testlibrary.Utils;
import jdk.testlibrary.ProcessThread;

/*
 * Utility functions for test runners.
 * (Test runner = class that launch a test)
 */
public class RunnerUtil {
    /**
     * The Application process must be run concurrently with our tests since
     * the tests will attach to the Application.
     * We will run the Application process in a separate thread.
     *
     * The Application must be started with flag "-Xshare:off" for the Retransform
     * test in TestBasics to pass on all platforms.
     *
     * The Application will write its pid and shutdownPort in the given outFile.
     */
    public static ProcessThread startApplication(String outFile) throws Throwable {
        String classpath = System.getProperty("test.class.path", ".");
        String[] args = Utils.addTestJavaOpts(
            "-Dattach.test=true", "-classpath", classpath, "Application", outFile);
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(args);
        ProcessThread pt = new ProcessThread("runApplication", pb);
        pt.start();
        return pt;
    }

    /**
     * Will stop the running Application.
     * First tries to shutdown nicely by connecting to the shut down port.
     * If that fails, the process will be killed hard with stopProcess().
     *
     * If the nice shutdown fails, then an Exception is thrown and the test should fail.
     *
     * @param port The shut down port.
     * @param processThread The process to stop.
     */
    public static void stopApplication(int port, ProcessThread processThread) throws Throwable {
        if (processThread == null) {
            System.out.println("RunnerUtil.stopApplication ignored since proc is null");
            return;
        }
        try {
            System.out.println("RunnerUtil.stopApplication waiting to for shutdown");
            OutputAnalyzer output = ProcessTools.executeTestJvm(
                    "-classpath",
                    System.getProperty("test.class.path", "."),
                    "Shutdown",
                    Integer.toString(port));
            // Verify that both the Shutdown command and the Application finished ok.
            output.shouldHaveExitValue(0);
            processThread.joinAndThrow();
            processThread.getOutput().shouldHaveExitValue(0);
        } catch (Throwable t) {
            System.out.println("RunnerUtil.stopApplication failed. Will kill it hard: " + t);
            processThread.stopProcess();
            throw t;
        }
    }

    /**
     * Creates a jar file.
     * @param args Command to the jar tool.
     */
    public static void createJar(String... args) {
        System.out.println("Running: jar " + Arrays.toString(args));
        sun.tools.jar.Main jar = new sun.tools.jar.Main(System.out, System.err, "jar");
        if (!jar.run(args)) {
            throw new RuntimeException("jar failed: args=" + Arrays.toString(args));
        }
    }

    /**
     * Read process info for the running Application.
     * The Application writes its info to a file with this format:
     * shutdownPort=42994
     * pid=19597
     * done
     *
     * The final "done" is used to make sure the complete file has been written
     * before we try to read it.
     * This function will wait until the file is available.
     *
     * @param filename Path to file to read.
     * @return The ProcessInfo containing pid and shutdownPort.
     */
    public static ProcessInfo readProcessInfo(String filename) throws Throwable {
        System.out.println("Reading port and pid from file: " + filename);
        File file = new File(filename);
        String content = null;

        // Read file or wait for it to be created.
        while (true) {
            content = readFile(file);
            if (content != null && content.indexOf("done") >= 0) {
                break;
            }
            Thread.sleep(100);
        }

        ProcessInfo info = new ProcessInfo();
        // search for a line with format: key=nnn
        Pattern pattern = Pattern.compile("(\\w*)=([0-9]+)\\r?\\n");
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            String key = matcher.group(1);
            int value  = Integer.parseInt(matcher.group(2));
            if ("pid".equals(key)) {
                info.pid = value;
            } else if ("shutdownPort".equals(key)) {
                info.shutdownPort = value;
            }
        }
        System.out.println("processInfo.pid:" + info.pid);
        System.out.println("processInfo.shutdownPort:" + info.shutdownPort);
        return info;
    }

    /**
     * Read the content of a file.
     * @param file The file to read.
     * @return The file content or null if file does not exists.
     */
    public static String readFile(File file) throws IOException {
        if (!file.exists()) {
            return null;
        }
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            String content = new String(bytes);
            return content;
        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * Helper class with info of the running Application.
     */
    public static class ProcessInfo {
        public int pid = -1;
        public int shutdownPort = -1;
    }

}
