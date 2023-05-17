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
 * @requires (os.family == "linux" & !vm.musl)
 * @requires vm.debug
 * @library /test/lib
 * @run main/othervm/timeout=300 JspawnhelperProtocol
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import jdk.test.lib.process.ProcessTools;

public class JspawnhelperProtocol {
    // Timout in seconds
    private static final int TIMEOUT = 60;
    private static final int ERROR = 10;
    private static final String[] CMD = { "pwd" };
    private static final String ENV_KEY = "JTREG_JSPAWNHELPER_PROTOCOL_TEST";

    private static void childCode(String arg) throws IOException, InterruptedException {
        System.out.println("Recursively executing 'JspawnhelperProtocol " + arg + "'");
        Process p = null;
        try {
            p = Runtime.getRuntime().exec(CMD);
        } catch (Exception e) {
            System.out.println(e.toString());
            System.exit(ERROR);
        }
        if (!p.waitFor(TIMEOUT, TimeUnit.SECONDS)) {
            System.exit(ERROR + 1);
        }
        if (p.exitValue() == 0) {
            String pwd = p.inputReader().readLine();
            if (!Path.of("").toAbsolutePath().toString().equals(pwd)) {
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
        pb = ProcessTools.createJavaProcessBuilder("-Djdk.lang.Process.launchMechanism=posix_spawn",
                                                   "JspawnhelperProtocol",
                                                   "normalExec");
        Process p = pb.start();
        if (!p.waitFor(TIMEOUT, TimeUnit.SECONDS)) {
            System.exit(ERROR + 4);
        }
        if (p.exitValue() != 0) {
            throw new Exception("Child exited with " + p.exitValue());
        }
        System.out.println(new String(p.getInputStream().readAllBytes()));
    }

    private static void simulateCrashInChild(int stage) throws Exception {
        ProcessBuilder pb;
        pb = ProcessTools.createJavaProcessBuilder("-Djdk.lang.Process.launchMechanism=posix_spawn",
                                                   "JspawnhelperProtocol",
                                                   "simulateCrashInChild" + stage);
        pb.environment().put(ENV_KEY, Integer.toString(stage));
        Process p = pb.start();

        BufferedReader br = p.inputReader();
        String line = br.readLine();
        boolean foundCrashInfo = false;
        while (line != null) {
            System.out.println(line);
            if (line.equals("posix_spawn:0")) {
                foundCrashInfo = true;
            }
            line = br.readLine();
        }
        if (!foundCrashInfo) {
            throw new Exception("Wrong output from child process");
        }
        if (!p.waitFor(TIMEOUT, TimeUnit.SECONDS)) {
            System.exit(ERROR + 5);
        }

        int ret = p.exitValue();
        if (ret == 0) {
            throw new Exception("Expected error in child execution");
        }
        System.out.println("Child exit code: " + ret);
    }

    private static void simulateCrashInParent(int stage) throws Exception {
        ProcessBuilder pb;
        pb = ProcessTools.createJavaProcessBuilder("-Djdk.lang.Process.launchMechanism=posix_spawn",
                                                   "JspawnhelperProtocol",
                                                   "simulateCrashInParent" + stage);
        pb.environment().put(ENV_KEY, Integer.toString(stage));
        Process p = pb.start();

        BufferedReader br = p.inputReader();
        String line = br.readLine();
        while (line != null && !line.startsWith("posix_spawn:")) {
            System.out.println(line);
            line = br.readLine();
        }
        if (line == null) {
            throw new Exception("Wrong output from child process");
        }
        System.out.println(line);
        long grandChildPid = Integer.parseInt(line.substring(line.indexOf(':') + 1));

        if (!p.waitFor(TIMEOUT, TimeUnit.SECONDS)) {
            System.exit(ERROR + 6);
        }

        Optional<ProcessHandle> oph = ProcessHandle.of(grandChildPid);
        if (!oph.isEmpty()) {
            ProcessHandle ph = oph.get();
            Optional<String> cmd = ph.info().command();
            if (cmd.isPresent() && cmd.get().endsWith("jspawnhelper")) {
                throw new Exception("jspawnhelper still alive after parent Java process terminated");
            }
        }
        int ret = p.exitValue();
        if (ret != stage) {
            throw new Exception("Expected exit code " + stage + " but got " + ret);
        }
        System.out.println("Child exit code: " + ret);
    }

    public static void main(String[] args) throws Exception {

        if (args.length > 0) {
            // We enter here if we get executed recursively from within this test (see below)
            childCode(args[0]);
        } else {
            // Normal test entry
            normalExec();
            simulateCrashInParent(1);
            simulateCrashInParent(2);
            simulateCrashInParent(3);
            simulateCrashInChild(4);
            simulateCrashInChild(5);
            simulateCrashInChild(6);
        }
    }
}