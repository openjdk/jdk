/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.Arrays;

import jdk.test.lib.apps.LingeredApp;
import jdk.test.lib.Platform;
import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.JDKToolFinder;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.SA.SATestUtils;
import jtreg.SkippedException;


/**
 * This is a framework to run 'jhsdb clhsdb' commands.
 * See open/test/hotspot/jtreg/serviceability/sa/ClhsdbLongConstant.java for
 * an example of how to write a test.
 */

public class ClhsdbLauncher {

    private Process toolProcess;
    private boolean needPrivileges;

    public ClhsdbLauncher() {
        toolProcess = null;
        needPrivileges = false;
    }

    /**
     *
     * Launches 'jhsdb clhsdb' and attaches to the Lingered App process.
     * @param lingeredAppPid  - pid of the Lingered App or one its sub-classes.
     */
    private void attach(long lingeredAppPid)
        throws IOException {
        JDKToolLauncher launcher = JDKToolLauncher.createUsingTestJDK("jhsdb");
        launcher.addToolArg("clhsdb");
        if (lingeredAppPid != -1) {
            launcher.addToolArg("--pid=" + Long.toString(lingeredAppPid));
            System.out.println("Starting clhsdb against " + lingeredAppPid);
        }

        List<String> cmdStringList = Arrays.asList(launcher.getCommand());
        if (needPrivileges) {
            cmdStringList = SATestUtils.addPrivileges(cmdStringList);
        }
        ProcessBuilder processBuilder = new ProcessBuilder(cmdStringList);
        processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
        toolProcess = processBuilder.start();
    }

    /**
     *
     * Launches 'jhsdb clhsdb' and loads a core file.
     * @param coreFileName - Name of the corefile to be loaded.
     */
    private void loadCore(String coreFileName)
        throws IOException {

        JDKToolLauncher launcher = JDKToolLauncher.createUsingTestJDK("jhsdb");
        launcher.addToolArg("clhsdb");
        launcher.addToolArg("--core=" + coreFileName);
        launcher.addToolArg("--exe=" + JDKToolFinder.getTestJDKTool("java"));
        System.out.println("Starting clhsdb against corefile " + coreFileName +
                           " and exe " + JDKToolFinder.getTestJDKTool("java"));

        ProcessBuilder processBuilder = new ProcessBuilder(launcher.getCommand());
        processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);

        toolProcess = processBuilder.start();
    }

    /**
     *
     * Runs 'jhsdb clhsdb' commands and checks for expected and unexpected strings.
     * @param commands  - clhsdb commands to execute.
     * @param expectedStrMap - Map of expected strings per command which need to
     *                         be checked in the output of the command.
     * @param unExpectedStrMap - Map of unexpected strings per command which should
     *                           not be present in the output of the command.
     * @return Output of the commands as a String.
     */
    private String runCmd(List<String> commands,
                          Map<String, List<String>> expectedStrMap,
                          Map<String, List<String>> unExpectedStrMap)
        throws IOException, InterruptedException {
        String output;

        if (commands == null) {
            throw new RuntimeException("CLHSDB command must be provided\n");
        }

        try (OutputStream out = toolProcess.getOutputStream()) {
            for (String cmd : commands) {
                out.write((cmd + "\n").getBytes());
            }
            out.write("quit\n".getBytes());
            out.flush();
        }

        OutputAnalyzer oa = new OutputAnalyzer(toolProcess);
        try {
            toolProcess.waitFor();
        } catch (InterruptedException ie) {
            toolProcess.destroyForcibly();
            throw new Error("Problem awaiting the child process: " + ie);
        }

        oa.shouldHaveExitValue(0);
        output = oa.getOutput();
        System.out.println(output);

        String[] parts = output.split("hsdb>");
        for (String cmd : commands) {
            int index = commands.indexOf(cmd) + 1;
            OutputAnalyzer out = new OutputAnalyzer(parts[index]);

            if (expectedStrMap != null) {
                List<String> expectedStr = expectedStrMap.get(cmd);
                if (expectedStr != null) {
                    for (String exp : expectedStr) {
                        out.shouldContain(exp);
                    }
                }
            }

            if (unExpectedStrMap != null) {
                List<String> unExpectedStr = unExpectedStrMap.get(cmd);
                if (unExpectedStr != null) {
                    for (String unExp : unExpectedStr) {
                        out.shouldNotContain(unExp);
                    }
                }
            }
        }
        return output;
    }

    /**
     *
     * Launches 'jhsdb clhsdb', attaches to the Lingered App, executes the commands,
     * checks for expected and unexpected strings.
     * @param lingeredAppPid  - pid of the Lingered App or one its sub-classes.
     * @param commands  - clhsdb commands to execute.
     * @param expectedStrMap - Map of expected strings per command which need to
     *                         be checked in the output of the command.
     * @param unExpectedStrMap - Map of unexpected strings per command which should
     *                           not be present in the output of the command.
     * @return Output of the commands as a String.
     */
    public String run(long lingeredAppPid,
                      List<String> commands,
                      Map<String, List<String>> expectedStrMap,
                      Map<String, List<String>> unExpectedStrMap)
        throws Exception {

        if (!Platform.shouldSAAttach()) {
            if (Platform.isOSX() && SATestUtils.canAddPrivileges()) {
                needPrivileges = true;
            }
            else {
               // Skip the test if we don't have enough permissions to attach
               // and cannot add privileges.
               throw new SkippedException(
                   "SA attach not expected to work. Insufficient privileges.");
           }
        }

        attach(lingeredAppPid);
        return runCmd(commands, expectedStrMap, unExpectedStrMap);
    }

    /**
     *
     * Launches 'jhsdb clhsdb', loads a core file, executes the commands,
     * checks for expected and unexpected strings.
     * @param coreFileName - Name of the core file to be debugged.
     * @param commands  - clhsdb commands to execute.
     * @param expectedStrMap - Map of expected strings per command which need to
     *                         be checked in the output of the command.
     * @param unExpectedStrMap - Map of unexpected strings per command which should
     *                           not be present in the output of the command.
     * @return Output of the commands as a String.
     */
    public String runOnCore(String coreFileName,
                            List<String> commands,
                            Map<String, List<String>> expectedStrMap,
                            Map<String, List<String>> unExpectedStrMap)
        throws IOException, InterruptedException {

        loadCore(coreFileName);
        return runCmd(commands, expectedStrMap, unExpectedStrMap);
    }
}
