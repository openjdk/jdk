/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @summary Basic test for jhsdb launcher
 * @library /test/lib/share/classes
 * @library /lib/testlibrary
 * @build jdk.testlibrary.*
 * @build jdk.test.lib.apps.*
 * @run main BasicLauncherTest
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import jdk.testlibrary.JDKToolLauncher;
import jdk.testlibrary.Utils;
import jdk.testlibrary.OutputAnalyzer;
import jdk.testlibrary.ProcessTools;
import jdk.test.lib.apps.LingeredApp;
import jdk.testlibrary.Platform;

public class BasicLauncherTest {

    private final static String toolName = "jhsdb";
    private static LingeredApp theApp = null;

    /**
     *
     * @return exit code of tool
     */
    public static int launchCLHSDB()
        throws IOException {

        System.out.println("Starting LingeredApp");
        try {
            theApp = LingeredApp.startApp();

            System.out.println("Starting clhsdb against " + theApp.getPid());
            JDKToolLauncher launcher = JDKToolLauncher.createUsingTestJDK(toolName);
            launcher.addToolArg("clhsdb");
            launcher.addToolArg("--pid=" + Long.toString(theApp.getPid()));

            ProcessBuilder processBuilder = new ProcessBuilder(launcher.getCommand());
            processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
            Process toolProcess = processBuilder.start();
            toolProcess.getOutputStream().write("quit\n".getBytes());
            toolProcess.getOutputStream().close();

            // By default child process output stream redirected to pipe, so we are reading it in foreground.
            BufferedReader reader = new BufferedReader(new InputStreamReader(toolProcess.getInputStream()));

            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line.trim());
            }

            toolProcess.waitFor();

            return toolProcess.exitValue();
        } catch (Exception ex) {
            throw new RuntimeException("Test ERROR " + ex, ex);
        } finally {
            LingeredApp.stopApp(theApp);
        }
    }

    /**
     *
     * @param vmArgs  - vm and java arguments to launch test app
     * @return exit code of tool
     */
    public static void launch(String expectedMessage, List<String> toolArgs)
        throws IOException {

        System.out.println("Starting LingeredApp");
        try {
            theApp = LingeredApp.startApp();

            System.out.println("Starting " + toolName + " " + toolArgs.get(0) + " against " + theApp.getPid());
            JDKToolLauncher launcher = JDKToolLauncher.createUsingTestJDK(toolName);

            for (String cmd : toolArgs) {
                launcher.addToolArg(cmd);
            }

            launcher.addToolArg("--pid=" + Long.toString(theApp.getPid()));

            ProcessBuilder processBuilder = new ProcessBuilder(launcher.getCommand());
            processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
            OutputAnalyzer output = ProcessTools.executeProcess(processBuilder);;
            output.shouldContain(expectedMessage);
            output.shouldHaveExitValue(0);

        } catch (Exception ex) {
            throw new RuntimeException("Test ERROR " + ex, ex);
        } finally {
            LingeredApp.stopApp(theApp);
        }
    }

    public static void launch(String expectedMessage, String... toolArgs)
        throws IOException {

        launch(expectedMessage, Arrays.asList(toolArgs));
    }

    public static void main(String[] args)
        throws IOException {

        if (!Platform.shouldSAAttach()) {
            // Silently skip the test if we don't have enough permissions to attach
            System.err.println("Error! Insufficient permissions to attach.");
            return;
        }

        launchCLHSDB();

        launch("No deadlocks found", "jstack");
        launch("compiler detected", "jmap");
        launch("Java System Properties", "jinfo");

        // The test throws RuntimeException on error.
        // IOException is thrown if LingeredApp can't start because of some bad
        // environment condition
        System.out.println("Test PASSED");
    }
}
