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

/**
 * @test
 * @bug 8030750
 * @summary Test alternate hashing of strings in Serviceability Agent.
 * @requires vm.hasSAandCanAttach
 * @library /test/lib
 * @library /lib/testlibrary
 * @compile AlternateHashingTest.java
 * @run main/timeout=240 AlternateHashingTest
 */

import java.io.BufferedReader;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.Arrays;
import jdk.testlibrary.JDKToolLauncher;
import jdk.test.lib.apps.LingeredApp;
import jdk.test.lib.Platform;

public class AlternateHashingTest {

    private static LingeredAppWithAltHashing theApp = null;

    /**
     *
     * @param vmArgs  - tool arguments to launch jhsdb
     * @return exit code of tool
     */
    public static void launch(String expectedMessage, String cmd) throws IOException {

        System.out.println("Starting LingeredApp");
        try {
            theApp = new LingeredAppWithAltHashing();
            LingeredApp.startApp(Arrays.asList("-Xmx256m"), theApp);

            System.out.println("Starting clhsdb against " + theApp.getPid());
            JDKToolLauncher launcher = JDKToolLauncher.createUsingTestJDK("jhsdb");
            launcher.addToolArg("clhsdb");
            launcher.addToolArg("--pid=" + Long.toString(theApp.getPid()));

            ProcessBuilder processBuilder = new ProcessBuilder(launcher.getCommand());
            processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
            Process toolProcess = processBuilder.start();

            try (OutputStream out = toolProcess.getOutputStream()) {
                out.write(cmd.getBytes());
                out.write("quit\n".getBytes());
            }

            boolean result = false;
            try (BufferedReader reader =
                 new BufferedReader(new InputStreamReader(toolProcess.getInputStream()))) {
                String line;

                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    System.out.println(line);

                    if (line.contains(expectedMessage)) {
                        result = true;
                        break;
                    }
                }
            }

            toolProcess.waitFor();

            if (toolProcess.exitValue() != 0) {
                throw new RuntimeException("FAILED CLHSDB terminated with non-zero exit code " + toolProcess.exitValue());
            }

            if (!result) {
                throw new RuntimeException(cmd + " command output is missing the message " + expectedMessage);
            }

        } catch (Exception ex) {
            throw new RuntimeException("Test ERROR " + ex, ex);
        } finally {
            LingeredApp.stopApp(theApp);
        }
    }


    public static void testAltHashing() throws IOException {

        launch("Stack in use by Java", "threads\n");
    }

    public static void main(String[] args) throws Exception {

        testAltHashing();

        // The test throws RuntimeException on error.
        // IOException is thrown if LingeredApp can't start because of some bad
        // environment condition
        System.out.println("Test PASSED");
    }
}
