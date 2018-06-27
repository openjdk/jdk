/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Scanner;
import java.util.List;
import java.io.File;
import java.io.IOException;
import java.util.stream.Collectors;
import java.io.OutputStream;
import jdk.test.lib.apps.LingeredApp;
import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.Platform;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.Utils;
import jdk.test.lib.Asserts;

/**
 * @test
 * @requires vm.hasSAandCanAttach
 * @library /test/lib
 * @run main/othervm TestClhsdbJstackLock
 */

public class TestClhsdbJstackLock {

    private static final String JSTACK_OUT_FILE = "jstack_out.txt";

    private static void verifyJStackOutput() throws Exception {

        Exception unexpected = null;
        File jstackFile = new File(JSTACK_OUT_FILE);
        Asserts.assertTrue(jstackFile.exists() && jstackFile.isFile(),
                           "File with jstack output not created: " +
                           jstackFile.getAbsolutePath());
        try {
            Scanner scanner = new Scanner(jstackFile);

            boolean classLockOwnerFound = false;
            boolean classLockWaiterFound = false;
            boolean objectLockOwnerFound = false;
            boolean primitiveLockOwnerFound = false;

            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                System.out.println(line);

                if (line.contains("missing reason for ")) {
                    unexpected = new RuntimeException("Unexpected msg: missing reason for ");
                    break;
                }
                if (line.matches("^\\s+- locked <0x[0-9a-f]+> \\(a java\\.lang\\.Class for LingeredAppWithLock\\)$")) {
                    classLockOwnerFound = true;
                }
                if (line.matches("^\\s+- waiting to lock <0x[0-9a-f]+> \\(a java\\.lang\\.Class for LingeredAppWithLock\\)$")) {
                    classLockWaiterFound = true;
                }
                if (line.matches("^\\s+- locked <0x[0-9a-f]+> \\(a java\\.lang\\.Thread\\)$")) {
                    objectLockOwnerFound = true;
                }
                if (line.matches("^\\s+- locked <0x[0-9a-f]+> \\(a java\\.lang\\.Class for int\\)$")) {
                    primitiveLockOwnerFound = true;
                }
            }

            if (!classLockOwnerFound || !classLockWaiterFound ||
                !objectLockOwnerFound || !primitiveLockOwnerFound) {
                unexpected = new RuntimeException(
                      "classLockOwnerFound = " + classLockOwnerFound +
                      ", classLockWaiterFound = " + classLockWaiterFound +
                      ", objectLockOwnerFound = " + objectLockOwnerFound +
                      ", primitiveLockOwnerFound = " + primitiveLockOwnerFound);
            }
            if (unexpected != null) {
                throw unexpected;
            }
        } catch (Exception ex) {
            throw new RuntimeException("Test ERROR " + ex, ex);
        } finally {
            jstackFile.delete();
        }
    }

    private static void startClhsdbForLock(long lingeredAppPid) throws Exception {

        Process p;
        JDKToolLauncher launcher = JDKToolLauncher.createUsingTestJDK("jhsdb");
        launcher.addToolArg("clhsdb");
        launcher.addToolArg("--pid");
        launcher.addToolArg(Long.toString(lingeredAppPid));

        ProcessBuilder pb = new ProcessBuilder();
        pb.command(launcher.getCommand());
        System.out.println(pb.command().stream().collect(Collectors.joining(" ")));

        try {
            p = pb.start();
        } catch (Exception attachE) {
            throw new Error("Couldn't start jhsdb or attach to LingeredApp : " + attachE);
        }

        // Issue the 'jstack' input at the clhsdb prompt.
        OutputStream input = p.getOutputStream();
        String str = "jstack > " + JSTACK_OUT_FILE + "\nquit\n";
        try {
            input.write(str.getBytes());
            input.flush();
        } catch (IOException ioe) {
            throw new Error("Problem issuing the jstack command: " + str, ioe);
        }

        OutputAnalyzer output = new OutputAnalyzer(p);

        try {
            p.waitFor();
        } catch (InterruptedException ie) {
            p.destroyForcibly();
            throw new Error("Problem awaiting the child process: " + ie, ie);
        }

        output.shouldHaveExitValue(0);
    }

    public static void main (String... args) throws Exception {

        LingeredApp app = null;

        try {
            List<String> vmArgs = new ArrayList<String>(Utils.getVmOptions());

            app = new LingeredAppWithLock();
            LingeredApp.startApp(vmArgs, app);
            System.out.println ("Started LingeredApp with pid " + app.getPid());
            startClhsdbForLock(app.getPid());
            verifyJStackOutput();
        } finally {
            LingeredApp.stopApp(app);
        }
    }
}
