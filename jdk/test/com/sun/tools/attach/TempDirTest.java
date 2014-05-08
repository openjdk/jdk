/*
 * Copyright (c) 2014 Oracle and/or its affiliates. All rights reserved.
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

import jdk.testlibrary.OutputAnalyzer;
import jdk.testlibrary.ProcessTools;
import jdk.testlibrary.ProcessThread;

/*
 * @test
 * @bug 8033104
 * @summary Test to make sure attach and jvmstat works correctly when java.io.tmpdir is set
 * @library /lib/testlibrary
 * @run build Application Shutdown RunnerUtil
 * @run main/timeout=10 TempDirTest
 */

public class TempDirTest {

    public static void main(String args[]) throws Throwable {

        Path clientTmpDir = Files.createTempDirectory("TempDirTest-client");
        clientTmpDir.toFile().deleteOnExit();
        Path targetTmpDir = Files.createTempDirectory("TempDirTest-target");
        targetTmpDir.toFile().deleteOnExit();

        // run the test with all possible combinations of setting java.io.tmpdir
        runExperiment(null, null);
        runExperiment(clientTmpDir, null);
        runExperiment(clientTmpDir, targetTmpDir);
        runExperiment(null, targetTmpDir);

    }

    private static int counter = 0;

    /*
     * The actual test is in the nested class TestMain.
     * The responsibility of this class is to:
     * 1. Start the Application class in a separate process.
     * 2. Find the pid and shutdown port of the running Application.
     * 3. Launches the tests in nested class TestMain that will attach to the Application.
     * 4. Shut down the Application.
     */
    public static void runExperiment(Path clientTmpDir, Path targetTmpDir) throws Throwable {

        System.out.print("### Running tests with overridden tmpdir for");
        System.out.print(" client: " + (clientTmpDir == null ? "no" : "yes"));
        System.out.print(" target: " + (targetTmpDir == null ? "no" : "yes"));
        System.out.println(" ###");

        final String pidFile = "TempDirTest.Application.pid-" + counter++;
        ProcessThread processThread = null;
        RunnerUtil.ProcessInfo info = null;
        try {
            String[] tmpDirArg = null;
            if (targetTmpDir != null) {
                tmpDirArg = new String[] {"-Djava.io.tmpdir=" + targetTmpDir};
            }
            processThread = RunnerUtil.startApplication(pidFile, tmpDirArg);
            info = RunnerUtil.readProcessInfo(pidFile);
            launchTests(info.pid, clientTmpDir);
        } catch (Throwable t) {
            System.out.println("TempDirTest got unexpected exception: " + t);
            t.printStackTrace();
            throw t;
        } finally {
            // Make sure the Application process is stopped.
            RunnerUtil.stopApplication(info.shutdownPort, processThread);
        }
    }

    /**
     * Runs the actual tests in nested class TestMain.
     * The reason for running the tests in a separate process
     * is that we need to modify the class path and
     * the -Djava.io.tmpdir property.
     */
    private static void launchTests(int pid, Path clientTmpDir) throws Throwable {
        final String sep = File.separator;

        // Need to add jdk/lib/tools.jar to classpath.
        String classpath =
            System.getProperty("test.class.path", "") + File.pathSeparator +
            System.getProperty("test.jdk", ".") + sep + "lib" + sep + "tools.jar";

        String[] tmpDirArg = null;
        if (clientTmpDir != null) {
            tmpDirArg = new String [] {"-Djava.io.tmpdir=" + clientTmpDir};
        }

        // Arguments : [-Djava.io.tmpdir=] -classpath cp TempDirTest$TestMain pid
        String[] args = RunnerUtil.concat(
                tmpDirArg,
                new String[] {
                    "-classpath",
                    classpath,
                    "TempDirTest$TestMain",
                    Integer.toString(pid) });
        OutputAnalyzer output = ProcessTools.executeTestJvm(args);
        output.shouldHaveExitValue(0);
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
}
