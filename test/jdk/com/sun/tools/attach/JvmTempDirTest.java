/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.tools.attach.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.List;
import java.io.File;

import jdk.test.lib.thread.ProcessThread;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

/*
 * @test
 * @bug 8384557
 * @summary Test to make sure attach and jvmstat work correctly when -XX:AltTempDir is set.
 *
 * @requires os.family == "linux"
 * @library /test/lib
 * @modules jdk.attach
 *          jdk.jartool/sun.tools.jar
 *
 * @run build Application RunnerUtil
 * @run main/timeout=200 JvmTempDirTest
 */

/*
 * This test is similar to TempDirTest.java. The property java.io.tmpdir does not affect how
 * jdk.attach works, but -XX:AltTempDir does.
 *
 * This test runs with an extra long timeout since it takes a really long time with -Xcomp
 * when starting many processes.
 */

import jdk.test.lib.util.FileUtils;

public class JvmTempDirTest {

    private static long startTime;

    public static void main(String args[]) throws Throwable {

        startTime = System.currentTimeMillis();

        Path clientTmpDir = Files.createTempDirectory(Path.of("/tmp"), "c");
        Path targetTmpDir = Files.createTempDirectory(Path.of("/tmp"), "t");

        try {
            // Run the test with all possible combinations of setting AltTempDir.
            // Different setting will cause the attach mechanism to fail.
            String notFound = "not found in VM list";
            runExperiment(null, null, true, null);
            runExperiment(targetTmpDir, targetTmpDir, true, null);
            runExperiment(clientTmpDir, clientTmpDir, true, null);

            runExperiment(clientTmpDir, null, false, notFound);
            runExperiment(clientTmpDir, targetTmpDir, false, notFound);
            runExperiment(null, targetTmpDir, false, notFound);
        } finally {
            FileUtils.deleteFileTreeWithRetry(clientTmpDir);
            FileUtils.deleteFileTreeWithRetry(targetTmpDir);
        }

        String name = String.valueOf('a').repeat(200);
        Path veryLongDir = Files.createTempDirectory(Path.of("/tmp"), name);
        try {
            runExperiment(veryLongDir, veryLongDir, false, "Socket file path too long");
        } finally {
            FileUtils.deleteFileTreeWithRetry(veryLongDir);
        }

        // Test a directory with only proc in one part of the name.
        Path procTempDir = Files.createTempDirectory(Path.of("/tmp"), "proc");
        Path procDir = Files.createDirectory(procTempDir.resolve("proc"));
        try {
            runExperiment(procDir, procDir, true, null);
        } finally {
            FileUtils.deleteFileTreeWithRetry(procDir);
            FileUtils.deleteFileTreeWithRetry(procTempDir);
        }

        Path hsperfDir = Files.createTempDirectory(Path.of("/tmp"), "hsperfdata_");
        try {
            runExperiment(hsperfDir, hsperfDir, true, null);
        } finally {
            FileUtils.deleteFileTreeWithRetry(hsperfDir);
        }

        // Create /tmp/tmp<tempdir>, and try to use /tmp/tmp<tempdir>/noexist
        Path tmpDir = Files.createTempDirectory(Path.of("/tmp"), "tmp");
        try {
            Path noExist = tmpDir.resolve("noexist");
            runNoExistTest(noExist);
        } finally {
            FileUtils.deleteFileTreeWithRetry(tmpDir);
        }

        Path relativeDir = Files.createTempDirectory(Path.of("."), "a");
        try {
            runRelativeTest(relativeDir);
        } finally {
            FileUtils.deleteFileTreeWithRetry(relativeDir);
        }
    }

    /*
     * The actual test is in the nested class TestMain.
     * The responsibility of this class is to:
     * 1. Start the Application class in a separate process.
     * 2. Find the pid and shutdown port of the running Application.
     * 3. Launch the tests in nested class TestMain that will attach to the Application.
     * 4. Shut down the Application.
     */
    public static void runExperiment(Path clientTmpDir, Path targetTmpDir, boolean shouldPass, String message) throws Throwable {

        System.out.print("### Running tests with overridden tmpdir for");
        System.out.print(" client: " + (clientTmpDir == null ? "no" : "yes"));
        System.out.print(" target: " + (targetTmpDir == null ? "no" : "yes"));
        System.out.println(" ###");

        long elapsedTime = (System.currentTimeMillis() - startTime) / 1000;
        System.out.println("Started after " + elapsedTime + "s");

        ProcessThread processThread = null;
        try {
            String[] tmpDirArg = null;
            if (targetTmpDir != null) {
                tmpDirArg = new String[] {"-XX:AltTempDir=" + targetTmpDir};
            }
            processThread = RunnerUtil.startApplication(tmpDirArg);
            launchTests(processThread.getPid(), clientTmpDir, shouldPass, message);
        } catch (Throwable t) {
            System.out.println("JvmTempDirTest got unexpected exception: " + t);
            t.printStackTrace();
            throw t;
        } finally {
            // Make sure the Application process is stopped.
            RunnerUtil.stopApplication(processThread);
        }

        elapsedTime = (System.currentTimeMillis() - startTime) / 1000;
        System.out.println("Completed after " + elapsedTime + "s");

    }

    /**
     * Runs the actual tests in nested class TestMain.
     * The reason for running the tests in a separate process
     * is that we need to modify the class path and
     * the -XX:AltTempDir argument.
     */
    private static void launchTests(long pid, Path clientTmpDir, boolean shouldPass, String message) throws Throwable {

        String classpath =
            System.getProperty("test.class.path", "");

        String[] tmpDirArg = null;
        if (clientTmpDir != null) {
            tmpDirArg = new String [] {"-XX:AltTempDir=" + clientTmpDir};
        }

        // Arguments : [-XX:AltTempDir=] -classpath cp JvmTempDirTest$TestMain pid
        String[] args = RunnerUtil.concat(
                tmpDirArg,
                new String[] {
                    "-classpath",
                    classpath,
                    "JvmTempDirTest$TestMain",
                    Long.toString(pid) });
        OutputAnalyzer output = ProcessTools.executeTestJava(args);
        if (shouldPass) {
            output.shouldHaveExitValue(0);
        } else {
            output.shouldContain(message);
            output.shouldNotHaveExitValue(0);
        }
    }

    /**
     * This is the actual test. It will attach to the running Application
     * and perform a number of basic attach tests.
     */
    public static class TestMain {
        public static void main(String args[]) throws Exception {
            String pid = args[0];

            // Test 1 - list method should list the target VM
            System.out.println(" - Test: VirtualMachine.list");
            List<VirtualMachineDescriptor> l = VirtualMachine.list();
            boolean found = false;
            for (VirtualMachineDescriptor vmd: l) {
                if (vmd.id().equals(pid)) {
                    found = true;
                    break;
                }
            }
            if (found) {
                System.out.println(" - " + pid + " found.");
            } else {
                throw new RuntimeException(pid + " not found in VM list");
            }

            // Test 2 - try to attach and verify connection

            System.out.println(" - Attaching to application ...");
            VirtualMachine vm = VirtualMachine.attach(pid);

            System.out.println(" - Test: system properties in target VM");
            Properties props = vm.getSystemProperties();
            String value = props.getProperty("attach.test");
            if (value == null || !value.equals("true")) {
                throw new RuntimeException("attach.test property not set");
            }
            System.out.println(" - attach.test property set as expected");
        }
    }

    private static void runNoExistTest(Path tmpDir) throws Throwable {
        // Arguments : [-XX:AltTempDir=] -version
        String[] args = new String[] { "-XX:AltTempDir=" + tmpDir, "-version" };
        OutputAnalyzer output = ProcessTools.executeTestJava(args);
        output.shouldMatch("\\[warning\\]\\[os *\\] Warning: AltTempDir is not an existing or writable directory");
        // Still passes, it's just a warning.
        output.shouldHaveExitValue(0);
    }

    private static void runRelativeTest(Path tmpDir) throws Throwable {
        // Arguments : [-XX:AltTempDir=] -version
        String[] args = new String[] { "-XX:AltTempDir=" + tmpDir, "-version" };
        OutputAnalyzer output = ProcessTools.executeTestJava(args);
        output.shouldMatch("\\[warning\\]\\[os *\\] Warning: AltTempDir is ignored because it must be an absolute pathname");
        // Still passes, it's just a warning.
        output.shouldHaveExitValue(0);
    }
}
