/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 *
 */

/*
 * @test
 * @bug 8307990
 * @requires (os.family == "linux") | (os.family == "aix")
 * @requires vm.debug
 * @library /test/lib
 * @run main/othervm/timeout=300 JspawnhelperProtocol
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jdk.test.lib.process.ProcessTools;

public class JspawnhelperProtocol {
    // Timout in seconds
    private static final int TIMEOUT = 60;
    // Base error code to communicate various error states from the parent process to the top-level test
    private static final int ERROR = 10;
    private static final String[] CMD = { "pwd" };
    private static final String ENV_KEY = "JTREG_JSPAWNHELPER_PROTOCOL_TEST";

    private static void parentCode(String arg) throws IOException, InterruptedException {
        System.out.println("Recursively executing 'JspawnhelperProtocol " + arg + "'");
        Process p = null;
        try {
            p = Runtime.getRuntime().exec(CMD);
        } catch (Exception e) {
            e.printStackTrace(System.out);
            System.exit(ERROR);
        }
        if (!p.waitFor(TIMEOUT, TimeUnit.SECONDS)) {
            System.out.println("Child process timed out");
            System.exit(ERROR + 1);
        }
        if (p.exitValue() == 0) {
            String pwd = p.inputReader().readLine();
            String realPwd = Path.of("").toAbsolutePath().toString();
            if (!realPwd.equals(pwd)) {
                System.out.println("Child process returned '" + pwd + "' (expected '" + realPwd + "')");
                System.exit(ERROR + 2);
            }
            System.out.println("  Successfully executed '" + CMD[0] + "'");
            System.exit(0);
        } else {
            System.out.println("  Failed to executed '" + CMD[0] + "' (exitValue=" + p.exitValue() + ")");
            System.exit(ERROR + 3);
        }
    }

    private static void normalExec() throws Exception {
        ProcessBuilder pb;
        pb = ProcessTools.createLimitedTestJavaProcessBuilder("-Djdk.lang.Process.launchMechanism=posix_spawn",
                                                              "JspawnhelperProtocol",
                                                              "normalExec");
        pb.inheritIO();
        Process p = pb.start();
        if (!p.waitFor(TIMEOUT, TimeUnit.SECONDS)) {
            throw new Exception("Parent process timed out");
        }
        if (p.exitValue() != 0) {
            throw new Exception("Parent process exited with " + p.exitValue());
        }
    }

    private static void simulateCrashInChild(int stage) throws Exception {
        ProcessBuilder pb;
        pb = ProcessTools.createLimitedTestJavaProcessBuilder("-Djdk.lang.Process.launchMechanism=posix_spawn",
                                                              "JspawnhelperProtocol",
                                                              "simulateCrashInChild" + stage);
        pb.environment().put(ENV_KEY, Integer.toString(stage));
        Process p = pb.start();

        boolean foundCrashInfo = false;
        try (BufferedReader br = p.inputReader()) {
            String line = br.readLine();
            while (line != null) {
                System.out.println(line);
                if (line.equals("posix_spawn:0")) {
                    foundCrashInfo = true;
                }
                line = br.readLine();
            }
        }
        if (!foundCrashInfo) {
            throw new Exception("Wrong output from child process");
        }
        if (!p.waitFor(TIMEOUT, TimeUnit.SECONDS)) {
            throw new Exception("Parent process timed out");
        }

        int ret = p.exitValue();
        if (ret == 0) {
            throw new Exception("Expected error during child execution");
        }
        System.out.println("Parent exit code: " + ret);
    }

    private static void simulateCrashInParent(int stage) throws Exception {
        ProcessBuilder pb;
        pb = ProcessTools.createLimitedTestJavaProcessBuilder("-Djdk.lang.Process.launchMechanism=posix_spawn",
                                                              "JspawnhelperProtocol",
                                                              "simulateCrashInParent" + stage);
        pb.environment().put(ENV_KEY, Integer.toString(stage));
        Process p = pb.start();

        String line = null;
        try (BufferedReader br = p.inputReader()) {
            line = br.readLine();
            while (line != null && !line.startsWith("posix_spawn:")) {
                System.out.println(line);
                line = br.readLine();
            }
        }
        if (line == null) {
            throw new Exception("Wrong output from parent process");
        }
        System.out.println(line);
        long childPid = Integer.parseInt(line.substring(line.indexOf(':') + 1));

        if (!p.waitFor(TIMEOUT, TimeUnit.SECONDS)) {
            throw new Exception("Parent process timed out");
        }

        Optional<ProcessHandle> oph = ProcessHandle.of(childPid);
        if (!oph.isEmpty()) {
            ProcessHandle ph = oph.get();
            try {
                // Give jspawnhelper a chance to exit gracefully
                ph.onExit().get(TIMEOUT, TimeUnit.SECONDS);
            } catch (TimeoutException te) {
                Optional<String> cmd = ph.info().command();
                if (cmd.isPresent() && cmd.get().endsWith("jspawnhelper")) {
                    throw new Exception("jspawnhelper still alive after parent Java process terminated");
                }
            }
        }
        int ret = p.exitValue();
        if (ret != stage) {
            throw new Exception("Expected exit code " + stage + " but got " + ret);
        }
        System.out.println("Parent exit code: " + ret);
    }

    private static void simulateTruncatedWriteInParent(int stage) throws Exception {
        ProcessBuilder pb;
        pb = ProcessTools.createLimitedTestJavaProcessBuilder("-Djdk.lang.Process.launchMechanism=posix_spawn",
                                                              "JspawnhelperProtocol",
                                                              "simulateTruncatedWriteInParent" + stage);
        pb.environment().put(ENV_KEY, Integer.toString(stage));
        Process p = pb.start();

        BufferedReader br = p.inputReader();
        String line = br.readLine();
        while (line != null && !line.startsWith("posix_spawn:")) {
            System.out.println(line);
            line = br.readLine();
        }
        if (line == null) {
            throw new Exception("Wrong output from parent process");
        }
        System.out.println(line);

        if (!p.waitFor(TIMEOUT, TimeUnit.SECONDS)) {
            throw new Exception("Parent process timed out");
        }
        line = br.readLine();
        while (line != null) {
            System.out.println(line);
            line = br.readLine();
        }

        int ret = p.exitValue();
        if (ret != ERROR) {
            throw new Exception("Expected exit code " + ERROR + " but got " + ret);
        }
        System.out.println("Parent exit code: " + ret);
    }

    public static void main(String[] args) throws Exception {
        // This test works as follows:
        //  - jtreg executes the test class `JspawnhelperProtocol` without arguments.
        //    This is the initial "grandparent" process.
        //  - For each sub-test (i.e. `normalExec()`, `simulateCrashInParent()` and
        //    `simulateCrashInChild()`), a new sub-process (called the "parent") will be
        //    forked which executes `JspawnhelperProtocol` recursively with a corresponding
        //    command line argument.
        //  - The forked `JspawnhelperProtocol` process (i.e. the "parent") runs
        //    `JspawnhelperProtocol::parentCode()` which forks off yet another sub-process
        //    (called the "child").
        //  - The sub-tests in the "grandparent" check that various abnormal program
        //    terminations in the "parent" or the "child" process are handled gracefully and
        //    don't lead to deadlocks or zombie processes.
        if (args.length > 0) {
            // Entry point for recursive execution in the "parent" process
            parentCode(args[0]);
        } else {
            // Main test entry for execution from jtreg
            normalExec();
            simulateCrashInParent(1);
            simulateCrashInParent(2);
            simulateCrashInParent(3);
            simulateCrashInChild(4);
            simulateCrashInChild(5);
            simulateCrashInChild(6);
            simulateTruncatedWriteInParent(99);
        }
    }
}
